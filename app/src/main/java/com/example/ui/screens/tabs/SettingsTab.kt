package com.example.ui.screens.tabs

import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.theme.MC
import com.example.ui.viewmodel.TraccarViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    description: String? = null,
    initiallyExpanded: Boolean = false,
    collapsible: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "arrow_rotation"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MC.Surface1),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(if (isExpanded) 14.dp else 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (collapsible) {
                            Modifier.clickable { isExpanded = !isExpanded }
                        } else Modifier
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MC.Surface2, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MC.AccentPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = title,
                            color = MC.TextPrimary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (description != null) {
                            Text(
                                text = description,
                                color = MC.TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (collapsible) {
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse Section" else "Expand Section",
                            tint = MC.TextSecondary,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer { rotationZ = rotationAngle }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded || !collapsible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    viewModel: TraccarViewModel,
    appLanguage: String,
    unitSystem: String = "metric",
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Page Header
        Column(modifier = Modifier.padding(bottom = 2.dp)) {
            Text(
                text = viewModel.translate("customization_panel"),
                style = MaterialTheme.typography.headlineSmall,
                color = MC.TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Configure display languages, telemetry refresh intervals, marker cosmetics, and map layer profiles.",
                style = MaterialTheme.typography.bodySmall,
                color = MC.TextSecondary
            )
        }

        // ==========================================
        // 1. OPERATOR SESSION & FLEET SYNC
        // ==========================================
        SettingsSectionCard(
            title = "Operator Session & Fleet Sync",
            icon = Icons.Default.AccountCircle,
            description = "Manage real-time telemetry synchronization and active credentials"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.fetchInitialState()
                        viewModel.triggerFeedback("Syncing fleet data...")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MC.AccentPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MC.TextPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MC.TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Refresh Data",
                            style = MaterialTheme.typography.titleSmall,
                            color = MC.TextPrimary
                        )
                    }
                }

                Button(
                    onClick = {
                        viewModel.logout()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MC.StatusOffline),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MC.TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Log Out",
                            style = MaterialTheme.typography.titleSmall,
                            color = MC.TextPrimary
                        )
                    }
                }
            }
        }

        // ==========================================
        // 2. DISPLAY & LOCALIZATION
        // ==========================================
        SettingsSectionCard(
            title = viewModel.translate("language"),
            icon = Icons.Default.Language,
            description = "Interface display language and measurement units"
        ) {
            Text(
                text = "Display Language",
                style = MaterialTheme.typography.titleSmall,
                color = MC.TextPrimary
            )
            val langs = listOf("en" to "English", "am" to "አማርኛ", "om" to "Oromoo")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                langs.forEachIndexed { i, (code, label) ->
                    SegmentedButton(
                        selected = appLanguage == code,
                        onClick = { viewModel.setAppLanguage(code) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = langs.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MC.AccentPrimary,
                            activeContentColor = MC.TextPrimary,
                            inactiveContainerColor = MC.Surface2,
                            inactiveContentColor = MC.TextSecondary
                        )
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            HorizontalDivider(color = MC.Surface3)

            Text(
                text = "Measurement Units (Speed & Distance)",
                style = MaterialTheme.typography.titleSmall,
                color = MC.TextPrimary
            )
            val unitModes = listOf(
                "metric" to "Metric (km/h, km)",
                "imperial" to "Imperial (mph, mi)"
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                unitModes.forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = unitSystem == mode,
                        onClick = { viewModel.setUnitSystem(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = unitModes.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MC.StatusOnline,
                            activeContentColor = MC.TextPrimary,
                            inactiveContainerColor = MC.Surface2,
                            inactiveContentColor = MC.TextSecondary
                        )
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // ==========================================
        // 3. MAP & VEHICLE MARKERS
        // ==========================================
        SettingsSectionCard(
            title = "Map & Vehicle Markers",
            icon = Icons.Default.Place,
            description = "Layer styles, vehicle icons, labeling badges, and telemetry color schemes"
        ) {
            Text(
                text = "Google Maps Layer Style",
                style = MaterialTheme.typography.titleSmall,
                color = MC.TextPrimary
            )
            val mapStyles = listOf(
                "google_road" to "Roadmap",
                "google_satellite" to "Satellite",
                "google_hybrid" to "Hybrid",
                "google_terrain" to "Terrain"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                mapStyles.forEach { (styleKey, label) ->
                    val isSelected = mapProviderStyle == styleKey
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setMapProviderStyle(styleKey) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MC.StatusOnline,
                            selectedLabelColor = MC.TextPrimary,
                            containerColor = MC.Surface2,
                            labelColor = MC.TextSecondary
                        ),
                        border = null,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            HorizontalDivider(color = MC.Surface3)

            Text(
                text = "Marker Caption Label",
                style = MaterialTheme.typography.titleSmall,
                color = MC.TextPrimary
            )
            val labelStyles = listOf(
                "name" to viewModel.translate("device_name"),
                "coordinates" to viewModel.translate("coordinates"),
                "plate" to viewModel.translate("plate_number")
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                labelStyles.forEachIndexed { i, (styleKey, label) ->
                    SegmentedButton(
                        selected = markerLabelStyle == styleKey,
                        onClick = { viewModel.setMarkerLabelStyle(styleKey) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = labelStyles.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MC.StatusOnline,
                            activeContentColor = MC.TextPrimary,
                            inactiveContainerColor = MC.Surface2,
                            inactiveContentColor = MC.TextSecondary
                        )
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            HorizontalDivider(color = MC.Surface3)

            Text(
                text = "Marker Vehicle Icon & Avatar",
                style = MaterialTheme.typography.titleSmall,
                color = MC.TextPrimary
            )
            val iconPresets = listOf(
                Triple("car", "Car", Icons.Default.DirectionsCar),
                Triple("truck", "Truck", Icons.Default.LocalShipping),
                Triple("bike", "Bike", Icons.Default.TwoWheeler),
                Triple("pin", "Pin", Icons.Default.Place),
                Triple("arrow", "Arrow", Icons.Default.Navigation),
                Triple("custom", "Custom Photo", Icons.Default.Image)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                iconPresets.forEach { (iconKey, label, iconVector) ->
                    val isSelected = markerIconStyle == iconKey
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setMarkerIconStyle(iconKey) },
                        leadingIcon = {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected) MC.TextPrimary else MC.TextSecondary
                            )
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MC.AccentPrimary,
                            selectedLabelColor = MC.TextPrimary,
                            containerColor = MC.Surface2,
                            labelColor = MC.TextSecondary
                        ),
                        border = null,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            if (markerIconStyle == "custom" || !customIconUri.isNullOrEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MC.Surface2),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Local Photo Library Icon",
                                color = MC.TextPrimary,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (!customIconUri.isNullOrEmpty()) "Custom avatar is active on live tracking." else "No local file assigned yet.",
                                color = MC.TextSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Button(
                                onClick = { imageLauncher?.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = MC.AccentPrimary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text("Upload JPEG/PNG Icon", color = MC.TextPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!customIconUri.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.width(12.dp))
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

            HorizontalDivider(color = MC.Surface3)

            Text(
                text = "Marker Status Colors (Moving / Idle / Offline)",
                style = MaterialTheme.typography.titleSmall,
                color = MC.TextPrimary
            )
            Text(
                text = "Customize ring & halo colors based on live telemetry status.",
                color = MC.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            val colorPresets = listOf(
                "#10B981" to "Emerald",
                "#F59E0B" to "Amber",
                "#EF4444" to "Crimson",
                "#3B82F6" to "Blue",
                "#8B5CF6" to "Purple",
                "#06B6D4" to "Cyan"
            )

            val statusGroups = listOf(
                Triple("Moving / Online", colorMoving) { c: String -> viewModel.setColorMoving(c) },
                Triple("Idle / Parked", colorIdle) { c: String -> viewModel.setColorIdle(c) },
                Triple("Offline / Inactive", colorOffline) { c: String -> viewModel.setColorOffline(c) }
            )

            statusGroups.forEach { (label, currentCol, setter) ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(label, color = MC.TextPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        colorPresets.forEach { (hex, name) ->
                            val isSel = currentCol.equals(hex, ignoreCase = true)
                            val parsedColor = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray }
                            FilterChip(
                                selected = isSel,
                                onClick = { setter(hex) },
                                label = { Text(name, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .background(parsedColor, CircleShape)
                                            .border(1.dp, if (isSel) Color.White else Color.Transparent, CircleShape)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = parsedColor.copy(alpha = 0.25f),
                                    selectedLabelColor = MC.TextPrimary,
                                    containerColor = MC.Surface2,
                                    labelColor = MC.TextSecondary
                                ),
                                border = null,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MC.Surface3)

            Text(
                text = "Info Card Trigger Mode",
                style = MaterialTheme.typography.titleSmall,
                color = MC.TextPrimary
            )
            val triggerModes = listOf(
                "click" to "On Click",
                "hover" to "On Hover",
                "always" to "Always Show"
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                triggerModes.forEachIndexed { i, (modeKey, label) ->
                    SegmentedButton(
                        selected = markerTriggerMode == modeKey,
                        onClick = { viewModel.setMarkerTriggerMode(modeKey) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = triggerModes.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MC.AccentPrimary,
                            activeContentColor = MC.TextPrimary,
                            inactiveContainerColor = MC.Surface2,
                            inactiveContentColor = MC.TextSecondary
                        )
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // ==========================================
        // 4. INFO CARD FIELDS & REORDERING
        // ==========================================
        SettingsSectionCard(
            title = "Info Card Fields & Ordering",
            icon = Icons.Default.List,
            description = "Select telemetry attributes to display on the map marker card. Use arrows to reorder."
        ) {
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

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                allAvailableFields.forEach { (fieldKey, fieldName) ->
                    val isChecked = currentList.contains(fieldKey)
                    val idx = currentList.indexOf(fieldKey)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isChecked) MC.Surface2 else MC.Surface0
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
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
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MC.AccentPrimary,
                                        uncheckedColor = MC.TextTertiary
                                    )
                                )
                                Text(
                                    text = fieldName,
                                    color = if (isChecked) MC.TextPrimary else MC.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                                )
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
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Move Up",
                                            tint = if (idx > 0) MC.TextPrimary else MC.TextTertiary,
                                            modifier = Modifier.size(18.dp)
                                        )
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
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Move Down",
                                            tint = if (idx >= 0 && idx < currentList.size - 1) MC.TextPrimary else MC.TextTertiary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 5. PERFORMANCE & STORAGE
        // ==========================================
        SettingsSectionCard(
            title = "Performance & Storage",
            icon = Icons.Default.Speed,
            description = "Optimize real-time update frequencies and clear local disk cache"
        ) {
            Text(
                text = "Position Update Interval / Battery Saver",
                style = MaterialTheme.typography.titleSmall,
                color = MC.TextPrimary
            )
            Text(
                text = "Longer intervals reduce battery drain and network usage during active tracking.",
                color = MC.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            val intervals = listOf(
                10 to "10s",
                60 to "1m",
                180 to "3m",
                300 to "5m",
                600 to "10m"
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                intervals.forEachIndexed { i, (seconds, label) ->
                    SegmentedButton(
                        selected = positionUpdateInterval == seconds,
                        onClick = { viewModel.setPositionUpdateInterval(seconds) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = intervals.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MC.AccentPrimary,
                            activeContentColor = MC.TextPrimary,
                            inactiveContainerColor = MC.Surface2,
                            inactiveContentColor = MC.TextSecondary
                        )
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            HorizontalDivider(color = MC.Surface3)

            Text(
                text = "Map Tile & Data Cache",
                style = MaterialTheme.typography.titleSmall,
                color = MC.TextPrimary
            )
            Text(
                text = "If you notice rendering glitches or outdated satellite tiles, clear the local storage cache to force-refresh all map data.",
                color = MC.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            var isClearing by remember { mutableStateOf(false) }

            Button(
                onClick = {
                    coroutineScope.launch {
                        isClearing = true
                        try {
                            withContext(Dispatchers.IO) {
                                coil.Coil.imageLoader(context).diskCache?.clear()
                                coil.Coil.imageLoader(context).memoryCache?.clear()
                                viewModel.repository.clearReportCaches()
                            }
                            viewModel.triggerFeedback("Map tile & report caches cleared successfully!")
                        } catch (e: Exception) {
                            viewModel.triggerFeedback("Failed to clear cache: ${e.message}")
                        } finally {
                            isClearing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MC.StatusOffline,
                    contentColor = MC.TextPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                enabled = !isClearing
            ) {
                if (isClearing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MC.TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clearing Cache...", style = MaterialTheme.typography.titleSmall)
                } else {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Cache Icon",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Local Tile & Report Cache", style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        // ==========================================
        // 6. FLEET MANAGEMENT TOOLS NAVIGATION
        // ==========================================
        SettingsSectionCard(
            title = "Fleet Tools & Control",
            icon = Icons.Default.Build,
            description = "Direct access to mission-critical fleet tracking modules"
        ) {
            val tools = listOf(
                Triple(
                    if (appLanguage == "am") "ቁጥጥር ትዕዛዝ (Commands)" else if (appLanguage == "om") "Ergaa Ergi" else "Send Commands",
                    "Dispatch GPRS configurations and remote execution payloads",
                    3
                ) to (Icons.Default.Send to MC.AccentPrimary),
                Triple(
                    if (appLanguage == "am") "የጂኦፌንስ ፕላነር" else if (appLanguage == "om") "Daangaa Geofence" else "Geofence Planner",
                    "Establish geographical rules and perimeter boundaries",
                    4
                ) to (Icons.Default.Place to MC.StatusOnline),
                Triple(
                    if (appLanguage == "am") "የመንገድ ታሪክ ሪፖርቶች" else if (appLanguage == "om") "Gabaasa Seenaa" else "Historical Route Reports",
                    "Generate mileage, velocity, and trip detail summaries",
                    6
                ) to (Icons.Default.DateRange to MC.StatusIdle),
                Triple(
                    if (appLanguage == "am") "የቀጥታ ማንቂያዎችና ማሳወቂያዎች" else if (appLanguage == "om") "Akeekkachiisa Haaraya" else "Alerts & Live Notifications",
                    "Review active geographical fence violations and security logs",
                    7
                ) to (Icons.Default.Notifications to MC.StatusOffline)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tools.forEach { (info, styling) ->
                    val (title, subtitle, tabIndex) = info
                    val (icon, color) = styling
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MC.Surface2),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateTab(tabIndex) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    color = MC.TextPrimary,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = subtitle,
                                    color = MC.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MC.TextTertiary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 7. SAAS MULTI-TENANT BADGE FOOTER
        // ==========================================
        Card(
            colors = CardDefaults.cardColors(containerColor = MC.Surface1),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MC.AccentPrimary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MC.AccentPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = viewModel.translate("tenant_mode"),
                        color = MC.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = viewModel.translate("assigned_vehicles"),
                        color = MC.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
