package com.example

import com.example.data.model.DailySummary
import com.example.data.model.Event
import com.example.data.model.PeriodType
import com.example.data.model.Position
import com.example.util.ReportReconciliationManager
import com.example.util.TelemetrySanitizerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.TimeZone

class TelemetryAndReconciliationTest {

    // =========================================================================
    // 1. RECONCILIATION & MATHEMATICAL CONSISTENCY (0.01% TOLERANCE)
    // =========================================================================

    @Test
    fun dailySums_matchWeeklyAndMonthlyTotals_withinTolerance() {
        // Create 7 daily summaries for a week
        val dailySummaries = listOf(
            DailySummary("2026-08-21", 1L, "Truck 01", 125_450.0, 3_600_000L * 2, 1_800_000L, 0L, 45.0, 33.87, 11.5, 9_000_000L),
            DailySummary("2026-08-22", 1L, "Truck 01", 89_200.0, 3_600_000L * 1, 900_000L, 0L, 42.0, 48.16, 8.2, 4_500_000L),
            DailySummary("2026-08-23", 1L, "Truck 01", 210_000.0, 3_600_000L * 3, 3_600_000L, 0L, 50.0, 37.80, 19.3, 14_400_000L),
            DailySummary("2026-08-24", 1L, "Truck 01", 45_000.0, 1_800_000L, 600_000L, 0L, 38.0, 48.60, 4.1, 2_400_000L),
            DailySummary("2026-08-25", 1L, "Truck 01", 175_300.0, 3_600_000L * 2, 1_200_000L, 0L, 48.0, 47.33, 16.1, 8_400_000L),
            DailySummary("2026-08-26", 1L, "Truck 01", 130_000.0, 3_600_000L * 2, 1_800_000L, 0L, 44.0, 35.10, 12.0, 9_000_000L),
            DailySummary("2026-08-27", 1L, "Truck 01", 95_500.0, 3_600_000L * 1, 900_000L, 0L, 40.0, 51.57, 8.8, 4_500_000L)
        )

        val weeklyReport = ReportReconciliationManager.reconcilePeriodReport(
            periodType = PeriodType.WEEKLY,
            dailySummaries = dailySummaries,
            deviceId = 1L,
            deviceName = "Truck 01",
            fromUtc = "2026-08-21T00:00:00Z",
            toUtc = "2026-08-27T23:59:59Z"
        )

        val manualSumDistance = dailySummaries.sumOf { it.totalDistanceMeters }
        val manualSumMovingMs = dailySummaries.sumOf { it.movingDurationMs }
        val manualSumIdleMs = dailySummaries.sumOf { it.idleDurationMs }
        val manualSumFuel = dailySummaries.sumOf { it.spentFuelLiters }
        val manualSumEngineHours = dailySummaries.sumOf { it.engineHoursMs }
        val manualMaxSpeed = dailySummaries.maxOf { it.maxSpeedKnots }

        // Verify 0.01% (0.0001) tolerance
        assertEquals(manualSumDistance, weeklyReport.totalDistanceMeters, manualSumDistance * 0.0001)
        assertEquals(manualSumMovingMs, weeklyReport.totalMovingDurationMs)
        assertEquals(manualSumIdleMs, weeklyReport.totalIdleDurationMs)
        assertEquals(manualSumFuel, weeklyReport.totalFuelLiters, manualSumFuel * 0.0001)
        assertEquals(manualSumEngineHours, weeklyReport.totalEngineHoursMs)
        assertEquals(manualMaxSpeed, weeklyReport.maxSpeedKnots, 0.001)
    }

    @Test
    fun weightedAverageSpeed_calculatesAccurately_usingTotalMovingDuration() {
        // Day 1: 100 km in 1 hour = 100 km/h
        // Day 2: 100 km in 2 hours = 50 km/h
        // Naive average of daily speeds = (100 + 50) / 2 = 75 km/h (INCORRECT)
        // Weighted average = (100 + 100) / (1 + 2) = 200 km / 3 h = 66.67 km/h (CORRECT)
        val dailySummaries = listOf(
            DailySummary(
                date = "2026-08-01",
                totalDistanceMeters = 100_000.0,
                movingDurationMs = 3_600_000L, // 1 hour
                maxSpeedKnots = 60.0
            ),
            DailySummary(
                date = "2026-08-02",
                totalDistanceMeters = 100_000.0,
                movingDurationMs = 7_200_000L, // 2 hours
                maxSpeedKnots = 40.0
            )
        )

        val report = ReportReconciliationManager.reconcilePeriodReport(
            periodType = PeriodType.CUSTOM,
            dailySummaries = dailySummaries,
            deviceId = 1L,
            deviceName = "Test Vehicle",
            fromUtc = "2026-08-01T00:00:00Z",
            toUtc = "2026-08-02T23:59:59Z"
        )

        val expectedWeightedAvgKmh = 200.0 / 3.0 // 66.6666...
        val actualWeightedAvgKmh = report.weightedAverageSpeedKmh

        assertEquals(expectedWeightedAvgKmh, actualWeightedAvgKmh, 0.01)
    }

    // =========================================================================
    // 2. UNIT NORMALIZATION LAYER
    // =========================================================================

    @Test
    fun unitNormalization_convertsMetricsAccurately() {
        // Distance
        val meters = 15_430.0
        val km = TelemetrySanitizerService.metersToKilometers(meters)
        val miles = TelemetrySanitizerService.metersToMiles(meters)

        assertEquals(15.43, km, 0.001)
        assertEquals(9.5877, miles, 0.01)
        assertEquals("15.43 km", TelemetrySanitizerService.formatDistance(meters, isMetric = true))
        assertEquals("9.59 mi", TelemetrySanitizerService.formatDistance(meters, isMetric = false))

        // Speed
        val knots = 30.0
        val kmh = TelemetrySanitizerService.knotsToKmh(knots)
        val mph = TelemetrySanitizerService.knotsToMph(knots)

        assertEquals(55.56, kmh, 0.01)
        assertEquals(34.523, mph, 0.01)
        assertEquals("55.6 km/h", TelemetrySanitizerService.formatSpeedFromKnots(knots, isMetric = true))
        assertEquals("34.5 mph", TelemetrySanitizerService.formatSpeedFromKnots(knots, isMetric = false))

        // Duration
        val durationMs = 3_723_000L // 1h 2m 3s
        assertEquals("01:02:03", TelemetrySanitizerService.formatDurationHms(durationMs))
        assertEquals("1h 2m", TelemetrySanitizerService.formatDurationCompact(durationMs))
    }

    // =========================================================================
    // 3. ROUTE INTERPOLATION & STATIC DRIFT SCRUBBING
    // =========================================================================

    @Test
    fun telemetrySanitizer_filtersStaticDriftAndInvalidCoordinates() {
        val rawPositions = listOf(
            Position(id = 1, latitude = 37.7749, longitude = -122.4194, speed = 0.0, valid = true, accuracy = 5.0),
            Position(id = 2, latitude = 37.774901, longitude = -122.419402, speed = 0.1, valid = true, accuracy = 5.0), // static jitter (<1m)
            Position(id = 3, latitude = 37.774902, longitude = -122.419401, speed = 0.2, valid = true, accuracy = 5.0), // static jitter (<1m)
            Position(id = 4, latitude = 0.0, longitude = 0.0, speed = 0.0, valid = false), // corrupt 0,0
            Position(id = 5, latitude = 37.7800, longitude = -122.4100, speed = 25.0, valid = true, accuracy = 5.0), // real move
            Position(id = 6, latitude = 37.7850, longitude = -122.4000, speed = 30.0, valid = true, accuracy = 5.0), // real move
            Position(id = 7, latitude = 37.785001, longitude = -122.400001, speed = 0.0, valid = true, accuracy = 5.0) // final stop
        )

        val sanitized = TelemetrySanitizerService.sanitizeRoute(rawPositions)

        // Invalid 0,0 position should be excluded
        assertFalse(sanitized.any { it.latitude == 0.0 && it.longitude == 0.0 })
        // Static jitter points 2 and 3 should be scrubbed
        assertEquals(4, sanitized.size) // Point 1 (start), Point 5, Point 6, Point 7 (end)
    }

    // =========================================================================
    // 4. ADVANCED TELEMATICS REPORT GENERATORS
    // =========================================================================

    @Test
    fun speedingViolationReport_detectsBreachesCorrectly() {
        val positions = listOf(
            Position(id = 1, latitude = 37.7749, longitude = -122.4194, speed = 30.0, valid = true, deviceTime = "2026-08-27T10:00:00Z"), // 55.56 km/h
            Position(id = 2, latitude = 37.7760, longitude = -122.4180, speed = 50.0, valid = true, deviceTime = "2026-08-27T10:01:00Z"), // 92.6 km/h -> OVERSPEED
            Position(id = 3, latitude = 37.7770, longitude = -122.4170, speed = 55.0, valid = true, deviceTime = "2026-08-27T10:02:00Z"), // 101.86 km/h -> OVERSPEED
            Position(id = 4, latitude = 37.7780, longitude = -122.4160, speed = 35.0, valid = true, deviceTime = "2026-08-27T10:03:00Z")  // 64.82 km/h -> NORMAL
        )

        val report = ReportReconciliationManager.generateSpeedingViolationReport(
            positions = positions,
            speedLimitKmh = 80.0,
            deviceId = 1L,
            deviceName = "Express Cargo"
        )

        assertEquals(1, report.violationCount)
        assertTrue(report.topRecordedSpeedKmh > 100.0)
        assertEquals(1, report.violations.size)
        assertEquals(101.86, report.violations[0].topSpeedKmh, 0.5)
    }

    @Test
    fun geofenceAnalyticsReport_calculatesDwellDurationAccurately() {
        val events = listOf(
            Event(id = 1, type = "geofenceEnter", eventTime = "2026-08-27T08:00:00Z", deviceId = 1L, geofenceId = 10L),
            Event(id = 2, type = "geofenceExit", eventTime = "2026-08-27T09:30:00Z", deviceId = 1L, geofenceId = 10L), // 1.5 hours = 5,400,000 ms
            Event(id = 3, type = "geofenceEnter", eventTime = "2026-08-27T14:00:00Z", deviceId = 1L, geofenceId = 10L),
            Event(id = 4, type = "geofenceExit", eventTime = "2026-08-27T15:00:00Z", deviceId = 1L, geofenceId = 10L)  // 1.0 hour = 3,600,000 ms
        )

        val report = ReportReconciliationManager.generateGeofenceReport(
            events = events,
            geofenceId = 10L,
            geofenceName = "Central Logistics Depot",
            deviceId = 1L,
            deviceName = "Truck 01",
            periodStartUtc = "2026-08-27T00:00:00Z",
            periodEndUtc = "2026-08-27T23:59:59Z"
        )

        val expectedDwellMs = (1.5 + 1.0) * 3600 * 1000L // 9,000,000 ms
        assertEquals(expectedDwellMs.toLong(), report.totalDwellDurationMs)
        assertEquals(2, report.entryCount)
        assertEquals(2, report.exitCount)
        assertEquals(2, report.history.size)
    }

    @Test
    fun timezoneBoundaryAlignment_projectsIso8601UtcCorrectly() {
        val (fromUtc, toUtc) = ReportReconciliationManager.calculateUtcRangeForPeriod(
            periodType = PeriodType.DAILY,
            referenceDate = Date(),
            localTimeZone = TimeZone.getTimeZone("UTC")
        )

        assertTrue(fromUtc.endsWith("00:00:00Z"))
        assertTrue(toUtc.endsWith("23:59:59Z"))
    }

    @Test
    fun telemetrySanitizer_formatsIso8601ToLocalTimezone() {
        val isoUtc = "2026-08-27T14:30:00Z"
        val utcZone = TimeZone.getTimeZone("UTC")
        val formattedUtc = TelemetrySanitizerService.formatIsoToLocalDisplay(
            isoString = isoUtc,
            pattern = "yyyy-MM-dd HH:mm",
            targetTimeZone = utcZone
        )
        assertEquals("2026-08-27 14:30", formattedUtc)

        val timeOnly = TelemetrySanitizerService.formatIsoToLocalTime(
            isoString = isoUtc,
            targetTimeZone = utcZone
        )
        assertEquals("14:30:00", timeOnly)
    }
}
