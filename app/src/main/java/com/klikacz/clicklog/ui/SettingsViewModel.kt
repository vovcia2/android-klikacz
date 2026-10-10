package com.klikacz.clicklog.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klikacz.clicklog.clicks.ClickPipeline
import com.klikacz.clicklog.data.AppSettings
import com.klikacz.clicklog.data.PathMode
import com.klikacz.clicklog.data.PressGesture
import com.klikacz.clicklog.data.Settings
import com.klikacz.clicklog.export.CsvExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface SettingsEvent {
    data class Message(val text: String) : SettingsEvent
    data class Share(val intent: Intent) : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: Settings,
    private val exporter: CsvExporter,
    private val pipeline: ClickPipeline,
) : ViewModel() {

    val state: StateFlow<AppSettings?> = settings.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    fun newSession() = viewModelScope.launch {
        settings.newSession()
        pipeline.processor.reset()
    }

    fun setDebounce(ms: Int) = viewModelScope.launch { settings.setDebounceMs(ms) }
    fun setCrossSourceMerge(ms: Int) = viewModelScope.launch { settings.setCrossSourceMergeMs(ms) }
    fun setPathMode(mode: PathMode) = viewModelScope.launch { settings.setPathMode(mode) }
    fun setPressGesture(g: PressGesture) = viewModelScope.launch { settings.setPressGesture(g) }
    fun setDiagnosticsMode(on: Boolean) = viewModelScope.launch { settings.setDiagnosticsMode(on) }
    fun setVibrate(on: Boolean) = viewModelScope.launch { settings.setVibrateOnClick(on) }

    /** Export to a document picked with `ACTION_CREATE_DOCUMENT`. */
    fun exportCsvTo(uri: Uri, allSessions: Boolean) = viewModelScope.launch {
        runCatching {
            exporter.writeTo(uri, exporter.clicksCsv(currentSessionOnly = !allSessions))
        }.onSuccess {
            _events.tryEmit(SettingsEvent.Message(context.getString(com.klikacz.clicklog.R.string.settings_exported)))
        }.onFailure {
            Timber.e(it, "CSV export failed")
            _events.tryEmit(SettingsEvent.Message(context.getString(com.klikacz.clicklog.R.string.settings_export_failed, it.message ?: "?")))
        }
    }

    /** Export through the share sheet. */
    fun shareCsv(allSessions: Boolean) = viewModelScope.launch {
        runCatching {
            exporter.shareIntent(CsvExporter.CLICKS_FILE, CsvExporter.CSV_MIME, exporter.clicksCsv(currentSessionOnly = !allSessions))
        }.onSuccess { _events.tryEmit(SettingsEvent.Share(it)) }
            .onFailure {
                Timber.e(it, "CSV share failed")
                _events.tryEmit(SettingsEvent.Message(context.getString(com.klikacz.clicklog.R.string.settings_export_failed, it.message ?: "?")))
            }
    }
}
