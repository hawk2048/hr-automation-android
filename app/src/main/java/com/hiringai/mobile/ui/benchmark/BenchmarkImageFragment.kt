package com.hiringai.mobile.ui.benchmark

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.hiringai.mobile.R
import com.hiringai.mobile.databinding.FragmentBenchmarkImageBinding
import com.hiringai.mobile.ml.LocalImageModelService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 图像模型基准测试 Fragment
 *
 * 测试指标:
 * - 模型加载时间
 * - 推理延迟 (单次/批量)
 * - 内存占用
 * - 吞吐量 (inferences/second)
 */
class BenchmarkImageFragment : Fragment() {

    private var _binding: FragmentBenchmarkImageBinding? = null
    private val binding get() = _binding!!

    private lateinit var imageModelService: LocalImageModelService
    private lateinit var prefs: SharedPreferences

    private var selectedModelIndex = 0
    private val benchmarkResults = mutableListOf<BenchmarkResult>()

    data class BenchmarkResult(
        val modelName: String,
        val modelType: LocalImageModelService.ModelType,
        val loadTimeMs: Long,
        val inferenceTimeMs: Long,
        val memoryMB: Long,
        val throughput: Float,
        val iterations: Int
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBenchmarkImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageModelService = LocalImageModelService.getInstance(requireContext())
        prefs = requireContext().getSharedPreferences("benchmark_settings", 0)

        setupModelSelector()
        setupTestButtons()
        updateStatus()
    }

    private fun setupModelSelector() {
        val models = LocalImageModelService.AVAILABLE_MODELS
        val modelDisplayList = models.mapIndexed { index, config ->
            val isDownloaded = imageModelService.isModelDownloaded(config.name)
            val typeStr = when (config.type) {
                LocalImageModelService.ModelType.OCR -> "OCR"
                LocalImageModelService.ModelType.CLASSIFICATION -> "分类"
                LocalImageModelService.ModelType.VLM -> "VLM"
            }
            val sizeStr = formatSize(config.modelSize)
            if (isDownloaded) {
                "✓ ${config.name} ($typeStr, $sizeStr)"
            } else {
                "${config.name} ($typeStr, $sizeStr)"
            }
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, modelDisplayList)
        binding.spinnerImageModel.setAdapter(adapter)

        selectedModelIndex = 0
        binding.spinnerImageModel.setText(modelDisplayList[0], false)

        updateModelButtonState()

        binding.spinnerImageModel.setOnItemClickListener { _, _, position, _ ->
            selectedModelIndex = position
            updateModelButtonState()
        }
    }

    private fun updateModelButtonState() {
        val models = LocalImageModelService.AVAILABLE_MODELS
        if (selectedModelIndex < 0 || selectedModelIndex >= models.size) return

        val config = models[selectedModelIndex]
        val isDownloaded = imageModelService.isModelDownloaded(config.name)
        val isLoaded = imageModelService.isModelLoaded && imageModelService.getLoadedModelName() == config.name

        binding.btnDownloadImageModel.text = when {
            isLoaded -> "已加载 ✓"
            isDownloaded -> "重新下载"
            else -> "下载模型"
        }

        binding.btnDownloadImageModel.isEnabled = !isLoaded
    }

    private fun setupTestButtons() {
        binding.btnDownloadImageModel.setOnClickListener {
            downloadModel()
        }

        binding.btnRunBenchmark.setOnClickListener {
            runBenchmark()
        }

        binding.btnExportReport.setOnClickListener {
            exportReport()
        }

        binding.btnClearResults.setOnClickListener {
            benchmarkResults.clear()
            updateStatus()
            Toast.makeText(requireContext(), "结果已清除", Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadModel() {
        val models = LocalImageModelService.AVAILABLE_MODELS
        if (selectedModelIndex < 0 || selectedModelIndex >= models.size) return

        val config = models[selectedModelIndex]
        if (imageModelService.isModelLoaded && imageModelService.getLoadedModelName() == config.name) {
            Toast.makeText(requireContext(), "模型已在内存中", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressDownloadImage.visibility = View.VISIBLE
        binding.btnDownloadImageModel.isEnabled = false
        binding.btnDownloadImageModel.text = "下载中..."

        lifecycleScope.launch {
            val success = imageModelService.downloadModel(config) { progress ->
                requireActivity().runOnUiThread {
                    binding.progressDownloadImage.progress = progress
                    if (progress == 100) {
                        binding.progressDownloadImage.visibility = View.GONE
                        updateModelButtonState()
                        Toast.makeText(requireContext(), "下载完成: ${config.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            requireActivity().runOnUiThread {
                binding.progressDownloadImage.visibility = View.GONE
                updateModelButtonState()
                if (!success) {
                    Toast.makeText(requireContext(), getString(R.string.settings_download_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun runBenchmark() {
        val models = LocalImageModelService.AVAILABLE_MODELS
        if (selectedModelIndex < 0 || selectedModelIndex >= models.size) return

        val config = models[selectedModelIndex]

        if (!imageModelService.isModelDownloaded(config.name)) {
            Toast.makeText(requireContext(), "请先下载模型", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnRunBenchmark.isEnabled = false
        binding.btnRunBenchmark.text = "测试中..."
        binding.tvBenchmarkStatus.text = "开始基准测试..."

        val iterations = binding.etIterations.text.toString().toIntOrNull() ?: 10

        lifecycleScope.launch {
            val result = performBenchmark(config, iterations)

            withContext(Dispatchers.Main) {
                benchmarkResults.add(result)
                displayBenchmarkResult(result)
                binding.btnRunBenchmark.isEnabled = true
                binding.btnRunBenchmark.text = "运行基准测试"
            }
        }
    }

    private suspend fun performBenchmark(
        config: LocalImageModelService.ImageModelConfig,
        iterations: Int
    ): BenchmarkResult = withContext(Dispatchers.IO) {
        // 1. Measure model loading time
        val loadStart = System.currentTimeMillis()
        val loaded = imageModelService.loadModel(config)
        val loadTimeMs = System.currentTimeMillis() - loadStart

        if (!loaded) {
            return@withContext BenchmarkResult(
                modelName = config.name,
                modelType = config.type,
                loadTimeMs = -1,
                inferenceTimeMs = -1,
                memoryMB = -1,
                throughput = 0f,
                iterations = 0
            )
        }

        // 2. Get memory usage after loading
        val memoryMB = getMemoryUsageMB()

        // 3. Create a dummy test image
        val testBitmap = createTestBitmap(config.inputSize.first, config.inputSize.second)

        // 4. Run inference iterations and measure average time
        val inferenceTimes = mutableListOf<Long>()

        // Warmup
        repeat(2) {
            when (config.type) {
                LocalImageModelService.ModelType.CLASSIFICATION -> {
                    imageModelService.classifyImage(testBitmap)
                }
                LocalImageModelService.ModelType.OCR -> {
                    imageModelService.recognizeText(testBitmap)
                }
                LocalImageModelService.ModelType.VLM -> {
                    imageModelService.encodeImage(testBitmap)
                }
            }
        }

        // Timed iterations
        repeat(iterations) {
            val start = System.currentTimeMillis()
            when (config.type) {
                LocalImageModelService.ModelType.CLASSIFICATION -> {
                    imageModelService.classifyImage(testBitmap)
                }
                LocalImageModelService.ModelType.OCR -> {
                    imageModelService.recognizeText(testBitmap)
                }
                LocalImageModelService.ModelType.VLM -> {
                    imageModelService.encodeImage(testBitmap)
                }
            }
            val elapsed = System.currentTimeMillis() - start
            inferenceTimes.add(elapsed)
        }

        testBitmap.recycle()

        // Calculate metrics
        val avgInferenceMs = inferenceTimes.average().toLong()
        val throughput = if (avgInferenceMs > 0) 1000f / avgInferenceMs else 0f

        BenchmarkResult(
            modelName = config.name,
            modelType = config.type,
            loadTimeMs = loadTimeMs,
            inferenceTimeMs = avgInferenceMs,
            memoryMB = memoryMB,
            throughput = throughput,
            iterations = iterations
        )
    }

    private fun createTestBitmap(width: Int, height: Int): Bitmap {
        // Create a simple test image (gradient pattern)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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
        val usedMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        return usedMB
    }

    private fun displayBenchmarkResult(result: BenchmarkResult) {
        val typeStr = when (result.modelType) {
            LocalImageModelService.ModelType.OCR -> "OCR"
            LocalImageModelService.ModelType.CLASSIFICATION -> "图像分类"
            LocalImageModelService.ModelType.VLM -> "VLM"
        }

        val report = buildString {
            appendLine("=" .repeat(50))
            appendLine("📊 ${result.modelName} 基准测试结果")
            appendLine("=" .repeat(50))
            appendLine("模型类型: $typeStr")
            appendLine("测试次数: ${result.iterations}")
            appendLine()
            appendLine("📈 性能指标:")
            appendLine("  • 模型加载时间: ${result.loadTimeMs} ms")
            appendLine("  • 平均推理延迟: ${result.inferenceTimeMs} ms")
            appendLine("  • 内存占用: ${result.memoryMB} MB")
            appendLine("  • 吞吐量: %.2f inferences/s".format(result.throughput))
            appendLine()
        }

        binding.tvBenchmarkStatus.text = report

        // Also show in results list
        val currentResults = binding.tvBenchmarkResults.text.toString()
        binding.tvBenchmarkResults.text = currentResults + "\n" + report

        binding.btnExportReport.isEnabled = true
    }

    private fun exportReport() {
        if (benchmarkResults.isEmpty()) {
            Toast.makeText(requireContext(), "没有可导出的结果", Toast.LENGTH_SHORT).show()
            return
        }

        val report = generateReport()

        // Save to Downloads folder
        lifecycleScope.launch {
            try {
                val file = File(requireContext().getExternalFilesDir(null), "image_benchmark_report.txt")
                file.writeText(report)

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "报告已保存到: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun generateReport(): String {
        return buildString {
            appendLine("=" .repeat(60))
            appendLine("图像模型基准测试报告")
            appendLine("生成时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine("=" .repeat(60))
            appendLine()

            // Summary
            appendLine("📋 摘要:")
            appendLine("  测试模型数量: ${benchmarkResults.size}")
            appendLine()

            // Summary table
            appendLine("📊 汇总表:")
            appendLine("-".repeat(60))
            appendLine(String.format("%-25s %10s %12s %10s", "模型", "加载(ms)", "推理(ms)", "吞吐(inf/s)"))
            appendLine("-".repeat(60))

            benchmarkResults.forEach { result ->
                appendLine(String.format("%-25s %10d %12d %10.2f",
                    result.modelName.take(25),
                    result.loadTimeMs,
                    result.inferenceTimeMs,
                    result.throughput))
            }
            appendLine("-".repeat(60))
            appendLine()

            // Detailed results
            appendLine("📝 详细结果:")
            benchmarkResults.forEach { result ->
                val typeStr = when (result.modelType) {
                    LocalImageModelService.ModelType.OCR -> "OCR"
                    LocalImageModelService.ModelType.CLASSIFICATION -> "图像分类"
                    LocalImageModelService.ModelType.VLM -> "VLM"
                }
                appendLine()
                appendLine("▶ ${result.modelName} ($typeStr)")
                appendLine("  加载时间: ${result.loadTimeMs} ms")
                appendLine("  推理延迟: ${result.inferenceTimeMs} ms")
                appendLine("  内存占用: ${result.memoryMB} MB")
                appendLine("  吞吐量: %.2f inf/s".format(result.throughput))
                appendLine("  测试迭代: ${result.iterations}")
            }
        }
    }

    private fun updateStatus() {
        val isLoaded = imageModelService.isModelLoaded
        val modelName = imageModelService.getLoadedModelName()

        binding.tvModelStatus.text = if (isLoaded) {
            "✓ 已加载: $modelName"
        } else {
            "✗ 未加载任何模型"
        }

        binding.btnRunBenchmark.isEnabled = isLoaded
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0)
            else -> "$bytes B"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}