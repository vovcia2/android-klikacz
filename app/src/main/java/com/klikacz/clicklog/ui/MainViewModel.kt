package com.klikacz.clicklog.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klikacz.clicklog.clicks.ClickPipeline
import com.klikacz.clicklog.clicks.ClickRepository
import com.klikacz.clicklog.data.AppSettings
import com.klikacz.clicklog.data.ClickEntity
import com.klikacz.clicklog.data.Settings
import com.klikacz.clicklog.service.ClickAccessibilityService
import com.klikacz.clicklog.service.ClickLogService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val settings: AppSettings? = null,
    val count: Int = 0,
    val lastClick: ClickEntity? = null,
    val serviceRunning: Boolean = false,
    val accessibilityConnected: Boolean = false,
    val status: SystemStatus? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: Settings,
    private val repository: ClickRepository,
    private val pipeline: ClickPipeline,
) : ViewModel() {

    private val status = MutableStateFlow<SystemStatus?>(null)

    val state: StateFlow<MainUiState> = combine(
        settings.flow,
        repository.sessionCount,
        repository.lastClick,
        ClickLogService.isRunning,
        ClickAccessibilityService.isConnected,
        status,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        MainUiState(
            settings = values[0] as AppSettings,
            count = values[1] as Int,
            lastClick = values[2] as ClickEntity?,
            serviceRunning = values[3] as Boolean,
            accessibilityConnected = values[4] as Boolean,
            status = values[5] as SystemStatus?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun refreshStatus() {
        status.value = SystemStatus.check(context)
    }

    fun setServiceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setServiceEnabled(enabled)
            if (enabled) ClickLogService.start(context) else ClickLogService.stop(context)
        }
    }

    fun newSession() {
        viewModelScope.launch {
            settings.newSession()
            pipeline.processor.reset()
        }
    }
}
