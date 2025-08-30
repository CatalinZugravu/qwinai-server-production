const fs = require('fs');
const pdf = require('pdf-parse');
const mammoth = require('mammoth');
const XLSX = require('xlsx');
const AdmZip = require('adm-zip');
const xml2js = require('xml2js');

/**
 * Advanced file content extraction for multiple formats
 * Handles PDF, DOCX, XLSX, PPTX, and TXT files
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
    }

    /**
     * Extract content from any supported file type
     */
    async extractContent(filePath, mimeType) {
        const fileType = this.supportedTypes[mimeType];
        
        if (!fileType) {
            throw new Error(`Unsupported file type: ${mimeType}`);
        }

        console.log(`📄 Extracting ${fileType.toUpperCase()} content from: ${filePath}`);

        switch (fileType) {
            case 'pdf':
                return await this.extractPDF(filePath);
            case 'docx':
                return await this.extractDOCX(filePath);
            case 'xlsx':
                return await this.extractXLSX(filePath);
            case 'pptx':
                return await this.extractPPTX(filePath);
            case 'txt':
            case 'csv':
                return await this.extractTXT(filePath);
            case 'rtf':
                return await this.extractRTF(filePath);
            default:
                throw new Error(`Handler not implemented for: ${fileType}`);
        }
    }

    /**
     * Extract content from PDF files
     */
    async extractPDF(filePath) {
        try {
            const dataBuffer = fs.readFileSync(filePath);
            const data = await pdf(dataBuffer, {
                // PDF parsing options
                max: 0, // Parse all pages
                version: 'v2.0.0'
            });

            // Clean up the extracted text
            const cleanText = this.cleanExtractedText(data.text);

            console.log(`✅ PDF extracted: ${data.numpages} pages, ${cleanText.length} characters`);

            return {
                text: cleanText,
                pageCount: data.numpages,
                metadata: {
                    format: 'pdf',
                    info: data.info || {},
                    wordCount: this.countWords(cleanText)
                }
            };
        } catch (error) {
            console.error('❌ PDF extraction failed:', error);
            throw new Error(`PDF extraction failed: ${error.message}`);
        }
    }

    /**
     * Extract content from DOCX files (Microsoft Word)
     */
    async extractDOCX(filePath) {
        try {
            // Extract raw text (faster) or rich text with formatting
            const result = await mammoth.extractRawText({path: filePath});
            
            if (result.messages.length > 0) {
                console.log('📋 DOCX extraction warnings:', result.messages);
            }

            const cleanText = this.cleanExtractedText(result.value);
            const estimatedPages = Math.ceil(cleanText.length / 2000); // ~2000 chars per page

            console.log(`✅ DOCX extracted: ~${estimatedPages} pages, ${cleanText.length} characters`);

            return {
                text: cleanText,
                pageCount: estimatedPages,
                metadata: {
                    format: 'docx',
                    warnings: result.messages,
                    wordCount: this.countWords(cleanText)
                }
            };
        } catch (error) {
            console.error('❌ DOCX extraction failed:', error);
            throw new Error(`DOCX extraction failed: ${error.message}`);
        }
    }

    /**
     * Extract content from XLSX files (Microsoft Excel)
     */
    async extractXLSX(filePath) {
        try {
            const workbook = XLSX.readFile(filePath, {
                cellText: true,
                cellDates: true
            });

            let allText = '';
            let totalRows = 0;

            // Process each worksheet
            workbook.SheetNames.forEach((sheetName, index) => {
                const worksheet = workbook.Sheets[sheetName];
                
                // Get sheet range
                const range = XLSX.utils.decode_range(worksheet['!ref'] || 'A1:A1');
                totalRows += (range.e.r - range.s.r + 1);

                // Convert to CSV format for better text representation
                const csvText = XLSX.utils.sheet_to_csv(worksheet, {
                    header: 1,
                    defval: '', // Default value for empty cells
                    blankrows: false
                });

                if (csvText.trim()) {
                    allText += `\n=== Sheet: ${sheetName} ===\n`;
                    allText += csvText;
                    allText += '\n';
                }
            });

            const cleanText = this.cleanExtractedText(allText);
            console.log(`✅ XLSX extracted: ${workbook.SheetNames.length} sheets, ${totalRows} rows, ${cleanText.length} characters`);

            return {
                text: cleanText,
                pageCount: workbook.SheetNames.length, // Each sheet = 1 "page"
                metadata: {
                    format: 'xlsx',
                    sheets: workbook.SheetNames,
                    totalRows: totalRows,
                    wordCount: this.countWords(cleanText)
                }
            };
        } catch (error) {
            console.error('❌ XLSX extraction failed:', error);
            throw new Error(`XLSX extraction failed: ${error.message}`);
        }
    }

    /**
     * Extract content from PPTX files (Microsoft PowerPoint)
     */
    async extractPPTX(filePath) {
        try {
            const zip = new AdmZip(filePath);
            let allText = '';
            let slideCount = 0;

            // Find all slide XML files
            const slideEntries = zip.getEntries()
                .filter(entry => 
                    entry.entryName.startsWith('ppt/slides/slide') && 
                    entry.entryName.endsWith('.xml')
                )
                .sort((a, b) => {
                    // Sort slides by number
                    const aNum = parseInt(a.entryName.match(/slide(\d+)\.xml/)[1]);
                    const bNum = parseInt(b.entryName.match(/slide(\d+)\.xml/)[1]);
                    return aNum - bNum;
                });

            // Process each slide
            for (const slideEntry of slideEntries) {
                slideCount++;
                const slideXml = slideEntry.getData().toString('utf8');
                
                try {
                    const slideData = await xml2js.parseStringPromise(slideXml);
                    const slideText = this.extractTextFromSlideXML(slideData);
                    
                    if (slideText.trim()) {
                        allText += `\n=== Slide ${slideCount} ===\n`;
                        allText += slideText.trim();
                        allText += '\n';
                    }
                } catch (xmlError) {
                    console.warn(`⚠️ Could not parse slide ${slideCount}: ${xmlError.message}`);
                }
            }

            // Also try to extract notes if available
            const notesEntries = zip.getEntries()
                .filter(entry => 
                    entry.entryName.startsWith('ppt/notesSlides/notesSlide') && 
                    entry.entryName.endsWith('.xml')
                );

            if (notesEntries.length > 0) {
                allText += '\n=== Speaker Notes ===\n';
                for (const noteEntry of notesEntries) {
                    try {
                        const noteXml = noteEntry.getData().toString('utf8');
                        const noteData = await xml2js.parseStringPromise(noteXml);
                        const noteText = this.extractTextFromSlideXML(noteData);
                        if (noteText.trim()) {
                            allText += noteText.trim() + '\n';
                        }
                    } catch (noteError) {
                        console.warn(`⚠️ Could not parse notes: ${noteError.message}`);
                    }
                }
            }

            const cleanText = this.cleanExtractedText(allText);
            console.log(`✅ PPTX extracted: ${slideCount} slides, ${cleanText.length} characters`);

            return {
                text: cleanText,
                pageCount: slideCount,
                metadata: {
                    format: 'pptx',
                    slides: slideCount,
                    hasNotes: notesEntries.length > 0,
                    wordCount: this.countWords(cleanText)
                }
            };
        } catch (error) {
            console.error('❌ PPTX extraction failed:', error);
            throw new Error(`PPTX extraction failed: ${error.message}`);
        }
    }

    /**
     * Extract text from PowerPoint slide XML
     */
    extractTextFromSlideXML(slideData) {
        let text = '';

        const extractTextRecursive = (obj) => {
            if (typeof obj === 'string') {
                text += obj + ' ';
                return;
            }

            if (typeof obj !== 'object' || obj === null) {
                return;
            }

            // Look for text elements
            if (obj['a:t']) {
                if (Array.isArray(obj['a:t'])) {
                    obj['a:t'].forEach(t => text += (t._ || t) + ' ');
                } else {
                    text += (obj['a:t']._ || obj['a:t']) + ' ';
                }
            }

            // Recursively process all properties
            Object.values(obj).forEach(value => {
                if (Array.isArray(value)) {
                    value.forEach(item => extractTextRecursive(item));
                } else if (typeof value === 'object') {
                    extractTextRecursive(value);
                }
            });
        };

        extractTextRecursive(slideData);
        return text.trim();
    }

    /**
     * Extract content from TXT/CSV files
     */
    async extractTXT(filePath) {
        try {
            const text = fs.readFileSync(filePath, 'utf8');
            const cleanText = this.cleanExtractedText(text);
            const estimatedPages = Math.ceil(cleanText.length / 2000);

            console.log(`✅ TXT extracted: ${cleanText.length} characters, ~${estimatedPages} pages`);

            return {
                text: cleanText,
                pageCount: estimatedPages,
                metadata: {
                    format: 'txt',
                    encoding: 'utf8',
                    wordCount: this.countWords(cleanText)
                }
            };
        } catch (error) {
            console.error('❌ TXT extraction failed:', error);
            throw new Error(`TXT extraction failed: ${error.message}`);
        }
    }

    /**
     * Basic RTF extraction (converts to plain text)
     */
    async extractRTF(filePath) {
        try {
            const rtfContent = fs.readFileSync(filePath, 'utf8');
            
            // Basic RTF to text conversion (removes RTF formatting codes)
            let text = rtfContent
                .replace(/\{\\[^}]*\}/g, '') // Remove RTF control groups
                .replace(/\\[a-z]+\d*\s?/gi, '') // Remove RTF control words
                .replace(/[{}]/g, '') // Remove remaining braces
                .replace(/\\\'/g, "'") // Decode some escaped characters
                .trim();

            const cleanText = this.cleanExtractedText(text);
            const estimatedPages = Math.ceil(cleanText.length / 2000);

            console.log(`✅ RTF extracted: ${cleanText.length} characters, ~${estimatedPages} pages`);

            return {
                text: cleanText,
                pageCount: estimatedPages,
                metadata: {
                    format: 'rtf',
                    wordCount: this.countWords(cleanText)
                }
            };
        } catch (error) {
            console.error('❌ RTF extraction failed:', error);
            throw new Error(`RTF extraction failed: ${error.message}`);
        }
    }

    /**
     * Clean up extracted text (remove excessive whitespace, fix encoding issues)
     */
    cleanExtractedText(text) {
        return text
            .replace(/\r\n/g, '\n')           // Normalize line endings
            .replace(/\r/g, '\n')             // Convert remaining CR to LF
            .replace(/\n{3,}/g, '\n\n')       // Limit consecutive newlines to 2
            .replace(/[ \t]{2,}/g, ' ')       // Reduce multiple spaces to single space
            .replace(/^\s+|\s+$/gm, '')       // Trim whitespace from each line
            .trim();                          // Trim overall
    }

    /**
     * Count words in text (simple word count)
     */
    countWords(text) {
        return text.split(/\s+/).filter(word => word.length > 0).length;
    }

    /**
     * Get list of supported MIME types
     */
    getSupportedTypes() {
        return Object.keys(this.supportedTypes);
    }

    /**
     * Check if a MIME type is supported
     */
    isSupported(mimeType) {
        return mimeType in this.supportedTypes;
    }
}

module.exports = FileExtractor;