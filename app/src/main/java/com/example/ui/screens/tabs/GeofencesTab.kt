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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Device
import com.example.ui.map.SlippyMap
import com.example.ui.screens.components.EmptyStateView
import com.example.ui.screens.components.MapStyleControlLayer
import com.example.ui.screens.components.StatusBadge
import com.example.ui.theme.MC
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text(
                text = viewModel.translate("geofence"),
                style = MaterialTheme.typography.headlineSmall,
                color = MC.TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Design custom polygonal boundaries or circular quarantine hubs directly onto the active map canvas, and sync them with the Traccar backend.",
                style = MaterialTheme.typography.bodySmall,
                color = MC.TextSecondary
            )
        }

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
                .height(300.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MC.Surface1),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MC.Surface1.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, MC.AccentPrimary.copy(alpha = 0.4f)),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = when (drawMode) {
                                "polygon" -> Icons.Default.Polyline
                                "circle" -> Icons.Default.Adjust
                                else -> Icons.Default.Edit
                            },
                            contentDescription = null,
                            tint = MC.AccentPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = when (drawMode) {
                                "polygon" -> "Polygon Mode: Tap map to plot vertices (${drawnPoints.size})"
                                "circle" -> "Circle Mode: Tap center, then tap edge to set radius"
                                else -> "Select a drawing tool to plot a new boundary"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MC.TextPrimary
                        )
                    }
                }

                // Floating GIS Draw toolbar panel
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(12.dp)
                        .width(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MC.Surface1.copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, MC.Surface3),
                    tonalElevation = 6.dp
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
                                imageVector = Icons.Default.Polyline,
                                contentDescription = "Draw Polygon",
                                tint = if (drawMode == "polygon") MC.StatusOnline else MC.TextSecondary
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MC.Surface3))

                        // Circle button
                        IconButton(
                            onClick = {
                                drawMode = if (drawMode == "circle") "none" else "circle"
                                drawnPoints.clear()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Adjust,
                                contentDescription = "Draw Circle",
                                tint = if (drawMode == "circle") MC.AccentPrimary else MC.TextSecondary
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MC.Surface3))

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
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset",
                                tint = MC.StatusOffline
                            )
                        }
                    }
                }
            }
        }

        // Geofence attributes & Device links
        Card(
            colors = CardDefaults.cardColors(containerColor = MC.Surface1),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Establish Hardware Boundaries & Alert Rules",
                    style = MaterialTheme.typography.titleSmall,
                    color = MC.TextPrimary
                )

                OutlinedTextField(
                    value = gfName,
                    onValueChange = { gfName = it },
                    label = { Text("Geofence Name") },
                    placeholder = { Text("e.g., Depot Alpha Safe Zone") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MC.TextPrimary,
                        unfocusedTextColor = MC.TextPrimary,
                        focusedBorderColor = MC.AccentPrimary,
                        unfocusedBorderColor = MC.Surface3,
                        focusedContainerColor = MC.Surface2,
                        unfocusedContainerColor = MC.Surface2
                    ),
                    shape = RoundedCornerShape(10.dp),
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
                        colors = ButtonDefaults.buttonColors(containerColor = MC.Surface2),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
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
                                Icon(
                                    imageVector = if (currentAssignedDevice != null) Icons.Default.DirectionsCar else Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MC.AccentPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = buttonLabelText,
                                    color = MC.TextPrimary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Open List",
                                tint = MC.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = isDeviceDropdownExpanded,
                        onDismissRequest = { isDeviceDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f).background(MC.Surface1)
                    ) {
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(Icons.Default.Language, contentDescription = null, tint = MC.AccentPrimary, modifier = Modifier.size(16.dp))
                            },
                            text = {
                                Text("All Fleet Vehicles (Global Zone)", color = MC.AccentPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            },
                            onClick = {
                                selectedDeviceIdForGeofence = null
                                isDeviceDropdownExpanded = false
                            }
                        )
                        devices.forEach { dev ->
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MC.TextSecondary, modifier = Modifier.size(16.dp))
                                },
                                text = {
                                    Text("${dev.name} [ID: ${dev.uniqueId}]", color = MC.TextPrimary, style = MaterialTheme.typography.bodyMedium)
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
                Text("Push Notification Triggers:", style = MaterialTheme.typography.labelSmall, color = MC.TextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = triggerOnEnter,
                        onClick = { triggerOnEnter = !triggerOnEnter },
                        leadingIcon = {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        label = { Text("Notify on Enter", style = MaterialTheme.typography.bodySmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MC.StatusOnline.copy(alpha = 0.2f),
                            selectedLabelColor = MC.StatusOnline,
                            selectedLeadingIconColor = MC.StatusOnline,
                            containerColor = MC.Surface2,
                            labelColor = MC.TextSecondary,
                            iconColor = MC.TextSecondary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = triggerOnExit,
                        onClick = { triggerOnExit = !triggerOnExit },
                        leadingIcon = {
                            Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        label = { Text("Notify on Exit", style = MaterialTheme.typography.bodySmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MC.StatusOffline.copy(alpha = 0.2f),
                            selectedLabelColor = MC.StatusOffline,
                            selectedLeadingIconColor = MC.StatusOffline,
                            containerColor = MC.Surface2,
                            labelColor = MC.TextSecondary,
                            iconColor = MC.TextSecondary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Circle Radius Slider & Quick Presets (when drawMode == "circle")
                if (drawMode == "circle") {
                    Column(
                        modifier = Modifier.fillMaxWidth().background(MC.Surface2, RoundedCornerShape(10.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Zone Radius Adjustment:", style = MaterialTheme.typography.bodySmall, color = MC.TextSecondary)
                            Text("${drawnCircleRadiusMeters.roundToInt()} meters", style = MaterialTheme.typography.bodySmall, color = MC.AccentPrimary, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = drawnCircleRadiusMeters.toFloat(),
                            onValueChange = { drawnCircleRadiusMeters = it.toDouble() },
                            valueRange = 100f..10000f,
                            colors = SliderDefaults.colors(
                                thumbColor = MC.AccentPrimary,
                                activeTrackColor = MC.AccentPrimary,
                                inactiveTrackColor = MC.Surface3
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(250, 500, 1000, 2500, 5000).forEach { preset ->
                                SuggestionChip(
                                    onClick = { drawnCircleRadiusMeters = preset.toDouble() },
                                    label = { Text(if (preset >= 1000) "${preset / 1000}km" else "${preset}m", style = MaterialTheme.typography.labelSmall) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MC.Surface3, labelColor = MC.TextPrimary),
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
                        colors = CardDefaults.cardColors(containerColor = MC.Surface2),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Boundary Structure", style = MaterialTheme.typography.labelSmall, color = MC.TextTertiary)
                            Text(
                                text = if (drawMode == "polygon") "GIS Polygon" else if (drawMode == "circle") "Circular Geo-Ring" else "Not Plotted Yet",
                                color = MC.TextPrimary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MC.Surface2),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Geographical Scale", style = MaterialTheme.typography.labelSmall, color = MC.TextTertiary)
                            Text(
                                text = if (drawMode == "polygon") {
                                    "${drawnPoints.size} nodes mapped"
                                } else if (drawMode == "circle" && drawnCircleCenter != null) {
                                    "${drawnCircleRadiusMeters.roundToInt()}m radius"
                                } else {
                                    "Awaiting drawings"
                                },
                                color = MC.AccentPrimary,
                                style = MaterialTheme.typography.bodySmall,
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
                    colors = ButtonDefaults.buttonColors(containerColor = MC.StatusOnline),
                    enabled = gfName.isNotBlank() && ((drawMode == "polygon" && drawnPoints.size >= 3) || (drawMode == "circle" && drawnCircleCenter != null)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MC.TextPrimary, modifier = Modifier.size(16.dp))
                        Text("Deploy & Sync Geofence", fontWeight = FontWeight.Bold, color = MC.TextPrimary)
                    }
                }
            }
        }

        // Geofences List
        Text(
            text = "Active Synced Fleet Geofences (${geofences.size})",
            style = MaterialTheme.typography.titleMedium,
            color = MC.TextPrimary
        )

        if (geofences.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Polyline,
                title = "No active geofences",
                subtitle = "Draw boundaries on the map canvas above to define and deploy virtual zones for your fleet."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(geofences) { gf ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (gf.isActive) MC.Surface1 else MC.Surface0),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    color = if (!gf.isActive) MC.Surface3.copy(alpha = 0.4f)
                                    else if (gf.type == "polygon") MC.StatusOnline.copy(alpha = 0.15f)
                                    else MC.AccentPrimary.copy(alpha = 0.15f),
                                    shape = CircleShape,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (gf.type == "polygon") Icons.Default.Polyline else Icons.Default.Adjust,
                                            contentDescription = null,
                                            tint = if (!gf.isActive) MC.TextTertiary
                                            else if (gf.type == "polygon") MC.StatusOnline
                                            else MC.AccentPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = gf.name,
                                            color = if (gf.isActive) MC.TextPrimary else MC.TextTertiary,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        StatusBadge(
                                            text = if (gf.isActive) "Active" else "Paused",
                                            color = if (gf.isActive) MC.StatusOnline else MC.TextTertiary
                                        )
                                    }
                                    
                                    if (gf.type == "polygon") {
                                        Text("GIS Polygon (${gf.points.size} vertices)", color = MC.StatusOnline, style = MaterialTheme.typography.labelSmall)
                                    } else {
                                        Text("Circular Zone (${gf.radiusMeters.roundToInt()}m radius)", color = MC.AccentPrimary, style = MaterialTheme.typography.labelSmall)
                                    }

                                    // Target Device display
                                    val targetDev = devices.find { d -> d.id == gf.targetDeviceId }
                                    Text(
                                        text = "Target: ${targetDev?.name ?: "All Fleet Vehicles"}",
                                        color = MC.TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    // Triggers display
                                    val triggerText = buildString {
                                        if (gf.triggerOnEnter) append("Enter ")
                                        if (gf.triggerOnExit) append("Exit")
                                        if (!gf.triggerOnEnter && !gf.triggerOnExit) append("Silent")
                                    }
                                    Text("Alert Triggers: $triggerText", color = MC.AccentCyan, style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = gf.isActive,
                                    onCheckedChange = { viewModel.toggleGeofenceActive(gf.id) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MC.StatusOnline,
                                        checkedTrackColor = MC.StatusOnline.copy(alpha = 0.3f),
                                        uncheckedThumbColor = MC.TextTertiary,
                                        uncheckedTrackColor = MC.Surface3
                                    )
                                )
                                IconButton(onClick = { viewModel.deleteGeofence(gf.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove Geofence Plan", tint = MC.StatusOffline)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
