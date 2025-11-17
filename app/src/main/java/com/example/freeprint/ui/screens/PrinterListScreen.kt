package com.example.freeprint.ui.screens

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.freeprint.model.PrinterInfo
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PrinterListScreen(
    navController: NavController,
    printerViewModel: PrinterViewModel = viewModel()
) {
    val savedPrinters by printerViewModel.savedPrinters.collectAsStateWithLifecycle()
    val uiState by printerViewModel.uiState.collectAsStateWithLifecycle()
    val drivers by printerViewModel.drivers.collectAsStateWithLifecycle()

    val locationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)

    val requestPermissionLauncher = { locationPermissionState.launchPermissionRequest() }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(if (uiState.isDiscovering) "Stop Scan" else "Find Printers") },
                icon = {
                    if (uiState.isDiscovering) Icon(Icons.Default.Stop, "Stop Scan")
                    else Icon(Icons.Default.Search, "Find Printers")
                },
                onClick = {
                    if (uiState.isDiscovering) {
                        printerViewModel.stopDiscovery()
                    } else {
                        if (locationPermissionState.status.isGranted) {
                            printerViewModel.startComprehensiveScan()
                        } else {
                            requestPermissionLauncher()
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            when {
                locationPermissionState.status.isGranted -> {
                    PrinterListContent(
                        uiState = uiState,
                        savedPrinters = savedPrinters,
                        drivers = drivers,
                        onAddPrinter = { printer -> printerViewModel.addDiscoveredPrinter(printer) },
                        onRemovePrinter = { printer -> printerViewModel.removePrinter(printer) },
                        onNavigateToSettings = { printerIp ->
                            navController.navigate("printer_settings/$printerIp")
                        }
                    )
                }
                locationPermissionState.status.shouldShowRationale -> {
                    PermissionDeniedContent(
                        rationaleText = "Location permission is required to scan the Wi-Fi network for printers. Please grant the permission to continue.",
                        buttonText = "Grant Permission",
                        onRequestPermission = requestPermissionLauncher
                    )
                }
                else -> {
                    PermissionDeniedContent(
                        rationaleText = "To discover printers on the network, this app needs location access. This is an Android requirement for scanning Wi-Fi networks.",
                        buttonText = "Grant Permission",
                        onRequestPermission = requestPermissionLauncher
                    )
                }
            }
        }
    }
}


@Composable
private fun PrinterListContent(
    uiState: PrinterScreenUiState,
    savedPrinters: List<PrinterInfo>,
    drivers: List<com.example.freeprint.model.Driver>,
    onAddPrinter: (PrinterInfo) -> Unit,
    onRemovePrinter: (PrinterInfo) -> Unit,
    onNavigateToSettings: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- Discovered Printers Section (is unchanged) ---
        if (uiState.isDiscovering) {
            item {
                Text("Discovered Printers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (uiState.discoveredPrinters.isEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text("Searching for printers on the network...")
                    }
                }
            }
            items(uiState.discoveredPrinters, key = { "discovered_${it.hostAddress}" }) { printer ->
                val isAlreadySaved = savedPrinters.any { sp -> sp.hostAddress == printer.hostAddress }
                if (!isAlreadySaved) {
                    DiscoveredPrinterItem(
                        printer = printer,
                        onAddClicked = { onAddPrinter(printer) }
                    )
                }
            }
            if (uiState.discoveredPrinters.any { p -> !savedPrinters.any { sp -> sp.hostAddress == p.hostAddress } }) {
                item {
                    Divider(modifier = Modifier.padding(vertical = 16.dp))
                }
            }
        }

        // --- Saved Printers Section ---
        item {
            Text("Saved Printers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (savedPrinters.isEmpty() && !uiState.isDiscovering) {
            item {
                Text("No printers have been saved. Use 'Find Printers' to begin.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            items(savedPrinters, key = { "saved_${it.hostAddress}" }) { printer ->
                var menuExpanded by remember { mutableStateOf(false) }

                Box {
                    PrinterCard(
                        printer = printer,
                        // --- FIX: Pass `originalFileName` instead of `name` ---
                        driverName = drivers.find { it.id == printer.driverId }?.originalFileName,
                        onMoreClick = {
                            menuExpanded = true
                        }
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                menuExpanded = false
                                onNavigateToSettings(printer.hostAddress)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                menuExpanded = false
                                onRemovePrinter(printer)
                            }
                        )
                    }
                }
            }
        }

        // Add padding at the bottom to ensure content is not blocked by the FAB
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun DiscoveredPrinterItem(
    printer: PrinterInfo,
    onAddClicked: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = printer.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "(${printer.hostAddress}:${printer.port})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(16.dp))

            if (printer.isEnriching) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Button(onClick = onAddClicked) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }
    }
}

@Composable
fun PrinterInfoRow(
    printer: PrinterInfo,
    driverName: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(printer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))

            // Network Info
            printer.ssid?.let {
                Text(
                    text = "Network: $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Driver Info
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (driverName == null) {
                    Icon(
                        imageVector = Icons.Default.PriorityHigh,
                        contentDescription = "Caution",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "No Driver Assigned",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = "Driver: $driverName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}


// In PrinterListScreen.kt

@Composable
fun PrinterCard(
    printer: PrinterInfo,
    driverName: String?,
    onMoreClick: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Name and Status Column
            Column(modifier = Modifier.weight(1f)) {
                Text(printer.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = printer.hostAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                // --- NEW: Online/Offline Status Indicator ---
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = if (printer.isOnline) Color(0xFF388E3C) else MaterialTheme.colorScheme.error
                    val statusText = if (printer.isOnline) "Online" else "Offline"

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(color = statusColor, shape = CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor
                    )
                }
            }
            IconButton(onClick = onMoreClick) {
                Icon(Icons.Default.MoreVert, "More options")
            }
        }
        Spacer(Modifier.height(16.dp))
        if (driverName != null) {
            Text("Driver: $driverName", style = MaterialTheme.typography.bodyMedium)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PriorityHigh,
                    "No driver assigned",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "No driver assigned",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    }
}



@Composable
private fun PermissionDeniedContent(rationaleText: String, buttonText: String, onRequestPermission: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(rationaleText, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermission) { Text(buttonText) }
    }
}
