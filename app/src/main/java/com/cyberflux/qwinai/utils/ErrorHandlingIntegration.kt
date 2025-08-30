package com.cyberflux.qwinai.utils

import android.content.Context
import com.cyberflux.qwinai.network.AimlApiService
import com.cyberflux.qwinai.network.NetworkErrorHandler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.Response
import timber.log.Timber

/**
 * Integration helper for comprehensive error handling system
 * Provides usage examples and integration patterns for the error handling components
 */
class ErrorHandlingIntegration(
    private val context: Context,
    private val errorManager: ErrorManager,
    private val networkErrorHandler: NetworkErrorHandler,
    private val crashPreventionManager: CrashPreventionManager
) {
    
    /**
     * Create a safe coroutine scope with comprehensive error handling
     */
    fun createSafeCoroutineScope(scopeName: String): CoroutineScope {
        val exceptionHandler = crashPreventionManager.createSafeCoroutineExceptionHandler(scopeName)
        return CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)
    }
    
    /**
     * Example: Safe API call with comprehensive error handling
     */
    suspend fun <T> safeApiCall(
        operation: String,
        apiCall: suspend () -> Response<T>
    ): Result<T> {
        return networkErrorHandler.executeWithRetry(
            operation = operation,
            config = NetworkErrorHandler.RetryConfigs.API_CALL
        ) {
            val response = apiCall()
            if (response.isSuccessful && response.body() != null) {
                response.body()!!
            } else {
                throw Exception("API call failed: ${response.code()} ${response.message()}")
            }
        }
    }
    
    /**
     * Example: Safe streaming operation with error handling
     */
    suspend fun <T> safeStreamingOperation(
        operation: String,
        streamingCall: suspend () -> T
    ): Result<T> {
        return NetworkErrorHandler.Scenarios.handleStreamingCall(
            errorHandler = networkErrorHandler,
            operation = operation,
            call = streamingCall
        )
    }
    
    /**
     * Example: Safe database operation with error handling
     */
    suspend fun <T> safeDatabaseOperation(
        operation: String,
        dbCall: suspend () -> T
    ): T? {
        return crashPreventionManager.safeExecute(
            operation = "db_$operation",
            fallbackValue = null
        ) {
            dbCall()
        }
    }
    
    /**
     * Example: Safe file operation with error handling
     */
    suspend fun <T> safeFileOperation(
        operation: String,
        fileCall: suspend () -> T
    ): Result<T> {
        return NetworkErrorHandler.Scenarios.handleFileCall(
            errorHandler = networkErrorHandler,
            operation = operation,
            call = fileCall
        )
    }
    
    /**
     * Integration pattern for MainActivity
     */
    object MainActivityIntegration {
        
        /**
         * Initialize error handling in MainActivity
         */
        fun initialize(
            context: Context,
            onErrorHandlingReady: (ErrorHandlingIntegration) -> Unit
        ) {
            try {
                // Initialize error manager
                val errorManager = ErrorManager.getInstance(context)
                
                // Initialize network error handler
                val networkErrorHandler = NetworkErrorHandler(context, errorManager)
                
                // Initialize crash prevention
                val crashPreventionManager = CrashPreventionManager.getInstance(context, errorManager)
                crashPreventionManager.initialize()
                
                // Create integration helper
                val integration = ErrorHandlingIntegration(
                    context, errorManager, networkErrorHandler, crashPreventionManager
                )
                
                // Register error callbacks for UI updates
                errorManager.registerErrorCallback(object : ErrorManager.ErrorCallback {
                    override fun onError(errorResult: ErrorManager.ErrorResult, originalError: Throwable) {
                        Timber.d("MainActivity error callback: ${errorResult.errorType}")
                        // Handle UI updates, show error messages, etc.
                    }
                    
                    override fun onRecovery(errorType: ErrorManager.ErrorType) {
                        Timber.d("MainActivity recovery callback: $errorType")
                        // Handle UI recovery, show success messages, etc.
                    }
                })
                
                onErrorHandlingReady(integration)
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize error handling")
                // Fallback: create basic integration without advanced features
                val basicErrorManager = ErrorManager.getInstance(context)
                val basicNetworkHandler = NetworkErrorHandler(context, basicErrorManager)
                val basicCrashPrevention = CrashPreventionManager.getInstance(context, basicErrorManager)
                
                onErrorHandlingReady(
                    ErrorHandlingIntegration(context, basicErrorManager, basicNetworkHandler, basicCrashPrevention)
                )
            }
        }
        
        /**
         * Handle chat message sending with comprehensive error handling
         */
        suspend fun sendMessageSafely(
            integration: ErrorHandlingIntegration,
            messageText: String,
            apiService: AimlApiService,
            onSuccess: (String) -> Unit,
            onError: (String) -> Unit
        ) {
            val result = integration.safeApiCall("send_message") {
                // Create proper request object
                val message = com.cyberflux.qwinai.network.AimlApiRequest.Message(
                    role = "user",
                    content = messageText
                )
                val request = com.cyberflux.qwinai.network.AimlApiRequest(
                    model = "gpt-3.5-turbo", // Default model
                    messages = listOf(message),
                    maxTokens = 1000,
                    topA = null,
                    parallelToolCalls = null,
                    minP = null,
                    useWebSearch = false
                )
                apiService.sendMessage(request).execute()
            }
            
            result.fold(
                onSuccess = { response: com.cyberflux.qwinai.network.AimlApiResponse ->
                    onSuccess(response.toString())
                },
                onFailure = { error: Throwable ->
                    Timber.e(error, "Failed to send message")
                    onError(error.message ?: "Failed to send message")
                }
            )
        }
        
        /**
         * Handle streaming response with error handling
         */
        fun handleStreamingResponseSafely(
            integration: ErrorHandlingIntegration,
            scope: CoroutineScope,
            onStreamingStart: () -> Unit,
            onStreamingError: (String) -> Unit
        ) {
            scope.launch(integration.crashPreventionManager.createSafeCoroutineExceptionHandler("streaming")) {
                try {
                    onStreamingStart()
                    
                    // Your streaming logic here with error handling
                    val result = integration.safeStreamingOperation("process_stream") {
                        // Your streaming processing code
                        "streaming_result"
                    }
                    
                    result.fold(
                        onSuccess = { _ ->
                            Timber.d("Streaming completed successfully")
                        },
                        onFailure = { error ->
                            onStreamingError(error.message ?: "Streaming failed")
                        }
                    )
                    
                } catch (e: Exception) {
                    onStreamingError("Unexpected streaming error")
                }
            }
        }
    }
    
    /**
     * Integration pattern for Service classes
     */
    object ServiceIntegration {
        
        /**
         * Create safe service with error handling
         */
        fun createSafeAiChatService(
            context: Context,
            originalService: Any, // Your existing service
            integration: ErrorHandlingIntegration
        ): SafeAiChatServiceWrapper {
            return SafeAiChatServiceWrapper(originalService, integration)
        }
        
        /**
         * Wrapper for existing service with added error handling
         */
        class SafeAiChatServiceWrapper(
            private val originalService: Any,
            private val integration: ErrorHandlingIntegration
        ) {
            
            /**
             * Safe message sending with error handling
             */
            suspend fun sendMessageSafely(
                message: String,
                onSuccess: (String) -> Unit,
                onError: (String) -> Unit
            ) {
                val result = integration.safeApiCall("service_send_message") {
                    // Your original service call here
                    // originalService.sendMessage(message)
                    // For now, simulate a response
                    Response.success("Simulated response")
                }
                
                result.fold(
                    onSuccess = onSuccess,
                    onFailure = { error ->
                        onError(error.message ?: "Service error")
                    }
                )
            }
            
            /**
             * Safe database operations with error handling
             */
            suspend fun saveToDatabaseSafely(
                data: Any,
                onSuccess: () -> Unit,
                onError: (String) -> Unit
            ) {
                val result = integration.safeDatabaseOperation("save_data") {
                    // Your original database save logic here
                    // originalService.saveToDatabase(data)
                    true // Simulate success
                }
                
                if (result == true) {
                    onSuccess()
                } else {
                    onError("Failed to save to database")
                }
            }
        }
    }
    
    /**
     * Integration pattern for Adapter classes
     */
    object AdapterIntegration {
        
        /**
         * Safe adapter operations with error handling
         */
        fun handleAdapterOperationSafely(
            integration: ErrorHandlingIntegration,
            operation: String,
            adapterOperation: () -> Unit,
            onError: (String) -> Unit
        ) {
            try {
                adapterOperation()
            } catch (e: Exception) {
                Timber.e(e, "Adapter operation failed: $operation")
                
                // Handle through error manager
                integration.createSafeCoroutineScope("adapter_error_recovery").launch {
                    integration.crashPreventionManager.safeExecute(
                        operation = "adapter_$operation",
                        fallbackValue = Unit
                    ) {
                        // Attempt recovery
                        onError(e.message ?: "Adapter operation failed")
                    }
                }
            }
        }
        
        /**
         * Safe list updates with error handling
         */
        fun updateListSafely(
            integration: ErrorHandlingIntegration,
            newList: List<Any>,
            updateFunction: (List<Any>) -> Unit,
            onError: (String) -> Unit
        ) {
            handleAdapterOperationSafely(
                integration = integration,
                operation = "update_list",
                adapterOperation = { updateFunction(newList) },
                onError = onError
            )
        }
    }
    
    /**
     * Utility functions for error handling integration
     */
    object Utils {
        
        /**
         * Check system health before critical operations
         */
        fun checkSystemHealth(integration: ErrorHandlingIntegration): Boolean {
            return try {
                val crashStats = integration.crashPreventionManager.getCrashStatistics()
                val recentCrashes = crashStats["recentCrashCount"] as? Int ?: 0
                val isInSafeMode = crashStats["isInSafeMode"] as? Boolean ?: false
                
                // System is healthy if there are no recent crashes and not in safe mode
                recentCrashes == 0 && !isInSafeMode
            } catch (e: Exception) {
                Timber.e(e, "Error checking system health")
                false
            }
        }
        
        /**
         * Get comprehensive error statistics
         */
        fun getErrorStatistics(integration: ErrorHandlingIntegration): Map<String, Any> {
            return try {
                val errorStats = integration.errorManager.getErrorStatistics()
                val crashStats = integration.crashPreventionManager.getCrashStatistics()
                
                mapOf(
                    "errorManager" to errorStats,
                    "crashPrevention" to crashStats,
                    "systemHealthy" to checkSystemHealth(integration)
                )
            } catch (e: Exception) {
                Timber.e(e, "Error getting statistics")
                mapOf("error" to "Failed to get statistics")
            }
        }
        
        /**
         * Reset all error handling systems
         */
        fun resetErrorHandling(integration: ErrorHandlingIntegration) {
            try {
                integration.errorManager.resetErrorStatistics()
                integration.crashPreventionManager.reset()
                integration.crashPreventionManager.exitSafeMode()
                
                Timber.d("Error handling systems reset")
            } catch (e: Exception) {
                Timber.e(e, "Error resetting error handling")
            }
        }
    }
}