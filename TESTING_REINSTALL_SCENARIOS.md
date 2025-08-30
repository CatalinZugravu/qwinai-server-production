# Testing Reinstall Scenarios - Complete Guide

This guide helps you thoroughly test that users can no longer get fresh credits or lose subscriptions when they uninstall and reinstall your app.

## 🚨 CRITICAL: Test on Real Devices Only

**DO NOT test on emulators** - device fingerprinting doesn't work reliably on emulators. Use real Android devices for accurate testing.

## Pre-Testing Setup

### 1. Backend API Setup
Before testing, ensure your backend API is running and accessible:

```bash
# Test your API endpoints
curl -X GET "https://your-backend-api.com/user-state/test123456789012"
```

Should return either a 404 (new user) or existing user data.

### 2. Update Server URL
In `UserStateManager.kt`, update:
```kotlin
private const val SERVER_BASE_URL = "https://your-actual-backend-api.com/"
```

### 3. Enable Debug Logging
In debug builds, you'll see detailed logs:
```logcat
🔒 Device fingerprint: a1b2c3d4...
📊 Recorded credit consumption: CHAT = 5
🔄 UserStateManager initialized for device: a1b2c3d4...
```

## Test Scenario 1: Credit Consumption Persistence

### Step 1: Fresh Install
1. Install app on test device
2. Note the device fingerprint in logs
3. Use the app to consume credits:
   - Have a few chat conversations (consumes chat credits)
   - Generate a few images (consumes image credits)
4. Check remaining credits in app

### Step 2: Verify Server State
Check your backend logs or database:
```sql
SELECT * FROM user_states WHERE device_id = 'your-device-id';
```

Should show consumed credits for today.

### Step 3: Uninstall and Reinstall
1. **Completely uninstall** the app
2. **Clear all app data** (important!)
3. **Reinstall** the app
4. **Open the app** - should trigger restoration

### Step 4: Verify Credits Are NOT Reset
1. Check credit counts - should reflect previous consumption
2. Should NOT get fresh daily credits
3. Check logs for restoration messages:
```logcat
🚀 Starting comprehensive app restoration...
✅ Credit state restoration successful
```

### Expected Results:
- ✅ Credits should reflect previous usage
- ❌ Should NOT get fresh 15/20 daily credits
- ✅ Server should show same consumption data

## Test Scenario 2: Subscription Restoration

### Step 1: Subscribe to Premium
1. Fresh app install
2. Go to subscription screen
3. Subscribe (use real subscription or mock in debug)
4. Verify premium status is active

### Step 2: Verify Subscription Status
1. Check app shows premium features
2. Verify subscription end date
3. Check server logs for subscription sync

### Step 3: Uninstall and Reinstall
1. **Completely uninstall** the app
2. **Reinstall** the app
3. **Open the app** - wait for restoration

### Step 4: Verify Subscription Restored
1. Premium status should be restored
2. Check logs for subscription restoration:
```logcat
🔄 Performing enhanced subscription restoration
✅ Subscription restored from GooglePlayBillingProvider
🎉 Restored subscription type: qwinai_monthly_subscription
```

### Expected Results:
- ✅ Subscription should be automatically restored
- ✅ Premium features should be available
- ✅ Subscription end date should be preserved

## Test Scenario 3: Mixed Credit and Subscription Test

### Step 1: Complex Usage Pattern
1. Fresh install
2. Consume some credits (e.g., 10 chat, 5 image)
3. Subscribe to premium
4. Consume more credits (now with premium)
5. Note exact state

### Step 2: Uninstall/Reinstall Test
1. Uninstall completely
2. Reinstall
3. Verify both credits AND subscription are restored

### Expected Results:
- ✅ Credit consumption should be preserved
- ✅ Subscription should be restored
- ✅ User should have premium + consumed credits

## Test Scenario 4: Daily Reset Behavior

### Step 1: Test Daily Reset
1. Consume credits on Day 1
2. Wait for midnight (or change device date)
3. Open app on Day 2

### Step 2: Verify Reset Logic
1. Credits should reset to daily base amounts
2. Subscription should remain active
3. Server should reset consumed counts for new day

### Step 3: Test Reinstall After Reset
1. Uninstall/reinstall after daily reset
2. Should get fresh daily credits (correct behavior)
3. Should maintain subscription status

## Debug Tools and Commands

### 1. View Device Fingerprint
In debug builds, use the debug menu in subscription activity:
- Tap "Show Debug Info"
- Note the device fingerprint
- Verify it's consistent across reinstalls

### 2. ADB Commands for Testing
```bash
# View app logs
adb logcat | grep -E "(DeviceFingerprinter|UserStateManager|StartupRestoration)"

# View specific components
adb logcat | grep "🔒\|📊\|🚀\|✅"

# Clear app data (more thorough than uninstall)
adb shell pm clear com.cyberflux.qwinai

# Force stop app
adb shell am force-stop com.cyberflux.qwinai
```

### 3. Debug Database Queries
```sql
-- Check user state
SELECT device_id, credits_consumed_today_chat, credits_consumed_today_image, 
       has_active_subscription, subscription_type, current_date
FROM user_states 
WHERE device_id = 'your-device-id';

-- Check recent activity
SELECT device_id, last_active, app_version, created_at, updated_at
FROM user_states 
ORDER BY updated_at DESC 
LIMIT 10;

-- Daily reset check
SELECT device_id, current_date, credits_consumed_today_chat + credits_consumed_today_image as total_consumed
FROM user_states 
WHERE current_date = CURDATE();
```

## Common Issues and Troubleshooting

### Issue 1: Credits Still Reset After Reinstall
**Possible Causes:**
- Backend API not reachable
- Device fingerprint changed
- Network issues during startup

**Debug Steps:**
1. Check network connectivity
2. Verify API endpoints work with curl
3. Check device fingerprint consistency
4. Look for error logs in UserStateManager

### Issue 2: Subscription Not Restored
**Possible Causes:**
- Google Play/Huawei billing not working
- Mock billing enabled in production
- Billing provider connection issues

**Debug Steps:**
1. Check billing provider logs
2. Verify subscription is actually active in Play Console/AppGallery
3. Check if app is using mock billing accidentally

### Issue 3: Device Fingerprint Changes
**Possible Causes:**
- Device was factory reset
- Android ID changed (rare)
- Hardware changes

**Debug Steps:**
1. Compare fingerprints before/after
2. Check device info in debug menu
3. Verify fingerprint generation components

### Issue 4: Server API Errors
**Possible Causes:**
- API server down
- Authentication issues
- Database connection problems

**Debug Steps:**
1. Test API endpoints manually
2. Check server logs
3. Verify database connectivity

## Performance Testing

### Test Network Conditions
1. **Good Network:** Normal WiFi/4G
2. **Slow Network:** Throttle connection
3. **No Network:** Airplane mode during startup
4. **Intermittent:** Turn WiFi on/off during startup

### Expected Behavior:
- App should work offline (using cached state)
- Should sync when network becomes available
- Should not block UI during restoration

## Security Testing

### Test Tampering Attempts
1. Try modifying local SharedPreferences
2. Try clearing app cache (not data)
3. Try changing device time
4. Try using VPN to change location

### Expected Results:
- Local tampering should not affect server-side state
- Device fingerprint should remain stable
- Server should validate data consistency

## Production Testing Checklist

Before releasing to users:

- [ ] Test on multiple device types (Samsung, Pixel, Huawei, etc.)
- [ ] Test with real subscriptions (not just mock)
- [ ] Test daily reset behavior over actual midnight
- [ ] Test with poor network conditions
- [ ] Test server load with multiple devices
- [ ] Verify API rate limiting works
- [ ] Test backup/recovery procedures
- [ ] Monitor server logs during testing
- [ ] Test with different app versions
- [ ] Verify ProGuard doesn't break fingerprinting

## Automated Testing Script

Create a test script to automate some checks:

```bash
#!/bin/bash
# reinstall-test.sh

PACKAGE="com.cyberflux.qwinai"
DEVICE_ID="" # Will be extracted from logs

echo "🧪 Starting reinstall testing..."

# Install and get device ID
adb install app-debug.apk
adb shell am start -n $PACKAGE/.StartActivity
sleep 5
DEVICE_ID=$(adb logcat -d | grep "Device fingerprint:" | tail -1 | sed 's/.*: \(.*\)\.\.\..*/\1/')
echo "📱 Device ID: $DEVICE_ID"

# Use app to consume credits
echo "🔄 Consuming credits..."
# Add your automation steps here

# Check server state
echo "🔍 Checking server state..."
curl -s "https://your-api.com/user-state/$DEVICE_ID" | jq '.'

# Uninstall and reinstall
echo "🗑️ Uninstalling..."
adb uninstall $PACKAGE
echo "📥 Reinstalling..."
adb install app-debug.apk

# Start and check restoration
adb shell am start -n $PACKAGE/.StartActivity
sleep 10
echo "✅ Checking restoration logs..."
adb logcat -d | grep -E "restoration|restored"

echo "🧪 Test completed. Check logs and server state."
```

## Success Criteria

The implementation is working correctly when:

1. ✅ Users cannot get fresh credits after reinstall
2. ✅ Subscriptions are automatically restored
3. ✅ Daily credit reset still works normally  
4. ✅ App works offline with cached state
5. ✅ Server accurately tracks all consumption
6. ✅ Device fingerprint is stable across reinstalls
7. ✅ No significant impact on app startup performance
8. ✅ System is secure against local tampering

Remember: Test thoroughly before releasing to production. This system prevents a major revenue leak!