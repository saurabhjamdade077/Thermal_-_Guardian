package com.thermalguardian.app.data.model

/**
 * Snapshot of real-time device telemetry at a specific millisecond timestamp.
 */
data class MetricSample(
    val timestampMs: Long = System.currentTimeMillis(),
    val tempCelsius: Float,
    val fps: Float,
    val batteryPercent: Int,
    val batteryDrainRatePerMin: Float = 0f, // Estimated drain rate in % per minute
    val currentMa: Float = 0f,
    val voltageMv: Int = 4000,
    val thermalHeadroom: Float = -1f, // Float in [0.0, 1.0] where 1.0 is severe throttling (API 30+)
    val thermalStatus: Int = 0, // PowerManager.THERMAL_STATUS_*
    val isTempSensorAvailable: Boolean = true // True if hardware/battery temperature read succeeded
)
