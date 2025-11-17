package com.example.freeprint.network

import java.net.Socket

class RawPrinterClient {
    fun sendToPrinter(host: String, port: Int, data: ByteArray) {
        Socket(host, port).use { socket ->
            socket.getOutputStream().write(data)
            socket.getOutputStream().flush()
        }
    }
}