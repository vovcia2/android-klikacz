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
    /** Rough location at (or shortly after) the click; null when unavailable. */
    @ColumnInfo(name = "latitude") val latitude: Double? = null,
    @ColumnInfo(name = "longitude") val longitude: Double? = null,
    @ColumnInfo(name = "location_accuracy_m") val locationAccuracyM: Float? = null,
    /** Age of the fix relative to the click (negative = fix obtained after the click). */
    @ColumnInfo(name = "location_age_ms") val locationAgeMs: Long? = null,
    @ColumnInfo(name = "location_provider") val locationProvider: String? = null,
)
