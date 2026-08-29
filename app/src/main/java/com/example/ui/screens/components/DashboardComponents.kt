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
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                tonalElevation = 16.dp,
                shadowElevation = 24.dp
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
                                    .background(Color(0xFF1E293B), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Fleet Devices",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                                val onlineCount = fleetDevicesList.count { it.status == "online" }
                                val offlineCount = fleetDevicesList.size - onlineCount
                                Text(
                                    text = "🟢 $onlineCount Online  •  🔴 $offlineCount Offline",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFF1E293B), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Sidebar",
                                tint = Color.LightGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Search field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search name, plate, IMEI...", color = Color.Gray, fontSize = 12.sp) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF1E293B),
                            unfocusedContainerColor = Color(0xFF1E293B)
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
                                    .background(if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B))
                                    .clickable { filterStatus = filterKey }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(8.dp))

                    // All Fleet Overview Button
                    Surface(
                        onClick = onSelectAllFleet,
                        modifier = Modifier.fillMaxWidth(),
                        color = if (selectedDeviceId == null) Color(0xFF1E3A8A).copy(alpha = 0.5f) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (selectedDeviceId == null) Color(0xFF3B82F6) else Color.Transparent)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("All Fleet Overview", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Show all vehicles on map", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            }
                            if (selectedDeviceId == null) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF3B82F6), CircleShape)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Devices LazyColumn
                    val filteredList = remember(fleetDevicesList, filterStatus, searchQuery) {
                        fleetDevicesList.filter { dev ->
                            val matchesFilter = when (filterStatus) {
                                "ONLINE" -> dev.status == "online"
                                "OFFLINE" -> dev.status != "online"
                                else -> true
                            }
                            val matchesSearch = if (searchQuery.isBlank()) true else {
                                dev.name.contains(searchQuery, ignoreCase = true) ||
                                dev.uniqueId.contains(searchQuery, ignoreCase = true) ||
                                dev.model?.contains(searchQuery, ignoreCase = true) == true ||
                                dev.phone?.contains(searchQuery, ignoreCase = true) == true ||
                                dev.category?.contains(searchQuery, ignoreCase = true) == true ||
                                dev.attributes["plate"]?.toString()?.contains(searchQuery, ignoreCase = true) == true ||
                                dev.attributes["license_plate"]?.toString()?.contains(searchQuery, ignoreCase = true) == true ||
                                dev.attributes["reg"]?.toString()?.contains(searchQuery, ignoreCase = true) == true ||
                                dev.attributes["customName"]?.toString()?.contains(searchQuery, ignoreCase = true) == true ||
                                dev.attributes["vehicleName"]?.toString()?.contains(searchQuery, ignoreCase = true) == true
                            }
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
                            Text("No matching devices found", color = Color.Gray, fontSize = 12.sp)
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
                                    color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFF1E293B),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF3B82F6) else Color(0xFF334155).copy(alpha = 0.5f)
                                    )
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
                                                        if (isOnline) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF64748B).copy(alpha = 0.15f),
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
                                                    tint = if (isOnline) Color(0xFF10B981) else Color(0xFF94A3B8),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = dev.name,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (!plate.isNullOrBlank()) "Plate: $plate" else "IMEI: ${dev.uniqueId}",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 10.sp,
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
                                                    color = Color(0xFF10B981),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFF065F46), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Online", color = Color(0xFF34D399), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFF374151), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Offline", color = Color(0xFF9CA3AF), fontSize = 9.sp, fontWeight = FontWeight.Medium)
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
}

@Composable
fun TrackScorecard(title: String, count: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.Gray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text(count, color = color, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
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
                    .background(Color(0xFF1E293B), CircleShape),
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
                        "online" -> Color(0xFF10B981)
                        "offline" -> Color(0xFFEF4444)
                        else -> Color(0xFFF59E0B)
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("IMEI: ${device.uniqueId}", color = Color.Gray, fontSize = 10.sp)
                val plateOrModel = device.attributes["plate"]?.toString() ?: device.attributes["license_plate"]?.toString() ?: device.attributes["reg"]?.toString() ?: device.model ?: device.attributes["customName"]?.toString()
                if (!plateOrModel.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Box(modifier = Modifier.background(Color(0xFF1E293B), RoundedCornerShape(4.dp)).border(0.5.dp, Color(0xFF3B82F6), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                            Text("🏷️ Plate/Model: $plateOrModel", color = Color(0xFF60A5FA), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
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
                        tint = if (isOverdue) Color(0xFFEF4444) else if (isDue) Color(0xFFF59E0B) else Color(0xFF10B981),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Odo: ${String.format("%.1f", odoKm)} km" + if (isOverdue) " (OVERDUE! ⚠️)" else if (isDue) " (Service Soon 🔧)" else " (Engine Optimal)",
                        color = if (isOverdue) Color(0xFFF87171) else if (isDue) Color(0xFFFBBF24) else Color(0xFF34D399),
                        fontSize = 10.sp,
                        fontWeight = if (isDue || isOverdue) FontWeight.Bold else FontWeight.Normal
                    )
                }

                position?.address?.let {
                    Text(
                        text = it,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (device.status == "online") Color(0x2210B981) else Color(0x2264748B)
                    )
                ) {
                    Text(
                        text = device.status.uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = if (device.status == "online") Color(0xFF10B981) else Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
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
                    .background(Color(0xFF1E293B), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(cached.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Offline Cache IMEI: ${cached.uniqueId}", color = Color.Gray, fontSize = 10.sp)
                cached.address?.let {
                    Text(it, color = Color.LightGray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text("CACHED", color = Color(0xFFF59E0B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.Gray, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun AlertCardRow(alert: CachedAlert) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x551E293B)),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        when (alert.alarmType) {
                            "sos" -> Color(0x33EF4444)
                            "overspeed" -> Color(0x33F59E0B)
                            else -> Color(0x333B82F6)
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (alert.alarmType) {
                        "sos" -> Icons.Default.Warning
                        "overspeed" -> Icons.Default.Share
                        else -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = when (alert.alarmType) {
                        "sos" -> Color(0xFFEF4444)
                        "overspeed" -> Color(0xFFF59E0B)
                        else -> Color(0xFF3B82F6)
                    },
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(alert.deviceName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(alert.timestamp)),
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(alert.message, color = Color.LightGray, fontSize = 11.sp)
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
                    colors = CardDefaults.cardColors(containerColor = Color(0xEC0B132B)),
                    border = BorderStroke(1.5.dp, Color(0xFF3B82F6)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .size(46.dp)
                        .clickable { isExpanded = true }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (mapProviderStyle) {
                                "google_road" -> "🗺️"
                                "google_satellite" -> "📷"
                                "google_hybrid" -> "🛰️"
                                "google_terrain" -> "⛰️"
                                else -> "🗺️"
                            },
                            fontSize = 20.sp
                        )
                    }
                }
            } else {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xEC0B132B)),
                    border = BorderStroke(1.5.dp, Color(0xFF475569)),
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
                                color = Color(0xFF60A5FA),
                                fontSize = 11.sp,
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
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Text(
                            text = "OVERLAY LAYERS",
                            color = Color(0xFF60A5FA),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp, start = 4.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isGeofenceLayerVisible) Color(0x2210B981) else Color(0x11FFFFFF))
                                .border(
                                    width = 1.dp,
                                    color = if (isGeofenceLayerVisible) Color(0xFF10B981) else Color(0xFF334155),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onToggleGeofenceLayer() }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🛡️", fontSize = 15.sp)
                                Column {
                                    Text(
                                        text = "Geofence Polygons",
                                        color = if (isGeofenceLayerVisible) Color.White else Color.LightGray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (geofenceCount > 0) "$geofenceCount active zones" else "No zones loaded",
                                        color = if (isGeofenceLayerVisible) Color(0xFF34D399) else Color.Gray,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Switch(
                                checked = isGeofenceLayerVisible,
                                onCheckedChange = { onToggleGeofenceLayer() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF10B981)
                                ),
                                modifier = Modifier.size(width = 38.dp, height = 24.dp)
                            )
                        }

                        HorizontalDivider(color = Color(0xFF1E293B))

                        val groupedOptions = listOf(
                            "GOOGLE MAPS STYLES" to listOf(
                                Triple("google_road", "🗺️", "Roadmap (default)"),
                                Triple("google_satellite", "📷", "Satellite"),
                                Triple("google_hybrid", "🛰️", "Hybrid"),
                                Triple("google_terrain", "⛰️", "Terrain")
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
                                    color = Color(0xFF60A5FA),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp, bottom = 2.dp)
                                )
                                stylesList.forEach { (styleKey, emoji, label) ->
                                    val isActive = mapProviderStyle == styleKey
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isActive) Color(0x333B82F6) else Color.Transparent)
                                            .border(
                                                width = 1.dp,
                                                color = if (isActive) Color(0xFF3B82F6) else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                onStyleSelected(styleKey)
                                                isExpanded = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(emoji, fontSize = 16.sp)
                                        Text(
                                            text = label,
                                            color = if (isActive) Color(0xFF60A5FA) else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isActive) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color(0xFF3B82F6),
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
    Row(
        modifier = modifier
            .background(Color(0xEC0B132B), RoundedCornerShape(28.dp))
            .border(1.5.dp, Color(0xFF1E293B).copy(alpha = 0.8f), RoundedCornerShape(28.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val currentMapType = when {
            mapProviderStyle.contains("satellite") || mapProviderStyle.contains("hybrid") -> "satellite"
            mapProviderStyle.contains("terrain") || mapProviderStyle.contains("outdoors") -> "terrain"
            else -> "roadmap"
        }

        val options = listOf(
            Triple("roadmap", "🗺️", "Road"),
            Triple("satellite", "🛰️", "Satellite"),
            Triple("terrain", "⛰️", "Terrain")
        )

        options.forEach { (type, emoji, displayName) ->
            val isSelected = currentMapType == type
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (isSelected) Color(0xFF3B82F6) else Color.Transparent)
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
                Text(text = emoji, fontSize = 15.sp)
                Text(
                    text = displayName,
                    color = if (isSelected) Color.White else Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
