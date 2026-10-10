package com.klikacz.clicklog.clicks

import com.klikacz.clicklog.data.ClickEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClickProcessorTest {

    private fun key(monotonic: Long, wall: Long = 1_000_000L + monotonic) =
        RawClick(wallTimeMs = wall, uptimeMs = monotonic, monotonicMs = monotonic, source = ClickSourceType.KEY_EVENT)

    private fun intent(monotonic: Long, wall: Long = 1_000_000L + monotonic) =
        RawClick(wallTimeMs = wall, uptimeMs = null, monotonicMs = monotonic, source = ClickSourceType.LAUNCH_INTENT)

    /**
     * The processor's consumer coroutine runs on the test scheduler under a job we cancel at the
     * end of each test; `backgroundScope` is not driven by `advanceUntilIdle`.
     */
    private class Harness(scope: TestScope, debounce: Int = 150, merge: Int = 600, double: Int = 0) {
        val persisted = mutableListOf<ClickEntity>()
        private var nextId = 1L
        private val job = SupervisorJob()
        val processor = ClickProcessor(
            scope = CoroutineScope(StandardTestDispatcher(scope.testScheduler) + job),
            ioDispatcher = StandardTestDispatcher(scope.testScheduler),
            debounceMs = { debounce },
            sessionId = { 42L },
            persist = { e -> e.copy(id = nextId++).also { persisted += it } },
            crossSourceMergeMs = { merge },
            doublePressWindowMs = { double },
        )

        fun close() = job.cancel()
    }

    private fun harnessTest(debounce: Int = 150, merge: Int = 600, double: Int = 0, body: suspend TestScope.(Harness) -> Unit) = runTest {
        val h = Harness(this, debounce, merge, double)
        try {
            body(h)
        } finally {
            h.close()
        }
    }

    @Test
    fun `eventTime to wall conversion subtracts delivery latency`() {
        // event happened at uptime 10_000, handled at uptime 10_045 when wall clock was 1_700_000_000_045
        val wall = ClickProcessor.eventWallTime(eventTimeUptimeMs = 10_000, nowUptimeMs = 10_045, nowWallMs = 1_700_000_000_045)
        assertEquals(1_700_000_000_000, wall)
    }

    @Test
    fun `eventTime equal to now gives current wall time`() {
        assertEquals(5_000L, ClickProcessor.eventWallTime(100, 100, 5_000))
    }

    @Test
    fun `second click 149 ms later is debounced`() = harnessTest { h ->
        assertNotNull(h.processor.offer(key(1_000)))
        assertNull(h.processor.offer(key(1_149)))
        advanceUntilIdle()
        assertEquals(1, h.persisted.size)
    }

    @Test
    fun `second click 151 ms later is accepted`() = harnessTest { h ->
        assertNotNull(h.processor.offer(key(1_000)))
        assertNotNull(h.processor.offer(key(1_151)))
        advanceUntilIdle()
        assertEquals(2, h.persisted.size)
    }

    @Test
    fun `click exactly at window boundary is accepted`() = harnessTest { h ->
        assertNotNull(h.processor.offer(key(1_000)))
        assertNotNull(h.processor.offer(key(1_150)))
        advanceUntilIdle()
        assertEquals(2, h.persisted.size)
    }

    @Test
    fun `debounce is measured from last accepted click, not last rejected`() = harnessTest { h ->
        assertNotNull(h.processor.offer(key(1_000)))
        assertNull(h.processor.offer(key(1_100)))   // rejected
        assertNull(h.processor.offer(key(1_140)))   // 140 ms after accepted -> rejected
        assertNotNull(h.processor.offer(key(1_160))) // 160 ms after accepted -> accepted
        advanceUntilIdle()
        assertEquals(2, h.persisted.size)
    }

    @Test
    fun `clicks are persisted in arrival order with session id and source`() = harnessTest { h ->
        h.processor.offer(key(1_000, wall = 10))
        h.processor.offer(intent(2_000, wall = 20))
        h.processor.offer(key(3_000, wall = 30))
        advanceUntilIdle()
        assertEquals(listOf(10L, 20L, 30L), h.persisted.map { it.wallTimeMs })
        assertEquals(listOf(1L, 2L, 3L), h.persisted.map { it.id })
        assertEquals(listOf(42L, 42L, 42L), h.persisted.map { it.sessionId })
        assertEquals(listOf(ClickSourceType.KEY_EVENT, ClickSourceType.LAUNCH_INTENT, ClickSourceType.KEY_EVENT), h.persisted.map { it.source })
        assertEquals(listOf<Long?>(1_000L, null, 3_000L), h.persisted.map { it.uptimeMs })
    }

    @Test
    fun `stored flow emits after persistence`() = harnessTest { h ->
        val seen = mutableListOf<Long>()
        val job = launch { h.processor.stored.collect { seen += it.id } }
        advanceUntilIdle()
        h.processor.offer(key(1_000))
        h.processor.offer(key(2_000))
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), seen)
        job.cancel()
    }

    @Test
    fun `reset forgets debounce state`() = harnessTest { h ->
        assertNotNull(h.processor.offer(key(1_000)))
        h.processor.reset()
        assertNotNull(h.processor.offer(key(1_010)))
        advanceUntilIdle()
        assertEquals(2, h.persisted.size)
    }

    @Test
    fun `custom debounce window is honoured`() = harnessTest(debounce = 500) { h ->
        assertNotNull(h.processor.offer(key(1_000)))
        assertNull(h.processor.offer(key(1_400)))
        assertNotNull(h.processor.offer(key(1_500)))
        advanceUntilIdle()
        assertEquals(2, h.persisted.size)
    }

    // ---- cross-source merge ---------------------------------------------------------------

    @Test
    fun `launch 400 ms after a key event is the same press and is merged`() = harnessTest { h ->
        assertNotNull(h.processor.offer(key(1_000)))
        assertNull(h.processor.offer(intent(1_400)))
        advanceUntilIdle()
        assertEquals(1, h.persisted.size)
        assertEquals(ClickSourceType.KEY_EVENT, h.persisted.single().source)
    }

    @Test
    fun `key event 400 ms after a launch is merged too`() = harnessTest { h ->
        assertNotNull(h.processor.offer(intent(1_000)))
        assertNull(h.processor.offer(key(1_400)))
        advanceUntilIdle()
        assertEquals(1, h.persisted.size)
        assertEquals(ClickSourceType.LAUNCH_INTENT, h.persisted.single().source)
    }

    @Test
    fun `launch 600 ms after a key event is a new press`() = harnessTest { h ->
        assertNotNull(h.processor.offer(key(1_000)))
        assertNotNull(h.processor.offer(intent(1_600)))
        advanceUntilIdle()
        assertEquals(2, h.persisted.size)
    }

    @Test
    fun `same-source clicks 400 ms apart are not merged, only debounced`() = harnessTest { h ->
        assertNotNull(h.processor.offer(intent(1_000)))
        assertNotNull(h.processor.offer(intent(1_400)))
        assertNotNull(h.processor.offer(key(2_000)))
        assertNotNull(h.processor.offer(key(2_400)))
        advanceUntilIdle()
        assertEquals(4, h.persisted.size)
    }

    @Test
    fun `merge window of zero disables merging`() = harnessTest(merge = 0) { h ->
        assertNotNull(h.processor.offer(key(1_000)))
        assertNotNull(h.processor.offer(intent(1_200)))
        advanceUntilIdle()
        assertEquals(2, h.persisted.size)
    }

    @Test
    fun `screen-off sequence of launches only is counted in full`() = harnessTest { h ->
        assertNotNull(h.processor.offer(intent(1_000)))
        assertNotNull(h.processor.offer(intent(3_000)))
        assertNotNull(h.processor.offer(intent(5_000)))
        advanceUntilIdle()
        assertEquals(3, h.persisted.size)
        assertEquals(listOf<Long?>(null, null, null), h.persisted.map { it.uptimeMs })
    }

    @Test
    fun `a second real press shortly after a merged launch is still a new click`() = harnessTest { h ->
        assertNotNull(h.processor.offer(key(1_000)))     // press 1
        assertNull(h.processor.offer(intent(1_300)))     // launch for press 1 -> merged
        assertNotNull(h.processor.offer(key(1_400)))     // press 2, only 100 ms after the launch
        assertNull(h.processor.offer(intent(1_700)))     // launch for press 2 -> merged
        advanceUntilIdle()
        assertEquals(2, h.persisted.size)
    }

    @Test
    fun `a gesture never absorbs a second launch`() = harnessTest { h ->
        assertNotNull(h.processor.offer(key(1_000)))
        assertNull(h.processor.offer(intent(1_200)))
        assertNotNull(h.processor.offer(intent(1_500)))
        advanceUntilIdle()
        assertEquals(2, h.persisted.size)
    }

    // ---- double-press gesture ---------------------------------------------------------------

    @Test
    fun `double press mode - two key events and the launch are one click`() = harnessTest(double = 800) { h ->
        assertNotNull(h.processor.offer(key(1_000)))     // DOWN 1 -> click
        assertNull(h.processor.offer(key(1_250)))        // DOWN 2 -> second half
        assertNull(h.processor.offer(intent(1_600)))     // vendor launch 350 ms after DOWN 2
        assertNotNull(h.processor.offer(key(3_000)))     // next double press
        assertNull(h.processor.offer(key(3_260)))
        assertNull(h.processor.offer(intent(3_700)))
        advanceUntilIdle()
        assertEquals(listOf(1_000L, 3_000L), h.persisted.map { it.uptimeMs })
    }

    @Test
    fun `double press mode - third key event in the window starts a new click`() = harnessTest(double = 800) { h ->
        assertNotNull(h.processor.offer(key(1_000)))
        assertNull(h.processor.offer(key(1_250)))
        assertNotNull(h.processor.offer(key(1_500)))
        advanceUntilIdle()
        assertEquals(2, h.persisted.size)
    }

    @Test
    fun `double press mode - screen off launches count one each`() = harnessTest(double = 800) { h ->
        assertNotNull(h.processor.offer(intent(1_000)))
        assertNotNull(h.processor.offer(intent(3_000)))
        advanceUntilIdle()
        assertEquals(2, h.persisted.size)
    }

    @Test
    fun `single press mode - two key events 250 ms apart are two clicks`() = harnessTest { h ->
        assertNotNull(h.processor.offer(key(1_000)))
        assertNotNull(h.processor.offer(key(1_250)))
        advanceUntilIdle()
        assertEquals(2, h.persisted.size)
    }
}
