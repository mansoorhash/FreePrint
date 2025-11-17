package com.example.freeprint.network

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.freeprint.model.PrinterInfo
import com.example.freeprint.utils.PpdParser
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.Socket
import java.nio.charset.Charset

class PrintManager(private val context: Context) {

    private val UEL = "\u001B%-12345X" // Universal Exit Language command
    private val FORM_FEED = "\u000C"   // ASCII Form Feed

    suspend fun executePrintJob(
        printer: PrinterInfo,
        fileUri: Uri,
        jobName: String,
        language: String,
        options: Map<String, String>,
        driverPath: String
    ): Boolean {
        try {
            val fileBytes = context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                ?: run {
                    Log.e("PrintManager", "Failed to read file bytes from Uri: $fileUri")
                    return false
                }

            val printData = generateDriverLedPrintData(
                driverPath,
                fileBytes,
                jobName,
                language,
                options
            )

            // --- REVERTING TO THE CORRECT PORT AND PROTOCOL ---
            val rawPort = 9100
            Log.d("PrintManager", "Sending RAW job to ${printer.hostAddress}:$rawPort")

            Socket(printer.hostAddress, rawPort).use { socket ->
                socket.soTimeout = 60000
                socket.getOutputStream().use { outputStream ->
                    outputStream.write(printData)
                    outputStream.flush()
                }
                Log.d("PrintManager", "RAW job sent successfully. Total size: ${printData.size} bytes.")
            }
            return true
        } catch (e: Exception) {
            Log.e("PrintManager", "Driver-led printing failed for ${printer.hostAddress}", e)
            return false
        }
    }

    // --- The LPD-specific functions (sendLpdJob, checkAck) have been removed ---

    private fun generateDriverLedPrintData(
        driverPath: String,
        fileBytes: ByteArray,
        jobName: String,
        language: String,
        selectedOptions: Map<String, String>
    ): ByteArray {
        val pjlHeader = StringBuilder()
        val postscriptHeader = StringBuilder()
        val postscriptBody = StringBuilder()
        val pjlFooter = StringBuilder()

        // --- 1. PJL Header ---
        pjlHeader.appendLine(UEL)
        pjlHeader.appendLine("@PJL JOB NAME=\"$jobName\"")

        val copies = selectedOptions["Copies"]?.toIntOrNull()
        if (copies != null && copies > 1) {
            pjlHeader.appendLine("@PJL SET COPIES=$copies")
        }

        // --- 2. PostScript Header (mimicking .prn structure) ---
        postscriptHeader.appendLine("@PJL ENTER LANGUAGE=$language")
        postscriptHeader.appendLine("%!PS-Adobe-3.0")
        postscriptHeader.appendLine("%%EndComments")
        postscriptHeader.appendLine()
        postscriptHeader.appendLine("%%BeginSetup")

        // --- 3. Build Individual Feature Blocks (The Correct Logic) ---
        val driverFile = File(driverPath)
        if (driverFile.exists()) {
            val ppd = PpdParser.parse(FileInputStream(driverFile))

            val optionProcessingOrder = listOf("PageSize", "Orientation") +
                    selectedOptions.keys.filterNot { it in listOf("PageSize", "Orientation", "Copies") }

            optionProcessingOrder.forEach { optionKeyword ->
                val choiceKeyword = selectedOptions[optionKeyword] ?: return@forEach

                // A. Handle synthetic options
                if (optionKeyword in listOf("PageSize", "Orientation")) {
                    val code = when (optionKeyword) {
                        "PageSize" -> when (choiceKeyword) {
                            "A4" -> "<< /PageSize [595 842] /ImagingBBox null >> setpagedevice"
                            "Legal" -> "<< /PageSize [612 1008] /ImagingBBox null >> setpagedevice"
                            else -> "<< /PageSize [612 792] /ImagingBBox null >> setpagedevice" // Letter
                        }
                        "Orientation" -> when (choiceKeyword) {
                            "Landscape" -> "<< /Orientation 1 >> setpagedevice"
                            else -> "<< /Orientation 0 >> setpagedevice" // Portrait
                        }
                        else -> ""
                    }
                    if (code.isNotBlank()) {
                        postscriptHeader.appendLine("featurebegin{")
                        postscriptHeader.appendLine("%%BeginFeature: *$optionKeyword")
                        postscriptHeader.appendLine(code)
                        postscriptHeader.appendLine("%%EndFeature")
                        postscriptHeader.appendLine("}featurecleanup")
                    }
                    return@forEach
                }

                // B. Handle options from the PPD file
                val ppdOption = ppd.options.find { it.keyword.equals(optionKeyword, ignoreCase = true) }
                val ppdChoice = ppdOption?.choices?.find { it.keyword.equals(choiceKeyword, ignoreCase = true) }

                if (ppdChoice != null && ppdChoice.invocationCode.isNotBlank()) {
                    postscriptHeader.appendLine("featurebegin{")
                    postscriptHeader.appendLine("%%BeginFeature: *${ppdOption.keyword} ${ppdChoice.keyword}")
                    postscriptHeader.appendLine(ppdChoice.invocationCode)
                    postscriptHeader.appendLine("%%EndFeature")
                    postscriptHeader.appendLine("}featurecleanup")
                }
            }
        }

        postscriptHeader.appendLine("%%EndSetup")
        postscriptHeader.appendLine()

        // --- 4. PostScript Body (mimicking .prn structure) ---
        postscriptBody.appendLine("userdict begin /ehsave save def end")
        postscriptBody.appendLine("%%Page: 1 1")
        postscriptBody.appendLine("/Courier findfont 12 scalefont setfont")
        postscriptBody.appendLine("72 720 moveto")
        val content = String(fileBytes, Charsets.UTF_8)
        content.lines().forEach { line ->
            val escaped = line.replace("(", "\\(").replace(")", "\\)")
            postscriptBody.appendLine("($escaped) show")
            postscriptBody.appendLine("0 -14 rmoveto")
        }
        postscriptBody.appendLine("showpage")
        postscriptBody.appendLine("ehsave restore")

        // --- 5. Footer (matching the .prn file precisely) ---
        pjlFooter.appendLine("%%EOF")
        pjlFooter.append(FORM_FEED)
        pjlFooter.appendLine(UEL)
        pjlFooter.appendLine("@PJL EOJ")

        // --- 6. Combine all parts ---
        val finalPayload = buildString {
            append(pjlHeader)
            append(postscriptHeader)
            append(postscriptBody)
            append(pjlFooter)
        }

        Log.d("PrintManager-PrintData", "--- START OF PRINT DATA ---")
        Log.d("PrintManager-PrintData", finalPayload)
        Log.d("PrintManager-PrintData", "--- END OF PRINT DATA ---")

        return finalPayload.toByteArray(Charsets.US_ASCII)
    }
}
