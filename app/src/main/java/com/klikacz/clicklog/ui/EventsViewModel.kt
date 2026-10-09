package com.klikacz.clicklog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klikacz.clicklog.clicks.ClickRepository
import com.klikacz.clicklog.data.ClickEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class EventsViewModel @Inject constructor(repository: ClickRepository) : ViewModel() {
    val clicks: StateFlow<List<ClickEntity>> =
        repository.sessionClicks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
