package com.klikacz.clicklog.clicks

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Path B: the system launches [com.klikacz.clicklog.service.LaunchReceiverActivity]; every launch
 * is a click. There is no kernel timestamp, so the caller passes `currentTimeMillis` taken as
 * early as possible in `onCreate`.
 *
 * Launches of that activity are registered regardless of the configured path: on the CAT S75 the
 * vendor key handler delivers screen-off presses only this way, even when path A (KeyEvent) is
 * active with the screen on. [ClickProcessor] merges a press seen on both sources into one click.
 */
@Singleton
class IntentClickSource @Inject constructor() : ClickSource {

    override val type = ClickSourceType.LAUNCH_INTENT

    private val channel = Channel<RawClick>(Channel.UNLIMITED)
    override val clicks: Flow<RawClick> = channel.receiveAsFlow()

    /** Registers one launch as a raw click; returns it for callers that want to await persistence. */
    fun onLaunch(wallTimeMs: Long, nowUptimeMs: Long): RawClick {
        val click = RawClick(wallTimeMs = wallTimeMs, uptimeMs = null, monotonicMs = nowUptimeMs, source = type)
        if (channel.trySend(click).isFailure) Timber.w("IntentClickSource channel closed, dropping click")
        return click
    }
}
