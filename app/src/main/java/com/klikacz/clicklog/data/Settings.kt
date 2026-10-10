package com.klikacz.clicklog.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.klikacz.clicklog.clicks.ClickSourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** How the active capture path is chosen (spec §2.2 "wybór ścieżki AUTO / A / B"). */
enum class PathMode { AUTO, KEY_EVENT, LAUNCH_INTENT }

/**
 * Which key gesture is assigned to ClickLog in the system's Programmable Key settings.
 * With DOUBLE, two KeyEvents inside [AppSettings.DOUBLE_PRESS_WINDOW_MS] count as one click,
 * matching the single app launch the vendor handler produces for a double press.
 */
enum class PressGesture { SINGLE, DOUBLE }

/** Outcome of the setup wizard, persisted in DataStore (spec §3.1 step 5). */
data class DiagnosisResult(
    val path: ClickSourceType?,
    val keycode: Int?,
    val scancode: Int?,
    val worksScreenOff: Boolean?,
    val completedAtMs: Long,
    /**
     * Referrer seen when the key launched MainActivity (rather than LaunchReceiverActivity)
     * during the wizard test. On path B a MainActivity launch with this referrer counts as a click.
     */
    val mainLaunchReferrer: String? = null,
)

data class AppSettings(
    val sessionId: Long,
    val debounceMs: Int,
    /** Window in which a KeyEvent click and an app-launch click count as one press. */
    val crossSourceMergeMs: Int,
    val pressGesture: PressGesture,
    val pathMode: PathMode,
    val diagnosis: DiagnosisResult?,
    val wizardCompleted: Boolean,
    val serviceEnabled: Boolean,
    val diagnosticsMode: Boolean,
    val vibrateOnClick: Boolean,
    /** Attach a rough location to each click (requires the location permission). */
    val saveLocation: Boolean,
) {
    /** Path that is actually used for capturing clicks right now. */
    val effectivePath: ClickSourceType?
        get() = when (pathMode) {
            PathMode.AUTO -> diagnosis?.path
            PathMode.KEY_EVENT -> ClickSourceType.KEY_EVENT
            PathMode.LAUNCH_INTENT -> ClickSourceType.LAUNCH_INTENT
        }

    /** Window for the second KeyEvent of a double press; 0 in single-press mode. */
    val doublePressWindowMs: Int
        get() = if (pressGesture == PressGesture.DOUBLE) DOUBLE_PRESS_WINDOW_MS else 0

    /** Keycode consumed by the accessibility service on path A, if known. */
    val activeKeycode: Int? get() = diagnosis?.keycode

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 150
        const val DEFAULT_CROSS_SOURCE_MERGE_MS = 600
        const val DOUBLE_PRESS_WINDOW_MS = 800
        const val MIN_DEBOUNCE_MS = 0
        const val MAX_DEBOUNCE_MS = 2000
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class Settings @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val SESSION_ID = longPreferencesKey("session_id")
        val DEBOUNCE_MS = intPreferencesKey("debounce_ms")
        val CROSS_SOURCE_MERGE_MS = intPreferencesKey("cross_source_merge_ms")
        val PATH_MODE = stringPreferencesKey("path_mode")
        val PRESS_GESTURE = stringPreferencesKey("press_gesture")
        val DIAG_PATH = stringPreferencesKey("diag_path")
        val DIAG_KEYCODE = intPreferencesKey("diag_keycode")
        val DIAG_SCANCODE = intPreferencesKey("diag_scancode")
        val DIAG_SCREEN_OFF = booleanPreferencesKey("diag_screen_off")
        val DIAG_COMPLETED_AT = longPreferencesKey("diag_completed_at")
        val DIAG_MAIN_REFERRER = stringPreferencesKey("diag_main_referrer")
        val WIZARD_COMPLETED = booleanPreferencesKey("wizard_completed")
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val DIAGNOSTICS_MODE = booleanPreferencesKey("diagnostics_mode")
        val VIBRATE = booleanPreferencesKey("vibrate_on_click")
        val SAVE_LOCATION = booleanPreferencesKey("save_location")
    }

    private val store get() = context.settingsDataStore

    val flow: Flow<AppSettings> = store.data.map { p -> p.toSettings() }

    suspend fun current(): AppSettings = flow.first()

    /** Creates a fresh session id. Called on first launch and from "Nowa sesja". */
    suspend fun newSession(): Long {
        val id = System.currentTimeMillis()
        store.edit { it[Keys.SESSION_ID] = id }
        return id
    }

    /** Makes sure a session exists; returns the active session id. */
    suspend fun ensureSession(): Long {
        val cur = current().sessionId
        return if (cur == 0L) newSession() else cur
    }

    suspend fun setDebounceMs(value: Int) {
        val clamped = value.coerceIn(AppSettings.MIN_DEBOUNCE_MS, AppSettings.MAX_DEBOUNCE_MS)
        store.edit { it[Keys.DEBOUNCE_MS] = clamped }
    }

    suspend fun setCrossSourceMergeMs(value: Int) {
        val clamped = value.coerceIn(AppSettings.MIN_DEBOUNCE_MS, AppSettings.MAX_DEBOUNCE_MS)
        store.edit { it[Keys.CROSS_SOURCE_MERGE_MS] = clamped }
    }

    suspend fun setPressGesture(gesture: PressGesture) = store.edit { it[Keys.PRESS_GESTURE] = gesture.name }

    suspend fun setPathMode(mode: PathMode) = store.edit { it[Keys.PATH_MODE] = mode.name }

    suspend fun setServiceEnabled(enabled: Boolean) = store.edit { it[Keys.SERVICE_ENABLED] = enabled }

    suspend fun setDiagnosticsMode(enabled: Boolean) = store.edit { it[Keys.DIAGNOSTICS_MODE] = enabled }

    suspend fun setSaveLocation(enabled: Boolean) = store.edit { it[Keys.SAVE_LOCATION] = enabled }

    suspend fun setVibrateOnClick(enabled: Boolean) = store.edit { it[Keys.VIBRATE] = enabled }

    suspend fun setWizardCompleted(completed: Boolean) = store.edit { it[Keys.WIZARD_COMPLETED] = completed }

    suspend fun saveDiagnosis(result: DiagnosisResult) {
        store.edit { p ->
            if (result.path != null) p[Keys.DIAG_PATH] = result.path.name else p.remove(Keys.DIAG_PATH)
            if (result.keycode != null) p[Keys.DIAG_KEYCODE] = result.keycode else p.remove(Keys.DIAG_KEYCODE)
            if (result.scancode != null) p[Keys.DIAG_SCANCODE] = result.scancode else p.remove(Keys.DIAG_SCANCODE)
            if (result.worksScreenOff != null) p[Keys.DIAG_SCREEN_OFF] = result.worksScreenOff else p.remove(Keys.DIAG_SCREEN_OFF)
            if (result.mainLaunchReferrer != null) p[Keys.DIAG_MAIN_REFERRER] = result.mainLaunchReferrer else p.remove(Keys.DIAG_MAIN_REFERRER)
            p[Keys.DIAG_COMPLETED_AT] = result.completedAtMs
            p[Keys.WIZARD_COMPLETED] = true
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val completedAt = this[Keys.DIAG_COMPLETED_AT]
        val diagnosis = if (completedAt != null) {
            DiagnosisResult(
                path = this[Keys.DIAG_PATH]?.let { runCatching { ClickSourceType.valueOf(it) }.getOrNull() },
                keycode = this[Keys.DIAG_KEYCODE],
                scancode = this[Keys.DIAG_SCANCODE],
                worksScreenOff = this[Keys.DIAG_SCREEN_OFF],
                completedAtMs = completedAt,
                mainLaunchReferrer = this[Keys.DIAG_MAIN_REFERRER],
            )
        } else null
        return AppSettings(
            sessionId = this[Keys.SESSION_ID] ?: 0L,
            debounceMs = this[Keys.DEBOUNCE_MS] ?: AppSettings.DEFAULT_DEBOUNCE_MS,
            crossSourceMergeMs = this[Keys.CROSS_SOURCE_MERGE_MS] ?: AppSettings.DEFAULT_CROSS_SOURCE_MERGE_MS,
            pressGesture = this[Keys.PRESS_GESTURE]?.let { runCatching { PressGesture.valueOf(it) }.getOrNull() } ?: PressGesture.SINGLE,
            pathMode = this[Keys.PATH_MODE]?.let { runCatching { PathMode.valueOf(it) }.getOrNull() } ?: PathMode.AUTO,
            diagnosis = diagnosis,
            wizardCompleted = this[Keys.WIZARD_COMPLETED] ?: false,
            serviceEnabled = this[Keys.SERVICE_ENABLED] ?: false,
            diagnosticsMode = this[Keys.DIAGNOSTICS_MODE] ?: false,
            vibrateOnClick = this[Keys.VIBRATE] ?: true,
            saveLocation = this[Keys.SAVE_LOCATION] ?: true,
        )
    }
}
