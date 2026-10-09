package com.klikacz.clicklog.diagnostics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.klikacz.clicklog.data.DiagnosticChannel
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Dynamically registered receiver for [VendorActions.ACTIONS] (spec §3.2). */
@Singleton
class VendorBroadcastListener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: DiagnosticLogger,
) {
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            Timber.i("Vendor broadcast: %s", intent)
            logger.logIntent(
                DiagnosticChannel.VENDOR_BROADCAST, intent,
                extra = mapOf("matchesKeywords" to VendorActions.matches(intent.action)),
            )
        }
    }

    @Synchronized
    fun register() {
        if (registered) return
        val filter = IntentFilter().apply { VendorActions.ACTIONS.forEach(::addAction) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        registered = true
    }

    @Synchronized
    fun unregister() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }
}
