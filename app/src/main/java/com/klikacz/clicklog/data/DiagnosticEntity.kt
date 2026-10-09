package com.klikacz.clicklog.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Channel through which a diagnostic event reached the app (spec §3.2). */
enum class DiagnosticChannel {
    /** `Activity.onKeyDown/onKeyUp` (via `dispatchKeyEvent`) while a screen is in the foreground. */
    ACTIVITY_KEY,

    /** `AccessibilityService.onKeyEvent`. */
    ACCESSIBILITY_KEY,

    /** Exported `LaunchReceiverActivity` was started by an Intent. */
    LAUNCH_INTENT,

    /** `MainActivity.onCreate` / `onNewIntent` received an Intent. */
    MAIN_INTENT,

    /** Dynamically registered receiver for vendor broadcast actions. */
    VENDOR_BROADCAST,

    /** `MediaSession` media-button callback. */
    MEDIA_BUTTON,

    /** Marker written by the app itself (test start/end, notes). */
    APP,
}

/** Raw diagnostic record (spec §3.2 `diagnostic_log`). */
@Entity(tableName = "diagnostic_log")
data class DiagnosticEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "ts_ms") val tsMs: Long,
    @ColumnInfo(name = "channel") val channel: DiagnosticChannel,
    @ColumnInfo(name = "keycode") val keycode: Int? = null,
    @ColumnInfo(name = "scancode") val scancode: Int? = null,
    /** `DOWN` / `UP` / `MULTIPLE` for key events, null otherwise. */
    @ColumnInfo(name = "action") val action: String? = null,
    @ColumnInfo(name = "intent_action") val intentAction: String? = null,
    @ColumnInfo(name = "extras_json") val extrasJson: String? = null,
    @ColumnInfo(name = "screen_on") val screenOn: Boolean = true,
) {
    val isKeyDown: Boolean get() = action == ACTION_DOWN

    companion object {
        const val ACTION_DOWN = "DOWN"
        const val ACTION_UP = "UP"
        const val ACTION_MULTIPLE = "MULTIPLE"
    }
}
