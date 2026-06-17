package com.example.data.repo

import android.content.Context
import android.util.Log
import com.example.data.api.TraccarApi
import com.example.data.api.TraccarWebSocket
import com.example.data.db.AppDatabase
import com.example.data.db.CachedAlert
import com.example.data.db.CachedDevice
import com.example.data.model.*
import com.example.data.pref.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TraccarRepository(private val context: Context) {
    private val TAG = "TraccarRepository"
    val sessionManager = SessionManager(context)
    private val database = AppDatabase.getDatabase(context)

    // Dynamic API and Socket holds
    private var traccarApi: TraccarApi? = null
    private var traccarSocket: TraccarWebSocket? = null

    // Real-time tracking data flows
    private val _realtimePositions = MutableStateFlow<Map<Long, Position>>(emptyMap())
    val realtimePositions: StateFlow<Map<Long, Position>> = _realtimePositions.asStateFlow()

    private val _realtimeDevices = MutableStateFlow<List<Device>>(emptyList())
    val realtimeDevices: StateFlow<List<Device>> = _realtimeDevices.asStateFlow()

    private val _isSocketConnected = MutableStateFlow(false)
    val isSocketConnected: StateFlow<Boolean> = _isSocketConnected.asStateFlow()

    // Database Flows
    val cachedDevices: Flow<List<CachedDevice>> = database.deviceDao().getAllDevicesFlow()
    val cachedAlerts: Flow<List<CachedAlert>> = database.alertDao().getAllAlertsFlow()

    // Sandbox Simulation helpers
    private var isSimulating = false
    private val simulatedPositions = mutableMapOf<Long, Position>()
    private val simulatedDevices = mutableListOf<Device>()
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        initializeServices()
        observeWebSocketUpdates()
    }

    private fun isDemo(): Boolean {
        return sessionManager.serverUrl.equals("DEMO", ignoreCase = true) || sessionManager.serverUrl.isBlank()
    }

    fun initializeServices() {
        val serverUrl = sessionManager.serverUrl
        if (isDemo()) {
            Log.d(TAG, "Configuring Sandbox Demo Simulation Mode...")
            traccarApi = null
            traccarSocket = null
            _isSocketConnected.value = true
            startSandboxSimulation()
        } else {
            stopSandboxSimulation()
            Log.d(TAG, "Configuring REST & WebSocket API clients for: $serverUrl")
            traccarApi = TraccarApi.create(serverUrl) {
                val email = sessionManager.email
                val password = sessionManager.password
                if (email.isNotBlank() && password.isNotBlank()) {
                    Pair(email, password)
                } else null
            }
            traccarSocket = TraccarWebSocket(serverUrl) {
                val email = sessionManager.email
                val password = sessionManager.password
                if (email.isNotBlank() && password.isNotBlank()) {
                    Pair(email, password)
                } else null
            }
            
            // Connect socket if user is logged in
            if (sessionManager.isLoggedIn) {
                traccarSocket?.connect()
            }
        }
        observeWebSocketUpdates()
    }

    private var webSocketCollectJob: kotlinx.coroutines.Job? = null

    private fun observeWebSocketUpdates() {
        webSocketCollectJob?.cancel()
        val socket = traccarSocket
        if (socket == null) {
            _isSocketConnected.value = isDemo()
            return
        }

        webSocketCollectJob = scope.launch {
            // Track socket updates dynamically without leaking coroutines
            launch {
                socket.updates.collect { update ->
                    handleSocketUpdate(update)
                }
            }
            launch {
                socket.connectionState.collect { connected ->
                    _isSocketConnected.value = connected
                }
            }
        }
    }

    private suspend fun handleSocketUpdate(update: SocketUpdate) {
        update.positions?.let { positions ->
            val current = _realtimePositions.value.toMutableMap()
            positions.forEach { current[it.deviceId] = it }
            _realtimePositions.value = current
            
            // Cache positions on the local database for offline usage
            updateDevicesDatabase(devices = _realtimeDevices.value, lastPositions = current)
        }

        update.devices?.let { devices ->
            _realtimeDevices.value = devices
            updateDevicesDatabase(devices = devices, lastPositions = _realtimePositions.value)
        }

        update.events?.let { events ->
            events.forEach { event ->
                val deviceName = _realtimeDevices.value.find { it.id == event.deviceId }?.name ?: "Device ${event.deviceId}"
                val lastPos = _realtimePositions.value[event.deviceId]
                val lat = lastPos?.latitude ?: 0.0
                val lng = lastPos?.longitude ?: 0.0
                
                val alert = CachedAlert(
                    deviceId = event.deviceId,
                    deviceName = deviceName,
                    type = "event",
                    alarmType = event.type,
                    latitude = lat,
                    longitude = lng,
                    message = "Alert triggered: ${event.type} for $deviceName"
                )
                database.alertDao().insertAlert(alert)
            }
        }
    }

    private suspend fun updateDevicesDatabase(devices: List<Device>, lastPositions: Map<Long, Position>) {
        val cached = devices.map { device ->
            val pos = lastPositions[device.id]
            CachedDevice(
                id = device.id,
                name = device.name ?: "Unknown Device",
                uniqueId = device.uniqueId ?: "",
                status = device.status ?: "unknown",
                lastUpdate = device.lastUpdate,
                latitude = pos?.latitude ?: 0.0,
                longitude = pos?.longitude ?: 0.0,
                speed = pos?.speedKmh ?: 0.0,
                address = pos?.address,
                category = device.category
            )
        }
        database.deviceDao().insertDevices(cached)
    }

    // AUTH ACTIONS
    suspend fun login(server: String, email: String, pass: String): User {
        sessionManager.serverUrl = server
        sessionManager.email = email
        sessionManager.password = pass
        
        initializeServices()

        return if (isDemo()) {
            delay(1000) // Simulated network
            val mockUser = User(
                id = 101L,
                name = "SaaS Admin Tenant",
                email = email,
                administrator = true,
                deviceLimit = 50,
                userLimit = 10
            )
            sessionManager.isLoggedIn = true
            sessionManager.isAdmin = true
            sessionManager.userName = mockUser.name
            sessionManager.userId = mockUser.id
            mockUser
        } else {
            val api = traccarApi ?: throw IllegalStateException("Custom API client uninitialized")
            val user = api.login(email, pass)
            sessionManager.isLoggedIn = true
            sessionManager.isAdmin = user.administrator
            sessionManager.userName = user.name
            sessionManager.userId = user.id
            
            // Start real-time stream
            traccarSocket?.connect()
            user
        }
    }

    fun logout() {
        traccarSocket?.disconnect()
        sessionManager.logout()
        initializeServices()
        _realtimePositions.value = emptyMap()
        _realtimeDevices.value = emptyList()
        scope.launch {
            database.deviceDao().clearDevices()
        }
    }

    // CRUD: DEVICES
    suspend fun getDevices(): List<Device> {
        if (isDemo()) {
            delay(500)
            return simulatedDevices
        } else {
            val api = traccarApi ?: throw IllegalStateException("API not configured")
            val devices = try {
                api.getDevices(all = true)
            } catch (e: Exception) {
                Log.w(TAG, "getDevices(all=true) failed, falling back to all=null: ${e.message}")
                api.getDevices(all = null)
            }
            _realtimeDevices.value = devices
            
            // Pull initial positions if available
            try {
                val positions = api.getLatestPositions()
                val posMap = positions.filter { it.deviceId != null }.associateBy { it.deviceId!! }
                _realtimePositions.value = posMap
                updateDevicesDatabase(devices, posMap)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get latest positions: ${e.message}")
            }
            return devices
        }
    }

    suspend fun addDevice(device: Device): Device {
        if (isDemo()) {
            val newDevice = device.copy(id = Math.abs(Random().nextLong()))
            simulatedDevices.add(newDevice)
            return newDevice
        } else {
            val api = traccarApi ?: throw IllegalStateException("API not configured")
            val result = api.createDevice(device)
            getDevices() // refresh
            return result
        }
    }

    suspend fun updateDevice(id: Long, device: Device): Device {
        if (isDemo()) {
            val index = simulatedDevices.indexOfFirst { it.id == id }
            if (index != -1) {
                simulatedDevices[index] = device
            }
            return device
        } else {
            val api = traccarApi ?: throw IllegalStateException("API not configured")
            val result = api.updateDevice(id, device)
            getDevices() // refresh
            return result
        }
    }

    suspend fun deleteDevice(id: Long) {
        if (isDemo()) {
            simulatedDevices.removeAll { it.id == id }
            simulatedPositions.remove(id)
        } else {
            val api = traccarApi ?: throw IllegalStateException("API not configured")
            api.deleteDevice(id)
            getDevices() // refresh
        }
    }

    private val Long.absoluteValue: Long
        get() = if (this < 0) -this else this

    // CRUD: USERS (Admin View)
    suspend fun getUsers(): List<User> {
        if (isDemo()) {
            return listOf(
                User(id = 101, name = "SaaS Admin Tenant", email = "abinet22@gmail.com", administrator = true),
                User(id = 102, name = "Logistics Dispatcher", email = "dispatcher@saas.com", administrator = false),
                User(id = 103, name = "Fleet Supervisor B", email = "supervisor@saas.com", administrator = false)
            )
        } else {
            val api = traccarApi ?: throw IllegalStateException("API not configured")
            return api.getUsers()
        }
    }

    suspend fun createUser(user: User): User {
        if (isDemo()) {
            return user.copy(id = Random().nextLong().absoluteValue)
        } else {
            val api = traccarApi ?: throw IllegalStateException("API not configured")
            return api.createUser(user)
        }
    }

    suspend fun deleteUser(id: Long) {
        if (!isDemo()) {
            val api = traccarApi ?: throw IllegalStateException("API not configured")
            api.deleteUser(id)
        }
    }

    suspend fun sendCommand(deviceId: Long, commandType: String, description: String) {
        if (!isDemo()) {
            val api = traccarApi ?: throw IllegalStateException("API not configured")
            api.sendCommand(
                DeviceCommand(
                    deviceId = deviceId,
                    type = commandType,
                    description = description
                )
            )
        }
    }

    // HISTORICAL REPORTS & PLAYBACK
    suspend fun getRouteHistory(deviceId: Long, from: String, to: String): List<Position> {
        if (isDemo()) {
            // Generate some beautiful mock route coordinates for historical playback
            delay(800)
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            val list = mutableListOf<Position>()
            // Route from San Francisco towards Oakland or Seattle starting point
            val startLat = 37.7749
            val startLng = -122.4194
            val count = 25
            
            for (i in 0 until count) {
                // progressive motion
                val lat = startLat + (i * 0.0035) * if (deviceId % 2 == 0L) 1 else -1
                val lng = startLng + (i * 0.0055)
                val date = Date(format.parse(from).time + (i * 15 * 60 * 1000L)) // every 15 min
                val timeString = format.format(date)
                
                list.add(
                    Position(
                        id = i.toLong(),
                        deviceId = deviceId,
                        protocol = "mock_gps",
                        deviceTime = timeString,
                        fixTime = timeString,
                        latitude = lat,
                        longitude = lng,
                        altitude = 45.0,
                        speed = 35.0 + (i % 5) * 5.0,
                        course = 45.0 + (i * 3) % 360,
                        address = "Highway 101, Mile ${i + 1}",
                        accuracy = 5.0,
                        attributes = mapOf("ign" to true, "voltage" to 12.8)
                    )
                )
            }
            return list
        } else {
            val api = traccarApi ?: throw IllegalStateException("API not configured")
            return api.getRouteReport(deviceId, from, to)
        }
    }

    // GEOFENCE NETWORK MANAGERS OR SANDBOX FALLBACKS
    private val simulatedGeofences = mutableListOf<TraccarGeofence>()

    suspend fun getGeofences(): List<TraccarGeofence> {
        if (isDemo()) {
            delay(300)
            return simulatedGeofences
        } else {
            val api = traccarApi ?: throw IllegalStateException("API not configured")
            return api.getGeofences()
        }
    }

    suspend fun createGeofence(geofence: TraccarGeofence): TraccarGeofence {
        if (isDemo()) {
            delay(300)
            val newGf = geofence.copy(id = Math.abs(Random().nextLong()))
            simulatedGeofences.add(newGf)
            return newGf
        } else {
            val api = traccarApi ?: throw IllegalStateException("API not configured")
            return api.createGeofence(geofence)
        }
    }

    suspend fun linkGeofenceDevice(deviceId: Long, geofenceId: Long): Boolean {
        if (isDemo()) {
            delay(300)
            return true
        } else {
            val api = traccarApi ?: throw IllegalStateException("API not configured")
            val p = TraccarPermission(deviceId = deviceId, geofenceId = geofenceId)
            val response = api.linkGeofenceDevice(p)
            return response.isSuccessful
        }
    }

    suspend fun deleteGeofence(id: Long): Boolean {
        if (isDemo()) {
            delay(300)
            simulatedGeofences.removeAll { it.id == id }
            return true
        } else {
            val api = traccarApi ?: throw IllegalStateException("API not configured")
            val response = api.deleteGeofence(id)
            return response.isSuccessful
        }
    }

    // SANDBOX SIMULATOR BACKEND
    private fun startSandboxSimulation() {
        if (isSimulating) return
        isSimulating = true

        simulatedDevices.clear()
        simulatedDevices.addAll(
            listOf(
                Device(id = 1, name = "Interstate Truck 04", uniqueId = "TRK04", status = "online", category = "truck"),
                Device(id = 2, name = "Enterprise Delivery Van", uniqueId = "VAN12", status = "online", category = "car"),
                Device(id = 3, name = "Asset Container Tracker", uniqueId = "CNT08", status = "offline", category = "arrow"),
                Device(id = 4, name = "Sales Rep Sedan B", uniqueId = "CAR89", status = "online", category = "car")
            )
        )

        // Starting positions around San Francisco
        simulatedPositions[1] = Position(1001, 1, "sim", "2026-06-16T12:00:00Z", "2026-06-16T12:00:00Z", true, 37.7749, -122.4194, 15.0, 48.0, 180.0, "Fell St, San Francisco, CA")
        simulatedPositions[2] = Position(1002, 2, "sim", "2026-06-16T12:00:00Z", "2026-06-16T12:00:00Z", true, 37.7599, -122.4368, 5.0, 22.0, 90.0, "Castro St, San Francisco, CA")
        simulatedPositions[3] = Position(1003, 3, "sim", "2026-06-16T12:00:00Z", "2026-06-16T12:00:00Z", true, 37.8024, -122.4058, 0.0, 0.0, 0.0, "The Embarcadero, San Francisco, CA")
        simulatedPositions[4] = Position(1004, 4, "sim", "2026-06-16T12:00:00Z", "2026-06-16T12:00:00Z", true, 37.7394, -122.4494, 23.0, 31.0, 270.0, "Ocean Ave, San Francisco, CA")

        _realtimePositions.value = simulatedPositions.toMap()
        _realtimeDevices.value = simulatedDevices.toList()

        scope.launch {
            var counter = 0
            while (isSimulating) {
                delay(3000) // Update map locations every 3 seconds

                // Update active vehicles positions
                listOf(1L, 2L, 4L).forEach { devId ->
                    val last = simulatedPositions[devId]
                    if (last != null) {
                        // Drift lat/lng slightly to simulate real travel
                        val latDrift = (Random().nextDouble() - 0.5) * 0.0012
                        val lngDrift = (Random().nextDouble() - 0.5) * 0.0016
                        val newSpeed = (30..75).random().toDouble()
                        val newCourse = (0..359).random().toDouble()
                        
                        val newPos = last.copy(
                            id = last.id + 1,
                            latitude = last.latitude + latDrift,
                            longitude = last.longitude + lngDrift,
                            speed = newSpeed / 1.852, // convert to knots
                            course = newCourse,
                            deviceTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                        )
                        simulatedPositions[devId] = newPos
                    }
                }

                _realtimePositions.value = simulatedPositions.toMap()

                // Cache simulation data dynamically
                updateDevicesDatabase(simulatedDevices, simulatedPositions)

                // Periodically trigger a random, beautiful event alert log for presentation
                counter++
                if (counter % 5 == 0) {
                    val alertDevId = listOf(1L, 2L, 4L).random()
                    val devName = simulatedDevices.find { it.id == alertDevId }?.name ?: "Unknown Vehicle"
                    val alarms = listOf("overspeed", "geofenceEnter", "geofenceExit", "sos", "powerRestored", "lowBattery")
                    val chosenAlarm = alarms.random()
                    
                    val pos = simulatedPositions[alertDevId]
                    val alert = CachedAlert(
                        deviceId = alertDevId,
                        deviceName = devName,
                        type = "alarm",
                        alarmType = chosenAlarm,
                        latitude = pos?.latitude ?: 37.7749,
                        longitude = pos?.longitude ?: -122.4194,
                        message = when (chosenAlarm) {
                            "overspeed" -> "Overspeed detected: ${String.format("%.1f", (pos?.speedKmh ?: 75.0))} km/h (Limit: 60 km/h)"
                            "sos" -> "⚠️ Emergency SOS triggered by Driver!"
                            "geofenceEnter" -> "Geofence Enclosed Region entered safely"
                            "geofenceExit" -> "⚠️ Left authorized company depot boundary"
                            else -> "Device report alert status generated"
                        }
                    )
                    database.alertDao().insertAlert(alert)
                }
            }
        }
    }

    private fun stopSandboxSimulation() {
        isSimulating = false
    }
}
