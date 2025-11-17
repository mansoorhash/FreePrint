package com.example.freeprint.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.freeprint.model.PrinterInfo
import com.example.freeprint.model.PrinterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.net.InetSocketAddress
import java.net.Socket

class PrinterScanner(context: Context) {
    private val appContext = context.applicationContext
    private val ippClient = IppClient()
    private val snmpClient = SnmpClient()

    fun discoverOnLocalSubnet(
        timeoutMs: Int = 2000,
        concurrency: Int = 50
    ): Flow<PrinterInfo> = channelFlow {
        val subnet = getLocalSubnetPrefix()
        if (subnet == null) {
            Log.e("PrinterScanner", "Could not determine local subnet.")
            close()
            return@channelFlow
        }

        // --- NEW: Get the current SSID once at the start of the scan ---
        val currentSsid = getCurrentSsid()
        Log.d("PrinterScanner", "Starting active scan on subnet: $subnet.* (SSID: $currentSsid)")

        val semaphore = Semaphore(concurrency)

        for (i in 1..254) {
            val host = "$subnet.$i"
            launch {
                semaphore.acquire()
                try {
                    val isRawPortOpen = isPortOpen(host, 9100, 500)
                    val isIppPortOpen = isPortOpen(host, 631, 500)

                    if (!isRawPortOpen && !isIppPortOpen) {
                        return@launch
                    }

                    Log.d("PrinterScanner", "Active scan found potential printer on $host (Raw: $isRawPortOpen, IPP: $isIppPortOpen)")

                    val snmpModel = snmpClient.getPrinterModel(host)
                    val bestName = snmpModel ?: "Printer @ $host"
                    val snmpProps = if (snmpModel != null) mapOf("snmp_sysDescr" to snmpModel) else emptyMap()

                    if (isRawPortOpen) {
                        send(
                            PrinterInfo(
                                name = bestName,
                                hostAddress = host,
                                port = 9100,
                                type = PrinterType.RAW_SOCKET,
                                properties = snmpProps,
                                ssid = currentSsid // --- NEW: Attach the SSID ---
                            )
                        )
                    }

                    if (isIppPortOpen) {
                        val ippAttributes = ippClient.getPrinterAttributes("http://$host:631/ipp/print")
                        if (ippAttributes != null && ippAttributes["ipp-status-code"]?.contains("successful-ok") == true) {
                            val ippPrinterName = ippAttributes["printer-make-and-model"] ?: bestName
                            Log.i("PrinterScanner", "BONUS: Active scan got IPP data for $host. Name: $ippPrinterName")
                            send(
                                PrinterInfo(
                                    name = ippPrinterName,
                                    hostAddress = host,
                                    port = 631,
                                    type = PrinterType.IPP,
                                    properties = snmpProps + ippAttributes,
                                    ssid = currentSsid // --- NEW: Attach the SSID ---
                                )
                            )
                        }
                    }

                } finally {
                    semaphore.release()
                }
            }
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    private fun isPortOpen(host: String, port: Int, timeoutMs: Int): Boolean {
        // ... (function is unchanged)
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun getLocalSubnetPrefix(): String? {
        // ... (function is unchanged)
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        if (!wifiManager.isWifiEnabled) return null
        val ipAddressInt = wifiManager.connectionInfo.ipAddress
        if (ipAddressInt == 0) return null
        val ipString = String.format("%d.%d.%d.%d", ipAddressInt and 0xff, ipAddressInt shr 8 and 0xff, ipAddressInt shr 16 and 0xff, ipAddressInt shr 24 and 0xff)
        return ipString.substringBeforeLast('.')
    }

    // --- NEW: Function to get the current Wi-Fi SSID ---
    private fun getCurrentSsid(): String? {
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        if (!wifiManager.isWifiEnabled) return null
        // connectionInfo.ssid returns the SSID surrounded by double quotes, which we remove.
        val ssid = wifiManager.connectionInfo.ssid
        return if (ssid != null && ssid != "<unknown ssid>") {
            ssid.removeSurrounding("\"")
        } else {
            null
        }
    }
}
