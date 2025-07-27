package com.cyberflux.qwinai.network

import com.cyberflux.qwinai.utils.ModelManager
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * OCR API Service for document text extraction
 */
interface OCRApiService {
    @POST("v1/ocr")
    suspend fun processDocument(@Body request: OCRRequest): Response<OCRResponse>
}

/**
 * OCR API Request - matches Mistral OCR API format
 */
data class OCRRequest(
    val model: String = "mistral/mistral-ocr-latest",
    val document: DocumentInput,
    val pages: Any? = null, // String like "3" or "0-2" or IntArray [0, 3, 4]
    val include_image_base64: Boolean? = null,
    val image_limit: Int? = null,
    val image_min_size: Int? = null
)

/**
 * Document input - can be either document_url or image_url
 * Kept for backward compatibility but now using flattened request
 */
sealed class DocumentInput {
    data class DocumentUrl(val type: String = "document_url", val document_url: String) : DocumentInput()
    data class ImageUrl(val type: String = "image_url", val image_url: String) : DocumentInput()
}

/**
 * OCR API Response - matches Mistral OCR format
 */
data class OCRResponse(
    val pages: List<OCRPage>,
    val model: String,
    val usage_info: OCRUsageInfo
)

/**
 * OCR Page in response
 */
data class OCRPage(
    val index: Int,
    val markdown: String,
    val images: List<OCRImage>,
    val dimensions: OCRDimensions
)

/**
 * OCR Image in response
 */
data class OCRImage(
    val id: String,
    val top_left_x: Int,
    val top_left_y: Int,
    val bottom_right_x: Int,
    val bottom_right_y: Int,
    val image_base64: String?
)

/**
 * OCR Page dimensions
 */
data class OCRDimensions(
    val dpi: Int,
    val height: Int,
    val width: Int
)

/**
 * OCR Usage information
 */
data class OCRUsageInfo(
    val pages_processed: Int,
    val doc_size_bytes: Int
)