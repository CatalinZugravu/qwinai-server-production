package com.cyberflux.qwinai.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class GeneratedImage(
    val id: String,
    val filePath: String,
    val fileName: String,
    val aiModel: String,
    val prompt: String,
    val timestamp: Long,
    val fileSize: Long,
    val width: Int,
    val height: Int,
    val isDownloaded: Boolean = false,
    val generationSettings: GenerationSettings? = null
) : Parcelable

@Parcelize
data class GenerationSettings(
    val style: String? = null,
    val quality: String? = null,
    val aspectRatio: String? = null,
    val seed: String? = null
) : Parcelable