package com.cyberflux.qwinai.database

import androidx.room.TypeConverter
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.network.AimlApiResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Type converters for complex types in the database
 * These converters allow Room to store complex types in SQLite
 */
class Converters {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    /**
     * Convert from String to List<String>
     */
    @TypeConverter
    fun stringToStringList(value: String?): List<String> {
        if (value == null) {
            return emptyList()
        }
        val listType = Types.newParameterizedType(List::class.java, String::class.java)
        val adapter = moshi.adapter<List<String>>(listType)
        return adapter.fromJson(value) ?: emptyList()
    }

    /**
     * Convert from List<String> to String
     */
    @TypeConverter
    fun stringListToString(list: List<String>?): String {
        val listType = Types.newParameterizedType(List::class.java, String::class.java)
        val adapter = moshi.adapter<List<String>>(listType)
        return adapter.toJson(list ?: emptyList())
    }

    /**
     * Convert from LocationInfo to String
     */
    @TypeConverter
    fun locationInfoToString(locationInfo: ChatMessage.LocationInfo?): String? {
        return if (locationInfo == null) null else moshi.adapter(ChatMessage.LocationInfo::class.java).toJson(locationInfo)
    }

    /**
     * Convert from String to LocationInfo
     */
    @TypeConverter
    fun stringToLocationInfo(locationInfoString: String?): ChatMessage.LocationInfo? {
        return if (locationInfoString == null) null else moshi.adapter(ChatMessage.LocationInfo::class.java).fromJson(locationInfoString)
    }

    /**
     * Convert from MutableList<String> to String
     * Separate method for MutableList to handle specific type
     */
    @TypeConverter
    fun mutableStringListToString(list: MutableList<String>?): String {
        val listType = Types.newParameterizedType(List::class.java, String::class.java)
        val adapter = moshi.adapter<List<String>>(listType)
        return adapter.toJson(list ?: mutableListOf())
    }

    /**
     * Convert from AimlApiResponse.AudioData to String
     */
    @TypeConverter
    fun audioDataToString(audioData: AimlApiResponse.AudioData?): String? {
        return if (audioData == null) null else moshi.adapter(AimlApiResponse.AudioData::class.java).toJson(audioData)
    }

    /**
     * Convert from String to AimlApiResponse.AudioData
     */
    @TypeConverter
    fun stringToAudioData(audioDataString: String?): AimlApiResponse.AudioData? {
        return if (audioDataString == null) null else moshi.adapter(AimlApiResponse.AudioData::class.java).fromJson(audioDataString)
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
        val listType = Types.newParameterizedType(List::class.java, String::class.java)
        val adapter = moshi.adapter<List<String>>(listType)
        val result = adapter.fromJson(value) ?: emptyList()
        return result.toMutableList()
    }

    /**
     * Convert from Map<String, String> to String for database storage
     */
    @TypeConverter
    fun mapToString(map: Map<String, String>?): String {
        return if (map == null || map.isEmpty()) {
            ""
        } else {
            val mapType = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
            val adapter = moshi.adapter<Map<String, String>>(mapType)
            adapter.toJson(map)
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

        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
        val adapter = moshi.adapter<Map<String, String>>(mapType)
        return adapter.fromJson(value) ?: emptyMap()
    }

    /**
     * Convert from List<ToolCallResult> to String for database storage
     */
    @TypeConverter
    fun toolCallResultsToString(toolCalls: List<ChatMessage.ToolCallResult>?): String {
        return if (toolCalls == null || toolCalls.isEmpty()) {
            ""
        } else {
            val listType = Types.newParameterizedType(List::class.java, ChatMessage.ToolCallResult::class.java)
            val adapter = moshi.adapter<List<ChatMessage.ToolCallResult>>(listType)
            adapter.toJson(toolCalls)
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

        val listType = Types.newParameterizedType(List::class.java, ChatMessage.ToolCallResult::class.java)
        val adapter = moshi.adapter<List<ChatMessage.ToolCallResult>>(listType)
        return adapter.fromJson(value) ?: emptyList()
    }

    /**
     * Convert from Map<String, Any> to String for database storage
     */
    @TypeConverter
    fun anyMapToString(map: Map<String, Any>?): String {
        return if (map == null || map.isEmpty()) {
            ""
        } else {
            val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val adapter = moshi.adapter<Map<String, Any>>(mapType)
            adapter.toJson(map)
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

        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val adapter = moshi.adapter<Map<String, Any>>(mapType)
        return adapter.fromJson(value) ?: emptyMap()
    }
}