package com.ifind.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Device : Screen("device", "Device", Icons.Rounded.TrackChanges)
    data object Location : Screen("location", "Location", Icons.Rounded.LocationOn)
    data object Settings : Screen("settings", "Settings", Icons.Rounded.Settings)
    data object Diagnostics : Screen("diagnostics", "Diagnostics", Icons.Rounded.Psychology)
    data object Camera : Screen("camera", "Camera", Icons.Rounded.CameraAlt)

    companion object {
        val bottomNavItems = listOf(Device, Location, Settings)
    }
}
