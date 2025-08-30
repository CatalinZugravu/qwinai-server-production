const express = require('express');
const router = express.Router();

// Get user state
router.get('/:deviceId', async (req, res) => {
    try {
        const { deviceId } = req.params;
        
        // Validate device ID format
        if (!deviceId || deviceId.length !== 16 || !/^[a-zA-Z0-9]+$/.test(deviceId)) {
            return res.status(400).json({ 
                error: 'Invalid device ID format. Must be 16 alphanumeric characters.' 
            });
        }

        console.log(`🔍 Getting user state for device: ${deviceId}`);

        const result = await req.db.query(
            'SELECT * FROM user_states WHERE device_id = $1',
            [deviceId]
        );

        if (result.rows.length === 0) {
            // New user - return default state
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
                userId: null
            };
            
            console.log(`👤 New user detected: ${deviceId}`);
            return res.json(defaultState);
        }

        const userState = result.rows[0];
        const today = new Date().toISOString().split('T')[0];

        // Reset daily credits if new day
        if (userState.current_date !== today) {
            console.log(`🔄 Resetting daily credits for device: ${deviceId} (was ${userState.current_date}, now ${today})`);
            
            await req.db.query(
                `UPDATE user_states 
                 SET credits_consumed_today_chat = 0, 
                     credits_consumed_today_image = 0, 
                     current_date = $1,
                     updated_at = CURRENT_TIMESTAMP
                 WHERE device_id = $2`,
                [today, deviceId]
            );
            
            userState.credits_consumed_today_chat = 0;
            userState.credits_consumed_today_image = 0;
            userState.current_date = today;
        }

        const responseState = {
            deviceId: userState.device_id,
            deviceFingerprint: userState.device_fingerprint,
            creditsConsumedTodayChat: userState.credits_consumed_today_chat,
            creditsConsumedTodayImage: userState.credits_consumed_today_image,
            hasActiveSubscription: userState.has_active_subscription,
            subscriptionType: userState.subscription_type,
            subscriptionEndTime: userState.subscription_end_time || 0,
            lastActive: Date.now(),
            appVersion: userState.app_version || "18",
            date: userState.current_date,
            userId: userState.id?.toString()
        };

        console.log(`✅ User state retrieved: chat=${responseState.creditsConsumedTodayChat}, image=${responseState.creditsConsumedTodayImage}, subscribed=${responseState.hasActiveSubscription}`);
        res.json(responseState);

    } catch (error) {
        console.error('❌ Get user state error:', error);
        res.status(500).json({
            error: 'Failed to retrieve user state',
            message: error.message
        });
    }
});

// Update user state
router.put('/:deviceId', async (req, res) => {
    try {
        const { deviceId } = req.params;
        const userState = req.body;

        // Validate device ID
        if (!deviceId || deviceId.length !== 16 || !/^[a-zA-Z0-9]+$/.test(deviceId)) {
            return res.status(400).json({ 
                error: 'Invalid device ID format' 
            });
        }

        // Validate required fields
        if (!userState.deviceFingerprint) {
            return res.status(400).json({ 
                error: 'Device fingerprint is required' 
            });
        }

        // Validate credit consumption values
        if (userState.creditsConsumedTodayChat < 0 || userState.creditsConsumedTodayImage < 0) {
            return res.status(400).json({ 
                error: 'Credit consumption values cannot be negative' 
            });
        }

        // Security check: Prevent unrealistic consumption (abuse detection)
        if (userState.creditsConsumedTodayChat > 200 || userState.creditsConsumedTodayImage > 200) {
            console.log(`🚨 Suspicious activity detected for device ${deviceId}: chat=${userState.creditsConsumedTodayChat}, image=${userState.creditsConsumedTodayImage}`);
            return res.status(400).json({ 
                error: 'Unrealistic credit consumption detected. Please contact support if this is an error.',
                details: {
                    chatCredits: userState.creditsConsumedTodayChat,
                    imageCredits: userState.creditsConsumedTodayImage,
                    maxAllowed: 200
                }
            });
        }

        console.log(`💾 Updating user state for device: ${deviceId}`);
        console.log(`   Credits: chat=${userState.creditsConsumedTodayChat}, image=${userState.creditsConsumedTodayImage}`);
        console.log(`   Subscription: ${userState.hasActiveSubscription} (${userState.subscriptionType || 'none'})`);

        // Upsert user state (insert or update)
        const result = await req.db.query(`
            INSERT INTO user_states (
                device_id, device_fingerprint, credits_consumed_today_chat, 
                credits_consumed_today_image, has_active_subscription, subscription_type, 
                subscription_end_time, current_date, app_version, updated_at
            ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, CURRENT_TIMESTAMP)
            ON CONFLICT (device_id) DO UPDATE SET
                device_fingerprint = EXCLUDED.device_fingerprint,
                credits_consumed_today_chat = EXCLUDED.credits_consumed_today_chat,
                credits_consumed_today_image = EXCLUDED.credits_consumed_today_image,
                has_active_subscription = EXCLUDED.has_active_subscription,
                subscription_type = EXCLUDED.subscription_type,
                subscription_end_time = EXCLUDED.subscription_end_time,
                current_date = EXCLUDED.current_date,
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

        console.log(`✅ User state updated successfully for device: ${deviceId}`);

        res.json({
            success: true,
            message: 'User state updated successfully',
            data: {
                userId: result.rows[0].id.toString(),
                updated: true,
                timestamp: Date.now(),
                wasCreated: result.rows[0].created_at === result.rows[0].updated_at
            }
        });

    } catch (error) {
        console.error('❌ Update user state error:', error);
        res.status(500).json({
            error: 'Failed to update user state',
            message: error.message
        });
    }
});

// Reset user state (for support/debugging)
router.post('/:deviceId/reset', async (req, res) => {
    try {
        const { deviceId } = req.params;

        // Validate device ID
        if (!deviceId || deviceId.length !== 16) {
            return res.status(400).json({ 
                error: 'Invalid device ID' 
            });
        }

        console.log(`🔄 Resetting user state for device: ${deviceId}`);

        const result = await req.db.query(`
            UPDATE user_states 
            SET credits_consumed_today_chat = 0,
                credits_consumed_today_image = 0,
                has_active_subscription = false,
                subscription_type = null,
                subscription_end_time = 0,
                updated_at = CURRENT_TIMESTAMP
            WHERE device_id = $1
            RETURNING id
        `, [deviceId]);

        if (result.rows.length === 0) {
            return res.status(404).json({
                error: 'Device not found',
                deviceId: deviceId
            });
        }

        console.log(`✅ User state reset successfully for device: ${deviceId}`);

        res.json({
            success: true,
            message: 'User state reset successfully',
            data: {
                deviceId: deviceId,
                resetTimestamp: Date.now(),
                userId: result.rows[0].id.toString()
            }
        });

    } catch (error) {
        console.error('❌ Reset user state error:', error);
        res.status(500).json({
            error: 'Failed to reset user state',
            message: error.message
        });
    }
});

// Get user statistics (optional - for analytics)
router.get('/:deviceId/stats', async (req, res) => {
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
                current_date,
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
        
        res.json({
            deviceId: user.device_id,
            statistics: {
                totalChatCreditsToday: user.credits_consumed_today_chat,
                totalImageCreditsToday: user.credits_consumed_today_image,
                isSubscriber: user.has_active_subscription,
                subscriptionType: user.subscription_type,
                accountAge: Math.floor((Date.now() - new Date(user.created_at).getTime()) / (1000 * 60 * 60 * 24)) + ' days',
                lastUpdated: user.updated_at,
                currentDate: user.current_date,
                appVersion: user.app_version
            }
        });

    } catch (error) {
        console.error('❌ Get user stats error:', error);
        res.status(500).json({
            error: 'Failed to get user statistics',
            message: error.message
        });
    }
});

module.exports = router;