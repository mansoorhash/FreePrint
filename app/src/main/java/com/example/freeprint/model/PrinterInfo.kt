package com.example.freeprint.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class PrinterInfo(
    val name: String,
    val hostAddress: String,
    val port: Int,
    val type: PrinterType,
    val properties: Map<String, String> = emptyMap(),
    val ssid: String? = null,
    val driverId: String? = null,
    // --- NEW: Transient field for real-time online status ---
    @Transient val isOnline: Boolean = false,
    @Transient val isEnriching: Boolean = false
)

@Serializable
enum class PrinterType {
    IPP,
    RAW_SOCKET,
    UNKNOWN
}
