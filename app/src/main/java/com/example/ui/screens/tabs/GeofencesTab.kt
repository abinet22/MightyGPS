package com.example.ui.screens.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Device
import com.example.ui.map.SlippyMap
import com.example.ui.screens.components.MapStyleControlLayer
import com.example.ui.viewmodel.TraccarViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofencesTab(
    viewModel: TraccarViewModel,
    devices: List<Device>,
    mapProviderStyle: String,
    modifier: Modifier = Modifier
) {
    val geofences by viewModel.geofences.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = viewModel.translate("geofence"),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Design custom polygonal boundaries or circular quarantine hubs directly onto the active map canvas, and sync them with the Traccar backend.",
            color = Color.Gray,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Local states for custom GIS drawing board
        var drawMode by remember { mutableStateOf("none") } // "none", "polygon", "circle"
        val drawnPoints = remember { mutableStateListOf<Pair<Double, Double>>() }
        var drawnCircleCenter by remember { mutableStateOf<Pair<Double, Double>?>(null) }
        var drawnCircleRadiusMeters by remember { mutableStateOf(1500.0) }
        
        var gfName by remember { mutableStateOf("") }
        var selectedDeviceIdForGeofence by remember { mutableStateOf<Long?>(null) }
        var isDeviceDropdownExpanded by remember { mutableStateOf(false) }
        var triggerOnEnter by remember { mutableStateOf(true) }
        var triggerOnExit by remember { mutableStateOf(true) }

        // Map Drawing Sandbox Board
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF070B19)),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Embedded interactive map
                SlippyMap(
                    modifier = Modifier.fillMaxSize(),
                    initialCenterLat = 8.7832,
                    initialCenterLng = 38.7405,
                    initialZoom = 6.0f,
                    markers = emptyList(),
                    geofences = geofences,
                    drawMode = drawMode,
                    drawnPoints = drawnPoints,
                    onDrawPointsChanged = { newPoints ->
                        drawnPoints.clear()
                        drawnPoints.addAll(newPoints)
                    },
                    drawnCircleCenter = drawnCircleCenter,
                    drawnCircleRadiusMeters = drawnCircleRadiusMeters,
                    onDrawCircleChanged = { center, radius ->
                        drawnCircleCenter = center
                        drawnCircleRadiusMeters = radius
                    },
                    mapStyle = mapProviderStyle,
                    isDarkMode = true
                )

                MapStyleControlLayer(
                    mapProviderStyle = mapProviderStyle,
                    onStyleSelected = { viewModel.setMapProviderStyle(it) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )

                // Floating Info banner
                Card(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xCC0F172A)),
                    border = BorderStroke(1.dp, Color(0x553B82F6))
                ) {
                    Text(
                        text = when (drawMode) {
                            "polygon" -> "🔨 Google Maps Draw: Tap map to plot vertices (${drawnPoints.size})"
                            "circle" -> "🔨 Google Maps Draw: Click to place center, click edge to size"
                            else -> "📐 Choose a Sketch Tool from standard toolbar ➔"
                        },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }

                // Floating Google Maps Draw toolbar panel
                Card(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(12.dp)
                        .width(44.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xDD0F172A)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Polygon button
                        IconButton(
                            onClick = {
                                drawMode = if (drawMode == "polygon") "none" else "polygon"
                                drawnCircleCenter = null
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = "Draw Polygon",
                                tint = if (drawMode == "polygon") Color(0xFF10B981) else Color.White
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1E293B)))

                        // Circle button
                        IconButton(
                            onClick = {
                                drawMode = if (drawMode == "circle") "none" else "circle"
                                drawnPoints.clear()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Draw Circle",
                                tint = if (drawMode == "circle") Color(0xFF3B82F6) else Color.White
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1E293B)))

                        // Clear button
                        IconButton(
                            onClick = {
                                drawnPoints.clear()
                                drawnCircleCenter = null
                                drawMode = "none"
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Reset",
                                tint = Color(0xFFEF4444)
                            )
                        }
                    }
                }
            }
        }

        // Geofence attributes & Device links
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Establish Hardware Boundaries & Alert Rules", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = gfName,
                    onValueChange = { gfName = it },
                    label = { Text("Geofence Name") },
                    placeholder = { Text("e.g., Depot Alpha Safe Zone") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF1E293B)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Dynamic Device linkage Dropdown (assigned hardware devices or global)
                val currentAssignedDevice = devices.find { d -> d.id == selectedDeviceIdForGeofence }
                val buttonLabelText = if (currentAssignedDevice != null) {
                    "Target: ${currentAssignedDevice.name} (${currentAssignedDevice.uniqueId})"
                } else {
                    "Target: All Fleet Vehicles (Global Geofence)"
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { isDeviceDropdownExpanded = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = buttonLabelText,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Open List",
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = isDeviceDropdownExpanded,
                        onDismissRequest = { isDeviceDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f).background(Color(0xFF0F172A))
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text("🌐 All Fleet Vehicles (Global Zone)", color = Color(0xFF3B82F6), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            },
                            onClick = {
                                selectedDeviceIdForGeofence = null
                                isDeviceDropdownExpanded = false
                            }
                        )
                        devices.forEach { dev ->
                            DropdownMenuItem(
                                text = {
                                    Text("🚗 ${dev.name} [ID: ${dev.uniqueId}]", color = Color.White, fontSize = 13.sp)
                                },
                                onClick = {
                                    selectedDeviceIdForGeofence = dev.id
                                    isDeviceDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Push Notification Trigger Preferences
                Text("Push Notification Triggers:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = triggerOnEnter,
                        onClick = { triggerOnEnter = !triggerOnEnter },
                        label = { Text("🚨 Notify on Enter", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0x3310B981),
                            selectedLabelColor = Color(0xFF10B981),
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color.Gray
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = triggerOnExit,
                        onClick = { triggerOnExit = !triggerOnExit },
                        label = { Text("🚪 Notify on Exit", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0x33EF4444),
                            selectedLabelColor = Color(0xFFEF4444),
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color.Gray
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Circle Radius Slider & Quick Presets (when drawMode == "circle")
                if (drawMode == "circle") {
                    Column(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF070B19), RoundedCornerShape(8.dp)).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Zone Radius Adjustment:", color = Color.Gray, fontSize = 11.sp)
                            Text("${drawnCircleRadiusMeters.roundToInt()} meters", color = Color(0xFF3B82F6), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = drawnCircleRadiusMeters.toFloat(),
                            onValueChange = { drawnCircleRadiusMeters = it.toDouble() },
                            valueRange = 100f..10000f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF3B82F6),
                                activeTrackColor = Color(0xFF3B82F6),
                                inactiveTrackColor = Color(0xFF1E293B)
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(250, 500, 1000, 2500, 5000).forEach { preset ->
                                SuggestionChip(
                                    onClick = { drawnCircleRadiusMeters = preset.toDouble() },
                                    label = { Text(if (preset >= 1000) "${preset / 1000}km" else "${preset}m", fontSize = 9.sp) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFF1E293B), labelColor = Color.White),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Interactive readings readout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF070B19)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Boundary Structure", color = Color.Gray, fontSize = 9.sp)
                            Text(
                                text = if (drawMode == "polygon") "GIS Polygon Shape" else if (drawMode == "circle") "Circular Geo-Ring" else "Not Plotted Yet",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF070B19)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Geographical Scale", color = Color.Gray, fontSize = 9.sp)
                            Text(
                                text = if (drawMode == "polygon") {
                                    "${drawnPoints.size} nodes mapped"
                                } else if (drawMode == "circle" && drawnCircleCenter != null) {
                                    "${drawnCircleRadiusMeters.roundToInt()}m radius"
                                } else {
                                    "Awaiting drawings"
                                },
                                color = Color(0xFF3B82F6),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        if (gfName.isNotBlank()) {
                            if (drawMode == "polygon" && drawnPoints.size >= 3) {
                                val centerLat = drawnPoints.map { it.first }.average()
                                val centerLng = drawnPoints.map { it.second }.average()
                                viewModel.addGeofence(
                                    name = gfName,
                                    lat = centerLat,
                                    lng = centerLng,
                                    radius = 0.0,
                                    type = "polygon",
                                    points = drawnPoints.toList(),
                                    deviceId = selectedDeviceIdForGeofence,
                                    triggerOnEnter = triggerOnEnter,
                                    triggerOnExit = triggerOnExit
                                )
                                gfName = ""
                                drawnPoints.clear()
                                drawMode = "none"
                            } else if (drawMode == "circle" && drawnCircleCenter != null) {
                                viewModel.addGeofence(
                                    name = gfName,
                                    lat = drawnCircleCenter!!.first,
                                    lng = drawnCircleCenter!!.second,
                                    radius = drawnCircleRadiusMeters,
                                    type = "circle",
                                    points = emptyList(),
                                    deviceId = selectedDeviceIdForGeofence,
                                    triggerOnEnter = triggerOnEnter,
                                    triggerOnExit = triggerOnExit
                                )
                                gfName = ""
                                drawnCircleCenter = null
                                drawMode = "none"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    enabled = gfName.isNotBlank() && ((drawMode == "polygon" && drawnPoints.size >= 3) || (drawMode == "circle" && drawnCircleCenter != null)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("DEPLOY & BROADCAST GEOFENCE 🌐", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Geofences List
        Text("Active Sync'd Fleet Geovallas", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        if (geofences.isEmpty()) {
            Text("No active geofences configured on the Traccar channel.", color = Color.Gray, fontSize = 11.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(geofences) { gf ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (gf.isActive) Color(0xFF0F172A) else Color(0xFF070B19)),
                        border = BorderStroke(1.dp, if (gf.isActive) Color(0xFF1E293B) else Color(0x331E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (!gf.isActive) Color(0x2264748B)
                                        else if (gf.type == "polygon") Color(0x3310B981)
                                        else Color(0x333B82F6)
                                    ),
                                    shape = CircleShape,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = if (gf.type == "polygon") "⬡" else "◯",
                                            color = if (!gf.isActive) Color.Gray
                                            else if (gf.type == "polygon") Color(0xFF10B981)
                                            else Color(0xFF3B82F6),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(gf.name, color = if (gf.isActive) Color.White else Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        if (!gf.isActive) {
                                            Text("[Paused]", color = Color.Gray, fontSize = 10.sp)
                                        }
                                    }
                                    
                                    if (gf.type == "polygon") {
                                        Text("GIS Polygon (${gf.points.size} nodes)", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("Circular Geo-Ring (${gf.radiusMeters.roundToInt()}m radius)", color = Color(0xFF3B82F6), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Target Device display
                                    val targetDev = devices.find { d -> d.id == gf.targetDeviceId }
                                    Text(
                                        text = "Target: ${targetDev?.name ?: "All Fleet Vehicles"}",
                                        color = Color.LightGray,
                                        fontSize = 10.sp
                                    )

                                    // Triggers display
                                    val triggerText = buildString {
                                        if (gf.triggerOnEnter) append("🚨 Enter ")
                                        if (gf.triggerOnExit) append("🚪 Exit")
                                        if (!gf.triggerOnEnter && !gf.triggerOnExit) append("Silent")
                                    }
                                    Text("Alert Triggers: $triggerText", color = Color(0xFF38BDF8), fontSize = 9.sp)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = gf.isActive,
                                    onCheckedChange = { viewModel.toggleGeofenceActive(gf.id) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF10B981),
                                        checkedTrackColor = Color(0x3310B981)
                                    )
                                )
                                IconButton(onClick = { viewModel.deleteGeofence(gf.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove Geofence Plan", tint = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
