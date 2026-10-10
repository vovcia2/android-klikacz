package com.klikacz.clicklog.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.klikacz.clicklog.clicks.ClickSourceType

class Converters {
    @TypeConverter
    fun sourceToString(value: ClickSourceType): String = value.name

    @TypeConverter
    fun stringToSource(value: String): ClickSourceType = ClickSourceType.valueOf(value)

    @TypeConverter
    fun channelToString(value: DiagnosticChannel): String = value.name

    @TypeConverter
    fun stringToChannel(value: String): DiagnosticChannel = DiagnosticChannel.valueOf(value)
}

@Database(
    entities = [ClickEntity::class, DiagnosticEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clickDao(): ClickDao
    abstract fun diagnosticDao(): DiagnosticDao

    companion object {
        const val NAME = "clicklog.db"

        /** v2: rough location columns on clicks. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clicks ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE clicks ADD COLUMN longitude REAL")
                db.execSQL("ALTER TABLE clicks ADD COLUMN location_accuracy_m REAL")
                db.execSQL("ALTER TABLE clicks ADD COLUMN location_age_ms INTEGER")
                db.execSQL("ALTER TABLE clicks ADD COLUMN location_provider TEXT")
            }
        }
    }
}
