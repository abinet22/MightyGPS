package com.example.data.api

import com.example.data.model.Device
import com.example.data.model.Position
import com.example.data.model.User
import com.example.data.model.Event
import com.squareup.moshi.FromJson

class DeviceAdapter {
    @FromJson
    fun fromJson(map: Map<String, Any?>): Device {
        val id = (map["id"] as? Number)?.toLong() ?: 0L
        val name = map["name"] as? String ?: "Unknown"
        val uniqueId = map["uniqueId"] as? String ?: ""
        val status = map["status"] as? String ?: "offline"
        val lastUpdate = map["lastUpdate"] as? String
        val positionId = (map["positionId"] as? Number)?.toLong() ?: 0L
        val phone = map["phone"] as? String
        val model = map["model"] as? String
        val contact = map["contact"] as? String
        val category = map["category"] as? String
        val attributes = map["attributes"] as? Map<String, Any> ?: emptyMap()
        
        return Device(id, name, uniqueId, status, lastUpdate, positionId, phone, model, contact, category, attributes)
    }
}

class PositionAdapter {
    @FromJson
    fun fromJson(map: Map<String, Any?>): Position {
        val id = (map["id"] as? Number)?.toLong() ?: 0L
        val deviceId = (map["deviceId"] as? Number)?.toLong() ?: 0L
        val protocol = map["protocol"] as? String
        val deviceTime = map["deviceTime"] as? String
        val fixTime = map["fixTime"] as? String
        val valid = map["valid"] as? Boolean ?: true
        val latitude = (map["latitude"] as? Number)?.toDouble() ?: 0.0
        val longitude = (map["longitude"] as? Number)?.toDouble() ?: 0.0
        val altitude = (map["altitude"] as? Number)?.toDouble() ?: 0.0
        val speed = (map["speed"] as? Number)?.toDouble() ?: 0.0
        val course = (map["course"] as? Number)?.toDouble() ?: 0.0
        val address = map["address"] as? String
        val accuracy = (map["accuracy"] as? Number)?.toDouble() ?: 0.0
        val attributes = map["attributes"] as? Map<String, Any> ?: emptyMap()
        
        return Position(id, deviceId, protocol, deviceTime, fixTime, valid, latitude, longitude, altitude, speed, course, address, accuracy, attributes)
    }
}

class UserAdapter {
    @FromJson
    fun fromJson(map: Map<String, Any?>): User {
        val id = (map["id"] as? Number)?.toLong() ?: 0L
        val name = map["name"] as? String ?: "Operator"
        val email = map["email"] as? String ?: ""
        val phone = map["phone"] as? String
        val administrator = map["administrator"] as? Boolean ?: false
        val expirationTime = map["expirationTime"] as? String
        val deviceLimit = (map["deviceLimit"] as? Number)?.toInt() ?: 0
        val userLimit = (map["userLimit"] as? Number)?.toInt() ?: 0
        val attributes = map["attributes"] as? Map<String, Any> ?: emptyMap()
        
        return User(id, name, email, phone, administrator, expirationTime, deviceLimit, userLimit, attributes)
    }
}

class EventAdapter {
    @FromJson
    fun fromJson(map: Map<String, Any?>): Event {
        val id = (map["id"] as? Number)?.toLong() ?: 0L
        val type = map["type"] as? String ?: "report"
        val eventTime = map["eventTime"] as? String ?: ""
        val deviceId = (map["deviceId"] as? Number)?.toLong() ?: 0L
        val positionId = (map["positionId"] as? Number)?.toLong() ?: 0L
        val geofenceId = (map["geofenceId"] as? Number)?.toLong() ?: 0L
        val attributes = map["attributes"] as? Map<String, Any> ?: emptyMap()
        
        return Event(id, type, eventTime, deviceId, positionId, geofenceId, attributes)
    }
}

class ReportSummaryAdapter {
    @FromJson
    fun fromJson(map: Map<String, Any?>): com.example.data.model.ReportSummary {
        val deviceId = (map["deviceId"] as? Number)?.toLong() ?: 0L
        val deviceName = map["deviceName"] as? String ?: ""
        val maxSpeed = (map["maxSpeed"] as? Number)?.toDouble() ?: 0.0
        val averageSpeed = (map["averageSpeed"] as? Number)?.toDouble() ?: 0.0
        val distance = (map["distance"] as? Number)?.toDouble() ?: 0.0
        val spentFuel = (map["spentFuel"] as? Number)?.toDouble() ?: 0.0
        val engineHours = (map["engineHours"] as? Number)?.toLong() ?: 0L
        return com.example.data.model.ReportSummary(deviceId, deviceName, maxSpeed, averageSpeed, distance, spentFuel, engineHours)
    }
}

class ReportTripAdapter {
    @FromJson
    fun fromJson(map: Map<String, Any?>): com.example.data.model.ReportTrip {
        val deviceId = (map["deviceId"] as? Number)?.toLong() ?: 0L
        val deviceName = map["deviceName"] as? String ?: ""
        val distance = (map["distance"] as? Number)?.toDouble() ?: 0.0
        val averageSpeed = (map["averageSpeed"] as? Number)?.toDouble() ?: 0.0
        val maxSpeed = (map["maxSpeed"] as? Number)?.toDouble() ?: 0.0
        val spentFuel = (map["spentFuel"] as? Number)?.toDouble() ?: 0.0
        val startPositionId = (map["startPositionId"] as? Number)?.toLong() ?: 0L
        val endPositionId = (map["endPositionId"] as? Number)?.toLong() ?: 0L
        val startTime = map["startTime"] as? String
        val startAddress = map["startAddress"] as? String
        val startLat = (map["startLat"] as? Number)?.toDouble() ?: 0.0
        val startLon = (map["startLon"] as? Number)?.toDouble() ?: 0.0
        val endTime = map["endTime"] as? String
        val endAddress = map["endAddress"] as? String
        val endLat = (map["endLat"] as? Number)?.toDouble() ?: 0.0
        val endLon = (map["endLon"] as? Number)?.toDouble() ?: 0.0
        val duration = (map["duration"] as? Number)?.toLong() ?: 0L
        val driverUniqueId = map["driverUniqueId"] as? String
        val driverName = map["driverName"] as? String
        return com.example.data.model.ReportTrip(
            deviceId, deviceName, distance, averageSpeed, maxSpeed, spentFuel,
            startPositionId, endPositionId, startTime, startAddress, startLat, startLon,
            endTime, endAddress, endLat, endLon, duration, driverUniqueId, driverName
        )
    }
}

class ReportStopAdapter {
    @FromJson
    fun fromJson(map: Map<String, Any?>): com.example.data.model.ReportStop {
        val deviceId = (map["deviceId"] as? Number)?.toLong() ?: 0L
        val deviceName = map["deviceName"] as? String ?: ""
        val duration = (map["duration"] as? Number)?.toLong() ?: 0L
        val startTime = map["startTime"] as? String
        val endTime = map["endTime"] as? String
        val positionId = (map["positionId"] as? Number)?.toLong() ?: 0L
        val latitude = (map["latitude"] as? Number)?.toDouble() ?: 0.0
        val longitude = (map["longitude"] as? Number)?.toDouble() ?: 0.0
        val address = map["address"] as? String
        val spentFuel = (map["spentFuel"] as? Number)?.toDouble() ?: 0.0
        val engineHours = (map["engineHours"] as? Number)?.toLong() ?: 0L
        return com.example.data.model.ReportStop(
            deviceId, deviceName, duration, startTime, endTime, positionId, latitude, longitude, address, spentFuel, engineHours
        )
    }
}

