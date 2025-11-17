package com.example.freeprint.data.models

import kotlinx.serialization.Serializable

// New enum to represent the status of a print job
@Serializable
enum class PrintJobStatus {
    QUEUED,
    PRINTING,
    COMPLETED,
    FAILED
}

@Serializable
data class PrintJob(
    val id: String,
    // Store the file path as a String, which is robust for serialization
    val filePath: String,
    val fileName: String,
    val printerName: String,
    val printerHostAddress: String,
    val printerPort: Int,
    var status: PrintJobStatus,
    val timestamp: Long = System.currentTimeMillis(),
    // --- FIX: Add the missing map to store selected PPD options ---
    val selectedOptions: Map<String, String> = emptyMap()
)
