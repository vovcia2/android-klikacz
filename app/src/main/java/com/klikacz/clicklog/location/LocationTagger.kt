package com.klikacz.clicklog.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.klikacz.clicklog.clicks.ClickConfigProvider
import com.klikacz.clicklog.data.ClickDao
import com.klikacz.clicklog.data.ClickEntity
import com.klikacz.clicklog.di.ApplicationScope
import com.klikacz.clicklog.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Attaches a rough location to stored clicks.
 *
 * Two passes per click: the freshest *last known* location is written immediately (if any
 * provider has one), then a low-power current-location request runs and overwrites it when it
 * returns. Uses the platform [LocationManager] only (no Play Services). Background access relies
 * on the foreground service running with the `location` type, or on the optional
 * "allow all the time" permission.
 */
@Singleton
class LocationTagger @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val dao: ClickDao,
    private val config: ClickConfigProvider,
) {
    private val locationManager: LocationManager? by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }
    private val mainExecutor: Executor by lazy { ContextCompat.getMainExecutor(context) }

    fun tag(click: ClickEntity) {
        if (!config.current.saveLocation) return
        if (!hasPermission(context)) return
        val lm = locationManager ?: return
        scope.launch {
            runCatching {
                lastKnown(lm)?.let { write(click, it) }
                val fresh = withTimeoutOrNull(FRESH_FIX_TIMEOUT_MS) { current(lm) }
                if (fresh != null) write(click, fresh)
            }.onFailure { Timber.w(it, "location tagging failed for click %d", click.id) }
        }
    }

    private suspend fun write(click: ClickEntity, loc: Location) = withContext(io) {
        val fixWallMs = System.currentTimeMillis() - (android.os.SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000
        dao.updateLocation(
            id = click.id,
            lat = loc.latitude,
            lon = loc.longitude,
            accuracyM = if (loc.hasAccuracy()) loc.accuracy else null,
            ageMs = click.wallTimeMs - fixWallMs,
            provider = loc.provider,
        )
        Timber.d("click %d located: %.5f,%.5f ±%.0fm via %s", click.id, loc.latitude, loc.longitude, loc.accuracy, loc.provider)
    }

    // Permission is checked in tag(); SecurityException is caught by the caller's runCatching.
    @SuppressLint("MissingPermission")
    private fun lastKnown(lm: LocationManager): Location? = PROVIDERS
        .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
        .maxByOrNull { it.elapsedRealtimeNanos }

    @SuppressLint("MissingPermission")
    private suspend fun current(lm: LocationManager): Location? {
        val provider = PROVIDERS.firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        val request = LocationRequest.Builder(0L)
            .setQuality(LocationRequest.QUALITY_LOW_POWER)
            .setDurationMillis(FRESH_FIX_TIMEOUT_MS)
            .build()
        return suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            lm.getCurrentLocation(provider, request, signal, mainExecutor) { loc ->
                if (cont.isActive) cont.resume(loc)
            }
        }
    }

    companion object {
        const val FRESH_FIX_TIMEOUT_MS = 30_000L

        /** Preference order: fused (coarsened by permission level), then network, then GPS. */
        private val PROVIDERS = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)

        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun hasBackgroundPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}
