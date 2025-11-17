package com.example.freeprint.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.freeprint.data.models.PrintJob
import com.example.freeprint.data.models.PrintJobStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PrintQueueScreen(
    printerViewModel: PrinterViewModel
) {
    val printJobs by printerViewModel.printJobs.collectAsState()

    // Display jobs in reverse chronological order
    val sortedJobs = printJobs.sortedByDescending { it.timestamp }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Print Queue",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
        if (sortedJobs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "There are no print jobs in the queue.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sortedJobs, key = { it.id }) { job ->
                    PrintJobItemRow(job = job)
                }
            }
        }
    }
}

@Composable
fun PrintJobItemRow(job: PrintJob) {
    val statusInfo = getStatusInfo(job.status)
    val timeFormatter = remember { SimpleDateFormat("h:mm:ss a", Locale.getDefault()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = statusInfo.first,
                contentDescription = "Status: ${job.status}",
                tint = statusInfo.second,
                modifier = Modifier.size(32.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = job.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "To: ${job.printerName}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Queued at: ${timeFormatter.format(Date(job.timestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (job.status == PrintJobStatus.PRINTING) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun getStatusInfo(status: PrintJobStatus): Pair<ImageVector, Color> {
    return when (status) {
        PrintJobStatus.QUEUED -> Icons.Default.HourglassTop to MaterialTheme.colorScheme.onSurfaceVariant
        PrintJobStatus.PRINTING -> Icons.Default.Print to MaterialTheme.colorScheme.primary
        PrintJobStatus.COMPLETED -> Icons.Default.CheckCircle to Color(0xFF008000) // Dark Green
        PrintJobStatus.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
    }
}
