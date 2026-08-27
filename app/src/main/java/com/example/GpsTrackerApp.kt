package com.example

import android.app.Application
import com.example.util.CrashLogger

class GpsTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Global Crash Logger & Diagnostic Interceptor
        CrashLogger.init(this)
        CrashLogger.log("GpsTrackerApp", "Application initialized successfully.")
    }
}
