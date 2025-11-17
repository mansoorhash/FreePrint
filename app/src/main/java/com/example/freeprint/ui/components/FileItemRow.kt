package com.example.freeprint.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage // For error state in Coil
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource // For painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage // Coil import
import coil.request.ImageRequest
import com.example.freeprint.data.models.PrintFile
import java.io.File // For creating a File object from path

@Composable
fun FileItemRow(
    file: PrintFile,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Container for the image/icon and checkmark
            Box(
                modifier = Modifier
                    .size(48.dp) // Slightly larger to accommodate potential image aspect ratios
                    .padding(end = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                // Determine whether to show an image thumbnail or a file type icon
                if (file.mimeType?.startsWith("image/") == true) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(file.path)) // Load from the local file path
                            .crossfade(true) // Optional: nice fade-in effect
                            .size(192) // Request a slightly larger image to allow for good downscaling for the 48.dp view. Adjust as needed.
                            // You can also use .scale(Scale.FIT) or .scale(Scale.FILL)
                            .build(),
                        contentDescription = "Thumbnail of ${file.name}",
                        modifier = Modifier.fillMaxSize(), // Fill the Box
                        contentScale = ContentScale.Crop, // Crop to fit the square Box
                        // Placeholder while loading (optional, but good UX)
                        // You can use a generic image icon as a placeholder
                        placeholder = painterResource(id = android.R.drawable.ic_menu_gallery), // Example placeholder
                        // Error image if loading fails (optional, but good UX)
                        error = painterResource(id = android.R.drawable.ic_delete) // Example error icon
                    )
                } else {
                    // Display file type icon for non-image files
                    val fileIcon = getIconForMimeType(file.mimeType)
                    Image(
                        imageVector = fileIcon,
                        contentDescription = "File type: ${file.mimeType ?: file.name}",
                        modifier = Modifier.fillMaxSize(),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }

                // Checkmark Icon Overlay (visible if selected)
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp) // Adjusted size
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                shape = CircleShape
                            )
                            .padding(2.dp)
                    )
                }
            }

            // Column for Text elements
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    // Using mimeType for type display is generally better
                    text = "Type: ${file.mimeType?.substringAfterLast('/')?.uppercase() ?: file.type} | Size: ${file.size / 1024} KB",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Options Menu
            Box {
                var expanded by remember { mutableStateOf(false) }
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(text = { Text("Edit Settings") }, onClick = { expanded = false; onEdit() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { expanded = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun getIconForMimeType(mimeType: String?): ImageVector {
    return when {
        // Prioritize specific known types for better icons
        mimeType == "application/pdf" -> Icons.Filled.PictureAsPdf
        mimeType == "application/msword" ||
                mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                mimeType == "application/vnd.oasis.opendocument.text" ||
                mimeType == "text/plain" ||
                mimeType == "text/rtf" ||
                mimeType == "text/csv" -> Icons.Filled.Description // Or a more specific document/text icon
        // Add more specific icons for spreadsheets, presentations if desired
        // mimeType == "application/vnd.ms-excel" -> Icons.Filled.GridOn (example)
        // mimeType.startsWith("image/") -> Icons.Filled.Image // This is a fallback if AsyncImage fails or for non-AsyncImage cases
        else -> Icons.Filled.Description // Default fallback for any other unhandled type
    }
}
