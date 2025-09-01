const UniversalTokenCounter = require('./universalTokenCounter');

/**
 * PRODUCTION TOKEN COUNTER WRAPPER
 * Provides backward compatibility while leveraging the enhanced UniversalTokenCounter
 */
class TokenCounter {
    constructor() {
        this.universalCounter = new UniversalTokenCounter();
        this.initializationPromise = this.universalCounter.initializationPromise;
        
        console.log('🔧 Production TokenCounter initialized with universal backend');
    }

    /**
     * Count tokens using the universal counter
     */
    async countTokens(text, model = 'gpt-4') {
        try {
            await this.initializationPromise;
            return await this.universalCounter.countTokens(text, model);
        } catch (error) {
            console.warn(`⚠️ Token counting failed for model ${model}:`, error.message);
            // Fallback to character-based estimation
            return Math.ceil((text || '').length / 4);
        }
    }

    /**
     * Normalize model names
     */
    normalizeModelName(model) {
        return this.universalCounter.normalizeModelName(model);
    }

    /**
     * Estimate cost using enhanced pricing
     */
    async estimateCost(tokenCount, model = 'gpt-4', type = 'input') {
        try {
            await this.initializationPromise;
            
            if (type === 'output') {
                return await this.universalCounter.estimateOutputCost(tokenCount, model);
            } else {
                return await this.universalCounter.estimateInputCost(tokenCount, model);
            }
        } catch (error) {
            console.warn(`⚠️ Cost estimation failed:`, error.message);
            return '0.0000';
        }
    }

    /**
     * Get context limits with enhanced model support
     */
    async getContextLimits(model = 'gpt-4') {
        try {
            await this.initializationPromise;
            const analysis = await this.universalCounter.analyzeText('test', model);
            return analysis.contextWindow;
        } catch (error) {
            console.warn(`⚠️ Context limit lookup failed:`, error.message);
            
            // Fallback limits
            const fallbackLimits = {
                'gpt-4': 8192,
                'gpt-4o': 128000,
                'gpt-4-turbo': 128000,
                'gpt-3.5-turbo': 4096,
                'claude-3-opus': 200000,
                'claude-3-sonnet': 200000,
                'claude-3-haiku': 200000,
                'claude-3.5-sonnet': 200000,
                'gemini-1.5-pro': 2000000,
                'gemini-pro': 32768,
                'deepseek-chat': 32768
            };
            
            return fallbackLimits[model] || fallbackLimits['gpt-4'];
        }
    }

    /**
     * Calculate recommended chunk size
     */
    async getRecommendedChunkSize(model = 'gpt-4', bufferPercent = 0.2) {
        try {
            await this.initializationPromise;
            return await this.universalCounter.getOptimalChunkSize(model, bufferPercent);
        } catch (error) {
            console.warn(`⚠️ Chunk size calculation failed:`, error.message);
            const contextLimit = await this.getContextLimits(model);
            const buffer = Math.floor(contextLimit * bufferPercent);
            return Math.max(1000, contextLimit - buffer);
        }
    }

    /**
     * Enhanced text analysis with comprehensive information
     */
    async analyzeText(text, model = 'gpt-4') {
        try {
            await this.initializationPromise;
            const analysis = await this.universalCounter.analyzeText(text, model);
            
            // Convert to legacy format for backward compatibility
            return {
                tokenCount: analysis.tokenCount,
                characterCount: analysis.characterCount,
                wordCount: analysis.wordCount,
                contextLimit: analysis.contextWindow,
                exceedsContext: analysis.exceedsContext,
                utilizationPercent: analysis.utilizationPercent,
                estimatedCost: analysis.costs.input,
                recommendedChunkSize: await this.getRecommendedChunkSize(model),
                chunksNeeded: Math.ceil(analysis.tokenCount / await this.getRecommendedChunkSize(model)),
                model: analysis.model,
                provider: analysis.provider,
                accuracy: analysis.accuracy,
                category: analysis.category
            };
        } catch (error) {
            console.error('❌ Text analysis failed:', error);
            
            // Fallback analysis
            const fallbackTokens = Math.ceil((text || '').length / 4);
            const fallbackContext = await this.getContextLimits(model);
            
            return {
                tokenCount: fallbackTokens,
                characterCount: (text || '').length,
                wordCount: (text || '').split(/\s+/).filter(word => word.length > 0).length,
                contextLimit: fallbackContext,
                exceedsContext: fallbackTokens > fallbackContext,
                utilizationPercent: Math.round((fallbackTokens / fallbackContext) * 100),
                estimatedCost: '$0.0000',
                recommendedChunkSize: Math.floor(fallbackContext * 0.8),
                chunksNeeded: Math.ceil(fallbackTokens / Math.floor(fallbackContext * 0.8)),
                model: model,
                provider: 'unknown',
                accuracy: 'fallback',
                category: 'unknown'
            };
        }
    }

    /**
     * Get comprehensive usage statistics
     */
    async getUsageStats() {
        try {
            await this.initializationPromise;
            const universalStats = this.universalCounter.getSystemStats();
            const supportedModels = await this.universalCounter.getSupportedModels();
            
            return {
                encodersLoaded: universalStats.encodersLoaded,
                modelsSupported: universalStats.modelsSupported,
                availableModels: Object.keys(supportedModels).reduce((acc, category) => {
                    acc.push(...supportedModels[category].map(model => model.id));
                    return acc;
                }, []),
                providers: universalStats.providers,
                categories: universalStats.categories,
                pricingLastUpdated: universalStats.pricingLastUpdated,
                fallbackEncoder: 'available',
                version: '2.0.0'
            };
        } catch (error) {
            console.warn(`⚠️ Usage stats retrieval failed:`, error.message);
            return {
                encodersLoaded: 0,
                modelsSupported: 0,
                availableModels: ['gpt-4'],
                providers: ['openai'],
                categories: ['fallback'],
                pricingLastUpdated: null,
                fallbackEncoder: 'available',
                version: '2.0.0-fallback'
            };
        }
    }

    /**
     * Get supported models by category
     */
    async getSupportedModels() {
        try {
            await this.initializationPromise;
            return await this.universalCounter.getSupportedModels();
        } catch (error) {
            console.warn(`⚠️ Supported models lookup failed:`, error.message);
            return {
                flagship: [{ id: 'gpt-4', provider: 'openai', contextWindow: 8192 }],
                efficient: [{ id: 'gpt-3.5-turbo', provider: 'openai', contextWindow: 4096 }]
            };
        }
    }

    /**
     * Check if model is supported
     */
    async isModelSupported(model) {
        try {
            await this.initializationPromise;
            const supportedModels = await this.getSupportedModels();
            const allModels = Object.values(supportedModels).flat().map(m => m.id);
            return allModels.includes(model);
        } catch (error) {
            console.warn(`⚠️ Model support check failed:`, error.message);
            return false;
        }
    }

    /**
     * Get model information
     */
    async getModelInfo(model) {
        try {
            await this.initializationPromise;
            const analysis = await this.universalCounter.analyzeText('test', model);
            
            return {
                id: analysis.model,
                provider: analysis.provider,
                contextWindow: analysis.contextWindow,
                category: analysis.category,
                hasEncoder: analysis.accuracy === 'exact',
                supported: true
            };
        } catch (error) {
            console.warn(`⚠️ Model info lookup failed for ${model}:`, error.message);
            return {
                id: model,
                provider: 'unknown',
                contextWindow: 8192,
                category: 'unknown',
                hasEncoder: false,
                supported: false
            };
        }
    }

    /**
     * Clean up resources
     */
    cleanup() {
        try {
            console.log('🧹 TokenCounter wrapper cleaned up');
            // The UniversalTokenCounter doesn't need explicit cleanup
        } catch (error) {
            console.error('❌ TokenCounter cleanup failed:', error);
        }
    }
}

module.exports = TokenCounter;