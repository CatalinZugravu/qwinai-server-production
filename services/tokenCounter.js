const tiktoken = require('tiktoken');

/**
 * Accurate token counting service using OpenAI's tiktoken library
 * Supports multiple AI models with precise token counting and cost estimation
 */
class TokenCounter {
    constructor() {
        this.encoders = new Map();
        this.initializeEncoders();
    }

    /**
     * Initialize encoders for different models
     */
    initializeEncoders() {
        try {
            // Load common model encoders
            console.log('🔧 Initializing token encoders...');
            
            // OpenAI models
            this.encoders.set('gpt-4', tiktoken.encoding_for_model('gpt-4'));
            this.encoders.set('gpt-4-turbo', tiktoken.encoding_for_model('gpt-4'));
            this.encoders.set('gpt-4o', tiktoken.encoding_for_model('gpt-4'));
            this.encoders.set('gpt-3.5-turbo', tiktoken.encoding_for_model('gpt-3.5-turbo'));
            
            // Fallback encoder for other models
            this.fallbackEncoder = tiktoken.encoding_for_model('gpt-4');
            
            console.log('✅ Token encoders initialized successfully');
        } catch (error) {
            console.error('❌ Failed to initialize token encoders:', error);
            // Use character-based estimation as fallback
            this.fallbackEncoder = null;
        }
    }

    /**
     * Count tokens in text for a specific model
     */
    countTokens(text, model = 'gpt-4') {
        try {
            if (!text || typeof text !== 'string') {
                return 0;
            }

            const normalizedModel = this.normalizeModelName(model);
            const encoder = this.encoders.get(normalizedModel) || this.fallbackEncoder;
            
            if (!encoder) {
                console.warn(`⚠️ No encoder found for model: ${model}, using fallback`);
                return Math.ceil(text.length / 4); // Rough estimation: ~4 chars per token
            }

            const tokens = encoder.encode(text);
            return tokens.length;
        } catch (error) {
            console.error('❌ Token counting failed:', error);
            // Fallback to character-based estimation
            return Math.ceil(text.length / 4);
        }
    }

    /**
     * Normalize model names to standard formats
     */
    normalizeModelName(model) {
        const modelName = model.toLowerCase().trim();
        
        // Map various model names to standard encoders
        const modelMappings = {
            // OpenAI variants
            'gpt4': 'gpt-4',
            'gpt-4o': 'gpt-4',
            'gpt-4-turbo': 'gpt-4',
            'gpt-4-turbo-preview': 'gpt-4',
            'gpt-4-1106-preview': 'gpt-4',
            'gpt-4-0613': 'gpt-4',
            
            // GPT-3.5 variants
            'gpt3.5': 'gpt-3.5-turbo',
            'gpt-3.5': 'gpt-3.5-turbo',
            'gpt-3.5-turbo-1106': 'gpt-3.5-turbo',
            'gpt-3.5-turbo-0613': 'gpt-3.5-turbo',
            
            // Other models (approximate using GPT-4 encoder)
            'claude': 'gpt-4',
            'claude-3': 'gpt-4',
            'claude-3-opus': 'gpt-4',
            'claude-3-sonnet': 'gpt-4',
            'claude-3-haiku': 'gpt-4',
            'gemini': 'gpt-4',
            'gemini-pro': 'gpt-4',
            'gemini-1.5-pro': 'gpt-4',
            'deepseek': 'gpt-4',
            'deepseek-chat': 'gpt-4',
            'deepseek-coder': 'gpt-4'
        };

        return modelMappings[modelName] || modelName;
    }

    /**
     * Estimate cost for token count and model
     */
    estimateCost(tokenCount, model = 'gpt-4') {
        try {
            const costs = this.getModelCosts();
            const normalizedModel = this.normalizeModelName(model);
            
            // Get cost per token (prices are per 1K tokens, so divide by 1000)
            const costPerToken = costs[normalizedModel] || costs['gpt-4'];
            const totalCost = (tokenCount * costPerToken);
            
            return totalCost.toFixed(4);
        } catch (error) {
            console.error('❌ Cost estimation failed:', error);
            return '0.0000';
        }
    }

    /**
     * Get current model pricing (per token, not per 1K tokens)
     * Prices are regularly updated based on provider rates
     */
    getModelCosts() {
        return {
            // OpenAI models (input pricing per token)
            'gpt-4': 0.03 / 1000,              // $0.03 per 1K tokens
            'gpt-4-turbo': 0.01 / 1000,        // $0.01 per 1K tokens
            'gpt-4o': 0.005 / 1000,            // $0.005 per 1K tokens
            'gpt-3.5-turbo': 0.002 / 1000,     // $0.002 per 1K tokens
            
            // Estimated costs for other providers
            'claude': 0.008 / 1000,             // Approximate
            'claude-3': 0.008 / 1000,
            'gemini': 0.001 / 1000,             // Approximate
            'deepseek': 0.0014 / 1000,          // Approximate
            
            // Default fallback
            'default': 0.01 / 1000
        };
    }

    /**
     * Get context window limits for different models
     */
    getContextLimits(model = 'gpt-4') {
        const limits = {
            // OpenAI models
            'gpt-4': 8192,
            'gpt-4-turbo': 128000,
            'gpt-4o': 128000,
            'gpt-4-32k': 32768,
            'gpt-3.5-turbo': 4096,
            'gpt-3.5-turbo-16k': 16384,
            
            // Other models
            'claude': 100000,
            'claude-3': 200000,
            'claude-3-opus': 200000,
            'claude-3-sonnet': 200000,
            'claude-3-haiku': 200000,
            'gemini': 30720,
            'gemini-pro': 30720,
            'gemini-1.5-pro': 1000000,
            'deepseek': 32000,
            'deepseek-chat': 32000,
            'deepseek-coder': 16000
        };
        
        const normalizedModel = this.normalizeModelName(model);
        return limits[normalizedModel] || limits['gpt-4'];
    }

    /**
     * Calculate recommended chunk size for a model (leaves buffer for prompt)
     */
    getRecommendedChunkSize(model = 'gpt-4', bufferPercent = 0.2) {
        const contextLimit = this.getContextLimits(model);
        const buffer = Math.floor(contextLimit * bufferPercent);
        return contextLimit - buffer;
    }

    /**
     * Analyze text and provide comprehensive token information
     */
    analyzeText(text, model = 'gpt-4') {
        const tokenCount = this.countTokens(text, model);
        const contextLimit = this.getContextLimits(model);
        const estimatedCost = this.estimateCost(tokenCount, model);
        const recommendedChunkSize = this.getRecommendedChunkSize(model);
        
        return {
            tokenCount,
            characterCount: text.length,
            wordCount: text.split(/\s+/).filter(word => word.length > 0).length,
            contextLimit,
            exceedsContext: tokenCount > contextLimit,
            utilizationPercent: Math.round((tokenCount / contextLimit) * 100),
            estimatedCost: `$${estimatedCost}`,
            recommendedChunkSize,
            chunksNeeded: Math.ceil(tokenCount / recommendedChunkSize),
            model: model
        };
    }

    /**
     * Get token usage statistics for monitoring
     */
    getUsageStats() {
        return {
            encodersLoaded: this.encoders.size,
            availableModels: Array.from(this.encoders.keys()),
            fallbackEncoder: this.fallbackEncoder ? 'available' : 'unavailable'
        };
    }

    /**
     * Clean up resources
     */
    cleanup() {
        try {
            // Free encoders (tiktoken handles this internally)
            this.encoders.clear();
            console.log('🧹 Token counter resources cleaned up');
        } catch (error) {
            console.error('❌ Token counter cleanup failed:', error);
        }
    }
}

module.exports = TokenCounter;
