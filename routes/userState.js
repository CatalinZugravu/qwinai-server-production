// ==================== DATABASE SCHEMA ====================
/*
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    user_uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE device_fingerprints (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id),
    fingerprint_hash VARCHAR(64) UNIQUE NOT NULL,
    android_id VARCHAR(64),
    google_ad_id VARCHAR(64),
    huawei_oaid VARCHAR(64),
    device_model VARCHAR(128),
    device_brand VARCHAR(64),
    android_version VARCHAR(32),
    app_version VARCHAR(16),
    screen_resolution VARCHAR(32),
    timezone VARCHAR(64),
    language VARCHAR(16),
    country VARCHAR(8),
    carrier VARCHAR(64),
    total_ram BIGINT,
    cpu_cores INTEGER,
    first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    trust_score DECIMAL(3,2) DEFAULT 1.00,
    is_suspicious BOOLEAN DEFAULT FALSE
);

CREATE TABLE user_states (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id) NOT NULL,
    credits_chat_daily INTEGER DEFAULT 0,
    credits_image_daily INTEGER DEFAULT 0,
    credits_chat_bonus INTEGER DEFAULT 0,
    credits_image_bonus INTEGER DEFAULT 0,
    model_usage JSONB DEFAULT '{}',
    daily_reset_date DATE DEFAULT CURRENT_DATE,
    subscription_type VARCHAR(32),
    subscription_end_time BIGINT DEFAULT 0,
    subscription_platform VARCHAR(32),
    weekly_ad_credits INTEGER DEFAULT 0,
    total_ads_watched INTEGER DEFAULT 0,
    last_ad_timestamp BIGINT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE usage_history (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id),
    action_type VARCHAR(32),
    credits_used INTEGER,
    model_name VARCHAR(64),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    device_fingerprint_id INTEGER REFERENCES device_fingerprints(id),
    ip_address INET,
    session_id VARCHAR(64)
);

CREATE TABLE fraud_events (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id),
    event_type VARCHAR(64),
    severity VARCHAR(16),
    details JSONB,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_fingerprint_hash ON device_fingerprints(fingerprint_hash);
CREATE INDEX idx_user_states_user_id ON user_states(user_id);
CREATE INDEX idx_usage_history_user_timestamp ON usage_history(user_id, timestamp);
*/

const crypto = require('crypto');
const express = require('express');
const router = express.Router();

// ==================== CONFIGURATION ====================
const CONFIG = {
    DAILY_LIMITS: {
        FREE: {
            chat: 10,
            image: 3,
            models: {
                'gpt-3.5': 10,
                'gpt-4': 2,
                'claude': 3,
                'dalle': 3
            }
        },
        WEEKLY_SUB: {
            chat: 100,
            image: 30,
            models: {
                'gpt-3.5': 100,
                'gpt-4': 20,
                'claude': 30,
                'dalle': 30
            }
        },
        MONTHLY_SUB: {
            chat: 500,
            image: 150,
            models: {
                'gpt-3.5': 500,
                'gpt-4': 100,
                'claude': 150,
                'dalle': 150
            }
        }
    },
    AD_REWARDS: {
        chat: 2,
        image: 1,
        dailyLimit: 10,
        cooldownMinutes: 30
    },
    FRAUD_THRESHOLDS: {
        maxDailyReinstalls: 3,
        maxWeeklyReinstalls: 10,
        suspiciousPatternScore: 0.7,
        rapidConsumptionMinutes: 5,
        maxDevicesPerUser: 5
    }
};

// ==================== FINGERPRINTING ENGINE ====================
class DeviceFingerprinter {
    static generateFingerprint(deviceData) {
        // Create a stable fingerprint from multiple device characteristics
        const components = [
            deviceData.androidId || '',
            deviceData.googleAdId || deviceData.huaweiOaid || '',
            deviceData.deviceModel || '',
            deviceData.deviceBrand || '',
            deviceData.screenResolution || '',
            deviceData.totalRam ? Math.floor(deviceData.totalRam / 100000000) : '', // Round to 100MB
            deviceData.cpuCores || '',
            deviceData.timezone || '',
            deviceData.language || ''
        ];
        
        const fingerprintString = components.join('|');
        return crypto.createHash('sha256').update(fingerprintString).digest('hex');
    }

    static calculateSimilarity(fp1, fp2) {
        // Calculate similarity score between two fingerprints
        let matches = 0;
        const fields = ['device_model', 'device_brand', 'screen_resolution', 'timezone', 'language', 'total_ram', 'cpu_cores'];
        
        for (const field of fields) {
            if (fp1[field] && fp2[field] && fp1[field] === fp2[field]) {
                matches++;
            }
        }
        
        return matches / fields.length;
    }
}

// ==================== FRAUD DETECTION ====================
class FraudDetector {
    static async checkForFraud(db, userId, deviceData, action) {
        const fraudIndicators = [];
        
        // Check reinstall frequency
        const recentDevices = await db.query(`
            SELECT COUNT(DISTINCT fingerprint_hash) as device_count,
                   MAX(first_seen) as latest_device
            FROM device_fingerprints
            WHERE user_id = $1
                AND first_seen > NOW() - INTERVAL '24 hours'
        `, [userId]);
        
        if (recentDevices.rows[0].device_count > CONFIG.FRAUD_THRESHOLDS.maxDailyReinstalls) {
            fraudIndicators.push({
                type: 'EXCESSIVE_REINSTALLS',
                severity: 'HIGH',
                detail: `${recentDevices.rows[0].device_count} devices in 24h`
            });
        }
        
        // Check rapid credit consumption
        const recentUsage = await db.query(`
            SELECT SUM(credits_used) as total_credits,
                   COUNT(*) as action_count
            FROM usage_history
            WHERE user_id = $1
                AND timestamp > NOW() - INTERVAL '5 minutes'
        `, [userId]);
        
        if (recentUsage.rows[0].action_count > 20) {
            fraudIndicators.push({
                type: 'RAPID_CONSUMPTION',
                severity: 'MEDIUM',
                detail: `${recentUsage.rows[0].action_count} actions in 5 minutes`
            });
        }
        
        // Check for device spoofing patterns
        const similarDevices = await db.query(`
            SELECT fingerprint_hash, android_id, google_ad_id, device_model
            FROM device_fingerprints
            WHERE user_id = $1
                AND last_seen > NOW() - INTERVAL '7 days'
        `, [userId]);
        
        if (similarDevices.rows.length > CONFIG.FRAUD_THRESHOLDS.maxDevicesPerUser) {
            fraudIndicators.push({
                type: 'MULTIPLE_DEVICES',
                severity: 'MEDIUM',
                detail: `${similarDevices.rows.length} different devices`
            });
        }
        
        // Log fraud events
        for (const indicator of fraudIndicators) {
            await db.query(`
                INSERT INTO fraud_events (user_id, event_type, severity, details)
                VALUES ($1, $2, $3, $4)
            `, [userId, indicator.type, indicator.severity, JSON.stringify(indicator)]);
        }
        
        return {
            isSuspicious: fraudIndicators.length > 0,
            indicators: fraudIndicators,
            trustScore: Math.max(0, 1 - (fraudIndicators.length * 0.25))
        };
    }
}

// ==================== USER MANAGEMENT ====================
class UserManager {
    static async findOrCreateUser(db, deviceData) {
        const fingerprint = DeviceFingerprinter.generateFingerprint(deviceData);
        
        // Try to find existing user by fingerprint
        let userResult = await db.query(`
            SELECT u.*, df.trust_score, df.is_suspicious
            FROM device_fingerprints df
            JOIN users u ON df.user_id = u.id
            WHERE df.fingerprint_hash = $1
            ORDER BY df.last_seen DESC
            LIMIT 1
        `, [fingerprint]);
        
        if (userResult.rows.length > 0) {
            // Update last seen
            await db.query(`
                UPDATE device_fingerprints
                SET last_seen = CURRENT_TIMESTAMP,
                    app_version = $2
                WHERE fingerprint_hash = $1
            `, [fingerprint, deviceData.appVersion]);
            
            return userResult.rows[0];
        }
        
        // Try to find by advertising ID (more persistent)
        if (deviceData.googleAdId || deviceData.huaweiOaid) {
            const adId = deviceData.googleAdId || deviceData.huaweiOaid;
            userResult = await db.query(`
                SELECT DISTINCT u.*, df.trust_score
                FROM device_fingerprints df
                JOIN users u ON df.user_id = u.id
                WHERE df.google_ad_id = $1 OR df.huawei_oaid = $1
                ORDER BY df.last_seen DESC
                LIMIT 1
            `, [adId]);
            
            if (userResult.rows.length > 0) {
                // Add new fingerprint to existing user
                await this.addDeviceFingerprint(db, userResult.rows[0].id, deviceData, fingerprint);
                return userResult.rows[0];
            }
        }
        
        // Check for similar devices (fuzzy matching)
        const similarDevice = await this.findSimilarDevice(db, deviceData);
        if (similarDevice && similarDevice.similarity > 0.8) {
            // High similarity - likely same user
            await this.addDeviceFingerprint(db, similarDevice.user_id, deviceData, fingerprint);
            return await db.query('SELECT * FROM users WHERE id = $1', [similarDevice.user_id]).then(r => r.rows[0]);
        }
        
        // Create new user
        const newUser = await db.query(`
            INSERT INTO users DEFAULT VALUES
            RETURNING *
        `);
        
        await this.addDeviceFingerprint(db, newUser.rows[0].id, deviceData, fingerprint);
        
        // Initialize user state
        await db.query(`
            INSERT INTO user_states (user_id) VALUES ($1)
        `, [newUser.rows[0].id]);
        
        return newUser.rows[0];
    }
    
    static async addDeviceFingerprint(db, userId, deviceData, fingerprint) {
        await db.query(`
            INSERT INTO device_fingerprints (
                user_id, fingerprint_hash, android_id, google_ad_id, huawei_oaid,
                device_model, device_brand, android_version, app_version,
                screen_resolution, timezone, language, country, carrier,
                total_ram, cpu_cores
            ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16)
            ON CONFLICT (fingerprint_hash) DO UPDATE
            SET last_seen = CURRENT_TIMESTAMP,
                app_version = EXCLUDED.app_version
        `, [
            userId, fingerprint, deviceData.androidId, deviceData.googleAdId, deviceData.huaweiOaid,
            deviceData.deviceModel, deviceData.deviceBrand, deviceData.androidVersion, deviceData.appVersion,
            deviceData.screenResolution, deviceData.timezone, deviceData.language, deviceData.country,
            deviceData.carrier, deviceData.totalRam, deviceData.cpuCores
        ]);
    }
    
    static async findSimilarDevice(db, deviceData) {
        const candidates = await db.query(`
            SELECT * FROM device_fingerprints
            WHERE (device_model = $1 OR device_brand = $2)
                AND last_seen > NOW() - INTERVAL '30 days'
        `, [deviceData.deviceModel, deviceData.deviceBrand]);
        
        let bestMatch = null;
        let maxSimilarity = 0;
        
        for (const candidate of candidates.rows) {
            const similarity = DeviceFingerprinter.calculateSimilarity(candidate, deviceData);
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                bestMatch = candidate;
            }
        }
        
        return bestMatch ? { ...bestMatch, similarity: maxSimilarity } : null;
    }
}

// ==================== API ENDPOINTS ====================

// Get user state
router.post('/state', async (req, res) => {
    try {
        const deviceData = req.body;
        
        // Validate device data
        if (!deviceData.deviceModel || !deviceData.deviceBrand) {
            return res.status(400).json({ error: 'Incomplete device information' });
        }
        
        // Find or create user
        const user = await UserManager.findOrCreateUser(req.db, deviceData);
        
        // Check for fraud
        const fraudCheck = await FraudDetector.checkForFraud(req.db, user.id, deviceData, 'STATE_CHECK');
        
        // Get current state
        const stateResult = await req.db.query(`
            SELECT * FROM user_states WHERE user_id = $1
        `, [user.id]);
        
        let state = stateResult.rows[0];
        
        // Reset daily credits if needed
        const today = new Date().toISOString().split('T')[0];
        if (!state || state.daily_reset_date !== today) {
            const limits = state?.subscription_type === 'weekly' ? CONFIG.DAILY_LIMITS.WEEKLY_SUB :
                          state?.subscription_type === 'monthly' ? CONFIG.DAILY_LIMITS.MONTHLY_SUB :
                          CONFIG.DAILY_LIMITS.FREE;
            
            await req.db.query(`
                UPDATE user_states
                SET credits_chat_daily = 0,
                    credits_image_daily = 0,
                    model_usage = '{}',
                    daily_reset_date = $2,
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = $1
            `, [user.id, today]);
            
            state = await req.db.query('SELECT * FROM user_states WHERE user_id = $1', [user.id]).then(r => r.rows[0]);
        }
        
        // Calculate available credits
        const limits = state.subscription_type === 'weekly' ? CONFIG.DAILY_LIMITS.WEEKLY_SUB :
                      state.subscription_type === 'monthly' ? CONFIG.DAILY_LIMITS.MONTHLY_SUB :
                      CONFIG.DAILY_LIMITS.FREE;
        
        res.json({
            userId: user.user_uuid,
            credits: {
                chat: {
                    used: state.credits_chat_daily,
                    limit: limits.chat,
                    bonus: state.credits_chat_bonus,
                    available: limits.chat - state.credits_chat_daily + state.credits_chat_bonus
                },
                image: {
                    used: state.credits_image_daily,
                    limit: limits.image,
                    bonus: state.credits_image_bonus,
                    available: limits.image - state.credits_image_daily + state.credits_image_bonus
                }
            },
            modelUsage: state.model_usage || {},
            modelLimits: limits.models,
            subscription: {
                active: !!state.subscription_type,
                type: state.subscription_type,
                endTime: state.subscription_end_time,
                platform: state.subscription_platform
            },
            ads: {
                watchedToday: state.weekly_ad_credits,
                dailyLimit: CONFIG.AD_REWARDS.dailyLimit,
                nextAvailable: state.last_ad_timestamp ? 
                    state.last_ad_timestamp + (CONFIG.AD_REWARDS.cooldownMinutes * 60000) : 0
            },
            security: {
                trustScore: fraudCheck.trustScore,
                isRestricted: fraudCheck.isSuspicious
            }
        });
        
    } catch (error) {
        console.error('State retrieval error:', error);
        res.status(500).json({ error: 'Failed to get user state' });
    }
});

// Consume credits
router.post('/consume', async (req, res) => {
    const client = await req.db.connect();
    
    try {
        await client.query('BEGIN');
        
        const { deviceData, creditType, amount, model, sessionId } = req.body;
        
        // Find user
        const user = await UserManager.findOrCreateUser(client, deviceData);
        
        // Check fraud
        const fraudCheck = await FraudDetector.checkForFraud(client, user.id, deviceData, 'CONSUME');
        if (fraudCheck.isSuspicious && fraudCheck.trustScore < 0.5) {
            await client.query('ROLLBACK');
            return res.status(403).json({ 
                error: 'Suspicious activity detected',
                trustScore: fraudCheck.trustScore 
            });
        }
        
        // Get current state
        const state = await client.query('SELECT * FROM user_states WHERE user_id = $1 FOR UPDATE', [user.id])
            .then(r => r.rows[0]);
        
        // Check limits
        const limits = state.subscription_type === 'weekly' ? CONFIG.DAILY_LIMITS.WEEKLY_SUB :
                      state.subscription_type === 'monthly' ? CONFIG.DAILY_LIMITS.MONTHLY_SUB :
                      CONFIG.DAILY_LIMITS.FREE;
        
        const currentUsed = creditType === 'chat' ? state.credits_chat_daily : state.credits_image_daily;
        const currentBonus = creditType === 'chat' ? state.credits_chat_bonus : state.credits_image_bonus;
        const totalAvailable = limits[creditType] - currentUsed + currentBonus;
        
        if (amount > totalAvailable) {
            await client.query('ROLLBACK');
            return res.status(403).json({ 
                error: 'Insufficient credits',
                available: totalAvailable,
                requested: amount
            });
        }
        
        // Check model-specific limits
        if (model && limits.models[model]) {
            const modelUsage = state.model_usage || {};
            const modelUsed = modelUsage[model] || 0;
            if (modelUsed + amount > limits.models[model]) {
                await client.query('ROLLBACK');
                return res.status(403).json({
                    error: 'Model limit exceeded',
                    model: model,
                    limit: limits.models[model],
                    used: modelUsed
                });
            }
        }
        
        // Deduct credits (use bonus first)
        let bonusUsed = 0;
        let dailyUsed = amount;
        
        if (currentBonus > 0) {
            bonusUsed = Math.min(amount, currentBonus);
            dailyUsed = amount - bonusUsed;
        }
        
        // Update state
        const modelUsage = state.model_usage || {};
        modelUsage[model] = (modelUsage[model] || 0) + amount;
        
        await client.query(`
            UPDATE user_states
            SET credits_${creditType}_daily = credits_${creditType}_daily + $2,
                credits_${creditType}_bonus = credits_${creditType}_bonus - $3,
                model_usage = $4,
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = $1
        `, [user.id, dailyUsed, bonusUsed, JSON.stringify(modelUsage)]);
        
        // Log usage
        const fingerprint = DeviceFingerprinter.generateFingerprint(deviceData);
        const fpResult = await client.query(
            'SELECT id FROM device_fingerprints WHERE fingerprint_hash = $1',
            [fingerprint]
        );
        
        await client.query(`
            INSERT INTO usage_history (user_id, action_type, credits_used, model_name, device_fingerprint_id, ip_address, session_id)
            VALUES ($1, $2, $3, $4, $5, $6, $7)
        `, [user.id, creditType, amount, model, fpResult.rows[0]?.id, req.ip, sessionId]);
        
        await client.query('COMMIT');
        
        res.json({
            success: true,
            creditsUsed: amount,
            bonusUsed: bonusUsed,
            regularUsed: dailyUsed,
            remaining: totalAvailable - amount
        });
        
    } catch (error) {
        await client.query('ROLLBACK');
        console.error('Consume credits error:', error);
        res.status(500).json({ error: 'Failed to consume credits' });
    } finally {
        client.release();
    }
});

// Watch ad for credits
router.post('/ad-reward', async (req, res) => {
    const client = await req.db.connect();
    
    try {
        await client.query('BEGIN');
        
        const { deviceData, adNetwork, adId } = req.body;
        
        // Find user
        const user = await UserManager.findOrCreateUser(client, deviceData);
        
        // Check fraud
        const fraudCheck = await FraudDetector.checkForFraud(client, user.id, deviceData, 'AD_WATCH');
        if (fraudCheck.trustScore < 0.3) {
            await client.query('ROLLBACK');
            return res.status(403).json({ error: 'Cannot process ad reward' });
        }
        
        // Get current state
        const state = await client.query('SELECT * FROM user_states WHERE user_id = $1 FOR UPDATE', [user.id])
            .then(r => r.rows[0]);
        
        // Check ad limits
        const now = Date.now();
        if (state.weekly_ad_credits >= CONFIG.AD_REWARDS.dailyLimit) {
            await client.query('ROLLBACK');
            return res.status(403).json({ 
                error: 'Daily ad limit reached',
                nextResetTime: new Date().setHours(24, 0, 0, 0)
            });
        }
        
        if (state.last_ad_timestamp && 
            (now - state.last_ad_timestamp) < CONFIG.AD_REWARDS.cooldownMinutes * 60000) {
            await client.query('ROLLBACK');
            return res.status(403).json({
                error: 'Ad cooldown active',
                nextAvailable: state.last_ad_timestamp + (CONFIG.AD_REWARDS.cooldownMinutes * 60000)
            });
        }
        
        // Grant rewards
        await client.query(`
            UPDATE user_states
            SET credits_chat_bonus = credits_chat_bonus + $2,
                credits_image_bonus = credits_image_bonus + $3,
                weekly_ad_credits = weekly_ad_credits + 1,
                total_ads_watched = total_ads_watched + 1,
                last_ad_timestamp = $4,
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = $1
        `, [user.id, CONFIG.AD_REWARDS.chat, CONFIG.AD_REWARDS.image, now]);
        
        // Log ad watch
        await client.query(`
            INSERT INTO usage_history (user_id, action_type, credits_used, details)
            VALUES ($1, 'AD_REWARD', 0, $2)
        `, [user.id, JSON.stringify({ adNetwork, adId })]);
        
        await client.query('COMMIT');
        
        res.json({
            success: true,
            rewards: {
                chat: CONFIG.AD_REWARDS.chat,
                image: CONFIG.AD_REWARDS.image
            },
            adsWatchedToday: state.weekly_ad_credits + 1,
            dailyLimit: CONFIG.AD_REWARDS.dailyLimit
        });
        
    } catch (error) {
        await client.query('ROLLBACK');
        console.error('Ad reward error:', error);
        res.status(500).json({ error: 'Failed to process ad reward' });
    } finally {
        client.release();
    }
});

// Update subscription
router.post('/subscription', async (req, res) => {
    try {
        const { deviceData, subscriptionType, platform, purchaseToken, endTime } = req.body;
        
        // Validate with Google Play / Huawei AppGallery
        // TODO: Add actual validation with store APIs
        
        const user = await UserManager.findOrCreateUser(req.db, deviceData);
        
        await req.db.query(`
            UPDATE user_states
            SET subscription_type = $2,
                subscription_platform = $3,
                subscription_end_time = $4,
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = $1
        `, [user.id, subscriptionType, platform, endTime]);
        
        res.json({
            success: true,
            subscription: {
                type: subscriptionType,
                platform: platform,
                endTime: endTime
            }
        });
        
    } catch (error) {
        console.error('Subscription update error:', error);
        res.status(500).json({ error: 'Failed to update subscription' });
    }
});

// Get usage analytics
router.post('/analytics', async (req, res) => {
    try {
        const { deviceData } = req.body;
        const user = await UserManager.findOrCreateUser(req.db, deviceData);
        
        const analytics = await req.db.query(`
            SELECT 
                DATE(timestamp) as date,
                action_type,
                SUM(credits_used) as total_credits,
                COUNT(*) as action_count
            FROM usage_history
            WHERE user_id = $1
                AND timestamp > NOW() - INTERVAL '30 days'
            GROUP BY DATE(timestamp), action_type
            ORDER BY date DESC
        `, [user.id]);
        
        res.json({
            userId: user.user_uuid,
            usage: analytics.rows
        });
        
    } catch (error) {
        console.error('Analytics error:', error);
        res.status(500).json({ error: 'Failed to get analytics' });
    }
});

module.exports = router;
