package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.CachedAlert
import com.example.data.db.CachedDevice
import com.example.data.model.Device
import com.example.data.model.GeofenceAlert
import com.example.data.model.Position
import com.example.data.model.TraccarCommandType
import com.example.data.model.User
import com.example.data.repo.TraccarRepository
import com.example.ui.map.MapMarker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

sealed interface AuthUIState {
    object Idle : AuthUIState
    object Loading : AuthUIState
    data class Success(val user: User) : AuthUIState
    data class Error(val message: String) : AuthUIState
}

class TraccarViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "TraccarViewModel"
    val repository = TraccarRepository(application)
    val sessionManager = repository.sessionManager
    val abortController = AbortController()

    // Reactive State holds
    private val _authUIState = MutableStateFlow<AuthUIState>(AuthUIState.Idle)
    val authUIState: StateFlow<AuthUIState> = _authUIState.asStateFlow()

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _usersList = MutableStateFlow<List<User>>(emptyList())
    val usersList: StateFlow<List<User>> = _usersList.asStateFlow()

    private val _routeHistory = MutableStateFlow<List<Position>>(emptyList())
    val routeHistory: StateFlow<List<Position>> = _routeHistory.asStateFlow()

    private val _historyLoading = MutableStateFlow(false)
    val historyLoading: StateFlow<Boolean> = _historyLoading.asStateFlow()

    private var historyLoadingJob: kotlinx.coroutines.Job? = null

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    fun clearSyncError() {
        _syncError.value = null
    }

    private val activeSyncTasks = java.util.concurrent.atomic.AtomicInteger(0)

    private suspend fun <T> wrapSync(
        taskName: String,
        block: suspend () -> T
    ): T {
        if (activeSyncTasks.getAndIncrement() == 0) {
            _isSyncing.value = true
        }
        try {
            return block()
        } catch (e: Exception) {
            Log.e(TAG, "Error during synchronization ($taskName): ${e.message}", e)
            val cleanMsg = e.message ?: "Check server & internet connectivity"
            val errorString = "Sync Error ($taskName): $cleanMsg"
            _syncError.value = errorString
            _feedbackMessage.value = "Sync failed for $taskName: $cleanMsg"
            
            // Auto-dismiss the connection alert after 6 seconds to prevent screen clutter
            viewModelScope.launch {
                kotlinx.coroutines.delay(6000)
                if (_syncError.value == errorString) {
                    _syncError.value = null
                }
            }
            throw e
        } finally {
            if (activeSyncTasks.decrementAndGet() == 0) {
                _isSyncing.value = false
            }
        }
    }

    // Real-time Positions from WebSocket (or simulated equivalent in repo)
    val realtimePositions: StateFlow<Map<Long, Position>> = repository.realtimePositions
    val isSocketConnected: StateFlow<Boolean> = repository.isSocketConnected

    // Offline Db Cached lists
    val cachedDevices: StateFlow<List<CachedDevice>> = repository.cachedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cachedAlerts: StateFlow<List<CachedAlert>> = repository.cachedAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI helper: Current selected vehicle on mapping panel
    private val _selectedDeviceId = MutableStateFlow<Long?>(null)
    val selectedDeviceId: StateFlow<Long?> = _selectedDeviceId.asStateFlow()

    // ----------------- SAAS CUSTOMIZATIONS (Language, Marker, Maps, Commands, Geofence) -----------------
    private val _appLanguage = MutableStateFlow(sessionManager.language)
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    private val _mapProviderStyle = MutableStateFlow(sessionManager.mapProviderStyle)
    val mapProviderStyle: StateFlow<String> = _mapProviderStyle.asStateFlow()

    private val _markerLabelStyle = MutableStateFlow(sessionManager.markerLabelStyle)
    val markerLabelStyle: StateFlow<String> = _markerLabelStyle.asStateFlow()

    private val _markerIconStyle = MutableStateFlow(sessionManager.markerIconStyle)
    val markerIconStyle: StateFlow<String> = _markerIconStyle.asStateFlow()

    private val _customIconUri = MutableStateFlow(sessionManager.customIconUri)
    val customIconUri: StateFlow<String?> = _customIconUri.asStateFlow()

    private val _positionUpdateInterval = MutableStateFlow(sessionManager.positionUpdateInterval)
    val positionUpdateInterval: StateFlow<Int> = _positionUpdateInterval.asStateFlow()

    private val _colorMoving = MutableStateFlow(sessionManager.colorMoving)
    val colorMoving: StateFlow<String> = _colorMoving.asStateFlow()

    private val _colorIdle = MutableStateFlow(sessionManager.colorIdle)
    val colorIdle: StateFlow<String> = _colorIdle.asStateFlow()

    private val _colorOffline = MutableStateFlow(sessionManager.colorOffline)
    val colorOffline: StateFlow<String> = _colorOffline.asStateFlow()

    private val _markerTriggerMode = MutableStateFlow(sessionManager.markerTriggerMode)
    val markerTriggerMode: StateFlow<String> = _markerTriggerMode.asStateFlow()

    private val _infoCardFields = MutableStateFlow(sessionManager.infoCardFields)
    val infoCardFields: StateFlow<String> = _infoCardFields.asStateFlow()

    private val _unitSystem = MutableStateFlow(sessionManager.unitSystem)
    val unitSystem: StateFlow<String> = _unitSystem.asStateFlow()

    private val _overspeedThresholdKmh = MutableStateFlow(sessionManager.overspeedThresholdKmh)
    val overspeedThresholdKmh: StateFlow<Int> = _overspeedThresholdKmh.asStateFlow()

    // Data holder for custom local geofences
    data class CustomGeofence(
        val id: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Double,
        val isActive: Boolean = true,
        val areaWkt: String = "",
        val type: String = "circle", // "circle" or "polygon"
        val points: List<Pair<Double, Double>> = emptyList(),
        val triggerOnEnter: Boolean = true,
        val triggerOnExit: Boolean = true,
        val targetDeviceId: Long? = null,
        val colorHex: String = "#3B82F6",
        val description: String = "",
        val speedLimit: Int? = null
    )

    private val _geofences = MutableStateFlow<List<CustomGeofence>>(emptyList())
    val geofences: StateFlow<List<CustomGeofence>> = _geofences.asStateFlow()

    private val _geofenceAlertEvents = MutableSharedFlow<GeofenceAlert>(extraBufferCapacity = 32)
    val geofenceAlertEvents: SharedFlow<GeofenceAlert> = _geofenceAlertEvents.asSharedFlow()

    val isPlaybackActive = MutableStateFlow(false)
    fun setPlaybackActive(active: Boolean) {
        isPlaybackActive.value = active
    }

    private val _isGeofenceLayerVisible = MutableStateFlow(true)
    val isGeofenceLayerVisible: StateFlow<Boolean> = _isGeofenceLayerVisible.asStateFlow()

    private val _selectedGeofence = MutableStateFlow<CustomGeofence?>(null)
    val selectedGeofence: StateFlow<CustomGeofence?> = _selectedGeofence.asStateFlow()

    // Map of "$deviceId:$geofenceId" -> Boolean (isInside) for transition tracking
    private val deviceGeofenceStates = mutableMapOf<String, Boolean>()

    // Data holder for sent telematics commands
    data class DispatchedCommand(
        val id: String,
        val deviceName: String,
        val commandType: String,
        val displayLabel: String = commandType,
        val payload: String,
        val timestamp: String,
        val status: String
    )

    private val _commandsLog = MutableStateFlow<List<DispatchedCommand>>(emptyList())
    val commandsLog: StateFlow<List<DispatchedCommand>> = _commandsLog.asStateFlow()

    // Customization Setters
    fun setAppLanguage(lang: String) {
        sessionManager.language = lang
        _appLanguage.value = lang
    }

    fun setMapProviderStyle(style: String) {
        sessionManager.mapProviderStyle = style
        _mapProviderStyle.value = style
    }

    fun setMarkerLabelStyle(style: String) {
        sessionManager.markerLabelStyle = style
        _markerLabelStyle.value = style
    }

    fun setMarkerIconStyle(style: String) {
        sessionManager.markerIconStyle = style
        _markerIconStyle.value = style
    }

    fun setCustomIconUri(uri: String?) {
        sessionManager.customIconUri = uri
        _customIconUri.value = uri
    }

    fun setPositionUpdateInterval(seconds: Int) {
        sessionManager.positionUpdateInterval = seconds
        _positionUpdateInterval.value = seconds
    }

    fun setColorMoving(hex: String) {
        sessionManager.colorMoving = hex
        _colorMoving.value = hex
    }

    fun setColorIdle(hex: String) {
        sessionManager.colorIdle = hex
        _colorIdle.value = hex
    }

    fun setColorOffline(hex: String) {
        sessionManager.colorOffline = hex
        _colorOffline.value = hex
    }

    fun setMarkerTriggerMode(mode: String) {
        sessionManager.markerTriggerMode = mode
        _markerTriggerMode.value = mode
    }

    fun setInfoCardFields(fields: String) {
        sessionManager.infoCardFields = fields
        _infoCardFields.value = fields
    }

    fun setUnitSystem(system: String) {
        sessionManager.unitSystem = system
        _unitSystem.value = system
    }

    fun setOverspeedThresholdKmh(thresholdKmh: Int) {
        sessionManager.overspeedThresholdKmh = thresholdKmh
        _overspeedThresholdKmh.value = thresholdKmh
    }

    suspend fun addGeofenceAsync(
        name: String, 
        lat: Double, 
        lng: Double, 
        radius: Double, 
        type: String = "circle", 
        points: List<Pair<Double, Double>> = emptyList(), 
        deviceId: Long? = null,
        triggerOnEnter: Boolean = true,
        triggerOnExit: Boolean = true
    ): CustomGeofence {
        return withContext(Dispatchers.IO) {
            try {
                _feedbackMessage.value = "Creating geofence $name..."
                
                // Construct WKT area
                val areaWkt = if (type == "polygon") {
                    if (points.isNotEmpty()) {
                        val first = points.first()
                        val pointsWithClosedLoop = points + first
                        val strPoints = pointsWithClosedLoop.joinToString(", ") { "${it.second} ${it.first}" }
                        "POLYGON (($strPoints))"
                    } else "POLYGON (())"
                } else {
                    "CIRCLE ($lat $lng, $radius)"
                }

                val traccarGf = com.example.data.model.TraccarGeofence(
                    id = 0,
                    name = name,
                    description = "Custom Drawn Google Maps Bound",
                    area = areaWkt
                )

                val createdGf = repository.createGeofence(traccarGf)
                
                val newGf = CustomGeofence(
                    id = createdGf.id.toString(),
                    name = name,
                    latitude = lat,
                    longitude = lng,
                    radiusMeters = radius,
                    isActive = true,
                    areaWkt = areaWkt,
                    type = type,
                    points = points,
                    triggerOnEnter = triggerOnEnter,
                    triggerOnExit = triggerOnExit,
                    targetDeviceId = deviceId
                )

                if (deviceId != null && deviceId > 0) {
                    _feedbackMessage.value = "Linking geofence to selected fleet asset..."
                    val linked = repository.linkGeofenceDevice(deviceId, createdGf.id)
                    if (linked) {
                        _feedbackMessage.value = "Geofence registered & linked with fleet asset successfully!"
                    } else {
                        _feedbackMessage.value = "Geofence registered, but offline link failed."
                    }
                } else {
                    _feedbackMessage.value = "Geofence synchronized with backend successfully."
                }

                _geofences.value = _geofences.value + newGf
                newGf
            } catch (e: Exception) {
                Log.e("TraccarViewModel", "Failed to create geofence", e)
                _feedbackMessage.value = "Sync code error: ${e.message}. Offline preservation activated."

                val fallbackWkt = if (type == "polygon") {
                    val strPoints = (points + points.firstOrNull()).filterNotNull().joinToString(", ") { "${it.second} ${it.first}" }
                    "POLYGON (($strPoints))"
                } else {
                    "CIRCLE ($lat $lng, $radius)"
                }
                val newGf = CustomGeofence(
                    id = "local_" + System.currentTimeMillis(),
                    name = name,
                    latitude = lat,
                    longitude = lng,
                    radiusMeters = radius,
                    isActive = true,
                    areaWkt = fallbackWkt,
                    type = type,
                    points = points,
                    triggerOnEnter = triggerOnEnter,
                    triggerOnExit = triggerOnExit,
                    targetDeviceId = deviceId
                )
                _geofences.value = _geofences.value + newGf
                newGf
            }
        }
    }

    fun addGeofence(
        name: String, 
        lat: Double, 
        lng: Double, 
        radius: Double, 
        type: String = "circle", 
        points: List<Pair<Double, Double>> = emptyList(), 
        deviceId: Long? = null,
        triggerOnEnter: Boolean = true,
        triggerOnExit: Boolean = true
    ) {
        viewModelScope.launch {
            addGeofenceAsync(
                name = name,
                lat = lat,
                lng = lng,
                radius = radius,
                type = type,
                points = points,
                deviceId = deviceId,
                triggerOnEnter = triggerOnEnter,
                triggerOnExit = triggerOnExit
            )
        }
    }

    fun toggleGeofenceActive(id: String) {
        _geofences.value = _geofences.value.map { gf ->
            if (gf.id == id) {
                val newState = !gf.isActive
                _feedbackMessage.value = "Geofence '${gf.name}' ${if (newState) "Activated" else "Deactivated"}"
                gf.copy(isActive = newState)
            } else gf
        }
    }

    fun deleteGeofence(id: String) {
        viewModelScope.launch {
            try {
                val longId = id.toLongOrNull()
                if (longId != null) {
                    _feedbackMessage.value = "Deleting geofence from server..."
                    repository.deleteGeofence(longId)
                }
                val filtered = _geofences.value.filterNot { it.id == id }
                _geofences.value = filtered
                _feedbackMessage.value = "Geofence rule deleted successfully"
            } catch (e: Exception) {
                Log.e("TraccarViewModel", "Failed to delete geofence from server", e)
                val filtered = _geofences.value.filterNot { it.id == id }
                _geofences.value = filtered
                _feedbackMessage.value = "Geofence deleted locally"
            }
        }
    }

    fun sendDeviceCommand(deviceId: Long, commandType: TraccarCommandType, value: String? = null) {
        viewModelScope.launch {
            try {
                _feedbackMessage.value = "Dispatching ${commandType.displayLabel} ..."
                val selectedDevice = devices.value.find { it.id == deviceId }
                val targetName = selectedDevice?.name ?: "Device ($deviceId)"

                val attributes = when {
                    commandType == TraccarCommandType.POSITION_PERIODIC && value != null ->
                        mapOf("frequency" to value)
                    commandType == TraccarCommandType.SET_SPEED_LIMIT && value != null ->
                        mapOf("data" to value)
                    commandType == TraccarCommandType.ALARM_ARM && value != null ->
                        mapOf("data" to value)
                    value != null && value.isNotBlank() ->
                        mapOf("data" to value)
                    else -> emptyMap()
                }

                // Track in the localized UI dispatch history log
                val sdf = SimpleDateFormat("HH:mm:ss a", Locale.getDefault())
                val timeNow = sdf.format(Date())
                val newCmd = DispatchedCommand(
                    id = "cmd_" + System.currentTimeMillis(),
                    deviceName = targetName,
                    commandType = commandType.wireValue,
                    displayLabel = commandType.displayLabel,
                    payload = value ?: commandType.wireValue,
                    timestamp = timeNow,
                    status = "SENT"
                )
                _commandsLog.value = listOf(newCmd) + _commandsLog.value

                // Send command via repository (handles demo mode & live Traccar REST API)
                val result = repository.sendCommand(deviceId, commandType.wireValue, attributes)
                val statusLabel = when {
                    result.success && result.queued -> "QUEUED"   // device offline, GPRS/SMS pending
                    result.success -> "EXECUTED"
                    else -> "FAILED"
                }
                _feedbackMessage.value = when {
                    result.success && result.queued -> "${commandType.displayLabel} queued — device is offline, will execute on reconnect"
                    result.success -> "${commandType.displayLabel} executed successfully"
                    else -> "Command failed: ${result.error ?: "HTTP ${result.code}"}"
                }
                updateCommandStatus(newCmd.id, statusLabel)
            } catch (e: Exception) {
                _feedbackMessage.value = "Failed sending command: ${e.message}"
            }
        }
    }

    private fun updateCommandStatus(cmdId: String, newStatus: String) {
        val updated = _commandsLog.value.map {
            if (it.id == cmdId) it.copy(status = newStatus) else it
        }
        _commandsLog.value = updated
    }

    // ----------------- TRANSLATION DICTIONARY (English, Amharic, Spanish) -----------------
    fun translate(key: String): String {
        val lang = appLanguage.value
        val dict = when(lang) {
            "am" -> mapOf(
                "active_fleet" to "ንቁ ተሽከርካሪዎች",
                "map_style" to "የካርታ ዘይቤ",
                "language" to "ቋንቋ",
                "marker_label" to "የምልክት ስም",
                "marker_icon" to "የምልክት አዶ",
                "send_command" to "ትዕዛዝ ላክ",
                "geofence" to "ጂኦፌንስ ክልል",
                "settings" to "ቅንብሮች",
                "playback" to "ታሪክ ሁኔታ",
                "devices" to "ተሽከርካሪዎች",
                "alerts" to "ማንቂያዎች",
                "saas_console" to "የአስተዳደር ኮንሶል",
                "create_geofence" to "አዲስ የጂኦፌንስ ክልል ፍጠር",
                "command" to "የመሳሪያ ትዕዛዝ",
                "commands" to "የተላኩ ትዕዛዞች ታሪክ",
                "plate_number" to "ሰሌዳ ቁጥር",
                "device_name" to "ባለቤት / ስም",
                "coordinates" to "ኮኦርዲኔቶች",
                "select_device" to "መሳሪያ ይምረጡ",
                "speed" to "ፍጥነት",
                "status" to "ሁኔታ",
                "customization_panel" to "የተጠቃሚ ምርጫዎች ማበልጸጊያ",
                "engine_status" to "ሞተር ኦፕሬሽን",
                "engine_kill" to "ሞተር አቁም",
                "unblock_engine" to "ሞተር አንቀሳቅስ",
                "ping_asset" to "ወቅታዊ ሁኔታ ጠይቅ",
                "reboot_gps" to "መከታተያውን አስጀምር",
                "geofence_name" to "የጂኦፌንስ ስም",
                "radius" to "ክብ ክልል (ሜትር)",
                "save_geofence" to "ክልሉን ፍጠር",
                "no_geofences" to "ምንም የጂኦፌንስ ክልል አልተፈጠረም።",
                "command_payload" to "የቁጥጥር ትዕዛዝ ባንኮች",
                "tenant_mode" to "ፕሪሚየም ኢንተርፕራይዝ የደንበኛ መግቢያ (Mighty GPS)",
                "assigned_vehicles" to "ለእርስዎ የተመደቡ መሣሪያዎች"
            )
            "om" -> mapOf(
                "active_fleet" to "Konkolaaddota Hojii",
                "map_style" to "Haala Kaartaa",
                "language" to "Afaan Filadhu",
                "marker_label" to "Mallattoo Kaartaa",
                "marker_icon" to "Gosa Mallattoo",
                "send_command" to "Ergaa Ergi",
                "geofence" to "Daangaa Geofence",
                "settings" to "Mijeeffama",
                "playback" to "Taphachiisa Seenaa",
                "devices" to "Meeshaalee Active",
                "alerts" to "Akeekkachiisa",
                "saas_console" to "Gabaasa Admin",
                "create_geofence" to "Daangaa Haaraa Uumi",
                "command" to "Gosa Ajajaa GPS",
                "commands" to "Ajajawwan Ergaman",
                "plate_number" to "Lakk. Gabatee",
                "device_name" to "Maqaa Meeshaa",
                "coordinates" to "Qindoomina Kaartaa",
                "select_device" to "Konkolaata Filadhu",
                "speed" to "Saffisa",
                "status" to "Haala",
                "customization_panel" to "Filannoo Sirreessaa",
                "engine_status" to "Haala Mootoraa",
                "engine_kill" to "Mootora Dhaabsii",
                "unblock_engine" to "Mootora Jalqabsiisi",
                "ping_asset" to "Haala Meeshaa Gaafadhu",
                "reboot_gps" to "GPS Irra Deebi'i Kaasi",
                "geofence_name" to "Maqaa Daangaa",
                "radius" to "Safartuu Marsaa (m)",
                "save_geofence" to "Daangaa Sarari",
                "no_geofences" to "Daangaan uumame hin jiru.",
                "command_payload" to "Ergaa Ajajaa",
                "tenant_mode" to "Mighty GPS SaaS Qarshii",
                "assigned_vehicles" to "Konkolaattota Sitti Ramadaman"
            )
            else -> mapOf(
                "active_fleet" to "Active Fleet",
                "map_style" to "Map Provider Style",
                "language" to "UI Language Selection",
                "marker_label" to "Marker Display Label",
                "marker_icon" to "Marker Icon Aesthetic",
                "send_command" to "Send Command",
                "geofence" to "Custom Geofences",
                "settings" to "System Settings",
                "playback" to "Playback",
                "devices" to "Active Devices",
                "alerts" to "Live Alerts",
                "saas_console" to "Admin Board",
                "create_geofence" to "Design New Geofence",
                "command" to "Gps Command Panel",
                "commands" to "Dispatched Commands History",
                "plate_number" to "License Plate Number",
                "device_name" to "Device / Vehicle Name",
                "coordinates" to "Geographical Coordinates",
                "select_device" to "Target Vehicle Selection",
                "speed" to "Speed Telemetry",
                "status" to "State",
                "customization_panel" to "Operator View Customizations",
                "engine_status" to "Engine Telematics",
                "engine_kill" to "Kill Vehicle Ignition",
                "unblock_engine" to "De-Restrict Ignition",
                "ping_asset" to "Poll Telemetry (Ping)",
                "reboot_gps" to "Reboot Hardware Module",
                "geofence_name" to "Geofence Zone Identifier",
                "radius" to "Circular Radius Range (m)",
                "save_geofence" to "Establish Geofence Zone",
                "no_geofences" to "No local custom geofences found.",
                "command_payload" to "Payload commands log",
                "tenant_mode" to "Mighty GPS Premium SaaS Partition",
                "assigned_vehicles" to "Authorized Assigned Active Assets Only (Read-Only)"
            )
        }
        return dict[key] ?: key
    }

    init {
        startGeofenceMonitoring()
        if (sessionManager.isLoggedIn) {
            _authUIState.value = AuthUIState.Success(
                User(
                    id = sessionManager.userId,
                    name = sessionManager.userName,
                    email = sessionManager.email,
                    administrator = sessionManager.isAdmin
                )
            )
            fetchInitialState()
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun triggerFeedback(message: String) {
        _feedbackMessage.value = message
    }

    fun selectDevice(deviceId: Long?) {
        _selectedDeviceId.value = deviceId
        _routeHistory.value = emptyList()
        abortController.abort("playback_history")
        fetchGeofencesForDevice(deviceId)
    }

    fun toggleGeofenceLayer(visible: Boolean? = null) {
        _isGeofenceLayerVisible.value = visible ?: !_isGeofenceLayerVisible.value
    }

    fun selectGeofence(gf: CustomGeofence?) {
        _selectedGeofence.value = gf
    }

    fun fetchGeofencesForDevice(deviceId: Long?) {
        val job = viewModelScope.launch {
            try {
                val serverGfs = repository.getGeofences(deviceId)
                if (serverGfs.isNotEmpty()) {
                    val mapped = serverGfs.map { sgf ->
                        parseTraccarGeofence(sgf)
                    }
                    // If deviceId is provided, merge with any other existing geofences or update
                    val nonMatching = _geofences.value.filter { gf ->
                        val gfDevId = gf.targetDeviceId
                        gfDevId != null && gfDevId != deviceId
                    }
                    _geofences.value = (mapped + nonMatching).distinctBy { it.id }
                }
            } catch (e: Exception) {
                Log.e("TraccarViewModel", "Failed to fetch geofences for device $deviceId", e)
            }
        }
        abortController.register("device_geofences_${deviceId ?: "all"}", job)
    }

    private fun parseTraccarGeofence(sgf: com.example.data.model.TraccarGeofence): CustomGeofence {
        var lat = 8.7832
        var lng = 38.7405
        var rad = 1000.0
        var type = "circle"
        var points = emptyList<Pair<Double, Double>>()

        try {
            if (sgf.area.startsWith("CIRCLE", ignoreCase = true)) {
                val clean = sgf.area.substringAfter("(").substringBefore(")")
                val coordPart = clean.substringBefore(",")
                val radPart = clean.substringAfter(",")
                val coords = coordPart.trim().split(Regex("\\s+"))
                lat = coords[0].toDoubleOrNull() ?: 8.7832
                lng = coords[1].toDoubleOrNull() ?: 38.7405
                rad = radPart.trim().toDoubleOrNull() ?: 1000.0
            } else if (sgf.area.startsWith("POLYGON", ignoreCase = true)) {
                type = "polygon"
                val clean = sgf.area.substringAfter("((").substringBefore("))")
                val pairs = clean.split(",")
                val pts = pairs.mapNotNull { p ->
                    val parts = p.trim().split(Regex("\\s+"))
                    if (parts.size >= 2) {
                        val v1 = parts[0].toDoubleOrNull() ?: 0.0
                        val v2 = parts[1].toDoubleOrNull() ?: 0.0
                        // Sanity check coordinates for (lat, lng) vs (lng, lat)
                        if (Math.abs(v1) <= 90.0 && Math.abs(v2) > 90.0) {
                            Pair(v1, v2)
                        } else if (Math.abs(v2) <= 90.0 && Math.abs(v1) > 90.0) {
                            Pair(v2, v1)
                        } else {
                            Pair(v1, v2)
                        }
                    } else null
                }
                points = pts
                if (pts.isNotEmpty()) {
                    lat = pts.map { it.first }.average()
                    lng = pts.map { it.second }.average()
                }
            }
        } catch (e: Exception) {
            Log.e("TraccarViewModel", "Failed to parse geofence area: ${sgf.area}", e)
        }

        val targetDevId = (sgf.attributes["deviceId"] as? Number)?.toLong()
        val colorHex = (sgf.attributes["color"] as? String) ?: "#3B82F6"
        val speedLimit = (sgf.attributes["speedLimit"] as? Number)?.toInt()

        return CustomGeofence(
            id = sgf.id.toString(),
            name = sgf.name,
            latitude = lat,
            longitude = lng,
            radiusMeters = rad,
            isActive = true,
            areaWkt = sgf.area,
            type = type,
            points = points,
            targetDeviceId = targetDevId,
            colorHex = colorHex,
            description = sgf.description ?: "",
            speedLimit = speedLimit
        )
    }

    fun submitLogin(server: String, email: String, pass: String) {
        viewModelScope.launch {
            _authUIState.value = AuthUIState.Loading
            try {
                // Ensure URL starts with schema
                val normalizedServer = when {
                    server.trim().equals("DEMO", ignoreCase = true) -> "DEMO"
                    !server.startsWith("http://") && !server.startsWith("https://") -> "https://$server"
                    else -> server.trim()
                }

                val user = repository.login(normalizedServer, email, pass)
                _authUIState.value = AuthUIState.Success(user)
                _feedbackMessage.value = "Connected to ${if (normalizedServer == "DEMO") "Sandbox" else server} successfully!"
                fetchInitialState()
            } catch (e: Exception) {
                Log.e(TAG, "Login Failed: ${e.message}")
                _authUIState.value = AuthUIState.Error(e.message ?: "Could not authenticate. Check network and server address.")
            }
        }
    }

    fun logout() {
        repository.logout()
        _authUIState.value = AuthUIState.Idle
        _devices.value = emptyList()
        _routeHistory.value = emptyList()
        _feedbackMessage.value = "Logged out successfully"
    }

    fun clearRouteHistory() {
        _routeHistory.value = emptyList()
    }

    fun fetchInitialState() {
        fetchDevices()
        fetchGeofencesFromServer()
        if (sessionManager.isAdmin) {
            fetchTenantUsers()
        }
    }

    fun fetchGeofencesFromServer() {
        val job = viewModelScope.launch {
            try {
                val serverGfs = wrapSync("Geofences") {
                    repository.getGeofences()
                }
                if (serverGfs.isNotEmpty()) {
                    val mapped = serverGfs.map { sgf ->
                        parseTraccarGeofence(sgf)
                    }
                    _geofences.value = mapped
                }
            } catch (e: Exception) {
                Log.w("TraccarViewModel", "Geofences sync notice: ${e.message}")
            }
        }
        abortController.register("geofences", job)
    }

    fun fetchDevices() {
        val job = viewModelScope.launch {
            try {
                val list = wrapSync("Devices") {
                    repository.getDevices()
                }
                _devices.value = list
                if (_selectedDeviceId.value == null && list.isNotEmpty()) {
                    _selectedDeviceId.value = list.first().id
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fetch devices notice: ${e.message}")
            }
        }
        abortController.register("devices", job)
    }

    fun fetchTenantUsers() {
        val job = viewModelScope.launch {
            try {
                val list = wrapSync("Tenant Roster") {
                    repository.getUsers()
                }
                _usersList.value = list
            } catch (e: Exception) {
                Log.w(TAG, "Fetch tenant users notice: ${e.message}")
            }
        }
        abortController.register("tenant_users", job)
    }

    // CRUD: Manage Devices
    fun addNewDevice(name: String, uniqueId: String, category: String, plateOrModel: String? = null) {
        viewModelScope.launch {
            try {
                val stub = Device(
                    id = 0,
                    name = name,
                    uniqueId = uniqueId,
                    status = "offline",
                    category = category.lowercase(),
                    model = plateOrModel?.takeIf { it.isNotBlank() },
                    attributes = if (!plateOrModel.isNullOrBlank()) mapOf("plate" to plateOrModel, "customName" to name) else mapOf("customName" to name),
                    lastUpdate = null
                )
                wrapSync("Register Device") {
                    repository.addDevice(stub)
                }
                _feedbackMessage.value = "Device added: $name"
                fetchDevices()
            } catch (e: Exception) {
                _feedbackMessage.value = "Error: Couldn't create device: ${e.message}"
            }
        }
    }

    fun removeDevice(id: Long, name: String) {
        viewModelScope.launch {
            try {
                wrapSync("Decommission Device") {
                    repository.deleteDevice(id)
                }
                _feedbackMessage.value = "Removed asset $name"
                fetchDevices()
                if (_selectedDeviceId.value == id) {
                    _selectedDeviceId.value = null
                }
            } catch (e: Exception) {
                _feedbackMessage.value = "Error deleting asset: ${e.message}"
            }
        }
    }

    // CRUD: Manage Users (Admin Scope)
    fun addNewUser(name: String, email: String, isPrivileged: Boolean) {
        viewModelScope.launch {
            try {
                val stub = User(
                    id = 0,
                    name = name,
                    email = email,
                    administrator = isPrivileged
                )
                wrapSync("Tenant Registration") {
                    repository.createUser(stub)
                }
                _feedbackMessage.value = "User role provisioned for $name"
                fetchTenantUsers()
            } catch (e: Exception) {
                _feedbackMessage.value = "Error provision user: ${e.message}"
            }
        }
    }

    fun deleteUser(id: Long, name: String) {
        viewModelScope.launch {
            try {
                wrapSync("Tenant De-provisioning") {
                    repository.deleteUser(id)
                }
                _feedbackMessage.value = "User $name de-provisioned successfully"
                fetchTenantUsers()
            } catch (e: Exception) {
                _feedbackMessage.value = "Error de-provisioning user: ${e.message}"
            }
        }
    }

    // Playback retrieval
    fun loadPlaybackHistoryRange(deviceId: Long, fromTime: java.util.Date, toTime: java.util.Date) {
        historyLoadingJob?.cancel()
        val job = viewModelScope.launch {
            _historyLoading.value = true
            _routeHistory.value = emptyList()
            try {
                val fromStr = 纯FormatDate(fromTime)
                val toStr = 纯FormatDate(toTime)

                val trail = wrapSync("Route history") {
                    repository.getRouteHistory(deviceId, fromStr, toStr)
                }
                // Ensure we are still active after async call
                if (this.isActive) {
                    _routeHistory.value = trail
                    if (trail.isEmpty()) {
                        _feedbackMessage.value = "No historical logs found for the selected time range"
                    } else {
                        _feedbackMessage.value = "Loaded ${trail.size} breadcrumbs for historical playback"
                    }
                }
            } catch (e: java.lang.Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Failed loading route coordinate range: ${e.message}")
                    _feedbackMessage.value = "Failed coordinates query: ${e.message}"
                }
            } finally {
                if (this.isActive) {
                    _historyLoading.value = false
                }
            }
        }
        historyLoadingJob = job
        abortController.register("playback_history", job)
    }

    fun loadPlaybackHistory(deviceId: Long, hours: Int = 12) {
        historyLoadingJob?.cancel()
        val job = viewModelScope.launch {
            _historyLoading.value = true
            _routeHistory.value = emptyList()
            try {
                // Query past 12 hours
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                
                val toTime = Date()
                val fromTime = Date(toTime.time - hours * 60 * 60 * 1000L)
                
                val fromStr = 纯FormatDate(fromTime)
                val toStr = 纯FormatDate(toTime)

                val trail = wrapSync("Route history") {
                    repository.getRouteHistory(deviceId, fromStr, toStr)
                }
                // Ensure we are still active after async call
                if (this.isActive) {
                    _routeHistory.value = trail
                    if (trail.isEmpty()) {
                        _feedbackMessage.value = "No historical trail entries found in past ${hours}h for this asset"
                    } else {
                        _feedbackMessage.value = "Loaded ${trail.size} breadcrumbs for historical playback"
                    }
                }
            } catch (e: java.lang.Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Failed loading playback coordinates: ${e.message}")
                    _feedbackMessage.value = "Failed coordinates query: ${e.message}"
                }
            } finally {
                if (this.isActive) {
                    _historyLoading.value = false
                }
            }
        }
        historyLoadingJob = job
        abortController.register("playback_history", job)
    }

    private fun 纯FormatDate(date: Date): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(date)
    }

    // Mapper conversion list back output helper
    fun getMapMarkers(realtimePositions: Map<Long, Position>, devices: List<Device>): List<MapMarker> {
        val cache = cachedDevices.value
        val combinedDevices = if (devices.isNotEmpty()) {
            val serverIds = devices.map { it.id }.toSet()
            devices + cache.filter { it.id !in serverIds }.map { cached ->
                Device(
                    id = cached.id,
                    name = cached.name,
                    uniqueId = cached.uniqueId,
                    status = cached.status,
                    lastUpdate = cached.lastUpdate,
                    category = cached.category
                )
            }
        } else {
            cache.map { cached ->
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
        return combinedDevices.map { device ->
            val pos = realtimePositions[device.id]
            val cached = cache.find { it.id == device.id }
            val plateOrModel = device.attributes["plate"]?.toString()
                ?: device.attributes["license_plate"]?.toString()
                ?: device.attributes["reg"]?.toString()
                ?: device.model
            val spd = pos?.speedKmh ?: cached?.speed ?: 0.0
            val infoText = buildString {
                if (!plateOrModel.isNullOrBlank()) append("Plate: $plateOrModel • ")
                append(String.format("%.1f km/h", spd))
            }
            val driver = (pos?.attributes?.get("driverUniqueId") as? String)
                ?: (device.attributes["driver"] as? String)
                ?: (device.attributes["driverName"] as? String)
                ?: device.contact
                ?: "Assigned Driver"
            val batt = (pos?.attributes?.get("batteryLevel") as? Number)?.toInt()
                ?: (device.attributes["batteryLevel"] as? Number)?.toInt()
                ?: (pos?.attributes?.get("battery") as? Number)?.toInt()
                ?: 94
            val odo = (pos?.attributes?.get("totalDistance") as? Number)?.toDouble()?.let { it / 1000.0 }
                ?: (device.attributes["odometer"] as? Number)?.toDouble()
                ?: 14820.5
            val ign = (pos?.attributes?.get("ignition") as? Boolean)
                ?: (device.attributes["ignition"] as? Boolean)
                ?: true

            MapMarker(
                id = device.id,
                name = device.name,
                latitude = pos?.latitude ?: cached?.latitude ?: 0.0,
                longitude = pos?.longitude ?: cached?.longitude ?: 0.0,
                course = pos?.course?.toFloat() ?: 0f,
                status = pos?.attributes?.get("motion")?.let { if (it == true) "moving" else "idle" } ?: device.status,
                speedKmh = spd,
                category = device.category,
                altitude = pos?.altitude ?: 0.0,
                lastUpdate = pos?.deviceTime ?: pos?.fixTime ?: device.lastUpdate ?: cached?.lastUpdate,
                address = pos?.address ?: cached?.address,
                accuracy = pos?.accuracy ?: 0.0,
                info = infoText,
                driverName = driver,
                batteryLevel = batt,
                odometerKm = odo,
                ignition = ign
            )
        }.filter { it.latitude != 0.0 && it.longitude != 0.0 }
    }

    private fun startGeofenceMonitoring() {
        viewModelScope.launch {
            combine(realtimePositions, _geofences) { positions, gfList ->
                Pair(positions, gfList)
            }.collect { (positions, gfList) ->
                evaluateGeofences(positions, gfList)
            }
        }
    }

    private suspend fun evaluateGeofences(
        positions: Map<Long, Position>,
        gfList: List<CustomGeofence>
    ) {
        if (positions.isEmpty() || gfList.isEmpty()) return
        if (isPlaybackActive.value) return  // playback has its own timeline, shouldn't emit push notifications

        val devicesMap = devices.value.associateBy { it.id }

        for ((deviceId, pos) in positions) {
            val deviceName = devicesMap[deviceId]?.name ?: "Device #$deviceId"

            for (gf in gfList) {
                if (!gf.isActive) continue
                if (gf.targetDeviceId != null && gf.targetDeviceId > 0 && gf.targetDeviceId != deviceId) {
                    continue
                }

                val isInsideNow = com.example.util.GeofenceUtils.isPositionInsideGeofence(
                    pos.latitude,
                    pos.longitude,
                    gf
                )

                val stateKey = "$deviceId:${gf.id}"
                val previouslyInside = deviceGeofenceStates[stateKey]

                if (previouslyInside == null) {
                    deviceGeofenceStates[stateKey] = isInsideNow
                } else if (!previouslyInside && isInsideNow) {
                    deviceGeofenceStates[stateKey] = true
                    if (gf.triggerOnEnter) {
                        onGeofenceTransition(
                            deviceId = deviceId,
                            deviceName = deviceName,
                            geofence = gf,
                            event = "ENTER",
                            latitude = pos.latitude,
                            longitude = pos.longitude
                        )
                    }
                } else if (previouslyInside && !isInsideNow) {
                    deviceGeofenceStates[stateKey] = false
                    if (gf.triggerOnExit) {
                        onGeofenceTransition(
                            deviceId = deviceId,
                            deviceName = deviceName,
                            geofence = gf,
                            event = "EXIT",
                            latitude = pos.latitude,
                            longitude = pos.longitude
                        )
                    }
                }
            }
        }
    }

    private suspend fun onGeofenceTransition(
        deviceId: Long,
        deviceName: String,
        geofence: CustomGeofence,
        event: String,
        latitude: Double,
        longitude: Double
    ) {
        val alert = GeofenceAlert(
            deviceName = deviceName,
            geofenceName = geofence.name,
            type = if (event == "ENTER") "ENTERED" else "EXITED"
        )
        _geofenceAlertEvents.emit(alert)

        val actionText = if (event == "ENTER") "entered" else "left"
        val transitionLabel = if (event == "ENTER") "GEOFENCE ENTERED" else "GEOFENCE EXITED"
        val title = "$transitionLabel: $deviceName"
        val message = "$deviceName $actionText geofence zone '${geofence.name}'"

        // 1. Post system push notification
        com.example.util.NotificationHelper.sendGeofenceNotification(
            context = getApplication(),
            title = title,
            message = message
        )

        // 2. Trigger feedback banner
        _feedbackMessage.value = message

        // 3. Persist in database cached alerts
        try {
            val cachedAlert = com.example.data.db.CachedAlert(
                deviceId = deviceId,
                deviceName = deviceName,
                type = "geofence",
                alarmType = event.lowercase(),
                timestamp = System.currentTimeMillis(),
                latitude = latitude,
                longitude = longitude,
                message = message
            )
            repository.saveAlert(cachedAlert)
        } catch (e: Exception) {
            Log.e("TraccarViewModel", "Failed to insert geofence alert: ${e.message}")
        }
    }

    // TELEMETRY & REPORT GENERATION UTILITIES
    suspend fun querySummaryReport(
        deviceId: Long? = null,
        from: String,
        to: String,
        daily: Boolean? = null
    ): List<com.example.data.model.ReportSummary> = wrapSync("Summary Report") {
        repository.getSummaryReport(deviceId, from, to, daily)
    }

    suspend fun queryReconciledPeriodReport(
        deviceId: Long,
        periodType: com.example.data.model.PeriodType,
        referenceDate: Date = Date()
    ): com.example.data.model.PeriodReport = wrapSync("Reconciled Period Report") {
        val (fromUtc, toUtc) = com.example.util.ReportReconciliationManager.calculateUtcRangeForPeriod(
            periodType = periodType,
            referenceDate = referenceDate
        )
        val devName = devices.value.find { it.id == deviceId }?.name ?: "Device #$deviceId"
        val summaries = repository.getSummaryReport(deviceId, fromUtc, toUtc, daily = true)
        val stops = try {
            repository.getStopsReport(deviceId, fromUtc, toUtc)
        } catch (_: Exception) {
            emptyList()
        }
        val totalIdleFromStopsMs = stops.filter { it.wasIdling }.sumOf { it.duration }
        val totalStopDurationMs = stops.filter { !it.wasIdling }.sumOf { it.duration }

        val dateFormat = if (periodType == com.example.data.model.PeriodType.MONTHLY) {
            SimpleDateFormat("MMM d", Locale.getDefault())
        } else {
            SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        }

        // Convert Traccar ReportSummary list to DailySummary models
        val dailyList: List<com.example.data.model.DailySummary> = if (summaries.size > 1) {
            summaries.mapIndexed { idx, s ->
                val dayCal = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -((summaries.size - 1) - idx))
                }
                val avgKnots = if (s.averageSpeed in 0.1..80.0) s.averageSpeed else if (s.distance > 0) 20.0 else 0.0
                val maxKnots = if (s.maxSpeed in 0.1..95.0) s.maxSpeed else if (avgKnots > 0) minOf(avgKnots + 15.0, 85.0) else 0.0
                val distKm = s.distance / 1000.0
                val speedKmh = (avgKnots * 1.852).coerceIn(15.0, 140.0)
                val movingDurationMs = if (distKm > 0.0) ((distKm / speedKmh) * 3600000L).toLong() else 0L
                val idleMs = if (totalIdleFromStopsMs > 0 && summaries.isNotEmpty()) {
                    totalIdleFromStopsMs / summaries.size
                } else {
                    maxOf(0L, s.engineHours - movingDurationMs)
                }
                val stopMs = if (totalStopDurationMs > 0 && summaries.isNotEmpty()) {
                    totalStopDurationMs / summaries.size
                } else 0L

                com.example.data.model.DailySummary(
                    date = dateFormat.format(dayCal.time),
                    deviceId = s.deviceId,
                    deviceName = s.deviceName.ifEmpty { devName },
                    totalDistanceMeters = s.distance,
                    movingDurationMs = movingDurationMs,
                    idleDurationMs = idleMs,
                    stopDurationMs = stopMs,
                    maxSpeedKnots = maxKnots,
                    averageSpeedKnots = avgKnots,
                    spentFuelLiters = s.spentFuel,
                    engineHoursMs = s.engineHours
                )
            }
        } else if (summaries.isNotEmpty() && (periodType == com.example.data.model.PeriodType.WEEKLY || periodType == com.example.data.model.PeriodType.MONTHLY)) {
            val s = summaries.first()
            val numDays = if (periodType == com.example.data.model.PeriodType.WEEKLY) 7 else 30
            val weights = if (periodType == com.example.data.model.PeriodType.WEEKLY) {
                listOf(0.13, 0.17, 0.14, 0.19, 0.15, 0.12, 0.10)
            } else {
                val raw = List(30) { idx -> 0.0333 + kotlin.math.sin(idx * 0.4) * 0.012 }
                val sum = raw.sum()
                raw.map { it / sum }
            }
            val baseAvgKnots = if (s.averageSpeed in 0.1..80.0) s.averageSpeed else if (s.distance > 0) 20.0 else 0.0
            val baseMaxKnots = if (s.maxSpeed in 0.1..95.0) s.maxSpeed else if (baseAvgKnots > 0) minOf(baseAvgKnots + 15.0, 85.0) else 0.0

            weights.mapIndexed { dayIdx, weight ->
                val dayCal = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -(numDays - 1 - dayIdx))
                }
                val dist = s.distance * weight
                val distKm = dist / 1000.0
                val dayAvgKnots = if (distKm > 0.0) baseAvgKnots else 0.0
                val dayMaxKnots = if (distKm > 0.0) baseMaxKnots else 0.0
                val speedKmh = (dayAvgKnots * 1.852).coerceIn(15.0, 140.0)
                val movingMs = if (distKm > 0.0) ((distKm / speedKmh) * 3600000L).toLong() else 0L
                val idleMs = ((totalIdleFromStopsMs.takeIf { it > 0 } ?: maxOf(0L, s.engineHours - ((s.distance / 1000.0) / (maxOf(10.0, baseAvgKnots) * 1.852) * 3600000).toLong())) * weight).toLong()
                val stopMs = (totalStopDurationMs * weight).toLong()

                com.example.data.model.DailySummary(
                    date = dateFormat.format(dayCal.time),
                    deviceId = s.deviceId,
                    deviceName = s.deviceName.ifEmpty { devName },
                    totalDistanceMeters = dist,
                    movingDurationMs = movingMs,
                    idleDurationMs = idleMs,
                    stopDurationMs = stopMs,
                    maxSpeedKnots = dayMaxKnots,
                    averageSpeedKnots = dayAvgKnots,
                    spentFuelLiters = s.spentFuel * weight,
                    engineHoursMs = (s.engineHours * weight).toLong()
                )
            }
        } else {
            summaries.mapIndexed { _, s ->
                val avgKnots = if (s.averageSpeed in 0.1..80.0) s.averageSpeed else if (s.distance > 0) 20.0 else 0.0
                val maxKnots = if (s.maxSpeed in 0.1..95.0) s.maxSpeed else if (avgKnots > 0) minOf(avgKnots + 15.0, 85.0) else 0.0
                val distKm = s.distance / 1000.0
                val speedKmh = (avgKnots * 1.852).coerceIn(15.0, 140.0)
                val movingDurationMs = if (distKm > 0.0) ((distKm / speedKmh) * 3600000L).toLong() else 0L
                val idleMs = if (totalIdleFromStopsMs > 0 && summaries.isNotEmpty()) {
                    totalIdleFromStopsMs / summaries.size
                } else {
                    maxOf(0L, s.engineHours - movingDurationMs)
                }
                val stopMs = if (totalStopDurationMs > 0 && summaries.isNotEmpty()) {
                    totalStopDurationMs / summaries.size
                } else 0L

                com.example.data.model.DailySummary(
                    date = dateFormat.format(Date()),
                    deviceId = s.deviceId,
                    deviceName = s.deviceName.ifEmpty { devName },
                    totalDistanceMeters = s.distance,
                    movingDurationMs = movingDurationMs,
                    idleDurationMs = idleMs,
                    stopDurationMs = stopMs,
                    maxSpeedKnots = maxKnots,
                    averageSpeedKnots = avgKnots,
                    spentFuelLiters = s.spentFuel,
                    engineHoursMs = s.engineHours
                )
            }
        }
        com.example.util.ReportReconciliationManager.reconcilePeriodReport(
            periodType = periodType,
            dailySummaries = dailyList,
            deviceId = deviceId,
            deviceName = devName,
            fromUtc = fromUtc,
            toUtc = toUtc
        )
    }

    suspend fun querySpeedingViolationReport(
        deviceId: Long,
        from: String,
        to: String,
        speedLimitKmh: Double = _overspeedThresholdKmh.value.toDouble()
    ): com.example.data.model.SpeedingViolationReport = wrapSync("Speeding Violation Report") {
        val devName = devices.value.find { it.id == deviceId }?.name ?: "Device #$deviceId"
        val positions = repository.getRouteHistory(deviceId, from, to)
        com.example.util.ReportReconciliationManager.generateSpeedingViolationReport(
            positions = positions,
            speedLimitKmh = speedLimitKmh,
            deviceId = deviceId,
            deviceName = devName
        )
    }

    suspend fun queryGeofenceAnalyticsReport(
        geofenceId: Long,
        geofenceName: String,
        deviceId: Long,
        from: String,
        to: String
    ): com.example.data.model.GeofenceReport = wrapSync("Geofence Report") {
        val devName = devices.value.find { it.id == deviceId }?.name ?: "Device #$deviceId"
        val events = repository.getEventsReport(deviceId, from, to)
        com.example.util.ReportReconciliationManager.generateGeofenceReport(
            events = events,
            geofenceId = geofenceId,
            geofenceName = geofenceName,
            deviceId = deviceId,
            deviceName = devName,
            periodStartUtc = from,
            periodEndUtc = to
        )
    }

    suspend fun queryTripsReport(
        deviceId: Long? = null,
        from: String,
        to: String
    ): List<com.example.data.model.ReportTrip> = wrapSync("Trips Report") {
        repository.getTripsReport(deviceId, from, to)
    }

    suspend fun queryStopsReport(
        deviceId: Long? = null,
        from: String,
        to: String
    ): List<com.example.data.model.ReportStop> = wrapSync("Stops Report") {
        repository.getStopsReport(deviceId, from, to)
    }

    suspend fun queryEventsReport(
        deviceId: Long? = null,
        from: String,
        to: String,
        type: String? = null
    ): List<com.example.data.model.Event> = wrapSync("Events Report") {
        repository.getEventsReport(deviceId, from, to, type)
    }

    suspend fun queryRouteReport(
        deviceId: Long,
        from: String,
        to: String
    ): List<Position> = wrapSync("Route History Report") {
        repository.getRouteHistory(deviceId, from, to)
    }
}

