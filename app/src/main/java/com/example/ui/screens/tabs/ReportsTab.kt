package com.example.ui.screens.tabs

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.screens.components.DailyBreakdownTable
import com.example.ui.screens.components.DailyTrendBarChart
import com.example.ui.screens.components.EmptyStateView
import com.example.ui.screens.components.MetricBox
import com.example.ui.screens.components.ReportAnomalyBanner
import com.example.ui.screens.components.ReportSkeletonLoader
import com.example.ui.theme.MC
import com.example.ui.viewmodel.TraccarViewModel
import com.example.util.GeofenceUtils
import com.example.util.ReportDataLoader
import com.example.util.ReportReconciliationManager
import com.example.util.ReportStateHolder
import com.example.util.TelemetrySanitizerService
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
fun ReportsTab(
    viewModel: TraccarViewModel,
    devices: List<Device>,
    appLanguage: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = if (appLanguage == "am") "የተሽከርካሪ እና የፍሊት ታሪካዊ ሪፖርቶች" else "FLEET & ASSET TELEMETRICS REPORTS",
            style = MaterialTheme.typography.titleMedium,
            color = MC.TextPrimary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = if (appLanguage == "am") "ዛሬ፣ ሳምንታዊ እና ወርሃዊ የተሟሉ የጉዞ፣ የነዳጅ እና የፍጥነት ሪፖርቶችን ያመንጩ።" else "Generate comprehensive Today, Weekly, and Monthly telematics reports with trips, stops, and fuel.",
            style = MaterialTheme.typography.bodySmall,
            color = MC.TextSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        var selectedAssetForReport by remember { mutableStateOf<Long?>(devices.firstOrNull()?.id) }
        var reportTimeframeType by remember { mutableStateOf("Today") }
        var reportCategoryType by remember { mutableStateOf("Summary") }

        val reportState = rememberReportState(viewModel, scope)

        val loadReportForAssetAndTimeframe: (Long, String) -> Unit = { devId, timeframe ->
            reportState.load(devId, timeframe) {
                viewModel.triggerFeedback("Report compiled for $timeframe: ${reportState.trips.size} trips, ${reportState.stops.size} stops, ${reportState.events.size} events")
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MC.Surface1),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 1. Asset Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (appLanguage == "am") "ተሽከርካሪ ይምረጡ" else "Select Fleet Asset",
                        style = MaterialTheme.typography.titleSmall,
                        color = MC.TextPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        devices.forEach { dev ->
                            val active = selectedAssetForReport == dev.id
                            FilterChip(
                                selected = active,
                                onClick = {
                                    if (selectedAssetForReport != dev.id) {
                                        selectedAssetForReport = dev.id
                                        loadReportForAssetAndTimeframe(dev.id, reportTimeframeType)
                                    }
                                },
                                label = { Text(dev.name, style = MaterialTheme.typography.labelSmall, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) },
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
                }

                // 2. Primary Timeframe Presets (Today, Weekly, Monthly)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (appLanguage == "am") "የሪፖርት የጊዜ ገደብ" else "Report Time Window",
                        style = MaterialTheme.typography.titleSmall,
                        color = MC.TextPrimary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MC.Surface0, RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val primaryOptions = listOf(
                            "Today" to (if (appLanguage == "am") "ዛሬ" else "Today"),
                            "Weekly" to (if (appLanguage == "am") "ሳምንታዊ" else "Weekly (7d)"),
                            "Monthly" to (if (appLanguage == "am") "ወርሃዊ" else "Monthly (30d)")
                        )
                        primaryOptions.forEach { (key, label) ->
                            val active = reportTimeframeType == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (active) MC.StatusOnline else MC.Surface0, RoundedCornerShape(6.dp))
                                    .clickable {
                                        if (reportTimeframeType != key) {
                                            reportTimeframeType = key
                                            // Immediately clear old results to avoid stale data while new report loads
                                            selectedAssetForReport?.let { devId ->
                                                loadReportForAssetAndTimeframe(devId, key)
                                            }
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = if (active) MC.TextPrimary else MC.TextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Quick hour chips
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Past 3h", "Past 12h", "Past 24h", "Past 72h").forEach { hLabel ->
                            val active = reportTimeframeType == hLabel
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (active) MC.AccentPrimary else MC.Surface2, RoundedCornerShape(6.dp))
                                    .clickable {
                                        if (reportTimeframeType != hLabel) {
                                            reportTimeframeType = hLabel
                                            // Immediately clear old results to avoid stale data while new report loads
                                            selectedAssetForReport?.let { devId ->
                                                loadReportForAssetAndTimeframe(devId, hLabel)
                                            }
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(hLabel, color = if (active) MC.TextPrimary else MC.TextSecondary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // 3. Report Category Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (appLanguage == "am") "የሪፖርት አይነት" else "Report Category",
                        style = MaterialTheme.typography.titleSmall,
                        color = MC.TextPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Summary" to "Overview Summary",
                            "Trips" to "Trips Log",
                            "Stops" to "Stops & Idling",
                            "Safety" to "Safety & Events"
                        ).forEach { (catKey, catLabel) ->
                            val active = reportCategoryType == catKey
                            FilterChip(
                                selected = active,
                                onClick = { reportCategoryType = catKey },
                                label = { Text(catLabel, style = MaterialTheme.typography.labelSmall, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) },
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
                }

                Button(
                    onClick = {
                        val devId = selectedAssetForReport
                        if (devId != null) {
                            loadReportForAssetAndTimeframe(devId, reportTimeframeType)
                        }
                    },
                    enabled = selectedAssetForReport != null && !reportState.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MC.AccentPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (reportState.isLoading && !reportState.isCacheHit) {
                        CircularProgressIndicator(color = MC.TextPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Assessment, contentDescription = null, tint = MC.TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate $reportTimeframeType Report", style = MaterialTheme.typography.titleSmall, color = MC.TextPrimary)
                    }
                }
            }
        }

        if (reportState.isLoading && !reportState.isCacheHit) {
            Spacer(modifier = Modifier.height(16.dp))
            ReportSkeletonLoader(modifier = Modifier.fillMaxWidth())
        }

        var anomalyDismissed by remember(selectedAssetForReport, reportTimeframeType) { mutableStateOf(false) }

        if ((!reportState.isLoading || reportState.isCacheHit) && selectedAssetForReport != null && (reportState.summaries.isNotEmpty() || reportState.trips.isNotEmpty() || reportState.route.isNotEmpty() || reportState.periodReport != null)) {
            val currentDev = devices.find { it.id == selectedAssetForReport }
            val speedingViolationsCount = reportState.speedingViolationsCount
            val geofenceBreaksCount = reportState.geofenceBreaksCount

            val totalDistKm = reportState.totalDistanceKm
            val avgSpd = reportState.averageSpeedKmh
            val maxSpd = reportState.maxSpeedKmh
            val fuelLiters = reportState.spentFuelLiters
            val engineRuntime = reportState.engineRuntimeFormatted

            Spacer(modifier = Modifier.height(16.dp))

            // ANOMALY PROMINENT WARNING BANNER
            if (!anomalyDismissed && (speedingViolationsCount > 0 || geofenceBreaksCount > 0)) {
                ReportAnomalyBanner(
                    speedingViolationsCount = speedingViolationsCount,
                    geofenceBreaksCount = geofenceBreaksCount,
                    onViewSafetyTab = { reportCategoryType = "Safety" },
                    onDismiss = { anomalyDismissed = true }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "COMPILED REPORT: ${currentDev?.name}",
                        color = MC.AccentPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Timeframe: $reportTimeframeType • Updated just now",
                        color = MC.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val isMetric = viewModel.sessionManager.unitSystem == "metric"

            // KPI Metric Cards
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricBox(
                    title = "Distance",
                    value = UnitFormatter.distance(totalDistKm, isMetric),
                    color = MC.AccentPrimary,
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    title = "Fuel Spent",
                    value = String.format(Locale.US, "%.1f L", fuelLiters),
                    color = MC.StatusOnline,
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    title = "Avg Velocity",
                    value = UnitFormatter.speed(avgSpd, isMetric),
                    color = MC.StatusIdle,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricBox(
                    title = "Peak Velocity",
                    value = UnitFormatter.speed(maxSpd, isMetric),
                    color = if (maxSpd > 80.0) MC.StatusOffline else MC.StatusOnline,
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    title = "Engine Runtime",
                    value = engineRuntime,
                    color = MC.AccentPrimary,
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    title = "Trips / Stops",
                    value = "${reportState.trips.size} / ${reportState.stops.size}",
                    color = MC.StatusOnline,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons (Share & Export PDF)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val formattedTotalDist = UnitFormatter.distance(totalDistKm, isMetric)
                val formattedAvgSpd = UnitFormatter.speed(avgSpd, isMetric)
                val formattedMaxSpd = UnitFormatter.speed(maxSpd, isMetric)

                Button(
                    onClick = {
                        val reportText = buildString {
                            appendLine("=========================================")
                            appendLine("       FLEET TELEMATICS REPORT           ")
                            appendLine("=========================================")
                            appendLine("Device Name : ${currentDev?.name}")
                            appendLine("IMEI        : ${currentDev?.uniqueId}")
                            appendLine("Timeframe   : $reportTimeframeType")
                            appendLine("Generated   : ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}")
                            appendLine("-----------------------------------------")
                            appendLine("Distance    : $formattedTotalDist")
                            appendLine("Avg Speed   : $formattedAvgSpd")
                            appendLine("Max Speed   : $formattedMaxSpd")
                            appendLine("Fuel Spent  : ${String.format(Locale.US, "%.1f L", fuelLiters)}")
                            appendLine("Engine Hours: $engineRuntime")
                            appendLine("Trips Count : ${reportState.trips.size}")
                            appendLine("Stops Count : ${reportState.stops.size}")
                            appendLine("-----------------------------------------")
                            if (reportState.trips.isNotEmpty()) {
                                appendLine("TRIP BREAKDOWNS:")
                                reportState.trips.forEachIndexed { i, trip ->
                                    appendLine(" Trip #${i + 1} (${trip.timeRangeFormatted}): ${trip.startAddress ?: "Origin"} -> ${trip.endAddress ?: "Destination"}")
                                    appendLine("   Duration: ${trip.durationFormatted} | Distance: ${UnitFormatter.distance(trip.distanceKm, isMetric)} | Driver: ${trip.driverName ?: "N/A"}")
                                }
                            }
                            appendLine("=========================================")
                        }
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TITLE, "Report - ${currentDev?.name}")
                            putExtra(Intent.EXTRA_TEXT, reportText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Export Report Text"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MC.Surface2),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = MC.TextPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Text", style = MaterialTheme.typography.labelSmall, color = MC.TextPrimary, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (currentDev != null) {
                            scope.launch {
                                val pdfFile = withContext(Dispatchers.Default) {
                                    generatePdfReport(
                                        context = context,
                                        device = currentDev,
                                        reportTimeframe = reportTimeframeType,
                                        totalDistance = formattedTotalDist,
                                        avgSpeed = formattedAvgSpd,
                                        maxSpeed = formattedMaxSpd,
                                        spentFuel = String.format(Locale.US, "%.1f L", fuelLiters),
                                        engineHours = engineRuntime,
                                        speedingViolations = reportState.events.count { it.type == "alarm" || it.attributes.containsKey("alarm") }.toString(),
                                        geofenceBreaks = reportState.events.count { it.type.contains("geofence", ignoreCase = true) }.toString(),
                                        trips = reportState.trips,
                                        stops = reportState.stops,
                                        events = reportState.events
                                    )
                                }
                                if (pdfFile != null) {
                                    sharePdfReport(context, pdfFile, currentDev.name)
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MC.StatusOnline),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.List, contentDescription = null, tint = MC.TextPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export PDF", style = MaterialTheme.typography.labelSmall, color = MC.TextPrimary, fontWeight = FontWeight.Bold)
                }
            }

            // DAILY ACTIVITY TREND BAR CHART (If weekly / monthly report with daily breakdown)
            if (reportState.periodReport != null && reportState.periodReport!!.dailyBreakdown.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                DailyTrendBarChart(
                    dailyBreakdown = reportState.periodReport!!.dailyBreakdown,
                    isMetric = isMetric
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Content based on selected category tab
            when (reportCategoryType) {
                "Trips" -> {
                    val displayTrips = remember(reportState.trips) { reportState.trips.take(10) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                    Spacer(modifier = Modifier.height(4.dp))

                    if (reportState.trips.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.DirectionsCar,
                            title = "No trips logged",
                            subtitle = "No completed trips recorded for this timeframe.",
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        displayTrips.forEachIndexed { idx, trip ->
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
                                            text = "Trip #${idx + 1} • ${trip.durationFormatted}",
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
                "Stops" -> {
                    val displayStops = remember(reportState.stops) { reportState.stops.take(10) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                    Spacer(modifier = Modifier.height(4.dp))

                    if (reportState.stops.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.Place,
                            title = "No stops recorded",
                            subtitle = "No parking or idling stops logged for this period.",
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        displayStops.forEachIndexed { idx, stop ->
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
                                                text = "Stop #${idx + 1}",
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
                "Safety" -> {
                    val displayEvents = remember(reportState.events) { reportState.events.take(10) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                    Spacer(modifier = Modifier.height(4.dp))

                    if (reportState.events.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.CheckCircle,
                            title = "Clean Safety Record",
                            subtitle = "No security alarms or speeding incidents reported. Clean record!",
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        displayEvents.forEach { evt ->
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
                                        Text("Event: ${evt.type}", color = MC.TextPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        Text(evt.eventTimeFormatted, color = MC.TextTertiary, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        if (reportState.events.size > 10) {
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
                else -> {
                    // Overview Summary Highlights
                    Text("Executive Telematics Summary", color = MC.TextPrimary, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(6.dp))

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
                                Text(currentDev?.name ?: "Vehicle", color = MC.TextPrimary, style = MaterialTheme.typography.titleSmall)
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

                    if (reportState.periodReport != null && reportState.periodReport!!.dailyBreakdown.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        DailyBreakdownTable(
                            dailyBreakdown = reportState.periodReport!!.dailyBreakdown,
                            isMetric = isMetric
                        )
                    }

                    if (reportState.trips.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Latest Trip Highlight", color = MC.TextPrimary, style = MaterialTheme.typography.titleSmall)
                        val latestTrip = reportState.trips.first()
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MC.Surface2),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
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
            }
        } else if (selectedAssetForReport != null) {
            EmptyStateView(
                icon = Icons.Default.Assessment,
                title = "No report generated",
                subtitle = if (appLanguage == "am") "ለተመረጠው የጊዜ ገደብ የተመዘገበ መረጃ አልተገኘም። ሪፖርቱን ለማምጣት 'ሪፖርት አውጣ' የሚለውን ይጫኑ።" else "No telematics data or route records found for the selected timeframe. Click 'Generate' to fetch.",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
