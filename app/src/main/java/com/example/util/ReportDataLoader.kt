package com.example.util

import com.example.data.model.Event
import com.example.data.model.Position
import com.example.data.model.ReportStop
import com.example.data.model.ReportSummary
import com.example.data.model.ReportTrip
import com.example.data.repo.TraccarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class ReportBundle(
    val trips: List<ReportTrip>,
    val stops: List<ReportStop>,
    val summaries: List<ReportSummary>,
    val events: List<Event>,
    val route: List<Position> = emptyList()
)

object ReportDataLoader {
    suspend fun load(
        repository: TraccarRepository,
        deviceId: Long?,
        from: String,
        to: String
    ): ReportBundle = coroutineScope {
        val tripsDeferred     = async(Dispatchers.IO) { repository.getTripsReport(deviceId, from, to) }
        val stopsDeferred     = async(Dispatchers.IO) { repository.getStopsReport(deviceId, from, to) }
        val summariesDeferred = async(Dispatchers.IO) { repository.getSummaryReport(deviceId, from, to) }
        val eventsDeferred    = async(Dispatchers.IO) { repository.getEventsReport(deviceId, from, to) }
        ReportBundle(
            trips = tripsDeferred.await(),
            stops = stopsDeferred.await(),
            summaries = summariesDeferred.await(),
            events = eventsDeferred.await()
        )
    }
}
