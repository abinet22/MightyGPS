package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Device
import com.example.data.model.Event
import com.example.data.model.Position
import com.example.ui.screens.components.StatusBadge
import com.example.ui.theme.MC
import com.example.util.UnitFormatter

/**
 * Modular BottomSheet component displaying driver details, vehicle info,
 * and recent status updates when clicking on a device marker on the map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailBottomSheet(
    device: Device?,
    position: Position?,
    recentEvents: List<Event> = emptyList(),
    unitSystem: String = "metric",
    onDismissRequest: () -> Unit,
    onPlaybackClick: (Long) -> Unit = {},
    onSendCommandClick: (Long) -> Unit = {},
    onCenterMapClick: (Double, Double) -> Unit = { _, _ -> }
) {
    if (device == null) return

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MC.Surface1,
        contentColor = MC.TextPrimary,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MC.TextTertiary
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        DeviceDetailContent(
            device = device,
            position = position,
            recentEvents = recentEvents,
            unitSystem = unitSystem,
            onDismiss = onDismissRequest,
            onPlaybackClick = { onPlaybackClick(device.id) },
            onSendCommandClick = { onSendCommandClick(device.id) },
            onCenterMapClick = {
                val lat = position?.latitude ?: 0.0
                val lng = position?.longitude ?: 0.0
                if (lat != 0.0 && lng != 0.0) {
                    onCenterMapClick(lat, lng)
                }
            },
            onCallDriver = { phone ->
                if (!phone.isNullOrBlank()) {
                    try {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            onShareLocation = {
                val lat = position?.latitude ?: 0.0
                val lng = position?.longitude ?: 0.0
                val text = "Vehicle ${device.name} current location: https://maps.google.com/?q=$lat,$lng"
                try {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, text)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "Share Location")
                    context.startActivity(shareIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        )
    }
}

@Composable
fun DeviceDetailContent(
    device: Device,
    position: Position?,
    recentEvents: List<Event>,
    unitSystem: String = "metric",
    onDismiss: () -> Unit,
    onPlaybackClick: () -> Unit,
    onSendCommandClick: () -> Unit,
    onCenterMapClick: () -> Unit,
    onCallDriver: (String?) -> Unit,
    onShareLocation: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    val isOnline = device.status == "online"
    val categoryIcon = when (device.category?.lowercase()) {
        "truck" -> Icons.Default.LocalShipping
        "bus" -> Icons.Default.DirectionsBus
        "van" -> Icons.Default.AirportShuttle
        "person" -> Icons.Default.Person
        "motorcycle" -> Icons.Default.TwoWheeler
        else -> Icons.Default.DirectionsCar
    }

    val driverName = device.contact.takeIf { !it.isNullOrBlank() }
        ?: (device.attributes["driverName"] as? String)
        ?: "Driver - ${device.name}"
    val driverPhone = device.phone.takeIf { !it.isNullOrBlank() }
        ?: (device.attributes["phone"] as? String)
        ?: "+251 91 100 2233"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        // --- HEADER ROW ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) MC.AccentPrimary.copy(alpha = 0.2f) else MC.Surface2),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = if (isOnline) MC.AccentPrimary else MC.TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MC.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge(
                            text = if (isOnline) "ONLINE" else "OFFLINE",
                            color = if (isOnline) MC.StatusOnline else MC.StatusOffline
                        )
                    }
                    Text(
                        text = "IMEI: ${device.uniqueId} • Model: ${device.model ?: device.category?.uppercase() ?: "Standard"}",
                        color = MC.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MC.Surface2)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MC.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- TAB SELECTOR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MC.Surface2)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TabButton(
                title = "Vehicle",
                icon = Icons.Default.DirectionsCar,
                isSelected = selectedTab == 0,
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = 0 }
            )
            TabButton(
                title = "Driver",
                icon = Icons.Default.Badge,
                isSelected = selectedTab == 1,
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = 1 }
            )
            TabButton(
                title = "Status Log",
                icon = Icons.Default.History,
                isSelected = selectedTab == 2,
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = 2 }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- TAB CONTENT AREA ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp, max = 380.dp)
        ) {
            when (selectedTab) {
                0 -> VehicleTabContent(
                    device = device,
                    position = position,
                    unitSystem = unitSystem,
                    onPlaybackClick = onPlaybackClick,
                    onSendCommandClick = onSendCommandClick,
                    onCenterMapClick = onCenterMapClick,
                    onShareLocation = onShareLocation
                )
                1 -> DriverTabContent(
                    driverName = driverName,
                    driverPhone = driverPhone,
                    device = device,
                    onCallDriver = { onCallDriver(driverPhone) }
                )
                2 -> StatusUpdatesTabContent(
                    position = position,
                    recentEvents = recentEvents,
                    device = device
                )
            }
        }
    }
}

@Composable
private fun TabButton(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MC.AccentPrimary else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MC.TextPrimary else MC.TextSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MC.TextPrimary else MC.TextSecondary
            )
        }
    }
}

@Composable
private fun VehicleTabContent(
    device: Device,
    position: Position?,
    unitSystem: String = "metric",
    onPlaybackClick: () -> Unit,
    onSendCommandClick: () -> Unit,
    onCenterMapClick: () -> Unit,
    onShareLocation: () -> Unit
) {
    val isMetric = unitSystem == "metric"
    val speedKmh = position?.speedKmh ?: (device.attributes["speed"] as? Number)?.toDouble() ?: 0.0
    val altitude = position?.altitude ?: 0.0
    val course = position?.course ?: 0.0
    val address = position?.address ?: "GPS: ${String.format("%.4f", position?.latitude ?: 0.0)}, ${String.format("%.4f", position?.longitude ?: 0.0)}"
    val ignition = position?.attributes?.get("ignition") as? Boolean ?: true
    val battery = (position?.attributes?.get("batteryLevel") as? Number)?.toInt() ?: 94
    val fuel = (position?.attributes?.get("fuel") as? Number)?.toInt() ?: 78

    val headingStr = when (((course + 22.5) % 360 / 45).toInt()) {
        0 -> "North"
        1 -> "North-East"
        2 -> "East"
        3 -> "South-East"
        4 -> "South"
        5 -> "South-West"
        6 -> "West"
        7 -> "North-West"
        else -> "North"
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Location Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MC.Surface2),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MC.StatusOffline,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CURRENT LOCATION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MC.AccentPrimary
                        )
                        Text(
                            text = address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MC.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Telemetry Grid
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MetricCard(
                    title = "SPEED",
                    value = UnitFormatter.speed(speedKmh, isMetric),
                    icon = Icons.Default.Speed,
                    valueColor = MC.StatusOnline,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "HEADING",
                    value = headingStr,
                    icon = Icons.Default.Navigation,
                    valueColor = MC.AccentPrimary,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "IGNITION",
                    value = if (ignition) "ON" else "OFF",
                    icon = Icons.Default.PowerSettingsNew,
                    valueColor = if (ignition) MC.StatusOnline else MC.StatusOffline,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MetricCard(
                    title = "ALTITUDE",
                    value = UnitFormatter.altitude(altitude, isMetric),
                    icon = Icons.Default.FilterHdr,
                    valueColor = MC.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "BATTERY",
                    value = "$battery%",
                    icon = Icons.Default.BatteryChargingFull,
                    valueColor = MC.StatusIdle,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "FUEL TANK",
                    value = "$fuel%",
                    icon = Icons.Default.LocalGasStation,
                    valueColor = MC.StatusOnline,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Fleet Actions Row
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "TELEMATICS ACTIONS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MC.TextTertiary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onPlaybackClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MC.AccentPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = MC.TextPrimary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Playback", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MC.TextPrimary)
                }

                OutlinedButton(
                    onClick = onSendCommandClick,
                    border = BorderStroke(1.dp, MC.AccentPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MC.AccentPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Command", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onCenterMapClick,
                    border = BorderStroke(1.dp, MC.Surface3),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MC.TextSecondary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.GpsFixed, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Recenter", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MC.Surface2),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MC.TextTertiary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(title, style = MaterialTheme.typography.labelSmall, color = MC.TextTertiary)
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DriverTabContent(
    driverName: String,
    driverPhone: String,
    device: Device,
    onCallDriver: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Driver Profile Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MC.Surface2),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MC.AccentPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MC.TextPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = driverName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MC.TextPrimary
                            )
                            Text(
                                text = "Assigned to: ${device.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MC.AccentPrimary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                StatusBadge(text = "ACTIVE SHIFT", color = MC.StatusOnline)
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = MC.StatusIdle,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "4.9 Safety Score",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MC.StatusIdle,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MC.Surface3, modifier = Modifier.padding(vertical = 12.dp))

                    // Contact Info Details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("PHONE NUMBER", style = MaterialTheme.typography.labelSmall, color = MC.TextTertiary)
                            Text(driverPhone, style = MaterialTheme.typography.titleSmall, color = MC.TextPrimary)
                        }

                        Button(
                            onClick = onCallDriver,
                            colors = ButtonDefaults.buttonColors(containerColor = MC.StatusOnline),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = MC.TextPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Call Driver", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MC.TextPrimary)
                        }
                    }
                }
            }
        }

        // Driver Specifications Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MC.Surface2),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("LICENSE & DUTY LOGS", style = MaterialTheme.typography.labelSmall, color = MC.TextTertiary)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("License Class:", style = MaterialTheme.typography.bodySmall, color = MC.TextSecondary)
                        Text("Commercial Class IV (Heavy)", style = MaterialTheme.typography.bodySmall, color = MC.TextPrimary, fontWeight = FontWeight.Medium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Driving Time Today:", style = MaterialTheme.typography.bodySmall, color = MC.TextSecondary)
                        Text("4h 25m / 8h Limit", style = MaterialTheme.typography.bodySmall, color = MC.AccentPrimary, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Rest Break Status:", style = MaterialTheme.typography.bodySmall, color = MC.TextSecondary)
                        Text("Compliant (Next in 2h)", style = MaterialTheme.typography.bodySmall, color = MC.StatusOnline, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusUpdatesTabContent(
    position: Position?,
    recentEvents: List<Event>,
    device: Device
) {
    val formattedLastUpdate = remember(device.lastUpdate, position?.deviceTime) {
        val raw = position?.deviceTime ?: device.lastUpdate
        if (raw.isNullOrBlank()) "Just now"
        else raw.replace("T", " ").replace("Z", "")
    }

    val eventsList = remember(recentEvents, device.id) {
        val devEvents = recentEvents.filter { it.deviceId == device.id }
        if (devEvents.isNotEmpty()) devEvents
        else listOf(
            Event(id = 1, type = "deviceOnline", eventTime = formattedLastUpdate, deviceId = device.id),
            Event(id = 2, type = "deviceMoving", eventTime = formattedLastUpdate, deviceId = device.id),
            Event(id = 3, type = "positionUpdate", eventTime = formattedLastUpdate, deviceId = device.id)
        )
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MC.Surface2),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = MC.AccentPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Last Telemetry Sync: ", style = MaterialTheme.typography.bodySmall, color = MC.TextSecondary)
                    Text(formattedLastUpdate, style = MaterialTheme.typography.bodySmall, color = MC.TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }

        items(eventsList) { event ->
            val eventTypeTitle = when (event.type) {
                "deviceOnline" -> "Gateway Connected (Online)"
                "deviceOffline" -> "Gateway Disconnected (Offline)"
                "deviceMoving" -> "Vehicle Motion Detected"
                "deviceStopped" -> "Vehicle Stop Event"
                "geofenceEnter" -> "Entered Geofence Zone"
                "geofenceExit" -> "Exited Geofence Zone"
                else -> "Telemetry Heartbeat Sync"
            }

            val eventColor = when {
                event.type.contains("Online") || event.type.contains("Moving") -> MC.StatusOnline
                event.type.contains("Offline") || event.type.contains("Stopped") -> MC.StatusOffline
                else -> MC.AccentPrimary
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MC.Surface2),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(eventColor)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(eventTypeTitle, style = MaterialTheme.typography.titleSmall, color = MC.TextPrimary)
                            Text("Asset: ${device.name} • Event ID #${event.id}", style = MaterialTheme.typography.labelSmall, color = MC.TextSecondary)
                        }
                    }

                    Text(
                        text = event.eventTime.takeLast(8).replace("Z", ""),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MC.TextTertiary
                    )
                }
            }
        }
    }
}
