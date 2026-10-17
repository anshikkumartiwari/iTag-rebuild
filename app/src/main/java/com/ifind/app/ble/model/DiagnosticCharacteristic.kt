package com.ifind.app.ble.model

import java.util.UUID

data class DiagnosticCharacteristic(
    val serviceUuid: UUID,
    val characteristicUuid: UUID,
    val name: String,
    val readable: Boolean,
    val writable: Boolean,
    val notifiable: Boolean,
    val lastValueHex: String? = null,
    val lastReadTimestamp: Long? = null,
    val isReading: Boolean = false
)
