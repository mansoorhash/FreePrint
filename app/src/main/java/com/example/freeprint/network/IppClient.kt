package com.example.freeprint.network

import android.annotation.SuppressLint
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class IppClient {

    private val client: OkHttpClient by lazy {
        try {
            val trustAllCerts = arrayOf<TrustManager>(
                @SuppressLint("CustomX509TrustManager")
                object : X509TrustManager {
                    @SuppressLint("TrustAllX509TrustManager")
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    @SuppressLint("TrustAllX509TrustManager")
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory
            OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
        } catch (e: Exception) {
            Log.e("IppClient", "Failed to create a trusting OkHttpClient, falling back to default.", e)
            OkHttpClient()
        }
    }

    companion object IppConstants {
        // Operation IDs
        const val GET_PRINTER_ATTRIBUTES: Short = 0x000B

        // Status Codes
        const val SUCCESSFUL_OK: Short = 0x0000

        // Attribute Group Tags
        const val OPERATION_ATTRIBUTES_TAG: Byte = 0x01
        const val PRINTER_ATTRIBUTES_TAG: Byte = 0x04
        const val END_OF_ATTRIBUTES_TAG: Byte = 0x03

        // Value Tags (Data Types)
        const val INTEGER: Byte = 0x21
        const val BOOLEAN: Byte = 0x22
        const val ENUM: Byte = 0x23
        const val KEYWORD: Byte = 0x44
        const val URI: Byte = 0x45
        const val CHARSET: Byte = 0x47
        const val NATURAL_LANGUAGE: Byte = 0x48
        const val MIME_MEDIA_TYPE: Byte = 0x49
    }

    fun getPrinterAttributes(printerUri: String): Map<String, String>? {
        try {
            val requestBody = buildGetPrinterAttributesRequest(printerUri)
            val request = Request.Builder()
                .url(printerUri)
                .post(requestBody.toRequestBody("application/ipp".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("IppClient", "IPP request failed with HTTP code: ${response.code} for $printerUri")
                    return null
                }
                val responseBytes = response.body?.bytes() ?: return null
                Log.d("IppClient", "Received ${responseBytes.size} bytes from $printerUri")

                // Parse the response using the new, robust parser
                val parsedAttributes = parseIppResponse(responseBytes).toMutableMap()

                // Also include the raw hex string for debugging purposes
                val rawDataString = responseBytes.joinToString(separator = " ") {
                    String.format("%02X", it)
                }
                parsedAttributes["raw_ipp_response_hex"] = rawDataString

                return parsedAttributes
            }
        } catch (e: Exception) {
            Log.e("IppClient", "Failed to get IPP attributes from $printerUri", e)
            return null
        }
    }

    // --- THIS IS THE FIX: Build a more compliant request ---
    private fun buildGetPrinterAttributesRequest(printerUri: String): ByteArray {
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)

        // Standard IPP Header
        dos.writeShort(0x0101) // IPP Version 1.1
        dos.writeShort(GET_PRINTER_ATTRIBUTES.toInt()) // Operation ID
        dos.writeInt(12345) // Request ID

        // Operation Attributes Group
        dos.writeByte(OPERATION_ATTRIBUTES_TAG.toInt())

        // Standard attributes that every printer must support
        writeAttribute(dos, CHARSET, "attributes-charset", "utf-8")
        writeAttribute(dos, NATURAL_LANGUAGE, "attributes-natural-language", "en")
        writeAttribute(dos, URI, "printer-uri", printerUri)

        // Explicitly ask for all available printer attributes
        writeAttribute(dos, KEYWORD, "requested-attributes", "all")

        // End of Attributes
        dos.writeByte(END_OF_ATTRIBUTES_TAG.toInt())
        dos.flush()
        return bos.toByteArray()
    }

    private fun writeAttribute(dos: DataOutputStream, tag: Byte, name: String, value: String) {
        dos.writeByte(tag.toInt())
        dos.writeShort(name.length)
        dos.write(name.toByteArray(StandardCharsets.UTF_8))
        dos.writeShort(value.length)
        dos.write(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun parseIppResponse(data: ByteArray): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        if (data.size < 9) {
            Log.e("IppClient", "Response too short to be a valid IPP response.")
            return emptyMap()
        }
        val buffer = ByteBuffer.wrap(data)

        // Read header
        val version = buffer.short
        val statusCode = buffer.short
        val requestId = buffer.int

        // Add the status code to the map for debugging regardless of success
        if (statusCode != SUCCESSFUL_OK) {
            attributes["ipp-status-code"] = "0x" + statusCode.toString(16).padStart(4, '0')
            Log.e("IppClient", "Printer returned error status: ${attributes["ipp-status-code"]}")
            // Do not return early. Some printers send attributes even with an error.
        } else {
            attributes["ipp-status-code"] = "0x0000 (successful-ok)"
        }

        var lastAttributeName = ""

        while (buffer.hasRemaining()) {
            val tag = buffer.get()

            // Check for group tags or end of attributes
            when (tag) {
                OPERATION_ATTRIBUTES_TAG, PRINTER_ATTRIBUTES_TAG -> continue
                END_OF_ATTRIBUTES_TAG -> break
                in 0x00..0x0F -> continue // Ignore delimiter tags
            }

            // At this point, we have an attribute tag.
            // Check if the next field (name-length) is 0. This indicates another value for the PREVIOUS attribute.
            val nameLength = buffer.short.toInt()
            val currentAttributeName = if (nameLength > 0) {
                if (nameLength > buffer.remaining()) break // Corrupt data
                val nameBytes = ByteArray(nameLength)
                buffer.get(nameBytes)
                String(nameBytes, StandardCharsets.UTF_8).also { lastAttributeName = it }
            } else {
                // Name length is 0, so this is an additional value for the last attribute name we saw.
                lastAttributeName
            }

            if (currentAttributeName.isBlank()) continue

            val valueLength = buffer.short.toInt()
            if (valueLength > buffer.remaining() || valueLength < 0) break // Corrupt data

            val valueBytes = ByteArray(valueLength)
            buffer.get(valueBytes)
            val value = parseValue(tag, valueBytes)

            // Add or append the value to the map
            attributes.compute(currentAttributeName) { _, existingValue ->
                if (existingValue == null) value else "$existingValue, $value"
            }
        }
        return attributes
    }

    private fun parseValue(tag: Byte, valueBytes: ByteArray): String {
        return when (tag) {
            INTEGER, ENUM -> {
                when (valueBytes.size) {
                    1 -> valueBytes[0].toInt().toString()
                    2 -> ByteBuffer.wrap(valueBytes).short.toString()
                    4 -> ByteBuffer.wrap(valueBytes).int.toString()
                    else -> "int_val(${valueBytes.joinToString()})"
                }
            }
            BOOLEAN -> {
                if (valueBytes.isNotEmpty()) (valueBytes[0].toInt() != 0).toString()
                else "invalid_bool"
            }
            else -> {
                // Default to parsing as a string (covers keyword, uri, charset, name, text, etc.)
                String(valueBytes, StandardCharsets.UTF_8).trim()
            }
        }
    }

    fun sendPrintJob(url: String, data: ByteArray) {
        Log.d("IppClient", "sendPrintJob called for $url, but is not implemented.")
    }
}
