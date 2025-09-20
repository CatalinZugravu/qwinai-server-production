const express = require('express');
const multer = require('multer');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

// Import our services
const FileExtractor = require('../services/fileExtractor');
const TokenCounter = require('../services/tokenCounter');
const DocumentChunker = require('../services/documentChunker');

const router = express.Router();

// Initialize services
const fileExtractor = new FileExtractor();
const tokenCounter = new TokenCounter();
const documentChunker = new DocumentChunker(tokenCounter);

/**
 * CRITICAL FIX: Proper MIME type detection based on file extension
 * This fixes the issue where all files are incorrectly identified as text/plain
 */
function detectMimeType(filename, multerMimeType) {
    // Extract extension and normalize it
    const ext = path.extname(filename).toLowerCase().replace('.', '');
    
    console.log(`🔍 MIME Detection for: ${filename}`);
    console.log(`   Extension: ${ext}`);
    console.log(`   Multer reported: ${multerMimeType}`);
    
    // COMPREHENSIVE MIME TYPE MAPPING - PRIORITY ON FILE EXTENSION
    const mimeTypeMap = {
        // PDF
        'pdf': 'application/pdf',
        
        // Microsoft Office formats - THESE ARE CRITICAL
        'docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'doc': 'application/msword',
        'xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'xls': 'application/vnd.ms-excel',
        'pptx': 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
        'ppt': 'application/vnd.ms-powerpoint',
        
        // Text formats
        'txt': 'text/plain',
        'csv': 'text/csv',
        'rtf': 'application/rtf',
        'md': 'text/markdown',
        
        // OpenDocument formats
        'odt': 'application/vnd.oasis.opendocument.text',
        'ods': 'application/vnd.oasis.opendocument.spreadsheet',
        'odp': 'application/vnd.oasis.opendocument.presentation'
    };
    
    // ALWAYS USE EXTENSION-BASED DETECTION FIRST
    if (ext && mimeTypeMap[ext]) {
        const detectedMime = mimeTypeMap[ext];
        console.log(`✅ MIME type detected from extension: ${detectedMime}`);
        return detectedMime;
    }
    
    // Only use multer MIME if it's specific and not generic
    if (multerMimeType && 
        multerMimeType !== 'application/octet-stream' && 
        multerMimeType !== 'text/plain' && // Don't trust text/plain for Office files
        Object.values(mimeTypeMap).includes(multerMimeType)) {
        console.log(`✅ Using validated multer MIME: ${multerMimeType}`);
        return multerMimeType;
    }
    
    // Log warning for unknown extension
    console.warn(`⚠️ Unknown file extension '${ext}', cannot determine MIME type`);
    throw new Error(`Unsupported file type: .${ext}`);
}

// Configure file upload with multer
const upload = multer({
    dest: 'uploads/',
    limits: { 
        fileSize: 50 * 1024 * 1024, // 50MB limit
        files: 1 // Only one file at a time
    },
    fileFilter: (req, file, cb) => {
        console.log(`📤 File upload attempt: ${file.originalname}`);
        
        try {
            // Detect correct MIME type
            const correctMimeType = detectMimeType(file.originalname, file.mimetype);
            
            // Check if the correctly detected MIME type is supported
            if (fileExtractor.isSupported(correctMimeType)) {
                // Store the correct MIME type for later use
                file.correctedMimeType = correctMimeType;
                cb(null, true);
            } else {
                const supportedTypes = fileExtractor.getSupportedTypes().join(', ');
                cb(new Error(`Unsupported file type: ${correctMimeType}. Supported types: ${supportedTypes}`), false);
            }
        } catch (error) {
            cb(error, false);
        }
    }
});

// Main file processing endpoint
router.post('/process', upload.single('file'), async (req, res) => {
    const startTime = Date.now();
    const processingId = crypto.randomUUID().substring(0, 8);
    
    try {
        const { file } = req;
        const { model = 'gpt-4', maxTokensPerChunk = 6000 } = req.body;
        
        if (!file) {
            return res.status(400).json({ 
                error: 'No file uploaded',
                success: false 
            });
        }

        // CRITICAL: Use the corrected MIME type, not the multer-provided one
        const correctMimeType = file.correctedMimeType || detectMimeType(file.originalname, file.mimetype);

        console.log(`\n🚀 [${processingId}] Processing file: ${file.originalname}`);
        console.log(`   📊 Size: ${(file.size / 1024 / 1024).toFixed(2)}MB`);
        console.log(`   🎯 Correct MIME type: ${correctMimeType}`);
        console.log(`   ❌ Multer MIME (incorrect): ${file.mimetype}`);
        console.log(`   🤖 Target model: ${model}`);
        console.log(`   ✂️ Max tokens per chunk: ${maxTokensPerChunk}`);

        // Generate file hash for caching
        const fileHash = crypto.createHash('md5')
            .update(fs.readFileSync(file.path))
            .digest('hex');

        // Check cache first (if Redis is available)
        if (req.redis && req.redis.isReady) {
            try {
                const cacheKey = `file:${fileHash}:${model}:${maxTokensPerChunk}`;
                const cached = await req.redis.get(cacheKey);
                
                if (cached) {
                    console.log(`💨 [${processingId}] Cache hit for file: ${file.originalname}`);
                    
                    // Clean up uploaded file
                    fs.unlinkSync(file.path);
                    
                    return res.json({
                        ...JSON.parse(cached),
                        cached: true,
                        processingTime: Date.now() - startTime
                    });
                }
            } catch (cacheError) {
                console.warn(`⚠️ [${processingId}] Cache check failed:`, cacheError.message);
            }
        }

        // Step 1: Extract content from file WITH CORRECT MIME TYPE
        console.log(`📄 [${processingId}] Step 1: Extracting content from ${correctMimeType} file...`);
        const extractedContent = await fileExtractor.extractContent(file.path, correctMimeType);
        
        // Check for extraction errors
        if (extractedContent.metadata?.extractionError) {
            console.error(`❌ [${processingId}] Extraction failed:`, extractedContent.text);
            throw new Error(extractedContent.metadata.errorMessage || 'Content extraction failed');
        }
        
        if (!extractedContent.text || extractedContent.text.trim().length === 0) {
            throw new Error('No extractable text content found in file');
        }

        console.log(`✅ [${processingId}] Content extracted: ${extractedContent.text.length} characters, ${extractedContent.pageCount} pages`);

        // Step 2: Analyze tokens
        console.log(`🧮 [${processingId}] Step 2: Analyzing tokens for model ${model}...`);
        const tokenAnalysis = tokenCounter.analyzeText(extractedContent.text, model);
        
        console.log(`📊 [${processingId}] Token analysis:`);
        console.log(`   📝 Tokens: ${tokenAnalysis.tokenCount}`);
        console.log(`   💰 Cost: ${tokenAnalysis.estimatedCost}`);
        console.log(`   🎯 Context limit: ${tokenAnalysis.contextLimit}`);
        console.log(`   ⚠️ Exceeds context: ${tokenAnalysis.exceedsContext}`);

        // Step 3: Chunk document if necessary
        console.log(`✂️ [${processingId}] Step 3: Chunking document...`);
        const chunks = documentChunker.chunkDocument(
            extractedContent.text, 
            parseInt(maxTokensPerChunk), 
            model
        );

        // Step 4: Filter chunks that fit in model's context window
        const optimalChunks = chunks.filter(chunk => 
            chunk.tokenCount <= tokenAnalysis.contextLimit * 0.8 // Leave 20% buffer
        );

        if (optimalChunks.length === 0 && chunks.length > 0) {
            console.warn(`⚠️ [${processingId}] No chunks fit in ${model} context window (${tokenAnalysis.contextLimit} tokens)`);
        }

        // Get chunking statistics
        const chunkingStats = documentChunker.getChunkingStats(chunks);
        console.log(`📈 [${processingId}] Chunking stats: ${chunkingStats.totalChunks} chunks, avg ${chunkingStats.averageTokens} tokens`);

        // Step 5: Prepare response
        const result = {
            success: true,
            originalFileName: file.originalname,
            fileSize: file.size,
            mimeType: correctMimeType, // Use the correct MIME type in response
            detectedFormat: extractedContent.metadata?.format,
            fileHash: fileHash,
            extractedContent: {
                text: extractedContent.text,
                pageCount: extractedContent.pageCount,
                metadata: extractedContent.metadata
            },
            tokenAnalysis: {
                totalTokens: tokenAnalysis.tokenCount,
                contextLimit: tokenAnalysis.contextLimit,
                estimatedCost: tokenAnalysis.estimatedCost,
                model: model,
                exceedsContext: tokenAnalysis.exceedsContext,
                utilizationPercent: tokenAnalysis.utilizationPercent,
                chunksNeeded: tokenAnalysis.chunksNeeded
            },
            chunks: chunks.map((chunk, index) => ({
                index: index + 1,
                totalChunks: chunks.length,
                text: chunk.text,
                tokenCount: chunk.tokenCount,
                characterCount: chunk.characterCount,
                wordCount: chunk.wordCount,
                preview: chunk.preview,
                sentences: chunk.sentences,
                fitsInContext: chunk.tokenCount <= tokenAnalysis.contextLimit * 0.8
            })),
            optimalChunks: optimalChunks.length,
            processing: {
                recommendedApproach: tokenAnalysis.exceedsContext ? 'chunked' : 'single',
                chunkCount: chunks.length,
                processingTimeMs: Date.now() - startTime,
                extractionMethod: extractedContent.metadata?.format,
                chunkingStats: chunkingStats,
                processingId: processingId
            },
            cached: false
        };

        // Cache the result if Redis is available
        if (req.redis && req.redis.isReady) {
            try {
                const cacheKey = `file:${fileHash}:${model}:${maxTokensPerChunk}`;
                await req.redis.setex(cacheKey, 3600, JSON.stringify(result)); // Cache for 1 hour
                console.log(`💾 [${processingId}] Result cached for 1 hour`);
            } catch (cacheError) {
                console.warn(`⚠️ [${processingId}] Failed to cache result:`, cacheError.message);
            }
        }

        // Store in database for longer-term caching
        try {
            const expiresAt = new Date();
            expiresAt.setHours(expiresAt.getHours() + 24); // Expire in 24 hours
            
            await req.db.query(`
                INSERT INTO processed_files (
                    file_hash, original_name, mime_type, file_size, 
                    total_tokens, chunk_count, processed_content, expires_at
                ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
                ON CONFLICT (file_hash) DO UPDATE SET
                    processed_at = CURRENT_TIMESTAMP,
                    expires_at = EXCLUDED.expires_at
            `, [
                fileHash,
                file.originalname,
                correctMimeType, // Store correct MIME type
                file.size,
                tokenAnalysis.tokenCount,
                chunks.length,
                JSON.stringify(result),
                expiresAt
            ]);
            
            console.log(`💾 [${processingId}] Result stored in database`);
        } catch (dbError) {
            console.warn(`⚠️ [${processingId}] Failed to store in database:`, dbError.message);
        }

        console.log(`🎉 [${processingId}] File processing completed successfully in ${Date.now() - startTime}ms`);
        
        res.json(result);

    } catch (error) {
        console.error(`❌ [${processingId}] File processing error:`, error);
        
        res.status(500).json({ 
            error: 'File processing failed', 
            message: error.message,
            success: false,
            processingTime: Date.now() - startTime,
            processingId: processingId
        });
    } finally {
        // Always clean up uploaded file
        if (req.file && fs.existsSync(req.file.path)) {
            try {
                fs.unlinkSync(req.file.path);
                console.log(`🗑️ [${processingId}] Cleaned up temp file: ${req.file.filename}`);
            } catch (cleanupError) {
                console.warn(`⚠️ [${processingId}] Failed to clean up temp file:`, cleanupError.message);
            }
        }
    }
});

// Get optimal chunks for specific model
router.post('/optimize-chunks', async (req, res) => {
    try {
        const { fileHash, targetModel, maxContextTokens } = req.body;
        
        if (!fileHash || !targetModel) {
            return res.status(400).json({
                error: 'fileHash and targetModel are required'
            });
        }

        // Get processed file from database
        const result = await req.db.query(
            'SELECT processed_content FROM processed_files WHERE file_hash = $1 AND expires_at > NOW()',
            [fileHash]
        );

        if (result.rows.length === 0) {
            return res.status(404).json({
                error: 'Processed file not found or expired. Please reprocess the file.'
            });
        }

        const processedData = JSON.parse(result.rows[0].processed_content);
        const contextLimit = maxContextTokens || tokenCounter.getContextLimits(targetModel);
        
        // Filter chunks that fit in the target model's context
        const optimalChunks = processedData.chunks.filter(chunk => 
            chunk.tokenCount <= contextLimit * 0.8
        );

        // Re-analyze for target model if different
        let tokenAnalysis = processedData.tokenAnalysis;
        if (targetModel !== processedData.tokenAnalysis.model) {
            tokenAnalysis = tokenCounter.analyzeText(processedData.extractedContent.text, targetModel);
        }

        res.json({
            success: true,
            originalFileName: processedData.originalFileName,
            targetModel: targetModel,
            contextLimit: contextLimit,
            totalChunks: processedData.chunks.length,
            optimalChunks: optimalChunks,
            tokenAnalysis: {
                ...tokenAnalysis,
                model: targetModel
            },
            recommendation: {
                approach: optimalChunks.length === 0 ? 'file_too_large' : 
                         optimalChunks.length === 1 ? 'single_chunk' : 'multiple_chunks',
                usableChunks: optimalChunks.length,
                totalTokensInOptimalChunks: optimalChunks.reduce((sum, chunk) => sum + chunk.tokenCount, 0)
            }
        });

    } catch (error) {
        console.error('❌ Chunk optimization error:', error);
        res.status(500).json({
            error: 'Failed to optimize chunks',
            message: error.message
        });
    }
});

// Get processing statistics
router.get('/stats', async (req, res) => {
    try {
        // Get statistics from database
        const stats = await req.db.query(`
            SELECT 
                COUNT(*) as total_files,
                SUM(file_size) as total_size_bytes,
                AVG(total_tokens) as avg_tokens,
                MAX(total_tokens) as max_tokens,
                COUNT(DISTINCT mime_type) as unique_file_types,
                array_agg(DISTINCT mime_type) as supported_types
            FROM processed_files 
            WHERE expires_at > NOW()
        `);

        const tokenCounterStats = tokenCounter.getUsageStats();
        const extractorStats = fileExtractor.getStats();

        res.json({
            success: true,
            statistics: {
                files: {
                    totalProcessed: parseInt(stats.rows[0].total_files) || 0,
                    totalSizeBytes: parseInt(stats.rows[0].total_size_bytes) || 0,
                    totalSizeMB: Math.round((stats.rows[0].total_size_bytes || 0) / 1024 / 1024),
                    uniqueFileTypes: parseInt(stats.rows[0].unique_file_types) || 0,
                    supportedTypes: stats.rows[0].supported_types || []
                },
                tokens: {
                    averagePerFile: Math.round(stats.rows[0].avg_tokens) || 0,
                    maximumInSingleFile: parseInt(stats.rows[0].max_tokens) || 0
                },
                processing: {
                    tokenCounter: tokenCounterStats,
                    extractor: extractorStats,
                    supportedFileTypes: fileExtractor.getSupportedTypes()
                }
            }
        });

    } catch (error) {
        console.error('❌ Stats retrieval error:', error);
        res.status(500).json({
            error: 'Failed to retrieve statistics',
            message: error.message
        });
    }
});

// Health check for file processing service
router.get('/health', (req, res) => {
    const extractorStats = fileExtractor.getStats();
    
    const health = {
        status: 'OK',
        timestamp: new Date().toISOString(),
        services: {
            fileExtractor: fileExtractor ? 'available' : 'unavailable',
            tokenCounter: tokenCounter ? 'available' : 'unavailable',
            documentChunker: documentChunker ? 'available' : 'unavailable',
            redis: req.redis && req.redis.isReady ? 'connected' : 'disconnected',
            database: req.db ? 'connected' : 'disconnected'
        },
        limits: {
            maxFileSize: '50MB',
            maxTokensPerChunk: 32000,
            cacheExpiry: '1 hour (Redis), 24 hours (Database)'
        },
        extractorInfo: extractorStats,
        supportedFormats: fileExtractor.getSupportedTypes()
    };

    res.json(health);
});

// Error handling middleware specific to file processing
router.use((error, req, res, next) => {
    if (error instanceof multer.MulterError) {
        if (error.code === 'LIMIT_FILE_SIZE') {
            return res.status(413).json({
                error: 'File too large',
                message: 'Maximum file size is 50MB',
                code: 'FILE_TOO_LARGE'
            });
        }
        
        if (error.code === 'LIMIT_FILE_COUNT') {
            return res.status(400).json({
                error: 'Too many files',
                message: 'Only one file can be processed at a time',
                code: 'TOO_MANY_FILES'
            });
        }
    }

    // File type error
    if (error.message.includes('Unsupported file type')) {
        return res.status(415).json({
            error: 'Unsupported file type',
            message: error.message,
            supportedTypes: fileExtractor.getSupportedTypes(),
            code: 'UNSUPPORTED_FILE_TYPE'
        });
    }

    // Default error handling
    console.error('❌ File processing middleware error:', error);
    res.status(500).json({
        error: 'File processing failed',
        message: error.message,
        code: 'PROCESSING_ERROR'
    });
});

module.exports = router;
