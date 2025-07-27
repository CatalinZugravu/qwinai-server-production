package com.cyberflux.qwinai.utils

import android.content.Context
import android.net.Uri
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import timber.log.Timber

/**
 * Enhanced TypeAdapter for Gson to handle Uri serialization and deserialization
 * with support for persistent file storage
 */
class UriTypeAdapter(private val context: Context) : TypeAdapter<Uri>() {

    override fun write(out: JsonWriter, value: Uri?) {
        if (value == null) {
            out.nullValue()
        } else {
            // Just serialize the URI as a string
            out.value(value.toString())
        }
    }

    override fun read(reader: JsonReader): Uri? {
        val uriString = reader.nextString()
        return if (uriString.isNullOrEmpty()) {
            null
        } else {
            try {
                // First try to parse as standard URI
                Uri.parse(uriString)
            } catch (e: Exception) {
                Timber.e(e, "Error parsing URI: $uriString")
                null
            }
        }
    }
}