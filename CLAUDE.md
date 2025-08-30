# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is DeepSeekChat4, an advanced Android AI chat application built with Kotlin and Jetpack Compose. The app features comprehensive AI chat capabilities, image generation, voice/audio processing, OCR functionality, and premium subscription features with support for both Google Play and Huawei AppGallery stores.

## Project Structure

### Core Application
- **Package**: `com.cyberflux.qwinai`
- **Target SDK**: 35, **Min SDK**: 28
- **Build System**: Gradle with Kotlin DSL (.kts files)
- **Architecture**: MVVM with Android Architecture Components
- **UI Framework**: Jetpack Compose + View Binding hybrid
- **Database**: Room with SQLite
- **Network**: Retrofit + OkHttp with coroutines

### Key Technologies
- **Language**: Kotlin with coroutines
- **UI**: Jetpack Compose + Material Design 3 + custom themes
- **Dependency Injection**: Manual DI with singleton pattern
- **Image Processing**: Glide for loading, custom generators for AI images
- **Markdown**: Markwon 4.6.2 with comprehensive feature support
- **Billing**: Unified system supporting both Google Play Billing and Huawei IAP
- **Ads**: Multi-platform (AdMob, AppLovin, IronSource)

## Common Development Commands

### Android Development
```bash
# Navigate to project directory
cd AndroidStudioProjects/DeepSeekChat4

# Clean and build
./gradlew clean build

# Run debug build
./gradlew assembleDebug

# Run release build (requires signing config)
./gradlew assembleRelease

# Install debug APK
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Check dependencies
./gradlew dependencies

# Generate bundle for Play Store
./gradlew bundleRelease
```

### Linting and Code Quality
```bash
# Run lint checks
./gradlew lint

# Check for dependency updates
./gradlew dependencyUpdates

# Run specific test class
./gradlew test --tests "com.cyberflux.qwinai.TestClassName"

# Build with specific configuration
./gradlew assembleDebug -Pandroid.useAndroidX=true

# Generate APK and Bundle simultaneously
./gradlew assembleRelease bundleRelease
```

## Architecture Overview

This is a complex AI chat application with a hybrid architecture combining MVVM, Repository pattern, and Clean Architecture principles. The app uses Hilt for dependency injection and follows Android's recommended architecture patterns.

### Application Structure
- **MyApp.kt**: Application class with device detection, billing initialization, and global services (Hilt @HiltAndroidApp)
- **MainActivity.kt**: Main chat interface with hybrid View Binding + minimal Compose components (4000+ lines)
- **StartActivity.kt**: App launcher with conversation loading and theme initialization
- **Specialized Activities**: AudioAiActivity, ImageGenerationActivity, SettingsActivity, BlockingUpdateActivity, etc.

### Core Architecture Patterns

#### 1. Unified Management Pattern
Key managers follow a unified singleton pattern with lifecycle-aware initialization:
- `UnifiedStreamingManager`: Central streaming state management
- `UnifiedUpdateManager`: App update coordination
- `UnifiedFileHandler`: File operations across the app
- Pattern: Static object with lazy initialization, SharedPreferences caching, coroutine scopes

#### 2. Provider Pattern (Platform Detection)
- `BillingManager` detects Google Play vs Huawei and initializes appropriate provider
- `AdMediationManager` switches between AdMob/AppLovin based on device type
- `DeviceDetectionManager` caches device type detection results

#### 3. Streaming Architecture
Real-time streaming uses a sophisticated state management system:
- `StreamingHandler`: Core streaming logic with cancellation tokens
- `StreamingStateManager`: Session state tracking
- `StreamingCancellationManager`: Per-stream cancellation
- `UnifiedStreamingManager`: Configuration and session management

#### 4. Markdown Processing Pipeline
Multi-stage markdown rendering with custom plugins:
- `UnifiedMarkdownProcessor`: Main processor with plugin system
- `WebSearchCitationPlugin`: Custom citation handling
- `CustomCodeBlockPlugin`: Syntax highlighting with Prism4j
- `FixedCodeBlockPlugin`: Performance-optimized code rendering

### Core Components Deep Dive

#### Data Layer Architecture
- **Room Database**: `AppDatabase.kt` with sophisticated indexing strategy
  - Primary entities: `ChatMessage` (30+ fields), `Conversation`, with composite indices
  - Converters for complex types (Lists, Maps, enums)
  - Migration strategy handled by `DatabaseMigrationManager.kt`
- **Models**: 17 model classes with rich domain logic
  - `ChatMessage`: Complex entity with versioning, streaming state, web search integration
  - `AIModel`: Model configuration with capabilities and pricing
  - `AIParameters`: Runtime AI parameters (temperature, top_p, etc.)
  - Relationship mapping: Parent-child messages, conversation grouping
- **Repository Pattern**: 
  - `ChatRepositoryImpl`: Central data access with caching
  - Domain-driven design with use cases in `/core/domain/usecase/`

#### Network Layer Deep Dive
- **Multi-Provider API Architecture**:
  - `AimlApiService`: Primary AI service with streaming support
  - `OCRApiService`: Document processing and text extraction
  - `ImageGenerationHttpClient`: AI image generation across multiple providers
  - `WebSearchUtils`: Integrated web search capabilities
- **Retrofit Configuration**:
  - Custom interceptors for auth, logging, retry logic
  - Moshi serialization with custom adapters
  - Streaming response handling with okio.Buffer
- **Streaming Implementation**:
  - Server-sent events (SSE) parsing
  - Incremental UI updates with `StringBuilder` accumulation
  - Cancellation token system for interrupting streams
  - Web search integration during streaming

#### UI Layer Deep Dive
- **Hybrid Architecture**: View Binding (primary) + Compose (specific components)
- **Fragment Architecture**:
  - `BaseConversationsFragment`: Abstract base with common functionality
  - `HomeFragment`, `HistoryFragment`: Specialized conversation views
  - ViewPager2 navigation with fragment state management
- **Adapter Optimization**:
  - `ChatAdapter`: 2000+ lines, sophisticated view recycling
  - Multiple view types: TEXT, IMAGE, DOCUMENT, AUDIO, etc.
  - `DiffUtil` implementation for efficient updates
  - View caching with `LruCache` for markdown rendering
- **Custom Components**:
  - `MarkdownTextView`: Custom TextView with markdown support
  - `DotLoadingTextView`: Animated loading indicators
  - `PulsingGlowTextView`: Custom animations

#### Business Logic Architecture
- **Manager Pattern**: 20+ specialized managers
  - `BillingManager`: Platform-aware billing with provider abstraction
  - `ThemeManager`: Dynamic theming with accent color customization
  - `ModelManager`: AI model selection and configuration
  - `FileCacheManager`: Efficient file caching and cleanup
- **State Management**:
  - SharedPreferences with `PrefsManager` wrapper
  - In-memory caches with TTL (Time-To-Live)
  - Coroutine-based async state updates
- **Background Processing**:
  - `WorkManager` for periodic tasks
  - `CheckSubscriptionWorker`: Subscription validation
  - `ConversationCleanupWorker`: Database maintenance
  - `CreditResetWorker`: Usage limit resets

### Key Features

#### AI Chat System
- Multi-model support (GPT, Claude, Gemini, DeepSeek, etc.)
- Real-time streaming responses
- Message history with conversation management
- Advanced markdown rendering with syntax highlighting

#### Multi-Platform Billing
- Automatic device detection (Google Play vs Huawei)
- Unified billing interface with provider abstraction
- Subscription management and credit system

#### Image Generation
- Multiple AI image models (DALL-E, Stable Diffusion, Flux, etc.)
- Gallery management with local storage
- Image editing and sharing capabilities

#### Voice & Audio
- Speech-to-text and text-to-speech
- Audio message recording and playback
- Voice chat interface

#### File Processing
- OCR for document scanning
- Office document processing (PDF, Word, Excel, PowerPoint)
- File upload and attachment system

## Data Flow Architecture

### Message Processing Pipeline
1. **User Input** → `MainActivity.sendMessage()` → `OptimizedMessageHandler`
2. **Pre-processing** → `ModelValidator.validateInput()` → `ResponseInstructionsBuilder`
3. **API Request** → `AimlApiService` → `StreamingHandler.processStream()`
4. **Real-time Updates** → `UnifiedStreamingManager` → `ChatAdapter.updateMessage()`
5. **Post-processing** → `UnifiedMarkdownProcessor` → UI Rendering
6. **Persistence** → `AppDatabase` → `ChatRepositoryImpl`

### State Management Flow
- **Global State**: `MyApp` → Device detection, billing, theme initialization
- **Activity State**: `MainActivity` → Conversation management, UI state
- **Fragment State**: Individual fragments manage their ViewModels
- **Streaming State**: `UnifiedStreamingManager` → Session tracking, cancellation
- **Persistent State**: SharedPreferences via `PrefsManager` → User settings, cache

### Billing Flow (Platform-Aware)
1. **Device Detection** → `DeviceDetectionManager.isHuaweiDevice()`
2. **Provider Selection** → `BillingManager.activeProvider`
3. **Purchase Flow** → Platform-specific provider (Google/Huawei)
4. **Validation** → Server-side verification → Local state update
5. **Subscription Status** → Background workers → UI updates

## Development Workflows & Common Scenarios

### Adding a New AI Model
```kotlin
// 1. Update model configuration
// File: app/src/main/java/com/cyberflux/qwinai/model/AIModel.kt
val newModel = AIModel(
    id = "new-model-id",
    name = "New Model",
    provider = "provider-name",
    capabilities = listOf("chat", "streaming"),
    maxTokens = 4096
)

// 2. Add to model manager
// File: app/src/main/java/com/cyberflux/qwinai/utils/ModelManager.kt
fun getAvailableModels(): List<AIModel> {
    return listOf(/* existing models */, newModel)
}

// 3. Update API handling
// File: app/src/main/java/com/cyberflux/qwinai/network/ModelApiHandler.kt
when (model.provider) {
    "provider-name" -> handleNewProviderRequest(request)
    // existing cases...
}

// 4. Test integration
./gradlew test --tests "*ModelTest*"
```

### Implementing a New Feature (Step-by-Step)
1. **Model Layer**: Define data structures in `/model/`
2. **Database Layer**: Add Room entities, DAOs, migrations
3. **Network Layer**: Create API service interfaces
4. **Repository Layer**: Implement data access patterns
5. **UI Layer**: Create fragments/activities, adapters
6. **Business Logic**: Add managers, utilities
7. **Testing**: Unit tests, integration tests
8. **ProGuard**: Update rules for new classes

### Debugging Streaming Issues
```bash
# Enable verbose logging
adb shell setprop log.tag.StreamingHandler VERBOSE
adb shell setprop log.tag.UnifiedStreamingManager VERBOSE

# Monitor streaming logs
adb logcat -s StreamingHandler UnifiedStreamingManager

# Check streaming state
# Look for: "🔄 STREAMING SESSION" messages
# Verify: Session IDs, cancellation tokens, buffer states
```

### Adding Custom Markdown Plugin
```kotlin
// 1. Create plugin class
class CustomMarkdownPlugin : AbstractMarkwonPlugin() {
    override fun configureParser(builder: Parser.Builder) {
        // Configure custom syntax
    }
    
    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        // Configure rendering
    }
}

// 2. Register in UnifiedMarkdownProcessor
val markwon = Markwon.builder(context)
    .usePlugin(CustomMarkdownPlugin())
    .build()

// 3. Update ProGuard rules
-keep class com.cyberflux.qwinai.utils.CustomMarkdownPlugin { *; }
```

### Performance Optimization Workflow
1. **Profile**: Use Android Studio Profiler → Memory, CPU, Network
2. **Identify**: Look for memory leaks, excessive allocations
3. **Common Issues**:
   - `ChatAdapter`: Large message lists → Implement pagination
   - `Markwon`: Heavy rendering → Cache parsed results
   - `Glide`: Image loading → Configure memory cache
4. **Monitor**: Use `PerformanceMonitor.kt` for runtime metrics
5. **Test**: Verify improvements with synthetic data

### Release Workflow
```bash
# 1. Update version numbers
# File: app/build.gradle.kts
versionCode = X
versionName = "X.X"

# 2. Update ProGuard rules for new classes
# File: app/proguard-rules.pro

# 3. Test release build
./gradlew clean assembleRelease bundleRelease

# 4. Test on multiple devices
# - Google Play device (Pixel, Samsung)  
# - Huawei device (P30, Mate series)

# 5. Verify billing functionality
# Test subscription flows on both platforms

# 6. Check APK size and optimize if needed
./gradlew analyzeReleaseBundle
```

## Build Configuration

### Gradle Setup
- **AGP Version**: 8.10.1
- **Kotlin Version**: 2.0.0
- **Compose Version**: 2025.08.00 BOM
- **Min/Target SDK**: 28/35
- **Java Version**: 17
- **KSP Version**: 2.0.0-1.0.24

### Dependencies Deep Analysis

#### Core Android & Jetpack
- **Core**: `androidx.core:core-ktx:1.17.0` - AndroidX core functionality
- **Compose BOM**: `2025.08.00` - Latest Compose libraries with Material3
- **Lifecycle**: `2.9.2` - ViewModel, LiveData, lifecycle-aware components
- **Navigation**: `2.9.3` - Fragment navigation and UI state management
- **Work**: `2.10.3` - Background processing with constraints
- **Room**: `2.7.2` - Local database with coroutines support
- **Hilt**: `2.51.1` - Dependency injection (Dagger-based)

#### Networking & Serialization
- **Retrofit**: `2.11.0` - REST API client with coroutines
- **OkHttp**: `4.12.0` - HTTP client with logging, caching, retry
- **Moshi**: `1.15.1` - JSON serialization with Kotlin support
- **Coroutines**: `1.8.0` - Async programming with structured concurrency

#### UI & Graphics
- **Material3**: Latest Material Design 3 components
- **Glide**: `4.16.0` - Image loading with memory/disk caching
- **Lottie**: `6.6.6` - Vector animations and micro-interactions
- **PhotoView**: `2.3.0` - Zoomable image views
- **FlexboxLayout**: `3.0.0` - Flexible layouts for dynamic content

#### Markdown & Text Processing
- **Markwon Core**: `4.6.2` - Markdown parsing and rendering
- **Markwon Extensions**: Tables, task lists, strikethrough, LaTeX support
- **Prism4j**: `2.0.0` - Syntax highlighting with 100+ languages
- **Syntax Highlighting**: Professional code block rendering
- **Citation Plugin**: Custom web search result citations

#### Platform Integration
- **Google Play Billing**: `8.0.0` - In-app purchases and subscriptions
- **Huawei HMS**: IAP `6.10.0.300`, Update `4.0.4.301`, Base `6.13.0.302`
- **Google Ads**: `24.5.0` - Ad mediation and display
- **Security**: `androidx.security:security-crypto:1.1.0` - Encrypted storage

#### Utilities & Tools
- **Timber**: `5.0.1` - Structured logging with tree-based configuration
- **CSV Processing**: `opencsv:5.9` - Document parsing capabilities
- **Math Parser**: `MathParser.org-mXparser:5.2.1` - Calculator functionality
- **File Detection**: `juniversalchardet:2.4.0` - Character encoding detection

#### Development & Testing
- **JUnit**: `4.13.2` - Unit testing framework
- **Espresso**: `3.7.0` - UI testing and automation
- **AndroidX Test**: `1.3.0` - Testing utilities and runners

### Signing & Release
- Release signing configured in `gradle.properties` (environment variables supported)
- ProGuard enabled with comprehensive rules (`proguard-rules.pro` - 650+ lines)
- Bundle generation for both Play Store and AppGallery
- Multi-APK support with ABI splitting (arm64-v8a optimized)
- R8 code shrinking and resource optimization enabled

## Development Guidelines

### Code Organization
- Follow package-by-feature structure within main packages
- Use consistent naming conventions (PascalCase for classes, camelCase for functions)
- Implement proper error handling for all network operations
- Use coroutines for asynchronous operations

### UI Development
- Prefer Jetpack Compose for new UI components
- Use Material Design 3 components and theming
- Implement proper state management with ViewModels
- Support dark/light themes and custom theme variants

### Data Management
- Use Room for local database operations
- Implement proper caching strategies
- Handle offline scenarios gracefully
- Use encrypted storage for sensitive data (API keys, tokens)

### Testing
- Unit tests in `src/test/`
- Instrumented tests in `src/androidTest/`
- Test coverage for critical business logic
- UI testing for main user flows

## Code Navigation & Organization Guide

### Quick Navigation by Feature
```
📁 Core Features
├── 💬 Chat System: MainActivity.kt, ChatAdapter.kt, StreamingHandler.kt
├── 🏪 Billing: /billing/*.kt, DeviceDetectionManager.kt
├── 🎨 Image Generation: ImageGenerationActivity.kt, ImageGenerationManager.kt
├── 🎤 Audio: AudioAiActivity.kt, /network/AudioResponseHandler.kt
├── 📄 File Processing: /utils/DocumentContentExtractor.kt, OCRCameraActivity.kt
├── 📝 Markdown: UnifiedMarkdownProcessor.kt, /utils/*Plugin.kt
└── ⚙️ Settings: SettingsActivity.kt, ThemeManager.kt

📁 Architecture Layers  
├── 🗄️ Data: /database/*.kt, /model/*.kt
├── 🌐 Network: /network/*.kt, RetrofitInstance.kt
├── 🎯 Business Logic: /utils/*Manager.kt, /workers/*.kt
└── 🎨 UI: /adapter/*.kt, /ui/*.kt, fragments
```

### Key Class Hierarchies
- **Activities**: `BaseThemedActivity` → `MainActivity`, `AudioAiActivity`, etc.
- **Fragments**: `BaseConversationsFragment` → `HomeFragment`, `HistoryFragment`
- **Adapters**: Base patterns with view holder recycling and DiffUtil
- **Managers**: Singleton pattern with lifecycle awareness
- **Models**: Room entities with complex relationships and indexing

### Finding Specific Functionality
- **Streaming Logic**: Search for `StreamingHandler`, `UnifiedStreamingManager`
- **Billing Issues**: Look in `/billing/` and `DeviceDetectionManager.kt`
- **Markdown Problems**: Check `UnifiedMarkdownProcessor.kt` and `*Plugin.kt` files
- **Database Queries**: Find DAOs in `/database/` or search for `@Query`
- **API Integration**: Check `/network/` and specific service files
- **Theme/UI Issues**: Look in `ThemeManager.kt`, `UiUtils.kt`
- **File Operations**: Search in `/utils/` for `File*` classes
- **Performance Issues**: Check `PerformanceMonitor.kt`, `MemoryManager.kt`

## Testing, Debugging & Maintenance

### Testing Strategy
```bash
# Unit Tests Structure
src/test/java/com/cyberflux/qwinai/
├── billing/         # Billing provider tests
├── utils/           # Utility class tests  
├── network/         # API service tests
└── database/        # Repository tests

# Running Specific Tests
./gradlew test --tests "*.BillingManagerTest"
./gradlew test --tests "*Stream*"
./gradlew connectedAndroidTest --tests "*ChatAdapterTest*"

# Testing with Different Devices
# Google Play device
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunner.arguments.device=google

# Huawei device (requires HMS)
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunner.arguments.device=huawei
```

### Debugging Techniques

#### Logging System
```kotlin
// Structured logging with Timber
Timber.d("🔄 Streaming session started: %s", sessionId)
Timber.w("⚠️ Device detection cached result: %s", isHuawei)
Timber.e("❌ Billing error: %s", exception.message)

// Enable debugging logs
adb shell setprop log.tag.BillingManager DEBUG
adb shell setprop log.tag.StreamingHandler VERBOSE
adb logcat -s BillingManager:D StreamingHandler:V
```

#### Common Debugging Scenarios
```bash
# Streaming Issues
adb logcat | grep -E "(StreamingHandler|UnifiedStreamingManager|STREAMING SESSION)"

# Billing Problems  
adb logcat | grep -E "(BillingManager|DeviceDetectionManager|Huawei|GooglePlay)"

# Markdown Rendering Issues
adb logcat | grep -E "(MarkdownProcessor|CommonMark|Markwon)"

# Performance Issues
adb logcat | grep -E "(PerformanceMonitor|MemoryManager|OOM)"

# Database Issues
adb logcat | grep -E "(Room|SQLite|Database|Migration)"
```

#### Debug Builds vs Release
```kotlin
// Debug-specific code
if (BuildConfig.DEBUG) {
    // Enable additional logging
    Timber.plant(Timber.DebugTree())
    // Show debug overlays
}

// Release-specific optimizations
if (!BuildConfig.DEBUG) {
    // Disable verbose logging
    // Enable performance monitoring
}
```

### Maintenance Procedures

#### Database Maintenance
```kotlin
// Database migration example
@Database(version = 2, entities = [...], exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN newField TEXT")
            }
        }
    }
}
```

#### Regular Maintenance Tasks
1. **Weekly**: 
   - Check error reports in Play Console/AppGallery
   - Review performance metrics
   - Update dependencies if security patches available

2. **Monthly**:
   - Run full test suite on multiple devices
   - Review and update ProGuard rules
   - Analyze APK size and optimize if needed
   - Clean up unused resources and code

3. **Quarterly**:
   - Major dependency updates
   - Database schema optimizations
   - Performance profiling and optimization
   - Security audit of API keys and encryption

#### Performance Monitoring
```kotlin
// Built-in performance monitoring
PerformanceMonitor.startTiming("messageProcessing")
// ... processing logic
PerformanceMonitor.endTiming("messageProcessing")

// Memory monitoring
MemoryManager.checkMemoryUsage()
MemoryManager.optimizeIfNeeded()

// Streaming performance
UnifiedStreamingManager.getPerformanceMetrics()
```

#### Error Handling Patterns
```kotlin
// Centralized error handling
try {
    // Risky operation
} catch (e: Exception) {
    ErrorManager.handleError(
        error = e,
        context = "billing_flow",
        recoverable = true,
        userMessage = "Purchase failed. Please try again."
    )
}

// Graceful degradation
val result = SmartRetryManager.executeWithRetry(
    maxRetries = 3,
    backoffMs = 1000
) {
    // Network operation
}
```

## Important Implementation Notes

### Device Detection
The app automatically detects device type (Google Play vs Huawei) for billing:
- Cached detection for performance (`DeviceDetectionManager.kt`)
- Fallback mechanisms for reliability
- HMS Core availability verification
- Detection results cached in SharedPreferences

### ProGuard Configuration
Critical ProGuard rules in `proguard-rules.pro`:
- **HMS/Huawei IAP**: Complete preservation of Huawei billing classes
- **Markwon/CommonMark**: HTML entities and markdown parsing classes
- **Streaming System**: All streaming managers and state classes
- **Startup Classes**: MyApp, MainActivity, StartActivity fully preserved
- **API Models**: All network models and Moshi serialization
- **Room Database**: DAO classes and entity relationships

### API Integration
- Multiple AI service endpoints configured
- Streaming response handling
- Rate limiting and error recovery
- API key management via BuildConfig

### Performance Optimizations
- LazyLoading for large lists
- Image caching and optimization
- Background service management
- Memory leak prevention

### Security Implementation Deep Dive

#### API Key Management
```kotlin
// BuildConfig integration (gradle.properties → BuildConfig)
buildConfigField("String", "AIMLAPI_KEY", "\"${project.property("AIMLAPI_KEY")}\"")

// Secure access pattern
object SecureConfigManager {
    fun getApiKey(): String {
        return if (BuildConfig.DEBUG) {
            BuildConfig.AIMLAPI_KEY
        } else {
            // Additional obfuscation for release builds
            decodeKey(BuildConfig.AIMLAPI_KEY)
        }
    }
}

// Never commit keys to source control
# gradle.properties (not in VCS)
AIMLAPI_KEY=your_api_key_here
TOGETHER_AI_KEY=your_key_here
```

#### Data Encryption
```kotlin
// Encrypted SharedPreferences
private fun getEncryptedPrefs(): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    return EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}
```

#### Network Security
```xml
<!-- app/src/main/res/xml/network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">api.aimlapi.com</domain>
        <trust-anchors>
            <certificates src="system"/>
        </trust-anchors>
    </domain-config>
</network-security-config>
```

#### Platform-Specific Security
- **Huawei Devices**: HMS Core security validation
- **Google Play**: Play Integrity API for app authenticity
- **Rooted Device Detection**: Security manager prevents sensitive operations
- **Certificate Pinning**: Network layer validates server certificates

### Platform-Specific Considerations

#### Huawei HMS Integration
```kotlin
// HMS availability check
fun isHMSCoreAvailable(): Boolean {
    val result = HuaweiApiAvailability.getInstance()
        .isHuaweiMobileServicesAvailable(context)
    return ConnectionResult.SUCCESS == result
}

// Fallback strategies
when {
    isHMSCoreAvailable() -> initializeHuaweiServices()
    isGooglePlayAvailable() -> initializeGoogleServices()
    else -> initializeStandaloneMode()
}
```

#### Device Detection Strategy
```kotlin
// Multi-level device detection
object DeviceDetectionManager {
    fun detectDevice(): DeviceType {
        return when {
            // 1. Check HMS Core availability
            isHMSCoreInstalled() -> DeviceType.HUAWEI
            // 2. Check manufacturer
            Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true) -> DeviceType.HUAWEI
            // 3. Check Google Play Services
            isGooglePlayServicesAvailable() -> DeviceType.GOOGLE_PLAY
            // 4. Default fallback
            else -> DeviceType.GENERIC
        }
    }
}
```

### Performance Optimization Deep Dive

#### Memory Management
```kotlin
// Efficient message list handling
class ChatAdapter {
    private val messageCache = LruCache<String, SpannableString>(100)
    private val imageCache = LruCache<String, Bitmap>(50)
    
    fun optimizeMemoryUsage() {
        // Release unused resources
        messageCache.trimToSize(50)
        imageCache.evictAll()
        
        // Force garbage collection if needed
        if (getAvailableMemory() < MEMORY_THRESHOLD) {
            System.gc()
        }
    }
}
```

#### Streaming Optimization
```kotlin
// Buffer management for streaming
object StreamingOptimizer {
    private const val OPTIMAL_BUFFER_SIZE = 8192
    private const val MAX_ACCUMULATED_CHARS = 100_000
    
    fun optimizeStreamBuffer(currentLength: Int): Int {
        return when {
            currentLength < 1000 -> 256      // Small responses
            currentLength < 10000 -> 1024    // Medium responses  
            currentLength < 50000 -> 2048    // Large responses
            else -> OPTIMAL_BUFFER_SIZE      // Very large responses
        }
    }
}
```

#### ProGuard Advanced Configuration
```proguard
# Performance-critical classes - keep completely
-keep class com.cyberflux.qwinai.utils.UnifiedStreamingManager { *; }
-keep class com.cyberflux.qwinai.adapter.ChatAdapter$ChatViewHolder { *; }

# Aggressive optimization for non-critical classes
-optimizations !code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Keep performance monitoring
-keep class com.cyberflux.qwinai.utils.PerformanceMonitor {
    public void startTiming(java.lang.String);
    public void endTiming(java.lang.String);
}
```

## Troubleshooting

### Common Build Issues
- **Dependency Conflicts**: Clean project and invalidate caches: `./gradlew clean && ./gradlew build --refresh-dependencies`
- **API Key Issues**: Verify `gradle.properties` contains all required BuildConfig keys (AIMLAPI_KEY, TOGETHER_AI_KEY, GOOGLE_API_KEY, etc.)
- **Signing Issues**: Ensure environment variables (KEY_PATH, STORE_PWD, KEY_ALIAS, KEY_PWD) are set for release builds
- **ProGuard Issues**: Check `proguard-rules.pro` if classes are being obfuscated incorrectly
- **KSP/Kapt Issues**: Clean build directory and rebuild: `rm -rf app/build && ./gradlew build`

### Runtime Issues
- **Device Detection**: Check logs for Huawei HMS Core availability (cached results in PrefsManager)
- **Billing Problems**: Verify device type detection and appropriate billing provider initialization
- **Markdown Crashes**: Ensure CommonMark HTML entities are preserved (ProGuard issue)
- **Streaming Issues**: Check UnifiedStreamingManager initialization and network connectivity
- **Memory Issues**: Monitor large file operations, especially image processing and OCR

## Future Development Considerations

### Scalability Roadmap
- **Message Pagination**: Implement lazy loading for conversations with 1000+ messages
- **Cloud Sync**: Consider Firebase/AWS integration for cross-device synchronization  
- **AI Model Caching**: Local model caching for offline functionality
- **Multi-language Support**: I18n framework for global deployment
- **Advanced Analytics**: User behavior tracking with privacy compliance

### Technical Debt & Refactoring Opportunities
- **MainActivity Complexity**: 4000+ lines - consider feature-based modularization
- **ChatAdapter Optimization**: Implement view recycling pool for better memory usage
- **Dependency Injection**: Migrate remaining singletons to proper Hilt injection
- **Architecture Components**: Consider migrating to Compose Navigation and ViewModel composition

### Code Quality Metrics to Monitor
- **Cyclomatic Complexity**: Keep MainActivity methods < 15 complexity
- **Method Length**: Limit methods to < 50 lines for maintainability  
- **Class Coupling**: Monitor dependencies between core managers
- **Test Coverage**: Maintain > 70% coverage for critical business logic

### Performance Targets
- **App Startup**: < 2 seconds cold start on mid-range devices
- **Message Rendering**: < 100ms for markdown processing
- **Streaming Latency**: < 500ms first token response
- **Memory Usage**: < 150MB peak usage during normal operation
- **APK Size**: < 50MB after optimization and splitting