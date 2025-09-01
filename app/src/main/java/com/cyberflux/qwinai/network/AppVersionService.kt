package com.cyberflux.qwinai.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Service interface for checking app version and update requirements
 */
interface AppVersionService {
    
    @GET("version-check")
    suspend fun checkVersion(
        @Query("package") packageName: String,
        @Query("version") currentVersion: String,
        @Query("platform") platform: String // "google" or "huawei"
    ): Response<VersionCheckResponse>
}

/**
 * Response model for version check API
 */
data class VersionCheckResponse(
    val currentVersion: String,
    val latestVersion: String,
    val minimumRequiredVersion: String,
    val updateRequired: Boolean,
    val forceUpdate: Boolean,
    val updateMessage: String,
    val downloadUrls: UpdateUrls
)

/**
 * Download URLs for different platforms
 */
data class UpdateUrls(
    val googlePlay: String,
    val huaweiAppGallery: String,
    val directDownload: String?
)