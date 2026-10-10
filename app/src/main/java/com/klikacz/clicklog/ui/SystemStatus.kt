package com.klikacz.clicklog.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as SystemSettings
import androidx.core.content.ContextCompat
import com.klikacz.clicklog.location.LocationTagger
import com.klikacz.clicklog.service.ClickAccessibilityService

/** Snapshot of system-level prerequisites shown on the main screen (spec §2.2). */
data class SystemStatus(
    val accessibilityEnabled: Boolean,
    val notificationsGranted: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val locationGranted: Boolean,
    val backgroundLocationGranted: Boolean,
) {
    companion object {
        fun check(context: Context): SystemStatus {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
            return SystemStatus(
                accessibilityEnabled = ClickAccessibilityService.isEnabled(context),
                notificationsGranted = notifications,
                ignoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(context.packageName),
                locationGranted = LocationTagger.hasPermission(context),
                backgroundLocationGranted = LocationTagger.hasBackgroundPermission(context),
            )
        }
    }
}

/** Intents to the system screens the user needs (spec §3.1 step 1 & 2, §5). */
object SystemIntents {
    fun accessibilitySettings(): Intent =
        Intent(SystemSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Tries known vendor screens for the programmable key first, then falls back to the general
     * settings (spec §3.1 step 1).
     */
    fun programmableKeySettings(context: Context): Intent {
        val candidates = listOf(
            Intent().setClassName("com.bullitt.programmablekey", "com.bullitt.programmablekey.MainActivity"),
            Intent().setClassName("com.bullitt.programmablekey", "com.bullitt.programmablekey.ProgrammableKeyActivity"),
            Intent().setClassName("com.bullitt.keyconfig", "com.bullitt.keyconfig.MainActivity"),
            Intent().setClassName("com.cat.programmablekey", "com.cat.programmablekey.MainActivity"),
            Intent("com.bullitt.programmablekey.SETTINGS"),
            Intent("com.bullitt.intent.action.PROGRAMMABLE_KEY_SETTINGS"),
        )
        val pm = context.packageManager
        val found = candidates.firstOrNull { pm.resolveActivity(it, 0) != null }
        return (found ?: Intent(SystemSettings.ACTION_SETTINGS)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun requestIgnoreBatteryOptimizations(context: Context): Intent =
        Intent(SystemSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData("package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Package of the current home launcher, used to tell icon taps from key launches. */
    fun homeLauncherPackage(context: Context): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    }
}
