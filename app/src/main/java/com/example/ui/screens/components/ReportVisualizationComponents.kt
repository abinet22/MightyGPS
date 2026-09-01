package com.example.ui.screens.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DailySummary
import com.example.ui.theme.MC
import com.example.util.UnitFormatter
import java.util.Locale

/**
 * High-visibility dismissible anomaly banner for safety & compliance anomalies (speeding & geofence breaches).
 */
@Composable
fun ReportAnomalyBanner(
    speedingViolationsCount: Int,
    geofenceBreaksCount: Int,
    onViewSafetyTab: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalAnomalies = speedingViolationsCount + geofenceBreaksCount
    if (totalAnomalies <= 0) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF450A0A).copy(alpha = 0.85f),
            contentColor = Color(0xFFFCA5A5)
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MC.StatusOffline.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(MC.StatusOffline.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Anomaly Alert",
                            tint = MC.StatusOffline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "CRITICAL FLEET ANOMALIES DETECTED",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "$totalAnomalies violation${if (totalAnomalies > 1) "s" else ""} during this timeframe",
                            style = MaterialTheme.typography.labelSmall,
                            color = MC.StatusOffline
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss Banner",
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val violationDetails = buildList {
                if (speedingViolationsCount > 0) add("$speedingViolationsCount Speeding Alarm${if (speedingViolationsCount > 1) "s" else ""}")
                if (geofenceBreaksCount > 0) add("$geofenceBreaksCount Geofence Breach${if (geofenceBreaksCount > 1) "es" else ""}")
            }.joinToString(" • ")

            Text(
                text = violationDetails,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onViewSafetyTab,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, MC.StatusOffline),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MC.StatusOffline,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Inspect Safety Logs",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Clean Canvas-drawn trend bar chart showing daily activity profile (distance per day).
 */
@Composable
fun DailyTrendBarChart(
    dailyBreakdown: List<DailySummary>,
    isMetric: Boolean,
    modifier: Modifier = Modifier
) {
    if (dailyBreakdown.isEmpty()) return

    val maxDistMeters = remember(dailyBreakdown) {
        dailyBreakdown.maxOfOrNull { it.totalDistanceMeters }?.takeIf { it > 0 } ?: 1000.0
    }
    val totalDistKm = remember(dailyBreakdown) {
        dailyBreakdown.sumOf { it.totalDistanceKm }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MC.Surface1),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MC.Surface3),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        tint = MC.AccentPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Activity Trend (Distance / Day)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MC.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Total: ${UnitFormatter.distance(totalDistKm, isMetric)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MC.AccentPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Canvas Bar Chart
            val barCount = dailyBreakdown.size
            val accentColor = MC.AccentPrimary
            val maxColor = MC.StatusOnline
            val surface3Color = MC.Surface3

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val bottomBaseline = height - 16f

                    // Draw baseline
                    drawLine(
                        color = surface3Color,
                        start = Offset(0f, bottomBaseline),
                        end = Offset(width, bottomBaseline),
                        strokeWidth = 1.5f
                    )

                    val slotWidth = width / barCount
                    val barWidth = (slotWidth * 0.55f).coerceIn(6f, 32f)

                    dailyBreakdown.forEachIndexed { index, day ->
                        val centerX = (index * slotWidth) + (slotWidth / 2f)
                        val ratio = (day.totalDistanceMeters / maxDistMeters).toFloat().coerceIn(0.04f, 1f)
                        val barHeight = (bottomBaseline - 8f) * ratio
                        val topY = bottomBaseline - barHeight
                        val leftX = centerX - (barWidth / 2f)

                        val isPeak = day.totalDistanceMeters >= maxDistMeters && maxDistMeters > 0
                        val barColor = if (isPeak) maxColor else accentColor.copy(alpha = 0.85f)

                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(leftX, topY),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // X-Axis Day Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dailyBreakdown.forEach { day ->
                    val label = day.date.takeLast(6).replace(",", "").trim()
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MC.TextTertiary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Collapsible table presenting the day-by-day reconciled breakdown for Weekly / Monthly reports.
 */
@Composable
fun DailyBreakdownTable(
    dailyBreakdown: List<DailySummary>,
    isMetric: Boolean,
    modifier: Modifier = Modifier
) {
    if (dailyBreakdown.isEmpty()) return

    var isExpanded by remember { mutableStateOf(true) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MC.Surface1),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MC.Surface3),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MC.AccentPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "Daily Breakdown (${dailyBreakdown.size} Days)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MC.TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Reconciled day-by-day telemetry sums",
                            style = MaterialTheme.typography.labelSmall,
                            color = MC.TextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MC.TextSecondary
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    // Table Column Headers
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MC.Surface2, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DATE / DAY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MC.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1.3f)
                        )
                        Text(
                            text = "DISTANCE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MC.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.1f)
                        )
                        Text(
                            text = "AVG SPEED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MC.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "IDLE / RUNTIME",
                            style = MaterialTheme.typography.labelSmall,
                            color = MC.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Daily Rows
                    dailyBreakdown.forEachIndexed { index, day ->
                        val idleSeconds = day.idleDurationMs / 1000
                        val idleH = idleSeconds / 3600
                        val idleM = (idleSeconds % 3600) / 60
                        val idleText = if (idleH > 0) "${idleH}h ${idleM}m" else "${idleM}m"

                        val movingSeconds = day.movingDurationMs / 1000
                        val movingH = movingSeconds / 3600
                        val movingM = (movingSeconds % 3600) / 60
                        val movingText = if (movingH > 0) "${movingH}h ${movingM}m" else "${movingM}m"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = day.date,
                                style = MaterialTheme.typography.bodySmall,
                                color = MC.TextPrimary,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1.3f)
                            )
                            Text(
                                text = UnitFormatter.distance(day.totalDistanceKm, isMetric),
                                style = MaterialTheme.typography.bodySmall,
                                color = MC.AccentPrimary,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1.1f)
                            )
                            Text(
                                text = UnitFormatter.speed(day.calculatedAvgSpeedKmh, isMetric),
                                style = MaterialTheme.typography.bodySmall,
                                color = MC.TextSecondary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$movingText / $idleText",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (idleH > 1) MC.StatusIdle else MC.TextTertiary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1.2f)
                            )
                        }

                        if (index < dailyBreakdown.size - 1) {
                            HorizontalDivider(color = MC.Surface3.copy(alpha = 0.5f))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Summary Reconciled Totals Row
                    val totalDistanceKm = dailyBreakdown.sumOf { it.totalDistanceKm }
                    val totalMovingMs = dailyBreakdown.sumOf { it.movingDurationMs }
                    val totalIdleMs = dailyBreakdown.sumOf { it.idleDurationMs }
                    val weightedAvgSpeed = if (totalMovingMs > 0) {
                        (totalDistanceKm / (totalMovingMs / 3600000.0))
                    } else 0.0

                    val totalIdleSeconds = totalIdleMs / 1000
                    val totalIdleH = totalIdleSeconds / 3600
                    val totalIdleM = (totalIdleSeconds % 3600) / 60
                    val totalIdleFormatted = if (totalIdleH > 0) "${totalIdleH}h ${totalIdleM}m" else "${totalIdleM}m"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MC.Surface3.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOTAL SUM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MC.TextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1.3f)
                        )
                        Text(
                            text = UnitFormatter.distance(totalDistanceKm, isMetric),
                            style = MaterialTheme.typography.labelSmall,
                            color = MC.StatusOnline,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.1f)
                        )
                        Text(
                            text = UnitFormatter.speed(weightedAvgSpeed, isMetric),
                            style = MaterialTheme.typography.labelSmall,
                            color = MC.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Idle: $totalIdleFormatted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MC.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Skeleton placeholder loader displayed during report fetching to eliminate layout shifts and reduce perceived latency.
 */
@Composable
fun ReportSkeletonLoader(
    modifier: Modifier = Modifier,
    statusMessage: String? = null
) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton_shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 850, easing = androidx.compose.animation.core.EaseInOutQuad),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (statusMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MC.Surface1),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MC.Surface3),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MC.AccentPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MC.TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Top Summary Bar Skeleton
        Card(
            colors = CardDefaults.cardColors(containerColor = MC.Surface1),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MC.Surface3),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 110.dp, height = 18.dp)
                        .background(MC.Surface3.copy(alpha = alpha), RoundedCornerShape(4.dp))
                )
                Box(
                    modifier = Modifier
                        .size(width = 80.dp, height = 18.dp)
                        .background(MC.Surface3.copy(alpha = alpha), RoundedCornerShape(4.dp))
                )
            }
        }

        // 6-Metric Box Skeleton Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in 0 until 3) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MC.Surface1),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MC.Surface3),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 50.dp, height = 10.dp)
                                .background(MC.Surface3.copy(alpha = alpha), RoundedCornerShape(3.dp))
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 65.dp, height = 16.dp)
                                .background(MC.Surface3.copy(alpha = alpha), RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in 0 until 3) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MC.Surface1),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MC.Surface3),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 50.dp, height = 10.dp)
                                .background(MC.Surface3.copy(alpha = alpha), RoundedCornerShape(3.dp))
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 65.dp, height = 16.dp)
                                .background(MC.Surface3.copy(alpha = alpha), RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }

        // Chart Placeholder Skeleton
        Card(
            colors = CardDefaults.cardColors(containerColor = MC.Surface1),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MC.Surface3),
            modifier = Modifier.fillMaxWidth().height(110.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 140.dp, height = 14.dp)
                        .background(MC.Surface3.copy(alpha = alpha), RoundedCornerShape(4.dp))
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    listOf(40, 65, 30, 80, 55, 70, 45).forEach { h ->
                        Box(
                            modifier = Modifier
                                .size(width = 18.dp, height = h.dp)
                                .background(MC.Surface3.copy(alpha = alpha), RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}
