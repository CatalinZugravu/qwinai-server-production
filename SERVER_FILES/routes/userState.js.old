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
        
        console.log(`🔍 [${req.ip}] Getting user state for device: ${deviceId}`);
        req.monitoring?.recordRequest('GET_USER_STATE', { deviceId, ip: req.ip });

        // Check for device fingerprint conflicts (security measure)
        const existingUser = await req.db.query(
            'SELECT device_id, device_fingerprint FROM user_states WHERE device_id = $1',
            [deviceId]
        );

        if (existingUser.rows.length === 0) {
            // New user - return secure default state
            const today = new Date().toISOString().split('T')[0];
            const defaultState = {
                deviceId,
                deviceFingerprint: "",
                creditsConsumedTodayChat: 0,
                creditsConsumedTodayImage: 0,
                hasActiveSubscription: false,
                subscriptionType: null,
                subscriptionEndTime: 0,
                lastActive: Date.now(),
                appVersion: "18",
                date: today,
                userId: null,
                serverVersion: "2.0.0"
            };
            
            console.log(`👤 New user detected: ${deviceId}`);
            req.monitoring?.recordEvent('NEW_USER_DETECTED', { deviceId });
            
            return res.json(defaultState);
        }

        const userState = existingUser.rows[0];
        const today = new Date().toISOString().split('T')[0];

        // Get full user state for existing user
        const fullUserResult = await req.db.query(
            'SELECT * FROM user_states WHERE device_id = $1',
            [deviceId]
        );
        
        const fullUserState = fullUserResult.rows[0];

        // Reset daily credits if new day
        let creditsReset = false;
        if (fullUserState.tracking_date !== today) {
            console.log(`🔄 Resetting daily credits for device: ${deviceId} (${fullUserState.tracking_date} → ${today})`);
            
            await req.db.query(
                `UPDATE user_states 
                 SET credits_consumed_today_chat = 0, 
                     credits_consumed_today_image = 0, 
                     tracking_date = $1,
                     updated_at = CURRENT_TIMESTAMP
                 WHERE device_id = $2`,
                [today, deviceId]
            );
            
            creditsReset = true;
            fullUserState.credits_consumed_today_chat = 0;
            fullUserState.credits_consumed_today_image = 0;
            fullUserState.tracking_date = today;
            
            req.monitoring?.recordEvent('DAILY_CREDITS_RESET', { deviceId });
        }

        // Enhanced response with security metadata
        const responseState = {
            deviceId: fullUserState.device_id,
            deviceFingerprint: fullUserState.device_fingerprint,
            creditsConsumedTodayChat: fullUserState.credits_consumed_today_chat,
            creditsConsumedTodayImage: fullUserState.credits_consumed_today_image,
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

        console.log(`✅ User state retrieved: chat=${responseState.creditsConsumedTodayChat}, image=${responseState.creditsConsumedTodayImage}, subscribed=${responseState.hasActiveSubscription}`);
        
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
        console.log(`   Credits: chat=${userState.creditsConsumedTodayChat}, image=${userState.creditsConsumedTodayImage}`);
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

module.exports = router;