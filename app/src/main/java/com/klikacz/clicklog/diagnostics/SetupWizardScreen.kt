package com.klikacz.clicklog.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klikacz.clicklog.R
import com.klikacz.clicklog.clicks.ClickSourceType
import com.klikacz.clicklog.data.DiagnosticEntity
import com.klikacz.clicklog.ui.DiagnosticRow
import com.klikacz.clicklog.ui.SectionCard
import com.klikacz.clicklog.ui.SystemIntents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(viewModel: SetupWizardViewModel, onFinished: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text(stringResource(R.string.wizard_title))
                    Text(stringResource(R.string.wizard_step, state.stepIndex, state.stepCount), style = MaterialTheme.typography.labelMedium)
                }
            })
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LinearProgressIndicator(progress = { state.stepIndex / state.stepCount.toFloat() }, modifier = Modifier.fillMaxWidth())

            when (state.step) {
                WizardStep.SYSTEM -> StepSystem(onOpen = { context.startActivity(SystemIntents.programmableKeySettings(context)) })
                WizardStep.ACCESSIBILITY -> StepAccessibility(state.accessibilityEnabled, onOpen = { context.startActivity(SystemIntents.accessibilitySettings()) })
                WizardStep.BUTTON_TEST -> StepButtonTest(state, onStart = viewModel::startButtonTest)
                WizardStep.SCREEN_OFF_TEST -> StepScreenOff(state, onStart = viewModel::startScreenOffTest, onFinish = viewModel::finishScreenOffTest)
                WizardStep.RESULT -> StepResult(state)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (state.step != WizardStep.SYSTEM) {
                    OutlinedButton(onClick = viewModel::back) { Text(stringResource(R.string.back)) }
                } else {
                    TextButton(onClick = { viewModel.save(onFinished) }) { Text(stringResource(R.string.skip)) }
                }
                when (state.step) {
                    WizardStep.BUTTON_TEST -> Button(onClick = viewModel::next, enabled = state.decision != null) { Text(stringResource(R.string.next)) }
                    WizardStep.SCREEN_OFF_TEST -> Button(onClick = viewModel::next, enabled = !state.screenOffRunning) {
                        Text(stringResource(if (state.decision?.path == null) R.string.wizard_step4_skip_no_path else R.string.next))
                    }
                    WizardStep.RESULT -> Button(onClick = { viewModel.save(onFinished) }, enabled = !state.saved) {
                        Text(stringResource(if (state.decision?.path != null) R.string.wizard_result_save else R.string.wizard_result_save_nopath))
                    }
                    else -> Button(onClick = viewModel::next) { Text(stringResource(R.string.next)) }
                }
            }
        }
    }
}

@Composable
private fun StepSystem(onOpen: () -> Unit) {
    SectionCard(title = stringResource(R.string.wizard_step1_title)) {
        Text(stringResource(R.string.wizard_step1_text))
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wizard_step1_open_settings)) }
    }
}

@Composable
private fun StepAccessibility(enabled: Boolean, onOpen: () -> Unit) {
    SectionCard(title = stringResource(R.string.wizard_step2_title)) {
        Text(stringResource(R.string.wizard_step2_text))
        Text(
            stringResource(if (enabled) R.string.wizard_step2_status_on else R.string.wizard_step2_status_off),
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium,
        )
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wizard_step2_open)) }
        if (!enabled) {
            Text(stringResource(R.string.wizard_step2_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StepButtonTest(state: WizardState, onStart: () -> Unit) {
    SectionCard(title = stringResource(R.string.wizard_step3_title)) {
        Text(stringResource(R.string.wizard_step3_text))
        if (state.testStartMs == null) {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wizard_step3_start)) }
        } else {
            Text(stringResource(R.string.wizard_step3_presses, state.pressCount), style = MaterialTheme.typography.titleLarge)
            if (state.testRunning) {
                Text(stringResource(R.string.wizard_step3_time_left, state.secondsLeft))
            }
            val decision = state.decision
            if (decision != null) {
                HorizontalDivider()
                Text(stringResource(R.string.wizard_step3_summary), style = MaterialTheme.typography.titleMedium)
                DecisionSummary(decision, state.accessibilityEnabled)
                OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.retry)) }
            }
        }
    }
    if (state.testStartMs != null) {
        LiveEvents(title = stringResource(R.string.wizard_step3_live), events = state.liveEvents)
    }
}

@Composable
private fun StepScreenOff(state: WizardState, onStart: () -> Unit, onFinish: () -> Unit) {
    SectionCard(title = stringResource(R.string.wizard_step4_title)) {
        Text(stringResource(R.string.wizard_step4_text))
        if (state.decision?.path == ClickSourceType.LAUNCH_INTENT) {
            Text(stringResource(R.string.wizard_result_hint_b), style = MaterialTheme.typography.bodySmall)
        }
        if (state.screenOffStartMs == null) {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth(), enabled = state.decision?.path != null) {
                Text(stringResource(R.string.wizard_step4_start))
            }
        } else {
            Text(stringResource(R.string.wizard_step4_count, state.screenOffCount), style = MaterialTheme.typography.titleLarge)
            if (state.screenOffCount > 0) {
                Text(stringResource(R.string.wizard_step4_breakdown, state.screenOffKeyCount, state.screenOffLaunchCount), style = MaterialTheme.typography.bodySmall)
            }
            if (state.screenOffRunning) {
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.finish)) }
            } else {
                OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.retry)) }
            }
        }
    }
    if (state.screenOffStartMs != null) {
        LiveEvents(title = stringResource(R.string.wizard_step3_live), events = state.screenOffEvents)
    }
}

@Composable
private fun StepResult(state: WizardState) {
    val decision = state.decision
    SectionCard(title = stringResource(R.string.wizard_step5_title)) {
        val pathText = when (decision?.path) {
            ClickSourceType.KEY_EVENT -> stringResource(R.string.path_a)
            ClickSourceType.LAUNCH_INTENT -> stringResource(R.string.path_b)
            null -> stringResource(R.string.path_none)
        }
        Text(stringResource(R.string.wizard_result_path, pathText), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(
                R.string.wizard_result_keycode,
                decision?.keycode?.toString() ?: stringResource(R.string.unknown),
                decision?.scancode?.toString() ?: stringResource(R.string.unknown),
            ),
        )
        Text(
            stringResource(
                R.string.wizard_result_screen_off,
                when (state.worksScreenOff) {
                    true -> stringResource(R.string.yes)
                    false -> stringResource(R.string.no)
                    null -> stringResource(R.string.wizard_result_screen_off_untested)
                },
            ),
        )
        if (state.worksScreenOff == true) {
            Text(stringResource(R.string.wizard_step4_breakdown, state.screenOffKeyCount, state.screenOffLaunchCount), style = MaterialTheme.typography.bodySmall)
            if (state.screenOffLaunchCount > 0 && state.screenOffKeyCount == 0) {
                Text(stringResource(R.string.wizard_result_screen_off_via_launch), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (decision != null) DecisionSummary(decision, state.accessibilityEnabled)
        if (decision?.path == ClickSourceType.LAUNCH_INTENT) {
            Text(stringResource(R.string.wizard_result_hint_b), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DecisionSummary(decision: PathDecision, accessibilityEnabled: Boolean) {
    Text(
        stringResource(
            R.string.wizard_summary_counts,
            decision.accessibilityDownCount, decision.activityDownCount, decision.launchIntentCount,
            decision.vendorBroadcastCount, decision.mediaButtonCount,
        ),
        style = MaterialTheme.typography.bodySmall,
    )
    when (decision.reason) {
        PathReason.ACCESSIBILITY_KEY -> Text(stringResource(R.string.wizard_reason_accessibility, decision.keycode ?: -1))
        PathReason.ACTIVITY_ONLY -> Text(
            stringResource(
                if (accessibilityEnabled) R.string.wizard_reason_activity_only else R.string.wizard_reason_activity_only_disabled,
                decision.keycode ?: -1,
            ),
            color = MaterialTheme.colorScheme.error,
        )
        PathReason.LAUNCH_INTENT -> Text(stringResource(R.string.wizard_reason_launch))
        PathReason.NOT_DETECTED -> {
            Text(stringResource(R.string.wizard_reason_none), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.wizard_checklist_title))
            Text(stringResource(R.string.wizard_checklist_assigned))
            Text(stringResource(R.string.wizard_checklist_sos))
            Text(stringResource(R.string.wizard_checklist_accessibility))
        }
    }
}

@Composable
private fun LiveEvents(title: String, events: List<DiagnosticEntity>) {
    SectionCard(title = title) {
        if (events.isEmpty()) {
            Text(stringResource(R.string.wizard_step3_nothing_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                items(events, key = { it.id }) { e ->
                    DiagnosticRow(e, screenOffLabel = stringResource(R.string.diag_screen_off_tag))
                    HorizontalDivider()
                }
            }
        }
    }
}
