package com.nugetzrul3.minersworldcoinandroidminer

import android.content.Context
import android.content.SharedPreferences

class SharedPref(context: Context) {

    internal var mysharedPref: SharedPreferences =
        context.getSharedPreferences("filename", Context.MODE_PRIVATE)

    // ------------------------
    // NIGHT MODE
    // ------------------------

    fun setNightModeState(state: Boolean) {
        val editor = mysharedPref.edit()
        editor.putBoolean("NightMode", state)
        editor.apply()
    }

    fun loadNightModestate(): Boolean {
        return mysharedPref.getBoolean("NightMode", false)
    }

    // ------------------------
    // START BUTTON STATE
    // ------------------------

    fun setButtonModeState(state: Boolean) {
        val editor = mysharedPref.edit()
        editor.putBoolean("STARTTRUE", state)
        editor.apply()
    }

    fun loadButtonModestate(): Boolean {
        return mysharedPref.getBoolean("STARTTRUE", true)
    }

    // ------------------------
    // MINING STATE
    // ------------------------

    fun miningtrue(state: Boolean) {
        val editor = mysharedPref.edit()
        editor.putBoolean("miningtrue", state)
        editor.apply()
    }

    fun loadminingstate(): Boolean {
        return mysharedPref.getBoolean("miningtrue", false)
    }

    // =====================================================
    // 🔥 MANUAL DIFFICULTY SUPPORT (NEW)
    // =====================================================

    fun setManualDiffEnabled(enabled: Boolean) {
        val editor = mysharedPref.edit()
        editor.putBoolean("manual_diff_enabled", enabled)
        editor.apply()
    }

    fun getManualDiffEnabled(): Boolean {
        return mysharedPref.getBoolean("manual_diff_enabled", false)
    }

    fun setManualDiffValue(value: String) {
        val editor = mysharedPref.edit()
        editor.putString("manual_diff_value", value)
        editor.apply()
    }

    fun getManualDiffValue(): String {
        return mysharedPref.getString("manual_diff_value", "") ?: ""
    }
}