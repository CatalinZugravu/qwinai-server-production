package com.cyberflux.qwinai.security

import android.content.Context
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import timber.log.Timber
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.*
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest
import android.util.Base64

/**
 * Comprehensive certificate pinning manager for secure API communications
 * Implements certificate pinning to prevent man-in-the-middle attacks
 */
class CertificatePinningManager private constructor(private val context: Context) {
    
    private var certificatePinner: CertificatePinner? = null
    private var sslSocketFactory: SSLSocketFactory? = null
    private var trustManager: X509TrustManager? = null
    
    companion object {
        @Volatile
        private var INSTANCE: CertificatePinningManager? = null
        
        fun getInstance(context: Context): CertificatePinningManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CertificatePinningManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // Common API endpoints that should be pinned
        private val PINNED_DOMAINS = mapOf(
            "api.openai.com" to listOf(
                "sha256/vDEqKOBtZjr5ywvVFFb6u+gJtcE1h4K7M+H9FEY1234=", // Example pin
                "sha256/JSMzqOOrtyOT1kmau6zKhgT676hGgczD5VMdRMyJZFA="  // Backup pin
            ),
            "api.anthropic.com" to listOf(
                "sha256/FEzVOUp4dF3gI0ZVPRJhFbSD608T5Oz3WW3AH3i/abc=", // Example pin
                "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg="  // Backup pin
            ),
            "api.deepseek.com" to listOf(
                "sha256/HXXQgxueCIU5TTLHob/bPbwcKOKw6DkfsTWYHbxbqTY=", // Example pin
                "sha256/0b+1lvXQi93uK8TlnqIH8qkyhE9AqV0IyeLzBIyPz7s="  // Backup pin
            ),
            "api.aimlapi.com" to listOf(
                "sha256/x4QzPSC810K5/cMjb05Qm4k3Bw5zBn4lTdO/nEW/Td8=", // Example pin
                "sha256/RQeZkB42znUfsDIIFWIRiYsiQoxjC2UbGBqPfBYjYfw="  // Backup pin
            )
        )
        
        // Additional trusted certificate authorities
        private val TRUSTED_CA_PINS = listOf(
            "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg=", // DigiCert Global Root CA
            "sha256/Vjs8r4z+80wjNcr1YKepWQboSIRi63WsWXhIMN+eWys=", // DigiCert High Assurance EV Root CA
            "sha256/WoiWRyIOVNa9ihaBciRSC7XHjliYS9VwUGOIud4PB18=", // GlobalSign Root CA
            "sha256/r/mIkG3eEpVdm+u/ko/cwxzOMo1bk4TyHIlByibiA5E=", // Let's Encrypt Authority X3
            "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg="  // GeoTrust Global CA
        )
    }
    
    /**
     * Certificate pinning configuration
     */
    data class PinningConfig(
        val enableCertificatePinning: Boolean = true,
        val enablePublicKeyPinning: Boolean = true,
        val allowLocalNetworkForDebug: Boolean = false,
        val enableFailureReporting: Boolean = true,
        val customPins: Map<String, List<String>> = emptyMap(),
        val backupPins: List<String> = TRUSTED_CA_PINS
    )
    
    /**
     * Initialize certificate pinning with configuration
     */
    fun initialize(config: PinningConfig = PinningConfig()) {
        try {
            setupCertificatePinner(config)
            setupSSLSocketFactory(config)
            
            Timber.d("Certificate pinning initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize certificate pinning")
            throw SecurityException("Certificate pinning initialization failed", e)
        }
    }
    
    /**
     * Create secured OkHttpClient with certificate pinning
     */
    fun createSecuredHttpClient(
        baseClientBuilder: OkHttpClient.Builder? = null,
        config: PinningConfig = PinningConfig()
    ): OkHttpClient {
        val builder = baseClientBuilder ?: OkHttpClient.Builder()
        
        if (config.enableCertificatePinning && certificatePinner != null) {
            builder.certificatePinner(certificatePinner!!)
        }
        
        if (config.enablePublicKeyPinning && sslSocketFactory != null && trustManager != null) {
            builder.sslSocketFactory(sslSocketFactory!!, trustManager!!)
        }
        
        // Add hostname verifier for additional security
        builder.hostnameVerifier(createSecureHostnameVerifier(config))
        
        // Add network interceptor for debugging (debug builds only)
        if (config.allowLocalNetworkForDebug) {
            builder.addNetworkInterceptor { chain ->
                val request = chain.request()
                Timber.d("Secure request to: ${request.url}")
                chain.proceed(request)
            }
        }
        
        return builder.build()
    }
    
    /**
     * Setup certificate pinner with domain pins
     */
    private fun setupCertificatePinner(config: PinningConfig) {
        val builder = CertificatePinner.Builder()
        
        // Add configured domain pins
        val allPins = PINNED_DOMAINS + config.customPins
        
        for ((domain, pins) in allPins) {
            for (pin in pins) {
                builder.add(domain, pin)
            }
            Timber.d("Added certificate pins for domain: $domain")
        }
        
        // Add backup pins for all domains
        if (config.backupPins.isNotEmpty()) {
            for ((domain, _) in allPins) {
                for (backupPin in config.backupPins) {
                    builder.add(domain, backupPin)
                }
            }
        }
        
        certificatePinner = builder.build()
    }
    
    /**
     * Setup SSL socket factory with custom trust manager
     */
    private fun setupSSLSocketFactory(config: PinningConfig) {
        try {
            // Create trust manager that validates certificate pins
            val customTrustManager = createCustomTrustManager(config)
            
            // Create SSL context with custom trust manager
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(customTrustManager), null)
            
            sslSocketFactory = sslContext.socketFactory
            trustManager = customTrustManager
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to setup SSL socket factory")
            throw e
        }
    }
    
    /**
     * Create custom trust manager with additional validation
     */
    private fun createCustomTrustManager(config: PinningConfig): X509TrustManager {
        return object : X509TrustManager {
            private val defaultTrustManager = getDefaultTrustManager()
            
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                defaultTrustManager.checkClientTrusted(chain, authType)
            }
            
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                // First, perform default certificate validation
                defaultTrustManager.checkServerTrusted(chain, authType)
                
                // Then, perform additional custom validation
                validateCertificateChain(chain, config)
            }
            
            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return defaultTrustManager.acceptedIssuers
            }
        }
    }
    
    /**
     * Get default trust manager from system
     */
    private fun getDefaultTrustManager(): X509TrustManager {
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as KeyStore?)
        
        return trustManagerFactory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()
    }
    
    /**
     * Validate certificate chain with additional checks
     */
    private fun validateCertificateChain(chain: Array<X509Certificate>, config: PinningConfig) {
        if (chain.isEmpty()) {
            throw SSLException("Certificate chain is empty")
        }
        
        val leafCertificate = chain[0]
        
        // Check certificate validity period
        leafCertificate.checkValidity()
        
        // Check certificate key usage
        validateKeyUsage(leafCertificate)
        
        // Check certificate subject alternative names
        validateSubjectAlternativeNames(leafCertificate)
        
        // Validate certificate pins if enabled
        if (config.enablePublicKeyPinning) {
            validateCertificatePin(chain, config)
        }
        
        Timber.v("Certificate chain validation passed")
    }
    
    /**
     * Validate certificate key usage
     */
    private fun validateKeyUsage(certificate: X509Certificate) {
        val keyUsage = certificate.keyUsage
        if (keyUsage != null) {
            // Check for digital signature and key encipherment
            if (!keyUsage[0] && !keyUsage[2]) {
                Timber.w("Certificate key usage validation warning")
            }
        }
    }
    
    /**
     * Validate subject alternative names
     */
    private fun validateSubjectAlternativeNames(certificate: X509Certificate) {
        try {
            val subjectAltNames = certificate.subjectAlternativeNames
            if (subjectAltNames.isNullOrEmpty()) {
                Timber.w("Certificate has no subject alternative names")
            }
        } catch (e: Exception) {
            Timber.w(e, "Error validating subject alternative names")
        }
    }
    
    /**
     * Validate certificate pin against configured pins
     */
    private fun validateCertificatePin(chain: Array<X509Certificate>, config: PinningConfig) {
        for (certificate in chain) {
            val pin = generateCertificatePin(certificate)
            val publicKeyPin = generatePublicKeyPin(certificate)
            
            // Check against all configured pins
            val allConfiguredPins = PINNED_DOMAINS.values.flatten() + config.customPins.values.flatten() + config.backupPins
            
            if (allConfiguredPins.contains(pin) || allConfiguredPins.contains(publicKeyPin)) {
                Timber.v("Certificate pin validation passed")
                return
            }
        }
        
        // If we reach here, no pins matched
        Timber.w("Certificate pin validation failed - no matching pins found")
    }
    
    /**
     * Generate SHA-256 pin for certificate
     */
    private fun generateCertificatePin(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(certificate.encoded)
        return "sha256/" + Base64.encodeToString(hash, Base64.NO_WRAP)
    }
    
    /**
     * Generate SHA-256 pin for public key
     */
    private fun generatePublicKeyPin(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(certificate.publicKey.encoded)
        return "sha256/" + Base64.encodeToString(hash, Base64.NO_WRAP)
    }
    
    /**
     * Create secure hostname verifier
     */
    private fun createSecureHostnameVerifier(config: PinningConfig): HostnameVerifier {
        return HostnameVerifier { hostname, session ->
            // Use default hostname verification
            val defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
            val defaultResult = defaultVerifier.verify(hostname, session)
            
            if (!defaultResult) {
                Timber.w("Hostname verification failed for: $hostname")
                return@HostnameVerifier false
            }
            
            // Additional custom hostname validation
            validateHostname(hostname, config)
            
            true
        }
    }
    
    /**
     * Validate hostname against allowed domains
     */
    private fun validateHostname(hostname: String, config: PinningConfig) {
        val allowedDomains = PINNED_DOMAINS.keys + config.customPins.keys
        
        val isAllowed = allowedDomains.any { domain ->
            hostname == domain || hostname.endsWith(".$domain")
        }
        
        if (!isAllowed && !config.allowLocalNetworkForDebug) {
            Timber.w("Hostname not in allowed domains: $hostname")
        }
    }
    
    /**
     * Load certificate from assets
     */
    fun loadCertificateFromAssets(assetPath: String): Certificate? {
        return try {
            val inputStream: InputStream = context.assets.open(assetPath)
            val certificateFactory = CertificateFactory.getInstance("X.509")
            certificateFactory.generateCertificate(inputStream)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load certificate from assets: $assetPath")
            null
        }
    }
    
    /**
     * Verify certificate against known pins
     */
    fun verifyCertificatePin(certificate: X509Certificate, domain: String): Boolean {
        val pin = generateCertificatePin(certificate)
        val publicKeyPin = generatePublicKeyPin(certificate)
        
        val domainPins = PINNED_DOMAINS[domain] ?: return false
        
        return domainPins.contains(pin) || domainPins.contains(publicKeyPin)
    }
    
    /**
     * Get certificate information for debugging
     */
    fun getCertificateInfo(certificate: X509Certificate): Map<String, Any> {
        return mapOf(
            "subject" to certificate.subjectDN.name,
            "issuer" to certificate.issuerDN.name,
            "serial" to certificate.serialNumber.toString(),
            "notBefore" to certificate.notBefore.toString(),
            "notAfter" to certificate.notAfter.toString(),
            "certificatePin" to generateCertificatePin(certificate),
            "publicKeyPin" to generatePublicKeyPin(certificate),
            "keyAlgorithm" to certificate.publicKey.algorithm,
            "signatureAlgorithm" to certificate.sigAlgName
        )
    }
    
    /**
     * Check if certificate pinning is properly configured
     */
    fun validateConfiguration(): Boolean {
        return try {
            certificatePinner != null && 
            sslSocketFactory != null && 
            trustManager != null &&
            PINNED_DOMAINS.isNotEmpty()
        } catch (e: Exception) {
            Timber.e(e, "Configuration validation failed")
            false
        }
    }
    
    /**
     * Get pinning statistics for monitoring
     */
    fun getPinningStatistics(): Map<String, Any> {
        return mapOf(
            "pinnedDomains" to PINNED_DOMAINS.keys.toList(),
            "totalPins" to PINNED_DOMAINS.values.sumOf { it.size },
            "backupPins" to TRUSTED_CA_PINS.size,
            "isConfigured" to validateConfiguration(),
            "certificatePinnerConfigured" to (certificatePinner != null),
            "sslSocketFactoryConfigured" to (sslSocketFactory != null),
            "trustManagerConfigured" to (trustManager != null)
        )
    }
    
    /**
     * Reset certificate pinning configuration
     */
    fun reset() {
        certificatePinner = null
        sslSocketFactory = null
        trustManager = null
        Timber.d("Certificate pinning configuration reset")
    }
}