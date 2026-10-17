package com.ifind.app

import android.app.Application
import android.util.Log
import com.ifind.app.ble.BleManager
import com.ifind.app.ble.BleScanner
import com.ifind.app.ble.model.ButtonEvent
import com.ifind.app.data.PreferencesManager
import com.ifind.app.location.LocationTracker
import com.ifind.app.service.TrackerMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "IFindApp"

class IFindApp : Application() {

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var bleManager: BleManager
        private set

    lateinit var bleScanner: BleScanner
        private set

    lateinit var locationTracker: LocationTracker
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this

        preferencesManager = PreferencesManager(this)
        bleManager = BleManager(this)
        bleScanner = BleScanner(this)
        locationTracker = LocationTracker(this, preferencesManager)

        setupBleListeners()
    }

    private fun setupBleListeners() {
        // Disconnect hook
        bleManager.onDeviceDisconnectedListener = { address, name ->
            Log.i(TAG, "Device disconnected: $address ($name)")
            // Record phone location at the moment of link loss
            locationTracker.recordCurrentLocation()

            if (preferencesManager.disconnectAlertEnabled.value) {
                val displayName = preferencesManager.friendlyName.value ?: name ?: "Tracker"
                TrackerMonitorService.notifyDisconnect(this, displayName)
            }
        }

        // Button events hook (e.g. Find My Phone double click)
        applicationScope.launch {
            bleManager.buttonEvents.collect { event ->
                when (event) {
                    is ButtonEvent.DoublePress -> {
                        if (preferencesManager.findMyPhoneEnabled.value) {
                            Log.i(TAG, "Double press received - triggering Find My Phone!")
                            TrackerMonitorService.triggerFindPhone(this@IFindApp)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    companion object {
        lateinit var instance: IFindApp
            private set
    }
}
