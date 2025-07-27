package com.cyberflux.qwinai.utils

import android.content.Context
import android.os.Build
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Helper class to ensure PDFBox-Android has required glyph list resources
 * Must be called before using any PDFBox functionality
 */
object PDFBoxResourceHelper {

    private const val PDFBOX_RESOURCES_PATH = "com/tom_roush/pdfbox/resources/glyphlist"
    private var isInitialized = false

    private val REQUIRED_FILES = listOf(
        "glyphlist.txt",
        "additional.txt",
        "zapfdingbats.txt"
    )

    /**
     * Initialize PDFBox resources by copying them from assets if needed
     * Call this before using any PDFBox functionality
     */
    @Synchronized
    fun initializePDFBoxResources(context: Context): Boolean {
        // Check if already initialized to avoid redundant work
        if (isInitialized) {
            return true
        }

        return try {
            Timber.d("Initializing PDFBox resources...")

            // Create the target directory in internal storage
            val baseDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // For Android 7.0+ use no-backup directory
                File(context.dataDir, "pdfbox_resources")
            } else {
                // For older devices use files directory
                File(context.filesDir, "pdfbox_resources")
            }

            val targetDir = File(baseDir, PDFBOX_RESOURCES_PATH)
            if (!targetDir.exists()) {
                if (!targetDir.mkdirs()) {
                    Timber.w("Failed to create directory: ${targetDir.absolutePath}")
                    // Try with absolute path
                    targetDir.absoluteFile.mkdirs()
                }
            }

            Timber.d("PDFBox resource directory: ${targetDir.absolutePath}")

            // Verify directory exists
            if (!targetDir.exists() || !targetDir.isDirectory) {
                Timber.e("Failed to create resource directory: ${targetDir.absolutePath}")
                return false
            }

            // Copy each required file from assets
            var allFilesOk = true
            REQUIRED_FILES.forEach { fileName ->
                val targetFile = File(targetDir, fileName)
                if (!targetFile.exists()) {
                    try {
                        // Try with both asset paths - first the simple one, then the full path
                        val assetPaths = listOf(
                            "pdfbox/$fileName",                            // pdfbox/glyphlist.txt
                            "com/tom_roush/pdfbox/resources/glyphlist/$fileName" // full path
                        )

                        var copied = false
                        for (assetPath in assetPaths) {
                            try {
                                copyAssetToFile(context, assetPath, targetFile)
                                Timber.d("Copied PDFBox resource from $assetPath to ${targetFile.absolutePath}")
                                copied = true
                                break
                            } catch (e: Exception) {
                                Timber.d("Could not copy from $assetPath: ${e.message}")
                            }
                        }

                        if (!copied) {
                            // Try creating a blank file with the right name as last resort
                            Timber.w("Creating blank file for $fileName as fallback")
                            targetFile.createNewFile()

                            // For glyphlist.txt, add minimum content to prevent crash
                            if (fileName == "glyphlist.txt") {
                                targetFile.writeText("# Minimal glyphlist to prevent crashes\nspace 0020\n")
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to copy PDFBox resource: $fileName")
                        allFilesOk = false
                    }
                }

                // Verify file was created and is readable
                if (!targetFile.exists() || !targetFile.canRead()) {
                    Timber.e("Resource file $fileName doesn't exist or can't be read")
                    allFilesOk = false
                } else {
                    Timber.d("Verified file $fileName exists (size: ${targetFile.length()} bytes)")
                }
            }

            if (!allFilesOk) {
                Timber.w("Some PDFBox resources could not be copied")
            }

            // Set system property to point to our resources
            // IMPORTANT: Use the BASE directory, not the specific glyphlist directory
            System.setProperty("com.tom_roush.pdfbox.resources", baseDir.absolutePath)

            Timber.d("PDFBox resources path set to: ${baseDir.absolutePath}")

            // Verify system property was set correctly
            val resourcesPath = System.getProperty("com.tom_roush.pdfbox.resources")
            if (resourcesPath != baseDir.absolutePath) {
                Timber.e("System property not set correctly: $resourcesPath")
                return false
            }

            Timber.d("PDFBox resources initialized successfully")

            // Mark as initialized
            isInitialized = true
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize PDFBox resources: ${e.message}")
            false
        }
    }

    private fun copyAssetToFile(context: Context, assetPath: String, targetFile: File) {
        try {
            context.assets.open(assetPath).use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: IOException) {
            throw IOException("Failed to copy asset $assetPath to ${targetFile.absolutePath}", e)
        }
    }

    /**
     * Verifies if a specific glyph list file exists in the resources
     */
    fun verifyGlyphListExists(fileName: String): Boolean {
        val resourceProperty = System.getProperty("com.tom_roush.pdfbox.resources")
        if (resourceProperty.isNullOrEmpty()) {
            return false
        }

        val filePath = "$resourceProperty/$PDFBOX_RESOURCES_PATH/$fileName"
        val file = File(filePath)
        val exists = file.exists() && file.canRead()
        Timber.d("Glyph file $fileName exists at $filePath: $exists (size: ${if (exists) file.length() else 0} bytes)")
        return exists
    }

    /**
     * Reset initialization state (for testing)
     */
    fun reset() {
        isInitialized = false
    }
}