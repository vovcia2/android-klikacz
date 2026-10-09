package com.klikacz.clicklog.service

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import com.klikacz.clicklog.clicks.ClickConfigProvider
import com.klikacz.clicklog.clicks.IntentClickSource
import com.klikacz.clicklog.data.DiagnosticChannel
import com.klikacz.clicklog.diagnostics.DiagnosticLogger
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Path B (spec §4): exported, invisible activity that the programmable key launches.
 * Each launch is one click. It records the time as early as possible and finishes immediately.
 */
@AndroidEntryPoint
class LaunchReceiverActivity : ComponentActivity() {

    @Inject lateinit var intentSource: IntentClickSource
    @Inject lateinit var logger: DiagnosticLogger
    @Inject lateinit var config: ClickConfigProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        // Capture time before anything else: there is no kernel eventTime on this path.
        val wall = System.currentTimeMillis()
        val uptime = SystemClock.uptimeMillis()
        super.onCreate(savedInstanceState)

        val referrer = runCatching { referrer?.toString() }.getOrNull()
        logger.logIntent(
            DiagnosticChannel.LAUNCH_INTENT, intent, tsMs = wall,
            extra = mapOf("referrer" to referrer, "callingPackage" to callingPackage),
        )
        val registered = intentSource.onLaunch(wall, uptime)
        Timber.i("LaunchReceiverActivity launched, registered=%s intent=%s", registered, intent)

        if (config.current.serviceEnabled && !ClickLogService.isRunning.value) {
            ClickLogService.start(this)
        }
        finish()
    }
}
