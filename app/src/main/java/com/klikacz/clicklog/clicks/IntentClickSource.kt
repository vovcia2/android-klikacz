package com.klikacz.clicklog.clicks

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Path B: the system launches [com.klikacz.clicklog.service.LaunchReceiverActivity]; every launch
 * is a click. There is no kernel timestamp, so the caller passes `currentTimeMillis` taken as
 * early as possible in `onCreate`.
 */
@Singleton
class IntentClickSource @Inject constructor(
    private val config: ClickConfigProvider,
) : ClickSource {

    override val type = ClickSourceType.LAUNCH_INTENT

    private val _clicks = MutableSharedFlow<RawClick>(extraBufferCapacity = 64)
    override val clicks: SharedFlow<RawClick> = _clicks.asSharedFlow()

    /** @return true if the launch was registered as a click (path B active). */
    fun onLaunch(wallTimeMs: Long, nowUptimeMs: Long): Boolean {
        if (config.current.effectivePath != ClickSourceType.LAUNCH_INTENT) return false
        val click = RawClick(wallTimeMs = wallTimeMs, uptimeMs = null, monotonicMs = nowUptimeMs, source = type)
        if (!_clicks.tryEmit(click)) Timber.w("IntentClickSource buffer full, dropping click")
        return true
    }
}
