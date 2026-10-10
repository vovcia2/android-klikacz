package com.klikacz.clicklog.clicks

import android.view.KeyEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Path A: presses delivered as [KeyEvent]s to the accessibility service.
 *
 * The accessibility service forwards every key event here; this class decides whether it is
 * "our" button (configured keycode on an active path A) and emits a [RawClick] for ACTION_DOWN.
 */
@Singleton
class KeyEventClickSource @Inject constructor(
    private val config: ClickConfigProvider,
) : ClickSource {

    override val type = ClickSourceType.KEY_EVENT

    private val channel = Channel<RawClick>(Channel.UNLIMITED)
    override val clicks: Flow<RawClick> = channel.receiveAsFlow()

    /**
     * @return true if the event belongs to the configured button and should be consumed so the
     *   system does not also run its default SOS action (spec §4).
     */
    fun onKeyEvent(keyCode: Int, action: Int, repeatCount: Int, eventTimeUptimeMs: Long, nowUptimeMs: Long, nowWallMs: Long): Boolean {
        val settings = config.current
        if (settings.effectivePath != ClickSourceType.KEY_EVENT) return false
        val wanted = settings.activeKeycode ?: return false
        if (keyCode != wanted) return false

        // Auto-repeat DOWNs (key held) are the same press: consume but do not emit.
        if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
            val click = RawClick(
                wallTimeMs = ClickProcessor.eventWallTime(eventTimeUptimeMs, nowUptimeMs, nowWallMs),
                uptimeMs = eventTimeUptimeMs,
                monotonicMs = eventTimeUptimeMs,
                source = type,
            )
            if (channel.trySend(click).isFailure) Timber.w("KeyEventClickSource channel closed, dropping click")
        }
        // Consume both DOWN and UP of the configured key.
        return true
    }
}
