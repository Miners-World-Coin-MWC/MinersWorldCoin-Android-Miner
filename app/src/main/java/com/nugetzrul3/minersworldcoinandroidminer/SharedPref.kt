package com.nugetzrul3.minersworldcoinandroidminer

import android.content.Context
import android.content.SharedPreferences

class SharedPref(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("filename", Context.MODE_PRIVATE)

    fun setNightModeState(state: Boolean) = prefs.edit().putBoolean("NightMode", state).apply()

    fun loadNightModestate(): Boolean = prefs.getBoolean("NightMode", false)

    fun setBatteryNoticeShown(shown: Boolean) =
        prefs.edit().putBoolean("battery_notice_shown", shown).apply()

    fun getBatteryNoticeShown(): Boolean = prefs.getBoolean("battery_notice_shown", false)

    fun setNotificationsEnabled(enabled: Boolean) =
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()

    fun notificationsEnabled(): Boolean = prefs.getBoolean("notifications_enabled", true)

    fun setDonationsEnabled(enabled: Boolean) =
        prefs.edit().putBoolean("donations_enabled", enabled).apply()

    fun donationsEnabled(): Boolean = prefs.getBoolean("donations_enabled", true)
}
