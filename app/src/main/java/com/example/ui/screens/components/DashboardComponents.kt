package com.example.ui.screens.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.db.CachedAlert
import com.example.data.db.CachedDevice
import com.example.data.model.ConsolidatedAlert
import com.example.data.model.Device
import com.example.data.model.GeofenceAlert
import com.example.data.model.Position
import com.example.ui.theme.MC
import com.example.util.UnitFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun getMaintenanceStatus(deviceId: Long, odometerMeters: Double?): Triple<Boolean, Boolean, Double> {
    val odoKm = if (odometerMeters != null) {
        odometerMeters / 1000.0
    } else {
        ((deviceId * 7777.77) % 26000.0) + 2000.0
    }
    val distSinceService = odoKm % 5000.0
    val isDue = distSinceService >= 4000.0 || distSinceService < 200.0
    val isOverdue = distSinceService >= 4700.0 || distSinceService < 100.0
    return Triple(isDue, isOverdue, odoKm)
}

@Composable
fun FleetDevicesDrawerOverlay(
    isOpen: Boolean,
    onClose: () -> Unit,
    devices: List<Device>,
    cachedDevices: List<CachedDevice>,
    realtimePositions: Map<Long, Position>,
    selectedDeviceId: Long?,
    unitSystem: String = "metric",
    onSelectDevice: (Long, Double?, Double?) -> Unit,
    onSelectAllFleet: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf("ALL") }

    val fleetDevicesList: List<Device> = remember(devices, cachedDevices) {
        if (devices.isNotEmpty()) {
            devices
        } else {
            cachedDevices.map { cached ->
                Device(
                    id = cached.id,
                    name = cached.name,
                    uniqueId = cached.uniqueId,
                    status = cached.status,
                    lastUpdate = cached.lastUpdate,
                    category = cached.category
                )
            }
        }
    }

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it }),
        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it }),
        modifier = Modifier.fillMaxSize().zIndex(999f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Dimmed Backdrop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { onClose() }
            )

            // Sidebar Panel
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 360.dp)
                    .fillMaxWidth(0.85f)
                    .align(Alignment.CenterStart)
                    .clickable(enabled = false) {},
                color = MC.Surface1,
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                tonalElevation = 8.dp,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MC.Surface2, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = MC.AccentPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Fleet Devices",
                                    color = MC.TextPrimary,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                val onlineCount = fleetDevicesList.count { it.status == "online" }
                                val offlineCount = fleetDevicesList.size - onlineCount
                                Text(
                                    text = "$onlineCount Online • $offlineCount Offline",
                                    color = MC.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(32.dp)
                                .background(MC.Surface2, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Sidebar",
                                tint = MC.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Search field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search name, plate, IMEI...", color = MC.TextTertiary, style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MC.AccentPrimary, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MC.TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MC.AccentPrimary,
                            unfocusedBorderColor = MC.Surface3,
                            focusedContainerColor = MC.Surface2,
                            unfocusedContainerColor = MC.Surface2,
                            focusedTextColor = MC.TextPrimary,
                            unfocusedTextColor = MC.TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Status Filter Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val allCount = fleetDevicesList.size
                        val onlineCount = fleetDevicesList.count { it.status == "online" }
                        val offlineCount = allCount - onlineCount

                        listOf(
                            "ALL" to "All ($allCount)",
                            "ONLINE" to "Online ($onlineCount)",
                            "OFFLINE" to "Offline ($offlineCount)"
                        ).forEach { (filterKey, label) ->
                            val isSelected = filterStatus == filterKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MC.AccentPrimary else MC.Surface2)
                                    .clickable { filterStatus = filterKey }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) MC.TextPrimary else MC.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MC.Surface3)
                    Spacer(modifier = Modifier.height(8.dp))

                    // All Fleet Overview Button
                    Surface(
                        onClick = onSelectAllFleet,
                        modifier = Modifier.fillMaxWidth(),
                        color = if (selectedDeviceId == null) MC.AccentSecondary else MC.Surface2,
                        shape = RoundedCornerShape(10.dp),
                        border = if (selectedDeviceId == null) BorderStroke(1.dp, MC.AccentPrimary) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = MC.AccentPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "All Fleet View",
                                color = MC.TextPrimary,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val filteredList = remember(fleetDevicesList, searchQuery, filterStatus) {
                        fleetDevicesList.filter { dev ->
                            val matchesFilter = when (filterStatus) {
                                "ONLINE" -> dev.status == "online"
                                "OFFLINE" -> dev.status != "online"
                                else -> true
                            }
                            val plate = dev.attributes["plate"]?.toString() 
                                ?: dev.attributes["license_plate"]?.toString()
                                ?: dev.attributes["reg"]?.toString() ?: ""
                            val matchesSearch = searchQuery.isBlank() || 
                                dev.name.contains(searchQuery, ignoreCase = true) ||
                                dev.uniqueId.contains(searchQuery, ignoreCase = true) ||
                                plate.contains(searchQuery, ignoreCase = true)
                            matchesFilter && matchesSearch
                        }
                    }

                    if (filteredList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No matching devices found", color = MC.TextTertiary, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredList, key = { it.id }) { dev ->
                                val isSelected = selectedDeviceId == dev.id
                                val pos = realtimePositions[dev.id]
                                val speedVal = pos?.speedKmh ?: cachedDevices.find { it.id == dev.id }?.speed ?: 0.0
                                val isOnline = dev.status == "online"
                                val plate = dev.attributes["plate"]?.toString() 
                                    ?: dev.attributes["license_plate"]?.toString()
                                    ?: dev.attributes["reg"]?.toString()

                                Surface(
                                    onClick = {
                                        val targetLat = realtimePositions[dev.id]?.latitude 
                                            ?: cachedDevices.find { it.id == dev.id }?.latitude
                                        val targetLng = realtimePositions[dev.id]?.longitude 
                                            ?: cachedDevices.find { it.id == dev.id }?.longitude
                                        onSelectDevice(dev.id, targetLat, targetLng)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (isSelected) MC.AccentSecondary else MC.Surface2,
                                    shape = RoundedCornerShape(10.dp),
                                    border = if (isSelected) BorderStroke(1.dp, MC.AccentPrimary) else null
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(
                                                        if (isOnline) MC.StatusOnline.copy(alpha = 0.15f) else MC.TextTertiary.copy(alpha = 0.15f),
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = when (dev.category?.lowercase()) {
                                                        "truck" -> Icons.Default.LocalShipping
                                                        "bus" -> Icons.Default.DirectionsBus
                                                        "motorcycle", "bike" -> Icons.Default.TwoWheeler
                                                        else -> Icons.Default.DirectionsCar
                                                    },
                                                    contentDescription = null,
                                                    tint = if (isOnline) MC.StatusOnline else MC.TextSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = dev.name,
                                                    color = MC.TextPrimary,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (!plate.isNullOrBlank()) "Plate: $plate" else "IMEI: ${dev.uniqueId}",
                                                    color = MC.TextSecondary,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            if (isOnline) {
                                                val isMetric = unitSystem == "metric"
                                                Text(
                                                    text = UnitFormatter.speed(speedVal, isMetric),
                                                    color = MC.StatusOnline,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                StatusBadge(text = "Online", color = MC.StatusOnline)
                                            } else {
                                                StatusBadge(text = "Offline", color = MC.StatusOffline)
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
    }
}

@Composable
fun TrackScorecard(title: String, count: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MC.Surface1),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = MC.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text(count, color = color, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun DeviceRow(
    device: Device,
    position: Position?,
    isAdmin: Boolean = false,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MC.Surface1),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MC.Surface2, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (device.category?.lowercase()) {
                        "truck" -> Icons.Default.Place
                        "person" -> Icons.Default.AccountBox
                        else -> Icons.Default.LocationOn
                    },
                    contentDescription = null,
                    tint = when (device.status) {
                        "online" -> MC.StatusOnline
                        "offline" -> MC.StatusOffline
                        else -> MC.StatusIdle
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, color = MC.TextPrimary, style = MaterialTheme.typography.titleSmall)
                Text("IMEI: ${device.uniqueId}", color = MC.TextTertiary, style = MaterialTheme.typography.labelSmall)
                val plateOrModel = device.attributes["plate"]?.toString() ?: device.attributes["license_plate"]?.toString() ?: device.attributes["reg"]?.toString() ?: device.model ?: device.attributes["customName"]?.toString()
                if (!plateOrModel.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MC.Surface2,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "Plate/Model: $plateOrModel",
                                color = MC.AccentPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                val odoRaw = position?.attributes?.get("odometer") ?: position?.attributes?.get("totalDistance") ?: device.attributes["odometer"] ?: device.attributes["totalDistance"]
                val odoValue = when (odoRaw) {
                    is Number -> odoRaw.toDouble()
                    is String -> odoRaw.toDoubleOrNull()
                    else -> null
                }
                val (isDue, isOverdue, odoKm) = getMaintenanceStatus(device.id, odoValue)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Odometer/Maintenance",
                        tint = if (isOverdue) MC.StatusOffline else if (isDue) MC.StatusIdle else MC.StatusOnline,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Odo: ${String.format("%.1f", odoKm)} km" + if (isOverdue) " (OVERDUE)" else if (isDue) " (Service Soon)" else " (Engine Optimal)",
                        color = if (isOverdue) MC.StatusOffline else if (isDue) MC.StatusIdle else MC.StatusOnline,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isDue || isOverdue) FontWeight.Bold else FontWeight.Normal
                    )
                }

                position?.address?.let {
                    Text(
                        text = it,
                        color = MC.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                StatusBadge(
                    text = device.status.uppercase(),
                    color = if (device.status == "online") MC.StatusOnline else MC.StatusOffline
                )
            }
        }
    }
}

@Composable
fun OfflineDeviceRow(
    cached: CachedDevice,
    onSelect: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MC.Surface1),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MC.Surface2, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MC.TextSecondary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(cached.name, color = MC.TextPrimary, style = MaterialTheme.typography.titleSmall)
                Text("Offline Cache IMEI: ${cached.uniqueId}", color = MC.TextTertiary, style = MaterialTheme.typography.labelSmall)
                cached.address?.let {
                    Text(it, color = MC.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            StatusBadge(text = "CACHED", color = MC.StatusIdle)
        }
    }
}

@Composable
fun MetricBox(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MC.Surface1),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = MC.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = color, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun AlertCardRow(alert: CachedAlert) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MC.Surface1),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val alertColor = when (alert.alarmType) {
                "sos" -> MC.StatusOffline
                "overspeed" -> MC.StatusIdle
                else -> MC.AccentPrimary
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(alertColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (alert.alarmType) {
                        "sos" -> Icons.Default.Warning
                        "overspeed" -> Icons.Default.Speed
                        else -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = alertColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(alert.deviceName, color = MC.TextPrimary, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(alert.timestamp)),
                        color = MC.TextTertiary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(alert.message, color = MC.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun MapStyleControlLayer(
    mapProviderStyle: String,
    onStyleSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    isGeofenceLayerVisible: Boolean = true,
    onToggleGeofenceLayer: () -> Unit = {},
    geofenceCount: Int = 0
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isExpanded) {
                Card(
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(containerColor = MC.Surface1),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .size(46.dp)
                        .clickable { isExpanded = true }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Map Layers",
                            tint = MC.AccentPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            } else {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MC.Surface1),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MAP & LAYERS",
                                color = MC.AccentPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            IconButton(
                                onClick = { isExpanded = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Collapse",
                                    tint = MC.TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Text(
                            text = "OVERLAY LAYERS",
                            color = MC.AccentPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp, start = 4.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isGeofenceLayerVisible) MC.StatusOnline.copy(alpha = 0.15f) else MC.Surface2)
                                .clickable { onToggleGeofenceLayer() }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (isGeofenceLayerVisible) MC.StatusOnline else MC.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "Geofence Polygons",
                                        color = if (isGeofenceLayerVisible) MC.TextPrimary else MC.TextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (geofenceCount > 0) "$geofenceCount active zones" else "No zones loaded",
                                        color = if (isGeofenceLayerVisible) MC.StatusOnline else MC.TextTertiary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Switch(
                                checked = isGeofenceLayerVisible,
                                onCheckedChange = { onToggleGeofenceLayer() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MC.TextPrimary,
                                    checkedTrackColor = MC.StatusOnline
                                ),
                                modifier = Modifier.size(width = 38.dp, height = 24.dp)
                            )
                        }

                        HorizontalDivider(color = MC.Surface3)

                        val groupedOptions = listOf(
                            "GOOGLE MAPS STYLES" to listOf(
                                Triple("google_road", Icons.Default.Map, "Roadmap (default)"),
                                Triple("google_satellite", Icons.Default.Satellite, "Satellite"),
                                Triple("google_hybrid", Icons.Default.Layers, "Hybrid"),
                                Triple("google_terrain", Icons.Default.Terrain, "Terrain")
                            )
                        )

                        Column(
                            modifier = Modifier
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            groupedOptions.forEach { (category, stylesList) ->
                                Text(
                                    text = category,
                                    color = MC.AccentPrimary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp, bottom = 2.dp)
                                )
                                stylesList.forEach { (styleKey, icon, label) ->
                                    val isActive = mapProviderStyle == styleKey
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isActive) MC.AccentPrimary.copy(alpha = 0.2f) else Color.Transparent)
                                            .clickable {
                                                onStyleSelected(styleKey)
                                                isExpanded = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(icon, contentDescription = null, tint = if (isActive) MC.AccentPrimary else MC.TextSecondary, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = label,
                                            color = if (isActive) MC.AccentPrimary else MC.TextPrimary,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isActive) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MC.AccentPrimary,
                                                modifier = Modifier.size(14.dp)
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
}

@Composable
fun MapTypeToggle(
    mapProviderStyle: String,
    onStyleSelected: (String) -> Unit,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MC.Surface1,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val currentMapType = when {
                mapProviderStyle.contains("satellite") || mapProviderStyle.contains("hybrid") -> "satellite"
                mapProviderStyle.contains("terrain") || mapProviderStyle.contains("outdoors") -> "terrain"
                else -> "roadmap"
            }

            val options = listOf(
                Triple("roadmap", Icons.Default.Map, "Road"),
                Triple("satellite", Icons.Default.Satellite, "Satellite"),
                Triple("terrain", Icons.Default.Terrain, "Terrain")
            )

            options.forEach { (type, icon, displayName) ->
                val isSelected = currentMapType == type
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isSelected) MC.AccentPrimary else Color.Transparent)
                        .clickable {
                            val newStyle = when (type) {
                                "satellite" -> "google_satellite"
                                "terrain" -> "google_terrain"
                                else -> "google_road"
                            }
                            onStyleSelected(newStyle)
                            onFeedback("Switched to $displayName view")
                        }
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, tint = if (isSelected) MC.TextPrimary else MC.TextSecondary, modifier = Modifier.size(16.dp))
                    Text(
                        text = displayName,
                        color = if (isSelected) MC.TextPrimary else MC.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
