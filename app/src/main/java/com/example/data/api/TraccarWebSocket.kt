package com.example.data.api

import android.util.Log
import com.example.data.model.Device
import com.example.data.model.Event
import com.example.data.model.Position
import com.example.data.model.SocketUpdate
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

class TraccarWebSocket(
    private val serverUrl: String,
    private val credentialsProvider: () -> Pair<String, String>?
) {
    private val TAG = "TraccarWebSocket"
    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    
    private val moshi = Moshi.Builder()
        .add(DeviceAdapter())
        .add(PositionAdapter())
        .add(UserAdapter())
        .add(EventAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()
        
    private val socketUpdateAdapter = moshi.adapter(SocketUpdate::class.java)
    private val positionListAdapter = moshi.adapter<List<Position>>(
        Types.newParameterizedType(List::class.java, Position::class.java)
    )
    private val singlePositionAdapter = moshi.adapter(Position::class.java)
    private val singleDeviceAdapter = moshi.adapter(Device::class.java)

    private val _updates = MutableSharedFlow<SocketUpdate>(extraBufferCapacity = 128)
    val updates: SharedFlow<SocketUpdate> = _updates.asSharedFlow()

    private val _connectionState = MutableSharedFlow<Boolean>(replay = 1)
    val connectionState: SharedFlow<Boolean> = _connectionState.asSharedFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null
    private var failedAttempts = 0
    private var isExplicitlyDisconnected = false

    fun connect() {
        isExplicitlyDisconnected = false
        reconnectJob?.cancel()
        disconnectInternal()

        val credential = credentialsProvider()
        val basicToken = if (credential != null) {
            Credentials.basic(credential.first, credential.second)
        } else null

        val isHttps = serverUrl.trim().startsWith("https://", ignoreCase = true)
        val cleanUrl = serverUrl
            .replace("https://", "", ignoreCase = true)
            .replace("http://", "", ignoreCase = true)
            .replace("wss://", "", ignoreCase = true)
            .replace("ws://", "", ignoreCase = true)
            .trim()

        if (cleanUrl.isBlank() || cleanUrl.equals("DEMO", ignoreCase = true)) {
            coroutineScope.launch { _connectionState.emit(false) }
            return
        }

        val appendPath = if (cleanUrl.endsWith("/")) "api/socket" else "/api/socket"
        val protocol = if (isHttps) "wss://" else "ws://"
        val finalSocketUrl = "$protocol$cleanUrl$appendPath"

        Log.d(TAG, "Connecting to Traccar WebSocket: $finalSocketUrl")

        val clientInterceptor = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .cookieJar(TraccarCookieJar)
            .retryOnConnectionFailure(true)
            .build()
        client = clientInterceptor

        val requestBuilder = Request.Builder()
            .url(finalSocketUrl)
            .header("Accept", "application/json")
        
        if (basicToken != null) {
            requestBuilder.header("Authorization", basicToken)
        }

        val request = requestBuilder.build()
        
        webSocket = client?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Traccar WebSocket connection established successfully (HTTP ${response.code})")
                failedAttempts = 0
                coroutineScope.launch {
                    _connectionState.emit(true)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseAndEmitPayload(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val text = bytes.utf8()
                parseAndEmitPayload(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Traccar WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Traccar WebSocket Closed: $reason ($code)")
                coroutineScope.launch {
                    _connectionState.emit(false)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val errorMsg = t.message ?: (response?.let { "HTTP ${it.code} ${it.message}" } ?: "Unknown socket error")
                val isProxyHttp200 = errorMsg.contains("200 OK", ignoreCase = true)
                
                if (failedAttempts < 2) {
                    Log.w(TAG, "WebSocket connection notice: $errorMsg. REST fallback active.")
                } else {
                    Log.d(TAG, "WebSocket offline: $errorMsg")
                }
                failedAttempts++
                coroutineScope.launch {
                    _connectionState.emit(false)
                }

                if (!isExplicitlyDisconnected) {
                    reconnectJob?.cancel()
                    val backoffSeconds = when {
                        isProxyHttp200 -> 120L
                        failedAttempts <= 1 -> 5L
                        failedAttempts == 2 -> 15L
                        failedAttempts == 3 -> 30L
                        else -> 60L
                    }
                    reconnectJob = coroutineScope.launch {
                        delay(backoffSeconds * 1000L)
                        if (!isExplicitlyDisconnected) {
                            Log.d(TAG, "Attempting WebSocket reconnect after ${backoffSeconds}s...")
                            connect()
                        }
                    }
                }
            }
        })
    }

    private fun parseAndEmitPayload(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        
        coroutineScope.launch {
            try {
                if (trimmed.startsWith("{")) {
                    // Try primary SocketUpdate standard JSON structure
                    val update = socketUpdateAdapter.fromJson(trimmed)
                    if (update != null && (update.positions != null || update.devices != null || update.events != null)) {
                        _updates.emit(update)
                        return@launch
                    }
                    
                    // Try single Position object fallback
                    try {
                        val singlePos = singlePositionAdapter.fromJson(trimmed)
                        if (singlePos != null && (singlePos.latitude != 0.0 || singlePos.longitude != 0.0)) {
                            _updates.emit(SocketUpdate(positions = listOf(singlePos)))
                            return@launch
                        }
                    } catch (_: Exception) {}

                    // Try single Device object fallback
                    try {
                        val singleDev = singleDeviceAdapter.fromJson(trimmed)
                        if (singleDev != null && singleDev.id != 0L) {
                            _updates.emit(SocketUpdate(devices = listOf(singleDev)))
                            return@launch
                        }
                    } catch (_: Exception) {}
                } else if (trimmed.startsWith("[")) {
                    // Array of positions fallback
                    try {
                        val posList = positionListAdapter.fromJson(trimmed)
                        if (!posList.isNullOrEmpty()) {
                            _updates.emit(SocketUpdate(positions = posList))
                            return@launch
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse Traccar WebSocket payload: ${e.message}")
            }
        }
    }

    private fun disconnectInternal() {
        try {
            webSocket?.close(1000, "Reset")
        } catch (e: Exception) {
            // ignore
        }
        webSocket = null
        client = null
    }

    fun disconnect() {
        isExplicitlyDisconnected = true
        reconnectJob?.cancel()
        disconnectInternal()
        coroutineScope.launch {
            _connectionState.emit(false)
        }
    }
}
