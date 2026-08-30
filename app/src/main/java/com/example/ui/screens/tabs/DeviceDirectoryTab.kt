package com.example.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.db.CachedDevice
import com.example.data.model.Device
import com.example.data.model.Position
import com.example.ui.screens.components.DeviceReportPage
import com.example.ui.screens.components.DeviceRow
import com.example.ui.screens.components.EmptyStateView
import com.example.ui.screens.components.OfflineDeviceRow
import com.example.ui.screens.components.TrackScorecard
import com.example.ui.theme.MC
import com.example.ui.viewmodel.TraccarViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDirectoryTab(
    viewModel: TraccarViewModel,
    devices: List<Device>,
    cachedDevices: List<CachedDevice>,
    realtimePositions: Map<Long, Position>,
    selectedReportDevice: Device?,
    onSelectReportDevice: (Device?) -> Unit,
    onViewOnMap: (Long) -> Unit,
    onViewPlayback: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedStatusFilter by remember { mutableStateOf("All") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val activeReportDev = selectedReportDevice
        if (activeReportDev != null) {
            val position = realtimePositions[activeReportDev.id]
            DeviceReportPage(
                device = activeReportDev,
                position = position,
                viewModel = viewModel,
                onBack = { onSelectReportDevice(null) },
                onViewOnMap = {
                    onViewOnMap(activeReportDev.id)
                },
                onViewPlayback = {
                    onViewPlayback(activeReportDev.id)
                },
                appLanguage = viewModel.appLanguage.value
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Status Scorecards list
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val total = devices.size
                    val online = devices.count { it.status == "online" }
                    val offline = devices.count { it.status == "offline" }

                    TrackScorecard(title = "Total Active", count = total.toString(), color = MC.AccentPrimary, modifier = Modifier.weight(1f))
                    TrackScorecard(title = "Moving/Online", count = online.toString(), color = MC.StatusOnline, modifier = Modifier.weight(1f))
                    TrackScorecard(title = "Parked/Offline", count = offline.toString(), color = MC.StatusOffline, modifier = Modifier.weight(1f))
                }

                // Searchable Filter Bar at the top of vehicle list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search vehicles", tint = MC.AccentPrimary) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = MC.TextSecondary)
                                }
                            }
                        },
                        placeholder = { Text("Search by plate, name, or IMEI...", color = MC.TextTertiary, style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MC.TextPrimary,
                            unfocusedTextColor = MC.TextPrimary,
                            focusedContainerColor = MC.Surface2,
                            unfocusedContainerColor = MC.Surface1,
                            focusedBorderColor = MC.AccentPrimary,
                            unfocusedBorderColor = MC.Surface3
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick Filter Chips (Status & Categories)
                    val categories = remember(devices, cachedDevices) {
                        listOf("All") + (devices.mapNotNull { it.category?.takeIf { c -> c.isNotBlank() } } + cachedDevices.mapNotNull { it.category?.takeIf { c -> c.isNotBlank() } })
                            .map { it.lowercase() }
                            .distinct()
                            .map { it.replaceFirstChar { char -> char.uppercase() } }
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            val isSel = selectedStatusFilter == "All" && selectedCategoryFilter == "All"
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedStatusFilter = "All"; selectedCategoryFilter = "All" },
                                label = { Text("All Assets", style = MaterialTheme.typography.labelSmall, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium) },
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
                        item {
                            val isSel = selectedStatusFilter == "online"
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedStatusFilter = if (isSel) "All" else "online" },
                                leadingIcon = { Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MC.StatusOnline)) },
                                label = { Text("Online", style = MaterialTheme.typography.labelSmall, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium) },
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
                        item {
                            val isSel = selectedStatusFilter == "offline"
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedStatusFilter = if (isSel) "All" else "offline" },
                                leadingIcon = { Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MC.StatusOffline)) },
                                label = { Text("Offline", style = MaterialTheme.typography.labelSmall, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MC.StatusOffline,
                                    selectedLabelColor = MC.TextPrimary,
                                    containerColor = MC.Surface2,
                                    labelColor = MC.TextSecondary
                                ),
                                border = null,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        items(categories.filter { it != "All" }) { cat ->
                            val isSel = selectedCategoryFilter.equals(cat, ignoreCase = true)
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedCategoryFilter = if (isSel) "All" else cat },
                                label = { Text(cat, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium) },
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
                }

                // Unified Offline support notice if data from database cache is shown
                if (devices.isEmpty() && cachedDevices.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MC.StatusIdle.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MC.StatusIdle, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Network offline. Loading local secure cached database coordinates.", style = MaterialTheme.typography.labelSmall, color = MC.StatusIdle)
                        }
                    }
                }

                // Rendering the actual list elements
                if (devices.isEmpty() && cachedDevices.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.DirectionsCar,
                        title = "No active hardware devices configured",
                        subtitle = "Click the (+) FAB button to commission this fleet's first asset tracker."
                    )
                } else {
                    val matchDevice = { name: String, imei: String, status: String, category: String?, model: String?, phone: String?, attrs: Map<String, Any> ->
                        val matchesQuery = if (searchQuery.isBlank()) true else {
                            name.contains(searchQuery, ignoreCase = true) ||
                            imei.contains(searchQuery, ignoreCase = true) ||
                            model?.contains(searchQuery, ignoreCase = true) == true ||
                            phone?.contains(searchQuery, ignoreCase = true) == true ||
                            attrs["plate"]?.toString()?.contains(searchQuery, ignoreCase = true) == true ||
                            attrs["license_plate"]?.toString()?.contains(searchQuery, ignoreCase = true) == true ||
                            attrs["reg"]?.toString()?.contains(searchQuery, ignoreCase = true) == true ||
                            attrs["customName"]?.toString()?.contains(searchQuery, ignoreCase = true) == true ||
                            attrs["vehicleName"]?.toString()?.contains(searchQuery, ignoreCase = true) == true
                        }
                        val matchesCat = if (selectedCategoryFilter == "All") true else {
                            category?.equals(selectedCategoryFilter, ignoreCase = true) == true
                        }
                        val matchesStat = if (selectedStatusFilter == "All") true else {
                            status.equals(selectedStatusFilter, ignoreCase = true)
                        }
                        matchesQuery && matchesCat && matchesStat
                    }

                    val filteredDevices = devices.filter { dev ->
                        matchDevice(dev.name, dev.uniqueId, dev.status, dev.category, dev.model, dev.phone, dev.attributes)
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (filteredDevices.isNotEmpty()) {
                            items(filteredDevices, key = { it.id }) { device ->
                                val position = realtimePositions[device.id]
                                DeviceRow(
                                    device = device,
                                    isAdmin = viewModel.sessionManager.isAdmin,
                                    position = position,
                                    onSelect = {
                                        onSelectReportDevice(device)
                                    },
                                    onDelete = {
                                        viewModel.removeDevice(device.id, device.name)
                                    }
                                )
                            }
                        } else {
                            // Load cached items fallback
                            val filteredOffline = cachedDevices.filter { cached ->
                                matchDevice(cached.name, cached.uniqueId, cached.status, cached.category, null, null, emptyMap())
                            }
                            items(filteredOffline, key = { it.id }) { cached ->
                                OfflineDeviceRow(
                                    cached = cached,
                                    onSelect = {
                                        val dummyDev = Device(
                                            id = cached.id,
                                            name = cached.name,
                                            uniqueId = cached.uniqueId,
                                            status = "offline"
                                        )
                                        onSelectReportDevice(dummyDev)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
