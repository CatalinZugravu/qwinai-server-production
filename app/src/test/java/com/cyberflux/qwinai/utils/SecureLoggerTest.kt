package com.cyberflux.qwinai.utils

import com.cyberflux.qwinai.BuildConfig
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

/**
 * CRITICAL TEST: SecureLogger test suite
 * Verifies that logging security measures work correctly
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SecureLoggerTest {

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        
        // Mock Timber for testing
        mockkStatic(Timber::class)
        every { Timber.tag(any()).d(any<String>()) } just Runs
        every { Timber.tag(any()).v(any<String>()) } just Runs
        every { Timber.tag(any()).i(any<String>()) } just Runs
        every { Timber.tag(any()).w(any<String>()) } just Runs
        every { Timber.tag(any()).e(any<Throwable>(), any<String>()) } just Runs
        every { Timber.d(any<String>()) } just Runs
        every { Timber.v(any<String>()) } just Runs
        every { Timber.i(any<String>()) } just Runs
        every { Timber.w(any<String>()) } just Runs
        every { Timber.e(any<Throwable>(), any<String>()) } just Runs
    }

    @Test
    fun `test debug logging only works in debug builds`() {
        // Given
        val testMessage = "Debug test message"
        
        // When
        SecureLogger.d("Test", testMessage)
        
        // Then
        if (BuildConfig.DEBUG) {
            verify { Timber.tag("Test").d(testMessage) }
        } else {
            verify(exactly = 0) { Timber.tag("Test").d(any<String>()) }
        }
    }

    @Test
    fun `test verbose logging only works in debug builds`() {
        // Given
        val testMessage = "Verbose test message"
        
        // When
        SecureLogger.v("Test", testMessage)
        
        // Then
        if (BuildConfig.DEBUG) {
            verify { Timber.tag("Test").v(testMessage) }
        } else {
            verify(exactly = 0) { Timber.tag("Test").v(any<String>()) }
        }
    }

    @Test
    fun `test info logging works in all builds`() {
        // Given
        val testMessage = "Info test message"
        
        // When
        SecureLogger.i("Test", testMessage)
        
        // Then
        verify { Timber.tag("Test").i(testMessage) }
    }

    @Test
    fun `test warning logging works in all builds`() {
        // Given
        val testMessage = "Warning test message"
        
        // When
        SecureLogger.w("Test", testMessage)
        
        // Then
        verify { Timber.tag("Test").w(testMessage) }
    }

    @Test
    fun `test error logging works in all builds`() {
        // Given
        val testMessage = "Error test message"
        val testException = RuntimeException("Test exception")
        
        // When
        SecureLogger.e("Test", testException, testMessage)
        
        // Then
        verify { Timber.tag("Test").e(testException, testMessage) }
    }

    @Test
    fun `test sensitive data redaction in production`() {
        // Given
        val sensitiveData = "secret-api-key-12345"
        
        // When
        SecureLogger.logSensitive("Test", "API Key", sensitiveData)
        
        // Then
        if (BuildConfig.DEBUG) {
            verify { Timber.tag("Test").d("API Key: $sensitiveData") }
        } else {
            verify { Timber.tag("Test").d("API Key: [REDACTED]") }
        }
    }

    @Test
    fun `test API response logging redacts sensitive fields`() {
        // Given
        val response = """
            {
                "api_key": "secret-key-123",
                "token": "bearer-token-456",
                "authorization": "auth-header-789",
                "data": "safe-data"
            }
        """.trimIndent()
        
        // When
        SecureLogger.logApiResponse("Test", "/api/test", response)
        
        // Then
        if (BuildConfig.DEBUG) {
            verify { 
                Timber.tag("Test").d(match { logMessage ->
                    logMessage.contains("\"api_key\":\"[REDACTED]\"") &&
                    logMessage.contains("\"token\":\"[REDACTED]\"") &&
                    logMessage.contains("\"authorization\":\"[REDACTED]\"") &&
                    logMessage.contains("safe-data")
                })
            }
        } else {
            verify { Timber.tag("Test").i("API call to /api/test completed") }
        }
    }

    @Test
    fun `test user action logging filters sensitive keys`() {
        // Given
        val actionDetails = mapOf(
            "username" to "testuser",
            "password" to "secret123",
            "api_key" to "key123",
            "action_type" to "login"
        )
        
        // When
        SecureLogger.logUserAction("login", actionDetails)
        
        // Then
        verify { 
            Timber.tag("UserAction").i(match { logMessage ->
                logMessage.contains("username") &&
                logMessage.contains("action_type") &&
                !logMessage.contains("password") &&
                !logMessage.contains("api_key")
            })
        }
    }

    @Test
    fun `test performance logging works correctly`() {
        // Given
        val operation = "database_query"
        val duration = 150L
        val additionalInfo = "users table"
        
        // When
        SecureLogger.logPerformance(operation, duration, additionalInfo)
        
        // Then
        verify { 
            Timber.tag("Performance").i("$operation took ${duration}ms ($additionalInfo)")
        }
    }

    @Test
    fun `test device detection logging`() {
        // Given
        val isHuawei = true
        val method = "brand-detection"
        
        // When
        SecureLogger.logDeviceDetection(isHuawei, method)
        
        // Then
        verify { 
            Timber.tag("DeviceDetection").i("Device type determined: Huawei via $method")
        }
    }

    @Test
    fun `test billing logging with success`() {
        // Given
        val operation = "purchase"
        val success = true
        val details = "premium subscription"
        
        // When
        SecureLogger.logBilling(operation, success, details)
        
        // Then
        if (BuildConfig.DEBUG) {
            verify { 
                Timber.tag("Billing").i("Billing $operation: SUCCESS - $details")
            }
        } else {
            verify { 
                Timber.tag("Billing").i("Billing $operation: SUCCESS - [details redacted]")
            }
        }
    }

    @Test
    fun `test billing logging with failure`() {
        // Given
        val operation = "purchase"
        val success = false
        
        // When
        SecureLogger.logBilling(operation, success)
        
        // Then
        verify { 
            Timber.tag("Billing").i("Billing $operation: FAILED")
        }
    }

    @Test
    fun `test security event logging`() {
        // Given
        val event = "unauthorized_access_attempt"
        val severity = "WARNING"
        
        // When
        SecureLogger.logSecurityEvent(event, severity)
        
        // Then
        verify { 
            Timber.tag("Security").w("[$severity] Security event: $event")
        }
    }

    @Test
    fun `test null tag handling`() {
        // Given
        val testMessage = "Test message without tag"
        
        // When
        SecureLogger.i(null, testMessage)
        
        // Then
        verify { Timber.i(testMessage) }
    }

    @Test
    fun `test empty sensitive data handling`() {
        // Given
        val emptyData = ""
        
        // When
        SecureLogger.logSensitive("Test", "Empty Data", emptyData)
        
        // Then
        if (BuildConfig.DEBUG) {
            verify { Timber.tag("Test").d("Empty Data: ") }
        } else {
            verify { Timber.tag("Test").d("Empty Data: [REDACTED]") }
        }
    }

    @Test
    fun `test multiple redaction patterns in API response`() {
        // Given
        val response = """
            {
                "api-key": "secret1",
                "API_KEY": "secret2", 
                "token": "secret3",
                "authorization": "secret4",
                "normal_field": "safe_value"
            }
        """.trimIndent()
        
        // When
        SecureLogger.logApiResponse("Test", "/api/complex", response)
        
        // Then
        if (BuildConfig.DEBUG) {
            verify { 
                Timber.tag("Test").d(match { logMessage ->
                    !logMessage.contains("secret1") &&
                    !logMessage.contains("secret2") &&
                    !logMessage.contains("secret3") &&
                    !logMessage.contains("secret4") &&
                    logMessage.contains("safe_value")
                })
            }
        }
    }
}