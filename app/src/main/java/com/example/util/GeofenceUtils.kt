package com.example.util

import com.example.ui.viewmodel.TraccarViewModel.CustomGeofence
import kotlin.math.*

object GeofenceUtils {

    /**
     * Calculates distance in meters between two lat/lng coordinates using Haversine formula.
     */
    fun calculateDistanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distance = r * c
        return if (distance.isNaN()) 0.0 else distance
    }

    /**
     * Point-in-polygon algorithm using Ray Casting.
     * [points] is a list of Pair(lat, lng).
     */
    fun isPointInPolygon(
        lat: Double, lng: Double,
        points: List<Pair<Double, Double>>
    ): Boolean {
        if (points.size < 3) return false
        var inside = false
        var j = points.size - 1
        for (i in points.indices) {
            val xi = points[i].first
            val yi = points[i].second
            val xj = points[j].first
            val yj = points[j].second

            val intersect = ((yi > lng) != (yj > lng)) &&
                    (lat < (xj - xi) * (lng - yi) / (yj - yi + 0.000000001) + xi)
            if (intersect) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Calculates the destination LatLng from a starting point, distance in meters, and bearing in degrees.
     */
    fun computeOffset(
        lat: Double, lng: Double,
        distanceMeters: Double,
        headingDegrees: Double = 90.0
    ): Pair<Double, Double> {
        val distanceRatio = distanceMeters / 6371000.0
        val headingRad = Math.toRadians(headingDegrees)
        val fromLatRad = Math.toRadians(lat)
        val fromLngRad = Math.toRadians(lng)

        val toLatRad = asin(
            sin(fromLatRad) * cos(distanceRatio) +
            cos(fromLatRad) * sin(distanceRatio) * cos(headingRad)
        )
        val toLngRad = fromLngRad + atan2(
            sin(headingRad) * sin(distanceRatio) * cos(fromLatRad),
            cos(distanceRatio) - sin(fromLatRad) * sin(toLatRad)
        )
        return Pair(Math.toDegrees(toLatRad), Math.toDegrees(toLngRad))
    }

    /**
     * Determines whether a given lat/lng position is inside a custom geofence.
     */
    fun isPositionInsideGeofence(
        latitude: Double,
        longitude: Double,
        geofence: CustomGeofence
    ): Boolean {
        if (!geofence.isActive) return false

        return if (geofence.type == "polygon" && geofence.points.size >= 3) {
            isPointInPolygon(latitude, longitude, geofence.points)
        } else {
            val dist = calculateDistanceMeters(
                latitude, longitude,
                geofence.latitude, geofence.longitude
            )
            dist <= geofence.radiusMeters
        }
    }
}
