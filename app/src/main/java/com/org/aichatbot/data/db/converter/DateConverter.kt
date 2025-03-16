package com.org.aichatbot.data.db.converter

import androidx.room.TypeConverter
import java.util.Date

/**
 * Type converter for Room database to convert between Date and Long
 */
class DateConverter {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}
