package com.thermalguardian.app.collector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.HardwarePropertiesManager
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Choreographer
import com.thermalguardian.app.data.model.MetricSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SensorDataCollector
 *
 * Core telemetry engine that samples real-time device hardware metrics:
 * 1. Temperature (°C):
 *    - Primary Signal: BatteryManager (EXTRA_TEMPERATURE), which provides battery temperature in tenths of a °C.
 *      On unrooted production Android devices, direct access to CPU/GPU thermal zone sysfs files
 *      (/sys/class/thermal/thermal_zone*) is restricted by SELinux on almost all OEM builds (Samsung, Xiaomi, iQOO, etc.).
 *      Battery temperature provides a consistent, universally accessible proxy that correlates strongly with SoC thermal load.
 *    - Supplementary: HardwarePropertiesManager (DEVICE_TEMPERATURE_CPU) and PowerManager.OnThermalStatusChangedListener / getThermalHeadroom (API 29+/30+)
 *      are used when permitted to detect throttling headroom.
 *
 * 2. FPS (Frames Per Second):
 *    - Uses Choreographer.FrameCallback to measure vsync frame render intervals on the main thread.
 *    - Maintains a 1-second (1000 ms) rolling window buffer of timestamps to calculate instantaneous FPS and pacing jitter.
 *
 * 3. Battery Drain Rate (% per minute):
 *    - Tracks battery level changes over a rolling sliding window.
 *    - Computes percentage drain per minute: (ΔBattery% / ΔTime_minutes).
 *    - Also reads instantaneous discharge current in microamperes (BATTERY_PROPERTY_CURRENT_NOW) converted to mA.
 *
 * 4. Permissions:
 *    - Battery broadcasts (ACTION_BATTERY_CHANGED) and Choreographer require NO runtime permissions.
 *    - Floating HUD requires SYSTEM_ALERT_WINDOW, handled via helper methods.
 */
class SensorDataCollector(private val context: Context) {

    private val applicationContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val collectorScope = CoroutineScope(Dispatchers.Default + Job())

    private val isRunning = AtomicBoolean(false)
    private var samplingJob: Job? = null

    // System Services
    private val batteryManager = applicationContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    private val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val hardwarePropertiesManager = applicationContext.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE) as? HardwarePropertiesManager

    // ==========================================
    // 1. TEMPERATURE & BATTERY STATE
    // ==========================================
    @Volatile
    private var currentTempCelsius: Float = 30.0f

    @Volatile
    private var currentBatteryPct: Int = 100

    @Volatile
    private var currentVoltageMv: Int = 4000

    @Volatile
    private var currentDischargeMa: Float = 0.0f

    @Volatile
    private var currentThermalStatus: Int = 0 // PowerManager.THERMAL_STATUS_NONE

    @Volatile
    private var currentThermalHeadroom: Float = -1.0f

    // Battery Drain Tracking (Rolling history of timestamped battery percentages)
    private val batteryHistory = ArrayDeque<Pair<Long, Int>>() // Pair(timestampMs, batteryPct)
    private var initialSessionBatteryPct: Int = -1
    private var sessionStartTimeMs: Long = 0L

    @Volatile
    private var calculatedDrainRatePerMin: Float = 0.0f

    // Thermal Status listener (Android 10+ / API 29)
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

    /**
     * BroadcastReceiver for sticky ACTION_BATTERY_CHANGED intents.
     * Note: ACTION_BATTERY_CHANGED does not require any special runtime permissions.
     */
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            intent?.let { extractBatteryMetrics(it) }
        }
    }

    // ==========================================
    // 2. FPS TRACKING (Choreographer 1-sec Window)
    // ==========================================
    @Volatile
    private var currentFps: Float = 60.0f

    private val frameTimestamps = ArrayDeque<Long>(150)
    private var lastFrameNanos: Long = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning.get()) return

            val now = System.currentTimeMillis()
            synchronized(frameTimestamps) {
                frameTimestamps.addLast(now)

                // Retain only frame timestamps within the 1-second rolling window (last 1000ms)
                val windowThreshold = now - 1000L
                while (frameTimestamps.isNotEmpty() && frameTimestamps.first() < windowThreshold) {
                    frameTimestamps.removeFirst()
                }

                val frameCount = frameTimestamps.size
                if (frameCount >= 2) {
                    val durationSec = (frameTimestamps.last() - frameTimestamps.first()) / 1000.0f
                    if (durationSec > 0.05f) {
                        currentFps = (frameCount / durationSec).coerceIn(0.0f, 144.0f)
                    }
                }
            }

            lastFrameNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    // ==========================================
    // 3. KOTLIN FLOWS (Exposed Streams)
    // ==========================================
    private val _sampleFlow = MutableStateFlow(
        MetricSample(
            tempCelsius = 30.0f,
            fps = 60.0f,
            batteryPercent = 100,
            batteryDrainRatePerMin = 0.0f,
            currentMa = 0f,
            voltageMv = 4000
        )
    )

    /**
     * Combined real-time snapshot flow emitted every 1 second.
     */
    val sampleFlow: StateFlow<MetricSample> = _sampleFlow.asStateFlow()

    /**
     * Dedicated stream for live Temperature (°C) updates.
     */
    val tempFlow: Flow<Float> = _sampleFlow.map { it.tempCelsius }.distinctUntilChanged()

    /**
     * Dedicated stream for live FPS updates.
     */
    val fpsFlow: Flow<Float> = _sampleFlow.map { it.fps }.distinctUntilChanged()

    /**
     * Dedicated stream for live Battery Drain Rate (% per minute).
     */
    val batteryDrainFlow: Flow<Float> = _sampleFlow.map { it.batteryDrainRatePerMin }.distinctUntilChanged()

    // ==========================================
    // LIFECYCLE & SAMPLING LOOP
    // ==========================================

    /**
     * Starts continuous data collection every 1 second.
     */
    fun start(sampleIntervalMs: Long = 1000L) {
        if (!isRunning.compareAndSet(false, true)) return

        sessionStartTimeMs = System.currentTimeMillis()
        initialSessionBatteryPct = -1
        batteryHistory.clear()
        synchronized(frameTimestamps) {
            frameTimestamps.clear()
        }

        // Register Battery Sticky Intent Receiver
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = applicationContext.registerReceiver(batteryReceiver, filter)
        stickyIntent?.let { extractBatteryMetrics(it) }

        // Register PowerManager Thermal Status Listener (API 29+)
        registerThermalStatusListener()

        // Start Choreographer on Main Looper
        mainHandler.post {
            lastFrameNanos = 0L
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }

        // Launch 1-Second Sampling Coroutine Loop
        samplingJob?.cancel()
        samplingJob = collectorScope.launch {
            while (isActive && isRunning.get()) {
                val now = System.currentTimeMillis()

                // Read Temperature
                val temp = readTemperature()

                // Read Instantaneous Discharge Current (mA)
                val currentMa = readInstantaneousCurrentMa()

                // Read Thermal Headroom (API 30+)
                val headroom = readThermalHeadroom()

                // Calculate Battery Drain Rate (% per min)
                val drainRatePerMin = updateAndCalculateDrainRate(now, currentBatteryPct, currentDischargeMa)

                val isTempAvailable = (temp > 5.0f)

                val sample = MetricSample(
                    timestampMs = now,
                    tempCelsius = temp,
                    fps = currentFps,
                    batteryPercent = currentBatteryPct,
                    batteryDrainRatePerMin = drainRatePerMin,
                    currentMa = currentMa,
                    voltageMv = currentVoltageMv,
                    thermalHeadroom = headroom,
                    thermalStatus = currentThermalStatus,
                    isTempSensorAvailable = isTempAvailable
                )

                _sampleFlow.value = sample
                delay(sampleIntervalMs)
            }
        }
    }

    /**
     * Stops all listeners, callbacks, and sampling coroutines.
     */
    fun stop() {
        if (!isRunning.compareAndSet(true, false)) return

        samplingJob?.cancel()
        samplingJob = null

        // Stop Choreographer
        mainHandler.post {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }

        // Unregister Battery Receiver
        try {
            applicationContext.unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}

        // Unregister Thermal Status Listener
        unregisterThermalStatusListener()
    }

    // ==========================================
    // SENSOR READING IMPLEMENTATIONS
    // ==========================================

    /**
     * SENSOR 1: TEMPERATURE
     * Reads temperature using BatteryManager.EXTRA_TEMPERATURE as the primary universal signal.
     * EXTRA_TEMPERATURE gives temperature in tenths of a degree Celsius (e.g., 345 = 34.5°C).
     *
     * In addition, attempts to query HardwarePropertiesManager for CPU core temperatures if available.
     */
    private fun readTemperature(): Float {
        // Attempt HardwarePropertiesManager CPU temperature (API 24+)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && hardwarePropertiesManager != null) {
                val cpuTemps = hardwarePropertiesManager.getDeviceTemperatures(
                    HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,
                    HardwarePropertiesManager.TEMPERATURE_CURRENT
                )
                if (cpuTemps != null && cpuTemps.isNotEmpty()) {
                    val validTemps = cpuTemps.filter { it in 15.0f..105.0f }
                    if (validTemps.isNotEmpty()) {
                        return validTemps.maxOrNull() ?: validTemps.average().toFloat()
                    }
                }
            }
        } catch (_: Exception) {
            // Falls back to BatteryManager temperature on restricted OEM platforms
        }

        // Primary robust signal: Battery temperature (correlates with device chassis & SoC heat)
        return currentTempCelsius
    }

    /**
     * SENSOR 2: BATTERY METRICS EXTRACTION
     * Extracts percentage, raw temperature, and voltage from Intent.ACTION_BATTERY_CHANGED.
     */
    private fun extractBatteryMetrics(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) {
            currentBatteryPct = ((level.toFloat() / scale.toFloat()) * 100).toInt()
            if (initialSessionBatteryPct < 0) {
                initialSessionBatteryPct = currentBatteryPct
            }
        }

        // Raw temperature is in tenths of a degree Celsius (350 => 35.0°C)
        val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        if (rawTemp > 0) {
            currentTempCelsius = rawTemp / 10.0f
        }

        currentVoltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000)
    }

    /**
     * SENSOR 3: BATTERY DRAIN RATE CALCULATION (% per minute)
     * Calculates the rate of battery level decline:
     * 1. Window-based: (Delta Battery% / Delta Time in Minutes)
     * 2. Instantaneous: If session is fresh (< 60s), estimates drain rate from current draw (mA)
     *    assuming an average ~4500mAh gaming battery capacity.
     */
    private fun updateAndCalculateDrainRate(nowMs: Long, batteryPct: Int, currentMa: Float): Float {
        synchronized(batteryHistory) {
            batteryHistory.addLast(Pair(nowMs, batteryPct))

            // Keep up to 5 minutes of battery percentage history
            val windowCutoff = nowMs - (5 * 60 * 1000L)
            while (batteryHistory.isNotEmpty() && batteryHistory.first().first < windowCutoff) {
                batteryHistory.removeFirst()
            }

            // If we have at least 30 seconds of history
            if (batteryHistory.size >= 2) {
                val oldest = batteryHistory.first()
                val deltaMinutes = (nowMs - oldest.first) / (1000.0f * 60.0f)
                val deltaPct = (oldest.second - batteryPct).coerceAtLeast(0)

                if (deltaMinutes >= 0.5f && deltaPct > 0) {
                    val rate = deltaPct / deltaMinutes
                    calculatedDrainRatePerMin = rate
                    return rate
                }
            }
        }

        // Instantaneous estimation fallback based on current draw (mA)
        // Rate (%/min) = (currentMa / capacity_4500mAh) * 100 / 60
        if (currentMa > 100f) {
            val estimatedRatePerMin = (currentMa / 4500.0f) * (100.0f / 60.0f)
            calculatedDrainRatePerMin = estimatedRatePerMin
            return estimatedRatePerMin
        }

        return calculatedDrainRatePerMin
    }

    /**
     * Reads instantaneous battery current in mA using BatteryManager.BATTERY_PROPERTY_CURRENT_NOW.
     * Note: Negative microamperes indicate discharging on standard Android HALs.
     */
    private fun readInstantaneousCurrentMa(): Float {
        if (batteryManager != null) {
            try {
                val microAmps = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                if (microAmps != 0 && microAmps != Int.MIN_VALUE) {
                    val ma = kotlin.math.abs(microAmps) / 1000.0f
                    currentDischargeMa = ma
                    return ma
                }
            } catch (_: Exception) {}
        }
        return currentDischargeMa
    }

    /**
     * Queries thermal headroom from PowerManager (API 30+).
     * Returns a float where:
     * - 0.0: No thermal stress
     * - 1.0: Severe throttling / critical threshold
     */
    private fun readThermalHeadroom(): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && powerManager != null) {
            try {
                val headroom = powerManager.getThermalHeadroom(10)
                if (headroom >= 0.0f) {
                    currentThermalHeadroom = headroom
                    return headroom
                }
            } catch (_: Exception) {}
        }
        return -1.0f
    }

    private fun registerThermalStatusListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            try {
                thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
                    currentThermalStatus = status
                }.also { listener ->
                    powerManager.addThermalStatusListener(applicationContext.mainExecutor, listener)
                }
            } catch (_: Exception) {}
        }
    }

    private fun unregisterThermalStatusListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null && thermalListener != null) {
            try {
                powerManager.removeThermalStatusListener(thermalListener!!)
            } catch (_: Exception) {}
            thermalListener = null
        }
    }

    // ==========================================
    // 4. PERMISSION HANDLING HELPERS
    // ==========================================
    companion object {
        /**
         * Checks whether SYSTEM_ALERT_WINDOW (Draw over other apps) permission is granted.
         * Required for OverlayService and floating HUD.
         */
        fun hasOverlayPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        /**
         * Creates an intent to navigate directly to the application's "Display over other apps" settings screen.
         */
        fun getOverlayPermissionIntent(context: Context): Intent {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            } else {
                Intent()
            }
        }
    }
}
