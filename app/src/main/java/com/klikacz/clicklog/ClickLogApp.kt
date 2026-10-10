package com.klikacz.clicklog

import android.app.Application
import com.klikacz.clicklog.clicks.ClickPipeline
import com.klikacz.clicklog.data.Settings
import com.klikacz.clicklog.di.ApplicationScope
import com.klikacz.clicklog.diagnostics.DiagnosticLogger
import com.klikacz.clicklog.diagnostics.VendorBroadcastListener
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class ClickLogApp : Application() {

    // Injected eagerly so the pipeline exists before the first key event / launch.
    @Inject lateinit var pipeline: ClickPipeline
    @Inject lateinit var vendorListener: VendorBroadcastListener
    @Inject lateinit var logger: DiagnosticLogger
    @Inject lateinit var settings: Settings
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        vendorListener.register()
        scope.launch { settings.ensureSession() }
        logger.note("process start pid=${android.os.Process.myPid()} screenOn=${logger.isScreenOn()}")
        Timber.i("ClickLogApp started")
    }
}
