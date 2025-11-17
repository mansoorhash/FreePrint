package com.example.freeprint.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.snmp4j.CommunityTarget
import org.snmp4j.PDU
import org.snmp4j.Snmp
import org.snmp4j.mp.SnmpConstants
import org.snmp4j.smi.OID
import org.snmp4j.smi.OctetString
import org.snmp4j.smi.UdpAddress
import org.snmp4j.smi.VariableBinding
import org.snmp4j.transport.DefaultUdpTransportMapping

class SnmpClient {

    // OID for sysDescr (System Description), which usually contains the model name.
    private val sysDescrOid = OID("1.3.6.1.2.1.1.1.0")

    suspend fun getPrinterModel(host: String): String? = withContext(Dispatchers.IO) {
        var snmp: Snmp? = null
        try {
            // Using use{} ensures the transport mapping is closed automatically
            DefaultUdpTransportMapping().use { transport ->
                transport.listen()
                snmp = Snmp(transport)

                val target = CommunityTarget<UdpAddress>().apply {
                    community = OctetString("public") // Default community string for read access
                    address = UdpAddress("$host/161")
                    retries = 1
                    timeout = 1500
                    version = SnmpConstants.version2c
                }

                val pdu = PDU().apply {
                    add(VariableBinding(sysDescrOid))
                    type = PDU.GET
                }

                val responseEvent = snmp.get(pdu, target)
                val responsePdu = responseEvent?.response

                if (responsePdu != null && responsePdu.errorStatus == PDU.noError && responsePdu.variableBindings.isNotEmpty()) {
                    // FIX: Get the first variable binding by its index (0) instead of by OID.
                    // This is a more robust way to get the result for a single-variable GET request.
                    val result = responsePdu.get(0).variable?.toString()
                    Log.d("SnmpClient", "SNMP response from $host: $result")
                    return@withContext result
                } else {
                    val error = responsePdu?.errorStatusText ?: "No response"
                    Log.w("SnmpClient", "SNMP request to $host failed. Error: $error")
                }
            }
        } catch (e: Exception) {
            Log.e("SnmpClient", "Error during SNMP query to $host", e)
        } finally {
            // The snmp object itself should be closed if it was created
            snmp?.close()
        }
        return@withContext null
    }
}
