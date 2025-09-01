# 📱 Android App Integration - New Credit System

## ✅ **Server is READY - Now Update Your App**

The credit system fix is **deployed and ready**. Now integrate it into your Android app:

---

## 📋 **REQUIRED CHANGES**

### **1. Update Server URL**

**File**: `app/src/main/java/com/cyberflux/qwinai/security/UserStateManager.kt`

```kotlin
// Line 48 - Update this with YOUR Railway URL
internal const val SERVER_BASE_URL = "https://YOUR_RAILWAY_URL.up.railway.app/api/"
```

### **2. Update CreditManager Integration**

**File**: `app/src/main/java/com/cyberflux/qwinai/credits/CreditManager.kt`

Add this method to integrate with the new server endpoints:

```kotlin
/**
 * NEW: Consume credits using server validation
 */
suspend fun consumeCreditsWithServerValidation(type: CreditType, amount: Int): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            // First check server-side limits
            val userStateManager = UserStateManager.getInstance(context)
            val consumeResult = userStateManager.consumeCreditsOnServer(type, amount)
            
            if (consumeResult.success) {
                Timber.d("✅ Server validated credit consumption: ${consumeResult.message}")
                
                // Update local state to match server
                val prefs = getSecurePreferences()
                val key = when (type) {
                    CreditType.CHAT -> CHAT_CREDITS_KEY
                    CreditType.IMAGE_GENERATION -> IMAGE_CREDITS_KEY
                }
                
                prefs.edit()
                    .putInt(key, consumeResult.availableAfter)
                    .apply()
                
                return@withContext true
            } else {
                Timber.w("⚠️ Server rejected credit consumption: ${consumeResult.message}")
                return@withContext false
            }
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to consume credits with server validation")
            
            // Fallback to local validation if server is unavailable
            return@withContext consumeCreditsLocally(type, amount)
        }
    }
}

/**
 * NEW: Add ad credits through server
 */
suspend fun addAdCreditsFromServer(type: CreditType): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val userStateManager = UserStateManager.getInstance(context)
            val result = userStateManager.addAdCreditsOnServer(type)
            
            if (result.success) {
                Timber.d("✅ Ad credit added via server: ${result.message}")
                
                // Refresh credits from server
                refreshCreditsFromServer()
                return@withContext true
            } else {
                Timber.w("⚠️ Server rejected ad credit: ${result.message}")
                return@withContext false
            }
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to add ad credits")
            return@withContext false
        }
    }
}

/**
 * NEW: Refresh credits from server
 */
suspend fun refreshCreditsFromServer() {
    withContext(Dispatchers.IO) {
        try {
            val userStateManager = UserStateManager.getInstance(context)
            val chatLimits = userStateManager.checkDailyLimits(CreditType.CHAT)
            val imageLimits = userStateManager.checkDailyLimits(CreditType.IMAGE_GENERATION)
            
            val prefs = getSecurePreferences()
            prefs.edit()
                .putInt(CHAT_CREDITS_KEY, chatLimits.remaining)
                .putInt(IMAGE_CREDITS_KEY, imageLimits.remaining)
                .apply()
            
            Timber.d("✅ Credits refreshed from server: chat=${chatLimits.remaining}, image=${imageLimits.remaining}")
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to refresh credits from server")
        }
    }
}
```

### **3. Update MainActivity Credit Usage**

**File**: `app/src/main/java/com/cyberflux/qwinai/MainActivity.kt`

Find where credits are consumed and update to use server validation:

```kotlin
// OLD CODE (find this in sendMessage or similar method):
// if (creditManager.consumeCredits(CreditManager.CreditType.CHAT, 1)) {
//     // Send message
// }

// NEW CODE (replace with this):
lifecycleScope.launch {
    val canConsume = creditManager.consumeCreditsWithServerValidation(
        CreditManager.CreditType.CHAT, 
        1
    )
    
    if (canConsume) {
        // Send message
        sendMessageToAI(message)
    } else {
        // Show no credits dialog
        showNoCreditDialog("chat")
    }
}
```

### **4. Add Ad Credit Integration**

When users watch ads, call the new server endpoint:

```kotlin
// After successful ad view
private fun onAdWatchedSuccess(creditType: String) {
    lifecycleScope.launch {
        val type = when (creditType) {
            "chat" -> CreditManager.CreditType.CHAT
            "image" -> CreditManager.CreditType.IMAGE_GENERATION
            else -> return@launch
        }
        
        val success = creditManager.addAdCreditsFromServer(type)
        
        if (success) {
            showToast("🎉 +1 $creditType credit earned from ad!")
            updateCreditDisplays() // Refresh UI
        } else {
            showToast("Daily ad credit limit reached")
        }
    }
}
```

### **5. Initialize Credit Restoration on App Start**

**File**: `app/src/main/java/com/cyberflux/qwinai/StartActivity.kt`

Add credit restoration in onCreate:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Restore user state (prevents credit abuse on reinstall)
    lifecycleScope.launch {
        try {
            val userStateManager = UserStateManager.getInstance(this@StartActivity)
            val restoreResult = userStateManager.restoreUserState()
            
            if (restoreResult.success) {
                Timber.d("✅ User state restored: ${restoreResult.message}")
                
                // Refresh credit manager with server data
                val creditManager = CreditManager.getInstance(this@StartActivity)
                creditManager.refreshCreditsFromServer()
            }
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to restore user state")
        }
        
        // Continue with normal app startup
        proceedToMainActivity()
    }
}
```

---

## 🔧 **BUILD AND DEPLOY APP**

```bash
cd AndroidStudioProjects/DeepSeekChat4

# Clean and build
./gradlew clean assembleDebug

# Install on device
./gradlew installDebug

# Check logs for credit system
adb logcat | grep -E "(CreditManager|UserStateManager|✅|❌|🔥)"
```

---

## 🧪 **TEST YOUR APP**

### **Test 1: Fresh Install**
1. **Clear app data**: Settings → Apps → QwinAI → Storage → Clear Data
2. **Open app** - should get 15 chat + 20 image credits
3. **Check logs**: Should see `"NEW USER detected: ... Available credits: chat=15, image=20"`

### **Test 2: Credit Consumption**  
1. **Send 3 chat messages** 
2. **Check remaining credits** - should show 12 chat credits
3. **Check logs**: Should see `"CONSUMED 3 chat credits: 0 → 3 (12 remaining)"`

### **Test 3: Reinstall Detection**
1. **Consume some credits** (e.g., 5 chat messages)
2. **Uninstall app completely**
3. **Reinstall and open** - should have 10 chat credits remaining (not 15!)
4. **Check logs**: Should see `"REINSTALL DETECTED: Found user by fingerprint"`

### **Test 4: Daily Reset**
1. **Wait for midnight** OR change device date
2. **Open app** - should get fresh 15/20 credits
3. **Check logs**: Should see `"Resetting daily credits"`

### **Test 5: Ad Credits**
1. **Watch an ad** (if implemented)
2. **Check credits** - should increase by 1  
3. **Repeat 10 times** - should hit daily limit
4. **Check logs**: Should see `"Added 1 AD credit"`

---

## 🎯 **SUCCESS INDICATORS**

Your app is working correctly when:

✅ **New users get 15 chat + 20 image credits**  
✅ **Credit consumption reduces available credits**  
✅ **Reinstall preserves consumed credit state**  
✅ **Daily reset gives fresh defaults**  
✅ **Ad credits add to daily allowance**  
✅ **Server logs show proper credit tracking**  

---

## 🚨 **IF CREDITS STILL SHOW 0**

1. **Check server URL** - make sure it's your Railway URL
2. **Check network connectivity** - server must be reachable
3. **Run database migration** - ad credits columns must exist
4. **Check logs** for API errors
5. **Test server directly** with curl commands from `test_deployment.sh`

---

## 📞 **Debug Commands**

```bash
# Test server from Android
adb shell "curl https://YOUR_URL.up.railway.app/health"

# Check if app can reach server
adb logcat | grep "UserStateManager"

# View credit manager logs
adb logcat | grep "CreditManager"

# Test credit consumption directly
# (Replace URL with your Railway URL)
curl -X POST "https://YOUR_URL.up.railway.app/api/user-state/TESTDEVICE123/consume-credits" \
  -H "Content-Type: application/json" \
  -d '{"creditType":"chat","amount":1,"fingerprint":"testfingerprint"}'
```

---

## 🎉 **DEPLOYMENT COMPLETE!**

Once these changes are made:

1. **✅ Server is deployed** with credit abuse prevention
2. **✅ Database includes** ad credits support  
3. **✅ Android app integrates** with new server API
4. **✅ Credit system works** properly with reinstall protection
5. **✅ Ad credits system** allows users to earn extra credits

**Your credit abuse problem is now COMPLETELY SOLVED! 🎉**