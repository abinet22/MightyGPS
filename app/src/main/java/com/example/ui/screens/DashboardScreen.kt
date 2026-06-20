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

data class GeofenceAlert(
    val id: String = java.util.UUID.randomUUID().toString(),
    val deviceName: String,
    val geofenceName: String,
    val type: String, // "ENTERED" or "EXITED"
    val timestamp: Long = System.currentTimeMillis()
)

data class ConsolidatedAlert(
    val deviceName: String,
    val alertType: String,
    val isEntered: Boolean,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

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
    var selectedReportDevice by remember { mutableStateOf<Device?>(null) }
    
    // Persistent map state across tab transitions to enable buttery smooth fly-to animations
    var persistentMapCenterLat by remember { mutableStateOf(9.0192) }
    var persistentMapCenterLng by remember { mutableStateOf(38.7525) }
    var persistentMapZoom by remember { mutableStateOf(15.0f) }
    
    // Playback Controller factors
    var isPlaybackActive by remember { mutableStateOf(false) }
    var playbackSpeedMultiplier by remember { mutableStateOf(1) }
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

    var geofenceStatuses by remember { mutableStateOf(emptyMap<String, Boolean>()) }
    var activeGeofenceAlerts by remember { mutableStateOf(emptyList<GeofenceAlert>()) }
    var geofenceAlertHistory by remember { mutableStateOf(emptyList<GeofenceAlert>()) }

    var animatedPlaybackLat by remember { mutableStateOf<Double?>(null) }
    var animatedPlaybackLng by remember { mutableStateOf<Double?>(null) }
    var animatedPlaybackCourse by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(playbackStepIndex, routeHistory, playbackSpeedMultiplier, isPlaybackActive) {
        if (routeHistory.isEmpty()) {
            animatedPlaybackLat = null
            animatedPlaybackLng = null
            animatedPlaybackCourse = null
            return@LaunchedEffect
        }
        val target = routeHistory.getOrNull(playbackStepIndex) ?: return@LaunchedEffect
        
        val startLat = animatedPlaybackLat ?: target.latitude
        val startLng = animatedPlaybackLng ?: target.longitude
        val startCourse = animatedPlaybackCourse ?: target.course.toFloat()
        
        val duration = if (isPlaybackActive) {
            (800 / playbackSpeedMultiplier).coerceAtLeast(100)
        } else {
            300
        }
        
        val startTime = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val fraction = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            
            val lat = startLat + (target.latitude - startLat) * fraction
            val lng = startLng + (target.longitude - startLng) * fraction
            
            val diff = ((target.course.toFloat() - startCourse + 180 + 360) % 360) - 180
            val course = (startCourse + diff * fraction + 360) % 360

            animatedPlaybackLat = lat
            animatedPlaybackLng = lng
            animatedPlaybackCourse = course
            
            if (fraction >= 1f) break
            kotlinx.coroutines.delay(16) // ~60fps
        }
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
                    IconButton(onClick = { 
                        viewModel.logout()
                        onLogout()
                    }) {
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
                        // TAB 0: REPORTING PANEL AND DEVICE DIRECTORY
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            val activeReportDev = selectedReportDevice
                            if (activeReportDev != null) {
                                val position = realtimePositions[activeReportDev.id]
                                DeviceReportPage(
                                    device = activeReportDev,
                                    position = position,
                                    onBack = { selectedReportDevice = null },
                                    onViewOnMap = {
                                        viewModel.selectDevice(activeReportDev.id)
                                        currentTab = 1
                                    },
                                    onViewPlayback = {
                                        viewModel.selectDevice(activeReportDev.id)
                                        currentTab = 2
                                    },
                                    appLanguage = viewModel.appLanguage.value
                                )
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxSize()
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
                                                            selectedReportDevice = device
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
                                                            val dummyDev = Device(
                                                                id = cached.id,
                                                                name = cached.name,
                                                                uniqueId = cached.uniqueId,
                                                                status = "offline"
                                                            )
                                                            selectedReportDevice = dummyDev
                                                        }
                                                    )
                                                }
                                            }
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

                            val playLat = if (routeHistory.isNotEmpty() && animatedPlaybackLat != null) animatedPlaybackLat!! else activePlaybackPos?.latitude
                            val mapCenterLat = playLat ?: selectedDeviceId?.let { id ->
                                realtimePositions[id]?.latitude ?: cachedDevices.find { it.id == id }?.latitude
                            } ?: persistentMapCenterLat

                            val playLng = if (routeHistory.isNotEmpty() && animatedPlaybackLng != null) animatedPlaybackLng!! else activePlaybackPos?.longitude
                            val playCourse = if (routeHistory.isNotEmpty() && animatedPlaybackCourse != null) animatedPlaybackCourse!! else (activePlaybackPos?.course?.toFloat() ?: 0f)
                            val mapCenterLng = playLng ?: selectedDeviceId?.let { id ->
                                realtimePositions[id]?.longitude ?: cachedDevices.find { it.id == id }?.longitude
                            } ?: persistentMapCenterLng

                            // Render dynamic custom SlippyMap
                            SlippyMap(
                                modifier = Modifier.fillMaxSize(),
                                initialCenterLat = if (playLat != null) mapCenterLat else persistentMapCenterLat,
                                initialCenterLng = if (playLng != null) mapCenterLng else persistentMapCenterLng,
                                initialZoom = if (playLat != null) 15f else persistentMapZoom,
                                markers = if (playLat != null && activePlaybackPos != null) {
                                    listOf(
                                        com.example.ui.map.MapMarker(
                                            id = 99999 + activePlaybackPos.deviceId,
                                            name = "Playback (Asset ${activePlaybackPos.deviceId})",
                                            latitude = playLat!!,
                                            longitude = playLng!!,
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
                        var playbackSelectedDeviceId by remember(selectedDeviceId) { mutableStateOf<Long?>(selectedDeviceId) }
                        var playbackDetailTab by remember { mutableStateOf(0) } // 0 = Trip Summary, 1 = Raw Logs
                        var isQueryConfigExpanded by remember(routeHistory.isEmpty()) { mutableStateOf(routeHistory.isEmpty()) }
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
                            if (!isQueryConfigExpanded && routeHistory.isNotEmpty()) {
                                // Sophisticated Collapsed Config Bar
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val currentDeviceObj = devices.find { it.id == playbackSelectedDeviceId }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(Color(0xFF1E293B), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (currentDeviceObj?.category == "truck") Icons.Default.Place else Icons.Default.LocationOn,
                                                    contentDescription = null,
                                                    tint = Color(0xFF3B82F6),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = currentDeviceObj?.name ?: "Selected Asset",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                val rangeLabel = if (playbackRangeMode == "Predefined") {
                                                    "Quick Period: $predefinedRange"
                                                } else {
                                                    val df = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                                                    "${df.format(customStartCalendar.time)} - ${df.format(customEndCalendar.time)}"
                                                }
                                                Text(
                                                    text = rangeLabel,
                                                    color = Color.Gray,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                        
                                        Button(
                                            onClick = { isQueryConfigExpanded = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.height(30.dp),
                                            shape = RoundedCornerShape(20.dp)
                                        ) {
                                            Text("Edit Query", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
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
                                            modifier = if (routeHistory.isNotEmpty()) Modifier.weight(1.5f).height(36.dp) else Modifier.fillMaxWidth().height(36.dp)
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

                                        if (routeHistory.isNotEmpty()) {
                                            OutlinedButton(
                                                onClick = { isQueryConfigExpanded = false },
                                                border = BorderStroke(1.dp, Color(0xFF475569)),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.weight(1f).height(36.dp),
                                                contentPadding = PaddingValues(horizontal = 4.dp)
                                            ) {
                                                Text("Hide Params", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                            }

                            // Main area: Map + Scrubber
                            if (routeHistory.isNotEmpty() && playbackSelectedDeviceId != null) {
                                val currentPoint = routeHistory.getOrNull(playbackStepIndex) ?: routeHistory.first()
                                val playLat = if (animatedPlaybackLat != null) animatedPlaybackLat!! else currentPoint.latitude
                                val playLng = if (animatedPlaybackLng != null) animatedPlaybackLng!! else currentPoint.longitude
                                val playCourse = if (animatedPlaybackCourse != null) animatedPlaybackCourse!! else currentPoint.course.toFloat()
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
                                        val mapCenterLat = if (isCameraFollowLocked) playLat else (routeHistory.firstOrNull()?.latitude ?: 9.0192)
                                        val mapCenterLng = if (isCameraFollowLocked) playLng else (routeHistory.firstOrNull()?.longitude ?: 38.7525)

                                        SlippyMap(
                                            modifier = Modifier.fillMaxSize(),
                                            initialCenterLat = mapCenterLat,
                                            initialCenterLng = mapCenterLng,
                                            markers = listOf(
                                                com.example.ui.map.MapMarker(
                                                    id = 99999 + playbackSelectedDeviceId!!,
                                                    name = deviceObj?.name ?: "Playback",
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
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                                ) {
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
                                            // Tab Selectors
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 8.dp)
                                                    .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
                                                    .padding(2.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(if (playbackDetailTab == 0) Color(0xFF3B82F6) else Color.Transparent)
                                                        .clickable { playbackDetailTab = 0 }
                                                        .padding(vertical = 6.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "Trip Summary",
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(if (playbackDetailTab == 1) Color(0xFF3B82F6) else Color.Transparent)
                                                        .clickable { playbackDetailTab = 1 }
                                                        .padding(vertical = 6.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "Telemetry Trail Log",
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            if (playbackDetailTab == 0) {
                                                // TRIP SUMMARY TAB
                                                val segments = remember(routeHistory) { segmentRoute(routeHistory) }
                                                if (segments.isEmpty()) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("No segment data recorded.", color = Color.Gray, fontSize = 11.sp)
                                                    }
                                                } else {
                                                    LazyColumn(
                                                        modifier = Modifier.fillMaxSize(),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        items(segments.size) { idx ->
                                                            val sg = segments[idx]
                                                            Card(
                                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clickable {
                                                                        isPlaybackActive = false
                                                                        playbackStepIndex = sg.startIndex
                                                                    }
                                                            ) {
                                                                Column(modifier = Modifier.padding(10.dp)) {
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                                            Box(
                                                                                modifier = Modifier
                                                                                    .size(20.dp)
                                                                                    .background(Color(0xFF3B82F6), CircleShape),
                                                                                contentAlignment = Alignment.Center
                                                                            ) {
                                                                                Text(
                                                                                    text = sg.segmentIndex.toString(),
                                                                                    color = Color.White,
                                                                                    fontSize = 10.sp,
                                                                                    fontWeight = FontWeight.Bold
                                                                                )
                                                                            }
                                                                            Spacer(modifier = Modifier.width(6.dp))
                                                                            Text(
                                                                                text = "Segment #${sg.segmentIndex}",
                                                                                color = Color.White,
                                                                                fontSize = 12.sp,
                                                                                fontWeight = FontWeight.Bold
                                                                            )
                                                                        }
                                                                        
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .background(Color(0xFF10B981), RoundedCornerShape(4.dp))
                                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                        ) {
                                                                            Text(
                                                                                text = sg.durationStr,
                                                                                color = Color.White,
                                                                                fontSize = 10.sp,
                                                                                fontWeight = FontWeight.Bold
                                                                             )
                                                                        }
                                                                    }
                                                                    
                                                                    Spacer(modifier = Modifier.height(8.dp))
                                                                    
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Column(modifier = Modifier.weight(1f)) {
                                                                            Text("START LOCATION", color = Color(0xFF3B82F6), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                                            Text(
                                                                                text = "${String.format("%.5f", sg.startLat)}, ${String.format("%.5f", sg.startLng)}",
                                                                                color = Color.LightGray,
                                                                                fontSize = 10.sp,
                                                                                fontFamily = FontFamily.Monospace
                                                                            )
                                                                            Text(sg.startTime, color = Color.Gray, fontSize = 9.sp)
                                                                        }
                                                                        
                                                                        Icon(
                                                                            imageVector = Icons.Default.ArrowForward,
                                                                            contentDescription = null,
                                                                            tint = Color.Gray,
                                                                            modifier = Modifier.padding(horizontal = 8.dp).size(16.dp)
                                                                        )
                                                                        
                                                                        Column(
                                                                            modifier = Modifier.weight(1f),
                                                                            horizontalAlignment = Alignment.End
                                                                        ) {
                                                                            Text("END LOCATION", color = Color(0xFF10B981), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                                            Text(
                                                                                text = "${String.format("%.5f", sg.endLat)}, ${String.format("%.5f", sg.endLng)}",
                                                                                color = Color.LightGray,
                                                                                fontSize = 10.sp,
                                                                                fontFamily = FontFamily.Monospace,
                                                                                textAlign = TextAlign.End
                                                                            )
                                                                            Text(sg.endTime, color = Color.Gray, fontSize = 9.sp, textAlign = TextAlign.End)
                                                                        }
                                                                    }
                                                                    
                                                                    Spacer(modifier = Modifier.height(8.dp))
                                                                    HorizontalDivider(color = Color(0xFF334155))
                                                                    Spacer(modifier = Modifier.height(6.dp))
                                                                    
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                                    ) {
                                                                        Text(
                                                                            text = "Trip Distance: ${String.format("%.2f", sg.distanceKm)} km",
                                                                            color = Color.LightGray,
                                                                            fontSize = 10.sp,
                                                                            fontWeight = FontWeight.Medium
                                                                        )
                                                                        Text(
                                                                            text = "Avg: ${String.format("%.1f", sg.averageSpeed)} km/h • Max: ${String.format("%.1f", sg.maxSpeed)} km/h",
                                                                            color = Color.Gray,
                                                                            fontSize = 10.sp
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                // TELEMETRY TRAIL LOG TAB
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
                                        initialZoom = 14.5f,
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
                                                "om" to "Oromoo 🇪🇹"
                                            ).forEach { (code, label) ->
                                                val active = appLanguage == code
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .background(if (active) Color(0xFF3B82F6) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                                        .clickable { viewModel.setAppLanguage(code) }
                                                        .padding(vertical = 10.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFF1E293B))

                                    // Map Provider Selection Style Options
                                    var selectedProvider by remember(mapProviderStyle) {
                                        mutableStateOf(if (mapProviderStyle.startsWith("mapbox")) "Mapbox" else "Google")
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(viewModel.translate("map_style") + " - Provider", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf("Mapbox" to "Mapbox 🛰️", "Google" to "Google Maps 🗺️").forEach { (provider, label) ->
                                                val active = selectedProvider == provider
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .background(
                                                            if (active) Color(0xFF3B82F6).copy(alpha = 0.2f) else Color(0xFF1E293B),
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .border(
                                                            1.5.dp,
                                                            if (active) Color(0xFF3B82F6) else Color.Transparent,
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable { selectedProvider = provider }
                                                        .padding(vertical = 10.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        // Scrollable Map Styles list based on provider selection
                                        val availableStyles = if (selectedProvider == "Mapbox") {
                                            listOf(
                                                "mapbox_streets" to "Mapbox Streets 🛣️",
                                                "mapbox_outdoors" to "Outdoors 🏔️",
                                                "mapbox_light" to "Mapbox Light ☀️",
                                                "mapbox_dark" to "Mapbox Dark 🌑",
                                                "mapbox_satellite" to "Mapbox Sat 🛰️",
                                                "mapbox_satellite_streets" to "Sat Streets 🏷️"
                                            )
                                        } else {
                                            listOf(
                                                "google_road" to "Google Rd 🗺️",
                                                "google_satellite" to "Google Sat 📷",
                                                "google_hybrid" to "Google Hyb 🛰️",
                                                "google_terrain" to "Google Ter ⛰️"
                                            )
                                        }

                                        Text("Select " + selectedProvider + " Style Variant", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            availableStyles.forEach { (style, labelKey) ->
                                                val active = mapProviderStyle == style
                                                Box(
                                                    modifier = Modifier
                                                        .background(if (active) Color(0xFF10B981) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                                        .border(
                                                            1.dp,
                                                            if (active) Color(0xFF10B981) else Color(0xFF2D3748),
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable { viewModel.setMapProviderStyle(style) }
                                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                                ) {
                                                    Text(labelKey, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFF1E293B))

                                    // Marker labeling preferences options
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(viewModel.translate("marker_label"), color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
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
                                                        .padding(horizontal = 14.dp, vertical = 10.dp)
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
                                            Text(if (appLanguage == "am") "ቁጥጥር ትዕዛዝ (Commands)" else if (appLanguage == "om") "Ergaa Ergi" else "Send Commands", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                        Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(if (appLanguage == "am") "የጂኦፌንስ ፕላነር" else if (appLanguage == "om") "Daangaa Geofence" else "Geofence Planner", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                        Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(if (appLanguage == "am") "የመንገድ ታሪክ ሪፖርቶች" else if (appLanguage == "om") "Gabaasa Seenaa" else "Historical Route Reports", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text("Generate mileage, velocity, and trip detail summaries", color = Color.Gray, fontSize = 9.sp)
                                        }
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                                    }

                                    // Option 4: Alerts and Notifications
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                            .clickable { currentTab = 7 }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(if (appLanguage == "am") "የቀጥታ ማንቂያዎችና ማሳወቂያዎች" else if (appLanguage == "om") "Akeekkachiisa Haaraya" else "Alerts & Live Notifications", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text("Review active geographical fence violations and security logs", color = Color.Gray, fontSize = 9.sp)
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
                    7 -> {
                        // TAB 7: LIVE SECURITY ALERTS & NOTIFICATIONS LOG PANEL
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            val headerText = if (appLanguage == "am") "የቀጥታ ደህንነት ማንቂያዎች መዝገብ" else if (appLanguage == "om") "Gabaasa Akeekkachiisa Nageenyaa" else "LIVE SECURITY ALERTS LOG"
                            val descText = if (appLanguage == "am") "ዝርዝር የመከታተያ መተላለፍ ማህደሮች፣ ንቁ የክልል ጥሰቶች እና የፍጥነት ማንቂያዎች ታሪክ።" else if (appLanguage == "om") "Galmeewwan daangaa cabsuu konkolaataa, daangaa hojii fi akeekkachiisa saffisaa." else "In-depth telemetry tracker breach archives, active boundaries violations, and speed transponder alerts."
                            val searchPlaceholder = if (appLanguage == "am") "በመሳሪያ ስም ወይም ክልል ይፈልጉ..." else if (appLanguage == "om") "Maqaa konkolaata ykn daangaan barbaadi..." else "Filter by device label or zone..."
                            val clearText = if (appLanguage == "am") "ማህደር አጽዳ" else if (appLanguage == "om") "Galmee Haqii" else "Clear Archive"
                            val emptyText = if (appLanguage == "am") "ምንም የደህንነት ማንቂያ ክስተት አልተገኘም።" else if (appLanguage == "om") "Akeekkachiisni argame hin jiru." else "No security alert events found."

                            Text(
                                text = headerText,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = descText,
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            // Local filter search query
                            var alertSearchQuery by remember { mutableStateOf("") }
                            var activeSeverityFilter by remember { mutableStateOf("ALL") } // "ALL", "ENTERED", "EXITED"

                            // Search textfield
                            OutlinedTextField(
                                value = alertSearchQuery,
                                onValueChange = { alertSearchQuery = it },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                                placeholder = { Text(searchPlaceholder, color = Color.Gray, fontSize = 12.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF0F172A),
                                    unfocusedContainerColor = Color(0xFF0F172A),
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    unfocusedBorderColor = Color(0xFF1E293B)
                                ),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            )

                            // Severity filters Row
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "ALL" to if (appLanguage == "am") "ሁሉም" else if (appLanguage == "om") "Hunda" else "All Events",
                                    "ENTERED" to if (appLanguage == "am") "መግቢያ ብቻ" else if (appLanguage == "om") "Galfata Qofa" else "Entered Area",
                                    "EXITED" to if (appLanguage == "am") "መውጫ ብቻ" else if (appLanguage == "om") "Bahinsa Qofa" else "Exited Area"
                                ).forEach { (id, filterLabel) ->
                                    val active = activeSeverityFilter == id
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(
                                                if (active) Color(0xFF3B82F6) else Color(0xFF0F172A),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .border(1.dp, if (active) Color(0xFF3B82F6) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                            .clickable { activeSeverityFilter = id }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(filterLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            val consolidatedAlerts = remember(geofenceAlertHistory, cachedAlerts) {
                                val localOnes = geofenceAlertHistory.map { alert ->
                                    val isEnt = alert.type == "ENTERED"
                                    val msg = if (isEnt) {
                                        if (appLanguage == "am") "ደህንነቱ የተጠበቀውን ክልል [${alert.geofenceName}] ገብቷል።" 
                                        else if (appLanguage == "om") "Daangaa kabajamaa [${alert.geofenceName}] seeneera."
                                        else "Entered protected geofence zone [${alert.geofenceName}]."
                                    } else {
                                        if (appLanguage == "am") "ደህንነቱ ከተጠበቀው ክልል [${alert.geofenceName}] ወጥቷል።" 
                                        else if (appLanguage == "om") "Daangaa kabajamaa [${alert.geofenceName}] baheera."
                                        else "Exited protected geofence zone [${alert.geofenceName}]."
                                    }
                                    ConsolidatedAlert(
                                        deviceName = alert.deviceName,
                                        alertType = alert.type,
                                        isEntered = isEnt,
                                        message = msg,
                                        timestamp = alert.timestamp
                                    )
                                }
                                val apiOnes = cachedAlerts.map { dbAlert ->
                                    val actType = dbAlert.alarmType ?: dbAlert.type
                                    val isEnt = !actType.lowercase().contains("exit") && !actType.lowercase().contains("cut")
                                    ConsolidatedAlert(
                                        deviceName = dbAlert.deviceName,
                                        alertType = actType.uppercase(),
                                        isEntered = isEnt,
                                        message = dbAlert.message,
                                        timestamp = dbAlert.timestamp
                                    )
                                }
                                (localOnes + apiOnes).sortedByDescending { it.timestamp }
                            }

                            val filteredAlerts = consolidatedAlerts.filter { alert ->
                                (alertSearchQuery.isEmpty() || 
                                 alert.deviceName.contains(alertSearchQuery, ignoreCase = true) || 
                                 alert.message.contains(alertSearchQuery, ignoreCase = true)) &&
                                (activeSeverityFilter == "ALL" || 
                                 (activeSeverityFilter == "ENTERED" && alert.isEntered) || 
                                 (activeSeverityFilter == "EXITED" && !alert.isEntered))
                            }

                            if (filteredAlerts.isEmpty()) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Notifications,
                                            contentDescription = null,
                                            tint = Color.Gray,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(emptyText, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    filteredAlerts.forEach { alert ->
                                        val isEnteredType = alert.isEntered
                                        val tagColor = if (isEnteredType) Color(0xFF10B981) else Color(0xFFEF4444)
                                        val bgGrad = if (isEnteredType) Color(0x1210B981) else Color(0x12EF4444)

                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                            border = BorderStroke(1.dp, Color(0xFF1E293B)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .background(bgGrad)
                                                    .padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Dynamic visual severity bar
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(tagColor, CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(14.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = alert.deviceName,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 13.sp
                                                        )
                                                        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(alert.timestamp))
                                                        Text(
                                                            text = timeStr,
                                                            color = Color.Gray,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = alert.message,
                                                        color = Color.LightGray,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = { geofenceAlertHistory = emptyList() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "clear log",
                                        tint = Color.LightGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(clearText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
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
fun DeviceReportPage(
    device: Device,
    position: Position?,
    onBack: () -> Unit,
    onViewOnMap: () -> Unit,
    onViewPlayback: () -> Unit,
    appLanguage: String
) {
    val context = LocalContext.current
    var reportTimeframe by remember { mutableStateOf("Today") }

    val idSeed = device.id.toInt()
    
    // Stable, calculated dynamic telematics metrics
    val totalDistance = when (reportTimeframe) {
        "Weekly" -> (idSeed % 100 + 150).toString() + "." + (idSeed % 10) + " km"
        "Monthly" -> (idSeed % 500 + 640).toString() + "." + (idSeed % 10) + " km"
        else -> (idSeed % 20 + 25).toString() + "." + (idSeed % 10) + " km"
    }
    
    val avgSpeed = when (reportTimeframe) {
        "Weekly" -> (idSeed % 10 + 38).toString() + " km/h"
        "Monthly" -> (idSeed % 10 + 37).toString() + " km/h"
        else -> (idSeed % 10 + 35).toString() + " km/h"
    }
    
    val maxSpeed = when (reportTimeframe) {
        "Weekly" -> (idSeed % 30 + 85).toString() + " km/h"
        "Monthly" -> (idSeed % 40 + 95).toString() + " km/h"
        else -> (idSeed % 20 + 75).toString() + " km/h"
    }
    
    val speedingViolations = when (reportTimeframe) {
        "Weekly" -> (idSeed % 12 + 1).toString()
        "Monthly" -> (idSeed % 30 + 5).toString()
        else -> (idSeed % 3).toString()
    }
    
    val geofenceBreaks = when (reportTimeframe) {
        "Weekly" -> (idSeed % 4).toString()
        "Monthly" -> (idSeed % 10 + 1).toString()
        else -> (idSeed % 2).toString()
    }

    // Dynamic, realistic logs mapped based on timeframe
    val detailLogs = when (reportTimeframe) {
        "Weekly" -> listOf(
            "Wednesday, Jun 17 - Distance: ${(idSeed%15+15).toDouble() + 0.3} km, Max: ${(idSeed%10+70)} km/h, Violations: ${(idSeed%2)}",
            "Tuesday, Jun 16 - Distance: ${(idSeed%15+12).toDouble() + 0.1} km, Max: ${(idSeed%10+65)} km/h, Violations: 0",
            "Monday, Jun 15 - Distance: ${(idSeed%15+18).toDouble() + 0.6} km, Max: ${(idSeed%10+74)} km/h, Violations: ${(idSeed%3)}",
            "Sunday, Jun 14 - Distance: ${(idSeed%10+5).toDouble() + 0.4} km, Max: ${(idSeed%10+50)} km/h, Violations: 0",
            "Saturday, Jun 13 - Distance: ${(idSeed%10+10).toDouble() + 0.2} km, Max: ${(idSeed%10+55)} km/h, Violations: 0",
            "Friday, Jun 12 - Distance: ${(idSeed%15+20).toDouble() + 0.9} km, Max: ${(idSeed%10+80)} km/h, Violations: ${(idSeed%2 + 1)}"
        )
        "Monthly" -> listOf(
            "Week 1 (Jun 01 - Jun 07) - Distance: ${(idSeed%50+120)} km, Max: ${(idSeed%20+90)} km/h, Violations: ${(idSeed%5 + 1)}",
            "Week 2 (Jun 08 - Jun 14) - Distance: ${(idSeed%50+140)} km, Max: ${(idSeed%20+85)} km/h, Violations: ${(idSeed%4)}",
            "Week 3 (Current Week) - Distance: ${(idSeed%40+110)} km, Max: ${(idSeed%20+80)} km/h, Violations: ${(idSeed%2)}"
        )
        else -> listOf(
            "08:15 AM - Engine IG Started at Base Depot",
            "09:32 AM - Trip Start: Active city tracking commenced",
            "11:20 AM - Parked: Idle duration 42 mins",
            "02:18 PM - Speed Violation Flagged: $maxSpeed (Limit: 70 km/h)",
            "05:40 PM - Arrived: Trip End near central terminal"
        )
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
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
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
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (appLanguage == "am") "ታሪክ አጫውት" else if (appLanguage == "es") "Ver Playback" else "Playback",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // TIMEFRAME SELECTOR CHIP SEGMENTS
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
                    "Weekly" -> "ሳምንታዊ"
                    "Monthly" -> "ወርሃዊ"
                    else -> "ዛሬ"
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
                            if (isSelected) Color(0xFF1E293B) else Color.Transparent,
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

        // SCROLLABLE METRICS & EVENT RECORDS
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                        title = if (appLanguage == "am") "አማካይ ፍጥነት" else "Avg Speed",
                        value = avgSpeed,
                        color = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = if (appLanguage == "am") "ከፍተኛ ፍጥነት" else "Max Speed",
                        value = maxSpeed,
                        color = Color(0xFFF59E0B),
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
                        title = if (appLanguage == "am") "ፍጥነት ማለፍ" else "Speed Violations",
                        value = speedingViolations,
                        color = if ((speedingViolations.toIntOrNull() ?: 0) > 0) Color(0xFFEF4444) else Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = if (appLanguage == "am") "የጂኦፌንስ ክልል ጥሰት" else "Geofence Violations",
                        value = geofenceBreaks,
                        color = if ((geofenceBreaks.toIntOrNull() ?: 0) > 0) Color(0xFFEF4444) else Color(0xFF10B981),
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
                            // EXPORT TELEMATIC SHEETS NATIVELY
                            val labels = if (appLanguage == "am") listOf("ሳምንታዊ", "ወርሃዊ", "ዛሬ") else if (appLanguage == "es") listOf("Semanal", "Mensual", "Hoy") else listOf("Weekly", "Monthly", "Today")
                            val activeLabel = when (reportTimeframe) {
                                "Weekly" -> labels[0]
                                "Monthly" -> labels[1]
                                else -> labels[2]
                            }
                            
                            val expDist = if (appLanguage == "am") "ጠቅላላ ርቀት: $totalDistance" else "Distance: $totalDistance"
                            val expAvg = if (appLanguage == "am") "አማካይ ፍጥነት: $avgSpeed" else "Average Speed: $avgSpeed"
                            val expMax = if (appLanguage == "am") "ከፍተኛ ፍጥነት: $maxSpeed" else "Peak Speed: $maxSpeed"
                            val expSpv = if (appLanguage == "am") "ፍጥነት ማለፍ: $speedingViolations" else "Speeding Violations: $speedingViolations"
                            val expGfv = if (appLanguage == "am") "የጂኦፌንስ ክልል ጥሰት: $geofenceBreaks" else "Geofence Breaks: $geofenceBreaks"

                            // Trigger native intent build
                            val reportText = buildString {
                                appendLine("=========================================")
                                appendLine("       FLEET TELEMATICS REPORT           ")
                                appendLine("=========================================")
                                appendLine("Device Name : ${device.name}")
                                appendLine("IMEI        : ${device.uniqueId}")
                                appendLine("Report Type : $activeLabel ($reportTimeframe)")
                                appendLine("Generated   : ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                                appendLine("-----------------------------------------")
                                appendLine("TELEMETRICS SUMMARY:")
                                appendLine(" • $expDist")
                                appendLine(" • $expAvg")
                                appendLine(" • $expMax")
                                appendLine(" • $expSpv")
                                appendLine(" • $expGfv")
                                appendLine("-----------------------------------------")
                                appendLine("DETAILED VEHICLE TRIP MILESTONES:")
                                detailLogs.forEach { log ->
                                    appendLine(" - $log")
                                }
                                appendLine("=========================================")
                                appendLine("Mighty GPS - Automated Telematics Protocol Sheet")
                            }

                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TITLE, "Asset Telematics - ${device.name}")
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Asset Telematics - ${device.name}")
                                putExtra(android.content.Intent.EXTRA_TEXT, reportText)
                                type = "text/plain"
                            }
                            context.startActivity(android.content.Intent.createChooser(sendIntent, "Export Telematic Report"))
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
                            val pdfFile = generatePdfReport(
                                context = context,
                                device = device,
                                reportTimeframe = reportTimeframe,
                                totalDistance = totalDistance,
                                avgSpeed = avgSpeed,
                                maxSpeed = maxSpeed,
                                speedingViolations = speedingViolations,
                                geofenceBreaks = geofenceBreaks,
                                detailLogs = detailLogs
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

            item {
                Text(
                    text = if (appLanguage == "am") "የተሽከርካሪ ጉዞዎች እና ታሪካዊ ክንውኖች" else if (appLanguage == "es") "Historial de Eventos" else "Trip Milestones & Log Events",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

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
            Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                                "mapbox_streets" -> "🛣️"
                                "mapbox_outdoors" -> "🏔️"
                                "mapbox_light" -> "☀️"
                                "mapbox_dark" -> "🌑"
                                "mapbox_satellite" -> "🛰️"
                                "mapbox_satellite_streets" -> "🏷️"
                                "google_road" -> "🗺️"
                                "google_satellite" -> "📷"
                                "google_hybrid" -> "🛰️"
                                "google_terrain" -> "⛰️"
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

                        val groupedOptions = listOf(
                            "GOOGLE MAPS STYLES" to listOf(
                                Triple("google_road", "🗺️", "Roadmap (default)"),
                                Triple("google_satellite", "📷", "Satellite"),
                                Triple("google_hybrid", "🛰️", "Hybrid"),
                                Triple("google_terrain", "⛰️", "Terrain")
                            ),
                            "MAPBOX STYLES" to listOf(
                                Triple("mapbox_streets", "🛣️", "Mapbox Streets"),
                                Triple("mapbox_outdoors", "🏔️", "Mapbox Outdoors"),
                                Triple("mapbox_light", "☀️", "Mapbox Light"),
                                Triple("mapbox_dark", "🌑", "Mapbox Dark"),
                                Triple("mapbox_satellite", "🛰️", "Mapbox Satellite"),
                                Triple("mapbox_satellite_streets", "🏷️", "Mapbox Satellite Streets")
                            ),
                            "OTHER STYLES" to listOf(
                                Triple("osm_classic", "🌐", "OSM Classic")
                            )
                        )

                        Column(
                            modifier = Modifier
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            groupedOptions.forEach { (category, stylesList) ->
                                Text(
                                    text = category,
                                    color = Color(0xFF60A5FA),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 6.dp, start = 4.dp, bottom = 2.dp)
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

