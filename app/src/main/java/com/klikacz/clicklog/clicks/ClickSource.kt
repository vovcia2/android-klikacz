package com.klikacz.clicklog.clicks

import kotlinx.coroutines.flow.SharedFlow

/** Capture path (spec §4). A = KEY_EVENT, B = LAUNCH_INTENT. */
enum class ClickSourceType(val letter: String) {
    KEY_EVENT("A"),
    LAUNCH_INTENT("B"),
}

/**
 * A button press as seen by one of the capture paths, before debouncing.
 *
 * @property wallTimeMs epoch millis of the press, already corrected for delivery latency
 *   where possible (spec §2.1).
 * @property uptimeMs `KeyEvent.eventTime` (uptime clock) or null for the intent path.
 * @property monotonicMs a monotonic timestamp used for debouncing. For key events this is
 *   `eventTime`; for intents it is `SystemClock.uptimeMillis()` at receipt.
 */
data class RawClick(
    val wallTimeMs: Long,
    val uptimeMs: Long?,
    val monotonicMs: Long,
    val source: ClickSourceType,
)

/** Common interface of the two capture paths (spec §4 "wspólny interfejs ClickSource"). */
interface ClickSource {
    val type: ClickSourceType
    val clicks: SharedFlow<RawClick>
}
