package com.thermalguardian.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.thermalguardian.app.ThermalGuardianApp
import com.thermalguardian.app.collector.SensorDataCollector
import com.thermalguardian.app.service.OverlayService
import com.thermalguardian.app.ui.screens.DashboardScreen
import com.thermalguardian.app.ui.theme.ThermalGuardianTheme
import com.thermalguardian.app.ui.util.PermissionHelper

class MainActivity : ComponentActivity() {

    private lateinit var sensorCollector: SensorDataCollector
    private var isOverlayPermissionGranted by mutableStateOf(false)
    private var autoStartOnPermission = false

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isOverlayPermissionGranted = PermissionHelper.hasOverlayPermission(this)
        if (isOverlayPermissionGranted) {
            Toast.makeText(this, "Overlay permission granted! Starting Guardian HUD...", Toast.LENGTH_SHORT).show()
            if (autoStartOnPermission) {
                startOverlayService()
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorCollector = SensorDataCollector(applicationContext)

        // 1. Check Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!PermissionHelper.hasNotificationPermission(this)) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 2. Check and Request Overlay Permission on First Launch
        isOverlayPermissionGranted = PermissionHelper.hasOverlayPermission(this)
        if (!isOverlayPermissionGranted) {
            autoStartOnPermission = true
            requestOverlayPermission()
        }

        setContent {
            ThermalGuardianTheme {
                val currentSample by sensorCollector.sampleFlow.collectAsState()
                val repository = (application as ThermalGuardianApp).sessionRepository
                val sessions by repository.allSessionsFlow.collectAsState(initial = emptyList())

                DashboardScreen(
                    currentSample = currentSample,
                    isOverlayPermissionGranted = isOverlayPermissionGranted,
                    recentSessions = sessions,
                    onRequestOverlayPermission = {
                        autoStartOnPermission = true
                        requestOverlayPermission()
                    },
                    onStartOverlayClicked = { startOverlayService() },
                    onSessionClicked = { sessionId ->
                        val intent = Intent(this, ReportCardActivity::class.java).apply {
                            putExtra(ReportCardActivity.EXTRA_SESSION_ID, sessionId)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isOverlayPermissionGranted = PermissionHelper.hasOverlayPermission(this)
        sensorCollector.start(sampleIntervalMs = 1500L)
    }

    override fun onPause() {
        super.onPause()
        sensorCollector.stop()
    }

    private fun requestOverlayPermission() {
        val intent = PermissionHelper.requestOverlayPermissionIntent(this)
        overlayPermissionLauncher.launch(intent)
    }

    private fun startOverlayService() {
        if (!PermissionHelper.hasOverlayPermission(this)) {
            autoStartOnPermission = true
            requestOverlayPermission()
            return
        }

        OverlayService.start(this)
        Toast.makeText(this, "Thermal Guardian Overlay Active! Switch to your game.", Toast.LENGTH_LONG).show()
    }
}
