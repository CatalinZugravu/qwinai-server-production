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
```

## Architecture Overview

### Application Structure
- **MyApp.kt**: Application class with device detection, billing initialization, and global services
- **MainActivity.kt**: Main chat interface with Compose UI
- **StartActivity.kt**: App launcher and onboarding
- **Multiple Activities**: Specialized activities for audio, image generation, gallery, settings

### Core Components

#### Data Layer
- **Room Database**: `AppDatabase.kt` with DAOs for conversations and messages
- **Models**: Located in `/model/` - includes `ChatMessage`, `Conversation`, `AIModel`, etc.
- **Repository Pattern**: Data repositories in `/data/` manage local and remote data

#### Network Layer
- **API Services**: Multiple AI service integrations (DeepSeek, AIML, Together AI)
- **Retrofit Setup**: `RetrofitInstance.kt` with custom interceptors
- **Streaming**: Real-time response streaming with `StreamingHandler.kt`

#### UI Layer
- **Fragments**: Home, History, Conversations with ViewPager navigation
- **Adapters**: Optimized RecyclerView adapters with DiffUtil
- **Components**: Custom Compose components and views

#### Business Logic
- **Managers**: BillingManager, ModelManager, ThemeManager, etc.
- **Utils**: Extensive utility classes for file handling, UI operations, etc.
- **Workers**: Background tasks using WorkManager

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

## Build Configuration

### Gradle Setup
- **AGP Version**: 8.6.0
- **Kotlin Version**: 2.0.0
- **Compose Version**: 2025.06.01 BOM
- **Min/Target SDK**: 28/35

### Dependencies
- **UI**: Compose BOM, Material3, Lottie animations
- **Network**: Retrofit 2.11.0, OkHttp 4.12.0
- **Database**: Room 2.7.2
- **Markdown**: Markwon 4.6.2 with extensions
- **Image**: Glide 4.16.0
- **Billing**: Google Play Billing + Huawei IAP

### Signing & Release
- Release signing configured in `gradle.properties`
- ProGuard enabled with custom rules
- Bundle generation for both Play Store and AppGallery

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

## Important Implementation Notes

### Device Detection
The app automatically detects device type (Google Play vs Huawei) for billing:
- Cached detection for performance
- Fallback mechanisms for reliability
- HMS Core availability verification

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

### Security Considerations
- API keys stored in BuildConfig (not in source)
- Encrypted local storage for sensitive data
- Network security configuration
- Proper permission handling

## Troubleshooting

### Common Build Issues
- Clean project if encountering dependency conflicts
- Check `gradle.properties` for correct API keys
- Verify signing configuration for release builds
- Update Android SDK if targeting new API levels

### Runtime Issues
- Check device detection logs for billing problems
- Verify network connectivity for AI services
- Monitor memory usage for large file operations
- Check permissions for camera/microphone features