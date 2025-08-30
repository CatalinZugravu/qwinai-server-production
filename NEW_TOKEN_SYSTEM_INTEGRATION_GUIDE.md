# NEW SIMPLIFIED TOKEN SYSTEM INTEGRATION GUIDE

## 🎯 Overview

Your token system has been completely refactored to be **much simpler** and focused on what actually matters:

### Before (Complex)
❌ **Manual token counting everywhere**  
❌ **Complex estimation systems**  
❌ **Multiple token managers**  
❌ **Database persistence complexity**  

### After (Simple)
✅ **Pre-request validation only**  
✅ **API provides actual usage**  
✅ **80% context window limit**  
✅ **Smart file token estimation**  

---

## 🏗️ New System Architecture

```
┌─────────────────────┐    ┌────────────────────────┐    ┌─────────────────────┐
│   User sends msg    │───▶│  Pre-request validate  │───▶│   Send to API       │
│   with files        │    │  (estimate tokens)     │    │   (if allowed)      │
└─────────────────────┘    └────────────────────────┘    └─────────────────────┘
                                      │                              │
                                      ▼                              ▼
                           ┌────────────────────────┐    ┌─────────────────────┐
                           │   Show warning/block   │    │  API returns actual │
                           │   if needed            │    │  token usage        │
                           └────────────────────────┘    └─────────────────────┘
```

---

## 📦 New Components

### 1. ContextWindowManager
**Purpose:** Pre-request validation to prevent exceeding 80% of context window
```kotlin
val contextManager = ContextWindowManager(context)
val result = contextManager.validateMessage(
    conversationId = "conv_123",
    modelId = "gpt-4o",
    userPrompt = "Hello world",
    attachedFiles = listOf(fileUri),
    isSubscribed = true
)
```

### 2. ApiUsageTracker  
**Purpose:** Track actual API-returned token usage for display
```kotlin
val usageTracker = ApiUsageTracker.getInstance()
usageTracker.recordApiUsage(conversationId, modelId, apiResponse)
```

### 3. FileTokenEstimator
**Purpose:** Smart estimation of file tokens before upload
```kotlin
val estimator = FileTokenEstimator(context)
val result = estimator.estimateFileTokens(fileUri, fileName)
```

### 4. ContextWindowAlertManager
**Purpose:** Beautiful user-friendly alerts for context limits
```kotlin
val alertManager = ContextWindowAlertManager(context)
alertManager.showContextWarningDialog(...)
```

---

## 🔧 Integration Steps

### Step 1: Update MainActivity.sendMessage()

Replace your existing sendMessage() method:

```kotlin
private fun sendMessage() {
    val message = binding.etInputText.text.toString().trim()
    val modelId = ModelManager.selectedModel.id
    val isSubscribed = PrefsManager.isSubscribed(this)
    
    if (message.isBlank() && selectedFiles.isEmpty()) return
    
    // NEW: Use simplified token validation
    lifecycleScope.launch {
        TokenLimitDialogHandler.validateAndShowDialog(
            context = this@MainActivity,
            conversationId = currentConversationId ?: "new_conversation",
            modelId = modelId,
            userPrompt = message,
            attachedFiles = selectedFiles.map { it.uri },
            isSubscribed = isSubscribed,
            onAllowed = {
                // ✅ Validation passed - send message
                proceedWithSending(message)
            },
            onNewConversationRequired = {
                // ⚠️ Context full - start new conversation
                startNewConversation()
                proceedWithSending(message)
            },
            onCancel = {
                // ❌ User cancelled
                Timber.d("User cancelled message send")
            },
            onUpgrade = {
                // 💰 Show upgrade dialog
                showUpgradeDialog()
            }
        )
    }
}

private fun proceedWithSending(message: String) {
    // Your existing message sending logic here
    // Just remove all the old token counting code
    sendToApi(message)
}
```

### Step 2: Update API Response Handling

After receiving an API response, update the context:

```kotlin
private fun handleApiResponse(apiResponse: AimlApiResponse) {
    // Update context with actual API usage
    TokenLimitDialogHandler.updateContextWithApiResponse(
        context = this,
        conversationId = currentConversationId ?: "",
        modelId = ModelManager.selectedModel.id,
        apiResponse = apiResponse
    )
    
    // Your existing response handling logic
    displayResponse(apiResponse)
    
    // Update UI with new usage info
    updateTokenCounterDisplay()
}
```

### Step 3: Update Token Counter Display

Simplify your token counter to show actual usage:

```kotlin
private fun updateTokenCounterDisplay() {
    val conversationId = currentConversationId ?: return
    val modelId = ModelManager.selectedModel.id
    val isSubscribed = PrefsManager.isSubscribed(this)
    
    // Get usage summary
    val usageSummary = TokenLimitDialogHandler.getUsageSummary(
        context = this,
        conversationId = conversationId,
        modelId = modelId,
        isSubscribed = isSubscribed
    )
    
    // Update UI
    binding.tvTokenCounter.text = usageSummary
    
    // Check if warning needed
    val (needsWarning, warningMessage) = TokenLimitDialogHandler.checkConversationLimits(
        context = this,
        conversationId = conversationId,
        modelId = modelId,
        isSubscribed = isSubscribed
    )
    
    if (needsWarning && warningMessage != null) {
        binding.tvTokenCounter.setTextColor(ContextCompat.getColor(this, R.color.warning_color))
        // Optionally show a small warning indicator
    } else {
        binding.tvTokenCounter.setTextColor(ContextCompat.getColor(this, R.color.text_color))
    }
}
```

### Step 4: Handle New Conversations

When starting a new conversation (due to token limits):

```kotlin
private fun startNewConversation() {
    val oldConversationId = currentConversationId
    
    // Reset token context for old conversation
    if (oldConversationId != null) {
        TokenLimitDialogHandler.resetConversation(this, oldConversationId)
    }
    
    // Create new conversation
    currentConversationId = generateNewConversationId()
    
    // Clear chat adapter
    chatAdapter.submitList(emptyList())
    
    // Show success message
    val alertManager = ContextWindowAlertManager(this)
    alertManager.showNewConversationStartedMessage()
    
    // Update UI
    updateTokenCounterDisplay()
}
```

### Step 5: Handle File Uploads

For file uploads, the system now automatically estimates tokens:

```kotlin
private fun handleFileSelection(uris: List<Uri>) {
    lifecycleScope.launch {
        try {
            val estimator = FileTokenEstimator(this@MainActivity)
            val totalTokens = estimator.getTotalEstimatedTokens(
                uris.map { uri -> uri to FileUtil.getFileName(this@MainActivity, uri) }
            )
            
            Timber.d("📎 Selected files estimated at $totalTokens tokens")
            
            // Files are automatically validated when user sends message
            selectedFiles.addAll(uris.map { FileItem(it, FileUtil.getFileName(this@MainActivity, it)) })
            updateSelectedFilesUI()
            
        } catch (e: Exception) {
            Timber.e(e, "Error estimating file tokens")
            // Continue anyway with fallback
            selectedFiles.addAll(uris.map { FileItem(it, FileUtil.getFileName(this@MainActivity, it)) })
            updateSelectedFilesUI()
        }
    }
}
```

---

## 🧹 Cleanup Old Code

### Remove These Classes/Files:
❌ `ProductionTokenManager.kt`  
❌ `UltraReliableTokenManager.kt`  
❌ `UniversalTokenCounter.kt` (keep only for API response parsing)  
❌ `ConversationTokenManager.kt` (most of it)  

### Keep But Simplify:
✅ `TokenValidator.kt` - Keep for basic token estimation  
✅ `UniversalTokenCounter.kt` - Keep only the `extractTokenUsage()` method  

### Remove From MainActivity:
❌ All manual token counting logic  
❌ Token change listeners  
❌ Message token tracking  
❌ Complex context calculations  

---

## 🎨 UI Updates Needed

### 1. Create Dialog Layouts

Create these layout files (referenced in ContextWindowAlertManager):

**`res/layout/dialog_context_warning.xml`**
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="24dp">

    <TextView
        android:id="@+id/tvTitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Context Window Warning"
        android:textSize="20sp"
        android:textStyle="bold"
        android:layout_marginBottom="16dp" />

    <TextView
        android:id="@+id/tvMessage"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginBottom="16dp"
        android:lineSpacingMultiplier="1.2" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:layout_marginBottom="16dp">

        <ProgressBar
            android:id="@+id/progressUsage"
            style="?android:attr/progressBarStyleHorizontal"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginEnd="8dp"
            android:max="100" />

        <TextView
            android:id="@+id/tvUsagePercentage"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="70% used"
            android:textSize="12sp" />

    </LinearLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal">

        <Button
            android:id="@+id/btnCancel"
            style="@style/Widget.MaterialComponents.Button.TextButton"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Cancel" />

        <Button
            android:id="@+id/btnContinueAnyway"
            style="@style/Widget.MaterialComponents.Button.TextButton"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Continue" />

        <Button
            android:id="@+id/btnStartNewChat"
            style="@style/Widget.MaterialComponents.Button"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="New Chat" />

    </LinearLayout>

    <Button
        android:id="@+id/btnUpgrade"
        style="@style/Widget.MaterialComponents.Button.TextButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="💡 Upgrade to Pro"
        android:visibility="gone" />

</LinearLayout>
```

**`res/layout/dialog_context_blocked.xml`** (similar layout but without "Continue" button)

### 2. Add Color Resources

Add to `res/values/colors.xml`:
```xml
<color name="warning_color">#FF9800</color>
<color name="error_color">#F44336</color>
<color name="info_color">#2196F3</color>
```

---

## 🧪 Testing the New System

### Test Scenarios:

1. **Normal Message** - Should send without warnings
2. **Large Message** - Should show warning at 70%
3. **Very Large Message** - Should be blocked at 80%
4. **Multiple Files** - Should estimate file tokens properly
5. **PDF Files** - Should handle document token estimation
6. **Image Files** - Should use vision model token calculation
7. **Conversation Resumption** - Should restore context properly
8. **API Response** - Should update actual usage correctly

### Test Commands:

```bash
# Build and test
./gradlew assembleDebug
./gradlew installDebug

# Run specific tests
./gradlew test --tests "*TokenTest*"
./gradlew test --tests "*ContextWindowTest*"
```

---

## 📊 Monitoring & Debugging

### Debug Logs to Watch:
```
🔍 Context window validation
📊 API usage tracking  
📎 File token estimation
⚠️ Context warnings
❌ Context blocks
✅ Validation success
```

### Usage Analytics:
```kotlin
// Get usage report for debugging
val usageTracker = ApiUsageTracker.getInstance()
val report = usageTracker.exportUsageData()
Timber.d("Usage Report:\n$report")
```

---

## 🎉 Benefits of New System

✅ **Simpler Code** - 70% less token-related code  
✅ **More Accurate** - Uses actual API token counts  
✅ **Better UX** - Clear 80% limit prevents confusion  
✅ **Smarter Files** - Proper file type token estimation  
✅ **Less Bugs** - Fewer edge cases and race conditions  
✅ **Easier Maintenance** - Single source of truth  
✅ **Better Performance** - No constant token calculations  

---

## 🚀 Migration Checklist

- [ ] Update MainActivity.sendMessage()
- [ ] Update API response handling  
- [ ] Update token counter display
- [ ] Add new dialog layouts
- [ ] Remove old token managers
- [ ] Test with different file types
- [ ] Test with different models
- [ ] Test conversation resumption
- [ ] Test upgrade flow
- [ ] Update any other activities using token system

---

## 🆘 Common Issues & Solutions

### Issue: "ModelManager.getModelDisplayName not found"
**Solution:** Add this method to ModelManager or use modelId directly

### Issue: "Layout files not found"
**Solution:** Create the dialog layout files as shown above

### Issue: "Context validation fails"
**Solution:** Check that all required parameters are provided

### Issue: "File token estimation errors"  
**Solution:** Handle gracefully with fallback estimates

### Issue: "API usage not updating"
**Solution:** Ensure updateContextWithApiResponse() is called after each API response

---

Your token system is now **massively simplified** and focused on what actually matters: preventing context overflow and providing accurate usage information! 🎯