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
 *  - [crossSourceMergeMs]: the same press can be seen more than once (KeyEvent, app launch,
 *    vendor broadcast). One click per *other* source closer than this to the gesture's last
 *    event is merged into it. A gesture never absorbs a second click of the same non-key source,
 *    and never absorbs a key once it has keys, so two real presses are never collapsed.
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
    private class Gesture(val startMs: Long, var lastMs: Long, first: ClickSourceType) {
        val counts: MutableMap<ClickSourceType, Int> = mutableMapOf(first to 1)
        val keyCount: Int get() = counts[ClickSourceType.KEY_EVENT] ?: 0
        fun has(source: ClickSourceType) = (counts[source] ?: 0) > 0
        fun hasOther(source: ClickSourceType) = counts.any { it.key != source && it.value > 0 }
        fun add(source: ClickSourceType) { counts[source] = (counts[source] ?: 0) + 1 }
    }

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
        gesture = Gesture(startMs = raw.monotonicMs, lastMs = raw.monotonicMs, first = raw.source)
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
        if (sinceStart < debounceMs()) {
            // Bounce / auto-repeat. A non-key source arriving this fast is still *this* gesture's
            // launch or broadcast, so record it: the gesture must not absorb another one later.
            if (raw.source != ClickSourceType.KEY_EVENT) g.add(raw.source)
            return true
        }
        return when (raw.source) {
            ClickSourceType.KEY_EVENT -> when {
                g.keyCount == 1 && sinceStart < doublePressWindowMs() -> { g.add(raw.source); true }
                g.keyCount == 0 && g.hasOther(raw.source) && sinceLast < crossSourceMergeMs() -> { g.add(raw.source); true }
                else -> false
            }
            // Launches and vendor broadcasts: at most one per gesture, merged only into a gesture
            // that already has a click from a different source.
            else -> when {
                !g.has(raw.source) && g.hasOther(raw.source) && sinceLast < crossSourceMergeMs() -> { g.add(raw.source); true }
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
