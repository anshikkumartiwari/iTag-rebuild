package com.ifind.app.location.model

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LocationEntry(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val note: String = "Tracker Spot"
) {
    val formattedDateTime: String
        get() = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(timestamp))

    val isoTimestamp: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    fun toCsvRow(): String {
        return "\"$isoTimestamp\",$latitude,$longitude,$accuracy,\"$note\""
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("timestamp", timestamp)
            put("latitude", latitude)
            put("longitude", longitude)
            put("accuracy", accuracy.toDouble())
            put("note", note)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): LocationEntry {
            return LocationEntry(
                id = json.optLong("id", System.currentTimeMillis()),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                latitude = json.optDouble("latitude", 0.0),
                longitude = json.optDouble("longitude", 0.0),
                accuracy = json.optDouble("accuracy", 0.0).toFloat(),
                note = json.optString("note", "Tracker Spot")
            )
        }

        const val CSV_HEADER = "Date & Time,Latitude,Longitude,Accuracy (meters),Note"
    }
}
