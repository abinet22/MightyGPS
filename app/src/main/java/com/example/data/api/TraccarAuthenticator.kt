package com.example.data.api

import com.example.data.pref.SessionManager
import okhttp3.*

class TraccarAuthenticator(
    private val sessionManager: SessionManager,
    private val reauth: suspend (String, String) -> Boolean // returns true if session refreshed
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Avoid infinite retry loops
        if (responseCount(response) >= 2) return null

        // Avoid re-authenticating if the login/session endpoint itself returned 401
        val path = response.request.url.encodedPath
        if (path.contains("api/session")) return null

        val email = sessionManager.email
        val password = sessionManager.password
        if (email.isBlank() || password.isBlank()) return null

        val refreshed = kotlinx.coroutines.runBlocking {
            try {
                reauth(email, password)
            } catch (e: Exception) {
                false
            }
        }
        if (!refreshed) return null

        return response.request.newBuilder()
            .header("Authorization", Credentials.basic(email, password))
            .build()
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
