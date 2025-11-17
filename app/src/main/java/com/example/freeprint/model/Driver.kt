package com.example.freeprint.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Driver(
    // The user-friendly name parsed from the file, e.g., "RICOH Aficio 2016"
    val displayName: String,

    // The original name of the imported file, e.g., "RAF16BP3.PPD"
    val originalFileName: String,

    // The path to the file's copy inside the app's internal storage
    val internalPath: String,

    // A unique ID to link this driver to a PrinterInfo object
    val id: String = UUID.randomUUID().toString()
)
