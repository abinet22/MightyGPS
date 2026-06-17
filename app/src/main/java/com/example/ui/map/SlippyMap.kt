package com.example.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
    val category: String? = "car"
)

@Composable
fun SlippyMap(
    modifier: Modifier = Modifier,
    initialCenterLat: Double = 9.0192, // Default to Addis Ababa (Mighty GPS headquarters territory)
    initialCenterLng: Double = 38.7525,
    initialZoom: Float = 12f,
    markers: List<MapMarker> = emptyList(),
    routePath: List<Position> = emptyList(),
    selectedMarkerId: Long? = null,
    onMarkerClick: (Long) -> Unit = {},
    isDarkMode: Boolean = true,
    mapStyle: String = "mapbox_dark", // "google_road", "google_satellite", "mapbox_dark", "mapbox_light", "osm_classic"
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

    // Report any viewport changes back to the parent to keep the map state synchronized during pan, zoom or fly-to.
    LaunchedEffect(centerLat, centerLng, zoom) {
        onViewportChanged(centerLat, centerLng, zoom)
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
                val destZoom = 18.0f // Zoom to level 18 as requested by user
                
                val steps = 30
                for (i in 1..steps) {
                    val progress = i.toFloat() / steps
                    // Decelerate transition equation
                    val t = 1f - (1f - progress) * (1f - progress)
                    centerLat = startLat + (destLat - startLat) * t
                    centerLng = startLng + (destLng - startLng) * t
                    zoom = (startZoom + (destZoom - startZoom) * t).coerceIn(2.5f, 18.5f)
                    kotlinx.coroutines.delay(16) // ~60 FPS
                }
            }
        }
    }

    // Dynamic scale helper
    val tileSize = 256
    val currentZoomInt = zoom.roundToInt().coerceIn(2, 19)

    // Select tile URL based on chosen map style
    val tileUrlTemplate = when (mapStyle) {
        "google_road" -> "https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}"
        "google_satellite" -> "https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}"
        "mapbox_light" -> "https://api.mapbox.com/styles/v1/mapbox/light-v11/tiles/256/{z}/{x}/{y}?access_token=pk.eyJ1IjoiYWJpbmV0MTIzIiwiYSI6ImNrbWR3d3Y5NzJwbG8ycGp4bGU1bXBtaGsifQ.LIZpH0mev90pUGXewX6lww"
        "osm_classic" -> "https://a.tile.openstreetmap.org/{z}/{x}/{y}.png"
        else -> "https://api.mapbox.com/styles/v1/mapbox/dark-v11/tiles/256/{z}/{x}/{y}?access_token=pk.eyJ1IjoiYWJpbmV0MTIzIiwiYSI6ImNrbWR3d3Y5NzJwbG8ycGp4bGU1bXBtaGsifQ.LIZpH0mev90pUGXewX6lww" // default mapbox_dark
    }

    // Convert coordinates to tile pixel positions
    fun getPixelCoords(lat: Double, lng: Double, z: Int): Pair<Double, Double> {
        val totalTiles = (1 shl z).toDouble()
        val totalPixels = totalTiles * tileSize
        
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

    BoxWithConstraints(
        modifier = modifier
            .background(if (isDarkMode) Color(0xFF1E1E24) else Color(0xFFF0F2F5))
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoomFactor, _ ->
                    // Zoom adjustment
                    val rawZoom = zoom * zoomFactor
                    zoom = rawZoom.coerceIn(2.5f, 18.5f)

                    // Convert current geo center to global coordinates
                    val centerZ = zoom.roundToInt()
                    val (cx, cy) = getPixelCoords(centerLat, centerLng, centerZ)
                    
                    // Adjust global coordinate by the physical map pan offsets
                    val nx = cx - pan.x
                    val ny = cy - pan.y

                    // Re-calculate geo coordinate for the new center
                    val (newLat, newLng) = getCoordsFromPixels(nx, ny, centerZ)
                    centerLat = newLat.coerceIn(-85.05, 85.05)
                    centerLng = newLng.coerceIn(-180.0, 180.0)
                }
            }
    ) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight

        if (width <= 0 || height <= 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@BoxWithConstraints
        }

        // Center globals
        val (centerXPixel, centerYPixel) = getPixelCoords(centerLat, centerLng, currentZoomInt)

        // Find visible tile index span
        val startTileX = floor((centerXPixel - width / 2) / tileSize).toInt()
        val endTileX = ceil((centerXPixel + width / 2) / tileSize).toInt()
        val startTileY = floor((centerYPixel - height / 2) / tileSize).toInt()
        val endTileY = ceil((centerYPixel + height / 2) / tileSize).toInt()

        val maxTileIndex = (1 shl currentZoomInt) - 1

        // Inner gesture container to isolate tap events
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(markers, zoom, centerLat, centerLng, width, height, drawMode, drawnPoints, drawnCircleCenter) {
                    detectTapGestures { tapOffset ->
                        val tapX = tapOffset.x
                        val tapY = tapOffset.y

                        if (drawMode != "none") {
                            // Translate tap position to coordinates
                            val globalPx = centerXPixel - width / 2 + tapX
                            val globalPy = centerYPixel - height / 2 + tapY
                            val (tapLat, tapLng) = getCoordsFromPixels(globalPx, globalPy, currentZoomInt)

                            if (drawMode == "polygon") {
                                onDrawPointsChanged(drawnPoints + Pair(tapLat, tapLng))
                            } else if (drawMode == "circle") {
                                if (drawnCircleCenter == null) {
                                    onDrawCircleChanged(Pair(tapLat, tapLng), 1000.0)
                                } else {
                                    // Calculate distance from center to tap offset
                                    val dLat = Math.toRadians(tapLat - drawnCircleCenter.first)
                                    val dLon = Math.toRadians(tapLng - drawnCircleCenter.second)
                                    val a = sin(dLat / 2) * sin(dLat / 2) +
                                            cos(Math.toRadians(drawnCircleCenter.first)) * cos(Math.toRadians(tapLat)) *
                                            sin(dLon / 2) * sin(dLon / 2)
                                    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
                                    val radius = 6371000.0 * c
                                    onDrawCircleChanged(drawnCircleCenter, radius.coerceIn(50.0, 50000.0))
                                }
                            }
                        } else {
                            var clickedId: Long? = null
                            var minDistance = 120f // Tap tolerance in pixels
                            
                            markers.forEach { m ->
                                val (px, py) = getPixelCoords(m.latitude, m.longitude, currentZoomInt)
                                val dx = px - centerXPixel
                                val dy = py - centerYPixel
                                val screenX = (width / 2 + dx).toFloat()
                                val screenY = (height / 2 + dy).toFloat()
                                
                                val dist = sqrt((screenX - tapX).pow(2) + (screenY - tapY).pow(2))
                                if (dist < minDistance) {
                                    minDistance = dist
                                    clickedId = m.id
                                }
                            }
                            
                            if (clickedId != null) {
                                onMarkerClick(clickedId!!)
                            } else {
                                // Tap on map background deselects / closes popup
                                onMarkerClick(-1L)
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
                    
                    val screenX = (currentTileLeftPx - centerXPixel + width / 2).toFloat()
                    val screenY = (currentTileTopPx - centerYPixel + height / 2).toFloat()

                    androidx.compose.foundation.Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier
                            .offset(x = (screenX / 2.75f).dp, y = (screenY / 2.75f).dp)
                            .size(93.dp)
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
                    val dx = px - centerXPixel
                    val dy = py - centerYPixel
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

                // B. Draw Active Vehicle Markers
                markers.forEach { m ->
                    val screenOffset = geoToScreen(m.latitude, m.longitude)
                    
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

            // 3. Custom Mapbox GL style popup overlay card
            val selectedMarker = markers.find { it.id == selectedMarkerId }
            if (selectedMarker != null) {
                val density = LocalDensity.current
                val (px, py) = getPixelCoords(selectedMarker.latitude, selectedMarker.longitude, currentZoomInt)
                val dx = px - centerXPixel
                val dy = py - centerYPixel
                
                val screenX = (width / 2 + dx).toFloat()
                val screenY = (height / 2 + dy).toFloat()
                
                if (screenX >= 0 && screenX <= width && screenY >= 0 && screenY <= height) {
                    val cardWidthDp = 240.dp
                    val cardHeightDp = 150.dp
                    
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
                                    .padding(12.dp)
                            ) {
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
                                            maxLines = 1
                                        )
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
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                val plateNumber = "ET 3-" + (10000 + selectedMarker.id % 90000) + " AA"
                                val lastUpdateStr = if (selectedMarker.status == "online") "Just now" else "12 mins ago"
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Plate number", color = Color.Gray, fontSize = 10.sp)
                                    Text(
                                        text = plateNumber,
                                        color = Color(0xFFF59E0B),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "Speed", color = Color.Gray, fontSize = 10.sp)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = String.format("%.1f km/h", selectedMarker.speedKmh),
                                            color = if (selectedMarker.speedKmh > 0) Color(0xFF10B981) else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Direction",
                                            tint = Color.LightGray,
                                            modifier = Modifier
                                                .size(10.dp)
                                                .graphicsLayer(rotationZ = selectedMarker.course)
                                        )
                                    }
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Last update", color = Color.Gray, fontSize = 10.sp)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .background(
                                                    color = if (selectedMarker.status == "online") Color(0xFF10B981) else Color(0xFF6B7280),
                                                    shape = CircleShape
                                                )
                                        )
                                        Text(
                                            text = lastUpdateStr,
                                            color = Color.LightGray,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Coordinates", color = Color.Gray, fontSize = 10.sp)
                                    Text(
                                        text = String.format("%.5f, %.5f", selectedMarker.latitude, selectedMarker.longitude),
                                        color = Color(0xFF3B82F6),
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
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
