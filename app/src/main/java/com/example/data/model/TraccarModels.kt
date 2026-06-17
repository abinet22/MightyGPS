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
    val id: Long,
    val deviceId: Long,
    val protocol: String? = null,
    val deviceTime: String?,
    val fixTime: String?,
    val valid: Boolean = true,
    val latitude: Double,
    val longitude: Double,
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

