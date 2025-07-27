package com.cyberflux.qwinai.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.cyberflux.qwinai.MyApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.concurrent.TimeUnit

class ConversationCleanupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return runBlocking(Dispatchers.IO) {
            try {
                // Check if auto-clean is enabled
                val sharedPrefs = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                val autoCleanEnabled = sharedPrefs.getBoolean("auto_clean_enabled", true)
                
                if (!autoCleanEnabled) {
                    Timber.d("Auto-clean is disabled, skipping cleanup")
                    return@runBlocking Result.success()
                }

                // Get cleanup interval from settings
                val cleanupIntervalDays = sharedPrefs.getInt("cleanup_interval_days", 30)
                
                // If interval is -1 (Never), skip cleanup
                if (cleanupIntervalDays == -1) {
                    Timber.d("Cleanup interval set to 'Never', skipping cleanup")
                    return@runBlocking Result.success()
                }

                val conversationDao = MyApp.database.conversationDao()
                val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(cleanupIntervalDays.toLong())

                // Only delete unsaved conversations that are older than the specified interval
                val deletedCount = conversationDao.deleteOldConversations(cutoffTime)

                Timber.d("Conversation cleanup completed: deleted $deletedCount old unsaved conversations (older than $cleanupIntervalDays days)")
                Result.success()
            } catch (e: Exception) {
                Timber.e(e, "Error during conversation cleanup: ${e.message}")
                Result.failure()
            }
        }
    }
}