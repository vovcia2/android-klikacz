package com.klikacz.clicklog.clicks

import com.klikacz.clicklog.data.ClickDao
import com.klikacz.clicklog.data.ClickEntity
import com.klikacz.clicklog.data.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ClickRepository @Inject constructor(
    private val dao: ClickDao,
    private val settings: Settings,
) {
    private val sessionId: Flow<Long> = settings.flow.map { it.sessionId }.distinctUntilChanged()

    suspend fun insert(click: ClickEntity): ClickEntity {
        val id = dao.insert(click)
        return click.copy(id = id)
    }

    val sessionCount: Flow<Int> = sessionId.flatMapLatest { dao.observeCount(it) }
    val lastClick: Flow<ClickEntity?> = sessionId.flatMapLatest { dao.observeLast(it) }
    val sessionClicks: Flow<List<ClickEntity>> = sessionId.flatMapLatest { dao.observeSession(it) }

    fun countSince(sinceWallMs: Long): Flow<Int> =
        sessionId.flatMapLatest { dao.observeCountSince(it, sinceWallMs) }

    suspend fun listCurrentSession(): List<ClickEntity> = dao.listSession(settings.current().sessionId)
    suspend fun listAll(): List<ClickEntity> = dao.listAll()
}
