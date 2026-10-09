package com.klikacz.clicklog.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings as SystemSettings
import android.text.TextUtils
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.klikacz.clicklog.clicks.KeyEventClickSource
import com.klikacz.clicklog.data.DiagnosticChannel
import com.klikacz.clicklog.diagnostics.DiagnosticLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject

/**
 * Path A (spec §4): receives hardware key events system-wide, including with the screen off,
 * via `onKeyEvent` with `canRequestFilterKeyEvents=true`.
 */
@AndroidEntryPoint
class ClickAccessibilityService : AccessibilityService() {

    @Inject lateinit var keySource: KeyEventClickSource
    @Inject lateinit var logger: DiagnosticLogger

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info
        _isConnected.value = true
        logger.note("AccessibilityService connected")
        Timber.i("ClickAccessibilityService connected, flags=0x%x", info.flags)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val nowUptime = SystemClock.uptimeMillis()
        val nowWall = System.currentTimeMillis()
        logger.logKeyEvent(DiagnosticChannel.ACCESSIBILITY_KEY, event)
        val consumed = keySource.onKeyEvent(
            keyCode = event.keyCode,
            action = event.action,
            eventTimeUptimeMs = event.eventTime,
            nowUptimeMs = nowUptime,
            nowWallMs = nowWall,
        )
        return consumed || super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        _isConnected.value = false
        logger.note("AccessibilityService unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        _isConnected.value = false
        super.onDestroy()
    }

    companion object {
        private val _isConnected = MutableStateFlow(false)

        /** True while the system has the service bound in this process. */
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        /** Whether the user has enabled this service in system accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, ClickAccessibilityService::class.java)
            val enabled = SystemSettings.Secure.getString(
                context.contentResolver,
                SystemSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            return splitter.any { ComponentName.unflattenFromString(it) == expected }
        }
    }
}
