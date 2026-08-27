package com.example

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.CrashReportActivity
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.TraccarViewModel
import com.example.util.CrashLogger

class TraccarViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TraccarViewModel::class.java)) {
            return TraccarViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.log("MainActivity", "onCreate started")

        try {
            enableEdgeToEdge()
            
            // Initialize Notification Channel for Geofence Alerts
            try {
                com.example.util.NotificationHelper.createNotificationChannel(this)
                CrashLogger.log("MainActivity", "Notification channel initialized")
            } catch (e: Exception) {
                CrashLogger.log("MainActivity", "Notification channel warning: ${e.message}")
            }

            setContent {
                MyApplicationTheme {
                    val navController = rememberNavController()
                    val traccarViewModel: TraccarViewModel = viewModel(
                        factory = TraccarViewModelFactory(application)
                    )

                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        // Set up core multi-tenant navigation graph
                        NavHost(
                            navController = navController,
                            startDestination = if (traccarViewModel.sessionManager.isLoggedIn) "dashboard" else "login",
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable("login") {
                                LoginScreen(
                                    viewModel = traccarViewModel,
                                    onLoginSuccess = {
                                        navController.navigate("dashboard") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }
                                )
                            }
                            
                            composable("dashboard") {
                                DashboardScreen(
                                    viewModel = traccarViewModel,
                                    onLogout = {
                                        navController.navigate("login") {
                                            popUpTo("dashboard") { inclusive = true }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            CrashLogger.log("MainActivity", "setContent configured successfully")
        } catch (e: Throwable) {
            CrashLogger.log("MainActivity", "Fatal error in onCreate: ${e.message}")
            val report = CrashLogger.buildCrashReport(this, Thread.currentThread(), e)
            CrashLogger.saveCrashLog(this, report)
            val intent = Intent(this, CrashReportActivity::class.java).apply {
                putExtra(CrashReportActivity.EXTRA_CRASH_LOG, report)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            finish()
        }
    }
}
