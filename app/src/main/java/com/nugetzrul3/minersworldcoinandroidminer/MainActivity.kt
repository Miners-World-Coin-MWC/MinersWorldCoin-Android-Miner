package com.nugetzrul3.minersworldcoinandroidminer

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import com.nugetzrul3.minersworldcoinandroidminer.databinding.ActivityMainBinding
import com.nugetzrul3.minersworldcoinmininglibrary.SugarMiner
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedpref: SharedPref

    private val logs: BlockingQueue<String?> = LinkedBlockingQueue(LOG_LINES)

    private lateinit var setupStore: SetupStore
    private var setups: MutableList<MinerSetup> = mutableListOf()
    private lateinit var poolPresetAdapter: ArrayAdapter<String>
    private lateinit var setupAdapter: ArrayAdapter<String>
    private var poolPresetUrls: List<String> = emptyList()
    private var settingPoolFromPreset = false
    private var settingPoolFromSetup = false
    private val statusHandler = Handler(Looper.getMainLooper())
    private val statusRunnable = object : Runnable {
        override fun run() {
            updateSessionStatus()
            statusHandler.postDelayed(this, 1000)
        }
    }

    private var receiverRegistered = false
    private val miningStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.getStringExtra("reason")?.let { updateLogs(it) }
            syncButtonState()
        }
    }

    // Held in a field so onDestroy only clears a listener owned by this instance.
    private val serviceLogListener: (String) -> Unit = { log ->
        if (!isFinishing && !isDestroyed) {
            runOnUiThread { updateLogs(log) }
        }
    }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(
                    this,
                    "Notifications are off; mining progress will not be shown in the status bar.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private fun updateLogs(logText: String?) {
        binding.textView6.text = Utils.rotateStringQueue(logs, logText)
        // Keep the newest line visible inside the dedicated log box.
        binding.textView6.post {
            val layout = binding.textView6.layout ?: return@post
            val scrollAmount =
                layout.getLineTop(binding.textView6.lineCount) - binding.textView6.height
            binding.textView6.scrollTo(
                binding.textView6.scrollX,
                if (scrollAmount > 0) scrollAmount else 0
            )
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        sharedpref = SharedPref(this)

        setTheme(
            if (sharedpref.loadNightModestate() == true) R.style.DarkTheme else R.style.AppTheme
        )
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupStore = SetupStore(this)
        setups = setupStore.load()

        setupUI()
        loadConfig()
        syncButtonState()

        showBatteryOptimizationNotice()
        restoreLogs()
        requestNotificationsIfNeeded()

        setupBackNavigation()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            miningStateReceiver,
            IntentFilter(MiningService.ACTION_MINING_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
        syncButtonState()
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(miningStateReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // The service is the source of truth: it may have been killed while the UI was away.
        syncButtonState()
        statusHandler.removeCallbacks(statusRunnable)
        statusHandler.post(statusRunnable)
    }

    override fun onPause() {
        statusHandler.removeCallbacks(statusRunnable)
        super.onPause()
    }

    private fun refreshPoolPresets(selectedUrl: String? = null) {
        val labels = mutableListOf("Custom")
        val urls = mutableListOf("")
        labels += "bmine.net:3033"
        urls += POOL_PRESET_URL

        val custom = sharedpref.customPools().sorted()
        for (pool in custom) {
            if (pool in urls || pool == POOL_PRESET_URL) continue
            urls += pool
            labels += pool.removePrefix("stratum+tcp://")
        }

        poolPresetUrls = urls
        settingPoolFromPreset = true
        poolPresetAdapter.clear()
        poolPresetAdapter.addAll(labels)
        poolPresetAdapter.notifyDataSetChanged()

        val index = selectedUrl?.let { urls.indexOf(it) } ?: -1
        binding.poolPresetSpinner.setSelection(if (index >= 0) index else 0)
        settingPoolFromPreset = false
    }

    private fun refreshSetupSpinner(selectedName: String? = null) {
        val names = mutableListOf("Select a saved setup")
        names += setups.map { it.name }
        setupAdapter.clear()
        setupAdapter.addAll(names)
        setupAdapter.notifyDataSetChanged()

        val index = selectedName?.let { names.indexOf(it) } ?: -1
        binding.setupSpinner.setSelection(if (index >= 0) index else 0)
    }

    private fun promptSavePool() {
        val pool = binding.editText.text.toString().trim()
        if (pool.isEmpty()) {
            Toast.makeText(this, "Enter a pool URL first", Toast.LENGTH_SHORT).show()
            return
        }

        val pools = sharedpref.customPools().toMutableSet()
        if (!pools.add(pool)) {
            Toast.makeText(this, "Pool already saved", Toast.LENGTH_SHORT).show()
            return
        }
        sharedpref.setCustomPools(pools)
        refreshPoolPresets(pool)
        Toast.makeText(this, "Pool saved", Toast.LENGTH_SHORT).show()
    }

    private fun promptSaveSetup() {
        val nameInput = EditText(this).apply {
            hint = "Setup name"
            setSingleLine(true)
            if (binding.setupSpinner.selectedItemPosition > 0) {
                setText(binding.setupSpinner.selectedItem.toString())
            }
        }

        AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle("Save setup")
            .setMessage("Give this configuration a name:")
            .setView(nameInput)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Enter a name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val setup = MinerSetup(
                    name = name,
                    pool = binding.editText.text.toString().trim(),
                    wallet = binding.editText2.text.toString().trim(),
                    password = binding.editText3.text.toString(),
                    threads = binding.threadSpinner.selectedItemPosition,
                    algo = binding.spinner.selectedItemPosition
                )

                val existing = setups.indexOfFirst { it.name.equals(name, ignoreCase = true) }
                if (existing >= 0) setups[existing] = setup else setups += setup

                setupStore.save(setups)
                refreshSetupSpinner(name)
                Toast.makeText(this, "Setup saved: $name", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSelectedSetup() {
        val position = binding.setupSpinner.selectedItemPosition
        if (position <= 0 || position > setups.size) {
            Toast.makeText(this, "Select a saved setup to delete", Toast.LENGTH_SHORT).show()
            return
        }

        val setup = setups[position - 1]
        setups.removeAt(position - 1)
        setupStore.save(setups)
        refreshSetupSpinner()
        Toast.makeText(this, "Deleted: ${setup.name}", Toast.LENGTH_SHORT).show()
    }

    private fun loadSetup(setup: MinerSetup) {
        settingPoolFromSetup = true
        binding.editText.setText(setup.pool)
        binding.editText2.setText(setup.wallet)
        binding.editText3.setText(setup.password)
        if (setup.threads in 0 until binding.threadSpinner.adapter.count) {
            binding.threadSpinner.setSelection(setup.threads)
        }
        if (setup.algo in 0 until binding.spinner.adapter.count) {
            binding.spinner.setSelection(setup.algo)
        }
        refreshPoolPresets(setup.pool)
        settingPoolFromSetup = false
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        binding.textView6.movementMethod = ScrollingMovementMethod()

        poolPresetAdapter = ArrayAdapter(this, R.layout.spinner_item, mutableListOf<String>())
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.poolPresetSpinner.adapter = poolPresetAdapter
        refreshPoolPresets()

        binding.poolPresetSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>, view: View?, position: Int, id: Long
                ) {
                    if (settingPoolFromPreset || settingPoolFromSetup) return
                    val url = poolPresetUrls.getOrNull(position).orEmpty()
                    if (url.isNotEmpty()) {
                        settingPoolFromPreset = true
                        binding.editText.setText(url)
                        settingPoolFromPreset = false
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) = Unit
            }

        binding.editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (settingPoolFromPreset || settingPoolFromSetup) return
                val selected = poolPresetUrls.getOrNull(binding.poolPresetSpinner.selectedItemPosition)
                if (!selected.isNullOrEmpty() && s.toString() != selected) {
                    binding.poolPresetSpinner.setSelection(0)
                }
            }
        })

        setupAdapter = ArrayAdapter(this, R.layout.spinner_item, mutableListOf<String>())
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.setupSpinner.adapter = setupAdapter
        refreshSetupSpinner()

        binding.savePoolButton.setOnClickListener { promptSavePool() }
        binding.saveSetupButton.setOnClickListener { promptSaveSetup() }
        binding.deleteSetupButton.setOnClickListener { deleteSelectedSetup() }

        binding.setupSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>, view: View?, position: Int, id: Long
                ) {
                    if (position > 0 && position <= setups.size) {
                        loadSetup(setups[position - 1])
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) = Unit
            }

        binding.spinner.adapter = ArrayAdapter(
            this,
            R.layout.spinner_item,
            arrayOf("YespowerMwc", "YespowerAdvc")
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val maxThreads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        binding.threadLabel.text = "Thread Count (Max: $maxThreads)"

        val threadList = (1..maxThreads).map { it.toString() }
        val threadAdapter = ArrayAdapter(this, R.layout.spinner_item, threadList)
        threadAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.threadSpinner.adapter = threadAdapter
        binding.threadSpinner.setSelection(threadList.lastIndex)

        binding.darkmode.isChecked = sharedpref.loadNightModestate() == true
        binding.darkmode.setOnCheckedChangeListener { _, isChecked ->
            sharedpref.setNightModeState(isChecked)
            saveConfig()
            recreateForThemeChange()
        }

        binding.clearLogsButton.setOnClickListener {
            logs.clear()
            binding.textView6.text = ""
        }

        binding.copyLogsButton.setOnClickListener {
            val text = binding.textView6.text.toString()
            if (text.isBlank()) {
                Toast.makeText(this, "No logs to copy", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Mining logs", text))
            Toast.makeText(this, "Logs copied", Toast.LENGTH_SHORT).show()
        }

        binding.shareLogsButton.setOnClickListener {
            val text = binding.textView6.text.toString()
            if (text.isBlank()) {
                Toast.makeText(this, "No logs to share", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(shareIntent, "Share mining logs"))
        }

        binding.button.setOnClickListener { toggleMining() }
    }

    private fun syncButtonState() {
        binding.button.text = getString(
            if (MiningService.isActive) R.string.stop else R.string.start
        )
    }

    @SuppressLint("SetTextI18n")
    private fun updateSessionStatus() {
        if (!MiningService.isActive) {
            binding.sessionStatus.text = "Mining: stopped"
            return
        }

        val now = System.currentTimeMillis()
        val uptime = (now - MiningService.sessionStartTime) / 1000
        val extra = when {
            MiningService.donationActive ->
                " · Support window: ${formatDuration((MiningService.donationEndAt - now) / 1000)} left"

            MiningService.nextDonationAt > 0 ->
                " · Next support window in ${formatDuration((MiningService.nextDonationAt - now) / 1000)}"

            else -> ""
        }
        val rate = if (MiningService.currentHashrate != "0 H/s") {
            " · ${MiningService.currentHashrate}"
        } else {
            ""
        }
        binding.sessionStatus.text =
            "${MiningService.connectionStatus}$rate · Uptime ${formatDuration(uptime)}$extra"
    }

    private fun formatDuration(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return when {
            s >= 3600 -> "${s / 3600}h ${(s % 3600) / 60}m"
            s >= 60 -> "${s / 60}m ${s % 60}s"
            else -> "${s}s"
        }
    }

    private fun getSelectedAlgorithm(): SugarMiner.Algorithms =
        when (binding.spinner.selectedItem.toString()) {
            "YespowerAdvc" -> SugarMiner.Algorithms.ALGO_ADVC_YESPOWER_1_0_1
            else -> SugarMiner.Algorithms.ALGO_MWC_YESPOWER_1_0_1
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

    private fun showBatteryOptimizationNotice() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed

            if (BatteryOptimization.isIgnoringOptimizations(this) ||
                sharedpref.getBatteryNoticeShown()
            ) return@postDelayed

            AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                .setTitle("Battery Optimization Detected")
                .setMessage(
                    "Battery optimization is enabled for this app.\n\n" +
                        "This may reduce mining performance and lower your hashrate.\n\n" +
                        "For best performance you can disable battery optimization for this app."
                )
                .setCancelable(true)
                .setPositiveButton("Open Settings") { _, _ ->
                    if (BatteryOptimization.openSettings(this)) {
                        sharedpref.setBatteryNoticeShown(true)
                    }
                }
                .setNegativeButton("Later") { dialog, _ ->
                    sharedpref.setBatteryNoticeShown(true)
                    dialog.dismiss()
                }
                .show()
        }, 1200)
    }

    private fun restoreLogs() {
        MiningService.logListener = serviceLogListener

        synchronized(MiningService.logBuffer) {
            for (log in MiningService.logBuffer) {
                updateLogs(log)
            }
        }
    }

    private fun requestNotificationsIfNeeded() {
        if (!sharedpref.notificationsEnabled()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun toggleMining() {
        if (binding.button.text == getString(R.string.start)) {
            val pool = binding.editText.text.toString().trim()
            val wallet = binding.editText2.text.toString().trim()

            if (pool.isEmpty()) {
                binding.textView6.text = "Error: No pool URL specified"
                syncButtonState()
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
                syncButtonState()
                return
            }

            logs.clear()
            binding.textView6.text = ""

            startMiningService(pool, wallet)
            binding.button.text = getString(R.string.stop)
        } else {
            stopMiningService()
            binding.button.text = getString(R.string.start)
        }
    }

    private fun startMiningService(pool: String, wallet: String) {
        val intent = Intent(this, MiningService::class.java).apply {
            action = MiningService.ACTION_START
            putExtra("pool", pool)
            putExtra("wallet", wallet)
            putExtra("password", binding.editText3.text.toString())
            putExtra("threads", binding.threadSpinner.selectedItem.toString().toInt())
            putExtra("algo", getSelectedAlgorithm().ordinal)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopMiningService() {
        val intent = Intent(this, MiningService::class.java).apply {
            action = MiningService.ACTION_STOP
        }
        // STOP must not go through startForegroundService(): a background service
        // that never calls startForeground() would crash on Android 12+.
        startService(intent)
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
            R.id.mobileWallets -> openUrl(
                "https://github.com/Miners-World-Coin-MWC/MinersWorldCoin-Mobile-Wallets/releases/download/1.0.5/app-release.apk"
            )

            R.id.Donate -> openUrl(
                "https://miners-world-coin-mwc.github.io/explorer/#/address/9R5aTkmbJs7pPL4hEXvTps1EyStYbHgGTF"
            )

            R.id.tutorial -> startActivity(Intent(this, TutorialActivity::class.java))
            R.id.settings -> startActivity(Intent(this, SettingsPage::class.java))

            R.id.stats -> startActivity(
                Intent(this, MiningStats::class.java).apply {
                    putExtra("walletaddress", binding.editText2.text.toString().trim())
                }
            )
        }
        return true
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            // Do not pre-check with resolveActivity(): since Android 11 package
            // visibility hides browsers unless <queries> declares them. A chooser
            // is resolved by the system and works on every device.
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No browser available", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to open $url", e)
            Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBackNavigation() {
        var backPressed = false
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backPressed) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    backPressed = true
                    Toast.makeText(this@MainActivity, "Click back again to Exit", Toast.LENGTH_SHORT)
                        .show()
                    Handler(Looper.getMainLooper()).postDelayed({ backPressed = false }, 1500)
                }
            }
        })
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

        try {
            File(filesDir, "config.json").writeText(json.toString())
        } catch (e: IOException) {
            Log.e(TAG, "Unable to save config", e)
        }
    }

    private fun loadConfig() {
        val file = File(filesDir, "config.json")
        if (!file.exists()) return

        try {
            val obj = JSONObject(file.bufferedReader().use { it.readText() })
            binding.editText.setText(obj.getString("URL"))
            refreshPoolPresets(binding.editText.text.toString())
            binding.editText2.setText(obj.getString("User"))
            binding.editText3.setText(obj.getString("Passwd"))

            val cpu = obj.getInt("CPU")
            if (cpu in 0 until binding.threadSpinner.adapter.count) {
                binding.threadSpinner.setSelection(cpu)
            }

            val algo = obj.getInt("Algorithm")
            binding.spinner.setSelection(
                if (algo in 0 until binding.spinner.adapter.count) algo else 0
            )
        } catch (e: IOException) {
            Log.e(TAG, "Unable to read config", e)
        } catch (e: JSONException) {
            Log.e(TAG, "Invalid config file", e)
        }
    }

    private fun recreateForThemeChange() {
        // recreate() guarantees onDestroy() before the new onCreate(), so the log
        // listener is always re-registered after the theme swap.
        recreate()
    }

    override fun onDestroy() {
        if (MiningService.logListener === serviceLogListener) {
            MiningService.logListener = null
        }
        super.onDestroy()
    }

    companion object {
        private const val LOG_LINES = 1000
        private const val TAG = "SugarMiner"
        private const val POOL_PRESET_URL = "stratum+tcp://bmine.net:3033"
    }
}
