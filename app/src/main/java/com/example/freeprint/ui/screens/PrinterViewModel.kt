package com.example.freeprint.ui.screens

import android.app.Application
import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.freeprint.data.DriverRepository
import com.example.freeprint.data.PrintJobRepository
import com.example.freeprint.data.PrinterRepository
import com.example.freeprint.data.models.PrintFile
import com.example.freeprint.data.models.PrintJob
import com.example.freeprint.data.models.PrintJobStatus
import com.example.freeprint.model.Driver
import com.example.freeprint.model.PrinterInfo
import com.example.freeprint.model.PrinterType
import com.example.freeprint.network.PrintManager
import com.example.freeprint.network.PrinterDiscovery
import com.example.freeprint.network.PrinterScanner
import com.example.freeprint.network.PrinterStatusChecker
import com.example.freeprint.utils.ParsedPpd
import com.example.freeprint.utils.PpdChoice
import com.example.freeprint.utils.PpdOption
import com.example.freeprint.utils.PpdParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.*
import kotlin.coroutines.cancellation.CancellationException

data class PrinterScreenUiState(
    val isDiscovering: Boolean = false,
    val discoveredPrinters: List<PrinterInfo> = emptyList(),
    val printerOptions: List<com.example.freeprint.utils.PpdOption> = emptyList(),
    val isLoadingOptions: Boolean = false
)

class PrinterViewModel(application: Application) : AndroidViewModel(application) {

    private val printerRepository = PrinterRepository(application)
    private val driverRepository = DriverRepository(application)
    private val printJobRepository = PrintJobRepository(application)
    private val mDnsDiscovery = PrinterDiscovery(application)
    private val activeScanner = PrinterScanner(application)
    private val printManager = PrintManager(application)
    private var discoveryJob: Job? = null
    private var statusCheckerJob: Job? = null

    val savedPrinters: StateFlow<List<PrinterInfo>> = printerRepository.savedPrinters
    val drivers: StateFlow<List<Driver>> = driverRepository.drivers
    val printJobs: StateFlow<List<PrintJob>> = printJobRepository.printJobs

    private val _uiState = MutableStateFlow(PrinterScreenUiState())
    val uiState = _uiState.asStateFlow()

    init {
        startQueueProcessor()
        startPrinterStatusChecker()
    }

    override fun onCleared() {
        super.onCleared()
        discoveryJob?.cancel()
        statusCheckerJob?.cancel()
    }

    private fun startPrinterStatusChecker() {
        if (statusCheckerJob?.isActive == true) return

        statusCheckerJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val wifiManager = getApplication<Application>().getSystemService(Context.WIFI_SERVICE) as WifiManager
                val currentSsid = wifiManager.connectionInfo.ssid?.removeSurrounding("\"")
                val currentPrinters = printerRepository.savedPrinters.value

                if (currentPrinters.isNotEmpty()) {
                    val updatedPrinters = currentPrinters.map { printer ->
                        val isSsidMatch = printer.ssid == currentSsid
                        val isReachable = PrinterStatusChecker.isPrinterOnline(printer.hostAddress, printer.port)
                        val isOnline = isSsidMatch && isReachable
                        if (printer.isOnline != isOnline) {
                            printer.copy(isOnline = isOnline)
                        } else {
                            printer
                        }
                    }
                    printerRepository.updatePrinterList(updatedPrinters)
                }
                delay(10000) // Check every 10 seconds
            }
        }
    }

    private suspend fun processPrintJob(job: PrintJob) {
        withContext(Dispatchers.IO) {
            printJobRepository.updateJobStatus(job.id, PrintJobStatus.PRINTING)
            val printer = savedPrinters.value.find { it.hostAddress == job.printerHostAddress }
            val file = File(job.filePath)

            if (printer == null || !file.exists()) {
                val reason = if (printer == null) "Printer not found" else "File not found"
                Log.e("QueueProcessor", "$reason for job ${job.id}. Marking as FAILED.")
                printJobRepository.updateJobStatus(job.id, PrintJobStatus.FAILED)
                return@withContext
            }

            val driver = printer.driverId?.let { id -> drivers.value.find { it.id == id } }
            if (driver == null) {
                Log.e("QueueProcessor", "Cannot process job ${job.id}: Driver not found for printer ${printer.name}.")
                printJobRepository.updateJobStatus(job.id, PrintJobStatus.FAILED)
                return@withContext
            }

            Log.d("QueueProcessor", "Processing job ${job.id} for printer ${printer.name} using driver ${driver.displayName}")
            val language = "POSTSCRIPT"
            val success = printManager.executePrintJob(
                printer = printer,
                fileUri = file.toUri(),
                jobName = job.fileName,
                language = language,
                options = job.selectedOptions,
                driverPath = driver.internalPath // Pass the driver path
            )

            val finalStatus = if (success) PrintJobStatus.COMPLETED else PrintJobStatus.FAILED
            printJobRepository.updateJobStatus(job.id, finalStatus)
            Log.d("QueueProcessor", "Job ${job.id} finished with status: $finalStatus")
        }
    }

    fun startComprehensiveScan() {
        if (discoveryJob?.isActive == true) return
        _uiState.update { it.copy(isDiscovering = true, discoveredPrinters = emptyList()) }
        val mDnsFlow = mDnsDiscovery.discoverPrinters()
        val subnetScanFlow = activeScanner.discoverOnLocalSubnet()
        discoveryJob = merge(mDnsFlow, subnetScanFlow)
            .onEach { printer -> updatePrinterInUi(printer) }
            .catch { exception ->
                if (exception !is CancellationException) {
                    Log.e("ViewModel", "Error during printer discovery", exception)
                }
            }
            .onCompletion { _uiState.update { it.copy(isDiscovering = false) } }
            .launchIn(viewModelScope)
    }

    private fun updatePrinterInUi(printer: PrinterInfo) {
        _uiState.update { currentState ->
            val existingPrinter = currentState.discoveredPrinters.find { it.hostAddress == printer.hostAddress }
            val updatedList = if (existingPrinter != null) {
                val preferredPrinter = if (printer.type == PrinterType.IPP) printer else existingPrinter
                val otherPrinter = if (printer.type == PrinterType.IPP) existingPrinter else printer
                val mergedPrinter = preferredPrinter.copy(
                    name = if (isMoreDescriptive(preferredPrinter.name, otherPrinter.name)) preferredPrinter.name else otherPrinter.name,
                    properties = otherPrinter.properties + preferredPrinter.properties,
                    ssid = preferredPrinter.ssid ?: otherPrinter.ssid
                )
                currentState.discoveredPrinters.map {
                    if (it.hostAddress == printer.hostAddress) mergedPrinter else it
                }
            } else {
                currentState.discoveredPrinters + printer
            }
            currentState.copy(discoveredPrinters = updatedList.distinctBy { it.hostAddress })
        }
    }

    private fun isMoreDescriptive(newName: String, oldName: String): Boolean {
        val isOldNameDefault = oldName.startsWith("Printer @ ")
        val isNewNameDefault = newName.startsWith("Printer @ ")
        return !isNewNameDefault || isOldNameDefault
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _uiState.update { it.copy(isDiscovering = false) }
    }

    fun addPrintJob(file: PrintFile, printer: PrinterInfo, selectedOptions: Map<String, String>) {
        viewModelScope.launch {
            if (printer.driverId == null) {
                Toast.makeText(getApplication(), "Cannot queue job: Printer has no driver.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val newJob = PrintJob(
                id = UUID.randomUUID().toString(),
                filePath = file.path,
                fileName = file.name,
                printerName = printer.name,
                printerHostAddress = printer.hostAddress,
                printerPort = printer.port,
                status = PrintJobStatus.QUEUED,
                selectedOptions = selectedOptions
            )
            printJobRepository.addJob(newJob)
        }
    }

    private fun startQueueProcessor() {
        viewModelScope.launch {
            while (isActive) {
                val nextJob = printJobs.value.firstOrNull { it.status == PrintJobStatus.QUEUED }
                if (nextJob != null) {
                    processPrintJob(nextJob)
                }
                delay(2000)
            }
        }
    }

    fun clearPrintHistory() {
        viewModelScope.launch {
            printJobRepository.clearFinishedJobs()
        }
    }

    private fun clearAndResetOptions() {
        _uiState.update { it.copy(printerOptions = emptyList(), isLoadingOptions = false) }
    }

    fun loadPrinterOptions(driverId: String?) {
        if (driverId == null) {
            clearAndResetOptions(); return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingOptions = true) }
            val driver = drivers.value.find { it.id == driverId }
            if (driver == null) {
                clearAndResetOptions(); return@launch
            }
            try {
                val driverFile = File(driver.internalPath)
                if (driverFile.exists()) {
                    val parsedPpd: ParsedPpd = PpdParser.parse(FileInputStream(driverFile))
                    val finalOptions = addSyntheticOptions(parsedPpd.options)
                    _uiState.update { it.copy(printerOptions = finalOptions, isLoadingOptions = false) }
                } else {
                    clearAndResetOptions()
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Error loading PPD options", e)
                clearAndResetOptions()
            }
        }
    }

    private fun getSyntheticOptions(): List<PpdOption> {
        return listOf(
            PpdOption(
                keyword = "PageSize",
                displayName = "Page Size",
                defaultChoice = "Letter",
                displayOrder = 1,
                choices = listOf(
                    PpdChoice("Letter", "Letter (8.5 x 11 in)", ""), // Invocation code is now handled by PrintManager
                    PpdChoice("A4", "A4 (210 x 297 mm)", ""),
                    PpdChoice("Legal", "Legal (8.5 x 14 in)", "")
                )
            ),
            PpdOption(
                keyword = "Orientation",
                displayName = "Orientation",
                defaultChoice = "Portrait",
                displayOrder = 2,
                choices = listOf(
                    PpdChoice("Portrait", "Portrait", ""), // Invocation code is now handled by PrintManager
                    PpdChoice("Landscape", "Landscape", "")
                )
            ),
            PpdOption(
                keyword = "Copies",
                displayName = "Copies",
                defaultChoice = "1",
                displayOrder = 3,
                choices = (1..20).map {
                    PpdChoice(it.toString(), it.toString(), "") // Invocation code is now handled by PrintManager
                }
            )
        )
    }

    private fun addSyntheticOptions(parsedOptions: List<PpdOption>): List<PpdOption> {
        val syntheticOptions = getSyntheticOptions()
        val finalOptions = parsedOptions.toMutableList()

        syntheticOptions.forEach { synthetic ->
            if (finalOptions.none { it.keyword.equals(synthetic.keyword, ignoreCase = true) }) {
                finalOptions.add(synthetic)
            }
        }

        if (finalOptions.none { it.keyword == "Copies" }) {
            finalOptions.add(syntheticOptions.first { it.keyword == "Copies" })
        }

        return finalOptions.sortedBy { it.displayOrder }
    }

    fun clearPrinterOptionsOnScreenExit() {
        clearAndResetOptions()
    }

    fun importDriversFromDirectory(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) { driverRepository.importDriversFromDirectory(treeUri) }
    }

    fun removeDriver(driver: Driver) {
        viewModelScope.launch(Dispatchers.IO) { driverRepository.removeDriver(driver) }
    }

    fun removeAllDrivers() {
        viewModelScope.launch(Dispatchers.IO) {
            driverRepository.removeAllDrivers()
        }
    }

    fun removeUnusedDrivers() {
        viewModelScope.launch(Dispatchers.IO) {
            driverRepository.removeUnusedDrivers(savedPrinters.value)
        }
    }

    fun addDiscoveredPrinter(printer: PrinterInfo) {
        printerRepository.addPrinter(printer)
    }

    fun getPrinterByIp(ip: String?): PrinterInfo? {
        return savedPrinters.value.find { it.hostAddress == ip }
    }

    fun updatePrinter(printer: PrinterInfo) {
        printerRepository.addPrinter(printer)
    }

    fun removePrinter(printer: PrinterInfo) {
        printerRepository.removePrinter(printer)
    }
}
