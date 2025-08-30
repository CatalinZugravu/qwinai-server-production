package com.cyberflux.qwinai.network

import android.content.Context
import com.cyberflux.qwinai.utils.ErrorManager
import kotlinx.coroutines.delay
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Enhanced network error handler with intelligent retry strategies
 * Works in conjunction with ErrorManager for comprehensive error handling
 */
class NetworkErrorHandler(
    private val context: Context,
    private val errorManager: ErrorManager
) {
    
    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val BASE_DELAY_MS = 1000L
        private const val MAX_DELAY_MS = 30000L
        private const val JITTER_RANGE = 0.1
    }
    
    /**
     * Retry configuration for different error types
     */
    data class RetryConfig(
        val maxAttempts: Int = MAX_RETRY_ATTEMPTS,
        val baseDelayMs: Long = BASE_DELAY_MS,
        val maxDelayMs: Long = MAX_DELAY_MS,
        val useExponentialBackoff: Boolean = true,
        val useJitter: Boolean = true
    )
    
    /**
     * Execute network operation with intelligent retry logic
     */
    suspend fun <T> executeWithRetry(
        operation: String,
        config: RetryConfig = RetryConfig(),
        networkCall: suspend () -> T
    ): Result<T> {
        var lastException: Exception? = null
        
        repeat(config.maxAttempts) { attempt ->
            try {
                Timber.d("Executing network operation: $operation (attempt ${attempt + 1}/${config.maxAttempts})")
                
                val result = networkCall()
                
                // Log successful recovery if this wasn't the first attempt
                if (attempt > 0) {
                    Timber.i("Network operation recovered after ${attempt + 1} attempts: $operation")
                }
                
                return Result.success(result)
                
            } catch (e: Exception) {
                lastException = e
                Timber.w(e, "Network operation failed (attempt ${attempt + 1}/${config.maxAttempts}): $operation")
                
                // Don't retry on the last attempt
                if (attempt == config.maxAttempts - 1) {
                    return@repeat
                }
                
                // Check if error is retryable
                if (!isRetryableError(e)) {
                    Timber.d("Error is not retryable, stopping retry attempts: ${e.javaClass.simpleName}")
                    return@repeat
                }
                
                // Calculate delay for next attempt
                val delayMs = calculateRetryDelay(attempt, config)
                Timber.d("Retrying in ${delayMs}ms...")
                delay(delayMs)
            }
        }
        
        // All retries failed, handle the error
        val finalException = lastException ?: Exception("Unknown network error")
        val errorResult = errorManager.handleError(finalException, operation, showToUser = false)
        
        Timber.e("Network operation failed after all retry attempts: $operation")
        return Result.failure(finalException)
    }
    
    /**
     * Determine if an error is retryable
     */
    private fun isRetryableError(error: Exception): Boolean {
        return when (error) {
            // Network connectivity issues - retryable
            is UnknownHostException,
            is ConnectException,
            is SocketTimeoutException,
            is IOException -> true
            
            // HTTP errors - selective retry
            is HttpException -> when (error.code()) {
                // Client errors - generally not retryable
                400, 401, 403, 404, 422 -> false
                
                // Rate limiting - retryable with backoff
                429 -> true
                
                // Server errors - retryable
                in 500..599 -> true
                
                // Other HTTP errors - not retryable by default
                else -> false
            }
            
            // Other exceptions - generally not retryable
            else -> false
        }
    }
    
    /**
     * Calculate delay for retry with exponential backoff and jitter
     */
    private fun calculateRetryDelay(attempt: Int, config: RetryConfig): Long {
        val baseDelay = if (config.useExponentialBackoff) {
            (config.baseDelayMs * 2.0.pow(attempt)).toLong()
        } else {
            config.baseDelayMs
        }
        
        val cappedDelay = min(baseDelay, config.maxDelayMs)
        
        return if (config.useJitter) {
            addJitter(cappedDelay)
        } else {
            cappedDelay
        }
    }
    
    /**
     * Add jitter to delay to avoid thundering herd problem
     */
    private fun addJitter(delay: Long): Long {
        val jitterAmount = (delay * JITTER_RANGE).toLong()
        val jitter = Random.nextLong(-jitterAmount, jitterAmount)
        return maxOf(0, delay + jitter)
    }
    
    /**
     * Create retry configuration for different operation types
     */
    object RetryConfigs {
        
        /**
         * Configuration for critical operations (authentication, billing)
         */
        val CRITICAL = RetryConfig(
            maxAttempts = 2,
            baseDelayMs = 500L,
            maxDelayMs = 5000L,
            useExponentialBackoff = false,
            useJitter = true
        )
        
        /**
         * Configuration for API calls
         */
        val API_CALL = RetryConfig(
            maxAttempts = 3,
            baseDelayMs = 1000L,
            maxDelayMs = 15000L,
            useExponentialBackoff = true,
            useJitter = true
        )
        
        /**
         * Configuration for streaming operations
         */
        val STREAMING = RetryConfig(
            maxAttempts = 2,
            baseDelayMs = 2000L,
            maxDelayMs = 10000L,
            useExponentialBackoff = true,
            useJitter = false
        )
        
        /**
         * Configuration for file operations
         */
        val FILE_OPERATION = RetryConfig(
            maxAttempts = 3,
            baseDelayMs = 1500L,
            maxDelayMs = 20000L,
            useExponentialBackoff = true,
            useJitter = true
        )
        
        /**
         * Configuration for non-critical operations
         */
        val NON_CRITICAL = RetryConfig(
            maxAttempts = 2,
            baseDelayMs = 1000L,
            maxDelayMs = 8000L,
            useExponentialBackoff = false,
            useJitter = true
        )
    }
    
    /**
     * Handle specific network scenarios
     */
    object Scenarios {
        
        /**
         * Handle API authentication with retry
         */
        suspend fun <T> handleAuthenticatedCall(
            errorHandler: NetworkErrorHandler,
            operation: String,
            call: suspend () -> T
        ): Result<T> {
            return errorHandler.executeWithRetry(
                operation = "auth_$operation",
                config = RetryConfigs.CRITICAL,
                networkCall = call
            )
        }
        
        /**
         * Handle streaming with specific retry logic
         */
        suspend fun <T> handleStreamingCall(
            errorHandler: NetworkErrorHandler,
            operation: String,
            call: suspend () -> T
        ): Result<T> {
            return errorHandler.executeWithRetry(
                operation = "stream_$operation",
                config = RetryConfigs.STREAMING,
                networkCall = call
            )
        }
        
        /**
         * Handle file upload/download with retry
         */
        suspend fun <T> handleFileCall(
            errorHandler: NetworkErrorHandler,
            operation: String,
            call: suspend () -> T
        ): Result<T> {
            return errorHandler.executeWithRetry(
                operation = "file_$operation",
                config = RetryConfigs.FILE_OPERATION,
                networkCall = call
            )
        }
    }
    
    /**
     * Monitor network health and adapt retry strategies
     */
    class NetworkHealthMonitor {
        
        private var consecutiveFailures = 0
        private var lastSuccessTime = System.currentTimeMillis()
        private var isHealthy = true
        
        /**
         * Record successful network operation
         */
        fun recordSuccess() {
            consecutiveFailures = 0
            lastSuccessTime = System.currentTimeMillis()
            isHealthy = true
            Timber.v("Network health: SUCCESS - consecutive failures reset")
        }
        
        /**
         * Record failed network operation
         */
        fun recordFailure() {
            consecutiveFailures++
            val timeSinceLastSuccess = System.currentTimeMillis() - lastSuccessTime
            
            isHealthy = consecutiveFailures < 3 && timeSinceLastSuccess < 60000 // 1 minute
            
            Timber.v("Network health: FAILURE - consecutive: $consecutiveFailures, healthy: $isHealthy")
        }
        
        /**
         * Get adaptive retry configuration based on network health
         */
        fun getAdaptiveConfig(baseConfig: RetryConfig): RetryConfig {
            return if (isHealthy) {
                baseConfig
            } else {
                // Reduce retry attempts and increase delays when network is unhealthy
                baseConfig.copy(
                    maxAttempts = maxOf(1, baseConfig.maxAttempts - 1),
                    baseDelayMs = baseConfig.baseDelayMs * 2,
                    maxDelayMs = baseConfig.maxDelayMs * 2
                )
            }
        }
        
        /**
         * Check if network appears healthy
         */
        fun isNetworkHealthy(): Boolean = isHealthy
        
        /**
         * Get health statistics
         */
        fun getHealthStats(): Map<String, Any> {
            return mapOf(
                "consecutiveFailures" to consecutiveFailures,
                "timeSinceLastSuccess" to (System.currentTimeMillis() - lastSuccessTime),
                "isHealthy" to isHealthy
            )
        }
    }
}