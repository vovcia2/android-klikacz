package com.klikacz.clicklog.ui

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klikacz.clicklog.BuildConfig
import com.klikacz.clicklog.R
import com.klikacz.clicklog.data.AppSettings
import com.klikacz.clicklog.data.PathMode
import com.klikacz.clicklog.data.PressGesture
import com.klikacz.clicklog.export.CsvExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onWizard: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmNewSession by remember { mutableStateOf(false) }
    var exportAll by remember { mutableStateOf<Boolean?>(null) }

    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val requestBackgroundLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CsvExporter.CSV_MIME)) { uri ->
        val all = exportAll ?: false
        if (uri != null) viewModel.exportCsvTo(uri, all)
        exportAll = null
    }

    val shareTitle = stringResource(R.string.share)
    LaunchedEffect(Unit) {
        viewModel.events.collect { ev ->
            when (ev) {
                is SettingsEvent.Message -> snackbar.showSnackbar(ev.text)
                is SettingsEvent.Share -> context.startActivity(Intent.createChooser(ev.intent, shareTitle))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val s = settings ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = stringResource(R.string.main_session_id) + " " + s.sessionId) {
                Button(onClick = { confirmNewSession = true }) { Text(stringResource(R.string.settings_new_session)) }
            }

            SectionCard(title = stringResource(R.string.settings_export_csv)) {
                ExportRow(label = stringResource(R.string.settings_export_csv), onSave = { exportAll = false; createDocument.launch(CsvExporter.CLICKS_FILE) }, onShare = { viewModel.shareCsv(false) })
                ExportRow(label = stringResource(R.string.settings_export_csv_all), onSave = { exportAll = true; createDocument.launch(CsvExporter.CLICKS_FILE) }, onShare = { viewModel.shareCsv(true) })
            }

            SectionCard(title = stringResource(R.string.settings_gesture)) {
                PathModeOption(stringResource(R.string.settings_gesture_single), s.pressGesture == PressGesture.SINGLE) { viewModel.setPressGesture(PressGesture.SINGLE) }
                PathModeOption(stringResource(R.string.settings_gesture_double), s.pressGesture == PressGesture.DOUBLE) { viewModel.setPressGesture(PressGesture.DOUBLE) }
                Text(stringResource(R.string.settings_gesture_hint), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }

            SectionCard(title = stringResource(R.string.settings_debounce)) {
                DebounceField(current = s.debounceMs, onChange = viewModel::setDebounce)
                Text(stringResource(R.string.settings_debounce_hint), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }

            SectionCard(title = stringResource(R.string.settings_merge)) {
                DebounceField(current = s.crossSourceMergeMs, onChange = viewModel::setCrossSourceMerge)
                Text(stringResource(R.string.settings_merge_hint), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }

            SectionCard(title = stringResource(R.string.settings_path)) {
                PathModeOption(stringResource(R.string.path_mode_auto), s.pathMode == PathMode.AUTO) { viewModel.setPathMode(PathMode.AUTO) }
                PathModeOption(stringResource(R.string.path_a), s.pathMode == PathMode.KEY_EVENT) { viewModel.setPathMode(PathMode.KEY_EVENT) }
                PathModeOption(stringResource(R.string.path_b), s.pathMode == PathMode.LAUNCH_INTENT) { viewModel.setPathMode(PathMode.LAUNCH_INTENT) }
                Text(stringResource(R.string.path_launch_note), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }

            SectionCard {
                SwitchRow(stringResource(R.string.settings_vibrate), s.vibrateOnClick, viewModel::setVibrate)
                SwitchRow(stringResource(R.string.settings_diagnostics_mode), s.diagnosticsMode, viewModel::setDiagnosticsMode)
            }

            SectionCard(title = stringResource(R.string.settings_location)) {
                SwitchRow(stringResource(R.string.settings_location_save), s.saveLocation, viewModel::setSaveLocation)
                Text(stringResource(R.string.settings_location_hint), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    requestLocation.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_location_permission)) }
                OutlinedButton(onClick = {
                    requestBackgroundLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_location_background)) }
            }

            SectionCard {
                OutlinedButton(onClick = { context.startActivity(SystemIntents.requestIgnoreBatteryOptimizations(context)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_battery))
                }
                OutlinedButton(onClick = { context.startActivity(SystemIntents.accessibilitySettings()) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_open_accessibility))
                }
                OutlinedButton(onClick = { context.startActivity(SystemIntents.programmableKeySettings(context)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_open_system))
                }
                Button(onClick = onWizard, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_run_wizard)) }
                Button(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_diagnostics)) }
            }

            Text(
                "ClickLog ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.GIT_SHA}",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmNewSession) {
        AlertDialog(
            onDismissRequest = { confirmNewSession = false },
            title = { Text(stringResource(R.string.settings_new_session)) },
            text = { Text(stringResource(R.string.settings_new_session_confirm)) },
            confirmButton = { TextButton(onClick = { viewModel.newSession(); confirmNewSession = false }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { confirmNewSession = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ExportRow(label: String, onSave: () -> Unit, onShare: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSave) { Text(stringResource(R.string.save_file)) }
            OutlinedButton(onClick = onShare) { Text(stringResource(R.string.share)) }
        }
    }
}

@Composable
private fun DebounceField(current: Int, onChange: (Int) -> Unit) {
    var text by remember(current) { mutableStateOf(current.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter(Char::isDigit).take(4) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f),
            label = { Text("ms (${AppSettings.MIN_DEBOUNCE_MS}–${AppSettings.MAX_DEBOUNCE_MS})") },
        )
        Button(onClick = { text.toIntOrNull()?.let(onChange) }, enabled = text.toIntOrNull() != null && text.toInt() != current) {
            Text(stringResource(R.string.ok))
        }
    }
}

@Composable
private fun PathModeOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
