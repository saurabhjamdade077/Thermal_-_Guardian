package com.thermalguardian.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thermalguardian.app.data.model.MetricSample
import com.thermalguardian.app.data.model.SessionSummary
import com.thermalguardian.app.ui.theme.CyberDark
import com.thermalguardian.app.ui.theme.DividerColor
import com.thermalguardian.app.ui.theme.ElectricAmber
import com.thermalguardian.app.ui.theme.NeonCyan
import com.thermalguardian.app.ui.theme.NeonCyanDark
import com.thermalguardian.app.ui.theme.NeonGreen
import com.thermalguardian.app.ui.theme.PurpleAccent
import com.thermalguardian.app.ui.theme.SurfaceCard
import com.thermalguardian.app.ui.theme.SurfaceDark
import com.thermalguardian.app.ui.theme.TextMuted
import com.thermalguardian.app.ui.theme.TextPrimary
import com.thermalguardian.app.ui.theme.TextSecondary
import com.thermalguardian.app.ui.theme.ThermalCrimson
import com.thermalguardian.app.ui.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    currentSample: MetricSample,
    isOverlayPermissionGranted: Boolean,
    recentSessions: List<SessionSummary>,
    onRequestOverlayPermission: () -> Unit,
    onStartOverlayClicked: () -> Unit,
    onSessionClicked: (Long) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Thermal Guardian",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberDark)
            )
        },
        containerColor = CyberDark
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Banner
            item {
                AnimatedVisibility(visible = !isOverlayPermissionGranted) {
                    PermissionBannerCard(onRequestPermission = onRequestOverlayPermission)
                }
            }

            // Real-Time Hardware Status Card
            item {
                LiveStatusCard(sample = currentSample)
            }

            // Big Start Guardian Button
            item {
                StartGuardianCard(
                    isReady = isOverlayPermissionGranted,
                    onStart = onStartOverlayClicked,
                    onRequestPermission = onRequestOverlayPermission
                )
            }

            // Recent Gaming Sessions Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Gaming Sessions",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${recentSessions.size} logged",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            if (recentSessions.isEmpty()) {
                item {
                    EmptySessionsCard()
                }
            } else {
                items(recentSessions, key = { it.sessionId }) { session ->
                    SessionItemCard(
                        session = session,
                        onClick = { onSessionClicked(session.sessionId) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun PermissionBannerCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ElectricAmber.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, ElectricAmber.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Permission Required",
                tint = ElectricAmber,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Overlay Permission Required",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Enable 'Display over other apps' to view real-time HUD while playing games.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricAmber),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Enable", color = CyberDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun LiveStatusCard(sample: MetricSample) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
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
                    text = "LIVE SYSTEM TELEMETRY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(NeonGreen.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(text = "ONLINE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonGreen)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusMetricTile(
                    icon = Icons.Default.Thermostat,
                    label = "CPU Temperature",
                    value = Formatters.formatTemp(sample.tempCelsius),
                    accentColor = if (sample.tempCelsius >= 42f) ThermalCrimson else NeonCyan,
                    modifier = Modifier.weight(1f)
                )
                StatusMetricTile(
                    icon = Icons.Default.Speed,
                    label = "Display FPS",
                    value = "${sample.fps.toInt()} FPS",
                    accentColor = NeonGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusMetricTile(
                    icon = Icons.Default.ElectricBolt,
                    label = "Battery & Drain",
                    value = "${sample.batteryPercent}% (${Formatters.formatCurrent(sample.currentMa)})",
                    accentColor = ElectricAmber,
                    modifier = Modifier.weight(1f)
                )
                StatusMetricTile(
                    icon = Icons.Default.Layers,
                    label = "Thermal Status",
                    value = if (sample.thermalStatus == 0) "Normal (Cool)" else "Level ${sample.thermalStatus}",
                    accentColor = PurpleAccent,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun StatusMetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = label, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StartGuardianCard(
    isReady: Boolean,
    onStart: () -> Unit,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isReady) onStart() else onRequestPermission()
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(NeonCyanDark, PurpleAccent)
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Launch Guardian Overlay",
                        color = CyberDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Real-time floating HUD + predictive throttle alerts over your game.",
                        color = CyberDark.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CyberDark),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        tint = NeonCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SessionItemCard(
    session: SessionSummary,
    onClick: () -> Unit
) {
    val gradeColor = when (session.grade) {
        "S" -> NeonCyan
        "A" -> NeonGreen
        "B" -> ElectricAmber
        "C" -> ElectricAmber
        else -> ThermalCrimson
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, DividerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Grade Badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(gradeColor.copy(alpha = 0.15f))
                    .border(1.5.dp, gradeColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = session.grade,
                    color = gradeColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Formatters.formatTimestamp(session.startTimeMs),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⏱ ${Formatters.formatDuration(session.durationSeconds)}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "⚡ ${session.avgFps.toInt()} FPS avg",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "🔥 ${Formatters.formatTemp(session.peakTemp)} peak",
                        color = if (session.peakTemp >= 42f) ThermalCrimson else TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View Details",
                tint = TextMuted
            )
        }
    }
}

@Composable
fun EmptySessionsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, DividerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "No gaming sessions yet",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Launch the Guardian Overlay and play a game to generate your first Report Card!",
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
