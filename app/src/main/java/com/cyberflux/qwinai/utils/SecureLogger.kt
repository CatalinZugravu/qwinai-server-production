package com.cyberflux.qwinai.utils

import com.cyberflux.qwinai.BuildConfig
import timber.log.Timber

/**
 * Secure logging utility that prevents sensitive information leakage in production
 * CRITICAL SECURITY FIX: Only logs debug/verbose messages in debug builds
 */
object SecureLogger {
    
    private const val REDACTED_TEXT = "[REDACTED]"
    
    /**
     * Debug logging - only in debug builds
     */
    fun d(tag: String? = null, message: String) {
        if (BuildConfig.DEBUG) {
            if (tag != null) {
                Timber.tag(tag).d(message)
            } else {
                Timber.d(message)
            }
        }
    }
    
    /**
     * Debug logging with throwable - only in debug builds
     */
    fun d(tag: String? = null, throwable: Throwable, message: String) {
        if (BuildConfig.DEBUG) {
            if (tag != null) {
                Timber.tag(tag).d(throwable, message)
            } else {
                Timber.d(throwable, message)
            }
        }
    }
    
    /**
     * Verbose logging - only in debug builds
     */
    fun v(tag: String? = null, message: String) {
        if (BuildConfig.DEBUG) {
            if (tag != null) {
                Timber.tag(tag).v(message)
            } else {
                Timber.v(message)
            }
        }
    }
    
    /**
     * Info logging - allowed in production for important events
     */
    fun i(tag: String? = null, message: String) {
        if (tag != null) {
            Timber.tag(tag).i(message)
        } else {
            Timber.i(message)
        }
    }
    
    /**
     * Warning logging - always allowed
     */
    fun w(tag: String? = null, message: String) {
        if (tag != null) {
            Timber.tag(tag).w(message)
        } else {
            Timber.w(message)
        }
    }
    
    /**
     * Error logging - always allowed
     */
    fun e(tag: String? = null, throwable: Throwable, message: String) {
        if (tag != null) {
            Timber.tag(tag).e(throwable, message)
        } else {
            Timber.e(throwable, message)
        }
    }
    
    /**
     * Log sensitive data with automatic redaction in production
     */
    fun logSensitive(tag: String? = null, label: String, sensitiveData: String) {
        val safeData = if (BuildConfig.DEBUG) sensitiveData else REDACTED_TEXT
        d(tag, "$label: $safeData")
    }
    
    /**
     * Log API responses with automatic sensitive data filtering
     */
    fun logApiResponse(tag: String? = null, endpoint: String, response: String) {
        if (BuildConfig.DEBUG) {
            // In debug, log full response but redact potential API keys or tokens
            val cleanedResponse = response
                .replace(Regex("\"api[_-]?key\"\\s*:\\s*\"[^\"]+\""), "\"api_key\":\"[REDACTED]\"")
                .replace(Regex("\"token\"\\s*:\\s*\"[^\"]+\""), "\"token\":\"[REDACTED]\"")
                .replace(Regex("\"authorization\"\\s*:\\s*\"[^\"]+\""), "\"authorization\":\"[REDACTED]\"")
            
            d(tag, "API Response from $endpoint: $cleanedResponse")
        } else {
            // In production, only log endpoint and status
            i(tag, "API call to $endpoint completed")
        }
    }
    
    /**
     * Log user actions for analytics (safe for production)
     */
    fun logUserAction(action: String, details: Map<String, Any>? = null) {
        val sanitizedDetails = details?.filterKeys { 
            !it.contains("password", ignoreCase = true) && 
            !it.contains("token", ignoreCase = true) &&
            !it.contains("key", ignoreCase = true)
        }
        
        i("UserAction", "Action: $action${sanitizedDetails?.let { ", Details: $it" } ?: ""}")
    }
    
    /**
     * Performance logging for optimization
     */
    fun logPerformance(operation: String, durationMs: Long, additionalInfo: String? = null) {
        val info = additionalInfo?.let { " ($it)" } ?: ""
        i("Performance", "$operation took ${durationMs}ms$info")
    }
    
    /**
     * Log device detection with security awareness
     */
    fun logDeviceDetection(isHuawei: Boolean, method: String) {
        i("DeviceDetection", "Device type determined: ${if (isHuawei) "Huawei" else "Google"} via $method")
    }
    
    /**
     * Log billing operations safely
     */
    fun logBilling(operation: String, success: Boolean, details: String? = null) {
        val safeDetails = if (BuildConfig.DEBUG) details else "[details redacted]"
        i("Billing", "Billing $operation: ${if (success) "SUCCESS" else "FAILED"}${safeDetails?.let { " - $it" } ?: ""}")
    }
    
    /**
     * Security event logging
     */
    fun logSecurityEvent(event: String, severity: String = "INFO") {
        w("Security", "[$severity] Security event: $event")
    }
}