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
 * Debounces raw clicks and persists them in order (spec §2.1, §5).
 *
 * Pure Kotlin so it can be unit-tested: all Android wiring lives in [ClickPipeline].
 *
 * Persistence is sequential: a single consumer coroutine drains an unbounded channel on the
 * IO dispatcher, so clicks are stored in arrival order and nothing is lost if the process is
 * killed shortly after a click (the insert is already queued and completes on the IO thread).
 */
class ClickProcessor(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val debounceMs: () -> Int,
    private val sessionId: () -> Long,
    private val persist: suspend (ClickEntity) -> ClickEntity,
) {
    private val queue = Channel<ClickEntity>(Channel.UNLIMITED)
    private val _stored = MutableSharedFlow<ClickEntity>(extraBufferCapacity = 64)

    /** Clicks after they have been written to the database. */
    val stored: SharedFlow<ClickEntity> = _stored.asSharedFlow()

    @Volatile
    private var lastAcceptedMonotonicMs: Long? = null

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
     * rejected by the debounce window.
     */
    @Synchronized
    fun offer(raw: RawClick): ClickEntity? {
        val last = lastAcceptedMonotonicMs
        val window = debounceMs()
        if (last != null && raw.monotonicMs - last < window) {
            return null
        }
        lastAcceptedMonotonicMs = raw.monotonicMs
        val entity = ClickEntity(
            sessionId = sessionId(),
            wallTimeMs = raw.wallTimeMs,
            uptimeMs = raw.uptimeMs,
            source = raw.source,
        )
        queue.trySend(entity)
        return entity
    }

    /** Forgets the debounce state (e.g. on new session). */
    @Synchronized
    fun reset() {
        lastAcceptedMonotonicMs = null
    }

    companion object {
        /**
         * Converts a kernel event time (uptime clock) to wall-clock epoch millis:
         * `wall = now_wall - (now_uptime - eventTime)` (spec §2.1).
         */
        fun eventWallTime(eventTimeUptimeMs: Long, nowUptimeMs: Long, nowWallMs: Long): Long =
            nowWallMs - (nowUptimeMs - eventTimeUptimeMs)
    }
}
