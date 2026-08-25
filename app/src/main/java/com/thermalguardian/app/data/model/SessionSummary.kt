package com.thermalguardian.app.data.model

data class SessionSummary(
    val sessionId: Long = 0,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationSeconds: Long,
    val sessionTimeFormatted: String = "",
    val efficiencyPct: Float = 100.0f,
    val throttlingEventsAvoided: Int = 0,
    val avgFps: Float = 60.0f,
    val minFps: Float = 60.0f,
    val onePercentLowFps: Float = 60.0f,
    val peakTemp: Float = 35.0f,
    val avgTemp: Float = 35.0f,
    val startTemp: Float = 35.0f,
    val endTemp: Float = 35.0f,
    val startBatteryPct: Int = 100,
    val endBatteryPct: Int = 100,
    val batteryDrainPctPerHour: Float = 0.0f,
    val throttlingIncidentsCount: Int = 0,
    val grade: String = "A",
    val gradeScore: Int = 90,
    val summaryFeedback: String = "",
    val samplesJson: String = ""
) {
    companion object {
        fun formatSessionTime(durationSeconds: Long): String {
            val minutes = durationSeconds / 60
            return if (minutes > 0) "$minutes min" else "${durationSeconds}s"
        }

        fun computeGrade(
            avgFps: Float,
            onePercentLowFps: Float,
            peakTemp: Float,
            incidentCount: Int,
            batteryDrainPerHour: Float
        ): Pair<String, Int> {
            var score = 100

            // Thermal penalties
            if (peakTemp >= 45.0f) {
                score -= 30
            } else if (peakTemp >= 42.0f) {
                score -= 15
            } else if (peakTemp >= 39.0f) {
                score -= 5
            }

            // FPS stability penalties
            val fpsStabilityRatio = if (avgFps > 0) onePercentLowFps / avgFps else 1.0f
            if (fpsStabilityRatio < 0.70f) {
                score -= 25
            } else if (fpsStabilityRatio < 0.85f) {
                score -= 10
            }

            // Throttle incidents penalties
            score -= (incidentCount * 8).coerceAtMost(30)

            // Battery drain rate penalties (> 30%/hr is heavy)
            if (batteryDrainPerHour > 35f) {
                score -= 10
            } else if (batteryDrainPerHour > 25f) {
                score -= 5
            }

            val finalScore = score.coerceIn(0, 100)
            val grade = when {
                finalScore >= 90 -> "S"
                finalScore >= 80 -> "A"
                finalScore >= 68 -> "B"
                finalScore >= 50 -> "C"
                else -> "D"
            }
            return Pair(grade, finalScore)
        }
    }
}
