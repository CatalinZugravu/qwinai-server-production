package com.cyberflux.qwinai.security

import android.content.Context
import com.cyberflux.qwinai.network.RetrofitInstance
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import timber.log.Timber
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Network security manager that integrates certificate pinning with HTTP clients
 * Provides secure network configuration for all API communications
 */
class NetworkSecurityManager private constructor(
    private val context: Context,
    private val certificatePinningManager: CertificatePinningManager
) {
    
    private var secureHttpClient: OkHttpClient? = null
    private var securityConfig: SecurityConfiguration? = null
    
    companion object {
        @Volatile
        private var INSTANCE: NetworkSecurityManager? = null
        
        fun getInstance(context: Context): NetworkSecurityManager {
            return INSTANCE ?: synchronized(this) {
                val pinningManager = CertificatePinningManager.getInstance(context)
                INSTANCE ?: NetworkSecurityManager(context, pinningManager).also { INSTANCE = it }
            }
        }
        
        private const val DEFAULT_TIMEOUT = 30L
        private const val DEFAULT_READ_TIMEOUT = 60L
        private const val DEFAULT_WRITE_TIMEOUT = 60L
    }
    
    /**
     * Security configuration for network requests
     */
    data class SecurityConfiguration(
        val enableCertificatePinning: Boolean = true,
        val enableTLSValidation: Boolean = true,
        val requireHTTPS: Boolean = true,
        val allowInsecureConnections: Boolean = false,
        val enableNetworkLogging: Boolean = false,
        val connectTimeoutSeconds: Long = DEFAULT_TIMEOUT,
        val readTimeoutSeconds: Long = DEFAULT_READ_TIMEOUT,
        val writeTimeoutSeconds: Long = DEFAULT_WRITE_TIMEOUT,
        val retryOnConnectionFailure: Boolean = true,
        val followRedirects: Boolean = true,
        val followSSLRedirects: Boolean = true
    )
    
    /**
     * Initialize network security manager
     */
    fun initialize(config: SecurityConfiguration = SecurityConfiguration()) {
        try {
            securityConfig = config
            
            // Initialize certificate pinning
            val pinningConfig = CertificatePinningManager.PinningConfig(
                enableCertificatePinning = config.enableCertificatePinning,
                enablePublicKeyPinning = config.enableTLSValidation,
                allowLocalNetworkForDebug = config.allowInsecureConnections,
                enableFailureReporting = true
            )
            
            certificatePinningManager.initialize(pinningConfig)
            
            // Create secure HTTP client
            secureHttpClient = createSecureHttpClient(config)
            
            Timber.d("Network security manager initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize network security manager")
            throw SecurityException("Network security initialization failed", e)
        }
    }
    
    /**
     * Create secure HTTP client with all security features
     */
    private fun createSecureHttpClient(config: SecurityConfiguration): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(config.writeTimeoutSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(config.retryOnConnectionFailure)
            .followRedirects(config.followRedirects)
            .followSslRedirects(config.followSSLRedirects)
        
        // Add security interceptors
        addSecurityInterceptors(builder, config)
        
        // Add logging interceptor for debugging
        if (config.enableNetworkLogging) {
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                Timber.tag("SecureNetwork").d(message)
            }.apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addInterceptor(loggingInterceptor)
        }
        
        // Apply certificate pinning
        val pinningConfig = CertificatePinningManager.PinningConfig(
            enableCertificatePinning = config.enableCertificatePinning,
            enablePublicKeyPinning = config.enableTLSValidation,
            allowLocalNetworkForDebug = config.allowInsecureConnections
        )
        
        return certificatePinningManager.createSecuredHttpClient(builder, pinningConfig)
    }
    
    /**
     * Add security interceptors to HTTP client
     */
    private fun addSecurityInterceptors(builder: OkHttpClient.Builder, config: SecurityConfiguration) {
        // HTTPS enforcement interceptor
        if (config.requireHTTPS) {
            builder.addInterceptor(HttpsEnforcementInterceptor())
        }
        
        // Security headers interceptor
        builder.addInterceptor(SecurityHeadersInterceptor())
        
        // Certificate pinning failure interceptor
        if (config.enableCertificatePinning) {
            builder.addInterceptor(CertificatePinningFailureInterceptor())
        }
        
        // Network security event interceptor
        builder.addNetworkInterceptor(NetworkSecurityEventInterceptor())
    }
    
    /**
     * Get secure HTTP client instance
     */
    fun getSecureHttpClient(): OkHttpClient {
        return secureHttpClient ?: throw IllegalStateException("Network security manager not initialized")
    }
    
    /**
     * Create secure Retrofit instance for API calls
     */
    fun createSecureRetrofitInstance(baseUrl: String): Retrofit {
        val httpClient = getSecureHttpClient()
        
        val moshi = Moshi.Builder()
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    
    /**
     * Validate URL security before making requests
     */
    fun validateUrlSecurity(url: String): SecurityValidationResult {
        return try {
            val httpUrl = url.toHttpUrl()
            
            // Check HTTPS requirement
            if (securityConfig?.requireHTTPS == true && httpUrl.scheme != "https") {
                return SecurityValidationResult.Invalid("HTTPS required but URL uses ${httpUrl.scheme}")
            }
            
            // Check if domain is pinned
            val host = httpUrl.host
            val isPinnedDomain = certificatePinningManager.getPinningStatistics()
                .let { stats ->
                    @Suppress("UNCHECKED_CAST")
                    val pinnedDomains = stats["pinnedDomains"] as? List<String> ?: emptyList()
                    pinnedDomains.contains(host)
                }
            
            // Check for known secure domains
            val isKnownSecure = isKnownSecureDomain(host)
            
            SecurityValidationResult.Valid(
                isHttps = httpUrl.scheme == "https",
                isPinnedDomain = isPinnedDomain,
                isKnownSecure = isKnownSecure,
                host = host
            )
        } catch (e: Exception) {
            Timber.e(e, "Error validating URL security: $url")
            SecurityValidationResult.Invalid("URL validation error: ${e.message}")
        }
    }
    
    /**
     * Check if domain is known to be secure
     */
    private fun isKnownSecureDomain(host: String): Boolean {
        val secureDomains = listOf(
            "api.openai.com",
            "api.anthropic.com",
            "api.deepseek.com",
            "api.aimlapi.com",
            "googleapis.com",
            "github.com",
            "stackoverflow.com"
        )
        
        return secureDomains.any { domain ->
            host == domain || host.endsWith(".$domain")
        }
    }
    
    /**
     * Handle network security failures
     */
    fun handleSecurityFailure(error: Throwable, url: String) {
        when (error) {
            is SSLHandshakeException -> {
                Timber.e(error, "SSL handshake failed for URL: $url")
                reportSecurityEvent("ssl_handshake_failure", url, error.message)
            }
            is SSLPeerUnverifiedException -> {
                Timber.e(error, "SSL peer verification failed for URL: $url")
                reportSecurityEvent("ssl_peer_verification_failure", url, error.message)
            }
            is CertificateException -> {
                Timber.e(error, "Certificate validation failed for URL: $url")
                reportSecurityEvent("certificate_validation_failure", url, error.message)
            }
            else -> {
                Timber.e(error, "Network security error for URL: $url")
                reportSecurityEvent("network_security_error", url, error.message)
            }
        }
    }
    
    /**
     * Report security events for monitoring
     */
    private fun reportSecurityEvent(eventType: String, url: String, details: String?) {
        val securityEvent = mapOf(
            "event_type" to eventType,
            "url" to url,
            "details" to (details ?: "No details"),
            "timestamp" to System.currentTimeMillis(),
            "security_config" to securityConfig.toString()
        )
        
        Timber.tag("SecurityEvent").w("Security event: $securityEvent")
        
        // Here you could integrate with your analytics/monitoring system
        // Analytics.logEvent("network_security_event", securityEvent)
    }
    
    /**
     * Get network security statistics
     */
    fun getSecurityStatistics(): Map<String, Any> {
        val pinningStats = certificatePinningManager.getPinningStatistics()
        
        return mapOf(
            "certificate_pinning" to pinningStats,
            "security_config" to (securityConfig?.toString() ?: "Not configured"),
            "secure_client_configured" to (secureHttpClient != null),
            "initialization_status" to "initialized"
        )
    }
    
    /**
     * Reset network security configuration
     */
    fun reset() {
        secureHttpClient = null
        securityConfig = null
        certificatePinningManager.reset()
        Timber.d("Network security manager reset")
    }
    
    /**
     * Security validation result
     */
    sealed class SecurityValidationResult {
        data class Valid(
            val isHttps: Boolean,
            val isPinnedDomain: Boolean,
            val isKnownSecure: Boolean,
            val host: String
        ) : SecurityValidationResult()
        
        data class Invalid(val reason: String) : SecurityValidationResult()
    }
    
    /**
     * HTTPS enforcement interceptor
     */
    private class HttpsEnforcementInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            
            if (request.url.scheme != "https") {
                throw SecurityException("HTTPS required but request uses ${request.url.scheme}")
            }
            
            return chain.proceed(request)
        }
    }
    
    /**
     * Security headers interceptor
     */
    private class SecurityHeadersInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("Cache-Control", "no-cache")
                .build()
            
            return chain.proceed(request)
        }
    }
    
    /**
     * Certificate pinning failure interceptor
     */
    private class CertificatePinningFailureInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            return try {
                chain.proceed(chain.request())
            } catch (e: SSLPeerUnverifiedException) {
                Timber.e(e, "Certificate pinning failure detected")
                throw SecurityException("Certificate pinning validation failed", e)
            }
        }
    }
    
    /**
     * Network security event interceptor
     */
    private class NetworkSecurityEventInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val startTime = System.currentTimeMillis()
            
            return try {
                val response = chain.proceed(request)
                val endTime = System.currentTimeMillis()
                
                Timber.v("Secure request completed: ${request.url} in ${endTime - startTime}ms")
                response
            } catch (e: Exception) {
                Timber.w(e, "Secure request failed: ${request.url}")
                throw e
            }
        }
    }
}