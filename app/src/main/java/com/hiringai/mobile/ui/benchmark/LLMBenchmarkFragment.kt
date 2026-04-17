package com.hiringai.mobile.ui.benchmark

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.hiringai.mobile.R
import com.hiringai.mobile.databinding.FragmentLlmBenchmarkBinding
import com.hiringai.mobile.ml.LocalLLMService
import com.hiringai.mobile.ml.benchmark.BatchBenchmarkReport
import com.hiringai.mobile.ml.benchmark.BenchmarkProgress
import com.hiringai.mobile.ml.benchmark.BenchmarkState
import com.hiringai.mobile.ml.benchmark.LLMBenchmarkRunner
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * LLM 基准测试 Fragment
 *
 * 提供 LLM 模型的批量性能测试功能，包括：
 * - 模型加载时间测试
 * - 推理延迟测试
 * - 内存占用测试
 * - 吞吐量测试
 * - 批量测试和报告生成
 */
class LLMBenchmarkFragment : Fragment() {

    private var _binding: FragmentLlmBenchmarkBinding? = null
    private val binding get() = _binding!!

    private lateinit var benchmarkRunner: LLMBenchmarkRunner
    private var selectedModelIndex = 0
    private var lastReport: BatchBenchmarkReport? = null

    companion object {
        fun newInstance() = LLMBenchmarkFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLlmBenchmarkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        benchmarkRunner = LLMBenchmarkRunner(requireContext())

        setupDeviceInfo()
        setupModelSelector()
        setupButtons()
        updateResults("等待测试...\n\n提示：\n- 单模型测试: 测试选中的模型\n- 批量测试: 测试所有已下载的模型\n- 快速测试: 测试当前已加载的模型")
    }

    private fun setupDeviceInfo() {
        binding.tvDeviceInfo.text = benchmarkRunner.getDeviceInfo()
    }

    private fun setupModelSelector() {
        val models = LocalLLMService.AVAILABLE_MODELS
        val llmService = LocalLLMService.getInstance(requireContext())
        val modelDisplayList = models.mapIndexed { index, config ->
            val isDownloaded = llmService.isModelDownloaded(config.name)
            val sizeStr = formatSize(config.size)
            if (isDownloaded) {
                "✓ $sizeStr | ${config.requiredRAM}GB | ${config.name}"
            } else {
                "$sizeStr | ${config.requiredRAM}GB | ${config.name}"
            }
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, modelDisplayList)
        binding.spinnerModels.setAdapter(adapter)

        // Find first downloaded model
        val firstDownloaded = models.indexOfFirst { llmService.isModelDownloaded(it.name) }
        selectedModelIndex = if (firstDownloaded >= 0) firstDownloaded else 0

        if (modelDisplayList.isNotEmpty()) {
            binding.spinnerModels.setText(modelDisplayList[selectedModelIndex], false)
        }

        binding.spinnerModels.setOnItemClickListener { _, _, position, _ ->
            selectedModelIndex = position
        }
    }

    private fun setupButtons() {
        binding.btnSingleBenchmark.setOnClickListener {
            runSingleBenchmark()
        }

        binding.btnBatchBenchmark.setOnClickListener {
            runBatchBenchmark()
        }

        binding.btnQuickBenchmark.setOnClickListener {
            runQuickBenchmark()
        }

        binding.btnExportReport.setOnClickListener {
            exportReport()
        }

        binding.btnClearResults.setOnClickListener {
            binding.tvResults.text = "等待测试..."
            lastReport = null
            binding.btnExportReport.isEnabled = false
        }
    }

    private fun runSingleBenchmark() {
        val models = LocalLLMService.AVAILABLE_MODELS
        if (selectedModelIndex < 0 || selectedModelIndex >= models.size) {
            Toast.makeText(requireContext(), "请选择有效的模型", Toast.LENGTH_SHORT).show()
            return
        }

        val config = models[selectedModelIndex]
        val testPrompt = binding.etTestPrompt.text.toString().ifEmpty { "请用一句话介绍你自己" }

        binding.cardProgress.visibility = View.VISIBLE
        binding.btnSingleBenchmark.isEnabled = false
        binding.btnBatchBenchmark.isEnabled = false

        updateProgress("正在测试: ${config.name}...", 0)

        lifecycleScope.launch {
            val result = benchmarkRunner.benchmarkModel(config, testPrompt)

            requireActivity().runOnUiThread {
                binding.btnSingleBenchmark.isEnabled = true
                binding.btnBatchBenchmark.isEnabled = true
                binding.cardProgress.visibility = View.GONE

                val report = BatchBenchmarkReport(
                    results = listOf(result),
                    deviceInfo = benchmarkRunner.getDeviceInfo(),
                    totalDurationMs = result.loadTimeMs
                )
                lastReport = report

                updateResults(result.toSummary() + "\n\n生成内容:\n${result.generatedText}")
                binding.btnExportReport.isEnabled = true

                if (result.success) {
                    Toast.makeText(requireContext(), "测试完成: ${config.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "测试失败: ${result.errorMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun runBatchBenchmark() {
        val llmService = LocalLLMService.getInstance(requireContext())
        val downloadedModels = LocalLLMService.AVAILABLE_MODELS.filter { llmService.isModelDownloaded(it.name) }

        if (downloadedModels.isEmpty()) {
            Toast.makeText(requireContext(), "没有已下载的模型可供测试", Toast.LENGTH_SHORT).show()
            return
        }

        val testPrompt = binding.etTestPrompt.text.toString().ifEmpty { "请用一句话介绍你自己" }

        binding.cardProgress.visibility = View.VISIBLE
        binding.btnSingleBenchmark.isEnabled = false
        binding.btnBatchBenchmark.isEnabled = false

        lifecycleScope.launch {
            benchmarkRunner.runBatchBenchmark(downloadedModels, testPrompt).collectLatest { progress ->
                requireActivity().runOnUiThread {
                    when (progress.state) {
                        BenchmarkState.LOADING -> {
                            updateProgress("加载模型: ${progress.currentModel?.name}", progress.progressPercent)
                        }
                        BenchmarkState.COMPLETED -> {
                            updateProgress("测试完成: ${progress.currentModel?.name}", progress.progressPercent)
                        }
                        BenchmarkState.FAILED -> {
                            updateProgress("测试失败: ${progress.currentModel?.name}", progress.progressPercent)
                        }
                        BenchmarkState.FINISHED -> {
                            // Batch complete
                        }
                        else -> {}
                    }

                    if (progress.state == BenchmarkState.FINISHED && progress.report != null) {
                        binding.btnSingleBenchmark.isEnabled = true
                        binding.btnBatchBenchmark.isEnabled = true
                        binding.cardProgress.visibility = View.GONE

                        lastReport = progress.report
                        updateResults(progress.report.toExportText())
                        binding.btnExportReport.isEnabled = true

                        Toast.makeText(
                            requireContext(),
                            "批量测试完成: ${progress.report.results.count { it.success }}/${progress.report.results.size} 成功",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun runQuickBenchmark() {
        val llmService = LocalLLMService.getInstance(requireContext())

        if (!llmService.isModelLoaded) {
            Toast.makeText(requireContext(), "请先在设置中加载模型", Toast.LENGTH_SHORT).show()
            return
        }

        val testPrompt = binding.etTestPrompt.text.toString().ifEmpty { "请用一句话介绍你自己" }

        binding.cardProgress.visibility = View.VISIBLE
        binding.btnQuickBenchmark.isEnabled = false

        updateProgress("正在快速测试已加载模型...", 0)

        lifecycleScope.launch {
            val result = benchmarkRunner.quickBenchmark(testPrompt)

            requireActivity().runOnUiThread {
                binding.btnQuickBenchmark.isEnabled = true
                binding.cardProgress.visibility = View.GONE

                val report = BatchBenchmarkReport(
                    results = listOf(result),
                    deviceInfo = benchmarkRunner.getDeviceInfo(),
                    totalDurationMs = 0
                )
                lastReport = report

                updateResults("⚡ 快速测试结果 (已加载模型)\n\n" + result.toSummary() + "\n\n生成内容:\n${result.generatedText}")
                binding.btnExportReport.isEnabled = true

                Toast.makeText(requireContext(), "快速测试完成", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportReport() {
        val report = lastReport
        if (report == null) {
            Toast.makeText(requireContext(), "没有可导出的报告", Toast.LENGTH_SHORT).show()
            return
        }

        // Copy to clipboard
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("LLM Benchmark Report", report.toExportText())
        clipboard.setPrimaryClip(clip)

        Toast.makeText(requireContext(), "报告已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    private fun updateProgress(status: String, progress: Int) {
        binding.tvProgressStatus.text = status
        binding.progressBenchmark.progress = progress
    }

    private fun updateResults(text: String) {
        binding.tvResults.text = text
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> "%.1fGB".format(bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> "%.0fMB".format(bytes / 1_000_000.0)
            else -> "$bytes B"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}