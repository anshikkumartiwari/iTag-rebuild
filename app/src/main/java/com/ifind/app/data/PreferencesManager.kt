package com.ifind.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTheme {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK
}

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ifind_prefs", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(getSavedTheme())
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    private val _friendlyName = MutableStateFlow(prefs.getString(KEY_FRIENDLY_NAME, null))
    val friendlyName: StateFlow<String?> = _friendlyName.asStateFlow()

    private val _savedDeviceAddress = MutableStateFlow(prefs.getString(KEY_SAVED_DEVICE_ADDR, null))
    val savedDeviceAddress: StateFlow<String?> = _savedDeviceAddress.asStateFlow()

    private val _savedDeviceName = MutableStateFlow(prefs.getString(KEY_SAVED_DEVICE_NAME, null))
    val savedDeviceName: StateFlow<String?> = _savedDeviceName.asStateFlow()

    private val _disconnectAlertEnabled = MutableStateFlow(prefs.getBoolean(KEY_DISCONNECT_ALERT, true))
    val disconnectAlertEnabled: StateFlow<Boolean> = _disconnectAlertEnabled.asStateFlow()

    private val _findMyPhoneEnabled = MutableStateFlow(prefs.getBoolean(KEY_FIND_MY_PHONE, true))
    val findMyPhoneEnabled: StateFlow<Boolean> = _findMyPhoneEnabled.asStateFlow()

    private val _cameraCountdownSeconds = MutableStateFlow(prefs.getInt(KEY_CAMERA_COUNTDOWN, 0))
    val cameraCountdownSeconds: StateFlow<Int> = _cameraCountdownSeconds.asStateFlow()

    // Location state
    private val _lastLatitude = MutableStateFlow(if (prefs.contains(KEY_LAST_LAT)) prefs.getFloat(KEY_LAST_LAT, 0f).toDouble() else null)
    val lastLatitude: StateFlow<Double?> = _lastLatitude.asStateFlow()

    private val _lastLongitude = MutableStateFlow(if (prefs.contains(KEY_LAST_LNG)) prefs.getFloat(KEY_LAST_LNG, 0f).toDouble() else null)
    val lastLongitude: StateFlow<Double?> = _lastLongitude.asStateFlow()

    private val _lastLocationTimestamp = MutableStateFlow(if (prefs.contains(KEY_LAST_LOC_TIME)) prefs.getLong(KEY_LAST_LOC_TIME, 0L) else null)
    val lastLocationTimestamp: StateFlow<Long?> = _lastLocationTimestamp.asStateFlow()

    private val _lastLocationAccuracy = MutableStateFlow(if (prefs.contains(KEY_LAST_LOC_ACC)) prefs.getFloat(KEY_LAST_LOC_ACC, 0f) else null)
    val lastLocationAccuracy: StateFlow<Float?> = _lastLocationAccuracy.asStateFlow()

    private fun getSavedTheme(): AppTheme {
        val name = prefs.getString(KEY_THEME, AppTheme.FOLLOW_SYSTEM.name) ?: AppTheme.FOLLOW_SYSTEM.name
        return try {
            AppTheme.valueOf(name)
        } catch (_: Exception) {
            AppTheme.FOLLOW_SYSTEM
        }
    }

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        _theme.value = theme
    }

    fun setFriendlyName(name: String?) {
        prefs.edit().putString(KEY_FRIENDLY_NAME, name).apply()
        _friendlyName.value = name
    }

    fun setSavedDeviceAddress(address: String?) {
        prefs.edit().putString(KEY_SAVED_DEVICE_ADDR, address).apply()
        _savedDeviceAddress.value = address
    }

    fun saveDevice(address: String, name: String?) {
        prefs.edit()
            .putString(KEY_SAVED_DEVICE_ADDR, address)
            .putString(KEY_SAVED_DEVICE_NAME, name)
            .apply()
        _savedDeviceAddress.value = address
        _savedDeviceName.value = name
    }

    fun forgetSavedDevice() {
        prefs.edit()
            .remove(KEY_SAVED_DEVICE_ADDR)
            .remove(KEY_SAVED_DEVICE_NAME)
            .apply()
        _savedDeviceAddress.value = null
        _savedDeviceName.value = null
    }

    fun setDisconnectAlertEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DISCONNECT_ALERT, enabled).apply()
        _disconnectAlertEnabled.value = enabled
    }

    fun setFindMyPhoneEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FIND_MY_PHONE, enabled).apply()
        _findMyPhoneEnabled.value = enabled
    }

    fun setCameraCountdown(seconds: Int) {
        prefs.edit().putInt(KEY_CAMERA_COUNTDOWN, seconds).apply()
        _cameraCountdownSeconds.value = seconds
    }

    fun saveLastLocation(lat: Double, lng: Double, accuracy: Float, timestamp: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putFloat(KEY_LAST_LAT, lat.toFloat())
            .putFloat(KEY_LAST_LNG, lng.toFloat())
            .putFloat(KEY_LAST_LOC_ACC, accuracy)
            .putLong(KEY_LAST_LOC_TIME, timestamp)
            .apply()

        _lastLatitude.value = lat
        _lastLongitude.value = lng
        _lastLocationAccuracy.value = accuracy
        _lastLocationTimestamp.value = timestamp
    }

    // Location History list
    private val _locationHistory = MutableStateFlow(loadLocationHistory())
    val locationHistory: StateFlow<List<com.ifind.app.location.model.LocationEntry>> = _locationHistory.asStateFlow()

    private fun loadLocationHistory(): List<com.ifind.app.location.model.LocationEntry> {
        val rawJson = prefs.getString(KEY_LOCATION_HISTORY, null) ?: return emptyList()
        val list = mutableListOf<com.ifind.app.location.model.LocationEntry>()
        try {
            val array = org.json.JSONArray(rawJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(com.ifind.app.location.model.LocationEntry.fromJson(obj))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun persistLocationHistory(list: List<com.ifind.app.location.model.LocationEntry>) {
        val array = org.json.JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_LOCATION_HISTORY, array.toString()).apply()
        _locationHistory.value = list
    }

    fun addLocationRecord(lat: Double, lng: Double, accuracy: Float, timestamp: Long = System.currentTimeMillis(), note: String = "Tracker Spot") {
        saveLastLocation(lat, lng, accuracy, timestamp)

        val newEntry = com.ifind.app.location.model.LocationEntry(
            id = System.currentTimeMillis(),
            timestamp = timestamp,
            latitude = lat,
            longitude = lng,
            accuracy = accuracy,
            note = note
        )
        val current = _locationHistory.value.toMutableList()
        current.add(0, newEntry)
        // Keep up to 200 recent records
        if (current.size > 200) {
            current.removeAt(current.lastIndex)
        }
        persistLocationHistory(current)
    }

    fun clearAllLocations() {
        persistLocationHistory(emptyList())
    }

    fun clearLocationsOlderThanOneWeek() {
        val oneWeekAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000L)
        val filtered = _locationHistory.value.filter { it.timestamp >= oneWeekAgo }
        persistLocationHistory(filtered)
    }

    fun exportLocationCsv(): String {
        val sb = StringBuilder()
        sb.appendLine(com.ifind.app.location.model.LocationEntry.CSV_HEADER)
        _locationHistory.value.forEach { entry ->
            sb.appendLine(entry.toCsvRow())
        }
        return sb.toString()
    }

    // Floating Clicker Preferences
    private val _floaterActionMode = MutableStateFlow(prefs.getString(KEY_FLOATER_MODE, "click") ?: "click")
    val floaterActionMode: StateFlow<String> = _floaterActionMode.asStateFlow()

    private val _floaterScrollDistanceDp = MutableStateFlow(prefs.getInt(KEY_FLOATER_SCROLL_DP, 550))
    val floaterScrollDistanceDp: StateFlow<Int> = _floaterScrollDistanceDp.asStateFlow()

    private val _floaterDoubleTapLike = MutableStateFlow(prefs.getBoolean(KEY_FLOATER_DOUBLE_TAP_LIKE, false))
    val floaterDoubleTapLike: StateFlow<Boolean> = _floaterDoubleTapLike.asStateFlow()

    fun setFloaterActionMode(mode: String) {
        prefs.edit().putString(KEY_FLOATER_MODE, mode).apply()
        _floaterActionMode.value = mode
    }

    fun setFloaterScrollDistanceDp(distanceDp: Int) {
        val clamped = distanceDp.coerceIn(200, 1200)
        prefs.edit().putInt(KEY_FLOATER_SCROLL_DP, clamped).apply()
        _floaterScrollDistanceDp.value = clamped
    }

    fun setFloaterDoubleTapLike(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLOATER_DOUBLE_TAP_LIKE, enabled).apply()
        _floaterDoubleTapLike.value = enabled
    }

    fun resetFloaterSettings() {
        setFloaterActionMode("click")
        setFloaterScrollDistanceDp(550)
        setFloaterDoubleTapLike(false)
    }

    companion object {
        private const val KEY_THEME = "app_theme"
        private const val KEY_FRIENDLY_NAME = "device_friendly_name"
        private const val KEY_SAVED_DEVICE_ADDR = "saved_device_addr"
        private const val KEY_SAVED_DEVICE_NAME = "saved_device_name"
        private const val KEY_DISCONNECT_ALERT = "disconnect_alert_enabled"
        private const val KEY_FIND_MY_PHONE = "find_my_phone_enabled"
        private const val KEY_CAMERA_COUNTDOWN = "camera_countdown_sec"
        private const val KEY_LAST_LAT = "last_latitude"
        private const val KEY_LAST_LNG = "last_longitude"
        private const val KEY_LAST_LOC_TIME = "last_location_time"
        private const val KEY_LAST_LOC_ACC = "last_location_acc"
        private const val KEY_LOCATION_HISTORY = "location_history_json"
        private const val KEY_FLOATER_MODE = "floater_action_mode"
        private const val KEY_FLOATER_SCROLL_DP = "floater_scroll_distance_dp"
        private const val KEY_FLOATER_DOUBLE_TAP_LIKE = "floater_double_tap_like"
    }
}
