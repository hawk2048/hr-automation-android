# ML 模型在 Android 上的应用

本文档详细描述了 HRAutomation Android 应用中本地 LLM 和 Embedding 模型的加载、推理、交互控制等技术实现。

> **⚠️ 架构变更说明 (2026-04)**
> 
> ML 代码已从主仓库 `app/` 模块剥离到独立 Git 子模块 `hiringai-ml-kit/`。
> - 所有 ML 服务代码位于 `hiringai-ml-kit/ml/src/main/java/com/hiringai/mobile/ml/`
> - 主仓库通过 `implementation project(':hiringai-ml-kit:ml')` 引用
> - 业务实体通过 `MlBridge` (JobInfo/CandidateInfo) 与 ML 层解耦
> - SafeNativeLoader 存在两个版本：主仓库版 (`com.hiringai.mobile`) 和子模块版 (`com.hiringai.mobile.ml`)
> - 模型选择 UI 已从 `ModelSelectionDialog` 迁移到 app 层的 ViewModel + RecyclerView + BottomSheet
> - 新增：模型目录系统 (ModelCatalogService)、日志系统 (MlLogger)、基准测试 v2 (子阶段进度)

## 1. 技术选型

### 1.1 LLM 推理：llama.cpp

**选择理由：**
- GGUF 格式广泛支持，HuggingFace 模型生态丰富
- 内存效率极高，支持 4-bit 量化
- 有 Kotlin 绑定库 `llama-kotlin-android`
- 纯 CPU 推理，无需 GPU 依赖

**依赖配置：**
```gradle
implementation 'org.codeshipping:llama-kotlin-android:0.1.3'
```

### 1.2 Embedding 推理：ONNX Runtime

**选择理由：**
- 微软官方支持，稳定可靠
- Android 平台支持完善
- 支持 NNAPI 加速（但我们禁用了，见下文）
- 模型转换工具链成熟

**依赖配置：**
```gradle
implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.24.3'
```

## 2. Native 库安全加载机制

### 2.1 问题背景

Android 设备上的 native 库加载存在以下风险：

1. **ABI 不兼容**：设备架构不支持（如 armeabi-v7a 设备）
2. **驱动崩溃**：NNAPI 驱动 bug 导致 SIGILL/SIGSEGV
3. **内存不足**：模型加载时 OOM
4. **启动崩溃**：在 Application.onCreate() 中加载导致用户看不到任何 UI

**关键问题**：Native crash 无法被 Java try-catch 捕获，应用会直接闪退。

### 2.2 SafeNativeLoader 设计

```
┌─────────────────────────────────────────────────────────────┐
│                     SafeNativeLoader                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  1. 设备兼容性检测 (detectDeviceCompatibility)          ││
│  │     - 检查 ABI 是否为 arm64-v8a 或 x86_64               ││
│  │     - 检测模拟器环境                                     ││
│  │     - 读取上次的 crash marker                            ││
│  └─────────────────────────────────────────────────────────┘│
│                           │                                  │
│                           ▼                                  │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  2. 延迟加载 (loadLibrary)                               ││
│  │     - 按需加载，不在 Application.onCreate() 中加载       ││
│  │     - 单次尝试，避免重复触发 crash                        ││
│  │     - 结果缓存，后续调用直接返回                          ││
│  └─────────────────────────────────────────────────────────┘│
│                           │                                  │
│                           ▼                                  │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  3. 崩溃标记 (markCrashed)                               ││
│  │     - 如果加载后立即闪退，下次启动时读取标记              ││
│  │     - 自动禁用问题库，允许应用正常运行                    ││
│  │     - 用户可在设置中手动重置                              ││
│  └─────────────────────────────────────────────────────────┘│
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 2.3 代码实现

```kotlin
object SafeNativeLoader {
    private val loadAttempts = mutableMapOf<String, Boolean>()
    private val loading = AtomicBoolean(false)
    
    var isDeviceCompatible: Boolean = true
        private set
    var incompatibilityReason: String = ""
        private set
    
    /**
     * 在 Application.onCreate() 中调用
     * 不加载任何 native 库，只做预检测
     */
    fun detectDeviceCompatibility() {
        val abi = Build.SUPPORTED_ABIS?.firstOrNull() ?: ""
        
        // 检查 ABI 支持
        if (abi != "arm64-v8a" && abi != "x86_64") {
            isDeviceCompatible = false
            incompatibilityReason = "Unsupported ABI: $abi"
            return
        }
        
        // 检查上次 crash marker
        val crashMarker = getCrashMarkerFile()
        if (crashMarker.exists()) {
            val crashedLib = crashMarker.readText().trim()
            loadAttempts[crashedLib] = false
        }
    }
    
    /**
     * 安全加载 native 库
     * @return true 加载成功，false 加载失败或已标记为不可用
     */
    fun loadLibrary(name: String): Boolean {
        // 已尝试过，返回缓存结果
        loadAttempts[name]?.let { return it }
        
        // 设备不兼容
        if (!isDeviceCompatible) {
            loadAttempts[name] = false
            return false
        }
        
        // 防止并发加载
        if (!loading.compareAndSet(false, true)) {
            return loadAttempts[name] ?: false
        }
        
        return try {
            when (name) {
                "onnxruntime" -> {
                    System.loadLibrary("onnxruntime")
                    // 验证 OrtEnvironment 可创建（这是 NNAPI 崩溃的触发点）
                    OrtEnvironment.getEnvironment()
                }
                "llama-android" -> {
                    System.loadLibrary("llama-android")
                }
                else -> System.loadLibrary(name)
            }
            loadAttempts[name] = true
            true
        } catch (e: UnsatisfiedLinkError) {
            loadAttempts[name] = false
            false
        } catch (e: Exception) {
            loadAttempts[name] = false
            false
        } finally {
            loading.set(false)
        }
    }
    
    /**
     * 标记某个库崩溃（供 CrashHandler 调用）
     */
    fun markCrashed(libName: String) {
        loadAttempts[libName] = false
        getCrashMarkerFile().writeText(libName)
    }
}
```

### 2.4 使用流程

```kotlin
// 1. Application.onCreate() - 预检测
class HiringAIApplication : Application() {
    override fun onCreate() {
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
        super.onCreate()
        SafeNativeLoader.detectDeviceCompatibility()
        // 注意：这里不加载任何 native 库！
    }
}

// 2. 首次使用 ML 功能时 - 延迟加载
suspend fun loadModel(config: ModelConfig): Boolean {
    // 先尝试加载 native 库
    if (!SafeNativeLoader.loadLibrary("llama-android")) {
        return false  // 加载失败，ML 功能不可用
    }
    
    // 然后加载模型
    model = LlamaModel.load(modelFile.absolutePath) { ... }
    return true
}

// 3. UI 层检查状态
if (SafeNativeLoader.hasCrashMarker()) {
    // 显示警告，ML 功能已禁用
}
```

## 3. LLM 服务 (LocalLLMService)

### 3.1 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                    LocalLLMService                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  配置层                                                      │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  AVAILABLE_MODELS: List<ModelConfig>                    ││
│  │  - Qwen2.5-0.5B-Instruct-Q4_0 (400MB, 2GB RAM)         ││
│  │  - TinyLlama-1.1B-Chat-Q4_K_M (670MB, 3GB RAM)         ││
│  └─────────────────────────────────────────────────────────┘│
│                                                              │
│  下载层                                                      │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  downloadModel(config, onProgress)                      ││
│  │  - 支持断点续传                                          ││
│  │  - 进度回调                                              ││
│  │  - 使用 hf-mirror.com 国内镜像                          ││
│  └─────────────────────────────────────────────────────────┘│
│                                                              │
│  加载层                                                      │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  loadModel(config, threads, gpuLayers)                  ││
│  │  - 先加载 native 库 (SafeNativeLoader)                  ││
│  │  - 卸载旧模型                                            ││
│  │  - 加载新模型到内存                                      ││
│  └─────────────────────────────────────────────────────────┘│
│                                                              │
│  推理层                                                      │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  generate(prompt, maxTokens, temperature): String?      ││
│  │  generateStream(prompt): Flow<String>                   ││
│  │  generateViaOllama(url, model, prompt): String?         ││
│  └─────────────────────────────────────────────────────────┘│
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 模型配置

```kotlin
data class ModelConfig(
    val name: String,           // 模型名称（用于文件命名）
    val url: String,            // 下载 URL
    val size: Long,             // 文件大小（字节）
    val requiredRAM: Int,       // 需要的 RAM (GB)
    val contextSize: Int = 2048, // 上下文长度
    val template: String = "chatml"  // prompt 模板格式
)

companion object {
    private const val HF_MIRROR = "https://hf-mirror.com"
    
    val AVAILABLE_MODELS = listOf(
        ModelConfig(
            name = "Qwen2.5-0.5B-Instruct-Q4_0",
            url = "$HF_MIRROR/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_0.gguf",
            size = 394_774_816,
            requiredRAM = 2,
            contextSize = 2048,
            template = "chatml"
        )
    )
}
```

### 3.3 模型下载

```kotlin
suspend fun downloadModel(
    config: ModelConfig,
    onProgress: (Int) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    try {
        val targetFile = File(getModelsDir(context), "${config.name}.gguf")
        
        // 已下载且大小正确
        if (targetFile.exists() && targetFile.length() == config.size) {
            onProgress(100)
            return@withContext true
        }
        
        // 删除部分下载
        if (targetFile.exists()) targetFile.delete()
        
        val url = URL(config.url)
        val connection = url.openConnection()
        connection.connect()
        val contentLength = connection.contentLengthLong
        
        // 下载到临时文件
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
        
        // 重命名为最终文件
        tempFile.renameTo(targetFile)
        onProgress(100)
        true
    } catch (e: Exception) {
        Log.e(TAG, "Download failed", e)
        false
    }
}
```

### 3.4 模型加载

```kotlin
suspend fun loadModel(
    config: ModelConfig,
    threads: Int = 4,      // CPU 线程数
    gpuLayers: Int = 0     // GPU 层数，0 = 纯 CPU
): Boolean = withContext(Dispatchers.IO) {
    try {
        // 1. 安全加载 native 库
        if (!SafeNativeLoader.loadLibrary("llama-android")) {
            Log.e(TAG, "llama.cpp native library not available")
            return@withContext false
        }
        
        // 2. 卸载旧模型
        unloadModel()
        
        // 3. 检查模型文件
        val modelFile = File(getModelsDir(context), "${config.name}.gguf")
        if (!modelFile.exists()) {
            Log.e(TAG, "Model file not found")
            return@withContext false
        }
        
        // 4. 加载模型
        model = LlamaModel.load(modelFile.absolutePath) {
            this.contextSize = config.contextSize
            this.threads = threads
            this.temperature = 0.7f
            this.topP = 0.9f
            this.maxTokens = 512
            this.gpuLayers = gpuLayers
        }
        currentModelName = config.name
        true
    } catch (e: UnsatisfiedLinkError) {
        SafeNativeLoader.markCrashed("llama-android")
        model = null
        false
    } catch (e: Exception) {
        model = null
        false
    }
}
```

### 3.5 推理调用

**同步生成：**
```kotlin
suspend fun generate(
    prompt: String,
    maxTokens: Int = 512,
    temperature: Float = 0.7f
): String? = withContext(Dispatchers.IO) {
    val m = model ?: return@withContext null
    
    try {
        val result = m.generate(prompt)
        result
    } catch (e: Exception) {
        Log.e(TAG, "Generation failed", e)
        null
    }
}
```

**流式生成：**
```kotlin
fun generateStream(prompt: String): Flow<String>? {
    val m = model ?: return null
    return try {
        m.generateStream(prompt)
    } catch (e: Exception) {
        null
    }
}
```

**远程 Ollama：**
```kotlin
suspend fun generateViaOllama(
    ollamaUrl: String,
    model: String,
    prompt: String,
    maxTokens: Int = 512
): String? = withContext(Dispatchers.IO) {
    val jsonBody = """{
        "model":"$model",
        "prompt":${escapeJson(prompt)},
        "stream":false,
        "options":{"num_predict":$maxTokens,"temperature":0.7}
    }"""
    
    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    
    val request = Request.Builder()
        .url("$ollamaUrl/api/generate")
        .post(jsonBody.toRequestBody("application/json".toMediaType()))
        .build()
    
    val response = client.newCall(request).execute()
    parseOllamaResponse(response.body?.string() ?: "")
}
```

## 4. Embedding 服务 (LocalEmbeddingService)

### 4.1 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                 LocalEmbeddingService                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  配置层                                                      │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  AVAILABLE_MODELS: List<EmbeddingModelConfig>           ││
│  │  - all-MiniLM-L6-v2 (90MB, 384维)                       ││
│  └─────────────────────────────────────────────────────────┘│
│                                                              │
│  Tokenization                                                │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  tokenize(text, maxLen): List<Int>                      ││
│  │  - WordPiece/BPE 分词                                   ││
│  │  - [CLS] + tokens + [SEP] + [PAD]                       ││
│  └─────────────────────────────────────────────────────────┘│
│                                                              │
│  推理层 (ONNX Runtime)                                       │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  encode(text): FloatArray?                              ││
│  │  - input_ids, attention_mask, token_type_ids            ││
│  │  - ONNX inference                                       ││
│  │  - 取 [CLS] token 作为句子向量                           ││
│  │  - L2 归一化                                            ││
│  └─────────────────────────────────────────────────────────┘│
│                                                              │
│  相似度计算                                                  │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  cosineSimilarity(a, b): Float                          ││
│  │  - 点积 / (||a|| * ||b||)                               ││
│  │  - 返回 -1.0 到 1.0，实际 0.0 到 1.0                     ││
│  └─────────────────────────────────────────────────────────┘│
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 Tokenization 实现

```kotlin
private fun tokenize(text: String, maxLen: Int): List<Int> {
    val tokens = mutableListOf<Int>()
    
    // [CLS] at start
    tokens.add(CLS_TOKEN_ID)  // 101
    
    // 分词
    val words = text.trim().split(Regex("\\s+"))
    for (word in words) {
        if (tokens.size >= maxLen - 1) break
        
        // 尝试整词匹配
        val wholeWordId = vocab[word.lowercase()]
        if (wholeWordId != null) {
            tokens.add(wholeWordId)
        } else {
            // 子词匹配 (WordPiece)
            var remaining = word.lowercase()
            while (remaining.isNotEmpty() && tokens.size < maxLen - 1) {
                var matched = false
                for (len in remaining.length downTo 1) {
                    val sub = if (remaining == word.lowercase()) 
                        remaining.substring(0, len)
                    else 
                        "##${remaining.substring(0, len)}"
                    
                    val subId = vocab[sub]
                    if (subId != null) {
                        tokens.add(subId)
                        remaining = remaining.substring(len)
                        matched = true
                        break
                    }
                }
                
                // 未匹配，用 [UNK] 或字符级
                if (!matched) {
                    for (ch in remaining) {
                        if (tokens.size >= maxLen - 1) break
                        val charId = vocab[ch.toString()] 
                            ?: vocab["##$ch"] 
                            ?: vocab[UNK_TOKEN] 
                            ?: 100
                        tokens.add(charId)
                    }
                    break
                }
            }
        }
    }
    
    // [SEP] at end
    tokens.add(SEP_TOKEN_ID)  // 102
    
    // Pad to fixed length
    while (tokens.size < maxLen) {
        tokens.add(PAD_TOKEN_ID)  // 0
    }
    
    return tokens.take(maxLen)
}
```

### 4.3 ONNX 推理

```kotlin
suspend fun encode(text: String): FloatArray? = withContext(Dispatchers.IO) {
    if (!isModelLoaded || session == null || env == null) return@withContext null
    
    try {
        val maxLen = AVAILABLE_MODELS.firstOrNull()?.maxSeqLength ?: 256
        
        // 1. Tokenize
        val tokenIds = tokenize(text, maxLen)
        val inputIds = tokenIds.map { it.toLong() }.toLongArray()
        val attentionMask = tokenIds.map { if (it != PAD_TOKEN_ID) 1L else 0L }.toLongArray()
        val tokenTypeIds = LongArray(inputIds.size) { 0L }
        
        val seqLen = inputIds.size
        
        // 2. 创建 Direct Buffer (ONNX Runtime 要求)
        val inputIdsBuf = ByteBuffer.allocateDirect(seqLen * 8)
            .order(ByteOrder.nativeOrder())
            .asLongBuffer()
        inputIdsBuf.put(inputIds)
        inputIdsBuf.rewind()
        
        // ... 同样处理 attentionMask, tokenTypeIds
        
        // 3. 创建 ONNX Tensors
        val inputIdsTensor = OnnxTensor.createTensor(env!!, inputIdsBuf, longArrayOf(1, seqLen.toLong()))
        val attentionMaskTensor = OnnxTensor.createTensor(env!!, attentionMaskBuf, longArrayOf(1, seqLen.toLong()))
        val tokenTypeIdsTensor = OnnxTensor.createTensor(env!!, tokenTypeIdsBuf, longArrayOf(1, seqLen.toLong()))
        
        // 4. 运行推理
        val inputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor,
            "token_type_ids" to tokenTypeIdsTensor
        )
        val output = session?.run(inputs) ?: return@withContext null
        
        // 5. 提取 [CLS] token embedding
        val lastHiddenState = output.get(0).value as Array<Array<FloatArray>>
        val clsEmbedding = lastHiddenState[0][0]  // [1, seqLen, dim] → [CLS]
        
        // 6. L2 归一化
        val normalized = l2Normalize(clsEmbedding)
        
        // 7. 清理资源
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
```

### 4.4 相似度计算

```kotlin
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
        dotProduct / (sqrt(normA) * sqrt(normB))
    } else {
        0f
    }
}
```

## 5. 交互控制

### 5.1 设置页面 UI 流程

```
┌─────────────────────────────────────────────────────────────┐
│                    SettingsFragment                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  推理模式选择                                                │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  [下拉] 设备端推理 (llama.cpp) / 远程 Ollama            ││
│  └─────────────────────────────────────────────────────────┘│
│                           │                                  │
│           ┌───────────────┴───────────────┐                 │
│           ▼                               ▼                  │
│  ┌─────────────────────┐      ┌─────────────────────────┐   │
│  │  本地模型配置        │      │  Ollama 配置            │   │
│  │  - LLM 模型选择      │      │  - 服务器 URL           │   │
│  │  - Embedding 选择    │      │  - 连接测试             │   │
│  │  - 下载按钮          │      └─────────────────────────┘   │
│  │  - 加载按钮          │                                     │
│  │  - 状态显示          │                                     │
│  └─────────────────────┘                                     │
│                                                              │
│  状态反馈                                                    │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  LLM: 已加载 / 已下载 / 未下载 / 禁用(crash)            ││
│  │  Embedding: 已加载 / 已下载 / 未下载 / 禁用(crash)      ││
│  └─────────────────────────────────────────────────────────┘│
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 状态管理

```kotlin
private fun updateModelStatus() {
    val llmDownloaded = LocalLLMService.AVAILABLE_MODELS.any { 
        llmService.isModelDownloaded(it.name) 
    }
    val embDownloaded = LocalEmbeddingService.AVAILABLE_MODELS.any { 
        embeddingService.isModelDownloaded(it.name) 
    }
    val llmLoaded = llmService.isModelLoaded
    val embLoaded = embeddingService.loaded
    
    // 检查 native 库状态
    val onnxAvailable = SafeNativeLoader.isAvailable("onnxruntime")
    val llamaAvailable = SafeNativeLoader.isAvailable("llama-android")
    val hasCrashMarker = SafeNativeLoader.hasCrashMarker()
    
    binding.tvLlmStatus.text = when {
        !llamaAvailable && hasCrashMarker -> 
            "LLM disabled (native crash detected) — use Ollama"
        !SafeNativeLoader.isDeviceCompatible -> 
            "LLM disabled (device incompatible)"
        llmLoaded -> 
            getString(R.string.settings_llm_loaded, llmService.getLoadedModelName())
        llmDownloaded -> 
            getString(R.string.settings_llm_downloaded)
        else -> 
            getString(R.string.settings_model_not_downloaded)
    }
    
    // 加载按钮可见性
    val canLoadML = SafeNativeLoader.isDeviceCompatible && 
                    !hasCrashMarker && 
                    (llmDownloaded || embDownloaded)
    binding.btnLoadModel.visibility = if (canLoadML) View.VISIBLE else View.GONE
}
```

### 5.3 下载交互

```kotlin
private fun downloadLLMModel(config: LocalLLMService.ModelConfig) {
    binding.progressDownloadLlm.visibility = View.VISIBLE
    binding.btnDownloadLlmModel.isEnabled = false
    
    lifecycleScope.launch {
        val success = llmService.downloadModel(config) { progress ->
            requireActivity().runOnUiThread {
                binding.progressDownloadLlm.progress = progress
                if (progress == 100) {
                    binding.progressDownloadLlm.visibility = View.GONE
                    binding.btnDownloadLlmModel.isEnabled = true
                    updateModelStatus()
                }
            }
        }
        
        if (!success) {
            requireActivity().runOnUiThread {
                binding.progressDownloadLlm.visibility = View.GONE
                binding.btnDownloadLlmModel.isEnabled = true
                Toast.makeText(context, "下载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
```

### 5.4 加载交互

```kotlin
private fun loadModels() {
    binding.btnLoadModel.isEnabled = false
    
    lifecycleScope.launch {
        var llmLoaded = false
        var embLoaded = false
        
        // 加载 LLM
        val llmConfig = LocalLLMService.AVAILABLE_MODELS.firstOrNull { 
            llmService.isModelDownloaded(it.name) 
        }
        if (llmConfig != null) {
            llmLoaded = llmService.loadModel(llmConfig)
        }
        
        // 加载 Embedding
        val embConfig = LocalEmbeddingService.AVAILABLE_MODELS.firstOrNull { 
            embeddingService.isModelDownloaded(it.name) 
        }
        if (embConfig != null) {
            embLoaded = embeddingService.loadModel(embConfig)
        }
        
        requireActivity().runOnUiThread {
            binding.btnLoadModel.isEnabled = true
            updateModelStatus()
            
            val msg = when {
                llmLoaded && embLoaded -> "模型加载成功"
                llmLoaded -> "LLM 加载成功，Embedding 失败"
                embLoaded -> "Embedding 加载成功，LLM 失败"
                else -> "模型加载失败"
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
```

## 6. 内存管理

### 6.1 模型卸载

```kotlin
// LLM
fun unloadModel() {
    try {
        model?.close()
    } catch (e: Exception) {
        Log.w(TAG, "Error closing model", e)
    }
    model = null
    currentModelName = ""
}

// Embedding
fun unloadModel() {
    try {
        session?.close()
    } catch (e: Exception) {
        Log.w(TAG, "Error closing session", e)
    }
    session = null
    isModelLoaded = false
    vocab = emptyMap()
}
```

### 6.2 内存压力处理

```kotlin
// 在 Application 中监听内存压力
override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    when (level) {
        TRIM_MEMORY_RUNNING_LOW, TRIM_MEMORY_UI_HIDDEN -> {
            // 内存紧张时卸载模型
            LocalLLMService.getInstance()?.unloadModel()
            LocalEmbeddingService.getInstance()?.unloadModel()
        }
    }
}
```

## 7. 错误处理

### 7.1 错误类型

| 错误类型 | 原因 | 处理方式 |
|----------|------|----------|
| 网络错误 | 下载模型失败 | Toast 提示，重试按钮 |
| ABI 不兼容 | 设备不支持 arm64-v8a | 禁用 ML，提示用户 |
| Native Crash | ONNX/llama.cpp 崩溃 | 记录 marker，禁用 ML |
| OOM | 内存不足 | 卸载模型，提示用户 |
| 模型损坏 | 下载不完整 | 删除文件，重新下载 |

### 7.2 Crash Handler

```kotlin
private class CrashHandler(private val app: Application) : Thread.UncaughtExceptionHandler {
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            pw.println("=== Crash at ${Date()} ===")
            pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            pw.println("ABI: ${Build.SUPPORTED_ABIS?.joinToString()}")
            pw.println("ML Status: onnx=${SafeNativeLoader.isAvailable("onnxruntime")}, " +
                      "llama=${SafeNativeLoader.isAvailable("llama-android")}")
            pw.println()
            throwable.printStackTrace(pw)
            
            File(app.filesDir, "crash.log").writeText(sw.toString())
        } catch (e: Exception) {
            Log.e("Crash", "Failed to write log", e)
        }
        
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
```

## 8. 性能优化建议

### 8.1 模型选择

| 场景 | 推荐 LLM | 推荐 Embedding |
|------|----------|----------------|
| 低端设备 (<4GB RAM) | Qwen2.5-0.5B | all-MiniLM-L6-v2 |
| 中端设备 (4-6GB) | TinyLlama-1.1B | all-MiniLM-L6-v2 |
| 高端设备 (>6GB) | TinyLlama-1.1B | 可选更大模型 |

### 8.2 线程配置

```kotlin
// 根据 CPU 核心数调整
val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
llmService.loadModel(config, threads = threads)
```

### 8.3 上下文长度

```kotlin
// 根据内存调整
val contextSize = when {
    Runtime.getRuntime().maxMemory() > 4_000_000_000 -> 4096
    Runtime.getRuntime().maxMemory() > 2_000_000_000 -> 2048
    else -> 1024
}
```
