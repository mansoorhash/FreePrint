package com.example.freeprint.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object PrinterStatusChecker {

    /**
     * Performs a quick "ping" by attempting to open a socket connection.
     * @param host The IP address of the printer.
     * @param port The port to check (e.g., 631 for IPP, 9100 for RAW).
     * @param timeoutMillis The connection timeout.
     * @return True if the connection was successful, false otherwise.
     */
    suspend fun isPrinterOnline(host: String, port: Int, timeoutMillis: Int = 1500): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                // Connect with a specified timeout.
                socket.connect(InetSocketAddress(host, port), timeoutMillis)
                // If we get here, the connection was successful.
                return@withContext true
            }
        } catch (e: Exception) {
            // Any exception (ConnectException, SocketTimeoutException, etc.) means it's offline.
            return@withContext false
        }
    }
}
