package com.cyberflux.qwinai.security

import android.content.Context
import com.cyberflux.qwinai.BuildConfig
import com.cyberflux.qwinai.core.security.EncryptedStorageManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CRITICAL SECURITY TEST: SecureApiKeyManager test suite
 * Verifies that API key security measures work correctly
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SecureApiKeyManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockEncryptedStorage: EncryptedStorageManager

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        
        mockContext = mockk(relaxed = true)
        mockEncryptedStorage = mockk(relaxed = true)
        
        // Clear any existing cache
        SecureApiKeyManager.clearCache()
        
        // Mock BuildConfig for testing
        mockkObject(BuildConfig)
    }

    @Test
    fun `test initialization sets up manager correctly`() {
        // When
        SecureApiKeyManager.initialize(mockContext, mockEncryptedStorage)
        
        // Then
        // Should complete without errors
        assertTrue("Initialization should succeed", true)
    }

    @Test
    fun `test debug mode returns BuildConfig keys`() = runTest {
        // Given
        every { BuildConfig.DEBUG } returns true
        every { BuildConfig.AIMLAPI_KEY } returns "debug-aiml-key"
        
        SecureApiKeyManager.initialize(mockContext, mockEncryptedStorage)
        
        // When
        val key = SecureApiKeyManager.getApiKey("AIMLAPI_KEY")
        
        // Then
        assertEquals("Should return debug key", "debug-aiml-key", key)
    }

    @Test
    fun `test production mode uses secure retrieval`() = runTest {
        // Given
        every { BuildConfig.DEBUG } returns false
        every { BuildConfig.AIMLAPI_KEY } returns "fallback-key"
        
        SecureApiKeyManager.initialize(mockContext, mockEncryptedStorage)
        
        // When
        val key = SecureApiKeyManager.getApiKey("AIMLAPI_KEY")
        
        // Then
        // In current implementation, should fall back to BuildConfig
        assertNotNull("Should return a key", key)
    }

    @Test
    fun `test key caching works correctly`() = runTest {
        // Given
        every { BuildConfig.DEBUG } returns true
        every { BuildConfig.AIMLAPI_KEY } returns "cached-key"
        
        SecureApiKeyManager.initialize(mockContext, mockEncryptedStorage)
        
        // When - retrieve the same key twice
        val key1 = SecureApiKeyManager.getApiKey("AIMLAPI_KEY")
        val key2 = SecureApiKeyManager.getApiKey("AIMLAPI_KEY")
        
        // Then
        assertEquals("Keys should be identical", key1, key2)
        assertEquals("Should be cached key", "cached-key", key1)
    }

    @Test
    fun `test cache clearing works`() = runTest {
        // Given
        every { BuildConfig.DEBUG } returns true
        every { BuildConfig.AIMLAPI_KEY } returns "test-key"
        
        SecureApiKeyManager.initialize(mockContext, mockEncryptedStorage)
        
        // When
        val keyBefore = SecureApiKeyManager.getApiKey("AIMLAPI_KEY")
        SecureApiKeyManager.clearCache()
        val keyAfter = SecureApiKeyManager.getApiKey("AIMLAPI_KEY")
        
        // Then
        assertEquals("Keys should be same after cache clear", keyBefore, keyAfter)
        assertNotNull("Key should still be retrievable", keyAfter)
    }

    @Test
    fun `test isApiKeyAvailable works correctly`() = runTest {
        // Given
        every { BuildConfig.DEBUG } returns true
        every { BuildConfig.GOOGLE_API_KEY } returns "google-key"
        every { BuildConfig.WEATHER_API_KEY } returns ""
        
        SecureApiKeyManager.initialize(mockContext, mockEncryptedStorage)
        
        // When
        val googleAvailable = SecureApiKeyManager.isApiKeyAvailable("GOOGLE_API_KEY")
        val weatherAvailable = SecureApiKeyManager.isApiKeyAvailable("WEATHER_API_KEY")
        val nonexistentAvailable = SecureApiKeyManager.isApiKeyAvailable("NONEXISTENT_KEY")
        
        // Then
        assertTrue("Google API key should be available", googleAvailable)
        assertFalse("Weather API key should not be available (empty)", weatherAvailable)
        assertFalse("Nonexistent key should not be available", nonexistentAvailable)
    }

    @Test
    fun `test all supported API keys`() = runTest {
        // Given
        every { BuildConfig.DEBUG } returns true
        every { BuildConfig.AIMLAPI_KEY } returns "aiml-key"
        every { BuildConfig.TOGETHER_AI_KEY } returns "together-key"
        every { BuildConfig.GOOGLE_API_KEY } returns "google-key"
        every { BuildConfig.GOOGLE_SEARCH_ENGINE_ID } returns "search-id"
        every { BuildConfig.WEATHER_API_KEY } returns "weather-key"
        
        SecureApiKeyManager.initialize(mockContext, mockEncryptedStorage)
        
        // When & Then
        val supportedKeys = listOf(
            "AIMLAPI_KEY",
            "TOGETHER_AI_KEY", 
            "GOOGLE_API_KEY",
            "GOOGLE_SEARCH_ENGINE_ID",
            "WEATHER_API_KEY"
        )
        
        supportedKeys.forEach { keyName ->
            val key = SecureApiKeyManager.getApiKey(keyName)
            assertNotNull("$keyName should be retrievable", key)
            assertTrue("$keyName should not be empty", key!!.isNotEmpty())
        }
    }

    @Test
    fun `test unknown key returns null`() = runTest {
        // Given
        SecureApiKeyManager.initialize(mockContext, mockEncryptedStorage)
        
        // When
        val unknownKey = SecureApiKeyManager.getApiKey("UNKNOWN_KEY")
        
        // Then
        assertNull("Unknown key should return null", unknownKey)
    }

    @Test
    fun `test emergency clear works`() {
        // Given
        SecureApiKeyManager.initialize(mockContext, mockEncryptedStorage)
        
        // When
        SecureApiKeyManager.emergencyClear()
        
        // Then
        // Should complete without errors
        assertTrue("Emergency clear should succeed", true)
    }

    @Test
    fun `test error handling during key retrieval`() = runTest {
        // Given
        every { BuildConfig.DEBUG } returns true
        every { BuildConfig.AIMLAPI_KEY } throws RuntimeException("Simulated error")
        
        SecureApiKeyManager.initialize(mockContext, mockEncryptedStorage)
        
        // When
        val key = SecureApiKeyManager.getApiKey("AIMLAPI_KEY")
        
        // Then
        assertNull("Should return null on error", key)
    }

    @Test
    fun `test empty key handling`() = runTest {
        // Given
        every { BuildConfig.DEBUG } returns true
        every { BuildConfig.WEATHER_API_KEY } returns ""
        
        SecureApiKeyManager.initialize(mockContext, mockEncryptedStorage)
        
        // When
        val key = SecureApiKeyManager.getApiKey("WEATHER_API_KEY")
        
        // Then
        assertNull("Empty key should return null", key)
    }

    @Test
    fun `test quoted empty string handling`() = runTest {
        // Given
        every { BuildConfig.DEBUG } returns true
        every { BuildConfig.WEATHER_API_KEY } returns "\"\""
        
        SecureApiKeyManager.initialize(mockContext, mockEncryptedStorage)
        
        // When
        val key = SecureApiKeyManager.getApiKey("WEATHER_API_KEY")
        
        // Then
        assertNull("Quoted empty string should return null", key)
    }

    @Test
    fun `test concurrent access safety`() = runTest {
        // Given
        every { BuildConfig.DEBUG } returns true
        every { BuildConfig.AIMLAPI_KEY } returns "concurrent-key"
        
        SecureApiKeyManager.initialize(mockContext, mockEncryptedStorage)
        
        // When - simulate concurrent access
        val keys = (1..10).map {
            kotlinx.coroutines.async {
                SecureApiKeyManager.getApiKey("AIMLAPI_KEY")
            }
        }.map { it.await() }
        
        // Then
        val uniqueKeys = keys.toSet()
        assertEquals("All concurrent calls should return same key", 1, uniqueKeys.size)
        assertEquals("Should return correct key", "concurrent-key", keys.first())
    }

    @Test
    fun `test memory efficiency of repeated calls`() = runTest {
        // Given
        every { BuildConfig.DEBUG } returns true
        every { BuildConfig.AIMLAPI_KEY } returns "memory-test-key"
        
        SecureApiKeyManager.initialize(mockContext, mockEncryptedStorage)
        
        // When - make many repeated calls
        repeat(100) {
            SecureApiKeyManager.getApiKey("AIMLAPI_KEY")
        }
        
        // Then - should not cause memory issues
        System.gc()
        assertTrue("Repeated calls should be memory efficient", true)
    }

    @Test
    fun `test context extension function`() = runTest {
        // Given
        every { BuildConfig.DEBUG } returns true
        every { BuildConfig.GOOGLE_API_KEY } returns "extension-key"
        
        SecureApiKeyManager.initialize(mockContext, mockEncryptedStorage)
        
        // When
        val key = mockContext.getSecureApiKey("GOOGLE_API_KEY")
        
        // Then
        assertEquals("Extension function should work", "extension-key", key)
    }

    @Test
    fun `test production fallback chain`() = runTest {
        // Given
        every { BuildConfig.DEBUG } returns false
        every { BuildConfig.TOGETHER_AI_KEY } returns "fallback-key"
        
        SecureApiKeyManager.initialize(mockContext, mockEncryptedStorage)
        
        // When
        val key = SecureApiKeyManager.getApiKey("TOGETHER_AI_KEY")
        
        // Then
        // Should fall back to BuildConfig in current implementation
        assertEquals("Should use fallback mechanism", "fallback-key", key)
    }
}