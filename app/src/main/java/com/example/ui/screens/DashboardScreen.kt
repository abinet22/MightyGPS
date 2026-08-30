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
import com.example.util.RouteSegment
import com.example.util.generatePdfReport
import com.example.util.segmentRoute
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
                        if (currentTab == 0 && selectedReportDevice != null) {
                            IconButton(onClick = { selectedReportDevice = null }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back to Fleet List",
                                    tint = Color.White
                                )
                            }
                        } else if (currentTab == 3 || currentTab == 4 || currentTab == 6 || currentTab == 7) {
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
                                text = if (currentTab == 0) {
                                    if (selectedReportDevice != null) "${selectedReportDevice?.name} Report" else "Fleet Reports"
                                } else if (currentTab == 5) "Settings & Preferences" else "MightyGPS Fleet Control",
                                style = MaterialTheme.typography.headlineSmall,
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
                                        style = MaterialTheme.typography.labelSmall,
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
                                        style = MaterialTheme.typography.labelSmall,
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
                    label = { Text(viewModel.translate("active_fleet"), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) },
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
                    label = { Text(if (viewModel.appLanguage.value == "es") "Reportes" else if (viewModel.appLanguage.value == "am") "ሪፖርቶች" else "Reports", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) },
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
                    label = { Text(viewModel.translate("settings"), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) },
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
                                                    style = MaterialTheme.typography.labelSmall,
                                                    letterSpacing = 1.sp
                                                )
                                                Text(
                                                    text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(alert.timestamp)),
                                                    color = Color.LightGray.copy(alpha = 0.6f),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = if (alert.type == "ENTERED") "${alert.deviceName} arrived inside ${alert.geofenceName}" else "${alert.deviceName} exited ${alert.geofenceName}",
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = if (alert.type == "ENTERED") "Safe boundary crossed successfully." else "Security notice: Fleet asset left zone.",
                                                color = Color.LightGray,
                                                style = MaterialTheme.typography.bodySmall
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
                                            style = MaterialTheme.typography.bodyMedium,
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
                                    style = MaterialTheme.typography.bodySmall,
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
                                            style = MaterialTheme.typography.bodySmall,
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
                                            style = MaterialTheme.typography.bodySmall,
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
                                style = MaterialTheme.typography.bodySmall,
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
            title = { Text("Commission New Tracker", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Register a new telematics tracker hardware device node onto this multi-tenant tenant account.", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                    
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



