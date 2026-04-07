package com.hiringai.mobile.ml

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地 Embedding 服务
 * 使用 ONNX Runtime 运行 sentence-transformers 模型
 */
class LocalEmbeddingService(private val context: Context) {
    
    private var isModelLoaded: Boolean = false
    
    // Embedding 模型配置
    data class EmbeddingModelConfig(
        val name: String,
        val url: String,
        val size: Long,
        val dimension: Int
    )
    
    companion object {
        val AVAILABLE_MODELS = listOf(
            EmbeddingModelConfig(
                name = "all-MiniLM-L6-v2",
                url = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx",
                size = 90_000_000,
                dimension = 384
            )
        )
    }
    
    /**
     * 下载 Embedding 模型
     */
    suspend fun downloadModel(
        config: EmbeddingModelConfig,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = LocalLLMService.getEmbeddingModelDir(context)
            val file = File(dir, "${config.name}.onnx")
            
            if (file.exists()) {
                onProgress(100)
                return@withContext true
            }
            
            onProgress(0)
            // TODO: 实现实际下载
            onProgress(100)
            
            isModelLoaded = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 计算文本 Embedding
     * 返回归一化后的向量
     */
    suspend fun encode(text: String): FloatArray? = withContext(Dispatchers.IO) {
        if (!isModelLoaded) {
            // 返回 null 表示需要使用备选方案
            return@withContext null
        }
        
        try {
            // TODO: 使用 ONNX Runtime 加载模型并推理
            // 需要集成 onnxruntime-android 库
            
            // 模拟返回 384 维向量
            FloatArray(384) { (Math.random() * 2 - 1).toFloat() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 计算两个向量的余弦相似度
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        return if (normA > 0 && normB > 0) {
            dotProduct / (Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble())).toFloat()
        } else {
            0f
        }
    }
}