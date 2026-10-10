package com.klikacz.clicklog.diagnostics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klikacz.clicklog.clicks.ClickSourceType
import com.klikacz.clicklog.data.DiagnosisResult
import com.klikacz.clicklog.data.DiagnosticChannel
import com.klikacz.clicklog.data.DiagnosticDao
import com.klikacz.clicklog.data.DiagnosticEntity
import com.klikacz.clicklog.data.Settings
import com.klikacz.clicklog.service.ClickAccessibilityService
import com.klikacz.clicklog.service.ClickLogService
import com.klikacz.clicklog.ui.SystemIntents
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

enum class WizardStep { SYSTEM, ACCESSIBILITY, BUTTON_TEST, SCREEN_OFF_TEST, RESULT }

data class WizardState(
    val step: WizardStep = WizardStep.SYSTEM,
    val accessibilityEnabled: Boolean = false,
    // Step 3
    val testRunning: Boolean = false,
    val testStartMs: Long? = null,
    val liveEvents: List<DiagnosticEntity> = emptyList(),
    val pressCount: Int = 0,
    val secondsLeft: Int = BUTTON_TEST_SECONDS,
    val decision: PathDecision? = null,
    // Step 4
    val screenOffRunning: Boolean = false,
    val screenOffStartMs: Long? = null,
    val screenOffCount: Int = 0,
    /** How many of [screenOffCount] arrived as KeyEvents vs. app launches. */
    val screenOffKeyCount: Int = 0,
    val screenOffLaunchCount: Int = 0,
    val screenOffEvents: List<DiagnosticEntity> = emptyList(),
    val worksScreenOff: Boolean? = null,
    // Step 5
    val saved: Boolean = false,
) {
    val stepIndex: Int get() = step.ordinal + 1
    val stepCount: Int get() = WizardStep.entries.size

    companion object {
        const val BUTTON_TEST_SECONDS = 30
        const val BUTTON_TEST_PRESSES = 3
    }
}

/** Drives the first-run wizard (spec §3.1). */
@HiltViewModel
class SetupWizardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: Settings,
    private val dao: DiagnosticDao,
    private val logger: DiagnosticLogger,
) : ViewModel() {

    private val _state = MutableStateFlow(WizardState())
    val state: StateFlow<WizardState> = _state.asStateFlow()

    private var testJobs = mutableListOf<Job>()
    private var capturing = false

    fun refresh() {
        _state.update { it.copy(accessibilityEnabled = ClickAccessibilityService.isEnabled(context)) }
    }

    fun goTo(step: WizardStep) {
        if (step == _state.value.step) return
        stopCapture()
        _state.update { it.copy(step = step) }
        refresh()
    }

    fun next() {
        val cur = _state.value.step
        val nxt = WizardStep.entries.getOrNull(cur.ordinal + 1) ?: return
        goTo(nxt)
    }

    fun back() {
        val cur = _state.value.step
        val prev = WizardStep.entries.getOrNull(cur.ordinal - 1) ?: return
        goTo(prev)
    }

    // ---- Step 3: button test ---------------------------------------------------------------

    fun startButtonTest() {
        stopCapture()
        val start = System.currentTimeMillis()
        logger.note("Button test start")
        beginCapture()
        _state.update {
            it.copy(
                testRunning = true, testStartMs = start, liveEvents = emptyList(), pressCount = 0,
                secondsLeft = WizardState.BUTTON_TEST_SECONDS, decision = null,
            )
        }
        testJobs += viewModelScope.launch {
            dao.observeSince(start).collect { events ->
                val relevant = events.filter { it.channel != DiagnosticChannel.APP }
                val presses = PathSelector.pressCount(relevant)
                _state.update { it.copy(liveEvents = relevant.asReversed(), pressCount = presses) }
                if (presses >= WizardState.BUTTON_TEST_PRESSES) finishButtonTest(relevant)
            }
        }
        testJobs += viewModelScope.launch {
            for (left in WizardState.BUTTON_TEST_SECONDS downTo 1) {
                _state.update { it.copy(secondsLeft = left) }
                delay(1_000)
            }
            _state.update { it.copy(secondsLeft = 0) }
            finishButtonTest(_state.value.liveEvents)
        }
    }

    private fun finishButtonTest(events: List<DiagnosticEntity>) {
        if (!_state.value.testRunning) return
        val decision = PathSelector.select(events)
        Timber.i("Wizard decision: %s", decision)
        logger.note("Button test end: ${decision.reason} path=${decision.path} keycode=${decision.keycode}")
        stopCapture()
        _state.update { it.copy(testRunning = false, decision = decision) }
    }

    // ---- Step 4: screen-off test -----------------------------------------------------------

    fun startScreenOffTest() {
        stopCapture()
        val start = System.currentTimeMillis()
        logger.note("Screen-off test start")
        beginCapture()
        _state.update {
            it.copy(
                screenOffRunning = true, screenOffStartMs = start, screenOffCount = 0,
                screenOffKeyCount = 0, screenOffLaunchCount = 0, screenOffEvents = emptyList(), worksScreenOff = null,
            )
        }
        val keycode = _state.value.decision?.keycode
        testJobs += viewModelScope.launch {
            dao.observeSince(start).collect { events ->
                val relevant = events.filter { it.channel != DiagnosticChannel.APP }
                val keys = PathSelector.screenOffKeyCount(relevant, keycode)
                val launches = PathSelector.screenOffLaunchCount(relevant)
                _state.update {
                    it.copy(
                        screenOffEvents = relevant.asReversed(),
                        screenOffCount = keys + launches,
                        screenOffKeyCount = keys,
                        screenOffLaunchCount = launches,
                    )
                }
            }
        }
    }

    fun finishScreenOffTest() {
        val count = _state.value.screenOffCount
        logger.note("Screen-off test end: $count")
        stopCapture()
        _state.update { it.copy(screenOffRunning = false, worksScreenOff = count > 0) }
    }

    // ---- Step 5: result --------------------------------------------------------------------

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        val decision = s.decision
        viewModelScope.launch {
            val referrer = decision?.let { mainLaunchReferrer(s.liveEvents) }
            settings.saveDiagnosis(
                DiagnosisResult(
                    path = decision?.path,
                    keycode = decision?.keycode,
                    scancode = decision?.scancode,
                    worksScreenOff = s.worksScreenOff,
                    completedAtMs = System.currentTimeMillis(),
                    mainLaunchReferrer = referrer,
                ),
            )
            settings.setWizardCompleted(true)
            if (decision?.path != null) {
                settings.setServiceEnabled(true)
                ClickLogService.start(context)
            }
            _state.update { it.copy(saved = true) }
            onSaved()
        }
    }

    /**
     * If the key launched MainActivity during the test, remember the referrer so that such
     * launches count as clicks on path B. The home launcher's own package is never used, so
     * tapping the icon is not mistaken for a press.
     */
    private fun mainLaunchReferrer(events: List<DiagnosticEntity>): String? {
        val home = SystemIntents.homeLauncherPackage(context)
        val referrers = events
            .filter { it.channel == DiagnosticChannel.MAIN_INTENT }
            .mapNotNull { e ->
                val json = e.extrasJson ?: return@mapNotNull null
                runCatching { JSONObject(json).optString("referrer") }.getOrNull()?.ifBlank { null }
            }
            .filter { home == null || !it.contains(home) }
        return referrers.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    // ---- helpers ---------------------------------------------------------------------------

    private fun beginCapture() {
        if (!capturing) {
            logger.beginCapture()
            capturing = true
        }
    }

    private fun stopCapture() {
        testJobs.forEach { it.cancel() }
        testJobs.clear()
        if (capturing) {
            logger.endCapture()
            capturing = false
        }
        _state.update { it.copy(testRunning = false, screenOffRunning = false) }
    }

    override fun onCleared() {
        stopCapture()
        super.onCleared()
    }
}
