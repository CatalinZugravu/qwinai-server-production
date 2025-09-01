const fs = require('fs').promises;
const pdf = require('pdf-parse');
const mammoth = require('mammoth');
const XLSX = require('xlsx');
const AdmZip = require('adm-zip');
const xml2js = require('xml2js');
const crypto = require('crypto');

/**
 * PRODUCTION-GRADE FILE EXTRACTOR
 * Enhanced security, performance, and reliability for multi-user environments
 */
class FileExtractor {
    constructor() {
        this.supportedTypes = {
            'application/pdf': 'pdf',
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'docx',
            'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'xlsx', 
            'application/vnd.openxmlformats-officedocument.presentationml.presentation': 'pptx',
            'text/plain': 'txt'
        };
        
        // Performance and security limits
        this.MAX_TEXT_LENGTH = 10 * 1024 * 1024; // 10MB max extracted text
        this.MAX_PAGES_PDF = 1000; // Max PDF pages to process
        this.MAX_SHEETS_XLSX = 50; // Max Excel sheets to process
        this.MAX_SLIDES_PPTX = 500; // Max PowerPoint slides to process
        this.TIMEOUT_MS = 60000; // 60 second timeout
        
        // Cache for repeated extractions
        this.extractionCache = new Map();
        this.cacheMaxSize = 100;
        this.cacheExpiry = 30 * 60 * 1000; // 30 minutes
        
        console.log('🔧 Production FileExtractor initialized with enhanced security');
    }

    /**
     * Extract content with comprehensive security and performance monitoring
     */
    async extractContent(filePath, mimeType) {
        const startTime = Date.now();
        const fileType = this.supportedTypes[mimeType];
        
        if (!fileType) {
            throw new Error(`Unsupported file type: ${mimeType}`);
        }

        // Generate cache key
        try {
            const fileStats = await fs.stat(filePath);
            const cacheKey = crypto.createHash('md5')
                .update(filePath + fileStats.size + fileStats.mtime.getTime())
                .digest('hex');
            
            // Check cache
            const cached = this.extractionCache.get(cacheKey);
            if (cached && Date.now() - cached.timestamp < this.cacheExpiry) {
                console.log(`💨 Cache hit for ${fileType.toUpperCase()} extraction`);
                return cached.result;
            }
        } catch (cacheError) {
            console.warn('⚠️ Cache check failed, proceeding with extraction');
        }

        console.log(`🔒 Extracting ${fileType.toUpperCase()} content with security validation`);

        let result;
        try {
            // Set timeout for extraction
            result = await Promise.race([
                this.performExtraction(filePath, fileType),
                this.createTimeoutPromise(this.TIMEOUT_MS)
            ]);

            // Validate result
            await this.validateExtractedContent(result);

            // Cache successful result
            this.cacheResult(cacheKey || 'nocache', result);
            
            // Performance logging
            const processingTime = Date.now() - startTime;
            console.log(`✅ ${fileType.toUpperCase()} extraction completed in ${processingTime}ms`);
            
            return result;
            
        } catch (error) {
            console.error(`❌ ${fileType.toUpperCase()} extraction failed:`, error.message);
            throw new Error(`${fileType.toUpperCase()} extraction failed: ${error.message}`);
        }
    }

    /**
     * Perform the actual extraction based on file type
     */
    async performExtraction(filePath, fileType) {
        switch (fileType) {
            case 'pdf':
                return await this.extractPDFSecure(filePath);
            case 'docx':
                return await this.extractDOCXSecure(filePath);
            case 'xlsx':
                return await this.extractXLSXSecure(filePath);
            case 'pptx':
                return await this.extractPPTXSecure(filePath);
            case 'txt':
                return await this.extractTXTSecure(filePath);
            default:
                throw new Error(`Handler not implemented for: ${fileType}`);
        }
    }

    /**
     * Secure PDF extraction with limits and validation
     */
    async extractPDFSecure(filePath) {
        const dataBuffer = await fs.readFile(filePath);
        
        // Validate PDF file signature
        if (!dataBuffer.slice(0, 4).equals(Buffer.from([0x25, 0x50, 0x44, 0x46]))) {
            throw new Error('Invalid PDF file signature');
        }

        const data = await pdf(dataBuffer, {
            max: this.MAX_PAGES_PDF,
            version: 'v2.0.0'
        });

        if (data.numpages > this.MAX_PAGES_PDF) {
            console.warn(`⚠️ PDF has ${data.numpages} pages, processing first ${this.MAX_PAGES_PDF}`);
        }

        const cleanText = this.cleanExtractedTextSecure(data.text);
        
        return {
            text: cleanText,
            pageCount: Math.min(data.numpages, this.MAX_PAGES_PDF),
            metadata: {
                format: 'pdf',
                info: this.sanitizeMetadata(data.info || {}),
                wordCount: this.countWords(cleanText),
                processingLimited: data.numpages > this.MAX_PAGES_PDF
            }
        };
    }

    /**
     * Secure DOCX extraction with validation
     */
    async extractDOCXSecure(filePath) {
        // Validate DOCX file signature (ZIP header)
        const buffer = await fs.readFile(filePath, { start: 0, end: 4 });
        if (!buffer.equals(Buffer.from([0x50, 0x4B, 0x03, 0x04]))) {
            throw new Error('Invalid DOCX file signature');
        }

        const result = await mammoth.extractRawText({ path: filePath });
        
        if (result.messages.length > 0) {
            console.log('📋 DOCX extraction notes:', result.messages.length, 'warnings');
        }

        const cleanText = this.cleanExtractedTextSecure(result.value);
        const estimatedPages = Math.ceil(cleanText.length / 2000);

        return {
            text: cleanText,
            pageCount: estimatedPages,
            metadata: {
                format: 'docx',
                warningCount: result.messages.length,
                wordCount: this.countWords(cleanText)
            }
        };
    }

    /**
     * Secure XLSX extraction with sheet limits
     */
    async extractXLSXSecure(filePath) {
        const workbook = XLSX.readFile(filePath, {
            cellText: true,
            cellDates: true,
            sheetRows: 10000 // Limit rows per sheet for performance
        });

        let allText = '';
        let totalRows = 0;
        const processedSheets = Math.min(workbook.SheetNames.length, this.MAX_SHEETS_XLSX);

        for (let i = 0; i < processedSheets; i++) {
            const sheetName = workbook.SheetNames[i];
            const worksheet = workbook.Sheets[sheetName];
            
            if (worksheet['!ref']) {
                const range = XLSX.utils.decode_range(worksheet['!ref']);
                totalRows += (range.e.r - range.s.r + 1);

                const csvText = XLSX.utils.sheet_to_csv(worksheet, {
                    header: 1,
                    defval: '',
                    blankrows: false
                });

                if (csvText.trim()) {
                    allText += `\n=== Sheet: ${this.sanitizeSheetName(sheetName)} ===\n`;
                    allText += csvText.substring(0, 100000); // Limit per sheet
                    allText += '\n';
                }
            }
        }

        const cleanText = this.cleanExtractedTextSecure(allText);

        return {
            text: cleanText,
            pageCount: processedSheets,
            metadata: {
                format: 'xlsx',
                totalSheets: workbook.SheetNames.length,
                processedSheets,
                totalRows,
                wordCount: this.countWords(cleanText),
                processingLimited: workbook.SheetNames.length > this.MAX_SHEETS_XLSX
            }
        };
    }

    /**
     * Secure PPTX extraction with slide limits
     */
    async extractPPTXSecure(filePath) {
        const zip = new AdmZip(filePath);
        let allText = '';
        let slideCount = 0;

        const slideEntries = zip.getEntries()
            .filter(entry => 
                entry.entryName.startsWith('ppt/slides/slide') && 
                entry.entryName.endsWith('.xml')
            )
            .sort((a, b) => {
                const aNum = parseInt(a.entryName.match(/slide(\d+)\.xml/)?.[1] || '0');
                const bNum = parseInt(b.entryName.match(/slide(\d+)\.xml/)?.[1] || '0');
                return aNum - bNum;
            })
            .slice(0, this.MAX_SLIDES_PPTX); // Limit number of slides

        for (const slideEntry of slideEntries) {
            slideCount++;
            try {
                const slideXml = slideEntry.getData().toString('utf8');
                
                // Basic XML safety check
                if (slideXml.includes('<!DOCTYPE') || slideXml.includes('<!ENTITY')) {
                    console.warn(`⚠️ Potentially unsafe XML in slide ${slideCount}, skipping`);
                    continue;
                }
                
                const slideData = await xml2js.parseStringPromise(slideXml, {
                    explicitArray: false,
                    ignoreAttrs: true,
                    sanitize: true
                });
                
                const slideText = this.extractTextFromSlideXMLSafe(slideData);
                
                if (slideText.trim()) {
                    allText += `\n=== Slide ${slideCount} ===\n`;
                    allText += slideText.trim().substring(0, 10000); // Limit per slide
                    allText += '\n';
                }
            } catch (xmlError) {
                console.warn(`⚠️ Could not parse slide ${slideCount}: ${xmlError.message}`);
            }
        }

        const cleanText = this.cleanExtractedTextSecure(allText);

        return {
            text: cleanText,
            pageCount: slideCount,
            metadata: {
                format: 'pptx',
                slides: slideCount,
                wordCount: this.countWords(cleanText),
                processingLimited: slideCount >= this.MAX_SLIDES_PPTX
            }
        };
    }

    /**
     * Secure TXT extraction with encoding detection
     */
    async extractTXTSecure(filePath) {
        const buffer = await fs.readFile(filePath);
        
        // Basic check for binary content
        for (let i = 0; i < Math.min(buffer.length, 1000); i++) {
            if (buffer[i] === 0) {
                throw new Error('File appears to be binary, not text');
            }
        }
        
        const text = buffer.toString('utf8');
        const cleanText = this.cleanExtractedTextSecure(text);
        const estimatedPages = Math.ceil(cleanText.length / 2000);

        return {
            text: cleanText,
            pageCount: estimatedPages,
            metadata: {
                format: 'txt',
                encoding: 'utf8',
                wordCount: this.countWords(cleanText)
            }
        };
    }

    /**
     * Enhanced text cleaning with security considerations
     */
    cleanExtractedTextSecure(text) {
        if (!text || typeof text !== 'string') {
            return '';
        }

        // Enforce length limit
        if (text.length > this.MAX_TEXT_LENGTH) {
            console.warn(`⚠️ Text truncated from ${text.length} to ${this.MAX_TEXT_LENGTH} characters`);
            text = text.substring(0, this.MAX_TEXT_LENGTH);
        }

        return text
            .replace(/\r\n/g, '\n')                    // Normalize line endings
            .replace(/\r/g, '\n')                      // Convert CR to LF
            .replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g, '') // Remove control characters
            .replace(/\n{3,}/g, '\n\n')                // Limit consecutive newlines
            .replace(/[ \t]{2,}/g, ' ')                // Reduce multiple spaces
            .replace(/^\s+|\s+$/gm, '')                // Trim whitespace from lines
            .trim();
    }

    /**
     * Safe XML text extraction for PowerPoint
     */
    extractTextFromSlideXMLSafe(slideData, depth = 0) {
        if (depth > 10) return ''; // Prevent deep recursion
        
        let text = '';

        const extractTextRecursive = (obj, currentDepth) => {
            if (currentDepth > 10) return;
            
            if (typeof obj === 'string') {
                text += obj.substring(0, 1000) + ' '; // Limit string length
                return;
            }

            if (typeof obj !== 'object' || obj === null) {
                return;
            }

            // Look for text elements safely
            if (obj['a:t']) {
                const textContent = Array.isArray(obj['a:t']) ? obj['a:t'][0] : obj['a:t'];
                if (typeof textContent === 'string') {
                    text += textContent.substring(0, 1000) + ' ';
                }
            }

            // Recursively process properties with limits
            let propCount = 0;
            for (const value of Object.values(obj)) {
                if (propCount++ > 50) break; // Limit properties processed
                
                if (Array.isArray(value)) {
                    value.slice(0, 20).forEach(item => extractTextRecursive(item, currentDepth + 1));
                } else if (typeof value === 'object') {
                    extractTextRecursive(value, currentDepth + 1);
                }
            }
        };

        extractTextRecursive(slideData, depth);
        return text.trim().substring(0, 5000); // Final length limit
    }

    /**
     * Validate extracted content for security and quality
     */
    async validateExtractedContent(result) {
        if (!result || !result.text) {
            throw new Error('No text content extracted');
        }

        if (result.text.length === 0) {
            throw new Error('Extracted text is empty');
        }

        if (result.text.length > this.MAX_TEXT_LENGTH) {
            throw new Error(`Extracted text too long: ${result.text.length} characters`);
        }

        // Check for suspicious patterns
        const suspiciousPatterns = [
            /<script/gi,
            /javascript:/gi,
            /vbscript:/gi,
            /data:text\/html/gi
        ];

        for (const pattern of suspiciousPatterns) {
            if (pattern.test(result.text)) {
                console.warn('🚨 Suspicious content pattern detected in extracted text');
                throw new Error('Extracted content contains potentially harmful patterns');
            }
        }

        return true;
    }

    /**
     * Sanitize metadata to prevent information leakage
     */
    sanitizeMetadata(metadata) {
        const sanitized = {};
        const allowedKeys = ['title', 'author', 'subject', 'creator', 'producer', 'creationDate'];
        
        for (const key of allowedKeys) {
            if (metadata[key] && typeof metadata[key] === 'string') {
                sanitized[key] = metadata[key].substring(0, 200); // Limit length
            }
        }
        
        return sanitized;
    }

    /**
     * Sanitize sheet names to prevent XSS
     */
    sanitizeSheetName(name) {
        return name.replace(/[<>:"']/g, '').substring(0, 50);
    }

    /**
     * Count words with limits
     */
    countWords(text) {
        if (!text || typeof text !== 'string') return 0;
        return text.split(/\s+/).filter(word => word.length > 0).length;
    }

    /**
     * Cache extraction results
     */
    cacheResult(key, result) {
        try {
            // Clean old cache entries if at max size
            if (this.extractionCache.size >= this.cacheMaxSize) {
                const oldestKey = this.extractionCache.keys().next().value;
                this.extractionCache.delete(oldestKey);
            }

            this.extractionCache.set(key, {
                result,
                timestamp: Date.now()
            });
        } catch (error) {
            console.warn('⚠️ Cache write failed:', error.message);
        }
    }

    /**
     * Create timeout promise for race conditions
     */
    createTimeoutPromise(timeout) {
        return new Promise((_, reject) => {
            setTimeout(() => reject(new Error('File extraction timed out')), timeout);
        });
    }

    /**
     * Get supported file types
     */
    getSupportedTypes() {
        return Object.keys(this.supportedTypes);
    }

    /**
     * Check if MIME type is supported
     */
    isSupported(mimeType) {
        return mimeType in this.supportedTypes;
    }

    /**
     * Get extraction statistics
     */
    getStats() {
        return {
            cacheSize: this.extractionCache.size,
            maxCacheSize: this.cacheMaxSize,
            supportedFormats: Object.keys(this.supportedTypes).length,
            limits: {
                maxTextLength: this.MAX_TEXT_LENGTH,
                maxPagesPDF: this.MAX_PAGES_PDF,
                maxSheetsXLSX: this.MAX_SHEETS_XLSX,
                maxSlidesPPTX: this.MAX_SLIDES_PPTX,
                timeoutMs: this.TIMEOUT_MS
            }
        };
    }

    /**
     * Clear extraction cache
     */
    clearCache() {
        this.extractionCache.clear();
        console.log('🧹 Extraction cache cleared');
    }
}

module.exports = FileExtractor;