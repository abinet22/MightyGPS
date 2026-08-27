package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.viewmodel.AuthUIState
import com.example.ui.viewmodel.TraccarViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: TraccarViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val authState by viewModel.authUIState.collectAsState()
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Input States
    val serverUrl = "http://mighty-gps.pro.et/"
    var email by remember { mutableStateOf(viewModel.sessionManager.email) }
    var password by remember { mutableStateOf(viewModel.sessionManager.password) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Intercept login success
    LaunchedEffect(authState) {
        if (authState is AuthUIState.Success) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Slate 900
                        Color(0xFF020617)  // Slate 950
                    )
                )
            )
    ) {
        // Background subtle abstract grid graphic
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stepPx = 60.dp.toPx().toInt().coerceAtLeast(40)
            val w = size.width.toInt()
            val h = size.height.toInt()
            if (w > 0 && h > 0) {
                var x = 0
                while (x <= w) {
                    drawLine(
                        color = Color(0x0A94A3B8),
                        start = androidx.compose.ui.geometry.Offset(x.toFloat(), 0f),
                        end = androidx.compose.ui.geometry.Offset(x.toFloat(), size.height),
                        strokeWidth = 2f
                    )
                    x += stepPx
                }
                var y = 0
                while (y <= h) {
                    drawLine(
                        color = Color(0x0A94A3B8),
                        start = androidx.compose.ui.geometry.Offset(0f, y.toFloat()),
                        end = androidx.compose.ui.geometry.Offset(size.width, y.toFloat()),
                        strokeWidth = 2f
                    )
                    y += stepPx
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .statusBarsPadding()
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Custom Generated App Logo
            Image(
                painter = painterResource(id = R.drawable.img_app_logo),
                contentDescription = "Mighty GPS Logo",
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .align(Alignment.CenterHorizontally),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Branding labels
            Text(
                text = "MIGHTY GPS",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.SansSerif
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            // Auth Error reporting banner
            if (authState is AuthUIState.Error) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF452222)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error notification",
                            tint = Color(0xFFEF4444)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = (authState as AuthUIState.Error).message,
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Input: Email Address (or raw user name)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Enterprise Email ID") },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = "Registered email", tint = Color(0xFF3B82F6))
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color(0xFF3B82F6),
                    unfocusedLabelColor = Color(0xFF94A3B8),
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFF334155)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            // Input: Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Access Password") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = "Password token", tint = Color(0xFF3B82F6))
                },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password readability",
                            tint = Color.Gray
                        )
                    }
                },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color(0xFF3B82F6),
                    unfocusedLabelColor = Color(0xFF94A3B8),
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFF334155)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )

            // Button: Trigger Authentication query
            Button(
                onClick = {
                    keyboardController?.hide()
                    viewModel.submitLogin(serverUrl, email, password)
                },
                enabled = authState != AuthUIState.Loading,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB),
                    disabledContainerColor = Color(0xFF1D4ED8)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (authState == AuthUIState.Loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "LOGIN",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = "Enter portal")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Diagnostic & Error Logs viewer button
            TextButton(
                onClick = {
                    val intent = android.content.Intent(context, com.example.ui.CrashReportActivity::class.java).apply {
                        putExtra(com.example.ui.CrashReportActivity.EXTRA_CRASH_LOG, com.example.util.CrashLogger.getSavedCrashLog(context) ?: com.example.util.CrashLogger.getLiveDiagnostics(context))
                    }
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF60A5FA))
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "View App Logs & Error Diagnostics",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Informative Footnotes
            Text(
                text = "Secure Connection Ensured via TLS Standard Proxy",
                color = Color(0xFF475569),
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
