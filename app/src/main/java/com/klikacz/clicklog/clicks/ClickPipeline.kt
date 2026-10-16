package com.klikacz.clicklog.clicks

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.klikacz.clicklog.di.ApplicationScope
import com.klikacz.clicklog.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import com.klikacz.clicklog.data.ClickEntity
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wires both [ClickSource]s into the [ClickProcessor] and the processor into the database.
 * Created eagerly by the Application so that it exists before any key event arrives.
 */
@Singleton
class ClickPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
    private val config: ClickConfigProvider,
    private val repository: ClickRepository,
    keySource: KeyEventClickSource,
    intentSource: IntentClickSource,
    broadcastSource: BroadcastClickSource,
    private val logger: com.klikacz.clicklog.diagnostics.DiagnosticLogger,
    private val locationTagger: com.klikacz.clicklog.location.LocationTagger,
) {
    val processor = ClickProcessor(
        scope = scope,
        ioDispatcher = ioDispatcher,
        debounceMs = { config.current.debounceMs },
        sessionId = { config.current.sessionId },
        persist = repository::insert,
        crossSourceMergeMs = { config.current.crossSourceMergeMs },
        doublePressWindowMs = { config.current.doublePressWindowMs },
    )

    /** Clicks after they have been written to the database. */
    val stored: SharedFlow<ClickEntity> get() = processor.stored

    private val vibrator: Vibrator? by lazy {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    }

    init {
        scope.launch {
            merge(keySource.clicks, intentSource.clicks, broadcastSource.clicks).collect { raw ->
                val accepted = processor.offer(raw)
                logger.logClick(raw.wallTimeMs, raw.source.name, accepted != null)
                if (accepted == null) {
                    Timber.d("Click absorbed: %s", raw)
                } else {
                    Timber.i("Click accepted: %s", accepted)
                    if (config.current.vibrateOnClick) vibrate()
                }
            }
        }
        scope.launch {
            processor.stored.collect {
                Timber.d("Click stored id=%d", it.id)
                logger.logClick(it.wallTimeMs, it.source.name, accepted = true, storedId = it.id)
                locationTagger.tag(it)
            }
        }
    }

    private fun vibrate() {
        runCatching {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        }.onFailure { Timber.w(it, "vibrate failed") }
    }
}
