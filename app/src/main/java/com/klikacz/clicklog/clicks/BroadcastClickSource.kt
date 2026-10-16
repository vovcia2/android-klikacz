package com.klikacz.clicklog.clicks

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Path C: a vendor broadcast that reports a hardware key press directly to the app.
 *
 * Samsung XCover / Top key: when the user picks ClickLog under Settings > Advanced features >
 * XCover key / Top key, the system sends `com.samsung.android.knox.intent.action.HARD_KEY_REPORT`
 * with key code and down/up extras (see [com.klikacz.clicklog.service.SamsungHardKeyReceiver]).
 * Like app launches, these are registered regardless of the configured path: the broadcast is
 * only ever sent because the user bound the key to this app.
 */
@Singleton
class BroadcastClickSource @Inject constructor() : ClickSource {

    override val type = ClickSourceType.VENDOR_BROADCAST

    private val channel = Channel<RawClick>(Channel.UNLIMITED)
    override val clicks: Flow<RawClick> = channel.receiveAsFlow()

    /** Registers one key-down broadcast as a raw click. */
    fun onKeyDown(wallTimeMs: Long, nowUptimeMs: Long): RawClick {
        val click = RawClick(wallTimeMs = wallTimeMs, uptimeMs = null, monotonicMs = nowUptimeMs, source = type)
        if (channel.trySend(click).isFailure) Timber.w("BroadcastClickSource channel closed, dropping click")
        return click
    }
}
