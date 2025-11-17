package com.example.freeprint.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.freeprint.data.FileRepository
import com.example.freeprint.data.models.PrintFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FilePickerViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepository = FileRepository(application)

    private val _files = MutableStateFlow<List<PrintFile>>(emptyList())
    val files = _files.asStateFlow()

    init {
        loadFiles()
    }

    /**
     * Loads the list of files from the repository and updates the UI state.
     */
    fun loadFiles() {
        viewModelScope.launch {
            _files.value = fileRepository.listFiles()
        }
    }

    /**
     * Deletes a file from storage and refreshes the UI list.
     */
    fun deleteFile(file: PrintFile) {
        viewModelScope.launch {
            fileRepository.deleteFile(file)
            loadFiles() // Refresh the list after deletion
        }
    }

    /**
     * Finds a file by its absolute path.
     */
    fun getFileByPath(filePath: String?): PrintFile? {
        if (filePath == null) return null
        return files.value.firstOrNull { it.path == filePath }
    }
}
