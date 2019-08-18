package com.uncmorfi.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.uncmorfi.helpers.toFormat
import java.util.*

//fun String?.toDayMenuList(): List<DayMenu> {
//
//}

@Entity(tableName = "menu")
data class DayMenu (
        @PrimaryKey() var date: Date = Date(),
        val food: List<String> = listOf("", "", "")
) {
    override fun toString() : String {
        val name = date.toFormat("EEEE").capitalize()
        val num = date.toFormat("d")
        val menu = food.joinToString()
        return "$name $num:\n$menu"
    }
}