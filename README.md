# iFind — Native Android iTAG Companion & Remote Shutter

iFind is a native Android application built in Kotlin and Jetpack Compose. It replaces the original *iSearching* tracker companion app with modern BLE GATT lifecycle handling, a responsive 3-tab consumer design, and an integrated CameraX remote selfie shutter triggered by the tracker's physical button.

---

## 1. BLE Protocol Analysis (from `iTAG - copy.xml`)

### What the XML Configuration Establishes

1. **Battery Service (`0x180F`)**:
   - Characteristic `0x2A19` (Battery Level) supports `READ` and `NOTIFY` with standard CCCD (`0x2902`) and Report Reference (`0x2908`).
   - Battery readings are reported as a single unsigned byte (0–100%).

2. **Immediate Alert Service (`0x1802`)**:
   - Characteristic `0x2A06` (Alert Level) supports `WRITE` and `WRITE_WITHOUT_RESPONSE`.
   - Used for the **Find Device** feature:
     - `0x02` (High Alert): Triggers the tracker's buzzer.
     - `0x00` (No Alert): Silences the buzzer.

3. **Link Loss Service (`0x1803`)**:
   - Characteristic `0x2A06` supports `READ` and `WRITE` (with response).
   - Configures the tracker's autonomous alarm behavior when Bluetooth connection is unexpectedly severed.

4. **Custom Proprietary Service (`0xFFE0`)**:
   - Characteristic `0xFFE1`: Supports `READ` and `NOTIFY` with CCCD (`0x2902`).
   - Initial value in XML: `0x01`.
   - In nRF Connect macros, physical button presses yielded notification assertions targeting `0xFFE1` with value `0x01`.

### What Remains Unknown & Needs Verification

1. **Notification Timing & Debouncing**:
   - In physical testing, does one press fire a single packet, or a burst of 2–3 packets due to hardware contact bounce or retransmissions?
2. **Payload Differentiation**:
   - Does a release event emit `0x00` or nothing?
   - Does a double-click or long-press emit a different payload (e.g. `0x02`), or simply two `0x01` bursts closely spaced in time?
3. **Custom Registers (`FFE2`–`FFE7`)**:
   - Some Chinese iTAG revisions use `FFE2` for custom commands or `FFE3`/`FFE4` for pairing state. These should only be safely read, never written blindly.

### Verification Strategy in iFind

- Built-in **Diagnostics Screen**:
  - Live millisecond packet feed (`HH:mm:ss.SSS`) displaying raw hex payloads, lengths, and delta intervals ($\Delta\text{ms}$).
  - Live **Button Verification Suite** with real-time test counters:
    - Single Press detection (debounced with burst filter).
    - Double Press detection (250ms–750ms timing window).
  - Safe GATT Characteristic Inspector for reading and introspecting registers.

---

## 2. App Architecture & Structure

```
com.ifind.app
├── IFindApp.kt                     # Application lifecycle & global listeners
├── MainActivity.kt                 # Navigation controller & runtime permissions
├── ble/
│   ├── BleConstants.kt             # Full UUIDs and standard alert byte codes
│   ├── BleScanner.kt               # BluetoothLeScanner with RSSI sorting & filter
│   ├── BleManager.kt               # Thread-safe GATT queue, FFE1 parser & notifications
│   └── model/
│       ├── ConnectionState.kt      # State machine (Connecting, Ready, Error)
│       ├── DiscoveredDevice.kt     # Scanned BLE device model
│       ├── RawBleEvent.kt          # Packet diagnostics with delta timing
│       ├── ButtonEvent.kt          # SinglePress / DoublePress events
│       └── DiagnosticCharacteristic.kt
├── data/
│   └── PreferencesManager.kt       # Theme, friendly name, countdown, disconnect alerts
├── location/
│   └── LocationTracker.kt          # Phone GPS capture on connect/disconnect & Maps intent
├── service/
│   └── TrackerMonitorService.kt    # Foreground service for background monitoring & alarms
└── ui/
    ├── navigation/
    │   ├── Screen.kt               # Navigation routes
    │   └── BottomNavBar.kt         # Sleek navigation bar for Device, Location, Settings
    ├── screens/
    │   ├── DeviceScreen.kt         # Tracker dashboard, battery, RSSI, Find Device, scan sheet
    │   ├── DiagnosticsScreen.kt    # FFE1 packet logger, gesture verifier, GATT inspector
    │   ├── LocationScreen.kt       # Last-known location card & Google Maps launch
    │   ├── SettingsScreen.kt       # Theme (System/Light/Dark), camera countdown, alert toggles
    │   └── CameraScreen.kt         # CameraX preview, remote shutter, flash, gallery save
    └── theme/
        ├── Color.kt                # Luxury deep obsidian & electric cyan palette
        ├── Type.kt                 # Clean typography
        └── Theme.kt                # Light / Dark / System dynamic theming
```

---

## 3. Phase Implementation Status

- [x] **Phase 1 — Project Foundation**:
  - Android Gradle structure with Compose BOM & CameraX.
  - Complete navigation with 3 primary tabs (**Device**, **Location**, **Settings**).
  - Light, Dark, and Follow System theme engine.
  - Runtime permissions for Android 12+ (Nearby Devices) and Android 8–11.
  - BLE scanning, discovery, and manual connection/disconnection.

- [x] **Phase 2 — Tracker Diagnostics & Protocol Verification**:
  - Battery level reading (`0x2A19`) and CCCD subscription (with strict distinction between unavailable reading and 0%).
  - RSSI signal strength reading with manual refresh and quality indicator.
  - `0xFFE1` CCCD subscription with thread-safe GATT command queue.
  - Real-time diagnostic logger showing raw hex bytes, millisecond timestamps, and delta times.
  - Physical button test suite (single click vs double click).
  - Reconnection handling and error recovery.

- [x] **Phase 3 — Tracker Functions**:
  - Find Device: Start Alarm (`0x02` on `0x1802/0x2A06`) and Stop Alarm (`0x00`).
  - Disconnect Alert toggle and background broadcast.
  - Find My Phone: Double-press detection triggering audible phone alarm.

- [x] **Phase 4 — Remote Camera**:
  - CameraX full-screen viewfinder with front/back camera switching.
  - Remote shutter triggered by physical button events (`ButtonEvent.SinglePress`).
  - Configurable countdown timer (Instant, 3s, 5s).
  - Flash control (Auto, On, Off).
  - MediaStore gallery photo storage.

- [x] **Phase 5 — Location, Settings and Background Monitoring**:
  - Phone location logging on tracker disconnect/connect.
  - "Open in Maps" navigation fallback.
  - Local persistence via SharedPreferences.
  - Foreground monitoring service (`TrackerMonitorService`).

---

## 4. How to Install & Test on Your Android Phone

### Option A: Using ADB (Command Line)
1. Enable **Developer Options** and **USB Debugging** on your phone.
2. Connect your phone via USB.
3. Install the pre-built debug APK directly:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

### Option B: Using Android Studio
1. Open this folder (`iTag-rebuild`) in **Android Studio**.
2. Let Gradle sync.
3. Click the green **Run 'app'** button to deploy directly to your connected device.

---

## 5. Verification Steps for Physical Tracker
1. Launch **iFind** and grant Bluetooth, Location, and Camera permissions.
2. Tap **Scan & Connect Tracker** on the Device tab.
3. Select your iTAG tracker from the list (highlighted with an *iTAG MATCH* badge).
4. Tap the **Brain/Psychology icon** in the top-right to enter **Diagnostics**:
   - Switch to **FFE1 Packet Feed**.
   - Press the tracker button once: inspect the hex bytes and delta ms.
   - Switch to **Button Verification**: press once to verify Single Click count increments, press twice quickly to test Double Click detection.
5. On the Device screen, test **Start Alarm** and **Stop Alarm** to verify the buzzer.
6. Open **Remote Selfie Camera** and test taking photos using the physical tracker button!
