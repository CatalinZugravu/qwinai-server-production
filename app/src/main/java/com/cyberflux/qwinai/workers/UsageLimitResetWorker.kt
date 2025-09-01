package com.cyberflux.qwinai.workers

import android.content.Context
import androidx.work.*
import com.cyberflux.qwinai.credits.CreditManager
import com.cyberflux.qwinai.utils.UnifiedUsageManager
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Background worker to reset daily usage limits at midnight
 * 
 * This worker handles:
 * - Daily request limits for premium users (30/day reset)
 * - Image generation limits for premium users (25/day reset) 
 * - Credit system reset (handled by CreditManager)
 * - Cleanup of old usage data
 */
class UsageLimitResetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        const val WORK_TAG = "usage_limit_reset"
        private const val WORK_NAME = "periodic_usage_limit_reset"
        
        /**
         * Schedule daily usage limit reset
         */
        fun scheduleResetWorker(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(false)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .setRequiresStorageNotLow(false)
                .build()
                
            val resetRequest = PeriodicWorkRequestBuilder<UsageLimitResetWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.DAYS,
                flexTimeInterval = 2, // Allow 2 hour window
                flexTimeIntervalUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .addTag(WORK_TAG)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP, // Keep existing schedule
                    resetRequest
                )
            
            Timber.d("🔄 Scheduled daily usage limit reset worker")
        }
        
        /**
         * Cancel the reset worker
         */
        fun cancelResetWorker(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Timber.d("❌ Cancelled usage limit reset worker")
        }
        
        /**
         * Force immediate reset (for testing)
         */
        fun forceReset(context: Context) {
            val oneTimeRequest = OneTimeWorkRequestBuilder<UsageLimitResetWorker>()
                .addTag(WORK_TAG)
                .build()
                
            WorkManager.getInstance(context).enqueue(oneTimeRequest)
            Timber.d("🚀 Force reset usage limits requested")
        }
    }
    
    override suspend fun doWork(): Result {
        return try {
            Timber.d("🔄 Starting daily usage limit reset...")
            
            val startTime = System.currentTimeMillis()
            
            // Reset credit system (existing functionality)
            resetCreditSystem()
            
            // Reset unified usage limits (new functionality)
            resetUnifiedUsageLimits()
            
            // Cleanup old data
            cleanupOldData()
            
            val duration = System.currentTimeMillis() - startTime
            Timber.d("✅ Usage limit reset completed in ${duration}ms")
            
            Result.success(createSuccessData(duration))
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Error during usage limit reset: ${e.message}")
            
            // Retry up to 3 times with exponential backoff
            if (runAttemptCount < 3) {
                Timber.d("🔄 Retry attempt ${runAttemptCount + 1}/3")
                Result.retry()
            } else {
                Result.failure(createErrorData(e))
            }
        }
    }
    
    /**
     * Reset the existing credit system 
     */
    private fun resetCreditSystem() {
        try {
            val creditManager = CreditManager.getInstance(applicationContext)
            creditManager.forceReset()
            Timber.d("✅ Credit system reset completed")
        } catch (e: Exception) {
            Timber.e(e, "❌ Error resetting credit system")
            throw e
        }
    }
    
    /**
     * Reset unified usage limits (daily requests, image generation)
     */
    private fun resetUnifiedUsageLimits() {
        try {
            val unifiedUsageManager = UnifiedUsageManager.getInstance(applicationContext)
            unifiedUsageManager.resetDailyUsage()
            Timber.d("✅ Unified usage limits reset completed")
        } catch (e: Exception) {
            Timber.e(e, "❌ Error resetting unified usage limits")
            throw e
        }
    }
    
    /**
     * Cleanup old usage data to prevent storage bloat
     */
    private fun cleanupOldData() {
        try {
            // The cleanup is handled by resetDailyUsage() in UnifiedUsageManager
            // which removes old daily tracking data automatically
            Timber.d("✅ Old data cleanup completed")
        } catch (e: Exception) {
            Timber.e(e, "❌ Error during data cleanup")
            // Don't throw here - cleanup failure shouldn't fail the entire reset
        }
    }
    
    /**
     * Create success data for WorkManager
     */
    private fun createSuccessData(duration: Long): Data {
        return Data.Builder()
            .putLong("reset_duration_ms", duration)
            .putLong("reset_timestamp", System.currentTimeMillis())
            .putString("status", "success")
            .build()
    }
    
    /**
     * Create error data for WorkManager
     */
    private fun createErrorData(error: Throwable): Data {
        return Data.Builder()
            .putLong("error_timestamp", System.currentTimeMillis())
            .putString("error_message", error.message ?: "Unknown error")
            .putString("error_type", error.javaClass.simpleName)
            .putString("status", "failed")
            .build()
    }
}