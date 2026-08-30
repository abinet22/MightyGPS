package com.example.ui.screens.tabs

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.db.CachedDevice
import com.example.data.model.Device
import com.example.data.model.Position
import com.example.ui.map.MapMarker
import com.example.ui.map.SlippyMap
import com.example.ui.screens.components.MapStyleControlLayer
import com.example.ui.viewmodel.TraccarViewModel
import com.example.ui.viewmodel.TraccarViewModel.CustomGeofence
import com.example.util.UnitFormatter

@Composable
fun MapTab(
    viewModel: TraccarViewModel,
    devices: List<Device>,
    cachedDevices: List<CachedDevice>,
    realtimePositions: Map<Long, Position>,
    selectedDeviceId: Long?,
    onSelectDevice: (Long?) -> Unit,
    routeHistory: List<Position>,
    isPlaybackActive: Boolean,
    onTogglePlayback: () -> Unit,
    playbackStepIndex: Int,
    onUpdatePlaybackStepIndex: (Int) -> Unit,
    animatedPlaybackLat: Double?,
    animatedPlaybackLng: Double?,
    animatedPlaybackCourse: Float?,
    persistentMapCenterLat: Double,
    persistentMapCenterLng: Double,
    persistentMapZoom: Float,
    mapRecenterTrigger: Int,
    isCameraFollowLocked: Boolean,
    onCameraFollowLockChanged: (Boolean) -> Unit,
    onViewportChanged: (Double, Double, Float) -> Unit,
    onUserInteraction: () -> Unit,
    mapProviderStyle: String,
    markerLabelStyle: String,
    markerIconStyle: String,
    customIconUri: String?,
    geofences: List<CustomGeofence>,
    isGeofenceLayerVisible: Boolean,
    colorMoving: String,
    colorIdle: String,
    colorOffline: String,
    markerTriggerMode: String,
    infoCardFields: String,
    historyLoading: Boolean,
    onResetMapState: () -> Unit,
    onOpenFleetDrawer: () -> Unit,
    onShowDeviceDetailSheet: () -> Unit,
    onLoadPlaybackHistory: (Long) -> Unit,
    onClearRouteHistory: () -> Unit,
    onCenterOnCoords: (Double, Double, Float) -> Unit
) {
    DisposableEffect(Unit) {
        onDispose {
            onResetMapState()
        }
    }

    val mapMarkers = viewModel.getMapMarkers(realtimePositions, devices)
    val selectedGeofenceDetail by viewModel.selectedGeofence.collectAsState()

    // If historical playback coordinates are loaded, draw them dynamically center on that trail
    val activePlaybackPos = if (routeHistory.isNotEmpty() && playbackStepIndex < routeHistory.size) {
        routeHistory[playbackStepIndex]
    } else null

    val playLat = if (routeHistory.isNotEmpty() && animatedPlaybackLat != null) animatedPlaybackLat else activePlaybackPos?.latitude
    val mapCenterLat = playLat ?: (if (selectedDeviceId != null && isCameraFollowLocked) {
        realtimePositions[selectedDeviceId]?.latitude ?: cachedDevices.find { it.id == selectedDeviceId }?.latitude
    } else null) ?: persistentMapCenterLat

    val playLng = if (routeHistory.isNotEmpty() && animatedPlaybackLng != null) animatedPlaybackLng else activePlaybackPos?.longitude
    val playCourse = if (routeHistory.isNotEmpty() && animatedPlaybackCourse != null) animatedPlaybackCourse else (activePlaybackPos?.course?.toFloat() ?: 0f)
    val mapCenterLng = playLng ?: (if (selectedDeviceId != null && isCameraFollowLocked) {
        realtimePositions[selectedDeviceId]?.longitude ?: cachedDevices.find { it.id == selectedDeviceId }?.longitude
    } else null) ?: persistentMapCenterLng

    // Filter geofences for selected device or universal fleet zones
    val displayedGeofences = remember(geofences, selectedDeviceId) {
        if (selectedDeviceId != null) {
            val deviceSpecific = geofences.filter { it.targetDeviceId == selectedDeviceId }
            val universal = geofences.filter { it.targetDeviceId == null }
            if (deviceSpecific.isNotEmpty() || universal.isNotEmpty()) {
                (deviceSpecific + universal).distinctBy { it.id }
            } else {
                geofences
            }
        } else {
            geofences
        }
    }

    // Compute live containment status for the selected device
    val activeContainedGeofence = remember(selectedDeviceId, realtimePositions, displayedGeofences) {
        if (selectedDeviceId != null) {
            val pos = realtimePositions[selectedDeviceId] ?: cachedDevices.find { it.id == selectedDeviceId }?.let {
                Position(0, it.id, "cache", "", "", true, it.latitude, it.longitude, 0.0, 0.0, 0.0, "")
            }
            if (pos != null && pos.latitude != 0.0 && pos.longitude != 0.0) {
                displayedGeofences.firstOrNull { gf ->
                    com.example.util.GeofenceUtils.isPositionInsideGeofence(pos.latitude, pos.longitude, gf)
                }
            } else null
        } else null
    }

    // Render dynamic custom SlippyMap
    val playDevName = selectedDeviceId?.let { id ->
        devices.find { it.id == id }?.name ?: cachedDevices.find { it.id == id }?.name
    } ?: "Playback Asset #${activePlaybackPos?.deviceId ?: 0L}"

    Box(modifier = Modifier.fillMaxSize()) {
        SlippyMap(
            modifier = Modifier.fillMaxSize(),
            initialCenterLat = if (playLat != null && isCameraFollowLocked) playLat else mapCenterLat,
            initialCenterLng = if (playLng != null && isCameraFollowLocked) playLng else mapCenterLng,
            initialZoom = if (playLat != null && isCameraFollowLocked) 17f else if (selectedDeviceId != null && isCameraFollowLocked) 17f else persistentMapZoom,
            playbackStepIndex = if (routeHistory.isNotEmpty()) playbackStepIndex else -1,
            markers = if (playLat != null && playLng != null && activePlaybackPos != null) {
                listOf(
                    MapMarker(
                        id = 99999 + activePlaybackPos.deviceId,
                        name = playDevName,
                        latitude = playLat,
                        longitude = playLng,
                        course = playCourse,
                        status = "online",
                        speedKmh = activePlaybackPos.speedKmh,
                        altitude = activePlaybackPos.altitude,
                        lastUpdate = activePlaybackPos.deviceTime,
                        address = activePlaybackPos.address,
                        accuracy = activePlaybackPos.accuracy
                    )
                )
            } else mapMarkers,
            routePath = routeHistory,
            selectedMarkerId = if (activePlaybackPos != null) 99999 + activePlaybackPos.deviceId else selectedDeviceId,
            recenterTriggerKey = mapRecenterTrigger,
            onMarkerClick = { id ->
                if (id == -1L) {
                    onSelectDevice(null)
                } else if (id < 99999) {
                    onSelectDevice(id)
                    onShowDeviceDetailSheet()
                }
            },
            isDarkMode = true,
            mapStyle = mapProviderStyle,
            markerLabelType = markerLabelStyle,
            markerIconStyle = markerIconStyle,
            customIconUri = customIconUri,
            geofences = displayedGeofences,
            isGeofenceLayerVisible = isGeofenceLayerVisible,
            onGeofenceClick = { gf -> viewModel.selectGeofence(gf) },
            colorMoving = colorMoving,
            colorIdle = colorIdle,
            colorOffline = colorOffline,
            markerTriggerMode = markerTriggerMode,
            infoCardFields = infoCardFields,
            onViewportChanged = { lat, lng, zm ->
                onViewportChanged(lat, lng, zm)
            },
            onUserInteraction = {
                onUserInteraction()
            }
        )

        // Route History Loading Banner
        if (historyLoading) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xEE0F172A)),
                border = BorderStroke(1.dp, Color(0xFF2563EB)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 135.dp)
                    .shadow(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color(0xFF3B82F6),
                        strokeWidth = 2.5.dp
                    )
                    Text(
                        "Loading route history & coordinates...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Live Playback Telemetry HUD Overlay (Speed, Timestamp, Progress)
        if (routeHistory.isNotEmpty() && activePlaybackPos != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xEE070B19)),
                border = BorderStroke(1.5.dp, Color(0xFF10B981)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 130.dp, start = 16.dp)
                    .shadow(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        val isMetric = viewModel.sessionManager.unitSystem == "metric"
                        Text(
                            text = UnitFormatter.speed(activePlaybackPos.speedKmh, isMetric).uppercase(),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Crumb ${playbackStepIndex + 1}/${routeHistory.size} • ${activePlaybackPos.deviceTime?.replace("T", " ")?.replace("Z", "") ?: ""}",
                            color = Color(0xFF94A3B8),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Top Right Floating Map Style & Geofence Layer Overlay Controls
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 12.dp, top = 12.dp)
                .zIndex(150f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapStyleControlLayer(
                mapProviderStyle = mapProviderStyle,
                onStyleSelected = { viewModel.setMapProviderStyle(it) },
                isGeofenceLayerVisible = isGeofenceLayerVisible,
                onToggleGeofenceLayer = { viewModel.toggleGeofenceLayer() },
                geofenceCount = displayedGeofences.size
            )

            // Quick Geofence Layer Pill Toggle
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isGeofenceLayerVisible) Color(0xEC0B132B) else Color(0xCC0F172A)
                ),
                border = BorderStroke(
                    1.dp,
                    if (isGeofenceLayerVisible) Color(0xFF10B981) else Color(0xFF475569)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.clickable { viewModel.toggleGeofenceLayer() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (isGeofenceLayerVisible) Color(0xFF10B981) else Color.Gray,
                                CircleShape
                            )
                    )
                    Text(
                        text = "Zones: ${displayedGeofences.size}",
                        color = if (isGeofenceLayerVisible) Color.White else Color.LightGray,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Live Vehicle Geofence Containment Status Banner
        if (activeContainedGeofence != null && selectedDeviceId != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .zIndex(100f)
                    .clickable { viewModel.selectGeofence(activeContainedGeofence) },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFA0B132B)),
                border = BorderStroke(1.5.dp, Color(0xFF10B981)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF10B981), CircleShape)
                    )
                    Text(
                        text = "📍 Inside Zone: ${activeContainedGeofence.name}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (activeContainedGeofence.speedLimit != null) {
                        Text(
                            text = "• ${activeContainedGeofence.speedLimit} km/h limit",
                            color = Color(0xFF6EE7B7),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        // Geofence Detail Inspector Modal
        if (selectedGeofenceDetail != null) {
            val gf = selectedGeofenceDetail!!
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .padding(bottom = 60.dp)
                    .fillMaxWidth(0.94f)
                    .zIndex(200f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFA0B132B)),
                border = BorderStroke(1.5.dp, Color(0xFF3B82F6)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        try { Color(android.graphics.Color.parseColor(gf.colorHex)) } catch (e: Exception) { Color(0xFF3B82F6) },
                                        CircleShape
                                    )
                            )
                            Column {
                                Text(
                                    text = gf.name,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (gf.type == "polygon") "Polygon Geofence (${gf.points.size} boundary vertices)" else "Circular Geofence (${gf.radiusMeters.toInt()}m radius)",
                                    color = Color(0xFF60A5FA),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.selectGeofence(null) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Geofence Details",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (gf.description.isNotBlank()) {
                        Text(
                            text = gf.description,
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Linked Asset & Speed Cap
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val targetDev = devices.find { it.id == gf.targetDeviceId }
                            ?: cachedDevices.find { it.id == gf.targetDeviceId }?.let {
                                Device(
                                    id = it.id,
                                    name = it.name,
                                    uniqueId = it.uniqueId,
                                    status = it.status,
                                    lastUpdate = it.lastUpdate,
                                    category = it.category
                                )
                            }
                        Text(
                            text = "Assigned: ${targetDev?.name ?: "All Fleet Assets"}",
                            color = Color(0xFF94A3B8),
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (gf.speedLimit != null) {
                            Text(
                                text = "Speed Cap: ${gf.speedLimit} km/h",
                                color = Color(0xFFFBBF24),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (gf.latitude != 0.0 && gf.longitude != 0.0) {
                                    onCenterOnCoords(gf.latitude, gf.longitude, 15.5f)
                                }
                            },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Center on Zone", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.selectGeofence(null) },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF475569))
                        ) {
                            Text("Dismiss", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Floating Fleet Devices Menu Button
        Surface(
            onClick = onOpenFleetDrawer,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 12.dp)
                .zIndex(150f)
                .testTag("btn_fleet_devices_menu"),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFA0F172A),
            border = BorderStroke(1.5.dp, Color(0xFF3B82F6)),
            shadowElevation = 10.dp,
            tonalElevation = 10.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Fleet Devices Sidebar",
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val devCount = if (devices.isNotEmpty()) devices.size else cachedDevices.size
                Text(
                    text = "Fleet ($devCount)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Overlay: Collapsible details HUD on top of map (shown ONLY when a device is selected)
        AnimatedVisibility(
            visible = selectedDeviceId != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xEC0B132B)),
                    shape = MaterialTheme.shapes.medium,
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (selectedDeviceId != null) {
                            val dev = devices.find { it.id == selectedDeviceId } ?: cachedDevices.find { it.id == selectedDeviceId }?.let { cached ->
                                Device(id = cached.id, name = cached.name, uniqueId = cached.uniqueId, status = cached.status, lastUpdate = cached.lastUpdate, category = cached.category)
                            }
                            val pos = realtimePositions[selectedDeviceId]

                            if (dev == null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF3B82F6), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Loading asset telemetry...", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dev.name,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            style = MaterialTheme.typography.headlineSmall
                                        )
                                        Text(
                                            text = "Telemetry ID: ${dev.uniqueId}",
                                            color = Color(0xFF64748B),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    // Speed badge
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                                    ) {
                                        val displaySpeed = pos?.speedKmh ?: cachedDevices.find { it.id == selectedDeviceId }?.speed ?: 0.0
                                        val isMetric = viewModel.sessionManager.unitSystem == "metric"
                                        Text(
                                            text = UnitFormatter.speed(displaySpeed, isMetric),
                                            color = Color(0xFF10B981),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(onClick = { onSelectDevice(null) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Deselect", tint = Color.Gray)
                                    }
                                }

                                HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 10.dp))

                                // Action commands block
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = onShowDeviceDetailSheet,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Driver & Vehicle Info", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Load 12h history command
                                    Button(
                                        onClick = {
                                            onLoadPlaybackHistory(dev.id)
                                        },
                                        enabled = !historyLoading,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (historyLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                                        } else {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF3B82F6))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Historical Playback", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                            }
                                        }
                                    }
                                }

                                // Playback interface active display
                                if (routeHistory.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp)
                                            .background(Color(0xCC0F172A), RoundedCornerShape(10.dp))
                                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                IconButton(
                                                    onClick = onTogglePlayback,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        if (isPlaybackActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        contentDescription = "Play/Pause",
                                                        tint = Color(0xFF3B82F6),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                val currentSpd = routeHistory.getOrNull(playbackStepIndex)?.speedKmh ?: 0.0
                                                Text(
                                                    text = "Pos ${playbackStepIndex + 1}/${routeHistory.size} • ${String.format("%.1f", currentSpd)} km/h",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                TextButton(
                                                    onClick = {
                                                        onLoadPlaybackHistory(dev.id)
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text("Reset", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8), maxLines = 1, softWrap = false)
                                                }
                                                TextButton(
                                                    onClick = onClearRouteHistory,
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text("Close", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                                }
                                            }
                                        }

                                        Slider(
                                            value = playbackStepIndex.toFloat(),
                                            onValueChange = {
                                                onUpdatePlaybackStepIndex(it.toInt())
                                            },
                                            valueRange = 0f..(routeHistory.size - 1).toFloat().coerceAtLeast(1f),
                                            colors = SliderDefaults.colors(
                                                thumbColor = Color(0xFF3B82F6),
                                                activeTrackColor = Color(0xFF3B82F6),
                                                inactiveTrackColor = Color(0xFF1E293B)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
