package com.example.util

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.ui.CrashReportActivity
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

object CrashLogger {
    private const val TAG = "CrashLogger"
    private const val PREFS_NAME = "mighty_gps_crash_logs"
    private const val KEY_LAST_CRASH = "key_last_crash_text"
    private const val KEY_CRASH_TIMESTAMP = "key_crash_timestamp"

    private val runtimeLogBuffer = ConcurrentLinkedQueue<String>()
    private const val MAX_BUFFER_LOGS = 60

    fun log(tag: String, message: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = "[$time] [$tag] $message"
        Log.i(tag, message)
        runtimeLogBuffer.add(entry)
        while (runtimeLogBuffer.size > MAX_BUFFER_LOGS) {
            runtimeLogBuffer.poll()
        }
    }

    fun init(application: Application) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "Uncaught Exception caught by CrashLogger on thread: ${thread.name}", throwable)
                val crashReport = buildCrashReport(application, thread, throwable)
                saveCrashLog(application, crashReport)

                // Launch standalone CrashReportActivity
                val intent = Intent(application, CrashReportActivity::class.java).apply {
                    putExtra(CrashReportActivity.EXTRA_CRASH_LOG, crashReport)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                application.startActivity(intent)

                // Give activity a moment to launch, then kill current crashed process
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch crash activity: ${e.message}", e)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun buildCrashReport(context: Context, thread: Thread?, throwable: Throwable?): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable?.printStackTrace(pw)
        val stackTrace = sw.toString()

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
        val currentTime = sdf.format(Date())

        val pInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }

        val versionName = pInfo?.versionName ?: "Unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pInfo?.longVersionCode ?: -1L
        } else {
            @Suppress("DEPRECATION")
            pInfo?.versionCode?.toLong() ?: -1L
        }

        val sb = StringBuilder()
        sb.appendLine("==================================================")
        sb.appendLine("🚨 MIGHTY GPS APP CRASH REPORT")
        sb.appendLine("==================================================")
        sb.appendLine("Timestamp: $currentTime")
        sb.appendLine("App Version: $versionName (Build $versionCode)")
        sb.appendLine("Device Manufacturer: ${Build.MANUFACTURER}")
        sb.appendLine("Device Brand: ${Build.BRAND}")
        sb.appendLine("Device Model: ${Build.MODEL}")
        sb.appendLine("Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Thread: ${thread?.name ?: "Main/Unknown"}")
        sb.appendLine("Error Type: ${throwable?.javaClass?.name ?: "Unknown Error"}")
        sb.appendLine("Error Message: ${throwable?.message ?: "No error message"}")
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("📋 STACK TRACE:")
        sb.appendLine(stackTrace.ifBlank { "No stack trace available." })
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("📝 RECENT IN-APP RUNTIME LOGS:")
        val logs = runtimeLogBuffer.toList()
        if (logs.isEmpty()) {
            sb.appendLine("(No runtime logs recorded prior to crash)")
        } else {
            logs.takeLast(30).forEach { sb.appendLine(it) }
        }
        sb.appendLine("==================================================")
        return sb.toString()
    }

    fun saveCrashLog(context: Context, logText: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_LAST_CRASH, logText)
                .putLong(KEY_CRASH_TIMESTAMP, System.currentTimeMillis())
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash log to preferences", e)
        }
    }

    fun getSavedCrashLog(context: Context): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_LAST_CRASH, null)
        } catch (e: Exception) {
            null
        }
    }

    fun clearSavedCrashLog(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_LAST_CRASH).remove(KEY_CRASH_TIMESTAMP).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear crash log", e)
        }
    }

    fun getLiveDiagnostics(context: Context): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val currentTime = sdf.format(Date())

        val pInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }

        val versionName = pInfo?.versionName ?: "Unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pInfo?.longVersionCode ?: -1L
        } else {
            @Suppress("DEPRECATION")
            pInfo?.versionCode?.toLong() ?: -1L
        }

        val sb = StringBuilder()
        sb.appendLine("==================================================")
        sb.appendLine("📱 MIGHTY GPS SYSTEM & RUNTIME DIAGNOSTICS")
        sb.appendLine("==================================================")
        sb.appendLine("Status: Healthy / Live")
        sb.appendLine("Timestamp: $currentTime")
        sb.appendLine("App Version: $versionName (Build $versionCode)")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.BRAND})")
        sb.appendLine("Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("📝 RECENT IN-APP RUNTIME LOGS:")
        val logs = runtimeLogBuffer.toList()
        if (logs.isEmpty()) {
            sb.appendLine("(No runtime logs recorded yet)")
        } else {
            logs.takeLast(40).forEach { sb.appendLine(it) }
        }
        sb.appendLine("==================================================")
        return sb.toString()
    }
}
