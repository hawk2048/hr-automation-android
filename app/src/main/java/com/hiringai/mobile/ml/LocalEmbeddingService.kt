package com.hiringai.mobile.ml

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * 本地 Embedding 服务
 * 使用 ONNX Runtime 运行 sentence-transformers 模型
 *
 * 工作流程：
 * 1. 下载 ONNX 模型 + tokenizer 词汇表到设备
 * 2. 加载模型到 OrtSession
 * 3. 对输入文本做简单 tokenization（WordPiece/BPE 分词）
 * 4. 运行推理，取 [CLS] token 的 hidden state 作为句子向量
 * 5. L2 归一化后返回
 */
class LocalEmbeddingService(private val context: Context) {

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var isModelLoaded: Boolean = false
    private var vocab: Map<String, Int> = emptyMap()

    data class EmbeddingModelConfig(
        val name: String,
        val modelUrl: String,       // ONNX model URL
        val vocabUrl: String,       // tokenizer vocab URL
        val modelSize: Long,
        val dimension: Int,
        val maxSeqLength: Int = 256
    )

    companion object {
        private const val TAG = "LocalEmbedding"

        val AVAILABLE_MODELS = listOf(
            EmbeddingModelConfig(
                name = "all-MiniLM-L6-v2",
                modelUrl = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx",
                vocabUrl = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/vocab.txt",
                modelSize = 90_000_000,
                dimension = 384,
                maxSeqLength = 256
            )
        )

        // Special tokens for BERT-style models
        private const val CLS_TOKEN = "[CLS]"
        private const val SEP_TOKEN = "[SEP]"
        private const val UNK_TOKEN = "[UNK]"
        private const val PAD_TOKEN_ID = 0
        private const val CLS_TOKEN_ID = 101
        private const val SEP_TOKEN_ID = 102
    }

    val loaded: Boolean get() = isModelLoaded

    /**
     * 检查模型是否已下载
     */
    fun isModelDownloaded(modelName: String): Boolean {
        val modelFile = File(LocalLLMService.getEmbeddingModelDir(context), "$modelName.onnx")
        val vocabFile = File(LocalLLMService.getEmbeddingModelDir(context), "$modelName.vocab.txt")
        return modelFile.exists() && vocabFile.exists()
    }

    /**
     * 下载 Embedding 模型和词汇表
     */
    suspend fun downloadModel(
        config: EmbeddingModelConfig,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = LocalLLMService.getEmbeddingModelDir(context)
            val modelFile = File(dir, "${config.name}.onnx")
            val vocabFile = File(dir, "${config.name}.vocab.txt")

            // Download vocab first (small file)
            if (!vocabFile.exists()) {
                onProgress(0)
                downloadFile(config.vocabUrl, vocabFile)
                onProgress(20)
            }

            // Download model (large file)
            if (!modelFile.exists()) {
                downloadFileWithProgress(config.modelUrl, modelFile) { pct ->
                    // Map 20-100% range for model download
                    onProgress(20 + (pct * 80 / 100))
                }
            }

            onProgress(100)
            Log.i(TAG, "Embedding model downloaded: ${config.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for ${config.name}", e)
            false
        }
    }

    /**
     * 加载模型到内存
     * 
     * 注意：ONNX Runtime 的 native 库加载和 OrtEnvironment 创建可能
     * 在某些设备上触发 native 层 SIGILL（非法指令），这种崩溃
     * 无法被 Java try-catch 捕获。因此我们：
     * 1. 不使用 NNAPI（小米设备高通驱动已知崩溃）
     * 2. 使用 CPU-only 执行提供程序
     * 3. 升级到 onnxruntime 1.24.4+ 修复 cpuinfo 检测 Bug
     */
    suspend fun loadModel(config: EmbeddingModelConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = LocalLLMService.getEmbeddingModelDir(context)
            val modelFile = File(dir, "${config.name}.onnx")
            val vocabFile = File(dir, "${config.name}.vocab.txt")

            if (!modelFile.exists() || !vocabFile.exists()) {
                Log.e(TAG, "Model or vocab file not found")
                return@withContext false
            }

            // Load vocab
            vocab = loadVocab(vocabFile)
            Log.i(TAG, "Vocab loaded: ${vocab.size} tokens")

            // Create ONNX Runtime session — CPU ONLY
            // NNAPI is explicitly disabled because Qualcomm NNAPI drivers
            // (qti-default, qti-dsp, qti-gpu, qti-hta) crash on Xiaomi devices
            // and the native SIGILL cannot be caught by Java try-catch.
            env = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            // DO NOT add NNAPI — it causes native SIGILL/SIGSEGV on Xiaomi devices

            session = env?.createSession(modelFile.absolutePath, sessionOptions)
            isModelLoaded = true
            Log.i(TAG, "Embedding model loaded: ${config.name}")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "ONNX Runtime native library not found or incompatible", e)
            isModelLoaded = false
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load embedding model", e)
            isModelLoaded = false
            false
        }
    }

    /**
     * 释放模型资源
     */
    fun unloadModel() {
        try {
            session?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing session", e)
        }
        // Note: OrtEnvironment is a singleton, don't close it
        session = null
        isModelLoaded = false
        vocab = emptyMap()
    }

    /**
     * 计算文本 Embedding
     * 返回 L2 归一化后的向量
     */
    suspend fun encode(text: String): FloatArray? = withContext(Dispatchers.IO) {
        if (!isModelLoaded || session == null || env == null) {
            Log.w(TAG, "Model not loaded, cannot encode")
            return@withContext null
        }

        try {
            val maxLen = AVAILABLE_MODELS.firstOrNull()?.maxSeqLength ?: 256

            // Tokenize
            val tokenIds = tokenize(text, maxLen)
            val inputIds = tokenIds.map { it.toLong() }.toLongArray()
            val attentionMask = tokenIds.map { if (it != PAD_TOKEN_ID) 1L else 0L }.toLongArray()
            val tokenTypeIds = LongArray(inputIds.size) { 0L }

            val seqLen = inputIds.size

            // Create direct buffers for ONNX Runtime (requires native-order direct buffers)
            val inputIdsBuf = ByteBuffer.allocateDirect(seqLen * 8).order(ByteOrder.nativeOrder()).asLongBuffer()
            inputIdsBuf.put(inputIds)
            inputIdsBuf.rewind()

            val attentionMaskBuf = ByteBuffer.allocateDirect(seqLen * 8).order(ByteOrder.nativeOrder()).asLongBuffer()
            attentionMaskBuf.put(attentionMask)
            attentionMaskBuf.rewind()

            val tokenTypeIdsBuf = ByteBuffer.allocateDirect(seqLen * 8).order(ByteOrder.nativeOrder()).asLongBuffer()
            tokenTypeIdsBuf.put(tokenTypeIds)
            tokenTypeIdsBuf.rewind()

            // Create ONNX tensors
            val inputIdsTensor = OnnxTensor.createTensor(
                env!!,
                inputIdsBuf,
                longArrayOf(1, seqLen.toLong())
            )
            val attentionMaskTensor = OnnxTensor.createTensor(
                env!!,
                attentionMaskBuf,
                longArrayOf(1, seqLen.toLong())
            )
            val tokenTypeIdsTensor = OnnxTensor.createTensor(
                env!!,
                tokenTypeIdsBuf,
                longArrayOf(1, seqLen.toLong())
            )

            // Run inference
            val inputNames = session?.inputNames ?: return@withContext null
            val inputs = mutableMapOf<String, OnnxTensor>()
            for (name in inputNames) {
                when {
                    name.contains("input_ids", ignoreCase = true) -> inputs[name] = inputIdsTensor
                    name.contains("attention_mask", ignoreCase = true) -> inputs[name] = attentionMaskTensor
                    name.contains("token_type_ids", ignoreCase = true) -> inputs[name] = tokenTypeIdsTensor
                }
            }

            val output = session?.run(inputs) ?: return@withContext null

            // Extract [CLS] token embedding (first token, index 0)
            val lastHiddenState = output.get(0).value as Array<Array<FloatArray>>
            val clsEmbedding = lastHiddenState[0][0]  // [1, seqLen, dim] → [0][0] = CLS

            // L2 normalize
            val normalized = l2Normalize(clsEmbedding)

            // Clean up tensors
            inputIdsTensor.close()
            attentionMaskTensor.close()
            tokenTypeIdsTensor.close()
            output.close()

            normalized
        } catch (e: Exception) {
            Log.e(TAG, "Encoding failed", e)
            null
        }
    }

    /**
     * 简单的 BERT WordPiece tokenizer
     * 对中文按字分词，对英文按词+子词分词
     */
    private fun tokenize(text: String, maxLen: Int): List<Int> {
        val tokens = mutableListOf<Int>()

        // [CLS] at start
        tokens.add(CLS_TOKEN_ID)

        // Basic tokenization: split by whitespace, then per-character for CJK
        val words = text.trim().split(Regex("\\s+"))
        for (word in words) {
            if (tokens.size >= maxLen - 1) break

            // Check if whole word is in vocab
            val wholeWordId = vocab[word.lowercase()]
            if (wholeWordId != null) {
                tokens.add(wholeWordId)
            } else {
                // Try to find subword tokens (simple greedy matching)
                var remaining = word.lowercase()
                while (remaining.isNotEmpty() && tokens.size < maxLen - 1) {
                    var matched = false
                    for (len in remaining.length downTo 1) {
                        val sub = if (remaining == word.lowercase()) remaining.substring(0, len)
                                  else "##${remaining.substring(0, len)}"
                        val subId = vocab[sub]
                        if (subId != null) {
                            tokens.add(subId)
                            remaining = remaining.substring(len)
                            matched = true
                            break
                        }
                    }
                    if (!matched) {
                        // Fallback: character-level tokenization for CJK or unknown chars
                        for (ch in remaining) {
                            if (tokens.size >= maxLen - 1) break
                            val charId = vocab[ch.toString()] ?: vocab["##$ch"] ?: vocab[UNK_TOKEN] ?: 100
                            tokens.add(charId)
                        }
                        break
                    }
                }
            }
        }

        // [SEP] at end
        tokens.add(SEP_TOKEN_ID)

        // Pad to fixed length
        while (tokens.size < maxLen) {
            tokens.add(PAD_TOKEN_ID)
        }

        return tokens.take(maxLen)
    }

    /**
     * 加载 vocab.txt 文件
     * 格式: 每行一个 token，行号 = token ID
     */
    private fun loadVocab(file: File): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        file.bufferedReader().useLines { lines ->
            lines.forEachIndexed { index, line ->
                val token = line.trim()
                if (token.isNotEmpty()) {
                    map[token] = index
                }
            }
        }
        return map
    }

    /**
     * L2 归一化向量
     */
    private fun l2Normalize(vec: FloatArray): FloatArray {
        var normSq = 0f
        for (v in vec) normSq += v * v
        val norm = Math.sqrt(normSq.toDouble()).toFloat()
        return if (norm > 0f) {
            FloatArray(vec.size) { i -> vec[i] / norm }
        } else {
            vec
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

    private suspend fun downloadFile(urlStr: String, target: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(urlStr)
                url.openStream().use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: $urlStr", e)
                target.delete()
                false
            }
        }

    private suspend fun downloadFileWithProgress(
        urlStr: String,
        target: File,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val temp = File(target.parent, "${target.name}.tmp")
            val url = URL(urlStr)
            val conn = url.openConnection()
            conn.connect()
            val contentLen = conn.contentLengthLong

            conn.getInputStream().use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead = 0L
                    var lastPct = -1

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (contentLen > 0) {
                            val pct = (bytesRead * 100 / contentLen).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
            temp.renameTo(target)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download with progress failed: $urlStr", e)
            false
        }
    }
}
