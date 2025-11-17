package com.example.freeprint.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.freeprint.model.PrinterInfo
import com.example.freeprint.utils.PpdOption
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintSettingsScreen(
    navController: NavController,
    filePath: String?,
    fileViewModel: FilePickerViewModel,
    printerViewModel: PrinterViewModel
) {
    val context = LocalContext.current
    val printers by printerViewModel.savedPrinters.collectAsStateWithLifecycle()
    val uiState by printerViewModel.uiState.collectAsStateWithLifecycle()

    var selectedPrinter by remember { mutableStateOf<PrinterInfo?>(null) }
    var selectedOptions by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var extraSettingsExpanded by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            printerViewModel.clearPrinterOptionsOnScreenExit()
        }
    }

    val decodedPath = remember(filePath) {
        try {
            filePath?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
        } catch (e: Exception) {
            Log.e("PrintSettings", "Failed to decode file path: $filePath", e)
            null
        }
    }

    val fileToPrint = remember(decodedPath) {
        fileViewModel.getFileByPath(decodedPath)
    }

    LaunchedEffect(selectedPrinter) {
        printerViewModel.loadPrinterOptions(selectedPrinter?.driverId)
    }

    LaunchedEffect(uiState.printerOptions) {
        val defaultOptions = uiState.printerOptions.associate { option ->
            option.keyword to option.defaultChoice
        }
        selectedOptions = defaultOptions
    }

    if (fileToPrint == null) {
        Text("Error: File not found.", modifier = Modifier.padding(16.dp))
        LaunchedEffect(decodedPath) {
            Toast.makeText(context, "Could not load file details.", Toast.LENGTH_LONG).show()
            navController.popBackStack()
        }
        return
    }

    // --- NEW: Separate options into essential and extra ---
    val (essentialOptions, extraOptions) = uiState.printerOptions.partition {
        it.keyword in listOf("PageSize", "Orientation", "Copies")
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(text = "Print Settings", style = MaterialTheme.typography.headlineMedium)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("File: ${fileToPrint.name}", style = MaterialTheme.typography.titleMedium)
                    Text("Size: ${fileToPrint.size / 1024} KB", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            PrinterSelectionDropdown(
                printers = printers,
                selectedPrinter = selectedPrinter,
                onPrinterSelected = { printer ->
                    selectedPrinter = printer
                }
            )
        }

        if (uiState.isLoadingOptions) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Loading printer options...")
                }
            }
        } else {
            if (selectedPrinter != null && selectedPrinter?.driverId == null) {
                item {
                    Text(
                        "This printer has no driver assigned. Please assign one in the 'Printers' screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // --- NEW: Render essential options directly ---
            items(essentialOptions, key = { "essential_${it.keyword}" }) { option ->
                PpdOptionDropdown(
                    option = option,
                    currentSelection = selectedOptions[option.keyword] ?: "",
                    onOptionSelected = { choiceKeyword ->
                        selectedOptions = selectedOptions + (option.keyword to choiceKeyword)
                    }
                )
            }

            // --- NEW: Collapsible section for extra settings ---
            if (extraOptions.isNotEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { extraSettingsExpanded = !extraSettingsExpanded }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Extra Settings", style = MaterialTheme.typography.titleSmall)
                                Icon(
                                    imageVector = if (extraSettingsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (extraSettingsExpanded) "Collapse" else "Expand"
                                )
                            }
                            AnimatedVisibility(visible = extraSettingsExpanded) {
                                Column(
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    extraOptions.forEach { option ->
                                        PpdOptionDropdown(
                                            option = option,
                                            currentSelection = selectedOptions[option.keyword] ?: "",
                                            onOptionSelected = { choiceKeyword ->
                                                selectedOptions = selectedOptions + (option.keyword to choiceKeyword)
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

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    selectedPrinter?.let { printer ->
                        printerViewModel.addPrintJob(fileToPrint, printer, selectedOptions)
                        Toast.makeText(context, "Print job added to queue.", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    } ?: run {
                        Toast.makeText(context, "Please select a printer.", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = selectedPrinter != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("ADD TO PRINT QUEUE")
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrinterSelectionDropdown(
    printers: List<PrinterInfo>,
    selectedPrinter: PrinterInfo?,
    onPrinterSelected: (PrinterInfo) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { isExpanded = it }
    ) {
        OutlinedTextField(
            value = selectedPrinter?.name ?: "Select a Printer",
            onValueChange = {},
            readOnly = true,
            label = { Text("Printer") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, "Select Printer") },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false }
        ) {
            printers.forEach { printer ->
                DropdownMenuItem(
                    text = { Text(printer.name) },
                    onClick = {
                        onPrinterSelected(printer)
                        isExpanded = false
                    }
                )
            }
            if (printers.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No printers found. Add one first.") },
                    onClick = {},
                    enabled = false
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PpdOptionDropdown(
    option: PpdOption,
    currentSelection: String,
    onOptionSelected: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val selectedChoiceDisplay = option.choices.find { it.keyword == currentSelection }?.displayName ?: "Select..."

    Column {
        Text(option.displayName, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
        ExposedDropdownMenuBox(
            expanded = isExpanded,
            onExpandedChange = { isExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedChoiceDisplay,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, "Select") },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = isExpanded,
                onDismissRequest = { isExpanded = false }
            ) {
                option.choices.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(choice.displayName) },
                        onClick = {
                            onOptionSelected(choice.keyword)
                            isExpanded = false
                        }
                    )
                }
            }
        }
    }
}
