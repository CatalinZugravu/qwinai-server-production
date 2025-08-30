const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const rateLimit = require('express-rate-limit');
const { Pool } = require('pg');
const redis = require('redis');
const cron = require('node-cron');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

// Import routes
const userStateRoutes = require('./routes/userState');
const fileProcessorRoutes = require('./routes/fileProcessor');

const app = express();
const port = process.env.PORT || 3000;

// Trust Railway's proxy for accurate IP addresses (MUST be set before any middleware)
app.set('trust proxy', true);

// Ensure uploads directory exists
if (!fs.existsSync('uploads')) {
    fs.mkdirSync('uploads');
}

// Security middleware
app.use(helmet());
app.use(cors({
    origin: process.env.ALLOWED_ORIGINS?.split(',') || '*',
    credentials: true
}));

// Rate limiting with proper proxy configuration
const limiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 100, // limit each IP to 100 requests per windowMs
    message: 'Too many requests from this IP',
    standardHeaders: true,
    legacyHeaders: false,
    // Trust the proxy headers
    trustProxy: true
});
app.use(limiter);

// Stricter rate limiting for file processing
const fileProcessingLimiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 10, // Only 10 file uploads per 15 minutes per IP
    message: 'Too many file uploads, please try again later.',
    standardHeaders: true,
    legacyHeaders: false,
    trustProxy: true
});

// Body parsing
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// Database connection (PostgreSQL)
const db = new Pool({
    connectionString: process.env.DATABASE_URL || 'postgresql://localhost:5432/qwinai',
    ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false,
    max: 20,        // Maximum connections
    min: 5,         // Minimum connections
    idle: 10000,    // 10 seconds
    acquire: 60000, // 60 seconds
    evict: 1000     // 1 second
});

// Redis connection
let redisClient;
try {
    redisClient = redis.createClient({
        url: process.env.REDIS_URL || 'redis://localhost:6379'
    });
    
    redisClient.on('error', (err) => {
        console.error('Redis connection error:', err);
    });
    
    redisClient.connect().then(() => {
        console.log('✅ Redis connected successfully');
    }).catch((err) => {
        console.error('❌ Redis connection failed:', err);
    });
} catch (error) {
    console.error('❌ Redis setup failed:', error);
}

// Initialize database tables
async function initializeDatabase() {
    try {
        // Create user_states table with tracking_date instead of current_date
        await db.query(`
            CREATE TABLE IF NOT EXISTS user_states (
                id SERIAL PRIMARY KEY,
                device_id VARCHAR(16) UNIQUE NOT NULL,
                device_fingerprint VARCHAR(64) NOT NULL,
                credits_consumed_today_chat INT DEFAULT 0,
                credits_consumed_today_image INT DEFAULT 0,
                has_active_subscription BOOLEAN DEFAULT FALSE,
                subscription_type VARCHAR(50),
                subscription_end_time BIGINT DEFAULT 0,
                tracking_date DATE NOT NULL DEFAULT CURRENT_DATE,
                app_version VARCHAR(10),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        `);

        // Create processed_files table for caching
        await db.query(`
            CREATE TABLE IF NOT EXISTS processed_files (
                id SERIAL PRIMARY KEY,
                file_hash VARCHAR(64) UNIQUE NOT NULL,
                original_name VARCHAR(255) NOT NULL,
                mime_type VARCHAR(100) NOT NULL,
                file_size BIGINT NOT NULL,
                total_tokens INT NOT NULL,
                chunk_count INT NOT NULL,
                processed_content TEXT,
                processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                expires_at TIMESTAMP NOT NULL
            )
        `);

        // Create indexes
        await db.query('CREATE INDEX IF NOT EXISTS idx_user_states_device_id ON user_states(device_id)');
        await db.query('CREATE INDEX IF NOT EXISTS idx_user_states_tracking_date ON user_states(tracking_date)');
        await db.query('CREATE INDEX IF NOT EXISTS idx_processed_files_hash ON processed_files(file_hash)');
        await db.query('CREATE INDEX IF NOT EXISTS idx_processed_files_expires ON processed_files(expires_at)');

        console.log('✅ Database tables initialized');
    } catch (error) {
        console.error('❌ Database initialization failed:', error);
        throw error;
    }
}

// Global middleware to add db and redis to requests
app.use((req, res, next) => {
    req.db = db;
    req.redis = redisClient;
    next();
});

// Routes
app.use('/api/user-state', userStateRoutes);
app.use('/api/file-processor', fileProcessingLimiter, fileProcessorRoutes);

// Health check
app.get('/health', async (req, res) => {
    try {
        // Test database connection
        const dbResult = await db.query('SELECT 1');
        
        // Test Redis connection
        let redisStatus = 'disconnected';
        try {
            if (redisClient && redisClient.isReady) {
                await redisClient.ping();
                redisStatus = 'connected';
            }
        } catch (redisError) {
            redisStatus = 'error: ' + redisError.message;
        }

        // Memory usage
        const memUsage = process.memoryUsage();

        res.json({
            status: 'OK',
            timestamp: new Date().toISOString(),
            uptime: process.uptime(),
            database: dbResult.rowCount >= 0 ? 'connected' : 'disconnected',
            redis: redisStatus,
            memory: {
                rss: `${Math.round(memUsage.rss / 1024 / 1024)}MB`,
                heapUsed: `${Math.round(memUsage.heapUsed / 1024 / 1024)}MB`,
                heapTotal: `${Math.round(memUsage.heapTotal / 1024 / 1024)}MB`
            },
            environment: process.env.NODE_ENV || 'development'
        });
    } catch (error) {
        res.status(503).json({
            status: 'ERROR',
            error: error.message,
            timestamp: new Date().toISOString()
        });
    }
});

// Performance metrics
let requestCount = 0;
let responseTimeSum = 0;
let errorCount = 0;

app.use((req, res, next) => {
    const start = Date.now();
    requestCount++;
    
    res.on('finish', () => {
        const duration = Date.now() - start;
        responseTimeSum += duration;
        
        if (res.statusCode >= 400) {
            errorCount++;
        }
    });
    
    next();
});

app.get('/metrics', (req, res) => {
    res.json({
        requests: {
            total: requestCount,
            errors: errorCount,
            errorRate: requestCount > 0 ? (errorCount / requestCount * 100).toFixed(2) + '%' : '0%'
        },
        performance: {
            avgResponseTime: requestCount > 0 ? (responseTimeSum / requestCount).toFixed(2) + 'ms' : '0ms',
            uptime: process.uptime() + 's'
        },
        timestamp: new Date().toISOString()
    });
});

// Error handling
app.use((err, req, res, next) => {
    console.error('Server error:', err);
    res.status(500).json({
        error: 'Internal server error',
        message: process.env.NODE_ENV === 'development' ? err.message : 'Something went wrong',
        timestamp: new Date().toISOString()
    });
});

// 404 handler
app.use('*', (req, res) => {
    res.status(404).json({
        error: 'Endpoint not found',
        path: req.originalUrl,
        timestamp: new Date().toISOString()
    });
});

// Cleanup tasks
cron.schedule('0 * * * *', async () => {
    try {
        // Clean up expired processed files
        const result = await db.query('DELETE FROM processed_files WHERE expires_at < NOW()');
        if (result.rowCount > 0) {
            console.log(`🧹 Cleaned up ${result.rowCount} expired processed files`);
        }

        // Clean up old temporary files
        const uploadsDir = path.join(__dirname, 'uploads');
        if (fs.existsSync(uploadsDir)) {
            const files = fs.readdirSync(uploadsDir);
            const now = Date.now();
            
            files.forEach(file => {
                const filePath = path.join(uploadsDir, file);
                const stats = fs.statSync(filePath);
                const ageInMs = now - stats.mtime.getTime();
                
                // Delete files older than 1 hour
                if (ageInMs > 60 * 60 * 1000) {
                    fs.unlinkSync(filePath);
                    console.log(`🗑️ Deleted old temp file: ${file}`);
                }
            });
        }
    } catch (error) {
        console.error('Cleanup error:', error);
    }
});

// Daily credit reset at midnight UTC
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
        console.error('Credit reset error:', error);
    }
});

// Graceful shutdown
process.on('SIGTERM', async () => {
    console.log('SIGTERM received, shutting down gracefully');
    
    try {
        await db.end();
        console.log('Database connections closed');
    } catch (error) {
        console.error('Error closing database:', error);
    }
    
    try {
        if (redisClient) {
            await redisClient.quit();
            console.log('Redis connection closed');
        }
    } catch (error) {
        console.error('Error closing Redis:', error);
    }
    
    process.exit(0);
});

process.on('SIGINT', async () => {
    console.log('SIGINT received, shutting down gracefully');
    
    try {
        await db.end();
        if (redisClient) {
            await redisClient.quit();
        }
    } catch (error) {
        console.error('Shutdown error:', error);
    }
    
    process.exit(0);
});

// Start server
async function startServer() {
    try {
        await initializeDatabase();
        
        app.listen(port, () => {
            console.log('🚀 QwinAI Server running on port', port);
            console.log('📊 Environment:', process.env.NODE_ENV || 'development');
            console.log('🔗 Health check:', `http://localhost:${port}/health`);
            console.log('📈 Metrics:', `http://localhost:${port}/metrics`);
            console.log('');
            console.log('📄 File Processing API: /api/file-processor/process');
            console.log('👤 User State API: /api/user-state/:deviceId');
        });
    } catch (error) {
        console.error('❌ Failed to start server:', error);
        process.exit(1);
    }
}

startServer();

module.exports = app;