package com.example.freeprint.data // Ensure this package is correct

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap // For MIME type inference
import com.example.freeprint.data.models.PrintFile // Make sure this import path is correct
import java.io.File

class FileRepository(private val context: Context) {

    private val printFilesDir = File(context.filesDir, "print_files")

    init {
        if (!printFilesDir.exists()) {
            val created = printFilesDir.mkdirs()
            if (!created) {
                Log.e("FileRepository", "Failed to create directory: ${printFilesDir.absolutePath}")
                // Consider throwing an exception if directory creation is critical
            }
        }
    }

    /**
     * This function is called from FilePickerScreen after a file has been
     * successfully copied into the 'print_files' directory.
     *
     * In the current setup (where listFiles() reads directly from the disk
     * and PrintFile objects are reconstructed), this function's main role
     * might be to confirm the operation or prepare for a database-backed setup.
     *
     * If you were using a database to store PrintFile metadata, this is where
     * you would insert the 'printFile' object into the database.
     *
     * @param printFile The PrintFile object containing metadata about the newly added file.
     */
    fun addFile(printFile: PrintFile) {
        // Since FilePickerScreen has already copied the file to printFilesDir,
        // and listFiles() will scan this directory, this function might:
        // 1. Log the addition.
        // 2. In a database setup: Insert printFile into the database.
        // 3. If maintaining an in-memory cache (not recommended as sole source): Add to that cache.

        // For now, let's just log. The file's existence is the primary "addition".
        Log.d("FileRepository", "addFile called for: ${printFile.name}, Path: ${printFile.path}")
        // If your listFiles() relies on some in-memory list that it returns,
        // you would add printFile to that list here.
        // However, the provided listFiles() scans the directory.
    }

    /**
     * Lists all files currently stored in the app's "print_files" directory.
     * It reconstructs PrintFile objects from the files found on disk.
     *
     * @return A list of PrintFile objects.
     */
    fun listFiles(): List<PrintFile> {
        if (!printFilesDir.exists() || !printFilesDir.isDirectory) {
            Log.w("FileRepository", "print_files directory does not exist or is not a directory.")
            return emptyList()
        }

        val filesOnDisk = printFilesDir.listFiles { file -> file.isFile } // Only list actual files
        if (filesOnDisk == null) {
            Log.e("FileRepository", "Failed to list files in ${printFilesDir.absolutePath} (IO Error or not a directory).")
            return emptyList()
        }

        return filesOnDisk.mapNotNull { file ->
            // Reconstruct PrintFile. MIME type inference is a fallback here.
            // The most accurate MIME type is the one captured in FilePickerScreen
            // when the file is initially picked.
            file.toPrintFileModel()
        }
    }

    /**
     * Deletes a file from the app's internal "print_files" storage.
     *
     * @param printFile The PrintFile object representing the file to delete.
     *                  It's assumed that printFile.path is the correct absolute path.
     */
    fun deleteFile(printFile: PrintFile) {
        val fileToDelete = File(printFile.path)

        // Security check: ensure the file path is within our app's directory.
        if (!fileToDelete.absolutePath.startsWith(context.filesDir.absolutePath)) {
            Log.w("FileRepository", "Attempt to delete file outside app's filesDir: ${printFile.path}")
            return
        }

        if (fileToDelete.exists()) {
            if (fileToDelete.delete()) {
                Log.d("FileRepository", "File deleted successfully: ${printFile.path}")
            } else {
                Log.w("FileRepository", "Failed to delete file from disk: ${printFile.path}")
            }
        } else {
            Log.w("FileRepository", "File not found on disk for deletion: ${printFile.path}")
        }
    }

    /**
     * Extension function to convert a java.io.File to a PrintFile data model.
     * This is used when listing files already on disk.
     */
    private fun File.toPrintFileModel(): PrintFile {
        // Infer MIME type from file extension as a fallback.
        // The original MIME type captured during file picking (in FilePickerScreen) is more reliable.
        val extension = this.extension.lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"

        return PrintFile(
            name = this.name.substringBeforeLast('.'),
            path = this.absolutePath,
            type = this.name.substringAfterLast('.').uppercase(),
            mimeType = mimeType,
            size = this.length()
        )
    }
}
