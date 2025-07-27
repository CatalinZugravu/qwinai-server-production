package com.cyberflux.qwinai.utils

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * Factory class to create properly configured Gson instances
 */
object GsonFactory {

    /**
     * Creates a Gson instance with all necessary type adapters registered
     */
    fun createGson(context: Context): Gson {
        return GsonBuilder()
            .registerTypeAdapter(Uri::class.java, UriTypeAdapter(context))
            .create()
    }
}