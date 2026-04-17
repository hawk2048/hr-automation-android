package com.hiringai.mobile.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * 本地图像模型服务 (OCR, Image Classification, VLM)
 * 使用 ONNX Runtime 运行图像模型
 *
 * 支持的模型类型:
 * 1. OCR - 文字识别 (CRNN/TrOCR)
 * 2. Image Classification - 图像分类 (MobileNet, EfficientNet)
 * 3. VLM - 视觉语言模型 (MiniGPT-v2, LLaVA variants)
 */
class LocalImageModelService(private val context: Context) {

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var currentModelName: String = ""
    private var currentModelType: ModelType = ModelType.CLASSIFICATION

    enum class ModelType {
        OCR,
        CLASSIFICATION,
        VLM
    }

    data class ImageModelConfig(
        val name: String,
        val modelUrl: String,
        val modelSize: Long,
        val type: ModelType,
        val description: String = "",
        val requiredRAM: Int = 1, // GB
        val inputSize: Pair<Int, Int> = 224 to 224, // width x height
        val inputChannels: Int = 3,
        val labelsUrl: String? = null // For classification models
    )

    companion object {
        private const val TAG = "LocalImageModel"

        private const val HF_MIRROR = "https://hf-mirror.com"

        val AVAILABLE_MODELS = listOf(
            // ========== Image Classification Models ==========
            ImageModelConfig(
                name = "mobilenet_v2_224",
                modelUrl = "$HF_MIRROR/onnx-community/mobilenet_v2_100/resolve/main/model.onnx",
                modelSize = 14_000_000,
                type = ModelType.CLASSIFICATION,
                description = "轻量级图像分类模型，适合移动设备",
                requiredRAM = 1,
                inputSize = 224 to 224,
                labelsUrl = "$HF_MIRROR/onnx-community/mobilenet_v2_100/resolve/main/labels.txt"
            ),
            ImageModelConfig(
                name = "efficientnet_b0",
                modelUrl = "$HF_MIRROR/onnx-community/timm_efficientnet_b0_ns_1k_32px/resolve/main/model.onnx",
                modelSize = 20_000_000,
                type = ModelType.CLASSIFICATION,
                description = "高效图像分类模型，精度与速度平衡",
                requiredRAM = 2,
                inputSize = 224 to 224,
                labelsUrl = "$HF_MIRROR/onnx-community/timm_efficientnet_b0_ns_1k_32px/resolve/main/labels.txt"
            ),

            // ========== Vision Transformer Models ==========
            ImageModelConfig(
                name = "vit_base_patch16_224",
                modelUrl = "$HF_MIRROR/onnx-community/vit-base-patch16-224/resolve/main/model.onnx",
                modelSize = 330_000_000,
                type = ModelType.CLASSIFICATION,
                description = "Vision Transformer Base - 基于注意力机制的图像分类",
                requiredRAM = 3,
                inputSize = 224 to 224,
                labelsUrl = "$HF_MIRROR/onnx-community/vit-base-patch16-224/resolve/main/labels.txt"
            ),
            ImageModelConfig(
                name = "vit_small_patch16_224",
                modelUrl = "$HF_MIRROR/onnx-community/vit-small-patch16-224/resolve/main/model.onnx",
                modelSize = 85_000_000,
                type = ModelType.CLASSIFICATION,
                description = "Vision Transformer Small - 轻量级ViT",
                requiredRAM = 2,
                inputSize = 224 to 224
            ),

            // ========== OCR Models ==========
            ImageModelConfig(
                name = "crnn_mobilenet_v3",
                modelUrl = "$HF_MIRROR/TheMuppets/CRNN_ResNet18/resolve/main/model.onnx",
                modelSize = 9_000_000,
                type = ModelType.OCR,
                description = "轻量级端到端OCR模型，支持英文数字识别",
                requiredRAM = 1,
                inputSize = 320 to 32
            ),
            ImageModelConfig(
                name = "trilingual_ocr",
                modelUrl = "$HF_MIRROR/TheMuppets/trilingual_ocr/resolve/main/model.onnx",
                modelSize = 75_000_000,
                type = ModelType.OCR,
                description = "支持中英日三国文字识别的高精度OCR模型",
                requiredRAM = 2,
                inputSize = 384 to 64
            ),

            // ========== VLM Models (Vision-Language) ==========
            ImageModelConfig(
                name = "clip_vit_b32",
                modelUrl = "$HF_MIRROR/OpenAI/clip-vit-base-patch32/resolve/main/visual.onnx",
                modelSize = 150_000_000,
                type = ModelType.VLM,
                description = "CLIP视觉编码器，支持图像-文本对比学习",
                requiredRAM = 2,
                inputSize = 224 to 224,
                labelsUrl = "$HF_MIRROR/OpenAI/clip-vit-base-patch32/resolve/main/labels.txt"
            ),
            ImageModelConfig(
                name = "mobilevlm_v2_1.7b",
                modelUrl = "$HF_MIRROR/TheMuppets/mobilevlm_v2_1.7b/resolve/main/model.onnx",
                modelSize = 1_800_000_000,
                type = ModelType.VLM,
                description = "移动端VLM模型，支持图像描述和问答",
                requiredRAM = 4,
                inputSize = 336 to 336
            ),

            // ========== Stable Diffusion (Image Generation) ==========
            // Note: SD models require significant RAM and compute
            ImageModelConfig(
                name = "sd_turbo_onnx",
                modelUrl = "$HF_MIRROR/stabilityai/sd-turbo/resolve/main/onnx/model.onnx",
                modelSize = 2_100_000_000,
                type = ModelType.VLM,  // Using VLM type for generative models
                description = "Stable Diffusion Turbo - 快速图像生成 (需高配设备)",
                requiredRAM = 6,
                inputSize = 512 to 512
            ),
            ImageModelConfig(
                name = "sdxl_turbo_onnx",
                modelUrl = "$HF_MIRROR/stabilityai/sdxl-turbo/resolve/main/onnx/model.onnx",
                modelSize = 6_500_000_000,
                type = ModelType.VLM,
                description = "SDXL Turbo - 高质量图像生成 (需8GB+ RAM)",
                requiredRAM = 8,
                inputSize = 512 to 512
            )
        )

        @Volatile
        private var instance: LocalImageModelService? = null

        fun getInstance(context: Context): LocalImageModelService {
            return instance ?: synchronized(this) {
                instance ?: LocalImageModelService(context.applicationContext).also { instance = it }
            }
        }

        fun getModelsDir(context: Context): File {
            val dir = File(context.filesDir, "image_models")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        private const val PREFS_NAME = "ml_image_model_state"
        private const val KEY_LOADED_MODEL = "loaded_image_model"

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

    val isModelLoaded: Boolean get() = session != null

    fun getLoadedModelName(): String = currentModelName

    fun getLoadedModelType(): ModelType = currentModelType

    /**
     * 检查模型是否已下载
     */
    fun isModelDownloaded(modelName: String): Boolean {
        val modelFile = File(getModelsDir(context), "$modelName.onnx")
        return modelFile.exists() && modelFile.length() > 0
    }

    /**
     * 下载图像模型
     */
    suspend fun downloadModel(
        config: ImageModelConfig,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetFile = File(getModelsDir(context), "${config.name}.onnx")

            if (targetFile.exists() && targetFile.length() == config.modelSize) {
                onProgress(100)
                return@withContext true
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }

            onProgress(0)

            val url = URL(config.modelUrl)
            val connection = url.openConnection()
            connection.connect()
            connection.readTimeout = 60000
            connection.connectTimeout = 30000
            val contentLength = connection.contentLengthLong

            val tempFile = File(targetFile.parent, "${config.name}.onnx.tmp")
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

    /**
     * 加载模型到内存
     */
    suspend fun loadModel(config: ImageModelConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelFile = File(getModelsDir(context), "${config.name}.onnx")
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file not found: ${modelFile.absolutePath}")
                return@withContext false
            }

            // Safe-load ONNX Runtime native library
            if (!com.hiringai.mobile.SafeNativeLoader.loadLibrary("onnxruntime")) {
                Log.e(TAG, "ONNX Runtime native library not available")
                return@withContext false
            }

            unloadModel()

            env = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)

            session = env?.createSession(modelFile.absolutePath, sessionOptions)
            currentModelName = config.name
            currentModelType = config.type

            // 保存加载状态
            saveLoadedModelName(context, config.name)

            Log.i(TAG, "Image model loaded: ${config.name} (type: ${config.type})")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "ONNX Runtime native library not found", e)
            com.hiringai.mobile.SafeNativeLoader.markCrashed("onnxruntime")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load image model: ${config.name}", e)
            false
        }
    }

    /**
     * 卸载模型
     */
    fun unloadModel() {
        try {
            session?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing session", e)
        }
        session = null
        currentModelName = ""
        currentModelType = ModelType.CLASSIFICATION

        clearLoadedModelName(context)
    }

    /**
     * 图像分类推理
     */
    suspend fun classifyImage(bitmap: Bitmap): Pair<String, Float>? = withContext(Dispatchers.IO) {
        if (!isModelLoaded || session == null || env == null) {
            Log.w(TAG, "Model not loaded, cannot classify")
            return@withContext null
        }

        try {
            // Preprocess: resize to model input size
            val (width, height) = getCurrentModelInputSize()
            val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)

            // Convert to RGB float tensor [1, 3, H, W]
            val inputData = preprocessImage(resized, 3, height, width)

            // Create input tensor
            val inputTensor = OnnxTensor.createTensor(
                env!!,
                inputData,
                longArrayOf(1, 3, height.toLong(), width.toLong())
            )

            // Run inference
            val inputs = mutableMapOf<String, OnnxTensor>()
            val inputNames = session?.inputNames
            if (!inputNames.isNullOrEmpty()) {
                inputs[inputNames.first()] = inputTensor
            }

            val output = session?.run(inputs)
            val result = extractClassificationResult(output)

            inputTensor.close()
            output?.close()
            resized.recycle()

            result
        } catch (e: Exception) {
            Log.e(TAG, "Classification failed", e)
            null
        }
    }

    /**
     * OCR 文字识别
     */
    suspend fun recognizeText(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        if (!isModelLoaded || session == null || env == null) {
            Log.w(TAG, "Model not loaded, cannot recognize text")
            return@withContext null
        }

        try {
            val (width, height) = getCurrentModelInputSize()
            val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)

            val inputData = preprocessImage(resized, 3, height, width)

            val inputTensor = OnnxTensor.createTensor(
                env!!,
                inputData,
                longArrayOf(1, 3, height.toLong(), width.toLong())
            )

            val inputs = mutableMapOf<String, OnnxTensor>()
            val inputNames = session?.inputNames
            if (!inputNames.isNullOrEmpty()) {
                inputs[inputNames.first()] = inputTensor
            }

            val output = session?.run(inputs)
            val result = extractOCRResult(output)

            inputTensor.close()
            output?.close()
            resized.recycle()

            result
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed", e)
            null
        }
    }

    /**
     * VLM 图像编码 (返回视觉特征)
     */
    suspend fun encodeImage(bitmap: Bitmap): FloatArray? = withContext(Dispatchers.IO) {
        if (!isModelLoaded || session == null || env == null) {
            Log.w(TAG, "Model not loaded, cannot encode image")
            return@withContext null
        }

        try {
            val (width, height) = getCurrentModelInputSize()
            val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)

            val inputData = preprocessImage(resized, 3, height, width)

            val inputTensor = OnnxTensor.createTensor(
                env!!,
                inputData,
                longArrayOf(1, 3, height.toLong(), width.toLong())
            )

            val inputs = mutableMapOf<String, OnnxTensor>()
            val inputNames = session?.inputNames
            if (!inputNames.isNullOrEmpty()) {
                inputs[inputNames.first()] = inputTensor
            }

            val output = session?.run(inputs)
            val features = extractVisionFeatures(output)

            inputTensor.close()
            output?.close()
            resized.recycle()

            features
        } catch (e: Exception) {
            Log.e(TAG, "VLM encoding failed", e)
            null
        }
    }

    private fun getCurrentModelInputSize(): Pair<Int, Int> {
        return when (currentModelType) {
            ModelType.CLASSIFICATION -> 224 to 224
            ModelType.OCR -> 320 to 32
            ModelType.VLM -> 336 to 336
        }
    }

    /**
     * 图像预处理: 归一化到 [-1, 1] 范围并转换为 NCHW 格式
     */
    private fun preprocessImage(bitmap: Bitmap, channels: Int, height: Int, width: Int): FloatArray {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // NCHW format: [batch, channels, height, width]
        val inputData = FloatArray(channels * height * width)

        for (c in 0 until channels) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = pixels[y * width + x]
                    val value = when (c) {
                        0 -> ((pixel shr 16) and 0xFF) / 255.0f * 2 - 1 // R
                        1 -> ((pixel shr 8) and 0xFF) / 255.0f * 2 - 1  // G
                        else -> (pixel and 0xFF) / 255.0f * 2 - 1       // B
                    }
                    inputData[c * height * width + y * width + x] = value
                }
            }
        }

        return inputData
    }

    private fun extractClassificationResult(output: OrtSession.Result?): Pair<String, Float>? {
        if (output == null) return null
        try {
            val outputArray = output.get(0).value as Array<FloatArray>
            val probabilities = outputArray[0]

            // Find max probability
            var maxIdx = 0
            var maxProb = probabilities[0]
            for (i in probabilities.indices) {
                if (probabilities[i] > maxProb) {
                    maxProb = probabilities[i]
                    maxIdx = i
                }
            }

            // Return placeholder label since we don't have label mapping
            return Pair("class_$maxIdx", maxProb)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract classification result", e)
            return null
        }
    }

    private fun extractOCRResult(output: OrtSession.Result?): String? {
        if (output == null) return null
        try {
            // OCR output format varies by model
            val outputValue = output.get(0).value
            return outputValue?.toString() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract OCR result", e)
            return null
        }
    }

    private fun extractVisionFeatures(output: OrtSession.Result?): FloatArray? {
        if (output == null) return null
        try {
            val outputArray = output.get(0).value as Array<Array<FloatArray>>
            // Return flattened features
            val batch0 = outputArray[0]
            val featureDim = batch0.size
            return batch0.flatten().toFloatArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract vision features", e)
            return null
        }
    }
}