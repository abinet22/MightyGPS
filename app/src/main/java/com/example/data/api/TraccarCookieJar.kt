package com.example.data.api

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

object TraccarCookieJar : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, MutableMap<String, Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isNotEmpty()) {
            val hostCookies = cookieStore.getOrPut(url.host) { ConcurrentHashMap() }
            for (cookie in cookies) {
                hostCookies[cookie.name] = cookie
            }
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
            if (cookie.expiresAt > now) {
                validCookies.add(cookie)
            } else {
                iterator.remove()
            }
        }
        return validCookies
    }

    fun clear() {
        cookieStore.clear()
    }
}
