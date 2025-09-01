package com.cyberflux.qwinai.utils

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any
import java.io.*

/**
 * Comprehensive test suite for DocumentContentExtractor
 * 
 * Tests all supported document formats:
 * - PDF files
 * - Word documents (.docx, .doc)
 * - Excel files (.xlsx, .xls)
 * - PowerPoint presentations (.pptx, .ppt)
 * - Text files
 * - CSV files
 */
class DocumentContentExtractorTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockUri: Uri

    private lateinit var extractor: DocumentContentExtractor

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        extractor = DocumentContentExtractor(mockContext)
    }

    @Test
    fun `test PDF extraction with valid file`() = runBlocking {
        // Mock PDF file
        val pdfContent = createMockPdfBytes()
        setupMockUri("test.pdf", "application/pdf", pdfContent)

        val result = extractor.extractContent(mockUri)

        assertTrue("PDF extraction should succeed", result.isSuccess)
        val content = result.getOrNull()
        assertNotNull("Extracted content should not be null", content)
        assertTrue("Should detect PDF format", content?.mimeType?.contains("pdf") == true)
        assertTrue("Should contain PDF identifier", content?.textContent?.contains("PDF Document") == true)
    }

    @Test
    fun `test Word DOCX extraction`() = runBlocking {
        val docxContent = createMockDocxBytes()
        setupMockUri("test.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxContent)

        val result = extractor.extractContent(mockUri)

        assertTrue("DOCX extraction should succeed", result.isSuccess)
        val content = result.getOrNull()
        assertNotNull("Extracted content should not be null", content)
        assertTrue("Should detect Word format", content?.textContent?.contains("Word Document") == true)
    }

    @Test
    fun `test Word DOC extraction`() = runBlocking {
        val docContent = createMockDocBytes()
        setupMockUri("test.doc", "application/msword", docContent)

        val result = extractor.extractContent(mockUri)

        assertTrue("DOC extraction should succeed", result.isSuccess)
        val content = result.getOrNull()
        assertNotNull("Extracted content should not be null", content)
        assertTrue("Should detect Word format", content?.textContent?.contains("Word Document") == true)
    }

    @Test
    fun `test Excel XLSX extraction`() = runBlocking {
        val xlsxContent = createMockXlsxBytes()
        setupMockUri("test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxContent)

        val result = extractor.extractContent(mockUri)

        assertTrue("XLSX extraction should succeed", result.isSuccess)
        val content = result.getOrNull()
        assertNotNull("Extracted content should not be null", content)
        assertTrue("Should detect Excel format", content?.textContent?.contains("Excel Workbook") == true)
    }

    @Test
    fun `test Excel XLS extraction`() = runBlocking {
        val xlsContent = createMockXlsBytes()
        setupMockUri("test.xls", "application/vnd.ms-excel", xlsContent)

        val result = extractor.extractContent(mockUri)

        assertTrue("XLS extraction should succeed", result.isSuccess)
        val content = result.getOrNull()
        assertNotNull("Extracted content should not be null", content)
        assertTrue("Should detect Excel format", content?.textContent?.contains("Excel Workbook") == true)
    }

    @Test
    fun `test PowerPoint PPTX extraction`() = runBlocking {
        val pptxContent = createMockPptxBytes()
        setupMockUri("test.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", pptxContent)

        val result = extractor.extractContent(mockUri)

        assertTrue("PPTX extraction should succeed", result.isSuccess)
        val content = result.getOrNull()
        assertNotNull("Extracted content should not be null", content)
        assertTrue("Should detect PowerPoint format", content?.textContent?.contains("PowerPoint Presentation") == true)
    }

    @Test
    fun `test PowerPoint PPT extraction`() = runBlocking {
        val pptContent = createMockPptBytes()
        setupMockUri("test.ppt", "application/vnd.ms-powerpoint", pptContent)

        val result = extractor.extractContent(mockUri)

        assertTrue("PPT extraction should succeed", result.isSuccess)
        val content = result.getOrNull()
        assertNotNull("Extracted content should not be null", content)
        assertTrue("Should detect PowerPoint format", content?.textContent?.contains("PowerPoint Presentation") == true)
    }

    @Test
    fun `test text file extraction with UTF-8 encoding`() = runBlocking {
        val textContent = "This is a test text file with UTF-8 encoding.\nSecond line with special characters: àáâãäå"
        setupMockUri("test.txt", "text/plain", textContent.toByteArray(Charsets.UTF_8))

        val result = extractor.extractContent(mockUri)

        assertTrue("Text extraction should succeed", result.isSuccess)
        val content = result.getOrNull()
        assertNotNull("Extracted content should not be null", content)
        assertTrue("Should contain original text", content?.textContent?.contains("test text file") == true)
        assertTrue("Should preserve special characters", content?.textContent?.contains("àáâãäå") == true)
    }

    @Test
    fun `test CSV file extraction`() = runBlocking {
        val csvContent = """
            Name,Age,City
            John Doe,25,New York
            Jane Smith,30,Los Angeles
            Bob Johnson,35,Chicago
        """.trimIndent()
        setupMockUri("test.csv", "text/csv", csvContent.toByteArray(Charsets.UTF_8))

        val result = extractor.extractContent(mockUri)

        assertTrue("CSV extraction should succeed", result.isSuccess)
        val content = result.getOrNull()
        assertNotNull("Extracted content should not be null", content)
        assertTrue("Should detect CSV format", content?.textContent?.contains("CSV File") == true)
        assertTrue("Should contain column headers", content?.textContent?.contains("Name") == true)
        assertTrue("Should contain data", content?.textContent?.contains("John Doe") == true)
    }

    @Test
    fun `test unsupported file format`() = runBlocking {
        val binaryContent = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05)
        setupMockUri("test.unknown", "application/octet-stream", binaryContent)

        val result = extractor.extractContent(mockUri)

        assertTrue("Should handle unsupported format gracefully", result.isSuccess)
        val content = result.getOrNull()
        assertNotNull("Should return content even for unsupported format", content)
        assertTrue("Should indicate unsupported format", content?.textContent?.contains("Unsupported file type") == true)
    }

    @Test
    fun `test corrupted file handling`() = runBlocking {
        // Create intentionally corrupted PDF-like file
        val corruptedContent = "This is not a real PDF".toByteArray()
        setupMockUri("corrupted.pdf", "application/pdf", corruptedContent)

        val result = extractor.extractContent(mockUri)

        assertTrue("Should handle corrupted files gracefully", result.isSuccess)
        val content = result.getOrNull()
        assertNotNull("Should return error information", content)
        // Should contain error message or fallback content
        assertTrue("Should indicate extraction issue", 
            content?.textContent?.contains("error") == true || 
            content?.textContent?.contains("failed") == true ||
            content?.textContent?.contains("extraction") == true)
    }

    @Test
    fun `test content caching functionality`() = runBlocking {
        val textContent = "Cached content test"
        setupMockUri("cache_test.txt", "text/plain", textContent.toByteArray())

        // First extraction
        val result1 = extractor.extractContent(mockUri)
        assertTrue("First extraction should succeed", result1.isSuccess)

        // Check if content is cached
        val cachedContent = DocumentContentExtractor.getCachedContent(mockUri.toString())
        assertNotNull("Content should be cached", cachedContent)
        assertEquals("Cached content should match", result1.getOrNull()?.textContent, cachedContent?.textContent)

        // Second extraction should use cache
        val result2 = extractor.extractContent(mockUri)
        assertTrue("Second extraction should succeed", result2.isSuccess)
        assertEquals("Cached and fresh content should be identical", 
            result1.getOrNull()?.textContent, result2.getOrNull()?.textContent)
    }

    @Test
    fun `test token count estimation`() = runBlocking {
        val longText = "word ".repeat(1000) // 1000 words
        setupMockUri("long_text.txt", "text/plain", longText.toByteArray())

        val result = extractor.extractContent(mockUri)

        assertTrue("Extraction should succeed", result.isSuccess)
        val content = result.getOrNull()
        assertNotNull("Content should not be null", content)
        assertTrue("Token count should be estimated", content?.tokenCount ?: 0 > 0)
        assertTrue("Token count should be reasonable for word count", content?.tokenCount ?: 0 > 500)
    }

    @Test
    fun `test large file handling`() = runBlocking {
        // Create content larger than MAX_CONTENT_LENGTH
        val largeText = "A".repeat(600_000) // 600K characters
        setupMockUri("large_file.txt", "text/plain", largeText.toByteArray())

        val result = extractor.extractContent(mockUri)

        assertTrue("Large file extraction should succeed", result.isSuccess)
        val content = result.getOrNull()
        assertNotNull("Content should not be null", content)
        assertTrue("Content should be truncated", content?.textContent?.length ?: 0 <= 500_000)
        assertTrue("Should indicate truncation", content?.textContent?.contains("truncated") == true)
    }

    // Helper methods for creating mock file content

    private fun setupMockUri(fileName: String, mimeType: String, content: ByteArray) {
        whenever(mockUri.toString()).thenReturn("content://test/$fileName")
        
        val inputStream = ByteArrayInputStream(content)
        whenever(mockContext.contentResolver.openInputStream(any())).thenReturn(inputStream)
        
        // Mock FileUtil methods if needed
        // This would require mocking the FileUtil class as well
    }

    private fun createMockPdfBytes(): ByteArray {
        // Create a minimal PDF-like structure for testing
        return """%PDF-1.4
1 0 obj
<<
/Type /Catalog
/Pages 2 0 R
>>
endobj
2 0 obj
<<
/Type /Pages
/Kids [3 0 R]
/Count 1
>>
endobj
3 0 obj
<<
/Type /Page
/Parent 2 0 R
/Contents 4 0 R
>>
endobj
4 0 obj
<<
/Length 44
>>
stream
BT
/F1 12 Tf
72 720 Td
(Test PDF Content) Tj
ET
endstream
endobj
xref
0 5
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
0000000115 00000 n 
0000000174 00000 n 
trailer
<<
/Size 5
/Root 1 0 R
>>
startxref
267
%%EOF""".toByteArray()
    }

    private fun createMockDocxBytes(): ByteArray {
        // Return minimal DOCX-like bytes for testing
        // In a real implementation, this would be a valid DOCX file
        return "Mock DOCX content".toByteArray()
    }

    private fun createMockDocBytes(): ByteArray {
        // Return minimal DOC-like bytes for testing
        return "Mock DOC content".toByteArray()
    }

    private fun createMockXlsxBytes(): ByteArray {
        // Return minimal XLSX-like bytes for testing
        return "Mock XLSX content".toByteArray()
    }

    private fun createMockXlsBytes(): ByteArray {
        // Return minimal XLS-like bytes for testing
        return "Mock XLS content".toByteArray()
    }

    private fun createMockPptxBytes(): ByteArray {
        // Return minimal PPTX-like bytes for testing
        return "Mock PPTX content".toByteArray()
    }

    private fun createMockPptBytes(): ByteArray {
        // Return minimal PPT-like bytes for testing
        return "Mock PPT content".toByteArray()
    }
}