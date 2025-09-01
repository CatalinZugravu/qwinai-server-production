/**
 * PRODUCTION-GRADE INTELLIGENT DOCUMENT CHUNKER
 * Enhanced with performance monitoring, security safeguards, and advanced context preservation
 */
class DocumentChunker {
    constructor(tokenCounter) {
        this.tokenCounter = tokenCounter;
        this.defaultMaxTokens = 6000;
        this.overlapTokens = 200; // Context overlap between chunks
        
        // Production limits and safeguards
        this.MAX_CHUNK_COUNT = 100; // Prevent memory exhaustion
        this.MAX_DOCUMENT_LENGTH = 5 * 1024 * 1024; // 5MB text limit
        this.PROCESSING_TIMEOUT = 30000; // 30 seconds
        
        // Performance tracking
        this.chunkingStats = {
            totalDocuments: 0,
            totalChunks: 0,
            averageChunksPerDocument: 0,
            processingTimes: []
        };
        
        console.log('🔧 Production DocumentChunker initialized with enhanced safeguards');
    }

    /**
     * Split document into intelligent chunks with comprehensive validation
     */
    async chunkDocument(content, maxTokens = this.defaultMaxTokens, model = 'gpt-4') {
        const startTime = Date.now();
        
        try {
            // Input validation and security checks
            await this.validateInput(content, maxTokens, model);
            
            console.log(`📑 Starting secure chunking: ${content.length} chars, max ${maxTokens} tokens per chunk`);
            
            // Process with timeout protection
            const chunks = await Promise.race([
                this.performChunking(content, maxTokens, model),
                this.createTimeoutPromise(this.PROCESSING_TIMEOUT)
            ]);
            
            // Final validation and optimization
            const validatedChunks = this.validateChunks(chunks, maxTokens);
            
            // Update statistics
            this.updateStats(validatedChunks, Date.now() - startTime);
            
            console.log(`✅ Document chunked securely: ${validatedChunks.length} pieces in ${Date.now() - startTime}ms`);
            console.log(`📊 Chunk sizes: ${validatedChunks.map(c => c.tokenCount).join(', ')} tokens`);
            
            return validatedChunks;
            
        } catch (error) {
            console.error('❌ Document chunking failed:', error.message);
            throw new Error(`Document chunking failed: ${error.message}`);
        }
    }

    /**
     * Validate input parameters and content
     */
    async validateInput(content, maxTokens, model) {
        if (!content || typeof content !== 'string') {
            throw new Error('Content must be a non-empty string');
        }
        
        if (content.length === 0) {
            throw new Error('Content is empty');
        }
        
        if (content.length > this.MAX_DOCUMENT_LENGTH) {
            throw new Error(`Document too large: ${content.length} characters (max: ${this.MAX_DOCUMENT_LENGTH})`);
        }
        
        if (!Number.isInteger(maxTokens) || maxTokens < 100 || maxTokens > 32000) {
            throw new Error('maxTokens must be an integer between 100 and 32000');
        }
        
        if (!model || typeof model !== 'string') {
            throw new Error('Model must be specified');
        }
        
        // Check for potentially malicious patterns
        const suspiciousPatterns = [
            /<script[^>]*>/gi,
            /javascript:/gi,
            /vbscript:/gi,
            /on\w+\s*=/gi
        ];
        
        for (const pattern of suspiciousPatterns) {
            if (pattern.test(content.substring(0, 10000))) { // Check first 10KB
                console.warn('🚨 Potentially malicious content detected');
                throw new Error('Content contains potentially harmful patterns');
            }
        }
    }

    /**
     * Perform the actual chunking with enhanced logic
     */
    async performChunking(content, maxTokens, model) {
        const chunks = [];
        const sections = this.splitIntoSectionsSecure(content);
        
        let currentChunk = '';
        let currentTokens = 0;
        let chunkIndex = 1;

        for (let i = 0; i < sections.length; i++) {
            const section = sections[i];
            
            // Security check for section length
            if (section.length > 100000) { // 100KB per section limit
                console.warn(`⚠️ Large section detected (${section.length} chars), splitting`);
            }
            
            let sectionTokens;
            try {
                sectionTokens = await this.tokenCounter.countTokens(section, model);
            } catch (error) {
                console.warn(`⚠️ Token counting failed for section, using estimate`);
                sectionTokens = Math.ceil(section.length / 4); // Rough estimate
            }
            
            // Handle oversized sections
            if (sectionTokens > maxTokens) {
                // Save current chunk if it has content
                if (currentChunk.trim()) {
                    chunks.push(this.createChunkSecure(currentChunk, currentTokens, chunkIndex));
                    chunkIndex++;
                    currentChunk = '';
                    currentTokens = 0;
                }
                
                // Split the large section
                const subChunks = await this.splitLargeSectionSecure(section, maxTokens, model, chunkIndex);
                chunks.push(...subChunks);
                chunkIndex += subChunks.length;
                
                // Prevent excessive chunk creation
                if (chunks.length > this.MAX_CHUNK_COUNT) {
                    console.warn(`⚠️ Maximum chunk count reached (${this.MAX_CHUNK_COUNT}), stopping`);
                    break;
                }
                continue;
            }

            // Check if adding this section would exceed the limit
            if (currentTokens + sectionTokens > maxTokens && currentChunk.trim()) {
                // Add contextual overlap
                const chunkWithOverlap = await this.addOverlapSecure(currentChunk, sections[i - 1] || '', model);
                chunks.push(this.createChunkSecure(chunkWithOverlap, currentTokens, chunkIndex));
                chunkIndex++;
                
                // Start new chunk with context
                const contextStart = await this.getContextStartSecure(currentChunk, model);
                currentChunk = contextStart + (contextStart ? '\n\n' : '') + section;
                currentTokens = await this.tokenCounter.countTokens(currentChunk, model);
            } else {
                // Add section to current chunk
                currentChunk += (currentChunk ? '\n\n' : '') + section;
                currentTokens = currentTokens + sectionTokens;
            }
            
            // Safety check to prevent runaway processing
            if (chunks.length > this.MAX_CHUNK_COUNT - 10) {
                console.warn(`⚠️ Approaching maximum chunk count, finalizing`);
                break;
            }
        }

        // Add final chunk if it has content
        if (currentChunk.trim()) {
            chunks.push(this.createChunkSecure(currentChunk, currentTokens, chunkIndex));
        }

        return chunks;
    }

    /**
     * Enhanced section splitting with security considerations
     */
    splitIntoSectionsSecure(content) {
        // Normalize content first
        const normalizedContent = content
            .replace(/\r\n/g, '\n')
            .replace(/\r/g, '\n')
            .replace(/\n{4,}/g, '\n\n\n'); // Limit excessive newlines
            
        // Split by double newlines (paragraphs)
        let sections = normalizedContent.split(/\n\s*\n/);
        
        // Filter out empty or very short sections
        sections = sections.filter(section => section.trim().length >= 10);
        
        const maxParagraphLength = 3000; // Increased limit for better context
        const refinedSections = [];
        
        for (const section of sections) {
            if (section.length <= maxParagraphLength) {
                refinedSections.push(section.trim());
            } else {
                // Split long paragraphs intelligently
                const subSections = this.splitLongParagraphSecure(section, maxParagraphLength);
                refinedSections.push(...subSections);
            }
            
            // Prevent excessive section creation
            if (refinedSections.length > this.MAX_CHUNK_COUNT * 2) {
                console.warn(`⚠️ Too many sections created, truncating`);
                break;
            }
        }
        
        return refinedSections.filter(section => section && section.trim().length > 0);
    }

    /**
     * Intelligently split long paragraphs
     */
    splitLongParagraphSecure(paragraph, maxLength) {
        const sentences = this.splitIntoSentencesSecure(paragraph);
        const sections = [];
        let currentSection = '';
        
        for (const sentence of sentences) {
            if (currentSection.length + sentence.length > maxLength && currentSection) {
                sections.push(currentSection.trim());
                currentSection = sentence;
            } else {
                currentSection += (currentSection ? ' ' : '') + sentence;
            }
            
            // Prevent excessive memory usage
            if (sections.length > 50) {
                console.warn(`⚠️ Too many subsections, truncating paragraph`);
                break;
            }
        }
        
        if (currentSection) {
            sections.push(currentSection.trim());
        }
        
        return sections;
    }

    /**
     * Enhanced sentence splitting with security
     */
    splitIntoSentencesSecure(text) {
        if (!text || text.length > 50000) { // Limit text size for security
            console.warn(`⚠️ Text too long for sentence splitting`);
            return [text.substring(0, 50000)];
        }
        
        try {
            // Enhanced sentence splitting with multiple patterns
            let sentences = text.split(/(?<=[.!?])\s+(?=[A-Z])/);
            
            // Filter and clean sentences
            sentences = sentences
                .filter(sentence => sentence.trim().length >= 10) // Min sentence length
                .map(sentence => sentence.trim())
                .filter(sentence => sentence.length <= 5000); // Max sentence length
                
            return sentences.slice(0, 200); // Limit number of sentences
            
        } catch (error) {
            console.warn(`⚠️ Sentence splitting failed, using paragraph as single unit`);
            return [text];
        }
    }

    /**
     * Secure large section splitting
     */
    async splitLargeSectionSecure(section, maxTokens, model, startIndex) {
        console.log(`📄 Splitting large section (${section.length} chars) securely`);
        
        const chunks = [];
        const sentences = this.splitIntoSentencesSecure(section);
        
        let currentChunk = '';
        let currentTokens = 0;
        let chunkIndex = startIndex;

        for (const sentence of sentences) {
            if (!sentence || sentence.length === 0) continue;
            
            let sentenceTokens;
            try {
                sentenceTokens = await this.tokenCounter.countTokens(sentence, model);
            } catch (error) {
                sentenceTokens = Math.ceil(sentence.length / 4);
            }
            
            // Handle oversized sentences
            if (sentenceTokens > maxTokens) {
                if (currentChunk) {
                    chunks.push(this.createChunkSecure(currentChunk, currentTokens, chunkIndex));
                    chunkIndex++;
                    currentChunk = '';
                    currentTokens = 0;
                }
                
                // Emergency character-based splitting
                const characterChunks = this.splitByCharacterLimitSecure(sentence, maxTokens, model);
                chunks.push(...characterChunks.map(chunk => 
                    this.createChunkSecure(chunk.text, chunk.tokenCount, chunkIndex++)
                ));
                continue;
            }

            // Check if adding sentence exceeds limit
            if (currentTokens + sentenceTokens > maxTokens && currentChunk) {
                chunks.push(this.createChunkSecure(currentChunk, currentTokens, chunkIndex));
                chunkIndex++;
                currentChunk = sentence;
                currentTokens = sentenceTokens;
            } else {
                currentChunk += (currentChunk ? ' ' : '') + sentence;
                currentTokens += sentenceTokens;
            }
            
            // Safety limit
            if (chunks.length >= this.MAX_CHUNK_COUNT - 5) {
                console.warn(`⚠️ Approaching chunk limit in large section splitting`);
                break;
            }
        }

        // Add final chunk
        if (currentChunk) {
            chunks.push(this.createChunkSecure(currentChunk, currentTokens, chunkIndex));
        }

        return chunks;
    }

    /**
     * Secure character-based splitting as last resort
     */
    splitByCharacterLimitSecure(text, maxTokens, model) {
        const chunks = [];
        const maxChars = Math.min(maxTokens * 3, 10000); // Conservative estimate with safety limit
        
        let start = 0;
        let attempts = 0;
        
        while (start < text.length && attempts < 20) { // Prevent infinite loops
            attempts++;
            let end = Math.min(start + maxChars, text.length);
            
            // Find word boundary
            if (end < text.length) {
                const lastSpace = text.lastIndexOf(' ', end);
                const lastPunct = Math.max(
                    text.lastIndexOf('.', end),
                    text.lastIndexOf('!', end),
                    text.lastIndexOf('?', end)
                );
                
                if (lastPunct > start + maxChars * 0.5) {
                    end = lastPunct + 1;
                } else if (lastSpace > start + maxChars * 0.3) {
                    end = lastSpace;
                }
            }
            
            const chunk = text.substring(start, end).trim();
            if (chunk && chunk.length >= 10) {
                let tokenCount;
                try {
                    tokenCount = this.tokenCounter.countTokens(chunk, model);
                } catch (error) {
                    tokenCount = Math.ceil(chunk.length / 4);
                }
                
                chunks.push({
                    text: chunk,
                    tokenCount: tokenCount
                });
            }
            
            start = end;
        }
        
        return chunks;
    }

    /**
     * Secure overlap addition
     */
    async addOverlapSecure(currentChunk, previousSection, model) {
        if (!previousSection || this.overlapTokens <= 0) {
            return currentChunk;
        }

        try {
            const sentences = this.splitIntoSentencesSecure(previousSection);
            let overlap = '';
            let overlapTokens = 0;
            
            // Add sentences from end until overlap limit
            for (let i = Math.max(0, sentences.length - 5); i < sentences.length; i++) {
                const sentence = sentences[i];
                if (!sentence) continue;
                
                const sentenceTokens = await this.tokenCounter.countTokens(sentence, model);
                
                if (overlapTokens + sentenceTokens <= this.overlapTokens) {
                    overlap = (overlap ? overlap + ' ' : '') + sentence;
                    overlapTokens += sentenceTokens;
                } else {
                    break;
                }
            }
            
            return overlap ? overlap + '\n\n' + currentChunk : currentChunk;
        } catch (error) {
            console.warn(`⚠️ Overlap addition failed:`, error.message);
            return currentChunk;
        }
    }

    /**
     * Secure context start extraction
     */
    async getContextStartSecure(text, model) {
        if (!text) return '';
        
        try {
            const sentences = this.splitIntoSentencesSecure(text);
            let context = '';
            let contextTokens = 0;
            
            // Add sentences from beginning until overlap limit
            for (let i = 0; i < Math.min(sentences.length, 3); i++) {
                const sentence = sentences[i];
                if (!sentence) continue;
                
                const sentenceTokens = await this.tokenCounter.countTokens(sentence, model);
                
                if (contextTokens + sentenceTokens <= this.overlapTokens) {
                    context += (context ? ' ' : '') + sentence;
                    contextTokens += sentenceTokens;
                } else {
                    break;
                }
            }
            
            return context;
        } catch (error) {
            console.warn(`⚠️ Context start extraction failed:`, error.message);
            return '';
        }
    }

    /**
     * Create secure chunk with validation
     */
    createChunkSecure(text, tokenCount, index) {
        const cleanText = text ? text.trim() : '';
        
        if (!cleanText || cleanText.length === 0) {
            console.warn(`⚠️ Empty chunk detected at index ${index}`);
            return null;
        }
        
        // Validate chunk size
        if (cleanText.length > 100000) { // 100KB limit per chunk
            console.warn(`⚠️ Chunk too large, truncating`);
            const truncated = cleanText.substring(0, 100000);
            return this.createChunkSecure(truncated, tokenCount, index);
        }
        
        const wordCount = cleanText.split(/\s+/).filter(word => word && word.length > 0).length;
        const sentences = this.splitIntoSentencesSecure(cleanText).length;
        
        return {
            index: index,
            text: cleanText,
            tokenCount: Math.max(1, tokenCount), // Ensure positive token count
            characterCount: cleanText.length,
            wordCount: wordCount,
            preview: cleanText.length > 200 ? 
                cleanText.substring(0, 200) + '...' : 
                cleanText,
            sentences: Math.max(1, sentences),
            createdAt: Date.now()
        };
    }

    /**
     * Validate final chunks
     */
    validateChunks(chunks, maxTokens) {
        const validChunks = chunks
            .filter(chunk => chunk !== null && chunk !== undefined)
            .filter(chunk => chunk.text && chunk.text.trim().length > 0)
            .filter(chunk => chunk.tokenCount > 0 && chunk.tokenCount <= maxTokens * 1.1); // Allow 10% buffer
            
        // Ensure we have at least one chunk
        if (validChunks.length === 0 && chunks.length > 0) {
            console.warn(`⚠️ All chunks filtered out, returning best effort chunk`);
            const bestChunk = chunks.find(c => c && c.text);
            if (bestChunk) {
                return [bestChunk];
            }
        }
        
        return validChunks.slice(0, this.MAX_CHUNK_COUNT); // Final safety limit
    }

    /**
     * Update performance statistics
     */
    updateStats(chunks, processingTime) {
        this.chunkingStats.totalDocuments++;
        this.chunkingStats.totalChunks += chunks.length;
        this.chunkingStats.averageChunksPerDocument = 
            this.chunkingStats.totalChunks / this.chunkingStats.totalDocuments;
        this.chunkingStats.processingTimes.push(processingTime);
        
        // Keep only recent processing times
        if (this.chunkingStats.processingTimes.length > 100) {
            this.chunkingStats.processingTimes = 
                this.chunkingStats.processingTimes.slice(-50);
        }
    }

    /**
     * Create timeout promise
     */
    createTimeoutPromise(timeout) {
        return new Promise((_, reject) => {
            setTimeout(() => reject(new Error('Document chunking timed out')), timeout);
        });
    }

    /**
     * Get comprehensive chunking statistics
     */
    getChunkingStats(chunks) {
        if (!chunks || chunks.length === 0) {
            return {
                totalChunks: 0,
                totalTokens: 0,
                averageTokens: 0,
                minTokens: 0,
                maxTokens: 0,
                efficiency: 0
            };
        }

        const tokenCounts = chunks.map(chunk => chunk.tokenCount);
        const totalTokens = tokenCounts.reduce((sum, count) => sum + count, 0);
        
        return {
            totalChunks: chunks.length,
            totalTokens: totalTokens,
            averageTokens: Math.round(totalTokens / chunks.length),
            minTokens: Math.min(...tokenCounts),
            maxTokens: Math.max(...tokenCounts),
            totalCharacters: chunks.reduce((sum, chunk) => sum + chunk.characterCount, 0),
            totalWords: chunks.reduce((sum, chunk) => sum + chunk.wordCount, 0),
            efficiency: totalTokens > 0 ? Math.round((totalTokens / (chunks.length * Math.max(...tokenCounts))) * 100) : 0,
            performance: {
                averageProcessingTime: this.chunkingStats.processingTimes.length > 0 ?
                    Math.round(this.chunkingStats.processingTimes.reduce((a, b) => a + b, 0) / this.chunkingStats.processingTimes.length) : 0,
                totalDocumentsProcessed: this.chunkingStats.totalDocuments
            }
        };
    }

    /**
     * Get system statistics for monitoring
     */
    getSystemStats() {
        return {
            ...this.chunkingStats,
            limits: {
                maxChunkCount: this.MAX_CHUNK_COUNT,
                maxDocumentLength: this.MAX_DOCUMENT_LENGTH,
                processingTimeout: this.PROCESSING_TIMEOUT,
                overlapTokens: this.overlapTokens
            }
        };
    }
}

module.exports = DocumentChunker;