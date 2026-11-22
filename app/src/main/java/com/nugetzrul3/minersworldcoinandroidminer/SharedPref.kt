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

    fun setBatteryGuardEnabled(enabled: Boolean) =
        prefs.edit().putBoolean("battery_guard_enabled", enabled).apply()

    fun batteryGuardEnabled(): Boolean = prefs.getBoolean("battery_guard_enabled", true)

    fun setTutorialSeen(seen: Boolean) =
        prefs.edit().putBoolean("tutorial_seen", seen).apply()

    fun tutorialSeen(): Boolean = prefs.getBoolean("tutorial_seen", false)

    fun customPools(): Set<String> =
        prefs.getStringSet("custom_pools", emptySet()) ?: emptySet()

    fun setCustomPools(pools: Set<String>) =
        prefs.edit().putStringSet("custom_pools", pools).apply()
}
