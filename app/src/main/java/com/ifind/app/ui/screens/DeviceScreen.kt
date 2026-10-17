package com.ifind.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ifind.app.ble.BleManager
import com.ifind.app.ble.BleScanner
import com.ifind.app.ble.model.ConnectionState
import com.ifind.app.ble.model.DiscoveredDevice
import com.ifind.app.data.PreferencesManager
import com.ifind.app.ui.theme.AccentAmber
import com.ifind.app.ui.theme.AccentCoral
import com.ifind.app.ui.theme.AccentEmerald
import com.ifind.app.ui.theme.BrandCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    bleManager: BleManager,
    bleScanner: BleScanner,
    preferencesManager: PreferencesManager,
    onNavigateToCamera: () -> Unit,
    onNavigateToDiagnostics: () -> Unit
) {
    val connectionState by bleManager.connectionState.collectAsState()
    val connectedDeviceAddress by bleManager.connectedDeviceAddress.collectAsState()
    val connectedDeviceName by bleManager.connectedDeviceName.collectAsState()
    val batteryLevel by bleManager.batteryLevel.collectAsState()
    val rssi by bleManager.rssi.collectAsState()
    val isRssiUpdating by bleManager.isRssiUpdating.collectAsState()
    val isAlarmActive by bleManager.isAlarmActive.collectAsState()
    val statusMessage by bleManager.statusMessage.collectAsState()

    val friendlyName by preferencesManager.friendlyName.collectAsState()
    val disconnectAlertEnabled by preferencesManager.disconnectAlertEnabled.collectAsState()

    var showScanSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newFriendlyNameText by remember { mutableStateOf("") }

    val isConnected = connectionState.isConnected
    val isReady = connectionState.isReady

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "iFind Tracker",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (isConnected) "Tracker connected & active" else "No tracker connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onNavigateToDiagnostics,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Psychology,
                    contentDescription = "Diagnostics",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Device Hero Card
        DeviceHeroCard(
            connectionState = connectionState,
            displayName = friendlyName ?: connectedDeviceName ?: "iTAG Tracker",
            macAddress = connectedDeviceAddress,
            onRenameClicked = {
                newFriendlyNameText = friendlyName ?: connectedDeviceName ?: ""
                showRenameDialog = true
            },
            onConnectClicked = { showScanSheet = true },
            onDisconnectClicked = { bleManager.disconnect() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Status message banner if any
        AnimatedVisibility(visible = statusMessage != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Metrics Row: Battery & RSSI
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Battery Card
            BatteryCard(
                batteryLevel = batteryLevel,
                isConnected = isConnected,
                modifier = Modifier.weight(1f)
            )

            // RSSI Signal Card
            SignalCard(
                rssi = rssi,
                isUpdating = isRssiUpdating,
                isConnected = isConnected,
                onRefresh = { bleManager.readRssi() },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Find Device Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isAlarmActive) AccentCoral.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isAlarmActive) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                                contentDescription = null,
                                tint = if (isAlarmActive) AccentCoral else MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Find Device",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isAlarmActive) "Alarm sounding on tracker" else "Trigger buzzer on iTAG",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { bleManager.startAlarm() },
                        enabled = isReady && !isAlarmActive,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandCyan, contentColor = Color.Black)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Start Alarm", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { bleManager.stopAlarm() },
                        enabled = isReady,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCoral)
                    ) {
                        Icon(imageVector = Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Stop Alarm", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Remote Camera Shortcut Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToCamera() },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(BrandCyan, MaterialTheme.colorScheme.primary))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Remote Selfie Camera",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isConnected) "Tracker button armed as shutter" else "Connect tracker to take remote selfies",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Disconnect Alert Preference Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Disconnect Alert",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Alert phone if tracker goes out of range",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = disconnectAlertEnabled,
                    onCheckedChange = { preferencesManager.setDisconnectAlertEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }

    // Scan Modal Bottom Sheet
    if (showScanSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                bleScanner.stopScan()
                showScanSheet = false
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ScanDevicesSheet(
                bleScanner = bleScanner,
                onDeviceSelected = { device ->
                    preferencesManager.setSavedDeviceAddress(device.address)
                    bleManager.connect(device.device)
                    bleScanner.stopScan()
                    showScanSheet = false
                },
                onClose = {
                    bleScanner.stopScan()
                    showScanSheet = false
                }
            )
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Set Friendly Name") },
            text = {
                OutlinedTextField(
                    value = newFriendlyNameText,
                    onValueChange = { newFriendlyNameText = it },
                    label = { Text("Tracker Name (e.g. Keys, Bag)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        preferencesManager.setFriendlyName(newFriendlyNameText.trim().takeIf { it.isNotEmpty() })
                        showRenameDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DeviceHeroCard(
    connectionState: ConnectionState,
    displayName: String,
    macAddress: String?,
    onRenameClicked: () -> Unit,
    onConnectClicked: () -> Unit,
    onDisconnectClicked: () -> Unit
) {
    val isConnected = connectionState.isConnected
    val isReady = connectionState.isReady

    // Infinite breathing animation for active connection
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated Beacon Indicator
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        if (isReady) BrandCyan.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isReady) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(BrandCyan.copy(alpha = 0.2f))
                    )
                }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isReady -> BrandCyan
                                isConnected -> MaterialTheme.colorScheme.primary
                                connectionState is ConnectionState.Connecting -> AccentAmber
                                connectionState is ConnectionState.Error -> AccentCoral
                                else -> MaterialTheme.colorScheme.outline
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isConnected -> Icons.Rounded.BluetoothConnected
                            connectionState is ConnectionState.Connecting -> Icons.Rounded.Bluetooth
                            connectionState is ConnectionState.Error -> Icons.Rounded.BluetoothDisabled
                            else -> Icons.Rounded.Bluetooth
                        },
                        contentDescription = null,
                        tint = if (isReady) Color.Black else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Friendly Name with Rename Pencil
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                if (isConnected) {
                    IconButton(onClick = onRenameClicked, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Rename",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // MAC Address
            Text(
                text = macAddress ?: "No Device Selected",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Connection State Pill
            ConnectionStatusBadge(connectionState = connectionState)

            Spacer(modifier = Modifier.height(20.dp))

            // Connect / Disconnect Buttons
            if (isConnected) {
                OutlinedButton(
                    onClick = onDisconnectClicked,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCoral),
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Disconnect Tracker")
                }
            } else {
                Button(
                    onClick = onConnectClicked,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    Icon(imageVector = Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Scan & Connect Tracker", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusBadge(connectionState: ConnectionState) {
    val (color, text) = when (connectionState) {
        ConnectionState.Ready -> Pair(AccentEmerald, "CONNECTED & READY")
        ConnectionState.Connected -> Pair(AccentEmerald, "CONNECTED")
        ConnectionState.DiscoveringServices -> Pair(AccentAmber, "CONFIGURING SERVICES...")
        ConnectionState.Connecting -> Pair(AccentAmber, "CONNECTING...")
        ConnectionState.Disconnecting -> Pair(AccentCoral, "DISCONNECTING...")
        ConnectionState.Disconnected -> Pair(MaterialTheme.colorScheme.onSurfaceVariant, "DISCONNECTED")
        is ConnectionState.Error -> Pair(AccentCoral, connectionState.message.take(30).uppercase())
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BatteryCard(
    batteryLevel: Int?,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Battery",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (batteryLevel != null) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryAlert,
                    contentDescription = null,
                    tint = when {
                        batteryLevel == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        batteryLevel > 30 -> AccentEmerald
                        batteryLevel > 15 -> AccentAmber
                        else -> AccentCoral
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isConnected) {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Disconnected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (batteryLevel != null) {
                Text(
                    text = "$batteryLevel%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Standard 0x2A19",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentEmerald
                )
            } else {
                Text(
                    text = "N/A",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Not reported yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SignalCard(
    rssi: Int?,
    isUpdating: Boolean,
    isConnected: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Signal",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = onRefresh,
                    enabled = isConnected && !isUpdating,
                    modifier = Modifier.size(24.dp)
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh RSSI",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isConnected) {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Disconnected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (rssi != null) {
                Text(
                    text = "$rssi dBm",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                val quality = when {
                    rssi >= -60 -> "Strong signal"
                    rssi >= -80 -> "Good signal"
                    else -> "Weak signal"
                }
                Text(
                    text = quality,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (rssi >= -70) AccentEmerald else AccentAmber
                )
            } else {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tap refresh",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ScanDevicesSheet(
    bleScanner: BleScanner,
    onDeviceSelected: (DiscoveredDevice) -> Unit,
    onClose: () -> Unit
) {
    val isScanning by bleScanner.isScanning.collectAsState()
    val devices by bleScanner.discoveredDevices.collectAsState()
    val scanError by bleScanner.scanError.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        bleScanner.startScan()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Discover Trackers",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isScanning) "Searching for nearby Bluetooth tags..." else "Scan finished",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            } else {
                IconButton(onClick = { bleScanner.startScan() }) {
                    Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "Rescan")
                }
            }
        }

        if (scanError != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AccentCoral.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentCoral.copy(alpha = 0.3f))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Rounded.ErrorOutline, contentDescription = null, tint = AccentCoral)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = scanError ?: "", style = MaterialTheme.typography.bodyMedium, color = AccentCoral)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isScanning) "Hold tracker button or bring it close to phone..." else "No Bluetooth devices found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(devices, key = { it.address }) { dev ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDeviceSelected(dev) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (dev.isLikelyITag) BrandCyan.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = if (dev.isLikelyITag) androidx.compose.foundation.BorderStroke(1.5.dp, BrandCyan.copy(alpha = 0.5f)) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = dev.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (dev.isLikelyITag) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = BrandCyan,
                                            contentColor = Color.Black
                                        ) {
                                            Text(
                                                text = "iTAG MATCH",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = dev.address,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${dev.rssi} dBm",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = { onDeviceSelected(dev) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Connect", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
