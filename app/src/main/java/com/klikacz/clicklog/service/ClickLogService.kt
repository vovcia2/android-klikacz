package com.klikacz.clicklog.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.klikacz.clicklog.MainActivity
import com.klikacz.clicklog.R
import com.klikacz.clicklog.clicks.ClickRepository
import com.klikacz.clicklog.data.DiagnosticChannel
import com.klikacz.clicklog.data.Settings
import com.klikacz.clicklog.diagnostics.DiagnosticLogger
import com.klikacz.clicklog.location.LocationTagger
import com.klikacz.clicklog.ui.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground service with a persistent notification showing the click counter (spec §5).
 *
 * It keeps the process alive so the singletons (click pipeline, Room) are always ready, hosts a
 * [MediaSessionCompat] so media-button mappings of the SOS key are observed during diagnostics,
 * and exposes [isRunning] for the UI.
 */
@AndroidEntryPoint
class ClickLogService : LifecycleService() {

    @Inject lateinit var repository: ClickRepository
    @Inject lateinit var settings: Settings
    @Inject lateinit var logger: DiagnosticLogger

    private var mediaSession: MediaSessionCompat? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground(buildNotification(0, null))
        _isRunning.value = true
        setUpMediaSession()
        lifecycleScope.launch {
            combine(repository.sessionCount, repository.lastClick) { count, last -> count to last }
                .collect { (count, last) ->
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(count, last?.wallTimeMs))
                }
        }
        Timber.i("ClickLogService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                lifecycleScope.launch { settings.setServiceEnabled(false) }
                stopSelf()
                return START_NOT_STICKY
            }
            Intent.ACTION_MEDIA_BUTTON -> mediaSession?.let { MediaButtonReceiver.handleIntent(it, intent) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        _isRunning.value = false
        mediaSession?.release()
        mediaSession = null
        Timber.i("ClickLogService destroyed")
        super.onDestroy()
    }

    // SPECIAL_USE is an inlined constant; ServiceCompat masks it on API < 34. The LOCATION type
    // lets clicks be geotagged while the app is in the background; it may only be declared when
    // the location permission is granted, otherwise startForeground throws on API 34+.
    @SuppressLint("InlinedApi")
    private fun startInForeground(notification: Notification) {
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        if (LocationTagger.hasPermission(this)) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun setUpMediaSession() {
        val session = MediaSessionCompat(this, "ClickLog").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                    val key: KeyEvent? = IntentCompat.getParcelableExtra(mediaButtonEvent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    if (key != null) {
                        logger.logKeyEvent(DiagnosticChannel.MEDIA_BUTTON, key, mapOf("intentAction" to mediaButtonEvent.action))
                    } else {
                        logger.logIntent(DiagnosticChannel.MEDIA_BUTTON, mediaButtonEvent)
                    }
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }
            })
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE)
                    .setState(PlaybackStateCompat.STATE_PAUSED, 0, 0f)
                    .build(),
            )
            isActive = true
        }
        mediaSession = session
    }

    private val notificationManager: NotificationManager
        get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.notification_channel_description) }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(count: Int, lastWallMs: Long?): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ClickLogService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (lastWallMs == null) getString(R.string.notification_no_clicks)
        else getString(R.string.notification_last_click, TimeFormat.timeWithMillis(lastWallMs))
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_click)
            .setContentTitle(getString(R.string.notification_title, count))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notification_action_stop), stopIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "clicklog_service"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.klikacz.clicklog.action.STOP_SERVICE"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ClickLogService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ClickLogService::class.java).setAction(ACTION_STOP))
        }
    }
}
