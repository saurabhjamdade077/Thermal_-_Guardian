package com.thermalguardian.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.thermalguardian.app.data.model.SessionSummary

@Entity(tableName = "gaming_sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationSeconds: Long,
    val sessionTimeFormatted: String,
    val efficiencyPct: Float,
    val throttlingEventsAvoided: Int,
    val avgFps: Float,
    val minFps: Float,
    val onePercentLowFps: Float,
    val peakTemp: Float,
    val avgTemp: Float,
    val startTemp: Float,
    val endTemp: Float,
    val startBatteryPct: Int,
    val endBatteryPct: Int,
    val batteryDrainPctPerHour: Float,
    val throttlingIncidentsCount: Int,
    val grade: String,
    val gradeScore: Int,
    val summaryFeedback: String,
    val telemetryJson: String = "" // Serialized samples for sparklines/charts
) {
    fun toSessionSummary(): SessionSummary {
        return SessionSummary(
            sessionId = id,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            durationSeconds = durationSeconds,
            sessionTimeFormatted = sessionTimeFormatted,
            efficiencyPct = efficiencyPct,
            throttlingEventsAvoided = throttlingEventsAvoided,
            avgFps = avgFps,
            minFps = minFps,
            onePercentLowFps = onePercentLowFps,
            peakTemp = peakTemp,
            avgTemp = avgTemp,
            startTemp = startTemp,
            endTemp = endTemp,
            startBatteryPct = startBatteryPct,
            endBatteryPct = endBatteryPct,
            batteryDrainPctPerHour = batteryDrainPctPerHour,
            throttlingIncidentsCount = throttlingIncidentsCount,
            grade = grade,
            gradeScore = gradeScore,
            summaryFeedback = summaryFeedback,
            samplesJson = telemetryJson
        )
    }

    companion object {
        fun fromSessionSummary(summary: SessionSummary): SessionEntity {
            return SessionEntity(
                id = summary.sessionId,
                startTimeMs = summary.startTimeMs,
                endTimeMs = summary.endTimeMs,
                durationSeconds = summary.durationSeconds,
                sessionTimeFormatted = if (summary.sessionTimeFormatted.isNotBlank()) summary.sessionTimeFormatted else SessionSummary.formatSessionTime(summary.durationSeconds),
                efficiencyPct = summary.efficiencyPct,
                throttlingEventsAvoided = summary.throttlingEventsAvoided,
                avgFps = summary.avgFps,
                minFps = summary.minFps,
                onePercentLowFps = summary.onePercentLowFps,
                peakTemp = summary.peakTemp,
                avgTemp = summary.avgTemp,
                startTemp = summary.startTemp,
                endTemp = summary.endTemp,
                startBatteryPct = summary.startBatteryPct,
                endBatteryPct = summary.endBatteryPct,
                batteryDrainPctPerHour = summary.batteryDrainPctPerHour,
                throttlingIncidentsCount = summary.throttlingIncidentsCount,
                grade = summary.grade,
                gradeScore = summary.gradeScore,
                summaryFeedback = summary.summaryFeedback,
                telemetryJson = summary.samplesJson
            )
        }
    }
}
