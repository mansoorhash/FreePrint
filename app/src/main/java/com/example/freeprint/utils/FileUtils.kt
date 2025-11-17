package com.example.freeprint.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream // Needed for copyUriToFile
import java.io.InputStream // Needed for copyUriToFile

object FileUtils {

    /**
     * Gets the display name of a file from a content URI.
     * Falls back to parsing the path if the display name is not available.
     *
     * @param context The context.
     * @param uri The URI of the file.
     * @return The file name, or null if it cannot be determined.
     */
    @SuppressLint("Range")
    fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    // Attempt to get the DISPLAY_NAME column
                    val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) { // Check if the column exists
                        result = cursor.getString(displayNameIndex)
                    }
                }
            } catch (e: Exception) {
                Log.e("FileUtils", "Error getting file name from content URI", e)
                // Fallthrough to path-based extraction if there's an error
            } finally {
                cursor?.close()
            }
        }

        // Fallback if result is still null (e.g., URI scheme is not 'content' or DISPLAY_NAME was not found)
        if (result == null) {
            result = uri.path
            result?.let {
                val cut = it.lastIndexOf('/')
                if (cut != -1) {
                    result = it.substring(cut + 1)
                }
            }
        }
        return result
    }

    /**
     * Copies the content from a given URI to a destination file.
     *
     * @param context The context.
     * @param uri The source URI to copy from.
     * @param destinationFile The destination file to copy to.
     */
    fun copyUriToFile(context: Context, uri: Uri, destinationFile: File) {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            outputStream = FileOutputStream(destinationFile) // Use FileOutputStream for File

            if (inputStream == null) {
                Log.e("FileUtils", "Failed to open input stream from URI: $uri")
                return // Or throw an exception
            }

            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            Log.d("FileUtils", "File copied successfully to: ${destinationFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("FileUtils", "Error copying URI to file. URI: $uri, Destination: ${destinationFile.absolutePath}", e)
            // Consider rethrowing, logging more details, or informing the user via a callback/toast
        } finally {
            // It's good practice to close streams in a finally block if not using .use {}
            // However, .use {} handles this automatically.
            // If you weren't using .use {}, you'd close them here:
            // try { inputStream?.close() } catch (ioe: IOException) { /* Log error */ }
            // try { outputStream?.close() } catch (ioe: IOException) { /* Log error */ }
        }
    }
}
