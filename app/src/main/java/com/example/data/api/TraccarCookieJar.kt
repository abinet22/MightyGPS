package com.example.data.api

import android.content.Context
import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

object TraccarCookieJar : CookieJar {
    private const val TAG = "TraccarCookieJar"
    private val cookieStore = ConcurrentHashMap<String, ConcurrentHashMap<String, Cookie>>()
    private var prefs: android.content.SharedPreferences? = null

    /**
     * Initializes the persistent cookie jar with device context
     */
    fun init(context: Context) {
        if (prefs == null) {
            Log.d(TAG, "Initializing persistent Traccar cookies from Shared Preferences")
            prefs = context.applicationContext.getSharedPreferences("traccar_cookies_prefs", Context.MODE_PRIVATE)
            loadFromPreferences()
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isNotEmpty()) {
            val hostCookies = cookieStore.getOrPut(url.host) { ConcurrentHashMap() }
            for (cookie in cookies) {
                hostCookies[cookie.name] = cookie
            }
            saveToPreferences()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val hostCookies = cookieStore[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        
        val validCookies = mutableListOf<Cookie>()
        val iterator = hostCookies.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val cookie = entry.value
            // Check if cookie is still valid and not expired (expiresAt <= 0L indicates session cookie)
            if (cookie.expiresAt <= 0L || cookie.expiresAt > now) {
                validCookies.add(cookie)
            } else {
                iterator.remove()
            }
        }
        return validCookies
    }

    /**
     * Clear all persistent and in-memory cookies on logout
     */
    fun clear() {
        Log.d(TAG, "Clearing all cookies on user request / logout")
        cookieStore.clear()
        prefs?.edit()?.clear()?.apply()
    }

    private fun saveToPreferences() {
        val p = prefs ?: return
        val editor = p.edit()
        editor.clear()
        for ((host, cookies) in cookieStore) {
            val serializedSet = cookies.values.map { serializeCookie(it) }.toSet()
            editor.putStringSet(host, serializedSet)
        }
        editor.apply()
    }

    private fun loadFromPreferences() {
        val p = prefs ?: return
        cookieStore.clear()
        val allEntries = p.all
        for ((host, serializedSetRaw) in allEntries) {
            if (serializedSetRaw is Set<*>) {
                val hostCookies = ConcurrentHashMap<String, Cookie>()
                for (item in serializedSetRaw) {
                    if (item is String) {
                        val cookie = deserializeCookie(item)
                        if (cookie != null) {
                            hostCookies[cookie.name] = cookie
                        }
                    }
                }
                cookieStore[host] = hostCookies
            }
        }
    }

    private fun serializeCookie(cookie: Cookie): String {
        return StringBuilder()
            .append(cookie.name).append("|")
            .append(cookie.value).append("|")
            .append(cookie.expiresAt).append("|")
            .append(cookie.domain).append("|")
            .append(cookie.path).append("|")
            .append(if (cookie.secure) "1" else "0").append("|")
            .append(if (cookie.httpOnly) "1" else "0").append("|")
            .append(if (cookie.hostOnly) "1" else "0")
            .toString()
    }

    private fun deserializeCookie(serialized: String): Cookie? {
        return try {
            val parts = serialized.split("|")
            if (parts.size < 8) return null
            val name = parts[0]
            val value = parts[1]
            val expiresAt = parts[2].toLongOrNull() ?: 0L
            val domain = parts[3]
            val path = parts[4]
            val secure = parts[5] == "1"
            val httpOnly = parts[6] == "1"
            val hostOnly = parts[7] == "1"

            val builder = Cookie.Builder()
                .name(name)
                .value(value)
                .expiresAt(expiresAt)
                .path(path)
            
            if (hostOnly) {
                builder.hostOnlyDomain(domain)
            } else {
                builder.domain(domain)
            }
            if (secure) builder.secure()
            if (httpOnly) builder.httpOnly()
            builder.build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize cookie: ${e.message}")
            null
        }
    }
}
