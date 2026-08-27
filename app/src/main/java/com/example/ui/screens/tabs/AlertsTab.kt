package com.example.ui.screens.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConsolidatedAlert
import com.example.data.model.GeofenceAlert
import com.example.ui.viewmodel.TraccarViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AlertsTab(
    viewModel: TraccarViewModel,
    geofenceAlertHistory: List<GeofenceAlert>,
    onClearAlertHistory: () -> Unit,
    appLanguage: String,
    modifier: Modifier = Modifier
) {
    val cachedAlerts by viewModel.cachedAlerts.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val headerText = if (appLanguage == "am") "የቀጥታ ደህንነት ማንቂያዎች መዝገብ" else if (appLanguage == "om") "Gabaasa Akeekkachiisa Nageenyaa" else "LIVE SECURITY ALERTS LOG"
        val descText = if (appLanguage == "am") "ዝርዝር የመከታተያ መተላለፍ ማህደሮች፣ ንቁ የክልል ጥሰቶች እና የፍጥነት ማንቂያዎች ታሪክ።" else if (appLanguage == "om") "Galmeewwan daangaa cabsuu konkolaataa, daangaa hojii fi akeekkachiisa saffisaa." else "In-depth telemetry tracker breach archives, active boundaries violations, and speed transponder alerts."
        val searchPlaceholder = if (appLanguage == "am") "በመሳሪያ ስም ወይም ክልል ይፈልጉ..." else if (appLanguage == "om") "Maqaa konkolaata ykn daangaan barbaadi..." else "Filter by device label or zone..."
        val clearText = if (appLanguage == "am") "ማህደር አጽዳ" else if (appLanguage == "om") "Galmee Haqii" else "Clear Archive"
        val emptyText = if (appLanguage == "am") "ምንም የደህንነት ማንቂያ ክስተት አልተገኘም።" else if (appLanguage == "om") "Akeekkachiisni argame hin jiru." else "No security alert events found."

        Text(
            text = headerText,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = descText,
            color = Color.Gray,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Local filter search query
        var alertSearchQuery by remember { mutableStateOf("") }
        var activeSeverityFilter by remember { mutableStateOf("ALL") } // "ALL", "ENTERED", "EXITED"

        // Search textfield
        OutlinedTextField(
            value = alertSearchQuery,
            onValueChange = { alertSearchQuery = it },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            placeholder = { Text(searchPlaceholder, color = Color.Gray, fontSize = 12.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF0F172A),
                unfocusedContainerColor = Color(0xFF0F172A),
                focusedBorderColor = Color(0xFF3B82F6),
                unfocusedBorderColor = Color(0xFF1E293B)
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )

        // Severity filters Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "ALL" to if (appLanguage == "am") "ሁሉም" else if (appLanguage == "om") "Hunda" else "All Events",
                "ENTERED" to if (appLanguage == "am") "መግቢያ ብቻ" else if (appLanguage == "om") "Galfata Qofa" else "Entered Area",
                "EXITED" to if (appLanguage == "am") "መውጫ ብቻ" else if (appLanguage == "om") "Bahinsa Qofa" else "Exited Area"
            ).forEach { (id, filterLabel) ->
                val active = activeSeverityFilter == id
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (active) Color(0xFF3B82F6) else Color(0xFF0F172A),
                            RoundedCornerShape(8.dp)
                        )
                        .border(1.dp, if (active) Color(0xFF3B82F6) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
                        .clickable { activeSeverityFilter = id }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(filterLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        val consolidatedAlerts = remember(geofenceAlertHistory, cachedAlerts) {
            val localOnes = geofenceAlertHistory.map { alert ->
                val isEnt = alert.type == "ENTERED"
                val msg = if (isEnt) {
                    if (appLanguage == "am") "ደህንነቱ የተጠበቀውን ክልል [${alert.geofenceName}] ገብቷል።" 
                    else if (appLanguage == "om") "Daangaa kabajamaa [${alert.geofenceName}] seeneera."
                    else "Entered protected geofence zone [${alert.geofenceName}]."
                } else {
                    if (appLanguage == "am") "ደህንነቱ ከተጠበቀው ክልል [${alert.geofenceName}] ወጥቷል።" 
                    else if (appLanguage == "om") "Daangaa kabajamaa [${alert.geofenceName}] baheera."
                    else "Exited protected geofence zone [${alert.geofenceName}]."
                }
                ConsolidatedAlert(
                    deviceName = alert.deviceName,
                    alertType = alert.type,
                    isEntered = isEnt,
                    message = msg,
                    timestamp = alert.timestamp
                )
            }
            val apiOnes = cachedAlerts.map { dbAlert ->
                val actType = dbAlert.alarmType ?: dbAlert.type
                val isEnt = !actType.lowercase().contains("exit") && !actType.lowercase().contains("cut")
                ConsolidatedAlert(
                    deviceName = dbAlert.deviceName,
                    alertType = actType.uppercase(),
                    isEntered = isEnt,
                    message = dbAlert.message,
                    timestamp = dbAlert.timestamp
                )
            }
            (localOnes + apiOnes).sortedByDescending { it.timestamp }
        }

        val filteredAlerts = consolidatedAlerts.filter { alert ->
            (alertSearchQuery.isEmpty() || 
             alert.deviceName.contains(alertSearchQuery, ignoreCase = true) || 
             alert.message.contains(alertSearchQuery, ignoreCase = true)) &&
            (activeSeverityFilter == "ALL" || 
             (activeSeverityFilter == "ENTERED" && alert.isEntered) || 
             (activeSeverityFilter == "EXITED" && !alert.isEntered))
        }

        if (filteredAlerts.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(emptyText, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filteredAlerts.forEach { alert ->
                    val isEnteredType = alert.isEntered
                    val tagColor = if (isEnteredType) Color(0xFF10B981) else Color(0xFFEF4444)
                    val bgGrad = if (isEnteredType) Color(0x1210B981) else Color(0x12EF4444)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .background(bgGrad)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Dynamic visual severity bar
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(tagColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = alert.deviceName,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(alert.timestamp))
                                    Text(
                                        text = timeStr,
                                        color = Color.Gray,
                                        fontSize = 10.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = alert.message,
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onClearAlertHistory,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "clear log",
                    tint = Color.LightGray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(clearText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
