package com.example.freeprint.ui.components

import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun FileItemCard(fileName: String) {
    Card { Text(fileName) }
}