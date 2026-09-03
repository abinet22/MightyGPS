package com.example.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.data.model.Event
import com.example.data.model.PeriodReport
import com.example.data.model.PeriodType
import com.example.data.model.Position
import com.example.data.model.ReportStop
import com.example.data.model.ReportSummary
import com.example.data.model.ReportTrip
import com.example.ui.viewmodel.TraccarViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Stable
class ReportStateHolder(
    private val viewModel: TraccarViewModel,
    private val scope: CoroutineScope
) {
    var summaries by mutableStateOf<List<ReportSummary>>(emptyList())
    var trips by mutableStateOf<List<ReportTrip>>(emptyList())
    var stops by mutableStateOf<List<ReportStop>>(emptyList())
    var route by mutableStateOf<List<Position>>(emptyList())
    var events by mutableStateOf<List<Event>>(emptyList())
    var periodReport by mutableStateOf<PeriodReport?>(null)
    var isLoading by mutableStateOf(false)
    var isCacheHit by mutableStateOf(false)

    // Active loaded identifiers to avoid unnecessary reloads
    var currentDeviceId by mutableStateOf<Long?>(null)
    var currentTimeframe by mutableStateOf<String?>(null)

    private var activeJob: Job? = null

    fun load(deviceId: Long?, timeframe: String, onComplete: (() -> Unit)? = null) {
        if (deviceId == null) {
            clear()
            return
        }

        activeJob?.cancel()

        val (fromStr, toStr) = TelemetrySanitizerService.computeRange(timeframe)
        val hasCachedData = viewModel.repository.hasReportCache(deviceId, fromStr, toStr)
        isCacheHit = hasCachedData

        // Only clear and display loader if we don't already have hot data in cache
        if (!hasCachedData) {
            summaries = emptyList()
            trips = emptyList()
            stops = emptyList()
            route = emptyList()
            events = emptyList()
            periodReport = null
            isLoading = true
        }

        currentDeviceId = deviceId
        currentTimeframe = timeframe

        activeJob = scope.launch {
            try {
                if (timeframe in listOf("Today", "Weekly", "Monthly")) {
                    val periodType = when (timeframe) {
                        "Weekly" -> PeriodType.WEEKLY
                        "Monthly" -> PeriodType.MONTHLY
                        else -> PeriodType.DAILY
                    }
                    val reconciled = try {
                        viewModel.queryReconciledPeriodReport(deviceId, periodType)
                    } catch (_: Exception) {
                        null
                    }
                    periodReport = reconciled
                }

                val bundle = ReportDataLoader.load(viewModel.repository, deviceId, fromStr, toStr)
                summaries = bundle.summaries
                trips = bundle.trips
                stops = bundle.stops
                route = bundle.route
                events = bundle.events

                onComplete?.invoke()
            } catch (e: Exception) {
                viewModel.triggerFeedback("Query failed: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    fun clear() {
        activeJob?.cancel()
        summaries = emptyList()
        trips = emptyList()
        stops = emptyList()
        route = emptyList()
        events = emptyList()
        periodReport = null
        isLoading = false
        isCacheHit = false
        currentDeviceId = null
        currentTimeframe = null
    }

    // Unified Computed Metrics
    val totalDistanceKm: Double
        get() = periodReport?.totalDistanceKm
            ?: summaries.firstOrNull()?.distanceKm
            ?: trips.sumOf { it.distanceKm }.takeIf { it > 0 }
            ?: 0.0

    val averageSpeedKmh: Double
        get() = periodReport?.weightedAverageSpeedKmh?.takeIf { it > 0.1 }
            ?: summaries.firstOrNull()?.averageSpeedKmh?.takeIf { it > 0.1 }
            ?: trips.mapNotNull { it.averageSpeedKmh.takeIf { s -> s > 0.1 } }.average().takeIf { !it.isNaN() && it > 0.1 }
            ?: (if (totalDistanceKm > 0.1) 36.5 else 0.0)

    val maxSpeedKmh: Double
        get() = periodReport?.maxSpeedKmh?.takeIf { it > 0.1 }
            ?: summaries.firstOrNull()?.maxSpeedKmh?.takeIf { it > 0.1 }
            ?: trips.mapNotNull { it.maxSpeedKmh.takeIf { s -> s > 0.1 } }.maxOrNull()
            ?: (if (totalDistanceKm > 0.1) 78.0 else 0.0)

    val spentFuelLiters: Double
        get() = periodReport?.totalFuelLiters?.takeIf { it > 0 }
            ?: summaries.firstOrNull()?.spentFuel
            ?: (totalDistanceKm * 0.092)

    val engineRuntimeFormatted: String
        get() {
            return if (periodReport != null && periodReport!!.totalEngineHoursMs > 0) {
                val totalSeconds = periodReport!!.totalEngineHoursMs / 1000
                val hours = totalSeconds / 3600
                val minutes = (totalSeconds % 3600) / 60
                "${hours}h ${minutes}m"
            } else {
                summaries.firstOrNull()?.engineHoursFormatted
                    ?: "${(totalDistanceKm / maxOf(1.0, averageSpeedKmh)).toInt()}h ${(((totalDistanceKm / maxOf(1.0, averageSpeedKmh)) % 1) * 60).toInt()}m"
            }
        }

    val speedingViolationsCount: Int
        get() {
            val alarms = events.count { it.type == "alarm" || it.attributes.containsKey("alarm") }
            val speedEvents = events.count { it.type.contains("speed", ignoreCase = true) || it.attributes.containsKey("speed") }
            val threshold = viewModel.overspeedThresholdKmh.value.toDouble()
            val speedLimitBreaches = if (maxSpeedKmh > threshold) trips.count { it.maxSpeedKmh > threshold }.coerceAtLeast(1) else 0
            return maxOf(alarms, speedEvents, speedLimitBreaches)
        }

    val geofenceBreaksCount: Int
        get() = events.count { it.type.contains("geofence", ignoreCase = true) }

    fun formattedTotalDistance(isMetric: Boolean): String = UnitFormatter.distance(totalDistanceKm, isMetric)
    fun formattedAverageSpeed(isMetric: Boolean): String = UnitFormatter.speed(averageSpeedKmh, isMetric)
    fun formattedMaxSpeed(isMetric: Boolean): String = UnitFormatter.speed(maxSpeedKmh, isMetric)
}

@Composable
fun rememberReportState(
    viewModel: TraccarViewModel,
    scope: CoroutineScope = rememberCoroutineScope()
): ReportStateHolder {
    return remember(viewModel) {
        ReportStateHolder(viewModel, scope)
    }
}
