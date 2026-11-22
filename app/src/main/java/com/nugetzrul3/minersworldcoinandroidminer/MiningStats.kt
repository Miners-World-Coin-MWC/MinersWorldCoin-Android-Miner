package com.nugetzrul3.minersworldcoinandroidminer

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class MiningStats : AppCompatActivity() {

    private lateinit var sharedpref: SharedPref
    private var statsTimer: Timer? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedpref = SharedPref(this)
        setTheme(if (sharedpref.loadNightModestate()) R.style.DarkTheme else R.style.AppTheme)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mining_stats)

        val toolbar: Toolbar = findViewById(R.id.statstoolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<RadioGroup>(R.id.poolGroup).setOnCheckedChangeListener { _, _ ->
            startPolling()
        }

        checkWallet()
    }

    private fun httpGetJson(url: String): JSONObject {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP error: ${response.code}")
            return JSONObject(response.body?.string().orEmpty())
        }
    }

    private fun checkWallet() {
        val wallet = intent.getStringExtra("walletaddress")?.trim().orEmpty()
        val errorText: TextView = findViewById(R.id.yourminer)

        when {
            wallet.isEmpty() -> errorText.text = "No wallet specified"
            !looksLikeWallet(wallet) -> errorText.text =
                "Not a valid wallet! Please check your address"

            else -> {
                // Default to NOMP so stats load without an extra tap.
                findViewById<RadioButton>(R.id.selectNomp).isChecked = true
            }
        }
    }

    private fun looksLikeWallet(wallet: String): Boolean {
        val address = wallet.split(".")[0]
        val base58 = Regex("^[59A][1-9A-HJ-NP-Za-km-z]{24,33}$")
        val bech32 = Regex("^(mwc|advc)1[qpzry9x8gf2tvdw0s3jn54khce6mua7l]{6,87}$")
        return base58.matches(address) || bech32.matches(address)
    }

    private fun startPolling() {
        val wallet = intent.getStringExtra("walletaddress")?.trim().orEmpty()
        if (wallet.isEmpty()) return

        val nomp = findViewById<RadioButton>(R.id.selectNomp).isChecked
        val yiimp = findViewById<RadioButton>(R.id.selectYIIMP).isChecked
        if (!nomp && !yiimp) {
            findViewById<TextView>(R.id.yourminer).text =
                "Please specify which type of pool you are using"
            return
        }

        statsTimer?.cancel()
        statsTimer = Timer("mining-stats", true).also { timer ->
            timer.scheduleAtFixedRate(object : TimerTask() {
                override fun run() = refreshStats(wallet, nomp)
            }, 0, 5000)
        }
    }

    private fun refreshStats(wallet: String, nomp: Boolean) {
        try {
            val url = if (nomp) {
                "https://bmine.net/api/worker_stats?$wallet"
            } else {
                "http://miningmadness.com/api/wallet?address=$wallet"
            }
            val json = httpGetJson(url)

            val miner = if (nomp) json.optString("miner", wallet) else wallet
            val immature = if (nomp) json.optString("immature") else json.optString("unpaid")
            val paid = if (nomp) json.optString("paid") else json.optString("paid24h")
            val hashRate = (json.optString("totalHash").toFloatOrNull() ?: 0f).roundToInt()
            val shares = json.optString("totalShares")

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setText(R.id.yourminer, "Your Address: $miner")
                setText(R.id.yourimmature, "Your Immature Balance: $immature MWC")
                setText(R.id.yourpaid, "Paid out: $paid MWC")
                setText(R.id.yourhashrate, "Your hashrate: $hashRate H/s")
                setText(R.id.yourtotalshares, "Your shares: $shares")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to fetch mining stats", e)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    setText(R.id.yourminer, "Error fetching mining stats")
                }
            }
        }
    }

    private fun setText(id: Int, text: String) {
        findViewById<TextView>(id).text = text
    }

    override fun onDestroy() {
        statsTimer?.cancel()
        statsTimer = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MiningStats"
    }
}
