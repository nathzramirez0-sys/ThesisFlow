package com.nathzramirez.thesisflow.data.local

import androidx.room.TypeConverter
import java.time.Instant

/** Stores [Instant] as epoch milliseconds so SQL can compare and sort dates. */
class Converters {
    @TypeConverter
    fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
