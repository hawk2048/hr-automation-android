package com.hiringai.mobile.ui.benchmark

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.hiringai.mobile.R
import com.hiringai.mobile.databinding.FragmentBenchmarkDashboardBinding
import com.hiringai.mobile.ml.LocalLLMService
import com.hiringai.mobile.ml.LocalImageModelService
import com.hiringai.mobile.ml.SpeechModelService
import com.hiringai.mobile.ui.benchmark.BenchmarkChartView.ChartType.BAR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        updateQuickStats()
    }

    private fun setupButtons() {
        binding.btnRunAllBenchmarks.setOnClickListener {
            runBatchBenchmarks()
        }

        binding.btnSpeechBenchmark.setOnClickListener {
            navigateToBenchmark(BenchmarkType.SPEECH)
        }

        binding.btnImageBenchmark.setOnClickListener {
            navigateToBenchmark(BenchmarkType.IMAGE)
        }

        binding.btnLlmBenchmark.setOnClickListener {
            navigateToBenchmark(BenchmarkType.LLM)
        }

        binding.btnExportReport.setOnClickListener {
            exportReport()
        }

        binding.btnClearReport.setOnClickListener {
            clearReport()
        }

        binding.btnCancelBatch.setOnClickListener {
            cancelBatchTest()
        }

        binding.btnCompareModels.setOnClickListener {
            showCompareDialog()
        }
    }

    private fun updateModelStatuses() {
        val llmService = LocalLLMService.getInstance(requireContext())
        val llmDownloaded = LocalLLMService.AVAILABLE_MODELS.any { llmService.isModelDownloaded(it.name) }
        val llmLoaded = llmService.isModelLoaded

        when {
            llmLoaded -> {
                binding.chipLlmStatus.setStatus(ModelStatusChip.Status.COMPLETED)
                binding.tvLlmStatus.text = "状态: ✓ 已加载 ${llmService.getLoadedModelName()}"
            }
            llmDownloaded -> {
                binding.chipLlmStatus.setStatus(ModelStatusChip.Status.IDLE)
                binding.tvLlmStatus.text = "状态: ✓ 已下载模型，待加载"
            }
            else -> {
                binding.chipLlmStatus.setStatus(ModelStatusChip.Status.NOT_INSTALLED)
                binding.tvLlmStatus.text = "状态: 未下载模型"
            }
        }

        val imageDownloaded = LocalImageModelService.AVAILABLE_MODELS.any { imageModelService.isModelDownloaded(it.name) }
        val imageLoaded = imageModelService.isModelLoaded

        when {
            imageLoaded -> {
                binding.chipImageStatus.setStatus(ModelStatusChip.Status.COMPLETED)
                binding.tvImageStatus.text = "状态: ✓ 已加载 ${imageModelService.getLoadedModelName()}"
            }
            imageDownloaded -> {
                binding.chipImageStatus.setStatus(ModelStatusChip.Status.IDLE)
                binding.tvImageStatus.text = "状态: ✓ 已下载模型，待加载"
            }
            else -> {
                binding.chipImageStatus.setStatus(ModelStatusChip.Status.NOT_INSTALLED)
                binding.tvImageStatus.text = "状态: 未下载模型"
            }
        }

        val speechDownloaded = SpeechModelService.AVAILABLE_MODELS.any { speechModelService.isModelDownloaded(it.name) }
        val speechLoaded = speechModelService.isModelLoaded

        when {
            speechLoaded -> {
                binding.chipSpeechStatus.setStatus(ModelStatusChip.Status.COMPLETED)
                binding.tvSpeechStatus.text = "状态: ✓ 已加载 ${speechModelService.getLoadedModelName()}"
            }
            speechDownloaded -> {
                binding.chipSpeechStatus.setStatus(ModelStatusChip.Status.IDLE)
                binding.tvSpeechStatus.text = "状态: ✓ 已下载模型，待加载"
            }
            else -> {
                binding.chipSpeechStatus.setStatus(ModelStatusChip.Status.IDLE)
                binding.tvSpeechStatus.text = "状态: Android TTS 可用，Whisper/Paraformer 待下载"
            }
        }
    }

    private fun updateQuickStats() {
        val deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        binding.tvDeviceInfo.text = deviceInfo
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
        binding.cardBatchTest.visibility = View.GONE
        binding.btnRunAllBenchmarks.isEnabled = false
        binding.progressBatch.progress = 0
        binding.progressRing.setProgress(0f)
        binding.progressRing.setCenterText("0%")
        binding.progressRing.setStageText("准备测试...")
        binding.resourceMonitor.startMonitoring()

        batchJob = lifecycleScope.launch {
            try {
                val totalSteps = 100
                var currentStep = 0

                binding.chipLlmStatus.setStatus(ModelStatusChip.Status.TESTING)
                binding.chipImageStatus.setStatus(ModelStatusChip.Status.TESTING)
                binding.chipSpeechStatus.setStatus(ModelStatusChip.Status.TESTING)

                updateBatchProgress("正在测试语音模型...", "语音", "加载", currentStep)

                runSpeechBenchmark()
                currentStep += 33

                updateBatchProgress("正在测试图像模型...", "图像", "推理", currentStep)

                runImageBenchmark()
                currentStep += 33

                updateBatchProgress("正在测试LLM模型...", "LLM", "推理", currentStep)

                runLLMBenchmark()
                currentStep = 100

                updateBatchProgress("测试完成", "完成", "结束", currentStep)

                withContext(Dispatchers.Main) {
                    binding.resourceMonitor.stopMonitoring()
                    binding.cardBatchProgress.visibility = View.GONE
                    binding.cardBatchTest.visibility = View.VISIBLE
                    binding.btnRunAllBenchmarks.isEnabled = true
                    binding.btnExportReport.isEnabled = true
                    binding.btnClearReport.visibility = View.VISIBLE
                    binding.btnCompareModels.visibility = View.VISIBLE

                    displaySummaryReport()
                    updateModelStatuses()

                    Toast.makeText(
                        requireContext(),
                        "批量测试完成！共测试 ${batchResults.size} 个模型",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.resourceMonitor.stopMonitoring()
                    binding.cardBatchProgress.visibility = View.GONE
                    binding.cardBatchTest.visibility = View.VISIBLE
                    binding.btnRunAllBenchmarks.isEnabled = true
                    updateModelStatuses()
                    Toast.makeText(requireContext(), "测试出错: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun runSpeechBenchmark() {
        try {
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

            val testBitmap = createTestBitmap(config.inputSize.first, config.inputSize.second)

            repeat(2) {
                when (config.type) {
                    LocalImageModelService.ModelType.CLASSIFICATION -> imageModelService.classifyImage(testBitmap)
                    LocalImageModelService.ModelType.OCR -> imageModelService.recognizeText(testBitmap)
                    LocalImageModelService.ModelType.VLM -> imageModelService.encodeImage(testBitmap)
                }
            }

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

    private fun updateBatchProgress(message: String, model: String, stage: String, progress: Int) {
        requireActivity().runOnUiThread {
            binding.tvBatchProgressStatus.text = message
            binding.tvCurrentModel.text = "当前模型: $model"
            binding.tvStageInfo.text = "阶段: $stage"
            binding.progressBatch.progress = progress
            binding.progressRing.setProgress(progress.toFloat())
            binding.progressRing.setCenterText("$progress%")
            binding.progressRing.setStageText(stage)
        }
    }

    private fun displaySummaryReport() {
        val report = buildString {
            appendLine("=" .repeat(50))
            appendLine("📊 AI 模型性能测试报告")
            appendLine("=" .repeat(50))
            appendLine("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("设备: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE}")
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

            appendLine("=" .repeat(50))
            appendLine("📈 汇总统计")
            appendLine("-".repeat(40))
            val successCount = batchResults.count { it.success }
            val failCount = batchResults.count { !it.success }
            appendLine("  成功: $successCount")
            appendLine("  失败: $failCount")
            appendLine("  总计: ${batchResults.size}")

            binding.tvSuccessCount.text = "$successCount/${batchResults.size}"

            if (successCount > 0) {
                val avgLatency = batchResults.filter { it.success }.map { it.latencyMs }.average()
                val maxThroughput = batchResults.filter { it.success }.maxOf { it.throughput }
                appendLine("  平均延迟: %.1fms".format(avgLatency))
                appendLine("  最高吞吐量: %.2f/s".format(maxThroughput))
            }
        }

        binding.tvSummaryReport.text = report
        showChart()
    }

    private fun showChart() {
        val successResults = batchResults.filter { it.success }
        if (successResults.isEmpty()) return

        val modelNames = successResults.map { it.modelName.take(10) }
        val latencyValues = successResults.map { it.latencyMs.toFloat() }

        val chartData = BenchmarkChartView.BenchmarkChartData(
            label = "模型延迟 (ms)",
            labels = modelNames,
            values = latencyValues
        )

        binding.chartContainer.removeAllViews()
        val chartView = BenchmarkChartView(requireContext())
        chartView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 300)
        binding.chartContainer.addView(chartView)
        chartView.showChart(BAR, chartData)
        binding.chartContainer.visibility = View.VISIBLE
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
        binding.btnCompareModels.visibility = View.GONE
        binding.tvSuccessCount.text = "成功数"
        binding.chartContainer.removeAllViews()
        binding.chartContainer.visibility = View.GONE
    }

    private fun cancelBatchTest() {
        batchJob?.cancel()
        batchJob = null

        binding.resourceMonitor.stopMonitoring()
        binding.cardBatchProgress.visibility = View.GONE
        binding.cardBatchTest.visibility = View.VISIBLE
        binding.btnRunAllBenchmarks.isEnabled = true
        updateModelStatuses()

        Toast.makeText(requireContext(), "已取消批量测试", Toast.LENGTH_SHORT).show()
    }

    private fun showCompareDialog() {
        val fragment = ModelCompareFragment.newInstance()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
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
