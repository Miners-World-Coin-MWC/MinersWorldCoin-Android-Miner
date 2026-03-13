package com.nugetzrul3.minersworldcoinandroidminer

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.*
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import android.content.pm.PackageManager
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.appcompat.widget.Toolbar
import android.provider.Settings
import android.os.PowerManager
import android.content.Context
import com.nugetzrul3.minersworldcoinandroidminer.databinding.ActivityMainBinding
import com.nugetzrul3.minersworldcoinmininglibrary.SugarMiner
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedpref: SharedPref

    private var sugarMiner: SugarMiner? = null
    private val logs: BlockingQueue<String?> = LinkedBlockingQueue(LOG_LINES)

    private val shareUpdateHandler = Handler(Looper.getMainLooper())
    
    private class JNIHandler(activity: MainActivity) :
        Handler(Looper.getMainLooper()) {

        private val activityRef = WeakReference(activity)

        override fun handleMessage(msg: Message) {
            activityRef.get()?.let { activity ->
                val log = msg.data.getString("log")
                activity.updateLogs(log)

                log?.let {
                    // Detect accepted / rejected shares
                    if (it.contains("(yay!!!)")) {
                        activity.incrementAccepted()
                    }

                    if (it.contains("(booooo)")) {
                        activity.incrementRejected()
                    }

                    // Extract hashrate from miner output
                    val regex = Regex("""accepted:\s+\d+/\d+\s+\([\d.]+%\),\s+([\d.]+)\s+hash/s""")

                    val match = regex.find(it)
                    if (match != null) {
                        val hash = match.groupValues[1]
                        activity.updateHashrate("$hash H/s")
                    }
                }
            }
        }
    }

    private fun updateLogs(logText: String?) {
        val rotated = Utils.rotateStringQueue(logs, logText)
        binding.textView6.text = rotated
        Log.d(TAG, rotated)
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {

        sharedpref = SharedPref(this)

        if (sharedpref.loadNightModestate() == true) {
            setTheme(R.style.DarkTheme)
        } else {
            setTheme(R.style.AppTheme)
        }

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        val handler = JNIHandler(this)
        sugarMiner = SugarMiner(handler)

        setupUI()
        loadConfig()

        // Ensure miner always starts stopped
        sharedpref.miningtrue(false)
        binding.button.text = "Start"
    }

    private fun setupUI() {

    setSupportActionBar(binding.toolbar)

    binding.textView6.movementMethod = ScrollingMovementMethod()

    // --------- ALGORITHM LIST ----------
    val algorithms = arrayOf(
        /*"yespower",
        "yespowersugar",*/
        "YespowerMwc",
        "YespowerAdvc",
        /*"yespowerlitb",
        "yespoweriots",
        "yespowermbc",
        "yespoweritc",
        "yespoweriso"*/
    )

    val adapter = ArrayAdapter(this, R.layout.spinner_item, algorithms)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinner.adapter = adapter

        // --------- AUTO DETECT THREADS ----------
        val maxThreads = Runtime.getRuntime().availableProcessors()
        binding.threadLabel.text = "Thread Count (Max: $maxThreads)"

        val threadList = (1..maxThreads).map { it.toString() }

        val threadAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            threadList
        )

        threadAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.threadSpinner.adapter = threadAdapter

        // Default to max threads selected
        binding.threadSpinner.setSelection(threadList.size - 1)

        // --------- DARK MODE ----------
        binding.darkmode.isChecked = sharedpref.loadNightModestate() == true
        binding.darkmode.setOnCheckedChangeListener { _, isChecked ->
            sharedpref.setNightModeState(isChecked)
            saveConfig()
            restartApp()
        }

        binding.button.setOnClickListener {
            toggleMining()
        }
    }

    private fun getSelectedAlgorithm(): SugarMiner.Algorithms {
        return when (binding.spinner.selectedItem.toString()) {
            /*"yespower" -> SugarMiner.Algorithms.ALGO_SUGAR_YESPOWER_1_0_1*/
            "YespowerMwc" -> SugarMiner.Algorithms.ALGO_MWC_YESPOWER_1_0_1
            "YespowerAdvc" -> SugarMiner.Algorithms.ALGO_ADVC_YESPOWER_1_0_1
            else -> SugarMiner.Algorithms.ALGO_MWC_YESPOWER_1_0_1
        }
    }

    private fun isValidMWCAddress(address: String): Boolean {

        val wallet = address.split(".")[0]

        val base58Regex = Regex("^[59][1-9A-HJ-NP-Za-km-z]{24,33}$")
        val bech32Regex = Regex("^mwc1[qpzry9x8gf2tvdw0s3jn54khce6mua7l]{6,87}$")

        return base58Regex.matches(wallet) || bech32Regex.matches(wallet)
    }

    private fun isValidADVCAddress(address: String): Boolean {

        val wallet = address.split(".")[0]

        val base58Regex = Regex("^[A5][1-9A-HJ-NP-Za-km-z]{24,33}$")
        val bech32Regex = Regex("^advc1[qpzry9x8gf2tvdw0s3jn54khce6mua7l]{6,87}$")

        return base58Regex.matches(wallet) || bech32Regex.matches(wallet)
    }

    private fun requestDisableBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = packageName

        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {

            Toast.makeText(
                this,
                "Please disable battery optimization for stable mining",
                Toast.LENGTH_LONG
            ).show()

            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun toggleMining() {
        if (binding.button.text == "Start") {

            val pool = binding.editText.text.toString().trim()
            val wallet = binding.editText2.text.toString().trim()
            val password = binding.editText3.text.toString()

            // Validate pool URL
            if (pool.isEmpty()) {
                binding.textView6.text = "Error: No pool URL specified"
                sharedpref.miningtrue(false)
                return
            }

            val algo = getSelectedAlgorithm()

            // Validate wallet depending on algorithm
            val validWallet = when (algo) {
                SugarMiner.Algorithms.ALGO_MWC_YESPOWER_1_0_1 -> isValidMWCAddress(wallet)
                SugarMiner.Algorithms.ALGO_ADVC_YESPOWER_1_0_1 -> isValidADVCAddress(wallet)
                else -> false
            }

            if (!validWallet) {
                binding.textView6.text = "Error: Invalid wallet address for selected algorithm"
                sharedpref.miningtrue(false)
                return
            }

            requestDisableBatteryOptimization()

            val threads = binding.threadSpinner.selectedItem.toString().toInt()

            // Reset mining stats for new session
            acceptedShares = 0
            rejectedShares = 0
            currentHashrate = "0 H/s"

            if (sharedpref.loadButtonModestate() == false) {
                sugarMiner?.stopMining()
            }

            // Create fresh miner instance
            sugarMiner = SugarMiner(JNIHandler(this))

            // Start miner
            sugarMiner?.initMining()
            sugarMiner?.beginMiner(
                pool,
                wallet,
                password,
                threads,
                algo
            )

            // Then start foreground service
            startMiningService()

            binding.button.text = "Stop"

            sharedpref.setButtonModeState(false)
            sharedpref.miningtrue(true)

        } else {

            binding.button.text = "Start"

            // Stop miner safely
            sugarMiner?.stopMining()
            Thread.sleep(300)   // allow native threads to exit
            sugarMiner = null

            // Stop foreground service
            stopMiningService()

            sharedpref.setButtonModeState(true)
            sharedpref.miningtrue(false)
        }
    }

    private fun updateButtonState() {
        binding.button.text =
            if (sharedpref.loadButtonModestate() == true) "Start" else "Stop"
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {

            R.id.mygithub ->
                openUrl("https://github.com/CryptoLover705")

            R.id.website ->
                openUrl("https://minersworld.org")

            R.id.MinersWorldCoingithub ->
                openUrl("https://github.com/Miners-World-Coin-MWC")

            R.id.Donate ->
                openUrl("https://miners-world-coin-mwc.github.io/explorer/#/address/9R5aTkmbJs7pPL4hEXvTps1EyStYbHgGTF")

            R.id.settings ->
                startActivity(Intent(this, SettingsPage::class.java))

            R.id.stats -> {
                val intent = Intent(this, MiningStats::class.java)
                intent.putExtra("walletaddress", binding.editText2.text.toString())
                startActivity(intent)
            }
        }

        return true
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override fun onBackPressed() {
        if (doubleBackPressed) {
            super.onBackPressed()
        } else {
            doubleBackPressed = true
            Toast.makeText(this, "Click back again to Exit", Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed(
                { doubleBackPressed = false }, 1500
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        saveConfig()
    }

    private fun saveConfig() {
        val json = JSONObject().apply {
            put("URL", binding.editText.text.toString())
            put("User", binding.editText2.text.toString())
            put("Passwd", binding.editText3.text.toString())
            put("CPU", binding.threadSpinner.selectedItemPosition)
            put("Algorithm", binding.spinner.selectedItemPosition)
        }

        val file = File(filesDir, "config.json")
        file.writeText(json.toString())
    }

    private fun loadConfig() {
        val file = File(filesDir, "config.json")
        if (!file.exists()) return

        try {
            val json = FileInputStream(file).bufferedReader().use { it.readText() }
            val obj = JSONObject(json)

            binding.editText.setText(obj.getString("URL"))
            binding.editText2.setText(obj.getString("User"))
            binding.editText3.setText(obj.getString("Passwd"))
            binding.threadSpinner.setSelection(obj.getInt("CPU"))
            val algoIndex = obj.getInt("Algorithm")
            if (algoIndex < binding.spinner.adapter.count) {
                binding.spinner.setSelection(algoIndex)
            } else {
                binding.spinner.setSelection(0) // fallback safely
            }

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private var acceptedShares = 0
    private var rejectedShares = 0
    private var currentHashrate = "0 H/s"

    fun incrementAccepted() {
        acceptedShares++
        updateNotification()
    }

    fun incrementRejected() {
        rejectedShares++
        updateNotification()
    }

    fun updateHashrate(hash: String) {
        currentHashrate = hash
        updateNotification()
    }

    private fun updateNotification() {
        val notificationText = "Hashrate: $currentHashrate | Shares: $acceptedShares/$rejectedShares"

        val builder = NotificationCompat.Builder(this, MiningService.CHANNEL_ID)
            .setContentTitle("⛏ MinersWorldCoin Mining")
            .setContentText(notificationText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(MiningService.NOTIFICATION_ID, builder.build())
    }
    
    private fun updateHashrateUI() {
        binding.textView6.append("\nHashrate: $currentHashrate | Shares: $acceptedShares/$rejectedShares")
    }

    private fun startMiningService() {

        val serviceIntent = Intent(this, MiningService::class.java)
        serviceIntent.action = "START_MINING"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopMiningService() {

        val intent = Intent(this, MiningService::class.java)
        intent.action = "STOP_MINING"
        startService(intent)
    }

    private fun restartApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        private const val LOG_LINES = 1000
        private const val TAG = "SugarMiner"
        private var doubleBackPressed = false
    }
}
