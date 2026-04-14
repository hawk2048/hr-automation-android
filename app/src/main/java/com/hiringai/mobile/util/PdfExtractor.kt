package com.hiringai.mobile.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PDF文本提取工具
 * 使用 PDFBox 从 PDF 简历中提取文本内容
 */
object PdfExtractor {

    private const val TAG = "PdfExtractor"

    /**
     * 从 URI 提取 PDF 文本
     */
    suspend fun extractText(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            // Initialize PDFBox
            PDFBoxResourceLoader.init(context)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val document = PDDocument.load(inputStream)
                document.use { doc ->
                    val textStripper = PDFTextStripper()
                    textStripper.sortByPosition = true
                    val text = textStripper.getText(doc)
                    Log.i(TAG, "Extracted ${text.length} characters from PDF")
                    return@withContext text
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract PDF text", e)
            null
        }
    }

    /**
     * 清理提取的文本，去除多余空白
     */
    fun cleanText(text: String): String {
        return text
            .replace(Regex("\\s+"), " ")  // 多个空白字符替换为单个空格
            .replace(Regex("\n\\s*\n"), "\n")  // 多个连续换行替换为单个
            .trim()
    }

    /**
     * 截取文本前 N 个字符（用于摘要）
     */
    fun truncateText(text: String, maxLength: Int = 200): String {
        return if (text.length > maxLength) {
            text.substring(0, maxLength) + "..."
        } else {
            text
        }
    }
}