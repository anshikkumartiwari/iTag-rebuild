package com.ifind.app.ble.model

import android.bluetooth.BluetoothDevice

data class DiscoveredDevice(
    val device: BluetoothDevice,
    val name: String?,
    val address: String,
    val rssi: Int,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: "Unknown Tracker ($address)"

    val isLikelyITag: Boolean
        get() {
            val n = (name ?: "").lowercase()
            return n.contains("itag") || n.contains("isearching") || n.contains("smart tag") || n.contains("c-tag")
        }
}
