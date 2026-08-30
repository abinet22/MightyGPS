package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.model.Device
import com.example.data.model.Event
import com.example.data.model.ReportStop
import com.example.data.model.ReportTrip
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun generatePdfReport(
    context: Context,
    device: Device,
    reportTimeframe: String,
    totalDistance: String,
    avgSpeed: String,
    maxSpeed: String,
    spentFuel: String,
    engineHours: String,
    speedingViolations: String,
    geofenceBreaks: String,
    trips: List<ReportTrip>,
    stops: List<ReportStop>,
    events: List<Event>
): File? {
    val pdfDocument = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    var pageNumber = 1

    var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
    var page = pdfDocument.startPage(pageInfo)
    var canvas = page.canvas

    val paint = Paint()
    val textPaint = Paint().apply {
        isAntiAlias = true
    }

    fun drawFooter(pageNum: Int) {
        val footerPaint = Paint().apply {
            isAntiAlias = true
            color = AndroidColor.parseColor("#94A3B8")
            textSize = 8f
        }
        canvas.drawText("Mighty GPS Telematics Engine • Asset: ${device.name} • Timeframe: $reportTimeframe", 30f, 815f, footerPaint)
        canvas.drawText("Page $pageNum", 530f, 815f, footerPaint)
    }

    fun checkAndCreateNewPage(requiredHeight: Float): Float {
        var currentY = 0f
        return currentY
    }

    var y = 30f

    // 1. Header Banner
    paint.color = AndroidColor.parseColor("#0F172A")
    canvas.drawRect(20f, y, 575f, y + 70f, paint)

    textPaint.color = AndroidColor.WHITE
    textPaint.textSize = 18f
    textPaint.isFakeBoldText = true
    canvas.drawText("FLEET TELEMATICS & TRIPS REPORT", 35f, y + 38f, textPaint)

    textPaint.textSize = 9f
    textPaint.isFakeBoldText = false
    val generatedTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    canvas.drawText("Generated: $generatedTime • System Timezone: ${java.util.TimeZone.getDefault().id}", 35f, y + 55f, textPaint)

    y += 85f

    // 2. Asset Profile Details Card
    paint.color = AndroidColor.parseColor("#F8FAFC")
    canvas.drawRect(20f, y, 575f, y + 65f, paint)
    paint.color = AndroidColor.parseColor("#E2E8F0")
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1f
    canvas.drawRect(20f, y, 575f, y + 65f, paint)
    paint.style = Paint.Style.FILL

    textPaint.color = AndroidColor.parseColor("#0F172A")
    textPaint.textSize = 12f
    textPaint.isFakeBoldText = true
    canvas.drawText("Asset Profile & Telematics Scope", 32f, y + 18f, textPaint)

    textPaint.textSize = 9f
    textPaint.isFakeBoldText = false
    textPaint.color = AndroidColor.parseColor("#475569")
    canvas.drawText("Asset Name: ${device.name}", 32f, y + 34f, textPaint)
    canvas.drawText("IMEI / ID: ${device.uniqueId}", 210f, y + 34f, textPaint)
    canvas.drawText("Category: ${device.category ?: "Fleet Vehicle"}", 400f, y + 34f, textPaint)

    canvas.drawText("Report Window: $reportTimeframe", 32f, y + 50f, textPaint)
    canvas.drawText("Total Trips: ${trips.size}", 210f, y + 50f, textPaint)
    canvas.drawText("Logged Stops: ${stops.size} (Events: ${events.size})", 400f, y + 50f, textPaint)

    y += 80f

    // 3. Performance Summary Grid (6 cards)
    textPaint.color = AndroidColor.parseColor("#0F172A")
    textPaint.textSize = 12f
    textPaint.isFakeBoldText = true
    canvas.drawText("Executive Telemetry Analytics", 25f, y, textPaint)
    y += 12f

    val cardWidth = 175f
    val cardHeight = 44f
    val row1Metrics = listOf(
        Triple("Total Distance", totalDistance, "#3B82F6"),
        Triple("Fuel Spent", spentFuel, "#10B981"),
        Triple("Engine Runtime", engineHours, "#3B82F6")
    )
    val row2Metrics = listOf(
        Triple("Average Speed", avgSpeed, "#F59E0B"),
        Triple("Peak Speed", maxSpeed, "#F59E0B"),
        Triple("Violations / Breaches", "$speedingViolations / $geofenceBreaks", if ((speedingViolations.toIntOrNull() ?: 0) > 0) "#EF4444" else "#10B981")
    )

    fun drawMetricRow(metrics: List<Triple<String, String, String>>, startY: Float) {
        var cx = 20f
        for (m in metrics) {
            paint.color = AndroidColor.parseColor("#F8FAFC")
            canvas.drawRect(cx, startY, cx + cardWidth, startY + cardHeight, paint)
            paint.color = AndroidColor.parseColor("#E2E8F0")
            paint.style = Paint.Style.STROKE
            canvas.drawRect(cx, startY, cx + cardWidth, startY + cardHeight, paint)
            paint.style = Paint.Style.FILL

            // Color indicator
            paint.color = AndroidColor.parseColor(m.third)
            canvas.drawRect(cx, startY, cx + 4f, startY + cardHeight, paint)

            textPaint.color = AndroidColor.parseColor("#64748B")
            textPaint.textSize = 8f
            textPaint.isFakeBoldText = true
            canvas.drawText(m.first.uppercase(Locale.getDefault()), cx + 10f, startY + 16f, textPaint)

            textPaint.color = AndroidColor.parseColor("#0F172A")
            textPaint.textSize = 11f
            textPaint.isFakeBoldText = true
            canvas.drawText(m.second, cx + 10f, startY + 34f, textPaint)

            cx += cardWidth + 5f
        }
    }

    drawMetricRow(row1Metrics, y)
    y += cardHeight + 6f
    drawMetricRow(row2Metrics, y)
    y += cardHeight + 20f

    // Helper for multi-page overflow
    fun ensurePageSpace(requiredHeight: Float) {
        if (y + requiredHeight > 790f) {
            drawFooter(pageNumber)
            pdfDocument.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas

            // Mini header on continuation pages
            paint.color = AndroidColor.parseColor("#0F172A")
            canvas.drawRect(20f, 25f, 575f, 55f, paint)
            textPaint.color = AndroidColor.WHITE
            textPaint.textSize = 11f
            textPaint.isFakeBoldText = true
            canvas.drawText("FLEET TELEMATICS REPORT • ${device.name} ($reportTimeframe)", 30f, 44f, textPaint)

            y = 70f
        }
    }

    // 4. ITEMIZED TRIPS SECTION
    ensurePageSpace(30f)
    textPaint.color = AndroidColor.parseColor("#0F172A")
    textPaint.textSize = 12f
    textPaint.isFakeBoldText = true
    canvas.drawText("Itemized Trips Log (${trips.size} Completed Trips)", 25f, y, textPaint)
    y += 14f

    if (trips.isEmpty()) {
        ensurePageSpace(28f)
        paint.color = AndroidColor.parseColor("#F1F5F9")
        canvas.drawRect(20f, y, 575f, y + 24f, paint)
        textPaint.color = AndroidColor.parseColor("#64748B")
        textPaint.textSize = 9f
        textPaint.isFakeBoldText = false
        canvas.drawText("No driving trips recorded for this timeframe.", 30f, y + 16f, textPaint)
        y += 32f
    } else {
        trips.forEachIndexed { idx, trip ->
            val tripBoxHeight = 58f
            ensurePageSpace(tripBoxHeight + 8f)

            // Card background
            paint.color = AndroidColor.parseColor("#F8FAFC")
            canvas.drawRect(20f, y, 575f, y + tripBoxHeight, paint)
            paint.color = AndroidColor.parseColor("#E2E8F0")
            paint.style = Paint.Style.STROKE
            canvas.drawRect(20f, y, 575f, y + tripBoxHeight, paint)
            paint.style = Paint.Style.FILL

            // Blue accent left bar
            paint.color = AndroidColor.parseColor("#3B82F6")
            canvas.drawRect(20f, y, 24f, y + tripBoxHeight, paint)

            // Line 1: Trip # and Timeframe
            textPaint.color = AndroidColor.parseColor("#1E3A8A")
            textPaint.textSize = 9.5f
            textPaint.isFakeBoldText = true
            val tripTitle = "Trip #${idx + 1}: ${trip.timeRangeFormatted}  (Duration: ${trip.durationFormatted})"
            canvas.drawText(tripTitle, 32f, y + 14f, textPaint)

            // Distance Badge on right
            textPaint.color = AndroidColor.parseColor("#059669")
            textPaint.textSize = 9.5f
            val distLabel = String.format(Locale.US, "%.1f km", trip.distanceKm)
            canvas.drawText(distLabel, 510f, y + 14f, textPaint)

            // Line 2: Origin location
            textPaint.color = AndroidColor.parseColor("#334155")
            textPaint.textSize = 8.5f
            textPaint.isFakeBoldText = false
            val startLoc = "From: ${trip.startAddress ?: "Origin Terminal"} (${trip.startTimeFormatted})"
            canvas.drawText(if (startLoc.length > 95) startLoc.take(92) + "..." else startLoc, 32f, y + 28f, textPaint)

            // Line 3: Destination location
            val endLoc = "To: ${trip.endAddress ?: "Destination Facility"} (${trip.endTimeFormatted})"
            canvas.drawText(if (endLoc.length > 95) endLoc.take(92) + "..." else endLoc, 32f, y + 42f, textPaint)

            // Line 4: Stats
            textPaint.color = AndroidColor.parseColor("#64748B")
            textPaint.textSize = 8f
            val driverStr = trip.driverName?.let { " • Driver: $it" } ?: ""
            canvas.drawText("Avg Speed: ${String.format(Locale.US, "%.1f km/h", trip.averageSpeedKmh)} • Max Speed: ${String.format(Locale.US, "%.1f km/h", trip.maxSpeedKmh)}$driverStr", 32f, y + 53f, textPaint)

            y += tripBoxHeight + 6f
        }
        y += 10f
    }

    // 5. ITEMIZED STOPS & IDLING SECTION
    ensurePageSpace(30f)
    textPaint.color = AndroidColor.parseColor("#0F172A")
    textPaint.textSize = 12f
    textPaint.isFakeBoldText = true
    canvas.drawText("Itemized Stops & Idling Log (${stops.size} Logged Stops)", 25f, y, textPaint)
    y += 14f

    if (stops.isEmpty()) {
        ensurePageSpace(28f)
        paint.color = AndroidColor.parseColor("#F1F5F9")
        canvas.drawRect(20f, y, 575f, y + 24f, paint)
        textPaint.color = AndroidColor.parseColor("#64748B")
        textPaint.textSize = 9f
        textPaint.isFakeBoldText = false
        canvas.drawText("No parking or idling stops logged for this timeframe.", 30f, y + 16f, textPaint)
        y += 32f
    } else {
        stops.forEachIndexed { idx, stop ->
            val stopBoxHeight = 44f
            ensurePageSpace(stopBoxHeight + 6f)

            // Card background
            paint.color = AndroidColor.parseColor("#F8FAFC")
            canvas.drawRect(20f, y, 575f, y + stopBoxHeight, paint)
            paint.color = AndroidColor.parseColor("#E2E8F0")
            paint.style = Paint.Style.STROKE
            canvas.drawRect(20f, y, 575f, y + stopBoxHeight, paint)
            paint.style = Paint.Style.FILL

            // Indicator bar (Orange for idling, Emerald for parked)
            val barColor = if (stop.wasIdling) "#F59E0B" else "#10B981"
            paint.color = AndroidColor.parseColor(barColor)
            canvas.drawRect(20f, y, 24f, y + stopBoxHeight, paint)

            // Line 1: Stop #, Type and Duration
            textPaint.color = if (stop.wasIdling) AndroidColor.parseColor("#B45309") else AndroidColor.parseColor("#047857")
            textPaint.textSize = 9.5f
            textPaint.isFakeBoldText = true
            val statusLabel = if (stop.wasIdling) "Engine Idling (Fuel burning)" else "Parked (Ignition Off)"
            canvas.drawText("Stop #${idx + 1}: $statusLabel  (Duration: ${stop.durationFormatted})", 32f, y + 14f, textPaint)

            // Time range on right
            textPaint.color = AndroidColor.parseColor("#475569")
            textPaint.textSize = 8.5f
            textPaint.isFakeBoldText = false
            canvas.drawText(stop.timeRangeFormatted, 380f, y + 14f, textPaint)

            // Line 2: Location Place Name
            textPaint.color = AndroidColor.parseColor("#1E293B")
            textPaint.textSize = 8.5f
            val locLabel = "Location: ${stop.address ?: "Staging Yard / Facility"}"
            canvas.drawText(if (locLabel.length > 90) locLabel.take(87) + "..." else locLabel, 32f, y + 30f, textPaint)

            y += stopBoxHeight + 5f
        }
        y += 10f
    }

    // 6. SECURITY & ALARM EVENTS (if any)
    if (events.isNotEmpty()) {
        ensurePageSpace(30f)
        textPaint.color = AndroidColor.parseColor("#0F172A")
        textPaint.textSize = 12f
        textPaint.isFakeBoldText = true
        canvas.drawText("Security Alarms & Geofence Breaches (${events.size} Events)", 25f, y, textPaint)
        y += 14f

        events.forEachIndexed { idx, evt ->
            ensurePageSpace(26f)
            paint.color = AndroidColor.parseColor("#FEF2F2")
            canvas.drawRect(20f, y, 575f, y + 22f, paint)
            paint.color = AndroidColor.parseColor("#EF4444")
            canvas.drawRect(20f, y, 23f, y + 22f, paint)

            textPaint.color = AndroidColor.parseColor("#991B1B")
            textPaint.textSize = 8.5f
            textPaint.isFakeBoldText = true
            canvas.drawText("Alert #${idx + 1}: ${evt.type}", 30f, y + 14f, textPaint)

            textPaint.color = AndroidColor.parseColor("#475569")
            textPaint.textSize = 8f
            textPaint.isFakeBoldText = false
            val timeStr = evt.eventTime ?: "Recorded"
            val localTimeStr = TelemetrySanitizerService.formatIsoToLocalDisplay(timeStr, "yyyy-MM-dd HH:mm")
            canvas.drawText("Timestamp: $localTimeStr", 280f, y + 14f, textPaint)

            y += 26f
        }
    }

    // Draw final page footer
    drawFooter(pageNumber)
    pdfDocument.finishPage(page)

    // Save to cache directory
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
            putExtra(Intent.EXTRA_TEXT, "Attached is the itemized PDF Telematics and Route Report for fleet asset: $deviceName.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share PDF Report"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
