package com.fort.messenger.data.local

import androidx.room.TypeConverter
import com.fort.messenger.model.CardType
import com.fort.messenger.model.PassDurationType

class FortTypeConverters {

    @TypeConverter
    fun fromCardType(value: CardType): String = value.name

    @TypeConverter
    fun toCardType(value: String): CardType = try {
        CardType.valueOf(value)
    } catch (e: Exception) {
        CardType.PERSONAL
    }

    @TypeConverter
    fun fromPassDurationType(value: PassDurationType): String = value.name

    @TypeConverter
    fun toPassDurationType(value: String): PassDurationType = try {
        PassDurationType.valueOf(value)
    } catch (e: Exception) {
        PassDurationType.SEVEN_DAYS
    }
}
