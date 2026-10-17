package com.ifind.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ifind.app.ui.navigation.BottomNavBar
import com.ifind.app.ui.navigation.Screen
import com.ifind.app.ui.screens.CameraScreen
import com.ifind.app.ui.screens.DeviceScreen
import com.ifind.app.ui.screens.DiagnosticsScreen
import com.ifind.app.ui.screens.LocationScreen
import com.ifind.app.ui.screens.SettingsScreen
import com.ifind.app.ui.theme.IFindTheme

class MainActivity : ComponentActivity() {

    private val requiredPermissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val app = application as? IFindApp ?: return@registerForActivityResult
        val savedAddr = app.preferencesManager.savedDeviceAddress.value
        if (!savedAddr.isNullOrEmpty() && !app.bleManager.connectionState.value.isConnected) {
            app.bleManager.autoConnect(savedAddr)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsIfNeeded()

        val app = application as IFindApp
        val bleManager = app.bleManager
        val bleScanner = app.bleScanner
        val preferencesManager = app.preferencesManager
        val locationTracker = app.locationTracker

        // Auto-connect to saved tracker immediately on launch
        val savedAddr = preferencesManager.savedDeviceAddress.value
        if (!savedAddr.isNullOrEmpty() && !bleManager.connectionState.value.isConnected) {
            bleManager.autoConnect(savedAddr)
        }

        setContent {
            val appTheme by preferencesManager.theme.collectAsState()

            IFindTheme(appTheme = appTheme) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val isPrimaryTab = currentRoute in Screen.bottomNavItems.map { it.route }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (isPrimaryTab) {
                            BottomNavBar(
                                currentRoute = currentRoute,
                                onNavigate = { screen ->
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Device.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Device.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Device.route) {
                            DeviceScreen(
                                bleManager = bleManager,
                                bleScanner = bleScanner,
                                preferencesManager = preferencesManager,
                                onNavigateToCamera = { navController.navigate(Screen.Camera.route) },
                                onNavigateToDiagnostics = { navController.navigate(Screen.Diagnostics.route) }
                            )
                        }

                        composable(Screen.Location.route) {
                            LocationScreen(
                                locationTracker = locationTracker,
                                preferencesManager = preferencesManager
                            )
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(preferencesManager = preferencesManager)
                        }

                        composable(Screen.Diagnostics.route) {
                            DiagnosticsScreen(
                                bleManager = bleManager,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.Camera.route) {
                            CameraScreen(
                                bleManager = bleManager,
                                preferencesManager = preferencesManager,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val app = application as? IFindApp ?: return
        val savedAddr = app.preferencesManager.savedDeviceAddress.value
        if (!savedAddr.isNullOrEmpty() && !app.bleManager.connectionState.value.isConnected) {
            app.bleManager.autoConnect(savedAddr)
        }
    }

    private fun requestPermissionsIfNeeded() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}
