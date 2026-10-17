package com.ifind.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ifind.app.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "TrackerMonitorService"

class TrackerMonitorService : Service() {

    private var alarmPlayer: MediaPlayer? = null
    private var alarmRingtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var originalVolume: Int? = null

    private var isMonitoringActive = false
    private var currentDeviceName: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> {
                val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "Tracker"
                isMonitoringActive = true
                currentDeviceName = deviceName
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID_FOREGROUND, buildForegroundNotification(deviceName), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                } else {
                    startForeground(NOTIFICATION_ID_FOREGROUND, buildForegroundNotification(deviceName))
                }
            }
            ACTION_STOP_MONITORING -> {
                isMonitoringActive = false
                currentDeviceName = null
                if (!_isFindPhoneRinging.value) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    stopSelf()
                }
            }
            ACTION_TRIGGER_FIND_PHONE -> {
                _isFindPhoneRinging.value = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID_FIND_PHONE, buildFindPhoneNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                } else {
                    startForeground(NOTIFICATION_ID_FIND_PHONE, buildFindPhoneNotification())
                }
                playFindPhoneAlarm()
            }
            ACTION_STOP_FIND_PHONE -> {
                _isFindPhoneRinging.value = false
                stopFindPhoneAlarm()
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(NOTIFICATION_ID_FIND_PHONE)
                if (!isMonitoringActive) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    stopSelf()
                } else {
                    currentDeviceName?.let { name ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            startForeground(NOTIFICATION_ID_FOREGROUND, buildForegroundNotification(name), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                        } else {
                            startForeground(NOTIFICATION_ID_FOREGROUND, buildForegroundNotification(name))
                        }
                    }
                }
            }
            ACTION_NOTIFY_DISCONNECT -> {
                val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "Tracker"
                showDisconnectNotification(deviceName)
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val monitorChannel = NotificationChannel(
                CHANNEL_ID_MONITOR,
                "Tracker Background Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps tracker connection active in background"
            }

            val alarmChannel = NotificationChannel(
                CHANNEL_ID_ALARM,
                "Tracker Alarms & Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Find My Phone and Disconnect alerts"
                enableVibration(true)
            }

            nm.createNotificationChannel(monitorChannel)
            nm.createNotificationChannel(alarmChannel)
        }
    }

    private fun buildForegroundNotification(deviceName: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_MONITOR)
            .setContentTitle("iFind Active")
            .setContentText("Monitoring $deviceName")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun showDisconnectNotification(deviceName: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALARM)
            .setContentTitle("Tracker Disconnected!")
            .setContentText("$deviceName was disconnected. Check your surroundings.")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_DISCONNECT, notification)
    }

    private fun buildFindPhoneNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, TrackerMonitorService::class.java).apply {
            action = ACTION_STOP_FIND_PHONE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_ALARM)
            .setContentTitle("Find My Phone Alert!")
            .setContentText("Triggered by your iTAG tracker button")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Sound", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun playFindPhoneAlarm() {
        try {
            stopFindPhoneAlarm()

            // 1. Maximize alarm volume so phone can be heard easily
            try {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (audioManager != null) {
                    originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not adjust alarm volume: ${e.message}")
            }

            // 2. Play alarm sound with continuous looping
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            var playerStarted = false
            try {
                alarmPlayer = MediaPlayer().apply {
                    setDataSource(applicationContext, alertUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
                playerStarted = true
            } catch (e: Exception) {
                Log.w(TAG, "MediaPlayer failed for $alertUri, falling back to Ringtone: ${e.message}")
            }

            if (!playerStarted) {
                try {
                    alarmRingtone = RingtoneManager.getRingtone(applicationContext, alertUri)?.apply {
                        audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            isLooping = true
                        }
                        play()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ringtone fallback also failed: ${e.message}")
                }
            }

            // 3. Repeating vibration pattern
            try {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                val pattern = longArrayOf(0, 600, 300, 600, 300)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Vibrator failed: ${e.message}")
            }

        } catch (t: Throwable) {
            Log.e(TAG, "Error playing Find Phone alarm: ${t.message}", t)
        }
    }

    private fun stopFindPhoneAlarm() {
        try {
            alarmPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            alarmPlayer = null
        } catch (_: Exception) {}

        try {
            alarmRingtone?.stop()
            alarmRingtone = null
        } catch (_: Exception) {}

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (_: Exception) {}

        try {
            originalVolume?.let { vol ->
                val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.setStreamVolume(AudioManager.STREAM_ALARM, vol, 0)
            }
            originalVolume = null
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        _isFindPhoneRinging.value = false
        stopFindPhoneAlarm()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID_MONITOR = "ifind_monitor_channel"
        const val CHANNEL_ID_ALARM = "ifind_alarm_channel"

        const val NOTIFICATION_ID_FOREGROUND = 1001
        const val NOTIFICATION_ID_DISCONNECT = 1002
        const val NOTIFICATION_ID_FIND_PHONE = 1003

        const val ACTION_START_MONITORING = "com.ifind.app.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.ifind.app.STOP_MONITORING"
        const val ACTION_TRIGGER_FIND_PHONE = "com.ifind.app.TRIGGER_FIND_PHONE"
        const val ACTION_STOP_FIND_PHONE = "com.ifind.app.STOP_FIND_PHONE"
        const val ACTION_NOTIFY_DISCONNECT = "com.ifind.app.NOTIFY_DISCONNECT"

        const val EXTRA_DEVICE_NAME = "extra_device_name"

        private val _isFindPhoneRinging = MutableStateFlow(false)
        val isFindPhoneRinging: StateFlow<Boolean> = _isFindPhoneRinging.asStateFlow()

        fun startMonitoring(context: Context, deviceName: String) {
            val intent = Intent(context, TrackerMonitorService::class.java).apply {
                action = ACTION_START_MONITORING
                putExtra(EXTRA_DEVICE_NAME, deviceName)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed startForegroundService for monitoring", t)
                try { context.startService(intent) } catch (_: Throwable) {}
            }
        }

        fun stopMonitoring(context: Context) {
            val intent = Intent(context, TrackerMonitorService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.startService(intent)
        }

        fun triggerFindPhone(context: Context) {
            val intent = Intent(context, TrackerMonitorService::class.java).apply {
                action = ACTION_TRIGGER_FIND_PHONE
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed startForegroundService for find phone", t)
                try { context.startService(intent) } catch (_: Throwable) {}
            }
        }

        fun stopFindPhone(context: Context) {
            val intent = Intent(context, TrackerMonitorService::class.java).apply {
                action = ACTION_STOP_FIND_PHONE
            }
            context.startService(intent)
        }

        fun notifyDisconnect(context: Context, deviceName: String) {
            val intent = Intent(context, TrackerMonitorService::class.java).apply {
                action = ACTION_NOTIFY_DISCONNECT
                putExtra(EXTRA_DEVICE_NAME, deviceName)
            }
            context.startService(intent)
        }
    }
}
