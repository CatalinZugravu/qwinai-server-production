package com.cyberflux.qwinai.utils

import android.content.Context
import android.os.StatFs
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manages file caching and cleanup
 */
object FileCacheManager {
    // Cache directory names
    private const val TEMP_FILES_DIR = "temp_files"
    private const val DOCUMENT_CACHE_DIR = "document_cache"
    private const val IMAGE_CACHE_DIR = "image_cache"

    private const val MAX_FILE_AGE_HOURS = 24  // Keep files for 24 hours max
    private const val LOW_STORAGE_THRESHOLD_MB = 500  // 500MB free space threshold

    /**
     * Initialize the cache system
     */
    fun initialize(context: Context) {
        // Create cache directories
        createCacheDirectories(context)

        // Schedule cleanup worker
        scheduleCacheCleanupWorker(context)
    }

    /**
     * Create required cache directories
     */
    private fun createCacheDirectories(context: Context) {
        File(context.cacheDir, TEMP_FILES_DIR).mkdirs()
        File(context.cacheDir, DOCUMENT_CACHE_DIR).mkdirs()
        File(context.cacheDir, IMAGE_CACHE_DIR).mkdirs()
    }

    /**
     * Clean expired files from the cache
     */
    suspend fun cleanExpiredFiles(context: Context): CleanupResult = withContext(Dispatchers.IO) {
        Timber.d("Starting cache cleanup")
        var deletedFiles = 0
        var freedSpace = 0L
        val startTime = System.currentTimeMillis()

        // Maximum age timestamp
        val maxAgeTimestamp = System.currentTimeMillis() - (MAX_FILE_AGE_HOURS * 60 * 60 * 1000)

        // Clean temporary files directory
        cleanDirectory(File(context.cacheDir, TEMP_FILES_DIR), maxAgeTimestamp).let {
            deletedFiles += it.deletedFiles
            freedSpace += it.freedSpace
        }

        // Clean document cache directory
        cleanDirectory(File(context.cacheDir, DOCUMENT_CACHE_DIR), maxAgeTimestamp).let {
            deletedFiles += it.deletedFiles
            freedSpace += it.freedSpace
        }

        // Clean image cache directory
        cleanDirectory(File(context.cacheDir, IMAGE_CACHE_DIR), maxAgeTimestamp).let {
            deletedFiles += it.deletedFiles
            freedSpace += it.freedSpace
        }

        // Check if device storage is still low - add more aggressive cleanup if needed
        if (isStorageLow(context) && isActive) {
            Timber.d("Storage still low after age-based cleanup, performing size-based cleanup")
            val additionalCleanup = performSizeBasedCleanup(context)
            deletedFiles += additionalCleanup.deletedFiles
            freedSpace += additionalCleanup.freedSpace
        }

        val duration = System.currentTimeMillis() - startTime
        Timber.d("Cache cleanup completed in ${duration}ms: Deleted $deletedFiles files, freed ${freedSpace / (1024 * 1024)}MB")

        CleanupResult(deletedFiles, freedSpace, duration)
    }

    /**
     * Clean a specific directory of old files
     */
    private suspend fun cleanDirectory(directory: File, maxAgeTimestamp: Long): CleanupResult = withContext(Dispatchers.IO) {
        if (!directory.exists() || !directory.isDirectory) {
            return@withContext CleanupResult(0, 0, 0)
        }

        var deletedFiles = 0
        var freedSpace = 0L

        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < maxAgeTimestamp) {
                val fileSize = file.length()
                if (file.delete()) {
                    deletedFiles++
                    freedSpace += fileSize
                }
            }
        }

        CleanupResult(deletedFiles, freedSpace, 0)
    }

    /**
     * Perform size-based cleanup when storage is low
     */
    private suspend fun performSizeBasedCleanup(context: Context): CleanupResult = withContext(Dispatchers.IO) {
        var deletedFiles = 0
        var freedSpace = 0L

        // Get all cache files
        val allFiles = mutableListOf<File>()
        File(context.cacheDir, TEMP_FILES_DIR).listFiles()?.let { allFiles.addAll(it) }
        File(context.cacheDir, DOCUMENT_CACHE_DIR).listFiles()?.let { allFiles.addAll(it) }
        File(context.cacheDir, IMAGE_CACHE_DIR).listFiles()?.let { allFiles.addAll(it) }

        // Sort by last modified time (oldest first)
        allFiles.sortBy { it.lastModified() }

        // Delete oldest files until we've freed enough space or reached half the files
        val filesToDelete = allFiles.take(allFiles.size / 2)
        for (file in filesToDelete) {
            if (file.isFile) {
                val fileSize = file.length()
                if (file.delete()) {
                    deletedFiles++
                    freedSpace += fileSize
                }
            }

            // If we've freed enough space, stop
            if (freedSpace > 100 * 1024 * 1024) {  // 100MB freed
                break
            }
        }

        CleanupResult(deletedFiles, freedSpace, 0)
    }

    /**
     * Check if device storage is running low
     */
    private fun isStorageLow(context: Context): Boolean {
        val stat = StatFs(context.cacheDir.path)
        val availableBytes =
            stat.availableBytes

        val availableMB = availableBytes / (1024 * 1024)
        return availableMB < LOW_STORAGE_THRESHOLD_MB
    }

    /**
     * Schedule the cache cleanup worker
     */
    private fun scheduleCacheCleanupWorker(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val cacheCleanupRequest = PeriodicWorkRequestBuilder<CacheCleanupWorker>(
            3, TimeUnit.HOURS,  // Run every 3 hours
            30, TimeUnit.MINUTES  // With 30 minute flex period
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "file_cache_cleanup",
            ExistingPeriodicWorkPolicy.UPDATE,
            cacheCleanupRequest
        )
    }

    /**
     * Cache cleanup result data class
     */
    data class CleanupResult(
        val deletedFiles: Int,
        val freedSpace: Long,
        val duration: Long
    )
}

/**
 * Worker for cache cleanup
 */
class CacheCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            FileCacheManager.cleanExpiredFiles(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Error in cache cleanup worker: ${e.message}")
            Result.retry()
        }
    }
}