const express = require('express');
const multer = require('multer');
const fs = require('fs').promises;
const path = require('path');
const crypto = require('crypto');

const router = express.Router();

// Import the file extractor and other services
const FileExtractor = require('../services/FileExtractor');
const TokenCounter = require('../services/TokenCounter');
const DocumentChunker = require('../services/DocumentChunker');

// Initialize services
const fileExtractor = new FileExtractor();
const tokenCounter = new TokenCounter();
const documentChunker = new DocumentChunker(tokenCounter);

// Configure multer for file uploads with proper MIME type detection
const storage = multer.diskStorage({
    destination: async (req, file, cb) => {
        const uploadDir = 'uploads';
        try {
            await fs.mkdir(uploadDir, { recursive: true });
            cb(null, uploadDir);
        } catch (error) {
            cb(error, null);
        }
    },
    filename: (req, file, cb) => {
        const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
        const ext = path.extname(file.originalname);
        cb(null, `temp_${uniqueSuffix}${ext}`);
    }
});

// CRITICAL FIX: Comprehensive MIME type detection
function detectMimeType(filename, multerMimeType) {
    // Extract extension and normalize it
    const ext = path.extname(filename).toLowerCase().replace('.', '');
    
    console.log(`🔍 Detecting MIME type for: ${filename}`);
    console.log(`   Extension: ${ext}`);
    console.log(`   Multer MIME: ${multerMimeType}`);
    
    // COMPREHENSIVE MIME TYPE MAPPING
    const mimeTypeMap = {
        // PDF
        'pdf': 'application/pdf',
        
        // Microsoft Office formats
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
        
        // Image formats (for future OCR support)
        'jpg': 'image/jpeg',
        'jpeg': 'image/jpeg',
        'png': 'image/png',
        'gif': 'image/gif',
        'bmp': 'image/bmp',
        'webp': 'image/webp',
        
        // Other document formats
        'odt': 'application/vnd.oasis.opendocument.text',
        'ods': 'application/vnd.oasis.opendocument.spreadsheet',
        'odp': 'application/vnd.oasis.opendocument.presentation'
    };
    
    // First priority: Use extension-based detection
    if (ext && mimeTypeMap[ext]) {
        const detectedMime = mimeTypeMap[ext];
        console.log(`✅ MIME type detected from extension: ${detectedMime}`);
        return detectedMime;
    }
    
    // Second priority: Check if multer MIME type is valid and specific
    if (multerMimeType && multerMimeType !== 'application/octet-stream') {
        // Validate against our supported types
        const supportedMimes = Object.values(mimeTypeMap);
        if (supportedMimes.includes(multerMimeType)) {
            console.log(`✅ Using multer MIME type: ${multerMimeType}`);
            return multerMimeType;
        }
    }
    
    // Third priority: Try to detect from file content patterns
    // This would require reading file headers, but for now fallback to extension
    
    // Last resort: Default based on common extensions
    if (ext) {
        console.warn(`⚠️ Unknown extension '${ext}', defaulting to application/octet-stream`);
    }
    
    return 'application/octet-stream';
}

// Enhanced multer configuration with file filter
const fileFilter = (req, file, cb) => {
    const ext = path.extname(file.originalname).toLowerCase();
    const allowedExtensions = [
        '.pdf', '.docx', '.doc', '.xlsx', '.xls', 
        '.pptx', '.ppt', '.txt', '.csv', '.rtf',
        '.jpg', '.jpeg', '.png', '.gif', '.bmp'
    ];
    
    if (allowedExtensions.includes(ext)) {
        cb(null, true);
    } else {
        cb(new Error(`Unsupported file type: ${ext}`), false);
    }
};

const upload = multer({
    storage: storage,
    fileFilter: fileFilter,
    limits: {
        fileSize: 50 * 1024 * 1024, // 50MB limit
        files: 1
    }
});

// Main file processing endpoint
router.post('/process', upload.single('file'), async (req, res) => {
    const processingId = crypto.randomUUID().substring(0, 8);
    console.log(`\n🚀 [${processingId}] Starting file processing request`);
    
    let tempFilePath = null;
    
    try {
        if (!req.file) {
            throw new Error('No file uploaded');
        }
        
        tempFilePath = req.file.path;
        const originalName = req.file.originalname;
        const fileSize = req.file.size;
        
        // CRITICAL: Properly detect MIME type
        const detectedMimeType = detectMimeType(originalName, req.file.mimetype);
        
        console.log(`📄 [${processingId}] Processing file: ${originalName}`);
        console.log(`   Size: ${fileSize} bytes`);
        console.log(`   Detected MIME: ${detectedMimeType}`);
        console.log(`   Temp path: ${tempFilePath}`);
        
        // Validate file exists and is readable
        try {
            await fs.access(tempFilePath, fs.constants.R_OK);
        } catch (error) {
            throw new Error(`Cannot read uploaded file: ${error.message}`);
        }
        
        // Calculate file hash for caching
        const fileBuffer = await fs.readFile(tempFilePath);
        const fileHash = crypto.createHash('md5').update(fileBuffer).digest('hex');
        
        console.log(`🔑 [${processingId}] File hash: ${fileHash}`);
        
        // Check cache if Redis is available
        if (req.redis && req.redis.isReady) {
            try {
                const cached = await req.redis.get(`file:${fileHash}`);
                if (cached) {
                    console.log(`💨 [${processingId}] Cache hit! Returning cached result`);
                    const cachedData = JSON.parse(cached);
                    
                    // Clean up temp file
                    await fs.unlink(tempFilePath).catch(() => {});
                    
                    return res.json({
                        success: true,
                        ...cachedData,
                        fromCache: true
                    });
                }
            } catch (cacheError) {
                console.warn(`⚠️ [${processingId}] Cache check failed:`, cacheError.message);
            }
        }
        
        // Check if file type is supported
        if (!fileExtractor.isSupported(detectedMimeType)) {
            // Check if it's an image for OCR
            const imageTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/bmp', 'image/webp'];
            if (imageTypes.includes(detectedMimeType)) {
                throw new Error('Image OCR not yet implemented. Please use document formats (PDF, DOCX, XLSX, PPTX, TXT)');
            }
            throw new Error(`Unsupported file type: ${detectedMimeType}`);
        }
        
        // Extract content with proper MIME type
        console.log(`🔧 [${processingId}] Starting extraction with FileExtractor v2.0`);
        const extractionResult = await fileExtractor.extractContent(tempFilePath, detectedMimeType);
        
        // Validate extraction result
        if (!extractionResult || !extractionResult.text) {
            throw new Error('File extraction failed - no content extracted');
        }
        
        // Check if extraction failed
        if (extractionResult.metadata?.extractionError) {
            console.error(`❌ [${processingId}] Extraction error:`, extractionResult.text);
            throw new Error(extractionResult.metadata.errorMessage || 'Content extraction failed');
        }
        
        console.log(`✅ [${processingId}] Extraction successful:`);
        console.log(`   Text length: ${extractionResult.text.length} chars`);
        console.log(`   Pages/Sheets: ${extractionResult.pageCount}`);
        console.log(`   Format: ${extractionResult.metadata?.format}`);
        
        // Chunk the document
        const model = req.body?.model || 'gpt-4';
        const maxTokens = req.body?.maxTokens || 6000;
        
        console.log(`📑 [${processingId}] Chunking document for model: ${model}`);
        const chunks = documentChunker.chunkDocument(
            extractionResult.text,
            maxTokens,
            model
        );
        
        // Get chunking statistics
        const stats = documentChunker.getChunkingStats(chunks);
        console.log(`📊 [${processingId}] Chunking complete:`, stats);
        
        // Prepare response
        const responseData = {
            success: true,
            originalFileName: originalName,
            fileSize: fileSize,
            mimeType: detectedMimeType,
            fileHash: fileHash,
            extractedContent: {
                text: extractionResult.text,
                pageCount: extractionResult.pageCount,
                metadata: extractionResult.metadata
            },
            chunks: chunks.map(chunk => ({
                index: chunk.index,
                text: chunk.text,
                tokenCount: chunk.tokenCount,
                characterCount: chunk.characterCount,
                wordCount: chunk.wordCount
            })),
            statistics: {
                totalChunks: stats.totalChunks,
                totalTokens: stats.totalTokens,
                averageTokensPerChunk: stats.averageTokens,
                totalCharacters: stats.totalCharacters,
                totalWords: stats.totalWords,
                estimatedCost: tokenCounter.estimateCost(stats.totalTokens, model)
            },
            processingTime: Date.now() - parseInt(processingId.split('-')[0])
        };
        
        // Cache the result if Redis is available
        if (req.redis && req.redis.isReady) {
            try {
                await req.redis.setex(
                    `file:${fileHash}`,
                    3600, // Cache for 1 hour
                    JSON.stringify(responseData)
                );
                console.log(`💾 [${processingId}] Result cached for 1 hour`);
            } catch (cacheError) {
                console.warn(`⚠️ [${processingId}] Failed to cache result:`, cacheError.message);
            }
        }
        
        // Store in database if available
        if (req.db) {
            try {
                await req.db.query(`
                    INSERT INTO processed_files 
                    (file_hash, original_name, mime_type, file_size, total_tokens, 
                     chunk_count, processed_content, expires_at)
                    VALUES ($1, $2, $3, $4, $5, $6, $7, NOW() + INTERVAL '24 hours')
                    ON CONFLICT (file_hash) 
                    DO UPDATE SET 
                        processed_at = CURRENT_TIMESTAMP,
                        expires_at = NOW() + INTERVAL '24 hours'
                `, [
                    fileHash,
                    originalName,
                    detectedMimeType,
                    fileSize,
                    stats.totalTokens,
                    stats.totalChunks,
                    JSON.stringify(responseData)
                ]);
                console.log(`💾 [${processingId}] Stored in database`);
            } catch (dbError) {
                console.warn(`⚠️ [${processingId}] Database storage failed:`, dbError.message);
            }
        }
        
        // Clean up temp file
        await fs.unlink(tempFilePath).catch(err => {
            console.warn(`⚠️ [${processingId}] Failed to delete temp file:`, err.message);
        });
        
        console.log(`✅ [${processingId}] File processing complete!`);
        res.json(responseData);
        
    } catch (error) {
        console.error(`❌ [${processingId}] Processing error:`, error);
        
        // Clean up temp file on error
        if (tempFilePath) {
            await fs.unlink(tempFilePath).catch(() => {});
        }
        
        res.status(400).json({
            success: false,
            error: error.message,
            details: process.env.NODE_ENV === 'development' ? error.stack : undefined
        });
    }
});

// Get processing statistics
router.get('/stats', async (req, res) => {
    try {
        const stats = {
            extractor: fileExtractor.getStats(),
            tokenCounter: tokenCounter.getUsageStats(),
            supportedFormats: [
                { format: 'PDF', mimeType: 'application/pdf', status: 'supported' },
                { format: 'Word (DOCX)', mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', status: 'supported' },
                { format: 'Excel (XLSX)', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', status: 'supported' },
                { format: 'PowerPoint (PPTX)', mimeType: 'application/vnd.openxmlformats-officedocument.presentationml.presentation', status: 'supported' },
                { format: 'Text', mimeType: 'text/plain', status: 'supported' },
                { format: 'CSV', mimeType: 'text/csv', status: 'supported' },
                { format: 'RTF', mimeType: 'application/rtf', status: 'supported' }
            ]
        };
        
        // Add database stats if available
        if (req.db) {
            const dbResult = await req.db.query(`
                SELECT 
                    COUNT(*) as total_files,
                    SUM(file_size) as total_size,
                    AVG(total_tokens) as avg_tokens
                FROM processed_files 
                WHERE expires_at > NOW()
            `);
            stats.database = dbResult.rows[0];
        }
        
        res.json(stats);
    } catch (error) {
        res.status(500).json({
            error: 'Failed to get statistics',
            message: error.message
        });
    }
});

// Clear cache endpoint
router.post('/clear-cache', async (req, res) => {
    try {
        fileExtractor.clearCache();
        
        if (req.redis && req.redis.isReady) {
            const keys = await req.redis.keys('file:*');
            if (keys.length > 0) {
                await req.redis.del(...keys);
            }
        }
        
        res.json({
            success: true,
            message: 'Cache cleared successfully'
        });
    } catch (error) {
        res.status(500).json({
            error: 'Failed to clear cache',
            message: error.message
        });
    }
});

module.exports = router;
