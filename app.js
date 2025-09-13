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
const userStateRoutes = require('./routes/userState');          // Existing sophisticated system
const androidCompatRoutes = require('./routes/androidCompatibility'); // New Android endpoints  
const fileProcessorRoutes = require('./routes/fileProcessor');

const app = express();
const port = process.env.PORT || 3000;

// Trust Railway's proxy for accurate IP addresses (MUST be set before any middleware)
app.set('trust proxy', true);

// Ensure uploads directory exists
if (!fs.existsSync('uploads')) {
    fs.mkdirSync('uploads');
}

// ==================== DATABASE CONNECTION ====================
// Initialize database connection
let pool = null;

function initializeDatabase() {
    if (pool) {
        return pool;
    }
    
    // Database configuration from environment variables
    const dbConfig = {
        user: process.env.DB_USER || 'postgres',
        host: process.env.DB_HOST || 'localhost',
        database: process.env.DB_NAME || 'qwinai',
        password: process.env.DB_PASSWORD || 'password',
        port: process.env.DB_PORT || 5432,
        ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false,
        max: 20, // Maximum number of clients in pool
        idleTimeoutMillis: 30000,
        connectionTimeoutMillis: 2000,
    };
    
    pool = new Pool(dbConfig);
    
    // Handle pool errors
    pool.on('error', (err, client) => {
        console.error('❌ Unexpected error on idle client', err);
        process.exit(-1);
    });
    
    // Test connection
    pool.connect((err, client, release) => {
        if (err) {
            console.error('❌ Error acquiring client', err.stack);
            return;
        }
        
        client.query('SELECT NOW()', (err, result) => {
            release();
            if (err) {
                console.error('❌ Error executing test query', err.stack);
                return;
            }
            console.log('✅ Database connected successfully at', result.rows[0].now);
        });
    });
    
    return pool;
}

// Database middleware
function databaseMiddleware(req, res, next) {
    if (!pool) {
        pool = initializeDatabase();
    }
    req.db = pool;
    next();
}

// ==================== SECURITY MIDDLEWARE ====================
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

// ==================== BODY PARSING ====================
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// ==================== DATABASE MIDDLEWARE ====================
app.use(databaseMiddleware);

// ==================== HEALTH CHECK ====================
app.get('/health', (req, res) => {
    res.json({
        status: 'healthy',
        timestamp: new Date().toISOString(),
        uptime: process.uptime(),
        memory: process.memoryUsage(),
        database: pool ? 'connected' : 'disconnected'
    });
});

app.get('/', (req, res) => {
    res.json({
        message: 'QwinAI Production Server',
        version: '2.0.0',
        endpoints: {
            health: '/health',
            userState: '/api/user-state/*',
            androidCompat: '/api/user-state/get and /api/user-state/update',
            fileProcessor: '/api/file/*'
        },
        database: pool ? 'connected' : 'disconnected'
    });
});

// ==================== ROUTE MOUNTING ====================

// Android App Compatibility Layer (CRITICAL - must come before general user state routes)
console.log('🔗 Mounting Android compatibility routes at /api/user-state/*');
app.use('/api/user-state', androidCompatRoutes);

// Existing sophisticated user state system
console.log('🔗 Mounting existing user state routes at /api/userstate/*');
app.use('/api/userstate', userStateRoutes);

// File processing routes
console.log('🔗 Mounting file processor routes at /api/file/*');
app.use('/api/file', fileProcessingLimiter, fileProcessorRoutes);

// ==================== ERROR HANDLING ====================
app.use((err, req, res, next) => {
    console.error('❌ Unhandled error:', err);
    res.status(500).json({
        error: 'Internal server error',
        message: process.env.NODE_ENV === 'production' ? 'Something went wrong' : err.message
    });
});

// 404 handler
app.use('*', (req, res) => {
    console.log(`❌ 404 - Route not found: ${req.method} ${req.originalUrl}`);
    res.status(404).json({
        error: 'Route not found',
        method: req.method,
        path: req.originalUrl,
        availableEndpoints: [
            'GET /health',
            'GET /',
            'POST /api/user-state/get',      // Android app endpoint
            'POST /api/user-state/update',   // Android app endpoint
            'POST /api/userstate/state',     // Existing system
            'POST /api/userstate/consume',   // Existing system
            'POST /api/file/upload'          // File processing
        ]
    });
});

// ==================== GRACEFUL SHUTDOWN ====================
process.on('SIGTERM', async () => {
    console.log('🛑 SIGTERM received, shutting down gracefully');
    
    if (pool) {
        await pool.end();
        console.log('✅ Database pool closed');
    }
    
    process.exit(0);
});

process.on('SIGINT', async () => {
    console.log('🛑 SIGINT received, shutting down gracefully');
    
    if (pool) {
        await pool.end();
        console.log('✅ Database pool closed');
    }
    
    process.exit(0);
});

// ==================== SERVER STARTUP ====================
app.listen(port, () => {
    console.log('');
    console.log('🚀 QwinAI Production Server Started');
    console.log('====================================');
    console.log(`📡 Server running on port ${port}`);
    console.log(`🌍 Environment: ${process.env.NODE_ENV || 'development'}`);
    console.log(`🗄️  Database: ${process.env.DB_HOST || 'localhost'}:${process.env.DB_PORT || 5432}`);
    console.log('');
    console.log('📋 Available Endpoints:');
    console.log('   GET  /health                    - Health check');
    console.log('   GET  /                          - API info');
    console.log('   POST /api/user-state/get        - Android app: Get user state');
    console.log('   POST /api/user-state/update     - Android app: Update user state');
    console.log('   POST /api/userstate/state       - Existing: Get user state');
    console.log('   POST /api/userstate/consume     - Existing: Consume credits');
    console.log('   POST /api/userstate/ad-reward   - Existing: Ad rewards');
    console.log('   POST /api/file/upload           - File processing');
    console.log('');
    console.log('✅ Server ready to handle requests!');
    console.log('');
});

module.exports = app;
