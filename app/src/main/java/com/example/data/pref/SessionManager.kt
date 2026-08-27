package com.example.data.pref

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("saas_gps_tracker_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_IS_ADMIN = "is_admin"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_ID = "user_id"
        
        // Customization keys
        private const val KEY_LANGUAGE = "custom_language"
        private const val KEY_MAP_PROVIDER_STYLE = "custom_map_provider_style"
        private const val KEY_MARKER_LABEL_STYLE = "custom_marker_label_style"
        private const val KEY_MARKER_ICON_STYLE = "custom_marker_icon_style"
        private const val KEY_CUSTOM_ICON_URI = "custom_icon_uri"
        private const val KEY_POSITION_UPDATE_INTERVAL = "custom_position_update_interval"
        private const val KEY_COLOR_MOVING = "custom_color_moving"
        private const val KEY_COLOR_IDLE = "custom_color_idle"
        private const val KEY_COLOR_OFFLINE = "custom_color_offline"
        private const val KEY_MARKER_TRIGGER_MODE = "custom_marker_trigger_mode"
        private const val KEY_INFO_CARD_FIELDS = "custom_info_card_fields"
    }

    var serverUrl: String
        get() = try { prefs.getString(KEY_SERVER_URL, "") ?: "" } catch (_: Exception) { "" }
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var email: String
        get() = try { prefs.getString(KEY_EMAIL, "") ?: "" } catch (_: Exception) { "" }
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    var password: String
        get() = try { prefs.getString(KEY_PASSWORD, "") ?: "" } catch (_: Exception) { "" }
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var isLoggedIn: Boolean
        get() = try { prefs.getBoolean(KEY_IS_LOGGED_IN, false) } catch (_: Exception) { false }
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var isAdmin: Boolean
        get() = try { prefs.getBoolean(KEY_IS_ADMIN, false) } catch (_: Exception) { false }
        set(value) = prefs.edit().putBoolean(KEY_IS_ADMIN, value).apply()

    var userName: String
        get() = try { prefs.getString(KEY_USER_NAME, "") ?: "" } catch (_: Exception) { "" }
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var userId: Long
        get() = try {
            prefs.getLong(KEY_USER_ID, -1L)
        } catch (_: Exception) {
            try {
                prefs.getString(KEY_USER_ID, null)?.toLongOrNull() ?: -1L
            } catch (_: Exception) {
                -1L
            }
        }
        set(value) = prefs.edit().putLong(KEY_USER_ID, value).apply()

    // Interactive customization variables
    var language: String
        get() = try { prefs.getString(KEY_LANGUAGE, "en") ?: "en" } catch (_: Exception) { "en" }
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var mapProviderStyle: String
        get() = try { prefs.getString(KEY_MAP_PROVIDER_STYLE, "google_road") ?: "google_road" } catch (_: Exception) { "google_road" }
        set(value) = prefs.edit().putString(KEY_MAP_PROVIDER_STYLE, value).apply()

    var markerLabelStyle: String
        get() = try { prefs.getString(KEY_MARKER_LABEL_STYLE, "name") ?: "name" } catch (_: Exception) { "name" }
        set(value) = prefs.edit().putString(KEY_MARKER_LABEL_STYLE, value).apply()

    var markerIconStyle: String
        get() = try { prefs.getString(KEY_MARKER_ICON_STYLE, "car") ?: "car" } catch (_: Exception) { "car" }
        set(value) = prefs.edit().putString(KEY_MARKER_ICON_STYLE, value).apply()

    var customIconUri: String?
        get() = try { prefs.getString(KEY_CUSTOM_ICON_URI, null) } catch (_: Exception) { null }
        set(value) = prefs.edit().putString(KEY_CUSTOM_ICON_URI, value).apply()

    var positionUpdateInterval: Int
        get() = try {
            prefs.getInt(KEY_POSITION_UPDATE_INTERVAL, 300)
        } catch (_: Exception) {
            try {
                prefs.getString(KEY_POSITION_UPDATE_INTERVAL, null)?.toIntOrNull() ?: 300
            } catch (_: Exception) {
                300
            }
        }
        set(value) = prefs.edit().putInt(KEY_POSITION_UPDATE_INTERVAL, value).apply()

    var colorMoving: String
        get() = try { prefs.getString(KEY_COLOR_MOVING, "#10B981") ?: "#10B981" } catch (_: Exception) { "#10B981" }
        set(value) = prefs.edit().putString(KEY_COLOR_MOVING, value).apply()

    var colorIdle: String
        get() = try { prefs.getString(KEY_COLOR_IDLE, "#F59E0B") ?: "#F59E0B" } catch (_: Exception) { "#F59E0B" }
        set(value) = prefs.edit().putString(KEY_COLOR_IDLE, value).apply()

    var colorOffline: String
        get() = try { prefs.getString(KEY_COLOR_OFFLINE, "#EF4444") ?: "#EF4444" } catch (_: Exception) { "#EF4444" }
        set(value) = prefs.edit().putString(KEY_COLOR_OFFLINE, value).apply()

    var markerTriggerMode: String
        get() = try { prefs.getString(KEY_MARKER_TRIGGER_MODE, "click") ?: "click" } catch (_: Exception) { "click" }
        set(value) = prefs.edit().putString(KEY_MARKER_TRIGGER_MODE, value).apply()

    var infoCardFields: String
        get() = try {
            prefs.getString(KEY_INFO_CARD_FIELDS, "name,speed,driver,lastUpdate,address,battery,odometer,ignition") 
                ?: "name,speed,driver,lastUpdate,address,battery,odometer,ignition"
        } catch (_: Exception) {
            "name,speed,driver,lastUpdate,address,battery,odometer,ignition"
        }
        set(value) = prefs.edit().putString(KEY_INFO_CARD_FIELDS, value).apply()

    fun logout() {
        prefs.edit().clear().apply()
    }
}
