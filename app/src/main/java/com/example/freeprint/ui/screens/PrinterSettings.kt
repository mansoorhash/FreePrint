package com.example.freeprint.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.freeprint.model.Driver

@Composable
fun PrinterSettings(
    navController: NavController,
    printerIp: String?,
    printerViewModel: PrinterViewModel = viewModel()
) {
    val printer = printerViewModel.getPrinterByIp(printerIp)
    val drivers by printerViewModel.drivers.collectAsStateWithLifecycle()

    if (printer == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Printer not found.", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { navController.popBackStack() }) { Text("Go Back") }
        }
        return
    }

    var currentName by remember(printer.name) { mutableStateOf(printer.name) }
    var currentDriverId by remember(printer.driverId) { mutableStateOf(printer.driverId) }
    var showDriverDialog by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        val updatedPrinter = printer.copy(name = currentName, driverId = currentDriverId)
                        printerViewModel.updatePrinter(updatedPrinter)
                        navController.popBackStack()
                    },
                    enabled = (currentName.isNotBlank() && currentName != printer.name) || (currentDriverId != printer.driverId)
                ) {
                    Text("Save")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Printer Name Field
            SettingsRow(label = "Printer Name") {
                OutlinedTextField(
                    value = currentName,
                    onValueChange = { newName -> currentName = newName },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = currentName.isBlank()
                )
            }

            // Network SSID Field
            SettingsRow(label = "Network SSID") {
                OutlinedTextField(
                    value = printer.ssid ?: "Not Available",
                    onValueChange = {},
                    modifier = Modifier.weight(1f),
                    readOnly = true,
                    colors = TextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledIndicatorColor = MaterialTheme.colorScheme.outline,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    enabled = false
                )
            }

            // Driver Selector Field
            val driverName = drivers.find { it.id == currentDriverId }?.displayName ?: "None"
            SettingsRow(label = "Driver") {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showDriverDialog = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = driverName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // --- FIX: Button logic is now conditional ---
                    if (currentDriverId == null) {
                        // If no driver is selected, show "Select" button
                        Button(onClick = { showDriverDialog = true }) {
                            Text("Select")
                        }
                    } else {
                        // If a driver IS selected, show "Clear" button
                        Button(
                            onClick = { currentDriverId = null },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }
        }
    }

    if (showDriverDialog) {
        DriverSelectionDialog(
            printerName = printer.name,
            drivers = drivers,
            onDismiss = { showDriverDialog = false },
            onDriverSelected = { driverId ->
                currentDriverId = driverId
                showDriverDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverSelectionDialog(
    printerName: String,
    drivers: List<Driver>,
    onDismiss: () -> Unit,
    onDriverSelected: (String?) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredDrivers = if (searchQuery.isBlank()) {
        drivers
    } else {
        drivers.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Select a Driver") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Close")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                // Header with printer name
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "Assign driver for:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        printerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Search drivers...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                            }
                        }
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    item {
                        ListItem(
                            headlineContent = { Text("None (Clear Selection)", fontWeight = FontWeight.Bold) },
                            modifier = Modifier.clickable { onDriverSelected(null) }
                        )
                        Divider()
                    }

                    items(filteredDrivers, key = { it.id }) { driver ->
                        ListItem(
                            headlineContent = { Text(driver.displayName) },
                            supportingContent = { Text(driver.originalFileName) }, // Show filename as extra info
                            modifier = Modifier.clickable { onDriverSelected(driver.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.6f)
        )
        content()
    }
}
