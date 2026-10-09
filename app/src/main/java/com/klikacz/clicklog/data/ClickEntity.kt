package com.klikacz.clicklog.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.klikacz.clicklog.clicks.ClickSourceType

/**
 * One registered press of the programmable (SOS) button.
 *
 * `uptimeMs` is `KeyEvent.eventTime` for the KEY_EVENT path and null for the
 * LAUNCH_INTENT path, where the system gives us no kernel timestamp.
 */
@Entity(tableName = "clicks")
data class ClickEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "wall_time_ms") val wallTimeMs: Long,
    @ColumnInfo(name = "uptime_ms") val uptimeMs: Long?,
    @ColumnInfo(name = "source") val source: ClickSourceType,
)
