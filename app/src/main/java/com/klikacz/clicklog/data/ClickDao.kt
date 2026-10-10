package com.klikacz.clicklog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClickDao {
    @Insert
    suspend fun insert(click: ClickEntity): Long

    @Query("SELECT COUNT(*) FROM clicks WHERE session_id = :sessionId")
    fun observeCount(sessionId: Long): Flow<Int>

    @Query("SELECT * FROM clicks WHERE session_id = :sessionId ORDER BY id DESC LIMIT 1")
    fun observeLast(sessionId: Long): Flow<ClickEntity?>

    @Query("SELECT * FROM clicks WHERE session_id = :sessionId ORDER BY id DESC")
    fun observeSession(sessionId: Long): Flow<List<ClickEntity>>

    @Query("SELECT * FROM clicks WHERE session_id = :sessionId ORDER BY id ASC")
    suspend fun listSession(sessionId: Long): List<ClickEntity>

    @Query("SELECT * FROM clicks ORDER BY id ASC")
    suspend fun listAll(): List<ClickEntity>

    @Query(
        "UPDATE clicks SET latitude = :lat, longitude = :lon, location_accuracy_m = :accuracyM, " +
            "location_age_ms = :ageMs, location_provider = :provider WHERE id = :id",
    )
    suspend fun updateLocation(id: Long, lat: Double, lon: Double, accuracyM: Float?, ageMs: Long, provider: String?)

    @Query("SELECT COUNT(*) FROM clicks WHERE session_id = :sessionId AND wall_time_ms >= :sinceWallMs")
    fun observeCountSince(sessionId: Long, sinceWallMs: Long): Flow<Int>
}
