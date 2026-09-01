package com.example.data.model

enum class TraccarCommandType(
    val wireValue: String,
    val displayLabel: String,
    val requiresValue: Boolean = false,
    val valuePrompt: String = "Value"
) {
    ENGINE_STOP("engineStop", "Engine Fuel Cut"),
    ENGINE_RESUME("engineResume", "Engine Resume"),
    POSITION_SINGLE("positionSingle", "Poll GPS (Ping)"),
    POSITION_PERIODIC("positionPeriodic", "Set Report Interval", requiresValue = true, valuePrompt = "Frequency (e.g. 30s)"),
    ALARM_ARM("custom", "Trigger Emergency Alarm"),
    SET_SPEED_LIMIT("custom", "Set Speed Limit", requiresValue = true, valuePrompt = "Speed Limit (e.g. 80)")
}
