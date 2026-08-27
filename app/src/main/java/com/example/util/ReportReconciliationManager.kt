package com.example.util

import com.example.data.model.DailySummary
import com.example.data.model.Event
import com.example.data.model.GeofenceDwellRecord
import com.example.data.model.GeofenceReport
import com.example.data.model.PeriodReport
import com.example.data.model.PeriodType
import com.example.data.model.Position
import com.example.data.model.SpeedViolationEvent
import com.example.data.model.SpeedingViolationReport
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ReportReconciliationManager {

    private const val ISO_8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    private const val DISPLAY_DATE_FORMAT = "MMM dd, yyyy"
    private const val DISPLAY_TIME_FORMAT = "MMM dd, HH:mm"

    // ==========================================
    // 1. TIMEZONE BOUNDARY ALIGNMENT (UTC <-> LOCAL)
    // ==========================================

    fun getUtcIso8601Formatter(): SimpleDateFormat {
        val format = SimpleDateFormat(ISO_8601_FORMAT, Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format
    }

    fun formatUtcIso8601(date: Date): String {
        return getUtcIso8601Formatter().format(date)
    }

    fun parseUtcIso8601(utcString: String): Date? {
        return try {
            getUtcIso8601Formatter().parse(utcString)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Calculates start and end timestamps for DAILY, WEEKLY, or MONTHLY ranges
     * based on the device's local timezone, and converts both to strict UTC ISO 8601 strings.
     */
    fun calculateUtcRangeForPeriod(
        periodType: PeriodType,
        referenceDate: Date = Date(),
        localTimeZone: TimeZone = TimeZone.getDefault()
    ): Pair<String, String> {
        val calendar = Calendar.getInstance(localTimeZone)
        calendar.time = referenceDate

        val fromCalendar = Calendar.getInstance(localTimeZone).apply {
            time = referenceDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val toCalendar = Calendar.getInstance(localTimeZone).apply {
            time = referenceDate
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        when (periodType) {
            PeriodType.DAILY -> {
                // Today: 00:00:00 to 23:59:59 in local time
            }
            PeriodType.WEEKLY -> {
                // Current week from first day of week
                fromCalendar.set(Calendar.DAY_OF_WEEK, fromCalendar.firstDayOfWeek)
            }
            PeriodType.MONTHLY -> {
                // Current month from 1st day to last day of month
                fromCalendar.set(Calendar.DAY_OF_MONTH, 1)
                toCalendar.set(Calendar.DAY_OF_MONTH, toCalendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            }
            PeriodType.CUSTOM -> {
                // Default 24h
            }
        }

        val fromUtc = formatUtcIso8601(fromCalendar.time)
        val toUtc = formatUtcIso8601(toCalendar.time)
        return Pair(fromUtc, toUtc)
    }

    fun formatLocalDisplayRange(
        fromUtc: String,
        toUtc: String,
        localTimeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val fromDate = parseUtcIso8601(fromUtc) ?: return "$fromUtc - $toUtc"
        val toDate = parseUtcIso8601(toUtc) ?: return "$fromUtc - $toUtc"

        val displayFormat = SimpleDateFormat(DISPLAY_DATE_FORMAT, Locale.US).apply {
            timeZone = localTimeZone
        }
        return "${displayFormat.format(fromDate)} - ${displayFormat.format(toDate)}"
    }

    // ==========================================
    // 2. MATHEMATICAL REPORT RECONCILIATION
    // ==========================================

    /**
     * Reconciles daily summaries into an overarching PeriodReport.
     * Guarantees that: Daily Sums == Weekly Total == Monthly Total.
     * Computes weighted average speed using total distance / total moving duration.
     */
    fun reconcilePeriodReport(
        periodType: PeriodType,
        dailySummaries: List<DailySummary>,
        deviceId: Long,
        deviceName: String,
        fromUtc: String,
        toUtc: String,
        localTimeZone: TimeZone = TimeZone.getDefault()
    ): PeriodReport {
        if (dailySummaries.isEmpty()) {
            return PeriodReport(
                periodType = periodType,
                deviceId = deviceId,
                deviceName = deviceName,
                fromUtc = fromUtc,
                toUtc = toUtc,
                localDateRangeLabel = formatLocalDisplayRange(fromUtc, toUtc, localTimeZone),
                dailyBreakdown = emptyList()
            )
        }

        var totalDistanceMeters = 0.0
        var totalMovingDurationMs = 0L
        var totalIdleDurationMs = 0L
        var totalStopDurationMs = 0L
        var maxSpeedKnots = 0.0
        var totalFuelLiters = 0.0
        var totalEngineHoursMs = 0L

        for (summary in dailySummaries) {
            totalDistanceMeters += summary.totalDistanceMeters
            totalMovingDurationMs += summary.movingDurationMs
            totalIdleDurationMs += summary.idleDurationMs
            totalStopDurationMs += summary.stopDurationMs
            if (summary.maxSpeedKnots > maxSpeedKnots) {
                maxSpeedKnots = summary.maxSpeedKnots
            }
            totalFuelLiters += summary.spentFuelLiters
            totalEngineHoursMs += summary.engineHoursMs
        }

        // Weighted Average Speed = Total Distance / Total Moving Duration
        val weightedAverageSpeedKnots = calculateWeightedAverageSpeedKnots(
            totalDistanceMeters = totalDistanceMeters,
            totalMovingDurationMs = totalMovingDurationMs
        )

        return PeriodReport(
            periodType = periodType,
            deviceId = deviceId,
            deviceName = deviceName,
            fromUtc = fromUtc,
            toUtc = toUtc,
            localDateRangeLabel = formatLocalDisplayRange(fromUtc, toUtc, localTimeZone),
            dailyBreakdown = dailySummaries,
            totalDistanceMeters = totalDistanceMeters,
            totalMovingDurationMs = totalMovingDurationMs,
            totalIdleDurationMs = totalIdleDurationMs,
            totalStopDurationMs = totalStopDurationMs,
            maxSpeedKnots = maxSpeedKnots,
            weightedAverageSpeedKnots = weightedAverageSpeedKnots,
            totalFuelLiters = totalFuelLiters,
            totalEngineHoursMs = totalEngineHoursMs
        )
    }

    fun calculateWeightedAverageSpeedKnots(
        totalDistanceMeters: Double,
        totalMovingDurationMs: Long
    ): Double {
        if (totalMovingDurationMs <= 0 || totalDistanceMeters <= 0.0) return 0.0
        val movingDurationHours = totalMovingDurationMs / 3600000.0
        val distanceKm = totalDistanceMeters / 1000.0
        val avgSpeedKmh = distanceKm / movingDurationHours
        return avgSpeedKmh / TelemetrySanitizerService.KNOTS_TO_KMH
    }

    // ==========================================
    // 3. ADVANCED TELEMATICS REPORT GENERATORS
    // ==========================================

    /**
     * Generates a speeding & violation report from a sequence of Position fixes.
     * Identifies contiguous segments exceeding [speedLimitKmh].
     */
    fun generateSpeedingViolationReport(
        positions: List<Position>,
        speedLimitKmh: Double,
        deviceId: Long,
        deviceName: String
    ): SpeedingViolationReport {
        val sanitized = TelemetrySanitizerService.sanitizeRoute(positions)
        if (sanitized.isEmpty() || speedLimitKmh <= 0.0) {
            return SpeedingViolationReport(
                deviceId = deviceId,
                deviceName = deviceName,
                speedLimitThresholdKmh = speedLimitKmh
            )
        }

        val violations = mutableListOf<SpeedViolationEvent>()
        var inViolation = false
        var currentViolationStartPos: Position? = null
        var maxSpeedInViolation = 0.0
        var totalOverLimitDurationMs = 0L
        var globalMaxSpeed = 0.0

        for (i in sanitized.indices) {
            val pos = sanitized[i]
            val speedKmh = pos.speedKmh
            if (speedKmh > globalMaxSpeed) globalMaxSpeed = speedKmh

            if (speedKmh > speedLimitKmh) {
                if (!inViolation) {
                    inViolation = true
                    currentViolationStartPos = pos
                    maxSpeedInViolation = speedKmh
                } else {
                    if (speedKmh > maxSpeedInViolation) {
                        maxSpeedInViolation = speedKmh
                    }
                }
            } else {
                if (inViolation && currentViolationStartPos != null) {
                    val startPos = currentViolationStartPos
                    val startTimeStr = startPos.deviceTime ?: formatUtcIso8601(Date())
                    val durationMs = parseUtcTimestamp(pos.deviceTime) - parseUtcTimestamp(startPos.deviceTime)
                    val validDurationMs = if (durationMs > 0) durationMs else 10_000L // Min 10s default

                    totalOverLimitDurationMs += validDurationMs
                    violations.add(
                        SpeedViolationEvent(
                            deviceId = deviceId,
                            deviceName = deviceName,
                            timestamp = startTimeStr,
                            durationMs = validDurationMs,
                            topSpeedKmh = maxSpeedInViolation,
                            speedLimitKmh = speedLimitKmh,
                            latitude = startPos.latitude,
                            longitude = startPos.longitude,
                            address = startPos.address
                        )
                    )
                    inViolation = false
                    currentViolationStartPos = null
                    maxSpeedInViolation = 0.0
                }
            }
        }

        // Close ongoing violation at the end of the window
        if (inViolation && currentViolationStartPos != null) {
            val startPos = currentViolationStartPos
            val startTimeStr = startPos.deviceTime ?: formatUtcIso8601(Date())
            val lastPos = sanitized.last()
            val durationMs = parseUtcTimestamp(lastPos.deviceTime) - parseUtcTimestamp(startPos.deviceTime)
            val validDurationMs = if (durationMs > 0) durationMs else 15_000L
            totalOverLimitDurationMs += validDurationMs
            violations.add(
                SpeedViolationEvent(
                    deviceId = deviceId,
                    deviceName = deviceName,
                    timestamp = startTimeStr,
                    durationMs = validDurationMs,
                    topSpeedKmh = maxSpeedInViolation,
                    speedLimitKmh = speedLimitKmh,
                    latitude = startPos.latitude,
                    longitude = startPos.longitude,
                    address = startPos.address
                )
            )
        }

        return SpeedingViolationReport(
            deviceId = deviceId,
            deviceName = deviceName,
            speedLimitThresholdKmh = speedLimitKmh,
            violationCount = violations.size,
            totalDurationOverLimitMs = totalOverLimitDurationMs,
            topRecordedSpeedKmh = globalMaxSpeed,
            violations = violations
        )
    }

    /**
     * Calculates time spent inside/outside specific geofences by matching
     * geofenceEnter and geofenceExit events.
     */
    fun generateGeofenceReport(
        events: List<Event>,
        geofenceId: Long,
        geofenceName: String,
        deviceId: Long,
        deviceName: String,
        periodStartUtc: String,
        periodEndUtc: String
    ): GeofenceReport {
        val periodStartMs = parseUtcIso8601(periodStartUtc)?.time ?: System.currentTimeMillis()
        val periodEndMs = parseUtcIso8601(periodEndUtc)?.time ?: System.currentTimeMillis()
        val totalPeriodDurationMs = maxOf(0L, periodEndMs - periodStartMs)

        // Filter events for this geofence & device
        val geofenceEvents = events.filter {
            (it.geofenceId == geofenceId || it.geofenceId == 0L) &&
            (it.deviceId == deviceId || deviceId == 0L) &&
            (it.type.equals("geofenceEnter", ignoreCase = true) || it.type.equals("geofenceExit", ignoreCase = true))
        }.sortedBy { parseUtcTimestamp(it.eventTime) }

        val dwellRecords = mutableListOf<GeofenceDwellRecord>()
        var lastEnterEvent: Event? = null
        var totalDwellMs = 0L
        var enterCount = 0
        var exitCount = 0

        for (event in geofenceEvents) {
            val eventType = event.type.lowercase(Locale.US)
            val eventTimeMs = parseUtcTimestamp(event.eventTime)

            if (eventType == "geofenceenter") {
                enterCount++
                lastEnterEvent = event
            } else if (eventType == "geofenceexit") {
                exitCount++
                if (lastEnterEvent != null) {
                    val enterTimeMs = parseUtcTimestamp(lastEnterEvent.eventTime)
                    val dwellMs = maxOf(0L, eventTimeMs - enterTimeMs)
                    totalDwellMs += dwellMs
                    dwellRecords.add(
                        GeofenceDwellRecord(
                            geofenceId = geofenceId,
                            geofenceName = geofenceName,
                            deviceId = deviceId,
                            deviceName = deviceName,
                            entryTime = lastEnterEvent.eventTime ?: formatUtcIso8601(Date(enterTimeMs)),
                            exitTime = event.eventTime,
                            dwellDurationMs = dwellMs,
                            isCurrentlyInside = false
                        )
                    )
                    lastEnterEvent = null
                }
            }
        }

        // Check if currently inside (unclosed entry event)
        if (lastEnterEvent != null) {
            val enterTimeMs = parseUtcTimestamp(lastEnterEvent.eventTime)
            val dwellMs = maxOf(0L, periodEndMs - enterTimeMs)
            totalDwellMs += dwellMs
            dwellRecords.add(
                GeofenceDwellRecord(
                    geofenceId = geofenceId,
                    geofenceName = geofenceName,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    entryTime = lastEnterEvent.eventTime ?: formatUtcIso8601(Date(enterTimeMs)),
                    exitTime = null,
                    dwellDurationMs = dwellMs,
                    isCurrentlyInside = true
                )
            )
        }

        val outsideDurationMs = maxOf(0L, totalPeriodDurationMs - totalDwellMs)

        return GeofenceReport(
            deviceId = deviceId,
            deviceName = deviceName,
            geofenceId = geofenceId,
            geofenceName = geofenceName,
            totalDwellDurationMs = totalDwellMs,
            totalOutsideDurationMs = outsideDurationMs,
            entryCount = enterCount,
            exitCount = exitCount,
            history = dwellRecords
        )
    }

    private fun parseUtcTimestamp(isoTimestamp: String?): Long {
        if (isoTimestamp.isNullOrEmpty()) return System.currentTimeMillis()
        return try {
            getUtcIso8601Formatter().parse(isoTimestamp)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
