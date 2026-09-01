package com.example.util

import com.example.data.model.Position
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object TelemetrySanitizerService {

    const val METERS_PER_MILE = 1609.344
    const val KNOTS_TO_KMH = 1.852
    const val KNOTS_TO_MPH = 1.15077945
    const val KMH_TO_MPH = 0.621371192
    const val DEFAULT_MAX_ACCURACY_METERS = 100.0
    const val DEFAULT_STATIC_DRIFT_SPEED_KMH = 0.8
    const val DEFAULT_MIN_DRIFT_DISTANCE_METERS = 3.5

    // ==========================================
    // 1. UNIT NORMALIZATION LAYER
    // ==========================================

    fun metersToKilometers(meters: Double): Double {
        return meters / 1000.0
    }

    fun metersToMiles(meters: Double): Double {
        return meters / METERS_PER_MILE
    }

    fun formatDistance(meters: Double, isMetric: Boolean = true): String {
        return if (isMetric) {
            String.format(Locale.US, "%.2f km", metersToKilometers(meters))
        } else {
            String.format(Locale.US, "%.2f mi", metersToMiles(meters))
        }
    }

    fun formatDistanceKm(km: Double, isMetric: Boolean = true): String {
        return if (isMetric) {
            String.format(Locale.US, "%.2f km", km)
        } else {
            String.format(Locale.US, "%.2f mi", km * KMH_TO_MPH)
        }
    }

    fun knotsToKmh(knots: Double): Double {
        return knots * KNOTS_TO_KMH
    }

    fun knotsToMph(knots: Double): Double {
        return knots * KNOTS_TO_MPH
    }

    fun kmhToMph(kmh: Double): Double {
        return kmh * KMH_TO_MPH
    }

    fun formatSpeedFromKnots(knots: Double, isMetric: Boolean = true): String {
        return if (isMetric) {
            String.format(Locale.US, "%.1f km/h", knotsToKmh(knots))
        } else {
            String.format(Locale.US, "%.1f mph", knotsToMph(knots))
        }
    }

    fun formatSpeedFromKmh(kmh: Double, isMetric: Boolean = true): String {
        return if (isMetric) {
            String.format(Locale.US, "%.1f km/h", kmh)
        } else {
            String.format(Locale.US, "%.1f mph", kmhToMph(kmh))
        }
    }

    fun formatDurationHms(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatDurationCompact(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    // ==========================================
    // 2. ROUTE DRIFT SCRUBBING & INTERPOLATION
    // ==========================================

    fun sanitizeRoute(
        positions: List<Position>,
        maxAccuracyMeters: Double = DEFAULT_MAX_ACCURACY_METERS,
        minDriftDistanceMeters: Double = DEFAULT_MIN_DRIFT_DISTANCE_METERS,
        driftSpeedThresholdKmh: Double = DEFAULT_STATIC_DRIFT_SPEED_KMH
    ): List<Position> {
        if (positions.isEmpty()) return emptyList()

        // 1. Filter corrupt or invalid fixes
        val validFixes = positions.filter { p ->
            p.valid &&
            p.latitude != 0.0 && p.longitude != 0.0 &&
            p.latitude in -90.0..90.0 &&
            p.longitude in -180.0..180.0 &&
            (p.accuracy <= 0.0 || p.accuracy <= maxAccuracyMeters)
        }

        if (validFixes.size <= 2) return validFixes

        // 2. Remove static GPS drift while stationary (speed ~ 0 and distance jitter < threshold)
        val sanitized = mutableListOf<Position>()
        sanitized.add(validFixes.first())

        for (i in 1 until validFixes.size) {
            val curr = validFixes[i]
            val prev = sanitized.last()
            val dist = GeofenceUtils.calculateDistanceMeters(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
            val speedKmh = curr.speedKmh

            // Check if device is stationary and jittering around same coordinate
            val isStaticDrift = (speedKmh <= driftSpeedThresholdKmh && dist < minDriftDistanceMeters)
            
            // Keep if moving, or if significant position change, or if it's the last point
            if (!isStaticDrift || i == validFixes.lastIndex) {
                sanitized.add(curr)
            }
        }

        return sanitized
    }

    // ==========================================
    // 3. TIMEZONE & ISO 8601 FORMATTING
    // ==========================================

    private const val ISO_8601_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    private const val DISPLAY_DATETIME_PATTERN = "yyyy-MM-dd HH:mm"
    private const val DISPLAY_TIME_PATTERN = "HH:mm"
    private const val DISPLAY_DATE_PATTERN = "yyyy-MM-dd"

    fun getUtcIso8601Formatter(): java.text.SimpleDateFormat {
        val format = java.text.SimpleDateFormat(ISO_8601_PATTERN, Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return format
    }

    fun formatToUtcIso8601(date: java.util.Date): String {
        return getUtcIso8601Formatter().format(date)
    }

    fun parseUtcIso8601(isoString: String?): java.util.Date? {
        if (isoString.isNullOrBlank()) return null
        val clean = isoString.trim()

        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm"
        )

        for (p in patterns) {
            try {
                val sdf = java.text.SimpleDateFormat(p, Locale.US)
                if (p.contains("Z") || p.contains("XXX")) {
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(clean)
                if (parsed != null) return parsed
            } catch (_: Exception) {
                // Try next pattern
            }
        }
        return null
    }

    fun formatIsoToLocalDisplay(
        isoString: String?,
        pattern: String = DISPLAY_DATETIME_PATTERN,
        targetTimeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
    ): String {
        if (isoString.isNullOrBlank()) return "N/A"
        val date = parseUtcIso8601(isoString)
        if (date != null) {
            val localFormat = java.text.SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = targetTimeZone
            }
            return localFormat.format(date)
        }

        // Fallback string manipulation to ensure seconds, milliseconds, and offsets like .000+00:00 are removed
        return try {
            var str = isoString.replace('T', ' ').trim()
            val plusIdx = str.indexOf('+')
            if (plusIdx > 0) str = str.substring(0, plusIdx).trim()
            if (str.endsWith("Z", ignoreCase = true)) str = str.substring(0, str.length - 1).trim()
            val dotIdx = str.indexOf('.')
            if (dotIdx > 0) str = str.substring(0, dotIdx).trim()
            if (str.length >= 16 && str.contains("-") && str.contains(":")) {
                str.substring(0, 16)
            } else {
                str
            }
        } catch (_: Exception) {
            isoString
        }
    }

    fun formatIsoToLocalTime(
        isoString: String?,
        targetTimeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
    ): String {
        return formatIsoToLocalDisplay(isoString, DISPLAY_TIME_PATTERN, targetTimeZone)
    }

    fun formatIsoToLocalDate(
        isoString: String?,
        targetTimeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
    ): String {
        return formatIsoToLocalDisplay(isoString, DISPLAY_DATE_PATTERN, targetTimeZone)
    }

    // ==========================================
    // 4. TIMEFRAME RANGE CALCULATION
    // ==========================================

    fun computeRange(timeframe: String): Pair<String, String> {
        val toTime = java.util.Date()
        val fromTime = when (timeframe) {
            "Today" -> {
                java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.time
            }
            "Weekly" -> java.util.Date(toTime.time - 7L * 24 * 3600 * 1000L)
            "Monthly" -> java.util.Date(toTime.time - 30L * 24 * 3600 * 1000L)
            "Past 3h" -> java.util.Date(toTime.time - 3L * 3600 * 1000L)
            "Past 12h" -> java.util.Date(toTime.time - 12L * 3600 * 1000L)
            "Past 24h" -> java.util.Date(toTime.time - 24L * 3600 * 1000L)
            "Past 72h" -> java.util.Date(toTime.time - 72L * 3600 * 1000L)
            else -> java.util.Date(toTime.time - 24L * 3600 * 1000L)
        }
        return Pair(formatToUtcIso8601(fromTime), formatToUtcIso8601(toTime))
    }
}
