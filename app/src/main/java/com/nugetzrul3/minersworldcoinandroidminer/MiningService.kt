package com.nugetzrul3.minersworldcoinandroidminer

import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import com.nugetzrul3.minersworldcoinmininglibrary.SugarMiner
import java.lang.ref.WeakReference

class MiningService : Service() {

    companion object {
        const val CHANNEL_ID = "mining_service_channel"
        const val NOTIFICATION_ID = 101

        var acceptedShares = 0
        var rejectedShares = 0
        var currentHashrate = "0 H/s"

        var logListener: ((String) -> Unit)? = null
        var logBuffer: MutableList<String> = mutableListOf()
    }

    private var sugarMiner: SugarMiner? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var isMining = false

    @Volatile
    private var isStopping = false
    private var minerStartTime = 0L
    private val notificationDelayMs = 60000L
    private val notificationIntervalMs = 5000L // update every 5 seconds

    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    private var updateRunnable: Runnable? = null

    private class JNIHandler(service: MiningService) :
        Handler(Looper.getMainLooper()) {

        private val serviceRef = WeakReference(service)

        override fun handleMessage(msg: Message) {

            val service = serviceRef.get() ?: return
            val log = msg.data.getString("log") ?: return

            logBuffer.add(log)

            if (logBuffer.size > 500){
                logBuffer.removeAt(0)
            }
            
            logListener?.invoke(log)

            if (log.contains("(yay!!!)")) {
                acceptedShares++
                service.updateNotification()
            }

            if (log.contains("(booooo)")) {
                rejectedShares++
                service.updateNotification()
            }

            val regex = Regex("""accepted:\s+\d+/\d+\s+\([\d.]+%\),\s+([\d.]+)\s+hash/s""")
            val match = regex.find(log)

            if (match != null) {
                currentHashrate = match.groupValues[1] + " H/s"
                service.updateNotification()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {

            "START_MINING" -> {

                if (isMining || isStopping || sugarMiner != null) {
                    return START_NOT_STICKY
                }

                val pool = intent.getStringExtra("pool") ?: return START_NOT_STICKY
                val wallet = intent.getStringExtra("wallet") ?: return START_NOT_STICKY
                val password = intent.getStringExtra("password") ?: ""
                val threads = intent.getIntExtra("threads", 1)
                val algoOrdinal = intent.getIntExtra("algo", 0)

                val algo = SugarMiner.Algorithms.values()[algoOrdinal]

                startMiner(pool, wallet, password, threads, algo)
            }

            "STOP_MINING" -> {

                if (!isMining || isStopping) return START_NOT_STICKY

                stopMiner()
            }
        }

        return START_NOT_STICKY
    }

    private fun startMiner(
        pool: String,
        wallet: String,
        password: String,
        threads: Int,
        algo: SugarMiner.Algorithms
    ) {

        if (isMining || isStopping || sugarMiner != null) return

        isMining = true
        isStopping = false

        minerStartTime = System.currentTimeMillis()

        if (wakeLock?.isHeld != true) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MWC::MinerWakeLock"
            )
            wakeLock?.acquire()
        }

        // Initial notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⛏ MinersWorldCoin Mining")
            .setContentText("Starting miner... 60s")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        acceptedShares = 0
        rejectedShares = 0
        currentHashrate = "0 H/s"

        sugarMiner = SugarMiner(JNIHandler(this))

        sugarMiner?.initMining()
        sugarMiner?.beginMiner(
            pool,
            wallet,
            password,
            threads,
            algo
        )

        startCountdown()
        startUpdateLoop()
    }

    private fun stopMiner() {

        if (!isMining || isStopping) return

        isStopping = true
        isMining = false

        countdownRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable?.let { handler.removeCallbacks(it) }

        try {
            sugarMiner?.stopMining()
        } catch (_: Exception) {}

        var attempts = 0

        while (sugarMiner?.isMining() == true && attempts < 50) {
            SystemClock.sleep(100)
            attempts++
        }

        // allow native threads to fully exit
        SystemClock.sleep(300)

        sugarMiner = null

        wakeLock?.let {
            if (it.isHeld) it.release()
        }

        stopForeground(true)
        stopSelf()

        isStopping = false
    }

    private fun startCountdown() {

        countdownRunnable = object : Runnable {

            override fun run() {

                if (!isMining) return

                val elapsed = System.currentTimeMillis() - minerStartTime
                val remaining = ((notificationDelayMs - elapsed) / 1000).toInt()

                if (remaining > 0) {

                    val text = "Starting miner... ${remaining}s"

                    val notification = NotificationCompat.Builder(this@MiningService, CHANNEL_ID)
                        .setContentTitle("⛏ MinersWorldCoin Mining")
                        .setContentText(text)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setOngoing(true)
                        .build()

                    val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, notification)

                    handler.postDelayed(this, 1000)

                } else {

                    updateNotification()
                }
            }
        }

        handler.post(countdownRunnable!!)
    }

    private fun startUpdateLoop() {
        updateRunnable = object : Runnable {
            override fun run() {
                if (!isMining) return
                updateNotification()
                handler.postDelayed(this, notificationIntervalMs)
            }
        }
        handler.postDelayed(updateRunnable!!, notificationIntervalMs)
    }

    private fun updateNotification() {

        if (!isMining) return

        val text = "Hashrate: $currentHashrate | Shares: $acceptedShares/$rejectedShares"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⛏ MinersWorldCoin Mining")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
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

    override fun onDestroy() {

        try {
            sugarMiner?.stopMining()
        } catch (_: Exception) {}

        sugarMiner = null

        isMining = false

        countdownRunnable?.let {
            handler.removeCallbacks(it)
        }

        updateRunnable?.let {
            handler.removeCallbacks(it)
        }

        wakeLock?.let {
            if (it.isHeld) it.release()
        }

        stopForeground(true)
        super.onDestroy()
    }
}