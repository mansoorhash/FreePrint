package com.example.freeprint.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.freeprint.model.Driver
import com.example.freeprint.model.PrinterInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream

class DriverRepository(private val context: Context) {

    private val driversJsonFile = File(context.filesDir, "saved_drivers.json")
    private val driversDir = File(context.filesDir, "drivers").also { it.mkdirs() }
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val _drivers = MutableStateFlow<List<Driver>>(emptyList())
    val drivers = _drivers.asStateFlow()

    // --- CHANGE 1: Simplify the list of known extensions to only PPD ---
    private val knownDriverExtensions = setOf("ppd")

    init {
        loadDriversFromFile()
    }

    suspend fun importDriversFromDirectory(treeUri: Uri) {
        withContext(Dispatchers.IO) {
            val documentTree = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext
            val driverFiles = findDriverFiles(documentTree)
            var importedCount = 0

            Log.d("DriverRepository", "Found ${driverFiles.size} potential PPD driver files.")

            val currentDrivers = _drivers.value.toMutableList()
            for (file in driverFiles) {
                val fileName = file.name ?: continue

                if (currentDrivers.any { it.originalFileName.equals(fileName, ignoreCase = true) }) {
                    Log.d("DriverRepository", "Skipping already imported driver: $fileName")
                    continue
                }

                try {
                    val displayName = context.contentResolver.openInputStream(file.uri)?.use {
                        parseDriverForName(it)
                    } ?: fileName

                    val destinationFile = File(driversDir, fileName)
                    context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                        destinationFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    val newDriver = Driver(
                        displayName = displayName,
                        originalFileName = fileName,
                        internalPath = destinationFile.absolutePath
                    )
                    currentDrivers.add(newDriver)
                    importedCount++
                } catch (e: IOException) {
                    Log.e("DriverRepository", "Failed to import driver file: $fileName", e)
                }
            }

            if (importedCount > 0) {
                _drivers.value = currentDrivers
                saveDriversMetadataToFile(currentDrivers)
                Log.d("DriverRepository", "Successfully imported $importedCount new drivers.")
            }
        }
    }

    /**
     * --- CHANGE 2: Simplify the name parser to only look for PPD keywords ---
     * Reads an InputStream from a PPD file to find the NickName or ModelName.
     */
    private fun parseDriverForName(inputStream: InputStream): String? {
        var nickName: String? = null
        var modelName: String? = null

        val reader = inputStream.bufferedReader()
        // Only read the first 200 lines for efficiency
        for (i in 1..200) {
            val line = reader.readLine() ?: break
            val trimmedLine = line.trim()

            if (trimmedLine.startsWith("*NickName:", ignoreCase = true)) {
                nickName = trimmedLine.substringAfter(":").trim().removeSurrounding("\"")
                break // NickName is the highest priority, so we can stop.
            }
            if (trimmedLine.startsWith("*ModelName:", ignoreCase = true)) {
                modelName = trimmedLine.substringAfter(":").trim().removeSurrounding("\"")
            }
        }
        // Prefer *NickName over *ModelName
        return nickName ?: modelName
    }

    /**
     * --- CHANGE 3: Heavily simplify findDriverFiles to only look for PPD files ---
     * Finds driver files by checking for the ".ppd" extension or by "sniffing"
     * for the PPD magic string.
     */
    private fun findDriverFiles(root: DocumentFile): List<DocumentFile> {
        val files = mutableListOf<DocumentFile>()
        val stack = ArrayDeque<DocumentFile>()
        stack.add(root)

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (current.isDirectory) {
                current.listFiles().forEach { stack.add(it) }
            } else if (current.isFile && current.name != null) {
                val extension = current.name!!.substringAfterLast('.', "").lowercase()

                // Strategy 1: Check for the .ppd extension.
                if (extension in knownDriverExtensions) {
                    files.add(current)
                    continue // File found, move to the next one.
                }

                // Strategy 2: "Sniff" the file content if the extension is wrong.
                try {
                    context.contentResolver.openInputStream(current.uri)?.use { inputStream ->
                        val buffer = ByteArray(512)
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead > 0) {
                            val contentSample = String(buffer, 0, bytesRead, Charsets.UTF_8)
                            // Look for the magic string that identifies PPD files
                            if (contentSample.contains("*PPD-Adobe:")) {
                                Log.d("DriverRepository", "Sniffed a PPD file without extension: ${current.name}")
                                files.add(current)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore files we can't read.
                }
            }
        }
        return files
    }

    fun removeDriver(driver: Driver) {
        try {
            val fileToDelete = File(driver.internalPath)
            if (fileToDelete.exists()) {
                fileToDelete.delete()
            }
        } catch (e: Exception) {
            Log.e("DriverRepository", "Error deleting driver file for ${driver.originalFileName}", e)
        }
        val updatedList = _drivers.value.filterNot { it.id == driver.id }
        _drivers.value = updatedList
        saveDriversMetadataToFile(updatedList)
    }

    private fun saveDriversMetadataToFile(drivers: List<Driver>) {
        try {
            val jsonString = json.encodeToString(drivers)
            driversJsonFile.writeText(jsonString)
        } catch (e: IOException) {
            Log.e("DriverRepository", "Failed to save drivers metadata.", e)
        }
    }

    private fun loadDriversFromFile() {
        if (!driversJsonFile.exists()) return
        try {
            val jsonString = driversJsonFile.readText()
            if (jsonString.isNotBlank()) {
                _drivers.value = json.decodeFromString(jsonString)
            }
        } catch (e: Exception) {
            Log.e("DriverRepository", "Failed to load or parse drivers metadata.", e)
        }
    }

    fun removeAllDrivers() {
        try {
            val files = driversDir.listFiles()
            files?.forEach { file ->
                if (file.isFile) {
                    file.delete()
                }
            }
            Log.d("DriverRepository", "All physical driver files deleted.")
        } catch (e: Exception) {
            Log.e("DriverRepository", "Error during bulk deletion of driver files.", e)
        }

        _drivers.value = emptyList()
        saveDriversMetadataToFile(emptyList())
    }

    fun removeUnusedDrivers(savedPrinters: List<PrinterInfo>) {
        val usedDriverIds = savedPrinters.mapNotNull { it.driverId }.toSet()
        val allDrivers = _drivers.value.toList()
        val unusedDrivers = allDrivers.filter { it.id !in usedDriverIds }

        if (unusedDrivers.isEmpty()) {
            Log.d("DriverRepository", "No unused drivers to delete.")
            return
        }

        unusedDrivers.forEach { driver ->
            try {
                val fileToDelete = File(driver.internalPath)
                if (fileToDelete.exists()) {
                    fileToDelete.delete()
                }
            } catch (e: Exception) {
                Log.e("DriverRepository", "Error deleting unused driver file: ${driver.originalFileName}", e)
            }
        }

        val remainingDrivers = allDrivers.filter { it.id in usedDriverIds }
        _drivers.value = remainingDrivers
        saveDriversMetadataToFile(remainingDrivers)
        Log.d("DriverRepository", "Deleted ${unusedDrivers.size} unused drivers.")
    }
}
