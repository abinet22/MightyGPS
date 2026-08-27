package com.example.data.model

import com.squareup.moshi.JsonClass

data class Device(
    val id: Long,
    val name: String,
    val uniqueId: String,
    val status: String, // "online", "offline", "unknown"
    val lastUpdate: String? = null,
    val positionId: Long = 0,
    val phone: String? = null,
    val model: String? = null,
    val contact: String? = null,
    val category: String? = null, // "car", "truck", "person", etc.
    val attributes: Map<String, Any> = emptyMap()
)

data class Position(
    val id: Long = 0L,
    val deviceId: Long = 0L,
    val protocol: String? = null,
    val deviceTime: String? = null,
    val fixTime: String? = null,
    val valid: Boolean = true,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val speed: Double = 0.0, // speed in knots
    val course: Double = 0.0,
    val address: String? = null,
    val accuracy: Double = 0.0,
    val attributes: Map<String, Any> = emptyMap()
) {
    // Speed conversion to km/h or mph
    val speedKmh: Double get() = speed * 1.852
    val speedMph: Double get() = speed * 1.15078
}

data class User(
    val id: Long,
    val name: String,
    val email: String,
    val phone: String? = null,
    val administrator: Boolean = false,
    val expirationTime: String? = null,
    val deviceLimit: Int = 0,
    val userLimit: Int = 0,
    val attributes: Map<String, Any> = emptyMap()
)

data class Event(
    val id: Long,
    val type: String, // "deviceOnline", "deviceOffline", "deviceMoving", "deviceStopped", "alarm", etc.
    val eventTime: String,
    val deviceId: Long,
    val positionId: Long = 0,
    val geofenceId: Long = 0,
    val attributes: Map<String, Any> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class SocketUpdate(
    val devices: List<Device>? = null,
    val positions: List<Position>? = null,
    val events: List<Event>? = null
)

@JsonClass(generateAdapter = true)
data class DeviceCommand(
    val deviceId: Long,
    val type: String,
    val description: String? = null,
    val attributes: Map<String, String> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class TraccarGeofence(
    val id: Long,
    val name: String,
    val description: String? = null,
    val area: String, // E.g., "CIRCLE (latitude longitude, radius_meters)" or "POLYGON ((lng lat, lng lat,...))"
    val calendarId: Long = 0,
    val attributes: Map<String, Any> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class TraccarPermission(
    val deviceId: Long? = null,
    val geofenceId: Long? = null,
    val userId: Long? = null,
    val groupId: Long? = null
)

@JsonClass(generateAdapter = true)
data class ReportSummary(
    val deviceId: Long = 0,
    val deviceName: String = "",
    val maxSpeed: Double = 0.0, // knots
    val averageSpeed: Double = 0.0, // knots
    val distance: Double = 0.0, // meters
    val spentFuel: Double = 0.0, // liters
    val engineHours: Long = 0 // milliseconds
) {
    val distanceKm: Double get() = distance / 1000.0
    val maxSpeedKmh: Double get() = maxSpeed * 1.852
    val averageSpeedKmh: Double get() = averageSpeed * 1.852
    val engineHoursFormatted: String
        get() {
            val totalSeconds = engineHours / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            return "${hours}h ${minutes}m"
        }
}

@JsonClass(generateAdapter = true)
data class ReportTrip(
    val deviceId: Long = 0,
    val deviceName: String = "",
    val distance: Double = 0.0, // meters
    val averageSpeed: Double = 0.0, // knots
    val maxSpeed: Double = 0.0, // knots
    val spentFuel: Double = 0.0,
    val startPositionId: Long = 0,
    val endPositionId: Long = 0,
    val startTime: String? = null,
    val startAddress: String? = null,
    val startLat: Double = 0.0,
    val startLon: Double = 0.0,
    val endTime: String? = null,
    val endAddress: String? = null,
    val endLat: Double = 0.0,
    val endLon: Double = 0.0,
    val duration: Long = 0, // milliseconds
    val driverUniqueId: String? = null,
    val driverName: String? = null
) {
    val distanceKm: Double get() = distance / 1000.0
    val averageSpeedKmh: Double get() = averageSpeed * 1.852
    val maxSpeedKmh: Double get() = maxSpeed * 1.852
    val durationFormatted: String
        get() {
            val totalSeconds = duration / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
}

@JsonClass(generateAdapter = true)
data class ReportStop(
    val deviceId: Long = 0,
    val deviceName: String = "",
    val duration: Long = 0, // milliseconds
    val startTime: String? = null,
    val endTime: String? = null,
    val positionId: Long = 0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String? = null,
    val spentFuel: Double = 0.0,
    val engineHours: Long = 0,
    val attributes: Map<String, Any> = emptyMap()
) {
    val wasIdling: Boolean get() = (attributes["ignition"] as? Boolean) == true && duration > 0

    val durationFormatted: String
        get() {
            val totalSeconds = duration / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
}

@JsonClass(generateAdapter = true)
data class Driver(
    val id: Long = 0,
    val name: String = "",
    val uniqueId: String = "",
    val attributes: Map<String, Any> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class Group(
    val id: Long = 0,
    val name: String = "",
    val groupId: Long = 0,
    val attributes: Map<String, Any> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class Server(
    val id: Long = 0,
    val registration: Boolean = false,
    val readonly: Boolean = false,
    val deviceReadonly: Boolean = false,
    val map: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val zoom: Int = 0,
    val version: String? = null
)

data class CommandResult(
    val success: Boolean,
    val queued: Boolean,
    val code: Int,
    val error: String? = null
)


