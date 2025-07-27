package com.cyberflux.qwinai.streaming

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.cyberflux.qwinai.adapter.ChatAdapter
import com.cyberflux.qwinai.model.ChatMessage
import com.cyberflux.qwinai.network.StreamingHandler
import com.cyberflux.qwinai.utils.StreamingConfig
import com.cyberflux.qwinai.utils.StreamingStateManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for streaming continuation functionality
 * Tests the production-ready streaming features
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StreamingContinuationTest {
    
    private lateinit var context: Context
    private lateinit var mockAdapter: ChatAdapter
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Mock SharedPreferences
        mockPrefs = mockk()
        mockEditor = mockk()
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
        every { mockEditor.putInt(any(), any()) } returns mockEditor
        every { mockEditor.putLong(any(), any()) } returns mockEditor
        every { mockEditor.apply() } returns Unit
        every { mockEditor.commit() } returns true
        
        // Mock context.getSharedPreferences
        mockkStatic("android.content.Context")
        every { context.getSharedPreferences(any(), any()) } returns mockPrefs
        
        // Setup default preferences values
        every { mockPrefs.getBoolean(any(), any()) } answers { secondArg() }
        every { mockPrefs.getString(any(), any()) } answers { secondArg() }
        every { mockPrefs.getInt(any(), any()) } answers { secondArg() }
        every { mockPrefs.getLong(any(), any()) } answers { secondArg() }
        every { mockPrefs.contains(any()) } returns false
        
        // Mock ChatAdapter
        mockAdapter = mockk()
        every { mockAdapter.currentList } returns listOf(
            ChatMessage(
                id = "test-message-1",
                message = "Test message",
                isUser = false,
                conversationId = "test-conversation",
                timestamp = System.currentTimeMillis(),
                isGenerating = true
            )
        )
        every { mockAdapter.updateStreamingContentDirect(any(), any(), any(), any()) } returns Unit
        every { mockAdapter.updateMessageDirectly(any(), any()) } returns Unit
        every { mockAdapter.startStreamingMode() } returns Unit
        every { mockAdapter.stopStreamingModeGradually() } returns Unit
        
        // Initialize managers
        StreamingConfig.initialize(context)
        StreamingStateManager.initialize(context)
    }
    
    @After
    fun tearDown() {
        unmockkAll()
    }
    
    @Test
    fun `test StreamingConfig initialization`() {
        assertTrue(StreamingConfig.isStreamingContinuationEnabled)
        assertTrue(StreamingConfig.isBackgroundStreamingEnabled)
        assertEquals(StreamingConfig.PerformanceMode.BALANCED, StreamingConfig.currentPerformanceMode)
    }
    
    @Test
    fun `test StreamingConfig feature flags`() {
        // Test enabling/disabling features
        StreamingConfig.updateFeatureEnabled(false)
        assertFalse(StreamingConfig.isStreamingContinuationEnabled)
        
        StreamingConfig.updateFeatureEnabled(true)
        assertTrue(StreamingConfig.isStreamingContinuationEnabled)
    }
    
    @Test
    fun `test StreamingConfig performance modes`() {
        StreamingConfig.updatePerformanceMode(StreamingConfig.PerformanceMode.HIGH_PERFORMANCE)
        assertEquals(StreamingConfig.PerformanceMode.HIGH_PERFORMANCE, StreamingConfig.currentPerformanceMode)
        assertEquals(4L, StreamingConfig.getPerformanceAdjustedUpdateInterval())
        
        StreamingConfig.updatePerformanceMode(StreamingConfig.PerformanceMode.BATTERY_SAVER)
        assertEquals(StreamingConfig.PerformanceMode.BATTERY_SAVER, StreamingConfig.currentPerformanceMode)
        assertEquals(33L, StreamingConfig.getPerformanceAdjustedUpdateInterval())
    }
    
    @Test
    fun `test StreamingConfig circuit breaker`() {
        assertFalse(StreamingConfig.isCircuitBreakerOpen())
        
        // Trigger multiple failures
        repeat(6) { StreamingConfig.reportStreamingFailure() }
        
        assertTrue(StreamingConfig.isCircuitBreakerOpen())
    }
    
    @Test
    fun `test StreamingStateManager session creation`() {
        val sessionId = "test-session"
        val messageId = "test-message"
        val conversationId = "test-conversation"
        val modelId = "test-model"
        
        val session = StreamingStateManager.startStreamingSession(
            messageId = messageId,
            conversationId = conversationId,
            modelId = modelId,
            sessionId = sessionId
        )
        
        assertEquals(messageId, session.messageId)
        assertEquals(conversationId, session.conversationId)
        assertEquals(modelId, session.modelId)
        assertTrue(session.isActive)
        assertFalse(session.isExpired())
    }
    
    @Test
    fun `test StreamingStateManager conversation queries`() {
        val conversationId = "test-conversation"
        val messageId1 = "message-1"
        val messageId2 = "message-2"
        
        // Create sessions for the same conversation
        StreamingStateManager.startStreamingSession(messageId1, conversationId, "model1")
        StreamingStateManager.startStreamingSession(messageId2, conversationId, "model2")
        
        assertTrue(StreamingStateManager.hasActiveStreamingInConversation(conversationId))
        
        val activeSessions = StreamingStateManager.getActiveSessionsForConversation(conversationId)
        assertEquals(2, activeSessions.size)
        
        val latestSession = StreamingStateManager.getLatestActiveMessageForConversation(conversationId)
        assertTrue(latestSession != null)
        assertTrue(activeSessions.contains(latestSession))
    }
    
    @Test
    fun `test StreamingStateManager content updates`() {
        val messageId = "test-message"
        val conversationId = "test-conversation"
        val initialContent = "Initial content"
        val updatedContent = "Updated content with more text"
        
        val session = StreamingStateManager.startStreamingSession(messageId, conversationId, "model")
        
        StreamingStateManager.updateStreamingContent(messageId, initialContent)
        assertEquals(initialContent, session.getPartialContent())
        
        StreamingStateManager.updateStreamingContent(messageId, updatedContent)
        assertEquals(updatedContent, session.getPartialContent())
    }
    
    @Test
    fun `test StreamingStateManager session completion`() {
        val messageId = "test-message"
        val conversationId = "test-conversation"
        val finalContent = "Final completed content"
        
        StreamingStateManager.startStreamingSession(messageId, conversationId, "model")
        assertTrue(StreamingStateManager.canContinueStreaming(messageId))
        
        StreamingStateManager.completeStreamingSession(messageId, finalContent)
        assertFalse(StreamingStateManager.canContinueStreaming(messageId))
    }
    
    @Test
    fun `test StreamingStateManager memory limits`() {
        val messageId = "test-message"
        val conversationId = "test-conversation"
        
        StreamingStateManager.startStreamingSession(messageId, conversationId, "model")
        
        // Try to add content that exceeds the limit
        val largeContent = "x".repeat(100_000) // Larger than the 50k limit
        StreamingStateManager.updateStreamingContent(messageId, largeContent)
        
        val session = StreamingStateManager.getStreamingSession(messageId)
        assertTrue(session != null)
        // Content should be trimmed
        assertTrue(session.getPartialContent().length < largeContent.length)
    }
    
    @Test
    fun `test ChatAdapter streaming methods`() {
        val messageId = "test-message"
        val content = "Test streaming content"
        
        // Test direct content update
        mockAdapter.updateStreamingContentDirect(messageId, content, true, true)
        verify { mockAdapter.updateStreamingContentDirect(messageId, content, true, true) }
        
        // Test streaming mode control
        mockAdapter.startStreamingMode()
        verify { mockAdapter.startStreamingMode() }
        
        mockAdapter.stopStreamingModeGradually()
        verify { mockAdapter.stopStreamingModeGradually() }
    }
    
    @Test
    fun `test feature flag integration`() = runTest {
        val conversationId = "test-conversation"
        
        // Test with feature enabled
        StreamingConfig.updateFeatureEnabled(true)
        
        every { mockAdapter.currentList } returns listOf(
            ChatMessage(
                id = "test-message",
                message = "",
                isUser = false,
                conversationId = conversationId,
                timestamp = System.currentTimeMillis(),
                isGenerating = true
            )
        )
        
        val handled = StreamingHandler.initializeConversationWithStreamingCheck(
            conversationId = conversationId,
            adapter = mockAdapter,
            checkActiveStreaming = true,
            onComplete = { },
            onError = { }
        )
        
        // Should return false (no active streaming) but not because feature is disabled
        assertFalse(handled)
        
        // Test with feature disabled
        StreamingConfig.updateFeatureEnabled(false)
        
        val handledDisabled = StreamingHandler.initializeConversationWithStreamingCheck(
            conversationId = conversationId,
            adapter = mockAdapter,
            checkActiveStreaming = true,
            onComplete = { },
            onError = { }
        )
        
        // Should return false because feature is disabled
        assertFalse(handledDisabled)
    }
}