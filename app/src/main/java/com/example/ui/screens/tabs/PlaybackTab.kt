package com.example.ui.screens.tabs

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.CachedDevice
import com.example.data.model.Device
import com.example.data.model.Position
import com.example.ui.map.MapMarker
import com.example.ui.map.SlippyMap
import com.example.ui.screens.components.MapStyleControlLayer
import com.example.ui.viewmodel.TraccarViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun PlaybackTab(
    viewModel: TraccarViewModel,
    devices: List<Device>,
    cachedDevices: List<CachedDevice>,
    routeHistory: List<Position>,
    historyLoading: Boolean,
    selectedDeviceId: Long?,
    isPlaybackActive: Boolean,
    onSetPlaybackActive: (Boolean) -> Unit,
    playbackStepIndex: Int,
    onSetPlaybackStepIndex: (Int) -> Unit,
    playbackSpeedMultiplier: Int,
    onSetPlaybackSpeedMultiplier: (Int) -> Unit,
    playbackLoop: Boolean,
    onSetPlaybackLoop: (Boolean) -> Unit,
    animatedPlaybackLat: Double?,
    animatedPlaybackLng: Double?,
    animatedPlaybackCourse: Float?,
    isCameraFollowLocked: Boolean,
    onSetCameraFollowLocked: (Boolean) -> Unit,
    playbackRangeMode: String,
    onSetPlaybackRangeMode: (String) -> Unit,
    predefinedRange: String,
    onSetPredefinedRange: (String) -> Unit,
    customStartCalendar: Calendar,
    onSetCustomStartCalendar: (Calendar) -> Unit,
    customEndCalendar: Calendar,
    onSetCustomEndCalendar: (Calendar) -> Unit,
    mapProviderStyle: String,
    markerLabelStyle: String,
    markerIconStyle: String,
    customIconUri: String?,
    colorMoving: String,
    colorIdle: String,
    colorOffline: String,
    markerTriggerMode: String,
    infoCardFields: String,
    onNavigateBack: () -> Unit,
    onResetMapState: () -> Unit
) {
    DisposableEffect(Unit) {
        onDispose {
            onResetMapState()
        }
    }

    var playbackSelectedDeviceId by remember(selectedDeviceId) { mutableStateOf<Long?>(selectedDeviceId) }
    var isQueryConfigExpanded by remember(routeHistory.isEmpty()) { mutableStateOf(routeHistory.isEmpty()) }
    val context = LocalContext.current

    // Distance calculation helper
    val calculateDistance: (Double, Double, Double, Double) -> Double = { lat1, lon1, lat2, lon2 ->
        val r = 6371.0 // earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0)
        val c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a))
        r * c
    }

    val calculateCumulativeDistance: (List<Position>, Int) -> Double = { trail, endIndex ->
        var dist = 0.0
        val limit = endIndex.coerceAtMost(trail.size - 1)
        for (i in 0 until limit) {
            val p1 = trail.getOrNull(i)
            val p2 = trail.getOrNull(i + 1)
            if (p1 != null && p2 != null) {
                dist += calculateDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
            }
        }
        dist
    }

    // Date and Time picker triggers
    val selectDate = { isStart: Boolean ->
        val currentCal = if (isStart) customStartCalendar else customEndCalendar
        val datePickerDialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    time = currentCal.time
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                if (isStart) {
                    onSetCustomStartCalendar(newCal)
                } else {
                    onSetCustomEndCalendar(newCal)
                }
                onSetPlaybackActive(false)
                onSetPlaybackStepIndex(0)
            },
            currentCal.get(Calendar.YEAR),
            currentCal.get(Calendar.MONTH),
            currentCal.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    val selectTime = { isStart: Boolean ->
        val currentCal = if (isStart) customStartCalendar else customEndCalendar
        val timePickerDialog = TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val newCal = Calendar.getInstance().apply {
                    time = currentCal.time
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                }
                if (isStart) {
                    onSetCustomStartCalendar(newCal)
                } else {
                    onSetCustomEndCalendar(newCal)
                }
                onSetPlaybackActive(false)
                onSetPlaybackStepIndex(0)
            },
            currentCal.get(Calendar.HOUR_OF_DAY),
            currentCal.get(Calendar.MINUTE),
            true // 24-hour format
        )
        timePickerDialog.show()
    }

    var playbackMapLat by remember { mutableStateOf(8.7832) }
    var playbackMapLng by remember { mutableStateOf(38.7405) }
    var playbackMapZoom by remember { mutableStateOf(6f) }

    // Auto-fit camera bounds when route history is fetched
    LaunchedEffect(routeHistory) {
        if (routeHistory.isNotEmpty()) {
            val bounds = com.example.ui.map.calculatePositionBoundsFit(routeHistory)
            if (bounds != null) {
                playbackMapLat = bounds.first
                playbackMapLng = bounds.second
                playbackMapZoom = bounds.third
                onSetCameraFollowLocked(false)
            }
            isQueryConfigExpanded = false // Collapse query box for maximum map space
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Top Bar Navigation for Playback Screen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                val currentDeviceName = devices.find { it.id == playbackSelectedDeviceId }?.name
                    ?: cachedDevices.find { it.id == playbackSelectedDeviceId }?.name
                    ?: "Select Device"
                Column {
                    Text("Route Playback", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (playbackSelectedDeviceId != null) {
                        Text(currentDeviceName, color = Color(0xFF60A5FA), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (routeHistory.isNotEmpty()) {
                Button(
                    onClick = { isQueryConfigExpanded = !isQueryConfigExpanded },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isQueryConfigExpanded) Color(0xFF334155) else Color(0xFF2563EB)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isQueryConfigExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isQueryConfigExpanded) "Close" else "Filter", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 1. Sleek Query Configuration Deck (Expanded or Collapsed Banner)
        if (!isQueryConfigExpanded && routeHistory.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x990F172A)),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val rangeLabel = if (playbackRangeMode == "Predefined") {
                        "Period: $predefinedRange • ${routeHistory.size} crumbs"
                    } else {
                        val df = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault())
                        "${df.format(customStartCalendar.time)} - ${df.format(customEndCalendar.time)}"
                    }
                    Text(
                        text = rangeLabel,
                        color = Color(0xFF10B981),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Button(
                        onClick = { isQueryConfigExpanded = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(24.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Change", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Full Expanded Query Deck
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x990F172A)),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Historical Playback Query", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        if (routeHistory.isNotEmpty()) {
                            IconButton(
                                onClick = { isQueryConfigExpanded = false },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close Query", tint = Color.Gray, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    // Asset Unit Picker - Compact list with Search
                    val activeDevices = if (devices.isNotEmpty()) devices else cachedDevices.map { cached ->
                        Device(id = cached.id, name = cached.name, uniqueId = cached.uniqueId, status = cached.status, lastUpdate = cached.lastUpdate, category = cached.category)
                    }

                    if (activeDevices.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            var playbackDeviceSearch by remember { mutableStateOf("") }
                            OutlinedTextField(
                                value = playbackDeviceSearch,
                                onValueChange = { playbackDeviceSearch = it },
                                placeholder = { Text("Filter devices...", fontSize = 10.sp, color = Color.Gray) },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(14.dp)) },
                                trailingIcon = {
                                    if (playbackDeviceSearch.isNotEmpty()) {
                                        IconButton(onClick = { playbackDeviceSearch = "" }, modifier = Modifier.size(18.dp)) {
                                            Icon(Icons.Default.Clear, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0x441E293B),
                                    unfocusedContainerColor = Color(0x221E293B),
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    unfocusedBorderColor = Color(0xFF334155)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(40.dp)
                            )

                            val filteredPlaybackDevices = activeDevices.filter { dev ->
                                if (playbackDeviceSearch.isBlank()) true else {
                                    dev.name.contains(playbackDeviceSearch, ignoreCase = true) ||
                                    dev.uniqueId.contains(playbackDeviceSearch, ignoreCase = true) ||
                                    dev.model?.contains(playbackDeviceSearch, ignoreCase = true) == true ||
                                    dev.phone?.contains(playbackDeviceSearch, ignoreCase = true) == true ||
                                    dev.attributes["plate"]?.toString()?.contains(playbackDeviceSearch, ignoreCase = true) == true ||
                                    dev.attributes["license_plate"]?.toString()?.contains(playbackDeviceSearch, ignoreCase = true) == true ||
                                    dev.attributes["reg"]?.toString()?.contains(playbackDeviceSearch, ignoreCase = true) == true ||
                                    dev.attributes["customName"]?.toString()?.contains(playbackDeviceSearch, ignoreCase = true) == true ||
                                    dev.attributes["vehicleName"]?.toString()?.contains(playbackDeviceSearch, ignoreCase = true) == true
                                }
                            }

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(filteredPlaybackDevices) { dev ->
                                    val isSelected = playbackSelectedDeviceId == dev.id
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) Color(0xCC2563EB) else Color(0x661E293B)
                                        ),
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) Color(0xFF60A5FA) else Color(0xFF334155)
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .clickable {
                                                if (playbackSelectedDeviceId != dev.id) {
                                                    playbackSelectedDeviceId = dev.id
                                                    onSetPlaybackActive(false)
                                                    onSetPlaybackStepIndex(0)
                                                    viewModel.clearRouteHistory()
                                                }
                                            }
                                            .widthIn(min = 100.dp, max = 135.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(if (dev.status == "online") Color(0xFF10B981) else Color(0xFF94A3B8))
                                            )
                                            Column {
                                                Text(
                                                    text = dev.name,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val plateOrModel = dev.attributes["plate"]?.toString() ?: dev.attributes["license_plate"]?.toString() ?: dev.attributes["reg"]?.toString() ?: dev.model ?: dev.attributes["customName"]?.toString()
                                                if (!plateOrModel.isNullOrBlank()) {
                                                    Text(
                                                        text = plateOrModel,
                                                        fontSize = 9.sp,
                                                        color = if (isSelected) Color(0xFFE0F2FE) else Color(0xFF60A5FA),
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E293B))

                    // Time Selection Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Period Mode:", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                                .padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (playbackRangeMode == "Predefined") Color(0xFF3B82F6) else Color.Transparent)
                                    .clickable { onSetPlaybackRangeMode("Predefined") }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("Quick", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (playbackRangeMode == "Custom") Color(0xFF3B82F6) else Color.Transparent)
                                    .clickable { onSetPlaybackRangeMode("Custom") }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("Custom", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (playbackRangeMode == "Predefined") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("1h", "6h", "12h", "24h", "Today", "Yesterday").forEach { range ->
                                val isRangeSelected = predefinedRange == range
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            color = if (isRangeSelected) Color(0xFF3B82F6) else Color(0xFF1E293B),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { onSetPredefinedRange(range) }
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(range, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        // Custom Range Pickers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("From:", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = { selectDate(true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(24.dp),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(customStartCalendar.time), fontSize = 9.sp, color = Color.White)
                                }
                                Button(
                                    onClick = { selectTime(true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(24.dp),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(SimpleDateFormat("HH:mm", Locale.US).format(customStartCalendar.time), fontSize = 9.sp, color = Color.White)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("To:", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = { selectDate(false) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(24.dp),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(customEndCalendar.time), fontSize = 9.sp, color = Color.White)
                                }
                                Button(
                                    onClick = { selectTime(false) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(24.dp),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(SimpleDateFormat("HH:mm", Locale.US).format(customEndCalendar.time), fontSize = 9.sp, color = Color.White)
                                }
                            }
                        }
                    }

                    // Fetch Action Button
                    Button(
                        onClick = {
                            onSetPlaybackActive(false)
                            onSetPlaybackStepIndex(0)
                            val fromTime: Date
                            val toTime: Date
                            if (playbackRangeMode == "Predefined") {
                                val now = Date()
                                toTime = now
                                fromTime = when (predefinedRange) {
                                    "1h" -> Date(now.time - 1 * 60 * 60 * 1000L)
                                    "6h" -> Date(now.time - 6 * 60 * 60 * 1000L)
                                    "12h" -> Date(now.time - 12 * 60 * 60 * 1000L)
                                    "24h" -> Date(now.time - 24 * 60 * 60 * 1000L)
                                    "Today" -> {
                                        Calendar.getInstance().apply {
                                            set(Calendar.HOUR_OF_DAY, 0)
                                            set(Calendar.MINUTE, 0)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }.time
                                    }
                                    "Yesterday" -> {
                                        Calendar.getInstance().apply {
                                            add(Calendar.DAY_OF_YEAR, -1)
                                            set(Calendar.HOUR_OF_DAY, 0)
                                            set(Calendar.MINUTE, 0)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }.time
                                    }
                                    else -> Date(now.time - 12 * 60 * 60 * 1000L)
                                }
                            } else {
                                fromTime = customStartCalendar.time
                                toTime = customEndCalendar.time
                            }

                            playbackSelectedDeviceId?.let { id ->
                                viewModel.loadPlaybackHistoryRange(id, fromTime, toTime)
                            }
                        },
                        enabled = playbackSelectedDeviceId != null && !historyLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(34.dp)
                    ) {
                        if (historyLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Fetch & Stream Route History", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 2. Main Interactive Map & Playback View (Larger Map, Bottom Controller, Speed Profile Only)
        if (routeHistory.isNotEmpty() && playbackSelectedDeviceId != null) {
            val currentPoint = routeHistory.getOrNull(playbackStepIndex) ?: routeHistory.first()
            val playLat = if (animatedPlaybackLat != null) animatedPlaybackLat else currentPoint.latitude
            val playLng = if (animatedPlaybackLng != null) animatedPlaybackLng else currentPoint.longitude
            val playCourse = if (animatedPlaybackCourse != null) animatedPlaybackCourse else currentPoint.course.toFloat()
            val deviceName = devices.find { it.id == playbackSelectedDeviceId }?.name
                ?: cachedDevices.find { it.id == playbackSelectedDeviceId }?.name
                ?: "Playback Unit"

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Upper portion: SlippyMap (Larger map occupying main space)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                ) {
                    LaunchedEffect(playbackStepIndex, isCameraFollowLocked, isPlaybackActive) {
                        if (isCameraFollowLocked || isPlaybackActive) {
                            playbackMapLat = playLat
                            playbackMapLng = playLng
                            if (playbackMapZoom < 17.0f) {
                                playbackMapZoom = 17.5f
                            }
                        }
                    }

                    SlippyMap(
                        modifier = Modifier.fillMaxSize(),
                        initialCenterLat = playbackMapLat,
                        initialCenterLng = playbackMapLng,
                        initialZoom = playbackMapZoom,
                        recenterTriggerKey = if (isCameraFollowLocked || isPlaybackActive) playbackStepIndex else null,
                        onViewportChanged = { lat, lng, zm ->
                            if (!isCameraFollowLocked && !isPlaybackActive) {
                                playbackMapLat = lat
                                playbackMapLng = lng
                                playbackMapZoom = zm
                            }
                        },
                        markers = listOf(
                            MapMarker(
                                id = 99999 + playbackSelectedDeviceId!!,
                                name = deviceName,
                                latitude = playLat,
                                longitude = playLng,
                                course = playCourse,
                                status = "online",
                                speedKmh = currentPoint.speedKmh,
                                altitude = currentPoint.altitude,
                                lastUpdate = currentPoint.deviceTime,
                                address = currentPoint.address,
                                accuracy = currentPoint.accuracy
                            )
                        ),
                        routePath = routeHistory,
                        playbackStepIndex = playbackStepIndex,
                        selectedMarkerId = 99999 + playbackSelectedDeviceId!!,
                        isDarkMode = true,
                        mapStyle = mapProviderStyle,
                        markerLabelType = markerLabelStyle,
                        markerIconStyle = markerIconStyle,
                        customIconUri = customIconUri,
                        geofences = emptyList(),
                        colorMoving = colorMoving,
                        colorIdle = colorIdle,
                        colorOffline = colorOffline,
                        markerTriggerMode = markerTriggerMode,
                        infoCardFields = infoCardFields
                    )

                    MapStyleControlLayer(
                        mapProviderStyle = mapProviderStyle,
                        onStyleSelected = { viewModel.setMapProviderStyle(it) },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    )

                    // Camera & Location HUD overlay
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Max Tracking Zoom Button
                        Box(
                            modifier = Modifier
                                .background(if (playbackMapZoom >= 17.5f) Color(0xFF10B981) else Color(0xCC0F172A), RoundedCornerShape(6.dp))
                                .clickable {
                                    playbackMapLat = playLat
                                    playbackMapLng = playLng
                                    playbackMapZoom = 18.0f
                                    onSetCameraFollowLocked(true)
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Max Zoom", tint = Color.White, modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Max Zoom", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Fit Route Bounds Button
                        Box(
                            modifier = Modifier
                                .background(Color(0xCC0F172A), RoundedCornerShape(6.dp))
                                .clickable {
                                    val bounds = com.example.ui.map.calculatePositionBoundsFit(routeHistory)
                                    if (bounds != null) {
                                        playbackMapLat = bounds.first
                                        playbackMapLng = bounds.second
                                        playbackMapZoom = bounds.third
                                        onSetCameraFollowLocked(false)
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ZoomOutMap, contentDescription = "Fit Route", tint = Color(0xFF60A5FA), modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Fit Trail", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Follow Lock Toggle
                        Box(
                            modifier = Modifier
                                .background(if (isCameraFollowLocked) Color(0xCC2563EB) else Color(0xCC0F172A), RoundedCornerShape(6.dp))
                                .clickable { onSetCameraFollowLocked(!isCameraFollowLocked) }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isCameraFollowLocked) "Cam Locked" else "Cam Free",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Vertical Floating Zoom Controls on Bottom-Right of map
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xDD0F172A), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                            .clickable {
                                playbackMapLat = playLat
                                playbackMapLng = playLng
                                playbackMapZoom = (playbackMapZoom + 1f).coerceAtMost(18.0f)
                                onSetCameraFollowLocked(true)
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.White, modifier = Modifier.size(16.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xDD0F172A), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                            .clickable {
                                playbackMapZoom = (playbackMapZoom - 1f).coerceAtLeast(4.0f)
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Lower portion: Player Scrubbing Controller Deck
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x990F172A)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        // Telemetry Status Badge Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                val timeStr = currentPoint.deviceTime ?: "No Timestamp"
                                val formattedTime = if (timeStr.contains("T")) {
                                    timeStr.substringBefore("Z").replace("T", " ")
                                } else {
                                    timeStr
                                }
                                Text(
                                    text = formattedTime,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )

                                val totalDist = calculateCumulativeDistance(routeHistory, playbackStepIndex)
                                Text(
                                    text = "Crumb ${playbackStepIndex + 1}/${routeHistory.size} • Distance: ${String.format("%.2f", totalDist)} km",
                                    color = Color.Gray,
                                    fontSize = 9.5.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                                ) {
                                    Text(
                                        text = "${String.format("%.1f", currentPoint.speedKmh)} km/h",
                                        color = Color(0xFF10B981),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                                ) {
                                    Text(
                                        text = "${String.format("%.0f", currentPoint.altitude)}m Alt",
                                        color = Color(0xFF3B82F6),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Slider Scrubber Bar
                        Slider(
                            value = playbackStepIndex.toFloat(),
                            onValueChange = {
                                onSetPlaybackActive(false)
                                onSetPlaybackStepIndex(it.toInt())
                            },
                            valueRange = 0f..(routeHistory.size - 1).toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF10B981),
                                activeTrackColor = Color(0xFF10B981),
                                inactiveTrackColor = Color(0xFF1E293B)
                            ),
                            modifier = Modifier.fillMaxWidth().height(20.dp)
                        )

                        // Media Controls & Speed Multipliers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                // Reset / Stop
                                IconButton(
                                    onClick = {
                                        onSetPlaybackActive(false)
                                        onSetPlaybackStepIndex(0)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Reset Playback", tint = Color.Gray, modifier = Modifier.size(15.dp))
                                }

                                // Step Backward
                                IconButton(
                                    onClick = {
                                        onSetPlaybackActive(false)
                                        if (playbackStepIndex > 0) onSetPlaybackStepIndex(playbackStepIndex - 1)
                                    },
                                    enabled = playbackStepIndex > 0,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Step Back", tint = if (playbackStepIndex > 0) Color.LightGray else Color.DarkGray, modifier = Modifier.size(15.dp))
                                }

                                // Play / Pause Action
                                IconButton(
                                    onClick = {
                                        if (!isPlaybackActive && routeHistory.isNotEmpty() && playbackStepIndex >= routeHistory.size - 1) {
                                            onSetPlaybackStepIndex(0)
                                        }
                                        val willPlay = !isPlaybackActive
                                        if (willPlay) {
                                            onSetCameraFollowLocked(true)
                                            playbackMapLat = playLat
                                            playbackMapLng = playLng
                                            playbackMapZoom = 17.5f
                                        }
                                        onSetPlaybackActive(willPlay)
                                    },
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (isPlaybackActive) Color(0xFF10B981) else Color(0xFF2563EB))
                                        .size(34.dp)
                                ) {
                                    if (isPlaybackActive) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            modifier = Modifier.size(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.fillMaxHeight().width(3.dp).background(Color.White))
                                            Box(modifier = Modifier.fillMaxHeight().width(3.dp).background(Color.White))
                                        }
                                    } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }

                                // Step Forward
                                IconButton(
                                    onClick = {
                                        onSetPlaybackActive(false)
                                        if (playbackStepIndex < routeHistory.size - 1) onSetPlaybackStepIndex(playbackStepIndex + 1)
                                    },
                                    enabled = playbackStepIndex < routeHistory.size - 1,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.ArrowForward, contentDescription = "Step Forward", tint = if (playbackStepIndex < routeHistory.size - 1) Color.LightGray else Color.DarkGray, modifier = Modifier.size(15.dp))
                                }

                                // Loop Toggle
                                IconButton(
                                    onClick = { onSetPlaybackLoop(!playbackLoop) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Loop repeat", tint = if (playbackLoop) Color(0xFF10B981) else Color.Gray, modifier = Modifier.size(15.dp))
                                }
                            }

                            // Speed Multipliers
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                listOf(1, 2, 5, 10, 20, 50).forEach { s ->
                                    val active = playbackSpeedMultiplier == s
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (active) Color(0xFF10B981) else Color(0xFF1E293B),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .clickable { onSetPlaybackSpeedMultiplier(s) }
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text("${s}x", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Speed Profile Analytics Card (Only Speed Profile tab kept, Trip Summary & Trail Logs removed)
                val maxSpd = remember(routeHistory) { routeHistory.maxOfOrNull { it.speedKmh } ?: 0.0 }
                val avgSpd = remember(routeHistory) { if (routeHistory.isNotEmpty()) routeHistory.map { it.speedKmh }.average() else 0.0 }
                val stoppedCount = remember(routeHistory) { routeHistory.count { it.speedKmh < 3.0 } }
                val highSpeedCount = remember(routeHistory) { routeHistory.count { it.speedKmh > 80.0 } }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x990F172A)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Speed Profile", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("${routeHistory.size} telemetry crumbs", color = Color.Gray, fontSize = 9.sp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.weight(1f)) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    Text("MAX SPEED", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    Text("${String.format("%.1f", maxSpd)} km/h", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.weight(1f)) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    Text("AVG SPEED", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    Text("${String.format("%.1f", avgSpd)} km/h", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.weight(1f)) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    Text("IDLE / STOPPED", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    Text("$stoppedCount pts", color = Color(0xFFF59E0B), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }

                        if (highSpeedCount > 0) {
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF451A1A)), modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Speeding events detected (>80 km/h): $highSpeedCount crumbs recorded.", color = Color(0xFFFCA5A5), fontSize = 9.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else if (!isQueryConfigExpanded) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("No telemetry history currently loaded. Tap 'Query' above.", color = Color.Gray, fontSize = 11.sp)
            }
        }
    }
}
