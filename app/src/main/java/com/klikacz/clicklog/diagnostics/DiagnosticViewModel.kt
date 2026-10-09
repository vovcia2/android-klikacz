package com.klikacz.clicklog.diagnostics

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klikacz.clicklog.R
import com.klikacz.clicklog.data.DiagnosticDao
import com.klikacz.clicklog.data.DiagnosticEntity
import com.klikacz.clicklog.export.CsvExporter
import com.klikacz.clicklog.ui.SettingsEvent
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

/** Live view of `diagnostic_log` with export and clear (spec §3.4). */
@HiltViewModel
class DiagnosticViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DiagnosticDao,
    private val logger: DiagnosticLogger,
    private val exporter: CsvExporter,
) : ViewModel() {

    val events: StateFlow<List<DiagnosticEntity>> =
        dao.observeLatest(500).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val count: StateFlow<Int> = dao.observeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _uiEvents = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 8)
    val uiEvents: SharedFlow<SettingsEvent> = _uiEvents.asSharedFlow()

    private var capturing = false

    fun startCapture() {
        if (!capturing) {
            logger.beginCapture(); capturing = true
        }
    }

    fun stopCapture() {
        if (capturing) {
            logger.endCapture(); capturing = false
        }
    }

    fun clear() = viewModelScope.launch { dao.clear() }

    fun share() = viewModelScope.launch {
        runCatching {
            val text = exporter.diagnosticLogText()
            exporter.shareIntent(CsvExporter.DIAG_FILE, CsvExporter.TEXT_MIME, text)
        }.onSuccess { _uiEvents.tryEmit(SettingsEvent.Share(it)) }
            .onFailure {
                Timber.e(it, "diagnostic export failed")
                _uiEvents.tryEmit(SettingsEvent.Message(context.getString(R.string.settings_export_failed, it.message ?: "?")))
            }
    }

    fun shareIntentChooser(intent: Intent): Intent = Intent.createChooser(intent, context.getString(R.string.share))

    override fun onCleared() {
        stopCapture()
        super.onCleared()
    }
}
