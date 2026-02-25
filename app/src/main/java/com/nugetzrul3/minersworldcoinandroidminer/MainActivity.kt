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
import androidx.appcompat.widget.Toolbar
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

    private class JNIHandler(activity: MainActivity) :
        Handler(Looper.getMainLooper()) {

        private val activityRef = WeakReference(activity)

        override fun handleMessage(msg: Message) {
            activityRef.get()?.let { activity ->
                val log = msg.data.getString("log")
                activity.updateLogs(log)
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
        "yespower",
        /*"yespowersugar",*/
        "yespowermwc",
        "yespoweradvc",
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
            "yespower" -> SugarMiner.Algorithms.YESPOWER
            "yespowermwc" -> SugarMiner.Algorithms.YESPOWERMWC
            "yespoweradvc" -> SugarMiner.Algorithms.YESPOWERADVC
            else -> SugarMiner.Algorithms.YESPOWERMWC
        }
    }

    private fun toggleMining() {
        if (binding.button.text == "Start") {
            if (binding.editText.text.isNullOrEmpty()) {
                binding.textView6.text = "Error, no pool url specified"
                sharedpref.miningtrue(false)
                return
            }

            binding.button.text = "Stop"

            val threads = binding.threadSpinner.selectedItem.toString().toInt()
            val algo = getSelectedAlgorithm() // <- use selected algorithm

            sugarMiner?.initMining()
            sugarMiner?.beginMiner(
                binding.editText.text.toString(),
                binding.editText2.text.toString(),
                binding.editText3.text.toString(),
                threads,
                algo
            )

            sharedpref.setButtonModeState(false)
            sharedpref.miningtrue(true)

        } else if (sugarMiner != null) { // only stop if miner exists
            binding.button.text = "Start"
            sugarMiner?.stopMining()
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
            put("URL", binding.editText.text)
            put("User", binding.editText2.text)
            put("Passwd", binding.editText3.text)
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
