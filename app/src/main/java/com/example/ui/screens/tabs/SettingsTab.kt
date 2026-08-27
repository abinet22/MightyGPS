package com.example.ui.screens.tabs

import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.viewmodel.TraccarViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsTab(
    viewModel: TraccarViewModel,
    appLanguage: String,
    mapProviderStyle: String,
    markerLabelStyle: String,
    markerIconStyle: String,
    customIconUri: String?,
    colorMoving: String,
    colorIdle: String,
    colorOffline: String,
    markerTriggerMode: String,
    infoCardFields: String,
    positionUpdateInterval: Int,
    isSyncing: Boolean,
    onLogout: () -> Unit,
    onNavigateTab: (Int) -> Unit,
    imageLauncher: ManagedActivityResultLauncher<String, android.net.Uri?>?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = viewModel.translate("customization_panel"),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Fine-tune language translations, map layer styles, and active labeling text badges.",
            color = Color.Gray,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Operator Session & Fleet Sync Controls
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "OPERATOR SESSION & FLEET SYNC",
                    color = Color(0xFF60A5FA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Manage real-time telemetry synchronization and operator credentials.",
                    color = Color.Gray,
                    fontSize = 11.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Refresh Button
                    Button(
                        onClick = {
                            viewModel.fetchInitialState()
                            viewModel.triggerFeedback("Syncing fleet data...")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh Data", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Logout Button
                    Button(
                        onClick = {
                            viewModel.logout()
                            onLogout()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Log Out", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Language Select Options
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(viewModel.translate("language"), color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "en" to "English 🇺🇸",
                            "am" to "አማርኛ 🇪🇹",
                            "om" to "Oromoo 🇪🇹"
                        ).forEach { (code, label) ->
                            val active = appLanguage == code
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (active) Color(0xFF3B82F6) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setAppLanguage(code) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                // Map Provider Selection Style Options (Pure Google Maps SDK)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(viewModel.translate("map_style") + " - Native Google Maps", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    val availableStyles = listOf(
                        "google_road" to "Google Roadmap 🗺️",
                        "google_satellite" to "Google Satellite 📷",
                        "google_hybrid" to "Google Hybrid 🛰️",
                        "google_terrain" to "Google Terrain ⛰️"
                    )

                    Text("Select Native Google Maps Layer", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableStyles.forEach { (style, labelKey) ->
                            val active = mapProviderStyle == style
                            Box(
                                modifier = Modifier
                                    .background(if (active) Color(0xFF10B981) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                    .border(
                                        1.dp,
                                        if (active) Color(0xFF10B981) else Color(0xFF2D3748),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.setMapProviderStyle(style) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(labelKey, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                // Marker labeling preferences options
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(viewModel.translate("marker_label"), color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "name" to viewModel.translate("device_name"),
                            "coordinates" to viewModel.translate("coordinates"),
                            "plate" to viewModel.translate("plate_number")
                        ).forEach { (labelType, textLabel) ->
                            val active = markerLabelStyle == labelType
                            Box(
                                modifier = Modifier
                                    .background(if (active) Color(0xFF10B981) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setMarkerLabelStyle(labelType) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(textLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                // Marker custom aesthetic selection options
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(viewModel.translate("marker_icon") + " / Profile Vehicle Avatar Setting", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "pin" to "Pin 📍",
                                "car" to "Car 🚗",
                                "truck" to "Truck 🚛"
                            ).forEach { (iconKey, label) ->
                                val active = markerIconStyle == iconKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (active) Color(0xFF8B5CF6) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setMarkerIconStyle(iconKey) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "bike" to "Bike 🏍️",
                            "arrow" to "Arrow 🧭",
                            "custom" to "Custom 🖼️"
                        ).forEach { (iconKey, label) ->
                            val active = markerIconStyle == iconKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (active) Color(0xFF8B5CF6) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setMarkerIconStyle(iconKey) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Image Upload / Preview portion for Custom URI Icon option
                if (markerIconStyle == "custom" || !customIconUri.isNullOrEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, Color(0xFF2D3748)),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Local Photo Library Icon", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (!customIconUri.isNullOrEmpty()) "Custom avatar is active on live tracking." else "No local file assigned yet.",
                                    color = Color.LightGray,
                                    fontSize = 10.sp
                                )
                                Button(
                                    onClick = { imageLauncher?.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text("Upload JPEG/PNG Icon", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Image thumbnail slot
                            if (!customIconUri.isNullOrEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.White, CircleShape)
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = customIconUri,
                                        contentDescription = "User Uploaded Vehicle Icon",
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B))

            // Status Colors Customizer
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Marker Status Colors (Moving / Idle / Offline)", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Customize ring & halo colors based on live telemetry status.", color = Color.Gray, fontSize = 11.sp)
                val colorPresets = listOf(
                    "#10B981" to "Emerald",
                    "#F59E0B" to "Amber",
                    "#EF4444" to "Crimson",
                    "#3B82F6" to "Blue",
                    "#8B5CF6" to "Purple",
                    "#06B6D4" to "Cyan"
                )
                listOf(
                    Triple("Moving / Online ⚡", colorMoving) { c: String -> viewModel.setColorMoving(c) },
                    Triple("Idle / Parked 💤", colorIdle) { c: String -> viewModel.setColorIdle(c) },
                    Triple("Offline / Lost 🚫", colorOffline) { c: String -> viewModel.setColorOffline(c) }
                ).forEach { (label, currentCol, setter) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            colorPresets.forEach { (hex, _) ->
                                val isSel = currentCol.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(
                                            try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray },
                                            CircleShape
                                        )
                                        .border(if (isSel) 2.dp else 0.dp, if (isSel) Color.White else Color.Transparent, CircleShape)
                                        .clickable { setter(hex) }
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B))

            // Marker Trigger Mode
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Info Card Trigger Mode", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Control how and when the large telemetry card appears on the map.", color = Color.Gray, fontSize = 11.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "click" to "Show on click (default)",
                        "hover" to "Show on hover",
                        "always" to "Always show for selected"
                    ).forEach { (modeKey, modeLabel) ->
                        val active = markerTriggerMode == modeKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (active) Color(0xFF3B82F6) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                .clickable { viewModel.setMarkerTriggerMode(modeKey) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(modeLabel, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B))

            // Info Card Fields Customizer
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Info Card Fields & Reordering", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Select which data fields to display in the map marker info card. Use arrows to reorder—order here is order shown!", color = Color.Gray, fontSize = 11.sp)
                
                val allAvailableFields = listOf(
                    "name" to "Vehicle Name & Plate Number",
                    "speed" to "Speed & Heading Angle",
                    "driver" to "Driver Name",
                    "lastUpdate" to "Last Update Time",
                    "address" to "Address (Reverse Geocoded)",
                    "battery" to "Battery & Fuel Level",
                    "odometer" to "Odometer Reading",
                    "ignition" to "Ignition Status"
                )
                val currentList = infoCardFields.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                
                allAvailableFields.forEach { (fieldKey, fieldName) ->
                    val isChecked = currentList.contains(fieldKey)
                    val idx = currentList.indexOf(fieldKey)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f).clickable {
                                val newList = if (isChecked) {
                                    if (currentList.size > 1) currentList - fieldKey else currentList
                                } else {
                                    currentList + fieldKey
                                }
                                viewModel.setInfoCardFields(newList.joinToString(","))
                            }
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { chk ->
                                    val newList = if (!chk) {
                                        if (currentList.size > 1) currentList - fieldKey else currentList
                                    } else {
                                        currentList + fieldKey
                                    }
                                    viewModel.setInfoCardFields(newList.joinToString(","))
                                },
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF3B82F6), uncheckedColor = Color.Gray)
                            )
                            Text(fieldName, color = if (isChecked) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal)
                        }
                        
                        if (isChecked) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = {
                                        if (idx > 0) {
                                            val mut = currentList.toMutableList()
                                            val tmp = mut[idx]
                                            mut[idx] = mut[idx - 1]
                                            mut[idx - 1] = tmp
                                            viewModel.setInfoCardFields(mut.joinToString(","))
                                        }
                                    },
                                    enabled = idx > 0,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Text("⬆️", fontSize = 12.sp, color = if (idx > 0) Color.White else Color.DarkGray)
                                }
                                IconButton(
                                    onClick = {
                                        if (idx >= 0 && idx < currentList.size - 1) {
                                            val mut = currentList.toMutableList()
                                            val tmp = mut[idx]
                                            mut[idx] = mut[idx + 1]
                                            mut[idx + 1] = tmp
                                            viewModel.setInfoCardFields(mut.joinToString(","))
                                        }
                                    },
                                    enabled = idx >= 0 && idx < currentList.size - 1,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Text("⬇️", fontSize = 12.sp, color = if (idx >= 0 && idx < currentList.size - 1) Color.White else Color.DarkGray)
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B))

            // WebSocket Update Interval Selection
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Position Update Interval / Battery Saver", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Longer intervals dramatically reduce battery usage, background data, and map flickering during live tracking.", color = Color.Gray, fontSize = 11.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        10 to "10s ⚡",
                        60 to "1m",
                        180 to "3m",
                        300 to "5m 🔋",
                        600 to "10m 💤"
                    ).forEach { (seconds, label) ->
                        val active = positionUpdateInterval == seconds
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (active) Color(0xFF3B82F6) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                .clickable { viewModel.setPositionUpdateInterval(seconds) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B))

            // Local Map Tile Cache Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Map Tile Storage Cache", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("If you notice rendering glitches, visual artifacts, or outdated satellite tiles, clear the local storage cache to force-refresh all map data.", color = Color.Gray, fontSize = 11.sp)
                
                var isClearing by remember { mutableStateOf(false) }
                val context = androidx.compose.ui.platform.LocalContext.current
                val coroutineScope = rememberCoroutineScope()

                Button(
                    onClick = {
                        coroutineScope.launch {
                            isClearing = true
                            try {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    coil.Coil.imageLoader(context).diskCache?.clear()
                                    coil.Coil.imageLoader(context).memoryCache?.clear()
                                }
                                viewModel.triggerFeedback("Map tile cache cleared successfully!")
                            } catch (e: Exception) {
                                viewModel.triggerFeedback("Failed to clear map tile cache: ${e.message}")
                            } finally {
                                isClearing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isClearing
                ) {
                    if (isClearing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clearing Cache...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Cache Icon",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Local Tile Cache", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Custom Fleet Tools & Controls Navigation Menu
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "FLEET TOOLS & CONTROL",
                color = Color(0xFF60A5FA),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            
            // Option 1: Send Command
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                    .clickable { onNavigateTab(3) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (appLanguage == "am") "ቁጥጥር ትዕዛዝ (Commands)" else if (appLanguage == "om") "Ergaa Ergi" else "Send Commands", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Dispatch GPRS configurations and remote execution payloads", color = Color.Gray, fontSize = 9.sp)
                }
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
            }

            // Option 2: Geofence Planner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                    .clickable { onNavigateTab(4) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (appLanguage == "am") "የጂኦፌንስ ፕላነር" else if (appLanguage == "om") "Daangaa Geofence" else "Geofence Planner", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Establish geographical rules and boundaries", color = Color.Gray, fontSize = 9.sp)
                }
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
            }

            // Option 3: Analytics & Reports
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                    .clickable { onNavigateTab(6) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (appLanguage == "am") "የመንገድ ታሪክ ሪፖርቶች" else if (appLanguage == "om") "Gabaasa Seenaa" else "Historical Route Reports", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Generate mileage, velocity, and trip detail summaries", color = Color.Gray, fontSize = 9.sp)
                }
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
            }

            // Option 4: Alerts and Notifications
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                    .clickable { onNavigateTab(7) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (appLanguage == "am") "የቀጥታ ማንቂያዎችና ማሳወቂያዎች" else if (appLanguage == "om") "Akeekkachiisa Haaraya" else "Alerts & Live Notifications", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Review active geographical fence violations and security logs", color = Color.Gray, fontSize = 9.sp)
                }
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
            }
        }
    }

    // SaaS Tenant details footer card
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(viewModel.translate("tenant_mode"), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            Text(viewModel.translate("assigned_vehicles"), color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
}
