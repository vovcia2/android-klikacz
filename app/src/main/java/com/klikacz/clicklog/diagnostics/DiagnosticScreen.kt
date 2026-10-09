package com.klikacz.clicklog.diagnostics

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klikacz.clicklog.R
import com.klikacz.clicklog.ui.DiagnosticRow
import com.klikacz.clicklog.ui.SettingsEvent

/** Always-available diagnostics screen (spec §3.4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticScreen(viewModel: DiagnosticViewModel, onBack: () -> Unit) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val count by viewModel.count.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.startCapture()
        onPauseOrDispose { viewModel.stopCapture() }
    }

    val shareTitle = stringResource(R.string.share)
    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { ev ->
            when (ev) {
                is SettingsEvent.Message -> snackbar.showSnackbar(ev.text)
                is SettingsEvent.Share -> context.startActivity(Intent.createChooser(ev.intent, shareTitle))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diag_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = viewModel::share) { Icon(Icons.Default.Share, contentDescription = stringResource(R.string.diag_export)) }
                    IconButton(onClick = { confirmClear = true }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.diag_clear)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(stringResource(R.string.diag_capture_on), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.diag_count, count), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium)
            if (events.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.diag_empty)) }
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(events, key = { it.id }) { e ->
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            DiagnosticRow(e, screenOffLabel = stringResource(R.string.diag_screen_off_tag))
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.diag_clear)) },
            text = { Text(stringResource(R.string.diag_clear_confirm)) },
            confirmButton = { TextButton(onClick = { viewModel.clear(); confirmClear = false }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
