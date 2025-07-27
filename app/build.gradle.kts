plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    id("kotlin-parcelize")
    id("com.google.devtools.ksp") version "2.0.0-1.0.21" // Updated KSP version
    // id("com.huawei.agconnect") // For HMS - temporarily disabled
}

android {
    namespace = "com.cyberflux.qwinai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cyberflux.qwinai"
        minSdk = 28
        targetSdk = 35
        versionCode = 18
        versionName = "18"
        vectorDrawables.useSupportLibrary = true
        multiDexEnabled = true // Enable multidex support

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // PERFORMANCE: Optimize vector drawables
        vectorDrawables {
            useSupportLibrary = true
            generatedDensities()
        }

        // BuildConfig fields
        buildConfigField("String", "AIMLAPI_KEY", "\"${project.property("AIMLAPI_KEY")}\"")
        buildConfigField("String", "TOGETHER_AI_KEY", "\"${project.property("TOGETHER_AI_KEY")}\"")
        buildConfigField("String", "GOOGLE_API_KEY", "\"${project.property("GOOGLE_API_KEY")}\"")
        buildConfigField("String", "GOOGLE_SEARCH_ENGINE_ID", "\"${project.property("GOOGLE_SEARCH_ENGINE_ID")}\"")
        buildConfigField("String", "WEATHER_API_KEY", "\"${project.property("WEATHER_API_KEY")}\"")
    }

    signingConfigs {
        create("release") {
            // Move credentials to gradle.properties for better security
            storeFile = file(System.getenv("KEY_PATH") ?: project.property("KEY_PATH").toString())
            storePassword = System.getenv("STORE_PWD") ?: project.property("STORE_PWD").toString()
            keyAlias = System.getenv("KEY_ALIAS") ?: project.property("KEY_ALIAS").toString()
            keyPassword = System.getenv("KEY_PWD") ?: project.property("KEY_PWD").toString()
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true // PERFORMANCE: Enable resource shrinking
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            signingConfig = if (project.hasProperty("RELEASE_STORE_FILE") &&
                !project.property("RELEASE_STORE_FILE").toString().isNullOrEmpty()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            
            // PERFORMANCE: Disable debugging and crash reporting overhead
            isDebuggable = false
            isMinifyEnabled = true
            
            // PERFORMANCE: Optimize for speed
            ndk {
                debugSymbolLevel = "NONE"
            }
        }
        debug {
            isMinifyEnabled = false
            // PERFORMANCE: Remove debug suffix to avoid Huawei AGConnect issues
            // applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // PERFORMANCE: Enable incremental compilation
        isCoreLibraryDesugaringEnabled = false
    }

    kotlinOptions {
        jvmTarget = "17"
        // PERFORMANCE: Enable Kotlin compiler optimizations
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all"
        )
    }

    composeOptions {
        // Updated for better performance
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    // PDFBox assets configuration removed due to compatibility issues

    // Enhanced packaging configuration for size optimization
    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/*.version",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module",
                "META-INF/proguard/**",
                "META-INF/versions/**",
                "META-INF/web-fragment.xml",
                "META-INF/services/**",
                "kotlin/**.kotlin_builtins",
                "**/*.properties",
                "DebugProbesKt.bin"
            )

            // Handle duplicate class files
            pickFirsts += listOf(
                "kotlin/collections/**",
                "kotlin/coroutines/**",
                "kotlin/internal/**",
                "kotlin/jvm/**",
                "kotlin/text/**",
                "io/noties/prism4j/**",
                "**/prism4j/**",
                "**/Prism_*.class"
            )
        }
    }

    // PERFORMANCE: Enhanced bundle configuration for faster installs
    bundle {
        language {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
        
        // PERFORMANCE: Enable on-demand features for faster startup
        storeArchive {
            enable = false // Disable for faster builds
        }
    }

    lint {
        checkReleaseBuilds = false
    }

    // Force consistent dependency versions
    configurations.all {
        resolutionStrategy {
            // Force specific versions for core libraries
            force("androidx.core:core-ktx:1.13.0")
            force("org.jetbrains.kotlin:kotlin-stdlib:2.0.0")
            force("com.google.code.gson:gson:2.10.1")
            force("androidx.multidex:multidex:2.0.1")

            // HMS conflict resolution
            force("com.huawei.hms:base:6.13.0.302")
            force("com.huawei.hms:iap:6.10.0.300")
            force("com.huawei.agconnect:agconnect-core:1.5.2.300")

            // Document processing libraries - PDFBox removed due to compatibility issues
            force("org.apache.poi:poi:5.2.3")
            force("org.apache.poi:poi-ooxml:5.2.3")
            force("org.apache.poi:poi-scratchpad:5.2.3")
            force("org.apache.poi:poi-ooxml-full:5.2.3")
            force("org.apache.xmlbeans:xmlbeans:5.1.1")
            force("org.apache.commons:commons-compress:1.24.0")
            force("commons-io:commons-io:2.14.0")
            force("org.apache.logging.log4j:log4j-api:2.20.0")
            force("commons-codec:commons-codec:1.15")
            force("commons-logging:commons-logging:1.2")
            force("com.opencsv:opencsv:5.8")

            // Resolve annotation conflicts
            force("org.jetbrains:annotations:23.0.0")
            exclude(group = "org.jetbrains", module = "annotations-java5")

            // Exclude the conflicting poi-ooxml-lite
            exclude(group = "org.apache.poi", module = "poi-ooxml-lite")

            // Coroutines
            force("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
            
            // CommonMark - force version compatibility with Markwon 4.6.2
            force("com.atlassian.commonmark:commonmark:0.13.0")
            force("com.atlassian.commonmark:commonmark-ext-gfm-tables:0.13.0")
            exclude(group = "org.commonmark", module = "commonmark")
            exclude(group = "org.commonmark", module = "commonmark-ext-gfm-tables")
            
            // Prism4j - disabled Nekogram version due to compatibility issues
            // Using CodeSyntaxHighlighter regex-based approach instead
            exclude(group = "io.noties", module = "prism4j")
            exclude(group = "io.noties", module = "prism4j-languages")
            exclude(group = "io.noties", module = "annotations")
            exclude(group = "app.nekogram.prism4j", module = "prism4j")
            exclude(group = "app.nekogram.prism4j", module = "prism4j-languages")
            exclude(group = "app.nekogram.prism4j", module = "annotations")
            exclude(group = "app.nekogram.prism4j", module = "prism4j-bundler")
        }
    }
}

// PDFBox configuration removed due to compatibility issues

dependencies {
    // AndroidX Core
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.constraintlayout)
    
    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation(libs.androidx.recyclerview)
    implementation(libs.material)
    implementation(libs.androidx.multidex)

    // Architecture Components
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.core.android)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.app.update.ktx)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.room.external.antlr)
    implementation(libs.androidx.core.animation)
    implementation(libs.androidx.adapters)
    implementation(libs.play.services.appsearch)
    // ML Kit dependencies removed - Unused functionality
    // implementation(libs.play.services.mlkit.text.recognition.common)
    // implementation(libs.image.labeling.default.common)
    // implementation(libs.play.services.mlkit.text.recognition)
    ksp(libs.androidx.room.compiler)

    // Coroutines - unified version
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android.v173)

    // UI Libraries
    implementation(libs.lottie)
    implementation(libs.shimmer)
    implementation(libs.photoview)
    
    // Markwon - Comprehensive Markdown Processing with AI Features
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-tasklist:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")
    implementation("io.noties.markwon:image:4.6.2")
    implementation("io.noties.markwon:image-glide:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")
    implementation("io.noties.markwon:syntax-highlight:4.6.2") {
        exclude(group = "io.noties", module = "prism4j")
        exclude(group = "io.noties", module = "prism4j-languages")
    }
    
    // NEW: Advanced Markwon Features for AI Chat
    implementation("io.noties.markwon:ext-latex:4.6.2")          // LaTeX/Math formulas
    implementation("io.noties.markwon:recycler:4.6.2")           // Performance for long content
    implementation("io.noties.markwon:simple-ext:4.6.2")         // Custom extensions
    
    // Use Markwon's built-in CommonMark dependencies - no extra needed
    
    // Prism4j for syntax highlighting - using regex-based CodeSyntaxHighlighter instead
    // Nekogram dependencies removed due to compatibility issues
    // implementation("app.nekogram.prism4j:prism4j:2.1.0")
    // implementation("app.nekogram.prism4j:prism4j-languages:2.1.0")

    // Image Loading - Single Glide implementation
    implementation(libs.glide) {
        exclude(group = "com.android.support")
    }
    ksp(libs.glide.compiler)

    // Huawei HMS Core and Update SDK - Conditional loading
    implementation(libs.agconnect.core)
    implementation(libs.base)
    implementation(libs.hms.update)
    implementation(libs.iap)

    // Billing
    implementation(libs.billing.ktx)

    // AndroidX Compose - Optimized
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.material3)
    implementation(libs.androidx.runtime)

    // Compose Debugging
    debugImplementation(libs.ui.tooling)
    implementation(libs.ui.tooling.preview)

    // Networking - Single implementation versions
    implementation(libs.retrofit.v290)
    implementation(libs.converter.gson.v290)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor.v4100)
    implementation(libs.retrofit2.kotlin.coroutines.adapter)

    // Preferences & UI
    implementation(libs.androidx.preference)
    implementation(libs.circleimageview)

    implementation(libs.play.services.ads.v2260)

    // AppLovin SDK
    implementation(libs.applovin.sdk.v1210)

    // IronSource SDK
    implementation(libs.mediationsdk.v760)

    // Utilities
    implementation(libs.timber)
    implementation(libs.gson)
    implementation(libs.mathparser.org.mxparser)

    // Document Processing Libraries - Temporary for build fix
    implementation(libs.commons.logging)

    // Apache POI for Office documents - Temporarily restored for build
    implementation("org.apache.poi:poi:5.2.3") {
        exclude(group = "commons-codec", module = "commons-codec")
    }
    implementation("org.apache.poi:poi-ooxml:5.2.3") {
        exclude(group = "org.apache.poi", module = "poi-ooxml-lite")
        exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
        exclude(group = "org.apache.commons", module = "commons-compress")
    }
    implementation(libs.poi.scratchpad)
    implementation(libs.poi.ooxml.full)

    // POI dependencies
    implementation(libs.xmlbeans)
    implementation(libs.commons.compress)
    implementation(libs.commons.io)
    implementation(libs.commons.codec)
    implementation(libs.log4j.api)

    // OpenCSV for CSV processing - Lightweight
    implementation("com.opencsv:opencsv:5.8") {
        exclude(group = "commons-collections", module = "commons-collections")
        exclude(group = "org.apache.commons", module = "commons-lang3")
    }

    // AndroidX Media3 for video support
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-common:1.2.1")

    // AndroidX additional dependencies
    implementation(libs.androidx.documentfile)
    implementation(libs.flexbox)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}