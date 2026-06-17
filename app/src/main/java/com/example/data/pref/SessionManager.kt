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
    }

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var email: String
        get() = prefs.getString(KEY_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var isAdmin: Boolean
        get() = prefs.getBoolean(KEY_IS_ADMIN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_ADMIN, value).apply()

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var userId: Long
        get() = prefs.getLong(KEY_USER_ID, -1L)
        set(value) = prefs.edit().putLong(KEY_USER_ID, value).apply()

    // Interactive customization variables
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var mapProviderStyle: String
        get() = prefs.getString(KEY_MAP_PROVIDER_STYLE, "mapbox_dark") ?: "mapbox_dark"
        set(value) = prefs.edit().putString(KEY_MAP_PROVIDER_STYLE, value).apply()

    var markerLabelStyle: String
        get() = prefs.getString(KEY_MARKER_LABEL_STYLE, "name") ?: "name"
        set(value) = prefs.edit().putString(KEY_MARKER_LABEL_STYLE, value).apply()

    var markerIconStyle: String
        get() = prefs.getString(KEY_MARKER_ICON_STYLE, "car") ?: "car"
        set(value) = prefs.edit().putString(KEY_MARKER_ICON_STYLE, value).apply()

    var customIconUri: String?
        get() = prefs.getString(KEY_CUSTOM_ICON_URI, null)
        set(value) = prefs.edit().putString(KEY_CUSTOM_ICON_URI, value).apply()

    fun logout() {
        prefs.edit().clear().apply()
    }
}
