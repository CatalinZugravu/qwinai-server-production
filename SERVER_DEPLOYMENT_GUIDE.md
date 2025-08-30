# Complete Server Deployment Guide

## 🚀 One-Click Deployment to Railway

### Step 1: Prepare Your Repository

1. **Create new GitHub repository**: `qwinai-server`
2. **Upload these files**:

```
📦 qwinai-server/
├── 📄 package.json
├── 📄 railway.toml
├── 📄 Dockerfile (optional)
├── 📁 src/
│   ├── app.js
│   ├── 📁 routes/
│   │   ├── userState.js
│   │   └── fileProcessor.js
│   └── 📁 services/
│       ├── fileExtractor.js
│       ├── tokenCounter.js
│       └── documentChunker.js
└── 📄 .env.example
```

### Step 2: package.json
```json
{
  "name": "qwinai-server",
  "version": "1.0.0",
  "description": "QwinAI production server with user state and file processing",
  "main": "src/app.js",
  "scripts": {
    "start": "node src/app.js",
    "dev": "nodemon src/app.js",
    "test": "jest"
  },
  "dependencies": {
    "express": "^4.18.2",
    "helmet": "^7.0.0",
    "cors": "^2.8.5",
    "express-rate-limit": "^6.7.0",
    "pg": "^8.11.0",
    "redis": "^4.6.7",
    "multer": "^1.4.5-lts.1",
    "pdf-parse": "^1.1.1",
    "mammoth": "^1.5.1",
    "xlsx": "^0.18.5",
    "tiktoken": "^1.0.7",
    "node-cron": "^3.0.2"
  },
  "devDependencies": {
    "nodemon": "^2.0.22",
    "jest": "^29.5.0"
  },
  "engines": {
    "node": "18.x"
  }
}
```

### Step 3: railway.toml
```toml
[build]
builder = "NIXPACKS"

[deploy]
healthcheckPath = "/health"
restartPolicyType = "ON_FAILURE"
restartPolicyMaxRetries = 10

[environments.production]
variables = { NODE_ENV = "production" }
```

### Step 4: Deploy to Railway (5 minutes)

1. **Go to railway.app** → Sign up with GitHub
2. **New Project** → Deploy from GitHub repo
3. **Select your repository** → `qwinai-server`
4. **Add Database** → PostgreSQL (automatic)
5. **Add Redis** → Redis (automatic)
6. **Deploy** → Railway handles everything!

**Result**: You'll get a URL like `https://qwinai-server-production.up.railway.app`

## 💰 Cost Breakdown & Optimization

### Railway Pricing (Production Ready)
```
🏗️ Starter Plan: $5/month
├── 500 hours execution time
├── 1GB RAM per service  
├── 1GB storage
├── PostgreSQL included
└── Redis included

🚀 Pro Plan: $20/month (Recommended)
├── Unlimited execution time
├── 8GB RAM per service
├── 100GB storage
├── Custom domains
└── Priority support
```

### Estimated Monthly Costs for 10,000+ Users:
```
📊 Railway Pro: $20/month
├── Web server: ~200 hours CPU time
├── Database: ~2GB storage, 50M queries
├── Redis cache: ~100MB memory
└── File storage: ~5GB uploads

💡 Total: $20/month for 10,000+ active users!
```

## 📈 Auto-Scaling Configuration

### Horizontal Scaling (Multiple Instances)
```javascript
// Add to package.json
"scripts": {
  "start:cluster": "pm2 start ecosystem.config.js"
}

// ecosystem.config.js
module.exports = {
  apps: [{
    name: 'qwinai-server',
    script: 'src/app.js',
    instances: 'max', // Use all CPU cores
    exec_mode: 'cluster',
    env: {
      NODE_ENV: 'production',
      PORT: process.env.PORT || 3000
    }
  }]
}
```

### Database Connection Pooling
```javascript
// Optimized database pool
const db = new Pool({
    connectionString: process.env.DATABASE_URL,
    ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false,
    max: 20,        // Maximum connections
    min: 5,         // Minimum connections
    idle: 10000,    // 10 seconds
    acquire: 60000, // 60 seconds
    evict: 1000     // 1 second
});
```

## 🔍 Monitoring & Performance

### Built-in Health Monitoring
```javascript
// Add comprehensive health check
app.get('/health', async (req, res) => {
    try {
        // Database health
        const dbHealth = await req.db.query('SELECT 1');
        
        // Redis health
        const redisHealth = await req.redis.ping();
        
        // Memory usage
        const memUsage = process.memoryUsage();
        
        res.json({
            status: 'OK',
            timestamp: new Date().toISOString(),
            uptime: process.uptime(),
            database: dbHealth.rowCount > 0 ? 'connected' : 'disconnected',
            redis: redisHealth === 'PONG' ? 'connected' : 'disconnected',
            memory: {
                rss: `${Math.round(memUsage.rss / 1024 / 1024)}MB`,
                heapUsed: `${Math.round(memUsage.heapUsed / 1024 / 1024)}MB`,
                heapTotal: `${Math.round(memUsage.heapTotal / 1024 / 1024)}MB`
            },
            environment: process.env.NODE_ENV
        });
    } catch (error) {
        res.status(503).json({
            status: 'ERROR',
            error: error.message
        });
    }
});
```

### Performance Metrics Endpoint
```javascript
// Add metrics tracking
let requestCount = 0;
let responseTimeSum = 0;
let errorCount = 0;

app.use((req, res, next) => {
    const start = Date.now();
    requestCount++;
    
    res.on('finish', () => {
        const duration = Date.now() - start;
        responseTimeSum += duration;
        
        if (res.statusCode >= 400) {
            errorCount++;
        }
    });
    
    next();
});

app.get('/metrics', (req, res) => {
    res.json({
        requests: {
            total: requestCount,
            errors: errorCount,
            errorRate: (errorCount / requestCount * 100).toFixed(2) + '%'
        },
        performance: {
            avgResponseTime: (responseTimeSum / requestCount).toFixed(2) + 'ms',
            uptime: process.uptime() + 's'
        },
        timestamp: new Date().toISOString()
    });
});
```

## 🛡️ Production Security

### Security Headers & Rate Limiting
```javascript
// Enhanced security configuration
app.use(helmet({
    contentSecurityPolicy: {
        directives: {
            defaultSrc: ["'self'"],
            scriptSrc: ["'self'"],
            styleSrc: ["'self'", "'unsafe-inline'"],
            imgSrc: ["'self'", "data:", "https:"]
        }
    },
    hsts: {
        maxAge: 31536000,
        includeSubDomains: true,
        preload: true
    }
}));

// Strict rate limiting for file processing
const fileProcessingLimiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 10, // Only 10 file uploads per 15 minutes per IP
    message: 'Too many file uploads, please try again later.'
});

app.use('/api/file-processor', fileProcessingLimiter);
```

### Environment Variables Setup
```bash
# .env.example (copy to .env in production)
NODE_ENV=production
PORT=3000
DATABASE_URL=postgresql://user:pass@host:port/dbname
REDIS_URL=redis://user:pass@host:port
ALLOWED_ORIGINS=https://your-android-app.com
MAX_FILE_SIZE_MB=50
MAX_REQUESTS_PER_MINUTE=100
JWT_SECRET=your-super-secret-key-here
```

## 📱 Android App Integration

### Update UserStateManager.kt
```kotlin
// Update this line in UserStateManager.kt
private const val SERVER_BASE_URL = "https://your-railway-domain.railway.app/api/"
```

### Add File Processing to MainActivity
```kotlin
// Add file processing capability
private suspend fun processFileBeforeSending(file: File): String {
    return try {
        val fileProcessingService = FileProcessingService.getInstance(this)
        val result = fileProcessingService.processFile(
            file = file,
            aiModel = selectedModel, // Current AI model
            maxTokensPerChunk = 6000
        )
        
        if (result.tokenAnalysis.exceedsContext) {
            // Handle large files with chunking
            val chunks = fileProcessingService.getOptimalChunksForModel(
                result, selectedModel
            )
            
            // Use first suitable chunk or ask user to choose
            fileProcessingService.createPromptWithFileContent(
                userMessage, chunks.take(1), result.originalFileName
            )
        } else {
            // Use full content for small files
            fileProcessingService.createPromptWithFileContent(
                userMessage, result.chunks, result.originalFileName
            )
        }
        
    } catch (e: Exception) {
        Timber.e(e, "Failed to process file")
        "Error processing file: ${e.message}"
    }
}
```

## 🔧 Maintenance & Updates

### Automated Database Cleanup
```javascript
// Add to src/app.js
const cron = require('node-cron');

// Clean up old processed files every hour
cron.schedule('0 * * * *', async () => {
    try {
        const result = await db.query(`
            DELETE FROM processed_files 
            WHERE expires_at < NOW()
        `);
        console.log(`🧹 Cleaned up ${result.rowCount} expired files`);
    } catch (error) {
        console.error('Cleanup error:', error);
    }
});

// Reset daily credits at midnight UTC
cron.schedule('0 0 * * *', async () => {
    try {
        const today = new Date().toISOString().split('T')[0];
        const result = await db.query(`
            UPDATE user_states 
            SET credits_consumed_today_chat = 0,
                credits_consumed_today_image = 0,
                current_date = $1
            WHERE current_date < $1
        `, [today]);
        console.log(`🔄 Reset daily credits for ${result.rowCount} users`);
    } catch (error) {
        console.error('Credit reset error:', error);
    }
});
```

## 🚀 Deployment Checklist

### Before Launch:
- [ ] Test all API endpoints with Postman/curl
- [ ] Set up monitoring (health checks every 5 minutes)
- [ ] Configure error alerting (email/Slack notifications)
- [ ] Test file upload limits (50MB max recommended)
- [ ] Verify token counting accuracy for your models
- [ ] Test database backups and recovery
- [ ] Load test with 100+ concurrent requests

### Post-Launch:
- [ ] Monitor server metrics daily
- [ ] Review error logs weekly
- [ ] Update dependencies monthly
- [ ] Scale up resources if needed
- [ ] Backup database daily

## 💡 Cost Optimization Tips

### 1. Caching Strategy
- Cache processed files for 1 hour
- Cache user states for 5 minutes
- Use Redis for frequent queries

### 2. File Processing Optimization
- Compress uploads before processing
- Delete temporary files immediately
- Use streaming for large files

### 3. Database Optimization
- Index frequently queried columns
- Archive old user states monthly
- Use connection pooling

**Final Result**: Production-ready server handling 10,000+ users for $20/month!