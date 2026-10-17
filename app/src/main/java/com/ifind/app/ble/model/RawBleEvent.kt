package com.ifind.app.ble.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class RawBleEvent(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val characteristicUuid: UUID,
    val rawBytes: ByteArray,
    val deltaMs: Long = 0L,
    val note: String = ""
) {
    val hexString: String
        get() = rawBytes.joinToString(separator = " ") { "%02X".format(it) }

    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))

    val intValue: Int?
        get() = if (rawBytes.isNotEmpty()) rawBytes[0].toInt() and 0xFF else null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RawBleEvent
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

sealed interface ButtonEvent {
    val timestamp: Long

    data class SinglePress(
        override val timestamp: Long = System.currentTimeMillis(),
        val rawByte: Byte
    ) : ButtonEvent

    data class DoublePress(
        override val timestamp: Long = System.currentTimeMillis(),
        val intervalMs: Long
    ) : ButtonEvent

    data class RawNotification(
        override val timestamp: Long = System.currentTimeMillis(),
        val payload: ByteArray
    ) : ButtonEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as RawNotification
            return timestamp == other.timestamp && payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = timestamp.hashCode()
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }
}
