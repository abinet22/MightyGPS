package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DailySummary(
    val date: String, // e.g. "2026-08-27"
    val deviceId: Long = 0,
    val deviceName: String = "",
    val totalDistanceMeters: Double = 0.0,
    val movingDurationMs: Long = 0,
    val idleDurationMs: Long = 0,
    val stopDurationMs: Long = 0,
    val maxSpeedKnots: Double = 0.0,
    val averageSpeedKnots: Double = 0.0,
    val spentFuelLiters: Double = 0.0,
    val engineHoursMs: Long = 0
) {
    val totalDistanceKm: Double get() = totalDistanceMeters / 1000.0
    val totalDistanceMiles: Double get() = totalDistanceMeters / 1609.344
    val maxSpeedKmh: Double get() = maxSpeedKnots * 1.852
    val maxSpeedMph: Double get() = maxSpeedKnots * 1.15078
    val calculatedAvgSpeedKmh: Double
        get() = if (movingDurationMs > 0) {
            (totalDistanceMeters / 1000.0) / (movingDurationMs / 3600000.0)
        } else 0.0
    val calculatedAvgSpeedKnots: Double
        get() = calculatedAvgSpeedKmh / 1.852
}

enum class PeriodType {
    DAILY,
    WEEKLY,
    MONTHLY,
    CUSTOM
}

@JsonClass(generateAdapter = true)
data class PeriodReport(
    val periodType: PeriodType,
    val deviceId: Long = 0,
    val deviceName: String = "",
    val fromUtc: String = "",
    val toUtc: String = "",
    val localDateRangeLabel: String = "",
    val dailyBreakdown: List<DailySummary> = emptyList(),
    val totalDistanceMeters: Double = 0.0,
    val totalMovingDurationMs: Long = 0,
    val totalIdleDurationMs: Long = 0,
    val totalStopDurationMs: Long = 0,
    val maxSpeedKnots: Double = 0.0,
    val weightedAverageSpeedKnots: Double = 0.0,
    val totalFuelLiters: Double = 0.0,
    val totalEngineHoursMs: Long = 0
) {
    val totalDistanceKm: Double get() = totalDistanceMeters / 1000.0
    val totalDistanceMiles: Double get() = totalDistanceMeters / 1609.344
    val maxSpeedKmh: Double get() = maxSpeedKnots * 1.852
    val maxSpeedMph: Double get() = maxSpeedKnots * 1.15078
    val weightedAverageSpeedKmh: Double get() = weightedAverageSpeedKnots * 1.852
    val weightedAverageSpeedMph: Double get() = weightedAverageSpeedKnots * 1.15078
}

@JsonClass(generateAdapter = true)
data class SpeedViolationEvent(
    val deviceId: Long = 0,
    val deviceName: String = "",
    val timestamp: String = "",
    val durationMs: Long = 0,
    val topSpeedKmh: Double = 0.0,
    val speedLimitKmh: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String? = null
)

@JsonClass(generateAdapter = true)
data class SpeedingViolationReport(
    val deviceId: Long = 0,
    val deviceName: String = "",
    val speedLimitThresholdKmh: Double = 0.0,
    val violationCount: Int = 0,
    val totalDurationOverLimitMs: Long = 0,
    val topRecordedSpeedKmh: Double = 0.0,
    val violations: List<SpeedViolationEvent> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GeofenceDwellRecord(
    val geofenceId: Long = 0,
    val geofenceName: String = "",
    val deviceId: Long = 0,
    val deviceName: String = "",
    val entryTime: String = "",
    val exitTime: String? = null,
    val dwellDurationMs: Long = 0,
    val isCurrentlyInside: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GeofenceReport(
    val deviceId: Long = 0,
    val deviceName: String = "",
    val geofenceId: Long = 0,
    val geofenceName: String = "",
    val totalDwellDurationMs: Long = 0,
    val totalOutsideDurationMs: Long = 0,
    val entryCount: Int = 0,
    val exitCount: Int = 0,
    val history: List<GeofenceDwellRecord> = emptyList()
)
