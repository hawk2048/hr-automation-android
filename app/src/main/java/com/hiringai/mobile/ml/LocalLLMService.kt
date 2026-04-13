package com.hiringai.mobile.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.codeshipping.llamakotlin.LlamaModel
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * 本地 LLM 推理服务 (单例模式)
 *
 * 支持两种推理方式：
 * 1. 设备端推理 — 通过 llama-kotlin-android (llama.cpp JNI) 加载 GGUF 模型
 * 2. 远程 Ollama — 通过 HTTP 调用远程 Ollama 服务器
 */
class LocalLLMService private constructor(private val context: Context) {

    private var model: LlamaModel? = null
    private var currentModelName: String = ""

    data class ModelConfig(
        val name: String,
        val url: String,
        val size: Long,
        val requiredRAM: Int,
        val contextSize: Int = 2048,
        val template: String = "chatml"
    )

    companion object {
        private const val TAG = "LocalLLMService"

        @Volatile
        private var instance: LocalLLMService? = null

        private const val HF_MIRROR = "https://hf-mirror.com"

        val AVAILABLE_MODELS = listOf(
            ModelConfig(
                name = "Qwen2.5-0.5B-Instruct-Q4_0",
                url = "$HF_MIRROR/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_0.gguf",
                size = 394_774_816,
                requiredRAM = 2,
                contextSize = 2048,
                template = "chatml"
            ),
            ModelConfig(
                name = "TinyLlama-1.1B-Chat-Q4_K_M",
                url = "$HF_MIRROR/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
                size = 667_825_984,
                requiredRAM = 3,
                contextSize = 2048,
                template = "llama"
            )
        )

        fun getInstance(context: Context): LocalLLMService {
            return instance ?: synchronized(this) {
                instance ?: LocalLLMService(context.applicationContext).also { instance = it }
            }
        }

        fun getModelsDir(context: Context): File {
            val dir = File(context.filesDir, "models")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        fun getEmbeddingModelDir(context: Context): File {
            val dir = File(context.filesDir, "embedding")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        // 保存加载状态到SharedPreferences
        private const val PREFS_NAME = "ml_model_state"
        private const val KEY_LOADED_MODEL = "loaded_llm_model"

        fun saveLoadedModelName(context: Context, modelName: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LOADED_MODEL, modelName)
                .apply()
        }

        fun getLoadedModelNameFromPrefs(context: Context): String? {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LOADED_MODEL, null)
        }

        fun clearLoadedModelName(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LOADED_MODEL)
                .apply()
        }
    }

    val isModelLoaded: Boolean get() = model != null

    fun getLoadedModelName(): String = currentModelName

    fun isModelDownloaded(modelName: String): Boolean {
        val file = File(getModelsDir(context), "$modelName.gguf")
        return file.exists() && file.length() > 0
    }

    suspend fun downloadModel(
        config: ModelConfig,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetFile = File(getModelsDir(context), "${config.name}.gguf")

            if (targetFile.exists() && targetFile.length() == config.size) {
                onProgress(100)
                return@withContext true
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }

            onProgress(0)

            val url = URL(config.url)
            val connection = url.openConnection()
            connection.connect()
            connection.readTimeout = 60000
            connection.connectTimeout = 30000
            val contentLength = connection.contentLengthLong

            val tempFile = File(targetFile.parent, "${config.name}.gguf.tmp")
            connection.getInputStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Long = 0
                    var lastProgress = -1

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesRead += read

                        if (contentLength > 0) {
                            val progress = (bytesRead * 100 / contentLength).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }
            }

            if (tempFile.renameTo(targetFile)) {
                onProgress(100)
                Log.i(TAG, "Model downloaded: ${config.name} (${targetFile.length()} bytes)")
                true
            } else {
                tempFile.delete()
                Log.e(TAG, "Failed to rename temp file for ${config.name}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for ${config.name}", e)
            false
        }
    }

    suspend fun loadModel(
        config: ModelConfig,
        threads: Int = 4,
        gpuLayers: Int = 0
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!com.hiringai.mobile.SafeNativeLoader.loadLibrary("llama-android")) {
                Log.e(TAG, "llama.cpp native library not available")
                return@withContext false
            }

            unloadModel()

            val modelFile = File(getModelsDir(context), "${config.name}.gguf")
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file not found: ${modelFile.absolutePath}")
                return@withContext false
            }

            model = LlamaModel.load(modelFile.absolutePath) {
                this.contextSize = config.contextSize
                this.threads = threads
                this.temperature = 0.7f
                this.topP = 0.9f
                this.maxTokens = 512
                this.gpuLayers = gpuLayers
            }
            currentModelName = config.name

            // 保存加载状态
            saveLoadedModelName(context, config.name)

            Log.i(TAG, "Model loaded: ${config.name}")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "llama.cpp native library not found or incompatible", e)
            com.hiringai.mobile.SafeNativeLoader.markCrashed("llama-android")
            model = null
            currentModelName = ""
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${config.name}", e)
            model = null
            currentModelName = ""
            false
        }
    }

    fun unloadModel() {
        try {
            model?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing model", e)
        }
        model = null
        currentModelName = ""

        // 清除加载状态
        clearLoadedModelName(context)
    }

    suspend fun generate(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f
    ): String? = withContext(Dispatchers.IO) {
        val m = model
        if (m == null) {
            Log.w(TAG, "No model loaded, cannot generate locally")
            return@withContext null
        }

        try {
            val result = m.generate(prompt)
            Log.d(TAG, "Generated ${result.length} chars")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            null
        }
    }

    fun generateStream(prompt: String): Flow<String>? {
        val m = model
        if (m == null) {
            Log.w(TAG, "No model loaded, cannot stream generate")
            return null
        }
        return try {
            m.generateStream(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Stream generation failed", e)
            null
        }
    }

    suspend fun generateViaOllama(
        ollamaUrl: String,
        model: String,
        prompt: String,
        maxTokens: Int = 512
    ): String? = withContext(Dispatchers.IO) {
        try {
            val jsonBody = """{"model":"$model","prompt":${escapeJson(prompt)},"stream":false,"options":{"num_predict":$maxTokens,"temperature":0.7}}"""

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toRequestBody(mediaType)

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url("$ollamaUrl/api/generate")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful) {
                Log.e(TAG, "Ollama request failed: ${response.code} - $body")
                return@withContext null
            }

            body?.let { parseOllamaResponse(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Ollama call failed", e)
            null
        }
    }

    private fun parseOllamaResponse(json: String): String? {
        val key = "\"response\""
        val keyIndex = json.indexOf(key)
        if (keyIndex < 0) return null
        val colonIndex = json.indexOf(':', keyIndex + key.length)
        if (colonIndex < 0) return null
        val valueStart = json.indexOf('"', colonIndex) + 1
        val valueEnd = json.indexOf('"', valueStart)
        if (valueStart <= 0 || valueEnd <= valueStart) return null
        return json.substring(valueStart, valueEnd)
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun escapeJson(str: String): String {
        val sb = StringBuilder("\"")
        for (ch in str) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        sb.append("\"")
        return sb.toString()
    }
}