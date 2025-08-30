package com.cyberflux.qwinai.utils

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Factory class to create properly configured Moshi instances
 */
object MoshiFactory {

    /**
     * Creates a Moshi instance with all necessary adapters registered
     */
    fun createMoshi(context: Context): Moshi {
        return Moshi.Builder()
            .add(UriTypeAdapter())
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    /**
     * Creates a basic Moshi instance without custom adapters
     */
    fun createBasicMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }
}