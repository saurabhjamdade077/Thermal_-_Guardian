package com.thermalguardian.app.data.model

/**
 * Output of the trend-based ThrottlePredictor engine.
 */
data class ThrottlePrediction(
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val currentTemp: Float = 0f,
    val tempEma: Float = 0f,
    val tempRateOfChangePer10Sec: Float = 0f, // Temperature rate of change in °C per 10 seconds
    val tempSlopePerMin: Float = 0f, // Rate of change in °C per minute
    val currentFps: Float = 0f,
    val fpsEma: Float = 0f,
    val fpsVariance: Float = 0f, // FPS stability variance over the 15-second window
    val fpsDropPercent: Float = 0f, // % drop compared to recent baseline
    val batteryDrainAcceleration: Float = 0f, // Rate of change of drain rate (% / min^2)
    val estimatedSecondsToThrottle: Int? = null, // e.g. 45 seconds until critical threshold
    val recommendation: String = "Performance is optimal.",
    val isIncidentTriggered: Boolean = false,
    val isFallbackModelActive: Boolean = false // True if predicting via battery drain fallback
)
