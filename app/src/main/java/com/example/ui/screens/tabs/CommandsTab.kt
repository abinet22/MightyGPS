package com.example.ui.screens.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Device
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
            .padding(16.dp)
    ) {
        Text(
            text = viewModel.translate("command"),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Issue over-the-air instruction signals directly into the telemetry unit transponders.",
            color = Color.Gray,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        var selectedCommandDevId by remember { mutableStateOf<Long?>(devices.firstOrNull()?.id) }
        var selectedCommandType by remember { mutableStateOf("Engine Fuel Cut") }
        var commandPayload by remember { mutableStateOf("RELAY_1=OFF") }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(viewModel.translate("select_device"), color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                // Target Select dropdown simulation (row of buttons of first few devices)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    devices.take(3).forEach { dev ->
                        val active = selectedCommandDevId == dev.id
                        Box(
                            modifier = Modifier
                                .background(if (active) Color(0xFF3B82F6) else Color(0xFF1E293B), CircleShape)
                                .clickable { selectedCommandDevId = dev.id }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(dev.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                Text(viewModel.translate("engine_status") + " / " + viewModel.translate("command_payload"), color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                
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
                        Box(
                            modifier = Modifier
                                .background(if (active) Color(0xFF10B981) else Color(0xFF1E293B), CircleShape)
                                .clickable { 
                                    selectedCommandType = cmd
                                    commandPayload = commandsTemplates.find { it.first == cmd }?.second ?: ""
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(cmd, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedTextField(
                    value = commandPayload,
                    onValueChange = { commandPayload = it },
                    label = { Text("Command Raw Payload") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )

                Button(
                    onClick = {
                        selectedCommandDevId?.let { devId ->
                            viewModel.sendDeviceCommand(devId, selectedCommandType, commandPayload)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(viewModel.translate("send_command") + " 🚀", fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        // Sent log history
        Text(viewModel.translate("commands"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(commandsLog) { log ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(log.deviceName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Payload: ${log.payload}", color = Color.Gray, fontSize = 10.sp)
                            Text("Time: ${log.timestamp}", color = Color.DarkGray, fontSize = 9.sp)
                        }
                        val (badgeBg, badgeText) = when (log.status) {
                            "EXECUTED" -> Color(0x2210B981) to Color(0xFF10B981)
                            "QUEUED", "ACKNOWLEDGED" -> Color(0x22F59E0B) to Color(0xFFF59E0B)
                            "FAILED" -> Color(0x22EF4444) to Color(0xFFEF4444)
                            else -> Color(0x223B82F6) to Color(0xFF3B82F6)
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = badgeBg)
                        ) {
                            Text(
                                text = log.status,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
