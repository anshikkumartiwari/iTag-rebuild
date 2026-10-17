package com.ifind.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.ifind.app.data.PreferencesManager

private const val TAG = "LocationTracker"

class LocationTracker(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun recordCurrentLocation() {
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        Log.i(TAG, "Recorded location: ${location.latitude}, ${location.longitude} (accuracy: ${location.accuracy}m)")
                        preferencesManager.addLocationRecord(
                            lat = location.latitude,
                            lng = location.longitude,
                            accuracy = location.accuracy,
                            timestamp = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
                        )
                    } else {
                        // Fall back to last known location
                        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                            if (lastLoc != null) {
                                preferencesManager.addLocationRecord(
                                    lat = lastLoc.latitude,
                                    lng = lastLoc.longitude,
                                    accuracy = lastLoc.accuracy,
                                    timestamp = lastLoc.time.takeIf { it > 0 } ?: System.currentTimeMillis()
                                )
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to get current location: ${e.message}")
                }
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Location error: ${e.message}")
        }
    }

    fun openInMaps(context: Context, lat: Double, lng: Double, label: String = "Tracker Last Known Location") {
        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
        val mapIntent = Intent(Intent.ACTION_VIEW, uri)
        mapIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            context.startActivity(mapIntent)
        } catch (e: Exception) {
            // Fallback to browser Google Maps
            val webUri = Uri.parse("https://maps.google.com/?q=$lat,$lng")
            val webIntent = Intent(Intent.ACTION_VIEW, webUri)
            webIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            try {
                context.startActivity(webIntent)
            } catch (_: Exception) {}
        }
    }
}
