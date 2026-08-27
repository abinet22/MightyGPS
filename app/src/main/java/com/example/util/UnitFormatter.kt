package com.example.util

import java.util.Locale

object UnitFormatter {
    fun speed(kmh: Double, isMetric: Boolean): String =
        if (isMetric) {
            String.format(Locale.US, "%.1f km/h", kmh)
        } else {
            String.format(Locale.US, "%.1f mph", kmh * 0.621371)
        }

    fun speedUnit(isMetric: Boolean): String =
        if (isMetric) "km/h" else "mph"

    fun distance(km: Double, isMetric: Boolean): String =
        if (isMetric) {
            String.format(Locale.US, "%.2f km", km)
        } else {
            String.format(Locale.US, "%.2f mi", km * 0.621371)
        }

    fun distanceUnit(isMetric: Boolean): String =
        if (isMetric) "km" else "mi"

    fun altitude(meters: Double, isMetric: Boolean): String =
        if (isMetric) {
            String.format(Locale.US, "%.0f m", meters)
        } else {
            String.format(Locale.US, "%.0f ft", meters * 3.28084)
        }
}
