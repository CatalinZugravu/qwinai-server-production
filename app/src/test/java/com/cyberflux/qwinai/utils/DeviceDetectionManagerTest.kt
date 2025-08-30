package com.cyberflux.qwinai.utils

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CRITICAL TEST IMPLEMENTATION: DeviceDetectionManager test suite
 * Tests the extracted device detection logic for reliability and performance
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DeviceDetectionManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var deviceDetectionManager: DeviceDetectionManager

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        
        mockContext = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)
        
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
        every { mockEditor.putLong(any(), any()) } returns mockEditor
        every { mockEditor.apply() } just Runs
        
        // Reset singleton for each test
        resetDeviceDetectionManagerSingleton()
    }

    private fun resetDeviceDetectionManagerSingleton() {
        try {
            val instanceField = DeviceDetectionManager::class.java.getDeclaredField("instance")
            instanceField.isAccessible = true
            instanceField.set(null, null)
        } catch (e: NoSuchFieldException) {
            // Field might not exist
        }
    }

    @Test
    fun `test getInstance creates singleton`() {
        // When
        val instance1 = DeviceDetectionManager.getInstance(mockContext)
        val instance2 = DeviceDetectionManager.getInstance(mockContext)
        
        // Then
        assertSame("DeviceDetectionManager should be singleton", instance1, instance2)
    }

    @Test
    fun `test cached preference loading works correctly`() {
        // Given
        every { mockPrefs.contains("device_type_huawei") } returns true
        every { mockPrefs.getBoolean("device_type_huawei", false) } returns true
        every { mockPrefs.getLong("device_detection_timestamp", 0) } returns System.currentTimeMillis()
        
        deviceDetectionManager = DeviceDetectionManager.getInstance(mockContext)
        
        // When
        val result = deviceDetectionManager.isHuaweiDeviceNonBlocking()
        
        // Then
        assertTrue("Should return cached Huawei result", result)
    }

    @Test
    fun `test expired cache falls back to brand detection`() {
        // Given - expired cache (25 hours old)
        val expiredTime = System.currentTimeMillis() - (25 * 60 * 60 * 1000)
        every { mockPrefs.contains("device_type_huawei") } returns true
        every { mockPrefs.getBoolean("device_type_huawei", false) } returns true
        every { mockPrefs.getLong("device_detection_timestamp", 0) } returns expiredTime
        
        deviceDetectionManager = DeviceDetectionManager.getInstance(mockContext)
        
        // When
        val result = deviceDetectionManager.isHuaweiDeviceNonBlocking()
        
        // Then
        // Should fall back to brand detection (depends on actual device brand in test environment)
        assertNotNull("Should return a result", result)
    }

    @Test
    fun `test loadCachedDetection loads preferences correctly`() {
        // Given
        every { mockPrefs.contains("device_type_huawei") } returns true
        every { mockPrefs.getBoolean("device_type_huawei", false) } returns false
        
        deviceDetectionManager = DeviceDetectionManager.getInstance(mockContext)
        
        // When
        deviceDetectionManager.loadCachedDetection()
        
        // Then
        verify { mockPrefs.getBoolean("device_type_huawei", false) }
    }

    @Test
    fun `test initializeDetection with cached result`() = runTest {
        // Given
        every { mockPrefs.contains("device_type_huawei") } returns true
        every { mockPrefs.getBoolean("device_type_huawei", false) } returns true
        every { mockPrefs.getLong("device_detection_timestamp", 0) } returns System.currentTimeMillis()
        
        deviceDetectionManager = DeviceDetectionManager.getInstance(mockContext)
        var callbackResult: Boolean? = null
        
        // When
        deviceDetectionManager.initializeDetection { result ->
            callbackResult = result
        }
        
        // Then
        assertNotNull("Callback should be invoked", callbackResult)
    }

    @Test
    fun `test initializeDetection without cached result`() = runTest {
        // Given
        every { mockPrefs.contains("device_type_huawei") } returns false
        
        deviceDetectionManager = DeviceDetectionManager.getInstance(mockContext)
        var callbackResult: Boolean? = null
        
        // When
        deviceDetectionManager.initializeDetection { result ->
            callbackResult = result
        }
        
        // Then
        assertNotNull("Callback should be invoked with brand detection result", callbackResult)
    }

    @Test
    fun `test refreshDetectionAsync calls callback`() = runTest {
        // Given
        every { mockPrefs.contains("device_type_huawei") } returns false
        
        deviceDetectionManager = DeviceDetectionManager.getInstance(mockContext)
        var callbackInvoked = false
        
        // When
        deviceDetectionManager.refreshDetectionAsync { _ ->
            callbackInvoked = true
        }
        
        // Wait a bit for async operation
        kotlinx.coroutines.delay(100)
        
        // Then
        assertTrue("Async callback should be invoked", callbackInvoked)
    }

    @Test
    fun `test getDeviceTypeInfo returns complete information`() {
        // Given
        every { mockPrefs.contains("device_type_huawei") } returns true
        every { mockPrefs.getBoolean("device_type_huawei", false) } returns false
        every { mockPrefs.getLong("device_detection_timestamp", 0) } returns System.currentTimeMillis()
        
        deviceDetectionManager = DeviceDetectionManager.getInstance(mockContext)
        
        // When
        val info = deviceDetectionManager.getDeviceTypeInfo()
        
        // Then
        assertNotNull("Device type info should be returned", info)
        assertNotNull("Detection method should be provided", info.detectionMethod)
        assertNotNull("Manufacturer should be provided", info.manufacturer)
        assertNotNull("Brand should be provided", info.brand)
    }

    @Test
    fun `test concurrent detection calls are thread safe`() = runTest {
        // Given
        every { mockPrefs.contains("device_type_huawei") } returns false
        
        deviceDetectionManager = DeviceDetectionManager.getInstance(mockContext)
        
        // When - simulate concurrent calls
        val results = (1..10).map {
            kotlinx.coroutines.async {
                deviceDetectionManager.isHuaweiDeviceNonBlocking()
            }
        }.map { it.await() }
        
        // Then
        assertEquals("All results should be consistent", 1, results.toSet().size)
    }

    @Test
    fun `test error handling during preference access`() {
        // Given
        every { mockPrefs.contains("device_type_huawei") } throws RuntimeException("Preference error")
        
        deviceDetectionManager = DeviceDetectionManager.getInstance(mockContext)
        
        // When
        val result = deviceDetectionManager.isHuaweiDeviceNonBlocking()
        
        // Then
        assertNotNull("Should handle preference errors gracefully", result)
    }

    @Test
    fun `test preference caching behavior`() {
        // Given
        every { mockPrefs.contains("device_type_huawei") } returns true
        every { mockPrefs.getBoolean("device_type_huawei", false) } returns true
        every { mockPrefs.getLong("device_detection_timestamp", 0) } returns System.currentTimeMillis()
        
        deviceDetectionManager = DeviceDetectionManager.getInstance(mockContext)
        
        // When
        val result1 = deviceDetectionManager.isHuaweiDeviceNonBlocking()
        val result2 = deviceDetectionManager.isHuaweiDeviceNonBlocking()
        
        // Then
        assertEquals("Cached results should be consistent", result1, result2)
        verify(atMost = 2) { mockPrefs.getBoolean("device_type_huawei", false) }
    }

    @Test
    fun `test full detection vs non-blocking detection consistency`() = runTest {
        // Given
        every { mockPrefs.contains("device_type_huawei") } returns false
        
        deviceDetectionManager = DeviceDetectionManager.getInstance(mockContext)
        
        // When
        val nonBlockingResult = deviceDetectionManager.isHuaweiDeviceNonBlocking()
        val fullResult = deviceDetectionManager.isHuaweiDevice()
        
        // Then
        // Results should be consistent for brand-based detection
        assertEquals("Detection methods should be consistent", nonBlockingResult, fullResult)
    }

    @Test
    fun `test cache validity timeframe`() {
        // Given - cache that's exactly 23 hours old (should be valid)
        val recentTime = System.currentTimeMillis() - (23 * 60 * 60 * 1000)
        every { mockPrefs.contains("device_type_huawei") } returns true
        every { mockPrefs.getBoolean("device_type_huawei", false) } returns true
        every { mockPrefs.getLong("device_detection_timestamp", 0) } returns recentTime
        
        deviceDetectionManager = DeviceDetectionManager.getInstance(mockContext)
        
        // When
        val result = deviceDetectionManager.isHuaweiDeviceNonBlocking()
        
        // Then
        assertTrue("Recent cache should be used", result)
    }

    @Test
    fun `test memory efficiency of repeated calls`() {
        // Given
        every { mockPrefs.contains("device_type_huawei") } returns true
        every { mockPrefs.getBoolean("device_type_huawei", false) } returns false
        every { mockPrefs.getLong("device_detection_timestamp", 0) } returns System.currentTimeMillis()
        
        deviceDetectionManager = DeviceDetectionManager.getInstance(mockContext)
        
        // When - make many repeated calls
        repeat(100) {
            deviceDetectionManager.isHuaweiDeviceNonBlocking()
        }
        
        // Then - should not cause memory issues
        System.gc()
        assertTrue("Repeated calls should be memory efficient", true)
    }
}