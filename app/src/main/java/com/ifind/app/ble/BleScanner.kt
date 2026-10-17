package com.ifind.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.ifind.app.ble.model.DiscoveredDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BleScanner(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var scanner: BluetoothLeScanner? = null

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    private val deviceMap = LinkedHashMap<String, DiscoveredDevice>()
    private var scanTimeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val device = result.device ?: return
            val address = device.address ?: return
            val name = try {
                result.scanRecord?.deviceName ?: device.name
            } catch (e: SecurityException) {
                null
            }

            val item = DiscoveredDevice(
                device = device,
                name = name,
                address = address,
                rssi = result.rssi,
                lastSeenTimestamp = System.currentTimeMillis()
            )

            synchronized(deviceMap) {
                deviceMap[address] = item
                _discoveredDevices.value = deviceMap.values
                    .sortedWith(
                        compareByDescending<DiscoveredDevice> { it.isLikelyITag }
                            .thenByDescending { it.rssi }
                    )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            val msg = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE scan unsupported"
                SCAN_FAILED_INTERNAL_ERROR -> "BLE internal error"
                else -> "Scan failed ($errorCode)"
            }
            _scanError.value = msg
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(timeoutMillis: Long = 15000L) {
        if (!isBluetoothEnabled) {
            _scanError.value = "Bluetooth is turned off"
            return
        }

        scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            _scanError.value = "Bluetooth scanner not available"
            return
        }

        synchronized(deviceMap) {
            deviceMap.clear()
            _discoveredDevices.value = emptyList()
        }
        _scanError.value = null

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner?.startScan(emptyList<ScanFilter>(), settings, scanCallback)
            _isScanning.value = true

            scanTimeoutJob?.cancel()
            scanTimeoutJob = scope.launch {
                delay(timeoutMillis)
                stopScan()
            }
        } catch (e: SecurityException) {
            _scanError.value = "Bluetooth scan permission missing: ${e.message}"
            _isScanning.value = false
        } catch (e: Exception) {
            _scanError.value = "Failed to start scan: ${e.message}"
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        if (_isScanning.value) {
            try {
                scanner?.stopScan(scanCallback)
            } catch (_: Exception) {
            }
            _isScanning.value = false
        }
    }
}
