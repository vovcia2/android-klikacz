package com.klikacz.clicklog.diagnostics

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.view.KeyEvent
import com.klikacz.clicklog.clicks.ClickConfigProvider
import com.klikacz.clicklog.data.DiagnosticChannel
import com.klikacz.clicklog.data.DiagnosticDao
import com.klikacz.clicklog.data.DiagnosticEntity
import com.klikacz.clicklog.di.ApplicationScope
import com.klikacz.clicklog.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collects raw events from every listening channel (spec §3.2) into `diagnostic_log`.
 *
 * Events are written when a capture is active (setup wizard test step or diagnostics screen
 * open) or when the "diagnostics mode" setting is on. Everything always goes to Timber.
 */
@Singleton
class DiagnosticLogger @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val dao: DiagnosticDao,
    private val config: ClickConfigProvider,
) {
    private val captures = AtomicInteger(0)
    private val _captureActive = MutableStateFlow(false)
    val captureActive: StateFlow<Boolean> = _captureActive.asStateFlow()

    private val queue = Channel<DiagnosticEntity>(Channel.UNLIMITED)

    private val powerManager: PowerManager? by lazy {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    init {
        scope.launch {
            for (e in queue) {
                runCatching { withContext(ioDispatcher) { dao.insert(e) } }
                    .onFailure { Timber.e(it, "diagnostic insert failed") }
            }
        }
    }

    /** Begin a capture window; call [endCapture] when done. Reference counted. */
    fun beginCapture() {
        captures.incrementAndGet()
        _captureActive.value = true
    }

    fun endCapture() {
        if (captures.decrementAndGet() <= 0) {
            captures.set(0)
            _captureActive.value = false
        }
    }

    val shouldLog: Boolean get() = _captureActive.value || config.current.diagnosticsMode

    fun isScreenOn(): Boolean = powerManager?.isInteractive ?: true

    fun isKeyguardLocked(): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked ?: false

    /**
     * Channels that are rare and decisive for diagnosing the device, recorded even outside a
     * capture so a screen-off test can be reconstructed afterwards.
     */
    private val alwaysLogged = setOf(
        DiagnosticChannel.LAUNCH_INTENT, DiagnosticChannel.VENDOR_BROADCAST, DiagnosticChannel.APP, DiagnosticChannel.CLICK,
    )

    /**
     * Milliseconds the device has spent in deep sleep since boot. The *difference* between two
     * entries tells whether the device suspended between them (the vendor key handler behaves
     * differently when the device is awake with the screen off vs. asleep).
     */
    private fun asleepMs(): Long = android.os.SystemClock.elapsedRealtime() - android.os.SystemClock.uptimeMillis()

    fun log(entity: DiagnosticEntity) {
        Timber.tag("DIAG").i(
            "%s key=%s scan=%s action=%s intent=%s screenOn=%s extras=%s",
            entity.channel, entity.keycode, entity.scancode, entity.action,
            entity.intentAction, entity.screenOn, entity.extrasJson,
        )
        if (shouldLog || entity.channel in alwaysLogged) queue.trySend(entity)
    }

    /** Writes a marker regardless of capture state (used by the wizard to delimit tests). */
    fun note(text: String) {
        queue.trySend(
            DiagnosticEntity(
                tsMs = System.currentTimeMillis(),
                channel = DiagnosticChannel.APP,
                extrasJson = JSONObject().put("note", text).put("asleepMs", asleepMs()).toString(),
                screenOn = isScreenOn(),
            ),
        )
    }

    fun logKeyEvent(channel: DiagnosticChannel, event: KeyEvent, extra: Map<String, Any?> = emptyMap()) {
        val nowUptime = android.os.SystemClock.uptimeMillis()
        val wall = System.currentTimeMillis() - (nowUptime - event.eventTime)
        val json = JSONObject().apply {
            put("source", event.source)
            put("deviceId", event.deviceId)
            put("deviceName", event.device?.name)
            put("repeat", event.repeatCount)
            put("flags", event.flags)
            put("eventTime", event.eventTime)
            put("downTime", event.downTime)
            put("keyName", KeyEvent.keyCodeToString(event.keyCode))
            put("asleepMs", asleepMs())
            extra.forEach { (k, v) -> put(k, v) }
        }
        log(
            DiagnosticEntity(
                tsMs = wall,
                channel = channel,
                keycode = event.keyCode,
                scancode = event.scanCode,
                action = when (event.action) {
                    KeyEvent.ACTION_DOWN -> DiagnosticEntity.ACTION_DOWN
                    KeyEvent.ACTION_UP -> DiagnosticEntity.ACTION_UP
                    else -> DiagnosticEntity.ACTION_MULTIPLE
                },
                extrasJson = json.toString(),
                screenOn = isScreenOn(),
            ),
        )
    }

    fun logIntent(channel: DiagnosticChannel, intent: Intent?, tsMs: Long = System.currentTimeMillis(), extra: Map<String, Any?> = emptyMap()) {
        val json = JSONObject().apply {
            put("categories", intent?.categories?.joinToString(","))
            put("data", intent?.dataString)
            put("flags", intent?.flags?.let { "0x" + Integer.toHexString(it) })
            put("component", intent?.component?.flattenToShortString())
            put("package", intent?.`package`)
            put("extras", bundleToJson(intent?.extras))
            put("keyguardLocked", isKeyguardLocked())
            put("asleepMs", asleepMs())
            extra.forEach { (k, v) -> put(k, v) }
        }
        log(
            DiagnosticEntity(
                tsMs = tsMs,
                channel = channel,
                intentAction = intent?.action,
                extrasJson = json.toString(),
                screenOn = isScreenOn(),
            ),
        )
    }

    /** Records what the click processor did with a raw click (always logged). */
    fun logClick(wallTimeMs: Long, source: String, accepted: Boolean, storedId: Long? = null) {
        val json = JSONObject()
            .put("source", source)
            .put("result", if (accepted) "accepted" else "absorbed")
            .put("storedId", storedId)
            .put("asleepMs", asleepMs())
        log(DiagnosticEntity(tsMs = wallTimeMs, channel = DiagnosticChannel.CLICK, extrasJson = json.toString(), screenOn = isScreenOn()))
    }

    private fun bundleToJson(bundle: Bundle?): JSONObject? {
        if (bundle == null) return null
        val obj = JSONObject()
        for (key in bundle.keySet()) {
            @Suppress("DEPRECATION")
            val value = bundle.get(key)
            obj.put(key, value?.toString())
        }
        return obj
    }
}
