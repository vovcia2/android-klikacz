package com.klikacz.clicklog.diagnostics

import com.klikacz.clicklog.clicks.ClickSourceType
import com.klikacz.clicklog.data.DiagnosticChannel
import com.klikacz.clicklog.data.DiagnosticEntity

/** Why a path was (or was not) chosen; drives the wizard's message (spec §3.3). */
enum class PathReason {
    /** Rule 0: the vendor reports the key directly to the app (Samsung XCover / Top key). */
    VENDOR_BROADCAST,

    /** Rule 1: accessibility service saw ≥ 3 DOWN events of one keycode. */
    ACCESSIBILITY_KEY,

    /** Rule 2: the Activity saw key events but the accessibility service did not. */
    ACTIVITY_ONLY,

    /** Rule 3: only launch intents were seen. */
    LAUNCH_INTENT,

    /** Rule 4: nothing usable arrived. */
    NOT_DETECTED,
}

data class PathDecision(
    val path: ClickSourceType?,
    val keycode: Int?,
    val scancode: Int?,
    val reason: PathReason,
    /** Counts shown in the wizard summary. */
    val accessibilityDownCount: Int,
    val activityDownCount: Int,
    val launchIntentCount: Int,
    val vendorBroadcastCount: Int,
    val mediaButtonCount: Int,
)

/** Pure implementation of the AUTO path-selection rule (spec §3.3). */
object PathSelector {

    const val MIN_SAME_KEYCODE = 3

    /**
     * Keys the user might press to navigate the wizard itself; never treated as the SOS button
     * when seen only by the Activity.
     */
    private val IGNORED_ACTIVITY_KEYCODES = setOf(
        4,   // KEYCODE_BACK
        3,   // KEYCODE_HOME
        26,  // KEYCODE_POWER
    )

    fun select(events: List<DiagnosticEntity>): PathDecision {
        val asDown = events.filter { it.channel == DiagnosticChannel.ACCESSIBILITY_KEY && it.isKeyDown }
        val activityDown = events.filter {
            it.channel == DiagnosticChannel.ACTIVITY_KEY && it.isKeyDown && it.keycode !in IGNORED_ACTIVITY_KEYCODES
        }
        val asAny = events.any { it.channel == DiagnosticChannel.ACCESSIBILITY_KEY }
        val launches = events.filter { it.channel == DiagnosticChannel.LAUNCH_INTENT || it.channel == DiagnosticChannel.MAIN_INTENT }
        val vendorDown = events.filter { it.channel == DiagnosticChannel.VENDOR_BROADCAST && it.isKeyDown }
        val vendor = events.count { it.channel == DiagnosticChannel.VENDOR_BROADCAST }
        val media = events.count { it.channel == DiagnosticChannel.MEDIA_BUTTON }

        fun decision(path: ClickSourceType?, keycode: Int?, scancode: Int?, reason: PathReason) = PathDecision(
            path = path, keycode = keycode, scancode = scancode, reason = reason,
            accessibilityDownCount = asDown.size,
            activityDownCount = activityDown.size,
            launchIntentCount = launches.size,
            vendorBroadcastCount = vendor,
            mediaButtonCount = media,
        )

        // Rule 0: a vendor key broadcast is the officially supported channel (works with the
        // screen off) and does not require consuming the key, so it wins when present.
        if (vendorDown.size >= MIN_SAME_KEYCODE) {
            val kc = vendorDown.groupingBy { it.keycode }.eachCount().maxByOrNull { it.value }?.key
            return decision(ClickSourceType.VENDOR_BROADCAST, kc, null, PathReason.VENDOR_BROADCAST)
        }

        // Rule 1
        val best = asDown.groupBy { it.keycode }
            .filterKeys { it != null }
            .maxByOrNull { it.value.size }
        if (best != null && best.value.size >= MIN_SAME_KEYCODE) {
            val scan = best.value.mapNotNull { it.scancode }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            return decision(ClickSourceType.KEY_EVENT, best.key, scan, PathReason.ACCESSIBILITY_KEY)
        }

        // Rule 2
        if (activityDown.isNotEmpty() && !asAny) {
            val kc = activityDown.groupingBy { it.keycode }.eachCount().maxByOrNull { it.value }?.key
            val scan = activityDown.mapNotNull { it.scancode }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            return decision(ClickSourceType.LAUNCH_INTENT, kc, scan, PathReason.ACTIVITY_ONLY)
        }

        // Rule 3
        if (launches.isNotEmpty()) {
            return decision(ClickSourceType.LAUNCH_INTENT, null, null, PathReason.LAUNCH_INTENT)
        }

        // Rule 4
        return decision(null, null, null, PathReason.NOT_DETECTED)
    }

    /**
     * Number of distinct physical presses visible in [events] for the wizard's live counter.
     * The same press can appear on both key channels, so those are not summed.
     */
    fun pressCount(events: List<DiagnosticEntity>): Int {
        val asDown = events.count { it.channel == DiagnosticChannel.ACCESSIBILITY_KEY && it.isKeyDown }
        val activityDown = events.count {
            it.channel == DiagnosticChannel.ACTIVITY_KEY && it.isKeyDown && it.keycode !in IGNORED_ACTIVITY_KEYCODES
        }
        val vendorDown = events.count { it.channel == DiagnosticChannel.VENDOR_BROADCAST && it.isKeyDown }
        val launches = events.count { it.channel == DiagnosticChannel.LAUNCH_INTENT || it.channel == DiagnosticChannel.MAIN_INTENT }
        return maxOf(asDown, activityDown, vendorDown) + launches
    }

    /**
     * Presses registered while the screen was off (spec §3.1 step 4): accessibility key DOWN
     * events with `screen_on = false` (restricted to [keycode] when known) plus launch intents,
     * which arrive regardless of screen state.
     */
    fun screenOffPressCount(events: List<DiagnosticEntity>, keycode: Int?): Int =
        maxOf(screenOffKeyCount(events, keycode), screenOffVendorCount(events)) + screenOffLaunchCount(events)

    fun screenOffVendorCount(events: List<DiagnosticEntity>): Int =
        events.count { it.channel == DiagnosticChannel.VENDOR_BROADCAST && it.isKeyDown && !it.screenOn }

    fun screenOffKeyCount(events: List<DiagnosticEntity>, keycode: Int?): Int = events.count {
        it.channel == DiagnosticChannel.ACCESSIBILITY_KEY && it.isKeyDown && !it.screenOn &&
            (keycode == null || it.keycode == keycode)
    }

    fun screenOffLaunchCount(events: List<DiagnosticEntity>): Int =
        events.count { it.channel == DiagnosticChannel.LAUNCH_INTENT || it.channel == DiagnosticChannel.MAIN_INTENT }
}
