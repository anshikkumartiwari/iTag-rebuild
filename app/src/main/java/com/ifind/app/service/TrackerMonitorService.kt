package com.ifind.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ifind.app.IFindApp
import com.ifind.app.MainActivity

class TrackerMonitorService : Service() {

    private var alarmRingtone: Ringtone? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> {
                val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "Tracker"
                startForeground(NOTIFICATION_ID_FOREGROUND, buildForegroundNotification(deviceName))
            }
            ACTION_STOP_MONITORING -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_TRIGGER_FIND_PHONE -> {
                playFindPhoneAlarm()
                showFindPhoneNotification()
            }
            ACTION_STOP_FIND_PHONE -> {
                stopFindPhoneAlarm()
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(NOTIFICATION_ID_FIND_PHONE)
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

    private fun showFindPhoneNotification() {
        val stopIntent = Intent(this, TrackerMonitorService::class.java).apply {
            action = ACTION_STOP_FIND_PHONE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALARM)
            .setContentTitle("Find My Phone Alert!")
            .setContentText("Triggered by your iTAG tracker button")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Sound", stopPendingIntent)
            .setOngoing(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_FIND_PHONE, notification)
    }

    private fun playFindPhoneAlarm() {
        try {
            stopFindPhoneAlarm()
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            alarmRingtone = RingtoneManager.getRingtone(applicationContext, alertUri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        } catch (_: Exception) {}
    }

    private fun stopFindPhoneAlarm() {
        try {
            alarmRingtone?.stop()
            alarmRingtone = null
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
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

        fun startMonitoring(context: Context, deviceName: String) {
            val intent = Intent(context, TrackerMonitorService::class.java).apply {
                action = ACTION_START_MONITORING
                putExtra(EXTRA_DEVICE_NAME, deviceName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
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
