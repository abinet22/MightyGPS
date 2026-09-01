package com.example.ui.map

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.data.model.Position
import com.example.ui.theme.MC
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import kotlin.math.*

/**
 * Clean data model for a custom Map Marker
 */
data class MapMarker(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val info: String = "",
    val course: Float = 0f,
    val status: String = "online",
    val speedKmh: Double = 0.0,
    val category: String? = "car",
    val altitude: Double = 0.0,
    val lastUpdate: String? = null,
    val address: String? = null,
    val accuracy: Double = 0.0,
    val driverName: String? = "Assigned Driver",
    val batteryLevel: Int? = 94,
    val odometerKm: Double? = 14820.5,
    val ignition: Boolean? = true
)

/**
 * Data model for clustering map markers
 */
data class MapCluster(
    val id: Long,
    val centerLat: Double,
    val centerLng: Double,
    val markers: List<MapMarker>,
    val isCluster: Boolean
)

@Stable
data class StableGeofenceList(val list: List<com.example.ui.viewmodel.TraccarViewModel.CustomGeofence>)

@Stable
data class StablePositionList(val list: List<Position>)

@Stable
data class StableClusterList(val list: List<MapCluster>)

@Stable
data class StablePointList(val list: List<Pair<Double, Double>>)

private fun formatLastUpdate(rawTime: String?): String {
    if (rawTime.isNullOrBlank()) return "Just now"
    return try {
        val isoFormat = if (rawTime.contains(".")) {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
        } else {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
        }
        val date = isoFormat.parse(rawTime)
        if (date != null) {
            val diffMs = System.currentTimeMillis() - date.time
            val diffSec = diffMs / 1000
            when {
                diffSec < 60 -> "${diffSec.coerceAtLeast(1)}s ago"
                diffSec < 3600 -> "${diffSec / 60}m ago"
                diffSec < 86400 -> "${diffSec / 3600}h ago"
                else -> "${diffSec / 86400}d ago"
            }
        } else "Just now"
    } catch (e: Exception) {
        "Just now"
    }
}

private fun parseHexColor(hex: String, fallback: Color = MC.AccentPrimary): Color {
    return try {
        val clean = hex.removePrefix("#").trim()
        if (clean.length == 6 || clean.length == 8) {
            Color(android.graphics.Color.parseColor("#$clean"))
        } else {
            fallback
        }
    } catch (e: Exception) {
        fallback
    }
}

fun getPixelCoords(lat: Double, lng: Double, z: Int): Pair<Double, Double> {
    return getPixelCoordsCont(lat, lng, z.toDouble())
}

fun getPixelCoordsCont(lat: Double, lng: Double, z: Double): Pair<Double, Double> {
    val totalPixels = 256.0 * (2.0.pow(z))
    val x = ((lng + 180.0) / 360.0) * totalPixels
    val sinLat = sin(lat * PI / 180.0).coerceIn(-0.9999, 0.9999)
    val y = (0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI)) * totalPixels
    return Pair(x, y)
}

fun getCoordsFromPixels(px: Double, py: Double, z: Int): Pair<Double, Double> {
    return getCoordsFromPixelsCont(px, py, z.toDouble())
}

fun getCoordsFromPixelsCont(px: Double, py: Double, z: Double): Pair<Double, Double> {
    val totalPixels = 256.0 * (2.0.pow(z))
    val normLng = if (totalPixels > 0) ((px / totalPixels) * 360.0 - 180.0) else 0.0
    val lng = if (normLng < -180.0) normLng + 360.0 else if (normLng > 180.0) normLng - 360.0 else normLng
    val valueForLn = exp(PI * (1.0 - 2.0 * (py / totalPixels).coerceIn(0.0, 1.0)))
    val latRad = 2.0 * atan(valueForLn) - PI / 2.0
    val lat = Math.toDegrees(latRad).coerceIn(-85.0511, 85.0511)
    return Pair(lat, lng)
}

fun calculateBoundsFit(
    markers: List<MapMarker>,
    viewportWidth: Int,
    viewportHeight: Int,
    paddingPx: Int = 100
): Triple<Double, Double, Float>? {
    val validMarkers = markers.filter { it.latitude != 0.0 && it.longitude != 0.0 }
    if (validMarkers.isEmpty()) return null
    if (validMarkers.size == 1) {
        val single = validMarkers.first()
        return Triple(single.latitude, single.longitude, 15f)
    }

    var minX = Double.MAX_VALUE
    var maxX = -Double.MAX_VALUE
    var minY = Double.MAX_VALUE
    var maxY = -Double.MAX_VALUE

    for (m in validMarkers) {
        val (x, y) = getPixelCoords(m.latitude, m.longitude, 0)
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
    }

    val centerX = (minX + maxX) / 2.0
    val centerY = (minY + maxY) / 2.0
    val (centerLat, centerLng) = getCoordsFromPixels(centerX, centerY, 0)

    val deltaX = maxX - minX
    val deltaY = maxY - minY

    val usableWidth = (viewportWidth - 2 * paddingPx).coerceAtLeast(10)
    val usableHeight = (viewportHeight - 2 * paddingPx).coerceAtLeast(10)

    val zoomX = if (deltaX > 1e-6) (ln(usableWidth.toDouble() / deltaX) / ln(2.0)).toFloat() else 21f
    val zoomY = if (deltaY > 1e-6) (ln(usableHeight.toDouble() / deltaY) / ln(2.0)).toFloat() else 21f

    val calculatedZoom = minOf(zoomX, zoomY).coerceIn(2.5f, 18.0f)
    return Triple(centerLat, centerLng, calculatedZoom)
}

fun calculatePositionBoundsFit(
    positions: List<Position>,
    viewportWidth: Int,
    viewportHeight: Int,
    paddingPx: Int = 100
): Triple<Double, Double, Float>? {
    val valid = positions.filter { it.latitude != 0.0 && it.longitude != 0.0 }
    if (valid.isEmpty()) return null
    if (valid.size == 1) return Triple(valid[0].latitude, valid[0].longitude, 16f)

    var minX = Double.MAX_VALUE
    var maxX = -Double.MAX_VALUE
    var minY = Double.MAX_VALUE
    var maxY = -Double.MAX_VALUE

    for (p in valid) {
        val (x, y) = getPixelCoords(p.latitude, p.longitude, 0)
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
    }

    val centerX = (minX + maxX) / 2.0
    val centerY = (minY + maxY) / 2.0
    val (centerLat, centerLng) = getCoordsFromPixels(centerX, centerY, 0)

    val deltaX = maxX - minX
    val deltaY = maxY - minY

    val usableWidth = (viewportWidth - 2 * paddingPx).coerceAtLeast(10)
    val usableHeight = (viewportHeight - 2 * paddingPx).coerceAtLeast(10)

    val zoomX = if (deltaX > 1e-6) (ln(usableWidth.toDouble() / deltaX) / ln(2.0)).toFloat() else 21f
    val zoomY = if (deltaY > 1e-6) (ln(usableHeight.toDouble() / deltaY) / ln(2.0)).toFloat() else 21f

    val calculatedZoom = minOf(zoomX, zoomY).coerceIn(2.5f, 18.0f)
    return Triple(centerLat, centerLng, calculatedZoom)
}

fun calculatePositionBoundsFit(points: List<Position>): Triple<Double, Double, Float>? {
    if (points.isEmpty()) return null
    var minLat = points.first().latitude
    var maxLat = points.first().latitude
    var minLng = points.first().longitude
    var maxLng = points.first().longitude

    for (p in points) {
        if (p.latitude < minLat) minLat = p.latitude
        if (p.latitude > maxLat) maxLat = p.latitude
        if (p.longitude < minLng) minLng = p.longitude
        if (p.longitude > maxLng) maxLng = p.longitude
    }

    val centerLat = (minLat + maxLat) / 2.0
    val centerLng = (minLng + maxLng) / 2.0
    val latDiff = abs(maxLat - minLat)
    val lngDiff = abs(maxLng - minLng)
    val maxDiff = maxOf(latDiff, lngDiff)

    val zoom = when {
        maxDiff > 10.0 -> 5f
        maxDiff > 5.0 -> 7f
        maxDiff > 2.0 -> 9f
        maxDiff > 0.5 -> 11f
        maxDiff > 0.1 -> 13f
        maxDiff > 0.02 -> 15f
        else -> 16f
    }

    return Triple(centerLat, centerLng, zoom)
}

@Composable
fun SlippyMap(
    modifier: Modifier = Modifier,
    initialCenterLat: Double = 8.7832,
    initialCenterLng: Double = 38.7405,
    initialZoom: Float = 6f,
    markers: List<MapMarker> = emptyList(),
    routePath: List<Position> = emptyList(),
    selectedMarkerId: Long? = null,
    onMarkerClick: (Long) -> Unit = {},
    isDarkMode: Boolean = true,
    mapStyle: String = "google_road",
    markerLabelType: String = "name",
    markerIconStyle: String = "car",
    customIconUri: String? = null,
    geofences: List<com.example.ui.viewmodel.TraccarViewModel.CustomGeofence> = emptyList(),
    isGeofenceLayerVisible: Boolean = true,
    onGeofenceClick: (com.example.ui.viewmodel.TraccarViewModel.CustomGeofence) -> Unit = {},
    drawMode: String = "none",
    drawnPoints: List<Pair<Double, Double>> = emptyList(),
    onDrawPointsChanged: (List<Pair<Double, Double>>) -> Unit = {},
    drawnCircleCenter: Pair<Double, Double>? = null,
    drawnCircleRadiusMeters: Double = 1000.0,
    onDrawCircleChanged: (Pair<Double, Double>, Double) -> Unit = { _, _ -> },
    onViewportChanged: (Double, Double, Float) -> Unit = { _, _, _ -> },
    onUserInteraction: () -> Unit = {},
    recenterTriggerKey: Any? = null,
    playbackStepIndex: Int = -1,
    colorMoving: String = "#10B981",
    colorIdle: String = "#F59E0B",
    colorOffline: String = "#EF4444",
    markerTriggerMode: String = "click",
    infoCardFields: String = "name,speed,driver,lastUpdate,address,battery,odometer,ignition"
) {
    val googleMapType = remember(mapStyle) {
        when {
            mapStyle.contains("satellite", ignoreCase = true) || mapStyle.contains("hybrid", ignoreCase = true) -> MapType.HYBRID
            mapStyle.contains("terrain", ignoreCase = true) || mapStyle.contains("outdoors", ignoreCase = true) -> MapType.TERRAIN
            else -> MapType.NORMAL
        }
    }

    val mapProperties = remember(googleMapType) {
        MapProperties(
            mapType = googleMapType,
            isTrafficEnabled = false,
            isMyLocationEnabled = false
        )
    }

    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = true,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
            rotationGesturesEnabled = true,
            scrollGesturesEnabled = true,
            tiltGesturesEnabled = true,
            zoomGesturesEnabled = true
        )
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(initialCenterLat, initialCenterLng), initialZoom)
    }

    // Handle camera animations for recentering or selecting markers
    LaunchedEffect(recenterTriggerKey, selectedMarkerId) {
        if (recenterTriggerKey != null) {
            if (selectedMarkerId != null) {
                val targetMarker = markers.find { it.id == selectedMarkerId }
                if (targetMarker != null && targetMarker.latitude != 0.0) {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(targetMarker.latitude, targetMarker.longitude),
                            if (cameraPositionState.position.zoom < 17.5f) 18.0f else cameraPositionState.position.zoom
                        ),
                        250
                    )
                }
            } else if (routePath.isNotEmpty() && playbackStepIndex >= 0 && playbackStepIndex < routePath.size) {
                val activePos = routePath[playbackStepIndex]
                if (activePos.latitude != 0.0 && activePos.longitude != 0.0) {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(activePos.latitude, activePos.longitude),
                            if (cameraPositionState.position.zoom < 16.5f) 17.5f else cameraPositionState.position.zoom
                        ),
                        250
                    )
                }
            } else if (markers.isNotEmpty()) {
                val fitResult = calculateBoundsFit(markers, 1080, 1920, 150)
                if (fitResult != null) {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(LatLng(fitResult.first, fitResult.second), fitResult.third),
                        400
                    )
                }
            }
        }
    }

    // Automatically fit route when playback path is provided
    var lastFittedRouteHash by remember { mutableStateOf(0) }
    LaunchedEffect(routePath) {
        if (routePath.isNotEmpty()) {
            val currentHash = routePath.hashCode()
            if (currentHash != lastFittedRouteHash) {
                lastFittedRouteHash = currentHash
                val validPoints = routePath.filter { it.latitude != 0.0 && it.longitude != 0.0 }
                if (validPoints.isNotEmpty()) {
                    try {
                        val boundsBuilder = LatLngBounds.Builder()
                        validPoints.forEach { boundsBuilder.include(LatLng(it.latitude, it.longitude)) }
                        val bounds = boundsBuilder.build()
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngBounds(bounds, 120),
                            500
                        )
                    } catch (e: Exception) {
                        val first = validPoints.first()
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), 14f),
                            500
                        )
                    }
                }
            }
        }
    }

    // Notify viewport changes when user stops panning/zooming
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            val target = cameraPositionState.position.target
            val currentZoom = cameraPositionState.position.zoom
            onViewportChanged(target.latitude, target.longitude, currentZoom)
        }
    }

    var isMapLoaded by remember { mutableStateOf(false) }
    var showRetryButton by remember { mutableStateOf(false) }
    var retryTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(retryTrigger) {
        showRetryButton = false
        kotlinx.coroutines.delay(6000)
        if (!isMapLoaded) {
            showRetryButton = true
        }
    }

    val customBitmapPainter = remember(customIconUri) {
        if (!customIconUri.isNullOrEmpty()) {
            try {
                Uri.parse(customIconUri)
            } catch (e: Exception) { null }
        } else null
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings,
            onMapLoaded = {
                isMapLoaded = true
                showRetryButton = false
            },
            onMapClick = { latLng ->
                onUserInteraction()
                if (drawMode == "polygon") {
                    onDrawPointsChanged(drawnPoints + Pair(latLng.latitude, latLng.longitude))
                } else if (drawMode == "circle") {
                    onDrawCircleChanged(Pair(latLng.latitude, latLng.longitude), drawnCircleRadiusMeters)
                } else {
                    onMarkerClick(-1L)
                }
            }
        ) {
            // 1. Geofences Layer (Polygons & Circles)
            if (isGeofenceLayerVisible) {
                geofences.forEach { gf ->
                    val baseColor = parseHexColor(gf.colorHex, MC.AccentPrimary)
                    val fillColor = baseColor.copy(alpha = 0.25f)
                    val strokeColor = baseColor.copy(alpha = 0.95f)

                    if (gf.type == "polygon" && gf.points.size >= 3) {
                        Polygon(
                            points = gf.points.map { LatLng(it.first, it.second) },
                            fillColor = fillColor,
                            strokeColor = strokeColor,
                            strokeWidth = 5f,
                            clickable = true,
                            onClick = { onGeofenceClick(gf) },
                            zIndex = 10f
                        )

                        // Centroid label badge for polygon
                        if (gf.latitude != 0.0 && gf.longitude != 0.0) {
                            MarkerComposable(
                                state = rememberMarkerState(key = "gf_poly_label_${gf.id}", position = LatLng(gf.latitude, gf.longitude)),
                                zIndex = 25f,
                                onClick = {
                                    onGeofenceClick(gf)
                                    true
                                }
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MC.Surface1.copy(alpha = 0.92f),
                                    border = BorderStroke(1.2.dp, baseColor.copy(alpha = 0.85f)),
                                    shadowElevation = 6.dp,
                                    modifier = Modifier.clickable { onGeofenceClick(gf) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(baseColor, CircleShape)
                                        )
                                        Text(
                                            text = gf.name,
                                            color = MC.TextPrimary,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    } else if (gf.type == "circle" || gf.type.isEmpty()) {
                        if (gf.latitude != 0.0 && gf.longitude != 0.0 && gf.radiusMeters > 0) {
                            Circle(
                                center = LatLng(gf.latitude, gf.longitude),
                                radius = gf.radiusMeters,
                                fillColor = fillColor,
                                strokeColor = strokeColor,
                                strokeWidth = 4f,
                                clickable = true,
                                onClick = { onGeofenceClick(gf) },
                                zIndex = 10f
                            )

                            // Centroid label badge for circle
                            MarkerComposable(
                                state = rememberMarkerState(key = "gf_circ_label_${gf.id}", position = LatLng(gf.latitude, gf.longitude)),
                                zIndex = 25f,
                                onClick = {
                                    onGeofenceClick(gf)
                                    true
                                }
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MC.Surface1.copy(alpha = 0.92f),
                                    border = BorderStroke(1.2.dp, baseColor.copy(alpha = 0.85f)),
                                    shadowElevation = 6.dp,
                                    modifier = Modifier.clickable { onGeofenceClick(gf) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(baseColor, CircleShape)
                                        )
                                        Text(
                                            text = gf.name,
                                            color = MC.TextPrimary,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Drawing Mode Overlays
            if (drawnPoints.isNotEmpty()) {
                val latLngs = drawnPoints.map { LatLng(it.first, it.second) }
                if (latLngs.size >= 3) {
                    Polygon(
                        points = latLngs,
                        fillColor = MC.StatusOnline.copy(alpha = 0.2f),
                        strokeColor = MC.StatusOnline,
                        strokeWidth = 4f
                    )
                } else if (latLngs.size == 2) {
                    Polyline(
                        points = latLngs,
                        color = MC.StatusOnline,
                        width = 6f
                    )
                }
                latLngs.forEachIndexed { idx, pt ->
                    MarkerComposable(
                        state = rememberMarkerState(key = "draw_pt_$idx", position = pt),
                        zIndex = 50f
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(MC.StatusOnline, CircleShape)
                                .border(2.dp, MC.TextPrimary, CircleShape)
                        )
                    }
                }
            }
            if (drawnCircleCenter != null) {
                Circle(
                    center = LatLng(drawnCircleCenter.first, drawnCircleCenter.second),
                    radius = drawnCircleRadiusMeters,
                    fillColor = MC.AccentPrimary.copy(alpha = 0.2f),
                    strokeColor = MC.AccentPrimary,
                    strokeWidth = 4f
                )
                MarkerComposable(
                    state = rememberMarkerState(key = "draw_circle_center", position = LatLng(drawnCircleCenter.first, drawnCircleCenter.second)),
                    zIndex = 50f
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(MC.AccentPrimary, CircleShape)
                            .border(2.dp, MC.TextPrimary, CircleShape)
                    )
                }
            }

            // 3. Route History Playback Polyline & Markers
            if (routePath.isNotEmpty()) {
                val fullRouteLatLngs = remember(routePath) {
                    routePath.filter { it.latitude != 0.0 && it.longitude != 0.0 }
                        .map { LatLng(it.latitude, it.longitude) }
                }
                if (fullRouteLatLngs.isNotEmpty()) {
                    // Dark outer shadow backing for contrast
                    Polyline(
                        points = fullRouteLatLngs,
                        color = MC.Surface1.copy(alpha = 0.8f),
                        width = 16f,
                        zIndex = 1f
                    )
                    // Main background track
                    Polyline(
                        points = fullRouteLatLngs,
                        color = MC.AccentPrimary.copy(alpha = 0.65f),
                        width = 10f,
                        zIndex = 2f
                    )
                    // Traveled active segment
                    if (playbackStepIndex >= 0 && playbackStepIndex < fullRouteLatLngs.size) {
                        val traveledPoints = fullRouteLatLngs.take(playbackStepIndex + 1)
                        if (traveledPoints.isNotEmpty()) {
                            Polyline(
                                points = traveledPoints,
                                color = MC.StatusOnline,
                                width = 11f,
                                zIndex = 3f
                            )
                        }
                    }

                    // Start Badge
                    val startPos = fullRouteLatLngs.first()
                    MarkerComposable(
                        state = rememberMarkerState(key = "route_start", position = startPos),
                        title = "Start Point",
                        zIndex = 15f
                    ) {
                        Surface(
                            color = MC.StatusOnline,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(2.dp, Color.White),
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = "Start",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text("START", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    // End Badge
                    if (fullRouteLatLngs.size > 1) {
                        val endPos = fullRouteLatLngs.last()
                        MarkerComposable(
                            state = rememberMarkerState(key = "route_end", position = endPos),
                            title = "End Point",
                            zIndex = 15f
                        ) {
                            Surface(
                                color = MC.StatusOffline,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(2.dp, Color.White),
                                shadowElevation = 4.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = "End",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text("END", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }

                    // Sampled stop/event waypoints along the route
                    val stopPoints = remember(routePath) {
                        val stops = mutableListOf<Pair<Int, Position>>()
                        if (routePath.size > 3) {
                            val step = (routePath.size / 15).coerceIn(3, 30)
                            for (i in 1 until routePath.size - 1 step step) {
                                stops.add(i to routePath[i])
                            }
                        }
                        stops
                    }
                    stopPoints.forEach { (idx, pos) ->
                        if (pos.latitude != 0.0 && pos.longitude != 0.0) {
                            val isVisited = playbackStepIndex >= idx
                            MarkerComposable(
                                state = rememberMarkerState(key = "stop_$idx", position = LatLng(pos.latitude, pos.longitude)),
                                zIndex = 6f
                            ) {
                                Surface(
                                    color = if (isVisited) MC.StatusOnline.copy(alpha = 0.9f) else MC.TextTertiary.copy(alpha = 0.9f),
                                    shape = CircleShape,
                                    border = BorderStroke(1.5.dp, Color.White),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        val timeStr = (pos.fixTime ?: pos.deviceTime)?.let {
                                            try {
                                                if (it.length >= 16) it.substring(11, 16) else null
                                            } catch (e: Exception) { null }
                                        }
                                        if (timeStr != null) {
                                            Text(timeStr, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.AccessTime,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Active moving playback vehicle marker
                    if (playbackStepIndex >= 0 && playbackStepIndex < routePath.size) {
                        val activePos = routePath[playbackStepIndex]
                        if (activePos.latitude != 0.0 && activePos.longitude != 0.0) {
                            val activeMarkerState = rememberMarkerState(key = "active_playback_vehicle", position = LatLng(activePos.latitude, activePos.longitude))
                            LaunchedEffect(activePos.latitude, activePos.longitude) {
                                activeMarkerState.position = LatLng(activePos.latitude, activePos.longitude)
                            }
                            MarkerComposable(
                                state = activeMarkerState,
                                zIndex = 30f
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(MC.AccentPrimary, CircleShape)
                                        .border(3.dp, Color.White, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsCar,
                                        contentDescription = "Playback vehicle",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Vehicle Markers
            markers.forEach { m ->
                if (m.latitude != 0.0 && m.longitude != 0.0) {
                    key(m.id) {
                        val markerState = rememberMarkerState(
                            key = "marker_${m.id}",
                            position = LatLng(m.latitude, m.longitude)
                        )
                        LaunchedEffect(m.latitude, m.longitude) {
                            markerState.position = LatLng(m.latitude, m.longitude)
                        }
                        val isSelected = m.id == selectedMarkerId
                        val statusColor = when (m.status.lowercase()) {
                            "online", "moving" -> parseHexColor(colorMoving, MC.StatusOnline)
                            "idle", "parked" -> parseHexColor(colorIdle, MC.StatusIdle)
                            else -> parseHexColor(colorOffline, MC.StatusOffline)
                        }
                        val markerIcon: ImageVector = when (markerIconStyle) {
                            "car" -> Icons.Default.DirectionsCar
                            "truck" -> Icons.Default.LocalShipping
                            "bike" -> Icons.Default.TwoWheeler
                            "pin" -> Icons.Default.Place
                            "arrow" -> Icons.Default.Navigation
                            else -> when (m.category?.lowercase()) {
                                "truck" -> Icons.Default.LocalShipping
                                "bus" -> Icons.Default.DirectionsBus
                                "van" -> Icons.Default.AirportShuttle
                                "person" -> Icons.Default.Person
                                "motorcycle", "bike" -> Icons.Default.TwoWheeler
                                "arrow" -> Icons.Default.Navigation
                                "pin" -> Icons.Default.Place
                                else -> Icons.Default.DirectionsCar
                            }
                        }
                        val labelText = when (markerLabelType) {
                            "name" -> m.name
                            "coordinates" -> String.format("%.4f, %.4f", m.latitude, m.longitude)
                            "plate" -> if (m.info.contains("Plate: ")) m.info.substringAfter("Plate: ").substringBefore(" •") else "ET 3-" + (10000 + (abs(m.id) % 90000)) + " AA"
                            else -> ""
                        }

                        MarkerComposable(
                            keys = arrayOf<Any>(
                                m.id,
                                m.name,
                                m.info,
                                m.status,
                                m.category ?: "",
                                isSelected,
                                labelText,
                                markerIconStyle,
                                markerLabelType,
                                statusColor.value,
                                customBitmapPainter?.toString() ?: ""
                            ),
                            state = markerState,
                            title = m.name,
                            snippet = m.info,
                            onClick = { _ ->
                                onMarkerClick(m.id)
                                onUserInteraction()
                                true
                            },
                            zIndex = if (isSelected) 100f else 10f
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(4.dp)
                            ) {
                                if (labelText.isNotEmpty()) {
                                    Surface(
                                        color = MC.Surface1.copy(alpha = 0.95f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, if (isSelected) MC.AccentPrimary else statusColor),
                                        shadowElevation = 3.dp,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    ) {
                                        Text(
                                            text = labelText,
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(if (isSelected) 44.dp else 36.dp)
                                        .background(statusColor.copy(alpha = 0.25f), CircleShape)
                                        .border(if (isSelected) 3.dp else 2.dp, if (isSelected) MC.AccentPrimary else statusColor, CircleShape)
                                        .padding(3.dp)
                                        .background(if (isSelected) MC.AccentPrimary else statusColor, CircleShape)
                                ) {
                                    if (customBitmapPainter != null) {
                                        androidx.compose.foundation.Image(
                                            painter = rememberAsyncImagePainter(customBitmapPainter),
                                            contentDescription = "Custom Icon",
                                            modifier = Modifier.size(if (isSelected) 24.dp else 18.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = markerIcon,
                                            contentDescription = m.name,
                                            tint = Color.White,
                                            modifier = Modifier.size(if (isSelected) 22.dp else 18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Loading Overlay & Retry Logic
        AnimatedVisibility(
            visible = !isMapLoaded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MC.Surface0.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .background(MC.Surface1, RoundedCornerShape(20.dp))
                        .border(1.dp, MC.Surface3, RoundedCornerShape(20.dp))
                        .padding(32.dp)
                ) {
                    CircularProgressIndicator(color = MC.AccentPrimary, strokeWidth = 3.dp)
                    Text(
                        "Loading Google Maps...",
                        color = MC.TextPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Connecting to native rendering engine and GPS tiles",
                        color = MC.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    if (showRetryButton) {
                        Button(
                            onClick = {
                                isMapLoaded = false
                                retryTrigger++
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MC.AccentPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry Connection", color = MC.TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

    }
}
