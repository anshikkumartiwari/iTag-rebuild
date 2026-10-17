package com.ifind.app.ble

import java.util.UUID

object BleConstants {
    // Battery Service (0x180F)
    val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val BATTERY_LEVEL_CHAR_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    // Link Loss Service (0x1803)
    val LINK_LOSS_SERVICE_UUID: UUID = UUID.fromString("00001803-0000-1000-8000-00805f9b34fb")
    val LINK_LOSS_ALERT_LEVEL_CHAR_UUID: UUID = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb")

    // Immediate Alert Service (0x1802)
    val IMMEDIATE_ALERT_SERVICE_UUID: UUID = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb")
    val IMMEDIATE_ALERT_LEVEL_CHAR_UUID: UUID = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb")

    // Custom Service (0xFFE0) from iTAG XML
    val CUSTOM_SERVICE_FFE0_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    val NOTIFY_CHAR_FFE1_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    val WRITE_CHAR_FFE2_UUID: UUID = UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb")
    val READ_CHAR_FFE3_UUID: UUID = UUID.fromString("0000ffe3-0000-1000-8000-00805f9b34fb")
    val RW_CHAR_FFE4_UUID: UUID = UUID.fromString("0000ffe4-0000-1000-8000-00805f9b34fb")
    val WRITE_CHAR_FFE5_UUID: UUID = UUID.fromString("0000ffe5-0000-1000-8000-00805f9b34fb")
    val READ_CHAR_FFE6_UUID: UUID = UUID.fromString("0000ffe6-0000-1000-8000-00805f9b34fb")
    val RW_CHAR_FFE7_UUID: UUID = UUID.fromString("0000ffe7-0000-1000-8000-00805f9b34fb")

    // Standard Descriptors
    val CCCD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    val REPORT_REFERENCE_DESCRIPTOR_UUID: UUID = UUID.fromString("00002908-0000-1000-8000-00805f9b34fb")

    // Standard Alert Levels
    const val ALERT_LEVEL_NO_ALERT: Byte = 0x00
    const val ALERT_LEVEL_MILD: Byte = 0x01
    const val ALERT_LEVEL_HIGH: Byte = 0x02

    fun shortUuid(uuid: UUID): String {
        val s = uuid.toString()
        return if (s.startsWith("0000") && s.endsWith("-0000-1000-8000-00805f9b34fb")) {
            "0x" + s.substring(4, 8).uppercase()
        } else {
            s.take(8).uppercase()
        }
    }
}
