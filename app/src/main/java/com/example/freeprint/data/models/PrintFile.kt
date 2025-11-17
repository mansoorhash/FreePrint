package com.example.freeprint.data.models

import kotlinx.serialization.Serializable

/**
 * A simplified and corrected data class representing a file to be printed.
 * This model is created when listing files from storage.
 */
@Serializable
data class PrintFile(
    // The user-friendly name of the file (e.g., "MyDocument")
    val name: String,

    // The absolute path to the file in the app's internal storage
    val path: String,

    // The file's extension (e.g., "PDF", "JPG")
    val type: String,

    // The IANA MIME type of the file (e.g., "application/pdf")
    val mimeType: String?,

    // The size of the file in bytes
    val size: Long
)
