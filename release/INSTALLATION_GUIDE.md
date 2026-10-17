# iFind Android Installation & Setup Guide
Version: 1.1.0 (Build 2)

Thank you for downloading **iFind** — the modern, open-source companion app for iTAG Bluetooth trackers and remote selfie shutters.

---

## 🚀 Quick Installation Steps

### Step 1: Install the APK
1. Extract this zip file or transfer `iFind-v1.1.0.apk` directly to your Android device.
2. Tap the `iFind-v1.1.0.apk` file in your phone's File Manager or Downloads.
3. If prompted with *"For your security, your phone is not allowed to install unknown apps from this source"*:
   - Tap **Settings** in the pop-up dialog.
   - Toggle **Allow from this source** ON.
   - Press the Back button and tap **Install**.
4. Once installation is complete, tap **Open**.

---

## 📱 Permissions Setup (Why they are needed)

When you first launch iFind, the app will request the following permissions:

| Permission | Purpose |
|------------|---------|
| **Nearby Devices / Bluetooth** | Required to discover, connect, and receive button clicks from your iTAG tracker. |
| **Location (Precise)** | Required by Android BLE subsystem to scan for Bluetooth beacons, and used to log your tracker's last known location. |
| **Notifications** | Required for foreground monitoring, disconnect alarm alerts, and "Find My Phone" notifications. |
| **Camera** (Optional) | Required only if you use the built-in CameraX selfie viewfinder. |
| **Accessibility Service** (Optional) | Required only for the **Floating Auto-Clicker & Reel Scroller** overlay to click or scroll inside third-party apps like Instagram, TikTok, Snapchat, or your stock camera. |

---

## 🔘 Pairing Your iTAG Tracker

1. Ensure Bluetooth and GPS/Location are turned ON on your phone.
2. Turn ON your physical iTAG tracker by holding its physical button down for 3 seconds until you hear two beeps.
3. In **iFind**, on the **Device** tab:
   - Tap **Scan & Connect Tracker**.
   - Your tracker will appear with an **iTAG MATCH** green badge.
   - Tap your tracker to connect.
4. Once connected, you will see real-time Battery %, RSSI signal strength, and connection status.

---

## 🎯 Key Features & How to Use

### 1. Find My Device (Buzzer)
- Tap the **Start Alarm** button on the Device screen to trigger the tracker's buzzer.
- Tap **Stop Alarm** to silence it.

### 2. Find My Phone (Double Click)
- When your tracker is connected, **double-click the physical button** on your iTAG.
- Your phone will ring at maximum volume with a repeating alarm and vibration, even if on silent.
- Single-click the tracker button again or tap **Stop Sound** in the notification to silence.

### 3. In-App Remote Selfie Shutter
- Tap **Remote Selfie Camera** on the Device screen.
- Set a countdown timer (Instant, 3s, 5s) or flash mode.
- Press the iTAG tracker button to capture high-resolution photos saved directly to your Gallery.

### 4. Floating Clicker & Reel Scroller (Overlay)
- Toggle the **Floating Auto-Clicker** switch on the Device screen.
- Enable the **iFind Accessibility Service** when prompted in Android Settings.
- A circular glowing reticle overlay will appear on your screen:
  - Drag it over the shutter button in Instagram, Snapchat, TikTok, or your default camera.
  - Choose mode: **Tap** (clicks the spot under the reticle) or **Scroll** (swipes up to the next Reel/Short/Video).
  - Press your physical iTAG tracker button to click or scroll effortlessly from afar!

### 5. Hardware Diagnostics
- Tap the **Diagnostics** icon in the top app bar.
- Inspect live incoming BLE `FFE1` packets, delta millisecond timing, and verify single-click vs double-click responsiveness.

---

## 🛠️ Troubleshooting

- **Tracker won't connect?**
  - Replace the CR2032 coin cell battery in the iTAG.
  - Restart phone Bluetooth.
  - Make sure the tracker isn't already paired to another phone nearby.
- **Floater Clicker not clicking?**
  - Verify that **iFind Accessibility Service** is enabled in `Android Settings > Accessibility > Downloaded Apps / Installed Services > iFind`.
- **Background disconnect alerts delayed?**
  - Exclude iFind from aggressive battery optimization in `Settings > Apps > iFind > Battery > Unrestricted`.

---
*Happy tracking and remote snapping with iFind!*
