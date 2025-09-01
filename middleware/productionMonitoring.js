const fs = require('fs').promises;
const path = require('path');

/**
 * PRODUCTION-GRADE MONITORING & ERROR HANDLING
 * Comprehensive system monitoring, alerting, and performance tracking
 */
class ProductionMonitoring {
    constructor() {
        this.metrics = {
            requests: {
                total: 0,
                successful: 0,
                failed: 0,
                byEndpoint: new Map(),
                byMethod: new Map()
            },
            files: {
                processed: 0,
                failed: 0,
                totalSizeBytes: 0,
                averageProcessingTime: 0,
                byType: new Map()
            },
            performance: {
                responseTimeSum: 0,
                slowRequests: 0, // >5 seconds
                memoryUsage: [],
                cpuUsage: [],
                activeSessions: 0
            },
            errors: {
                total: 0,
                byType: new Map(),
                critical: 0,
                recent: [] // Last 100 errors
            },
            security: {
                suspiciousActivity: 0,
                blockedRequests: 0,
                rateLimitHits: 0,
                failedValidations: 0
            }
        };
        
        this.startTime = Date.now();
        this.alertThresholds = this.getAlertThresholds();
        this.lastHealthCheck = Date.now();
        
        // Start periodic monitoring
        this.startPeriodicMonitoring();
        
        console.log('📊 Production monitoring initialized');
    }

    /**
     * Define alert thresholds for production monitoring
     */
    getAlertThresholds() {
        return {
            responseTime: {
                warning: 2000,  // 2 seconds
                critical: 5000  // 5 seconds
            },
            errorRate: {
                warning: 0.05,  // 5%
                critical: 0.15  // 15%
            },
            memoryUsage: {
                warning: 0.8,   // 80%
                critical: 0.9   // 90%
            },
            activeConnections: {
                warning: 50,
                critical: 100
            },
            fileProcessingFailureRate: {
                warning: 0.1,   // 10%
                critical: 0.25  // 25%
            }
        };
    }

    /**
     * Middleware for request monitoring
     */
    requestMonitoringMiddleware() {
        return (req, res, next) => {
            const startTime = Date.now();
            const originalSend = res.send;
            
            // Track request start
            this.trackRequestStart(req);
            
            // Override response send to capture metrics
            res.send = function(data) {
                const responseTime = Date.now() - startTime;
                
                // Track request completion
                this.trackRequestComplete(req, res, responseTime);
                
                return originalSend.call(this, data);
            }.bind(this);
            
            next();
        };
    }

    /**
     * Track request start
     */
    trackRequestStart(req) {
        this.metrics.requests.total++;
        this.metrics.performance.activeSessions++;
        
        // Track by endpoint
        const endpoint = req.route ? req.route.path : req.path;
        this.incrementMapValue(this.metrics.requests.byEndpoint, endpoint);
        
        // Track by method
        this.incrementMapValue(this.metrics.requests.byMethod, req.method);
    }

    /**
     * Track request completion
     */
    trackRequestComplete(req, res, responseTime) {
        this.metrics.performance.activeSessions--;
        this.metrics.performance.responseTimeSum += responseTime;
        
        // Track success/failure
        if (res.statusCode >= 200 && res.statusCode < 400) {
            this.metrics.requests.successful++;
        } else {
            this.metrics.requests.failed++;
        }
        
        // Track slow requests
        if (responseTime > this.alertThresholds.responseTime.warning) {
            this.metrics.performance.slowRequests++;
            
            if (responseTime > this.alertThresholds.responseTime.critical) {
                this.logSlowRequest(req, responseTime);
            }
        }
        
        // Check for alerts
        this.checkPerformanceAlerts(responseTime);
    }

    /**
     * Track file processing metrics
     */
    trackFileProcessing(fileName, fileSize, mimeType, processingTime, success = true) {
        if (success) {
            this.metrics.files.processed++;
            this.metrics.files.totalSizeBytes += fileSize;
            
            // Update average processing time
            const totalFiles = this.metrics.files.processed;
            const currentAvg = this.metrics.files.averageProcessingTime;
            this.metrics.files.averageProcessingTime = 
                (currentAvg * (totalFiles - 1) + processingTime) / totalFiles;
            
            // Track by file type
            this.incrementMapValue(this.metrics.files.byType, mimeType);
            
            console.log(`📄 File processed: ${fileName} (${fileSize} bytes, ${processingTime}ms)`);
        } else {
            this.metrics.files.failed++;
            this.trackError('FILE_PROCESSING_FAILED', `Failed to process ${fileName}`);
        }
        
        this.checkFileProcessingAlerts();
    }

    /**
     * Track errors with context
     */
    trackError(errorType, message, severity = 'error', metadata = {}) {
        this.metrics.errors.total++;
        this.incrementMapValue(this.metrics.errors.byType, errorType);
        
        const errorRecord = {
            timestamp: new Date().toISOString(),
            type: errorType,
            message,
            severity,
            metadata,
            id: this.generateErrorId()
        };
        
        // Add to recent errors (keep last 100)
        this.metrics.errors.recent.unshift(errorRecord);
        if (this.metrics.errors.recent.length > 100) {
            this.metrics.errors.recent.pop();
        }
        
        // Track critical errors
        if (severity === 'critical') {
            this.metrics.errors.critical++;
            this.handleCriticalError(errorRecord);
        }
        
        console.error(`❌ [${errorType}] ${message}`, metadata);
        
        // Check error rate alerts
        this.checkErrorRateAlerts();
    }

    /**
     * Track security events
     */
    trackSecurityEvent(eventType, details = {}) {
        switch (eventType) {
            case 'SUSPICIOUS_ACTIVITY':
                this.metrics.security.suspiciousActivity++;
                break;
            case 'BLOCKED_REQUEST':
                this.metrics.security.blockedRequests++;
                break;
            case 'RATE_LIMIT_HIT':
                this.metrics.security.rateLimitHits++;
                break;
            case 'VALIDATION_FAILED':
                this.metrics.security.failedValidations++;
                break;
        }
        
        console.warn(`🚨 Security event: ${eventType}`, details);
        
        // Log security events to separate file
        this.logSecurityEvent(eventType, details);
    }

    /**
     * Check for performance alerts
     */
    checkPerformanceAlerts(responseTime) {
        const errorRate = this.getErrorRate();
        const memoryUsage = this.getMemoryUsagePercent();
        
        // Response time alert
        if (responseTime > this.alertThresholds.responseTime.critical) {
            this.sendAlert('CRITICAL', 'SLOW_RESPONSE', {
                responseTime,
                threshold: this.alertThresholds.responseTime.critical
            });
        }
        
        // Error rate alert
        if (errorRate > this.alertThresholds.errorRate.critical) {
            this.sendAlert('CRITICAL', 'HIGH_ERROR_RATE', {
                errorRate: (errorRate * 100).toFixed(2) + '%',
                threshold: (this.alertThresholds.errorRate.critical * 100) + '%'
            });
        }
        
        // Memory usage alert
        if (memoryUsage > this.alertThresholds.memoryUsage.critical) {
            this.sendAlert('CRITICAL', 'HIGH_MEMORY_USAGE', {
                memoryUsage: (memoryUsage * 100).toFixed(2) + '%',
                threshold: (this.alertThresholds.memoryUsage.critical * 100) + '%'
            });
        }
    }

    /**
     * Check file processing alerts
     */
    checkFileProcessingAlerts() {
        const failureRate = this.getFileProcessingFailureRate();
        
        if (failureRate > this.alertThresholds.fileProcessingFailureRate.critical) {
            this.sendAlert('CRITICAL', 'HIGH_FILE_PROCESSING_FAILURE_RATE', {
                failureRate: (failureRate * 100).toFixed(2) + '%',
                threshold: (this.alertThresholds.fileProcessingFailureRate.critical * 100) + '%'
            });
        }
    }

    /**
     * Check error rate alerts
     */
    checkErrorRateAlerts() {
        const errorRate = this.getErrorRate();
        
        if (errorRate > this.alertThresholds.errorRate.warning && 
            errorRate <= this.alertThresholds.errorRate.critical) {
            this.sendAlert('WARNING', 'ELEVATED_ERROR_RATE', {
                errorRate: (errorRate * 100).toFixed(2) + '%'
            });
        }
    }

    /**
     * Send alert (implement your alerting system here)
     */
    sendAlert(severity, alertType, details) {
        const alert = {
            timestamp: new Date().toISOString(),
            severity,
            type: alertType,
            details,
            server: process.env.NODE_ENV || 'development',
            uptime: this.getUptimeString()
        };
        
        console.error(`🚨 ${severity} ALERT: ${alertType}`, details);
        
        // In production, implement:
        // - Email notifications
        // - Slack/Discord webhooks
        // - SMS alerts for critical issues
        // - Integration with monitoring services (DataDog, New Relic, etc.)
        
        this.logAlert(alert);
    }

    /**
     * Handle critical errors
     */
    async handleCriticalError(errorRecord) {
        console.error(`🚨 CRITICAL ERROR DETECTED:`, errorRecord);
        
        // In production:
        // - Immediate notification to on-call team
        // - Automatic health check trigger
        // - Consider graceful service degradation
        
        await this.logCriticalError(errorRecord);
    }

    /**
     * Start periodic monitoring tasks
     */
    startPeriodicMonitoring() {
        // System health check every minute
        setInterval(() => {
            this.performHealthCheck();
        }, 60 * 1000);
        
        // Metrics collection every 5 minutes
        setInterval(() => {
            this.collectSystemMetrics();
        }, 5 * 60 * 1000);
        
        // Generate hourly reports
        setInterval(() => {
            this.generateHourlyReport();
        }, 60 * 60 * 1000);
        
        // Daily cleanup
        setInterval(() => {
            this.performDailyCleanup();
        }, 24 * 60 * 60 * 1000);
    }

    /**
     * Perform comprehensive health check
     */
    async performHealthCheck() {
        try {
            const health = {
                timestamp: new Date().toISOString(),
                uptime: this.getUptimeString(),
                memory: process.memoryUsage(),
                cpu: process.cpuUsage(),
                performance: this.getPerformanceMetrics(),
                errors: this.getErrorSummary(),
                status: 'healthy'
            };
            
            // Check if system is unhealthy
            const errorRate = this.getErrorRate();
            const memoryUsage = this.getMemoryUsagePercent();
            
            if (errorRate > this.alertThresholds.errorRate.critical ||
                memoryUsage > this.alertThresholds.memoryUsage.critical) {
                health.status = 'unhealthy';
                this.sendAlert('CRITICAL', 'SYSTEM_UNHEALTHY', health);
            } else if (errorRate > this.alertThresholds.errorRate.warning ||
                      memoryUsage > this.alertThresholds.memoryUsage.warning) {
                health.status = 'degraded';
            }
            
            this.lastHealthCheck = Date.now();
            
            console.log(`💓 Health check completed: ${health.status}`);
            
        } catch (error) {
            this.trackError('HEALTH_CHECK_FAILED', error.message, 'critical');
        }
    }

    /**
     * Collect system metrics
     */
    collectSystemMetrics() {
        const memory = process.memoryUsage();
        const cpu = process.cpuUsage();
        
        // Store memory usage history (keep last 24 hours)
        this.metrics.performance.memoryUsage.push({
            timestamp: Date.now(),
            heapUsed: memory.heapUsed,
            heapTotal: memory.heapTotal,
            rss: memory.rss
        });
        
        // Keep only last 24 hours (288 samples at 5-minute intervals)
        if (this.metrics.performance.memoryUsage.length > 288) {
            this.metrics.performance.memoryUsage.shift();
        }
        
        // Store CPU usage
        this.metrics.performance.cpuUsage.push({
            timestamp: Date.now(),
            user: cpu.user,
            system: cpu.system
        });
        
        if (this.metrics.performance.cpuUsage.length > 288) {
            this.metrics.performance.cpuUsage.shift();
        }
    }

    /**
     * Generate comprehensive monitoring report
     */
    getMonitoringReport() {
        const uptime = Date.now() - this.startTime;
        const totalRequests = this.metrics.requests.total;
        const avgResponseTime = totalRequests > 0 ? 
            this.metrics.performance.responseTimeSum / totalRequests : 0;
        
        return {
            timestamp: new Date().toISOString(),
            uptime: this.getUptimeString(),
            
            requests: {
                total: totalRequests,
                successful: this.metrics.requests.successful,
                failed: this.metrics.requests.failed,
                successRate: totalRequests > 0 ? 
                    (this.metrics.requests.successful / totalRequests * 100).toFixed(2) + '%' : '0%',
                averageResponseTime: Math.round(avgResponseTime) + 'ms',
                slowRequests: this.metrics.performance.slowRequests,
                byEndpoint: Object.fromEntries(this.metrics.requests.byEndpoint),
                byMethod: Object.fromEntries(this.metrics.requests.byMethod)
            },
            
            fileProcessing: {
                processed: this.metrics.files.processed,
                failed: this.metrics.files.failed,
                successRate: this.getFileProcessingSuccessRate(),
                totalSizeProcessed: this.formatBytes(this.metrics.files.totalSizeBytes),
                averageProcessingTime: Math.round(this.metrics.files.averageProcessingTime) + 'ms',
                byType: Object.fromEntries(this.metrics.files.byType)
            },
            
            performance: {
                activeSessions: this.metrics.performance.activeSessions,
                memoryUsage: this.formatBytes(process.memoryUsage().heapUsed),
                memoryUsagePercent: (this.getMemoryUsagePercent() * 100).toFixed(2) + '%',
                uptime: this.getUptimeString()
            },
            
            errors: {
                total: this.metrics.errors.total,
                critical: this.metrics.errors.critical,
                errorRate: (this.getErrorRate() * 100).toFixed(2) + '%',
                recentErrors: this.metrics.errors.recent.slice(0, 10),
                byType: Object.fromEntries(this.metrics.errors.byType)
            },
            
            security: {
                suspiciousActivity: this.metrics.security.suspiciousActivity,
                blockedRequests: this.metrics.security.blockedRequests,
                rateLimitHits: this.metrics.security.rateLimitHits,
                failedValidations: this.metrics.security.failedValidations
            }
        };
    }

    /**
     * Helper methods for calculations
     */
    getErrorRate() {
        const total = this.metrics.requests.total;
        return total > 0 ? this.metrics.requests.failed / total : 0;
    }

    getFileProcessingFailureRate() {
        const total = this.metrics.files.processed + this.metrics.files.failed;
        return total > 0 ? this.metrics.files.failed / total : 0;
    }

    getFileProcessingSuccessRate() {
        const total = this.metrics.files.processed + this.metrics.files.failed;
        return total > 0 ? (this.metrics.files.processed / total * 100).toFixed(2) + '%' : '100%';
    }

    getMemoryUsagePercent() {
        const memory = process.memoryUsage();
        return memory.heapUsed / memory.heapTotal;
    }

    getPerformanceMetrics() {
        return {
            averageResponseTime: this.metrics.requests.total > 0 ? 
                Math.round(this.metrics.performance.responseTimeSum / this.metrics.requests.total) : 0,
            slowRequestsPercent: this.metrics.requests.total > 0 ?
                (this.metrics.performance.slowRequests / this.metrics.requests.total * 100).toFixed(2) + '%' : '0%',
            activeSessions: this.metrics.performance.activeSessions
        };
    }

    getErrorSummary() {
        return {
            total: this.metrics.errors.total,
            critical: this.metrics.errors.critical,
            rate: (this.getErrorRate() * 100).toFixed(2) + '%',
            mostCommon: this.getMostCommonError()
        };
    }

    getMostCommonError() {
        let maxCount = 0;
        let mostCommon = 'none';
        
        for (const [errorType, count] of this.metrics.errors.byType) {
            if (count > maxCount) {
                maxCount = count;
                mostCommon = errorType;
            }
        }
        
        return { type: mostCommon, count: maxCount };
    }

    /**
     * Utility methods
     */
    incrementMapValue(map, key) {
        map.set(key, (map.get(key) || 0) + 1);
    }

    generateErrorId() {
        return Date.now().toString(36) + Math.random().toString(36).substr(2);
    }

    getUptimeString() {
        const uptime = Date.now() - this.startTime;
        const hours = Math.floor(uptime / (1000 * 60 * 60));
        const minutes = Math.floor((uptime % (1000 * 60 * 60)) / (1000 * 60));
        return `${hours}h ${minutes}m`;
    }

    formatBytes(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    /**
     * Logging methods (implement file logging as needed)
     */
    async logSlowRequest(req, responseTime) {
        const logEntry = {
            timestamp: new Date().toISOString(),
            method: req.method,
            url: req.originalUrl,
            responseTime,
            userAgent: req.get('User-Agent'),
            ip: req.ip
        };
        
        console.warn('🐌 Slow request detected:', logEntry);
        // In production: write to log file
    }

    async logSecurityEvent(eventType, details) {
        const logEntry = {
            timestamp: new Date().toISOString(),
            event: eventType,
            details
        };
        
        console.warn('🚨 Security event:', logEntry);
        // In production: write to security log file
    }

    async logAlert(alert) {
        console.error('🚨 ALERT:', alert);
        // In production: write to alert log file
    }

    async logCriticalError(errorRecord) {
        console.error('🚨 CRITICAL ERROR:', errorRecord);
        // In production: write to critical error log file
    }

    async generateHourlyReport() {
        const report = this.getMonitoringReport();
        console.log('📊 Hourly monitoring report generated');
        // In production: send to monitoring dashboard, save to database
    }

    async performDailyCleanup() {
        // Reset daily counters if needed
        // Clean up old logs
        // Generate daily summary report
        console.log('🧹 Daily cleanup completed');
    }
}

module.exports = ProductionMonitoring;