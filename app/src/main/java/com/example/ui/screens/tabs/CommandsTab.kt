package com.example.ui.screens.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Device
import com.example.data.model.TraccarCommandType
import com.example.ui.screens.components.EmptyStateView
import com.example.ui.screens.components.StatusBadge
import com.example.ui.theme.MC
import com.example.ui.viewmodel.TraccarViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommandsTab(
    viewModel: TraccarViewModel,
    devices: List<Device>,
    modifier: Modifier = Modifier
) {
    val commandsLog by viewModel.commandsLog.collectAsState()

    var deviceSearchQuery by remember { mutableStateOf("") }
    var selectedCommandDevId by remember { mutableStateOf<Long?>(devices.firstOrNull()?.id) }
    var selectedCommand by remember { mutableStateOf(TraccarCommandType.ENGINE_STOP) }
    var commandValue by remember { mutableStateOf("") }

    // Auto select first device if none selected or device removed
    LaunchedEffect(devices) {
        if (selectedCommandDevId == null || devices.none { it.id == selectedCommandDevId }) {
            selectedCommandDevId = devices.firstOrNull()?.id
        }
    }

    val filteredDevices = remember(devices, deviceSearchQuery) {
        if (deviceSearchQuery.isBlank()) devices else {
            devices.filter { dev ->
                dev.name.contains(deviceSearchQuery, ignoreCase = true) ||
                dev.uniqueId.contains(deviceSearchQuery, ignoreCase = true) ||
                dev.model?.contains(deviceSearchQuery, ignoreCase = true) == true ||
                dev.phone?.contains(deviceSearchQuery, ignoreCase = true) == true ||
                dev.attributes["plate"]?.toString()?.contains(deviceSearchQuery, ignoreCase = true) == true ||
                dev.attributes["license_plate"]?.toString()?.contains(deviceSearchQuery, ignoreCase = true) == true
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text(
                text = viewModel.translate("command"),
                style = MaterialTheme.typography.headlineSmall,
                color = MC.TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Issue structured over-the-air commands directly to telemetry hardware transponders.",
                style = MaterialTheme.typography.bodySmall,
                color = MC.TextSecondary
            )
        }

        // Command Builder Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MC.Surface1),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section: Target Device Picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = viewModel.translate("select_device"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MC.TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    val selectedDevice = devices.find { it.id == selectedCommandDevId }
                    if (selectedDevice != null) {
                        Text(
                            text = selectedDevice.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MC.AccentPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Search field if many devices
                if (devices.size > 3) {
                    OutlinedTextField(
                        value = deviceSearchQuery,
                        onValueChange = { deviceSearchQuery = it },
                        placeholder = { Text("Search vehicle or IMEI...", style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MC.TextSecondary, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (deviceSearchQuery.isNotBlank()) {
                                IconButton(onClick = { deviceSearchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = MC.TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MC.TextPrimary,
                            unfocusedTextColor = MC.TextPrimary,
                            focusedBorderColor = MC.AccentPrimary,
                            unfocusedBorderColor = MC.Surface3,
                            focusedContainerColor = MC.Surface2,
                            unfocusedContainerColor = MC.Surface2
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )
                }

                // Searchable LazyRow for Devices
                if (filteredDevices.isEmpty()) {
                    Text(
                        text = "No matching devices found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MC.TextSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(filteredDevices) { dev ->
                            val isSelected = selectedCommandDevId == dev.id
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MC.AccentPrimary.copy(alpha = 0.2f) else MC.Surface2
                                ),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) MC.AccentPrimary else MC.Surface3
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .clickable { selectedCommandDevId = dev.id }
                                    .widthIn(min = 110.dp, max = 160.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (dev.status == "online") MC.StatusOnline else MC.StatusOffline)
                                    )
                                    Column {
                                        Text(
                                            text = dev.name,
                                            color = if (isSelected) MC.TextPrimary else MC.TextSecondary,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = dev.uniqueId,
                                            color = MC.TextTertiary,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MC.Surface3)

                // Section: Command Type Selector
                Text(
                    text = "Command Type",
                    style = MaterialTheme.typography.titleSmall,
                    color = MC.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TraccarCommandType.entries.forEach { cmdType ->
                        val isSelected = selectedCommand == cmdType
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedCommand = cmdType
                                if (!cmdType.requiresValue) {
                                    commandValue = ""
                                }
                            },
                            label = {
                                Text(
                                    text = cmdType.displayLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MC.Surface2,
                                labelColor = MC.TextSecondary,
                                selectedContainerColor = MC.AccentPrimary,
                                selectedLabelColor = MC.TextPrimary,
                                selectedLeadingIconColor = MC.TextPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MC.Surface3,
                                selectedBorderColor = MC.AccentPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                // Value input if command requires attributes
                if (selectedCommand.requiresValue) {
                    OutlinedTextField(
                        value = commandValue,
                        onValueChange = { commandValue = it },
                        label = { Text(selectedCommand.valuePrompt) },
                        placeholder = {
                            Text(
                                if (selectedCommand == TraccarCommandType.POSITION_PERIODIC) "e.g. 30s, 60s, 300s" else "e.g. 80",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MC.TextPrimary,
                            unfocusedTextColor = MC.TextPrimary,
                            focusedBorderColor = MC.AccentPrimary,
                            unfocusedBorderColor = MC.Surface3,
                            focusedContainerColor = MC.Surface2,
                            unfocusedContainerColor = MC.Surface2
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Dispatch Button
                val canSend = selectedCommandDevId != null && (!selectedCommand.requiresValue || commandValue.isNotBlank())
                Button(
                    onClick = {
                        selectedCommandDevId?.let { devId ->
                            viewModel.sendDeviceCommand(
                                deviceId = devId,
                                commandType = selectedCommand,
                                value = commandValue.takeIf { it.isNotBlank() }
                            )
                        }
                    },
                    enabled = canSend,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MC.AccentPrimary,
                        disabledContainerColor = MC.Surface3
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = null,
                            tint = if (canSend) MC.TextPrimary else MC.TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = viewModel.translate("send_command"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (canSend) MC.TextPrimary else MC.TextTertiary
                        )
                    }
                }
            }
        }

        // Sent log history
        Text(
            text = viewModel.translate("commands"),
            style = MaterialTheme.typography.titleMedium,
            color = MC.TextPrimary,
            fontWeight = FontWeight.SemiBold
        )

        if (commandsLog.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Terminal,
                title = "No commands dispatched yet",
                subtitle = "Executed, queued, and acknowledged telemetry commands will be listed here."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(commandsLog) { log ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MC.Surface1),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = log.deviceName,
                                        color = MC.TextPrimary,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "• ${log.displayLabel}",
                                        color = MC.AccentPrimary,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Wire type: ${log.commandType}",
                                        color = MC.TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (log.payload != log.commandType && log.payload.isNotBlank()) {
                                        Text(
                                            text = "(Value: ${log.payload})",
                                            color = MC.TextTertiary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                Text(
                                    text = "Time: ${log.timestamp}",
                                    color = MC.TextTertiary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            val badgeColor = when (log.status) {
                                "EXECUTED" -> MC.StatusOnline
                                "QUEUED", "ACKNOWLEDGED" -> MC.StatusIdle
                                "FAILED" -> MC.StatusOffline
                                else -> MC.AccentPrimary
                            }

                            StatusBadge(
                                text = log.status,
                                color = badgeColor
                            )
                        }
                    }
                }
            }
        }
    }
}
