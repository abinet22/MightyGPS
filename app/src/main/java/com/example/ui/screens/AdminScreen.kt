package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.User
import com.example.ui.viewmodel.TraccarViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: TraccarViewModel,
    onNavigateBack: () -> Unit
) {
    val usersList by viewModel.usersList.collectAsState()
    
    // Provision form states
    var showAddUserDialog by remember { mutableStateOf(false) }
    var newUserName by remember { mutableStateOf("") }
    var newUserEmail by remember { mutableStateOf("") }
    var newUserPrivileged by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.fetchTenantUsers()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SaaS Admin Console", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Manage Multi-Tenant Subgroups & Operators", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Return", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddUserDialog = true },
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Provision User")
            }
        },
        containerColor = Color(0xFF020617)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Enterprise metrics overview row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    modifier = Modifier.weight(1.0f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Managed Tenants", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        Text(usersList.size.toString(), color = Color(0xFF3B82F6), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    modifier = Modifier.weight(1.0f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("SaaS Tier", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        Text("Premium Enterprise", color = Color(0xFF10B981), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            Text("Authorized Operators & Tenants", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))

            if (usersList.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AccountBox, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No sub-users registered.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(usersList) { user ->
                        UserAdminCard(
                            user = user,
                            onDelete = {
                                viewModel.deleteUser(user.id, user.name)
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal provision dialogue
    if (showAddUserDialog) {
        AlertDialog(
            onDismissRequest = { showAddUserDialog = false },
            title = { Text("Delegate Organization User", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Provision a new operator handle onto this enterprise tenant partition.", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                    
                    OutlinedTextField(
                        value = newUserName,
                        onValueChange = { newUserName = it },
                        label = { Text("Full Name / Group Name") },
                        placeholder = { Text("e.g. John Doe, Fleet B Manager") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newUserEmail,
                        onValueChange = { newUserEmail = it },
                        label = { Text("Email Handle") },
                        placeholder = { Text("e.g. operator@tenant.com") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Grant Tenant Admin Rights?", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = newUserPrivileged,
                            onCheckedChange = { newUserPrivileged = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF2563EB))
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newUserName.isNotBlank() && newUserEmail.isNotBlank()) {
                            viewModel.addNewUser(newUserName, newUserEmail, newUserPrivileged)
                            showAddUserDialog = false
                            newUserName = ""
                            newUserEmail = ""
                            newUserPrivileged = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Provision User", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddUserDialog = false }) {
                    Text("Abort", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

@Composable
fun UserAdminCard(
    user: User,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF1E293B), shape = androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (user.administrator) Icons.Default.Star else Icons.Default.Person,
                    contentDescription = null,
                    tint = if (user.administrator) Color(0xFFF59E0B) else Color(0xFF3B82F6),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user.name, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(user.email, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            if (user.id != 101L) { // Prevent deleting primary mock admin for demo security
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Revoke Role", tint = Color(0xFFEF4444))
                }
            } else {
                Text(
                    text = "PRIMARY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}
