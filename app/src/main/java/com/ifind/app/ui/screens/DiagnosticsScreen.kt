package com.ifind.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ifind.app.ble.BleConstants
import com.ifind.app.ble.BleManager
import com.ifind.app.ble.model.ButtonEvent
import com.ifind.app.ble.model.ConnectionState
import com.ifind.app.ble.model.RawBleEvent
import com.ifind.app.ui.theme.AccentAmber
import com.ifind.app.ui.theme.AccentCoral
import com.ifind.app.ui.theme.AccentEmerald
import com.ifind.app.ui.theme.BrandCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    bleManager: BleManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val connectionState by bleManager.connectionState.collectAsState()
    val rawEvents by bleManager.rawBleEvents.collectAsState()
    val diagnosticChars by bleManager.diagnosticCharacteristics.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var singlePressCount by remember { mutableIntStateOf(0) }
    var doublePressCount by remember { mutableIntStateOf(0) }
    var lastButtonEventTime by remember { mutableLongStateOf(0L) }
    var lastButtonFeedback by remember { mutableStateOf("No physical button press recorded yet") }

    LaunchedEffect(Unit) {
        bleManager.buttonEvents.collect { event ->
            when (event) {
                is ButtonEvent.SinglePress -> {
                    singlePressCount++
                    lastButtonEventTime = event.timestamp
                    lastButtonFeedback = "Single Press Detected (Byte: 0x%02X)".format(event.rawByte)
                }
                is ButtonEvent.DoublePress -> {
                    doublePressCount++
                    lastButtonEventTime = event.timestamp
                    lastButtonFeedback = "Double Press Detected (Interval: ${event.intervalMs}ms)"
                }
                is ButtonEvent.RawNotification -> {
                    // Handled in rawEvents feed
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text("Tracker Diagnostics", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (connectionState.isConnected) "CCCD FFE1 Subscribed & Listening" else "Disconnected",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (connectionState.isConnected) AccentEmerald else AccentCoral
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        val report = buildString {
                            appendLine("=== iFind Tracker Diagnostics Log ===")
                            appendLine("Connection: $connectionState")
                            appendLine("Single presses: $singlePressCount")
                            appendLine("Double presses: $doublePressCount")
                            appendLine("--- Raw Packets (${rawEvents.size}) ---")
                            rawEvents.forEach { ev ->
                                appendLine("[${ev.formattedTime}] Δ${ev.deltaMs}ms | ${ev.characteristicUuid} -> ${ev.hexString}")
                            }
                        }
                        clipboardManager.setText(AnnotatedString(report))
                        Toast.makeText(context, "Copied diagnostic log to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = "Copy Log")
                }
                IconButton(onClick = { bleManager.clearRawEvents() }) {
                    Icon(imageVector = Icons.Rounded.CleaningServices, contentDescription = "Clear")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("FFE1 Packet Feed (${rawEvents.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Button Verification") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("GATT Inspector") }
            )
        }

        when (selectedTab) {
            0 -> PacketFeedView(rawEvents = rawEvents)
            1 -> ButtonVerificationView(
                singlePressCount = singlePressCount,
                doublePressCount = doublePressCount,
                lastFeedback = lastButtonFeedback,
                onResetCounts = {
                    singlePressCount = 0
                    doublePressCount = 0
                    lastButtonFeedback = "Counters reset"
                }
            )
            2 -> GattInspectorView(
                diagnosticChars = diagnosticChars,
                onReadChar = { item ->
                    bleManager.readDiagnosticCharacteristic(item.serviceUuid, item.characteristicUuid)
                },
                onWriteChar = { item, payload, writeType ->
                    bleManager.testWriteCharacteristic(item.serviceUuid, item.characteristicUuid, payload, writeType)
                }
            )
        }
    }
}

@Composable
fun PacketFeedView(rawEvents: List<RawBleEvent>) {
    if (rawEvents.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(54.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Awaiting FFE1 Notifications",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Press the physical button on your iTAG tracker.\nEach incoming byte payload will appear here with millisecond timestamps and interval deltas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rawEvents, key = { it.id }) { ev ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = ev.formattedTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "+${ev.deltaMs} ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Char: ${BleConstants.shortUuid(ev.characteristicUuid)} (${ev.note})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Hex Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BrandCyan.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandCyan.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "0x" + ev.hexString,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = BrandCyan,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ButtonVerificationView(
    singlePressCount: Int,
    doublePressCount: Int,
    lastFeedback: String,
    onResetCounts: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Live Button Gesture Verifier",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tests the debouncer, burst-filter, and double-click window against physical hardware.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = lastFeedback,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Test Counters Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Single Press Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Single Clicks",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$singlePressCount",
                        style = MaterialTheme.typography.headlineLarge,
                        color = BrandCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Mapped to Shutter",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentEmerald
                    )
                }
            }

            // Double Press Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Double Clicks",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$doublePressCount",
                        style = MaterialTheme.typography.headlineLarge,
                        color = AccentAmber,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Mapped to Find Phone",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentAmber
                    )
                }
            }
        }

        Button(
            onClick = onResetCounts,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
            Icon(imageVector = Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset Test Counters")
        }
    }
}

@Composable
fun GattInspectorView(
    diagnosticChars: List<com.ifind.app.ble.model.DiagnosticCharacteristic>,
    onReadChar: (com.ifind.app.ble.model.DiagnosticCharacteristic) -> Unit,
    onWriteChar: (com.ifind.app.ble.model.DiagnosticCharacteristic, ByteArray, Int) -> Unit
) {
    if (diagnosticChars.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Connect to tracker to inspect GATT characteristics",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(diagnosticChars, key = { "${it.serviceUuid}_${it.characteristicUuid}" }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = item.characteristicUuid.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (item.readable) {
                                    Button(
                                        onClick = { onReadChar(item) },
                                        enabled = !item.isReading,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        if (item.isReading) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text("Read", fontSize = 12.sp)
                                        }
                                    }
                                }
                                if (item.writable) {
                                    OutlinedButton(
                                        onClick = {
                                            onWriteChar(item, byteArrayOf(0x01), android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Write 0x01", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            onWriteChar(item, byteArrayOf(0x02), android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Write 0x02", fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        if (item.lastValueHex != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Value: 0x${item.lastValueHex}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandCyan
                                    )
                                    Text(
                                        text = "Read OK",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AccentEmerald
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
