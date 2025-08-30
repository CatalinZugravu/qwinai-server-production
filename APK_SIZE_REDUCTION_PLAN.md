# APK Size Reduction Plan - DeepSeekChat4

## Current Size: 100+ MB → Target: <50 MB

### Phase 1: Remove Heaviest Dependencies (Immediate ~60 MB savings)

1. **Remove TensorFlow Lite + GPU** (Lines 221-222)
```kotlin
// COMMENT OUT OR REMOVE:
// implementation("com.google.mlkit:text-recognition:16.0.1")
// implementation("org.tensorflow:tensorflow-lite:2.14.0")  
// implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
```
**Alternative:** Use cloud OCR (Google Vision API, Azure Cognitive Services)

2. **Consolidate Ad Networks** (Lines 382-384)
```kotlin
// KEEP ONLY ONE AD NETWORK:
implementation(libs.play.services.ads.v2260)    // Keep Google Ads
// implementation(libs.applovin.sdk.v1210)      // Remove AppLovin
// implementation(libs.mediationsdk.v760)       // Remove IronSource
```

3. **Add ABI Filtering**
```kotlin
android {
    ndk {
        abiFilters "arm64-v8a"  // Only 64-bit ARM (covers 95% of devices)
    }
}
```

### Phase 2: Optional Removals (Additional ~20 MB savings)

4. **Remove Huawei HMS** (if not targeting Huawei devices)
```kotlin
// Comment out lines 376-379:
// implementation(libs.agconnect.core)
// implementation(libs.base) 
// implementation(libs.hms.update)
// implementation(libs.iap)
```

5. **Remove Media3** (if no video/audio playback needed)
```kotlin
// Comment out lines 363-366:
// implementation(libs.androidx.media3.exoplayer)
// implementation(libs.androidx.media3.ui)
// implementation(libs.androidx.media3.common)
// implementation(libs.androidx.media3.datasource)
```

6. **Simplify Markwon** (remove heavy extensions)
```kotlin
// Keep core Markwon, remove heavy extensions:
// implementation("io.noties.markwon:syntax-highlight:4.6.2")
// implementation(libs.ext.latex)
```

### Phase 3: Build Optimization

7. **Update bundle configuration** (already good in your build.gradle.kts)
```kotlin
bundle {
    language { enableSplit = true }
    density { enableSplit = true }  
    abi { enableSplit = true }      // ✓ Already enabled
}
```

8. **Verify ProGuard is working**
```kotlin
buildTypes {
    release {
        isMinifyEnabled = true        // ✓ Already enabled
        isShrinkResources = true      // ✓ Already enabled
    }
}
```

## Expected Results
- **Current:** 100+ MB
- **After Phase 1:** ~40-50 MB  
- **After Phase 2:** ~30-40 MB
- **Google Play Store:** Even smaller with App Bundles

## Migration Notes

### OCR Alternative (instead of ML Kit):
```kotlin
// Use cloud OCR API instead of local ML Kit
suspend fun performOCR(imageBase64: String): String {
    val response = ocrApiService.extractText(
        OcrRequest(image = imageBase64)
    )
    return response.extractedText
}
```

### Video Playback Alternative (instead of Media3):
```kotlin
// Use system video player for basic video playback
fun playVideo(videoUrl: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(videoUrl), "video/*")
    }
    startActivity(intent)
}
```