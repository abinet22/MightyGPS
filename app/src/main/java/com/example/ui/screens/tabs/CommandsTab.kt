package com.example.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Device
import com.example.ui.screens.components.EmptyStateView
import com.example.ui.screens.components.StatusBadge
import com.example.ui.theme.MC
import com.example.ui.viewmodel.TraccarViewModel

@Composable
fun CommandsTab(
    viewModel: TraccarViewModel,
    devices: List<Device>,
    modifier: Modifier = Modifier
) {
    val commandsLog by viewModel.commandsLog.collectAsState()

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
                text = "Issue over-the-air instruction signals directly to telemetry unit transponders.",
                style = MaterialTheme.typography.bodySmall,
                color = MC.TextSecondary
            )
        }

        var selectedCommandDevId by remember { mutableStateOf<Long?>(devices.firstOrNull()?.id) }
        var selectedCommandType by remember { mutableStateOf("Engine Fuel Cut") }
        var commandPayload by remember { mutableStateOf("RELAY_1=OFF") }

        Card(
            colors = CardDefaults.cardColors(containerColor = MC.Surface1),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = viewModel.translate("select_device"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MC.TextPrimary
                )

                // Target Select row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    devices.take(3).forEach { dev ->
                        val active = selectedCommandDevId == dev.id
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedCommandDevId = dev.id },
                            shape = RoundedCornerShape(8.dp),
                            color = if (active) MC.AccentPrimary else MC.Surface2,
                            tonalElevation = if (active) 4.dp else 0.dp
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dev.name,
                                    color = if (active) MC.TextPrimary else MC.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MC.Surface3)

                Text(
                    text = "${viewModel.translate("engine_status")} / ${viewModel.translate("command_payload")}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MC.TextPrimary
                )
                
                val commandsTemplates = listOf(
                    "Engine Fuel Cut" to "RELAY_1=OFF (Ignition Lock)",
                    "Engine Resume" to "RELAY_1=ON (De-restrict ignition)",
                    "Hardware Reboot" to "SYS_REBOOT_FORCE=1",
                    "Poll GPS (Ping)" to "QUERY_POLL_INTERVAL=5s"
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Engine Fuel Cut", "Engine Resume", "Poll GPS (Ping)").forEach { cmd ->
                        val active = selectedCommandType == cmd
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { 
                                    selectedCommandType = cmd
                                    commandPayload = commandsTemplates.find { it.first == cmd }?.second ?: ""
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (active) MC.StatusOnline else MC.Surface2,
                            tonalElevation = if (active) 4.dp else 0.dp
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cmd,
                                    color = if (active) MC.TextPrimary else MC.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = commandPayload,
                    onValueChange = { commandPayload = it },
                    label = { Text("Command Raw Payload") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MC.TextPrimary,
                        unfocusedTextColor = MC.TextPrimary,
                        focusedBorderColor = MC.AccentPrimary,
                        unfocusedBorderColor = MC.Surface3,
                        focusedContainerColor = MC.Surface2,
                        unfocusedContainerColor = MC.Surface2
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        selectedCommandDevId?.let { devId ->
                            viewModel.sendDeviceCommand(devId, selectedCommandType, commandPayload)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MC.AccentPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = MC.TextPrimary, modifier = Modifier.size(16.dp))
                        Text(
                            text = viewModel.translate("send_command"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MC.TextPrimary
                        )
                    }
                }
            }
        }

        // Sent log history
        Text(
            text = viewModel.translate("commands"),
            style = MaterialTheme.typography.titleMedium,
            color = MC.TextPrimary
        )

        if (commandsLog.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Terminal,
                title = "No commands dispatched yet",
                subtitle = "Executed, queued, and acknowledged telemetry commands will be listed here."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
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
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = log.deviceName,
                                    color = MC.TextPrimary,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Payload: ${log.payload}",
                                    color = MC.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
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
