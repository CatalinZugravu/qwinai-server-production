const fs = require('fs').promises;
const pdf = require('pdf-parse');
const mammoth = require('mammoth');
const XLSX = require('xlsx');
const AdmZip = require('adm-zip');
const xml2js = require('xml2js');
const crypto = require('crypto');

/**
 * PROFESSIONAL-GRADE FILE EXTRACTOR v2.0
 * 🎯 COMPLETELY REWRITTEN TO FIX BINARY CONTENT BUG
 * ✅ Guaranteed to return extracted text, never binary content
 * 🛡️ Enhanced error handling with detailed logging
 * 🚀 Optimized for production reliability
 */
class FileExtractor {
    constructor() {
        this.supportedTypes = {
            'application/pdf': 'pdf',
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'docx',
            'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'xlsx', 
            'application/vnd.openxmlformats-officedocument.presentationml.presentation': 'pptx',
            'text/plain': 'txt',
            'text/csv': 'csv',
            'application/rtf': 'rtf'
        };
        
        // Enhanced limits for production
        this.MAX_TEXT_LENGTH = 10 * 1024 * 1024; // 10MB max extracted text
        this.MAX_PAGES_PDF = 1000;
        this.MAX_SHEETS_XLSX = 50;
        this.MAX_SLIDES_PPTX = 500;
        this.TIMEOUT_MS = 120000; // 2 minutes timeout
        this.MIN_TEXT_LENGTH = 10; // Minimum text to consider valid extraction
        
        // Cache for extraction results
        this.extractionCache = new Map();
        this.cacheMaxSize = 50;
        this.cacheExpiry = 15 * 60 * 1000; // 15 minutes
        
        console.log('🚀 Professional FileExtractor v2.0 initialized - BINARY BUG FIXED');
    }

    /**
     * 🎯 MAIN EXTRACTION METHOD - GUARANTEED TO RETURN TEXT, NEVER BINARY
     */
    async extractContent(filePath, mimeType) {
        const startTime = Date.now();
        const extractionId = crypto.randomUUID().substring(0, 8);
        
        console.log(`🔍 [${extractionId}] Starting extraction: ${mimeType}`);
        
        const fileType = this.supportedTypes[mimeType];
        if (!fileType) {
            throw new Error(`❌ Unsupported file type: ${mimeType}`);
        }

        // Generate cache key
        let cacheKey = null;
        try {
            const fileStats = await fs.stat(filePath);
            cacheKey = crypto.createHash('md5')
                .update(filePath + fileStats.size + fileStats.mtime.getTime())
                .digest('hex');
            
            // Check cache first
            const cached = this.extractionCache.get(cacheKey);
            if (cached && Date.now() - cached.timestamp < this.cacheExpiry) {
                console.log(`💨 [${extractionId}] Cache hit for ${fileType.toUpperCase()}`);
                return cached.result;
            }
        } catch (cacheError) {
            console.warn(`⚠️ [${extractionId}] Cache check failed, proceeding with extraction`);
        }

        let result = null;
        try {
            console.log(`🔧 [${extractionId}] Extracting ${fileType.toUpperCase()} content...`);
            
            // Perform extraction with timeout protection
            result = await Promise.race([
                this.performRobustExtraction(filePath, fileType, extractionId),
                this.createTimeoutPromise(this.TIMEOUT_MS, `${fileType.toUpperCase()} extraction timed out after ${this.TIMEOUT_MS/1000}s`)
            ]);

            // 🛡️ CRITICAL VALIDATION: Ensure we never return binary content
            await this.validateExtractedContentStrict(result, extractionId);

            // Cache successful result
            if (cacheKey) {
                this.cacheResult(cacheKey, result);
            }
            
            const processingTime = Date.now() - startTime;
            console.log(`✅ [${extractionId}] ${fileType.toUpperCase()} extraction completed in ${processingTime}ms - ${result.text.length} chars extracted`);
            
            return result;
            
        } catch (error) {
            console.error(`❌ [${extractionId}] ${fileType?.toUpperCase() || 'Unknown'} extraction failed:`, error.message);
            
            // 🚨 CRITICAL: If extraction fails, return error message instead of binary
            return {
                text: `❌ Content extraction failed: ${error.message}. File type: ${fileType || 'unknown'}. Please try a different file format.`,
                pageCount: 0,
                metadata: {
                    format: fileType || 'unknown',
                    extractionError: true,
                    errorMessage: error.message,
                    timestamp: new Date().toISOString()
                }
            };
        }
    }

    /**
     * 🔧 ROBUST EXTRACTION DISPATCHER
     */
    async performRobustExtraction(filePath, fileType, extractionId) {
        switch (fileType) {
            case 'pdf':
                return await this.extractPDFRobust(filePath, extractionId);
            case 'docx':
                return await this.extractDOCXRobust(filePath, extractionId);
            case 'xlsx':
                return await this.extractXLSXRobust(filePath, extractionId);
            case 'pptx':
                return await this.extractPPTXRobust(filePath, extractionId);
            case 'txt':
            case 'csv':
                return await this.extractTXTRobust(filePath, extractionId);
            case 'rtf':
                return await this.extractRTFRobust(filePath, extractionId);
            default:
                throw new Error(`❌ Handler not implemented for: ${fileType}`);
        }
    }

    /**
     * 📄 PROFESSIONAL PDF EXTRACTION
     */
    async extractPDFRobust(filePath, extractionId) {
        console.log(`📄 [${extractionId}] Processing PDF...`);
        
        const dataBuffer = await fs.readFile(filePath);
        
        // Validate PDF signature
        const pdfSignature = [0x25, 0x50, 0x44, 0x46]; // %PDF
        const fileSignature = Array.from(dataBuffer.slice(0, 4));
        
        if (!pdfSignature.every((byte, index) => byte === fileSignature[index])) {
            throw new Error('Invalid PDF file signature - file may be corrupted');
        }

        console.log(`📄 [${extractionId}] PDF signature validated, extracting text...`);

        const data = await pdf(dataBuffer, {
            max: this.MAX_PAGES_PDF,
            version: 'v2.0.0'
        });

        if (!data.text || data.text.trim().length < this.MIN_TEXT_LENGTH) {
            console.warn(`⚠️ [${extractionId}] PDF extraction returned minimal text: ${data.text?.length || 0} chars`);
            
            if (data.numpages > 0) {
                return {
                    text: `📄 PDF Document (${data.numpages} pages)\n\n⚠️ This PDF contains mostly images, scanned content, or formatting that cannot be extracted as text. The document has ${data.numpages} pages but minimal readable text content.`,
                    pageCount: data.numpages,
                    metadata: {
                        format: 'pdf',
                        pages: data.numpages,
                        extractionNote: 'Minimal text content - mostly images/formatting',
                        wordCount: 0
                    }
                };
            } else {
                throw new Error('PDF appears to be empty or corrupted - no pages found');
            }
        }

        const cleanText = this.cleanExtractedText(data.text);
        
        console.log(`📄 [${extractionId}] PDF extraction successful: ${cleanText.length} chars from ${data.numpages} pages`);

        return {
            text: cleanText,
            pageCount: Math.min(data.numpages, this.MAX_PAGES_PDF),
            metadata: {
                format: 'pdf',
                pages: data.numpages,
                info: this.sanitizeMetadata(data.info || {}),
                wordCount: this.countWords(cleanText),
                processingLimited: data.numpages > this.MAX_PAGES_PDF
            }
        };
    }

    /**
     * 📝 PROFESSIONAL DOCX EXTRACTION - FIXES THE BINARY BUG
     */
    async extractDOCXRobust(filePath, extractionId) {
        console.log(`📝 [${extractionId}] Processing DOCX...`);
        
        // Validate DOCX file signature (ZIP header)
        const buffer = await fs.readFile(filePath, { length: 4 });
        const zipSignature = [0x50, 0x4B, 0x03, 0x04]; // PK..
        const fileSignature = Array.from(buffer.slice(0, 4));
        
        if (!zipSignature.every((byte, index) => byte === fileSignature[index])) {
            throw new Error('Invalid DOCX file signature - file may be corrupted or not a valid DOCX');
        }

        console.log(`📝 [${extractionId}] DOCX signature validated, extracting with Mammoth...`);

        // 🔧 CRITICAL FIX: Use mammoth with explicit error handling
        let mammothResult;
        try {
            mammothResult = await mammoth.extractRawText({ 
                path: filePath,
                convertImage: mammoth.images.imgElement(function() {
                    return { src: "[IMAGE]" };
                })
            });
            
            console.log(`📝 [${extractionId}] Mammoth extraction completed. Text length: ${mammothResult.value?.length || 0}`);
            
            if (mammothResult.messages && mammothResult.messages.length > 0) {
                console.log(`📝 [${extractionId}] Mammoth messages:`, mammothResult.messages.map(m => m.message).join('; '));
            }
            
        } catch (mammothError) {
            console.error(`❌ [${extractionId}] Mammoth extraction failed:`, mammothError.message);
            throw new Error(`DOCX text extraction failed: ${mammothError.message}`);
        }

        // 🛡️ CRITICAL VALIDATION: Ensure mammoth returned actual text
        if (!mammothResult || typeof mammothResult.value !== 'string') {
            throw new Error('DOCX extraction failed - mammoth returned invalid data structure');
        }

        if (mammothResult.value.trim().length < this.MIN_TEXT_LENGTH) {
            console.warn(`⚠️ [${extractionId}] DOCX extraction returned minimal text: ${mammothResult.value.length} chars`);
            
            // Return meaningful message instead of empty/binary content
            return {
                text: `📝 Word Document\n\n⚠️ This document contains mostly formatting, images, or complex layouts that cannot be extracted as plain text. Please ensure the document contains readable text content.`,
                pageCount: 1,
                metadata: {
                    format: 'docx',
                    extractionNote: 'Minimal text content detected',
                    warnings: mammothResult.messages || [],
                    wordCount: 0
                }
            };
        }

        const cleanText = this.cleanExtractedText(mammothResult.value);
        const estimatedPages = Math.max(1, Math.ceil(cleanText.length / 2000));

        console.log(`📝 [${extractionId}] DOCX extraction successful: ${cleanText.length} chars, ~${estimatedPages} pages`);

        return {
            text: cleanText,
            pageCount: estimatedPages,
            metadata: {
                format: 'docx',
                warnings: mammothResult.messages || [],
                warningCount: (mammothResult.messages || []).length,
                wordCount: this.countWords(cleanText)
            }
        };
    }

    /**
     * 📊 PROFESSIONAL XLSX EXTRACTION
     */
    async extractXLSXRobust(filePath, extractionId) {
        console.log(`📊 [${extractionId}] Processing XLSX...`);

        let workbook;
        try {
            workbook = XLSX.readFile(filePath, {
                cellText: true,
                cellDates: true,
                sheetRows: 10000, // Limit for performance
                raw: false // Ensure we get formatted text, not raw values
            });
        } catch (xlsxError) {
            console.error(`❌ [${extractionId}] XLSX reading failed:`, xlsxError.message);
            throw new Error(`Excel file reading failed: ${xlsxError.message}`);
        }

        if (!workbook.SheetNames || workbook.SheetNames.length === 0) {
            throw new Error('Excel file contains no worksheets');
        }

        let allText = '';
        let totalRows = 0;
        const processedSheets = Math.min(workbook.SheetNames.length, this.MAX_SHEETS_XLSX);

        console.log(`📊 [${extractionId}] Processing ${processedSheets}/${workbook.SheetNames.length} sheets...`);

        for (let i = 0; i < processedSheets; i++) {
            const sheetName = workbook.SheetNames[i];
            const worksheet = workbook.Sheets[sheetName];
            
            console.log(`📊 [${extractionId}] Processing sheet: ${sheetName}`);
            
            if (!worksheet || !worksheet['!ref']) {
                console.log(`📊 [${extractionId}] Sheet ${sheetName} is empty, skipping`);
                continue;
            }

            try {
                const range = XLSX.utils.decode_range(worksheet['!ref']);
                const sheetRows = (range.e.r - range.s.r + 1);
                totalRows += sheetRows;

                const csvText = XLSX.utils.sheet_to_csv(worksheet, {
                    header: 1,
                    defval: '',
                    blankrows: false,
                    skipHidden: true
                });

                if (csvText && csvText.trim()) {
                    allText += `\n=== Excel Sheet: ${this.sanitizeSheetName(sheetName)} (${sheetRows} rows) ===\n`;
                    allText += csvText.substring(0, 50000); // Limit per sheet
                    allText += '\n';
                    
                    console.log(`📊 [${extractionId}] Sheet ${sheetName}: ${csvText.length} chars extracted`);
                } else {
                    console.log(`📊 [${extractionId}] Sheet ${sheetName}: No text content`);
                }
            } catch (sheetError) {
                console.warn(`⚠️ [${extractionId}] Error processing sheet ${sheetName}:`, sheetError.message);
                allText += `\n=== Excel Sheet: ${sheetName} ===\n⚠️ Error processing this sheet: ${sheetError.message}\n`;
            }
        }

        if (!allText || allText.trim().length < this.MIN_TEXT_LENGTH) {
            return {
                text: `📊 Excel Spreadsheet (${workbook.SheetNames.length} sheets)\n\n⚠️ This spreadsheet contains mostly formatting, formulas, or data that cannot be extracted as readable text. Total sheets: ${workbook.SheetNames.length}, processed: ${processedSheets}.`,
                pageCount: processedSheets,
                metadata: {
                    format: 'xlsx',
                    totalSheets: workbook.SheetNames.length,
                    processedSheets,
                    extractionNote: 'Minimal text content detected',
                    totalRows,
                    wordCount: 0
                }
            };
        }

        const cleanText = this.cleanExtractedText(allText);
        
        console.log(`📊 [${extractionId}] XLSX extraction successful: ${cleanText.length} chars from ${processedSheets} sheets`);

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
     * 🎨 PROFESSIONAL PPTX EXTRACTION
     */
    async extractPPTXRobust(filePath, extractionId) {
        console.log(`🎨 [${extractionId}] Processing PPTX...`);

        let zip;
        try {
            zip = new AdmZip(filePath);
        } catch (zipError) {
            console.error(`❌ [${extractionId}] PPTX ZIP reading failed:`, zipError.message);
            throw new Error(`PowerPoint file reading failed: ${zipError.message}`);
        }

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
            .slice(0, this.MAX_SLIDES_PPTX);

        console.log(`🎨 [${extractionId}] Found ${slideEntries.length} slides to process`);

        for (const slideEntry of slideEntries) {
            slideCount++;
            
            try {
                const slideXml = slideEntry.getData().toString('utf8');
                
                // Basic XML safety validation
                if (slideXml.includes('<!DOCTYPE') || slideXml.includes('<!ENTITY')) {
                    console.warn(`⚠️ [${extractionId}] Slide ${slideCount} contains unsafe XML patterns, skipping`);
                    continue;
                }
                
                const slideData = await xml2js.parseStringPromise(slideXml, {
                    explicitArray: false,
                    ignoreAttrs: true,
                    sanitize: true,
                    trim: true
                });
                
                const slideText = this.extractTextFromSlideXML(slideData);
                
                if (slideText && slideText.trim()) {
                    allText += `\n=== PowerPoint Slide ${slideCount} ===\n`;
                    allText += slideText.trim().substring(0, 5000); // Limit per slide
                    allText += '\n';
                    
                    console.log(`🎨 [${extractionId}] Slide ${slideCount}: ${slideText.length} chars extracted`);
                } else {
                    console.log(`🎨 [${extractionId}] Slide ${slideCount}: No text content`);
                }
            } catch (slideError) {
                console.warn(`⚠️ [${extractionId}] Error processing slide ${slideCount}:`, slideError.message);
                allText += `\n=== PowerPoint Slide ${slideCount} ===\n⚠️ Error processing this slide: ${slideError.message}\n`;
            }
        }

        if (!allText || allText.trim().length < this.MIN_TEXT_LENGTH) {
            return {
                text: `🎨 PowerPoint Presentation (${slideCount} slides)\n\n⚠️ This presentation contains mostly images, graphics, or formatting that cannot be extracted as text. Total slides processed: ${slideCount}.`,
                pageCount: slideCount,
                metadata: {
                    format: 'pptx',
                    slides: slideCount,
                    extractionNote: 'Minimal text content detected',
                    wordCount: 0
                }
            };
        }

        const cleanText = this.cleanExtractedText(allText);
        
        console.log(`🎨 [${extractionId}] PPTX extraction successful: ${cleanText.length} chars from ${slideCount} slides`);

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
     * 📄 TEXT FILE EXTRACTION
     */
    async extractTXTRobust(filePath, extractionId) {
        console.log(`📄 [${extractionId}] Processing text file...`);
        
        const buffer = await fs.readFile(filePath);
        
        // Enhanced binary detection
        let binaryByteCount = 0;
        const sampleSize = Math.min(buffer.length, 2000);
        
        for (let i = 0; i < sampleSize; i++) {
            const byte = buffer[i];
            // Check for null bytes and non-printable characters
            if (byte === 0 || (byte < 32 && byte !== 9 && byte !== 10 && byte !== 13)) {
                binaryByteCount++;
            }
        }
        
        // If more than 1% of sampled bytes are binary, reject
        if (binaryByteCount > sampleSize * 0.01) {
            throw new Error(`File appears to contain binary data (${binaryByteCount}/${sampleSize} non-text bytes detected)`);
        }
        
        const text = buffer.toString('utf8');
        const cleanText = this.cleanExtractedText(text);
        const estimatedPages = Math.max(1, Math.ceil(cleanText.length / 2000));

        console.log(`📄 [${extractionId}] Text extraction successful: ${cleanText.length} chars, ~${estimatedPages} pages`);

        return {
            text: cleanText,
            pageCount: estimatedPages,
            metadata: {
                format: 'txt',
                encoding: 'utf8',
                fileSize: buffer.length,
                wordCount: this.countWords(cleanText)
            }
        };
    }

    /**
     * 📝 RTF FILE EXTRACTION
     */
    async extractRTFRobust(filePath, extractionId) {
        console.log(`📝 [${extractionId}] Processing RTF file...`);
        
        const buffer = await fs.readFile(filePath);
        const content = buffer.toString('utf8');
        
        // Basic RTF validation
        if (!content.startsWith('{\\rtf')) {
            throw new Error('Invalid RTF file format - file does not start with RTF signature');
        }
        
        // Simple RTF text extraction (remove RTF formatting codes)
        let text = content
            .replace(/\{\\[^}]*\}/g, '') // Remove RTF control groups
            .replace(/\\[a-z]+\d*\s?/g, '') // Remove RTF control words
            .replace(/\{|\}/g, '') // Remove braces
            .replace(/\\\\/g, '\\') // Unescape backslashes
            .replace(/\\'/g, "'") // Unescape quotes
            .trim();
        
        const cleanText = this.cleanExtractedText(text);
        const estimatedPages = Math.max(1, Math.ceil(cleanText.length / 2000));

        console.log(`📝 [${extractionId}] RTF extraction successful: ${cleanText.length} chars, ~${estimatedPages} pages`);

        return {
            text: cleanText,
            pageCount: estimatedPages,
            metadata: {
                format: 'rtf',
                originalSize: buffer.length,
                wordCount: this.countWords(cleanText)
            }
        };
    }

    /**
     * 🧹 PROFESSIONAL TEXT CLEANING
     */
    cleanExtractedText(text) {
        if (!text || typeof text !== 'string') {
            return '';
        }

        // Enforce length limit with warning
        if (text.length > this.MAX_TEXT_LENGTH) {
            console.warn(`⚠️ Text truncated from ${text.length} to ${this.MAX_TEXT_LENGTH} characters for safety`);
            text = text.substring(0, this.MAX_TEXT_LENGTH) + '\n\n[...text truncated for length...]';
        }

        return text
            .replace(/\r\n/g, '\n')                    // Normalize line endings
            .replace(/\r/g, '\n')                      // Convert CR to LF  
            .replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g, '') // Remove control characters
            .replace(/\n{4,}/g, '\n\n\n')              // Limit excessive newlines
            .replace(/[ \t]{3,}/g, '  ')               // Reduce excessive spaces
            .replace(/^\s+$/gm, '')                    // Remove whitespace-only lines
            .trim();
    }

    /**
     * 🎨 SAFE XML TEXT EXTRACTION FOR POWERPOINT
     */
    extractTextFromSlideXML(slideData, depth = 0) {
        if (depth > 8) return ''; // Prevent deep recursion
        
        let text = '';

        const extractRecursively = (obj, currentDepth) => {
            if (currentDepth > 8 || !obj) return;
            
            if (typeof obj === 'string') {
                // Limit individual string length
                text += obj.substring(0, 500) + ' ';
                return;
            }

            if (typeof obj !== 'object') return;

            // Extract text from PowerPoint text elements
            if (obj['a:t']) {
                const textContent = Array.isArray(obj['a:t']) ? obj['a:t'][0] : obj['a:t'];
                if (typeof textContent === 'string') {
                    text += textContent.substring(0, 500) + ' ';
                }
            }

            // Recursively process with limits
            let propCount = 0;
            for (const [key, value] of Object.entries(obj)) {
                if (propCount++ > 30) break; // Limit properties
                
                if (Array.isArray(value)) {
                    value.slice(0, 10).forEach(item => extractRecursively(item, currentDepth + 1));
                } else {
                    extractRecursively(value, currentDepth + 1);
                }
            }
        };

        extractRecursively(slideData, depth);
        return text.trim().substring(0, 3000); // Final limit
    }

    /**
     * 🛡️ STRICT CONTENT VALIDATION - PREVENTS BINARY CONTENT
     */
    async validateExtractedContentStrict(result, extractionId) {
        if (!result || typeof result !== 'object') {
            throw new Error('Extraction returned invalid result structure');
        }

        if (!result.text || typeof result.text !== 'string') {
            throw new Error('Extraction returned no text content');
        }

        if (result.text.length === 0) {
            throw new Error('Extracted text is completely empty');
        }

        // 🚨 CRITICAL: Check for binary content indicators
        const binaryIndicators = [
            result.text.startsWith('PK'),           // ZIP header
            result.text.startsWith('%PDF'),         // PDF header  
            result.text.includes('\u0000'),         // Null bytes
            result.text.match(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/), // Control chars
        ];

        if (binaryIndicators.some(indicator => indicator)) {
            console.error(`🚨 [${extractionId}] BINARY CONTENT DETECTED IN EXTRACTION RESULT!`);
            console.error(`🚨 [${extractionId}] Text starts with: "${result.text.substring(0, 50)}"`);
            throw new Error('CRITICAL: Extraction returned binary content instead of text. This is a server-side extraction bug.');
        }

        // Check for reasonable text content
        const printableChars = result.text.replace(/\s/g, '').length;
        if (printableChars < this.MIN_TEXT_LENGTH) {
            console.warn(`⚠️ [${extractionId}] Very little printable content: ${printableChars} chars`);
        }

        console.log(`✅ [${extractionId}] Content validation passed - ${result.text.length} chars, no binary content detected`);
        return true;
    }

    /**
     * 🏷️ UTILITY METHODS
     */
    sanitizeMetadata(metadata) {
        const sanitized = {};
        const allowedKeys = ['title', 'author', 'subject', 'creator', 'producer', 'creationDate'];
        
        for (const key of allowedKeys) {
            if (metadata[key] && typeof metadata[key] === 'string') {
                sanitized[key] = metadata[key].substring(0, 100);
            }
        }
        return sanitized;
    }

    sanitizeSheetName(name) {
        return String(name).replace(/[<>:"']/g, '').substring(0, 30);
    }

    countWords(text) {
        if (!text || typeof text !== 'string') return 0;
        return text.split(/\s+/).filter(word => word.length > 0).length;
    }

    cacheResult(key, result) {
        try {
            if (this.extractionCache.size >= this.cacheMaxSize) {
                const oldestKey = this.extractionCache.keys().next().value;
                this.extractionCache.delete(oldestKey);
            }

            this.extractionCache.set(key, {
                result: result,
                timestamp: Date.now()
            });
        } catch (error) {
            console.warn('⚠️ Failed to cache extraction result:', error.message);
        }
    }

    createTimeoutPromise(timeout, message) {
        return new Promise((_, reject) => {
            setTimeout(() => reject(new Error(message)), timeout);
        });
    }

    getSupportedTypes() {
        return Object.keys(this.supportedTypes);
    }

    isSupported(mimeType) {
        return mimeType in this.supportedTypes;
    }

    getStats() {
        return {
            version: '2.0.0',
            cacheSize: this.extractionCache.size,
            maxCacheSize: this.cacheMaxSize,
            supportedFormats: Object.keys(this.supportedTypes).length,
            formats: Object.keys(this.supportedTypes),
            limits: {
                maxTextLength: this.MAX_TEXT_LENGTH,
                maxPagesPDF: this.MAX_PAGES_PDF,
                maxSheetsXLSX: this.MAX_SHEETS_XLSX,
                maxSlidesPPTX: this.MAX_SLIDES_PPTX,
                timeoutMs: this.TIMEOUT_MS,
                minTextLength: this.MIN_TEXT_LENGTH
            }
        };
    }

    clearCache() {
        this.extractionCache.clear();
        console.log('🧹 FileExtractor cache cleared');
    }
}

module.exports = FileExtractor;
