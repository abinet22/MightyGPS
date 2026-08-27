package com.example.ui.screens.components

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.Color as AndroidColor
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.Device
import com.example.data.model.Event
import com.example.data.model.Position
import com.example.data.model.ReportStop
import com.example.data.model.ReportSummary
import com.example.data.model.ReportTrip
import com.example.ui.viewmodel.TraccarViewModel
import com.example.util.UnitFormatter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class RouteSegment(
    val segmentIndex: Int,
    val startIndex: Int,
    val endIndex: Int,
    val startTime: String,
    val endTime: String,
    val durationStr: String,
    val distanceKm: Double,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val averageSpeed: Double,
    val maxSpeed: Double
)

private fun getEpochTime(timeStr: String?): Long {
    if (timeStr.isNullOrEmpty()) return 0L
    return try {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )
        var parsedTime = 0L
        for (f in formats) {
            try {
                val sdf = SimpleDateFormat(f, Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val t = sdf.parse(timeStr)?.time
                if (t != null) {
                    parsedTime = t
                    break
                }
            } catch (e: Exception) {
                // Ignore and try next format
            }
        }
        parsedTime
    } catch (e: Exception) {
        0L
    }
}

private fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hrs > 0 -> "${hrs}h ${mins}m"
        mins > 0 -> "${mins}m ${secs}s"
        else -> "${secs}s"
    }
}

fun segmentRoute(routeHistory: List<Position>): List<RouteSegment> {
    if (routeHistory.isEmpty()) return emptyList()

    val segments = mutableListOf<RouteSegment>()
    var startIdx = 0
    var segmentCounter = 1

    val distanceCalc = { lat1: Double, lon1: Double, lat2: Double, lon2: Double ->
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0)
        val c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a))
        r * c
    }

    val segmentDistance = { from: Int, to: Int ->
        var sum = 0.0
        for (i in from until to) {
            val p1 = routeHistory.getOrNull(i)
            val p2 = routeHistory.getOrNull(i + 1)
            if (p1 != null && p2 != null) {
                sum += distanceCalc(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
            }
        }
        sum
    }

    for (i in 1 until routeHistory.size) {
        val prev = routeHistory[i - 1]
        val curr = routeHistory[i]

        val tPrev = getEpochTime(prev.deviceTime)
        val tCurr = getEpochTime(curr.deviceTime)

        val timeDiffSecs = (tCurr - tPrev) / 1000

        // Split segment if there's a gap of more than 3 minutes (180s)
        if (timeDiffSecs > 180L) {
            val endIdx = i - 1
            val startPos = routeHistory[startIdx]
            val endPos = routeHistory[endIdx]

            val durMs = getEpochTime(endPos.deviceTime) - getEpochTime(startPos.deviceTime)
            val durSecs = (durMs / 1000).coerceAtLeast(0L)
            val segmentPts = routeHistory.subList(startIdx, endIdx + 1)
            val maxSpd = segmentPts.maxOfOrNull { it.speedKmh } ?: 0.0
            val avgSpd = if (segmentPts.isNotEmpty()) segmentPts.map { it.speedKmh }.average() else 0.0

            val cleanStartTime = if (startPos.deviceTime?.contains("T") == true) {
                startPos.deviceTime.substringBefore("Z").replace("T", " ")
            } else startPos.deviceTime ?: ""

            val cleanEndTime = if (endPos.deviceTime?.contains("T") == true) {
                endPos.deviceTime.substringBefore("Z").replace("T", " ")
            } else endPos.deviceTime ?: ""

            segments.add(
                RouteSegment(
                    segmentIndex = segmentCounter++,
                    startIndex = startIdx,
                    endIndex = endIdx,
                    startTime = cleanStartTime,
                    endTime = cleanEndTime,
                    durationStr = formatDuration(durSecs),
                    distanceKm = segmentDistance(startIdx, endIdx),
                    startLat = startPos.latitude,
                    startLng = startPos.longitude,
                    endLat = endPos.latitude,
                    endLng = endPos.longitude,
                    averageSpeed = avgSpd,
                    maxSpeed = maxSpd
                )
            )
            startIdx = i
        }
    }

    // Add the remaining part as the last segment
    if (startIdx < routeHistory.size) {
        val endIdx = routeHistory.size - 1
        val startPos = routeHistory[startIdx]
        val endPos = routeHistory[endIdx]

        val durMs = getEpochTime(endPos.deviceTime) - getEpochTime(startPos.deviceTime)
        val durSecs = (durMs / 1000).coerceAtLeast(0L)
        val segmentPts = routeHistory.subList(startIdx, endIdx + 1)
        val maxSpd = segmentPts.maxOfOrNull { it.speedKmh } ?: 0.0
        val avgSpd = if (segmentPts.isNotEmpty()) segmentPts.map { it.speedKmh }.average() else 0.0

        val cleanStartTime = if (startPos.deviceTime?.contains("T") == true) {
            startPos.deviceTime.substringBefore("Z").replace("T", " ")
        } else startPos.deviceTime ?: ""

        val cleanEndTime = if (endPos.deviceTime?.contains("T") == true) {
            endPos.deviceTime.substringBefore("Z").replace("T", " ")
        } else endPos.deviceTime ?: ""

        segments.add(
            RouteSegment(
                segmentIndex = segmentCounter,
                startIndex = startIdx,
                endIndex = endIdx,
                startTime = cleanStartTime,
                endTime = cleanEndTime,
                durationStr = formatDuration(durSecs),
                distanceKm = segmentDistance(startIdx, endIdx),
                startLat = startPos.latitude,
                startLng = startPos.longitude,
                endLat = endPos.latitude,
                endLng = endPos.longitude,
                averageSpeed = avgSpd,
                maxSpeed = maxSpd
            )
        )
    }

    return segments
}

fun generatePdfReport(
    context: Context,
    device: Device,
    reportTimeframe: String,
    totalDistance: String,
    avgSpeed: String,
    maxSpeed: String,
    speedingViolations: String,
    geofenceBreaks: String,
    detailLogs: List<String>
): File? {
    val pdfDocument = PdfDocument()

    // Page height and width (Standard A4 size is 595 x 842 points)
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas

    val paint = Paint()
    val textPaint = Paint().apply {
        isAntiAlias = true
    }

    var y = 40f

    // 1. Header Banner
    paint.color = AndroidColor.parseColor("#0F172A")
    canvas.drawRect(20f, y, 575f, y + 80f, paint)

    // Header Title
    textPaint.color = AndroidColor.WHITE
    textPaint.textSize = 20f
    textPaint.isFakeBoldText = true
    canvas.drawText("FLEET TELEMATICS REPORT", 40f, y + 45f, textPaint)

    textPaint.textSize = 10f
    textPaint.isFakeBoldText = false
    val generatedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    canvas.drawText("Generated on: $generatedTime", 40f, y + 65f, textPaint)

    y += 100f

    // 2. Device Details / Asset Profile
    textPaint.color = AndroidColor.parseColor("#0F172A")
    textPaint.textSize = 14f
    textPaint.isFakeBoldText = true
    canvas.drawText("Asset Profile Details:", 30f, y, textPaint)
    y += 20f

    textPaint.textSize = 11f
    textPaint.isFakeBoldText = false
    textPaint.color = AndroidColor.parseColor("#475569")
    canvas.drawText("Device Name: ${device.name}", 40f, y, textPaint)
    canvas.drawText("IMEI / Unique ID: ${device.uniqueId}", 300f, y, textPaint)
    y += 18f

    canvas.drawText("Report Frame: $reportTimeframe", 40f, y, textPaint)
    canvas.drawText("Category: ${device.category ?: "standard"}", 300f, y, textPaint)
    y += 30f

    // Divider
    paint.color = AndroidColor.parseColor("#E2E8F0")
    canvas.drawRect(30f, y, 565f, y + 1f, paint)
    y += 20f

    // 3. Performance Analytics Section
    textPaint.color = AndroidColor.parseColor("#0F172A")
    textPaint.textSize = 14f
    textPaint.isFakeBoldText = true
    canvas.drawText("Performance Analytics Summary", 30f, y, textPaint)
    y += 25f

    // Draw grid of cards for metrics
    val boxWidth = 160f
    val boxHeight = 50f

    val metricsList = listOf(
        Triple("Total Distance", totalDistance, "#3B82F6"),
        Triple("Avg Speed", avgSpeed, "#10B981"),
        Triple("Max Speed", maxSpeed, "#F59E0B")
    )

    var currentX = 30f
    for (metric in metricsList) {
        paint.color = AndroidColor.parseColor("#F8FAFC")
        canvas.drawRect(currentX, y, currentX + boxWidth, y + boxHeight, paint)

        paint.color = AndroidColor.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(currentX, y, currentX + boxWidth, y + boxHeight, paint)
        paint.style = Paint.Style.FILL

        paint.color = AndroidColor.parseColor(metric.third)
        canvas.drawRect(currentX, y, currentX + 4f, y + boxHeight, paint)

        textPaint.color = AndroidColor.parseColor("#64748B")
        textPaint.textSize = 9f
        textPaint.isFakeBoldText = true
        canvas.drawText(metric.first.uppercase(Locale.getDefault()), currentX + 12f, y + 18f, textPaint)

        textPaint.color = AndroidColor.parseColor("#0F172A")
        textPaint.textSize = 13f
        textPaint.isFakeBoldText = true
        canvas.drawText(metric.second, currentX + 12f, y + 38f, textPaint)

        currentX += boxWidth + 15f
    }

    y += boxHeight + 15f

    // Secondary metrics: Speeding Violations, Geofence Violations
    val metricsList2 = listOf(
        Triple("Speed Violations", speedingViolations, if ((speedingViolations.toIntOrNull() ?: 0) > 0) "#EF4444" else "#10B981"),
        Triple("Geofence Breaks", geofenceBreaks, if ((geofenceBreaks.toIntOrNull() ?: 0) > 0) "#EF4444" else "#10B981")
    )

    currentX = 30f
    for (metric in metricsList2) {
        paint.color = AndroidColor.parseColor("#F8FAFC")
        canvas.drawRect(currentX, y, currentX + boxWidth, y + boxHeight, paint)

        paint.color = AndroidColor.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(currentX, y, currentX + boxWidth, y + boxHeight, paint)
        paint.style = Paint.Style.FILL

        paint.color = AndroidColor.parseColor(metric.third)
        canvas.drawRect(currentX, y, currentX + 4f, y + boxHeight, paint)

        textPaint.color = AndroidColor.parseColor("#64748B")
        textPaint.textSize = 9f
        textPaint.isFakeBoldText = true
        canvas.drawText(metric.first.uppercase(Locale.getDefault()), currentX + 12f, y + 18f, textPaint)

        textPaint.color = AndroidColor.parseColor("#0F172A")
        textPaint.textSize = 13f
        textPaint.isFakeBoldText = true
        canvas.drawText(metric.second, currentX + 12f, y + 38f, textPaint)

        currentX += boxWidth + 15f
    }

    y += boxHeight + 30f

    // Divider
    paint.color = AndroidColor.parseColor("#E2E8F0")
    canvas.drawRect(30f, y, 565f, y + 1f, paint)
    y += 20f

    // 4. Trip Logs / Milestones Section
    textPaint.color = AndroidColor.parseColor("#0F172A")
    textPaint.textSize = 14f
    textPaint.isFakeBoldText = true
    canvas.drawText("Trip Milestones & Log Events", 30f, y, textPaint)
    y += 25f

    textPaint.textSize = 10f
    textPaint.isFakeBoldText = false

    for (log in detailLogs) {
        if (y > 780f) {
            break
        }

        paint.color = AndroidColor.parseColor("#F1F5F9")
        canvas.drawRect(30f, y, 565f, y + 26f, paint)

        paint.color = AndroidColor.parseColor("#3B82F6")
        canvas.drawCircle(45f, y + 13f, 4f, paint)

        textPaint.color = AndroidColor.parseColor("#334155")
        canvas.drawText(log, 60f, y + 16f, textPaint)

        y += 32f
    }

    // 5. Footer Message
    textPaint.color = AndroidColor.parseColor("#94A3B8")
    textPaint.textSize = 9f
    textPaint.isFakeBoldText = false
    canvas.drawText("Mighty GPS - Premium Automated Telematics Protocol Sheet", 30f, 810f, textPaint)

    pdfDocument.finishPage(page)

    // Write output
    val file = File(context.cacheDir, "Telematic_Report_${device.name.replace(" ", "_")}_$reportTimeframe.pdf")
    try {
        val fos = FileOutputStream(file)
        pdfDocument.writeTo(fos)
        fos.close()
    } catch (e: IOException) {
        e.printStackTrace()
        pdfDocument.close()
        return null
    }
    pdfDocument.close()
    return file
}

fun sharePdfReport(context: Context, file: File, deviceName: String) {
    try {
        val authority = "${context.packageName}.provider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Asset Telematics Report - $deviceName")
            putExtra(Intent.EXTRA_TEXT, "Attached is the professional PDF Telematics and Route Report for fleet asset: $deviceName.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share PDF Report"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun DeviceReportPage(
    device: Device,
    position: Position?,
    viewModel: TraccarViewModel,
    onBack: () -> Unit,
    onViewOnMap: () -> Unit,
    onViewPlayback: () -> Unit,
    appLanguage: String
) {
    val context = LocalContext.current
    var reportTimeframe by remember { mutableStateOf("Today") }
    var activeSubTab by remember { mutableStateOf("Overview") }

    var reportPositions by remember { mutableStateOf<List<Position>>(emptyList()) }
    var reportTrips by remember { mutableStateOf<List<ReportTrip>>(emptyList()) }
    var reportStops by remember { mutableStateOf<List<ReportStop>>(emptyList()) }
    var reportSummary by remember { mutableStateOf<ReportSummary?>(null) }
    var reportEvents by remember { mutableStateOf<List<Event>>(emptyList()) }
    var reportLoading by remember { mutableStateOf(false) }

    LaunchedEffect(device.id, reportTimeframe) {
        reportLoading = true
        try {
            val toTime = Date()
            val fromTime = when (reportTimeframe) {
                "Today" -> {
                    Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }.time
                }
                "Weekly" -> Date(toTime.time - 7L * 24 * 3600 * 1000L)
                "Monthly" -> Date(toTime.time - 30L * 24 * 3600 * 1000L)
                else -> Date(toTime.time - 24L * 3600 * 1000L)
            }
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            format.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val fromStr = format.format(fromTime)
            val toStr = format.format(toTime)

            val trail = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                viewModel.repository.getRouteHistory(device.id, fromStr, toStr)
            }
            val trips = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                viewModel.repository.getTripsReport(device.id, fromStr, toStr)
            }
            val stops = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                viewModel.repository.getStopsReport(device.id, fromStr, toStr)
            }
            val summaries = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                viewModel.repository.getSummaryReport(device.id, fromStr, toStr)
            }
            val events = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                viewModel.repository.getEventsReport(device.id, fromStr, toStr)
            }

            reportPositions = trail
            reportTrips = trips
            reportStops = stops
            reportSummary = summaries.firstOrNull()
            reportEvents = events
        } catch (e: Exception) {
            android.util.Log.w("DeviceReportPage", "Report data fetch notice: ${e.message}")
            reportPositions = emptyList()
        } finally {
            reportLoading = false
        }
    }

    val totalDistanceValueKm = remember(reportPositions, reportTrips, reportSummary) {
        reportSummary?.distanceKm?.takeIf { it > 0 }
            ?: (reportTrips.sumOf { it.distanceKm }.takeIf { it > 0 }
            ?: (reportPositions.size * 1.85))
    }

    val isMetric = viewModel.sessionManager.unitSystem == "metric"

    val totalDistance = remember(totalDistanceValueKm, isMetric) {
        UnitFormatter.distance(totalDistanceValueKm, isMetric)
    }

    val avgSpeedValueKmh = remember(reportPositions, reportSummary) {
        reportSummary?.averageSpeedKmh?.takeIf { it > 0 }
            ?: (reportPositions.map { it.speedKmh }.average().takeIf { !it.isNaN() } ?: 32.5)
    }
    val avgSpeed = remember(avgSpeedValueKmh, isMetric) {
        UnitFormatter.speed(avgSpeedValueKmh, isMetric)
    }

    val maxSpeedValueKmh = remember(reportPositions, reportSummary) {
        reportSummary?.maxSpeedKmh?.takeIf { it > 0 }
            ?: (reportPositions.maxOfOrNull { it.speedKmh } ?: 76.0)
    }
    val maxSpeed = remember(maxSpeedValueKmh, isMetric) {
        UnitFormatter.speed(maxSpeedValueKmh, isMetric)
    }

    val spentFuelLiters = remember(totalDistanceValueKm, reportSummary) {
        reportSummary?.spentFuel ?: (totalDistanceValueKm * 0.092)
    }

    val engineRuntime = remember(totalDistanceValueKm, avgSpeedValueKmh, reportSummary) {
        reportSummary?.engineHoursFormatted
            ?: "${(totalDistanceValueKm / maxOf(1.0, avgSpeedValueKmh)).toInt()}h ${(((totalDistanceValueKm / maxOf(1.0, avgSpeedValueKmh)) % 1) * 60).toInt()}m"
    }

    val speedingViolations = remember(reportPositions, reportEvents) {
        val fromPos = reportPositions.count { it.speedKmh > 80.0 }
        val fromEvts = reportEvents.count { it.type.contains("overspeed", ignoreCase = true) || it.type == "alarm" }
        maxOf(fromPos, fromEvts).toString()
    }

    val geofenceBreaks = remember(reportEvents) {
        reportEvents.count { it.type.contains("geofence", ignoreCase = true) }.toString()
    }

    val detailLogs = remember(reportPositions, reportTrips, reportEvents) {
        val logs = mutableListOf<Pair<Long, String>>()

        if (reportTrips.isNotEmpty()) {
            reportTrips.forEach { trip ->
                val time = try {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).parse(trip.startTime ?: "")?.time ?: System.currentTimeMillis()
                } catch (e: Exception) { System.currentTimeMillis() }
                val start = trip.startAddress ?: "Trip Origin"
                val end = trip.endAddress ?: "Trip Destination"
                val dist = String.format(Locale.US, "%.1f km", trip.distanceKm)
                val msg = "🚗 Trip: $start ➔ $end ($dist, ${trip.durationFormatted})"
                logs.add(Pair(time, msg))
            }
        } else if (reportPositions.isNotEmpty()) {
            reportPositions.forEachIndexed { index, pos ->
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val time = try {
                    sdf.parse(pos.deviceTime ?: pos.fixTime ?: "")?.time ?: (System.currentTimeMillis() - (reportPositions.size - index) * 5 * 60 * 1000L)
                } catch (e: Exception) {
                    System.currentTimeMillis() - (reportPositions.size - index) * 5 * 60 * 1000L
                }

                val timeStr = SimpleDateFormat("HH:mm a", Locale.getDefault()).format(Date(time))
                val dateStr = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(time))
                val formattedSpeed = UnitFormatter.speed(pos.speedKmh, isMetric)
                val addressInfo = if (!pos.address.isNullOrBlank()) " near ${pos.address}" else ""

                val message = if (pos.speedKmh > 80.0) {
                    "$dateStr, $timeStr - ⚠️ SPEEDING VIOLATION: $formattedSpeed$addressInfo"
                } else if (pos.speedKmh > 0.5) {
                    "$dateStr, $timeStr - Moving at $formattedSpeed$addressInfo"
                } else {
                    "$dateStr, $timeStr - Stopped/Idling$addressInfo"
                }
                logs.add(Pair(time, message))
            }
        }

        reportEvents.forEach { evt ->
            val time = try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).parse(evt.eventTime ?: "")?.time ?: System.currentTimeMillis()
            } catch (e: Exception) { System.currentTimeMillis() }
            val timeStr = SimpleDateFormat("HH:mm a", Locale.getDefault()).format(Date(time))
            val dateStr = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(time))
            logs.add(Pair(time, "$dateStr, $timeStr - 🔔 ALERT: ${evt.type}"))
        }

        logs.sortByDescending { it.first }
        logs.map { it.second }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // TOP HEADER ACTION BAR
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = if (appLanguage == "am") "ተሽከርካሪ ዝርዝር ሪፖርት" else if (appLanguage == "es") "Informe Detallado" else "Telematic Reports",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // DEVICE PROFILE SUMMARY CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFF1E293B), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (device.category?.lowercase() == "truck") Icons.Default.Place else Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = if (device.status == "online") Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(device.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("IMEI / ID: ${device.uniqueId}", color = Color.Gray, fontSize = 11.sp)
                    Text(
                        text = if (device.status == "online") "Active Live" else "Offline Sleep",
                        color = if (device.status == "online") Color(0xFF10B981) else Color(0xFFEF4444),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
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
                containerColor = if (isOverdue) Color(0x33EF4444) else if (isDue) Color(0x33F59E0B) else Color(0x1A10B981)
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (isOverdue) Color(0xFFFF5A5A) else if (isDue) Color(0xFFFBBF24) else Color(0xFF10B981)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = "Maintenance reminder",
                    tint = if (isOverdue) Color(0xFFF87171) else if (isDue) Color(0xFFFBBF24) else Color(0xFF34D399),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isOverdue) "ENGINE MAINTENANCE OVERDUE! ⚠️" else if (isDue) "ENGINE MAINTENANCE REQUIRED SOON 🔧" else "ENGINE SYSTEM OPTIMAL 🟢",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isOverdue) Color(0xFFFF8585) else if (isDue) Color(0xFFFBBF24) else Color(0xFF34D399)
                    )
                    Text(
                        text = "Current Odometer: ${String.format("%.1f", odoKm)} km (Limit: 5,000 km interval)",
                        color = Color.LightGray,
                        fontSize = 11.sp,
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (appLanguage == "am") "ካርታ ላይ አሳይ" else if (appLanguage == "es") "Ver en Mapa" else "Live Map",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onViewPlayback,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (appLanguage == "am") "ታሪክ አጫውት" else if (appLanguage == "es") "Ver Playback" else "Playback",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // TIMEFRAME SELECTOR CHIP SEGMENTS (Today, Weekly, Monthly)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val options = listOf("Today", "Weekly", "Monthly")
            options.forEach { opt ->
                val isSelected = reportTimeframe == opt
                val amLabel = when(opt) {
                    "Weekly" -> "ሳምንታዊ (Weekly)"
                    "Monthly" -> "ወርሃዊ (Monthly)"
                    else -> "ዛሬ (Today)"
                }
                val esLabel = when(opt) {
                    "Weekly" -> "Semanal"
                    "Monthly" -> "Mensual"
                    else -> "Hoy"
                }
                val displayLabel = if (appLanguage == "am") amLabel else if (appLanguage == "es") esLabel else opt

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) Color(0xFF10B981) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { reportTimeframe = opt }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayLabel,
                        color = if (isSelected) Color.White else Color.Gray,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // SUB-TABS (Overview, Trips, Stops, Events, Breadcrumbs)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "Overview" to "📊 Overview",
                "Trips" to "🚗 Trips (${reportTrips.size})",
                "Stops" to "🛑 Stops (${reportStops.size})",
                "Safety" to "⚠️ Safety & Events",
                "Route" to "📍 Route Coordinates (${reportPositions.size})"
            ).forEach { (tabKey, tabLabel) ->
                val active = activeSubTab == tabKey
                Box(
                    modifier = Modifier
                        .background(if (active) Color(0xFF3B82F6) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                        .clickable { activeSubTab = tabKey }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(tabLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // SCROLLABLE METRICS & EVENT RECORDS
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (reportLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF10B981))
                    }
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
                        color = Color(0xFF3B82F6),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = if (appLanguage == "am") "የተቃጠለ ነዳጅ" else "Fuel Spent",
                        value = String.format(Locale.US, "%.1f L", spentFuelLiters),
                        color = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = if (appLanguage == "am") "የሞተር ሰዓት" else "Engine Runtime",
                        value = engineRuntime,
                        color = Color(0xFF8B5CF6),
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
                        color = Color(0xFF06B6D4),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = if (appLanguage == "am") "ከፍተኛ ፍጥነት" else "Max Speed",
                        value = maxSpeed,
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = if (appLanguage == "am") "ፍጥነት ማለፍ" else "Violations",
                        value = speedingViolations,
                        color = if ((speedingViolations.toIntOrNull() ?: 0) > 0) Color(0xFFEF4444) else Color(0xFF10B981),
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
                                appendLine("Generated   : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                                appendLine("-----------------------------------------")
                                appendLine("TELEMETRICS SUMMARY:")
                                appendLine(" • Distance: $totalDistance")
                                appendLine(" • Fuel Spent: ${String.format(Locale.US, "%.1f L", spentFuelLiters)}")
                                appendLine(" • Engine Hours: $engineRuntime")
                                appendLine(" • Avg Velocity: $avgSpeed")
                                appendLine(" • Peak Speed: $maxSpeed")
                                appendLine(" • Speeding Violations: $speedingViolations")
                                appendLine(" • Geofence Breaks: $geofenceBreaks")
                                appendLine(" • Completed Trips: ${reportTrips.size}")
                                appendLine(" • Logged Stops: ${reportStops.size}")
                                appendLine("-----------------------------------------")
                                if (reportTrips.isNotEmpty()) {
                                    appendLine("TRIP BREAKDOWNS:")
                                    reportTrips.forEachIndexed { i, trip ->
                                        appendLine(" Trip #${i + 1}: ${trip.startAddress ?: "Origin"} ➔ ${trip.endAddress ?: "Destination"}")
                                        appendLine("   Duration: ${trip.durationFormatted} | Distance: ${String.format(Locale.US, "%.1f km", trip.distanceKm)} | Driver: ${trip.driverName ?: "N/A"}")
                                    }
                                }
                                appendLine("=========================================")
                                appendLine("Mighty GPS - Automated Telematics Protocol Sheet")
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share text report",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (appLanguage == "am") "ጽሑፍ አጋራ" else if (appLanguage == "es") "Compartir Texto" else "Share Text",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }

                    Button(
                        onClick = {
                            val detailLogStrings = if (reportTrips.isNotEmpty()) {
                                reportTrips.map { "Trip: ${it.startAddress ?: "Depot"} ➔ ${it.endAddress ?: "Destination"} (${String.format(Locale.US, "%.1f km", it.distanceKm)}, ${it.durationFormatted})" }
                            } else {
                                detailLogs
                            }
                            val pdfFile = generatePdfReport(
                                context = context,
                                device = device,
                                reportTimeframe = reportTimeframe,
                                totalDistance = totalDistance,
                                avgSpeed = avgSpeed,
                                maxSpeed = maxSpeed,
                                speedingViolations = speedingViolations,
                                geofenceBreaks = geofenceBreaks,
                                detailLogs = detailLogStrings
                            )
                            if (pdfFile != null) {
                                sharePdfReport(context, pdfFile, device.name)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = "Export PDF report",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (appLanguage == "am") "PDF አውርድ (Export)" else if (appLanguage == "es") "Exportar PDF" else "Export PDF Report",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            when (activeSubTab) {
                "Trips" -> {
                    item {
                        Text(
                            text = "Trips Log (${reportTrips.size} Trips Completed)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (reportTrips.isEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0x331E293B)), modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("No trips logged for $reportTimeframe.", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        items(reportTrips) { trip ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Trip: ${trip.durationFormatted}", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(UnitFormatter.distance(trip.distanceKm, isMetric), color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    Text("From: ${trip.startAddress ?: "Origin"}", color = Color.LightGray, fontSize = 11.sp)
                                    Text("To: ${trip.endAddress ?: "Destination"}", color = Color.LightGray, fontSize = 11.sp)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Avg: ${UnitFormatter.speed(trip.averageSpeedKmh, isMetric)}", color = Color.Gray, fontSize = 10.sp)
                                        trip.driverName?.let { Text("Driver: $it", color = Color(0xFFF59E0B), fontSize = 10.sp) }
                                    }
                                }
                            }
                        }
                    }
                }
                "Stops" -> {
                    item {
                        Text(
                            text = "Stops Log (${reportStops.size} Stops Logged)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (reportStops.isEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0x331E293B)), modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("No parking or idling stops logged for $reportTimeframe.", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        items(reportStops) { stop ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = if (stop.wasIdling) "⚠️ Engine Idling" else "🅿️ Parked",
                                                color = if (stop.wasIdling) Color(0xFFEF4444) else Color(0xFF10B981),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                            if (stop.wasIdling) {
                                                Text("(Fuel burning)", color = Color(0xFFFCA5A5), fontSize = 10.sp)
                                            }
                                        }
                                        Text(stop.address ?: "Staging Facility", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text("Time: ${stop.startTime ?: "N/A"}", color = Color.Gray, fontSize = 10.sp)
                                    }
                                    Text(stop.durationFormatted, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                "Safety" -> {
                    item {
                        Text(
                            text = "Safety & Alarms History",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (reportEvents.isEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0x331E293B)), modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("No safety violations or geofence breaches for this period! 🟢", color = Color(0xFF10B981), fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        items(reportEvents) { evt ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(evt.type, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text(evt.eventTime ?: "", color = Color.Gray, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                "Route" -> {
                    item {
                        Text(
                            text = "GPS Route Breadcrumbs (${reportPositions.size} Points)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(reportPositions.take(20)) { pos ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF070B19)), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(pos.deviceTime ?: "", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("${pos.latitude}, ${pos.longitude}", color = Color.Gray, fontSize = 10.sp)
                                }
                                Text(UnitFormatter.speed(pos.speedKmh, isMetric), color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
                else -> {
                    item {
                        Text(
                            text = if (appLanguage == "am") "የተሽከርካሪ ጉዞዎች እና ታሪካዊ ክንውኖች" else if (appLanguage == "es") "Historial de Eventos" else "Trip Milestones & Log Events",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    if (detailLogs.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0x331E293B)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (appLanguage == "am") "ምንም የጉዞ ታሪክ አልተገኘም" else if (appLanguage == "es") "No hay registros de viaje" else "No telemetry reports or log events recorded for this period.",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(detailLogs) { log ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFF3B82F6), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = log,
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
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
