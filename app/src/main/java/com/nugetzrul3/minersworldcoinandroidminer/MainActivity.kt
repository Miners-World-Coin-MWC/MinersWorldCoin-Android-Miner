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
import androidx.appcompat.widget.Toolbar
import android.provider.Settings
import android.os.PowerManager
import android.content.Context
import android.content.ComponentName
import androidx.appcompat.app.AlertDialog
import com.nugetzrul3.minersworldcoinandroidminer.databinding.ActivityMainBinding
import com.nugetzrul3.minersworldcoinmininglibrary.SugarMiner
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedpref: SharedPref

    private val logs: BlockingQueue<String?> = LinkedBlockingQueue(LOG_LINES)

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

        // Show battery optimization dialog on startup
        Handler(Looper.getMainLooper()).postDelayed({

            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

            val batteryOptimized = !powerManager.isIgnoringBatteryOptimizations(packageName)
            val noticeShown = sharedpref.getBatteryNoticeShown()

            if (batteryOptimized && !noticeShown) {

                AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                    .setTitle("Battery Optimization Detected")
                    .setMessage(
                        "Battery optimization is enabled for this app.\n\n" +
                        "This may reduce mining performance and lower your hashrate.\n\n" +
                        "For best performance you can disable battery optimization for this app."
                    )
                    .setCancelable(true)
                    .setPositiveButton("Open Settings") { _, _ ->
                        requestDisableBatteryOptimization()
                        sharedpref.setBatteryNoticeShown(true)
                    }
                    .setNegativeButton("Later") { dialog, _ ->
                        sharedpref.setBatteryNoticeShown(true)
                        dialog.dismiss()
                    }
                    .show()
            }

        }, 1200)

        // Receive logs from service
        MiningService.logListener = null

        MiningService.logListener = { log ->
            if (!isFinishing && !isDestroyed) {
                runOnUiThread {
                    updateLogs(log)
                }
            }
        }

        // Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        setupUI()
        loadConfig()

        if (sharedpref.loadminingstate()) {
            binding.button.text = "Stop"
        } else {
            binding.button.text = "Start"
        }
    }

    private fun setupUI() {

        setSupportActionBar(binding.toolbar)

        binding.textView6.movementMethod = ScrollingMovementMethod()

        val algorithms = arrayOf(
            "YespowerMwc",
            "YespowerAdvc",
        )

        val adapter = ArrayAdapter(this, R.layout.spinner_item, algorithms)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinner.adapter = adapter

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

        binding.threadSpinner.setSelection(threadList.size - 1)

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !powerManager.isIgnoringBatteryOptimizations(packageName)) {

            // 1️⃣ Try direct request
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Direct optimization request failed", e)
            }

            // 2️⃣ Open battery optimization list
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Battery optimization list failed", e)
            }

            // 3️⃣ Open battery saver settings
            try {
                val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                startActivity(intent)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Battery saver settings failed", e)
            }

            // 4️⃣ Manufacturer specific battery managers
            val manufacturer = Build.MANUFACTURER.lowercase()

            try {
                when {

                    manufacturer.contains("xiaomi") -> {
                        val intent = Intent()
                        intent.component = ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                        startActivity(intent)
                        return
                    }

                    manufacturer.contains("oppo") -> {
                        val intent = Intent()
                        intent.component = ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                        )
                        startActivity(intent)
                        return
                    }

                    manufacturer.contains("vivo") -> {
                        val intent = Intent()
                        intent.component = ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                        startActivity(intent)
                        return
                    }

                    manufacturer.contains("huawei") -> {
                        val intent = Intent()
                        intent.component = ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.optimize.process.ProtectActivity"
                        )
                        startActivity(intent)
                        return
                    }

                    manufacturer.contains("samsung") -> {
                        val intent = Intent()
                        intent.component = ComponentName(
                            "com.samsung.android.lool",
                            "com.samsung.android.sm.ui.battery.BatteryActivity"
                        )
                        startActivity(intent)
                        return
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Manufacturer battery manager failed", e)
            }

            // 5️⃣ Final fallback → App settings
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                return
            } catch (e: Exception) {
                Log.e(TAG, "App settings fallback failed", e)
            }

            Toast.makeText(
                this,
                "Unable to open battery settings on this device.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun toggleMining() {

        if (binding.button.text == "Start") {

            val pool = binding.editText.text.toString().trim()
            val wallet = binding.editText2.text.toString().trim()
            val password = binding.editText3.text.toString()

            if (pool.isEmpty()) {
                binding.textView6.text = "Error: No pool URL specified"
                sharedpref.miningtrue(false)
                return
            }

            val algo = getSelectedAlgorithm()

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

            /*val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                updateLogs("Battery optimization detected. Please disable it for stable mining.")
                requestDisableBatteryOptimization()
                return
            }*/

            logs.clear()
            binding.textView6.text = ""

            startMiningService()

            binding.button.text = "Stop"
            sharedpref.setButtonModeState(false)
            sharedpref.miningtrue(true)

        } else {

            stopMiningService()

            binding.button.text = "Start"
            sharedpref.setButtonModeState(true)
            sharedpref.miningtrue(false)
        }
    }

    private fun startMiningService() {

        val intent = Intent(this, MiningService::class.java)

        intent.action = "START_MINING"
        intent.putExtra("pool", binding.editText.text.toString())
        intent.putExtra("wallet", binding.editText2.text.toString())
        intent.putExtra("password", binding.editText3.text.toString())
        intent.putExtra("threads", binding.threadSpinner.selectedItem.toString().toInt())
        intent.putExtra("algo", getSelectedAlgorithm().ordinal)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopMiningService() {

        val intent = Intent(this, MiningService::class.java)
        intent.action = "STOP_MINING"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {

            R.id.mygithub -> openUrl("https://github.com/CryptoLover705")
            R.id.website -> openUrl("https://minersworld.org")
            R.id.MinersWorldCoingithub -> openUrl("https://github.com/Miners-World-Coin-MWC")
            R.id.Donate -> openUrl("https://miners-world-coin-mwc.github.io/explorer/#/address/9R5aTkmbJs7pPL4hEXvTps1EyStYbHgGTF")

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
                binding.spinner.setSelection(0)
            }

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun restartApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        MiningService.logListener = null
        super.onDestroy()
    }

    companion object {
        private const val LOG_LINES = 1000
        private const val TAG = "SugarMiner"
        private var doubleBackPressed = false
    }
}