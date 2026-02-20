package com.nugetzrul3.minersworldcoinandroidminer

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.math.roundToInt

class MiningStats : AppCompatActivity() {

    protected lateinit var sharedpref: SharedPref
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedpref = SharedPref(this)

        if (sharedpref.loadNightModestate() == true) {
            setTheme(R.style.DarkTheme)
        } else {
            setTheme(R.style.AppTheme)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mining_stats)

        val toolbar: Toolbar = findViewById(R.id.statstoolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        checkWalletLength()
    }

    private fun httpGetJson(url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP error: ${response.code}")
            }
            val body = response.body?.string() ?: "{}"
            return JSONObject(body)
        }
    }

    private fun checkWalletLength() {
        val wallet = intent.getStringExtra("walletaddress") ?: ""

        val errorText: TextView = findViewById(R.id.yourminer)

        when {
            wallet.isEmpty() -> {
                errorText.text = "No wallet specified"
            }
            wallet.length != 45 -> {
                errorText.text = "Not a valid wallet! Please check your address"
            }
            else -> {
                nomporyiimp()
            }
        }
    }

    private fun nomporyiimp() {
        val radioButtonNomp: RadioButton = findViewById(R.id.selectNomp)
        val radioButtonYiimp: RadioButton = findViewById(R.id.selectYIIMP)

        when {
            radioButtonNomp.isChecked -> getmininginfoNOMP()
            radioButtonYiimp.isChecked -> getmininginfoYIIMP()
            else -> {
                val errorSay: TextView = findViewById(R.id.yourminer)
                errorSay.text = "Please specify which type of pool you are using"
            }
        }
    }

    private fun getmininginfoNOMP() {
        val wallet = intent.getStringExtra("walletaddress") ?: return

        Timer().scheduleAtFixedRate(0, 5000) {
            Thread {
                try {
                    val url = "https://bmine.net/api/worker_stats?$wallet"
                    val json = httpGetJson(url)

                    val info1 = json.getString("miner")
                    val info2 = json.getString("immature")
                    val info3 = json.getString("paid")
                    val info4 = json.getString("totalHash")
                    val info5 = json.getString("totalShares")

                    runOnUiThread {
                        findViewById<TextView>(R.id.yourminer).text =
                            "Your Address: $info1"

                        findViewById<TextView>(R.id.yourimmature).text =
                            "Your Immature Balance: $info2 MWC"

                        findViewById<TextView>(R.id.yourpaid).text =
                            "Paid out: $info3 MWC"

                        findViewById<TextView>(R.id.yourhashrate).text =
                            "Your hashrate: ${(info4.toFloat().roundToInt() / 1000)} KH/s"

                        findViewById<TextView>(R.id.yourtotalshares).text =
                            "Your shares: $info5"
                    }

                } catch (e: Exception) {
                    runOnUiThread {
                        findViewById<TextView>(R.id.yourminer).text =
                            "Error fetching mining stats"
                    }
                }
            }.start()
        }
    }

    private fun getmininginfoYIIMP() {
        val wallet = intent.getStringExtra("walletaddress") ?: return

        Timer().scheduleAtFixedRate(0, 5000) {
            Thread {
                try {
                    val url = "http://miningmadness.com/api/wallet?address=$wallet"
                    val json = httpGetJson(url)

                    val info2 = json.getString("unpaid")
                    val info3 = json.getString("paid24h")
                    val info4 = json.getString("totalHash")
                    val info5 = json.getString("totalShares")

                    runOnUiThread {
                        findViewById<TextView>(R.id.yourminer).text =
                            "Your Address: $wallet"

                        findViewById<TextView>(R.id.yourimmature).text =
                            "Your Immature Balance: $info2 MWC"

                        findViewById<TextView>(R.id.yourpaid).text =
                            "Paid out: $info3 MWC"

                        findViewById<TextView>(R.id.yourhashrate).text =
                            "Your hashrate: ${(info4.toFloat().roundToInt() / 1000)} KH/s"

                        findViewById<TextView>(R.id.yourtotalshares).text =
                            "Your shares: $info5"
                    }

                } catch (e: Exception) {
                    runOnUiThread {
                        findViewById<TextView>(R.id.yourminer).text =
                            "Error fetching mining stats"
                    }
                }
            }.start()
        }
    }
}
