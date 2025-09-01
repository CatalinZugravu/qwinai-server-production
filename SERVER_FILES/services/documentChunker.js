/**
 * Intelligent document chunking service
 * Splits large documents into optimal chunks while preserving context and meaning
 */
class DocumentChunker {
    constructor(tokenCounter) {
        this.tokenCounter = tokenCounter;
        this.defaultMaxTokens = 6000;
        this.overlapTokens = 200; // Overlap between chunks to maintain context
    }

    /**
     * Split document into intelligent chunks
     */
    chunkDocument(content, maxTokens = this.defaultMaxTokens, model = 'gpt-4') {
        console.log(`📑 Starting document chunking: ${content.length} characters, max ${maxTokens} tokens per chunk`);

        if (!content || content.trim().length === 0) {
            return [];
        }

        const chunks = [];
        
        // First, try to split by semantic sections
        const sections = this.splitIntoSections(content);
        
        let currentChunk = '';
        let currentTokens = 0;
        let chunkIndex = 1;

        for (let i = 0; i < sections.length; i++) {
            const section = sections[i];
            const sectionTokens = this.tokenCounter.countTokens(section, model);
            
            // If a single section exceeds the limit, we need to split it further
            if (sectionTokens > maxTokens) {
                // Save current chunk if it has content
                if (currentChunk.trim()) {
                    chunks.push(this.createChunk(currentChunk, currentTokens, chunkIndex));
                    chunkIndex++;
                    currentChunk = '';
                    currentTokens = 0;
                }
                
                // Split the large section
                const subChunks = this.splitLargeSection(section, maxTokens, model, chunkIndex);
                chunks.push(...subChunks);
                chunkIndex += subChunks.length;
                continue;
            }

            // Check if adding this section would exceed the limit
            if (currentTokens + sectionTokens > maxTokens && currentChunk.trim()) {
                // Add overlap from previous chunk for context continuity
                const chunkWithOverlap = this.addOverlap(currentChunk, sections[i - 1] || '', model);
                chunks.push(this.createChunk(chunkWithOverlap, currentTokens, chunkIndex));
                chunkIndex++;
                
                // Start new chunk with some context from the previous chunk
                const contextStart = this.getContextStart(currentChunk, model);
                currentChunk = contextStart + (contextStart ? '\n\n' : '') + section;
                currentTokens = this.tokenCounter.countTokens(currentChunk, model);
            } else {
                // Add section to current chunk
                currentChunk += (currentChunk ? '\n\n' : '') + section;
                currentTokens += sectionTokens;
            }
        }

        // Add final chunk if it has content
        if (currentChunk.trim()) {
            chunks.push(this.createChunk(currentChunk, currentTokens, chunkIndex));
        }

        console.log(`✅ Document chunked into ${chunks.size} pieces`);
        console.log(`📊 Chunk sizes: ${chunks.map(c => c.tokenCount).join(', ')} tokens`);
        
        return chunks;
    }

    /**
     * Split content into logical sections (paragraphs, headings, etc.)
     */
    splitIntoSections(content) {
        // Split by double newlines first (paragraphs)
        let sections = content.split(/\n\s*\n/);
        
        // Further split very long paragraphs
        const maxParagraphLength = 2000; // characters
        const refinedSections = [];
        
        for (const section of sections) {
            if (section.length <= maxParagraphLength) {
                refinedSections.push(section);
            } else {
                // Split long paragraphs by sentences
                const sentences = this.splitIntoSentences(section);
                let currentParagraph = '';
                
                for (const sentence of sentences) {
                    if (currentParagraph.length + sentence.length > maxParagraphLength && currentParagraph) {
                        refinedSections.push(currentParagraph);
                        currentParagraph = sentence;
                    } else {
                        currentParagraph += (currentParagraph ? ' ' : '') + sentence;
                    }
                }
                
                if (currentParagraph) {
                    refinedSections.push(currentParagraph);
                }
            }
        }
        
        return refinedSections.filter(section => section.trim().length > 0);
    }

    /**
     * Split text into sentences using multiple delimiters
     */
    splitIntoSentences(text) {
        // Split on sentence endings, but be careful about abbreviations
        const sentences = text.split(/(?<=[.!?])\s+(?=[A-Z])/);
        
        // Filter out very short "sentences" (likely abbreviations)
        return sentences.filter(sentence => sentence.trim().length > 10);
    }

    /**
     * Split a large section that exceeds token limits
     */
    splitLargeSection(section, maxTokens, model, startIndex) {
        console.log(`📄 Splitting large section (${section.length} chars) into smaller chunks`);
        
        const chunks = [];
        const sentences = this.splitIntoSentences(section);
        
        let currentChunk = '';
        let currentTokens = 0;
        let chunkIndex = startIndex;

        for (const sentence of sentences) {
            const sentenceTokens = this.tokenCounter.countTokens(sentence, model);
            
            // If even a single sentence exceeds the limit, split it by commas or clauses
            if (sentenceTokens > maxTokens) {
                if (currentChunk) {
                    chunks.push(this.createChunk(currentChunk, currentTokens, chunkIndex));
                    chunkIndex++;
                    currentChunk = '';
                    currentTokens = 0;
                }
                
                // Split sentence by commas and clauses
                const subSentences = this.splitSentenceByCommas(sentence);
                for (const subSentence of subSentences) {
                    const subTokens = this.tokenCounter.countTokens(subSentence, model);
                    if (subTokens > maxTokens) {
                        // Last resort: split by character limit
                        const characterChunks = this.splitByCharacterLimit(subSentence, maxTokens, model);
                        chunks.push(...characterChunks.map(chunk => 
                            this.createChunk(chunk.text, chunk.tokenCount, chunkIndex++)
                        ));
                    } else {
                        chunks.push(this.createChunk(subSentence, subTokens, chunkIndex));
                        chunkIndex++;
                    }
                }
                continue;
            }

            // Check if adding this sentence exceeds the limit
            if (currentTokens + sentenceTokens > maxTokens && currentChunk) {
                chunks.push(this.createChunk(currentChunk, currentTokens, chunkIndex));
                chunkIndex++;
                currentChunk = sentence;
                currentTokens = sentenceTokens;
            } else {
                currentChunk += (currentChunk ? ' ' : '') + sentence;
                currentTokens += sentenceTokens;
            }
        }

        // Add final chunk
        if (currentChunk) {
            chunks.push(this.createChunk(currentChunk, currentTokens, chunkIndex));
        }

        return chunks;
    }

    /**
     * Split sentence by commas for finer granularity
     */
    splitSentenceByCommas(sentence) {
        const parts = sentence.split(',');
        const result = [];
        let current = '';
        
        for (let i = 0; i < parts.length; i++) {
            const part = parts[i] + (i < parts.length - 1 ? ',' : '');
            if (current.length + part.length > 500 && current) {
                result.push(current.trim());
                current = part;
            } else {
                current += part;
            }
        }
        
        if (current) {
            result.push(current.trim());
        }
        
        return result.filter(part => part.length > 0);
    }

    /**
     * Emergency split by character limit (preserves word boundaries)
     */
    splitByCharacterLimit(text, maxTokens, model) {
        const chunks = [];
        const maxChars = maxTokens * 3; // Rough estimate: 3 chars per token
        
        let start = 0;
        while (start < text.length) {
            let end = Math.min(start + maxChars, text.length);
            
            // Try to break at word boundary
            if (end < text.length) {
                const lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start + maxChars * 0.5) { // Don't break too early
                    end = lastSpace;
                }
            }
            
            const chunk = text.substring(start, end).trim();
            if (chunk) {
                const tokenCount = this.tokenCounter.countTokens(chunk, model);
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
     * Add contextual overlap between chunks
     */
    addOverlap(currentChunk, previousSection, model) {
        if (!previousSection || this.overlapTokens <= 0) {
            return currentChunk;
        }

        // Get last sentences from previous section for context
        const sentences = this.splitIntoSentences(previousSection);
        let overlap = '';
        let overlapTokens = 0;
        
        // Add sentences from the end until we reach overlap limit
        for (let i = sentences.length - 1; i >= 0; i--) {
            const sentence = sentences[i];
            const sentenceTokens = this.tokenCounter.countTokens(sentence, model);
            
            if (overlapTokens + sentenceTokens <= this.overlapTokens) {
                overlap = sentence + (overlap ? ' ' : '') + overlap;
                overlapTokens += sentenceTokens;
            } else {
                break;
            }
        }
        
        return overlap ? overlap + '\n\n' + currentChunk : currentChunk;
    }

    /**
     * Get context from the beginning of text for next chunk
     */
    getContextStart(text, model) {
        if (!text) return '';
        
        const sentences = this.splitIntoSentences(text);
        let context = '';
        let contextTokens = 0;
        
        // Add sentences from the beginning until we reach overlap limit
        for (const sentence of sentences) {
            const sentenceTokens = this.tokenCounter.countTokens(sentence, model);
            
            if (contextTokens + sentenceTokens <= this.overlapTokens) {
                context += (context ? ' ' : '') + sentence;
                contextTokens += sentenceTokens;
            } else {
                break;
            }
        }
        
        return context;
    }

    /**
     * Create a formatted chunk with metadata
     */
    createChunk(text, tokenCount, index) {
        const cleanText = text.trim();
        
        return {
            index: index,
            text: cleanText,
            tokenCount: tokenCount,
            characterCount: cleanText.length,
            wordCount: cleanText.split(/\s+/).filter(word => word.length > 0).length,
            preview: cleanText.substring(0, 200) + (cleanText.length > 200 ? '...' : ''),
            sentences: this.splitIntoSentences(cleanText).length
        };
    }

    /**
     * Optimize chunk sizes for specific model
     */
    optimizeForModel(chunks, model) {
        const contextLimit = this.tokenCounter.getContextLimits(model);
        const optimalSize = Math.floor(contextLimit * 0.8); // Leave 20% buffer
        
        return chunks.filter(chunk => chunk.tokenCount <= optimalSize);
    }

    /**
     * Get chunking statistics
     */
    getChunkingStats(chunks) {
        if (chunks.length === 0) {
            return {
                totalChunks: 0,
                totalTokens: 0,
                averageTokens: 0,
                minTokens: 0,
                maxTokens: 0
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
            totalWords: chunks.reduce((sum, chunk) => sum + chunk.wordCount, 0)
        };
    }
}

module.exports = DocumentChunker;