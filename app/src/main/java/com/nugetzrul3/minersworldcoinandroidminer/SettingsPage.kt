package com.nugetzrul3.minersworldcoinandroidminer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class SettingsPage : AppCompatActivity() {

    private lateinit var sharedpref: SharedPref
    private lateinit var notificationsSwitch: Switch

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                sharedpref.setNotificationsEnabled(true)
                sendBroadcast(Intent(MiningService.ACTION_REFRESH_NOTIFICATION))
            } else {
                notificationsSwitch.isChecked = false
                sharedpref.setNotificationsEnabled(false)
                Toast.makeText(
                    this,
                    "Notification permission denied; mining progress will not be shown.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedpref = SharedPref(this)

        setTheme(if (sharedpref.loadNightModestate()) R.style.DarkTheme else R.style.AppTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_page)

        val toolbar: Toolbar = findViewById(R.id.settingstoolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        showVersion()
        setupNotificationSetting()
        setupDonationSetting()
        setupBatteryGuardSetting()

        findViewById<android.widget.Button>(R.id.batterySettingsButton).setOnClickListener {
            BatteryOptimization.openSettings(this)
        }
    }

    override fun onResume() {
        super.onResume()
        updateBatteryStatus()
    }

    private fun setupNotificationSetting() {
        notificationsSwitch = findViewById(R.id.notificationsSwitch)
        notificationsSwitch.isChecked = sharedpref.notificationsEnabled()
        notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            val needsPermission = isChecked &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED

            if (needsPermission) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                sharedpref.setNotificationsEnabled(isChecked)
                sendBroadcast(Intent(MiningService.ACTION_REFRESH_NOTIFICATION))
            }
        }
    }

    private fun setupDonationSetting() {
        val donationSwitch = findViewById<Switch>(R.id.donationsSwitch)
        donationSwitch.isChecked = sharedpref.donationsEnabled()
        donationSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedpref.setDonationsEnabled(isChecked)
            sendBroadcast(Intent(MiningService.ACTION_DONATION_SETTINGS_CHANGED))
        }
    }

    private fun setupBatteryGuardSetting() {
        val guardSwitch = findViewById<Switch>(R.id.batteryGuardSwitch)
        guardSwitch.isChecked = sharedpref.batteryGuardEnabled()
        guardSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedpref.setBatteryGuardEnabled(isChecked)
        }
    }

    private fun updateBatteryStatus() {
        val status = if (BatteryOptimization.isIgnoringOptimizations(this)) {
            "Exempt from optimization"
        } else {
            "Optimization enabled (may reduce hashrate)"
        }
        findViewById<TextView>(R.id.battery_status).text = "Battery optimization: $status"
    }

    private fun showVersion() {
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        findViewById<TextView>(R.id.version_number).text =
            "MinersWorldCoin Android Miner\nVersion: $version"
    }
}
