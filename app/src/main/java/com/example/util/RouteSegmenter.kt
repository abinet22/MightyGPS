package com.example.util

import com.example.data.model.Position
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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

fun getEpochTime(timeStr: String?): Long {
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
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val t = sdf.parse(timeStr)?.time
                if (t != null) {
                    parsedTime = t
                    break
                }
            } catch (_: Exception) {
                // Ignore and try next format
            }
        }
        parsedTime
    } catch (_: Exception) {
        0L
    }
}

fun formatDuration(seconds: Long): String {
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

    val segmentDistance = { from: Int, to: Int ->
        var sum = 0.0
        for (i in from until to) {
            val p1 = routeHistory.getOrNull(i)
            val p2 = routeHistory.getOrNull(i + 1)
            if (p1 != null && p2 != null) {
                sum += GeofenceUtils.calculateDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude) / 1000.0
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
        val distKm = GeofenceUtils.calculateDistanceMeters(prev.latitude, prev.longitude, curr.latitude, curr.longitude) / 1000.0

        // Split criteria: time gap > 10 minutes (600s) OR stopped for > 5 mins
        val isTripBreak = timeDiffSecs > 600 || (timeDiffSecs > 300 && (curr.speed ?: 0.0) < 1.0 && (prev.speed ?: 0.0) < 1.0)

        if (isTripBreak || i == routeHistory.size - 1) {
            val endIdx = if (isTripBreak) i - 1 else i
            if (endIdx >= startIdx) {
                val segPositions = routeHistory.subList(startIdx, endIdx + 1)
                val dist = segmentDistance(startIdx, endIdx)
                val tStart = getEpochTime(segPositions.first().deviceTime)
                val tEnd = getEpochTime(segPositions.last().deviceTime)
                val durationSec = if (tEnd >= tStart) (tEnd - tStart) / 1000 else 0L

                val speedsKmh = segPositions.map { (it.speed ?: 0.0) * 1.852 }
                val maxSpd = speedsKmh.maxOrNull() ?: 0.0
                val avgSpd = if (durationSec > 0) (dist / (durationSec / 3600.0)) else 0.0

                val startPos = segPositions.first()
                val endPos = segPositions.last()

                val sTime = startPos.deviceTime?.substringAfter("T")?.substringBefore("Z")?.take(5)
                    ?: startPos.deviceTime?.takeLast(8)?.take(5)
                    ?: "00:00"
                val eTime = endPos.deviceTime?.substringAfter("T")?.substringBefore("Z")?.take(5)
                    ?: endPos.deviceTime?.takeLast(8)?.take(5)
                    ?: "00:00"

                segments.add(
                    RouteSegment(
                        segmentIndex = segmentCounter++,
                        startIndex = startIdx,
                        endIndex = endIdx,
                        startTime = sTime,
                        endTime = eTime,
                        durationStr = formatDuration(durationSec),
                        distanceKm = dist,
                        startLat = startPos.latitude,
                        startLng = startPos.longitude,
                        endLat = endPos.latitude,
                        endLng = endPos.longitude,
                        averageSpeed = avgSpd,
                        maxSpeed = maxSpd
                    )
                )
            }
            startIdx = i
        }
    }

    return segments
}
