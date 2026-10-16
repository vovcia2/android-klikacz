package com.klikacz.clicklog.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klikacz.clicklog.R
import com.klikacz.clicklog.clicks.ClickSourceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onEvents: () -> Unit,
    onSettings: () -> Unit,
    onWizard: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refreshStatus()
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshStatus()
        onPauseOrDispose { }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.main_title)) }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val settings = state.settings
            SectionCard {
                Text(stringResource(R.string.main_session_count), style = MaterialTheme.typography.labelLarge)
                Text(state.count.toString(), style = MaterialTheme.typography.displayLarge)
                Text(stringResource(R.string.main_last_click), style = MaterialTheme.typography.labelLarge)
                val last = state.lastClick
                Text(
                    if (last == null) stringResource(R.string.main_no_clicks)
                    else TimeFormat.dateTimeWithMillis(last.wallTimeMs) + "  (" + last.source.letter + ")",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
                if (settings != null) {
                    Text(
                        stringResource(R.string.main_session_id) + ": " + settings.sessionId,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            SectionCard(title = stringResource(R.string.main_service_status)) {
                StatusRow(
                    stringResource(R.string.main_service_status),
                    stringResource(if (state.serviceRunning) R.string.main_service_running else R.string.main_service_stopped),
                    ok = state.serviceRunning,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.serviceRunning) {
                        OutlinedButton(onClick = { viewModel.setServiceEnabled(false) }) { Text(stringResource(R.string.main_stop_service)) }
                    } else {
                        Button(onClick = { viewModel.setServiceEnabled(true) }) { Text(stringResource(R.string.main_start_service)) }
                    }
                }
            }

            SectionCard(title = stringResource(R.string.main_active_path)) {
                val path = settings?.effectivePath
                StatusRow(
                    stringResource(R.string.main_active_path),
                    when (path) {
                        ClickSourceType.KEY_EVENT -> stringResource(R.string.path_a)
                        ClickSourceType.LAUNCH_INTENT -> stringResource(R.string.path_b)
                        ClickSourceType.VENDOR_BROADCAST -> stringResource(R.string.path_c)
                        null -> stringResource(R.string.path_none)
                    },
                    ok = path != null,
                )
                if (path == ClickSourceType.KEY_EVENT) {
                    StatusRow(stringResource(R.string.main_keycode), settings?.activeKeycode?.toString() ?: stringResource(R.string.unknown))
                }
                settings?.diagnosis?.worksScreenOff?.let {
                    StatusRow(stringResource(R.string.main_screen_off), stringResource(if (it) R.string.yes else R.string.no), ok = it)
                }
                if (settings != null && !settings.wizardCompleted) {
                    Text(stringResource(R.string.main_wizard_not_done), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onWizard) { Text(stringResource(R.string.main_wizard)) }
            }

            SectionCard(title = stringResource(R.string.main_permissions)) {
                val status = state.status
                StatusRow(
                    stringResource(R.string.main_accessibility),
                    when {
                        state.accessibilityConnected -> stringResource(R.string.main_accessibility_connected)
                        status?.accessibilityEnabled == true -> stringResource(R.string.main_accessibility_enabled)
                        else -> stringResource(R.string.main_accessibility_disabled)
                    },
                    ok = status?.accessibilityEnabled == true || state.accessibilityConnected,
                )
                if (status?.accessibilityEnabled == false) {
                    TextButton(onClick = { context.startActivity(SystemIntents.accessibilitySettings()) }) { Text(stringResource(R.string.main_fix)) }
                }
                StatusRow(
                    stringResource(R.string.main_notifications),
                    stringResource(if (status?.notificationsGranted != false) R.string.main_granted else R.string.main_denied),
                    ok = status?.notificationsGranted != false,
                )
                StatusRow(
                    stringResource(R.string.main_battery),
                    stringResource(if (status?.ignoringBatteryOptimizations == true) R.string.main_battery_ignored else R.string.main_battery_active),
                    ok = status?.ignoringBatteryOptimizations == true,
                )
                if (status?.ignoringBatteryOptimizations == false) {
                    TextButton(onClick = { context.startActivity(SystemIntents.requestIgnoreBatteryOptimizations(context)) }) { Text(stringResource(R.string.main_fix)) }
                }
                StatusRow(
                    stringResource(R.string.main_location),
                    stringResource(
                        when {
                            status?.backgroundLocationGranted == true -> R.string.main_location_background
                            status?.locationGranted == true -> R.string.main_location_foreground
                            else -> R.string.main_denied
                        },
                    ),
                    ok = status?.locationGranted == true,
                )
                if (status?.locationGranted == false) {
                    TextButton(onClick = {
                        requestLocation.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                    }) { Text(stringResource(R.string.main_fix)) }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEvents, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.main_events)) }
                OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.main_settings)) }
            }
        }
    }
}

