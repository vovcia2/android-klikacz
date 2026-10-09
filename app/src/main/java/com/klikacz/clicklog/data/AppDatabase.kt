package com.klikacz.clicklog.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
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
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clickDao(): ClickDao
    abstract fun diagnosticDao(): DiagnosticDao

    companion object {
        const val NAME = "clicklog.db"
    }
}
