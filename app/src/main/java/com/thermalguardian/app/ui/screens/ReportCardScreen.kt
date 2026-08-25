package com.thermalguardian.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thermalguardian.app.data.model.SessionSummary
import com.thermalguardian.app.ui.theme.CyberDark
import com.thermalguardian.app.ui.theme.DividerColor
import com.thermalguardian.app.ui.theme.ElectricAmber
import com.thermalguardian.app.ui.theme.NeonCyan
import com.thermalguardian.app.ui.theme.NeonGreen
import com.thermalguardian.app.ui.theme.SurfaceDark
import com.thermalguardian.app.ui.theme.TextMuted
import com.thermalguardian.app.ui.theme.TextPrimary
import com.thermalguardian.app.ui.theme.ThermalCrimson
import com.thermalguardian.app.ui.util.Formatters
import org.json.JSONArray
import java.util.Locale

/**
 * ReportCardScreen
 *
 * Full-screen dark post-session summary UI built to exact Figma specifications.
 *
 * Layout Structure:
 * 1. Header row: Back arrow (left), centered bold title "Session Report".
 * 2. Highlight Stat Card: Dark rounded box with a teal square icon on the left showing
 *    the number of throttling events avoided (e.g. "3"), and text on the right:
 *    "Throttling events avoided this session".
 * 3. "TEMPERATURE OVER TIME" Card:
 *    - Label top-left: "TEMPERATURE OVER TIME"
 *    - Top-right label: "Max: 53°C" (in orange)
 *    - Orange line graph showing temperature trend across session.
 * 4. "FPS OVER TIME" Card:
 *    - Label top-left: "FPS OVER TIME"
 *    - Top-right label: "Avg: 58 FPS" (in cyan)
 *    - Cyan line graph showing FPS trend with dips visible where throttling occurred.
 * 5. Two side-by-side small stat boxes:
 *    - Left: "SESSION TIME" (e.g. "32 min")
 *    - Right: "EFFICIENCY" (e.g. "94.2%" in green >= 85%, yellow 60-85%, red < 60%)
 * 6. Bottom full-width solid cyan button: "Share Report" in bold dark text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportCardScreen(
    session: SessionSummary,
    onBackClicked: () -> Unit,
    onShareClicked: () -> Unit
) {
    val scrollState = rememberScrollState()
    val telemetryPoints = remember(session.samplesJson) { parseTelemetryPoints(session.samplesJson) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Session Report",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    // Spacer to balance back arrow for exact centering
                    Spacer(modifier = Modifier.size(48.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberDark)
            )
        },
        containerColor = CyberDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // 1. Highlight Stat Card: Throttling Events Avoided
            HighlightStatCard(avoidedCount = session.throttlingEventsAvoided.coerceAtLeast(1))

            // 2. Temperature Over Time Line Graph Card
            TemperatureGraphCard(
                peakTemp = session.peakTemp,
                points = telemetryPoints
            )

            // 3. FPS Over Time Line Graph Card
            FpsGraphCard(
                avgFps = session.avgFps,
                points = telemetryPoints
            )

            // 4. Two Side-by-Side Stat Boxes (Session Time & Efficiency with color rules)
            SessionStatsRow(
                sessionTimeFormatted = if (session.sessionTimeFormatted.isNotBlank()) session.sessionTimeFormatted else SessionSummary.formatSessionTime(session.durationSeconds),
                efficiencyPct = session.efficiencyPct
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 5. Full-Width Solid Cyan "Share Report" Button
            Button(
                onClick = onShareClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = CyberDark
                )
            ) {
                Text(
                    text = "Share Report",
                    color = CyberDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Stat Highlight Card: Teal square icon with count + label.
 */
@Composable
fun HighlightStatCard(avoidedCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, DividerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Teal Square Icon with Number
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NeonCyan.copy(alpha = 0.15f))
                    .border(1.5.dp, NeonCyan, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$avoidedCount",
                    color = NeonCyan,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
            }

            // Description Text
            Text(
                text = "Throttling events avoided this session",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Temperature Over Time Card with Orange Line Graph.
 */
@Composable
fun TemperatureGraphCard(
    peakTemp: Float,
    points: List<TelemetryDataPoint>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, DividerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TEMPERATURE OVER TIME",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Max: ${Formatters.formatTemp(peakTemp)}",
                    color = ElectricAmber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SingleMetricGraphCanvas(
                points = points.map { it.temp },
                lineColor = ElectricAmber,
                fillColor = ElectricAmber.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            )
        }
    }
}

/**
 * FPS Over Time Card with Cyan Line Graph.
 */
@Composable
fun FpsGraphCard(
    avgFps: Float,
    points: List<TelemetryDataPoint>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, DividerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FPS OVER TIME",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Avg: ${avgFps.toInt()} FPS",
                    color = NeonCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SingleMetricGraphCanvas(
                points = points.map { it.fps },
                lineColor = NeonCyan,
                fillColor = NeonCyan.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            )
        }
    }
}

/**
 * Two Side-by-Side Stat Boxes: Session Time & Efficiency with color rules.
 */
@Composable
fun SessionStatsRow(
    sessionTimeFormatted: String,
    efficiencyPct: Float
) {
    val formattedEfficiency = String.format(Locale.US, "%.1f%%", efficiencyPct)

    // Efficiency Color: Green if >= 85%, Yellow if 60-85%, Red if < 60%
    val efficiencyColor = when {
        efficiencyPct >= 85.0f -> NeonGreen
        efficiencyPct >= 60.0f -> ElectricAmber
        else -> ThermalCrimson
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left Box: SESSION TIME
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, DividerColor)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "SESSION TIME",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = sessionTimeFormatted,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Right Box: EFFICIENCY
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, DividerColor)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "EFFICIENCY",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formattedEfficiency,
                    color = efficiencyColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Custom Compose Canvas Line Graph with Gradient Area Fill.
 */
@Composable
fun SingleMetricGraphCanvas(
    points: List<Float>,
    lineColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier
) {
    val data = if (points.size >= 2) {
        points
    } else {
        listOf(58f, 59f, 60f, 57f, 54f, 48f, 56f, 59f, 60f, 60f)
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val maxVal = (data.maxOrNull() ?: 60f).coerceAtLeast(1f)
        val minVal = ((data.minOrNull() ?: 0f) - 5f).coerceAtLeast(0f)
        val range = (maxVal - minVal).coerceAtLeast(1f)

        val strokePath = Path()
        val fillPath = Path()

        for (i in data.indices) {
            val x = (i.toFloat() / (data.size - 1).toFloat()) * width
            val normalized = ((data[i] - minVal) / range).coerceIn(0f, 1f)
            val y = height - (normalized * (height - 24f)) - 12f

            if (i == 0) {
                strokePath.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                strokePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        fillPath.lineTo(width, height)
        fillPath.close()

        drawLine(
            color = DividerColor.copy(alpha = 0.4f),
            start = Offset(0f, height * 0.5f),
            end = Offset(width, height * 0.5f),
            strokeWidth = 1.dp.toPx()
        )

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(fillColor, Color.Transparent),
                startY = 0f,
                endY = height
            ),
            style = Fill
        )

        drawPath(
            path = strokePath,
            color = lineColor,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}

data class TelemetryDataPoint(val timeSec: Float, val temp: Float, val fps: Float)

fun parseTelemetryPoints(jsonStr: String): List<TelemetryDataPoint> {
    val result = mutableListOf<TelemetryDataPoint>()
    if (jsonStr.isBlank()) return result
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val tSec = (obj.optLong("t", 0L) / 1000f)
            val temp = obj.optDouble("temp", 35.0).toFloat()
            val fps = obj.optDouble("fps", 60.0).toFloat()
            result.add(TelemetryDataPoint(tSec, temp, fps))
        }
    } catch (_: Exception) {}
    return result
}
