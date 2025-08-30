package com.cyberflux.qwinai.utils

import android.net.Uri
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import timber.log.Timber
import androidx.core.net.toUri

/**
 * Enhanced JsonAdapter for Moshi to handle Uri serialization and deserialization
 * with support for persistent file storage
 */
class UriTypeAdapter() : JsonAdapter<Uri>() {

    override fun toJson(writer: JsonWriter, value: Uri?) {
        if (value == null) {
            writer.nullValue()
        } else {
            // Just serialize the URI as a string
            writer.value(value.toString())
        }
    }

    override fun fromJson(reader: JsonReader): Uri? {
        if (reader.peek() == JsonReader.Token.NULL) {
            reader.nextNull<Unit>()
            return null
        }
        
        val uriString = reader.nextString()
        return if (uriString.isNullOrEmpty()) {
            null
        } else {
            try {
                // First try to parse as standard URI
                uriString.toUri()
            } catch (e: Exception) {
                Timber.e(e, "Error parsing URI: $uriString")
                null
            }
        }
    }
}