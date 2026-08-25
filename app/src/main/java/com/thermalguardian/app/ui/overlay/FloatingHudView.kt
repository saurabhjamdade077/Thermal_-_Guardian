package com.thermalguardian.app.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.thermalguardian.app.data.model.MetricSample
import com.thermalguardian.app.data.model.RiskLevel
import com.thermalguardian.app.data.model.ThrottlePrediction
import com.thermalguardian.app.ui.theme.CyberDark
import com.thermalguardian.app.ui.theme.ElectricAmber
import com.thermalguardian.app.ui.theme.NeonCyan
import com.thermalguardian.app.ui.theme.NeonGreen
import com.thermalguardian.app.ui.theme.TextMuted
import com.thermalguardian.app.ui.theme.TextPrimary
import com.thermalguardian.app.ui.theme.TextSecondary
import com.thermalguardian.app.ui.theme.ThermalCrimson
import kotlinx.coroutines.flow.StateFlow

/**
 * FloatingHudView
 *
 * Draggable, ultra-compact pill HUD designed for live gaming overlays.
 *
 * Figma Design Specs:
 * - Top-left pill-shaped widget overlaying gameplay.
 * - Dark semi-transparent background with rounded corners.
 * - Left side: Game/Session label text (e.g. "COSMIC_VOID_v1.0").
 * - Right side (inline):
 *   1. Small colored status dot (Green = Low, Yellow = Medium, Red = High).
 *   2. Temperature value in °C (e.g. "42°C") or fallback note.
 *   3. FPS value in cyan & bold (e.g. "60 FPS").
 *   4. Battery percentage (e.g. "78%").
 *   5. Stop session button to immediately conclude the session.
 */
class FloatingHudView(
    private val context: Context,
    private val sampleFlow: StateFlow<MetricSample>,
    private val predictionFlow: StateFlow<ThrottlePrediction>,
    private val sessionLabel: String = "COSMIC_VOID_v1.0",
    private val onStopClicked: () -> Unit = {}
) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (composeView != null) return

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 32
            y = 80
        }
        layoutParams = params

        composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@FloatingHudView)
            setViewTreeSavedStateRegistryOwner(this@FloatingHudView)
            setViewTreeViewModelStoreOwner(this@FloatingHudView)

            setContent {
                CompactPillHud(
                    sampleFlow = sampleFlow,
                    predictionFlow = predictionFlow,
                    sessionLabel = sessionLabel,
                    onDrag = { dx, dy ->
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        try {
                            windowManager.updateViewLayout(this, params)
                        } catch (_: Exception) {}
                    },
                    onStop = onStopClicked
                )
            }
        }

        try {
            windowManager.addView(composeView, params)
        } catch (_: Exception) {}
    }

    fun dismiss() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()

        composeView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            composeView = null
        }
    }
}

/**
 * Compact Single-Row Pill HUD Composable.
 */
@Composable
fun CompactPillHud(
    sampleFlow: StateFlow<MetricSample>,
    predictionFlow: StateFlow<ThrottlePrediction>,
    sessionLabel: String,
    onDrag: (Float, Float) -> Unit,
    onStop: () -> Unit
) {
    val sample by sampleFlow.collectAsState()
    val prediction by predictionFlow.collectAsState()

    val targetDotColor = when (prediction.riskLevel) {
        RiskLevel.LOW -> NeonGreen
        RiskLevel.MEDIUM -> ElectricAmber
        RiskLevel.HIGH -> ThermalCrimson
    }

    val dotColor by animateColorAsState(
        targetValue = targetDotColor,
        animationSpec = tween(400),
        label = "dotColor"
    )

    // Pulsing animation for High risk
    val infiniteTransition = rememberInfiniteTransition(label = "hudPulse")
    val dotPulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (prediction.riskLevel == RiskLevel.HIGH) 1.35f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotPulse"
    )

    Card(
        modifier = Modifier
            .wrapContentSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CyberDark.copy(alpha = 0.90f)),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (prediction.riskLevel == RiskLevel.HIGH) ThermalCrimson.copy(alpha = 0.85f) else Color(0x3300E5FF)
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left Side: Session / Game Label (Monospace style)
            Column {
                Text(
                    text = sessionLabel,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
                if (prediction.isFallbackModelActive) {
                    Text(
                        text = "Drain Model Active",
                        color = ElectricAmber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Subtle Vertical Divider
            Box(
                modifier = Modifier
                    .size(width = 1.dp, height = 12.dp)
                    .background(TextMuted.copy(alpha = 0.4f))
            )

            // Right Side Inline Metrics:
            // 1. Small colored status dot (Green / Yellow / Red)
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(dotPulseScale)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            // 2. Temperature Value in °C (or fallback power rate)
            if (!prediction.isFallbackModelActive && sample.tempCelsius > 5f) {
                Text(
                    text = "${sample.tempCelsius.toInt()}°C",
                    color = if (sample.tempCelsius >= 42f) ThermalCrimson else TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "${sample.batteryDrainRatePerMin.toInt()}%/m",
                    color = ElectricAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 3. FPS Value in Cyan & Bold
            Text(
                text = "${sample.fps.toInt()} FPS",
                color = NeonCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )

            // 4. Battery Percentage
            Text(
                text = "${sample.batteryPercent}%",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            // 5. Stop Session Button (to conclude & view report card)
            IconButton(
                onClick = onStop,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.StopCircle,
                    contentDescription = "Stop Session",
                    tint = ThermalCrimson,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
