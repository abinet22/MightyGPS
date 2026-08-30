package com.example.data.repo

import android.content.Context
import android.util.Log
import android.util.LruCache
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

    suspend fun saveAlert(alert: CachedAlert) {
        database.alertDao().insertAlert(alert)
    }

    // Lightweight In-Memory Caches for Reports & Route Histories
    private val routeCache = LruCache<String, List<Position>>(30)
    private val summaryCache = LruCache<String, List<ReportSummary>>(30)
    private val tripsCache = LruCache<String, List<ReportTrip>>(30)
    private val stopsCache = LruCache<String, List<ReportStop>>(30)
    private val eventsCache = LruCache<String, List<Event>>(30)

    fun clearReportCaches() {
        routeCache.evictAll()
        summaryCache.evictAll()
        tripsCache.evictAll()
        stopsCache.evictAll()
        eventsCache.evictAll()
    }

    // Sandbox Simulation helpers
    private var isSimulating = false
    private val simulatedPositions = mutableMapOf<Long, Position>()
    private val simulatedDevices = mutableListOf<Device>()
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        com.example.data.api.TraccarCookieJar.init(context)
        initializeServices()
        observeWebSocketUpdates()
    }

    private fun isDemo(): Boolean {
        return sessionManager.serverUrl.equals("DEMO", ignoreCase = true) || sessionManager.serverUrl.isBlank()
    }

    fun initializeServices() {
        try {
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
                traccarApi = TraccarApi.create(serverUrl, sessionManager) {
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
                    traccarSocket?.startStalenessWatchdog()
                }
            }
            observeWebSocketUpdates()
        } catch (e: Exception) {
            Log.e(TAG, "Failed initializing API / WebSocket services: ${e.message}", e)
            traccarApi = null
            traccarSocket = null
            _isSocketConnected.value = false
        }
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

    private fun handleSocketUpdate(update: SocketUpdate) {
        update.positions?.let { positions ->
            val current = _realtimePositions.value.toMutableMap()
            positions.forEach { current[it.deviceId] = it }
            _realtimePositions.value = current
            
            // Cache positions on the local database for offline usage in background thread
            scope.launch(Dispatchers.IO) {
                updateDevicesDatabase(devices = _realtimeDevices.value, lastPositions = current)
            }
        }

        update.devices?.let { devices ->
            _realtimeDevices.value = devices
            scope.launch(Dispatchers.IO) {
                updateDevicesDatabase(devices = devices, lastPositions = _realtimePositions.value)
            }
        }

        update.events?.let { events ->
            scope.launch(Dispatchers.IO) {
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
            traccarSocket?.startStalenessWatchdog()
            user
        }
    }

    fun logout() {
        traccarSocket?.disconnect()
        sessionManager.logout()
        com.example.data.api.TraccarCookieJar.clear()
        clearReportCaches()
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
            val api = traccarApi ?: return loadCachedDevicesFallback()
            try {
                val devices = try {
                    api.getDevices(all = true)
                } catch (e: Exception) {
                    Log.w(TAG, "getDevices(all=true) returned: ${e.message}, falling back to all=null")
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
                    Log.w(TAG, "Positions sync notice: ${e.message}")
                }
                return devices
            } catch (e: Exception) {
                if (e is retrofit2.HttpException && e.code() == 401) {
                    Log.w(TAG, "Remote fetch devices (401 Unauthorized), retrieving from local cache...")
                } else {
                    Log.w(TAG, "Remote fetch devices (${e.message}), retrieving from local cache...")
                }
                return loadCachedDevicesFallback()
            }
        }
    }

    private suspend fun loadCachedDevicesFallback(): List<Device> {
        val cachedList = database.deviceDao().getAllDevicesDirect()
        if (cachedList.isNotEmpty()) {
            val fallbackDevices = cachedList.map { cd ->
                Device(
                    id = cd.id,
                    name = cd.name,
                    uniqueId = cd.uniqueId,
                    status = cd.status,
                    lastUpdate = cd.lastUpdate,
                    category = cd.category
                )
            }
            val fallbackPositions = cachedList.associate { cd ->
                val pos = Position(
                    id = cd.id,
                    deviceId = cd.id,
                    deviceTime = cd.lastUpdate,
                    fixTime = cd.lastUpdate,
                    latitude = cd.latitude,
                    longitude = cd.longitude,
                    speed = cd.speed / 1.852, // convert km/h back to knots
                    address = cd.address
                )
                cd.id to pos
            }
            _realtimeDevices.value = fallbackDevices
            _realtimePositions.value = fallbackPositions
            return fallbackDevices
        } else {
            return _realtimeDevices.value
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
            val api = traccarApi ?: return emptyList()
            return try {
                api.getUsers()
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401 || e.code() == 403) {
                    Log.w(TAG, "getUsers unauthorized/forbidden on server (HTTP ${e.code()}). User may have operator permissions.")
                    emptyList()
                } else {
                    Log.w(TAG, "getUsers server response: HTTP ${e.code()}")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.w(TAG, "getUsers request failed: ${e.message}")
                emptyList()
            }
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

    suspend fun sendCommand(deviceId: Long, commandType: String, description: String): CommandResult {
        if (isDemo()) {
            delay(400)
            return CommandResult(success = true, queued = false, code = 200)
        }
        val api = traccarApi ?: return CommandResult(false, false, -1, "API not configured")
        return try {
            val response = api.sendCommand(
                DeviceCommand(deviceId = deviceId, type = commandType, description = description)
            )
            when {
                response.code() == 202 -> CommandResult(true, queued = true, code = 202) // Traccar returns 202 when device is offline & command is queued
                response.isSuccessful -> CommandResult(true, false, response.code())
                response.code() == 401 || response.code() == 403 -> CommandResult(false, false, response.code(), "Unauthorized to send this command")
                response.code() == 400 -> CommandResult(false, false, 400, "Command not supported by this device's protocol")
                response.code() == 504 -> CommandResult(false, false, 504, "Device did not respond in time")
                else -> CommandResult(false, false, response.code(), "Unexpected response ${response.code()}")
            }
        } catch (e: Exception) {
            CommandResult(false, false, -1, e.message)
        }
    }

    // HISTORICAL REPORTS & PLAYBACK
    suspend fun getSummaryReport(
        deviceId: Long? = null,
        from: String,
        to: String,
        daily: Boolean? = null
    ): List<ReportSummary> {
        val cacheKey = "${deviceId ?: "all"}:$from:$to:$daily"
        summaryCache.get(cacheKey)?.let { return it }

        val result = if (isDemo()) {
            delay(400)
            val devList = if (deviceId != null) {
                simulatedDevices.filter { it.id == deviceId }
            } else {
                simulatedDevices
            }
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            val fromDate = try { format.parse(from) } catch (_: Exception) { Date(System.currentTimeMillis() - 24 * 3600 * 1000L) }
            val toDate = try { format.parse(to) } catch (_: Exception) { Date() }
            val durationHours = ((toDate?.time ?: System.currentTimeMillis()) - (fromDate?.time ?: (System.currentTimeMillis() - 24 * 3600 * 1000L))) / (1000 * 3600.0)
            val daysMultiplier = maxOf(0.5, durationHours / 24.0)

            devList.map { dev ->
                val baseDistanceMeters = (85000.0 + (dev.id * 24500.0)) * daysMultiplier
                val avgSpeedKnots = 18.0 + (dev.id % 4) * 3.5
                val maxSpeedKnots = 42.0 + (dev.id % 3) * 6.0
                val fuelLiters = (baseDistanceMeters / 1000.0) * 0.095
                val engineHoursMs = ((baseDistanceMeters / 1000.0) / (avgSpeedKnots * 1.852) * 3600 * 1000).toLong()

                ReportSummary(
                    deviceId = dev.id,
                    deviceName = dev.name,
                    maxSpeed = maxSpeedKnots,
                    averageSpeed = avgSpeedKnots,
                    distance = baseDistanceMeters,
                    spentFuel = fuelLiters,
                    engineHours = engineHoursMs
                )
            }
        } else {
            val api = traccarApi ?: return emptyList()
            try {
                val results = api.getSummaryReport(deviceId = deviceId, from = from, to = to, daily = daily)
                if (results.isNotEmpty()) {
                    results
                } else if (deviceId != null) {
                    // Fallback calculate summary from route history
                    val route = getRouteHistory(deviceId, from, to)
                    if (route.isNotEmpty()) {
                        val maxSpd = route.maxOfOrNull { it.speed } ?: 0.0
                        val avgSpd = route.map { it.speed }.average()
                        var distMeters = 0.0
                        for (i in 0 until route.size - 1) {
                            val p1 = route[i]
                            val p2 = route[i + 1]
                            val r = 6371000.0 // meters
                            val dLat = Math.toRadians(p2.latitude - p1.latitude)
                            val dLon = Math.toRadians(p2.longitude - p1.longitude)
                            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                                    Math.cos(Math.toRadians(p1.latitude)) * Math.cos(Math.toRadians(p2.latitude)) *
                                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
                            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
                            distMeters += (r * c)
                        }
                        val devName = _realtimeDevices.value.find { it.id == deviceId }?.name ?: "Device #$deviceId"
                        listOf(
                            ReportSummary(
                                deviceId = deviceId,
                                deviceName = devName,
                                maxSpeed = maxSpd,
                                averageSpeed = avgSpd,
                                distance = distMeters,
                                spentFuel = (distMeters / 1000.0) * 0.088,
                                engineHours = (distMeters / 1000.0 / maxOf(20.0, avgSpd * 1.852) * 3600000).toLong()
                            )
                        )
                    } else emptyList()
                } else emptyList()
            } catch (e: Exception) {
                Log.w(TAG, "getSummaryReport remote error: ${e.message}")
                emptyList()
            }
        }
        if (result.isNotEmpty()) {
            summaryCache.put(cacheKey, result)
        }
        return result
    }

    suspend fun getTripsReport(
        deviceId: Long? = null,
        from: String,
        to: String
    ): List<ReportTrip> {
        val cacheKey = "${deviceId ?: "all"}:$from:$to"
        tripsCache.get(cacheKey)?.let { return it }

        val result = if (isDemo()) {
            delay(500)
            val devId = deviceId ?: 1L
            val devName = simulatedDevices.find { it.id == devId }?.name ?: "Interstate Truck 04"
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            val fromDate = try { format.parse(from) } catch (_: Exception) { Date(System.currentTimeMillis() - 24 * 3600 * 1000L) }
            val toDate = try { format.parse(to) } catch (_: Exception) { Date() }
            val durationHours = ((toDate?.time ?: System.currentTimeMillis()) - (fromDate?.time ?: (System.currentTimeMillis() - 24 * 3600 * 1000L))) / (1000 * 3600.0)
            val tripCount = when {
                durationHours > 200 -> 32 // Monthly
                durationHours > 48 -> 14  // Weekly
                else -> 4                 // Today
            }

            val trips = mutableListOf<ReportTrip>()
            val sampleLocations = listOf(
                "Central Logistics Depot, Hub A" to Pair(37.7749, -122.4194),
                "Mission Bay Distribution Center" to Pair(37.7690, -122.3890),
                "Oakland Freight Maritime Terminal" to Pair(37.8044, -122.2711),
                "South Bay Enterprise Warehouse" to Pair(37.7394, -122.4494),
                "North Waterfront Fulfillment Yard" to Pair(37.8080, -122.4120),
                "San Jose Logistics Staging Area" to Pair(37.3382, -121.8863),
                "Twin Peaks Transit Zone" to Pair(37.7599, -122.4368),
                "Commercial Delivery Hub Alpha" to Pair(37.7670, -122.4440)
            )

            val stepTime = ((toDate?.time ?: System.currentTimeMillis()) - (fromDate?.time ?: 0L)) / tripCount
            for (i in 0 until tripCount) {
                val startLoc = sampleLocations[i % sampleLocations.size]
                val endLoc = sampleLocations[(i + 1) % sampleLocations.size]
                val startTimeMillis = (fromDate?.time ?: System.currentTimeMillis()) + (i * stepTime) + (15 * 60 * 1000L)
                val durationMs = (25 + (i * 7) % 45) * 60 * 1000L
                val endTimeMillis = startTimeMillis + durationMs
                val tripDistMeters = (14000.0 + ((i * 8500) % 32000))
                val avgSpd = (32.0 + (i % 5) * 4.0) / 1.852
                val maxSpd = (55.0 + (i % 4) * 8.0) / 1.852

                trips.add(
                    ReportTrip(
                        deviceId = devId,
                        deviceName = devName,
                        distance = tripDistMeters,
                        averageSpeed = avgSpd,
                        maxSpeed = maxSpd,
                        spentFuel = (tripDistMeters / 1000.0) * 0.092,
                        startPositionId = (1000 + i * 10).toLong(),
                        endPositionId = (1000 + i * 10 + 9).toLong(),
                        startTime = format.format(Date(startTimeMillis)),
                        startAddress = startLoc.first,
                        startLat = startLoc.second.first,
                        startLon = startLoc.second.second,
                        endTime = format.format(Date(endTimeMillis)),
                        endAddress = endLoc.first,
                        endLat = endLoc.second.first,
                        endLon = endLoc.second.second,
                        duration = durationMs,
                        driverUniqueId = "DRV-0${(i % 3) + 1}",
                        driverName = when (i % 3) {
                            0 -> "Abebe Bekele"
                            1 -> "Dawit Haile"
                            else -> "Michael Tadesse"
                        }
                    )
                )
            }
            com.example.util.ReverseGeocoder.enhanceTrips(context, trips)
        } else {
            val api = traccarApi ?: return emptyList()
            try {
                val rawTrips = api.getTripsReport(deviceId = deviceId, from = from, to = to)
                com.example.util.ReverseGeocoder.enhanceTrips(context, rawTrips)
            } catch (e: Exception) {
                Log.w(TAG, "getTripsReport remote failed: ${e.message}")
                emptyList()
            }
        }
        if (result.isNotEmpty()) {
            tripsCache.put(cacheKey, result)
        }
        return result
    }

    suspend fun getStopsReport(
        deviceId: Long? = null,
        from: String,
        to: String
    ): List<ReportStop> {
        val cacheKey = "${deviceId ?: "all"}:$from:$to"
        stopsCache.get(cacheKey)?.let { return it }

        val result = if (isDemo()) {
            delay(400)
            val devId = deviceId ?: 1L
            val devName = simulatedDevices.find { it.id == devId }?.name ?: "Interstate Truck 04"
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            val fromDate = try { format.parse(from) } catch (_: Exception) { Date(System.currentTimeMillis() - 24 * 3600 * 1000L) }
            val toDate = try { format.parse(to) } catch (_: Exception) { Date() }
            val durationHours = ((toDate?.time ?: System.currentTimeMillis()) - (fromDate?.time ?: (System.currentTimeMillis() - 24 * 3600 * 1000L))) / (1000 * 3600.0)
            val stopCount = when {
                durationHours > 200 -> 36 // Monthly
                durationHours > 48 -> 16  // Weekly
                else -> 5                 // Today
            }

            val stops = mutableListOf<ReportStop>()
            val stopLocations = listOf(
                "Central Logistics Depot Bay 3" to Pair(37.7749, -122.4194),
                "Customer Fulfillment Loading Zone" to Pair(37.7690, -122.3890),
                "Highway Service Plaza Fueling Station" to Pair(37.8044, -122.2711),
                "Fleet Maintenance Yard" to Pair(37.7394, -122.4494),
                "Retail Distribution Staging Terminal" to Pair(37.8080, -122.4120),
                "San Jose Logistics Staging Area" to Pair(37.3382, -121.8863),
                "Twin Peaks Staging Zone, Castro Blvd" to Pair(37.7599, -122.4368)
            )

            val stepTime = ((toDate?.time ?: System.currentTimeMillis()) - (fromDate?.time ?: 0L)) / stopCount
            for (i in 0 until stopCount) {
                val loc = stopLocations[i % stopLocations.size]
                val startTimeMillis = (fromDate?.time ?: System.currentTimeMillis()) + (i * stepTime)
                val durationMs = (15 + (i * 12) % 60) * 60 * 1000L
                val endTimeMillis = startTimeMillis + durationMs

                val isEngineIdling = (i % 2 == 1) // Alternate between parked (engine off) and idling (engine on)
                stops.add(
                    ReportStop(
                        deviceId = devId,
                        deviceName = devName,
                        duration = durationMs,
                        startTime = format.format(Date(startTimeMillis)),
                        endTime = format.format(Date(endTimeMillis)),
                        positionId = (2000 + i).toLong(),
                        latitude = loc.second.first,
                        longitude = loc.second.second,
                        address = loc.first,
                        spentFuel = 0.4 + (i % 3) * 0.2,
                        engineHours = if (isEngineIdling) durationMs / 1000 else 0L,
                        attributes = mapOf("ignition" to isEngineIdling)
                    )
                )
            }
            com.example.util.ReverseGeocoder.enhanceStops(context, stops)
        } else {
            val api = traccarApi ?: return emptyList()
            try {
                val rawStops = api.getStopsReport(deviceId = deviceId, from = from, to = to)
                com.example.util.ReverseGeocoder.enhanceStops(context, rawStops)
            } catch (e: Exception) {
                Log.w(TAG, "getStopsReport remote failed: ${e.message}")
                emptyList()
            }
        }
        if (result.isNotEmpty()) {
            stopsCache.put(cacheKey, result)
        }
        return result
    }

    suspend fun getEventsReport(
        deviceId: Long? = null,
        from: String,
        to: String,
        type: String? = null
    ): List<Event> {
        val cacheKey = "${deviceId ?: "all"}:$from:$to:$type"
        eventsCache.get(cacheKey)?.let { return it }

        val result = if (isDemo()) {
            delay(300)
            val devId = deviceId ?: 1L
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            val toTime = System.currentTimeMillis()
            listOf(
                Event(1, "deviceMoving", format.format(Date(toTime - 30 * 60 * 1000L)), devId, 101, 0, mapOf("speed" to 58.2)),
                Event(2, "geofenceEnter", format.format(Date(toTime - 90 * 60 * 1000L)), devId, 102, 101, mapOf("geofence" to "SF Logistics & Central Depot")),
                Event(3, "alarm", format.format(Date(toTime - 180 * 60 * 1000L)), devId, 103, 0, mapOf("alarm" to "overspeed", "speed" to 88.4)),
                Event(4, "deviceStopped", format.format(Date(toTime - 240 * 60 * 1000L)), devId, 104, 0, mapOf("duration" to 1800000)),
                Event(5, "geofenceExit", format.format(Date(toTime - 360 * 60 * 1000L)), devId, 105, 102, mapOf("geofence" to "Mission Bay Transit Corridor"))
            )
        } else {
            val api = traccarApi ?: return emptyList()
            try {
                api.getEventsReport(deviceId = deviceId, from = from, to = to, type = type)
            } catch (e: Exception) {
                Log.w(TAG, "getEventsReport error: ${e.message}")
                emptyList()
            }
        }
        if (result.isNotEmpty()) {
            eventsCache.put(cacheKey, result)
        }
        return result
    }

    suspend fun getRouteHistory(deviceId: Long, from: String, to: String): List<Position> {
        val cacheKey = "$deviceId:$from:$to"
        routeCache.get(cacheKey)?.let { return it }

        val result = if (isDemo()) {
            // Generate rich mock route coordinates for historical playback & reports
            delay(500)
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            val list = mutableListOf<Position>()
            val startLat = 37.7749
            val startLng = -122.4194
            val fromDate = try { format.parse(from) } catch (_: Exception) { Date(System.currentTimeMillis() - 24 * 3600 * 1000L) }
            val toDate = try { format.parse(to) } catch (_: Exception) { Date() }
            val durationHours = ((toDate?.time ?: System.currentTimeMillis()) - (fromDate?.time ?: (System.currentTimeMillis() - 24 * 3600 * 1000L))) / (1000 * 3600.0)
            val count = when {
                durationHours > 200 -> 75 // Monthly
                durationHours > 48 -> 45  // Weekly
                else -> 28               // Today
            }
            val stepMs = ((toDate?.time ?: System.currentTimeMillis()) - (fromDate?.time ?: 0L)) / count
            
            for (i in 0 until count) {
                val lat = startLat + (i * 0.0028) * if (deviceId % 2 == 0L) 1 else -1
                val lng = startLng + (i * 0.0042)
                val date = Date((fromDate?.time ?: System.currentTimeMillis()) + (i * stepMs))
                val timeString = format.format(date)
                val currentSpdKnots = if (i % 6 == 0) 0.0 else (28.0 + (i % 5) * 4.5)
                
                list.add(
                    Position(
                        id = (10000 + i).toLong(),
                        deviceId = deviceId,
                        protocol = "osmand",
                        deviceTime = timeString,
                        fixTime = timeString,
                        latitude = lat,
                        longitude = lng,
                        altitude = 45.0,
                        speed = currentSpdKnots,
                        course = (45.0 + (i * 12)) % 360,
                        address = "Highway 101 corridor, Marker ${i + 1}",
                        accuracy = 4.0,
                        attributes = mapOf(
                            "ign" to (currentSpdKnots > 0),
                            "voltage" to 13.2,
                            "distance" to (i * 1850.0),
                            "totalDistance" to (i * 1850.0)
                        )
                    )
                )
            }
            list
        } else {
            val api = traccarApi ?: return emptyList()
            val raw = try {
                api.getRouteReport(deviceId, from, to)
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401 || e.code() == 403 || e.code() == 404) {
                    try {
                        api.getPositions(deviceId, from, to)
                    } catch (e2: Exception) {
                        Log.w(TAG, "getPositions fallback failed: ${e2.message}")
                        emptyList()
                    }
                } else {
                    Log.w(TAG, "getRouteReport failed with code ${e.code()}: ${e.message}")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.w(TAG, "getRouteHistory error: ${e.message}")
                try {
                    api.getPositions(deviceId, from, to)
                } catch (e2: Exception) {
                    emptyList()
                }
            }
            com.example.util.PositionSanitizer.sanitize(raw)
        }
        if (result.isNotEmpty()) {
            routeCache.put(cacheKey, result)
        }
        return result
    }

    suspend fun getServer(): Server? {
        if (isDemo()) {
            return Server(
                id = 1,
                registration = false,
                readonly = false,
                deviceReadonly = false,
                map = "custom",
                latitude = 37.7749,
                longitude = -122.4194,
                zoom = 12,
                version = "6.5"
            )
        } else {
            val api = traccarApi ?: return null
            return try {
                api.getServer()
            } catch (e: Exception) {
                Log.w(TAG, "getServer error: ${e.message}")
                null
            }
        }
    }

    suspend fun getDrivers(): List<Driver> {
        if (isDemo()) {
            return listOf(
                Driver(id = 1, name = "Abebe Bekele", uniqueId = "DRV-01"),
                Driver(id = 2, name = "Dawit Haile", uniqueId = "DRV-02"),
                Driver(id = 3, name = "Michael Tadesse", uniqueId = "DRV-03")
            )
        } else {
            val api = traccarApi ?: return emptyList()
            return try {
                api.getDrivers()
            } catch (e: Exception) {
                Log.w(TAG, "getDrivers error: ${e.message}")
                emptyList()
            }
        }
    }

    suspend fun getGroups(): List<Group> {
        if (isDemo()) {
            return listOf(
                Group(id = 1, name = "Heavy Haul Division"),
                Group(id = 2, name = "Urban Express Couriers")
            )
        } else {
            val api = traccarApi ?: return emptyList()
            return try {
                api.getGroups()
            } catch (e: Exception) {
                Log.w(TAG, "getGroups error: ${e.message}")
                emptyList()
            }
        }
    }


    // GEOFENCE NETWORK MANAGERS OR SANDBOX FALLBACKS
    private val simulatedGeofences = mutableListOf<TraccarGeofence>()

    private fun initPredefinedGeofences() {
        if (simulatedGeofences.isNotEmpty()) return
        simulatedGeofences.addAll(
            listOf(
                TraccarGeofence(
                    id = 101,
                    name = "SF Logistics & Central Depot",
                    description = "Primary freight distribution and staging hub for interstate heavy trucks.",
                    area = "POLYGON ((37.7810 -122.4260, 37.7810 -122.4110, 37.7670 -122.4110, 37.7670 -122.4260, 37.7810 -122.4260))",
                    attributes = mapOf("deviceId" to 1L, "color" to "#3B82F6", "speedLimit" to 50)
                ),
                TraccarGeofence(
                    id = 102,
                    name = "Mission Bay Transit Corridor",
                    description = "Designated high-capacity transit corridor connecting to central docks.",
                    area = "POLYGON ((37.7790 -122.4020, 37.7790 -122.3860, 37.7640 -122.3860, 37.7640 -122.4020, 37.7790 -122.4020))",
                    attributes = mapOf("deviceId" to 1L, "color" to "#60A5FA", "speedLimit" to 65)
                ),
                TraccarGeofence(
                    id = 201,
                    name = "Commercial Delivery Hub Alpha",
                    description = "High-density retail delivery and package fulfillment operating perimeter.",
                    area = "POLYGON ((37.7670 -122.4440, 37.7670 -122.4270, 37.7510 -122.4270, 37.7510 -122.4440, 37.7670 -122.4440))",
                    attributes = mapOf("deviceId" to 2L, "color" to "#10B981", "speedLimit" to 40)
                ),
                TraccarGeofence(
                    id = 202,
                    name = "Twin Peaks Transit Zone",
                    description = "Monitored secondary hill distribution zone for local vans.",
                    area = "POLYGON ((37.7610 -122.4560, 37.7610 -122.4390, 37.7440 -122.4390, 37.7440 -122.4560, 37.7610 -122.4560))",
                    attributes = mapOf("deviceId" to 2L, "color" to "#34D399", "speedLimit" to 45)
                ),
                TraccarGeofence(
                    id = 301,
                    name = "Embarcadero Container Terminal",
                    description = "Port maritime container staging and asset yard perimeter.",
                    area = "POLYGON ((37.8110 -122.4160, 37.8110 -122.3940, 37.7940 -122.3940, 37.7940 -122.4160, 37.8110 -122.4160))",
                    attributes = mapOf("deviceId" to 3L, "color" to "#8B5CF6", "speedLimit" to 30)
                ),
                TraccarGeofence(
                    id = 302,
                    name = "Fisherman's Wharf Port Perimeter",
                    description = "Secure northern waterfront asset holding zone.",
                    area = "POLYGON ((37.8130 -122.4230, 37.8130 -122.4070, 37.7990 -122.4070, 37.7990 -122.4230, 37.8130 -122.4230))",
                    attributes = mapOf("deviceId" to 3L, "color" to "#A78BFA", "speedLimit" to 25)
                ),
                TraccarGeofence(
                    id = 401,
                    name = "Ocean Ave Commercial District",
                    description = "Client visiting and enterprise sales representative zone.",
                    area = "POLYGON ((37.7470 -122.4590, 37.7470 -122.4390, 37.7310 -122.4390, 37.7310 -122.4590, 37.7470 -122.4590))",
                    attributes = mapOf("deviceId" to 4L, "color" to "#F59E0B", "speedLimit" to 55)
                ),
                TraccarGeofence(
                    id = 402,
                    name = "Balboa Express Gateway",
                    description = "Southern freeway corridor entrance boundary.",
                    area = "POLYGON ((37.7390 -122.4510, 37.7390 -122.4340, 37.7210 -122.4340, 37.7210 -122.4510, 37.7390 -122.4510))",
                    attributes = mapOf("deviceId" to 4L, "color" to "#FBBF24", "speedLimit" to 65)
                ),
                TraccarGeofence(
                    id = 501,
                    name = "Metropolitan Operating Boundary",
                    description = "Universal fleet operational perimeter encompassing the San Francisco metro area.",
                    area = "POLYGON ((37.8200 -122.4650, 37.8200 -122.3780, 37.7100 -122.3780, 37.7100 -122.4650, 37.8200 -122.4650))",
                    attributes = mapOf("color" to "#06B6D4", "speedLimit" to 80)
                )
            )
        )
    }

    suspend fun getGeofences(deviceId: Long? = null): List<TraccarGeofence> {
        initPredefinedGeofences()
        if (isDemo()) {
            delay(150)
            return if (deviceId != null) {
                simulatedGeofences.filter { gf ->
                    val targetDevId = (gf.attributes["deviceId"] as? Number)?.toLong()
                    targetDevId == null || targetDevId == deviceId
                }
            } else {
                simulatedGeofences.toList()
            }
        } else {
            val api = traccarApi ?: throw IllegalStateException("API not configured")
            return try {
                val serverList = api.getGeofences(deviceId = deviceId)
                if (serverList.isNotEmpty()) {
                    serverList
                } else if (deviceId != null) {
                    // Fallback to predefined for demo/sandbox devices if server has none
                    simulatedGeofences.filter { gf ->
                        val targetDevId = (gf.attributes["deviceId"] as? Number)?.toLong()
                        targetDevId == null || targetDevId == deviceId
                    }
                } else {
                    simulatedGeofences.toList()
                }
            } catch (e: Exception) {
                simulatedGeofences.toList()
            }
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
                val intervalSeconds = sessionManager.positionUpdateInterval
                delay(intervalSeconds * 1000L) // Dynamically configured position update interval

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
            }
        }
    }

    private fun stopSandboxSimulation() {
        isSimulating = false
    }
}
