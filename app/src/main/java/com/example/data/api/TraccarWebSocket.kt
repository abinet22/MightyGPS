package com.example.data.api

import android.util.Log
import com.example.data.model.SocketUpdate
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.*
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

    private val _updates = MutableSharedFlow<SocketUpdate>(extraBufferCapacity = 64)
    val updates: SharedFlow<SocketUpdate> = _updates.asSharedFlow()

    private val _connectionState = MutableSharedFlow<Boolean>(replay = 1)
    val connectionState: SharedFlow<Boolean> = _connectionState.asSharedFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var failedAttempts = 0

    fun connect() {
        disconnect()

        val credential = credentialsProvider()
        val basicToken = if (credential != null) {
            Credentials.basic(credential.first, credential.second)
        } else null

        // Clean any protocol prefixes from the serverUrl input
        val cleanUrl = serverUrl
            .replace("https://", "")
            .replace("http://", "")
            .replace("wss://", "")
            .replace("ws://", "")
            .trim()

        val appendPath = if (cleanUrl.endsWith("/")) "api/socket" else "/api/socket"
        
        // Alternate protocol on failures to guarantee compatibility
        // Attempt secure 'wss' first (especially needed in newer Android API targets)
        val isSecure = if (failedAttempts % 2 == 0) {
            true
        } else {
            false
        }
        
        val protocol = if (isSecure) "wss://" else "ws://"
        val finalSocketUrl = "$protocol$cleanUrl$appendPath"

        Log.d(TAG, "Connecting to WebSocket (attempt ${failedAttempts + 1}): $finalSocketUrl")

        val clientInterceptor = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS) // Disable timeouts for permanent socket
            .pingInterval(15, java.util.concurrent.TimeUnit.SECONDS) // Send keep-alive ping frame every 15s to prevent disconnection
            .cookieJar(TraccarCookieJar)
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
                Log.d(TAG, "WebSocket Opened successfully")
                failedAttempts = 0 // Reset attempt counter on success!
                coroutineScope.launch {
                    _connectionState.emit(true)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.v(TAG, "WebSocket received payload: $text")
                coroutineScope.launch {
                    try {
                        val parsed = socketUpdateAdapter.fromJson(text)
                        if (parsed != null) {
                            _updates.emit(parsed)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse WebSocket message JSON: ${e.message}")
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket Closed: $reason ($code)")
                coroutineScope.launch {
                    _connectionState.emit(false)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Error: ${t.message}")
                failedAttempts++
                coroutineScope.launch {
                    _connectionState.emit(false)
                }
                // Try to reconnect in 5 seconds
                coroutineScope.launch {
                    kotlinx.coroutines.delay(5000)
                    Log.d(TAG, "Reconnecting WebSocket (attempt $failedAttempts)...")
                    connect()
                }
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "User logout / context reset")
        webSocket = null
        client = null
        coroutineScope.launch {
            _connectionState.emit(false)
        }
    }
}
