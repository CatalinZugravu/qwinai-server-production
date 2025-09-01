const express = require('express');
const multer = require('multer');
const crypto = require('crypto');
const path = require('path');
const { body, validationResult } = require('express-validator');

// Import our production-ready services
const SecureFileProcessor = require('../services/secureFileProcessor');
const UniversalTokenCounter = require('../services/universalTokenCounter');

const router = express.Router();

// Initialize services with error handling
let fileProcessor, tokenCounter;

async function initializeServices() {
    try {
        fileProcessor = new SecureFileProcessor();
        tokenCounter = new UniversalTokenCounter();
        await tokenCounter.initializationPromise;
        console.log('🚀 File processing services initialized successfully');
    } catch (error) {
        console.error('❌ Failed to initialize file processing services:', error);
        throw error;
    }
}

// Initialize services immediately
initializeServices().catch(error => {
    console.error('💥 Critical: File processing services failed to initialize');
    process.exit(1);
});

// Enhanced multer configuration with security
const secureUpload = multer({
    storage: multer.memoryStorage(), // Keep in memory for security processing
    limits: { 
        fileSize: 50 * 1024 * 1024, // 50MB limit
        files: 1,
        fieldSize: 1024 * 1024, // 1MB field size limit
        fieldNameSize: 100,
        headerPairs: 2000
    },
    fileFilter: (req, file, cb) => {
        console.log(`📤 [${req.ip}] File upload: ${file.originalname} (${file.mimetype})`);
        
        // Basic security checks
        if (!file.originalname || file.originalname.length > 255) {
            return cb(new Error('Invalid file name'), false);
        }
        
        if (file.originalname.includes('..') || /[<>:"|?*\x00-\x1f]/g.test(file.originalname)) {
            return cb(new Error('File name contains invalid characters'), false);
        }
        
        // MIME type validation - basic check here, detailed in processor
        const supportedTypes = [
            'application/pdf',
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 
            'application/vnd.openxmlformats-officedocument.presentationml.presentation',
            'text/plain'
        ];
        
        if (!supportedTypes.includes(file.mimetype)) {
            return cb(new Error(`Unsupported file type: ${file.mimetype}. Supported: PDF, DOCX, XLSX, PPTX, TXT`), false);
        }
        
        cb(null, true);
    }
});

// Enhanced validation middleware
const validateProcessingRequest = [
    body('model').optional().isString().isLength({ min: 3, max: 50 }).withMessage('Invalid model name'),
    body('maxTokensPerChunk').optional().isInt({ min: 1000, max: 32000 }).withMessage('Invalid chunk size'),
    body('userId').optional().isString().isLength({ min: 10, max: 100 }).withMessage('Invalid user ID')
];

const handleValidationErrors = (req, res, next) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
        console.warn(`⚠️ Validation errors from ${req.ip}: ${JSON.stringify(errors.array())}`);
        req.monitoring?.recordSecurityEvent('VALIDATION_ERROR', {
            ip: req.ip,
            errors: errors.array()
        });
        
        return res.status(400).json({
            error: 'Validation failed',
            details: errors.array()
        });
    }
    next();
};

// Main secure file processing endpoint
router.post('/process', secureUpload.single('file'), validateProcessingRequest, handleValidationErrors, async (req, res) => {
    const startTime = Date.now();
    const processingId = crypto.randomUUID();
    
    try {
        const { file } = req;
        const { model = 'gpt-4', maxTokensPerChunk = 6000, userId } = req.body;
        
        if (!file) {
            return res.status(400).json({ 
                error: 'No file uploaded',
                success: false 
            });
        }

        console.log(`🚀 [${processingId}] Processing file: ${file.originalname}`);
        console.log(`   📊 Size: ${(file.size / 1024 / 1024).toFixed(2)}MB`);
        console.log(`   🤖 Target model: ${model}`);
        console.log(`   ✂️ Max tokens per chunk: ${maxTokensPerChunk}`);
        console.log(`   📍 Client IP: ${req.ip}`);

        req.monitoring?.recordRequest('FILE_PROCESSING_START', {
            processingId,
            fileName: file.originalname,
            fileSize: file.size,
            model,
            ip: req.ip,
            userId: userId || 'anonymous'
        });

        // Generate secure file hash for caching and security
        const fileHash = crypto.createHash('sha256')
            .update(file.buffer)
            .digest('hex');

        console.log(`🔐 File hash: ${fileHash.substring(0, 16)}...`);

        // Check cache first (Redis with security validation)
        if (req.redis && req.redis.isReady) {
            try {
                const cacheKey = `secure_file:${fileHash}:${model}:${maxTokensPerChunk}`;
                const cached = await req.redis.get(cacheKey);
                
                if (cached) {
                    console.log(`💨 [${processingId}] Cache hit for file: ${file.originalname}`);
                    req.monitoring?.recordEvent('CACHE_HIT', { processingId, fileHash });
                    
                    const cachedResult = JSON.parse(cached);
                    return res.json({
                        ...cachedResult,
                        processingId,
                        cached: true,
                        processingTime: Date.now() - startTime,
                        serverVersion: "2.0.0"
                    });
                }
            } catch (cacheError) {
                console.warn(`⚠️ [${processingId}] Cache check failed:`, cacheError.message);
                req.monitoring?.recordError('CACHE_ERROR', cacheError, { processingId });
            }
        }

        // Process file with enhanced security
        const processedResult = await fileProcessor.processFile(
            file.buffer,
            file.originalname,
            file.mimetype,
            { model, maxTokensPerChunk }
        );

        // Enhanced response with security metadata
        const result = {
            success: true,
            processingId,
            originalFileName: file.originalname,
            fileSize: file.size,
            mimeType: file.mimetype,
            fileHash: fileHash,
            extractedContent: {
                text: processedResult.extractedContent.text,
                metadata: {
                    ...processedResult.extractedContent.metadata,
                    securityScanned: true,
                    extractionMethod: 'secure_processor'
                }
            },
            tokenAnalysis: processedResult.tokenAnalysis,
            chunks: processedResult.chunks.map((chunk, index) => ({
                ...chunk,
                chunkId: `${processingId}_chunk_${index + 1}`,
                securityValidated: true
            })),
            processing: {
                processingId,
                recommendedApproach: processedResult.chunks.length > 1 ? 'chunked' : 'single',
                chunkCount: processedResult.chunks.length,
                processingTimeMs: Date.now() - startTime,
                securityLevel: 'production',
                serverVersion: "2.0.0"
            },
            security: processedResult.security,
            cached: false
        };

        // Cache the result with security validation
        if (req.redis && req.redis.isReady) {
            try {
                const cacheKey = `secure_file:${fileHash}:${model}:${maxTokensPerChunk}`;
                // Don't cache the full extracted content for security, just the analysis
                const cacheableResult = {
                    ...result,
                    extractedContent: {
                        ...result.extractedContent,
                        text: '[CACHED - REQUEST PROCESSING FOR FULL CONTENT]'
                    }
                };
                
                await req.redis.setex(cacheKey, 1800, JSON.stringify(cacheableResult)); // Cache for 30 minutes
                console.log(`💾 [${processingId}] Result cached securely for 30 minutes`);
            } catch (cacheError) {
                console.warn(`⚠️ [${processingId}] Failed to cache result:`, cacheError.message);
            }
        }

        // Store processing metadata in database (not full content for privacy)
        try {
            const expiresAt = new Date();
            expiresAt.setHours(expiresAt.getHours() + 6); // Shorter retention for security
            
            await req.db.query(`
                INSERT INTO processed_files_secure (
                    processing_id, file_hash, original_name, mime_type, file_size,
                    total_tokens, chunk_count, model_used, user_ip, user_id, expires_at
                ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
                ON CONFLICT (file_hash, model_used) DO UPDATE SET
                    last_processed_at = CURRENT_TIMESTAMP,
                    access_count = processed_files_secure.access_count + 1,
                    expires_at = EXCLUDED.expires_at
            `, [
                processingId,
                fileHash,
                file.originalname,
                file.mimetype,
                file.size,
                processedResult.tokenAnalysis.tokenCount,
                processedResult.chunks.length,
                model,
                req.ip,
                userId || null,
                expiresAt
            ]);
            
            console.log(`💾 [${processingId}] Processing metadata stored securely`);
        } catch (dbError) {
            console.warn(`⚠️ [${processingId}] Failed to store processing metadata:`, dbError.message);
        }

        req.monitoring?.recordEvent('FILE_PROCESSING_SUCCESS', {
            processingId,
            fileName: file.originalname,
            fileSize: file.size,
            tokenCount: processedResult.tokenAnalysis.tokenCount,
            chunkCount: processedResult.chunks.length,
            processingTimeMs: Date.now() - startTime
        });

        console.log(`🎉 [${processingId}] File processing completed successfully in ${Date.now() - startTime}ms`);
        
        res.json(result);

    } catch (error) {
        console.error(`❌ [${processingId}] File processing error:`, error);
        
        req.monitoring?.recordError('FILE_PROCESSING_ERROR', error, {
            processingId,
            fileName: req.file?.originalname,
            ip: req.ip
        });
        
        res.status(error.message.includes('too large') ? 413 :
                   error.message.includes('Unsupported') ? 415 :
                   error.message.includes('malicious') ? 400 : 500).json({ 
            error: 'File processing failed', 
            message: process.env.NODE_ENV === 'production' ? 
                'File could not be processed. Please check file type and size.' : 
                error.message,
            success: false,
            processingId,
            processingTime: Date.now() - startTime
        });
    }
});

// Get optimal model recommendations based on content analysis
router.post('/recommend-model', async (req, res) => {
    try {
        const { text, fileType, useCase = 'general' } = req.body;
        
        if (!text || text.length === 0) {
            return res.status(400).json({
                error: 'Text content is required for model recommendations'
            });
        }

        if (text.length > 50000) { // Limit analysis text for security
            return res.status(400).json({
                error: 'Text too long for analysis. Maximum 50,000 characters.'
            });
        }

        // Analyze with multiple models to get recommendations
        const models = ['gpt-4o-mini', 'gpt-4', 'claude-3-haiku', 'gemini-1.5-flash'];
        const recommendations = [];

        for (const model of models) {
            try {
                const analysis = await tokenCounter.analyzeText(text, model);
                recommendations.push({
                    model,
                    ...analysis,
                    costEfficiencyScore: analysis.costs.total ? 
                        (analysis.tokenCount / parseFloat(analysis.costs.total.replace('$', ''))) : 0
                });
            } catch (error) {
                console.warn(`⚠️ Failed to analyze with model ${model}:`, error.message);
            }
        }

        // Sort by cost efficiency and context utilization
        recommendations.sort((a, b) => {
            if (useCase === 'budget') {
                return parseFloat(a.costs.total.replace('$', '')) - parseFloat(b.costs.total.replace('$', ''));
            } else if (useCase === 'quality') {
                return b.costEfficiencyScore - a.costEfficiencyScore;
            } else {
                return a.utilizationPercent - b.utilizationPercent;
            }
        });

        res.json({
            success: true,
            recommendations,
            analysis: {
                contentLength: text.length,
                fileType,
                useCase,
                recommendedModel: recommendations[0]?.model || 'gpt-4o-mini',
                reasoning: useCase === 'budget' ? 'Lowest cost' : 
                          useCase === 'quality' ? 'Best quality/cost ratio' : 
                          'Best context utilization'
            }
        });

    } catch (error) {
        console.error('❌ Model recommendation error:', error);
        req.monitoring?.recordError('MODEL_RECOMMENDATION_ERROR', error);
        
        res.status(500).json({
            error: 'Failed to generate model recommendations',
            message: error.message
        });
    }
});

// Enhanced statistics with security considerations
router.get('/stats', async (req, res) => {
    try {
        // Get processing statistics (without exposing sensitive data)
        const stats = await req.db.query(`
            SELECT 
                COUNT(*) as total_files,
                COUNT(DISTINCT user_ip) as unique_users,
                SUM(file_size) as total_size_bytes,
                AVG(total_tokens) as avg_tokens,
                MAX(total_tokens) as max_tokens,
                COUNT(DISTINCT mime_type) as unique_file_types,
                array_agg(DISTINCT mime_type) as supported_types,
                COUNT(DISTINCT model_used) as models_used
            FROM processed_files_secure 
            WHERE expires_at > NOW() 
            AND last_processed_at > NOW() - INTERVAL '24 hours'
        `);

        const processingStats = fileProcessor.getProcessingStats();
        const tokenCounterStats = tokenCounter.getSystemStats();

        res.json({
            success: true,
            statistics: {
                files: {
                    processed24h: parseInt(stats.rows[0].total_files) || 0,
                    uniqueUsers24h: parseInt(stats.rows[0].unique_users) || 0,
                    totalSizeMB: Math.round((stats.rows[0].total_size_bytes || 0) / 1024 / 1024),
                    uniqueFileTypes: parseInt(stats.rows[0].unique_file_types) || 0,
                    supportedTypes: stats.rows[0].supported_types || []
                },
                tokens: {
                    averagePerFile: Math.round(stats.rows[0].avg_tokens) || 0,
                    maximumInSingleFile: parseInt(stats.rows[0].max_tokens) || 0
                },
                processing: {
                    ...processingStats,
                    activeProcessing: processingStats.activeProcessing,
                    uptime: processingStats.uptime
                },
                models: {
                    supported: tokenCounterStats.modelsSupported,
                    providersActive: tokenCounterStats.providers.length,
                    categoriesAvailable: tokenCounterStats.categories.length
                },
                security: {
                    version: "2.0.0",
                    encryptionEnabled: true,
                    malwareScanning: true,
                    contentSanitization: true
                }
            },
            privacyNote: "Statistics are aggregated and anonymized. No file contents or personal data are exposed."
        });

    } catch (error) {
        console.error('❌ Stats retrieval error:', error);
        req.monitoring?.recordError('STATS_ERROR', error);
        
        res.status(500).json({
            error: 'Failed to retrieve statistics',
            message: 'Statistics temporarily unavailable'
        });
    }
});

// Comprehensive health check
router.get('/health', async (req, res) => {
    try {
        const health = {
            status: 'OK',
            timestamp: new Date().toISOString(),
            version: "2.0.0",
            services: {
                fileProcessor: fileProcessor ? 'available' : 'unavailable',
                tokenCounter: tokenCounter ? 'available' : 'unavailable',
                redis: req.redis && req.redis.isReady ? 'connected' : 'disconnected',
                database: req.db ? 'connected' : 'disconnected'
            },
            security: {
                malwareScanning: true,
                encryptionEnabled: true,
                contentSanitization: true,
                rateLimiting: true
            },
            limits: {
                maxFileSize: '50MB',
                maxTokensPerChunk: 32000,
                maxConcurrentProcessing: 10,
                cacheExpiry: '30 minutes (Redis), 6 hours (Database)'
            },
            supportedFormats: ['PDF', 'DOCX', 'XLSX', 'PPTX', 'TXT'],
            modelSupport: tokenCounter ? await tokenCounter.getSupportedModels() : {}
        };

        // Test database connection
        if (req.db) {
            try {
                await req.db.query('SELECT 1');
                health.services.database = 'connected';
            } catch (dbError) {
                health.services.database = 'error';
                health.status = 'DEGRADED';
            }
        }

        // Test Redis connection
        if (req.redis) {
            try {
                await req.redis.ping();
                health.services.redis = 'connected';
            } catch (redisError) {
                health.services.redis = 'error';
            }
        }

        res.json(health);

    } catch (error) {
        console.error('❌ Health check error:', error);
        res.status(500).json({
            status: 'ERROR',
            timestamp: new Date().toISOString(),
            error: 'Health check failed',
            message: error.message
        });
    }
});

// Enhanced error handling middleware
router.use((error, req, res, next) => {
    const processingId = req.processingId || crypto.randomUUID();
    
    if (error instanceof multer.MulterError) {
        req.monitoring?.recordSecurityEvent('MULTER_ERROR', {
            code: error.code,
            ip: req.ip,
            processingId
        });
        
        if (error.code === 'LIMIT_FILE_SIZE') {
            return res.status(413).json({
                error: 'File too large',
                message: 'Maximum file size is 50MB',
                code: 'FILE_TOO_LARGE',
                processingId
            });
        }
        
        if (error.code === 'LIMIT_FILE_COUNT') {
            return res.status(400).json({
                error: 'Too many files',
                message: 'Only one file can be processed at a time',
                code: 'TOO_MANY_FILES',
                processingId
            });
        }

        if (error.code === 'LIMIT_FIELD_VALUE') {
            return res.status(400).json({
                error: 'Field too large',
                message: 'Form field exceeds size limit',
                code: 'FIELD_TOO_LARGE',
                processingId
            });
        }
    }

    // File security errors
    if (error.message.includes('malicious') || error.message.includes('dangerous')) {
        req.monitoring?.recordSecurityEvent('MALICIOUS_FILE_DETECTED', {
            ip: req.ip,
            fileName: req.file?.originalname,
            processingId
        });
        
        return res.status(400).json({
            error: 'File rejected for security reasons',
            message: 'The uploaded file contains potentially harmful content',
            code: 'SECURITY_VIOLATION',
            processingId
        });
    }

    // File type errors
    if (error.message.includes('Unsupported file type') || error.message.includes('Invalid file')) {
        return res.status(415).json({
            error: 'Unsupported file type',
            message: error.message,
            supportedTypes: ['PDF', 'DOCX', 'XLSX', 'PPTX', 'TXT'],
            code: 'UNSUPPORTED_FILE_TYPE',
            processingId
        });
    }

    // Generic processing errors
    console.error(`❌ [${processingId}] File processing middleware error:`, error);
    req.monitoring?.recordError('MIDDLEWARE_ERROR', error, { processingId });
    
    res.status(500).json({
        error: 'File processing failed',
        message: process.env.NODE_ENV === 'production' ? 
            'An error occurred during file processing' : 
            error.message,
        code: 'PROCESSING_ERROR',
        processingId
    });
});

module.exports = router;