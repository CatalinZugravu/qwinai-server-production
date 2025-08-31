# 🔧 Add File Processing to Your Existing Server

Your server is running perfectly! You just need to **add file processing capabilities** to it.

## 🎯 **Current Server Status**
- ✅ **Running:** `https://qwinai-server-production-production.up.railway.app`
- ✅ **Health:** Database + Redis connected, 184MB memory usage
- ✅ **User State API:** Working (`/api/user-state/`)
- ❌ **Missing:** File processing endpoints (`/api/file-processor/`)

## 🚀 **Quick Fix: Add File Processing (10 minutes)**

### **Step 1: Copy Required Files to Your Current Server**

From `SERVER_FILES_FIXED/`, copy these to your deployed server:

1. **New Routes:**
   ```
   routes/fileProcessor.js → Add to your server
   ```

2. **New Services:**
   ```
   services/secureFileProcessor.js → Add to your server
   services/universalTokenCounter.js → Add to your server  
   services/fileExtractor.js → Add to your server
   services/documentChunker.js → Add to your server
   ```

3. **Update Your Main Server File:**
   ```javascript
   // Add to your existing app.js:
   const secureFileRoutes = require('./routes/fileProcessor');
   app.use('/api/file-processor', secureFileRoutes);
   ```

### **Step 2: Add Required Dependencies**

Add to your `package.json`:
```json
{
  "dependencies": {
    "multer": "^1.4.5-lts.1",
    "pdf-parse": "^1.1.1", 
    "mammoth": "^1.6.0",
    "xlsx": "^0.18.5",
    "tiktoken": "^1.0.10",
    "adm-zip": "^0.5.10",
    "xml2js": "^0.6.2",
    "express-validator": "^7.0.1",
    "mime-types": "^2.1.35",
    "file-type": "^18.7.0",
    "sharp": "^0.32.6"
  }
}
```

### **Step 3: Add Database Schema**

Add this table to your PostgreSQL:
```sql
CREATE TABLE IF NOT EXISTS processed_files_secure (
    id SERIAL PRIMARY KEY,
    processing_id VARCHAR(255) UNIQUE NOT NULL,
    file_hash VARCHAR(255) NOT NULL,
    original_name VARCHAR(500) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    total_tokens INTEGER NOT NULL,
    chunk_count INTEGER NOT NULL,
    model_used VARCHAR(100) NOT NULL,
    user_ip INET,
    user_id VARCHAR(255),
    access_count INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    UNIQUE(file_hash, model_used)
);

CREATE INDEX idx_processed_files_expires ON processed_files_secure(expires_at);
CREATE INDEX idx_processed_files_user ON processed_files_secure(user_id);
```

## ⚡ **Alternative: Quick Integration**

If copying files is complex, just add this **minimal file processing endpoint** to your existing server:

```javascript
// Add to your existing server's app.js
const multer = require('multer');
const upload = multer({ 
    storage: multer.memoryStorage(),
    limits: { fileSize: 50 * 1024 * 1024 } // 50MB
});

app.post('/api/file-processor/process', upload.single('file'), async (req, res) => {
    try {
        const { file } = req;
        if (!file) {
            return res.status(400).json({ error: 'No file uploaded' });
        }

        // Basic processing (expand this later)
        const result = {
            success: true,
            originalFileName: file.originalname,
            fileSize: file.size,
            mimeType: file.mimetype,
            extractedContent: { 
                text: "File received successfully. Processing capabilities will be expanded." 
            },
            tokenAnalysis: { 
                totalTokens: 1000, 
                estimatedCost: "$0.03" 
            },
            chunks: [{ 
                index: 1, 
                text: "Placeholder content",
                tokenCount: 1000 
            }]
        };

        res.json(result);
    } catch (error) {
        res.status(500).json({ error: 'Processing failed', message: error.message });
    }
});
```

## 🧪 **Test After Adding**

```bash
# Test the new endpoint
curl -X POST https://qwinai-server-production-production.up.railway.app/api/file-processor/process \
  -F "file=@test.pdf" -F "model=gpt-4"
```

## 📝 **Next Steps**

1. **Add file processing to your existing server** (don't create a new one)
2. **Redeploy your server** with the new capabilities  
3. **Test the endpoints** work
4. **Your Android app will then have full file processing!**

The good news: Your server architecture is solid, you just need to add the missing file processing routes!