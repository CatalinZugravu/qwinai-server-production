const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const rateLimit = require('express-rate-limit');
const { Pool } = require('pg');
const redis = require('redis');
const cron = require('node-cron');
const fs = require('fs').promises;
const path = require('path');
const crypto = require('crypto');

// Import enhanced services
const ProductionMonitoring = require('./middleware/productionMonitoring');
const SecureFileProcessor = require('./services/secureFileProcessor');
const UniversalTokenCounter = require('./services/universalTokenCounter');

// Import routes (these need to be updated to use new services)
const userStateRoutes = require('./routes/userState');
const secureFileRoutes = require('./routes/fileProcessor');

/**
 * PRODUCTION-READY QWINAI SERVER
 * Features:
 * - Enterprise-grade security
 * - Multi-user concurrency support
 * - Comprehensive monitoring
 * - Universal AI model support
 * - Privacy protection
 * - Automatic scaling
 */

const app = express();
const port = process.env.PORT || 3000;

// Initialize monitoring first
const monitoring = new ProductionMonitoring();

// Initialize secure services
let secureFileProcessor;
let universalTokenCounter;

async function initializeServices() {
    try {
        console.log('🚀 Initializing production services...');
        
        // Initialize secure file processor
        secureFileProcessor = new SecureFileProcessor();
        
        // Initialize universal token counter
        universalTokenCounter = new UniversalTokenCounter();
        await universalTokenCounter.initialize();
        
        console.log('✅ All production services initialized');
    } catch (error) {
        console.error('❌ Service initialization failed:', error);
        throw error;
    }
}

// Enhanced security configuration
app.use(helmet({
    contentSecurityPolicy: {
        directives: {
            defaultSrc: ["'self'"],
            styleSrc: ["'self'", "'unsafe-inline'"],
            scriptSrc: ["'self'"],
            imgSrc: ["'self'", "data:", "https:"],
            connectSrc: ["'self'"],
            fontSrc: ["'self'"],
            objectSrc: ["'none'"],
            mediaSrc: ["'self'"],
            frameSrc: ["'none'"]
        }
    },
    crossOriginEmbedderPolicy: false, // Allow file uploads
    hsts: {
        maxAge: 31536000,
        includeSubDomains: true,
        preload: true
    }
}));

// CORS with strict origin control
const allowedOrigins = process.env.ALLOWED_ORIGINS ? 
    process.env.ALLOWED_ORIGINS.split(',') : 
    ['http://localhost:3000', 'https://localhost:3000'];

app.use(cors({
    origin: function(origin, callback) {
        // Allow requests with no origin (mobile apps, curl, etc.)
        if (!origin) return callback(null, true);
        
        if (allowedOrigins.includes(origin) || process.env.NODE_ENV === 'development') {
            callback(null, true);
        } else {
            monitoring.trackSecurityEvent('BLOCKED_REQUEST', { origin });
            callback(new Error('Not allowed by CORS'));
        }
    },
    credentials: true,
    optionsSuccessStatus: 200
}));

// Request monitoring middleware
app.use(monitoring.requestMonitoringMiddleware());

// Enhanced rate limiting with different tiers
const createRateLimit = (windowMs, max, message, skipSuccessfulRequests = false) => {
    return rateLimit({
        windowMs,
        max,
        message: { error: message, retryAfter: Math.ceil(windowMs / 1000) },
        standardHeaders: true,
        legacyHeaders: false,
        skipSuccessfulRequests,
        handler: (req, res) => {
            monitoring.trackSecurityEvent('RATE_LIMIT_HIT', {
                ip: req.ip,
                endpoint: req.path,
                userAgent: req.get('User-Agent')
            });
            res.status(429).json({
                error: message,
                retryAfter: Math.ceil(windowMs / 1000)
            });
        }
    });
};

// Apply different rate limits
app.use('/api/', createRateLimit(15 * 60 * 1000, 100, 'Too many requests, please try again later')); // 100 per 15 min
app.use('/api/file-processor/', createRateLimit(15 * 60 * 1000, 10, 'Too many file uploads, please try again later')); // 10 per 15 min
app.use('/api/user-state/', createRateLimit(5 * 60 * 1000, 50, 'Too many state updates, please slow down')); // 50 per 5 min

// Body parsing with size limits and validation
app.use(express.json({ 
    limit: '10mb',
    verify: (req, res, buf) => {
        try {
            JSON.parse(buf);
        } catch (e) {
            monitoring.trackSecurityEvent('VALIDATION_FAILED', {
                type: 'invalid_json',
                ip: req.ip,
                error: e.message
            });
            throw new Error('Invalid JSON');
        }
    }
}));

app.use(express.urlencoded({ 
    extended: true, 
    limit: '10mb',
    parameterLimit: 100 // Prevent parameter pollution
}));

// Database connection with enhanced configuration
const db = new Pool({
    connectionString: process.env.DATABASE_URL || 'postgresql://localhost:5432/qwinai',
    ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false,
    max: 20,                    // Maximum connections
    min: 5,                     // Minimum connections
    idle: 10000,               // 10 seconds idle timeout
    acquire: 30000,            // 30 seconds acquire timeout
    evict: 1000,               // 1 second eviction timeout
    handleDisconnects: true,
    reconnect: true
});

// Enhanced Redis connection with error handling
let redisClient = null;
async function initializeRedis() {
    try {
        redisClient = redis.createClient({
            url: process.env.REDIS_URL || 'redis://localhost:6379',
            retry_strategy: (options) => {
                if (options.error && options.error.code === 'ECONNREFUSED') {
                    console.warn('⚠️ Redis server refused connection');
                    return new Error('Redis connection refused');
                }
                if (options.total_retry_time > 1000 * 60 * 60) {
                    console.error('❌ Redis retry time exhausted');
                    return new Error('Redis retry time exhausted');
                }
                if (options.attempt > 10) {
                    return undefined;
                }
                return Math.min(options.attempt * 100, 3000);
            }
        });
        
        redisClient.on('error', (err) => {
            console.error('❌ Redis error:', err);
            monitoring.trackError('REDIS_ERROR', err.message);
        });
        
        redisClient.on('connect', () => {
            console.log('✅ Redis connected successfully');
        });
        
        redisClient.on('ready', () => {
            console.log('✅ Redis ready for commands');
        });
        
        await redisClient.connect();
    } catch (error) {
        console.warn('⚠️ Redis initialization failed, running without cache:', error.message);
        redisClient = null;
    }
}

// Database initialization with comprehensive schema
async function initializeDatabase() {
    try {
        console.log('🗄️ Initializing database schema...');
        
        // Enhanced user_states table
        await db.query(`
            CREATE TABLE IF NOT EXISTS user_states (
                id SERIAL PRIMARY KEY,
                device_id VARCHAR(16) UNIQUE NOT NULL,
                device_fingerprint VARCHAR(128) NOT NULL,
                credits_consumed_today_chat INT DEFAULT 0,
                credits_consumed_today_image INT DEFAULT 0,
                has_active_subscription BOOLEAN DEFAULT FALSE,
                subscription_type VARCHAR(50),
                subscription_end_time BIGINT DEFAULT 0,
                tracking_date DATE NOT NULL DEFAULT CURRENT_DATE,
                app_version VARCHAR(20),
                last_activity TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                total_sessions INT DEFAULT 1,
                total_credits_used INT DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                
                -- Add constraints
                CONSTRAINT check_credits_positive CHECK (
                    credits_consumed_today_chat >= 0 AND 
                    credits_consumed_today_image >= 0
                ),
                CONSTRAINT check_valid_device_id CHECK (
                    LENGTH(device_id) = 16 AND device_id ~ '^[a-zA-Z0-9]+$'
                )
            )
        `);

        // Enhanced processed_files table with security features
        await db.query(`
            CREATE TABLE IF NOT EXISTS processed_files (
                id SERIAL PRIMARY KEY,
                file_hash VARCHAR(64) UNIQUE NOT NULL,
                original_name VARCHAR(255) NOT NULL,
                sanitized_name VARCHAR(255) NOT NULL,
                mime_type VARCHAR(100) NOT NULL,
                file_size BIGINT NOT NULL,
                total_tokens INT NOT NULL,
                chunk_count INT NOT NULL,
                processing_time_ms INT NOT NULL,
                model_used VARCHAR(50) NOT NULL,
                processed_content TEXT,
                content_preview TEXT,
                is_cached BOOLEAN DEFAULT TRUE,
                security_scanned BOOLEAN DEFAULT FALSE,
                processing_ip INET,
                processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                expires_at TIMESTAMP NOT NULL,
                access_count INT DEFAULT 0,
                last_accessed TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                
                -- Add constraints
                CONSTRAINT check_positive_values CHECK (
                    file_size > 0 AND 
                    total_tokens >= 0 AND 
                    chunk_count >= 0 AND
                    processing_time_ms >= 0
                )
            )
        `);

        // System monitoring table
        await db.query(`
            CREATE TABLE IF NOT EXISTS system_metrics (
                id SERIAL PRIMARY KEY,
                metric_type VARCHAR(50) NOT NULL,
                metric_value NUMERIC NOT NULL,
                metadata JSONB,
                recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        `);

        // Security events table
        await db.query(`
            CREATE TABLE IF NOT EXISTS security_events (
                id SERIAL PRIMARY KEY,
                event_type VARCHAR(50) NOT NULL,
                severity VARCHAR(20) NOT NULL DEFAULT 'info',
                source_ip INET,
                user_agent TEXT,
                request_path TEXT,
                event_details JSONB,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        `);

        // Create comprehensive indexes for performance
        await db.query(`
            CREATE INDEX IF NOT EXISTS idx_user_states_device_id ON user_states(device_id);
            CREATE INDEX IF NOT EXISTS idx_user_states_tracking_date ON user_states(tracking_date);
            CREATE INDEX IF NOT EXISTS idx_user_states_subscription ON user_states(has_active_subscription, subscription_end_time);
            CREATE INDEX IF NOT EXISTS idx_user_states_activity ON user_states(last_activity);
            
            CREATE INDEX IF NOT EXISTS idx_processed_files_hash ON processed_files(file_hash);
            CREATE INDEX IF NOT EXISTS idx_processed_files_expires ON processed_files(expires_at);
            CREATE INDEX IF NOT EXISTS idx_processed_files_mime_type ON processed_files(mime_type);
            CREATE INDEX IF NOT EXISTS idx_processed_files_processed_at ON processed_files(processed_at);
            
            CREATE INDEX IF NOT EXISTS idx_metrics_type_time ON system_metrics(metric_type, recorded_at);
            CREATE INDEX IF NOT EXISTS idx_metrics_time ON system_metrics(recorded_at);
            
            CREATE INDEX IF NOT EXISTS idx_security_events_type_time ON security_events(event_type, created_at);
            CREATE INDEX IF NOT EXISTS idx_security_events_severity ON security_events(severity, created_at);
            CREATE INDEX IF NOT EXISTS idx_security_events_ip ON security_events(source_ip, created_at);
        `);

        // Create stored procedures for common operations
        await db.query(`
            CREATE OR REPLACE FUNCTION update_user_last_activity()
            RETURNS TRIGGER AS $$
            BEGIN
                NEW.updated_at = CURRENT_TIMESTAMP;
                NEW.last_activity = CURRENT_TIMESTAMP;
                RETURN NEW;
            END;
            $$ language 'plpgsql';
            
            DROP TRIGGER IF EXISTS trigger_user_states_updated_at ON user_states;
            CREATE TRIGGER trigger_user_states_updated_at
                BEFORE UPDATE ON user_states
                FOR EACH ROW
                EXECUTE FUNCTION update_user_last_activity();
        `);

        console.log('✅ Database schema initialized successfully');
    } catch (error) {
        console.error('❌ Database initialization failed:', error);
        throw error;
    }
}

// Global middleware for enhanced request context
app.use((req, res, next) => {
    // Add services to request context
    req.db = db;
    req.redis = redisClient;
    req.monitoring = monitoring;
    req.secureFileProcessor = secureFileProcessor;
    req.universalTokenCounter = universalTokenCounter;
    
    // Add security context
    req.security = {
        ip: req.ip || req.connection.remoteAddress,
        userAgent: req.get('User-Agent') || 'Unknown',
        timestamp: Date.now(),
        requestId: crypto.randomUUID()
    };
    
    // Log request for monitoring
    console.log(`📥 ${req.method} ${req.path} from ${req.security.ip}`);
    
    next();
});

// Routes with enhanced security
app.use('/api/user-state', userStateRoutes);
app.use('/api/file-processor', secureFileRoutes);

// Enhanced health check endpoint
app.get('/health', async (req, res) => {
    try {
        const healthChecks = {
            timestamp: new Date().toISOString(),
            status: 'healthy',
            version: process.env.npm_package_version || '1.0.0',
            uptime: process.uptime(),
            environment: process.env.NODE_ENV || 'development'
        };
        
        // Database health
        try {
            const dbResult = await db.query('SELECT 1 as health_check');
            healthChecks.database = {
                status: 'connected',
                responseTime: Date.now() - req.security.timestamp
            };
        } catch (dbError) {
            healthChecks.database = {
                status: 'error',
                error: dbError.message
            };
            healthChecks.status = 'degraded';
        }
        
        // Redis health
        if (redisClient && redisClient.isReady) {
            try {
                await redisClient.ping();
                healthChecks.redis = { status: 'connected' };
            } catch (redisError) {
                healthChecks.redis = { 
                    status: 'error', 
                    error: redisError.message 
                };
                healthChecks.status = 'degraded';
            }
        } else {
            healthChecks.redis = { status: 'disconnected' };
        }
        
        // Service health
        healthChecks.services = {
            fileProcessor: secureFileProcessor ? 'ready' : 'not_initialized',
            tokenCounter: universalTokenCounter ? 'ready' : 'not_initialized',
            monitoring: monitoring ? 'active' : 'inactive'
        };
        
        // Memory health
        const memory = process.memoryUsage();
        healthChecks.memory = {
            heapUsed: `${Math.round(memory.heapUsed / 1024 / 1024)}MB`,
            heapTotal: `${Math.round(memory.heapTotal / 1024 / 1024)}MB`,
            rss: `${Math.round(memory.rss / 1024 / 1024)}MB`,
            external: `${Math.round(memory.external / 1024 / 1024)}MB`
        };
        
        // Processing status
        if (secureFileProcessor) {
            healthChecks.processing = secureFileProcessor.getProcessingStats();
        }
        
        const statusCode = healthChecks.status === 'healthy' ? 200 : 503;
        res.status(statusCode).json(healthChecks);
        
    } catch (error) {
        monitoring.trackError('HEALTH_CHECK_ERROR', error.message, 'critical');
        res.status(503).json({
            status: 'error',
            error: error.message,
            timestamp: new Date().toISOString()
        });
    }
});

// Comprehensive metrics endpoint
app.get('/metrics', (req, res) => {
    try {
        const report = monitoring.getMonitoringReport();
        
        // Add additional system metrics
        report.system = {
            nodeVersion: process.version,
            platform: process.platform,
            arch: process.arch,
            pid: process.pid,
            uptime: process.uptime()
        };
        
        // Add service-specific metrics
        if (universalTokenCounter) {
            report.tokenCounter = universalTokenCounter.getSystemStats();
        }
        
        if (secureFileProcessor) {
            report.fileProcessor = secureFileProcessor.getProcessingStats();
        }
        
        res.json(report);
    } catch (error) {
        monitoring.trackError('METRICS_ERROR', error.message);
        res.status(500).json({
            error: 'Failed to generate metrics',
            message: error.message
        });
    }
});

// AI models information endpoint
app.get('/api/models', async (req, res) => {
    try {
        if (!universalTokenCounter) {
            return res.status(503).json({
                error: 'Token counter service not available'
            });
        }
        
        const supportedModels = await universalTokenCounter.getSupportedModels();
        
        res.json({
            success: true,
            totalModels: Object.values(supportedModels).flat().length,
            categories: supportedModels,
            lastUpdated: new Date().toISOString()
        });
        
    } catch (error) {
        monitoring.trackError('MODELS_ENDPOINT_ERROR', error.message);
        res.status(500).json({
            error: 'Failed to retrieve model information',
            message: error.message
        });
    }
});

// Enhanced error handling middleware
app.use((error, req, res, next) => {
    const errorId = crypto.randomUUID();
    
    // Log error with context
    monitoring.trackError('UNHANDLED_ERROR', error.message, 'error', {
        errorId,
        stack: error.stack,
        path: req.path,
        method: req.method,
        ip: req.security?.ip,
        userAgent: req.security?.userAgent
    });
    
    // Don't leak sensitive information in production
    const isDevelopment = process.env.NODE_ENV === 'development';
    
    res.status(error.status || 500).json({
        error: 'Internal server error',
        errorId,
        message: isDevelopment ? error.message : 'Something went wrong',
        timestamp: new Date().toISOString(),
        ...(isDevelopment && { stack: error.stack })
    });
});

// 404 handler
app.use('*', (req, res) => {
    monitoring.trackSecurityEvent('NOT_FOUND_REQUEST', {
        path: req.originalUrl,
        method: req.method,
        ip: req.security?.ip,
        userAgent: req.security?.userAgent
    });
    
    res.status(404).json({
        error: 'Endpoint not found',
        path: req.originalUrl,
        message: 'The requested resource does not exist',
        timestamp: new Date().toISOString()
    });
});

// Production cron jobs
if (process.env.NODE_ENV === 'production') {
    // Cleanup expired processed files every hour
    cron.schedule('0 * * * *', async () => {
        try {
            const result = await db.query('DELETE FROM processed_files WHERE expires_at < NOW()');
            if (result.rowCount > 0) {
                console.log(`🧹 Cleaned up ${result.rowCount} expired processed files`);
            }
        } catch (error) {
            monitoring.trackError('CLEANUP_ERROR', error.message);
        }
    });

    // Reset daily credits at midnight UTC
    cron.schedule('0 0 * * *', async () => {
        try {
            const today = new Date().toISOString().split('T')[0];
            const result = await db.query(`
                UPDATE user_states 
                SET credits_consumed_today_chat = 0,
                    credits_consumed_today_image = 0,
                    tracking_date = $1
                WHERE tracking_date < $1
            `, [today]);
            console.log(`🔄 Reset daily credits for ${result.rowCount} users`);
        } catch (error) {
            monitoring.trackError('CREDIT_RESET_ERROR', error.message, 'critical');
        }
    });

    // Update token counter pricing daily
    cron.schedule('0 2 * * *', async () => {
        try {
            if (universalTokenCounter && universalTokenCounter.shouldUpdatePricing()) {
                await universalTokenCounter.updatePricing();
                console.log('💰 Token pricing updated');
            }
        } catch (error) {
            monitoring.trackError('PRICING_UPDATE_ERROR', error.message);
        }
    });

    // Weekly security event summary
    cron.schedule('0 0 * * 0', async () => {
        try {
            const weekAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
            const result = await db.query(`
                SELECT event_type, severity, COUNT(*) as count
                FROM security_events 
                WHERE created_at > $1
                GROUP BY event_type, severity
                ORDER BY count DESC
            `, [weekAgo]);
            
            console.log('🔒 Weekly security summary:', result.rows);
        } catch (error) {
            monitoring.trackError('SECURITY_SUMMARY_ERROR', error.message);
        }
    });
}

// Graceful shutdown handling
const gracefulShutdown = async (signal) => {
    console.log(`\n🛑 ${signal} received, shutting down gracefully...`);
    
    try {
        // Close database connections
        await db.end();
        console.log('✅ Database connections closed');
        
        // Close Redis connection
        if (redisClient) {
            await redisClient.quit();
            console.log('✅ Redis connection closed');
        }
        
        // Shutdown secure file processor
        if (secureFileProcessor) {
            await secureFileProcessor.shutdown();
            console.log('✅ Secure file processor shutdown');
        }
        
        console.log('✅ Graceful shutdown completed');
        process.exit(0);
        
    } catch (error) {
        console.error('❌ Error during graceful shutdown:', error);
        process.exit(1);
    }
};

// Handle shutdown signals
process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT', () => gracefulShutdown('SIGINT'));

// Handle uncaught exceptions
process.on('uncaughtException', (error) => {
    console.error('🚨 Uncaught Exception:', error);
    monitoring.trackError('UNCAUGHT_EXCEPTION', error.message, 'critical');
    gracefulShutdown('UNCAUGHT_EXCEPTION');
});

process.on('unhandledRejection', (reason, promise) => {
    console.error('🚨 Unhandled Rejection at:', promise, 'reason:', reason);
    monitoring.trackError('UNHANDLED_REJECTION', String(reason), 'critical');
});

// Start server
async function startServer() {
    try {
        // Initialize all services
        await initializeRedis();
        await initializeDatabase();
        await initializeServices();
        
        // Start HTTP server
        const server = app.listen(port, () => {
            console.log('\n🚀 QwinAI Production Server Started Successfully!');
            console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
            console.log(`🌐 Server running on port: ${port}`);
            console.log(`📊 Environment: ${process.env.NODE_ENV || 'development'}`);
            console.log(`🔗 Health check: http://localhost:${port}/health`);
            console.log(`📈 Metrics: http://localhost:${port}/metrics`);
            console.log(`🤖 AI Models: http://localhost:${port}/api/models`);
            console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
            console.log('🛡️ Security Features:');
            console.log('   ✅ Multi-user concurrency support');
            console.log('   ✅ Advanced rate limiting');
            console.log('   ✅ File security scanning');
            console.log('   ✅ Privacy protection');
            console.log('   ✅ Real-time monitoring');
            console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
            console.log('📄 File Processing:');
            console.log('   ✅ PDF, DOCX, XLSX, PPTX, TXT support');
            console.log('   ✅ Universal AI model token counting');
            console.log('   ✅ Intelligent document chunking');
            console.log('   ✅ Cost estimation for all models');
            console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');
        });
        
        // Handle server errors
        server.on('error', (error) => {
            console.error('❌ Server error:', error);
            monitoring.trackError('SERVER_ERROR', error.message, 'critical');
        });
        
        return server;
        
    } catch (error) {
        console.error('❌ Failed to start server:', error);
        monitoring.trackError('STARTUP_ERROR', error.message, 'critical');
        process.exit(1);
    }
}

// Start the server
startServer().catch(error => {
    console.error('❌ Fatal startup error:', error);
    process.exit(1);
});

module.exports = app;