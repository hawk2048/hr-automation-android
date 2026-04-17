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
import com.hiringai.mobile.databinding.FragmentBenchmarkSpeechBinding
import com.hiringai.mobile.ml.speech.LocalSpeechService
import com.hiringai.mobile.ml.speech.SpeechModelType
import com.hiringai.mobile.ml.benchmark.SpeechModelBenchmark
import com.hiringai.mobile.ml.benchmark.SpeechModelBenchmarkResult
import com.hiringai.mobile.ml.benchmark.SpeechBatchReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 语音模型基准测试 Fragment
 *
 * 支持测试:
 * - Whisper (OpenAI 语音识别)
 * - Paraformer (阿里中文语音识别)
 * - Cam++ VAD (语音活动检测)
 * - TTS (语音合成)
 */
class BenchmarkSpeechFragment : Fragment() {

    private var _binding: FragmentBenchmarkSpeechBinding? = null
    private val binding get() = _binding!!

    private lateinit var speechService: LocalSpeechService
    private lateinit var benchmarkRunner: SpeechModelBenchmark
    private var selectedModelIndex = 0
    private var lastReport: SpeechBatchReport? = null

    companion object {
        fun newInstance() = BenchmarkSpeechFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBenchmarkSpeechBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        speechService = LocalSpeechService.getInstance(requireContext())
        benchmarkRunner = SpeechModelBenchmark(requireContext())

        setupModelSelector()
        setupButtons()
        updateStatus("等待开始测试...\n\n选择模型类型后点击\"开始测试\"")
        updateDeviceInfo()
    }

    private fun updateDeviceInfo() {
        binding.tvDeviceInfo.text = benchmarkRunner.getDeviceInfo()
    }

    private fun setupModelSelector() {
        val models = LocalSpeechService.AVAILABLE_MODELS
        val modelDisplayList = models.mapIndexed { index, config ->
            val isDownloaded = speechService.isModelDownloaded(config.name)
            val typeStr = when (config.type) {
                SpeechModelType.WHISPER -> "Whisper"
                SpeechModelType.PARAFORMER -> "Paraformer"
                SpeechModelType.CAM_PLUS -> "Cam++"
                SpeechModelType.TTS -> "TTS"
                SpeechModelType.VAD -> "VAD"
            }
            val sizeStr = formatSize(config.modelSize)
            if (isDownloaded) {
                "✓ $typeStr | $sizeStr | ${config.name}"
            } else {
                "$typeStr | $sizeStr | ${config.name}"
            }
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, modelDisplayList)
        binding.spinnerModels.setAdapter(adapter)

        selectedModelIndex = 0
        if (modelDisplayList.isNotEmpty()) {
            binding.spinnerModels.setText(modelDisplayList[0], false)
        }

        binding.spinnerModels.setOnItemClickListener { _, _, position, _ ->
            selectedModelIndex = position
            updateModelStatus()
        }
    }

    private fun updateModelStatus() {
        val models = LocalSpeechService.AVAILABLE_MODELS
        if (selectedModelIndex < 0 || selectedModelIndex >= models.size) return

        val config = models[selectedModelIndex]
        val isDownloaded = speechService.isModelDownloaded(config.name)
        val isLoaded = speechService.isModelLoaded && speechService.getLoadedModelName() == config.name

        binding.tvModelStatus.text = when {
            isLoaded -> "✓ 已加载: ${config.name}"
            isDownloaded -> "✓ 已下载: ${config.name}"
            else -> "✗ 未下载: ${config.name}"
        }
    }

    private fun setupButtons() {
        // 下载模型按钮
        binding.btnDownloadModel.setOnClickListener {
            downloadModel()
        }

        // 单模型测试按钮
        binding.btnSingleBenchmark.setOnClickListener {
            runSingleBenchmark()
        }

        // 批量测试按钮
        binding.btnBatchBenchmark.setOnClickListener {
            runBatchBenchmark()
        }

        // 批量 Whisper 测试
        binding.btnBenchmarkWhisper.setOnClickListener {
            runWhisperBenchmark()
        }

        // 批量 Paraformer 测试
        binding.btnBenchmarkParaformer.setOnClickListener {
            runParaformerBenchmark()
        }

        // 批量 VAD 测试
        binding.btnBenchmarkVad.setOnClickListener {
            runVADBenchmark()
        }

        // 导出报告
        binding.btnExportReport.setOnClickListener {
            exportReport()
        }

        // 清空结果
        binding.btnClearResults.setOnClickListener {
            clearResults()
        }
    }

    private fun downloadModel() {
        val models = LocalSpeechService.AVAILABLE_MODELS
        if (selectedModelIndex < 0 || selectedModelIndex >= models.size) return

        val config = models[selectedModelIndex]

        binding.progressDownload.visibility = View.VISIBLE
        binding.btnDownloadModel.isEnabled = false
        binding.btnDownloadModel.text = "下载中..."

        lifecycleScope.launch {
            val success = speechService.downloadModel(config) { progress ->
                requireActivity().runOnUiThread {
                    binding.progressDownload.progress = progress
                    if (progress == 100) {
                        binding.progressDownload.visibility = View.GONE
                        updateModelStatus()
                        Toast.makeText(requireContext(), "下载完成: ${config.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            requireActivity().runOnUiThread {
                binding.progressDownload.visibility = View.GONE
                binding.btnDownloadModel.text = "下载模型"
                binding.btnDownloadModel.isEnabled = true
                updateModelStatus()

                if (!success) {
                    Toast.makeText(requireContext(), "下载失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun runSingleBenchmark() {
        val models = LocalSpeechService.AVAILABLE_MODELS
        if (selectedModelIndex < 0 || selectedModelIndex >= models.size) {
            Toast.makeText(requireContext(), "请选择模型", Toast.LENGTH_SHORT).show()
            return
        }

        val config = models[selectedModelIndex]
        if (!speechService.isModelDownloaded(config.name)) {
            Toast.makeText(requireContext(), "请先下载模型", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        updateStatus("正在测试: ${config.name}...")

        lifecycleScope.launch {
            val result = benchmarkRunner.benchmarkModel(config)

            withContext(Dispatchers.Main) {
                setLoading(false)
                displayResult(result)
                updateStatus("测试完成: ${config.name}\n${if (result.success) "✅ 成功" else "❌ 失败"}")
            }
        }
    }

    private fun runBatchBenchmark() {
        setLoading(true)
        updateStatus("正在批量测试所有语音模型...")

        lifecycleScope.launch {
            val report = benchmarkRunner.runBatchBenchmark { progress, result ->
                requireActivity().runOnUiThread {
                    binding.progressBenchmark.progress = progress
                    if (result != null) {
                        appendResult(result)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                setLoading(false)
                lastReport = report
                displayReport(report)
                updateStatus("批量测试完成\n成功: ${report.results.count { it.success }}/${report.results.size}")
            }
        }
    }

    private fun runWhisperBenchmark() {
        setLoading(true)
        updateStatus("正在测试 Whisper 模型...")

        lifecycleScope.launch {
            val report = benchmarkRunner.benchmarkWhisperModels { progress, result ->
                requireActivity().runOnUiThread {
                    binding.progressBenchmark.progress = progress
                    if (result != null) {
                        appendResult(result)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                setLoading(false)
                lastReport = report
                displayReport(report)
            }
        }
    }

    private fun runParaformerBenchmark() {
        setLoading(true)
        updateStatus("正在测试 Paraformer 模型...")

        lifecycleScope.launch {
            val report = benchmarkRunner.benchmarkParaformerModels { progress, result ->
                requireActivity().runOnUiThread {
                    binding.progressBenchmark.progress = progress
                    if (result != null) {
                        appendResult(result)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                setLoading(false)
                lastReport = report
                displayReport(report)
            }
        }
    }

    private fun runVADBenchmark() {
        setLoading(true)
        updateStatus("正在测试 VAD 模型...")

        lifecycleScope.launch {
            val report = benchmarkRunner.benchmarkVADModels { progress, result ->
                requireActivity().runOnUiThread {
                    binding.progressBenchmark.progress = progress
                    if (result != null) {
                        appendResult(result)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                setLoading(false)
                lastReport = report
                displayReport(report)
            }
        }
    }

    private fun displayResult(result: SpeechModelBenchmarkResult) {
        binding.tvResults.text = result.toSummary()
        binding.btnExportReport.visibility = View.VISIBLE
    }

    private fun appendResult(result: SpeechModelBenchmarkResult) {
        binding.tvResults.append("\n\n${result.toSummary()}")
    }

    private fun displayReport(report: SpeechBatchReport) {
        binding.tvResults.text = report.toExportText()
        binding.btnExportReport.visibility = View.VISIBLE
    }

    private fun exportReport() {
        val report = lastReport ?: return

        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Speech Benchmark Report", report.toExportText())
        clipboard.setPrimaryClip(clip)

        Toast.makeText(requireContext(), "报告已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    private fun clearResults() {
        binding.tvResults.text = "暂无测试结果"
        binding.btnExportReport.visibility = View.GONE
        lastReport = null
        updateStatus("结果已清空")
    }

    private fun setLoading(loading: Boolean) {
        binding.btnSingleBenchmark.isEnabled = !loading
        binding.btnBatchBenchmark.isEnabled = !loading
        binding.btnBenchmarkWhisper.isEnabled = !loading
        binding.btnBenchmarkParaformer.isEnabled = !loading
        binding.btnBenchmarkVad.isEnabled = !loading
        binding.progressBenchmark.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun updateStatus(message: String) {
        binding.tvStatusArea.text = message
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
