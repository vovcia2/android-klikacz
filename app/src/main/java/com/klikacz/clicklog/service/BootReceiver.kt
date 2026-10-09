package com.klikacz.clicklog.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.klikacz.clicklog.data.Settings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

/** Restarts the foreground service after boot if it was enabled (spec §5). */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: Settings

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val enabled = runBlocking { settings.current().serviceEnabled }
        Timber.i("BootReceiver %s serviceEnabled=%s", intent.action, enabled)
        if (enabled) ClickLogService.start(context)
    }
}
