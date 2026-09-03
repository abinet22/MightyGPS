package com.example.ui.screens.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.theme.MC
import com.example.ui.viewmodel.TraccarViewModel
import com.example.util.GeofenceUtils
import com.example.util.ReportStateHolder
import com.example.util.UnitFormatter
import com.example.util.generatePdfReport
import com.example.util.rememberReportState
import com.example.util.sharePdfReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DeviceReportPage(
    viewModel: TraccarViewModel,
    device: Device,
    position: Position?,
    appLanguage: String,
    onBack: () -> Unit,
    onViewOnMap: () -> Unit,
    onViewPlayback: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isMetric = viewModel.sessionManager.unitSystem == "metric"

    var reportTimeframe by remember { mutableStateOf("Today") }
    var activeSubTab by remember { mutableStateOf("Overview") }
    val reportState = rememberReportState(viewModel, coroutineScope)
    var anomalyDismissed by remember(device.id, reportTimeframe) { mutableStateOf(false) }

    // Fetch report data asynchronously using ReportStateHolder
    LaunchedEffect(device.id, reportTimeframe) {
        reportState.load(device.id, reportTimeframe)
    }

    val totalDistance = if (reportState.isLoading && !reportState.isCacheHit) "--" else reportState.formattedTotalDistance(isMetric)
    val avgSpeed = if (reportState.isLoading && !reportState.isCacheHit) "--" else reportState.formattedAverageSpeed(isMetric)
    val maxSpeed = if (reportState.isLoading && !reportState.isCacheHit) "--" else reportState.formattedMaxSpeed(isMetric)
    val spentFuelLiters = if (reportState.isLoading && !reportState.isCacheHit) 0.0 else reportState.spentFuelLiters
    val engineRuntime = if (reportState.isLoading && !reportState.isCacheHit) "--" else reportState.engineRuntimeFormatted
    val speedingViolations = if (reportState.isLoading && !reportState.isCacheHit) "--" else reportState.speedingViolationsCount.toString()
    val geofenceBreaks = if (reportState.isLoading && !reportState.isCacheHit) "--" else reportState.geofenceBreaksCount.toString()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // DEVICE PROFILE SUMMARY CARD WITH EMBEDDED BACK NAVIGATION
        Card(
            colors = CardDefaults.cardColors(containerColor = MC.Surface1),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(MC.Surface2, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (device.category?.lowercase() == "truck") Icons.Default.Place else Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = if (device.status == "online") MC.StatusOnline else MC.StatusOffline
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(device.name, color = MC.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("IMEI / ID: ${device.uniqueId}", color = MC.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    StatusBadge(
                        text = if (device.status == "online") "Active Live" else "Offline Sleep",
                        color = if (device.status == "online") MC.StatusOnline else MC.StatusOffline,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(36.dp)
                        .background(MC.Surface2, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back to Fleet",
                        tint = MC.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // AUTOMATED ENGINE MAINTENANCE ALERT CARD
        val odoRaw = position?.attributes?.get("odometer") ?: position?.attributes?.get("totalDistance") ?: device.attributes["odometer"] ?: device.attributes["totalDistance"]
        val odoValue = when (odoRaw) {
            is Number -> odoRaw.toDouble()
            is String -> odoRaw.toDoubleOrNull()
            else -> null
        }
        val (isDue, isOverdue, odoKm) = getMaintenanceStatus(device.id, odoValue)

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isOverdue) MC.StatusOffline.copy(alpha = 0.15f) else if (isDue) MC.StatusIdle.copy(alpha = 0.15f) else MC.StatusOnline.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = "Maintenance reminder",
                    tint = if (isOverdue) MC.StatusOffline else if (isDue) MC.StatusIdle else MC.StatusOnline,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isOverdue) "Engine Maintenance Overdue" else if (isDue) "Engine Maintenance Required Soon" else "Engine System Optimal",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isOverdue) MC.StatusOffline else if (isDue) MC.StatusIdle else MC.StatusOnline
                    )
                    Text(
                        text = "Current Odometer: ${String.format("%.1f", odoKm)} km (Limit: 5,000 km interval)",
                        color = MC.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // NAVIGATION QUICK PORTALS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onViewOnMap,
                colors = ButtonDefaults.buttonColors(containerColor = MC.AccentPrimary),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MC.TextPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (appLanguage == "am") "ካርታ ላይ አሳይ" else if (appLanguage == "om") "Kaartaa Irra Agarsiisi" else "Live Map",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MC.TextPrimary
                )
            }

            Button(
                onClick = onViewPlayback,
                colors = ButtonDefaults.buttonColors(containerColor = MC.Surface2),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MC.TextPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (appLanguage == "am") "ታሪክ አጫውት" else if (appLanguage == "om") "Seenaa Taphadhu" else "Playback",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MC.TextPrimary
                )
            }
        }

        // TIMEFRAME SELECTOR CHIP SEGMENTS (Today, Weekly, Monthly)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MC.Surface1, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val options = listOf("Today", "Weekly", "Monthly")
            options.forEach { opt ->
                val isSelected = reportTimeframe == opt
                val amLabel = when(opt) {
                    "Weekly" -> "ሳምንታዊ"
                    "Monthly" -> "ወርሃዊ"
                    else -> "ዛሬ"
                }
                val omLabel = when(opt) {
                    "Weekly" -> "Torbee"
                    "Monthly" -> "Ji'a"
                    else -> "Har'a"
                }
                val displayLabel = if (appLanguage == "am") amLabel else if (appLanguage == "om") omLabel else opt

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) MC.AccentPrimary else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            if (reportTimeframe != opt) {
                                reportTimeframe = opt
                            }
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayLabel,
                        color = if (isSelected) MC.TextPrimary else MC.TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // SUB-TABS (Overview, Trips, Stops, Safety)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "Overview" to "Overview",
                "Trips" to "Trips (${reportState.trips.size})",
                "Stops" to "Stops (${reportState.stops.size})",
                "Safety" to "Safety & Events"
            ).forEach { (tabKey, tabLabel) ->
                val active = activeSubTab == tabKey
                FilterChip(
                    selected = active,
                    onClick = { activeSubTab = tabKey },
                    label = { Text(tabLabel, style = MaterialTheme.typography.labelSmall, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MC.AccentPrimary,
                        selectedLabelColor = MC.TextPrimary,
                        containerColor = MC.Surface2,
                        labelColor = MC.TextSecondary
                    ),
                    border = null,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        // SCROLLABLE METRICS & EVENT RECORDS
        if (reportState.isLoading && !reportState.isCacheHit) {
            ReportSkeletonLoader(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Anomaly Banner at top of report
                val speedingCountNum = speedingViolations.toIntOrNull() ?: 0
                val geofenceCountNum = geofenceBreaks.toIntOrNull() ?: 0
                if (!anomalyDismissed && (speedingCountNum > 0 || geofenceCountNum > 0)) {
                    item {
                        ReportAnomalyBanner(
                            speedingViolationsCount = speedingCountNum,
                            geofenceBreaksCount = geofenceCountNum,
                            onViewSafetyTab = { activeSubTab = "Safety" },
                            onDismiss = { anomalyDismissed = true }
                        )
                    }
                }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(
                        title = if (appLanguage == "am") "ጠቅላላ ርቀት" else "Total Distance",
                        value = totalDistance,
                        color = MC.AccentPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = if (appLanguage == "am") "የተቃጠለ ነዳጅ" else "Fuel Spent",
                        value = String.format(Locale.US, "%.1f L", spentFuelLiters),
                        color = MC.StatusOnline,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = if (appLanguage == "am") "የሞተር ሰዓት" else "Engine Runtime",
                        value = engineRuntime,
                        color = MC.AccentPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(
                        title = if (appLanguage == "am") "አማካይ ፍጥነት" else "Avg Speed",
                        value = avgSpeed,
                        color = MC.StatusIdle,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = if (appLanguage == "am") "ከፍተኛ ፍጥነት" else "Max Speed",
                        value = maxSpeed,
                        color = MC.StatusIdle,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = if (appLanguage == "am") "ፍጥነት ማለፍ" else "Violations",
                        value = speedingViolations,
                        color = if ((speedingViolations.toIntOrNull() ?: 0) > 0) MC.StatusOffline else MC.StatusOnline,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val activeLabel = reportTimeframe
                            val reportText = buildString {
                                appendLine("=========================================")
                                appendLine("       FLEET TELEMATICS REPORT           ")
                                appendLine("=========================================")
                                appendLine("Device Name : ${device.name}")
                                appendLine("IMEI        : ${device.uniqueId}")
                                appendLine("Report Type : $activeLabel")
                                appendLine("Generated   : ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}")
                                appendLine("-----------------------------------------")
                                appendLine("TELEMETRICS SUMMARY:")
                                appendLine(" • Distance: $totalDistance")
                                appendLine(" • Fuel Spent: ${String.format(Locale.US, "%.1f L", spentFuelLiters)}")
                                appendLine(" • Engine Hours: $engineRuntime")
                                appendLine(" • Avg Velocity: $avgSpeed")
                                appendLine(" • Peak Speed: $maxSpeed")
                                appendLine(" • Speeding Violations: $speedingViolations")
                                appendLine(" • Geofence Breaks: $geofenceBreaks")
                                appendLine(" • Completed Trips: ${reportState.trips.size}")
                                appendLine(" • Logged Stops: ${reportState.stops.size}")
                                appendLine("-----------------------------------------")
                                if (reportState.trips.isNotEmpty()) {
                                    appendLine("TRIP BREAKDOWNS:")
                                    reportState.trips.forEachIndexed { i, trip ->
                                        appendLine(" Trip #${i + 1} (${trip.timeRangeFormatted}): ${trip.startAddress ?: "Origin"} -> ${trip.endAddress ?: "Destination"}")
                                        appendLine("   Duration: ${trip.durationFormatted} | Distance: ${String.format(Locale.US, "%.1f km", trip.distanceKm)} | Driver: ${trip.driverName ?: "N/A"}")
                                    }
                                }
                                appendLine("=========================================")
                            }

                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TITLE, "Asset Telematics - ${device.name}")
                                putExtra(Intent.EXTRA_SUBJECT, "Asset Telematics - ${device.name}")
                                putExtra(Intent.EXTRA_TEXT, reportText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export Telematic Report"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MC.Surface2),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share text report",
                            tint = MC.TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (appLanguage == "am") "ጽሑፍ አጋራ" else if (appLanguage == "om") "Barreeffama Qoodi" else "Share Text",
                            color = MC.TextPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val pdfFile = withContext(Dispatchers.Default) {
                                    generatePdfReport(
                                        context = context,
                                        device = device,
                                        reportTimeframe = reportTimeframe,
                                        totalDistance = totalDistance,
                                        avgSpeed = avgSpeed,
                                        maxSpeed = maxSpeed,
                                        spentFuel = String.format(Locale.US, "%.1f L", spentFuelLiters),
                                        engineHours = engineRuntime,
                                        speedingViolations = speedingViolations,
                                        geofenceBreaks = geofenceBreaks,
                                        trips = reportState.trips,
                                        stops = reportState.stops,
                                        events = reportState.events
                                    )
                                }
                                if (pdfFile != null) {
                                    sharePdfReport(context, pdfFile, device.name)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MC.StatusOnline),
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = "Export PDF report",
                            tint = MC.TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (appLanguage == "am") "PDF አውርድ" else if (appLanguage == "om") "PDF Buusi" else "Export PDF",
                            color = MC.TextPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Daily Trend Bar Chart for Weekly/Monthly
            val periodRep = reportState.periodReport
            if (periodRep != null && periodRep.dailyBreakdown.isNotEmpty()) {
                item {
                    DailyTrendBarChart(
                        dailyBreakdown = periodRep.dailyBreakdown,
                        isMetric = isMetric
                    )
                }
            }

            when (activeSubTab) {
                "Trips" -> {
                    val displayTrips = reportState.trips.take(10)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Trips Log (${reportState.trips.size} Total)",
                                color = MC.TextPrimary,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (reportState.trips.size > 10) {
                                Text(
                                    text = "Showing 10 recent",
                                    color = MC.AccentPrimary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    if (reportState.trips.isEmpty()) {
                        item {
                            EmptyStateView(
                                icon = Icons.Default.DirectionsCar,
                                title = "No trips logged",
                                subtitle = "No recorded journeys found for $reportTimeframe.",
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else {
                        items(displayTrips) { trip ->
                            val idx = reportState.trips.indexOf(trip)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MC.Surface1),
                                shape = RoundedCornerShape(8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    // Row 1: Header + Metrics
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Trip #${if (idx >= 0) idx + 1 else 1} • ${trip.durationFormatted}",
                                            color = MC.AccentPrimary,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${UnitFormatter.distance(trip.distanceKm, isMetric)} (${UnitFormatter.speed(trip.averageSpeedKmh, isMetric)})",
                                            color = MC.StatusOnline,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Row 2: Origin Location & Time
                                    Text(
                                        text = "From: ${trip.startAddress ?: "Origin Terminal"} (${trip.startTimeFormatted})",
                                        color = MC.TextPrimary,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )

                                    // Row 3: Destination Location & Time
                                    Text(
                                        text = "To: ${trip.endAddress ?: "Destination Facility"} (${trip.endTimeFormatted})",
                                        color = MC.TextPrimary,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )

                                    trip.driverName?.let {
                                        Text(
                                            text = "Driver: $it",
                                            color = MC.StatusIdle,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }

                        if (reportState.trips.size > 10) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MC.Surface2),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Showing 10 of ${reportState.trips.size} trips. Full trip logs available in Export PDF.",
                                        color = MC.TextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                "Stops" -> {
                    val displayStops = reportState.stops.take(10)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Stops Log (${reportState.stops.size} Total)",
                                color = MC.TextPrimary,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (reportState.stops.size > 10) {
                                Text(
                                    text = "Showing 10 recent",
                                    color = MC.AccentPrimary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    if (reportState.stops.isEmpty()) {
                        item {
                            EmptyStateView(
                                icon = Icons.Default.Place,
                                title = "No stops recorded",
                                subtitle = "No parking or idling stops logged for $reportTimeframe.",
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else {
                        items(displayStops) { stop ->
                            val idx = reportState.stops.indexOf(stop)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MC.Surface1),
                                shape = RoundedCornerShape(8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (stop.wasIdling) "Engine Idling" else "Parked",
                                                color = if (stop.wasIdling) MC.StatusIdle else MC.StatusOnline,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Stop #${if (idx >= 0) idx + 1 else 1}",
                                                color = MC.TextSecondary,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        Text(
                                            text = stop.durationFormatted,
                                            color = MC.StatusIdle,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Text(
                                        text = stop.address ?: "Facility Staging Area",
                                        color = MC.TextPrimary,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stop.timeRangeFormatted,
                                        color = MC.TextTertiary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }

                        if (reportState.stops.size > 10) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MC.Surface2),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Showing 10 of ${reportState.stops.size} stops. Full stops log available in Export PDF.",
                                        color = MC.TextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                "Safety" -> {
                    val displayEvents = reportState.events.take(10)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Safety Alarms (${reportState.events.size} Total)",
                                color = MC.TextPrimary,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (reportState.events.size > 10) {
                                Text(
                                    text = "Showing 10 recent",
                                    color = MC.AccentPrimary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    if (reportState.events.isEmpty()) {
                        item {
                            EmptyStateView(
                                icon = Icons.Default.CheckCircle,
                                title = "Clean Safety Record",
                                subtitle = "No safety violations or geofence breaches for $reportTimeframe.",
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else {
                        items(displayEvents) { evt ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MC.Surface1),
                                shape = RoundedCornerShape(8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MC.StatusOffline, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(evt.type, color = MC.TextPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        Text(evt.eventTimeFormatted, color = MC.TextTertiary, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        if (reportState.events.size > 10) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MC.Surface2),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Showing 10 of ${reportState.events.size} alerts. Full alerts history available in Export PDF.",
                                        color = MC.TextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    item {
                        Text(
                            text = if (appLanguage == "am") "አጠቃላይ የቴሌማቲክስ ማጠቃለያ" else if (appLanguage == "om") "Cuunfaa Teessuma Telematics" else "Executive Telematics Summary",
                            color = MC.TextPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MC.Surface1),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Fleet Asset", color = MC.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    Text(device.name, color = MC.TextPrimary, style = MaterialTheme.typography.titleSmall)
                                }
                                HorizontalDivider(color = MC.Surface3)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Logged Trips", color = MC.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    Text("${reportState.trips.size} completed", color = MC.AccentPrimary, style = MaterialTheme.typography.titleSmall)
                                }
                                HorizontalDivider(color = MC.Surface3)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Stops & Parked Periods", color = MC.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    Text("${reportState.stops.size} logged", color = MC.StatusOnline, style = MaterialTheme.typography.titleSmall)
                                }
                                HorizontalDivider(color = MC.Surface3)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Safety Alerts & Geofences", color = MC.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${reportState.events.size} alerts",
                                        color = if (reportState.events.isNotEmpty()) MC.StatusOffline else MC.StatusOnline,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }
                        }
                    }

                    val periodRepOverview = reportState.periodReport
                    if (periodRepOverview != null && periodRepOverview.dailyBreakdown.isNotEmpty()) {
                        item {
                            DailyBreakdownTable(
                                dailyBreakdown = periodRepOverview.dailyBreakdown,
                                isMetric = isMetric
                            )
                        }
                    }

                    if (reportState.trips.isNotEmpty()) {
                        item {
                            Text(
                                text = if (appLanguage == "am") "የቅርብ ጊዜ ጉዞ ማጠቃለያ" else "Recent Trip Highlight",
                                color = MC.TextPrimary,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        val latestTrip = reportState.trips.first()
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MC.Surface2),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${latestTrip.startAddress ?: "Origin"} -> ${latestTrip.endAddress ?: "Destination"}", color = MC.TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                        Text(latestTrip.durationFormatted, color = MC.StatusOnline, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                    Text("Distance: ${UnitFormatter.distance(latestTrip.distanceKm, isMetric)} • Avg Speed: ${UnitFormatter.speed(latestTrip.averageSpeedKmh, isMetric)}", color = MC.TextSecondary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MC.Surface2),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MC.AccentPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (appLanguage == "am") "ዝርዝር የጉዞ፣ የማቆሚያ እና የደህንነት መዛግብት ከላይ ባሉት ንዑስ ክፍሎች ይገኛሉ። ሙሉውን ሪፖርት በፒዲኤፍ ማውረድ ይችላሉ።"
                                    else "Detailed trips, idle stops, and security events are neatly organized under the tabs above. Tap 'Export PDF' to export the complete itemized dossier.",
                                    color = MC.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}
