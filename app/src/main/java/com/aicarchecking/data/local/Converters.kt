package com.aicarchecking.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Enums are stored by name using Room's built-in enum support; lists/maps as JSON text. */
class Converters {
    private val json = Json { ignoreUnknownKeys = true }
    private val listSer = ListSerializer(String.serializer())
    private val mapSer = MapSerializer(String.serializer(), String.serializer())

    @TypeConverter
    fun fromList(value: List<String>): String = json.encodeToString(listSer, value)

    @TypeConverter
    fun toList(value: String): List<String> =
        runCatching { json.decodeFromString(listSer, value) }.getOrDefault(emptyList())

    @TypeConverter
    fun fromMap(value: Map<String, String>): String = json.encodeToString(mapSer, value)

    @TypeConverter
    fun toMap(value: String): Map<String, String> =
        runCatching { json.decodeFromString(mapSer, value) }.getOrDefault(emptyMap())
}
