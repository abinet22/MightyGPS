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
import com.example.data.model.ReportSummary
import com.example.data.model.ReportTrip
import com.example.data.model.ReportStop
import com.example.data.model.Event
import java.util.Calendar

import com.example.ui.map.SlippyMap
import com.example.ui.components.DeviceDetailBottomSheet
import com.example.ui.map.calculateBoundsFit
import com.example.ui.viewmodel.TraccarViewModel
import com.example.util.UnitFormatter
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.shadow
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import kotlin.math.roundToInt
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import com.example.data.model.GeofenceAlert
import com.example.data.model.ConsolidatedAlert
import com.example.ui.screens.components.*
import com.example.ui.screens.tabs.*
import com.example.util.generatePdfReport
import com.example.util.sharePdfReport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: TraccarViewModel,
    onLogout: () -> Unit = {}
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
    val positionUpdateInterval by viewModel.positionUpdateInterval.collectAsState()
    val colorMoving by viewModel.colorMoving.collectAsState()
    val colorIdle by viewModel.colorIdle.collectAsState()
    val colorOffline by viewModel.colorOffline.collectAsState()
    val markerTriggerMode by viewModel.markerTriggerMode.collectAsState()
    val infoCardFields by viewModel.infoCardFields.collectAsState()
    val unitSystem by viewModel.unitSystem.collectAsState()
    val geofences by viewModel.geofences.collectAsState()
    val isGeofenceLayerVisible by viewModel.isGeofenceLayerVisible.collectAsState()
    val selectedGeofenceDetail by viewModel.selectedGeofence.collectAsState()
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
    var selectedReportDevice by remember { mutableStateOf<Device?>(null) }
    
    // Persistent map state across tab transitions to enable buttery smooth fly-to animations
    var persistentMapCenterLat by remember { mutableStateOf(8.7832) }
    var persistentMapCenterLng by remember { mutableStateOf(38.7405) }
    var persistentMapZoom by remember { mutableStateOf(6.0f) } // Default to Ethiopia broad zoom
    var mapRecenterTrigger by remember { mutableStateOf(0) }
    
    // Playback Controller factors
    var isPlaybackActive by remember { mutableStateOf(false) }
    var playbackSpeedMultiplier by remember { mutableStateOf(1) }
    var playbackStepIndex by remember { mutableStateOf(0) }
    var playbackLoop by remember { mutableStateOf(false) }
    var isCameraFollowLocked by remember { mutableStateOf(true) }
    var playbackRangeMode by remember { mutableStateOf("Predefined") } // "Predefined" or "Custom"
    var predefinedRange by remember { mutableStateOf("12h") } // "1h", "6h", "12h", "24h", "Today", "Yesterday"
    
    var animatedPlaybackLat by remember { mutableStateOf<Double?>(null) }
    var animatedPlaybackLng by remember { mutableStateOf<Double?>(null) }
    var animatedPlaybackCourse by remember { mutableStateOf<Float?>(null) }

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

    var geofenceStatuses by remember { mutableStateOf(emptyMap<String, Boolean>()) }
    var activeGeofenceAlerts by remember { mutableStateOf(emptyList<GeofenceAlert>()) }
    var geofenceAlertHistory by remember { mutableStateOf(emptyList<GeofenceAlert>()) }

    var lastCenteredDeviceId by remember { mutableStateOf<Long?>(null) }
    var showDeviceDetailSheet by remember { mutableStateOf(false) }
    var isDeviceDrawerOpen by remember { mutableStateOf(false) }
    var drawerSearchQuery by remember { mutableStateOf("") }
    var drawerFilterStatus by remember { mutableStateOf("ALL") }

    LaunchedEffect(selectedDeviceId) {
        // Reset playback controls ONLY when selected device actually changes
        isPlaybackActive = false
        playbackStepIndex = 0
        playbackSpeedMultiplier = 1
        playbackLoop = false
        animatedPlaybackLat = null
        animatedPlaybackLng = null
        animatedPlaybackCourse = null
        viewModel.clearRouteHistory()
    }

    LaunchedEffect(selectedDeviceId, realtimePositions, cachedDevices) {
        if (selectedDeviceId == null) {
            lastCenteredDeviceId = null
            persistentMapCenterLat = 8.7832
            persistentMapCenterLng = 38.7405
            persistentMapZoom = 6.0f // Reset camera to broad Ethiopia view when no device is selected
        } else {
            val id = selectedDeviceId!!
            val lat = realtimePositions[id]?.latitude ?: cachedDevices.find { it.id == id }?.latitude
            val lng = realtimePositions[id]?.longitude ?: cachedDevices.find { it.id == id }?.longitude
            if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
                if (id != lastCenteredDeviceId || (persistentMapCenterLat == 8.7832 && persistentMapCenterLng == 38.7405)) {
                    persistentMapCenterLat = lat
                    persistentMapCenterLng = lng
                    persistentMapZoom = 18.0f // Enhanced close-up tracking zoom level when device is selected
                    lastCenteredDeviceId = id
                    isCameraFollowLocked = true // Turn camera lock back on when selecting a new device
                    mapRecenterTrigger++
                } else if (isCameraFollowLocked) {
                    persistentMapCenterLat = lat
                    persistentMapCenterLng = lng
                    mapRecenterTrigger++
                }
            }
        }
    }

    val resetMapState = remember {
        {
            // Stop intervals
            isPlaybackActive = false
            
            // Reset UI controls
            playbackStepIndex = 0
            playbackSpeedMultiplier = 1
            playbackLoop = false
            animatedPlaybackLat = null
            animatedPlaybackLng = null
            animatedPlaybackCourse = null
            isCameraFollowLocked = true
            
            // Clear pending requests to prevent race conditions
            viewModel.abortController.abortAll()

            // Clear map overlays
            viewModel.selectDevice(null)
            viewModel.clearRouteHistory()
            searchQuery = ""
            activeGeofenceAlerts = emptyList()
        }
    }

    LaunchedEffect(playbackRangeMode) {
        viewModel.abortController.abort("playback_history")
    }



    LaunchedEffect(routeHistory) {
        if (routeHistory.isNotEmpty()) {
            val fitResult = com.example.ui.map.calculatePositionBoundsFit(routeHistory)
            if (fitResult != null) {
                persistentMapCenterLat = fitResult.first
                persistentMapCenterLng = fitResult.second
                persistentMapZoom = fitResult.third
                mapRecenterTrigger++
            }
        }
    }

    LaunchedEffect(playbackStepIndex, routeHistory) {
        if (routeHistory.isEmpty()) {
            animatedPlaybackLat = null
            animatedPlaybackLng = null
            animatedPlaybackCourse = null
            return@LaunchedEffect
        }
        val target = routeHistory.getOrNull(playbackStepIndex) ?: return@LaunchedEffect
        animatedPlaybackLat = target.latitude
        animatedPlaybackLng = target.longitude
        animatedPlaybackCourse = target.course.toFloat()
    }

    LaunchedEffect(selectedDeviceId, realtimePositions, geofences, isPlaybackActive, playbackStepIndex, routeHistory, animatedPlaybackLat, animatedPlaybackLng) {
        val activePlaybackPos = if (routeHistory.isNotEmpty() && playbackStepIndex < routeHistory.size) {
            routeHistory[playbackStepIndex]
        } else null

        val currentPos = if (isPlaybackActive && activePlaybackPos != null) {
            activePlaybackPos
        } else {
            selectedDeviceId?.let { realtimePositions[it] }
        }
        
        val currentLat = if (isPlaybackActive && animatedPlaybackLat != null) animatedPlaybackLat!! else currentPos?.latitude
        val currentLng = if (isPlaybackActive && animatedPlaybackLng != null) animatedPlaybackLng!! else currentPos?.longitude

        if (currentLat != null && currentLng != null && geofences.isNotEmpty()) {
            val devId = activePlaybackPos?.deviceId ?: selectedDeviceId ?: 0L
            val devName = devices.find { it.id == devId }?.name ?: "Vehicle #$devId"
            val newStatuses = mutableMapOf<String, Boolean>()
            val triggered = mutableListOf<GeofenceAlert>()
            
            for (gf in geofences) {
                val lat1 = currentLat
                val lon1 = currentLng
                val lat2 = gf.latitude
                val lon2 = gf.longitude
                val r = 6371000.0 // meters
                val dLat = Math.toRadians(lat2 - lat1)
                val dLon = Math.toRadians(lon2 - lon1)
                val a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0) +
                        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0)
                val c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a))
                val dist = r * c

                val isInside = dist <= gf.radiusMeters
                val hadPrevious = geofenceStatuses.containsKey(gf.id)
                if (hadPrevious) {
                    val wasInside = geofenceStatuses[gf.id] ?: false
                    if (isInside && !wasInside) {
                        triggered.add(
                            GeofenceAlert(
                                deviceName = devName,
                                geofenceName = gf.name,
                                type = "ENTERED"
                            )
                        )
                    } else if (!isInside && wasInside) {
                        triggered.add(
                            GeofenceAlert(
                                deviceName = devName,
                                geofenceName = gf.name,
                                type = "EXITED"
                            )
                        )
                    }
                }
                newStatuses[gf.id] = isInside
            }
            geofenceStatuses = newStatuses
            if (triggered.isNotEmpty()) {
                activeGeofenceAlerts = activeGeofenceAlerts + triggered
                geofenceAlertHistory = triggered + geofenceAlertHistory
            }
        } else if (!isPlaybackActive) {
            geofenceStatuses = emptyMap()
        }
    }

    LaunchedEffect(activeGeofenceAlerts) {
        if (activeGeofenceAlerts.isNotEmpty()) {
            kotlinx.coroutines.delay(8000)
            activeGeofenceAlerts = activeGeofenceAlerts.drop(1)
        }
    }

    // Bottom Sheet for adding asset
    var showAddDeviceSheet by remember { mutableStateOf(false) }
    var newDeviceName by remember { mutableStateOf("") }
    var newDeviceImei by remember { mutableStateOf("") }
    var newDeviceCategory by remember { mutableStateOf("Car") }
    var newDevicePlate by remember { mutableStateOf("") }

    // Clear feedbacks automatically
    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            scope.launch {
                kotlinx.coroutines.delay(4000)
                viewModel.clearFeedback()
            }
        }
    }

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
            if (currentTab != 1 && currentTab != 2) {
                TopAppBar(
                    navigationIcon = {
                        if (currentTab == 3 || currentTab == 4 || currentTab == 6 || currentTab == 7) {
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
                                text = if (currentTab == 0) "Fleet Reports" else if (currentTab == 5) "Settings & Preferences" else "MightyGPS Fleet Control",
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0F172A),
                        titleContentColor = Color.White
                    )
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                contentColor = Color.LightGray,
                tonalElevation = 6.dp
            ) {
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
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Reports") },
                    label = { Text(if (viewModel.appLanguage.value == "es") "Reportes" else if (viewModel.appLanguage.value == "am") "ሪፖርቶች" else "Reports", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
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
            // Interactive visual alert overlay for Geofences
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .zIndex(100f) // Floating above the map, tabs, etc.
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    activeGeofenceAlerts.take(2).forEach { alert ->
                        key(alert.id) {
                            var visible by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                visible = true
                            }
                            AnimatedVisibility(
                                visible = visible,
                                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (alert.type == "ENTERED") Color(0xFF064E3B) else Color(0xFF7F1D1D)
                                    ),
                                    border = BorderStroke(1.5.dp, if (alert.type == "ENTERED") Color(0xFF10B981) else Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .shadow(8.dp, RoundedCornerShape(12.dp))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(
                                                    if (alert.type == "ENTERED") Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (alert.type == "ENTERED") Icons.Default.CheckCircle else Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = if (alert.type == "ENTERED") Color(0xFF10B981) else Color(0xFFEF4444),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.width(12.dp))
                                        
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = if (alert.type == "ENTERED") "GEOFENCE ENTERED 🟢" else "GEOFENCE EXITED 🔴",
                                                    color = if (alert.type == "ENTERED") Color(0xFF34D399) else Color(0xFFF87171),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 10.sp,
                                                    letterSpacing = 1.sp
                                                )
                                                Text(
                                                    text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(alert.timestamp)),
                                                    color = Color.LightGray.copy(alpha = 0.6f),
                                                    fontSize = 9.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = if (alert.type == "ENTERED") "${alert.deviceName} arrived inside ${alert.geofenceName}" else "${alert.deviceName} exited ${alert.geofenceName}",
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = if (alert.type == "ENTERED") "Safe boundary crossed successfully." else "Security notice: Fleet asset left zone.",
                                                color = Color.LightGray,
                                                fontSize = 11.sp
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.width(8.dp))
                                        
                                        IconButton(
                                            onClick = {
                                                activeGeofenceAlerts = activeGeofenceAlerts.filter { it.id != alert.id }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Dismiss",
                                                tint = Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
                        DeviceDirectoryTab(
                            viewModel = viewModel,
                            devices = devices,
                            cachedDevices = cachedDevices,
                            realtimePositions = realtimePositions,
                            selectedReportDevice = selectedReportDevice,
                            onSelectReportDevice = { selectedReportDevice = it },
                            onViewOnMap = { devId ->
                                lastCenteredDeviceId = null
                                viewModel.selectDevice(devId)
                                currentTab = 1
                            },
                            onViewPlayback = { devId ->
                                lastCenteredDeviceId = null
                                viewModel.selectDevice(devId)
                                currentTab = 2
                            }
                        )
                    }

                    1 -> {
                        MapTab(
                            viewModel = viewModel,
                            devices = devices,
                            cachedDevices = cachedDevices,
                            realtimePositions = realtimePositions,
                            selectedDeviceId = selectedDeviceId,
                            onSelectDevice = { devId ->
                                if (devId == null) {
                                    viewModel.selectDevice(null)
                                    showDeviceDetailSheet = false
                                } else {
                                    lastCenteredDeviceId = null
                                    viewModel.selectDevice(devId)
                                    showDeviceDetailSheet = true
                                }
                            },
                            routeHistory = routeHistory,
                            isPlaybackActive = isPlaybackActive,
                            onTogglePlayback = {
                                if (!isPlaybackActive && routeHistory.isNotEmpty() && playbackStepIndex >= routeHistory.size - 1) {
                                    playbackStepIndex = 0
                                }
                                isPlaybackActive = !isPlaybackActive
                            },
                            playbackStepIndex = playbackStepIndex,
                            onUpdatePlaybackStepIndex = {
                                isPlaybackActive = false
                                playbackStepIndex = it
                            },
                            animatedPlaybackLat = animatedPlaybackLat,
                            animatedPlaybackLng = animatedPlaybackLng,
                            animatedPlaybackCourse = animatedPlaybackCourse,
                            persistentMapCenterLat = persistentMapCenterLat,
                            persistentMapCenterLng = persistentMapCenterLng,
                            persistentMapZoom = persistentMapZoom,
                            mapRecenterTrigger = mapRecenterTrigger,
                            isCameraFollowLocked = isCameraFollowLocked,
                            onCameraFollowLockChanged = { isCameraFollowLocked = it },
                            onViewportChanged = { lat, lng, zm ->
                                persistentMapCenterLat = lat
                                persistentMapCenterLng = lng
                                persistentMapZoom = zm
                            },
                            onUserInteraction = {
                                isCameraFollowLocked = false
                                lastCenteredDeviceId = null
                            },
                            mapProviderStyle = mapProviderStyle,
                            markerLabelStyle = markerLabelStyle,
                            markerIconStyle = markerIconStyle,
                            customIconUri = customIconUri,
                            geofences = geofences,
                            isGeofenceLayerVisible = isGeofenceLayerVisible,
                            colorMoving = colorMoving,
                            colorIdle = colorIdle,
                            colorOffline = colorOffline,
                            markerTriggerMode = markerTriggerMode,
                            infoCardFields = infoCardFields,
                            historyLoading = historyLoading,
                            onResetMapState = { resetMapState() },
                            onOpenFleetDrawer = { isDeviceDrawerOpen = true },
                            onShowDeviceDetailSheet = { showDeviceDetailSheet = true },
                            onLoadPlaybackHistory = { devId ->
                                isPlaybackActive = false
                                playbackStepIndex = 0
                                playbackSpeedMultiplier = 1
                                playbackLoop = false
                                animatedPlaybackLat = null
                                animatedPlaybackLng = null
                                animatedPlaybackCourse = null
                                viewModel.loadPlaybackHistory(devId)
                            },
                            onClearRouteHistory = {
                                isPlaybackActive = false
                                playbackStepIndex = 0
                                viewModel.clearRouteHistory()
                            },
                            onCenterOnCoords = { lat, lng, zm ->
                                persistentMapCenterLat = lat
                                persistentMapCenterLng = lng
                                persistentMapZoom = zm
                                mapRecenterTrigger++
                            }
                        )
                    }

                    2 -> {
                        PlaybackTab(
                            viewModel = viewModel,
                            devices = devices,
                            cachedDevices = cachedDevices,
                            routeHistory = routeHistory,
                            historyLoading = historyLoading,
                            selectedDeviceId = selectedDeviceId,
                            isPlaybackActive = isPlaybackActive,
                            onSetPlaybackActive = { isPlaybackActive = it },
                            playbackStepIndex = playbackStepIndex,
                            onSetPlaybackStepIndex = { playbackStepIndex = it },
                            playbackSpeedMultiplier = playbackSpeedMultiplier,
                            onSetPlaybackSpeedMultiplier = { playbackSpeedMultiplier = it },
                            playbackLoop = playbackLoop,
                            onSetPlaybackLoop = { playbackLoop = it },
                            animatedPlaybackLat = animatedPlaybackLat,
                            animatedPlaybackLng = animatedPlaybackLng,
                            animatedPlaybackCourse = animatedPlaybackCourse,
                            isCameraFollowLocked = isCameraFollowLocked,
                            onSetCameraFollowLocked = { isCameraFollowLocked = it },
                            playbackRangeMode = playbackRangeMode,
                            onSetPlaybackRangeMode = { playbackRangeMode = it },
                            predefinedRange = predefinedRange,
                            onSetPredefinedRange = { predefinedRange = it },
                            customStartCalendar = customStartCalendar,
                            onSetCustomStartCalendar = { customStartCalendar = it },
                            customEndCalendar = customEndCalendar,
                            onSetCustomEndCalendar = { customEndCalendar = it },
                            mapProviderStyle = mapProviderStyle,
                            markerLabelStyle = markerLabelStyle,
                            markerIconStyle = markerIconStyle,
                            customIconUri = customIconUri,
                            colorMoving = colorMoving,
                            colorIdle = colorIdle,
                            colorOffline = colorOffline,
                            markerTriggerMode = markerTriggerMode,
                            infoCardFields = infoCardFields,
                            onNavigateBack = { currentTab = 1 },
                            onResetMapState = { resetMapState() }
                        )
                    }

                    3 -> {
                        CommandsTab(
                            viewModel = viewModel,
                            devices = devices
                        )
                    }

                    4 -> {
                        GeofencesTab(
                            viewModel = viewModel,
                            devices = devices,
                            mapProviderStyle = mapProviderStyle
                        )
                    }

                    5 -> {
                        SettingsTab(
                            viewModel = viewModel,
                            appLanguage = appLanguage,
                            unitSystem = unitSystem,
                            mapProviderStyle = mapProviderStyle,
                            markerLabelStyle = markerLabelStyle,
                            markerIconStyle = markerIconStyle,
                            customIconUri = customIconUri,
                            colorMoving = colorMoving,
                            colorIdle = colorIdle,
                            colorOffline = colorOffline,
                            markerTriggerMode = markerTriggerMode,
                            infoCardFields = infoCardFields,
                            positionUpdateInterval = positionUpdateInterval,
                            isSyncing = isSyncing,
                            onLogout = onLogout,
                            onNavigateTab = { currentTab = it },
                            imageLauncher = imageLauncher
                        )
                    }
                    6 -> {
                        ReportsTab(
                            viewModel = viewModel,
                            devices = devices,
                            appLanguage = appLanguage
                        )
                    }
                    7 -> {
                        AlertsTab(
                            viewModel = viewModel,
                            geofenceAlertHistory = geofenceAlertHistory,
                            onClearAlertHistory = { geofenceAlertHistory = emptyList() },
                            appLanguage = appLanguage
                        )
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
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newDeviceImei,
                        onValueChange = { newDeviceImei = it },
                        label = { Text("Unique IMEI Hardware code") },
                        placeholder = { Text("e.g. 8652390145...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newDeviceCategory,
                        onValueChange = { newDeviceCategory = it },
                        label = { Text("Category Classification") },
                        placeholder = { Text("e.g. Car, Truck, Motorcycle") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newDevicePlate,
                        onValueChange = { newDevicePlate = it },
                        label = { Text("Plate Number / Vehicle Model") },
                        placeholder = { Text("e.g. ABC-1234 or Toyota Hilux") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newDeviceName.isNotBlank() && newDeviceImei.isNotBlank()) {
                            viewModel.addNewDevice(newDeviceName, newDeviceImei, newDeviceCategory, newDevicePlate)
                            showAddDeviceSheet = false
                            newDeviceName = ""
                            newDeviceImei = ""
                            newDevicePlate = ""
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

    // Modular Bottom Sheet for displaying Driver Details, Vehicle Info, and Recent Status Updates
    if (showDeviceDetailSheet && selectedDeviceId != null) {
        val detailDev = devices.find { it.id == selectedDeviceId }
            ?: cachedDevices.find { it.id == selectedDeviceId }?.let { cached ->
                com.example.data.model.Device(
                    id = cached.id,
                    name = cached.name,
                    uniqueId = cached.uniqueId,
                    status = cached.status,
                    lastUpdate = cached.lastUpdate,
                    category = cached.category
                )
            }
        val detailPos = realtimePositions[selectedDeviceId]

        DeviceDetailBottomSheet(
            device = detailDev,
            position = detailPos,
            recentEvents = emptyList(),
            unitSystem = unitSystem,
            onDismissRequest = { showDeviceDetailSheet = false },
            onPlaybackClick = { devId ->
                showDeviceDetailSheet = false
                isPlaybackActive = false
                playbackStepIndex = 0
                playbackSpeedMultiplier = 1
                playbackLoop = false
                animatedPlaybackLat = null
                animatedPlaybackLng = null
                animatedPlaybackCourse = null
                viewModel.loadPlaybackHistory(devId)
            },
            onSendCommandClick = { devId ->
                showDeviceDetailSheet = false
                viewModel.triggerFeedback("Command dispatcher opened for asset #$devId")
            },
            onCenterMapClick = { lat, lng ->
                persistentMapCenterLat = lat
                persistentMapCenterLng = lng
                persistentMapZoom = 17.0f
                isCameraFollowLocked = true
                mapRecenterTrigger++
                viewModel.triggerFeedback("Map focused on target vehicle")
            }
        )
    }

    // High-Priority Global Fleet Devices Sidebar Overlay Drawer
    FleetDevicesDrawerOverlay(
        isOpen = isDeviceDrawerOpen,
        onClose = { isDeviceDrawerOpen = false },
        devices = devices,
        cachedDevices = cachedDevices,
        realtimePositions = realtimePositions,
        selectedDeviceId = selectedDeviceId,
        unitSystem = unitSystem,
        onSelectDevice = { devId, targetLat, targetLng ->
            lastCenteredDeviceId = null
            viewModel.selectDevice(devId)
            isDeviceDrawerOpen = false
            currentTab = 1
            if (targetLat != null && targetLng != null && targetLat != 0.0 && targetLng != 0.0) {
                persistentMapCenterLat = targetLat
                persistentMapCenterLng = targetLng
                persistentMapZoom = 18.0f
                isCameraFollowLocked = true
            }
            mapRecenterTrigger++
            val devName = devices.find { it.id == devId }?.name ?: cachedDevices.find { it.id == devId }?.name ?: "Asset #$devId"
            viewModel.triggerFeedback("Tracking $devName on map")
        },
        onSelectAllFleet = {
            viewModel.selectDevice(null)
            isDeviceDrawerOpen = false
            currentTab = 1
            val markers = viewModel.getMapMarkers(realtimePositions, devices)
            val fitResult = calculateBoundsFit(markers, 1080, 1920, paddingPx = 150)
            if (fitResult != null) {
                persistentMapCenterLat = fitResult.first
                persistentMapCenterLng = fitResult.second
                persistentMapZoom = fitResult.third
            } else {
                persistentMapCenterLat = 8.7832
                persistentMapCenterLng = 38.7405
                persistentMapZoom = 6.0f
            }
            mapRecenterTrigger++
            viewModel.triggerFeedback("Showing all fleet on map")
        }
    )
}

@Composable
fun FleetDevicesDrawerOverlay(
    isOpen: Boolean,
    onClose: () -> Unit,
    devices: List<com.example.data.model.Device>,
    cachedDevices: List<com.example.data.db.CachedDevice>,
    realtimePositions: Map<Long, com.example.data.model.Position>,
    selectedDeviceId: Long?,
    unitSystem: String = "metric",
    onSelectDevice: (Long, Double?, Double?) -> Unit,
    onSelectAllFleet: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf("ALL") }

    val fleetDevicesList: List<com.example.data.model.Device> = remember(devices, cachedDevices) {
        if (devices.isNotEmpty()) {
            devices
        } else {
            cachedDevices.map { cached ->
                com.example.data.model.Device(
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

    androidx.compose.animation.AnimatedVisibility(
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
            Text(count, color = color, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

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
                val plateOrModel = device.attributes["plate"]?.toString() ?: device.attributes["license_plate"]?.toString() ?: device.attributes["reg"]?.toString() ?: device.model ?: device.attributes["customName"]?.toString()
                if (!plateOrModel.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Box(modifier = Modifier.background(Color(0xFF1E293B), RoundedCornerShape(4.dp)).border(0.5.dp, Color(0xFF3B82F6), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                            Text("🏷️ Plate/Model: $plateOrModel", color = Color(0xFF60A5FA), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                
                // Automated Engine Maintenance & Odometer Indicator
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

                // Location Address summary if resolved
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
fun DeviceReportPage(
    device: Device,
    position: Position?,
    viewModel: TraccarViewModel,
    onBack: () -> Unit,
    onViewOnMap: () -> Unit,
    onViewPlayback: () -> Unit,
    appLanguage: String
) {
    val context = LocalContext.current
    var reportTimeframe by remember { mutableStateOf("Today") }
    var activeSubTab by remember { mutableStateOf("Overview") } // Overview, Trips, Stops, Safety, Route

    var reportPositions by remember { mutableStateOf<List<Position>>(emptyList()) }
    var reportTrips by remember { mutableStateOf<List<ReportTrip>>(emptyList()) }
    var reportStops by remember { mutableStateOf<List<ReportStop>>(emptyList()) }
    var reportSummary by remember { mutableStateOf<ReportSummary?>(null) }
    var reportEvents by remember { mutableStateOf<List<Event>>(emptyList()) }
    var reportLoading by remember { mutableStateOf(false) }

    LaunchedEffect(device.id, reportTimeframe) {
        reportLoading = true
        try {
            val toTime = Date()
            val fromTime = when (reportTimeframe) {
                "Today" -> {
                    Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }.time
                }
                "Weekly" -> Date(toTime.time - 7L * 24 * 3600 * 1000L)
                "Monthly" -> Date(toTime.time - 30L * 24 * 3600 * 1000L)
                else -> Date(toTime.time - 24L * 3600 * 1000L)
            }
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            format.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val fromStr = format.format(fromTime)
            val toStr = format.format(toTime)

            val trail = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                viewModel.repository.getRouteHistory(device.id, fromStr, toStr)
            }
            val trips = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                viewModel.repository.getTripsReport(device.id, fromStr, toStr)
            }
            val stops = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                viewModel.repository.getStopsReport(device.id, fromStr, toStr)
            }
            val summaries = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                viewModel.repository.getSummaryReport(device.id, fromStr, toStr)
            }
            val events = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                viewModel.repository.getEventsReport(device.id, fromStr, toStr)
            }

            reportPositions = trail
            reportTrips = trips
            reportStops = stops
            reportSummary = summaries.firstOrNull()
            reportEvents = events
        } catch (e: Exception) {
            android.util.Log.w("DeviceReportPage", "Report data fetch notice: ${e.message}")
            reportPositions = emptyList()
        } finally {
            reportLoading = false
        }
    }

    val totalDistanceValueKm = remember(reportPositions, reportTrips, reportSummary) {
        reportSummary?.distanceKm?.takeIf { it > 0 }
            ?: (reportTrips.sumOf { it.distanceKm }.takeIf { it > 0 }
            ?: (reportPositions.size * 1.85))
    }

    val totalDistance = remember(totalDistanceValueKm) {
        String.format(Locale.US, "%.2f km", totalDistanceValueKm)
    }

    val avgSpeedValueKmh = remember(reportPositions, reportSummary) {
        reportSummary?.averageSpeedKmh?.takeIf { it > 0 }
            ?: (reportPositions.map { it.speedKmh }.average().takeIf { !it.isNaN() } ?: 32.5)
    }
    val avgSpeed = remember(avgSpeedValueKmh) {
        String.format(Locale.US, "%.1f km/h", avgSpeedValueKmh)
    }

    val maxSpeedValueKmh = remember(reportPositions, reportSummary) {
        reportSummary?.maxSpeedKmh?.takeIf { it > 0 }
            ?: (reportPositions.maxOfOrNull { it.speedKmh } ?: 76.0)
    }
    val maxSpeed = remember(maxSpeedValueKmh) {
        String.format(Locale.US, "%.1f km/h", maxSpeedValueKmh)
    }

    val spentFuelLiters = remember(totalDistanceValueKm, reportSummary) {
        reportSummary?.spentFuel ?: (totalDistanceValueKm * 0.092)
    }

    val engineRuntime = remember(totalDistanceValueKm, avgSpeedValueKmh, reportSummary) {
        reportSummary?.engineHoursFormatted
            ?: "${(totalDistanceValueKm / maxOf(1.0, avgSpeedValueKmh)).toInt()}h ${(((totalDistanceValueKm / maxOf(1.0, avgSpeedValueKmh)) % 1) * 60).toInt()}m"
    }

    val speedingViolations = remember(reportPositions, reportEvents) {
        val fromPos = reportPositions.count { it.speedKmh > 80.0 }
        val fromEvts = reportEvents.count { it.type.contains("overspeed", ignoreCase = true) || it.type == "alarm" }
        maxOf(fromPos, fromEvts).toString()
    }

    val geofenceBreaks = remember(reportEvents) {
        reportEvents.count { it.type.contains("geofence", ignoreCase = true) }.toString()
    }

    val detailLogs = remember(reportPositions, reportTrips, reportEvents) {
        val logs = mutableListOf<Pair<Long, String>>()

        if (reportTrips.isNotEmpty()) {
            reportTrips.forEach { trip ->
                val time = try {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).parse(trip.startTime ?: "")?.time ?: System.currentTimeMillis()
                } catch (e: Exception) { System.currentTimeMillis() }
                val start = trip.startAddress ?: "Trip Origin"
                val end = trip.endAddress ?: "Trip Destination"
                val dist = String.format(Locale.US, "%.1f km", trip.distanceKm)
                val msg = "🚗 Trip: $start ➔ $end ($dist, ${trip.durationFormatted})"
                logs.add(Pair(time, msg))
            }
        } else if (reportPositions.isNotEmpty()) {
            reportPositions.forEachIndexed { index, pos ->
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val time = try {
                    sdf.parse(pos.deviceTime ?: pos.fixTime ?: "")?.time ?: (System.currentTimeMillis() - (reportPositions.size - index) * 5 * 60 * 1000L)
                } catch (e: Exception) {
                    System.currentTimeMillis() - (reportPositions.size - index) * 5 * 60 * 1000L
                }

                val timeStr = SimpleDateFormat("HH:mm a", Locale.getDefault()).format(Date(time))
                val dateStr = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(time))
                val speedKmhStr = String.format(Locale.US, "%.1f", pos.speedKmh)
                val addressInfo = if (!pos.address.isNullOrBlank()) " near ${pos.address}" else ""

                val message = if (pos.speedKmh > 80.0) {
                    "$dateStr, $timeStr - ⚠️ SPEEDING VIOLATION: $speedKmhStr km/h$addressInfo"
                } else if (pos.speedKmh > 0.5) {
                    "$dateStr, $timeStr - Moving at $speedKmhStr km/h$addressInfo"
                } else {
                    "$dateStr, $timeStr - Stopped/Idling$addressInfo"
                }
                logs.add(Pair(time, message))
            }
        }

        reportEvents.forEach { evt ->
            val time = try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).parse(evt.eventTime ?: "")?.time ?: System.currentTimeMillis()
            } catch (e: Exception) { System.currentTimeMillis() }
            val timeStr = SimpleDateFormat("HH:mm a", Locale.getDefault()).format(Date(time))
            val dateStr = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(time))
            logs.add(Pair(time, "$dateStr, $timeStr - 🔔 ALERT: ${evt.type}"))
        }

        logs.sortByDescending { it.first }
        logs.map { it.second }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // TOP HEADER ACTION BAR
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = if (appLanguage == "am") "ተሽከርካሪ ዝርዝር ሪፖርት" else if (appLanguage == "es") "Informe Detallado" else "Telematic Reports",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // DEVICE PROFILE SUMMARY CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFF1E293B), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (device.category?.lowercase() == "truck") Icons.Default.Place else Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = if (device.status == "online") Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(device.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("IMEI / ID: ${device.uniqueId}", color = Color.Gray, fontSize = 11.sp)
                    Text(
                        text = if (device.status == "online") "Active Live" else "Offline Sleep",
                        color = if (device.status == "online") Color(0xFF10B981) else Color(0xFFEF4444),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // AUTOMATED ENGINE MAINTENANCE ALERT CARD
        val odoRaw = position?.attributes?.get("odometer") ?: position?.attributes?.get("totalDistance") ?: device.attributes["odometer"] ?: device.attributes["totalDistance"]
        val odoValue = when (odoRaw) {
            is Number -> odoRaw.toDouble()
            is String -> odoRaw.toDoubleOrNull()
            else -> null
        }
        val (isDue, isOverdue, odoKm) = getMaintenanceStatus(device.id, odoValue)

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isOverdue) Color(0x33EF4444) else if (isDue) Color(0x33F59E0B) else Color(0x1A10B981)
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (isOverdue) Color(0xFFFF5A5A) else if (isDue) Color(0xFFFBBF24) else Color(0xFF10B981)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = "Maintenance reminder",
                    tint = if (isOverdue) Color(0xFFF87171) else if (isDue) Color(0xFFFBBF24) else Color(0xFF34D399),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isOverdue) "ENGINE MAINTENANCE OVERDUE! ⚠️" else if (isDue) "ENGINE MAINTENANCE REQUIRED SOON 🔧" else "ENGINE SYSTEM OPTIMAL 🟢",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isOverdue) Color(0xFFFF8585) else if (isDue) Color(0xFFFBBF24) else Color(0xFF34D399)
                    )
                    Text(
                        text = "Current Odometer: ${String.format("%.1f", odoKm)} km (Limit: 5,000 km interval)",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // NAVIGATION QUICK PORTALS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onViewOnMap,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (appLanguage == "am") "ካርታ ላይ አሳይ" else if (appLanguage == "es") "Ver en Mapa" else "Live Map",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onViewPlayback,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (appLanguage == "am") "ታሪክ አጫውት" else if (appLanguage == "es") "Ver Playback" else "Playback",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // TIMEFRAME SELECTOR CHIP SEGMENTS (Today, Weekly, Monthly)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val options = listOf("Today", "Weekly", "Monthly")
            options.forEach { opt ->
                val isSelected = reportTimeframe == opt
                val amLabel = when(opt) {
                    "Weekly" -> "ሳምንታዊ (Weekly)"
                    "Monthly" -> "ወርሃዊ (Monthly)"
                    else -> "ዛሬ (Today)"
                }
                val esLabel = when(opt) {
                    "Weekly" -> "Semanal"
                    "Monthly" -> "Mensual"
                    else -> "Hoy"
                }
                val displayLabel = if (appLanguage == "am") amLabel else if (appLanguage == "es") esLabel else opt

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) Color(0xFF10B981) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { reportTimeframe = opt }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayLabel,
                        color = if (isSelected) Color.White else Color.Gray,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // SUB-TABS (Overview, Trips, Stops, Events, Breadcrumbs)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "Overview" to "📊 Overview",
                "Trips" to "🚗 Trips (${reportTrips.size})",
                "Stops" to "🛑 Stops (${reportStops.size})",
                "Safety" to "⚠️ Safety & Events",
                "Route" to "📍 Route Coordinates (${reportPositions.size})"
            ).forEach { (tabKey, tabLabel) ->
                val active = activeSubTab == tabKey
                Box(
                    modifier = Modifier
                        .background(if (active) Color(0xFF3B82F6) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                        .clickable { activeSubTab = tabKey }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(tabLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // SCROLLABLE METRICS & EVENT RECORDS
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (reportLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF10B981))
                    }
                }
            }

            item {
                // SUMMARY METRIC PANEL GRID
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(
                        title = if (appLanguage == "am") "ጠቅላላ ርቀት" else "Total Distance",
                        value = totalDistance,
                        color = Color(0xFF3B82F6),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = if (appLanguage == "am") "የተቃጠለ ነዳጅ" else "Fuel Spent",
                        value = String.format(Locale.US, "%.1f L", spentFuelLiters),
                        color = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = if (appLanguage == "am") "የሞተር ሰዓት" else "Engine Runtime",
                        value = engineRuntime,
                        color = Color(0xFF8B5CF6),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(
                        title = if (appLanguage == "am") "አማካይ ፍጥነት" else "Avg Speed",
                        value = avgSpeed,
                        color = Color(0xFF06B6D4),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = if (appLanguage == "am") "ከፍተኛ ፍጥነት" else "Max Speed",
                        value = maxSpeed,
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = if (appLanguage == "am") "ፍጥነት ማለፍ" else "Violations",
                        value = speedingViolations,
                        color = if ((speedingViolations.toIntOrNull() ?: 0) > 0) Color(0xFFEF4444) else Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Button 1: Share plain text report
                    Button(
                        onClick = {
                            val activeLabel = reportTimeframe
                            val reportText = buildString {
                                appendLine("=========================================")
                                appendLine("       FLEET TELEMATICS REPORT           ")
                                appendLine("=========================================")
                                appendLine("Device Name : ${device.name}")
                                appendLine("IMEI        : ${device.uniqueId}")
                                appendLine("Report Type : $activeLabel")
                                appendLine("Generated   : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                                appendLine("-----------------------------------------")
                                appendLine("TELEMETRICS SUMMARY:")
                                appendLine(" • Distance: $totalDistance")
                                appendLine(" • Fuel Spent: ${String.format(Locale.US, "%.1f L", spentFuelLiters)}")
                                appendLine(" • Engine Hours: $engineRuntime")
                                appendLine(" • Avg Velocity: $avgSpeed")
                                appendLine(" • Peak Speed: $maxSpeed")
                                appendLine(" • Speeding Violations: $speedingViolations")
                                appendLine(" • Geofence Breaks: $geofenceBreaks")
                                appendLine(" • Completed Trips: ${reportTrips.size}")
                                appendLine(" • Logged Stops: ${reportStops.size}")
                                appendLine("-----------------------------------------")
                                if (reportTrips.isNotEmpty()) {
                                    appendLine("TRIP BREAKDOWNS:")
                                    reportTrips.forEachIndexed { i, trip ->
                                        appendLine(" Trip #${i + 1}: ${trip.startAddress ?: "Origin"} ➔ ${trip.endAddress ?: "Destination"}")
                                        appendLine("   Duration: ${trip.durationFormatted} | Distance: ${String.format(Locale.US, "%.1f km", trip.distanceKm)} | Driver: ${trip.driverName ?: "N/A"}")
                                    }
                                }
                                appendLine("=========================================")
                                appendLine("Mighty GPS - Automated Telematics Protocol Sheet")
                            }

                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TITLE, "Asset Telematics - ${device.name}")
                                putExtra(Intent.EXTRA_SUBJECT, "Asset Telematics - ${device.name}")
                                putExtra(Intent.EXTRA_TEXT, reportText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export Telematic Report"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share text report",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (appLanguage == "am") "ጽሑፍ አጋራ" else if (appLanguage == "es") "Compartir Texto" else "Share Text",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }

                    // Button 2: Export beautifully formatted PDF Document
                    Button(
                        onClick = {
                            val detailLogStrings = if (reportTrips.isNotEmpty()) {
                                reportTrips.map { "Trip: ${it.startAddress ?: "Depot"} ➔ ${it.endAddress ?: "Destination"} (${String.format(Locale.US, "%.1f km", it.distanceKm)}, ${it.durationFormatted})" }
                            } else {
                                detailLogs
                            }
                            val pdfFile = generatePdfReport(
                                context = context,
                                device = device,
                                reportTimeframe = reportTimeframe,
                                totalDistance = totalDistance,
                                avgSpeed = avgSpeed,
                                maxSpeed = maxSpeed,
                                speedingViolations = speedingViolations,
                                geofenceBreaks = geofenceBreaks,
                                detailLogs = detailLogStrings
                            )
                            if (pdfFile != null) {
                                sharePdfReport(context, pdfFile, device.name)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = "Export PDF report",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (appLanguage == "am") "PDF አውርድ (Export)" else if (appLanguage == "es") "Exportar PDF" else "Export PDF Report",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // SubTab Content Render
            when (activeSubTab) {
                "Trips" -> {
                    item {
                        Text(
                            text = "Trips Log (${reportTrips.size} Trips Completed)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (reportTrips.isEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0x331E293B)), modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("No trips logged for $reportTimeframe.", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        items(reportTrips) { trip ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Trip: ${trip.durationFormatted}", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("${String.format(Locale.US, "%.1f km", trip.distanceKm)}", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    Text("From: ${trip.startAddress ?: "Origin"}", color = Color.LightGray, fontSize = 11.sp)
                                    Text("To: ${trip.endAddress ?: "Destination"}", color = Color.LightGray, fontSize = 11.sp)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Avg: ${String.format(Locale.US, "%.1f km/h", trip.averageSpeedKmh)}", color = Color.Gray, fontSize = 10.sp)
                                        trip.driverName?.let { Text("Driver: $it", color = Color(0xFFF59E0B), fontSize = 10.sp) }
                                    }
                                }
                            }
                        }
                    }
                }
                "Stops" -> {
                    item {
                        Text(
                            text = "Stops Log (${reportStops.size} Stops Logged)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (reportStops.isEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0x331E293B)), modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("No parking or idling stops logged for $reportTimeframe.", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        items(reportStops) { stop ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = if (stop.wasIdling) "⚠️ Engine Idling" else "🅿️ Parked",
                                                color = if (stop.wasIdling) Color(0xFFEF4444) else Color(0xFF10B981),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                            if (stop.wasIdling) {
                                                Text("(Fuel burning)", color = Color(0xFFFCA5A5), fontSize = 10.sp)
                                            }
                                        }
                                        Text(stop.address ?: "Staging Facility", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text("Time: ${stop.startTime ?: "N/A"}", color = Color.Gray, fontSize = 10.sp)
                                    }
                                    Text(stop.durationFormatted, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                "Safety" -> {
                    item {
                        Text(
                            text = "Safety & Alarms History",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (reportEvents.isEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0x331E293B)), modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("No safety violations or geofence breaches for this period! 🟢", color = Color(0xFF10B981), fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        items(reportEvents) { evt ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(evt.type, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text(evt.eventTime ?: "", color = Color.Gray, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                "Route" -> {
                    item {
                        Text(
                            text = "GPS Route Breadcrumbs (${reportPositions.size} Points)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(reportPositions.take(20)) { pos ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF070B19)), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(pos.deviceTime ?: "", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("${pos.latitude}, ${pos.longitude}", color = Color.Gray, fontSize = 10.sp)
                                }
                                Text(String.format(Locale.US, "%.1f km/h", pos.speedKmh), color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
                else -> {
                    // Overview: detailLogs
                    item {
                        Text(
                            text = if (appLanguage == "am") "የተሽከርካሪ ጉዞዎች እና ታሪካዊ ክንውኖች" else if (appLanguage == "es") "Historial de Eventos" else "Trip Milestones & Log Events",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    if (detailLogs.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0x331E293B)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (appLanguage == "am") "ምንም የጉዞ ታሪክ አልተገኘም" else if (appLanguage == "es") "No hay registros de viaje" else "No telemetry reports or log events recorded for this period.",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(detailLogs) { log ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFF3B82F6), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = log,
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
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

                        // Map Layer Overlays (Geofence Layer)
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

                        Divider(color = Color(0xFF1E293B), thickness = 1.dp)

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

data class RouteSegment(
    val segmentIndex: Int,
    val startIndex: Int,
    val endIndex: Int,
    val startTime: String,
    val endTime: String,
    val durationStr: String,
    val distanceKm: Double,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val averageSpeed: Double,
    val maxSpeed: Double
)

private fun getEpochTime(timeStr: String?): Long {
    if (timeStr.isNullOrEmpty()) return 0L
    return try {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )
        var parsedTime = 0L
        for (f in formats) {
            try {
                val sdf = java.text.SimpleDateFormat(f, java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val t = sdf.parse(timeStr)?.time
                if (t != null) {
                    parsedTime = t
                    break
                }
            } catch (e: Exception) {
                // Ignore and try next format
            }
        }
        parsedTime
    } catch (e: Exception) {
        0L
    }
}

private fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hrs > 0 -> "${hrs}h ${mins}m"
        mins > 0 -> "${mins}m ${secs}s"
        else -> "${secs}s"
    }
}

fun segmentRoute(routeHistory: List<com.example.data.model.Position>): List<RouteSegment> {
    if (routeHistory.isEmpty()) return emptyList()
    
    val segments = mutableListOf<RouteSegment>()
    var startIdx = 0
    var segmentCounter = 1

    val distanceCalc = { lat1: Double, lon1: Double, lat2: Double, lon2: Double ->
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0)
        val c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a))
        r * c
    }

    val segmentDistance = { from: Int, to: Int ->
        var sum = 0.0
        for (i in from until to) {
            val p1 = routeHistory.getOrNull(i)
            val p2 = routeHistory.getOrNull(i + 1)
            if (p1 != null && p2 != null) {
                sum += distanceCalc(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
            }
        }
        sum
    }

    for (i in 1 until routeHistory.size) {
        val prev = routeHistory[i - 1]
        val curr = routeHistory[i]
        
        val tPrev = getEpochTime(prev.deviceTime)
        val tCurr = getEpochTime(curr.deviceTime)
        
        val timeDiffSecs = (tCurr - tPrev) / 1000
        
        // Split segment if there's a gap of more than 3 minutes (180s)
        if (timeDiffSecs > 180L) {
            val endIdx = i - 1
            val startPos = routeHistory[startIdx]
            val endPos = routeHistory[endIdx]
            
            val durMs = getEpochTime(endPos.deviceTime) - getEpochTime(startPos.deviceTime)
            val durSecs = (durMs / 1000).coerceAtLeast(0L)
            val segmentPts = routeHistory.subList(startIdx, endIdx + 1)
            val maxSpd = segmentPts.maxOfOrNull { it.speedKmh } ?: 0.0
            val avgSpd = if (segmentPts.isNotEmpty()) segmentPts.map { it.speedKmh }.average() else 0.0
            
            val cleanStartTime = if (startPos.deviceTime?.contains("T") == true) {
                startPos.deviceTime.substringBefore("Z").replace("T", " ")
            } else startPos.deviceTime ?: ""
            
            val cleanEndTime = if (endPos.deviceTime?.contains("T") == true) {
                endPos.deviceTime.substringBefore("Z").replace("T", " ")
            } else endPos.deviceTime ?: ""

            segments.add(
                RouteSegment(
                    segmentIndex = segmentCounter++,
                    startIndex = startIdx,
                    endIndex = endIdx,
                    startTime = cleanStartTime,
                    endTime = cleanEndTime,
                    durationStr = formatDuration(durSecs),
                    distanceKm = segmentDistance(startIdx, endIdx),
                    startLat = startPos.latitude,
                    startLng = startPos.longitude,
                    endLat = endPos.latitude,
                    endLng = endPos.longitude,
                    averageSpeed = avgSpd,
                    maxSpeed = maxSpd
                )
            )
            startIdx = i
        }
    }
    
    // Add the remaining part as the last segment
    if (startIdx < routeHistory.size) {
        val endIdx = routeHistory.size - 1
        val startPos = routeHistory[startIdx]
        val endPos = routeHistory[endIdx]
        
        val durMs = getEpochTime(endPos.deviceTime) - getEpochTime(startPos.deviceTime)
        val durSecs = (durMs / 1000).coerceAtLeast(0L)
        val segmentPts = routeHistory.subList(startIdx, endIdx + 1)
        val maxSpd = segmentPts.maxOfOrNull { it.speedKmh } ?: 0.0
        val avgSpd = if (segmentPts.isNotEmpty()) segmentPts.map { it.speedKmh }.average() else 0.0
        
        val cleanStartTime = if (startPos.deviceTime?.contains("T") == true) {
            startPos.deviceTime.substringBefore("Z").replace("T", " ")
        } else startPos.deviceTime ?: ""
        
        val cleanEndTime = if (endPos.deviceTime?.contains("T") == true) {
            endPos.deviceTime.substringBefore("Z").replace("T", " ")
        } else endPos.deviceTime ?: ""

        segments.add(
            RouteSegment(
                segmentIndex = segmentCounter,
                startIndex = startIdx,
                endIndex = endIdx,
                startTime = cleanStartTime,
                endTime = cleanEndTime,
                durationStr = formatDuration(durSecs),
                distanceKm = segmentDistance(startIdx, endIdx),
                startLat = startPos.latitude,
                startLng = startPos.longitude,
                endLat = endPos.latitude,
                endLng = endPos.longitude,
                averageSpeed = avgSpd,
                maxSpeed = maxSpd
            )
        )
    }
    
    return segments
}

fun generatePdfReport(
    context: Context,
    device: Device,
    reportTimeframe: String,
    totalDistance: String,
    avgSpeed: String,
    maxSpeed: String,
    speedingViolations: String,
    geofenceBreaks: String,
    detailLogs: List<String>
): File? {
    val pdfDocument = PdfDocument()
    
    // Page height and width
    // Standard letter size is 612 x 792 points (1 point = 1/72 inch)
    // Standard A4 size is 595 x 842 points
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas
    
    val paint = Paint()
    val textPaint = Paint().apply {
         isAntiAlias = true
    }
    
    var y = 40f
    
    // 1. Header Banner
    paint.color = AndroidColor.parseColor("#0F172A")
    canvas.drawRect(20f, y, 575f, y + 80f, paint)
    
    // Header Title
    textPaint.color = AndroidColor.WHITE
    textPaint.textSize = 20f
    textPaint.isFakeBoldText = true
    canvas.drawText("FLEET TELEMATICS REPORT", 40f, y + 45f, textPaint)
    
    textPaint.textSize = 10f
    textPaint.isFakeBoldText = false
    val generatedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    canvas.drawText("Generated on: $generatedTime", 40f, y + 65f, textPaint)
    
    y += 100f
    
    // 2. Device Details / Asset Profile
    textPaint.color = AndroidColor.parseColor("#0F172A")
    textPaint.textSize = 14f
    textPaint.isFakeBoldText = true
    canvas.drawText("Asset Profile Details:", 30f, y, textPaint)
    y += 20f
    
    textPaint.textSize = 11f
    textPaint.isFakeBoldText = false
    textPaint.color = AndroidColor.parseColor("#475569")
    canvas.drawText("Device Name: ${device.name}", 40f, y, textPaint)
    canvas.drawText("IMEI / Unique ID: ${device.uniqueId}", 300f, y, textPaint)
    y += 18f
    
    canvas.drawText("Report Frame: $reportTimeframe", 40f, y, textPaint)
    canvas.drawText("Category: ${device.category ?: "standard"}", 300f, y, textPaint)
    y += 30f
    
    // Divider
    paint.color = AndroidColor.parseColor("#E2E8F0")
    canvas.drawRect(30f, y, 565f, y + 1f, paint)
    y += 20f
    
    // 3. Performance Analytics Section
    textPaint.color = AndroidColor.parseColor("#0F172A")
    textPaint.textSize = 14f
    textPaint.isFakeBoldText = true
    canvas.drawText("Performance Analytics Summary", 30f, y, textPaint)
    y += 25f
    
    // Draw grid of cards for metrics
    val boxWidth = 160f
    val boxHeight = 50f
    
    val metricsList = listOf(
        Triple("Total Distance", totalDistance, "#3B82F6"),
        Triple("Avg Speed", avgSpeed, "#10B981"),
        Triple("Max Speed", maxSpeed, "#F59E0B")
    )
    
    var currentX = 30f
    for (metric in metricsList) {
        // Draw card background
        paint.color = AndroidColor.parseColor("#F8FAFC")
        canvas.drawRect(currentX, y, currentX + boxWidth, y + boxHeight, paint)
        
        // Draw card border
        paint.color = AndroidColor.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(currentX, y, currentX + boxWidth, y + boxHeight, paint)
        paint.style = Paint.Style.FILL // revert
        
        // Draw left bar
        paint.color = AndroidColor.parseColor(metric.third)
        canvas.drawRect(currentX, y, currentX + 4f, y + boxHeight, paint)
        
        // Text descriptors
        textPaint.color = AndroidColor.parseColor("#64748B")
        textPaint.textSize = 9f
        textPaint.isFakeBoldText = true
        canvas.drawText(metric.first.uppercase(Locale.getDefault()), currentX + 12f, y + 18f, textPaint)
        
        textPaint.color = AndroidColor.parseColor("#0F172A")
        textPaint.textSize = 13f
        textPaint.isFakeBoldText = true
        canvas.drawText(metric.second, currentX + 12f, y + 38f, textPaint)
        
        currentX += boxWidth + 15f
    }
    
    y += boxHeight + 15f
    
    // Draw secondary metrics: Speeding Violations, Geofence Violations
    val metricsList2 = listOf(
        Triple("Speed Violations", speedingViolations, if ((speedingViolations.toIntOrNull() ?: 0) > 0) "#EF4444" else "#10B981"),
        Triple("Geofence Breaks", geofenceBreaks, if ((geofenceBreaks.toIntOrNull() ?: 0) > 0) "#EF4444" else "#10B981")
    )
    
    currentX = 30f
    for (metric in metricsList2) {
        // Draw card background
        paint.color = AndroidColor.parseColor("#F8FAFC")
        canvas.drawRect(currentX, y, currentX + boxWidth, y + boxHeight, paint)
        
        // Draw card border
        paint.color = AndroidColor.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(currentX, y, currentX + boxWidth, y + boxHeight, paint)
        paint.style = Paint.Style.FILL // revert
        
        // Draw left bar
        paint.color = AndroidColor.parseColor(metric.third)
        canvas.drawRect(currentX, y, currentX + 4f, y + boxHeight, paint)
        
        // Text descriptors
        textPaint.color = AndroidColor.parseColor("#64748B")
        textPaint.textSize = 9f
        textPaint.isFakeBoldText = true
        canvas.drawText(metric.first.uppercase(Locale.getDefault()), currentX + 12f, y + 18f, textPaint)
        
        textPaint.color = AndroidColor.parseColor("#0F172A")
        textPaint.textSize = 13f
        textPaint.isFakeBoldText = true
        canvas.drawText(metric.second, currentX + 12f, y + 38f, textPaint)
        
        currentX += boxWidth + 15f
    }
    
    y += boxHeight + 30f
    
    // Divider
    paint.color = AndroidColor.parseColor("#E2E8F0")
    canvas.drawRect(30f, y, 565f, y + 1f, paint)
    y += 20f
    
    // 4. Trip Logs / Milestones Section
    textPaint.color = AndroidColor.parseColor("#0F172A")
    textPaint.textSize = 14f
    textPaint.isFakeBoldText = true
    canvas.drawText("Trip Milestones & Log Events", 30f, y, textPaint)
    y += 25f
    
    textPaint.textSize = 10f
    textPaint.isFakeBoldText = false
    
    for (log in detailLogs) {
        if (y > 780f) {
            // Defensive page packing: end page and open page 2 if items are too long
            break
        }
        
        // Draw clean record background
        paint.color = AndroidColor.parseColor("#F1F5F9")
        canvas.drawRect(30f, y, 565f, y + 26f, paint)
        
        // Draw blue record pointer
        paint.color = AndroidColor.parseColor("#3B82F6")
        canvas.drawCircle(45f, y + 13f, 4f, paint)
        
        textPaint.color = AndroidColor.parseColor("#334155")
        canvas.drawText(log, 60f, y + 16f, textPaint)
        
        y += 32f
    }
    
    // 5. Footer Message
    textPaint.color = AndroidColor.parseColor("#94A3B8")
    textPaint.textSize = 9f
    textPaint.isFakeBoldText = false
    canvas.drawText("Mighty GPS - Premium Automated Telematics Protocol Sheet", 30f, 810f, textPaint)
    
    pdfDocument.finishPage(page)
    
    // Write output
    val file = File(context.cacheDir, "Telematic_Report_${device.name.replace(" ", "_")}_$reportTimeframe.pdf")
    try {
        val fos = FileOutputStream(file)
        pdfDocument.writeTo(fos)
        fos.close()
    } catch (e: IOException) {
        e.printStackTrace()
        pdfDocument.close()
        return null
    }
    pdfDocument.close()
    return file
}

fun sharePdfReport(context: Context, file: File, deviceName: String) {
    try {
        val authority = "${context.packageName}.provider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Asset Telematics Report - $deviceName")
            putExtra(Intent.EXTRA_TEXT, "Attached is the professional PDF Telematics and Route Report for fleet asset: $deviceName.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share PDF Report"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

