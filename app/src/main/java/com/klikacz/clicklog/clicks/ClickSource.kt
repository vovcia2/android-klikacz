package com.klikacz.clicklog.clicks

import kotlinx.coroutines.flow.Flow

/**
 * Capture path (spec §4). A = KEY_EVENT, B = LAUNCH_INTENT, C = VENDOR_BROADCAST
 * (e.g. Samsung XCover / Top key `HARD_KEY_REPORT`).
 */
enum class ClickSourceType(val letter: String) {
    KEY_EVENT("A"),
    LAUNCH_INTENT("B"),
    VENDOR_BROADCAST("C"),
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

/**
 * Common interface of the two capture paths (spec §4 "wspólny interfejs ClickSource").
 *
 * [clicks] is backed by a buffered channel: a click produced before the pipeline has started
 * collecting (e.g. a cold start of the process by a key launch) is kept, not dropped.
 */
interface ClickSource {
    val type: ClickSourceType
    val clicks: Flow<RawClick>
}
