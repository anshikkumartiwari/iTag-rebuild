package com.ifind.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import com.ifind.app.ble.model.ButtonEvent
import com.ifind.app.ble.model.ConnectionState
import com.ifind.app.ble.model.DiagnosticCharacteristic
import com.ifind.app.ble.model.RawBleEvent
import com.ifind.app.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "BleManager"

class BleManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager? = null
) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var connectedDevice: BluetoothDevice? = null

    // State flows
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _connectedDeviceAddress = MutableStateFlow<String?>(null)
    val connectedDeviceAddress: StateFlow<String?> = _connectedDeviceAddress.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

    private val _isBatteryReading = MutableStateFlow(false)
    val isBatteryReading: StateFlow<Boolean> = _isBatteryReading.asStateFlow()

    private val _rssi = MutableStateFlow<Int?>(null)
    val rssi: StateFlow<Int?> = _rssi.asStateFlow()

    private val _isRssiUpdating = MutableStateFlow(false)
    val isRssiUpdating: StateFlow<Boolean> = _isRssiUpdating.asStateFlow()

    private val _isAlarmActive = MutableStateFlow(false)
    val isAlarmActive: StateFlow<Boolean> = _isAlarmActive.asStateFlow()

    // Diagnostic & Button event streams
    private val _rawBleEvents = MutableStateFlow<List<RawBleEvent>>(emptyList())
    val rawBleEvents: StateFlow<List<RawBleEvent>> = _rawBleEvents.asStateFlow()

    private val _buttonEvents = MutableSharedFlow<ButtonEvent>(extraBufferCapacity = 64)
    val buttonEvents: SharedFlow<ButtonEvent> = _buttonEvents.asSharedFlow()

    private val _diagnosticCharacteristics = MutableStateFlow<List<DiagnosticCharacteristic>>(emptyList())
    val diagnosticCharacteristics: StateFlow<List<DiagnosticCharacteristic>> = _diagnosticCharacteristics.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Disconnect listener callback
    var onDeviceDisconnectedListener: ((address: String, name: String?) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.Main)
    private var lastRawPacketTime = 0L
    private var lastButtonClickTime = 0L
    private var pendingSingleClickJob: Job? = null

    // GATT command queue to guarantee sequential execution
    private sealed interface GattOp {
        data class ReadChar(val serviceUuid: UUID, val charUuid: UUID) : GattOp
        data class WriteDescriptor(val descriptor: BluetoothGattDescriptor, val value: ByteArray) : GattOp
        data class WriteChar(val characteristic: BluetoothGattCharacteristic, val value: ByteArray, val writeType: Int) : GattOp
    }

    private val gattQueue = Channel<GattOp>(Channel.UNLIMITED)
    private var isProcessingQueue = false
    private var queueWaitJob: Job? = null
    private val isWaitingCallback = AtomicBoolean(false)

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val device = gatt?.device
            Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState, device=${device?.address}")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                val errorMsg = when (status) {
                    133 -> "Connection timeout (133). Please bring tracker closer."
                    8 -> "Tracker disconnected (Link timeout)."
                    19 -> "Tracker terminated connection."
                    22 -> "Local host terminated connection."
                    else -> "Connection error ($status)"
                }
                Log.w(TAG, "GATT status error: $errorMsg")
                scope.launch {
                    val prevAddr = _connectedDeviceAddress.value
                    val prevName = _connectedDeviceName.value
                    closeGatt()
                    _connectionState.value = ConnectionState.Error(errorMsg, status)
                    if (prevAddr != null) {
                        onDeviceDisconnectedListener?.invoke(prevAddr, prevName)
                    }
                }
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "GATT Connected. Discovering services...")
                    scope.launch {
                        _connectionState.value = ConnectionState.Connected
                        _connectedDeviceAddress.value = device?.address
                        _connectedDeviceName.value = try { device?.name } catch (e: SecurityException) { null }
                        delay(400) // Brief delay to stabilize connection on Telink/Lenze chips
                        _connectionState.value = ConnectionState.DiscoveringServices
                        gatt?.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "GATT Disconnected cleanly.")
                    scope.launch {
                        val prevAddr = _connectedDeviceAddress.value
                        val prevName = _connectedDeviceName.value
                        closeGatt()
                        _connectionState.value = ConnectionState.Disconnected
                        if (prevAddr != null) {
                            onDeviceDisconnectedListener?.invoke(prevAddr, prevName)
                        }
                    }
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    _connectionState.value = ConnectionState.Connecting
                }
                BluetoothProfile.STATE_DISCONNECTING -> {
                    _connectionState.value = ConnectionState.Disconnecting
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            Log.d(TAG, "onServicesDiscovered: status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                scope.launch {
                    _connectionState.value = ConnectionState.Error("Service discovery failed ($status)", status)
                }
                return
            }

            scope.launch {
                _connectionState.value = ConnectionState.Ready
                _statusMessage.value = "Connected and ready"

                populateDiagnostics(gatt)
                // Configure notifications and read battery
                setupTrackerSubscriptions(gatt)
                readRssi()

                // Explicit delay then read battery directly
                delay(800)
                readBatteryLevel()
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            characteristic ?: return
            @Suppress("DEPRECATION")
            val data = characteristic.value ?: ByteArray(0)
            handleCharacteristicRead(characteristic.uuid, data, status)
            notifyQueueOpDone()
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            handleCharacteristicRead(characteristic.uuid, value, status)
            notifyQueueOpDone()
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            characteristic ?: return
            @Suppress("DEPRECATION")
            val data = characteristic.value ?: ByteArray(0)
            handleCharacteristicChanged(characteristic.uuid, data)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleCharacteristicChanged(characteristic.uuid, value)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            Log.d(TAG, "onDescriptorWrite: desc=${descriptor?.uuid}, status=$status")
            notifyQueueOpDone()
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            Log.d(TAG, "onCharacteristicWrite: char=${characteristic?.uuid}, status=$status")
            notifyQueueOpDone()
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            _isRssiUpdating.value = false
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _rssi.value = rssi
            }
        }
    }

    private fun handleCharacteristicRead(uuid: UUID, data: ByteArray, status: Int) {
        _isBatteryReading.value = false
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.w(TAG, "handleCharacteristicRead error: status=$status for char=$uuid")
            return
        }

        if (uuid == BleConstants.BATTERY_LEVEL_CHAR_UUID && data.isNotEmpty()) {
            val level = data[0].toInt() and 0xFF
            _batteryLevel.value = level.coerceIn(0, 100)
            Log.i(TAG, "Battery level successfully read: $level%")
        }

        // Update diagnostic list
        val hex = data.joinToString(" ") { "%02X".format(it) }
        scope.launch {
            _diagnosticCharacteristics.value = _diagnosticCharacteristics.value.map { item ->
                if (item.characteristicUuid == uuid) {
                    item.copy(lastValueHex = hex, lastReadTimestamp = System.currentTimeMillis(), isReading = false)
                } else {
                    item
                }
            }
        }
    }

    private fun handleCharacteristicChanged(uuid: UUID, data: ByteArray) {
        val now = System.currentTimeMillis()
        val delta = if (lastRawPacketTime > 0) now - lastRawPacketTime else 0L
        lastRawPacketTime = now

        val event = RawBleEvent(
            characteristicUuid = uuid,
            rawBytes = data,
            deltaMs = delta,
            note = if (uuid == BleConstants.NOTIFY_CHAR_FFE1_UUID) "Button notification" else "Notification"
        )

        scope.launch {
            // Keep up to 100 recent events
            val current = _rawBleEvents.value.toMutableList()
            current.add(0, event)
            if (current.size > 100) {
                current.removeAt(current.lastIndex)
            }
            _rawBleEvents.value = current
        }

        if (uuid == BleConstants.BATTERY_LEVEL_CHAR_UUID && data.isNotEmpty()) {
            val level = data[0].toInt() and 0xFF
            _batteryLevel.value = level.coerceIn(0, 100)
        } else if (uuid == BleConstants.NOTIFY_CHAR_FFE1_UUID) {
            // Process physical button payload
            processButtonNotification(data, now)
        }
    }

    private fun processButtonNotification(data: ByteArray, now: Long) {
        val rawByte = if (data.isNotEmpty()) data[0] else 0x01.toByte()

        scope.launch {
            _buttonEvents.emit(ButtonEvent.RawNotification(now, data))
        }

        val doubleTapEnabled = preferencesManager?.floaterDoubleTapLike?.value == true

        if (!doubleTapEnabled) {
            // Instant, zero-delay single click mode!
            // Debounce rapid duplicate BLE packets / physical contact bounce from same press within 140ms
            val intervalSinceLastClick = now - lastButtonClickTime
            if (intervalSinceLastClick < 140) {
                return
            }
            lastButtonClickTime = now
            pendingSingleClickJob?.cancel()
            pendingSingleClickJob = null

            scope.launch {
                Log.i(TAG, "Instant single click dispatched (Double-tap disabled)!")
                _buttonEvents.emit(ButtonEvent.SinglePress(now, rawByte))
            }
        } else {
            // Double-tap mode is enabled: wait briefly to distinguish single vs double click
            val intervalSinceLastClick = now - lastButtonClickTime
            if (intervalSinceLastClick < 120) {
                // Ignore contact bounce from same physical press
                return
            }

            // Check for double click window (between 120ms and 420ms)
            if (intervalSinceLastClick in 120..420) {
                pendingSingleClickJob?.cancel()
                pendingSingleClickJob = null
                lastButtonClickTime = now

                scope.launch {
                    Log.i(TAG, "Double press detected! Interval: ${intervalSinceLastClick}ms")
                    _buttonEvents.emit(ButtonEvent.DoublePress(now, intervalSinceLastClick))
                }
            } else {
                // First click: wait up to 380ms to check if a second click arrives
                lastButtonClickTime = now
                pendingSingleClickJob?.cancel()
                pendingSingleClickJob = scope.launch {
                    delay(380)
                    Log.i(TAG, "Single press confirmed (after double-click window)")
                    _buttonEvents.emit(ButtonEvent.SinglePress(now, rawByte))
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupTrackerSubscriptions(gatt: BluetoothGatt) {
        // Subscribe to FFE1 button notifications
        val ffe0Service = gatt.getService(BleConstants.CUSTOM_SERVICE_FFE0_UUID)
        val ffe1Char = ffe0Service?.getCharacteristic(BleConstants.NOTIFY_CHAR_FFE1_UUID)

        if (ffe1Char != null) {
            gatt.setCharacteristicNotification(ffe1Char, true)
            val cccd = ffe1Char.getDescriptor(BleConstants.CCCD_DESCRIPTOR_UUID)
            if (cccd != null) {
                enqueueOp(GattOp.WriteDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE))
            }
        }

        // Subscribe to Battery Service
        val batteryService = gatt.getService(BleConstants.BATTERY_SERVICE_UUID)
        val batteryChar = batteryService?.getCharacteristic(BleConstants.BATTERY_LEVEL_CHAR_UUID)
        if (batteryChar != null) {
            val cccd = batteryChar.getDescriptor(BleConstants.CCCD_DESCRIPTOR_UUID)
            if (cccd != null) {
                gatt.setCharacteristicNotification(batteryChar, true)
                enqueueOp(GattOp.WriteDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE))
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun readBatteryLevel() {
        val gatt = bluetoothGatt ?: return
        val batteryService = gatt.getService(BleConstants.BATTERY_SERVICE_UUID)
        val batteryChar = batteryService?.getCharacteristic(BleConstants.BATTERY_LEVEL_CHAR_UUID)
        if (batteryChar != null) {
            _isBatteryReading.value = true
            enqueueOp(GattOp.ReadChar(BleConstants.BATTERY_SERVICE_UUID, BleConstants.BATTERY_LEVEL_CHAR_UUID))
        } else {
            Log.w(TAG, "Battery Service or Characteristic not found on GATT server")
        }
    }

    private fun populateDiagnostics(gatt: BluetoothGatt) {
        val list = mutableListOf<DiagnosticCharacteristic>()
        for (service in gatt.services) {
            for (char in service.characteristics) {
                val readable = (char.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0
                val writable = (char.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                val notifiable = (char.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0

                val name = when {
                    service.uuid == BleConstants.BATTERY_SERVICE_UUID && char.uuid == BleConstants.BATTERY_LEVEL_CHAR_UUID -> "Battery Level (2A19)"
                    service.uuid == BleConstants.LINK_LOSS_SERVICE_UUID && char.uuid == BleConstants.LINK_LOSS_ALERT_LEVEL_CHAR_UUID -> "Link Loss Alert Level (2A06)"
                    service.uuid == BleConstants.IMMEDIATE_ALERT_SERVICE_UUID && char.uuid == BleConstants.IMMEDIATE_ALERT_LEVEL_CHAR_UUID -> "Immediate Alert Level (2A06)"
                    char.uuid == BleConstants.NOTIFY_CHAR_FFE1_UUID -> "Button Notify (FFE1)"
                    char.uuid == BleConstants.WRITE_CHAR_FFE2_UUID -> "Custom Write (FFE2)"
                    char.uuid == BleConstants.READ_CHAR_FFE3_UUID -> "Custom Read (FFE3)"
                    char.uuid == BleConstants.RW_CHAR_FFE4_UUID -> "Custom Read/Write (FFE4)"
                    char.uuid == BleConstants.WRITE_CHAR_FFE5_UUID -> "Custom Write (FFE5)"
                    char.uuid == BleConstants.READ_CHAR_FFE6_UUID -> "Custom Read (FFE6)"
                    char.uuid == BleConstants.RW_CHAR_FFE7_UUID -> "Custom Read/Write (FFE7)"
                    else -> "${BleConstants.shortUuid(char.uuid)} (${BleConstants.shortUuid(service.uuid)})"
                }

                list.add(
                    DiagnosticCharacteristic(
                        serviceUuid = service.uuid,
                        characteristicUuid = char.uuid,
                        name = name,
                        readable = readable,
                        writable = writable,
                        notifiable = notifiable
                    )
                )
            }
        }
        _diagnosticCharacteristics.value = list
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        disconnect()
        closeGatt()

        connectedDevice = device
        _connectedDeviceAddress.value = device.address
        _connectedDeviceName.value = try { device.name } catch (e: SecurityException) { null }
        _connectionState.value = ConnectionState.Connecting

        try {
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.Error("Bluetooth connect permission missing: ${e.message}")
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("Connection error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun connectByAddress(address: String) {
        val adapter = bluetoothAdapter ?: return
        try {
            val device = adapter.getRemoteDevice(address)
            connect(device)
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("Invalid Bluetooth address: $address")
        }
    }

    @SuppressLint("MissingPermission")
    fun autoConnect(address: String) {
        if (_connectionState.value.isConnected || _connectionState.value == ConnectionState.Connecting) {
            return
        }
        Log.i(TAG, "Starting auto-connect to saved tracker: $address")
        connectByAddress(address)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.let { gatt ->
            try {
                gatt.disconnect()
            } catch (_: Exception) {}
        }
        _batteryLevel.value = null
        _rssi.value = null
        _isAlarmActive.value = false
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        bluetoothGatt?.let { gatt ->
            try {
                gatt.close()
            } catch (_: Exception) {}
        }
        bluetoothGatt = null
        isProcessingQueue = false
        isWaitingCallback.set(false)
        queueWaitJob?.cancel()
    }

    @SuppressLint("MissingPermission")
    fun readRssi() {
        val gatt = bluetoothGatt ?: return
        if (_connectionState.value.isConnected) {
            _isRssiUpdating.value = true
            try {
                gatt.readRemoteRssi()
            } catch (e: Exception) {
                _isRssiUpdating.value = false
            }
        }
    }

    /**
     * Start Alarm: Transmits Immediate Alert 0x01 (Mild, Response) verified on physical hardware.
     */
    @SuppressLint("MissingPermission")
    fun startAlarm() {
        val gatt = bluetoothGatt ?: return
        var sent = false

        // Primary verified command: Immediate Alert Service (0x1802 -> 0x2A06) with 0x01 (Mild, Response)
        val immService = gatt.getService(BleConstants.IMMEDIATE_ALERT_SERVICE_UUID)
        val immChar = immService?.getCharacteristic(BleConstants.IMMEDIATE_ALERT_LEVEL_CHAR_UUID)
        if (immChar != null) {
            enqueueOp(
                GattOp.WriteChar(
                    characteristic = immChar,
                    value = byteArrayOf(BleConstants.ALERT_LEVEL_MILD),
                    writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            )
            sent = true
        }

        // Secondary fallback for Link Loss characteristic
        val linkService = gatt.getService(BleConstants.LINK_LOSS_SERVICE_UUID)
        val linkChar = linkService?.getCharacteristic(BleConstants.LINK_LOSS_ALERT_LEVEL_CHAR_UUID)
        if (linkChar != null) {
            enqueueOp(
                GattOp.WriteChar(
                    characteristic = linkChar,
                    value = byteArrayOf(BleConstants.ALERT_LEVEL_MILD),
                    writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            )
        }

        if (sent) {
            _isAlarmActive.value = true
            _statusMessage.value = "Alarm active (Immediate Alert 0x01)"
        } else {
            _statusMessage.value = "Immediate Alert Service not found on tracker"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAlarm() {
        val gatt = bluetoothGatt ?: return

        // 1. Immediate Alert Service (0x1802 -> 0x2A06)
        val immService = gatt.getService(BleConstants.IMMEDIATE_ALERT_SERVICE_UUID)
        val immChar = immService?.getCharacteristic(BleConstants.IMMEDIATE_ALERT_LEVEL_CHAR_UUID)
        if (immChar != null) {
            enqueueOp(
                GattOp.WriteChar(
                    characteristic = immChar,
                    value = byteArrayOf(BleConstants.ALERT_LEVEL_NO_ALERT),
                    writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            )
            enqueueOp(
                GattOp.WriteChar(
                    characteristic = immChar,
                    value = byteArrayOf(BleConstants.ALERT_LEVEL_NO_ALERT),
                    writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                )
            )
        }

        // 2. Custom Characteristic FFE2
        val ffe0Service = gatt.getService(BleConstants.CUSTOM_SERVICE_FFE0_UUID)
        val ffe2Char = ffe0Service?.getCharacteristic(BleConstants.WRITE_CHAR_FFE2_UUID)
        if (ffe2Char != null) {
            enqueueOp(
                GattOp.WriteChar(
                    characteristic = ffe2Char,
                    value = byteArrayOf(0x00),
                    writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            )
        }

        _isAlarmActive.value = false
        _statusMessage.value = "Stop alarm command transmitted"
    }

    /**
     * Direct test write for diagnostic exploration
     */
    @SuppressLint("MissingPermission")
    fun testWriteCharacteristic(serviceUuid: UUID, charUuid: UUID, payload: ByteArray, writeType: Int) {
        val gatt = bluetoothGatt ?: return
        val service = gatt.getService(serviceUuid)
        val char = service?.getCharacteristic(charUuid)
        if (char != null) {
            enqueueOp(GattOp.WriteChar(char, payload, writeType))
            _statusMessage.value = "Sent 0x${payload.joinToString("") { "%02X".format(it) }} to ${BleConstants.shortUuid(charUuid)}"
        }
    }

    @SuppressLint("MissingPermission")
    fun setLinkLossAlertLevel(level: Byte) {
        val gatt = bluetoothGatt ?: return
        val service = gatt.getService(BleConstants.LINK_LOSS_SERVICE_UUID)
        val char = service?.getCharacteristic(BleConstants.LINK_LOSS_ALERT_LEVEL_CHAR_UUID)
        if (char != null) {
            enqueueOp(
                GattOp.WriteChar(
                    characteristic = char,
                    value = byteArrayOf(level),
                    writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            )
        }
    }

    fun readDiagnosticCharacteristic(serviceUuid: UUID, charUuid: UUID) {
        scope.launch {
            _diagnosticCharacteristics.value = _diagnosticCharacteristics.value.map {
                if (it.characteristicUuid == charUuid && it.serviceUuid == serviceUuid) it.copy(isReading = true) else it
            }
            enqueueOp(GattOp.ReadChar(serviceUuid, charUuid))
        }
    }

    fun clearRawEvents() {
        _rawBleEvents.value = emptyList()
    }

    // GATT Sequential Queue handling
    private fun enqueueOp(op: GattOp) {
        scope.launch {
            gattQueue.send(op)
            processNextOp()
        }
    }

    @SuppressLint("MissingPermission")
    private fun processNextOp() {
        if (isProcessingQueue) return
        val gatt = bluetoothGatt ?: return

        scope.launch {
            val op = gattQueue.tryReceive().getOrNull() ?: return@launch
            isProcessingQueue = true
            isWaitingCallback.set(true)

            // Safety timeout: if device does not reply in 1500ms, unblock queue
            queueWaitJob?.cancel()
            queueWaitJob = scope.launch {
                delay(1500)
                Log.w(TAG, "GATT Op timed out, unblocking queue")
                notifyQueueOpDone()
            }

            when (op) {
                is GattOp.ReadChar -> {
                    val service = gatt.getService(op.serviceUuid)
                    val char = service?.getCharacteristic(op.charUuid)
                    if (char != null) {
                        try {
                            val ok = gatt.readCharacteristic(char)
                            if (!ok) notifyQueueOpDone()
                        } catch (e: Exception) {
                            notifyQueueOpDone()
                        }
                    } else {
                        notifyQueueOpDone()
                    }
                }
                is GattOp.WriteDescriptor -> {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val res = gatt.writeDescriptor(op.descriptor, op.value)
                            if (res != android.bluetooth.BluetoothStatusCodes.SUCCESS) {
                                notifyQueueOpDone()
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            op.descriptor.value = op.value
                            @Suppress("DEPRECATION")
                            val ok = gatt.writeDescriptor(op.descriptor)
                            if (!ok) notifyQueueOpDone()
                        }
                    } catch (e: Exception) {
                        notifyQueueOpDone()
                    }
                }
                is GattOp.WriteChar -> {
                    try {
                        op.characteristic.writeType = op.writeType
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val res = gatt.writeCharacteristic(op.characteristic, op.value, op.writeType)
                            if (res != android.bluetooth.BluetoothStatusCodes.SUCCESS) {
                                notifyQueueOpDone()
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            op.characteristic.value = op.value
                            @Suppress("DEPRECATION")
                            val ok = gatt.writeCharacteristic(op.characteristic)
                            if (!ok) notifyQueueOpDone()
                        }
                    } catch (e: Exception) {
                        notifyQueueOpDone()
                    }
                }
            }
        }
    }

    private fun notifyQueueOpDone() {
        if (isWaitingCallback.compareAndSet(true, false)) {
            queueWaitJob?.cancel()
            queueWaitJob = null
            isProcessingQueue = false
            processNextOp()
        }
    }
}
