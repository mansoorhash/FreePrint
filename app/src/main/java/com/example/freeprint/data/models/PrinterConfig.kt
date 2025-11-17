package com.example.freeprint.data.models

import kotlinx.serialization.Serializable

@Serializable
data class PrinterConfig(
    val name: String,
    val ip: String,
    val port: Int,
    val dpi: Int
)