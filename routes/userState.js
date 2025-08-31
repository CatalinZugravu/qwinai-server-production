const express = require('express');
const { body, param, validationResult } = require('express-validator');
const router = express.Router();

// Enhanced validation middleware
const validateDeviceId = param('deviceId')
    .isLength({ min: 16, max: 32 })
    .matches(/^[a-zA-Z0-9]+$/)
    .withMessage('Invalid device ID format');

const validateUserState = [
    body('deviceFingerprint').isLength({ min: 10, max: 500 }).withMessage('Invalid device fingerprint'),
    body('creditsConsumedTodayChat').isInt({ min: 0, max: 500 }).withMessage('Invalid chat credits'),
    body('creditsConsumedTodayImage').isInt({ min: 0, max: 200 }).withMessage('Invalid image credits'),
    body('hasActiveSubscription').isBoolean().withMessage('Invalid subscription status'),
    body('subscriptionType').optional().isString().isLength({ max: 50 }),
    body('subscriptionEndTime').optional().isInt({ min: 0 }),
    body('appVersion').isString().isLength({ min: 1, max: 10 }).withMessage('Invalid app version'),
    body('date').matches(/^\d{4}-\d{2}-\d{2}$/).withMessage('Invalid date format')
];

// Security middleware for validation errors
const handleValidationErrors = (req, res, next) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
        console.warn(`⚠️ Validation errors for ${req.ip}: ${JSON.stringify(errors.array())}`);
        return res.status(400).json({
            error: 'Validation failed',
            details: errors.array()
        });
    }
    next();
};

// Rate limiting for suspicious activity
const suspiciousActivityCheck = (req, res, next) => {
    const userState = req.body;
    const { deviceId } = req.params;
    
    // Check for unrealistic consumption patterns
    if (userState && (userState.creditsConsumedTodayChat > 300 || userState.creditsConsumedTodayImage > 150)) {
        console.log(`🚨 SECURITY ALERT: Suspicious activity from device ${deviceId} IP ${req.ip}`);
        req.monitoring?.recordSecurityEvent('SUSPICIOUS_CREDIT_CONSUMPTION', {
            deviceId,
            ip: req.ip,
            chatCredits: userState.creditsConsumedTodayChat,
            imageCredits: userState.creditsConsumedTodayImage
        });
        
        return res.status(429).json({
            error: 'Unusual activity detected. Account temporarily restricted.',
            message: 'If this is an error, please contact support.',
            retryAfter: 3600 // 1 hour
        });
    }
    next();
};

// Get user state with enhanced security
router.get('/:deviceId', validateDeviceId, handleValidationErrors, async (req, res) => {
    const startTime = Date.now();
    try {
        const { deviceId } = req.params;
        const { fingerprint } = req.query; // Get fingerprint from query params
        
        console.log(`🔍 [${req.ip}] Getting user state for device: ${deviceId}`);
        console.log(`🔍 Device fingerprint: ${fingerprint ? fingerprint.substring(0, 8) + '...' : 'not provided'}`);
        req.monitoring?.recordRequest('GET_USER_STATE', { deviceId, ip: req.ip });

        // Step 1: Check by device_id (existing behavior)
        let existingUser = await req.db.query(
            'SELECT device_id, device_fingerprint FROM user_states WHERE device_id = $1',
            [deviceId]
        );

        let userFound = false;
        let userRecord = null;

        if (existingUser.rows.length > 0) {
            // Found by device_id
            userRecord = existingUser.rows[0];
            userFound = true;
            console.log(`✅ User found by device_id: ${deviceId}`);
        } else if (fingerprint && fingerprint.length >= 8) {
            // Step 2: Check by device fingerprint (CRITICAL: prevents credit abuse on reinstall)
            console.log(`🔍 Device_id not found, checking by fingerprint: ${fingerprint.substring(0, 8)}...`);
            
            const fingerprintUser = await req.db.query(
                'SELECT device_id, device_fingerprint FROM user_states WHERE device_fingerprint = $1',
                [fingerprint]
            );

            if (fingerprintUser.rows.length > 0) {
                userRecord = fingerprintUser.rows[0];
                userFound = true;
                
                console.log(`🔒 IMPORTANT: User found by fingerprint! This is a reinstall scenario.`);
                console.log(`🔒 Previous device_id: ${userRecord.device_id} → New device_id: ${deviceId}`);
                
                // Update the device_id to the new one (user reinstalled app)
                await req.db.query(
                    'UPDATE user_states SET device_id = $1, updated_at = CURRENT_TIMESTAMP WHERE device_fingerprint = $2',
                    [deviceId, fingerprint]
                );
                
                console.log(`✅ Updated device_id from ${userRecord.device_id} to ${deviceId} for fingerprint ${fingerprint.substring(0, 8)}...`);
                req.monitoring?.recordEvent('DEVICE_ID_UPDATED_ON_REINSTALL', { 
                    oldDeviceId: userRecord.device_id, 
                    newDeviceId: deviceId, 
                    fingerprint: fingerprint.substring(0, 8) 
                });
            }
        }

        if (!userFound) {
            // Truly new user - return secure default state with CORRECT available credits
            const today = new Date().toISOString().split('T')[0];
            const defaultState = {
                deviceId,
                deviceFingerprint: fingerprint || "",
                // Return AVAILABLE credits, not consumed credits
                availableChatCredits: 15,      // Daily default for chat
                availableImageCredits: 20,     // Daily default for images  
                creditsConsumedTodayChat: 0,   // Keep for backwards compatibility
                creditsConsumedTodayImage: 0,  // Keep for backwards compatibility
                hasActiveSubscription: false,
                subscriptionType: null,
                subscriptionEndTime: 0,
                lastActive: Date.now(),
                appVersion: "18",
                date: today,
                userId: null,
                serverVersion: "2.0.0"
            };
            
            console.log(`👤 NEW USER detected: ${deviceId} - Available credits: chat=15, image=20 (fingerprint: ${fingerprint ? fingerprint.substring(0, 8) + '...' : 'none'})`);
            req.monitoring?.recordEvent('NEW_USER_DETECTED', { deviceId, hasFingerprint: !!fingerprint });
            
            return res.json(defaultState);
        }

        // Use the correct device_id for database queries (may have been updated above)
        const currentDeviceId = deviceId;
        const today = new Date().toISOString().split('T')[0];

        // Get full user state for existing user (use current device_id)
        const fullUserResult = await req.db.query(
            'SELECT * FROM user_states WHERE device_id = $1',
            [currentDeviceId]
        );
        
        const fullUserState = fullUserResult.rows[0];

        // Reset daily credits if new day
        let creditsReset = false;
        
        // Fix date comparison - convert database date to string for comparison
        const dbDate = fullUserState.tracking_date instanceof Date 
            ? fullUserState.tracking_date.toISOString().split('T')[0]
            : fullUserState.tracking_date?.toString()?.split('T')[0] || '';
        
        console.log(`🔍 Date comparison: DB date="${dbDate}", Today="${today}"`);
        
        if (dbDate !== today) {
            console.log(`🔄 Resetting daily credits for device: ${deviceId} (${dbDate} → ${today})`);
            
            await req.db.query(
                `UPDATE user_states 
                 SET credits_consumed_today_chat = 0, 
                     credits_consumed_today_image = 0, 
                     ad_credits_chat_today = 0,
                     ad_credits_image_today = 0,
                     tracking_date = $1,
                     updated_at = CURRENT_TIMESTAMP
                 WHERE device_id = $2`,
                [today, deviceId]
            );
            
            creditsReset = true;
            fullUserState.credits_consumed_today_chat = 0;
            fullUserState.credits_consumed_today_image = 0;
            fullUserState.ad_credits_chat_today = 0;
            fullUserState.ad_credits_image_today = 0;
            fullUserState.tracking_date = today;
            
            req.monitoring?.recordEvent('DAILY_CREDITS_RESET', { deviceId });
        } else {
            console.log(`✅ Same day detected, preserving credits: chat=${fullUserState.credits_consumed_today_chat}, image=${fullUserState.credits_consumed_today_image}`);
        }

        // Calculate AVAILABLE credits (this is what the client needs!)
        const DAILY_CHAT_CREDITS = 15;
        const DAILY_IMAGE_CREDITS = 20;
        const MAX_AD_CHAT_CREDITS = 10;   // From ads
        const MAX_AD_IMAGE_CREDITS = 10;  // From ads
        
        // Get ad credits earned today (if implemented in DB)
        const adChatCreditsEarned = fullUserState.ad_credits_chat_today || 0;
        const adImageCreditsEarned = fullUserState.ad_credits_image_today || 0;
        
        // Total daily allowance = base + ad credits
        const totalChatAllowance = DAILY_CHAT_CREDITS + adChatCreditsEarned;
        const totalImageAllowance = DAILY_IMAGE_CREDITS + adImageCreditsEarned;
        
        // Available = Total allowance - consumed
        const availableChatCredits = Math.max(0, totalChatAllowance - fullUserState.credits_consumed_today_chat);
        const availableImageCredits = Math.max(0, totalImageAllowance - fullUserState.credits_consumed_today_image);

        // Enhanced response with security metadata
        const responseState = {
            deviceId: fullUserState.device_id,
            deviceFingerprint: fullUserState.device_fingerprint,
            // Return AVAILABLE credits (what client expects)
            availableChatCredits: availableChatCredits,
            availableImageCredits: availableImageCredits,
            // Keep consumed for debugging/backwards compatibility
            creditsConsumedTodayChat: fullUserState.credits_consumed_today_chat,
            creditsConsumedTodayImage: fullUserState.credits_consumed_today_image,
            // Ad credit info
            adCreditsEarnedChat: adChatCreditsEarned,
            adCreditsEarnedImage: adImageCreditsEarned,
            canEarnMoreAdCredits: adChatCreditsEarned < MAX_AD_CHAT_CREDITS || adImageCreditsEarned < MAX_AD_IMAGE_CREDITS,
            hasActiveSubscription: fullUserState.has_active_subscription,
            subscriptionType: fullUserState.subscription_type,
            subscriptionEndTime: fullUserState.subscription_end_time || 0,
            lastActive: Date.now(),
            appVersion: fullUserState.app_version || "18",
            date: fullUserState.tracking_date,
            userId: fullUserState.id?.toString(),
            serverVersion: "2.0.0",
            securityFlags: {
                creditsResetToday: creditsReset,
                accountAge: Math.floor((Date.now() - new Date(fullUserState.created_at).getTime()) / (1000 * 60 * 60 * 24))
            }
        };

        console.log(`✅ User state retrieved: AVAILABLE credits: chat=${responseState.availableChatCredits}, image=${responseState.availableImageCredits}, consumed: chat=${responseState.creditsConsumedTodayChat}, image=${responseState.creditsConsumedTodayImage}, subscribed=${responseState.hasActiveSubscription}`);
        
        req.monitoring?.recordPerformance('GET_USER_STATE', Date.now() - startTime);
        res.json(responseState);

    } catch (error) {
        console.error('❌ Get user state error:', error);
        req.monitoring?.recordError('GET_USER_STATE_ERROR', error, { deviceId: req.params.deviceId });
        
        res.status(500).json({
            error: 'Failed to retrieve user state',
            message: process.env.NODE_ENV === 'production' ? 'Internal server error' : error.message
        });
    }
});

// Update user state with enhanced security validation
router.put('/:deviceId', validateDeviceId, validateUserState, handleValidationErrors, suspiciousActivityCheck, async (req, res) => {
    const startTime = Date.now();
    try {
        const { deviceId } = req.params;
        const userState = req.body;

        console.log(`💾 [${req.ip}] Updating user state for device: ${deviceId}`);
        console.log(`   Credits CONSUMED: chat=${userState.creditsConsumedTodayChat}, image=${userState.creditsConsumedTodayImage}`);
        console.log(`   Credits AVAILABLE after consumption: chat=${15 - userState.creditsConsumedTodayChat}, image=${20 - userState.creditsConsumedTodayImage}`);
        console.log(`   Subscription: ${userState.hasActiveSubscription} (${userState.subscriptionType || 'none'})`);

        // Enhanced security: Check for device fingerprint conflicts
        const conflictCheck = await req.db.query(
            'SELECT device_id FROM user_states WHERE device_fingerprint = $1 AND device_id != $2',
            [userState.deviceFingerprint, deviceId]
        );

        if (conflictCheck.rows.length > 0) {
            console.warn(`🚨 Device fingerprint conflict: ${userState.deviceFingerprint} already exists for different device`);
            req.monitoring?.recordSecurityEvent('FINGERPRINT_CONFLICT', {
                deviceId,
                conflictingDevice: conflictCheck.rows[0].device_id,
                ip: req.ip
            });
        }

        // Enhanced upsert with conflict resolution
        const result = await req.db.query(`
            INSERT INTO user_states (
                device_id, device_fingerprint, credits_consumed_today_chat, 
                credits_consumed_today_image, has_active_subscription, subscription_type, 
                subscription_end_time, tracking_date, app_version, updated_at
            ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, CURRENT_TIMESTAMP)
            ON CONFLICT (device_id) DO UPDATE SET
                device_fingerprint = EXCLUDED.device_fingerprint,
                credits_consumed_today_chat = GREATEST(user_states.credits_consumed_today_chat, EXCLUDED.credits_consumed_today_chat),
                credits_consumed_today_image = GREATEST(user_states.credits_consumed_today_image, EXCLUDED.credits_consumed_today_image),
                has_active_subscription = EXCLUDED.has_active_subscription,
                subscription_type = EXCLUDED.subscription_type,
                subscription_end_time = EXCLUDED.subscription_end_time,
                tracking_date = EXCLUDED.tracking_date,
                app_version = EXCLUDED.app_version,
                updated_at = CURRENT_TIMESTAMP
            RETURNING id, created_at, updated_at
        `, [
            deviceId,
            userState.deviceFingerprint,
            userState.creditsConsumedTodayChat,
            userState.creditsConsumedTodayImage,
            userState.hasActiveSubscription,
            userState.subscriptionType || null,
            userState.subscriptionEndTime || 0,
            userState.date,
            userState.appVersion || "18"
        ]);

        const wasCreated = result.rows[0].created_at === result.rows[0].updated_at;
        
        console.log(`✅ User state ${wasCreated ? 'created' : 'updated'} successfully for device: ${deviceId}`);
        
        req.monitoring?.recordEvent(wasCreated ? 'USER_STATE_CREATED' : 'USER_STATE_UPDATED', {
            deviceId,
            wasCreated,
            hasSubscription: userState.hasActiveSubscription
        });
        
        req.monitoring?.recordPerformance('UPDATE_USER_STATE', Date.now() - startTime);

        res.json({
            success: true,
            message: `User state ${wasCreated ? 'created' : 'updated'} successfully`,
            data: {
                userId: result.rows[0].id.toString(),
                updated: true,
                timestamp: Date.now(),
                wasCreated,
                serverVersion: "2.0.0"
            }
        });

    } catch (error) {
        console.error('❌ Update user state error:', error);
        req.monitoring?.recordError('UPDATE_USER_STATE_ERROR', error, { 
            deviceId: req.params.deviceId,
            ip: req.ip 
        });
        
        res.status(500).json({
            error: 'Failed to update user state',
            message: process.env.NODE_ENV === 'production' ? 'Internal server error' : error.message
        });
    }
});

// Enhanced reset with audit logging
router.post('/:deviceId/reset', validateDeviceId, handleValidationErrors, async (req, res) => {
    try {
        const { deviceId } = req.params;
        const { adminKey, reason } = req.body;

        // Basic admin protection (in production, use proper JWT)
        if (!adminKey || adminKey !== process.env.ADMIN_RESET_KEY) {
            console.warn(`🚨 Unauthorized reset attempt for device ${deviceId} from IP ${req.ip}`);
            req.monitoring?.recordSecurityEvent('UNAUTHORIZED_RESET_ATTEMPT', {
                deviceId,
                ip: req.ip
            });
            
            return res.status(403).json({
                error: 'Unauthorized reset operation'
            });
        }

        console.log(`🔄 [ADMIN] Resetting user state for device: ${deviceId}, reason: ${reason || 'No reason provided'}`);

        const result = await req.db.query(`
            UPDATE user_states 
            SET credits_consumed_today_chat = 0,
                credits_consumed_today_image = 0,
                has_active_subscription = false,
                subscription_type = null,
                subscription_end_time = 0,
                updated_at = CURRENT_TIMESTAMP
            WHERE device_id = $1
            RETURNING id, credits_consumed_today_chat, credits_consumed_today_image
        `, [deviceId]);

        if (result.rows.length === 0) {
            return res.status(404).json({
                error: 'Device not found',
                deviceId: deviceId
            });
        }

        req.monitoring?.recordEvent('ADMIN_USER_RESET', {
            deviceId,
            adminIp: req.ip,
            reason: reason || 'No reason provided'
        });

        console.log(`✅ [ADMIN] User state reset successfully for device: ${deviceId}`);

        res.json({
            success: true,
            message: 'User state reset successfully',
            data: {
                deviceId: deviceId,
                resetTimestamp: Date.now(),
                userId: result.rows[0].id.toString(),
                reason: reason || 'Admin reset'
            }
        });

    } catch (error) {
        console.error('❌ Reset user state error:', error);
        req.monitoring?.recordError('RESET_USER_STATE_ERROR', error, { deviceId: req.params.deviceId });
        
        res.status(500).json({
            error: 'Failed to reset user state',
            message: process.env.NODE_ENV === 'production' ? 'Internal server error' : error.message
        });
    }
});

// Enhanced statistics with privacy protection
router.get('/:deviceId/stats', validateDeviceId, handleValidationErrors, async (req, res) => {
    try {
        const { deviceId } = req.params;

        const result = await req.db.query(`
            SELECT 
                device_id,
                credits_consumed_today_chat,
                credits_consumed_today_image,
                has_active_subscription,
                subscription_type,
                created_at,
                updated_at,
                tracking_date,
                app_version
            FROM user_states 
            WHERE device_id = $1
        `, [deviceId]);

        if (result.rows.length === 0) {
            return res.status(404).json({
                error: 'Device not found'
            });
        }

        const user = result.rows[0];
        const accountAge = Math.floor((Date.now() - new Date(user.created_at).getTime()) / (1000 * 60 * 60 * 24));
        
        res.json({
            deviceId: user.device_id,
            statistics: {
                totalChatCreditsToday: user.credits_consumed_today_chat,
                totalImageCreditsToday: user.credits_consumed_today_image,
                isSubscriber: user.has_active_subscription,
                subscriptionType: user.subscription_type,
                accountAge: `${accountAge} days`,
                lastUpdated: user.updated_at,
                currentDate: user.tracking_date,
                appVersion: user.app_version,
                serverVersion: "2.0.0"
            },
            privacyNote: "Only basic usage statistics are provided. Device fingerprints and sensitive data are not exposed."
        });

    } catch (error) {
        console.error('❌ Get user stats error:', error);
        req.monitoring?.recordError('GET_USER_STATS_ERROR', error, { deviceId: req.params.deviceId });
        
        res.status(500).json({
            error: 'Failed to get user statistics',
            message: process.env.NODE_ENV === 'production' ? 'Internal server error' : error.message
        });
    }
});

// NEW: Dedicated credit consumption endpoint
router.post('/:deviceId/consume-credits', validateDeviceId, handleValidationErrors, async (req, res) => {
    try {
        const { deviceId } = req.params;
        const { creditType, amount, fingerprint } = req.body; // creditType: 'chat' or 'image'
        
        console.log(`🔥 [${req.ip}] CONSUMING ${amount} ${creditType} credits for device: ${deviceId}`);
        
        // Validate input
        if (!['chat', 'image'].includes(creditType) || !Number.isInteger(amount) || amount < 1 || amount > 10) {
            return res.status(400).json({
                error: 'Invalid credit consumption request',
                message: 'creditType must be "chat" or "image", amount must be 1-10'
            });
        }

        // Find user by device_id or fingerprint
        let user = await req.db.query('SELECT * FROM user_states WHERE device_id = $1', [deviceId]);
        
        if (user.rows.length === 0 && fingerprint) {
            console.log(`🔍 Device not found, checking by fingerprint: ${fingerprint.substring(0, 8)}...`);
            user = await req.db.query('SELECT * FROM user_states WHERE device_fingerprint = $1', [fingerprint]);
            
            if (user.rows.length > 0) {
                console.log(`🔒 REINSTALL DETECTED: Found user by fingerprint, updating device_id from ${user.rows[0].device_id} to ${deviceId}`);
                await req.db.query(
                    'UPDATE user_states SET device_id = $1, updated_at = CURRENT_TIMESTAMP WHERE device_fingerprint = $2',
                    [deviceId, fingerprint]
                );
                user.rows[0].device_id = deviceId; // Update local reference
            }
        }
        
        if (user.rows.length === 0) {
            return res.status(404).json({
                error: 'User not found',
                message: 'User must be registered before consuming credits'
            });
        }

        const currentUser = user.rows[0];
        const today = new Date().toISOString().split('T')[0];
        
        // Check if daily reset needed
        const dbDate = currentUser.tracking_date instanceof Date 
            ? currentUser.tracking_date.toISOString().split('T')[0]
            : currentUser.tracking_date?.toString()?.split('T')[0] || '';
            
        if (dbDate !== today) {
            console.log(`🔄 Daily reset needed before consumption: ${dbDate} → ${today}`);
            await req.db.query(
                `UPDATE user_states SET credits_consumed_today_chat = 0, credits_consumed_today_image = 0, ad_credits_chat_today = 0, ad_credits_image_today = 0, tracking_date = $1 WHERE device_id = $2`,
                [today, deviceId]
            );
            currentUser.credits_consumed_today_chat = 0;
            currentUser.credits_consumed_today_image = 0;
            currentUser.ad_credits_chat_today = 0;
            currentUser.ad_credits_image_today = 0;
        }
        
        // Calculate current consumption and check limits
        const currentConsumed = creditType === 'chat' ? currentUser.credits_consumed_today_chat : currentUser.credits_consumed_today_image;
        const dailyLimit = creditType === 'chat' ? 25 : 30; // 15+10 for chat, 20+10 for image
        const newConsumed = currentConsumed + amount;
        
        if (newConsumed > dailyLimit) {
            const remaining = Math.max(0, dailyLimit - currentConsumed);
            return res.status(429).json({
                error: 'Daily credit limit exceeded',
                message: `Cannot consume ${amount} ${creditType} credits. Daily limit: ${dailyLimit}, already consumed: ${currentConsumed}, remaining: ${remaining}`
            });
        }
        
        // Update consumption
        const updateField = creditType === 'chat' ? 'credits_consumed_today_chat' : 'credits_consumed_today_image';
        await req.db.query(
            `UPDATE user_states SET ${updateField} = $1, updated_at = CURRENT_TIMESTAMP WHERE device_id = $2`,
            [newConsumed, deviceId]
        );
        
        // Calculate remaining credits
        const availableAfter = dailyLimit - newConsumed;
        
        console.log(`✅ CONSUMED ${amount} ${creditType} credits for ${deviceId}: ${currentConsumed} → ${newConsumed} (${availableAfter} remaining)`);
        
        res.json({
            success: true,
            message: `Successfully consumed ${amount} ${creditType} credits`,
            data: {
                creditType,
                amountConsumed: amount,
                totalConsumedToday: newConsumed,
                availableCredits: availableAfter,
                dailyLimit: dailyLimit
            }
        });
        
    } catch (error) {
        console.error('❌ Consume credits error:', error);
        res.status(500).json({
            error: 'Failed to consume credits',
            message: process.env.NODE_ENV === 'production' ? 'Internal server error' : error.message
        });
    }
});

// NEW: Add ad credits endpoint
router.post('/:deviceId/add-ad-credits', validateDeviceId, handleValidationErrors, async (req, res) => {
    try {
        const { deviceId } = req.params;
        const { creditType, fingerprint } = req.body; // creditType: 'chat' or 'image'
        
        console.log(`📺 [${req.ip}] Adding AD credits (${creditType}) for device: ${deviceId}`);
        
        // Validate input
        if (!['chat', 'image'].includes(creditType)) {
            return res.status(400).json({
                error: 'Invalid credit type',
                message: 'creditType must be "chat" or "image"'
            });
        }

        // Find user (with fingerprint fallback)
        let user = await req.db.query('SELECT * FROM user_states WHERE device_id = $1', [deviceId]);
        
        if (user.rows.length === 0 && fingerprint) {
            user = await req.db.query('SELECT * FROM user_states WHERE device_fingerprint = $1', [fingerprint]);
            if (user.rows.length > 0) {
                await req.db.query(
                    'UPDATE user_states SET device_id = $1 WHERE device_fingerprint = $2',
                    [deviceId, fingerprint]
                );
            }
        }
        
        if (user.rows.length === 0) {
            return res.status(404).json({
                error: 'User not found',
                message: 'User must be registered before adding ad credits'
            });
        }

        const currentUser = user.rows[0];
        const adField = creditType === 'chat' ? 'ad_credits_chat_today' : 'ad_credits_image_today';
        const currentAdCredits = currentUser[adField] || 0;
        const maxAdCredits = 10;
        
        if (currentAdCredits >= maxAdCredits) {
            return res.status(429).json({
                error: 'Daily ad credit limit reached',
                message: `Maximum ${maxAdCredits} ad credits per day for ${creditType}`
            });
        }
        
        // Add ad credit to database
        const newAdCredits = currentAdCredits + 1;
        await req.db.query(
            `UPDATE user_states SET ${adField} = $1, updated_at = CURRENT_TIMESTAMP WHERE device_id = $2`,
            [newAdCredits, deviceId]
        );
        
        console.log(`✅ Added 1 AD credit for ${creditType}: ${currentAdCredits} → ${newAdCredits}`);
        
        res.json({
            success: true,
            message: `Successfully added 1 ${creditType} credit from ad`,
            data: {
                creditType,
                adCreditsToday: newAdCredits,
                maxAdCredits: maxAdCredits,
                canWatchMore: newAdCredits < maxAdCredits
            }
        });
        
    } catch (error) {
        console.error('❌ Add ad credits error:', error);
        res.status(500).json({
            error: 'Failed to add ad credits',
            message: process.env.NODE_ENV === 'production' ? 'Internal server error' : error.message
        });
    }
});

module.exports = router;