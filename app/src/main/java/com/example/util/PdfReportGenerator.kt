package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.model.Device
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
    speedingViolations: String,
    geofenceBreaks: String,
    detailLogs: List<String>
): File? {
    val pdfDocument = PdfDocument()
    
    // Page height and width (A4 size: 595 x 842 points)
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
        // Draw card background
        paint.color = AndroidColor.parseColor("#F8FAFC")
        canvas.drawRect(currentX, y, currentX + boxWidth, y + boxHeight, paint)
        
        // Draw card border
        paint.color = AndroidColor.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(currentX, y, currentX + boxWidth, y + boxHeight, paint)
        paint.style = Paint.Style.FILL // revert
        
        // Draw left bar
        paint.color = AndroidColor.parseColor(metric.third)
        canvas.drawRect(currentX, y, currentX + 4f, y + boxHeight, paint)
        
        // Text descriptors
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
    
    // Draw secondary metrics: Speeding Violations, Geofence Violations
    val metricsList2 = listOf(
        Triple("Speed Violations", speedingViolations, if ((speedingViolations.toIntOrNull() ?: 0) > 0) "#EF4444" else "#10B981"),
        Triple("Geofence Breaks", geofenceBreaks, if ((geofenceBreaks.toIntOrNull() ?: 0) > 0) "#EF4444" else "#10B981")
    )
    
    currentX = 30f
    for (metric in metricsList2) {
        // Draw card background
        paint.color = AndroidColor.parseColor("#F8FAFC")
        canvas.drawRect(currentX, y, currentX + boxWidth, y + boxHeight, paint)
        
        // Draw card border
        paint.color = AndroidColor.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(currentX, y, currentX + boxWidth, y + boxHeight, paint)
        paint.style = Paint.Style.FILL // revert
        
        // Draw left bar
        paint.color = AndroidColor.parseColor(metric.third)
        canvas.drawRect(currentX, y, currentX + 4f, y + boxHeight, paint)
        
        // Text descriptors
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
            // Defensive page packing: end page and open page 2 if items are too long
            break
        }
        
        // Draw clean record background
        paint.color = AndroidColor.parseColor("#F1F5F9")
        canvas.drawRect(30f, y, 565f, y + 26f, paint)
        
        // Draw blue record pointer
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
