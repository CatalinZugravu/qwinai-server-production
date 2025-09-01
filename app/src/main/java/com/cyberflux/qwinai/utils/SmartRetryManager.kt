package com.cyberflux.qwinai.utils

import kotlinx.coroutines.*
import timber.log.Timber
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Smart retry manager with exponential backoff, jitter, and circuit breaker patterns
 * Provides intelligent retry strategies for different types of operations
 */
@Singleton
class SmartRetryManager @Inject constructor(
    private val performanceMonitor: PerformanceMonitor
) {
    
    companion object {
        private const val DEFAULT_MAX_RETRIES = 3
        private const val DEFAULT_BASE_DELAY_MS = 1000L
        private const val DEFAULT_MAX_DELAY_MS = 30000L
        private const val DEFAULT_JITTER_FACTOR = 0.1
        private const val CIRCUIT_BREAKER_FAILURE_THRESHOLD = 5
        private const val CIRCUIT_BREAKER_TIMEOUT_MS = 60000L
        private const val CIRCUIT_BREAKER_SUCCESS_THRESHOLD = 3
    }
    
    // Circuit breaker state tracking
    private val circuitBreakers = ConcurrentHashMap<String, CircuitBreakerState>()
    private val operationStats = ConcurrentHashMap<String, OperationStats>()
    
    /**
     * Circuit breaker states
     */
    enum class CircuitBreakerState {
        CLOSED,    // Normal operation
        OPEN,      // Failing fast
        HALF_OPEN  // Testing if service recovered
    }
    
    /**
     * Operation statistics
     */
    data class OperationStats(
        val consecutiveFailures: AtomicInteger = AtomicInteger(0),
        val consecutiveSuccesses: AtomicInteger = AtomicInteger(0),
        val lastFailureTime: AtomicLong = AtomicLong(0),
        val totalRetries: AtomicLong = AtomicLong(0),
        val totalSuccesses: AtomicLong = AtomicLong(0)
    )
    
    /**
     * Retry configuration
     */
    data class RetryConfig(
        val maxRetries: Int = DEFAULT_MAX_RETRIES,
        val baseDelay: Long = DEFAULT_BASE_DELAY_MS,
        val maxDelay: Long = DEFAULT_MAX_DELAY_MS,
        val jitterFactor: Double = DEFAULT_JITTER_FACTOR,
        val retryableExceptions: List<Class<out Throwable>> = defaultRetryableExceptions,
        val backoffStrategy: BackoffStrategy = BackoffStrategy.EXPONENTIAL,
        val circuitBreakerEnabled: Boolean = true,
        val customRetryCondition: ((Throwable, Int) -> Boolean)? = null
    ) {
        companion object {
            val defaultRetryableExceptions = listOf(
                SocketTimeoutException::class.java,
                ConnectException::class.java,
                UnknownHostException::class.java,
                // Add more as needed
            )
        }
    }
    
    /**
     * Backoff strategies
     */
    enum class BackoffStrategy {
        EXPONENTIAL,
        LINEAR,
        FIXED,
        FIBONACCI
    }
    
    /**
     * Retry result
     */
    sealed class RetryResult<T> {
        data class Success<T>(val result: T, val attemptCount: Int) : RetryResult<T>()
        data class Failure<T>(val lastException: Throwable, val attemptCount: Int) : RetryResult<T>()
        data class CircuitBreakerOpen<T>(val operationKey: String) : RetryResult<T>()
    }
    
    /**
     * Execute operation with smart retry logic
     */
    suspend fun <T> executeWithRetry(
        operationKey: String,
        config: RetryConfig = RetryConfig(),
        operation: suspend (attempt: Int) -> T
    ): RetryResult<T> {
        
        // Check circuit breaker
        if (config.circuitBreakerEnabled && isCircuitBreakerOpen(operationKey)) {
            Timber.w("🔄 Circuit breaker OPEN for operation: $operationKey")
            return RetryResult.CircuitBreakerOpen(operationKey)
        }
        
        val stats = operationStats.getOrPut(operationKey) { OperationStats() }
        var lastException: Throwable? = null
        
        repeat(config.maxRetries + 1) { attempt ->
            try {
                val result = performanceMonitor.measureOperation("retry_$operationKey") {
                    operation(attempt)
                }
                
                // Success - update circuit breaker and stats
                onOperationSuccess(operationKey, stats)
                stats.totalSuccesses.incrementAndGet()
                
                Timber.d("✅ Operation '$operationKey' succeeded on attempt ${attempt + 1}")
                return RetryResult.Success(result, attempt + 1)
                
            } catch (e: Exception) {
                lastException = e
                stats.totalRetries.incrementAndGet()
                
                Timber.w("❌ Operation '$operationKey' failed on attempt ${attempt + 1}: ${e.message}")
                
                // Check if this exception is retryable
                val isRetryable = isRetryableException(e, config) || 
                                config.customRetryCondition?.invoke(e, attempt) == true
                
                if (!isRetryable) {
                    Timber.w("🚫 Exception not retryable for operation '$operationKey': ${e.javaClass.simpleName}")
                    onOperationFailure(operationKey, stats)
                    return RetryResult.Failure(e, attempt + 1)
                }
                
                // Don't delay on the last attempt
                if (attempt < config.maxRetries) {
                    val delay = calculateDelay(attempt, config)
                    Timber.d("⏳ Retrying operation '$operationKey' in ${delay}ms (attempt ${attempt + 1}/${config.maxRetries + 1})")
                    
                    try {
                        delay(delay)
                    } catch (ce: CancellationException) {
                        Timber.d("🛑 Retry cancelled for operation '$operationKey'")
                        throw ce
                    }
                }
            }
        }
        
        // All retries exhausted
        onOperationFailure(operationKey, stats)
        return RetryResult.Failure(lastException!!, config.maxRetries + 1)
    }
    
    /**
     * Execute operation with timeout and retry
     */
    suspend fun <T> executeWithTimeoutAndRetry(
        operationKey: String,
        timeoutMs: Long,
        config: RetryConfig = RetryConfig(),
        operation: suspend (attempt: Int) -> T
    ): RetryResult<T> {
        return executeWithRetry(operationKey, config) { attempt ->
            withTimeout(timeoutMs) {
                operation(attempt)
            }
        }
    }
    
    /**
     * Check if exception is retryable
     */
    private fun isRetryableException(exception: Throwable, config: RetryConfig): Boolean {
        return config.retryableExceptions.any { retryableClass ->
            retryableClass.isAssignableFrom(exception.javaClass)
        }
    }
    
    /**
     * Calculate delay based on backoff strategy
     */
    private fun calculateDelay(attempt: Int, config: RetryConfig): Long {
        val baseDelay = when (config.backoffStrategy) {
            BackoffStrategy.EXPONENTIAL -> {
                (config.baseDelay * 2.0.pow(attempt)).toLong()
            }
            BackoffStrategy.LINEAR -> {
                config.baseDelay * (attempt + 1)
            }
            BackoffStrategy.FIXED -> {
                config.baseDelay
            }
            BackoffStrategy.FIBONACCI -> {
                config.baseDelay * fibonacci(attempt + 1)
            }
        }
        
        // Apply max delay limit
        val clampedDelay = min(baseDelay, config.maxDelay)
        
        // Add jitter to prevent thundering herd
        val jitter = (clampedDelay * config.jitterFactor * Random.nextDouble(-1.0, 1.0)).toLong()
        
        return maxOf(0, clampedDelay + jitter)
    }
    
    /**
     * Calculate Fibonacci number
     */
    private fun fibonacci(n: Int): Long {
        if (n <= 1) return n.toLong()
        
        var a = 0L
        var b = 1L
        repeat(n - 1) {
            val temp = a + b
            a = b
            b = temp
        }
        return b
    }
    
    /**
     * Handle operation success
     */
    private fun onOperationSuccess(operationKey: String, stats: OperationStats) {
        stats.consecutiveFailures.set(0)
        stats.consecutiveSuccesses.incrementAndGet()
        
        val circuitBreakerState = circuitBreakers[operationKey]
        if (circuitBreakerState == CircuitBreakerState.HALF_OPEN) {
            if (stats.consecutiveSuccesses.get() >= CIRCUIT_BREAKER_SUCCESS_THRESHOLD) {
                circuitBreakers[operationKey] = CircuitBreakerState.CLOSED
                Timber.i("🔄 Circuit breaker CLOSED for operation: $operationKey")
            }
        }
    }
    
    /**
     * Handle operation failure
     */
    private fun onOperationFailure(operationKey: String, stats: OperationStats) {
        stats.consecutiveFailures.incrementAndGet()
        stats.consecutiveSuccesses.set(0)
        stats.lastFailureTime.set(System.currentTimeMillis())
        
        if (stats.consecutiveFailures.get() >= CIRCUIT_BREAKER_FAILURE_THRESHOLD) {
            circuitBreakers[operationKey] = CircuitBreakerState.OPEN
            Timber.w("🔄 Circuit breaker OPEN for operation: $operationKey (${stats.consecutiveFailures.get()} consecutive failures)")
        }
    }
    
    /**
     * Check if circuit breaker is open
     */
    private fun isCircuitBreakerOpen(operationKey: String): Boolean {
        val state = circuitBreakers[operationKey] ?: CircuitBreakerState.CLOSED
        val stats = operationStats[operationKey] ?: return false
        
        return when (state) {
            CircuitBreakerState.CLOSED -> false
            CircuitBreakerState.OPEN -> {
                val timeSinceLastFailure = System.currentTimeMillis() - stats.lastFailureTime.get()
                if (timeSinceLastFailure >= CIRCUIT_BREAKER_TIMEOUT_MS) {
                    // Transition to half-open
                    circuitBreakers[operationKey] = CircuitBreakerState.HALF_OPEN
                    Timber.i("🔄 Circuit breaker HALF_OPEN for operation: $operationKey")
                    false
                } else {
                    true
                }
            }
            CircuitBreakerState.HALF_OPEN -> false
        }
    }
    
    /**
     * Manually reset circuit breaker
     */
    fun resetCircuitBreaker(operationKey: String) {
        circuitBreakers[operationKey] = CircuitBreakerState.CLOSED
        operationStats[operationKey]?.apply {
            consecutiveFailures.set(0)
            consecutiveSuccesses.set(0)
        }
        Timber.i("🔄 Circuit breaker manually RESET for operation: $operationKey")
    }
    
    /**
     * Get circuit breaker state
     */
    fun getCircuitBreakerState(operationKey: String): CircuitBreakerState {
        return circuitBreakers[operationKey] ?: CircuitBreakerState.CLOSED
    }
    
    /**
     * Get operation statistics
     */
    fun getOperationStats(operationKey: String): OperationStatsSnapshot? {
        val stats = operationStats[operationKey] ?: return null
        return OperationStatsSnapshot(
            operationKey = operationKey,
            consecutiveFailures = stats.consecutiveFailures.get(),
            consecutiveSuccesses = stats.consecutiveSuccesses.get(),
            lastFailureTime = stats.lastFailureTime.get(),
            totalRetries = stats.totalRetries.get(),
            totalSuccesses = stats.totalSuccesses.get(),
            circuitBreakerState = getCircuitBreakerState(operationKey)
        )
    }
    
    /**
     * Get all operation statistics
     */
    fun getAllOperationStats(): List<OperationStatsSnapshot> {
        return operationStats.keys.mapNotNull { getOperationStats(it) }
    }
    
    /**
     * Clear statistics for an operation
     */
    fun clearStats(operationKey: String) {
        operationStats.remove(operationKey)
        circuitBreakers.remove(operationKey)
        Timber.d("🧹 Cleared retry stats for operation: $operationKey")
    }
    
    /**
     * Clear all statistics
     */
    fun clearAllStats() {
        operationStats.clear()
        circuitBreakers.clear()
        Timber.d("🧹 Cleared all retry statistics")
    }
    
    /**
     * Operation statistics snapshot
     */
    data class OperationStatsSnapshot(
        val operationKey: String,
        val consecutiveFailures: Int,
        val consecutiveSuccesses: Int,
        val lastFailureTime: Long,
        val totalRetries: Long,
        val totalSuccesses: Long,
        val circuitBreakerState: CircuitBreakerState
    ) {
        val successRate: Double get() = if (totalRetries + totalSuccesses > 0) {
            (totalSuccesses.toDouble() / (totalRetries + totalSuccesses)) * 100
        } else 100.0
        
        val timeSinceLastFailure: Long get() = if (lastFailureTime > 0) {
            System.currentTimeMillis() - lastFailureTime
        } else 0L
    }
}

/**
 * Extension function for easy retry execution
 */
suspend fun <T> SmartRetryManager.retry(
    operationKey: String,
    maxRetries: Int = 3,
    operation: suspend (attempt: Int) -> T
): T {
    val result = executeWithRetry(
        operationKey = operationKey,
        config = SmartRetryManager.RetryConfig(maxRetries = maxRetries),
        operation = operation
    )
    
    return when (result) {
        is SmartRetryManager.RetryResult.Success -> result.result
        is SmartRetryManager.RetryResult.Failure -> throw result.lastException
        is SmartRetryManager.RetryResult.CircuitBreakerOpen -> {
            throw Exception("Circuit breaker is open for operation: ${result.operationKey}")
        }
    }
}

/**
 * Predefined retry configurations for common scenarios
 */
object RetryConfigs {
    val NETWORK_REQUEST = SmartRetryManager.RetryConfig(
        maxRetries = 3,
        baseDelay = 1000L,
        maxDelay = 10000L,
        backoffStrategy = SmartRetryManager.BackoffStrategy.EXPONENTIAL,
        circuitBreakerEnabled = true
    )
    
    val DATABASE_OPERATION = SmartRetryManager.RetryConfig(
        maxRetries = 2,
        baseDelay = 500L,
        maxDelay = 2000L,
        backoffStrategy = SmartRetryManager.BackoffStrategy.LINEAR,
        circuitBreakerEnabled = false
    )
    
    val FILE_OPERATION = SmartRetryManager.RetryConfig(
        maxRetries = 2,
        baseDelay = 200L,
        maxDelay = 1000L,
        backoffStrategy = SmartRetryManager.BackoffStrategy.FIXED,
        circuitBreakerEnabled = false
    )
    
    val STREAMING_OPERATION = SmartRetryManager.RetryConfig(
        maxRetries = 5,
        baseDelay = 2000L,
        maxDelay = 30000L,
        backoffStrategy = SmartRetryManager.BackoffStrategy.EXPONENTIAL,
        circuitBreakerEnabled = true
    )
}