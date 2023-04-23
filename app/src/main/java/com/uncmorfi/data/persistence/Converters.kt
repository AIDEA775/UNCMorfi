package com.uncmorfi.data.persistence

import androidx.room.TypeConverter
import com.uncmorfi.shared.toCalendar
import com.uncmorfi.shared.toISOString
import java.time.LocalDate
import java.util.*

class Converters {
    @TypeConverter
    fun fromTimestamp(value: String?): Calendar? {
        return value?.toCalendar()
    }

    @TypeConverter
    fun dateToTimestamp(cal: Calendar?): String? {
        return cal?.toISOString()
    }

    @TypeConverter
    fun readLocalDate(value: String?): LocalDate? {
        return value?.let { LocalDate.parse(value) }
    }

    @TypeConverter
    fun saveLocalDate(date: LocalDate?): String? {
        return date?.toString()
    }

    @TypeConverter
    fun listToString(value: List<String>?): String {
        return value?.joinToString("*-*") ?: ""
    }

    @TypeConverter
    fun stringToList(value: String): List<String> {
        return value.split("*-*")
    }
}