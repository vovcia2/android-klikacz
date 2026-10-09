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
    private class Harness(scope: TestScope, debounce: Int = 150) {
        val persisted = mutableListOf<ClickEntity>()
        private var nextId = 1L
        private val job = SupervisorJob()
        val processor = ClickProcessor(
            scope = CoroutineScope(StandardTestDispatcher(scope.testScheduler) + job),
            ioDispatcher = StandardTestDispatcher(scope.testScheduler),
            debounceMs = { debounce },
            sessionId = { 42L },
            persist = { e -> e.copy(id = nextId++).also { persisted += it } },
        )

        fun close() = job.cancel()
    }

    private fun harnessTest(debounce: Int = 150, body: suspend TestScope.(Harness) -> Unit) = runTest {
        val h = Harness(this, debounce)
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
}
