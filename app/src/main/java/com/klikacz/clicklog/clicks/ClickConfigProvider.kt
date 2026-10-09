package com.klikacz.clicklog.clicks

import com.klikacz.clicklog.data.AppSettings
import com.klikacz.clicklog.data.Settings
import com.klikacz.clicklog.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hot, always-current snapshot of settings for hot paths (accessibility service,
 * launch activity) that cannot afford to suspend before deciding what to do with a key.
 */
@Singleton
class ClickConfigProvider @Inject constructor(
    settings: Settings,
    @ApplicationScope scope: CoroutineScope,
) {
    val state: StateFlow<AppSettings> = settings.flow.stateIn(
        scope,
        SharingStarted.Eagerly,
        // Small preferences file; a blocking first read at process start is acceptable
        // and guarantees the very first key press is handled with real settings.
        runBlocking { settings.current() },
    )

    val current: AppSettings get() = state.value
}
