package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.CachedAlert
import com.example.data.db.CachedDevice
import com.example.data.model.Device
import com.example.data.model.Position
import com.example.ui.map.SlippyMap
import com.example.ui.viewmodel.TraccarViewModel
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: TraccarViewModel
) {
    val devices by viewModel.devices.collectAsState()
    val realtimePositions by viewModel.realtimePositions.collectAsState()
    val isSocketConnected by viewModel.isSocketConnected.collectAsState()
    val cachedDevices by viewModel.cachedDevices.collectAsState()
    val cachedAlerts by viewModel.cachedAlerts.collectAsState()
    val selectedDeviceId by viewModel.selectedDeviceId.collectAsState()
    val routeHistory by viewModel.routeHistory.collectAsState()
    val historyLoading by viewModel.historyLoading.collectAsState()
    val feedbackMessage by viewModel.feedbackMessage.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncError by viewModel.syncError.collectAsState()

    // Collect SaaS customization settings
    val appLanguage by viewModel.appLanguage.collectAsState()
    val mapProviderStyle by viewModel.mapProviderStyle.collectAsState()
    val markerLabelStyle by viewModel.markerLabelStyle.collectAsState()
    val markerIconStyle by viewModel.markerIconStyle.collectAsState()
    val customIconUri by viewModel.customIconUri.collectAsState()
    val geofences by viewModel.geofences.collectAsState()
    val commandsLog by viewModel.commandsLog.collectAsState()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val cacheDir = context.cacheDir
                    val customFile = java.io.File(cacheDir, "custom_vehicle_icon.png")
                    val outputStream = java.io.FileOutputStream(customFile)
                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                    viewModel.setCustomIconUri(customFile.absolutePath)
                    viewModel.setMarkerIconStyle("custom")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    var currentTab by remember { mutableStateOf(1) } // Default to 1: Directly show map on login
    var searchQuery by remember { mutableStateOf("") }
    
    // Persistent map state across tab transitions to enable buttery smooth fly-to animations
    var persistentMapCenterLat by remember { mutableStateOf(9.0192) }
    var persistentMapCenterLng by remember { mutableStateOf(38.7525) }
    var persistentMapZoom by remember { mutableStateOf(11.5f) }
    
    // Playback Controller factors
    var isPlaybackActive by remember { mutableStateOf(false) }
    var playbackStepIndex by remember { mutableStateOf(0) }
    var playbackLoop by remember { mutableStateOf(false) }
    var isCameraFollowLocked by remember { mutableStateOf(true) }
    var playbackRangeMode by remember { mutableStateOf("Predefined") } // "Predefined" or "Custom"
    var predefinedRange by remember { mutableStateOf("12h") } // "1h", "6h", "12h", "24h", "Today", "Yesterday"
    
    // Calendar settings for custom dates
    var customStartCalendar by remember { 
        mutableStateOf(
            java.util.Calendar.getInstance().apply { 
                add(java.util.Calendar.HOUR_OF_DAY, -12) 
            }
        ) 
    }
    var customEndCalendar by remember { 
        mutableStateOf(
            java.util.Calendar.getInstance()
        ) 
    }

    // Bottom Sheet for adding asset
    var showAddDeviceSheet by remember { mutableStateOf(false) }
    var newDeviceName by remember { mutableStateOf("") }
    var newDeviceImei by remember { mutableStateOf("") }
    var newDeviceCategory by remember { mutableStateOf("Car") }

    // Clear feedbacks automatically
    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            scope.launch {
                kotlinx.coroutines.delay(4000)
                viewModel.clearFeedback()
            }
        }
    }

    var playbackSpeedMultiplier by remember { mutableStateOf(1) }

    // Auto increment step when historical playback is active
    LaunchedEffect(isPlaybackActive, playbackStepIndex, routeHistory, playbackSpeedMultiplier, playbackLoop) {
        if (isPlaybackActive && routeHistory.isNotEmpty()) {
            if (playbackStepIndex < routeHistory.size - 1) {
                val d = (800 / playbackSpeedMultiplier).coerceAtLeast(100).toLong()
                kotlinx.coroutines.delay(d)
                playbackStepIndex++
            } else {
                if (playbackLoop) {
                    kotlinx.coroutines.delay((800 / playbackSpeedMultiplier).coerceAtLeast(100).toLong())
                    playbackStepIndex = 0
                } else {
                    isPlaybackActive = false
                }
            }
        }
    }

    // Main scaffold design using Dark Mode colors
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (currentTab == 3 || currentTab == 4 || currentTab == 6) {
                        IconButton(onClick = { currentTab = 5 }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back to Settings",
                                tint = Color.White
                            )
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "MightyGPS Fleet Control",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF60A5FA)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSyncing) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            Color(0xFF60A5FA),
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Refreshing Assets...",
                                    fontSize = 10.sp,
                                    color = Color(0xFF60A5FA),
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                // Websocket connection status indicator
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            if (isSocketConnected) Color(0xFF10B981) else Color(0xFFEF4444),
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isSocketConnected) "Live Telemetry Connected" else "Reconnecting Gateway",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchInitialState() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync Fleet", tint = Color.LightGray)
                    }
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Log Out", tint = Color(0xFFEF4444))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                contentColor = Color.LightGray,
                tonalElevation = 6.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Asset Directory") },
                    label = { Text(viewModel.translate("devices"), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF3B82F6),
                        selectedTextColor = Color.White,
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B),
                        indicatorColor = Color(0xFF1E293B)
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = "Fleet tracking Map") },
                    label = { Text(viewModel.translate("active_fleet"), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF3B82F6),
                        selectedTextColor = Color.White,
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B),
                        indicatorColor = Color(0xFF1E293B)
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Historical Playback") },
                    label = { Text(viewModel.translate("playback"), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF3B82F6),
                        selectedTextColor = Color.White,
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B),
                        indicatorColor = Color(0xFF1E293B)
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 5,
                    onClick = { currentTab = 5 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "App Customizer") },
                    label = { Text(viewModel.translate("settings"), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF3B82F6),
                        selectedTextColor = Color.White,
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B),
                        indicatorColor = Color(0xFF1E293B)
                    )
                )
            }
        },
        floatingActionButton = {
            // Disabled: Customers requested removing add/remove device options for security
        },
        containerColor = Color(0xFF020617)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(Modifier.fillMaxSize()) {
                // Global Synchronization / API Request Loading Indicator
                AnimatedVisibility(
                    visible = isSyncing,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A))
                    ) {
                        LinearProgressIndicator(
                            color = Color(0xFF60A5FA),
                            trackColor = Color(0xFF1E293B),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                        )
                    }
                }

                // Global Synchronization API / Offline Sync Warning panel
                AnimatedVisibility(
                    visible = syncError != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    syncError?.let { err ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF7F1D1D), // Deep dark Red
                                contentColor = Color(0xFFFCA5A5)
                            ),
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, Color(0xFFB91C1C)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Sync Error",
                                            tint = Color(0xFFEF4444)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Sync / Connection Alert",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.clearSyncError() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            tint = Color(0xFFFCA5A5),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = err,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    TextButton(
                                        onClick = { 
                                            viewModel.clearSyncError()
                                            currentTab = 6
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF59E0B))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = Color(0xFFF59E0B)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Go to Reports",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFF59E0B)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(
                                        onClick = { 
                                            viewModel.clearSyncError()
                                            viewModel.fetchInitialState()
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Retry Sync",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Toast notification status alert overlay if available
                AnimatedVisibility(
                    visible = feedbackMessage != null,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    feedbackMessage?.let {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A8A)),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = it,
                                color = Color(0xFFBFDBFE),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(12.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // TAB CONTENTS
                when (currentTab) {
                    0 -> {
                        // TAB 0: DEVICE DIRECTORY (With fast search + offline list backup)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            // High Polish Status Scorecards list
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val total = devices.size
                                val online = devices.count { it.status == "online" }
                                val offline = devices.count { it.status == "offline" }

                                TrackScorecard(title = "Total Active", count = total.toString(), color = Color(0xFF3B82F6), modifier = Modifier.weight(1f))
                                TrackScorecard(title = "Moving/Online", count = online.toString(), color = Color(0xFF10B981), modifier = Modifier.weight(1f))
                                TrackScorecard(title = "Parked/Offline", count = offline.toString(), color = Color(0xFFEF4444), modifier = Modifier.weight(1f))
                            }

                            // Search bar inputs
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                                placeholder = { Text("Filter asset name, unique IMEI...") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF0F172A),
                                    unfocusedContainerColor = Color(0xFF0F172A),
                                    focusedBorderColor = Color(0xFF2563EB),
                                    unfocusedBorderColor = Color(0xFF1E293B)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            )

                            // Unified Offline support notice if data from database cache is shown
                            if (devices.isEmpty() && cachedDevices.isNotEmpty()) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0x33F59E0B)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                ) {
                                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Network offline. Loading local secure cached database coordinates.", fontSize = 11.sp, color = Color(0xFFFDE68A))
                                    }
                                }
                            }

                            // Rendering the actual list elements
                            if (devices.isEmpty() && cachedDevices.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(48.dp))
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("No active hardware devices configured.", color = Color.Gray, fontSize = 13.sp)
                                        Text("Click the (+) FAB button to commission this fleet's first asset tracker.", color = Color.Gray, fontSize = 11.sp)
                                    }
                                }
                            } else {
                                val filteredDevices = devices.filter {
                                    it.name.contains(searchQuery, ignoreCase = true) || it.uniqueId.contains(searchQuery, ignoreCase = true)
                                }

                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (filteredDevices.isNotEmpty()) {
                                        items(filteredDevices) { device ->
                                            val position = realtimePositions[device.id]
                                            DeviceRow(
                                                device = device,
                                                isAdmin = viewModel.sessionManager.isAdmin,
                                                position = position,
                                                onSelect = {
                                                    viewModel.selectDevice(device.id)
                                                    currentTab = 1 // slide automatically to layout map
                                                },
                                                onDelete = {
                                                    viewModel.removeDevice(device.id, device.name)
                                                }
                                            )
                                        }
                                    } else {
                                        // Load cached items fallback
                                        val filteredOffline = cachedDevices.filter {
                                            it.name.contains(searchQuery, ignoreCase = true) || it.uniqueId.contains(searchQuery, ignoreCase = true)
                                        }
                                        items(filteredOffline) { cached ->
                                            OfflineDeviceRow(
                                                cached = cached,
                                                onSelect = {
                                                    viewModel.selectDevice(cached.id)
                                                    currentTab = 1
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // TAB 1: FLEET TRACKING MAP & HISTORICAL PLAYBACK HUD
                        Box(modifier = Modifier.fillMaxSize()) {
                            val mapMarkers = viewModel.getMapMarkers(realtimePositions, devices)
                            
                            // If historical playback coordinates are loaded, draw them dynamically center on that trail
                            val activePlaybackPos = if (routeHistory.isNotEmpty() && playbackStepIndex < routeHistory.size) {
                                routeHistory[playbackStepIndex]
                            } else null

                            val mapCenterLat = activePlaybackPos?.latitude ?: selectedDeviceId?.let { id ->
                                realtimePositions[id]?.latitude ?: cachedDevices.find { it.id == id }?.latitude
                            } ?: persistentMapCenterLat

                            val mapCenterLng = activePlaybackPos?.longitude ?: selectedDeviceId?.let { id ->
                                realtimePositions[id]?.longitude ?: cachedDevices.find { it.id == id }?.longitude
                            } ?: persistentMapCenterLng

                            // Render dynamic custom SlippyMap
                            SlippyMap(
                                modifier = Modifier.fillMaxSize(),
                                initialCenterLat = if (activePlaybackPos != null) mapCenterLat else persistentMapCenterLat,
                                initialCenterLng = if (activePlaybackPos != null) mapCenterLng else persistentMapCenterLng,
                                initialZoom = if (activePlaybackPos != null) 15f else persistentMapZoom,
                                markers = if (activePlaybackPos != null) {
                                    listOf(
                                        com.example.ui.map.MapMarker(
                                            id = 99999 + activePlaybackPos.deviceId,
                                            name = "Playback (Asset ${activePlaybackPos.deviceId})",
                                            latitude = activePlaybackPos.latitude,
                                            longitude = activePlaybackPos.longitude,
                                            course = activePlaybackPos.course.toFloat(),
                                            status = "online",
                                            speedKmh = activePlaybackPos.speedKmh
                                        )
                                    )
                                } else mapMarkers,
                                routePath = routeHistory,
                                selectedMarkerId = if (activePlaybackPos != null) 99999 + activePlaybackPos.deviceId else selectedDeviceId,
                                onMarkerClick = { id ->
                                    if (id == -1L) {
                                        viewModel.selectDevice(null)
                                    } else if (id < 99999) {
                                        viewModel.selectDevice(id)
                                    }
                                },
                                isDarkMode = true,
                                mapStyle = mapProviderStyle,
                                markerLabelType = markerLabelStyle,
                                markerIconStyle = markerIconStyle,
                                customIconUri = customIconUri,
                                geofences = geofences,
                                onViewportChanged = { lat, lng, zm ->
                                    if (activePlaybackPos == null) {
                                        persistentMapCenterLat = lat
                                        persistentMapCenterLng = lng
                                        persistentMapZoom = zm
                                    }
                                }
                            )

                            // Floating Map Style/Control Layer Overlay
                            MapStyleControlLayer(
                                mapProviderStyle = mapProviderStyle,
                                onStyleSelected = { viewModel.setMapProviderStyle(it) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                            )

                            // Overlay: Collapsible details HUD on top of map
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xEC0B132B)),
                                    shape = MaterialTheme.shapes.medium,
                                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        if (selectedDeviceId != null) {
                                            val dev = devices.find { it.id == selectedDeviceId }
                                                ?: devices.firstOrNull() // fallback
                                            val pos = realtimePositions[dev?.id]
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = dev?.name ?: "Unknown Vehicle",
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color.White,
                                                        fontSize = 16.sp
                                                    )
                                                    Text(
                                                        text = "Telemetry ID: ${dev?.uniqueId ?: "Unknown"}",
                                                        color = Color(0xFF64748B),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                // Speed badge
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                                                ) {
                                                    Text(
                                                        text = "${String.format("%.1f", pos?.speedKmh ?: 0.0)} km/h",
                                                        color = Color(0xFF10B981),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                IconButton(onClick = { viewModel.selectDevice(null) }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Deselect", tint = Color.Gray)
                                                }
                                            }

                                            HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 10.dp))

                                            // Action commands block
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Load 12h history command
                                                Button(
                                                    onClick = {
                                                        dev?.let { viewModel.loadPlaybackHistory(it.id) }
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
                                                            Text("Historical Playback", fontSize = 11.sp, color = Color.White)
                                                        }
                                                    }
                                                }
                                            }

                                            // Playback interface active display
                                            if (routeHistory.isNotEmpty()) {
                                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        IconButton(onClick = { isPlaybackActive = !isPlaybackActive }) {
                                                            Icon(
                                                                if (isPlaybackActive) Icons.Default.Favorite else Icons.Default.PlayArrow,
                                                                contentDescription = "Play/Pause",
                                                                tint = Color(0xFF3B82F6)
                                                            )
                                                        }
                                                        Text(
                                                            text = "Position ${playbackStepIndex + 1}/${routeHistory.size} - Speed: ${String.format("%.1f", routeHistory.getOrNull(playbackStepIndex)?.speedKmh ?: 0.0)} km/h",
                                                            fontSize = 11.sp,
                                                            color = Color.White
                                                        )
                                                        TextButton(onClick = {
                                                            isPlaybackActive = false
                                                            playbackStepIndex = 0
                                                            viewModel.loadPlaybackHistory(dev?.id ?: -1) // clear or reset
                                                        }) {
                                                            Text("Reset", fontSize = 11.sp)
                                                        }
                                                    }
                                                    
                                                    Slider(
                                                        value = playbackStepIndex.toFloat(),
                                                        onValueChange = {
                                                            isPlaybackActive = false
                                                            playbackStepIndex = it.toInt()
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
                                        } else {
                                            // Bottom Up Active Fleet Scroller when no device is selected
                                            var isFleetScrollerExpanded by remember { mutableStateOf(false) }
                                            var scrollerSearchQuery by remember { mutableStateOf("") }

                                            Column(
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                // Tab Header to slide/click to toggle
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xEC030712)),
                                                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { isFleetScrollerExpanded = !isFleetScrollerExpanded }
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 10.dp, horizontal = 16.dp)
                                                    ) {
                                                        // Small drag handle visual indicator
                                                        Box(
                                                            modifier = Modifier
                                                                .size(width = 40.dp, height = 4.dp)
                                                                .background(Color.Gray, shape = RoundedCornerShape(2.dp))
                                                        )
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(Icons.Default.List, contentDescription = null, tint = Color(0xFF10B981))
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Text(
                                                                    text = "Active Fleet Gateway Scroller",
                                                                    color = Color.White,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 14.sp
                                                                 )
                                                            }
                                                            val deviceCount = if (devices.isNotEmpty()) devices.size else cachedDevices.size
                                                            Text(
                                                                text = "$deviceCount Devices",
                                                                color = Color(0xFF10B981),
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 12.sp,
                                                                modifier = Modifier
                                                                    .background(Color(0x3310B981), RoundedCornerShape(12.dp))
                                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                AnimatedVisibility(visible = isFleetScrollerExpanded) {
                                                    Card(
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xEC030712)),
                                                        shape = RoundedCornerShape(0.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(280.dp)
                                                    ) {
                                                        Column(modifier = Modifier.padding(12.dp)) {
                                                            // Search bar inside bottom up scroller
                                                            OutlinedTextField(
                                                                value = scrollerSearchQuery,
                                                                onValueChange = { scrollerSearchQuery = it },
                                                                placeholder = { Text("Search fleet asset...", color = Color.Gray, fontSize = 12.sp) },
                                                                singleLine = true,
                                                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(bottom = 12.dp),
                                                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                                                                colors = OutlinedTextFieldDefaults.colors(
                                                                    focusedBorderColor = Color(0xFF10B981),
                                                                    unfocusedBorderColor = Color(0xFF374151)
                                                                )
                                                            )

                                                            val activeList = if (devices.isNotEmpty()) devices else cachedDevices.map { cached ->
                                                                com.example.data.model.Device(id = cached.id, name = cached.name, uniqueId = cached.uniqueId, status = cached.status, lastUpdate = cached.lastUpdate, category = cached.category)
                                                            }

                                                            val filteredActive = activeList.filter {
                                                                it.name.contains(scrollerSearchQuery, ignoreCase = true) || it.uniqueId.contains(scrollerSearchQuery, ignoreCase = true)
                                                            }

                                                            if (filteredActive.isEmpty()) {
                                                                Box(
                                                                    modifier = Modifier.fillMaxSize(),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text("No devices matching search", color = Color.Gray, fontSize = 12.sp)
                                                                }
                                                            } else {
                                                                LazyColumn(
                                                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                                                    modifier = Modifier.fillMaxSize()
                                                                ) {
                                                                    items(filteredActive) { device ->
                                                                        val position = realtimePositions[device.id]
                                                                        Row(
                                                                            modifier = Modifier
                                                                                .fillMaxWidth()
                                                                                .background(Color(0xFF1F2937), RoundedCornerShape(8.dp))
                                                                                .clickable {
                                                                                    viewModel.selectDevice(device.id)
                                                                                    isFleetScrollerExpanded = false
                                                                                }
                                                                                .padding(10.dp),
                                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                                Icon(
                                                                                    Icons.Default.LocationOn,
                                                                                    contentDescription = null,
                                                                                    tint = if (device.status == "online") Color(0xFF10B981) else Color(0xFF9CA3AF),
                                                                                    modifier = Modifier.size(18.dp)
                                                                                )
                                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                                Column {
                                                                                    Text(device.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                                                    Text("IMEI: ${device.uniqueId}", color = Color.Gray, fontSize = 11.sp)
                                                                                }
                                                                            }

                                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                                if (position != null) {
                                                                                    Text(
                                                                                        text = "${String.format("%.1f", position.speedKmh)} km/h",
                                                                                        color = Color(0xFF10B981),
                                                                                        fontSize = 12.sp,
                                                                                        fontWeight = FontWeight.Bold
                                                                                    )
                                                                                } else if (device.id != null) {
                                                                                    val cachedSpeed = cachedDevices.find { it.id == device.id }?.speed ?: 0.0
                                                                                    Text(
                                                                                        text = "${String.format("%.1f", cachedSpeed)} km/h",
                                                                                        color = Color.Gray,
                                                                                        fontSize = 12.sp
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
                                                
                                                if (!isFleetScrollerExpanded) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.Center,
                                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                                    ) {
                                                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "Tap scroller title above to browse active fleet assets on the map.",
                                                            fontSize = 10.sp,
                                                            color = Color.LightGray
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

                    2 -> {
                        // TAB 2: POWERFUL TELEMATIC PLAYBACK AND HISTORICAL TRAIL PANEL
                        var playbackSelectedDeviceId by remember { mutableStateOf<Long?>(selectedDeviceId) }
                        val context = LocalContext.current
                        val dateTimeFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

                        // Distance calculation nested lambdas
                        val calculateDistance: (Double, Double, Double, Double) -> Double = { lat1, lon1, lat2, lon2 ->
                            val r = 6371.0 // earth radius in km
                            val dLat = java.lang.Math.toRadians(lat2 - lat1)
                            val dLon = java.lang.Math.toRadians(lon2 - lon1)
                            val a = java.lang.Math.sin(dLat / 2.0) * java.lang.Math.sin(dLat / 2.0) +
                                    java.lang.Math.cos(java.lang.Math.toRadians(lat1)) * java.lang.Math.cos(java.lang.Math.toRadians(lat2)) *
                                    java.lang.Math.sin(dLon / 2.0) * java.lang.Math.sin(dLon / 2.0)
                            val c = 2.0 * java.lang.Math.atan2(java.lang.Math.sqrt(a), java.lang.Math.sqrt(1.0 - a))
                            r * c
                        }

                        val calculateCumulativeDistance: (List<Position>, Int) -> Double = { trail, endIndex ->
                            var dist = 0.0
                            for (i in 0 until endIndex) {
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
                            val datePickerDialog = android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val newCal = java.util.Calendar.getInstance().apply {
                                        time = currentCal.time
                                        set(java.util.Calendar.YEAR, year)
                                        set(java.util.Calendar.MONTH, month)
                                        set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
                                    }
                                    if (isStart) {
                                        customStartCalendar = newCal
                                    } else {
                                        customEndCalendar = newCal
                                    }
                                    isPlaybackActive = false
                                    playbackStepIndex = 0
                                },
                                currentCal.get(java.util.Calendar.YEAR),
                                currentCal.get(java.util.Calendar.MONTH),
                                currentCal.get(java.util.Calendar.DAY_OF_MONTH)
                            )
                            datePickerDialog.show()
                        }

                        val selectTime = { isStart: Boolean ->
                            val currentCal = if (isStart) customStartCalendar else customEndCalendar
                            val timePickerDialog = android.app.TimePickerDialog(
                                context,
                                { _, hourOfDay, minute ->
                                    val newCal = java.util.Calendar.getInstance().apply {
                                        time = currentCal.time
                                        set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                                        set(java.util.Calendar.MINUTE, minute)
                                        set(java.util.Calendar.SECOND, 0)
                                    }
                                    if (isStart) {
                                        customStartCalendar = newCal
                                    } else {
                                        customEndCalendar = newCal
                                    }
                                    isPlaybackActive = false
                                    playbackStepIndex = 0
                                },
                                currentCal.get(java.util.Calendar.HOUR_OF_DAY),
                                currentCal.get(java.util.Calendar.MINUTE),
                                true // 24-hour format
                            )
                            timePickerDialog.show()
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Headers
                            Column {
                                Text(
                                    text = "Fleet Historical Playback",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "Query historic breadcrumb routes and telemetry playback logs with customizable parameters.",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }

                            // Device list picker
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Select Asset Hardware:",
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(devices) { dev ->
                                            val isSelected = playbackSelectedDeviceId == dev.id
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B)
                                                ),
                                                modifier = Modifier
                                                    .clickable { 
                                                        playbackSelectedDeviceId = dev.id
                                                        isPlaybackActive = false
                                                        playbackStepIndex = 0
                                                    }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    val catIcon = if (dev.category == "truck") {
                                                        Icons.Default.Place
                                                    } else {
                                                        Icons.Default.LocationOn
                                                    }
                                                    Icon(
                                                        imageVector = catIcon,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = dev.name,
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Query parameters selection
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Range Type Selector
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Time Selection Mode:",
                                            color = Color.LightGray,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(
                                            modifier = Modifier
                                                .background(Color(0xFF1E293B), RoundedCornerShape(20.dp))
                                                .padding(2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(18.dp))
                                                    .background(if (playbackRangeMode == "Predefined") Color(0xFF3B82F6) else Color.Transparent)
                                                    .clickable { playbackRangeMode = "Predefined" }
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("Quick Periods", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(18.dp))
                                                    .background(if (playbackRangeMode == "Custom") Color(0xFF3B82F6) else Color.Transparent)
                                                    .clickable { playbackRangeMode = "Custom" }
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("Custom Range", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Divider(color = Color(0xFF22293F), thickness = 1.dp)

                                    if (playbackRangeMode == "Predefined") {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            listOf("1h", "6h", "12h", "24h", "Today", "Yesterday").forEach { range ->
                                                val isRangeSelected = predefinedRange == range
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            color = if (isRangeSelected) Color(0xFF3B82F6) else Color(0xFF1E293B),
                                                            shape = RoundedCornerShape(6.dp)
                                                        )
                                                        .clickable { predefinedRange = range }
                                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                                ) {
                                                    Text(
                                                        text = range,
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        // Custom Date Pickers Setup
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            // Start range selector row
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("From UTC:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Button(
                                                        onClick = { selectDate(true) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(28.dp),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(customStartCalendar.time), fontSize = 10.sp, color = Color.White)
                                                    }
                                                    Button(
                                                        onClick = { selectTime(true) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(28.dp),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(SimpleDateFormat("HH:mm", Locale.US).format(customStartCalendar.time), fontSize = 10.sp, color = Color.White)
                                                    }
                                                }
                                            }

                                            // End range selector row
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("To UTC:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Button(
                                                        onClick = { selectDate(false) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(28.dp),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(customEndCalendar.time), fontSize = 10.sp, color = Color.White)
                                                    }
                                                    Button(
                                                        onClick = { selectTime(false) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(28.dp),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(SimpleDateFormat("HH:mm", Locale.US).format(customEndCalendar.time), fontSize = 10.sp, color = Color.White)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Primary Fetch Button
                                    Button(
                                        onClick = {
                                            isPlaybackActive = false
                                            playbackStepIndex = 0
                                            val fromTime: java.util.Date
                                            val toTime: java.util.Date
                                            if (playbackRangeMode == "Predefined") {
                                                val now = java.util.Date()
                                                toTime = now
                                                fromTime = when (predefinedRange) {
                                                    "1h" -> java.util.Date(now.time - 1 * 60 * 60 * 1000L)
                                                    "6h" -> java.util.Date(now.time - 6 * 60 * 60 * 1000L)
                                                    "12h" -> java.util.Date(now.time - 12 * 60 * 60 * 1000L)
                                                    "24h" -> java.util.Date(now.time - 24 * 60 * 60 * 1000L)
                                                    "Today" -> {
                                                        java.util.Calendar.getInstance().apply {
                                                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                                                            set(java.util.Calendar.MINUTE, 0)
                                                            set(java.util.Calendar.SECOND, 0)
                                                            set(java.util.Calendar.MILLISECOND, 0)
                                                        }.time
                                                    }
                                                    "Yesterday" -> {
                                                        java.util.Calendar.getInstance().apply {
                                                            add(java.util.Calendar.DAY_OF_YEAR, -1)
                                                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                                                            set(java.util.Calendar.MINUTE, 0)
                                                            set(java.util.Calendar.SECOND, 0)
                                                            set(java.util.Calendar.MILLISECOND, 0)
                                                        }.time
                                                    }
                                                    else -> java.util.Date(now.time - 12 * 60 * 60 * 1000L)
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
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth().height(36.dp)
                                    ) {
                                        if (historyLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                        } else {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Fetch Route Coordinates", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // Main area: Map + Scrubber
                            if (routeHistory.isNotEmpty() && playbackSelectedDeviceId != null) {
                                val currentPoint = routeHistory.getOrNull(playbackStepIndex) ?: routeHistory.first()
                                val deviceObj = devices.find { it.id == playbackSelectedDeviceId }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Upper portion: SlippyMap
                                    Box(
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .fillMaxWidth()
                                            .background(Color.Black, RoundedCornerShape(8.dp))
                                    ) {
                                        val mapCenterLat = if (isCameraFollowLocked) currentPoint.latitude else (routeHistory.firstOrNull()?.latitude ?: 9.0192)
                                        val mapCenterLng = if (isCameraFollowLocked) currentPoint.longitude else (routeHistory.firstOrNull()?.longitude ?: 38.7525)

                                        SlippyMap(
                                            modifier = Modifier.fillMaxSize(),
                                            initialCenterLat = mapCenterLat,
                                            initialCenterLng = mapCenterLng,
                                            markers = listOf(
                                                com.example.ui.map.MapMarker(
                                                    id = 99999 + playbackSelectedDeviceId!!,
                                                    name = deviceObj?.name ?: "Playback",
                                                    latitude = currentPoint.latitude,
                                                    longitude = currentPoint.longitude,
                                                    course = currentPoint.course.toFloat(),
                                                    status = "online",
                                                    speedKmh = currentPoint.speedKmh
                                                )
                                            ),
                                            routePath = routeHistory,
                                            selectedMarkerId = 99999 + playbackSelectedDeviceId!!,
                                            isDarkMode = true,
                                            mapStyle = mapProviderStyle,
                                            markerLabelType = markerLabelStyle,
                                            markerIconStyle = markerIconStyle,
                                            customIconUri = customIconUri,
                                            geofences = emptyList()
                                        )

                                        // Floating Map Style/Control Layer Overlay
                                        MapStyleControlLayer(
                                            mapProviderStyle = mapProviderStyle,
                                            onStyleSelected = { viewModel.setMapProviderStyle(it) },
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(8.dp)
                                        )

                                        // Camera settings controls
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // Follow Toggle
                                            Box(
                                                modifier = Modifier
                                                    .background(if (isCameraFollowLocked) Color(0xCC2563EB) else Color(0xCC0F172A), RoundedCornerShape(4.dp))
                                                    .clickable { isCameraFollowLocked = !isCameraFollowLocked }
                                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = if (isCameraFollowLocked) "Cam Locked" else "Cam Unlocked",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            // Overlay badge for point coordinates
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color(0xCC0F172A))
                                            ) {
                                                Text(
                                                    text = "${String.format("%.5f", currentPoint.latitude)}, ${String.format("%.5f", currentPoint.longitude)}",
                                                    color = Color.LightGray,
                                                    fontSize = 9.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    // Lower portion: Player scrubbing bar and status details
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            // Telemetry statuses
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
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                    
                                                    val totalDist = calculateCumulativeDistance(routeHistory, playbackStepIndex)
                                                    Text(
                                                        text = "Telemetry crumb ${playbackStepIndex + 1}/${routeHistory.size} • Trip: ${String.format("%.2f", totalDist)} km",
                                                        color = Color.Gray,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                                // Speed & alt badges
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Card(
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                                                    ) {
                                                        Text(
                                                            text = "${String.format("%.1f", currentPoint.speedKmh)} km/h",
                                                            color = Color(0xFF10B981),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                    Card(
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                                                    ) {
                                                        Text(
                                                            text = "${String.format("%.0f", currentPoint.altitude)}m Alt",
                                                            color = Color(0xFF3B82F6),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            // Slider scrubber
                                            Slider(
                                                value = playbackStepIndex.toFloat(),
                                                onValueChange = {
                                                    isPlaybackActive = false
                                                    playbackStepIndex = it.toInt()
                                                },
                                                valueRange = 0f..(routeHistory.size - 1).toFloat().coerceAtLeast(1f),
                                                colors = SliderDefaults.colors(
                                                    thumbColor = Color(0xFF3B82F6),
                                                    activeTrackColor = Color(0xFF3B82F6),
                                                    inactiveTrackColor = Color(0xFF1E293B)
                                                ),
                                                modifier = Modifier.fillMaxWidth().height(24.dp)
                                            )

                                            // Controls Row
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    // Reset
                                                    IconButton(
                                                        onClick = {
                                                            isPlaybackActive = false
                                                            playbackStepIndex = 0
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Stop Reset",
                                                            tint = Color.Gray,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }

                                                    // Step Backward
                                                    IconButton(
                                                        onClick = {
                                                            isPlaybackActive = false
                                                            if (playbackStepIndex > 0) playbackStepIndex--
                                                        },
                                                        enabled = playbackStepIndex > 0,
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.ArrowBack,
                                                            contentDescription = "Step Backward",
                                                            tint = if (playbackStepIndex > 0) Color.LightGray else Color.DarkGray,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }

                                                    // Play/Pause Action
                                                    IconButton(
                                                        onClick = { isPlaybackActive = !isPlaybackActive },
                                                        modifier = Modifier
                                                            .clip(CircleShape)
                                                            .background(if (isPlaybackActive) Color(0xFF2563EB) else Color(0xFF1E293B))
                                                            .size(36.dp)
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
                                                            Icon(
                                                                imageVector = Icons.Default.PlayArrow,
                                                                contentDescription = "Playback Action",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }

                                                    // Step Forward
                                                    IconButton(
                                                        onClick = {
                                                            isPlaybackActive = false
                                                            if (playbackStepIndex < routeHistory.size - 1) playbackStepIndex++
                                                        },
                                                        enabled = playbackStepIndex < routeHistory.size - 1,
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.ArrowForward,
                                                            contentDescription = "Step Forward",
                                                            tint = if (playbackStepIndex < routeHistory.size - 1) Color.LightGray else Color.DarkGray,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }

                                                    // Loop Repeat Button
                                                    IconButton(
                                                        onClick = { playbackLoop = !playbackLoop },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Refresh,
                                                            contentDescription = "Loop repeat option",
                                                            tint = if (playbackLoop) Color(0xFF3B82F6) else Color.Gray,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }

                                                // Speed Multipliers
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    listOf(1, 2, 5, 10, 20, 50).forEach { s ->
                                                        val active = playbackSpeedMultiplier == s
                                                        Box(
                                                            modifier = Modifier
                                                                .background(
                                                                    color = if (active) Color(0xFF3B82F6) else Color(0xFF1E293B),
                                                                    shape = RoundedCornerShape(4.dp)
                                                                )
                                                                .clickable { playbackSpeedMultiplier = s }
                                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                                        ) {
                                                            Text(
                                                                text = "${s}x",
                                                                color = Color.White,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Interactive Telemetry Trail Output Log List
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                        modifier = Modifier.fillMaxWidth().weight(0.6f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = "Telemetry Trail Log Output:",
                                                color = Color.LightGray,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(bottom = 6.dp)
                                            )
                                            
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                items(routeHistory.size) { idx ->
                                                    val pt = routeHistory[idx]
                                                    val isCurrent = idx == playbackStepIndex
                                                    val tStr = pt.deviceTime ?: ""
                                                    val timeLabel = if (tStr.contains("T")) tStr.substringAfter("T").substringBefore("Z") else tStr
                                                    
                                                    val rowBgColor = when {
                                                        isCurrent -> Color(0xFF1E40AF)
                                                        pt.speedKmh > 80.0 -> Color(0xFF7F1D1D)
                                                        pt.speedKmh < 5.0 -> Color(0xFF1F2937)
                                                        else -> Color(0xFF111827)
                                                    }

                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(rowBgColor)
                                                            .clickable {
                                                                isPlaybackActive = false
                                                                playbackStepIndex = idx
                                                            }
                                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = "#${idx + 1}",
                                                                color = if (isCurrent) Color.White else Color.Gray,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.width(32.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = timeLabel,
                                                                color = Color.LightGray,
                                                                fontSize = 10.sp,
                                                                fontFamily = FontFamily.Monospace
                                                            )
                                                        }
                                                        
                                                        Row {
                                                            Text(
                                                                text = "${String.format("%.1f", pt.speedKmh)} km/h",
                                                                color = if (pt.speedKmh > 80.0) Color(0xFFFCA5A5) else Color(0xFF10B981),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Spacer(modifier = Modifier.width(12.dp))
                                                            Text(
                                                                text = "${String.format("%.4f", pt.latitude)}, ${String.format("%.4f", pt.longitude)}",
                                                                color = Color.Gray,
                                                                fontSize = 9.sp,
                                                                fontFamily = FontFamily.Monospace
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Dynamic instruction status card
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.DarkGray,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        val statusLabel = if (playbackSelectedDeviceId == null) {
                                            "Select a telemetry hardware unit above to begin."
                                        } else {
                                            "Telemetry unit selected. Click \"Fetch Route Coordinates\" to stream historical trails."
                                        }
                                        Text(
                                            text = statusLabel,
                                            color = Color.LightGray,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Historical playback logs real coordinates from Traccar telemetry gateway.",
                                            color = Color.Gray,
                                            fontSize = 10.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        // TAB 2: REMOTE CONTROL TELEMATICS DISPATCH COMMANDS PANEL
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = viewModel.translate("command"),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = "Issue over-the-air instruction signals directly into the telemetry unit transponders.",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            var selectedCommandDevId by remember { mutableStateOf<Long?>(devices.firstOrNull()?.id) }
                            var selectedCommandType by remember { mutableStateOf("Engine Fuel Cut") }
                            var commandPayload by remember { mutableStateOf("RELAY_1=OFF") }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(viewModel.translate("select_device"), color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    // Target Select dropdown simulation (row of buttons of first few devices)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        devices.take(3).forEach { dev ->
                                            val active = selectedCommandDevId == dev.id
                                            Box(
                                                modifier = Modifier
                                                    .background(if (active) Color(0xFF3B82F6) else Color(0xFF1E293B), CircleShape)
                                                    .clickable { selectedCommandDevId = dev.id }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(dev.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFF1E293B))

                                    Text(viewModel.translate("engine_status") + " / " + viewModel.translate("command_payload"), color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    
                                    val commandsTemplates = listOf(
                                        "Engine Fuel Cut" to "RELAY_1=OFF (Ignition Lock)",
                                        "Engine Resume" to "RELAY_1=ON (De-restrict ignition)",
                                        "Hardware Reboot" to "SYS_REBOOT_FORCE=1",
                                        "Poll GPS (Ping)" to "QUERY_POLL_INTERVAL=5s"
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("Engine Fuel Cut", "Engine Resume", "Poll GPS (Ping)").forEach { cmd ->
                                            val active = selectedCommandType == cmd
                                            Box(
                                                modifier = Modifier
                                                    .background(if (active) Color(0xFF10B981) else Color(0xFF1E293B), CircleShape)
                                                    .clickable { 
                                                        selectedCommandType = cmd
                                                        commandPayload = commandsTemplates.find { it.first == cmd }?.second ?: ""
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(cmd, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = commandPayload,
                                        onValueChange = { commandPayload = it },
                                        label = { Text("Command Raw Payload") },
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                    )

                                    Button(
                                        onClick = {
                                            selectedCommandDevId?.let { devId ->
                                                viewModel.sendDeviceCommand(devId, selectedCommandType, commandPayload)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(viewModel.translate("send_command") + " 🚀", fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }

                            // Sent log history
                            Text(viewModel.translate("commands"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                items(commandsLog) { log ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(log.deviceName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text("Payload: ${log.payload}", color = Color.Gray, fontSize = 10.sp)
                                                Text("Time: ${log.timestamp}", color = Color.DarkGray, fontSize = 9.sp)
                                            }
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (log.status == "EXECUTED") Color(0x2210B981) else Color(0x22F59E0B)
                                                )
                                            ) {
                                                Text(
                                                    text = log.status,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (log.status == "EXECUTED") Color(0xFF10B981) else Color(0xFFF59E0B),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                     4 -> {
                        // TAB 3: CUSTOM GEOFENCE DESIGNER VIEW
                        Column(
                            modifier = Modifier
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

                            // Map Drawing Sandbox Board (Mapbox Draw visual simulator controls)
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
                                        initialCenterLat = 9.0192,
                                        initialCenterLng = 38.7525,
                                        initialZoom = 11.5f,
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
                                                "polygon" -> "🔨 Mapbox Draw: Tap map to plot vertices (${drawnPoints.size})"
                                                "circle" -> "🔨 Mapbox Draw: Click to place center, click edge to size"
                                                else -> "📐 Choose a Sketch Tool from standard toolbar ➔"
                                            },
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }

                                    // Floating Mapbox Draw toolbar panel
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
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Establish Hardware Boundaries", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                                    OutlinedTextField(
                                        value = gfName,
                                        onValueChange = { gfName = it },
                                        label = { Text("Geofence Name") },
                                        placeholder = { Text("e.g., Safe Zone Delta") },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFF3B82F6),
                                            unfocusedBorderColor = Color(0xFF1E293B)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // Dynamic Device linkage Dropdown (assigned hardware devices only)
                                    val currentAssignedDevice = devices.find { d -> d.id == selectedDeviceIdForGeofence }
                                    val buttonLabelText = if (currentAssignedDevice != null) {
                                        "Link Device: ${currentAssignedDevice.name} (${currentAssignedDevice.uniqueId})"
                                    } else {
                                        "Select Device to Bind Rule"
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
                                                    fontSize = 13.sp
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
                                            devices.forEach { dev ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text("${dev.name} [Unique ID: ${dev.uniqueId}]", color = Color.White, fontSize = 13.sp)
                                                    },
                                                    onClick = {
                                                        selectedDeviceIdForGeofence = dev.id
                                                        isDeviceDropdownExpanded = false
                                                    }
                                                )
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
                                                        deviceId = selectedDeviceIdForGeofence
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
                                                        deviceId = selectedDeviceIdForGeofence
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
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                            border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Card(
                                                        colors = CardDefaults.cardColors(containerColor = if (gf.type == "polygon") Color(0x3310B981) else Color(0x333B82F6)),
                                                        shape = CircleShape,
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = if (gf.type == "polygon") "⬡" else "◯",
                                                                color = if (gf.type == "polygon") Color(0xFF10B981) else Color(0xFF3B82F6),
                                                                fontSize = 16.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }

                                                    Column {
                                                        Text(gf.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                        if (gf.type == "polygon") {
                                                            Text("Boundary Type: GIS Polygon Bounds", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                            Text("${gf.points.size} node vertices plotted", color = Color.Gray, fontSize = 9.sp)
                                                        } else {
                                                            Text("Boundary Type: Circular Ring (${gf.radiusMeters.roundToInt()}m)", color = Color(0xFF3B82F6), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                            Text("Center Lat: ${String.format("%.4f", gf.latitude)}, Lng: ${String.format("%.4f", gf.longitude)}", color = Color.Gray, fontSize = 9.sp)
                                                        }
                                                    }
                                                }
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

                    5 -> {
                        // TAB 4: OPERATOR SETTINGS AND CUSTOMIZATION DIALS PANEL
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            Text(
                                text = viewModel.translate("customization_panel"),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = "Fine-tune language translations, map layer styles, and active labeling text badges.",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    // Language Select Options
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(viewModel.translate("language"), color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(
                                                "en" to "English 🇺🇸",
                                                "am" to "አማርኛ 🇪🇹",
                                                "es" to "Español 🇪🇸"
                                            ).forEach { (code, label) ->
                                                val active = appLanguage == code
                                                Box(
                                                    modifier = Modifier
                                                        .background(if (active) Color(0xFF3B82F6) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                                        .clickable { viewModel.setAppLanguage(code) }
                                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                                ) {
                                                    Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFF1E293B))

                                    // Map Provider Selection Style Options
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(viewModel.translate("map_style"), color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(
                                                "mapbox_dark" to "Mapbox Dark 🌑",
                                                "mapbox_light" to "Carto Light ☀️",
                                                "google_road" to "Google Road 🗺️",
                                                "google_satellite" to "Satellite 🛰️",
                                                "osm_classic" to "OSM Classic 🌐"
                                            ).forEach { (style, labelKey) ->
                                                val active = mapProviderStyle == style
                                                Box(
                                                    modifier = Modifier
                                                        .background(if (active) Color(0xFF3B82F6) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                                        .clickable { viewModel.setMapProviderStyle(style) }
                                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                                ) {
                                                    Text(labelKey, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFF1E293B))

                                    // Marker labeling preferences options
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(viewModel.translate("marker_label"), color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(
                                                "name" to viewModel.translate("device_name"),
                                                "coordinates" to viewModel.translate("coordinates"),
                                                "plate" to viewModel.translate("plate_number")
                                            ).forEach { (labelType, textLabel) ->
                                                val active = markerLabelStyle == labelType
                                                Box(
                                                    modifier = Modifier
                                                        .background(if (active) Color(0xFF10B981) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                                        .clickable { viewModel.setMarkerLabelStyle(labelType) }
                                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                                ) {
                                                    Text(textLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFF1E293B))

                                    // Marker custom aesthetic selection options
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(viewModel.translate("marker_icon") + " / Profile Vehicle Avatar Setting", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(
                                                "pin" to "Pin 📍",
                                                "car" to "Car 🚗",
                                                "truck" to "Truck 🚛",
                                                "bike" to "Bike 🏍️",
                                                "custom" to "Custom 🖼️"
                                            ).forEach { (iconKey, label) ->
                                                val active = markerIconStyle == iconKey
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .background(if (active) Color(0xFF8B5CF6) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                                        .clickable { viewModel.setMarkerIconStyle(iconKey) }
                                                        .padding(vertical = 10.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        // Image Upload / Preview portion for Custom URI Icon option
                                        if (markerIconStyle == "custom" || !customIconUri.isNullOrEmpty()) {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                                border = BorderStroke(1.dp, Color(0xFF2D3748)),
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Text("Local Photo Library Icon", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        Text(
                                                            text = if (!customIconUri.isNullOrEmpty()) "Custom avatar is active on live tracking." else "No local file assigned yet.",
                                                            color = Color.LightGray,
                                                            fontSize = 10.sp
                                                        )
                                                        Button(
                                                            onClick = { imageLauncher.launch("image/*") },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                            shape = RoundedCornerShape(6.dp),
                                                            modifier = Modifier.padding(top = 4.dp)
                                                        ) {
                                                            Text("Upload JPEG/PNG Icon", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }

                                                    // Image thumbnail slot
                                                    if (!customIconUri.isNullOrEmpty()) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(48.dp)
                                                                .background(Color.White, CircleShape)
                                                                .padding(4.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            AsyncImage(
                                                                model = customIconUri,
                                                                contentDescription = "User Uploaded Vehicle Icon",
                                                                modifier = Modifier.size(36.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Custom Fleet Tools & Controls Navigation Menu
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "FLEET TOOLS & CONTROL",
                                        color = Color(0xFF60A5FA),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp
                                    )
                                    
                                    // Option 1: Send Command
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                            .clickable { currentTab = 3 }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Send Commands", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text("Dispatch GPRS configurations and remote execution payloads", color = Color.Gray, fontSize = 9.sp)
                                        }
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                                    }

                                    // Option 2: Geofence Planner
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                            .clickable { currentTab = 4 }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Geofence Planner", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text("Establish geographical rules and boundaries", color = Color.Gray, fontSize = 9.sp)
                                        }
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                                    }

                                    // Option 3: Analytics & Reports
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                            .clickable { currentTab = 6 }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Historical Route Reports", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text("Generate mileage, velocity, and trip detail summaries", color = Color.Gray, fontSize = 9.sp)
                                        }
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }

                            // SaaS Tenant details footer card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(viewModel.translate("tenant_mode"), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                    Text(viewModel.translate("assigned_vehicles"), color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                    6 -> {
                        // TAB 6: HISTORICAL TRIP REPORTS & ANALYTICS
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "HISTORICAL ROUTE REPORTS",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = "Select an active fleet asset and historical time frame to compile a telematics route report.",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            var selectedAssetForReport by remember { mutableStateOf<Long?>(null) }
                            var queryHours by remember { mutableStateOf(24) }
                            var reportResults by remember { mutableStateOf<List<Position>>(emptyList()) }
                            var reportLoading by remember { mutableStateOf(false) }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    // Asset Selector
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Select Fleet Asset", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            devices.forEach { dev ->
                                                val active = selectedAssetForReport == dev.id
                                                Box(
                                                    modifier = Modifier
                                                        .background(if (active) Color(0xFF3B82F6) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                                        .clickable { selectedAssetForReport = dev.id }
                                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                                ) {
                                                    Text(dev.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    // Time Frame selector
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Query Time Window", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(3 to "Past 3 hrs", 12 to "Past 12 hrs", 24 to "Past 24 hrs", 72 to "Past 72 hrs").forEach { (h, lab) ->
                                                val active = queryHours == h
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .background(if (active) Color(0xFF10B981) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                                        .clickable { queryHours = h }
                                                        .padding(vertical = 10.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(lab, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            val devId = selectedAssetForReport
                                            if (devId != null) {
                                                reportLoading = true
                                                scope.launch {
                                                    try {
                                                        val toTime = Date()
                                                        val fromTime = Date(toTime.time - queryHours * 60 * 60 * 1000L)
                                                        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                                                        val fromStr = sdf.format(fromTime)
                                                        val toStr = sdf.format(toTime)

                                                        val history = viewModel.repository.getRouteHistory(devId, fromStr, toStr)
                                                        reportResults = history
                                                        viewModel.triggerFeedback("Fetched ${history.size} historic coordinates for reporting")
                                                    } catch (e: Exception) {
                                                        viewModel.triggerFeedback("Query failed: " + e.message)
                                                    } finally {
                                                        reportLoading = false
                                                    }
                                                }
                                            }
                                        },
                                        enabled = selectedAssetForReport != null && !reportLoading,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                                    ) {
                                        if (reportLoading) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                        } else {
                                            Text("EXECUTE TELEMETRY QUERY OR REPORT 📊", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            if (selectedAssetForReport != null && reportResults.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "REPORT COMPILED: " + devices.find { it.id == selectedAssetForReport }?.name,
                                    color = Color(0xFF60A5FA),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val avgSpeed = reportResults.map { it.speed ?: 0.0 }.average()
                                        val maxSpeed = reportResults.maxOfOrNull { it.speed ?: 0.0 } ?: 0.0
                                        val entries = reportResults.size

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Total Breadcrumbs:", color = Color.Gray, fontSize = 12.sp)
                                            Text("$entries positions", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Average Velocity:", color = Color.Gray, fontSize = 12.sp)
                                            Text(String.format(Locale.US, "%.1f km/h", avgSpeed), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Maximum Speed Detected:", color = Color.Gray, fontSize = 12.sp)
                                            Text(String.format(Locale.US, "%.1f km/h", maxSpeed), color = if (maxSpeed > 80) Color.Red else Color.Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    reportResults.take(15).forEach { pos ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF070B19)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text("Time: ${pos.deviceTime}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    Text("Coordinates: ${pos.latitude}, ${pos.longitude}", color = Color.Gray, fontSize = 10.sp)
                                                    pos.address?.let {
                                                        Text(it, color = Color.LightGray, fontSize = 9.sp)
                                                    }
                                                }
                                                Text(
                                                    text = String.format(Locale.US, "%.1f km/h", pos.speed ?: 0.0),
                                                    color = Color(0xFF10B981),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                    if (reportResults.size > 15) {
                                        Text(
                                            text = "... and ${reportResults.size - 15} more positions in full report history",
                                            color = Color.Gray,
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                                        )
                                    }
                                }
                            } else if (selectedAssetForReport != null) {
                                Text(
                                    text = "No route entries recorded during selected timeframe.",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(16.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet representation to register a hardware device
    if (showAddDeviceSheet) {
        AlertDialog(
            onDismissRequest = { showAddDeviceSheet = false },
            title = { Text("Commission New Tracker", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Register a new telematics tracker hardware device node onto this multi-tenant tenant account.", fontSize = 11.sp, color = Color.LightGray)
                    
                    OutlinedTextField(
                        value = newDeviceName,
                        onValueChange = { newDeviceName = it },
                        label = { Text("Friendly Label / Name") },
                        placeholder = { Text("e.g. Sales Rep Scooter B") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )

                    OutlinedTextField(
                        value = newDeviceImei,
                        onValueChange = { newDeviceImei = it },
                        label = { Text("Unique IMEI Hardware code") },
                        placeholder = { Text("e.g. 8652390145...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )

                    OutlinedTextField(
                        value = newDeviceCategory,
                        onValueChange = { newDeviceCategory = it },
                        label = { Text("Category Classification") },
                        placeholder = { Text("e.g. Car, Truck, Motorcycle") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newDeviceName.isNotBlank() && newDeviceImei.isNotBlank()) {
                            viewModel.addNewDevice(newDeviceName, newDeviceImei, newDeviceCategory)
                            showAddDeviceSheet = false
                            newDeviceName = ""
                            newDeviceImei = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Provision Node", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDeviceSheet = false }) {
                    Text("Abort", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

// Micro components
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
            Text(count, color = color, fontSize = 22.sp, fontWeight = FontWeight.Black)
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
            // Category status icon display
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
                
                // Location Address summary if resolved
                position?.address?.let {
                    Text(
                        text = it,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Speed telemetry tracker text
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
                // Space-saving container ending
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
    modifier: Modifier = Modifier
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
                                "mapbox_dark" -> "🌑"
                                "mapbox_light" -> "☀️"
                                "google_road" -> "🗺️"
                                "google_satellite" -> "🛰️"
                                "osm_classic" -> "🌐"
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
                                text = "MAP CHANGER",
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

                        val options = listOf(
                            Triple("mapbox_dark", "🌑", "Mapbox Dark"),
                            Triple("mapbox_light", "☀️", "Carto Light"),
                            Triple("google_road", "🗺️", "Google Road"),
                            Triple("google_satellite", "🛰️", "Satellite 2D"),
                            Triple("osm_classic", "🌐", "OSM Classic")
                        )

                        options.forEach { (styleKey, emoji, label) ->
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
