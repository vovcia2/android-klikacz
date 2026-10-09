package com.klikacz.clicklog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticDao {
    @Insert
    suspend fun insert(event: DiagnosticEntity): Long

    @Query("SELECT * FROM diagnostic_log ORDER BY id DESC LIMIT :limit")
    fun observeLatest(limit: Int = 500): Flow<List<DiagnosticEntity>>

    @Query("SELECT * FROM diagnostic_log WHERE ts_ms >= :sinceMs ORDER BY id ASC")
    fun observeSince(sinceMs: Long): Flow<List<DiagnosticEntity>>

    @Query("SELECT * FROM diagnostic_log ORDER BY id ASC")
    suspend fun listAll(): List<DiagnosticEntity>

    @Query("DELETE FROM diagnostic_log")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM diagnostic_log")
    fun observeCount(): Flow<Int>
}
