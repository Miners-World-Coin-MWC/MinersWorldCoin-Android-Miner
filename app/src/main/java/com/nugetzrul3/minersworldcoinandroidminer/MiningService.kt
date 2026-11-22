package com.nugetzrul3.minersworldcoinandroidminer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nugetzrul3.minersworldcoinmininglibrary.SugarMiner
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

class MiningService : Service() {

    companion object {
        const val CHANNEL_ID = "mining_service_channel"
        const val NOTIFICATION_ID = 101
        const val ACTION_START = "START_MINING"
        const val ACTION_STOP = "STOP_MINING"
        const val ACTION_MINING_STATE =
            "com.nugetzrul3.minersworldcoinandroidminer.MINING_STATE"
        const val ACTION_REFRESH_NOTIFICATION =
            "com.nugetzrul3.minersworldcoinandroidminer.REFRESH_NOTIFICATION"
        const val ACTION_DONATION_SETTINGS_CHANGED =
            "com.nugetzrul3.minersworldcoinandroidminer.DONATION_SETTINGS_CHANGED"

        /** Single source of truth for the UI: true only while a miner is running. */
        @Volatile
        var isActive = false

        var acceptedShares = 0
        var rejectedShares = 0
        var currentHashrate = "0 H/s"

        /** Status values consumed by the main screen. */
        @Volatile
        var sessionStartTime = 0L
        @Volatile
        var nextDonationAt = 0L
        @Volatile
        var donationEndAt = 0L
        @Volatile
        var donationActive = false
        @Volatile
        var connectionStatus = "Stopped"

        var logListener: ((String) -> Unit)? = null
        val logBuffer: MutableList<String> = mutableListOf()

        private const val TAG = "MiningService"
        private const val POWER_LOCK_TIMEOUT = 12 * 60 * 60 * 1000L
        private const val NOTIFICATION_DELAY_MS = 60_000L
        private const val NOTIFICATION_INTERVAL_MS = 5_000L
        private const val GUARD_INTERVAL_MS = 30_000L
        private const val MAX_LOG_LINES = 500
        private const val LOW_BATTERY_PERCENT = 15
    }

    private data class MinerConfig(
        val pool: String,
        val wallet: String,
        val password: String,
        val threads: Int,
        val algo: SugarMiner.Algorithms
    )

    @Volatile
    private var sugarMiner: SugarMiner? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var isStopping = false
    private var stopAfterSwap = false
    private var minerStartTime = 0L
    private var minerConfig: MinerConfig? = null
    private var hashDetected = false

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "mining-stop") }
    private var countdownRunnable: Runnable? = null
    private var updateRunnable: Runnable? = null
    private var donationRunnable: Runnable? = null
    private var donationEndRunnable: Runnable? = null
    private var guardRunnable: Runnable? = null
    private var receiverRegistered = false

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_REFRESH_NOTIFICATION -> updateNotification(force = true)
                ACTION_DONATION_SETTINGS_CHANGED -> onDonationSettingsChanged()
            }
        }
    }

    private class JNIHandler(service: MiningService) : Handler(Looper.getMainLooper()) {

        private val serviceRef = WeakReference(service)

        override fun handleMessage(msg: Message) {
            val service = serviceRef.get() ?: return
            val log = msg.data.getString("log") ?: return

            synchronized(logBuffer) {
                logBuffer.add(log)
                if (logBuffer.size > MAX_LOG_LINES) {
                    logBuffer.removeAt(0)
                }
            }
            logListener?.invoke(log)

            // These are independent checks: the same "accepted:" line carries both
            // the share result and the hashrate, so all three must run.
            if (MiningLogParser.isAccepted(log)) {
                acceptedShares++
                connectionStatus = "Connected"
                service.updateNotification()
            }

            if (MiningLogParser.isRejected(log)) {
                rejectedShares++
                connectionStatus = "Connected"
                service.updateNotification()
            }

            // Threads are running immediately; don't wait for a pool share before
            // reporting that mining has started.
            if (MiningLogParser.isMinerStarted(log)) {
                service.onMiningStarted()
            }

            val hashrate = MiningLogParser.parseHashrate(log)
                ?: MiningLogParser.parseTotalHashrate(log)
            if (hashrate != null) {
                service.onHashrateDetected(hashrate)
            }

            if (MiningLogParser.isConnected(log)) {
                connectionStatus = "Connected"
            }
            if (MiningLogParser.isConnectionFailure(log)) {
                connectionStatus = "Reconnecting…"
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val filter = IntentFilter().apply {
            addAction(ACTION_REFRESH_NOTIFICATION)
            addAction(ACTION_DONATION_SETTINGS_CHANGED)
        }
        ContextCompat.registerReceiver(
            this, settingsReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (isActive || isStopping || sugarMiner != null) return START_NOT_STICKY

                val pool = intent.getStringExtra("pool")?.trim()
                val wallet = intent.getStringExtra("wallet")?.trim()
                if (pool.isNullOrEmpty() || wallet.isNullOrEmpty()) return START_NOT_STICKY

                val password = intent.getStringExtra("password") ?: ""
                val threads = intent.getIntExtra("threads", 1)
                    .coerceIn(1, Runtime.getRuntime().availableProcessors())
                val algoOrdinal = intent.getIntExtra("algo", -1)
                val algo = SugarMiner.Algorithms.values().getOrNull(algoOrdinal)
                    ?: return START_NOT_STICKY

                startMiner(MinerConfig(pool, wallet, password, threads, algo))
            }

            ACTION_STOP -> stopMinerAsync()
        }

        return START_NOT_STICKY
    }

    private fun startMiner(config: MinerConfig, isSwap: Boolean = false) {
        isActive = true
        isStopping = false

        if (!isSwap) {
            stopAfterSwap = false
            minerConfig = config
            minerStartTime = System.currentTimeMillis()
            sessionStartTime = minerStartTime
            nextDonationAt = 0L
            donationEndAt = 0L
            donationActive = false
            connectionStatus = "Connecting…"
            acceptedShares = 0
            rejectedShares = 0
            currentHashrate = "0 H/s"
            hashDetected = false
            acquireWakeLock()
            startForeground(NOTIFICATION_ID, buildNotification("Starting miner..."))
            startCountdown()
            startUpdateLoop()
            broadcastState(true)
        } else {
            // A swap keeps the foreground notification and wake lock alive.
            updateNotification(force = true)
        }

        try {
            val miner = SugarMiner(JNIHandler(this))
            sugarMiner = miner
            miner.initMining()
            val result = miner.beginMiner(
                config.pool, config.wallet, config.password, config.threads, config.algo
            )
            if (result != 0) {
                Log.e(TAG, "beginMiner failed with code $result")
                showError("Could not start miner (code $result)")
                stopMinerAsync()
                return
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to start native miner", t)
            showError("Unable to load the mining library")
            stopMinerAsync()
            return
        }

        if (!isSwap) {
            startDeviceGuard()
            scheduleDonation(config)
        }
    }

    /** Native stop() blocks until all miner threads exit, so never call it on the main thread. */
    private fun stopNative(miner: SugarMiner, onStopped: () -> Unit) {
        worker.execute {
            try {
                miner.stopMining()
            } catch (t: Throwable) {
                Log.e(TAG, "Error stopping miner", t)
            }

            var attempts = 0
            while (miner.isMining() && attempts < 50) {
                SystemClock.sleep(100)
                attempts++
            }

            handler.post { onStopped() }
        }
    }

    private fun stopMinerAsync(reason: String? = null) {
        cancelDonationTimers()
        cancelDeviceGuard()
        stopAfterSwap = isStopping
        isActive = false
        broadcastState(false, reason)
        countdownRunnable?.let { handler.removeCallbacks(it); countdownRunnable = null }
        updateRunnable?.let { handler.removeCallbacks(it); updateRunnable = null }

        // A swap owns the current stop operation; its callback will finish the service.
        if (isStopping) return

        val miner = sugarMiner
        if (miner == null) {
            finishService()
            return
        }

        isStopping = true
        stopNative(miner) {
            sugarMiner = null
            isStopping = false
            finishService()
        }
    }

    private fun finishService() {
        cancelDonationTimers()
        cancelDeviceGuard()
        isActive = false
        stopAfterSwap = false
        connectionStatus = "Stopped"
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun swapMiner(config: MinerConfig, onComplete: () -> Unit) {
        val miner = sugarMiner ?: return
        if (isStopping) return

        isStopping = true
        updateNotification("Switching miner...", force = true)
        stopNative(miner) {
            sugarMiner = null
            isStopping = false
            if (stopAfterSwap || !isActive) {
                stopAfterSwap = false
                finishService()
                return@stopNative
            }
            startMiner(config, isSwap = true)
            onComplete()
        }
    }

    private fun scheduleDonation(config: MinerConfig) {
        donationRunnable?.let { handler.removeCallbacks(it) }
        donationRunnable = null
        if (!SharedPref(this).donationsEnabled()) {
            nextDonationAt = 0L
            return
        }

        nextDonationAt = System.currentTimeMillis() + DonationConfig.INTERVAL_MS
        donationRunnable = Runnable {
            donationRunnable = null
            if (!isActive || isStopping || minerConfig !== config) return@Runnable
            startDonationPeriod(config)
        }.also { handler.postDelayed(it, DonationConfig.INTERVAL_MS) }
    }

    private fun startDonationPeriod(config: MinerConfig) {
        donationActive = true
        donationEndAt = System.currentTimeMillis() + DonationConfig.DURATION_MS
        nextDonationAt = 0L
        updateNotification(force = true)

        val wallet = DonationConfig.walletFor(config.algo.name)
        swapMiner(config.copy(wallet = wallet)) {
            donationEndRunnable = Runnable {
                donationEndRunnable = null
                if (!isActive || minerConfig !== config) return@Runnable
                endDonationPeriod(config)
            }.also { handler.postDelayed(it, DonationConfig.DURATION_MS) }
        }
    }

    private fun endDonationPeriod(config: MinerConfig) {
        donationActive = false
        donationEndAt = 0L
        swapMiner(config) {
            if (SharedPref(this@MiningService).donationsEnabled()) {
                scheduleDonation(config)
            }
        }
    }

    private fun onDonationSettingsChanged() {
        if (SharedPref(this).donationsEnabled()) {
            minerConfig?.let { config ->
                if (!donationActive && donationRunnable == null) scheduleDonation(config)
            }
        } else {
            // Cancel the next cycle, but let an active 5-minute window finish so
            // the miner always returns to the user's address.
            donationRunnable?.let { handler.removeCallbacks(it); donationRunnable = null }
            nextDonationAt = 0L
        }
    }

    private fun cancelDonationTimers() {
        donationRunnable?.let { handler.removeCallbacks(it); donationRunnable = null }
        donationEndRunnable?.let { handler.removeCallbacks(it); donationEndRunnable = null }
        donationActive = false
        donationEndAt = 0L
        nextDonationAt = 0L
    }

    private fun startDeviceGuard() {
        cancelDeviceGuard()
        guardRunnable = object : Runnable {
            override fun run() {
                checkDeviceGuard()
                if (isActive) handler.postDelayed(this, GUARD_INTERVAL_MS)
            }
        }.also { handler.postDelayed(it, GUARD_INTERVAL_MS) }
    }

    private fun cancelDeviceGuard() {
        guardRunnable?.let { handler.removeCallbacks(it); guardRunnable = null }
    }

    private fun checkDeviceGuard() {
        if (!SharedPref(this).batteryGuardEnabled() || !isActive || isStopping) return

        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 100
        val lowBattery = plugged == 0 && percent <= LOW_BATTERY_PERCENT

        val hot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = getSystemService(PowerManager::class.java)
            powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        } else {
            false
        }

        if (!lowBattery && !hot) return

        val cause = if (hot) "device is too hot" else "battery is at $percent%"
        val reason = "Mining paused: $cause"
        Log.w(TAG, reason)
        showError(reason)
        stopMinerAsync(reason)
    }

    private fun broadcastState(active: Boolean, reason: String? = null) {
        sendBroadcast(
            Intent(ACTION_MINING_STATE)
                .putExtra("active", active)
                .putExtra("reason", reason)
        )
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MWC::MinerWakeLock")
        wakeLock?.acquire(POWER_LOCK_TIMEOUT)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun onMiningStarted() {
        hashDetected = true
        connectionStatus = "Connected"
        // Worker threads are live - stop the "Starting miner..." countdown now.
        countdownRunnable?.let { handler.removeCallbacks(it); countdownRunnable = null }
        updateNotification()
    }

    private fun onHashrateDetected(hashrate: String) {
        currentHashrate = hashrate
        hashDetected = true
        connectionStatus = "Connected"
        // Real hashing has begun - drop the "Starting miner..." countdown immediately.
        countdownRunnable?.let { handler.removeCallbacks(it); countdownRunnable = null }
        updateNotification()
    }

    private fun startCountdown() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = object : Runnable {
            override fun run() {
                if (!isActive) return

                if (hashDetected) {
                    countdownRunnable = null
                    updateNotification()
                    return
                }

                val remaining =
                    (NOTIFICATION_DELAY_MS - (System.currentTimeMillis() - minerStartTime)) / 1000
                if (remaining > 0) {
                    updateNotification("Starting miner... ${remaining}s")
                    handler.postDelayed(this, 1000)
                } else {
                    countdownRunnable = null
                    updateNotification()
                }
            }
        }
        handler.post(countdownRunnable!!)
    }

    private fun startUpdateLoop() {
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = object : Runnable {
            override fun run() {
                if (!isActive) return
                updateNotification()
                handler.postDelayed(this, NOTIFICATION_INTERVAL_MS)
            }
        }
        handler.postDelayed(updateRunnable!!, NOTIFICATION_INTERVAL_MS)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun buildNotification(text: String): Notification {
        val notificationsEnabled = SharedPref(this).notificationsEnabled()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⛏ MinersWorldCoin Mining")
            .setContentText(
                if (notificationsEnabled) text else "Mining in background (notifications off)"
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppIntent())
            .setOngoing(true)

        if (!notificationsEnabled) {
            // Android requires a foreground-service notification; keep only a quiet
            // static one when the user switches progress notifications off.
            builder.setPriority(NotificationCompat.PRIORITY_MIN)
        }
        return builder.build()
    }

    private fun updateNotification(text: String? = null, force: Boolean = false) {
        if (!isActive) return
        if (!SharedPref(this).notificationsEnabled() && !force) return

        val base = text ?: "Hashrate: $currentHashrate | Shares: $acceptedShares/$rejectedShares"
        val message = if (donationActive) "$base (supporting the project)" else base
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun showError(message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mining Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        cancelDonationTimers()
        cancelDeviceGuard()
        countdownRunnable?.let { handler.removeCallbacks(it); countdownRunnable = null }
        updateRunnable?.let { handler.removeCallbacks(it); updateRunnable = null }
        if (receiverRegistered) {
            unregisterReceiver(settingsReceiver)
            receiverRegistered = false
        }

        val miner = sugarMiner
        sugarMiner = null
        if (isActive) {
            isActive = false
            broadcastState(false)
        }
        if (miner != null) {
            worker.execute {
                try {
                    miner.stopMining()
                } catch (t: Throwable) {
                    Log.e(TAG, "Error stopping miner on destroy", t)
                }
            }
        }

        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        worker.shutdown()
        super.onDestroy()
    }
}
