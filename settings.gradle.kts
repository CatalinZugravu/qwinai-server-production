pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://developer.huawei.com/repo/") }
        maven { url = uri("https://jitpack.io") }  // JitPack repository for PhotoView
    }
}

dependencyResolutionManagement {
    // Updated to correctly use PREFER_SETTINGS mode
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        // PERFORMANCE: Order repositories by likelihood of use for faster resolution
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        
        // Huawei HMS repository
        maven { 
            url = uri("https://developer.huawei.com/repo/")
            content {
                includeGroupByRegex("com\\.huawei.*")
            }
        }
        
        // JitPack repository for GitHub-hosted libraries
        maven { 
            url = uri("https://jitpack.io")
            content {
                includeGroupByRegex("com\\.github.*")
            }
        }
        
        // IronSource repository
        maven { 
            url = uri("https://android-sdk.is.com/")
            content {
                includeGroupByRegex("com\\.ironsource.*")
            }
        }
        
        // Additional repositories for completeness
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
        maven { url = uri("https://repo1.maven.org/maven2/") }
        maven { url = uri("https://plugins.gradle.org/m2/") }
    }
}

rootProject.name = "Qwin AI"
include(":app")