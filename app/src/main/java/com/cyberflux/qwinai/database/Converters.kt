package com.cyberflux.qwinai.database

import androidx.room.TypeConverter
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.network.AimlApiResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.ArrayList

/**
 * Type converters for complex types in the database
 * These converters allow Room to store complex types in SQLite
 */
class Converters {
    private val gson = Gson()

    /**
     * Convert from String to List<String>
     */
    @TypeConverter
    fun stringToStringList(value: String?): List<String> {
        if (value == null) {
            return emptyList()
        }
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }

    /**
     * Convert from List<String> to String
     */
    @TypeConverter
    fun stringListToString(list: List<String>?): String {
        return gson.toJson(list ?: emptyList<String>())
    }

    /**
     * Convert from LocationInfo to String
     */
    @TypeConverter
    fun locationInfoToString(locationInfo: ChatMessage.LocationInfo?): String? {
        return if (locationInfo == null) null else gson.toJson(locationInfo)
    }

    /**
     * Convert from String to LocationInfo
     */
    @TypeConverter
    fun stringToLocationInfo(locationInfoString: String?): ChatMessage.LocationInfo? {
        return if (locationInfoString == null) null else gson.fromJson(locationInfoString, ChatMessage.LocationInfo::class.java)
    }

    /**
     * Convert from MutableList<String> to String
     * Separate method for MutableList to handle specific type
     */
    @TypeConverter
    fun mutableStringListToString(list: MutableList<String>?): String {
        return gson.toJson(list ?: mutableListOf<String>())
    }

    /**
     * Convert from AimlApiResponse.AudioData to String
     */
    @TypeConverter
    fun audioDataToString(audioData: AimlApiResponse.AudioData?): String? {
        return if (audioData == null) null else gson.toJson(audioData)
    }

    /**
     * Convert from String to AimlApiResponse.AudioData
     */
    @TypeConverter
    fun stringToAudioData(audioDataString: String?): AimlApiResponse.AudioData? {
        return if (audioDataString == null) null else gson.fromJson(audioDataString, AimlApiResponse.AudioData::class.java)
    }

    /**
     * Convert from String to MutableList<String>
     * Separate method for MutableList to handle specific type
     */
    @TypeConverter
    fun stringToMutableStringList(value: String?): MutableList<String> {
        if (value == null) {
            return mutableListOf()
        }
        val listType = object : TypeToken<MutableList<String>>() {}.type
        return gson.fromJson(value, listType)
    }

    /**
     * Convert from Map<String, String> to String for database storage
     */
    @TypeConverter
    fun mapToString(map: Map<String, String>?): String {
        return if (map == null || map.isEmpty()) {
            ""
        } else {
            gson.toJson(map)
        }
    }

    /**
     * Convert from String to Map<String, String> when reading from database
     */
    @TypeConverter
    fun stringToMap(value: String?): Map<String, String> {
        if (value.isNullOrEmpty()) {
            return emptyMap()
        }

        val mapType = object : TypeToken<Map<String, String>>() {}.type
        return gson.fromJson(value, mapType)
    }

    /**
     * Convert from List<ToolCallResult> to String for database storage
     */
    @TypeConverter
    fun toolCallResultsToString(toolCalls: List<ChatMessage.ToolCallResult>?): String {
        return if (toolCalls == null || toolCalls.isEmpty()) {
            ""
        } else {
            gson.toJson(toolCalls)
        }
    }

    /**
     * Convert from String to List<ToolCallResult> when reading from database
     */
    @TypeConverter
    fun stringToToolCallResults(value: String?): List<ChatMessage.ToolCallResult> {
        if (value.isNullOrEmpty()) {
            return emptyList()
        }

        val listType = object : TypeToken<List<ChatMessage.ToolCallResult>>() {}.type
        return gson.fromJson(value, listType)
    }

    /**
     * Convert from Map<String, Any> to String for database storage
     */
    @TypeConverter
    fun anyMapToString(map: Map<String, Any>?): String {
        return if (map == null || map.isEmpty()) {
            ""
        } else {
            gson.toJson(map)
        }
    }

    /**
     * Convert from String to Map<String, Any> when reading from database
     */
    @TypeConverter
    fun stringToAnyMap(value: String?): Map<String, Any> {
        if (value.isNullOrEmpty()) {
            return emptyMap()
        }

        val mapType = object : TypeToken<Map<String, Any>>() {}.type
        return gson.fromJson(value, mapType)
    }
}