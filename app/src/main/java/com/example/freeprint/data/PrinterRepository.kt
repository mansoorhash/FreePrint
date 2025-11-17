package com.example.freeprint.data

import android.content.Context
import android.util.Log
import com.example.freeprint.model.PrinterInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

class PrinterRepository(context: Context) {

    private val printersFile = File(context.filesDir, "saved_printers.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val _savedPrinters = MutableStateFlow<List<PrinterInfo>>(emptyList())
    val savedPrinters = _savedPrinters.asStateFlow()

    init {
        loadPrintersFromFile()
    }

    fun addPrinter(printerToAdd: PrinterInfo) {
        val currentList = _savedPrinters.value
        val existingPrinterIndex = currentList.indexOfFirst { it.hostAddress == printerToAdd.hostAddress }

        val updatedList = if (existingPrinterIndex != -1) {
            // This is an update to an existing printer.
            Log.d("PrinterRepository", "Updating existing printer: ${printerToAdd.hostAddress}")

            val existingPrinter = currentList[existingPrinterIndex]

            // When updating, we must merge the new info with the old.
            // This ensures the driverId, name, AND ssid are all handled correctly.
            val finalPrinter = existingPrinter.copy(
                name = printerToAdd.name,
                driverId = printerToAdd.driverId,
                // Preserve the existing SSID if the new info doesn't have one
                ssid = printerToAdd.ssid ?: existingPrinter.ssid,
                // Merge properties, giving precedence to new ones
                properties = existingPrinter.properties + printerToAdd.properties
            )

            // --- THE CRITICAL FIX ---
            // Create a completely new list by mapping over the old one.
            // This is a new object, which guarantees StateFlow will emit an update.
            currentList.mapIndexed { index, printer ->
                if (index == existingPrinterIndex) {
                    finalPrinter
                } else {
                    printer
                }
            }
        } else {
            // This is a new printer.
            Log.d("PrinterRepository", "Adding new printer: ${printerToAdd.hostAddress}")
            currentList + printerToAdd
        }

        // Only update and save if the list has actually changed.
        if (updatedList != currentList) {
            _savedPrinters.value = updatedList
            savePrintersToFile()
        }
    }

    fun removePrinter(printer: PrinterInfo) {
        val currentList = _savedPrinters.value
        // filterNot already creates a new list, so it's safe.
        val updatedList = currentList.filterNot { it.hostAddress == printer.hostAddress }

        if (updatedList.size < currentList.size) {
            _savedPrinters.value = updatedList
            savePrintersToFile()
        }
    }

    private fun savePrintersToFile() {
        try {
            val jsonString = json.encodeToString(_savedPrinters.value)
            printersFile.writeText(jsonString)
            Log.d("PrinterRepository", "Printers saved successfully to ${printersFile.absolutePath}")
        } catch (e: IOException) {
            Log.e("PrinterRepository", "Failed to save printers to file.", e)
        }
    }

    private fun loadPrintersFromFile() {
        if (!printersFile.exists()) {
            Log.d("PrinterRepository", "No saved printers file found. Starting with an empty list.")
            return
        }
        try {
            val jsonString = printersFile.readText()
            if (jsonString.isNotBlank()) {
                val loadedPrinters = json.decodeFromString<List<PrinterInfo>>(jsonString)
                _savedPrinters.value = loadedPrinters
                Log.d("PrinterRepository", "${loadedPrinters.size} printers loaded from file.")
            }
        } catch (e: Exception) {
            Log.e("PrinterRepository", "Failed to load or parse printers from file.", e)
            _savedPrinters.value = emptyList() // Default to empty list on error
        }
    }
    fun updatePrinterList(updatedList: List<PrinterInfo>) {
        _savedPrinters.value = updatedList
        // We don't save to file here, as this is transient status.
    }
}
