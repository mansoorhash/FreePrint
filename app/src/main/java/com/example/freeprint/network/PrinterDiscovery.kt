package com.example.freeprint.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.freeprint.model.PrinterInfo
import com.example.freeprint.model.PrinterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

class PrinterDiscovery(context: Context) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val serviceTypes = listOf(
        "_ipp._tcp.local.",
        "_ipps._tcp.local.", // Also listen for secure IPP
        "_pdl-datastream._tcp.local.",
        "_printer._tcp.local."
    )

    fun discoverPrinters(): Flow<PrinterInfo> = callbackFlow {
        val lock = wifiManager.createMulticastLock("freeprint_mdns_lock")
        var jmdns: JmDNS? = null

        try {
            lock.setReferenceCounted(true)
            lock.acquire()
            val wifiInfo = wifiManager.connectionInfo
            val ip = wifiInfo.ipAddress
            val inetAddress = InetAddress.getByName(String.format("%d.%d.%d.%d", ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff))
            Log.d("PrinterDiscovery", "Starting mDNS discovery on address: ${inetAddress.hostAddress}")
            jmdns = JmDNS.create(inetAddress, "freeprint-discovery")

            val listener = object : ServiceListener {
                override fun serviceAdded(event: ServiceEvent) {
                    jmdns?.requestServiceInfo(event.type, event.name, 1000)
                }

                override fun serviceRemoved(event: ServiceEvent) { /* Not implemented */ }

                override fun serviceResolved(event: ServiceEvent) {
                    val info = event.info
                    Log.d("PrinterDiscovery", "Service resolved: ${info.name} at ${info.inet4Addresses.firstOrNull()?.hostAddress}")

                    val printerType = when {
                        info.type.contains("_ipp") -> PrinterType.IPP
                        info.type.contains("_pdl-datastream") || info.type.contains("_printer") -> PrinterType.RAW_SOCKET
                        else -> PrinterType.UNKNOWN
                    }

                    val hostAddress = info.inet4Addresses.firstOrNull()?.hostAddress
                    if (hostAddress != null && printerType != PrinterType.UNKNOWN) {

                        val properties = parseTxtRecord(info)
                        Log.d("PrinterDiscovery", "Parsed TXT Record for ${info.name}: $properties")

                        val bestName = properties["product"]
                            ?.removeSurrounding("(", ")") // Clean up product name
                            ?: properties["ty"]
                            ?: info.name

                        val printer = PrinterInfo(
                            name = bestName,
                            hostAddress = hostAddress,
                            port = info.port,
                            type = printerType,
                            properties = properties
                        )
                        trySend(printer)
                    }
                }
            }

            serviceTypes.forEach { jmdns.addServiceListener(it, listener) }

            awaitClose {
                Log.d("PrinterDiscovery", "Closing mDNS discovery.")
                jmdns?.close()
                if (lock.isHeld) lock.release()
            }
        } catch (e: Exception) {
            Log.e("PrinterDiscovery", "Discovery failed", e)
            close(e)
        }
    }.flowOn(Dispatchers.IO)

    private fun parseTxtRecord(serviceInfo: ServiceInfo): Map<String, String> {
        val properties = mutableMapOf<String, String>()
        val txtBytes = serviceInfo.textBytes ?: return emptyMap()

        properties["raw_txt_data"] = txtBytes.joinToString(separator = " ") { (it.toInt() and 0xFF).toString() }

        var i = 0
        while (i < txtBytes.size) {
            val len = txtBytes[i].toInt() and 0xFF
            if (len == 0) break
            i++

            if (i + len > txtBytes.size) {
                Log.e("PrinterDiscovery", "Malformed TXT record: length byte indicates out of bounds.")
                break
            }

            val entry = String(txtBytes, i, len, Charsets.UTF_8)
            val parts = entry.split('=', limit = 2)
            if (parts.size == 2) {
                properties[parts[0]] = parts[1]
            } else if (parts.size == 1 && parts[0].isNotEmpty()) {
                properties[parts[0]] = "" // Boolean key
            }

            i += len
        }
        return properties
    }
}
