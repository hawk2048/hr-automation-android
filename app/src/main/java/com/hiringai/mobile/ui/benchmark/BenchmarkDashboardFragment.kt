package com.hiringai.mobile.ui.benchmark

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.hiringai.mobile.R
import com.hiringai.mobile.databinding.FragmentBenchmarkDashboardBinding
import com.hiringai.mobile.ml.LocalLLMService
import com.hiringai.mobile.ml.LocalImageModelService
import com.hiringai.mobile.ml.SpeechModelService
import com.hiringai.mobile.ml.benchmark.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI模型批量运行性能测试仪表板
 *
 * 功能：
 * 1. 语音模型 (Whisper/Paraformer/Cam++) 测试入口和报告
 * 2. 图像模型 (Vision Transformer/SD/图像理解) 测试入口
 * 3. Gemma/Qwen 等 LLM 性能测试入口
 * 4. 一键批量测试所有模型
 * 5. 测试报告汇总和导出
 */
class BenchmarkDashboardFragment : Fragment() {

    private var _binding: FragmentBenchmarkDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var speechBenchmark: SpeechBenchmark
    private lateinit var llmBenchmarkRunner: LLMBenchmarkRunner
    private lateinit var imageModelService: LocalImageModelService
    private lateinit var speechModelService: SpeechModelService

    private var batchJob: Job? = null
    private var batchResults = mutableListOf<BenchmarkSummary>()

    data class BenchmarkSummary(
        val category: String,
        val modelName: String,
        val latencyMs: Long,
        val throughput: Float,
        val memoryMB: Float,
        val success: Boolean,
        val errorMessage: String? = null
    )

    companion object {
        fun newInstance() = BenchmarkDashboardFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBenchmarkDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        speechBenchmark = SpeechBenchmark(requireContext())
        llmBenchmarkRunner = LLMBenchmarkRunner(requireContext())
        imageModelService = LocalImageModelService.getInstance(requireContext())
        speechModelService = SpeechModelService.getInstance(requireContext())

        setupButtons()
        updateModelStatuses()
    }

    private fun setupButtons() {
        // 批量测试按钮
        binding.btnRunAllBenchmarks.setOnClickListener {
            runBatchBenchmarks()
        }

        // 单独测试入口
        binding.btnSpeechBenchmark.setOnClickListener {
            navigateToBenchmark(BenchmarkType.SPEECH)
        }

        binding.btnImageBenchmark.setOnClickListener {
            navigateToBenchmark(BenchmarkType.IMAGE)
        }

        binding.btnLlmBenchmark.setOnClickListener {
            navigateToBenchmark(BenchmarkType.LLM)
        }

        // 导出和清除
        binding.btnExportReport.setOnClickListener {
            exportReport()
        }

        binding.btnClearReport.setOnClickListener {
            clearReport()
        }

        // 取消批量测试
        binding.btnCancelBatch.setOnClickListener {
            cancelBatchTest()
        }
    }

    private fun updateModelStatuses() {
        // 更新 LLM 状态
        val llmService = LocalLLMService.getInstance(requireContext())
        val llmDownloaded = LocalLLMService.AVAILABLE_MODELS.any { llmService.isModelDownloaded(it.name) }
        val llmLoaded = llmService.isModelLoaded

        binding.tvLlmStatus.text = when {
            llmLoaded -> "状态: ✓ 已加载 ${llmService.getLoadedModelName()}"
            llmDownloaded -> "状态: ✓ 已下载模型，待加载"
            else -> "状态: 未下载模型"
        }

        // 更新图像模型状态
        val imageDownloaded = LocalImageModelService.AVAILABLE_MODELS.any { imageModelService.isModelDownloaded(it.name) }
        val imageLoaded = imageModelService.isModelLoaded

        binding.tvImageStatus.text = when {
            imageLoaded -> "状态: ✓ 已加载 ${imageModelService.getLoadedModelName()}"
            imageDownloaded -> "状态: ✓ 已下载模型，待加载"
            else -> "状态: 未下载模型"
        }

        // 更新语音模型状态
        val speechDownloaded = SpeechModelService.AVAILABLE_MODELS.any { speechModelService.isModelDownloaded(it.name) }
        val speechLoaded = speechModelService.isModelLoaded

        binding.tvSpeechStatus.text = when {
            speechLoaded -> "状态: ✓ 已加载 ${speechModelService.getLoadedModelName()}"
            speechDownloaded -> "状态: ✓ 已下载模型，待加载"
            else -> "状态: Android TTS 可用，Whisper/Paraformer 待下载"
        }
    }

    private fun navigateToBenchmark(type: BenchmarkType) {
        val fragment: Fragment = when (type) {
            BenchmarkType.SPEECH -> BenchmarkSpeechFragment.newInstance()
            BenchmarkType.IMAGE -> BenchmarkImageFragment()
            BenchmarkType.LLM -> LLMBenchmarkFragment.newInstance()
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun runBatchBenchmarks() {
        batchResults.clear()

        binding.cardBatchProgress.visibility = View.VISIBLE
        binding.btnRunAllBenchmarks.isEnabled = false
        binding.progressBatch.progress = 0

        batchJob = lifecycleScope.launch {
            try {
                val totalSteps = 3
                var currentStep = 0

                // 1. 语音模型测试
                updateBatchProgress("正在测试语音模型...", currentStep, totalSteps)
                runSpeechBenchmark()
                currentStep++

                // 2. 图像模型测试
                updateBatchProgress("正在测试图像模型...", currentStep, totalSteps)
                runImageBenchmark()
                currentStep++

                // 3. LLM 测试
                updateBatchProgress("正在测试LLM模型...", currentStep, totalSteps)
                runLLMBenchmark()
                currentStep++

                // 完成
                updateBatchProgress("测试完成", totalSteps, totalSteps)

                withContext(Dispatchers.Main) {
                    binding.cardBatchProgress.visibility = View.GONE
                    binding.btnRunAllBenchmarks.isEnabled = true
                    binding.btnExportReport.isEnabled = true
                    binding.btnClearReport.visibility = View.VISIBLE

                    displaySummaryReport()

                    Toast.makeText(
                        requireContext(),
                        "批量测试完成！共测试 ${batchResults.size} 个模型",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.cardBatchProgress.visibility = View.GONE
                    binding.btnRunAllBenchmarks.isEnabled = true
                    Toast.makeText(requireContext(), "测试出错: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun runSpeechBenchmark() {
        try {
            // 初始化 TTS
            val initSuccess = speechBenchmark.initTTS()
            if (!initSuccess) {
                batchResults.add(BenchmarkSummary(
                    category = "语音",
                    modelName = "Android TTS",
                    latencyMs = 0,
                    throughput = 0f,
                    memoryMB = 0f,
                    success = false,
                    errorMessage = "TTS初始化失败"
                ))
                return
            }

            // 运行 TTS 测试
            val ttsResults = speechBenchmark.benchmarkTTS(warmupIterations = 1, testIterations = 3)

            for (result in ttsResults) {
                batchResults.add(BenchmarkSummary(
                    category = "语音-TTS",
                    modelName = result.modelName,
                    latencyMs = result.latencyMs,
                    throughput = result.throughput,
                    memoryMB = result.memoryMB,
                    success = result.success,
                    errorMessage = result.errorMessage
                ))
            }

            // 运行 VAD 测试
            val vadResults = speechBenchmark.benchmarkVAD(iterations = 50)
            for (result in vadResults) {
                batchResults.add(BenchmarkSummary(
                    category = "语音-VAD",
                    modelName = result.modelName,
                    latencyMs = result.latencyMs,
                    throughput = result.throughput,
                    memoryMB = result.memoryMB,
                    success = result.success,
                    errorMessage = result.errorMessage
                ))
            }
        } catch (e: Exception) {
            batchResults.add(BenchmarkSummary(
                category = "语音",
                modelName = "语音模型",
                latencyMs = 0,
                throughput = 0f,
                memoryMB = 0f,
                success = false,
                errorMessage = e.message
            ))
        }
    }

    private suspend fun runImageBenchmark() {
        val models = LocalImageModelService.AVAILABLE_MODELS
        val downloadedModels = models.filter { imageModelService.isModelDownloaded(it.name) }

        if (downloadedModels.isEmpty()) {
            batchResults.add(BenchmarkSummary(
                category = "图像",
                modelName = "无已下载模型",
                latencyMs = 0,
                throughput = 0f,
                memoryMB = 0f,
                success = false,
                errorMessage = "请先下载图像模型"
            ))
            return
        }

        // 只测试第一个已下载的图像模型（避免内存问题）
        val config = downloadedModels.first()

        try {
            val startTime = System.currentTimeMillis()
            val loadSuccess = imageModelService.loadModel(config)
            val loadTime = System.currentTimeMillis() - startTime

            if (!loadSuccess) {
                batchResults.add(BenchmarkSummary(
                    category = "图像",
                    modelName = config.name,
                    latencyMs = loadTime,
                    throughput = 0f,
                    memoryMB = 0f,
                    success = false,
                    errorMessage = "模型加载失败"
                ))
                return
            }

            // 创建测试图像
            val testBitmap = createTestBitmap(config.inputSize.first, config.inputSize.second)

            // 预热
            repeat(2) {
                when (config.type) {
                    LocalImageModelService.ModelType.CLASSIFICATION -> imageModelService.classifyImage(testBitmap)
                    LocalImageModelService.ModelType.OCR -> imageModelService.recognizeText(testBitmap)
                    LocalImageModelService.ModelType.VLM -> imageModelService.encodeImage(testBitmap)
                }
            }

            // 正式测试
            val inferenceTimes = mutableListOf<Long>()
            repeat(5) {
                val start = System.currentTimeMillis()
                when (config.type) {
                    LocalImageModelService.ModelType.CLASSIFICATION -> imageModelService.classifyImage(testBitmap)
                    LocalImageModelService.ModelType.OCR -> imageModelService.recognizeText(testBitmap)
                    LocalImageModelService.ModelType.VLM -> imageModelService.encodeImage(testBitmap)
                }
                inferenceTimes.add(System.currentTimeMillis() - start)
            }

            testBitmap.recycle()
            imageModelService.unloadModel()

            val avgLatency = inferenceTimes.average().toLong()
            val throughput = if (avgLatency > 0) 1000f / avgLatency else 0f
            val memoryMB = getMemoryUsageMB().toFloat()

            batchResults.add(BenchmarkSummary(
                category = "图像-${config.type.name}",
                modelName = config.name,
                latencyMs = avgLatency,
                throughput = throughput,
                memoryMB = memoryMB,
                success = true
            ))
        } catch (e: Exception) {
            batchResults.add(BenchmarkSummary(
                category = "图像",
                modelName = config.name,
                latencyMs = 0,
                throughput = 0f,
                memoryMB = 0f,
                success = false,
                errorMessage = e.message
            ))
        }
    }

    private suspend fun runLLMBenchmark() {
        val llmService = LocalLLMService.getInstance(requireContext())
        val models = LocalLLMService.AVAILABLE_MODELS
        val downloadedModels = models.filter { llmService.isModelDownloaded(it.name) }

        if (downloadedModels.isEmpty()) {
            batchResults.add(BenchmarkSummary(
                category = "LLM",
                modelName = "无已下载模型",
                latencyMs = 0,
                throughput = 0f,
                memoryMB = 0f,
                success = false,
                errorMessage = "请先下载LLM模型"
            ))
            return
        }

        // 测试已加载的模型（如果有的话）
        if (llmService.isModelLoaded) {
            val result = llmBenchmarkRunner.quickBenchmark(maxTokens = 64)

            batchResults.add(BenchmarkSummary(
                category = "LLM",
                modelName = result.modelName,
                latencyMs = result.avgTokenLatencyMs,
                throughput = result.throughputTokensPerSec.toFloat(),
                memoryMB = result.peakMemoryMB.toFloat(),
                success = result.success,
                errorMessage = result.errorMessage
            ))
        } else {
            // 测试第一个已下载的模型
            val config = downloadedModels.first()
            val result = llmBenchmarkRunner.benchmarkModel(config, maxTokens = 64)

            batchResults.add(BenchmarkSummary(
                category = "LLM",
                modelName = result.modelName,
                latencyMs = result.avgTokenLatencyMs,
                throughput = result.throughputTokensPerSec.toFloat(),
                memoryMB = result.peakMemoryMB.toFloat(),
                success = result.success,
                errorMessage = result.errorMessage
            ))
        }
    }

    private fun updateBatchProgress(message: String, current: Int, total: Int) {
        requireActivity().runOnUiThread {
            binding.tvBatchProgressStatus.text = message
            binding.progressBatch.progress = (current * 100 / total)
        }
    }

    private fun displaySummaryReport() {
        val report = buildString {
            appendLine("=" .repeat(50))
            appendLine("📊 AI 模型性能测试报告")
            appendLine("=" .repeat(50))
            appendLine("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine()

            val grouped = batchResults.groupBy { it.category }

            for ((category, results) in grouped) {
                appendLine("【$category】")
                appendLine("-".repeat(40))

                for (result in results) {
                    val status = if (result.success) "✓" else "✗"
                    appendLine("  $status ${result.modelName}")
                    if (result.success) {
                        appendLine("      延迟: ${result.latencyMs}ms")
                        appendLine("      吞吐量: %.2f/s".format(result.throughput))
                        appendLine("      内存: %.1fMB".format(result.memoryMB))
                    } else {
                        appendLine("      错误: ${result.errorMessage}")
                    }
                }
                appendLine()
            }

            // 汇总统计
            appendLine("=" .repeat(50))
            appendLine("📈 汇总统计")
            appendLine("-".repeat(40))
            val successCount = batchResults.count { it.success }
            val failCount = batchResults.count { !it.success }
            appendLine("  成功: $successCount")
            appendLine("  失败: $failCount")
            appendLine("  总计: ${batchResults.size}")

            if (successCount > 0) {
                val avgLatency = batchResults.filter { it.success }.map { it.latencyMs }.average()
                val maxThroughput = batchResults.filter { it.success }.maxOf { it.throughput }
                appendLine("  平均延迟: %.1fms".format(avgLatency))
                appendLine("  最高吞吐量: %.2f/s".format(maxThroughput))
            }
        }

        binding.tvSummaryReport.text = report
    }

    private fun exportReport() {
        val report = binding.tvSummaryReport.text.toString()

        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AI Model Benchmark Report", report)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(requireContext(), "报告已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    private fun clearReport() {
        batchResults.clear()
        binding.tvSummaryReport.text = "运行测试后，报告将显示在这里..."
        binding.btnExportReport.isEnabled = false
        binding.btnClearReport.visibility = View.GONE
    }

    private fun cancelBatchTest() {
        batchJob?.cancel()
        batchJob = null

        binding.cardBatchProgress.visibility = View.GONE
        binding.btnRunAllBenchmarks.isEnabled = true

        Toast.makeText(requireContext(), "已取消批量测试", Toast.LENGTH_SHORT).show()
    }

    private fun createTestBitmap(width: Int, height: Int): android.graphics.Bitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = (x * 255 / width)
                val g = (y * 255 / height)
                val b = 128
                pixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun getMemoryUsageMB(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        batchJob?.cancel()
        speechBenchmark.shutdownTTS()
        _binding = null
    }
}

enum class BenchmarkType {
    SPEECH,
    IMAGE,
    LLM
}
