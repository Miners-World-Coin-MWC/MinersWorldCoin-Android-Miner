package com.nugetzrul3.minersworldcoinandroidminer

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class SettingsPage : AppCompatActivity() {

    private lateinit var sharedpref: SharedPref

    override fun onCreate(savedInstanceState: Bundle?) {

        sharedpref = SharedPref(this)

        if (sharedpref.loadNightModestate()) {
            setTheme(R.style.DarkTheme)
        } else {
            setTheme(R.style.AppTheme)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_page)

        val toolbar: Toolbar = findViewById(R.id.settingstoolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val switchManual: Switch = findViewById(R.id.switch_manual_diff)
        val editDiff: EditText = findViewById(R.id.edit_manual_diff)
        val saveButton: Button = findViewById(R.id.button_save_settings)

        // Load saved state
        switchManual.isChecked = sharedpref.getManualDiffEnabled()
        editDiff.setText(sharedpref.getManualDiffValue())
        editDiff.isEnabled = switchManual.isChecked

        switchManual.setOnCheckedChangeListener { _, isChecked ->
            editDiff.isEnabled = isChecked
        }

        saveButton.setOnClickListener {
            sharedpref.setManualDiffEnabled(switchManual.isChecked)
            sharedpref.setManualDiffValue(editDiff.text.toString())
            Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show()
        }

        getVersionNumber()
    }

    private fun getVersionNumber() {
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        val versionTextView: TextView = findViewById(R.id.version_number)
        versionTextView.text = "MinersWorldCoin Android Miner\nVersion: $version"
    }
}