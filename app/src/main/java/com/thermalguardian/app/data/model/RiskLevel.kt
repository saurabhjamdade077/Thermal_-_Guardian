package com.thermalguardian.app.data.model

import androidx.compose.ui.graphics.Color

enum class RiskLevel(
    val title: String,
    val description: String,
    val colorHex: Long,
    val priority: Int
) {
    LOW(
        title = "Optimal",
        description = "Thermals and framerate stable. No throttling anticipated.",
        colorHex = 0xFF00E676, // Neon Green
        priority = 0
    ),
    MEDIUM(
        title = "Caution",
        description = "Temperature rising rapidly or approaching thermal limit.",
        colorHex = 0xFFFFB300, // Amber
        priority = 1
    ),
    HIGH(
        title = "Throttle Alert",
        description = "Imminent thermal throttling detected! FPS drops anticipated.",
        colorHex = 0xFFFF3D71, // Bright Crimson Red
        priority = 2
    );

    val composeColor: Color
        get() = Color(colorHex)
}
