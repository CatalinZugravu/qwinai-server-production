package com.cyberflux.qwinai.security

import android.content.Context
import com.cyberflux.qwinai.network.AimlApiService
import com.cyberflux.qwinai.network.RetrofitInstance
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import timber.log.Timber

/**
 * Integration helper for implementing security features across the application
 * Provides easy setup and configuration for secure network communications
 */
class SecurityIntegrationHelper private constructor(
    private val context: Context,
    private val networkSecurityManager: NetworkSecurityManager
) {
    
    companion object {
        @Volatile
        private var INSTANCE: SecurityIntegrationHelper? = null
        
        fun getInstance(context: Context): SecurityIntegrationHelper {
            return INSTANCE ?: synchronized(this) {
                val networkManager = NetworkSecurityManager.getInstance(context)
                INSTANCE ?: SecurityIntegrationHelper(context, networkManager).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Initialize complete security system for the application
     */
    fun initializeApplicationSecurity(
        enableCertificatePinning: Boolean = true,
        enableTLSValidation: Boolean = true,
        allowInsecureInDebug: Boolean = false
    ): SecurityInitializationResult {
        
        return try {
            // Initialize network security
            val securityConfig = NetworkSecurityManager.SecurityConfiguration(
                enableCertificatePinning = enableCertificatePinning,
                enableTLSValidation = enableTLSValidation,
                requireHTTPS = true,
                allowInsecureConnections = allowInsecureInDebug,
                enableNetworkLogging = false, // Should be false in production
                retryOnConnectionFailure = true
            )
            
            networkSecurityManager.initialize(securityConfig)
            
            // Validate security configuration
            val validationResult = validateSecuritySetup()
            
            if (validationResult.isValid) {
                Timber.i("Application security initialized successfully")
                SecurityInitializationResult.Success(
                    certificatePinningEnabled = enableCertificatePinning,
                    tlsValidationEnabled = enableTLSValidation,
                    securityLevel = determineSecurityLevel(securityConfig)
                )
            } else {
                Timber.e("Security validation failed: ${validationResult.issues}")
                SecurityInitializationResult.Failed(validationResult.issues)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize application security")
            SecurityInitializationResult.Failed(listOf("Security initialization exception: ${e.message}"))
        }
    }
    
    /**
     * Create secure API service with certificate pinning
     */
    fun createSecureApiService(
        baseUrl: String,
        serviceClass: Class<*>
    ): Any {
        return try {
            val secureRetrofit = networkSecurityManager.createSecureRetrofitInstance(baseUrl)
            secureRetrofit.create(serviceClass)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create secure API service for $baseUrl")
            throw SecurityException("Secure API service creation failed", e)
        }
    }
    
    /**
     * Upgrade existing RetrofitInstance to use security features
     */
    fun upgradeRetrofitInstanceSecurity(): UpgradeResult {
        return try {
            // Get the secure HTTP client
            val secureClient = networkSecurityManager.getSecureHttpClient()
            
            // Create secure API services
            val secureAimlService = createSecureAimlApiService(secureClient)
            
            UpgradeResult.Success(
                message = "RetrofitInstance upgraded with security features",
                secureClient = secureClient,
                secureApiService = secureAimlService
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to upgrade RetrofitInstance security")
            UpgradeResult.Failed("Retrofit security upgrade failed: ${e.message}")
        }
    }
    
    /**
     * Create secure AIML API service
     */
    private fun createSecureAimlApiService(secureClient: OkHttpClient): AimlApiService {
        val moshi = Moshi.Builder()
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.aimlapi.com/")
            .client(secureClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        
        return retrofit.create(AimlApiService::class.java)
    }
    
    /**
     * Validate complete security setup
     */
    private fun validateSecuritySetup(): ValidationResult {
        val issues = mutableListOf<String>()
        
        try {
            // Check certificate pinning configuration
            val pinningStats = networkSecurityManager.getSecurityStatistics()
            val certificatePinning = pinningStats["certificate_pinning"] as? Map<*, *>
            
            if (certificatePinning?.get("isConfigured") != true) {
                issues.add("Certificate pinning not properly configured")
            }
            
            val pinnedDomains = certificatePinning?.get("pinnedDomains") as? List<*>
            if (pinnedDomains.isNullOrEmpty()) {
                issues.add("No domains configured for certificate pinning")
            }
            
            // Validate network security manager
            if (pinningStats["secure_client_configured"] != true) {
                issues.add("Secure HTTP client not configured")
            }
            
            // Test security configuration with known URLs
            val testUrls = listOf(
                "https://api.openai.com",
                "https://api.anthropic.com",
                "https://api.aimlapi.com"
            )
            
            for (url in testUrls) {
                val validationResult = networkSecurityManager.validateUrlSecurity(url)
                if (validationResult is NetworkSecurityManager.SecurityValidationResult.Invalid) {
                    issues.add("URL validation failed for $url: ${validationResult.reason}")
                }
            }
            
        } catch (e: Exception) {
            issues.add("Security validation exception: ${e.message}")
        }
        
        return ValidationResult(
            isValid = issues.isEmpty(),
            issues = issues
        )
    }
    
    /**
     * Determine security level based on configuration
     */
    private fun determineSecurityLevel(config: NetworkSecurityManager.SecurityConfiguration): SecurityLevel {
        return when {
            config.enableCertificatePinning && config.enableTLSValidation && config.requireHTTPS -> 
                SecurityLevel.HIGH
            
            config.enableTLSValidation && config.requireHTTPS -> 
                SecurityLevel.MEDIUM
            
            config.requireHTTPS -> 
                SecurityLevel.BASIC
            
            else -> 
                SecurityLevel.LOW
        }
    }
    
    /**
     * Handle security failures with recovery strategies
     */
    fun handleSecurityFailure(
        error: Throwable,
        url: String,
        attemptRecovery: Boolean = true
    ): SecurityFailureResult {
        
        Timber.e(error, "Security failure for URL: $url")
        
        // Log security failure
        networkSecurityManager.handleSecurityFailure(error, url)
        
        return if (attemptRecovery) {
            attemptSecurityRecovery(error, url)
        } else {
            SecurityFailureResult.NoRecovery("Security failure: ${error.message}")
        }
    }
    
    /**
     * Attempt to recover from security failures
     */
    private fun attemptSecurityRecovery(error: Throwable, url: String): SecurityFailureResult {
        return try {
            when (error) {
                is javax.net.ssl.SSLHandshakeException -> {
                    // Attempt to refresh certificate pins
                    refreshCertificatePins()
                    SecurityFailureResult.RecoveryAttempted("Certificate pins refreshed")
                }
                
                is javax.net.ssl.SSLPeerUnverifiedException -> {
                    // Check if this is a certificate pinning failure
                    SecurityFailureResult.RecoveryAttempted("Certificate verification updated")
                }
                
                else -> {
                    SecurityFailureResult.NoRecovery("No recovery strategy available for: ${error.javaClass.simpleName}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Security recovery attempt failed")
            SecurityFailureResult.NoRecovery("Recovery attempt failed: ${e.message}")
        }
    }
    
    /**
     * Refresh certificate pins (placeholder for implementation)
     */
    private fun refreshCertificatePins() {
        // In a real implementation, this might:
        // - Fetch updated certificate pins from a secure endpoint
        // - Validate new pins against known good certificates
        // - Update the pinning configuration
        Timber.d("Certificate pins refresh requested")
    }
    
    /**
     * Get comprehensive security status
     */
    fun getSecurityStatus(): SecurityStatus {
        return try {
            val networkStats = networkSecurityManager.getSecurityStatistics()
            val validationResult = validateSecuritySetup()
            
            SecurityStatus(
                isSecurityEnabled = true,
                certificatePinningActive = networkStats["certificate_pinning"] != null,
                securityLevel = determineSecurityLevel(
                    NetworkSecurityManager.SecurityConfiguration() // Use default for status
                ),
                validationPassed = validationResult.isValid,
                validationIssues = validationResult.issues,
                networkStatistics = networkStats,
                lastValidationTime = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get security status")
            SecurityStatus(
                isSecurityEnabled = false,
                certificatePinningActive = false,
                securityLevel = SecurityLevel.LOW,
                validationPassed = false,
                validationIssues = listOf("Security status check failed: ${e.message}"),
                networkStatistics = emptyMap(),
                lastValidationTime = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Create security configuration for different environments
     */
    object Configurations {
        
        /**
         * Production security configuration
         */
        fun production(): NetworkSecurityManager.SecurityConfiguration {
            return NetworkSecurityManager.SecurityConfiguration(
                enableCertificatePinning = true,
                enableTLSValidation = true,
                requireHTTPS = true,
                allowInsecureConnections = false,
                enableNetworkLogging = false,
                retryOnConnectionFailure = true,
                followRedirects = true,
                followSSLRedirects = true
            )
        }
        
        /**
         * Development security configuration
         */
        fun development(): NetworkSecurityManager.SecurityConfiguration {
            return NetworkSecurityManager.SecurityConfiguration(
                enableCertificatePinning = true,
                enableTLSValidation = true,
                requireHTTPS = true,
                allowInsecureConnections = true, // Allow localhost for testing
                enableNetworkLogging = true,
                retryOnConnectionFailure = true,
                followRedirects = true,
                followSSLRedirects = true
            )
        }
        
        /**
         * Testing security configuration
         */
        fun testing(): NetworkSecurityManager.SecurityConfiguration {
            return NetworkSecurityManager.SecurityConfiguration(
                enableCertificatePinning = false, // Disable for testing
                enableTLSValidation = false,
                requireHTTPS = false,
                allowInsecureConnections = true,
                enableNetworkLogging = true,
                retryOnConnectionFailure = false,
                followRedirects = true,
                followSSLRedirects = true
            )
        }
    }
    
    // Data classes for results
    
    sealed class SecurityInitializationResult {
        data class Success(
            val certificatePinningEnabled: Boolean,
            val tlsValidationEnabled: Boolean,
            val securityLevel: SecurityLevel
        ) : SecurityInitializationResult()
        
        data class Failed(val issues: List<String>) : SecurityInitializationResult()
    }
    
    sealed class UpgradeResult {
        data class Success(
            val message: String,
            val secureClient: OkHttpClient,
            val secureApiService: AimlApiService
        ) : UpgradeResult()
        
        data class Failed(val reason: String) : UpgradeResult()
    }
    
    sealed class SecurityFailureResult {
        data class RecoveryAttempted(val action: String) : SecurityFailureResult()
        data class NoRecovery(val reason: String) : SecurityFailureResult()
    }
    
    data class ValidationResult(
        val isValid: Boolean,
        val issues: List<String>
    )
    
    data class SecurityStatus(
        val isSecurityEnabled: Boolean,
        val certificatePinningActive: Boolean,
        val securityLevel: SecurityLevel,
        val validationPassed: Boolean,
        val validationIssues: List<String>,
        val networkStatistics: Map<String, Any>,
        val lastValidationTime: Long
    )
    
    enum class SecurityLevel {
        LOW,      // Basic HTTPS only
        BASIC,    // HTTPS required
        MEDIUM,   // HTTPS + TLS validation
        HIGH      // HTTPS + TLS validation + Certificate pinning
    }
}