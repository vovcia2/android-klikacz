package com.klikacz.clicklog

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.klikacz.clicklog.clicks.ClickConfigProvider
import com.klikacz.clicklog.clicks.ClickSourceType
import com.klikacz.clicklog.clicks.IntentClickSource
import com.klikacz.clicklog.data.DiagnosticChannel
import com.klikacz.clicklog.data.Settings
import com.klikacz.clicklog.diagnostics.DiagnosticLogger
import com.klikacz.clicklog.ui.ClickLogNavHost
import com.klikacz.clicklog.ui.Routes
import com.klikacz.clicklog.ui.SystemIntents
import com.klikacz.clicklog.ui.theme.ClickLogTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var logger: DiagnosticLogger
    @Inject lateinit var settings: Settings
    @Inject lateinit var config: ClickConfigProvider
    @Inject lateinit var intentSource: IntentClickSource

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Timber.i("POST_NOTIFICATIONS granted=%s", granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val wall = System.currentTimeMillis()
        val uptime = SystemClock.uptimeMillis()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent, wall, uptime, created = true)
        requestNotificationPermissionIfNeeded()

        setContent {
            ClickLogTheme {
                val navController = rememberNavController()
                val start by produceState<String?>(initialValue = null) {
                    value = if (settings.current().wizardCompleted) Routes.MAIN else Routes.WIZARD
                }
                start?.let { ClickLogNavHost(navController = navController, startDestination = it) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        val wall = System.currentTimeMillis()
        val uptime = SystemClock.uptimeMillis()
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent, wall, uptime, created = false)
    }

    /**
     * Diagnostics channel "MainActivity onCreate/onNewIntent" (spec §3.2). Additionally, on path B
     * a launch whose referrer matches the one recorded by the wizard counts as a click, which
     * covers vendors whose key picker launches the main launcher activity.
     */
    private fun handleIntent(intent: Intent?, wallMs: Long, uptimeMs: Long, created: Boolean) {
        val referrer = runCatching { referrer?.toString() }.getOrNull()
        logger.logIntent(
            DiagnosticChannel.MAIN_INTENT, intent, tsMs = wallMs,
            extra = mapOf("referrer" to referrer, "created" to created),
        )
        val cfg = config.current
        val wanted = cfg.diagnosis?.mainLaunchReferrer
        if (cfg.effectivePath == ClickSourceType.LAUNCH_INTENT && wanted != null && referrer == wanted) {
            val home = SystemIntents.homeLauncherPackage(this)
            if (home == null || !referrer.contains(home)) {
                intentSource.onLaunch(wallMs, uptimeMs)
                Timber.i("MainActivity launch counted as click (referrer=%s)", referrer)
            }
        }
    }

    /** Diagnostics channel "Activity.onKeyDown/onKeyUp" (spec §3.2). Auto-repeat DOWNs are skipped. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount == 0) logger.logKeyEvent(DiagnosticChannel.ACTIVITY_KEY, event)
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        logger.logKeyEvent(DiagnosticChannel.ACTIVITY_KEY, event)
        return super.onKeyUp(keyCode, event)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
