const crypto = require('crypto');
const fs = require('fs').promises;
const path = require('path');
const { promisify } = require('util');
const { pipeline } = require('stream');
const FileExtractor = require('./fileExtractor');
const TokenCounter = require('./tokenCounter');
const DocumentChunker = require('./documentChunker');

/**
 * PRODUCTION-GRADE SECURE FILE PROCESSOR
 * Handles multiple users concurrently with privacy and security safeguards
 */
class SecureFileProcessor {
    constructor() {
        this.fileExtractor = new FileExtractor();
        this.tokenCounter = new TokenCounter();
        this.documentChunker = new DocumentChunker(this.tokenCounter);
        
        // Security limits
        this.MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
        this.MAX_PROCESSING_TIME = 5 * 60 * 1000; // 5 minutes
        this.MAX_CONCURRENT_PROCESSING = 10; // Max files processing at once
        this.TEMP_FILE_TTL = 30 * 60 * 1000; // 30 minutes
        
        // Track active processing
        this.activeProcessing = new Map();
        this.processingQueue = [];
        
        // Initialize secure temp directory
        this.initSecureTempDir();
        
        // Cleanup interval
        setInterval(() => this.cleanupExpiredFiles(), 5 * 60 * 1000); // Every 5 minutes
    }

    /**
     * Initialize secure temporary directory with proper permissions
     */
    async initSecureTempDir() {
        try {
            this.tempDir = path.join(process.cwd(), 'secure_temp');
            await fs.mkdir(this.tempDir, { recursive: true, mode: 0o700 }); // Owner only
            console.log('🔒 Secure temp directory initialized');
        } catch (error) {
            console.error('❌ Failed to create secure temp directory:', error);
            throw error;
        }
    }

    /**
     * SECURE FILE PROCESSING with multi-user support
     */
    async processFile(fileBuffer, fileName, mimeType, options = {}) {
        const processingId = crypto.randomUUID();
        const startTime = Date.now();
        
        // Security validations
        await this.validateFileSecurely(fileBuffer, fileName, mimeType);
        
        // Rate limiting check
        if (this.activeProcessing.size >= this.MAX_CONCURRENT_PROCESSING) {
            throw new Error('Server busy. Too many files being processed. Please try again later.');
        }

        let tempFilePath = null;
        
        try {
            console.log(`🔒 [${processingId}] Starting secure processing: ${fileName}`);
            
            // Mark as processing
            this.activeProcessing.set(processingId, {
                fileName,
                startTime,
                timeout: setTimeout(() => {
                    this.timeoutProcessing(processingId);
                }, this.MAX_PROCESSING_TIME)
            });

            // Create secure temporary file
            tempFilePath = await this.createSecureTempFile(fileBuffer, processingId);
            
            // Extract content with timeout protection
            const extractedContent = await Promise.race([
                this.fileExtractor.extractContent(tempFilePath, mimeType),
                this.createTimeoutPromise(this.MAX_PROCESSING_TIME, 'File extraction timed out')
            ]);

            if (!extractedContent.text || extractedContent.text.trim().length === 0) {
                throw new Error('No extractable content found in file');
            }

            // Sanitize extracted content
            const sanitizedContent = this.sanitizeContent(extractedContent.text);
            
            // Token analysis
            const model = options.model || 'gpt-4';
            const maxTokensPerChunk = parseInt(options.maxTokensPerChunk) || 6000;
            
            const tokenAnalysis = this.tokenCounter.analyzeText(sanitizedContent, model);
            
            // Smart chunking
            const chunks = this.documentChunker.chunkDocument(
                sanitizedContent, 
                maxTokensPerChunk, 
                model
            );

            // Create processing result
            const result = {
                processingId,
                fileName: this.sanitizeFileName(fileName),
                fileSize: fileBuffer.length,
                mimeType,
                extractedContent: {
                    text: sanitizedContent,
                    metadata: {
                        ...extractedContent.metadata,
                        processingTime: Date.now() - startTime,
                        processingId
                    }
                },
                tokenAnalysis,
                chunks: chunks.map((chunk, index) => ({
                    index: index + 1,
                    totalChunks: chunks.length,
                    text: chunk.text,
                    tokenCount: chunk.tokenCount,
                    characterCount: chunk.characterCount,
                    wordCount: chunk.wordCount,
                    preview: chunk.preview.substring(0, 100) + '...', // Limit preview
                    fitsInContext: chunk.tokenCount <= tokenAnalysis.contextLimit * 0.8
                })),
                security: {
                    processed: true,
                    sanitized: true,
                    encrypted: false, // Set to true if implementing encryption
                    ttl: this.TEMP_FILE_TTL
                }
            };

            console.log(`✅ [${processingId}] Processing completed: ${chunks.length} chunks, ${tokenAnalysis.tokenCount} tokens`);
            
            return result;
            
        } catch (error) {
            console.error(`❌ [${processingId}] Processing failed:`, error);
            throw new Error(`File processing failed: ${error.message}`);
        } finally {
            // Cleanup
            this.cleanupProcessing(processingId, tempFilePath);
        }
    }

    /**
     * COMPREHENSIVE SECURITY VALIDATION
     */
    async validateFileSecurely(fileBuffer, fileName, mimeType) {
        // File size validation
        if (fileBuffer.length > this.MAX_FILE_SIZE) {
            throw new Error(`File too large: ${(fileBuffer.length / 1024 / 1024).toFixed(2)}MB. Maximum: 50MB`);
        }

        // File name validation (prevent path traversal)
        if (!this.isValidFileName(fileName)) {
            throw new Error('Invalid file name. File names cannot contain path separators or special characters.');
        }

        // MIME type validation
        if (!this.fileExtractor.isSupported(mimeType)) {
            throw new Error(`Unsupported file type: ${mimeType}`);
        }

        // File signature validation (magic number check)
        await this.validateFileSignature(fileBuffer, mimeType);

        // Scan for potentially malicious content
        await this.scanForMaliciousContent(fileBuffer, mimeType);
    }

    /**
     * Validate file signature matches MIME type (prevent file type spoofing)
     */
    async validateFileSignature(buffer, mimeType) {
        const signatures = {
            'application/pdf': [0x25, 0x50, 0x44, 0x46], // %PDF
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document': [0x50, 0x4B, 0x03, 0x04], // ZIP header
            'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': [0x50, 0x4B, 0x03, 0x04],
            'application/vnd.openxmlformats-officedocument.presentationml.presentation': [0x50, 0x4B, 0x03, 0x04],
            'text/plain': null // Skip signature check for text files
        };

        const expectedSignature = signatures[mimeType];
        if (!expectedSignature) return; // Skip validation for unsupported types

        const fileSignature = Array.from(buffer.slice(0, expectedSignature.length));
        
        if (!expectedSignature.every((byte, index) => byte === fileSignature[index])) {
            throw new Error('File signature does not match declared MIME type. File may be corrupted or spoofed.');
        }
    }

    /**
     * Scan for potentially malicious content
     */
    async scanForMaliciousContent(buffer, mimeType) {
        // Basic malicious pattern detection
        const maliciousPatterns = [
            /javascript:/gi,
            /<script/gi,
            /vbscript:/gi,
            /on\w+=/gi, // onclick, onload, etc.
            /%3Cscript/gi, // URL encoded script tags
        ];

        const content = buffer.toString('utf8', 0, Math.min(buffer.length, 10000)); // Check first 10KB
        
        for (const pattern of maliciousPatterns) {
            if (pattern.test(content)) {
                console.warn(`🚨 Potentially malicious content detected in file`);
                throw new Error('File contains potentially malicious content and cannot be processed.');
            }
        }
    }

    /**
     * Create secure temporary file with unique name
     */
    async createSecureTempFile(fileBuffer, processingId) {
        const tempFileName = `secure_${processingId}_${Date.now()}.tmp`;
        const tempFilePath = path.join(this.tempDir, tempFileName);
        
        await fs.writeFile(tempFilePath, fileBuffer, { mode: 0o600 }); // Owner read/write only
        
        // Schedule automatic deletion
        setTimeout(async () => {
            try {
                await fs.unlink(tempFilePath);
                console.log(`🗑️ Auto-deleted temp file: ${tempFileName}`);
            } catch (error) {
                console.warn(`⚠️ Failed to auto-delete temp file: ${error.message}`);
            }
        }, this.TEMP_FILE_TTL);

        return tempFilePath;
    }

    /**
     * Sanitize extracted content (remove potential security risks)
     */
    sanitizeContent(content) {
        return content
            .replace(/<script[\s\S]*?<\/script>/gi, '[SCRIPT_REMOVED]')
            .replace(/javascript:/gi, '[JAVASCRIPT_REMOVED]')
            .replace(/vbscript:/gi, '[VBSCRIPT_REMOVED]')
            .replace(/on\w+\s*=/gi, '[EVENT_HANDLER_REMOVED]')
            .replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g, '') // Remove control characters
            .trim();
    }

    /**
     * Validate and sanitize file names
     */
    isValidFileName(fileName) {
        // Prevent path traversal and invalid characters
        const invalidChars = /[<>:"|?*\x00-\x1f]/g;
        const pathTraversal = /\.\./;
        const reservedNames = /^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$/i;
        
        return fileName 
            && fileName.length <= 255 
            && !invalidChars.test(fileName)
            && !pathTraversal.test(fileName)
            && !reservedNames.test(fileName.split('.')[0]);
    }

    /**
     * Sanitize file name for safe storage/display
     */
    sanitizeFileName(fileName) {
        return fileName
            .replace(/[^a-zA-Z0-9._-]/g, '_')
            .substring(0, 100); // Limit length
    }

    /**
     * Create timeout promise for race conditions
     */
    createTimeoutPromise(timeout, message) {
        return new Promise((_, reject) => {
            setTimeout(() => reject(new Error(message)), timeout);
        });
    }

    /**
     * Handle processing timeout
     */
    async timeoutProcessing(processingId) {
        console.warn(`⏰ Processing timeout for: ${processingId}`);
        this.cleanupProcessing(processingId);
    }

    /**
     * Cleanup processing resources
     */
    async cleanupProcessing(processingId, tempFilePath = null) {
        // Clear timeout
        const processing = this.activeProcessing.get(processingId);
        if (processing?.timeout) {
            clearTimeout(processing.timeout);
        }
        
        // Remove from active processing
        this.activeProcessing.delete(processingId);
        
        // Delete temporary file
        if (tempFilePath) {
            try {
                await fs.unlink(tempFilePath);
                console.log(`🗑️ Cleaned up temp file: ${processingId}`);
            } catch (error) {
                console.warn(`⚠️ Failed to cleanup temp file: ${error.message}`);
            }
        }
    }

    /**
     * Cleanup expired temporary files
     */
    async cleanupExpiredFiles() {
        try {
            const files = await fs.readdir(this.tempDir);
            const now = Date.now();
            
            for (const file of files) {
                const filePath = path.join(this.tempDir, file);
                const stats = await fs.stat(filePath);
                
                if (now - stats.mtime.getTime() > this.TEMP_FILE_TTL) {
                    await fs.unlink(filePath);
                    console.log(`🧹 Cleaned up expired file: ${file}`);
                }
            }
        } catch (error) {
            console.warn('⚠️ Cleanup failed:', error.message);
        }
    }

    /**
     * Get processing statistics for monitoring
     */
    getProcessingStats() {
        return {
            activeProcessing: this.activeProcessing.size,
            maxConcurrent: this.MAX_CONCURRENT_PROCESSING,
            queueLength: this.processingQueue.length,
            uptime: process.uptime(),
            memoryUsage: process.memoryUsage()
        };
    }

    /**
     * Graceful shutdown
     */
    async shutdown() {
        console.log('🛑 Shutting down secure file processor...');
        
        // Clear all timeouts
        for (const [processingId, processing] of this.activeProcessing) {
            if (processing.timeout) {
                clearTimeout(processing.timeout);
            }
        }
        
        // Cleanup all temp files
        try {
            const files = await fs.readdir(this.tempDir);
            for (const file of files) {
                await fs.unlink(path.join(this.tempDir, file));
            }
            console.log('🧹 All temp files cleaned up');
        } catch (error) {
            console.warn('⚠️ Shutdown cleanup failed:', error.message);
        }
    }
}

module.exports = SecureFileProcessor;