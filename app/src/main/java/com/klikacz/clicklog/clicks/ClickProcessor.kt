package com.klikacz.clicklog.clicks

import com.klikacz.clicklog.data.ClickEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Turns raw clicks into stored clicks (spec §2.1, §5): one stored click per physical *gesture*.
 *
 * A gesture starts with an accepted raw click and absorbs later raw clicks that belong to the
 * same press. All windows are on the uptime clock:
 *  - [debounceMs]: any raw click closer than this to the gesture start is bounce / auto-repeat;
 *  - [doublePressWindowMs] (0 = single-press mode): in double-press mode a second KeyEvent
 *    closer than this to the gesture start is the second half of the gesture;
 *  - [crossSourceMergeMs]: the same press can be seen twice, as a KeyEvent and as an app launch
 *    by the vendor key handler. One launch (resp. one key) closer than this to the gesture's
 *    last event is merged into it. A gesture never absorbs a second launch, and never absorbs a
 *    key once it has keys, so two real presses are never collapsed through a launch between them.
 *
 * Pure Kotlin so it can be unit-tested; Android wiring lives in [ClickPipeline].
 *
 * Persistence is sequential: a single consumer drains an unbounded channel on the IO dispatcher,
 * so clicks are stored in arrival order and nothing is lost if the process is killed shortly
 * after a click.
 */
class ClickProcessor(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val debounceMs: () -> Int,
    private val sessionId: () -> Long,
    private val persist: suspend (ClickEntity) -> ClickEntity,
    private val crossSourceMergeMs: () -> Int = { DEFAULT_CROSS_SOURCE_MERGE_MS },
    private val doublePressWindowMs: () -> Int = { 0 },
) {
    private class Gesture(val startMs: Long, var lastMs: Long, var keyCount: Int, var launchCount: Int)

    private val queue = Channel<ClickEntity>(Channel.UNLIMITED)
    private val _stored = MutableSharedFlow<ClickEntity>(extraBufferCapacity = 64)

    /** Clicks after they have been written to the database. */
    val stored: SharedFlow<ClickEntity> = _stored.asSharedFlow()

    private var gesture: Gesture? = null

    init {
        scope.launch {
            for (entity in queue) {
                val saved = withContext(ioDispatcher) { persist(entity) }
                _stored.emit(saved)
            }
        }
    }

    /**
     * Offers a raw click. Returns the entity queued for persistence, or null if the click was
     * absorbed into the current gesture (debounced, second half of a double press, or the same
     * press seen on the other source).
     */
    @Synchronized
    fun offer(raw: RawClick): ClickEntity? {
        val g = gesture
        if (g != null && absorb(g, raw)) {
            g.lastMs = raw.monotonicMs
            return null
        }
        gesture = Gesture(
            startMs = raw.monotonicMs,
            lastMs = raw.monotonicMs,
            keyCount = if (raw.source == ClickSourceType.KEY_EVENT) 1 else 0,
            launchCount = if (raw.source == ClickSourceType.LAUNCH_INTENT) 1 else 0,
        )
        val entity = ClickEntity(
            sessionId = sessionId(),
            wallTimeMs = raw.wallTimeMs,
            uptimeMs = raw.uptimeMs,
            source = raw.source,
        )
        queue.trySend(entity)
        return entity
    }

    private fun absorb(g: Gesture, raw: RawClick): Boolean {
        val sinceStart = raw.monotonicMs - g.startMs
        val sinceLast = raw.monotonicMs - g.lastMs
        if (sinceStart < debounceMs()) return true
        return when (raw.source) {
            ClickSourceType.KEY_EVENT -> when {
                g.keyCount == 1 && sinceStart < doublePressWindowMs() -> { g.keyCount++; true }
                g.keyCount == 0 && g.launchCount > 0 && sinceLast < crossSourceMergeMs() -> { g.keyCount++; true }
                else -> false
            }
            ClickSourceType.LAUNCH_INTENT -> when {
                g.launchCount == 0 && g.keyCount > 0 && sinceLast < crossSourceMergeMs() -> { g.launchCount++; true }
                else -> false
            }
        }
    }

    /** Forgets the gesture state (e.g. on new session). */
    @Synchronized
    fun reset() {
        gesture = null
    }

    companion object {
        const val DEFAULT_CROSS_SOURCE_MERGE_MS = 600

        /**
         * Converts a kernel event time (uptime clock) to wall-clock epoch millis:
         * `wall = now_wall - (now_uptime - eventTime)` (spec §2.1).
         */
        fun eventWallTime(eventTimeUptimeMs: Long, nowUptimeMs: Long, nowWallMs: Long): Long =
            nowWallMs - (nowUptimeMs - eventTimeUptimeMs)
    }
}
