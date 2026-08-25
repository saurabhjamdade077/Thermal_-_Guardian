package com.thermalguardian.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.thermalguardian.app.ThermalGuardianApp
import com.thermalguardian.app.data.model.SessionSummary
import com.thermalguardian.app.ui.screens.ReportCardScreen
import com.thermalguardian.app.ui.theme.CyberDark
import com.thermalguardian.app.ui.theme.NeonCyan
import com.thermalguardian.app.ui.theme.TextMuted
import com.thermalguardian.app.ui.theme.ThermalGuardianTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ReportCardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetSessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        val repository = (application as ThermalGuardianApp).sessionRepository

        setContent {
            ThermalGuardianTheme {
                var sessionSummary by remember { mutableStateOf<SessionSummary?>(null) }
                var isLoading by remember { mutableStateOf(true) }

                LaunchedEffect(targetSessionId) {
                    val summary = if (targetSessionId > 0) {
                        repository.getSessionById(targetSessionId)
                    } else {
                        repository.getLatestSession()
                    }
                    sessionSummary = summary
                    isLoading = false
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CyberDark),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = NeonCyan)
                    }
                } else {
                    val currentSession = sessionSummary
                    if (currentSession != null) {
                        ReportCardScreen(
                            session = currentSession,
                            onBackClicked = { finish() },
                            onShareClicked = {
                                shareReportAsImage(currentSession)
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(CyberDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "No session data found", color = TextMuted)
                        }
                    }
                }
            }
        }
    }

    /**
     * Exports the current screen as a PNG image and launches the Android share sheet.
     */
    private fun shareReportAsImage(session: SessionSummary) {
        val rootView = window.decorView.rootView

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
            val locationOfViewInWindow = IntArray(2)
            rootView.getLocationInWindow(locationOfViewInWindow)

            try {
                PixelCopy.request(
                    window,
                    android.graphics.Rect(
                        locationOfViewInWindow[0],
                        locationOfViewInWindow[1],
                        locationOfViewInWindow[0] + rootView.width,
                        locationOfViewInWindow[1] + rootView.height
                    ),
                    bitmap,
                    { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            saveBitmapAndShare(bitmap, session.sessionId)
                        } else {
                            fallbackCanvasShare(rootView, session.sessionId)
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            } catch (_: Exception) {
                fallbackCanvasShare(rootView, session.sessionId)
            }
        } else {
            fallbackCanvasShare(rootView, session.sessionId)
        }
    }

    private fun fallbackCanvasShare(view: View, sessionId: Long) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        saveBitmapAndShare(bitmap, sessionId)
    }

    private fun saveBitmapAndShare(bitmap: Bitmap, sessionId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val imagesDir = File(cacheDir, "images").apply { mkdirs() }
                val imageFile = File(imagesDir, "session_report_${sessionId}_${System.currentTimeMillis()}.png")

                FileOutputStream(imageFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val contentUri: Uri = FileProvider.getUriForFile(
                    this@ReportCardActivity,
                    "${packageName}.fileprovider",
                    imageFile
                )

                withContext(Dispatchers.Main) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        putExtra(Intent.EXTRA_SUBJECT, "Thermal Guardian - Gaming Session Report")
                        putExtra(Intent.EXTRA_TEXT, "🛡️ My Gaming Session Report from Thermal & Performance Guardian!")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Session Report"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ReportCardActivity, "Failed to export report image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
    }
}
