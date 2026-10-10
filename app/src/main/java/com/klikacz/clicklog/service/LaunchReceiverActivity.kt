package com.klikacz.clicklog.service

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.klikacz.clicklog.clicks.ClickConfigProvider
import com.klikacz.clicklog.clicks.ClickPipeline
import com.klikacz.clicklog.clicks.IntentClickSource
import com.klikacz.clicklog.data.DiagnosticChannel
import com.klikacz.clicklog.diagnostics.DiagnosticLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * Path B (spec §4): exported, invisible activity that the programmable key launches.
 * Each launch is one click. It records the time as early as possible and finishes as soon as the
 * click is stored (or after a short timeout), so a process kill right after the launch cannot
 * lose the click.
 *
 * On the CAT S75 this is also how presses arrive with the screen off, whatever the active path.
 */
@AndroidEntryPoint
class LaunchReceiverActivity : ComponentActivity() {

    @Inject lateinit var intentSource: IntentClickSource
    @Inject lateinit var pipeline: ClickPipeline
    @Inject lateinit var logger: DiagnosticLogger
    @Inject lateinit var config: ClickConfigProvider

    private var pendingLaunches = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        // Capture time before anything else: there is no kernel eventTime on this path.
        val wall = System.currentTimeMillis()
        val uptime = SystemClock.uptimeMillis()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleLaunch(intent, wall, uptime, "onCreate") else finish()
    }

    /** A press that arrives while a previous instance is still finishing lands here. */
    override fun onNewIntent(intent: Intent) {
        val wall = System.currentTimeMillis()
        val uptime = SystemClock.uptimeMillis()
        super.onNewIntent(intent)
        handleLaunch(intent, wall, uptime, "onNewIntent")
    }

    private fun handleLaunch(intent: Intent?, wallMs: Long, uptimeMs: Long, via: String) {
        val referrer = runCatching { referrer?.toString() }.getOrNull()
        logger.logIntent(
            DiagnosticChannel.LAUNCH_INTENT, intent, tsMs = wallMs,
            extra = mapOf("referrer" to referrer, "callingPackage" to callingPackage, "via" to via),
        )
        val raw = intentSource.onLaunch(wallMs, uptimeMs)
        Timber.i("LaunchReceiverActivity %s wall=%d intent=%s", via, wallMs, intent)

        if (config.current.serviceEnabled && !ClickLogService.isRunning.value) {
            ClickLogService.start(this)
        }

        pendingLaunches++
        lifecycleScope.launch {
            val stored = withTimeoutOrNull(STORE_TIMEOUT_MS) {
                pipeline.stored.first { it.wallTimeMs == raw.wallTimeMs && it.source == raw.source }
            }
            if (stored == null) Timber.d("Launch click not stored within %d ms (merged/debounced or slow)", STORE_TIMEOUT_MS)
            if (--pendingLaunches <= 0 && !isFinishing) finish()
        }
    }

    private companion object {
        const val STORE_TIMEOUT_MS = 1_500L
    }
}
