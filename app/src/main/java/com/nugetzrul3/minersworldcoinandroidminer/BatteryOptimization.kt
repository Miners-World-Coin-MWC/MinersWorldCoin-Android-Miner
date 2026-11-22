package com.nugetzrul3.minersworldcoinandroidminer

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast

/** Shared by the startup dialog and the Settings page. */
object BatteryOptimization {

    private const val TAG = "BatteryOptimization"

    fun isIgnoringOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Opens the first battery-related settings screen that exists on this device. */
    fun openSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false

        val manufacturer = Build.MANUFACTURER.lowercase()
        val manufacturerComponent: ComponentName? = when {
            manufacturer.contains("xiaomi") -> ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )

            manufacturer.contains("oppo") -> ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )

            manufacturer.contains("vivo") -> ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )

            manufacturer.contains("huawei") -> ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )

            manufacturer.contains("samsung") -> ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            )

            else -> null
        }

        val candidates = mutableListOf<Intent>()

        // Standard whitelist screen first: present on every Android 6+ device and
        // it does not need the permission-gated direct request.
        candidates += Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        manufacturerComponent?.let { candidates += Intent().setComponent(it) }
        candidates += Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
        candidates += Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
        candidates += Intent(Settings.ACTION_SETTINGS)

        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                Log.d(TAG, "No activity for ${intent.action}, trying next")
            } catch (e: Exception) {
                Log.e(TAG, "Unable to open ${intent.action}", e)
            }
        }

        Toast.makeText(context, "Unable to open battery settings on this device.", Toast.LENGTH_LONG)
            .show()
        return false
    }
}
