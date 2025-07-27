buildscript {
    repositories {
        // PERFORMANCE: Order repositories for faster plugin resolution
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven { 
            url = uri("https://developer.huawei.com/repo/")
            content {
                includeGroupByRegex("com\\.huawei.*")
            }
        }
        // Additional fallback repositories
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
        maven { url = uri("https://repo1.maven.org/maven2/") }
    }
    dependencies {
        // FIXED: Use compatible AGP version with latest dependencies
        classpath("com.android.tools.build:gradle:8.6.0")
        // Align Kotlin version with Compose
        classpath(libs.kotlin.gradle.plugin.v200)
        // Using direct declaration for consistency
        // classpath(libs.agcp) // Temporarily disabled
    }
}

plugins {
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.21" apply false
}

// Removed allprojects block to avoid conflicts with settings.gradle.kts

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory.asFile)
}