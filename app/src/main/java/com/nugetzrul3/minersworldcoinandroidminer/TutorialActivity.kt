package com.nugetzrul3.minersworldcoinandroidminer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class TutorialActivity : AppCompatActivity() {

    private data class Page(val title: String, val body: String)

    private lateinit var sharedpref: SharedPref
    private lateinit var titleView: TextView
    private lateinit var bodyView: TextView
    private lateinit var pageView: TextView
    private lateinit var backButton: Button
    private lateinit var nextButton: Button

    private val pages = listOf(
        Page(
            "Welcome",
            "This app mines Miners World Coin (MWC) and ADVC using your phone's CPU.\n\n" +
                    "Swipe through this short guide to learn what each setting does. You can skip it " +
                    "anytime and reopen it later from the menu (Tutorial)."
        ),
        Page(
            "1. Pool URL",
            "The pool is where your hashrate is sent. Use a stratum URL such as " +
                    "stratum+tcp://bmine.net:3033.\n\n" +
                    "Pick a bundled preset, or save your own pool with the 'Save pool' button. " +
                    "Saved pools appear in the preset list next time."
        ),
        Page(
            "2. Wallet address",
            "Enter your own wallet address. For pool workers you can append a worker name " +
                    "after a dot, for example: 9R5aTkm... .worker1\n\n" +
                    "The address must match the selected algorithm: an MWC address for " +
                    "YespowerMwc, and an ADVC address for YespowerAdvc."
        ),
        Page(
            "3. Password & threads",
            "Password is optional - most pools accept any value, so leave it blank.\n\n" +
                    "Thread count controls how many CPU cores mine. More threads usually means " +
                    "a higher hashrate but also more heat and battery use. Start with the default."
        ),
        Page(
            "4. Algorithm & start",
            "Choose YespowerMwc for MWC pools/wallets or YespowerAdvc for ADVC pools/wallets.\n\n" +
                    "Press Start to mine, and Stop when you want to finish. Mining continues in " +
                    "the background with a notification until you stop it."
        ),
        Page(
            "5. Saved setups",
            "A setup saves everything: pool, wallet, password, thread count, and algorithm.\n\n" +
                    "Use 'Save setup' to store the current form under a name, then pick it from " +
                    "the setups list to load it instantly. 'Delete setup' removes the selected one."
        ),
        Page(
            "6. Status & log",
            "The status bar shows connection state, uptime, and the countdown to the " +
                    "5-minute support window.\n\n" +
                    "The log box shows miner output. Use Clear, Copy, or Share to troubleshoot. " +
                    "No shares yet? Check your pool URL, wallet address, and network."
        ),
        Page(
            "7. Settings & menu",
            "In Settings you can toggle mining notifications, the 5-minute support window, " +
                    "and battery/heat protection, or open battery optimization settings.\n\n" +
                    "The menu has Website, Github, Mobile Wallets, Stats, Tutorial, and Settings. " +
                    "Battery optimization may reduce hashrate - exempting the app helps."
        )
    )

    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedpref = SharedPref(this)
        setTheme(if (sharedpref.loadNightModestate()) R.style.DarkTheme else R.style.AppTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)

        val toolbar: Toolbar = findViewById(R.id.tutorialToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        titleView = findViewById(R.id.tutorialTitle)
        bodyView = findViewById(R.id.tutorialBody)
        pageView = findViewById(R.id.tutorialPage)
        backButton = findViewById(R.id.tutorialBack)
        nextButton = findViewById(R.id.tutorialNext)

        backButton.setOnClickListener {
            if (index > 0) {
                index--
                render()
            }
        }
        findViewById<Button>(R.id.tutorialSkip).setOnClickListener { finishTutorial() }
        nextButton.setOnClickListener {
            if (index == pages.lastIndex) {
                finishTutorial()
            } else {
                index++
                render()
            }
        }

        render()
    }

    private fun render() {
        val page = pages[index]
        titleView.text = page.title
        bodyView.text = page.body
        pageView.text = "${index + 1} of ${pages.size}"
        backButton.isEnabled = index > 0
        nextButton.text = if (index == pages.lastIndex) "Finish" else "Next"
        bodyView.scrollTo(0, 0)
    }

    private fun finishTutorial() {
        sharedpref.setTutorialSeen(true)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finishTutorial()
        return true
    }
}
