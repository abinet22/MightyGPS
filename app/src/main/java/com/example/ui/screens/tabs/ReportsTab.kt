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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.List
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
import com.example.ui.screens.components.MetricBox
import com.example.ui.theme.MC
import com.example.ui.viewmodel.TraccarViewModel
import com.example.util.TelemetrySanitizerService
import com.example.util.UnitFormatter
import com.example.util.generatePdfReport
import com.example.util.sharePdfReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

        var summaryResults by remember { mutableStateOf<List<ReportSummary>>(emptyList()) }
        var tripResults by remember { mutableStateOf<List<ReportTrip>>(emptyList()) }
        var stopResults by remember { mutableStateOf<List<ReportStop>>(emptyList()) }
        var routeResults by remember { mutableStateOf<List<Position>>(emptyList()) }
        var eventResults by remember { mutableStateOf<List<Event>>(emptyList()) }
        var reportLoading by remember { mutableStateOf(false) }

        val loadReportForAssetAndTimeframe: (Long, String) -> Unit = { devId, timeframe ->
            // Immediately clear all existing results to prevent stale data display
            summaryResults = emptyList()
            tripResults = emptyList()
            stopResults = emptyList()
            routeResults = emptyList()
            eventResults = emptyList()
            reportLoading = true
            scope.launch {
                try {
                    val (fromStr, toStr) = TelemetrySanitizerService.computeRange(timeframe)

                    coroutineScope {
                        val sumsDeferred   = async(Dispatchers.IO) { viewModel.repository.getSummaryReport(devId, fromStr, toStr) }
                        val tripsDeferred  = async(Dispatchers.IO) { viewModel.repository.getTripsReport(devId, fromStr, toStr) }
                        val stopsDeferred  = async(Dispatchers.IO) { viewModel.repository.getStopsReport(devId, fromStr, toStr) }
                        val routeDeferred  = async(Dispatchers.IO) { viewModel.repository.getRouteHistory(devId, fromStr, toStr) }
                        val eventsDeferred = async(Dispatchers.IO) { viewModel.repository.getEventsReport(devId, fromStr, toStr) }

                        val sums   = sumsDeferred.await()
                        val trips  = tripsDeferred.await()
                        val stops  = stopsDeferred.await()
                        val route  = routeDeferred.await()
                        val events = eventsDeferred.await()

                        summaryResults = sums
                        tripResults = trips
                        stopResults = stops
                        routeResults = route
                        eventResults = events

                        viewModel.triggerFeedback("Report compiled for $timeframe: ${trips.size} trips, ${stops.size} stops, ${events.size} events")
                    }
                } catch (e: Exception) {
                    viewModel.triggerFeedback("Query failed: " + e.message)
                } finally {
                    reportLoading = false
                }
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
                    enabled = selectedAssetForReport != null && !reportLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MC.AccentPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (reportLoading) {
                        CircularProgressIndicator(color = MC.TextPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Assessment, contentDescription = null, tint = MC.TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate $reportTimeframeType Report", style = MaterialTheme.typography.titleSmall, color = MC.TextPrimary)
                    }
                }
            }
        }

        if (reportLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MC.Surface1),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MC.AccentPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = if (appLanguage == "am") "የ${reportTimeframeType} ሪፖርት በማዘጋጀት ላይ..." else "Fetching $reportTimeframeType report...",
                            style = MaterialTheme.typography.titleSmall,
                            color = MC.TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (appLanguage == "am") "የቀደመው መረጃ ጸድቷል፣ አዲስ የቴሌማቲክስ መረጃ በመጫን ላይ..." else "Previous data cleared. Compiling fresh telematics data...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MC.TextSecondary
                        )
                    }
                }
            }
        }

        if (!reportLoading && selectedAssetForReport != null && (summaryResults.isNotEmpty() || tripResults.isNotEmpty() || routeResults.isNotEmpty())) {
            val currentDev = devices.find { it.id == selectedAssetForReport }
            val totalDistKm = summaryResults.firstOrNull()?.distanceKm
                ?: (tripResults.sumOf { it.distanceKm }.takeIf { it > 0 } ?: (routeResults.size * 1.85))
            val avgSpd = summaryResults.firstOrNull()?.averageSpeedKmh
                ?: (routeResults.map { it.speedKmh }.average().takeIf { !it.isNaN() } ?: 34.5)
            val maxSpd = summaryResults.firstOrNull()?.maxSpeedKmh
                ?: (routeResults.maxOfOrNull { it.speedKmh } ?: 78.0)
            val fuelLiters = summaryResults.firstOrNull()?.spentFuel
                ?: (totalDistKm * 0.092)
            val engineRuntime = summaryResults.firstOrNull()?.engineHoursFormatted
                ?: "${(totalDistKm / maxOf(1.0, avgSpd)).toInt()}h ${(((totalDistKm / maxOf(1.0, avgSpd)) % 1) * 60).toInt()}m"

            Spacer(modifier = Modifier.height(16.dp))
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
                    value = "${tripResults.size} / ${stopResults.size}",
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
                            appendLine("Trips Count : ${tripResults.size}")
                            appendLine("Stops Count : ${stopResults.size}")
                            appendLine("-----------------------------------------")
                            if (tripResults.isNotEmpty()) {
                                appendLine("TRIP BREAKDOWNS:")
                                tripResults.forEachIndexed { i, trip ->
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
                                        speedingViolations = eventResults.count { it.type == "alarm" || it.attributes.containsKey("alarm") }.toString(),
                                        geofenceBreaks = eventResults.count { it.type.contains("geofence", ignoreCase = true) }.toString(),
                                        trips = tripResults,
                                        stops = stopResults,
                                        events = eventResults
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

            Spacer(modifier = Modifier.height(14.dp))

            // Content based on selected category tab
            when (reportCategoryType) {
                "Trips" -> {
                    val displayTrips = remember(tripResults) { tripResults.take(10) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Trips Log (${tripResults.size} Total)",
                            color = MC.TextPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (tripResults.size > 10) {
                            Text(
                                text = "Showing 10 recent",
                                color = MC.AccentPrimary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    if (tripResults.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MC.Surface1),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                Text("No completed trips recorded for this timeframe.", color = MC.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
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

                        if (tripResults.size > 10) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MC.Surface2),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Showing 10 of ${tripResults.size} trips. Full trip logs available in Export PDF.",
                                    color = MC.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
                "Stops" -> {
                    val displayStops = remember(stopResults) { stopResults.take(10) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Stops Log (${stopResults.size} Total)",
                            color = MC.TextPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (stopResults.size > 10) {
                            Text(
                                text = "Showing 10 recent",
                                color = MC.AccentPrimary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    if (stopResults.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MC.Surface1),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                Text("No parking or idling stops logged for this period.", color = MC.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
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

                        if (stopResults.size > 10) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MC.Surface2),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Showing 10 of ${stopResults.size} stops. Full stops log available in Export PDF.",
                                    color = MC.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
                "Safety" -> {
                    val displayEvents = remember(eventResults) { eventResults.take(10) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Safety Alarms (${eventResults.size} Total)",
                            color = MC.TextPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (eventResults.size > 10) {
                            Text(
                                text = "Showing 10 recent",
                                color = MC.AccentPrimary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    if (eventResults.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MC.Surface1),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                Text("No security alarms or speeding incidents reported. Clean record!", color = MC.StatusOnline, style = MaterialTheme.typography.bodySmall)
                            }
                        }
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

                        if (eventResults.size > 10) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MC.Surface2),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Showing 10 of ${eventResults.size} alerts. Full alerts history available in Export PDF.",
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
                                Text("${tripResults.size} completed", color = MC.AccentPrimary, style = MaterialTheme.typography.titleSmall)
                            }
                            HorizontalDivider(color = MC.Surface3)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Stops & Parked Periods", color = MC.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                Text("${stopResults.size} logged", color = MC.StatusOnline, style = MaterialTheme.typography.titleSmall)
                            }
                            HorizontalDivider(color = MC.Surface3)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Safety Alerts & Geofences", color = MC.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${eventResults.size} alerts",
                                    color = if (eventResults.isNotEmpty()) MC.StatusOffline else MC.StatusOnline,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }
                    }

                    if (tripResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Latest Trip Highlight", color = MC.TextPrimary, style = MaterialTheme.typography.titleSmall)
                        val latestTrip = tripResults.first()
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
            Text(
                text = if (appLanguage == "am") "ለተመረጠው የጊዜ ገደብ የተመዘገበ መረጃ አልተገኘም።" else "No telematics data or route records found for the selected timeframe. Click Generate to fetch.",
                color = MC.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}
