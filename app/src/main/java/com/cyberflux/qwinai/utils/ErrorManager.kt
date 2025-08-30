package com.cyberflux.qwinai.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.annotation.StringRes
import com.cyberflux.qwinai.R
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLException

/**
 * Comprehensive error handling manager
 * Provides centralized error management, recovery strategies, and user feedback
 */
class ErrorManager private constructor(private val context: Context) {
    
    private val errorCounts = ConcurrentHashMap<ErrorType, AtomicInteger>()
    private val lastErrorTimes = ConcurrentHashMap<ErrorType, Long>()
    private val errorCallbacks = mutableListOf<ErrorCallback>()
    private val retryHandlers = ConcurrentHashMap<String, RetryHandler>()
    
    companion object {
        @Volatile
        private var INSTANCE: ErrorManager? = null
        
        fun getInstance(context: Context): ErrorManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ErrorManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val BASE_RETRY_DELAY = 1000L // 1 second
        private const val MAX_RETRY_DELAY = 10000L // 10 seconds
        private const val ERROR_COOLDOWN_PERIOD = 5000L // 5 seconds
    }
    
    /**
     * Error types for classification
     */
    enum class ErrorType {
        NETWORK_UNAVAILABLE,
        NETWORK_TIMEOUT,
        NETWORK_CONNECTION_FAILED,
        SERVER_ERROR,
        AUTHENTICATION_ERROR,
        RATE_LIMIT_ERROR,
        VALIDATION_ERROR,
        DATABASE_ERROR,
        FILE_OPERATION_ERROR,
        PERMISSION_ERROR,
        PARSING_ERROR,
        API_ERROR,
        STREAMING_ERROR,
        BILLING_ERROR,
        UNKNOWN_ERROR
    }
    
    /**
     * Error severity levels
     */
    enum class ErrorSeverity {
        LOW,      // Minor errors that don't affect core functionality
        MEDIUM,   // Errors that affect some functionality
        HIGH,     // Errors that significantly impact user experience
        CRITICAL  // Errors that make the app unusable
    }
    
    /**
     * Error handling result
     */
    data class ErrorResult(
        val errorType: ErrorType,
        val severity: ErrorSeverity,
        val userMessage: String,
        val canRetry: Boolean,
        val retryDelay: Long = 0L,
        val suggestion: String? = null
    )
    
    /**
     * Retry handler interface
     */
    interface RetryHandler {
        suspend fun retry(): Boolean
    }
    
    /**
     * Error callback interface
     */
    interface ErrorCallback {
        fun onError(errorResult: ErrorResult, originalError: Throwable)
        fun onRecovery(errorType: ErrorType)
    }
    
    /**
     * Register error callback
     */
    fun registerErrorCallback(callback: ErrorCallback) {
        errorCallbacks.add(callback)
    }
    
    /**
     * Unregister error callback
     */
    fun unregisterErrorCallback(callback: ErrorCallback) {
        errorCallbacks.remove(callback)
    }
    
    /**
     * Handle error with comprehensive analysis and recovery
     */
    suspend fun handleError(
        error: Throwable,
        operation: String = "unknown",
        retryHandler: RetryHandler? = null,
        showToUser: Boolean = true
    ): ErrorResult {
        return withContext(Dispatchers.Main) {
            try {
                Timber.e(error, "Error in operation: $operation")
                
                val errorResult = analyzeError(error)
                
                // Track error statistics
                trackError(errorResult.errorType)
                
                // Register retry handler if provided
                if (retryHandler != null && errorResult.canRetry) {
                    retryHandlers[operation] = retryHandler
                }
                
                // Notify callbacks
                errorCallbacks.forEach { callback ->
                    try {
                        callback.onError(errorResult, error)
                    } catch (e: Exception) {
                        Timber.e(e, "Error in error callback")
                    }
                }
                
                // Show user message if requested
                if (showToUser && shouldShowErrorToUser(errorResult.errorType)) {
                    showErrorToUser(errorResult)
                }
                
                // Log to analytics/crash reporting
                logErrorToAnalytics(error, operation, errorResult)
                
                errorResult
            } catch (e: Exception) {
                Timber.e(e, "Error while handling error")
                createFallbackErrorResult(error)
            }
        }
    }
    
    /**
     * Analyze error and determine type, severity, and recovery options
     */
    private fun analyzeError(error: Throwable): ErrorResult {
        return when (error) {
            is UnknownHostException, is ConnectException -> {
                if (!isNetworkAvailable()) {
                    ErrorResult(
                        errorType = ErrorType.NETWORK_UNAVAILABLE,
                        severity = ErrorSeverity.HIGH,
                        userMessage = context.getString(R.string.error_no_internet),
                        canRetry = true,
                        retryDelay = BASE_RETRY_DELAY,
                        suggestion = "Check your internet connection and try again"
                    )
                } else {
                    ErrorResult(
                        errorType = ErrorType.NETWORK_CONNECTION_FAILED,
                        severity = ErrorSeverity.MEDIUM,
                        userMessage = context.getString(R.string.error_connection_failed),
                        canRetry = true,
                        retryDelay = BASE_RETRY_DELAY * 2
                    )
                }
            }
            
            is SocketTimeoutException, is TimeoutException -> {
                ErrorResult(
                    errorType = ErrorType.NETWORK_TIMEOUT,
                    severity = ErrorSeverity.MEDIUM,
                    userMessage = context.getString(R.string.error_request_timeout),
                    canRetry = true,
                    retryDelay = BASE_RETRY_DELAY * 3,
                    suggestion = "The request took too long. Please try again."
                )
            }
            
            is HttpException -> {
                analyzeHttpException(error)
            }
            
            is IOException -> {
                ErrorResult(
                    errorType = ErrorType.NETWORK_CONNECTION_FAILED,
                    severity = ErrorSeverity.MEDIUM,
                    userMessage = context.getString(R.string.error_network_io),
                    canRetry = true,
                    retryDelay = BASE_RETRY_DELAY
                )
            }
            
            is SSLException -> {
                ErrorResult(
                    errorType = ErrorType.NETWORK_CONNECTION_FAILED,
                    severity = ErrorSeverity.HIGH,
                    userMessage = context.getString(R.string.error_ssl_connection),
                    canRetry = false,
                    suggestion = "There's a problem with the secure connection"
                )
            }
            
            is JsonDataException -> {
                ErrorResult(
                    errorType = ErrorType.PARSING_ERROR,
                    severity = ErrorSeverity.MEDIUM,
                    userMessage = context.getString(R.string.error_data_parsing),
                    canRetry = true,
                    retryDelay = BASE_RETRY_DELAY
                )
            }
            
            is SecurityException -> {
                ErrorResult(
                    errorType = ErrorType.PERMISSION_ERROR,
                    severity = ErrorSeverity.HIGH,
                    userMessage = context.getString(R.string.error_permission_denied),
                    canRetry = false,
                    suggestion = "Please grant the required permissions"
                )
            }
            
            is IllegalStateException -> {
                ErrorResult(
                    errorType = ErrorType.VALIDATION_ERROR,
                    severity = ErrorSeverity.MEDIUM,
                    userMessage = context.getString(R.string.error_invalid_state),
                    canRetry = false,
                    suggestion = "Please restart the operation"
                )
            }
            
            is IllegalArgumentException -> {
                ErrorResult(
                    errorType = ErrorType.VALIDATION_ERROR,
                    severity = ErrorSeverity.LOW,
                    userMessage = context.getString(R.string.error_invalid_input),
                    canRetry = false,
                    suggestion = "Please check your input and try again"
                )
            }
            
            else -> {
                ErrorResult(
                    errorType = ErrorType.UNKNOWN_ERROR,
                    severity = ErrorSeverity.MEDIUM,
                    userMessage = context.getString(R.string.error_unexpected),
                    canRetry = true,
                    retryDelay = BASE_RETRY_DELAY,
                    suggestion = "An unexpected error occurred. Please try again."
                )
            }
        }
    }
    
    /**
     * Analyze HTTP exceptions for specific error codes
     */
    private fun analyzeHttpException(httpException: HttpException): ErrorResult {
        return when (httpException.code()) {
            401 -> ErrorResult(
                errorType = ErrorType.AUTHENTICATION_ERROR,
                severity = ErrorSeverity.HIGH,
                userMessage = context.getString(R.string.error_authentication),
                canRetry = false,
                suggestion = "Please check your API key or login credentials"
            )
            
            403 -> ErrorResult(
                errorType = ErrorType.PERMISSION_ERROR,
                severity = ErrorSeverity.HIGH,
                userMessage = context.getString(R.string.error_access_denied),
                canRetry = false,
                suggestion = "You don't have permission to perform this action"
            )
            
            429 -> ErrorResult(
                errorType = ErrorType.RATE_LIMIT_ERROR,
                severity = ErrorSeverity.MEDIUM,
                userMessage = context.getString(R.string.error_rate_limit),
                canRetry = true,
                retryDelay = BASE_RETRY_DELAY * 5,
                suggestion = "Too many requests. Please wait before trying again."
            )
            
            in 500..599 -> ErrorResult(
                errorType = ErrorType.SERVER_ERROR,
                severity = ErrorSeverity.HIGH,
                userMessage = context.getString(R.string.error_server),
                canRetry = true,
                retryDelay = BASE_RETRY_DELAY * 2,
                suggestion = "Server is experiencing issues. Please try again later."
            )
            
            else -> ErrorResult(
                errorType = ErrorType.API_ERROR,
                severity = ErrorSeverity.MEDIUM,
                userMessage = context.getString(R.string.error_api_request),
                canRetry = true,
                retryDelay = BASE_RETRY_DELAY
            )
        }
    }
    
    /**
     * Track error statistics
     */
    private fun trackError(errorType: ErrorType) {
        val count = errorCounts.getOrPut(errorType) { AtomicInteger(0) }
        count.incrementAndGet()
        lastErrorTimes[errorType] = System.currentTimeMillis()
        
        Timber.d("Error tracked: $errorType (count: ${count.get()})")
    }
    
    /**
     * Check if we should show error to user (avoid spam)
     */
    private fun shouldShowErrorToUser(errorType: ErrorType): Boolean {
        val lastErrorTime = lastErrorTimes[errorType] ?: 0L
        val timeSinceLastError = System.currentTimeMillis() - lastErrorTime
        
        return timeSinceLastError > ERROR_COOLDOWN_PERIOD
    }
    
    /**
     * Show error message to user
     */
    private fun showErrorToUser(errorResult: ErrorResult) {
        val message = if (errorResult.suggestion != null) {
            "${errorResult.userMessage}\n${errorResult.suggestion}"
        } else {
            errorResult.userMessage
        }
        
        val duration = when (errorResult.severity) {
            ErrorSeverity.LOW -> Toast.LENGTH_SHORT
            ErrorSeverity.MEDIUM -> Toast.LENGTH_LONG
            ErrorSeverity.HIGH, ErrorSeverity.CRITICAL -> Toast.LENGTH_LONG
        }
        
        Toast.makeText(context, message, duration).show()
    }
    
    /**
     * Check network availability
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Attempt to retry operation
     */
    suspend fun retryOperation(operation: String): Boolean {
        val retryHandler = retryHandlers[operation] ?: return false
        
        return withContext(Dispatchers.IO) {
            try {
                val success = retryHandler.retry()
                if (success) {
                    // Notify recovery
                    errorCallbacks.forEach { callback ->
                        try {
                            callback.onRecovery(ErrorType.UNKNOWN_ERROR) // Could track specific type
                        } catch (e: Exception) {
                            Timber.e(e, "Error in recovery callback")
                        }
                    }
                    retryHandlers.remove(operation)
                }
                success
            } catch (e: Exception) {
                Timber.e(e, "Retry failed for operation: $operation")
                false
            }
        }
    }
    
    /**
     * Create fallback error result when error handling fails
     */
    private fun createFallbackErrorResult(error: Throwable): ErrorResult {
        return ErrorResult(
            errorType = ErrorType.UNKNOWN_ERROR,
            severity = ErrorSeverity.CRITICAL,
            userMessage = "An unexpected error occurred",
            canRetry = false,
            suggestion = "Please restart the app if the problem persists"
        )
    }
    
    /**
     * Log error to analytics/crash reporting
     */
    private fun logErrorToAnalytics(error: Throwable, operation: String, errorResult: ErrorResult) {
        try {
            // Log structured error information
            val errorData = mapOf(
                "operation" to operation,
                "errorType" to errorResult.errorType.name,
                "severity" to errorResult.severity.name,
                "canRetry" to errorResult.canRetry,
                "errorClass" to error.javaClass.simpleName,
                "errorMessage" to (error.message ?: "No message"),
                "timestamp" to System.currentTimeMillis()
            )
            
            Timber.tag("ErrorAnalytics").d("Error logged: $errorData")
            
            // Here you could integrate with Firebase Crashlytics, Sentry, or other crash reporting
            // FirebaseCrashlytics.getInstance().recordException(error)
            // FirebaseCrashlytics.getInstance().setCustomKeys(errorData)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to log error to analytics")
        }
    }
    
    /**
     * Get error statistics summary
     */
    fun getErrorStatistics(): Map<String, Any> {
        return mapOf(
            "errorCounts" to errorCounts.mapKeys { it.key.name }.mapValues { it.value.get() },
            "lastErrorTimes" to lastErrorTimes.mapKeys { it.key.name },
            "activeRetryHandlers" to retryHandlers.size,
            "registeredCallbacks" to errorCallbacks.size
        )
    }
    
    /**
     * Reset error statistics
     */
    fun resetErrorStatistics() {
        errorCounts.clear()
        lastErrorTimes.clear()
        retryHandlers.clear()
        Timber.d("Error statistics reset")
    }
    
    /**
     * Handle specific streaming errors
     */
    suspend fun handleStreamingError(
        error: Throwable,
        messageId: String,
        retryHandler: (() -> Unit)? = null
    ): ErrorResult {
        val errorResult = analyzeError(error).copy(
            errorType = ErrorType.STREAMING_ERROR,
            userMessage = context.getString(R.string.error_streaming),
            suggestion = "The message stream was interrupted. Try sending your message again."
        )
        
        if (retryHandler != null && errorResult.canRetry) {
            retryHandlers["streaming_$messageId"] = object : RetryHandler {
                override suspend fun retry(): Boolean {
                    return try {
                        retryHandler.invoke()
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
            }
        }
        
        return handleError(error, "streaming_$messageId", showToUser = true)
    }
    
    /**
     * Handle database operation errors
     */
    suspend fun handleDatabaseError(
        error: Throwable,
        operation: String
    ): ErrorResult {
        val errorResult = analyzeError(error).copy(
            errorType = ErrorType.DATABASE_ERROR,
            userMessage = context.getString(R.string.error_database),
            canRetry = true,
            suggestion = "There was a problem saving your data. Please try again."
        )
        
        return handleError(error, "database_$operation", showToUser = true)
    }
    
    /**
     * Handle file operation errors
     */
    suspend fun handleFileError(
        error: Throwable,
        operation: String
    ): ErrorResult {
        val errorResult = analyzeError(error).copy(
            errorType = ErrorType.FILE_OPERATION_ERROR,
            userMessage = context.getString(R.string.error_file_operation),
            suggestion = "There was a problem with the file operation. Please check storage permissions."
        )
        
        return handleError(error, "file_$operation", showToUser = true)
    }
}