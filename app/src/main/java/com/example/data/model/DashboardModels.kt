package com.example.data.model

import java.util.UUID

data class GeofenceAlert(
    val id: String = UUID.randomUUID().toString(),
    val deviceName: String,
    val geofenceName: String,
    val type: String, // "ENTERED" or "EXITED"
    val timestamp: Long = System.currentTimeMillis()
)

data class ConsolidatedAlert(
    val deviceName: String,
    val alertType: String,
    val isEntered: Boolean,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
