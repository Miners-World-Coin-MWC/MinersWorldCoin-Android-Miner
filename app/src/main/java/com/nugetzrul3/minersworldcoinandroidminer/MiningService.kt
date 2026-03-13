package com.nugetzrul3.minersworldcoinandroidminer

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.os.PowerManager

class MiningService : Service() {

    companion object {
        const val CHANNEL_ID = "mining_service_channel"
        const val NOTIFICATION_ID = 101
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val action = intent?.action

        if (action == null) {
            // Android restarted service without intent → kill it
            stopSelf()
            return START_NOT_STICKY
        }

        when (action) {

            "START_MINING" -> {

                if (wakeLock?.isHeld != true) {
                    val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                    wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "MWC::MinerWakeLock"
                    )
                    wakeLock?.acquire()
                }

                val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Miners World Coin")
                    .setContentText("Mining in background")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setOngoing(true)
                    .build()

                startForeground(NOTIFICATION_ID, notification)
            }

            "STOP_MINING" -> {

                wakeLock?.let {
                    if (it.isHeld) it.release()
                }

                stopForeground(true)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mining Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onDestroy() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        stopForeground(true)
        super.onDestroy()
    }
}