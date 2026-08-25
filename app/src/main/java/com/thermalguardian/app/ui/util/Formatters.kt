package com.thermalguardian.app.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {

    fun formatTemp(temp: Float): String {
        return String.format(Locale.getDefault(), "%.1f°C", temp)
    }

    fun formatFps(fps: Float): String {
        return String.format(Locale.getDefault(), "%.1f", fps)
    }

    fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return if (mins > 0) {
            String.format(Locale.getDefault(), "%dm %02ds", mins, secs)
        } else {
            String.format(Locale.getDefault(), "%ds", secs)
        }
    }

    fun formatTimestamp(timestampMs: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }

    fun formatCurrent(currentMa: Float): String {
        return if (currentMa > 0f) {
            String.format(Locale.getDefault(), "%.0f mA", currentMa)
        } else {
            "-- mA"
        }
    }

    fun formatDrainRate(drainRatePerHour: Float): String {
        return String.format(Locale.getDefault(), "%.1f%% / hr", drainRatePerHour)
    }

    fun formatSlope(slopePerMin: Float): String {
        val sign = if (slopePerMin > 0f) "+" else ""
        return String.format(Locale.getDefault(), "%s%.2f°C/min", sign, slopePerMin)
    }
}
