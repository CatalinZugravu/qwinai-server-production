# Backend API Specification for User State Management

This document describes the backend API endpoints you need to implement to prevent users from getting fresh credits and losing subscriptions when they uninstall/reinstall your app.

## Overview

The app now implements a robust system that tracks user credits and subscription status server-side using device fingerprinting. This prevents users from:
- Getting fresh credits after reinstalling the app
- Losing their subscription status after reinstall
- Manipulating local data to get unlimited credits

## Required API Endpoints

### Base URL Configuration

In `UserStateManager.kt`, update this line with your actual backend URL:
```kotlin
private const val SERVER_BASE_URL = "https://your-backend-api.com/" // Replace with your backend URL
```

### 1. Get User State

**Endpoint:** `GET /user-state/{deviceId}`

**Description:** Retrieve current user state based on device fingerprint

**Parameters:**
- `deviceId` (path): 16-character device fingerprint

**Response:**
```json
{
  "deviceId": "a1b2c3d4e5f6g7h8",
  "deviceFingerprint": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6",
  "creditsConsumedTodayChat": 5,
  "creditsConsumedTodayImage": 3,
  "hasActiveSubscription": true,
  "subscriptionType": "qwinai_monthly_subscription",
  "subscriptionEndTime": 1735689600000,
  "lastActive": 1703289600000,
  "appVersion": "18",
  "date": "2024-12-23",
  "userId": "user_12345"
}
```

### 2. Update User State

**Endpoint:** `PUT /user-state/{deviceId}`

**Description:** Update user state when credits are consumed or subscription changes

**Parameters:**
- `deviceId` (path): 16-character device fingerprint

**Request Body:**
```json
{
  "deviceId": "a1b2c3d4e5f6g7h8",
  "deviceFingerprint": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6",
  "creditsConsumedTodayChat": 6,
  "creditsConsumedTodayImage": 3,
  "hasActiveSubscription": true,
  "subscriptionType": "qwinai_monthly_subscription",
  "subscriptionEndTime": 1735689600000,
  "lastActive": 1703289600000,
  "appVersion": "18",
  "date": "2024-12-23"
}
```

**Response:**
```json
{
  "success": true,
  "message": "User state updated successfully",
  "data": {
    "userId": "user_12345",
    "updated": true
  }
}
```

### 3. Reset User State (Optional)

**Endpoint:** `POST /user-state/{deviceId}/reset`

**Description:** Reset user state for debugging/support purposes

**Parameters:**
- `deviceId` (path): 16-character device fingerprint

**Response:**
```json
{
  "success": true,
  "message": "User state reset successfully",
  "data": {
    "resetTimestamp": 1703289600000
  }
}
```

## Database Schema Example

Here's a suggested database schema for storing user state:

### SQL Table Structure

```sql
CREATE TABLE user_states (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_id VARCHAR(16) UNIQUE NOT NULL,
    device_fingerprint VARCHAR(64) NOT NULL,
    credits_consumed_today_chat INT DEFAULT 0,
    credits_consumed_today_image INT DEFAULT 0,
    has_active_subscription BOOLEAN DEFAULT FALSE,
    subscription_type VARCHAR(50),
    subscription_end_time BIGINT DEFAULT 0,
    last_active BIGINT NOT NULL,
    app_version VARCHAR(10),
    current_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_device_id (device_id),
    INDEX idx_device_fingerprint (device_fingerprint),
    INDEX idx_current_date (current_date),
    INDEX idx_last_active (last_active)
);
```

### MongoDB Schema Example

```javascript
{
  _id: ObjectId(),
  deviceId: "a1b2c3d4e5f6g7h8", // 16-char fingerprint
  deviceFingerprint: "full-64-char-fingerprint", // Full fingerprint
  creditsConsumedTodayChat: 5,
  creditsConsumedTodayImage: 3,
  hasActiveSubscription: true,
  subscriptionType: "qwinai_monthly_subscription",
  subscriptionEndTime: 1735689600000,
  lastActive: 1703289600000,
  appVersion: "18",
  currentDate: "2024-12-23",
  createdAt: ISODate("2024-12-23T10:30:00Z"),
  updatedAt: ISODate("2024-12-23T10:35:00Z")
}

// Indexes
db.userStates.createIndex({ "deviceId": 1 }, { unique: true })
db.userStates.createIndex({ "deviceFingerprint": 1 })
db.userStates.createIndex({ "currentDate": 1 })
db.userStates.createIndex({ "lastActive": 1 })
```

## Implementation Examples

### Node.js/Express Example

```javascript
const express = require('express');
const app = express();

// Get user state
app.get('/user-state/:deviceId', async (req, res) => {
    const { deviceId } = req.params;
    
    try {
        const userState = await getUserStateFromDB(deviceId);
        
        if (!userState) {
            // New user - return default state
            return res.json({
                deviceId,
                deviceFingerprint: "",
                creditsConsumedTodayChat: 0,
                creditsConsumedTodayImage: 0,
                hasActiveSubscription: false,
                subscriptionType: null,
                subscriptionEndTime: 0,
                lastActive: Date.now(),
                appVersion: "18",
                date: new Date().toISOString().split('T')[0],
                userId: null
            });
        }
        
        // Reset daily credits if it's a new day
        const today = new Date().toISOString().split('T')[0];
        if (userState.date !== today) {
            userState.creditsConsumedTodayChat = 0;
            userState.creditsConsumedTodayImage = 0;
            userState.date = today;
            await updateUserStateInDB(deviceId, userState);
        }
        
        res.json(userState);
    } catch (error) {
        res.status(500).json({
            success: false,
            message: "Error retrieving user state: " + error.message
        });
    }
});

// Update user state
app.put('/user-state/:deviceId', async (req, res) => {
    const { deviceId } = req.params;
    const userState = req.body;
    
    try {
        // Validate data
        if (!userState.deviceFingerprint || userState.creditsConsumedTodayChat < 0) {
            return res.status(400).json({
                success: false,
                message: "Invalid user state data"
            });
        }
        
        // Security check: Prevent unrealistic credit consumption
        if (userState.creditsConsumedTodayChat > 100 || userState.creditsConsumedTodayImage > 100) {
            return res.status(400).json({
                success: false,
                message: "Unrealistic credit consumption detected"
            });
        }
        
        const result = await saveUserStateToDB(deviceId, userState);
        
        res.json({
            success: true,
            message: "User state updated successfully",
            data: {
                userId: result.userId,
                updated: true
            }
        });
    } catch (error) {
        res.status(500).json({
            success: false,
            message: "Error updating user state: " + error.message
        });
    }
});

// Reset user state (for support/debugging)
app.post('/user-state/:deviceId/reset', async (req, res) => {
    const { deviceId } = req.params;
    
    try {
        await resetUserStateInDB(deviceId);
        
        res.json({
            success: true,
            message: "User state reset successfully",
            data: {
                resetTimestamp: Date.now()
            }
        });
    } catch (error) {
        res.status(500).json({
            success: false,
            message: "Error resetting user state: " + error.message
        });
    }
});
```

### Python/FastAPI Example

```python
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from datetime import date, datetime
import asyncpg

app = FastAPI()

class UserState(BaseModel):
    deviceId: str
    deviceFingerprint: str
    creditsConsumedTodayChat: int
    creditsConsumedTodayImage: int
    hasActiveSubscription: bool
    subscriptionType: str = None
    subscriptionEndTime: int = 0
    lastActive: int
    appVersion: str
    date: str

@app.get("/user-state/{device_id}")
async def get_user_state(device_id: str):
    try:
        user_state = await get_user_state_from_db(device_id)
        
        if not user_state:
            # Return default state for new user
            return {
                "deviceId": device_id,
                "deviceFingerprint": "",
                "creditsConsumedTodayChat": 0,
                "creditsConsumedTodayImage": 0,
                "hasActiveSubscription": False,
                "subscriptionType": None,
                "subscriptionEndTime": 0,
                "lastActive": int(datetime.now().timestamp() * 1000),
                "appVersion": "18",
                "date": date.today().isoformat(),
                "userId": None
            }
        
        # Reset daily credits if new day
        today = date.today().isoformat()
        if user_state["date"] != today:
            user_state["creditsConsumedTodayChat"] = 0
            user_state["creditsConsumedTodayImage"] = 0
            user_state["date"] = today
            await update_user_state_in_db(device_id, user_state)
        
        return user_state
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error retrieving user state: {str(e)}")

@app.put("/user-state/{device_id}")
async def update_user_state(device_id: str, user_state: UserState):
    try:
        # Security validation
        if user_state.creditsConsumedTodayChat > 100 or user_state.creditsConsumedTodayImage > 100:
            raise HTTPException(status_code=400, detail="Unrealistic credit consumption detected")
        
        result = await save_user_state_to_db(device_id, user_state.dict())
        
        return {
            "success": True,
            "message": "User state updated successfully",
            "data": {
                "userId": result.get("userId"),
                "updated": True
            }
        }
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error updating user state: {str(e)}")
```

## Security Considerations

### 1. Input Validation
- Validate all incoming data
- Prevent unrealistic credit consumption values
- Check device fingerprint format

### 2. Rate Limiting
- Implement rate limiting to prevent API abuse
- Consider per-device limits

### 3. Authentication (Optional)
- Add API authentication if needed
- Consider JWT tokens for enhanced security

### 4. Data Encryption
- Use HTTPS for all communication
- Consider encrypting sensitive data at rest

## Testing Your Implementation

### 1. Test with curl:

```bash
# Get user state
curl -X GET "https://your-api.com/user-state/a1b2c3d4e5f6g7h8"

# Update user state
curl -X PUT "https://your-api.com/user-state/a1b2c3d4e5f6g7h8" \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "a1b2c3d4e5f6g7h8",
    "deviceFingerprint": "full-fingerprint-here",
    "creditsConsumedTodayChat": 5,
    "creditsConsumedTodayImage": 2,
    "hasActiveSubscription": false,
    "lastActive": 1703289600000,
    "appVersion": "18",
    "date": "2024-12-23"
  }'
```

### 2. Enable Debug Mode in App:

In debug builds, the app will show additional logging to help you troubleshoot:

```kotlin
// In your debug build, check logs for:
Timber.d("🔄 UserStateManager initialized for device: ${deviceId.take(8)}...")
Timber.d("📊 Recorded credit consumption: $type = $newConsumption")
```

## Deployment Notes

1. **Update SERVER_BASE_URL** in `UserStateManager.kt`
2. **Implement all three endpoints** (GET, PUT, POST)
3. **Set up proper database indexes** for performance
4. **Configure HTTPS** for security
5. **Test thoroughly** with actual app installs/uninstalls
6. **Monitor server logs** for any issues
7. **Consider backup/restore** procedures for user data

## Support & Troubleshooting

If users still get fresh credits after implementing this system:

1. Check server logs for API calls
2. Verify device fingerprinting is working
3. Test on actual devices (not emulators)
4. Check network connectivity during app startup
5. Use the debug menu in the app to inspect state

The system is designed to be fault-tolerant - if the server is temporarily unavailable, the app will continue to function normally but won't prevent credit abuse until the connection is restored.