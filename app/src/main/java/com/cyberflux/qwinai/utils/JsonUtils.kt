package com.cyberflux.qwinai.utils

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Utility class to handle JSON serialization/deserialization with Moshi
 * This replaces Gson usage throughout the app
 */
object JsonUtils {
    private val moshi = Moshi.Builder()
        .build()

    /**
     * Convert object to JSON string
     */
    fun toJson(obj: Any?): String {
        return if (obj == null) {
            ""
        } else {
            try {
                @Suppress("UNCHECKED_CAST")
                val adapter = moshi.adapter(obj::class.java) as com.squareup.moshi.JsonAdapter<Any>
                adapter.toJson(obj)
            } catch (e: Exception) {
                ""
            }
        }
    }

    /**
     * Convert JSON string to object
     */
    fun <T> fromJson(json: String?, clazz: Class<T>): T? {
        return if (json.isNullOrEmpty()) {
            null
        } else {
            try {
                val adapter = moshi.adapter(clazz)
                adapter.fromJson(json)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Convert JSON string to List of objects
     */
    fun <T> fromJsonList(json: String?, clazz: Class<T>): List<T>? {
        return if (json.isNullOrEmpty()) {
            null
        } else {
            try {
                val listType = Types.newParameterizedType(List::class.java, clazz)
                val adapter = moshi.adapter<List<T>>(listType)
                adapter.fromJson(json)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Convert JSON string to Map
     */
    fun <K, V> fromJsonMap(json: String?, keyClass: Class<K>, valueClass: Class<V>): Map<K, V>? {
        return if (json.isNullOrEmpty()) {
            null
        } else {
            try {
                val mapType = Types.newParameterizedType(Map::class.java, keyClass, valueClass)
                val adapter = moshi.adapter<Map<K, V>>(mapType)
                adapter.fromJson(json)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Convert List to JSON string
     */
    fun listToJson(list: List<*>?): String {
        return if (list == null) {
            ""
        } else {
            try {
                val listType = Types.newParameterizedType(List::class.java, list.firstOrNull()?.javaClass ?: Any::class.java)
                val adapter = moshi.adapter<List<*>>(listType)
                adapter.toJson(list)
            } catch (e: Exception) {
                ""
            }
        }
    }

    /**
     * Simple JSON serialization for GeneratedImage list using manual approach
     * This avoids Kotlin reflection issues in release builds
     */
    fun generatedImagesToJson(images: List<com.cyberflux.qwinai.model.GeneratedImage>): String {
        return try {
            val jsonBuilder = StringBuilder()
            jsonBuilder.append("[")
            
            images.forEachIndexed { index, image ->
                if (index > 0) jsonBuilder.append(",")
                jsonBuilder.append("{")
                jsonBuilder.append("\"id\":\"${image.id}\",")
                jsonBuilder.append("\"filePath\":\"${image.filePath.replace("\\", "\\\\").replace("\"", "\\\"")}\",")
                jsonBuilder.append("\"fileName\":\"${image.fileName.replace("\"", "\\\"")}\",")
                jsonBuilder.append("\"aiModel\":\"${image.aiModel.replace("\"", "\\\"")}\",")
                jsonBuilder.append("\"prompt\":\"${image.prompt.replace("\"", "\\\"")}\",")
                jsonBuilder.append("\"timestamp\":${image.timestamp},")
                jsonBuilder.append("\"fileSize\":${image.fileSize},")
                jsonBuilder.append("\"width\":${image.width},")
                jsonBuilder.append("\"height\":${image.height},")
                jsonBuilder.append("\"isDownloaded\":${image.isDownloaded}")
                
                // Handle optional generationSettings
                image.generationSettings?.let { settings ->
                    jsonBuilder.append(",\"generationSettings\":{")
                    val settingsParts = mutableListOf<String>()
                    settings.style?.let { settingsParts.add("\"style\":\"${it.replace("\"", "\\\"")}\"") }
                    settings.quality?.let { settingsParts.add("\"quality\":\"${it.replace("\"", "\\\"")}\"") }
                    settings.aspectRatio?.let { settingsParts.add("\"aspectRatio\":\"${it.replace("\"", "\\\"")}\"") }
                    settings.seed?.let { settingsParts.add("\"seed\":\"${it.replace("\"", "\\\"")}\"") }
                    jsonBuilder.append(settingsParts.joinToString(","))
                    jsonBuilder.append("}")
                }
                
                jsonBuilder.append("}")
            }
            
            jsonBuilder.append("]")
            jsonBuilder.toString()
        } catch (e: Exception) {
            android.util.Log.e("JsonUtils", "Error serializing GeneratedImage list", e)
            "[]"
        }
    }

    /**
     * Simple JSON deserialization for GeneratedImage list using manual parsing
     * This avoids Kotlin reflection issues in release builds
     */
    fun jsonToGeneratedImages(json: String?): List<com.cyberflux.qwinai.model.GeneratedImage> {
        return if (json.isNullOrEmpty() || json == "[]") {
            emptyList()
        } else {
            try {
                // Use Moshi for basic JSON parsing without reflection
                val adapter = moshi.adapter(Any::class.java)
                val parsed = adapter.fromJson(json) as? List<*> ?: return emptyList()
                
                parsed.mapNotNull { item ->
                    try {
                        val map = item as? Map<*, *> ?: return@mapNotNull null
                        
                        val generationSettings = map["generationSettings"]?.let { settingsMap ->
                            val settings = settingsMap as? Map<*, *>
                            com.cyberflux.qwinai.model.GenerationSettings(
                                style = settings?.get("style") as? String,
                                quality = settings?.get("quality") as? String,
                                aspectRatio = settings?.get("aspectRatio") as? String,
                                seed = settings?.get("seed") as? String
                            )
                        }
                        
                        com.cyberflux.qwinai.model.GeneratedImage(
                            id = map["id"] as? String ?: return@mapNotNull null,
                            filePath = map["filePath"] as? String ?: return@mapNotNull null,
                            fileName = map["fileName"] as? String ?: return@mapNotNull null,
                            aiModel = map["aiModel"] as? String ?: return@mapNotNull null,
                            prompt = map["prompt"] as? String ?: return@mapNotNull null,
                            timestamp = (map["timestamp"] as? Number)?.toLong() ?: return@mapNotNull null,
                            fileSize = (map["fileSize"] as? Number)?.toLong() ?: return@mapNotNull null,
                            width = (map["width"] as? Number)?.toInt() ?: return@mapNotNull null,
                            height = (map["height"] as? Number)?.toInt() ?: return@mapNotNull null,
                            isDownloaded = map["isDownloaded"] as? Boolean ?: false,
                            generationSettings = generationSettings
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("JsonUtils", "Failed to parse individual image", e)
                        null
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("JsonUtils", "Error deserializing GeneratedImage list", e)
                emptyList()
            }
        }
    }

    /**
     * Convert Map to JSON string
     */
    fun mapToJson(map: Map<*, *>?): String {
        return if (map == null) {
            ""
        } else {
            try {
                val keyClass = map.keys.firstOrNull()?.javaClass ?: Any::class.java
                val valueClass = map.values.firstOrNull()?.javaClass ?: Any::class.java
                val mapType = Types.newParameterizedType(Map::class.java, keyClass, valueClass)
                val adapter = moshi.adapter<Map<*, *>>(mapType)
                adapter.toJson(map)
            } catch (e: Exception) {
                ""
            }
        }
    }
}