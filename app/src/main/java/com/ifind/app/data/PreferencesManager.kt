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

    companion object {
        private const val KEY_THEME = "app_theme"
        private const val KEY_FRIENDLY_NAME = "device_friendly_name"
        private const val KEY_SAVED_DEVICE_ADDR = "saved_device_addr"
        private const val KEY_DISCONNECT_ALERT = "disconnect_alert_enabled"
        private const val KEY_FIND_MY_PHONE = "find_my_phone_enabled"
        private const val KEY_CAMERA_COUNTDOWN = "camera_countdown_sec"
        private const val KEY_LAST_LAT = "last_latitude"
        private const val KEY_LAST_LNG = "last_longitude"
        private const val KEY_LAST_LOC_TIME = "last_location_time"
        private const val KEY_LAST_LOC_ACC = "last_location_acc"
    }
}
