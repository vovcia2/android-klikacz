package com.klikacz.clicklog.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.klikacz.clicklog.clicks.BroadcastClickSource
import com.klikacz.clicklog.data.DiagnosticChannel
import com.klikacz.clicklog.data.DiagnosticEntity
import com.klikacz.clicklog.diagnostics.DiagnosticLogger
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

/**
 * Samsung XCover / Top key integration (Knox "unmanaged key mapping").
 *
 * The `HARD_KEY_PRESS` meta-data in the manifest makes ClickLog selectable under
 * Settings > Advanced features > XCover key / Top key; once selected, the system broadcasts
 * [ACTION_HARD_KEY_REPORT] on every press and release, with the screen on or off.
 */
@AndroidEntryPoint
class SamsungHardKeyReceiver : BroadcastReceiver() {

    @Inject lateinit var source: BroadcastClickSource
    @Inject lateinit var logger: DiagnosticLogger

    override fun onReceive(context: Context, intent: Intent) {
        val wall = System.currentTimeMillis()
        val uptime = SystemClock.uptimeMillis()
        if (intent.action != ACTION_HARD_KEY_REPORT) return

        val keyCode = intent.getIntExtra(EXTRA_KEY_CODE, -1)
        val reportType = intent.getIntExtra(EXTRA_KEY_REPORT_TYPE, -1)
        val action = when (reportType) {
            KEY_ACTION_DOWN -> DiagnosticEntity.ACTION_DOWN
            KEY_ACTION_UP -> DiagnosticEntity.ACTION_UP
            else -> "UNKNOWN_$reportType"
        }
        Timber.i("Samsung hard key: code=%d (%s) action=%s", keyCode, keyName(keyCode), action)

        logger.log(
            DiagnosticEntity(
                tsMs = wall,
                channel = DiagnosticChannel.VENDOR_BROADCAST,
                keycode = keyCode,
                action = action,
                intentAction = intent.action,
                extrasJson = JSONObject()
                    .put("keyName", keyName(keyCode))
                    .put("reportType", reportType)
                    .put("package", intent.`package`)
                    .put("keyguardLocked", logger.isKeyguardLocked())
                    .toString(),
                screenOn = logger.isScreenOn(),
            ),
        )

        if (reportType == KEY_ACTION_DOWN) {
            source.onKeyDown(wall, uptime)
            if (!ClickLogService.isRunning.value) ClickLogService.start(context)
        }
    }

    companion object {
        const val ACTION_HARD_KEY_REPORT = "com.samsung.android.knox.intent.action.HARD_KEY_REPORT"
        const val EXTRA_KEY_CODE = "com.samsung.android.knox.intent.extra.KEY_CODE"
        const val EXTRA_KEY_REPORT_TYPE = "com.samsung.android.knox.intent.extra.KEY_REPORT_TYPE"

        /** XCover key. */
        const val KEYCODE_PTT = 1015

        /** Top key. */
        const val KEYCODE_EMERGENCY = 1079

        const val KEY_ACTION_DOWN = 1
        const val KEY_ACTION_UP = 2

        fun keyName(code: Int): String = when (code) {
            KEYCODE_PTT -> "XCOVER_KEY"
            KEYCODE_EMERGENCY -> "TOP_KEY"
            else -> "KEY_$code"
        }
    }
}
