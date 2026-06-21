package com.example.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import com.example.data.model.Position
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
    val accuracy: Double = 0.0
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
        val date = isoFormat.parse(rawTime) ?: return rawTime
        val outputFormat = java.text.SimpleDateFormat("dd MMM, HH:mm:ss", java.util.Locale.US)
        outputFormat.format(date)
    } catch (e: Exception) {
        rawTime.replace("T", " ").replace("Z", "")
    }
}

private const val tileSize = 256

// Convert coordinates to tile pixel positions
fun getPixelCoords(lat: Double, lng: Double, z: Int): Pair<Double, Double> {
    val totalTiles = (1 shl z).toDouble()
    val totalPixels = totalTiles * tileSize
    val x = (lng + 180.0) / 360.0 * totalPixels
    val latRad = Math.toRadians(lat)
    val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * totalPixels
    return Pair(x, y)
}

fun getPixelCoordsCont(lat: Double, lng: Double, z: Double): Pair<Double, Double> {
    val totalPixels = 2.0.pow(z) * tileSize
    val x = (lng + 180.0) / 360.0 * totalPixels
    val latRad = Math.toRadians(lat)
    val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * totalPixels
    return Pair(x, y)
}

// Convert a pixel offset back to coordinates (for clicks/gestures if needed)
fun getCoordsFromPixels(px: Double, py: Double, z: Int): Pair<Double, Double> {
    val totalTiles = (1 shl z).toDouble()
    val totalPixels = totalTiles * tileSize
    val lng = px / totalPixels * 360.0 - 180.0
    val valY = (py / totalPixels) * 2.0 - 1.0
    val latRad = atan(sinh(PI * (1.0 - valY)))
    val lat = Math.toDegrees(latRad)
    return Pair(lat, lng)
}

fun getCoordsFromPixelsCont(px: Double, py: Double, z: Double): Pair<Double, Double> {
    val totalPixels = 2.0.pow(z) * tileSize
    val lng = px / totalPixels * 360.0 - 180.0
    val valY = (py / totalPixels) * 2.0 - 1.0
    val latRad = atan(sinh(PI * (1.0 - valY)))
    val lat = Math.toDegrees(latRad)
    return Pair(lat, lng)
}

@Composable
fun SlippyMap(
    modifier: Modifier = Modifier,
    initialCenterLat: Double = 9.0192, // Default to Addis Ababa (Mighty GPS headquarters territory)
    initialCenterLng: Double = 38.7525,
    initialZoom: Float = 15f,
    markers: List<MapMarker> = emptyList(),
    routePath: List<Position> = emptyList(),
    selectedMarkerId: Long? = null,
    onMarkerClick: (Long) -> Unit = {},
    isDarkMode: Boolean = true,
    mapStyle: String = "mapbox_streets", // Mapbox & Google styles
    markerLabelType: String = "name", // "name", "coordinates", "plate"
    markerIconStyle: String = "car",
    customIconUri: String? = null,
    geofences: List<com.example.ui.viewmodel.TraccarViewModel.CustomGeofence> = emptyList(),
    // Mapbox Draw bindings
    drawMode: String = "none", // "none", "polygon", "circle"
    drawnPoints: List<Pair<Double, Double>> = emptyList(),
    onDrawPointsChanged: (List<Pair<Double, Double>>) -> Unit = {},
    drawnCircleCenter: Pair<Double, Double>? = null,
    drawnCircleRadiusMeters: Double = 1000.0,
    onDrawCircleChanged: (Pair<Double, Double>, Double) -> Unit = { _, _ -> },
    onViewportChanged: (Double, Double, Float) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    
    val customBitmap = remember(customIconUri) {
        if (!customIconUri.isNullOrEmpty()) {
            try {
                if (customIconUri.startsWith("content://")) {
                    val inputStream = context.contentResolver.openInputStream(Uri.parse(customIconUri))
                    android.graphics.BitmapFactory.decodeStream(inputStream)?.let { bmp ->
                        val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, 48, 48, true)
                        scaled.asImageBitmap()
                    }
                } else {
                    val file = java.io.File(customIconUri)
                    if (file.exists()) {
                        android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.let { bmp ->
                            val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, 48, 48, true)
                            scaled.asImageBitmap()
                        }
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else {
            null
        }
    }
    
    // Smooth navigation states
    var centerLat by remember { mutableStateOf(initialCenterLat) }
    var centerLng by remember { mutableStateOf(initialCenterLng) }
    var zoom by remember { mutableStateOf(initialZoom) }

    val currentMarkers by rememberUpdatedState(markers)
    val currentDrawMode by rememberUpdatedState(drawMode)
    val currentDrawnPoints by rememberUpdatedState(drawnPoints)
    val currentDrawnCircleCenter by rememberUpdatedState(drawnCircleCenter)

    // Cluster calculation on markers list
    val clusteredMarkers = remember(markers, zoom) {
        if (zoom > 13.5f) {
            markers.map {
                MapCluster(
                    id = it.id,
                    centerLat = it.latitude,
                    centerLng = it.longitude,
                    markers = listOf(it),
                    isCluster = false
                )
            }
        } else {
            val result = mutableListOf<MapCluster>()
            val visited = BooleanArray(markers.size)
            val clusterThresholdPx = 100.0 // About 100 pixels threshold for grouping close markers
            
            for (i in markers.indices) {
                if (visited[i]) continue
                val m1 = markers[i]
                visited[i] = true
                
                val clusterList = mutableListOf(m1)
                val m1Coords = getPixelCoordsCont(m1.latitude, m1.longitude, zoom.toDouble())
                
                for (j in i + 1 until markers.size) {
                    if (visited[j]) continue
                    val m2 = markers[j]
                    val m2Coords = getPixelCoordsCont(m2.latitude, m2.longitude, zoom.toDouble())
                    
                    val dx = m1Coords.first - m2Coords.first
                    val dy = m1Coords.second - m2Coords.second
                    val dist = sqrt(dx * dx + dy * dy)
                    
                    if (dist < clusterThresholdPx) {
                        clusterList.add(m2)
                        visited[j] = true
                    }
                }
                
                if (clusterList.size > 1) {
                    val avgLat = clusterList.map { it.latitude }.average()
                    val avgLng = clusterList.map { it.longitude }.average()
                    result.add(
                        MapCluster(
                            id = -clusterList.first().id,
                            centerLat = avgLat,
                            centerLng = avgLng,
                            markers = clusterList,
                            isCluster = true
                        )
                    )
                } else {
                    result.add(
                        MapCluster(
                            id = m1.id,
                            centerLat = m1.latitude,
                            centerLng = m1.longitude,
                            markers = listOf(m1),
                            isCluster = false
                        )
                    )
                }
            }
            result
        }
    }

    // Tracking variables to distinguish self-reported camera updates from forced parent-initiated recentering
    var lastReportedLat by remember { mutableStateOf<Double?>(null) }
    var lastReportedLng by remember { mutableStateOf<Double?>(null) }
    var lastReportedZoom by remember { mutableStateOf<Float?>(null) }

    // Report any viewport changes back to the parent to keep the map state synchronized during pan, zoom or fly-to.
    LaunchedEffect(centerLat, centerLng, zoom) {
        lastReportedLat = centerLat
        lastReportedLng = centerLng
        lastReportedZoom = zoom
        onViewportChanged(centerLat, centerLng, zoom)
    }

    // Sync with externally modified initial position values (e.g., live tracking update or clicking recenter)
    LaunchedEffect(initialCenterLat, initialCenterLng, initialZoom) {
        val isSelfReported = lastReportedLat != null &&
                abs(initialCenterLat - lastReportedLat!!) < 1e-6 &&
                abs(initialCenterLng - lastReportedLng!!) < 1e-6 &&
                abs(initialZoom - lastReportedZoom!!) < 0.01f
        
        if (!isSelfReported) {
            centerLat = initialCenterLat
            centerLng = initialCenterLng
            zoom = initialZoom
        }
    }

    // If marker is selected externally, fly/glide smoothly to it with maximum zoom!
    LaunchedEffect(selectedMarkerId) {
        selectedMarkerId?.let { id ->
            val m = markers.find { it.id == id }
            if (m != null) {
                val startLat = centerLat
                val startLng = centerLng
                val startZoom = zoom
                
                val destLat = m.latitude
                val destLng = m.longitude
                val destZoom = 19.5f // Increased zoom level for closer detail
                
                val steps = 30
                for (i in 1..steps) {
                    val progress = i.toFloat() / steps
                    // Decelerate transition equation
                    val t = 1f - (1f - progress) * (1f - progress)
                    centerLat = startLat + (destLat - startLat) * t
                    centerLng = startLng + (destLng - startLng) * t
                    zoom = (startZoom + (destZoom - startZoom) * t).coerceIn(2.5f, 21.0f)
                    kotlinx.coroutines.delay(16) // ~60 FPS
                }
            }
        }
    }

    // Select tile URL based on chosen map style
    val tileUrlTemplate = when (mapStyle) {
        // Google Maps Styles
        "google_road" -> "https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}"
        "google_satellite" -> "https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}"
        "google_hybrid" -> "https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}"
        "google_terrain" -> "https://mt1.google.com/vt/lyrs=p&x={x}&y={y}&z={z}"
        
        // Mapbox Styles
        "mapbox_streets" -> "https://api.mapbox.com/styles/v1/mapbox/streets-v12/tiles/256/{z}/{x}/{y}?access_token=pk.eyJ1IjoiYWJpbmV0MTIzIiwiYSI6ImNrbWR3d3Y5NzJwbG8ycGp4bGU1bXBtaGsifQ.LIZpH0mev90pUGXewX6lww"
        "mapbox_outdoors" -> "https://api.mapbox.com/styles/v1/mapbox/outdoors-v12/tiles/256/{z}/{x}/{y}?access_token=pk.eyJ1IjoiYWJpbmV0MTIzIiwiYSI6ImNrbWR3d3Y5NzJwbG8ycGp4bGU1bXBtaGsifQ.LIZpH0mev90pUGXewX6lww"
        "mapbox_light" -> "https://api.mapbox.com/styles/v1/mapbox/light-v11/tiles/256/{z}/{x}/{y}?access_token=pk.eyJ1IjoiYWJpbmV0MTIzIiwiYSI6ImNrbWR3d3Y5NzJwbG8ycGp4bGU1bXBtaGsifQ.LIZpH0mev90pUGXewX6lww"
        "mapbox_dark" -> "https://api.mapbox.com/styles/v1/mapbox/dark-v11/tiles/256/{z}/{x}/{y}?access_token=pk.eyJ1IjoiYWJpbmV0MTIzIiwiYSI6ImNrbWR3d3Y5NzJwbG8ycGp4bGU1bXBtaGsifQ.LIZpH0mev90pUGXewX6lww"
        "mapbox_satellite" -> "https://api.mapbox.com/styles/v1/mapbox/satellite-v9/tiles/256/{z}/{x}/{y}?access_token=pk.eyJ1IjoiYWJpbmV0MTIzIiwiYSI6ImNrbWR3d3Y5NzJwbG8ycGp4bGU1bXBtaGsifQ.LIZpH0mev90pUGXewX6lww"
        "mapbox_satellite_streets" -> "https://api.mapbox.com/styles/v1/mapbox/satellite-streets-v12/tiles/256/{z}/{x}/{y}?access_token=pk.eyJ1IjoiYWJpbmV0MTIzIiwiYSI6ImNrbWR3d3Y5NzJwbG8ycGp4bGU1bXBtaGsifQ.LIZpH0mev90pUGXewX6lww"
        
        "osm_classic" -> "https://a.tile.openstreetmap.org/{z}/{x}/{y}.png"
        else -> "https://api.mapbox.com/styles/v1/mapbox/streets-v12/tiles/256/{z}/{x}/{y}?access_token=pk.eyJ1IjoiYWJpbmV0MTIzIiwiYSI6ImNrbWR3d3Y5NzJwbG8ycGp4bGU1bXBtaGsifQ.LIZpH0mev90pUGXewX6lww" // default mapbox_streets fallback
    }

    BoxWithConstraints(
        modifier = modifier
            .background(if (isDarkMode) Color(0xFF1E1E24) else Color(0xFFF0F2F5))
            .clipToBounds()
    ) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight

        if (width <= 0 || height <= 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@BoxWithConstraints
        }

        val currentZoomInt = floor(zoom.toDouble()).toInt().coerceIn(2, 20)
        val subScale = 2.0.pow(zoom.toDouble() - currentZoomInt).toFloat()
        val density = LocalDensity.current

        // Center globals
        val (centerXPixel, centerYPixel) = getPixelCoords(centerLat, centerLng, currentZoomInt)

        // Find visible tile index span
        val widthUnscaled = width / subScale
        val heightUnscaled = height / subScale
        val startTileX = floor((centerXPixel - widthUnscaled / 2) / tileSize).toInt()
        val endTileX = ceil((centerXPixel + widthUnscaled / 2) / tileSize).toInt()
        val startTileY = floor((centerYPixel - heightUnscaled / 2) / tileSize).toInt()
        val endTileY = ceil((centerYPixel + heightUnscaled / 2) / tileSize).toInt()

        val maxTileIndex = (1 shl currentZoomInt) - 1

        // Inner gesture container to isolate tap and transform events
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        
                        // For panning
                        var lastPanPosition: Offset? = firstDown.position
                        var isDragging = false
                        var totalDragDistance = 0f
                        
                        // For zooming
                        var initialFingerDistance: Float? = null
                        var initialZoomOnPinch: Float? = null
                        
                        // To identify if any multi-touch occurred in this gesture sequence
                        var multiTouchOccurred = false
                        
                        do {
                            val event = awaitPointerEvent()
                            val activePointers = event.changes.filter { it.pressed }
                            
                            if (activePointers.size >= 2) {
                                multiTouchOccurred = true
                                isDragging = false // Cancel panning if multi-touch starts
                                val p1 = activePointers[0].position
                                val p2 = activePointers[1].position
                                val currentDistance = (p1 - p2).getDistance()
                                
                                if (initialFingerDistance == null) {
                                    initialFingerDistance = currentDistance
                                    initialZoomOnPinch = zoom
                                } else {
                                    val distanceRatio = currentDistance / (initialFingerDistance.coerceAtLeast(1f))
                                    if (distanceRatio > 0) {
                                        val log2Ratio = ln(distanceRatio) / ln(2.0f)
                                        val targetZoom = (initialZoomOnPinch!! + log2Ratio).coerceIn(2.5f, 21.0f)
                                        zoom = targetZoom
                                    }
                                }
                                
                                // Consume the changes for zoom
                                event.changes.forEach { it.consume() }
                                lastPanPosition = null
                            } else if (activePointers.size == 1 && !multiTouchOccurred) {
                                // Single finger panning
                                initialFingerDistance = null
                                initialZoomOnPinch = null
                                
                                val pointer = activePointers[0]
                                val currentPosition = pointer.position
                                
                                if (lastPanPosition != null) {
                                    val panOffset = currentPosition - lastPanPosition!!
                                    val dist = panOffset.getDistance()
                                    totalDragDistance += dist
                                    
                                    if (isDragging) {
                                        // Update camera center based on panOffset
                                        val (cx, cy) = getPixelCoordsCont(centerLat, centerLng, zoom.toDouble())
                                        val nx = cx - panOffset.x
                                        val ny = cy - panOffset.y
                                        
                                        val (newLat, newLng) = getCoordsFromPixelsCont(nx, ny, zoom.toDouble())
                                        centerLat = newLat.coerceIn(-85.05, 85.05)
                                        centerLng = newLng.coerceIn(-180.0, 180.0)
                                        
                                        pointer.consume()
                                    } else {
                                        val touchSlop = 8f
                                        if (totalDragDistance > touchSlop) {
                                            isDragging = true
                                        }
                                    }
                                }
                                lastPanPosition = currentPosition
                            } else {
                                initialFingerDistance = null
                                initialZoomOnPinch = null
                                lastPanPosition = null
                            }
                            
                        } while (event.changes.any { it.pressed })
                        
                        // After the gesture sequence completely finishes (all fingers lifted)
                        if (!multiTouchOccurred && !isDragging && totalDragDistance < 15f) {
                            val tapOffset = firstDown.position
                            val tapX = tapOffset.x
                            val tapY = tapOffset.y

                            if (currentDrawMode != "none") {
                                // Translate tap position to coordinates
                                val unscaledTapX = (tapX - width / 2) / subScale
                                val unscaledTapY = (tapY - height / 2) / subScale
                                val globalPx = centerXPixel + unscaledTapX
                                val globalPy = centerYPixel + unscaledTapY
                                val (tapLat, tapLng) = getCoordsFromPixels(globalPx, globalPy, currentZoomInt)

                                if (currentDrawMode == "polygon") {
                                    onDrawPointsChanged(currentDrawnPoints + Pair(tapLat, tapLng))
                                } else if (currentDrawMode == "circle") {
                                    if (currentDrawnCircleCenter == null) {
                                        onDrawCircleChanged(Pair(tapLat, tapLng), 1000.0)
                                    } else {
                                        // Calculate distance from center to tap offset
                                        val dLat = Math.toRadians(tapLat - currentDrawnCircleCenter!!.first)
                                        val dLon = Math.toRadians(tapLng - currentDrawnCircleCenter!!.second)
                                        val a = sin(dLat / 2) * sin(dLat / 2) +
                                                cos(Math.toRadians(currentDrawnCircleCenter!!.first)) * cos(Math.toRadians(tapLat)) *
                                                sin(dLon / 2) * sin(dLon / 2)
                                        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
                                        val radius = 6371000.0 * c
                                        onDrawCircleChanged(currentDrawnCircleCenter!!, radius.coerceIn(50.0, 50000.0))
                                    }
                                }
                            } else {
                                var clickedCluster: MapCluster? = null
                                var minDistance = 120f // Tap tolerance in pixels
                                
                                clusteredMarkers.forEach { c ->
                                    val (px, py) = getPixelCoords(c.centerLat, c.centerLng, currentZoomInt)
                                    val dx = (px - centerXPixel) * subScale
                                    val dy = (py - centerYPixel) * subScale
                                    val screenX = (width / 2 + dx).toFloat()
                                    val screenY = (height / 2 + dy).toFloat()
                                    
                                    val dist = sqrt((screenX - tapX).pow(2) + (screenY - tapY).pow(2))
                                    if (dist < minDistance) {
                                        minDistance = dist
                                        clickedCluster = c
                                    }
                                }
                                
                                if (clickedCluster != null) {
                                    if (clickedCluster!!.isCluster) {
                                        // Zoom in on cluster center
                                        val targetLat = clickedCluster!!.centerLat
                                        val targetLng = clickedCluster!!.centerLng
                                        val targetZoom = (zoom + 2.5f).coerceAtMost(21.0f)
                                        
                                        centerLat = targetLat
                                        centerLng = targetLng
                                        zoom = targetZoom
                                    } else {
                                        onMarkerClick(clickedCluster!!.markers.first().id)
                                    }
                                } else {
                                    // Tap on map background deselects / closes popup
                                    onMarkerClick(-1L)
                                }
                            }
                        }
                    }
                }
        ) {
            // 1. Draw Map Tiles
            for (ty in startTileY..endTileY) {
                for (tx in startTileX..endTileX) {
                    // Wrap X coordinates around the 180-degree meridian
                    val wrappedX = ((tx % (maxTileIndex + 1)) + (maxTileIndex + 1)) % (maxTileIndex + 1)
                    
                    // Keep Y within world borders
                    if (ty < 0 || ty > maxTileIndex) continue

                    val tileUrl = tileUrlTemplate
                        .replace("{z}", currentZoomInt.toString())
                        .replace("{x}", wrappedX.toString())
                        .replace("{y}", ty.toString())

                    // Load image asynchronously
                    val painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(tileUrl)
                            .crossfade(true)
                            .build()
                    )

                    // Get physical screen position
                    val currentTileLeftPx = tx * tileSize
                    val currentTileTopPx = ty * tileSize
                    
                    val dx = currentTileLeftPx - centerXPixel
                    val dy = currentTileTopPx - centerYPixel
                    val screenX = (width / 2 + dx * subScale).toFloat()
                    val screenY = (height / 2 + dy * subScale).toFloat()
                    val tileSizeScaled = tileSize * subScale

                    androidx.compose.foundation.Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier
                            .offset(
                                x = with(density) { screenX.toDp() },
                                y = with(density) { screenY.toDp() }
                            )
                            .size(with(density) { tileSizeScaled.toDp() })
                    )
                }
            }

            // 2. Map Canvas Overlay: Trails, Geofences, Vectors & Alerts
            Canvas(modifier = Modifier.fillMaxSize()) {
                val totalTiles = (1 shl currentZoomInt).toDouble()
                val totalPixels = totalTiles * tileSize

                // Helper to get raw offset coordinates on map surface
                fun geoToScreen(lat: Double, lng: Double): Offset {
                    val (px, py) = getPixelCoords(lat, lng, currentZoomInt)
                    val dx = (px - centerXPixel) * subScale
                    val dy = (py - centerYPixel) * subScale
                    return Offset((width / 2 + dx).toFloat(), (height / 2 + dy).toFloat())
                }

                // Draw Custom Geofence Zones (Glow and boundary line)
                geofences.forEach { gf ->
                    if (gf.type == "polygon" && gf.points.isNotEmpty()) {
                        val polyPath = Path()
                        var first = true
                        gf.points.forEach { pt ->
                            val scr = geoToScreen(pt.first, pt.second)
                            if (first) {
                                polyPath.moveTo(scr.x, scr.y)
                                first = false
                            } else {
                                polyPath.lineTo(scr.x, scr.y)
                            }
                        }
                        if (gf.points.size >= 3) {
                            polyPath.close()
                        }

                        // Transparent fill
                        drawPath(
                            path = polyPath,
                            color = Color(0x2210B981) // Emerald transparent
                        )
                        // Emerald stroke
                        drawPath(
                            path = polyPath,
                            color = Color(0xFF10B981),
                            style = Stroke(width = 4f)
                        )

                        // Floating name label at centroid coordinates
                        val centroidX = gf.points.map { geoToScreen(it.first, it.second).x }.average().toFloat()
                        val centroidY = gf.points.map { geoToScreen(it.first, it.second).y }.average().toFloat()

                        val gfPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(185, 16, 185, 129)
                            textSize = 28f
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            gf.name,
                            centroidX,
                            centroidY,
                            gfPaint
                        )
                    } else {
                        val centerOffset = geoToScreen(gf.latitude, gf.longitude)
                        
                        // approximate meters per pixel scale
                        val metersPerPixel = 156543.03392 * cos(Math.toRadians(gf.latitude)) / Math.pow(2.0, zoom.toDouble())
                        val radiusPx = (gf.radiusMeters / metersPerPixel).toFloat()
                        
                        if (radiusPx > 0f) {
                            // Transparent fill
                            drawCircle(
                                color = Color(0x223B82F6),
                                radius = radiusPx,
                                center = centerOffset
                            )
                            // High quality glowing ring
                            drawCircle(
                                color = Color(0xFF3B82F6),
                                radius = radiusPx,
                                center = centerOffset,
                                style = Stroke(width = 4f)
                            )
                            
                            // Small floating badge text for geofence name
                            val gfPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.argb(180, 59, 130, 246)
                                textSize = 28f
                                isAntiAlias = true
                                textAlign = android.graphics.Paint.Align.CENTER
                                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                            }
                            drawContext.canvas.nativeCanvas.drawText(
                                gf.name,
                                centerOffset.x,
                                centerOffset.y,
                                gfPaint
                            )
                        }
                    }
                }

                // Draw currently drawn polygon if active (active Mapbox Draw progress editor)
                if (drawMode == "polygon" && drawnPoints.isNotEmpty()) {
                    val pPath = Path()
                    var isFirst = true
                    drawnPoints.forEach { pt ->
                        val scr = geoToScreen(pt.first, pt.second)
                        if (isFirst) {
                            pPath.moveTo(scr.x, scr.y)
                            isFirst = false
                        } else {
                            pPath.lineTo(scr.x, scr.y)
                        }
                    }
                    if (drawnPoints.size >= 3) {
                        pPath.close()
                        // Shaded active fill
                        drawPath(path = pPath, color = Color(0x333B82F6))
                    }
                    // Outer border
                    drawPath(path = pPath, color = Color(0xFF3B82F6), style = Stroke(width = 4f))

                    // Draw vertices as handles
                    drawnPoints.forEachIndexed { idx, pt ->
                        val scr = geoToScreen(pt.first, pt.second)
                        drawCircle(color = Color.White, radius = 9f, center = scr)
                        drawCircle(color = Color(0xFF2563EB), radius = 6f, center = scr)
                    }
                }

                // Draw currently drawn circle center and range if active (active Circular Drag editor)
                if (drawMode == "circle" && drawnCircleCenter != null) {
                    val crCenter = geoToScreen(drawnCircleCenter.first, drawnCircleCenter.second)
                    val metersPerPx = 156543.03392 * cos(Math.toRadians(drawnCircleCenter.first)) / Math.pow(2.0, zoom.toDouble())
                    val rPx = (drawnCircleRadiusMeters / metersPerPx).toFloat()

                    if (rPx > 0f) {
                        drawCircle(color = Color(0x333B82F6), radius = rPx, center = crCenter)
                        drawCircle(color = Color(0xFF3B82F6), radius = rPx, center = crCenter, style = Stroke(width = 4f))
                    }

                    // Draw center dot
                    drawCircle(color = Color.White, radius = 8f, center = crCenter)
                    drawCircle(color = Color(0xFFEF4444), radius = 5f, center = crCenter)
                }

                // A. Draw Route Trail
                if (routePath.isNotEmpty()) {
                    val path = Path()
                    var isStarted = false
                    routePath.forEach { pos ->
                        val screenOffset = geoToScreen(pos.latitude, pos.longitude)
                        if (!isStarted) {
                            path.moveTo(screenOffset.x, screenOffset.y)
                            isStarted = true
                        } else {
                            path.lineTo(screenOffset.x, screenOffset.y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF2563EB),
                        style = Stroke(width = 6f)
                    )
                    // Draw path circles at endpoints
                    routePath.firstOrNull()?.let {
                        val p = geoToScreen(it.latitude, it.longitude)
                        drawCircle(color = Color(0xFF10B981), radius = 12f, center = p)
                    }
                    routePath.lastOrNull()?.let {
                        val p = geoToScreen(it.latitude, it.longitude)
                        drawCircle(color = Color(0xFFEF4444), radius = 12f, center = p)
                    }
                }

                // B. Draw Active Vehicle Markers & Clusters
                clusteredMarkers.forEach { c ->
                    val screenOffset = geoToScreen(c.centerLat, c.centerLng)
                    
                    if (c.isCluster) {
                        // Draw a Beautiful, Rich Fleet Cluster Bubble with a pulsing halo
                        val size = c.markers.size
                        
                        // 1. Double Outer Glowing Ring
                        drawCircle(
                            color = Color(0xFF3B82F6).copy(alpha = 0.15f),
                            radius = 42f,
                            center = screenOffset
                        )
                        drawCircle(
                            color = Color(0xFF3B82F6).copy(alpha = 0.25f),
                            radius = 32f,
                            center = screenOffset
                        )
                        
                        // 2. High-contrast Solid Outer Ring
                        drawCircle(
                            color = Color(0xFF1E293B), // Slate Grey
                            radius = 24f,
                            center = screenOffset
                        )
                        
                        // 3. Thick Blue/Primary Circle Center
                        drawCircle(
                            color = Color(0xFF3B82F6), // Vibrant M3 Blue
                            radius = 20f,
                            center = screenOffset
                        )
                        
                        // 4. White Count Label Text in the middle
                        val countPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 24f
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            size.toString(),
                            screenOffset.x,
                            screenOffset.y + 8f,
                            countPaint
                        )
                        
                        // 5. Mini Label "FLEET CLUSTER"
                        val miniPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(220, 59, 130, 246)
                            textSize = 18f
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                        }
                        
                        // Small label backdrop bubble
                        val bgPaint = android.graphics.Paint().apply {
                            color = if (isDarkMode) android.graphics.Color.argb(200, 15, 23, 42) else android.graphics.Color.argb(200, 255, 255, 255)
                            style = android.graphics.Paint.Style.FILL
                        }
                        
                        val clusterLabel = "$size Vehicles"
                        val rect = android.graphics.Rect()
                        miniPaint.getTextBounds(clusterLabel, 0, clusterLabel.length, rect)
                        
                        val bubbleLeft = screenOffset.x - rect.width() / 2 - 8
                        val bubbleTop = screenOffset.y - 40 - rect.height() - 4
                        val bubbleRight = screenOffset.x + rect.width() / 2 + 8
                        val bubbleBottom = screenOffset.y - 40 + 4
                        
                        drawContext.canvas.nativeCanvas.drawRoundRect(
                            bubbleLeft, bubbleTop, bubbleRight, bubbleBottom, 8f, 8f, bgPaint
                        )
                        
                        drawContext.canvas.nativeCanvas.drawText(
                            clusterLabel,
                            screenOffset.x,
                            screenOffset.y - 40,
                            miniPaint
                        )
                    } else {
                        val m = c.markers.first()
                        
                        // Draw accuracy/geofence halo
                        drawCircle(
                            color = if (m.status == "online") Color(0x2210B981) else Color(0x22EF4444),
                            radius = 35f,
                            center = screenOffset
                        )

                        // Outer border ring (shows selection or status)
                        drawCircle(
                            color = if (m.id == selectedMarkerId) {
                                Color(0xFF3B82F6) // Active Selection Blue
                            } else {
                                when (m.status) {
                                    "online" -> Color(0xFF10B981) // Green
                                    "offline" -> Color(0xFF6B7280) // Gray
                                    else -> Color(0xFFF59E0B) // Amber
                                }
                            },
                            radius = 20f,
                            center = screenOffset
                        )

                        // Inner background circle
                        drawCircle(
                            color = Color.White,
                            radius = 16f,
                            center = screenOffset
                        )

                        // Status indicator fill or Custom Icon
                        if (markerIconStyle == "custom" && customBitmap != null) {
                            drawImage(
                                image = customBitmap,
                                dstOffset = IntOffset((screenOffset.x - 12).toInt(), (screenOffset.y - 12).toInt()),
                                dstSize = IntSize(24, 24)
                            )
                        } else {
                            val iconEmoji = when (markerIconStyle) {
                                "car" -> "🚗"
                                "truck" -> "🚛"
                                "bike" -> "🏍️"
                                "pin" -> "📍"
                                else -> "🚗"
                            }
                            val emojiPaint = android.graphics.Paint().apply {
                                textSize = 21f
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            drawContext.canvas.nativeCanvas.drawText(
                                iconEmoji,
                                screenOffset.x,
                                screenOffset.y + 7f,
                                emojiPaint
                            )
                        }

                        // Draw speed indicator arrowhead pointing custom course direction
                        val radians = Math.toRadians((m.course - 90.0)).toFloat()
                        val arrowLen = 14f
                        val tipX = screenOffset.x + arrowLen * cos(radians)
                        val tipY = screenOffset.y + arrowLen * sin(radians)
                        
                        // Arrow tail base left and right
                        val leftRad = Math.toRadians((m.course + 135.0).toDouble()).toFloat()
                        val rightRad = Math.toRadians((m.course - 135.0).toDouble()).toFloat()
                        
                        val lx = screenOffset.x + 8f * cos(leftRad)
                        val ly = screenOffset.y + 8f * sin(leftRad)
                        val rx = screenOffset.x + 8f * cos(rightRad)
                        val ry = screenOffset.y + 8f * sin(rightRad)

                        val arrowPath = Path().apply {
                            moveTo(tipX, tipY)
                            lineTo(lx, ly)
                            lineTo(rx, ry)
                            close()
                        }
                        drawPath(path = arrowPath, color = Color.White)

                        // Label Text - Draw vehicle name badge above the vehicle
                        val textPaint = android.graphics.Paint().apply {
                            color = if (isDarkMode) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY
                            textSize = 30f
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                        }

                        // Render background bubble for name badge
                        val labelText = when (markerLabelType) {
                            "coordinates" -> String.format("%.5f, %.5f", m.latitude, m.longitude)
                            "plate" -> "ET 3-" + (10000 + m.id % 90000) + " AA"
                            else -> m.name
                        }
                        val rect = android.graphics.Rect()
                        textPaint.getTextBounds(labelText, 0, labelText.length, rect)
                        
                        val bgPaint = android.graphics.Paint().apply {
                            color = if (isDarkMode) android.graphics.Color.argb(200, 15, 23, 42) else android.graphics.Color.argb(200, 255, 255, 255)
                            style = android.graphics.Paint.Style.FILL
                        }

                        // Bubble padding bounds
                        val padX = 12
                        val padY = 8
                        val bubbleLeft = screenOffset.x - rect.width() / 2 - padX
                        val bubbleTop = screenOffset.y - 45 - rect.height() - padY
                        val bubbleRight = screenOffset.x + rect.width() / 2 + padX
                        val bubbleBottom = screenOffset.y - 45 + padY

                        drawContext.canvas.nativeCanvas.drawRoundRect(
                            bubbleLeft, bubbleTop, bubbleRight, bubbleBottom, 12f, 12f, bgPaint
                        )
                        
                        drawContext.canvas.nativeCanvas.drawText(
                            labelText,
                            screenOffset.x,
                            screenOffset.y - 45,
                            textPaint
                        )
                    }
                }
            }

            // 3. Custom Mapbox GL style popup overlay card
            val selectedMarker = markers.find { it.id == selectedMarkerId }
            if (selectedMarker != null) {
                val density = LocalDensity.current
                val (px, py) = getPixelCoords(selectedMarker.latitude, selectedMarker.longitude, currentZoomInt)
                val dx = (px - centerXPixel) * subScale
                val dy = (py - centerYPixel) * subScale
                
                val screenX = (width / 2 + dx).toFloat()
                val screenY = (height / 2 + dy).toFloat()
                
                if (screenX >= 0 && screenX <= width && screenY >= 0 && screenY <= height) {
                    val cardWidthDp = 280.dp
                    val cardHeightDp = 220.dp
                    
                    val cardWidthPx = with(density) { cardWidthDp.toPx() }
                    val cardHeightPx = with(density) { cardHeightDp.toPx() }
                    val markerOffsetUpPx = with(density) { 35.dp.toPx() }
                    
                    val popupX = (screenX - cardWidthPx / 2).toInt()
                    val popupY = (screenY - cardHeightPx - markerOffsetUpPx).toInt()
                    
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(popupX, popupY) }
                            .width(cardWidthDp)
                            .height(cardHeightDp)
                    ) {
                        // Small triangle arrow pointing down
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .align(Alignment.BottomCenter)
                                .offset(y = (-4).dp)
                                .graphicsLayer(rotationZ = 45f)
                                .background(
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                        
                        // Main Dialog Card
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            border = BorderStroke(1.5.dp, Color(0xFF3B82F6)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                // 1. Header (Name + Status + Dismiss Icon)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val icon = if (selectedMarker.category == "truck") {
                                            Icons.Default.Place
                                        } else {
                                            Icons.Default.LocationOn
                                        }
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = "Device Type",
                                            tint = Color(0xFF3B82F6),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = selectedMarker.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            modifier = Modifier.widthIn(max = 140.dp)
                                        )
                                        
                                        // Status Pill Badge
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = if (selectedMarker.status == "online") Color(0x3310B981) else Color(0x336B7280),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = selectedMarker.status.uppercase(),
                                                color = if (selectedMarker.status == "online") Color(0xFF10B981) else Color(0xFF9CA3AF),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 8.sp
                                            )
                                        }
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clickable { onMarkerClick(-1L) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close details",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(2.dp))
                                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                // 2. Telemetry Row 1: Speed and Altitude Block
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Speed Tile
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(6.dp),
                                            horizontalAlignment = Alignment.Start
                                        ) {
                                            Text("⚡ SPEED", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = String.format("%.1f km/h", selectedMarker.speedKmh),
                                                    color = if (selectedMarker.speedKmh > 0) Color(0xFF10B981) else Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                                if (selectedMarker.speedKmh > 0) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = "Direction",
                                                        tint = Color(0xFF10B981),
                                                        modifier = Modifier
                                                            .size(10.dp)
                                                            .graphicsLayer(rotationZ = selectedMarker.course)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    // Altitude Tile
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(6.dp),
                                            horizontalAlignment = Alignment.Start
                                        ) {
                                            Text("🏔️ ALTITUDE", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = String.format("%.0f m", selectedMarker.altitude),
                                                color = Color(0xFF38BDF8),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                // Telemetry Row 2: Accuracy & Plate/Course details
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Accuracy Tile
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.3f)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(6.dp),
                                            horizontalAlignment = Alignment.Start
                                        ) {
                                            Text("🎯 ACCURACY", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (selectedMarker.accuracy > 0) String.format("±%.1f m", selectedMarker.accuracy) else "High Precision",
                                                color = Color.White,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    
                                    // Plate Tile
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.3f)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(6.dp),
                                            horizontalAlignment = Alignment.Start
                                        ) {
                                            Text("🏷️ PLATE ID", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            val plateNumber = "ET 3-" + (10000 + selectedMarker.id % 90000) + " AA"
                                            Text(
                                                text = plateNumber,
                                                color = Color(0xFFF59E0B),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                // Coordinates & Last reported time
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    // Coordinates
                                    val locationValue = if (!selectedMarker.address.isNullOrBlank()) {
                                        "📍 ${selectedMarker.address}"
                                    } else {
                                        String.format("📍 %.5f, %.5f", selectedMarker.latitude, selectedMarker.longitude)
                                    }
                                    Text(
                                        text = locationValue,
                                        color = Color(0xFF3B82F6),
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                    
                                    // Last Update
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "Last active:", color = Color.Gray, fontSize = 9.sp)
                                            val friendlyTime = formatLastUpdate(selectedMarker.lastUpdate)
                                            Text(
                                                text = friendlyTime,
                                                color = Color.LightGray,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Control HUD (Zoom indicator overlay top-left)
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .background(
                        if (isDarkMode) Color(0xAA020617) else Color(0xAAFFFFFF),
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .align(Alignment.TopStart)
            ) {
                Text(
                    text = "Zoom: ${String.format("%.1f", zoom)}",
                    color = if (isDarkMode) Color.White else Color.Black,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}
