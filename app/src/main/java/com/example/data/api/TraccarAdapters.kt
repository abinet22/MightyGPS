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
