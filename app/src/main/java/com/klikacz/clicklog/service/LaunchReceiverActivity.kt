package com.klikacz.clicklog.service

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import com.klikacz.clicklog.clicks.IntentClickSource
import com.klikacz.clicklog.data.DiagnosticChannel
import com.klikacz.clicklog.diagnostics.DiagnosticLogger
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Path B (spec §4): exported, invisible activity that the programmable key launches.
 * Each launch is one click. It records the time as early as possible and finishes immediately.
 *
 * The theme is `Theme.NoDisplay`, so the activity never becomes visible (and does not itself
 * turn the screen on); Android requires such an activity to call [finish] before `onResume`
 * completes, which is why nothing here is asynchronous. Persistence is handled by the
 * application-scoped click pipeline; the foreground service keeps the process alive.
 *
 * On the CAT S75 this is also how presses arrive with the screen off, whatever the active path.
 */
@AndroidEntryPoint
class LaunchReceiverActivity : ComponentActivity() {

    @Inject lateinit var intentSource: IntentClickSource
    @Inject lateinit var logger: DiagnosticLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        // Capture time before anything else: there is no kernel eventTime on this path.
        val wall = System.currentTimeMillis()
        val uptime = SystemClock.uptimeMillis()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleLaunch(intent, wall, uptime, "onCreate")
        finish()
    }

    /** Defensive: a launch routed to an existing instance (should not happen with launchMode standard). */
    override fun onNewIntent(intent: Intent) {
        val wall = System.currentTimeMillis()
        val uptime = SystemClock.uptimeMillis()
        super.onNewIntent(intent)
        handleLaunch(intent, wall, uptime, "onNewIntent")
        finish()
    }

    private fun handleLaunch(intent: Intent?, wallMs: Long, uptimeMs: Long, via: String) {
        val referrer = runCatching { referrer?.toString() }.getOrNull()
        logger.logIntent(
            DiagnosticChannel.LAUNCH_INTENT, intent, tsMs = wallMs,
            extra = mapOf(
                "referrer" to referrer, "callingPackage" to callingPackage, "via" to via,
                "serviceRunning" to ClickLogService.isRunning.value,
            ),
        )
        intentSource.onLaunch(wallMs, uptimeMs)
        Timber.i("LaunchReceiverActivity %s wall=%d intent=%s", via, wallMs, intent)

        // Keep the process alive so the click is stored and the next launch is warm. This also
        // covers the first wizard run, when the service has not been enabled yet.
        if (!ClickLogService.isRunning.value) ClickLogService.start(this)
    }
}
