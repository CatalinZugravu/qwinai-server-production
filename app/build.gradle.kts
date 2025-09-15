plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    id("kotlin-parcelize")
    id("com.google.devtools.ksp") version "2.0.0-1.0.24"
    id("dagger.hilt.android.plugin")
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
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

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
        // Only create release signing config if key file exists
        val keyPath = System.getenv("KEY_PATH") ?: project.findProperty("KEY_PATH")?.toString()
        if (keyPath != null && file(keyPath).exists()) {
            create("release") {
                try {
                    storeFile = file(keyPath)
                    storePassword = System.getenv("STORE_PWD") ?: project.property("STORE_PWD").toString()
                    keyAlias = System.getenv("KEY_ALIAS") ?: project.property("KEY_ALIAS").toString()
                    keyPassword = System.getenv("KEY_PWD") ?: project.property("KEY_PWD").toString()
                    enableV1Signing = true
                    enableV2Signing = true
                    println("✅ Release signing configured with key: $keyPath")
                } catch (e: Exception) {
                    println("⚠️ Release signing setup failed: ${e.message}")
                }
            }
        } else {
            println("⚠️ Release signing key not found at: $keyPath - will use debug signing only")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            signingConfig = try {
                // Check if release signing config exists
                signingConfigs.findByName("release")?.let { releaseConfig ->
                    println("🔑 Using release signing for build")
                    releaseConfig
                } ?: run {
                    println("🔧 Using debug signing for build (no release config available)")
                    signingConfigs.getByName("debug")
                }
            } catch (e: Exception) {
                println("⚠️ Signing config error, falling back to debug: ${e.message}")
                signingConfigs.getByName("debug")
            }

            isDebuggable = false

            ndk {
                debugSymbolLevel = "NONE"
                abiFilters += listOf("arm64-v8a")
            }
        }
        debug {
            isMinifyEnabled = false
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }
    }

    buildFeatures {
        viewBinding = true
        dataBinding = false  // Explicitly disable DataBinding to prevent task configuration issues
        compose = true
        buildConfig = true  // Explicitly enable for this module since we have API keys
        aidl = false
        renderScript = false
        resValues = false
        shaders = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all"
        )
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

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
                "DebugProbesKt.bin",
                "**/log4j2.xml",
                "**/log4j.xml",
                "**/log4j.properties",
                "**/log4j2.properties",
                "**/commons-logging.properties"
            )

            pickFirsts += listOf(
                "kotlin/collections/**",
                "kotlin/coroutines/**",
                "kotlin/internal/**",
                "kotlin/jvm/**",
                "kotlin/text/**"
            )
        }
    }


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

        storeArchive {
            enable = false
        }
    }

    lint {
        checkReleaseBuilds = false
    }

    configurations.all {
        resolutionStrategy {
            // Force specific versions for core libraries
            force("androidx.core:core-ktx:1.13.0")
            force("org.jetbrains.kotlin:kotlin-stdlib:2.0.0")
            force("com.squareup.moshi:moshi:1.15.1")
            force("androidx.multidex:multidex:2.0.1")

            // Minimal dependencies for text processing only
            force("commons-io:commons-io:2.15.1")
            force("commons-codec:commons-codec:1.16.0")
            force("com.opencsv:opencsv:5.9")

            // Exclude conflicting modules
            exclude(group = "org.jetbrains", module = "annotations-java5")

            // Coroutines
            force("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
        }
    }
}

dependencies {
    // ====================
    // DEPENDENCY INJECTION - HILT
    // ====================
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // ====================
    // MODERN ANDROID ARCHITECTURE
    // ====================
    
    // Navigation Component
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigation.ui.ktx)
    
    // Adaptive Layouts & Material Design 3
    implementation(libs.androidx.material3.window.size.class1)
    implementation(libs.androidx.window)
    implementation(libs.androidx.window.core)
    
    // StateFlow & Flow
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    
    // Biometrics & Security
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto.ktx)
    
    // Performance Monitoring
    implementation(libs.androidx.tracing)
    implementation(libs.androidx.benchmark.common)
    
    // Advanced UI Components
    implementation(libs.androidx.animation.graphics)
    implementation(libs.androidx.swiperefreshlayout)
    
    // Widget Support
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // ====================
    // CORE ANDROID LIBRARIES
    // ====================
    
    // AndroidX Core
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.multidex)
    implementation(libs.material)
    
    // Security
    implementation(libs.androidx.security.crypto)

    // ====================
    // ARCHITECTURE COMPONENTS
    // ====================
    
    // Lifecycle & ViewModels
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    
    // Background Work
    implementation(libs.androidx.work.runtime)
    
    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android.v173)

    // ====================
    // UI & GRAPHICS
    // ====================
    
    // Animations & UI Components
    implementation(libs.lottie)
    implementation(libs.photoview)
    implementation(libs.flexbox)
    implementation(libs.app.update.ktx)
    
    // Image Loading
    implementation(libs.glide) {
        exclude(group = "com.android.support")
    }
    ksp(libs.glide.compiler)

    // ====================
    // JETPACK COMPOSE
    // ====================
    
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.ui)
    implementation(libs.androidx.foundation)
    implementation(libs.material3)
    implementation(libs.androidx.runtime)
    
    // Compose Debugging
    debugImplementation(libs.ui.tooling)
    implementation(libs.ui.tooling.preview)

    // ====================
    // MARKDOWN PROCESSING
    // ====================
    
    // Markwon Core & Extensions
    implementation(libs.core)
    implementation(libs.ext.strikethrough)
    implementation(libs.ext.tables)
    implementation(libs.ext.tasklist)
    implementation(libs.html)
    implementation(libs.linkify)

    // NEW: Advanced Markwon Features
    implementation(libs.ext.latex)         // LaTeX math formulas
    implementation(libs.inline.parser)     // CRITICAL: Required for JLatexMathPlugin



    // ====================
    // NETWORKING
    // ====================
    
    implementation(libs.retrofit.v290)
    implementation(libs.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor.v4100)
    implementation(libs.retrofit2.kotlin.coroutines.adapter)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    // ====================
    // BILLING & MONETIZATION
    // ====================
    
    // Google Play Billing
    implementation(libs.billing.ktx)
    
    // Huawei HMS Core
    implementation(libs.agconnect.core)
    implementation(libs.base)
    implementation(libs.hms.update)
    implementation(libs.iap)
    
    // Ad Networks - OPTIMIZED: Keep only Google Ads (saves ~20 MB)
    implementation(libs.play.services.ads.v2260)

    // ====================
    // UTILITIES & TOOLS
    // ====================
    
    implementation(libs.timber)
    implementation(libs.mathparser.org.mxparser)

    // CSV Processing
    implementation("com.opencsv:opencsv:5.9")

    // ====================
    // TESTING
    // ====================
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}