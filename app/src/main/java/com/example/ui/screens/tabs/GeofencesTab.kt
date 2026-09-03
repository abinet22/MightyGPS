package com.example.ui.screens.tabs

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.model.Device
import com.example.ui.map.SlippyMap
import com.example.ui.screens.components.EmptyStateView
import com.example.ui.screens.components.MapStyleControlLayer
import com.example.ui.screens.components.StatusBadge
import com.example.ui.theme.MC
import com.example.ui.viewmodel.TraccarViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class GeofenceTabMode {
    CREATE,
    ACTIVE_LIST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofencesTab(
    viewModel: TraccarViewModel,
    devices: List<Device>,
    mapProviderStyle: String,
    modifier: Modifier = Modifier
) {
    val geofences by viewModel.geofences.collectAsState()
    val scope = rememberCoroutineScope()

    // GIS drawing board states
    var drawMode by remember { mutableStateOf("none") } // "none", "polygon", "circle"
    val drawnPoints = remember { mutableStateListOf<Pair<Double, Double>>() }
    var drawnCircleCenter by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var drawnCircleRadiusMeters by remember { mutableStateOf(1000.0) }

    // Geofence configuration states
    var gfName by remember { mutableStateOf("") }
    var selectedDeviceIdForGeofence by remember { mutableStateOf<Long?>(null) }
    var triggerOnEnter by remember { mutableStateOf(true) }
    var triggerOnExit by remember { mutableStateOf(true) }
    var attemptedSave by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Visual feedback & Sheet control
    var highlightedGeofenceId by remember { mutableStateOf<String?>(null) }
    var activeTabMode by remember { mutableStateOf(GeofenceTabMode.CREATE) }
    var isSheetExpanded by remember { mutableStateOf(false) }

    // Auto-expand sheet to Step 2 when drawing starts
    LaunchedEffect(drawnPoints.size, drawnCircleCenter) {
        if (drawnPoints.isNotEmpty() || drawnCircleCenter != null) {
            isSheetExpanded = true
            activeTabMode = GeofenceTabMode.CREATE
        }
    }

    // Validation & dynamic reason calculation for Step 3
    val isPolygon = drawMode == "polygon" || drawnPoints.isNotEmpty()
    val isCircle = drawMode == "circle" || drawnCircleCenter != null

    val isDrawingValid = when {
        isPolygon -> drawnPoints.size >= 3
        isCircle -> drawnCircleCenter != null && drawnCircleRadiusMeters > 0
        else -> false
    }

    val isFormValid = isDrawingValid && gfName.isNotBlank()

    val saveReasonLabel = when {
        drawMode == "none" && drawnPoints.isEmpty() && drawnCircleCenter == null -> "Select tool & draw on map"
        isPolygon && drawnPoints.size < 3 -> "Plot at least 3 points (${drawnPoints.size}/3)"
        isCircle && drawnCircleCenter == null -> "Tap map to set circle center"
        gfName.isBlank() -> "Add a name to continue"
        else -> "Save Geofence"
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Full Screen Interactive Map Canvas
        SlippyMap(
            modifier = Modifier.fillMaxSize(),
            initialCenterLat = 8.7832,
            initialCenterLng = 38.7405,
            initialZoom = 6.0f,
            markers = emptyList(),
            geofences = geofences,
            highlightedGeofenceId = highlightedGeofenceId,
            drawMode = drawMode,
            drawnPoints = drawnPoints,
            onDrawPointsChanged = { newPoints ->
                drawnPoints.clear()
                drawnPoints.addAll(newPoints)
            },
            drawnCircleCenter = drawnCircleCenter,
            drawnCircleRadiusMeters = drawnCircleRadiusMeters,
            onDrawCircleChanged = { center, radius ->
                drawnCircleCenter = center
                drawnCircleRadiusMeters = radius
            },
            mapStyle = mapProviderStyle,
            isDarkMode = true
        )

        // 2. Map Style Layer Controller
        MapStyleControlLayer(
            mapProviderStyle = mapProviderStyle,
            onStyleSelected = { viewModel.setMapProviderStyle(it) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .zIndex(10f)
        )

        // 3. Top Floating Guidance Banner
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 16.dp, start = 16.dp, end = 80.dp)
                .zIndex(10f),
            shape = RoundedCornerShape(12.dp),
            color = MC.Surface1.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, MC.Surface3),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = when {
                        drawMode == "polygon" -> MC.StatusOnline.copy(alpha = 0.2f)
                        drawMode == "circle" -> MC.AccentPrimary.copy(alpha = 0.2f)
                        else -> MC.Surface3
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (drawMode) {
                                "polygon" -> Icons.Default.Polyline
                                "circle" -> Icons.Default.Adjust
                                else -> Icons.Default.GpsFixed
                            },
                            contentDescription = null,
                            tint = when {
                                drawMode == "polygon" -> MC.StatusOnline
                                drawMode == "circle" -> MC.AccentPrimary
                                else -> MC.TextSecondary
                            },
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = when {
                            drawMode == "polygon" -> "Step 1: Plot Polygon Vertices (${drawnPoints.size} added)"
                            drawMode == "circle" && drawnCircleCenter == null -> "Step 1: Tap map to place circle center"
                            drawMode == "circle" && drawnCircleCenter != null -> "Step 1: Drag handle on circle edge to resize"
                            else -> "Virtual Geofence Zones (${geofences.size} Active)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MC.TextPrimary
                    )
                    Text(
                        text = when {
                            drawMode == "polygon" -> "Tap anywhere on the map to add boundary points (min 3)"
                            drawMode == "circle" && drawnCircleCenter == null -> "Tap the desired central asset hub coordinate"
                            drawMode == "circle" && drawnCircleCenter != null -> "Drag edge marker on map to tune radius live"
                            else -> "Select a drawing tool on right to create a new boundary"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MC.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 4. Floating GIS Draw Toolbar (Polygon / Circle / Reset)
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .width(48.dp)
                .zIndex(10f),
            shape = RoundedCornerShape(16.dp),
            color = MC.Surface1.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, MC.Surface3),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Polygon button
                IconButton(
                    onClick = {
                        drawMode = if (drawMode == "polygon") "none" else "polygon"
                        drawnCircleCenter = null
                        activeTabMode = GeofenceTabMode.CREATE
                    },
                    modifier = Modifier.size(38.dp).testTag("tool_polygon_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Polyline,
                        contentDescription = "Draw Polygon",
                        tint = if (drawMode == "polygon") MC.StatusOnline else MC.TextSecondary
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 6.dp), color = MC.Surface3)

                // Circle button
                IconButton(
                    onClick = {
                        drawMode = if (drawMode == "circle") "none" else "circle"
                        drawnPoints.clear()
                        activeTabMode = GeofenceTabMode.CREATE
                    },
                    modifier = Modifier.size(38.dp).testTag("tool_circle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Adjust,
                        contentDescription = "Draw Circle",
                        tint = if (drawMode == "circle") MC.AccentPrimary else MC.TextSecondary
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 6.dp), color = MC.Surface3)

                // Clear/Reset button
                IconButton(
                    onClick = {
                        drawnPoints.clear()
                        drawnCircleCenter = null
                        drawMode = "none"
                        attemptedSave = false
                    },
                    modifier = Modifier.size(38.dp).testTag("tool_reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset drawing",
                        tint = if (drawnPoints.isNotEmpty() || drawnCircleCenter != null) MC.StatusOffline else MC.TextTertiary
                    )
                }
            }
        }

        // 5. Persistent Bottom Sheet (Guided 3-Step Flow & Active Zones Manager)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .zIndex(20f),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MC.Surface1,
            border = BorderStroke(1.dp, MC.Surface3),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // Drag Pill Handle & Mode Switcher Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isSheetExpanded = !isSheetExpanded }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Center Pill Handle
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(36.dp)
                            .height(4.dp)
                            .background(MC.TextTertiary.copy(alpha = 0.4f), CircleShape)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Segmented Mode Selector
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = activeTabMode == GeofenceTabMode.CREATE,
                                onClick = {
                                    activeTabMode = GeofenceTabMode.CREATE
                                    isSheetExpanded = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.EditLocationAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (drawnPoints.isNotEmpty() || drawnCircleCenter != null) "Configure Zone" else "Draw Zone",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MC.AccentPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = MC.AccentPrimary,
                                    selectedLeadingIconColor = MC.AccentPrimary,
                                    containerColor = MC.Surface2,
                                    labelColor = MC.TextSecondary,
                                    iconColor = MC.TextSecondary
                                )
                            )

                            FilterChip(
                                selected = activeTabMode == GeofenceTabMode.ACTIVE_LIST,
                                onClick = {
                                    activeTabMode = GeofenceTabMode.ACTIVE_LIST
                                    isSheetExpanded = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Layers,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = "Active Zones (${geofences.size})",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MC.StatusOnline.copy(alpha = 0.2f),
                                    selectedLabelColor = MC.StatusOnline,
                                    selectedLeadingIconColor = MC.StatusOnline,
                                    containerColor = MC.Surface2,
                                    labelColor = MC.TextSecondary,
                                    iconColor = MC.TextSecondary
                                )
                            )
                        }

                        // Expand / Collapse Chevron
                        IconButton(
                            onClick = { isSheetExpanded = !isSheetExpanded },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isSheetExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = if (isSheetExpanded) "Collapse sheet" else "Expand sheet",
                                tint = MC.TextSecondary
                            )
                        }
                    }

                    // Collapsed summary preview when sheet is not expanded
                    if (!isSheetExpanded) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = when {
                                    drawnPoints.isNotEmpty() -> "✏️ ${drawnPoints.size} polygon vertices plotted • Tap to configure & save"
                                    drawnCircleCenter != null -> "📍 Circle center set (${drawnCircleRadiusMeters.roundToInt()}m) • Tap to configure"
                                    drawMode == "polygon" -> "📍 Tap map to plot polygon vertices"
                                    drawMode == "circle" -> "📍 Tap map to place circle center"
                                    else -> "✏️ Select Polygon or Circle tool on map to begin"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (drawnPoints.isNotEmpty() || drawnCircleCenter != null) MC.AccentPrimary else MC.TextTertiary,
                                fontWeight = if (drawnPoints.isNotEmpty() || drawnCircleCenter != null) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Expandable Content Body
                AnimatedVisibility(
                    visible = isSheetExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                    ) {
                        HorizontalDivider(color = MC.Surface3)

                        when (activeTabMode) {
                            GeofenceTabMode.CREATE -> {
                                // Step 2 (Configure) + Step 3 (Confirm & Save) Container
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false)
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Step 2 Header & Name Field
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Zone Identity & Label",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MC.TextSecondary,
                                            fontWeight = FontWeight.Bold
                                        )

                                        OutlinedTextField(
                                            value = gfName,
                                            onValueChange = {
                                                gfName = it
                                                if (attemptedSave && it.isNotBlank()) attemptedSave = false
                                            },
                                            placeholder = { Text("e.g., Central Warehouse Hub") },
                                            isError = attemptedSave && gfName.isBlank(),
                                            supportingText = {
                                                if (attemptedSave && gfName.isBlank()) {
                                                    Text(
                                                        text = "Geofence name is required to save",
                                                        color = MaterialTheme.colorScheme.error,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = MC.TextPrimary,
                                                unfocusedTextColor = MC.TextPrimary,
                                                focusedBorderColor = MC.AccentPrimary,
                                                unfocusedBorderColor = MC.Surface3,
                                                focusedContainerColor = MC.Surface2,
                                                unfocusedContainerColor = MC.Surface2,
                                                errorBorderColor = MaterialTheme.colorScheme.error,
                                                errorContainerColor = MC.Surface2
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("geofence_name_input")
                                        )
                                    }

                                    // Target Fleet Asset Selection: Compact Chip Row for <= 8 devices
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Target Device Scope",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MC.TextSecondary,
                                            fontWeight = FontWeight.Bold
                                        )

                                        if (devices.size <= 8) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                FilterChip(
                                                    selected = selectedDeviceIdForGeofence == null,
                                                    onClick = { selectedDeviceIdForGeofence = null },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    },
                                                    label = { Text("All Fleet", style = MaterialTheme.typography.bodySmall) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = MC.AccentPrimary.copy(alpha = 0.2f),
                                                        selectedLabelColor = MC.AccentPrimary,
                                                        selectedLeadingIconColor = MC.AccentPrimary,
                                                        containerColor = MC.Surface2,
                                                        labelColor = MC.TextSecondary,
                                                        iconColor = MC.TextSecondary
                                                    )
                                                )

                                                devices.forEach { dev ->
                                                    FilterChip(
                                                        selected = selectedDeviceIdForGeofence == dev.id,
                                                        onClick = { selectedDeviceIdForGeofence = dev.id },
                                                        leadingIcon = {
                                                            Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        },
                                                        label = { Text(dev.name, style = MaterialTheme.typography.bodySmall) },
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = MC.StatusOnline.copy(alpha = 0.2f),
                                                            selectedLabelColor = MC.StatusOnline,
                                                            selectedLeadingIconColor = MC.StatusOnline,
                                                            containerColor = MC.Surface2,
                                                            labelColor = MC.TextSecondary,
                                                            iconColor = MC.TextSecondary
                                                        )
                                                    )
                                                }
                                            }
                                        } else {
                                            // Searchable Dropdown if > 8 devices
                                            var isDevMenuOpen by remember { mutableStateOf(false) }
                                            var devFilterQuery by remember { mutableStateOf("") }
                                            val currentDev = devices.find { it.id == selectedDeviceIdForGeofence }

                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                Button(
                                                    onClick = { isDevMenuOpen = true },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MC.Surface2),
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = if (currentDev != null) Icons.Default.DirectionsCar else Icons.Default.Language,
                                                                contentDescription = null,
                                                                tint = MC.AccentPrimary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Text(
                                                                text = currentDev?.let { "${it.name} (${it.uniqueId})" } ?: "All Fleet Vehicles (Global)",
                                                                color = MC.TextPrimary,
                                                                style = MaterialTheme.typography.bodySmall
                                                            )
                                                        }
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MC.TextSecondary)
                                                    }
                                                }

                                                DropdownMenu(
                                                    expanded = isDevMenuOpen,
                                                    onDismissRequest = { isDevMenuOpen = false },
                                                    modifier = Modifier
                                                        .fillMaxWidth(0.9f)
                                                        .background(MC.Surface1)
                                                ) {
                                                    OutlinedTextField(
                                                        value = devFilterQuery,
                                                        onValueChange = { devFilterQuery = it },
                                                        placeholder = { Text("Filter devices...") },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(8.dp),
                                                        singleLine = true
                                                    )
                                                    DropdownMenuItem(
                                                        leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, tint = MC.AccentPrimary) },
                                                        text = { Text("All Fleet Vehicles (Global)", color = MC.AccentPrimary, fontWeight = FontWeight.Bold) },
                                                        onClick = {
                                                            selectedDeviceIdForGeofence = null
                                                            isDevMenuOpen = false
                                                        }
                                                    )
                                                    val filtered = devices.filter { it.name.contains(devFilterQuery, ignoreCase = true) || it.uniqueId.contains(devFilterQuery, ignoreCase = true) }
                                                    filtered.forEach { dev ->
                                                        DropdownMenuItem(
                                                            leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MC.TextSecondary) },
                                                            text = { Text("${dev.name} [${dev.uniqueId}]", color = MC.TextPrimary) },
                                                            onClick = {
                                                                selectedDeviceIdForGeofence = dev.id
                                                                isDevMenuOpen = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Push Notification Trigger Preferences
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Transition Triggers",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MC.TextSecondary,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FilterChip(
                                                selected = triggerOnEnter,
                                                onClick = { triggerOnEnter = !triggerOnEnter },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(14.dp))
                                                },
                                                label = { Text("Notify on Enter", style = MaterialTheme.typography.bodySmall) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MC.StatusOnline.copy(alpha = 0.2f),
                                                    selectedLabelColor = MC.StatusOnline,
                                                    selectedLeadingIconColor = MC.StatusOnline,
                                                    containerColor = MC.Surface2,
                                                    labelColor = MC.TextSecondary,
                                                    iconColor = MC.TextSecondary
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                            FilterChip(
                                                selected = triggerOnExit,
                                                onClick = { triggerOnExit = !triggerOnExit },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(14.dp))
                                                },
                                                label = { Text("Notify on Exit", style = MaterialTheme.typography.bodySmall) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MC.StatusOffline.copy(alpha = 0.2f),
                                                    selectedLabelColor = MC.StatusOffline,
                                                    selectedLeadingIconColor = MC.StatusOffline,
                                                    containerColor = MC.Surface2,
                                                    labelColor = MC.TextSecondary,
                                                    iconColor = MC.TextSecondary
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    // Geometry Live Status & Quick Circle Presets
                                    if (isCircle && drawnCircleCenter != null) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MC.Surface2, RoundedCornerShape(10.dp))
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Radius Presets (or drag map handle):", style = MaterialTheme.typography.bodySmall, color = MC.TextSecondary)
                                                Text(
                                                    text = if (drawnCircleRadiusMeters >= 1000) String.format("%.1f km", drawnCircleRadiusMeters / 1000.0) else "${drawnCircleRadiusMeters.roundToInt()} m",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MC.AccentPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                listOf(250, 500, 1000, 2500, 5000).forEach { preset ->
                                                    SuggestionChip(
                                                        onClick = { drawnCircleRadiusMeters = preset.toDouble() },
                                                        label = { Text(if (preset >= 1000) "${preset / 1000}km" else "${preset}m", style = MaterialTheme.typography.labelSmall) },
                                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                                            containerColor = if (drawnCircleRadiusMeters.roundToInt() == preset) MC.AccentPrimary.copy(alpha = 0.2f) else MC.Surface3,
                                                            labelColor = if (drawnCircleRadiusMeters.roundToInt() == preset) MC.AccentPrimary else MC.TextPrimary
                                                        ),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    } else if (isPolygon) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MC.Surface2,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Default.Polyline, contentDescription = null, tint = MC.StatusOnline, modifier = Modifier.size(16.dp))
                                                Text(
                                                    text = "Polygon Nodes: ${drawnPoints.size} vertices plotted (min 3 required)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MC.TextPrimary
                                                )
                                            }
                                        }
                                    }
                                }

                                // Step 3 Sticky Save Button Pinned to Bottom of Sheet
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MC.Surface1,
                                    border = BorderStroke(1.dp, MC.Surface3)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                if (!isFormValid) {
                                                    attemptedSave = true
                                                    return@Button
                                                }
                                                isSaving = true
                                                scope.launch {
                                                    try {
                                                        val createdGf = if (isPolygon && drawnPoints.size >= 3) {
                                                            val centerLat = drawnPoints.map { it.first }.average()
                                                            val centerLng = drawnPoints.map { it.second }.average()
                                                            viewModel.addGeofenceAsync(
                                                                name = gfName,
                                                                lat = centerLat,
                                                                lng = centerLng,
                                                                radius = 0.0,
                                                                type = "polygon",
                                                                points = drawnPoints.toList(),
                                                                deviceId = selectedDeviceIdForGeofence,
                                                                triggerOnEnter = triggerOnEnter,
                                                                triggerOnExit = triggerOnExit
                                                            )
                                                        } else if (isCircle && drawnCircleCenter != null) {
                                                            viewModel.addGeofenceAsync(
                                                                name = gfName,
                                                                lat = drawnCircleCenter!!.first,
                                                                lng = drawnCircleCenter!!.second,
                                                                radius = drawnCircleRadiusMeters,
                                                                type = "circle",
                                                                points = emptyList(),
                                                                deviceId = selectedDeviceIdForGeofence,
                                                                triggerOnEnter = triggerOnEnter,
                                                                triggerOnExit = triggerOnExit
                                                            )
                                                        } else null

                                                        // Success Actions
                                                        if (createdGf != null) {
                                                            highlightedGeofenceId = createdGf.id
                                                            // Clear drawing state
                                                            gfName = ""
                                                            drawnPoints.clear()
                                                            drawnCircleCenter = null
                                                            drawMode = "none"
                                                            attemptedSave = false
                                                            // Auto-collapse sheet
                                                            isSheetExpanded = false
                                                            // Stop pulse animation after 4 seconds
                                                            scope.launch {
                                                                delay(4000)
                                                                if (highlightedGeofenceId == createdGf.id) {
                                                                    highlightedGeofenceId = null
                                                                }
                                                            }
                                                        }
                                                    } finally {
                                                        isSaving = false
                                                    }
                                                }
                                            },
                                            enabled = !isSaving,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isFormValid) MC.StatusOnline else MC.Surface3,
                                                disabledContainerColor = MC.Surface3
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("save_geofence_button")
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (isSaving) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        color = Color.White,
                                                        strokeWidth = 2.dp
                                                    )
                                                    Text("Saving Geofence...", fontWeight = FontWeight.Bold, color = Color.White)
                                                } else {
                                                    Icon(
                                                        imageVector = if (isFormValid) Icons.Default.Check else Icons.Default.Info,
                                                        contentDescription = null,
                                                        tint = if (isFormValid) MC.TextPrimary else MC.TextTertiary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = saveReasonLabel,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isFormValid) MC.TextPrimary else MC.TextTertiary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            GeofenceTabMode.ACTIVE_LIST -> {
                                // Active Geofences List View
                                if (geofences.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        EmptyStateView(
                                            icon = Icons.Default.Polyline,
                                            title = "No active geofences",
                                            subtitle = "Use the GIS tools on the map canvas to draw and deploy virtual zones for your fleet."
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(geofences, key = { it.id }) { gf ->
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = if (gf.isActive) MC.Surface2 else MC.Surface0),
                                                shape = RoundedCornerShape(12.dp),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Surface(
                                                            color = if (!gf.isActive) MC.Surface3.copy(alpha = 0.4f)
                                                            else if (gf.type == "polygon") MC.StatusOnline.copy(alpha = 0.15f)
                                                            else MC.AccentPrimary.copy(alpha = 0.15f),
                                                            shape = CircleShape,
                                                            modifier = Modifier.size(36.dp)
                                                        ) {
                                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    imageVector = if (gf.type == "polygon") Icons.Default.Polyline else Icons.Default.Adjust,
                                                                    contentDescription = null,
                                                                    tint = if (!gf.isActive) MC.TextTertiary
                                                                    else if (gf.type == "polygon") MC.StatusOnline
                                                                    else MC.AccentPrimary,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }

                                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Text(
                                                                    text = gf.name,
                                                                    color = if (gf.isActive) MC.TextPrimary else MC.TextTertiary,
                                                                    style = MaterialTheme.typography.titleSmall
                                                                )
                                                                StatusBadge(
                                                                    text = if (gf.isActive) "Active" else "Paused",
                                                                    color = if (gf.isActive) MC.StatusOnline else MC.TextTertiary
                                                                )
                                                            }

                                                            val typeLabel = if (gf.type == "polygon") "Polygon (${gf.points.size} pts)" else "Circle (${gf.radiusMeters.roundToInt()}m)"
                                                            val targetDev = devices.find { d -> d.id == gf.targetDeviceId }
                                                            Text(
                                                                text = "$typeLabel • Target: ${targetDev?.name ?: "All Fleet"}",
                                                                color = MC.TextSecondary,
                                                                style = MaterialTheme.typography.bodySmall
                                                            )
                                                        }
                                                    }

                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Switch(
                                                            checked = gf.isActive,
                                                            onCheckedChange = { viewModel.toggleGeofenceActive(gf.id) },
                                                            colors = SwitchDefaults.colors(
                                                                checkedThumbColor = MC.StatusOnline,
                                                                checkedTrackColor = MC.StatusOnline.copy(alpha = 0.3f),
                                                                uncheckedThumbColor = MC.TextTertiary,
                                                                uncheckedTrackColor = MC.Surface3
                                                            )
                                                        )
                                                        IconButton(onClick = { viewModel.deleteGeofence(gf.id) }) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Delete Geofence", tint = MC.StatusOffline)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

