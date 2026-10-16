package com.klikacz.clicklog.diagnostics

import com.klikacz.clicklog.clicks.ClickSourceType
import com.klikacz.clicklog.data.DiagnosticChannel
import com.klikacz.clicklog.data.DiagnosticEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PathSelectorTest {

    private var ts = 0L
    private fun key(channel: DiagnosticChannel, keycode: Int, action: String = DiagnosticEntity.ACTION_DOWN, scancode: Int? = 100 + keycode, screenOn: Boolean = true) =
        DiagnosticEntity(tsMs = ts++, channel = channel, keycode = keycode, scancode = scancode, action = action, screenOn = screenOn)

    private fun launch(channel: DiagnosticChannel = DiagnosticChannel.LAUNCH_INTENT, screenOn: Boolean = true) =
        DiagnosticEntity(tsMs = ts++, channel = channel, intentAction = "android.intent.action.MAIN", screenOn = screenOn)

    private fun broadcast() = DiagnosticEntity(tsMs = ts++, channel = DiagnosticChannel.VENDOR_BROADCAST, intentAction = "com.bullitt.sos.ACTION_SOS")
    private fun samsung(keycode: Int = 1015, action: String = DiagnosticEntity.ACTION_DOWN, screenOn: Boolean = true) =
        DiagnosticEntity(tsMs = ts++, channel = DiagnosticChannel.VENDOR_BROADCAST, keycode = keycode, action = action,
            intentAction = "com.samsung.android.knox.intent.action.HARD_KEY_REPORT", screenOn = screenOn)
    private fun media(keycode: Int = 79) = key(DiagnosticChannel.MEDIA_BUTTON, keycode)

    private val AS = DiagnosticChannel.ACCESSIBILITY_KEY
    private val ACT = DiagnosticChannel.ACTIVITY_KEY

    // ---- Rule 1 -------------------------------------------------------------------------------

    @Test
    fun `rule 1 - three accessibility DOWN events with same keycode select path A`() {
        val d = PathSelector.select(listOf(key(AS, 288), key(AS, 288, DiagnosticEntity.ACTION_UP), key(AS, 288), key(AS, 288)))
        assertEquals(ClickSourceType.KEY_EVENT, d.path)
        assertEquals(288, d.keycode)
        assertEquals(388, d.scancode)
        assertEquals(PathReason.ACCESSIBILITY_KEY, d.reason)
        assertEquals(3, d.accessibilityDownCount)
    }

    @Test
    fun `rule 1 - accessibility wins even if activity and intents also fired`() {
        val d = PathSelector.select(listOf(key(AS, 288), key(ACT, 288), key(AS, 288), key(ACT, 288), key(AS, 288), launch()))
        assertEquals(ClickSourceType.KEY_EVENT, d.path)
        assertEquals(288, d.keycode)
    }

    @Test
    fun `rule 1 - picks the most frequent keycode`() {
        val d = PathSelector.select(listOf(key(AS, 24), key(AS, 288), key(AS, 288), key(AS, 25), key(AS, 288)))
        assertEquals(288, d.keycode)
    }

    @Test
    fun `rule 1 - UP events do not count toward the threshold`() {
        val up = DiagnosticEntity.ACTION_UP
        val d = PathSelector.select(listOf(key(AS, 288), key(AS, 288, up), key(AS, 288, up), key(AS, 288, up)))
        assertEquals(PathReason.NOT_DETECTED, d.reason)
        assertNull(d.path)
    }

    @Test
    fun `rule 1 - only two accessibility events is not enough and falls through`() {
        val d = PathSelector.select(listOf(key(AS, 288), key(AS, 288)))
        assertEquals(PathReason.NOT_DETECTED, d.reason)
    }

    // ---- Rule 2 -------------------------------------------------------------------------------

    @Test
    fun `rule 2 - activity saw keys but accessibility saw nothing selects path B with message`() {
        val d = PathSelector.select(listOf(key(ACT, 288), key(ACT, 288), key(ACT, 288)))
        assertEquals(ClickSourceType.LAUNCH_INTENT, d.path)
        assertEquals(PathReason.ACTIVITY_ONLY, d.reason)
        assertEquals(288, d.keycode)
        assertEquals(3, d.activityDownCount)
    }

    @Test
    fun `rule 2 - a single activity key event is enough`() {
        val d = PathSelector.select(listOf(key(ACT, 288)))
        assertEquals(PathReason.ACTIVITY_ONLY, d.reason)
    }

    @Test
    fun `rule 2 - does not apply when accessibility saw anything at all`() {
        // AS saw 2 (below threshold), activity saw 3, no intents -> not rule 1, not rule 2, not rule 3
        val d = PathSelector.select(listOf(key(AS, 288), key(AS, 288), key(ACT, 288), key(ACT, 288), key(ACT, 288)))
        assertEquals(PathReason.NOT_DETECTED, d.reason)
        assertNull(d.path)
    }

    @Test
    fun `rule 2 - back home and power keys seen by the activity are ignored`() {
        val d = PathSelector.select(listOf(key(ACT, 4), key(ACT, 3), key(ACT, 26)))
        assertEquals(PathReason.NOT_DETECTED, d.reason)
    }

    @Test
    fun `rule 2 - ranks above rule 3 when both activity keys and intents arrived`() {
        val d = PathSelector.select(listOf(key(ACT, 288), launch()))
        assertEquals(PathReason.ACTIVITY_ONLY, d.reason)
        assertEquals(ClickSourceType.LAUNCH_INTENT, d.path)
    }

    // ---- Rule 3 -------------------------------------------------------------------------------

    @Test
    fun `rule 3 - launch intents only select path B`() {
        val d = PathSelector.select(listOf(launch(), launch(), launch()))
        assertEquals(ClickSourceType.LAUNCH_INTENT, d.path)
        assertEquals(PathReason.LAUNCH_INTENT, d.reason)
        assertNull(d.keycode)
        assertEquals(3, d.launchIntentCount)
    }

    @Test
    fun `rule 3 - main activity intents count as launches`() {
        val d = PathSelector.select(listOf(launch(DiagnosticChannel.MAIN_INTENT)))
        assertEquals(PathReason.LAUNCH_INTENT, d.reason)
    }

    @Test
    fun `rule 3 - applies when accessibility saw too few events and activity saw none`() {
        val d = PathSelector.select(listOf(key(AS, 288), launch(), launch()))
        assertEquals(PathReason.LAUNCH_INTENT, d.reason)
    }

    // ---- Rule 4 -------------------------------------------------------------------------------

    @Test
    fun `rule 4 - nothing at all`() {
        val d = PathSelector.select(emptyList())
        assertNull(d.path)
        assertEquals(PathReason.NOT_DETECTED, d.reason)
    }

    @Test
    fun `rule 4 - vendor broadcasts and media buttons alone do not select a path but are counted`() {
        val d = PathSelector.select(listOf(broadcast(), broadcast(), media(), media(), media()))
        assertEquals(PathReason.NOT_DETECTED, d.reason)
        assertEquals(2, d.vendorBroadcastCount)
        assertEquals(3, d.mediaButtonCount)
    }

    @Test
    fun `app markers are ignored`() {
        val marker = DiagnosticEntity(tsMs = ts++, channel = DiagnosticChannel.APP, extrasJson = "{}")
        val d = PathSelector.select(listOf(marker, marker, marker))
        assertEquals(PathReason.NOT_DETECTED, d.reason)
    }

    // ---- counters -----------------------------------------------------------------------------

    @Test
    fun `pressCount does not double count a press seen on both key channels`() {
        val events = listOf(key(AS, 288), key(ACT, 288), key(AS, 288), key(ACT, 288))
        assertEquals(2, PathSelector.pressCount(events))
    }

    @Test
    fun `pressCount adds launch intents`() {
        val events = listOf(key(AS, 288), launch(), launch())
        assertEquals(3, PathSelector.pressCount(events))
    }

    @Test
    fun `pressCount ignores UP and navigation keys`() {
        val events = listOf(key(ACT, 4), key(ACT, 288, DiagnosticEntity.ACTION_UP))
        assertEquals(0, PathSelector.pressCount(events))
    }

    @Test
    fun `screenOffPressCount counts only screen-off accessibility DOWN of the keycode plus launches`() {
        val events = listOf(
            key(AS, 288, screenOn = false),
            key(AS, 288, screenOn = true),
            key(AS, 24, screenOn = false),
            key(AS, 288, DiagnosticEntity.ACTION_UP, screenOn = false),
            launch(screenOn = true),
        )
        assertEquals(2, PathSelector.screenOffPressCount(events, 288))
        assertEquals(3, PathSelector.screenOffPressCount(events, null))
    }

    // ---- Rule 0: vendor key broadcasts (Samsung XCover / Top key) ----------------------------

    @Test
    fun `rule 0 - three samsung key-down broadcasts select path C with the key code`() {
        val up = DiagnosticEntity.ACTION_UP
        val d = PathSelector.select(listOf(samsung(), samsung(action = up), samsung(), samsung(action = up), samsung()))
        assertEquals(ClickSourceType.VENDOR_BROADCAST, d.path)
        assertEquals(1015, d.keycode)
        assertEquals(PathReason.VENDOR_BROADCAST, d.reason)
    }

    @Test
    fun `rule 0 - top key reports 1079`() {
        val d = PathSelector.select(listOf(samsung(1079), samsung(1079), samsung(1079)))
        assertEquals(1079, d.keycode)
    }

    @Test
    fun `rule 0 - wins over accessibility when both see the key`() {
        val d = PathSelector.select(listOf(samsung(), key(AS, 1015), samsung(), key(AS, 1015), samsung(), key(AS, 1015)))
        assertEquals(ClickSourceType.VENDOR_BROADCAST, d.path)
    }

    @Test
    fun `rule 0 - broadcasts without key-down action do not count`() {
        val d = PathSelector.select(listOf(broadcast(), broadcast(), broadcast()))
        assertEquals(PathReason.NOT_DETECTED, d.reason)
    }

    @Test
    fun `pressCount does not double count a press seen as key and as broadcast`() {
        assertEquals(2, PathSelector.pressCount(listOf(samsung(), key(AS, 1015), samsung(), key(AS, 1015))))
    }

    @Test
    fun `screenOffPressCount counts screen-off broadcasts`() {
        val events = listOf(samsung(screenOn = false), samsung(screenOn = false), samsung(screenOn = true))
        assertEquals(2, PathSelector.screenOffPressCount(events, null))
    }
}
