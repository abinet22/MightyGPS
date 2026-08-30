package com.example.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ConsolidatedAlert
import com.example.data.model.GeofenceAlert
import com.example.ui.screens.components.EmptyStateView
import com.example.ui.screens.components.StatusBadge
import com.example.ui.theme.MC
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val headerText = if (appLanguage == "am") "የቀጥታ ደህንነት ማንቂያዎች መዝገብ" else if (appLanguage == "om") "Gabaasa Akeekkachiisa Nageenyaa" else "Live Security Alerts Log"
        val descText = if (appLanguage == "am") "ዝርዝር የመከታተያ መተላለፍ ማህደሮች፣ ንቁ የክልል ጥሰቶች እና የፍጥነት ማንቂያዎች ታሪክ።" else if (appLanguage == "om") "Galmeewwan daangaa cabsuu konkolaataa, daangaa hojii fi akeekkachiisa saffisaa." else "In-depth telemetry tracker breach archives, active boundaries violations, and speed transponder alerts."
        val searchPlaceholder = if (appLanguage == "am") "በመሳሪያ ስም ወይም ክልል ይፈልጉ..." else if (appLanguage == "om") "Maqaa konkolaata ykn daangaan barbaadi..." else "Filter by device label or zone..."
        val clearText = if (appLanguage == "am") "ማህደር አጽዳ" else if (appLanguage == "om") "Galmee Haqii" else "Clear Archive"

        Column {
            Text(
                text = headerText,
                style = MaterialTheme.typography.headlineSmall,
                color = MC.TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = descText,
                style = MaterialTheme.typography.bodySmall,
                color = MC.TextSecondary
            )
        }

        // Local filter search query
        var alertSearchQuery by remember { mutableStateOf("") }
        var activeSeverityFilter by remember { mutableStateOf("ALL") } // "ALL", "ENTERED", "EXITED"

        // Search textfield
        OutlinedTextField(
            value = alertSearchQuery,
            onValueChange = { alertSearchQuery = it },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MC.TextSecondary) },
            placeholder = { Text(searchPlaceholder, color = MC.TextTertiary, style = MaterialTheme.typography.bodySmall) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MC.TextPrimary,
                unfocusedTextColor = MC.TextPrimary,
                focusedContainerColor = MC.Surface1,
                unfocusedContainerColor = MC.Surface1,
                focusedBorderColor = MC.AccentPrimary,
                unfocusedBorderColor = MC.Surface3
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Severity filters Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "ALL" to if (appLanguage == "am") "ሁሉም" else if (appLanguage == "om") "Hunda" else "All Events",
                "ENTERED" to if (appLanguage == "am") "መግቢያ ብቻ" else if (appLanguage == "om") "Galfata Qofa" else "Entered Area",
                "EXITED" to if (appLanguage == "am") "መውጫ ብቻ" else if (appLanguage == "om") "Bahinsa Qofa" else "Exited Area"
            ).forEach { (id, filterLabel) ->
                val active = activeSeverityFilter == id
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeSeverityFilter = id },
                    shape = RoundedCornerShape(8.dp),
                    color = if (active) MC.AccentPrimary else MC.Surface1,
                    tonalElevation = if (active) 4.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filterLabel,
                            color = if (active) MC.TextPrimary else MC.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
            EmptyStateView(
                icon = Icons.Default.Notifications,
                title = if (appLanguage == "am") "ምንም የደህንነት ማንቂያ ክስተት አልተገኘም።" else if (appLanguage == "om") "Akeekkachiisni argame hin jiru." else "No security alert events recorded",
                subtitle = "Security incidents, geofence boundary events, and speed violations will appear here."
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredAlerts) { alert ->
                    val isEnteredType = alert.isEntered
                    val tagColor = if (isEnteredType) MC.StatusOnline else MC.StatusOffline
                    val bgGrad = if (isEnteredType) MC.StatusOnline.copy(alpha = 0.08f) else MC.StatusOffline.copy(alpha = 0.08f)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MC.Surface1),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .background(bgGrad)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(tagColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = alert.deviceName,
                                        color = MC.TextPrimary,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(alert.timestamp))
                                    Text(
                                        text = timeStr,
                                        color = MC.TextTertiary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = alert.message,
                                    color = MC.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                StatusBadge(
                                    text = alert.alertType,
                                    color = tagColor
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onClearAlertHistory,
                colors = ButtonDefaults.buttonColors(containerColor = MC.Surface2),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "clear log",
                        tint = MC.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = clearText,
                        color = MC.TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
