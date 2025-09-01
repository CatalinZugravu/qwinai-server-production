package com.cyberflux.qwinai

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions

@GlideModule
class QwinAppGlideModule : AppGlideModule() {
    
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // Memory cache configuration - 30MB
        val memoryCacheSizeBytes = 1024 * 1024 * 30 // 30MB
        builder.setMemoryCache(LruResourceCache(memoryCacheSizeBytes.toLong()))
        
        // Disk cache configuration - 100MB
        val diskCacheSizeBytes = 1024 * 1024 * 100 // 100MB
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, diskCacheSizeBytes.toLong()))
        
        // Default request options for performance
        val requestOptions = RequestOptions()
            .format(DecodeFormat.PREFER_RGB_565) // Use less memory
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC) // Smart caching
            .timeout(10000) // 10 second timeout
        
        builder.setDefaultRequestOptions(requestOptions)
        
        // Set log level for debugging
        if (BuildConfig.DEBUG) {
            builder.setLogLevel(android.util.Log.ERROR)
        }
    }
    
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        // Register custom components if needed
        super.registerComponents(context, glide, registry)
    }
    
    override fun isManifestParsingEnabled(): Boolean {
        // Disable manifest parsing for better performance
        return false
    }
}