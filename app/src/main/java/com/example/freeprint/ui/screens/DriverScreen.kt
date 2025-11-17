package com.example.freeprint.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.freeprint.model.Driver
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriversScreen(printerViewModel: PrinterViewModel) {
    val drivers by printerViewModel.drivers.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // State for controlling the new bottom sheet
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    // State for confirmation dialogs
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showDeleteUnusedDialog by remember { mutableStateOf(false) }

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri: Uri? ->
            uri?.let {
                printerViewModel.importDriversFromDirectory(it)
                Toast.makeText(context, "Importing drivers...", Toast.LENGTH_SHORT).show()
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Manage Drivers") })
        },
        // A Box to allow placing FABs in different corners
        floatingActionButton = {
            Box(modifier = Modifier.fillMaxSize()) {
                // "Add" FAB in the bottom right (standard position)
                FloatingActionButton(
                    onClick = { directoryPickerLauncher.launch(null) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Import Drivers")
                }

                // --- NEW: "Cleanup" FAB in the bottom left ---
                if (drivers.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = { showBottomSheet = true },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Delete, "Cleanup Drivers")
                    }
                }
            }
        }
    ) { innerPadding ->

        // --- NEW: Bottom Sheet for Cleanup Options ---
        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState
            ) {
                // --- Option 1: Delete Unused ---
                ListItem(
                    headlineContent = { Text("Delete Unused Drivers") },
                    supportingContent = { Text("Removes drivers that are not assigned to any printer.") },
                    leadingContent = {
                        Icon(
                            Icons.Default.CleaningServices,
                            contentDescription = "Delete Unused"
                        )
                    },
                    modifier = Modifier.clickable {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                showBottomSheet = false
                                showDeleteUnusedDialog = true
                            }
                        }
                    }
                )

                // --- Option 2: Delete All ---
                ListItem(
                    headlineContent = { Text("Delete All Drivers") },
                    supportingContent = { Text("Removes all imported drivers from the app.") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.DeleteForever,
                            contentDescription = "Delete All",
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                showBottomSheet = false
                                showDeleteAllDialog = true
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // --- Confirmation Dialog for Deleting All ---
        if (showDeleteAllDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAllDialog = false },
                title = { Text("Confirm Delete All") },
                text = { Text("Are you sure you want to delete all ${drivers.size} drivers? This cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            printerViewModel.removeAllDrivers()
                            showDeleteAllDialog = false
                            Toast.makeText(context, "All drivers deleted.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete All") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel") }
                }
            )
        }

        // --- Confirmation Dialog for Deleting Unused ---
        if (showDeleteUnusedDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteUnusedDialog = false },
                title = { Text("Confirm Delete Unused") },
                text = { Text("This will permanently delete all drivers that are not currently assigned to a printer. Continue?") },
                confirmButton = {
                    Button(
                        onClick = {
                            printerViewModel.removeUnusedDrivers()
                            showDeleteUnusedDialog = false
                            Toast.makeText(context, "Unused drivers deleted.", Toast.LENGTH_SHORT).show()
                        }
                    ) { Text("Confirm") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteUnusedDialog = false }) { Text("Cancel") }
                }
            )
        }


        // Main content display
        if (drivers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No drivers imported.\nPress the '+' button to select a folder containing driver files.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(drivers, key = { it.id }) { driver ->
                    DriverItem(
                        driver = driver,
                        onDelete = { printerViewModel.removeDriver(driver) }
                    )
                }
                // Spacer for the FABs
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
private fun DriverItem(driver: Driver, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(driver.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "File: ${driver.originalFileName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Driver",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
