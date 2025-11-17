package com.example.freeprint.ui.screens

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.freeprint.data.models.PrintFile
import com.example.freeprint.ui.components.FileItemRow
import com.example.freeprint.utils.FileUtils
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerScreen(
    navController: NavController,
    fileViewModel: FilePickerViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val files by fileViewModel.files.collectAsState()
    var selectedFile by remember { mutableStateOf<PrintFile?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        fileViewModel.loadFiles()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let { fileUri ->
                coroutineScope.launch {
                    isLoading = true
                    try {
                        val displayName = FileUtils.getFileName(context, fileUri)
                            ?: fileUri.lastPathSegment
                            ?: "imported_file_${System.currentTimeMillis()}"

                        val destinationDir = File(context.filesDir, "print_files")
                        if (!destinationDir.exists()) destinationDir.mkdirs()
                        val destinationFile = File(destinationDir, displayName)

                        FileUtils.copyUriToFile(context, fileUri, destinationFile)

                        if (!destinationFile.exists() || destinationFile.length() == 0L) {
                            throw IOException("Failed to copy file or file is empty.")
                        }

                        fileViewModel.loadFiles()
                        val newPrintFile = fileViewModel.getFileByPath(destinationFile.absolutePath)
                        if (newPrintFile != null) {
                            selectedFile = newPrintFile
                            Toast.makeText(context, "${newPrintFile.name} added", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("FilePickerScreen", "Error processing file URI: $fileUri", e)
                        Toast.makeText(context, "Error processing file.", Toast.LENGTH_LONG).show()
                    } finally {
                        isLoading = false
                    }
                }
            }
        }
    )

    val supportedMimeTypes = remember {
        arrayOf("application/pdf", "image/jpeg", "image/png", "text/plain")
    }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(
                    onClick = { filePickerLauncher.launch(supportedMimeTypes) },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add File")
                }

                ExtendedFloatingActionButton(
                    text = { Text("Print") },
                    icon = { Icon(Icons.Default.Print, "Print Selected File") },
                    onClick = {
                        selectedFile?.let { fileToPrint ->
                            val encodedPath = URLEncoder.encode(fileToPrint.path, StandardCharsets.UTF_8.toString())
                            navController.navigate("print_settings/$encodedPath")
                        } ?: run {
                            Toast.makeText(context, "Please select a file to print.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    expanded = selectedFile != null
                )
            }
        }
    ) { innerPadding ->
        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            files.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No files added yet.\nPress the '+' button to import a file.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(top = 8.dp)
                ) {
                    items(files, key = { it.path }) { file ->
                        FileItemRow(
                            file = file,
                            isSelected = selectedFile?.path == file.path,
                            onClick = {
                                selectedFile = if (selectedFile?.path == file.path) null else file
                            },
                            onEdit = {
                                val encodedPath = URLEncoder.encode(file.path, StandardCharsets.UTF_8.toString())
                                navController.navigate("print_settings/$encodedPath")
                            },
                            onDelete = {
                                if (selectedFile?.path == file.path) {
                                    selectedFile = null
                                }
                                fileViewModel.deleteFile(file)
                                Toast.makeText(context, "${file.name} deleted", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(160.dp)) // Spacer for FABs
                    }
                }
            }
        }
    }
}
