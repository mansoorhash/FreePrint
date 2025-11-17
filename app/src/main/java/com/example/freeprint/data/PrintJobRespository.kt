package com.example.freeprint.data

import android.content.Context
import android.util.Log
import com.example.freeprint.data.models.PrintJob
import com.example.freeprint.data.models.PrintJobStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

class PrintJobRepository(context: Context) {

    private val jobsFile = File(context.filesDir, "print_jobs.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val _printJobs = MutableStateFlow<List<PrintJob>>(emptyList())
    val printJobs = _printJobs.asStateFlow()

    init {
        loadJobsFromFile()
    }

    suspend fun addJob(newJob: PrintJob) = withContext(Dispatchers.IO) {
        val updatedList = _printJobs.value + newJob
        _printJobs.value = updatedList
        saveJobsToFile()
        Log.d("PrintJobRepository", "Added new job ${newJob.id}, queue size is now ${updatedList.size}")
    }

    suspend fun updateJobStatus(jobId: String, newStatus: PrintJobStatus) = withContext(Dispatchers.IO) {
        val updatedList = _printJobs.value.map {
            if (it.id == jobId) it.copy(status = newStatus) else it
        }
        _printJobs.value = updatedList
        saveJobsToFile()
        Log.d("PrintJobRepository", "Updated job $jobId status to $newStatus")
    }

    /**
     * Clears jobs that are considered "finished" (COMPLETED or FAILED).
     * This allows the user to clean up their history.
     */
    suspend fun clearFinishedJobs() = withContext(Dispatchers.IO) {
        val activeJobs = _printJobs.value.filter {
            it.status == PrintJobStatus.QUEUED || it.status == PrintJobStatus.PRINTING
        }
        _printJobs.value = activeJobs
        saveJobsToFile()
        Log.d("PrintJobRepository", "Cleared finished jobs. ${activeJobs.size} active jobs remain.")
    }

    private fun saveJobsToFile() {
        try {
            val jsonString = json.encodeToString(_printJobs.value)
            jobsFile.writeText(jsonString)
        } catch (e: IOException) {
            Log.e("PrintJobRepository", "Failed to save print jobs to file.", e)
        }
    }

    private fun loadJobsFromFile() {
        if (!jobsFile.exists()) {
            Log.d("PrintJobRepository", "No saved jobs file found.")
            return
        }
        try {
            val jsonString = jobsFile.readText()
            if (jsonString.isNotBlank()) {
                val loadedJobs = json.decodeFromString<List<PrintJob>>(jsonString)
                // IMPORTANT: Mark any "PRINTING" jobs as "QUEUED" on startup.
                // This ensures jobs that were interrupted by an app crash get re-processed.
                val correctedJobs = loadedJobs.map {
                    if (it.status == PrintJobStatus.PRINTING) {
                        it.copy(status = PrintJobStatus.QUEUED)
                    } else {
                        it
                    }
                }
                _printJobs.value = correctedJobs
                Log.d("PrintJobRepository", "Loaded ${correctedJobs.size} jobs from file.")
            }
        } catch (e: Exception) {
            Log.e("PrintJobRepository", "Failed to load or parse jobs from file.", e)
            _printJobs.value = emptyList()
        }
    }
}
