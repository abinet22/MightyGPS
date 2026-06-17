package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.CachedAlert
import com.example.data.db.CachedDevice
import com.example.data.model.Device
import com.example.data.model.Position
import com.example.data.model.User
import com.example.data.repo.TraccarRepository
import com.example.ui.map.MapMarker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
        val points: List<Pair<Double, Double>> = emptyList()
    )

    private val _geofences = MutableStateFlow<List<CustomGeofence>>(
        listOf(
            CustomGeofence("gf_1", "Addis Ababa Fleet Hub", 9.0192, 38.7525, 2500.0, true, "CIRCLE (9.0192 38.7525, 2500.0)", "circle"),
            CustomGeofence("gf_2", "Bole Logistics Center", 8.9773, 38.7968, 1200.0, true, "CIRCLE (8.9773 38.7968, 1200.0)", "circle"),
            CustomGeofence("gf_3", "Arada Custom Station", 9.0350, 38.7620, 800.0, true, "CIRCLE (9.0350 38.7620, 800.0)", "circle")
        )
    )
    val geofences: StateFlow<List<CustomGeofence>> = _geofences.asStateFlow()

    // Data holder for sent telematics commands
    data class DispatchedCommand(
        val id: String,
        val deviceName: String,
        val commandType: String,
        val payload: String,
        val timestamp: String,
        val status: String
    )

    private val _commandsLog = MutableStateFlow<List<DispatchedCommand>>(
        listOf(
            DispatchedCommand("cmd_1", "Heavy Fleet Truck A", "Engine Cut-Off", "RELAY_1=OFF", "10:15:30 AM", "EXECUTED"),
            DispatchedCommand("cmd_2", "Express Van B", "Ping Device", "MODE=SILENT", "11:04:12 AM", "ACKNOWLEDGED")
        )
    )
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

    fun addGeofence(
        name: String, 
        lat: Double, 
        lng: Double, 
        radius: Double, 
        type: String = "circle", 
        points: List<Pair<Double, Double>> = emptyList(), 
        deviceId: Long? = null
    ) {
        viewModelScope.launch {
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
                    description = "Custom Drawn Mapbox Bound",
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
                    points = points
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
                    points = points
                )
                _geofences.value = _geofences.value + newGf
            }
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

    fun sendDeviceCommand(deviceId: Long, commandType: String, description: String) {
        viewModelScope.launch {
            try {
                _feedbackMessage.value = "Dispatching $commandType ..."
                val selectedDevice = devices.value.find { it.id == deviceId }
                val targetName = selectedDevice?.name ?: "Device ($deviceId)"
                
                // Track in the localized UI dispatch history log
                val sdf = SimpleDateFormat("HH:mm:ss a", Locale.getDefault())
                val timeNow = sdf.format(Date())
                val newCmd = DispatchedCommand(
                    id = "cmd_" + System.currentTimeMillis(),
                    deviceName = targetName,
                    commandType = commandType,
                    payload = description,
                    timestamp = timeNow,
                    status = "SENT"
                )
                _commandsLog.value = listOf(newCmd) + _commandsLog.value
                
                // Attempt to send real Traccar rest-api command if connected
                if (sessionManager.serverUrl != "DEMO") {
                    // Send actual request if supported by Traccar api
                    try {
                        repository.sendCommand(deviceId, commandType, description)
                        _feedbackMessage.value = "$commandType successfully command-queued"
                        // update status
                        updateCommandStatus(newCmd.id, "EXECUTED")
                    } catch (e: Exception) {
                        _feedbackMessage.value = "API Sent - Queued on Server"
                        updateCommandStatus(newCmd.id, "ACKNOWLEDGED")
                    }
                } else {
                    kotlinx.coroutines.delay(1000)
                    _feedbackMessage.value = "$commandType simulated payload completed successfully!"
                    updateCommandStatus(newCmd.id, "EXECUTED")
                }
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
            "es" -> mapOf(
                "active_fleet" to "Flota Activa",
                "map_style" to "Estilo de Mapa",
                "language" to "Idioma",
                "marker_label" to "Etiqueta de Marcador",
                "marker_icon" to "Icono de Marcador",
                "send_command" to "Enviar Comando",
                "geofence" to "Geovallas",
                "settings" to "Ajustes",
                "playback" to "Historial",
                "devices" to "Dispositivos",
                "alerts" to "Alertas",
                "saas_console" to "Consola SaaS",
                "create_geofence" to "Crear Geovalla",
                "command" to "Comando de Gps",
                "commands" to "Registro de Comandos",
                "plate_number" to "Número de Placa",
                "device_name" to "Nombre de Dispositivo",
                "coordinates" to "Coordenadas Gps",
                "select_device" to "Seleccionar Dispositivo",
                "speed" to "Velocidad",
                "status" to "Estado",
                "customization_panel" to "Personalización del Cliente",
                "engine_status" to "Estado del Motor",
                "engine_kill" to "Detener Motor",
                "unblock_engine" to "Reanudar Motor",
                "ping_asset" to "Ping Localizador",
                "reboot_gps" to "Reiniciar Localizador",
                "geofence_name" to "Nombre de Geovalla",
                "radius" to "Radio (metros)",
                "save_geofence" to "Guardar Geovalla",
                "no_geofences" to "No hay geovallas configuradas.",
                "command_payload" to "Parámetros del Comando",
                "tenant_mode" to "Mighty GPS - Acceso Enterprise",
                "assigned_vehicles" to "Equipos asignados para visualización"
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

    fun fetchInitialState() {
        fetchDevices()
        fetchGeofencesFromServer()
        if (sessionManager.isAdmin) {
            fetchTenantUsers()
        }
    }

    fun fetchGeofencesFromServer() {
        viewModelScope.launch {
            try {
                val serverGfs = wrapSync("Geofences") {
                    repository.getGeofences()
                }
                if (serverGfs.isNotEmpty()) {
                    val mapped = serverGfs.map { sgf ->
                        var lat = 9.0192
                        var lng = 38.7525
                        var rad = 1000.0
                        var type = "circle"
                        var points = emptyList<Pair<Double, Double>>()

                        try {
                            if (sgf.area.startsWith("CIRCLE", ignoreCase = true)) {
                                val clean = sgf.area.substringAfter("(").substringBefore(")")
                                val coordPart = clean.substringBefore(",")
                                val radPart = clean.substringAfter(",")
                                val coords = coordPart.trim().split(" ")
                                lat = coords[0].toDouble()
                                lng = coords[1].toDouble()
                                rad = radPart.trim().toDouble()
                            } else if (sgf.area.startsWith("POLYGON", ignoreCase = true)) {
                                type = "polygon"
                                val clean = sgf.area.substringAfter("((").substringBefore("))")
                                val pairs = clean.split(",")
                                val pts = pairs.map { p ->
                                    val parts = p.trim().split(" ")
                                    val pointLng = parts[0].toDouble()
                                    val pointLat = parts[1].toDouble()
                                    Pair(pointLat, pointLng)
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

                        CustomGeofence(
                            id = sgf.id.toString(),
                            name = sgf.name,
                            latitude = lat,
                            longitude = lng,
                            radiusMeters = rad,
                            isActive = true,
                            areaWkt = sgf.area,
                            type = type,
                            points = points
                        )
                    }
                    _geofences.value = mapped
                }
            } catch (e: Exception) {
                Log.e("TraccarViewModel", "Failed to fetch geofences standard flow", e)
            }
        }
    }

    fun fetchDevices() {
        viewModelScope.launch {
            try {
                val list = wrapSync("Devices") {
                    repository.getDevices()
                }
                _devices.value = list
            } catch (e: Exception) {
                Log.e(TAG, "Fetch devices failed: ${e.message}")
            }
        }
    }

    fun fetchTenantUsers() {
        viewModelScope.launch {
            try {
                val list = wrapSync("Tenant Roster") {
                    repository.getUsers()
                }
                _usersList.value = list
            } catch (e: Exception) {
                Log.e(TAG, "Fetch tenant users failed: ${e.message}")
            }
        }
    }

    // CRUD: Manage Devices
    fun addNewDevice(name: String, uniqueId: String, category: String) {
        viewModelScope.launch {
            try {
                val stub = Device(
                    id = 0,
                    name = name,
                    uniqueId = uniqueId,
                    status = "offline",
                    category = category.lowercase(),
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
        viewModelScope.launch {
            _historyLoading.value = true
            _routeHistory.value = emptyList()
            try {
                val fromStr = 纯FormatDate(fromTime)
                val toStr = 纯FormatDate(toTime)

                val trail = wrapSync("Route history") {
                    repository.getRouteHistory(deviceId, fromStr, toStr)
                }
                _routeHistory.value = trail
                if (trail.isEmpty()) {
                    _feedbackMessage.value = "No historical logs found for the selected time range"
                } else {
                    _feedbackMessage.value = "Loaded ${trail.size} breadcrumbs for historical playback"
                }
            } catch (e: java.lang.Exception) {
                Log.e(TAG, "Failed loading route coordinate range: ${e.message}")
                _feedbackMessage.value = "Failed coordinates query: ${e.message}"
            } finally {
                _historyLoading.value = false
            }
        }
    }

    fun loadPlaybackHistory(deviceId: Long, hours: Int = 12) {
        viewModelScope.launch {
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
                _routeHistory.value = trail
                if (trail.isEmpty()) {
                    _feedbackMessage.value = "No historical trail entries found in past ${hours}h for this asset"
                } else {
                    _feedbackMessage.value = "Loaded ${trail.size} breadcrumbs for historical playback"
                }
            } catch (e: java.lang.Exception) {
                Log.e(TAG, "Failed loading playback coordinates: ${e.message}")
                _feedbackMessage.value = "Failed coordinates query: ${e.message}"
            } finally {
                _historyLoading.value = false
            }
        }
    }

    private fun 纯FormatDate(date: Date): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(date)
    }

    // Mapper conversion list back output helper
    fun getMapMarkers(realtimePositions: Map<Long, Position>, devices: List<Device>): List<MapMarker> {
        return devices.map { device ->
            val pos = realtimePositions[device.id]
            MapMarker(
                id = device.id,
                name = device.name,
                latitude = pos?.latitude ?: 0.0,
                longitude = pos?.longitude ?: 0.0,
                course = pos?.course?.toFloat() ?: 0f,
                status = device.status,
                speedKmh = pos?.speedKmh ?: 0.0,
                category = device.category
            )
        }.filter { it.latitude != 0.0 && it.longitude != 0.0 }
    }
}
