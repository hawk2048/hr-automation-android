package com.hiringai.mobile.ui.benchmark

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hiringai.mobile.R
import com.hiringai.mobile.databinding.FragmentBenchmarkSpeechBinding
import com.hiringai.mobile.ml.speech.LocalSpeechService
import com.hiringai.mobile.ml.speech.SpeechModelType
import com.hiringai.mobile.ml.benchmark.SpeechModelBenchmark
import com.hiringai.mobile.ml.benchmark.SpeechModelBenchmarkResult
import com.hiringai.mobile.ml.benchmark.SpeechBatchReport
import com.hiringai.mobile.ml.benchmark.TestAudioGenerator
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
    private var lastReport: SpeechBatchReport? = null

    // 音频录制相关
    private var audioRecorder: TestAudioGenerator.AudioRecorder? = null
    private var isRecording = false
    private var selectedAudioData: FloatArray? = null
    private var selectedAudioName: String = ""

    // 选中的测试类型
    private var selectedBenchmarkType: BenchmarkType = BenchmarkType.ALL

    private enum class BenchmarkType {
        TTS, STT, VAD, ALL
    }

    // Permission launcher using modern API
    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecordingInternal()
        } else {
            Toast.makeText(requireContext(), "需要录音权限才能录制音频", Toast.LENGTH_SHORT).show()
        }
    }

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

        setupChipGroup()
        setupButtons()
        updateStatus("等待开始测试...\n\n选择测试类型后点击\"开始测试\"")
        updateDeviceInfo()
    }

    private fun updateDeviceInfo() {
        binding.tvDeviceInfo.text = benchmarkRunner.getDeviceInfo()
    }

    private fun setupChipGroup() {
        binding.chipGroupBenchmarkType.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedBenchmarkType = when {
                checkedIds.contains(R.id.chip_tts) -> BenchmarkType.TTS
                checkedIds.contains(R.id.chip_stt) -> BenchmarkType.STT
                checkedIds.contains(R.id.chip_vad) -> BenchmarkType.VAD
                checkedIds.contains(R.id.chip_all) -> BenchmarkType.ALL
                else -> BenchmarkType.ALL
            }
        }
    }

    private fun setupButtons() {
        // 录音按钮
        binding.btnRecordAudio.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        // 停止录音按钮
        binding.btnStopRecording.setOnClickListener {
            stopRecording()
        }

        // 选择音频文件
        binding.btnSelectAudio.setOnClickListener {
            Toast.makeText(requireContext(), "音频文件选择功能开发中", Toast.LENGTH_SHORT).show()
        }

        // 使用内置测试音频
        binding.btnUseTestAudio.setOnClickListener {
            showTestAudioSelectionDialog()
        }

        // 开始测试
        binding.btnStartBenchmark.setOnClickListener {
            startBenchmark()
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

    private fun startRecording() {
        // 检查录音权限
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        startRecordingInternal()
    }

    private fun startRecordingInternal() {
        try {
            audioRecorder = TestAudioGenerator.AudioRecorder(requireContext())
            val success = audioRecorder?.startRecording() ?: false

            if (success) {
                isRecording = true
                binding.btnRecordAudio.text = "停止"
                binding.layoutRecordingStatus.visibility = View.VISIBLE
                binding.btnStopRecording.isEnabled = true
                binding.chronometerRecording.base = SystemClock.elapsedRealtime()
                binding.chronometerRecording.start()
                binding.tvRecordingStatus.text = "正在录音..."
            } else {
                Toast.makeText(requireContext(), "无法启动录音", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "录音失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        if (!isRecording || audioRecorder == null) return

        binding.chronometerRecording.stop()
        binding.tvRecordingStatus.text = "处理录音..."

        lifecycleScope.launch {
            val audioData = withContext(Dispatchers.IO) {
                audioRecorder?.stopRecording()
            }

            isRecording = false
            binding.btnRecordAudio.text = "录音"
            binding.btnStopRecording.isEnabled = false

            if (audioData != null && audioData.isNotEmpty()) {
                selectedAudioData = audioData
                selectedAudioName = "录音 (${audioData.size / TestAudioGenerator.SAMPLE_RATE}秒)"

                binding.tvAudioInfo.text = "音频: $selectedAudioName\n采样率: ${TestAudioGenerator.SAMPLE_RATE} Hz\n样本数: ${audioData.size}"
                binding.tvAudioInfo.visibility = View.VISIBLE

                // 立即进行语音识别测试
                runTranscriptionTest(audioData)
            } else {
                binding.tvRecordingStatus.text = "录音失败或为空"
            }
        }
    }

    private fun showTestAudioSelectionDialog() {
        val testAudios = TestAudioGenerator.getAvailableTestAudios()
        val displayNames = testAudios.map { "${it.displayName}\n${it.description}" }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择测试音频")
            .setItems(displayNames.toTypedArray()) { dialog, which ->
                val selectedInfo = testAudios[which]
                loadTestAudio(selectedInfo)
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadTestAudio(info: TestAudioGenerator.TestAudioInfo) {
        lifecycleScope.launch {
            binding.tvStatusArea.text = "加载测试音频: ${info.displayName}..."

            val audioData = withContext(Dispatchers.IO) {
                TestAudioGenerator.loadTestAudio(requireContext(), info)
            }

            if (audioData != null) {
                selectedAudioData = audioData
                selectedAudioName = info.displayName

                binding.tvAudioInfo.text = "音频: ${info.displayName}\n采样率: ${info.sampleRate} Hz\n时长: ${info.durationSeconds}秒"
                binding.tvAudioInfo.visibility = View.VISIBLE

                updateStatus("已加载测试音频: ${info.displayName}\n可以开始测试")
            } else {
                updateStatus("加载测试音频失败")
            }
        }
    }

    private fun runTranscriptionTest(audioData: FloatArray) {
        if (!speechService.isModelLoaded) {
            // 尝试加载第一个可用的模型
            val models = LocalSpeechService.AVAILABLE_MODELS
            val downloadedModel = models.firstOrNull { speechService.isModelDownloaded(it.name) }

            if (downloadedModel == null) {
                binding.tvTranscriptionResult.text = "请先下载并加载语音识别模型"
                binding.tvTranscriptionResult.visibility = View.VISIBLE
                return
            }

            lifecycleScope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    speechService.loadModel(downloadedModel)
                }

                if (loaded) {
                    performTranscription(audioData)
                } else {
                    binding.tvTranscriptionResult.text = "无法加载模型"
                    binding.tvTranscriptionResult.visibility = View.VISIBLE
                }
            }
        } else {
            performTranscription(audioData)
        }
    }

    private fun performTranscription(audioData: FloatArray) {
        lifecycleScope.launch {
            binding.tvStatusArea.text = "正在进行语音识别..."
            binding.tvTranscriptionResult.visibility = View.VISIBLE
            binding.tvTranscriptionResult.text = "识别中..."

            val result = withContext(Dispatchers.IO) {
                speechService.transcribe(audioData, TestAudioGenerator.SAMPLE_RATE)
            }

            if (result != null) {
                val displayText = buildString {
                    appendLine("📝 识别结果:")
                    appendLine(result.text)
                    appendLine()
                    appendLine("置信度: ${"%.2f".format(result.confidence)}")
                    appendLine("时长: ${"%.1f".format(result.duration)}秒")
                    if (result.language != null) {
                        appendLine("语言: ${result.language}")
                    }
                }
                binding.tvTranscriptionResult.text = displayText
                updateStatus("语音识别完成")
            } else {
                binding.tvTranscriptionResult.text = "识别失败"
                updateStatus("语音识别失败")
            }
        }
    }

    private fun startBenchmark() {
        val warmupIterations = binding.etWarmupIterations.text.toString().toIntOrNull() ?: 2
        val testIterations = binding.etTestIterations.text.toString().toIntOrNull() ?: 5

        setLoading(true)
        binding.progressBenchmark.visibility = View.VISIBLE
        binding.tvProgressStatus.visibility = View.VISIBLE

        lifecycleScope.launch {
            val report = when (selectedBenchmarkType) {
                BenchmarkType.STT -> benchmarkRunner.benchmarkWhisperModels { progress, result ->
                    requireActivity().runOnUiThread {
                        updateProgress(progress, result)
                    }
                }
                BenchmarkType.VAD -> benchmarkRunner.benchmarkVADModels { progress, result ->
                    requireActivity().runOnUiThread {
                        updateProgress(progress, result)
                    }
                }
                BenchmarkType.ALL -> benchmarkRunner.runBatchBenchmark { progress, result ->
                    requireActivity().runOnUiThread {
                        updateProgress(progress, result)
                    }
                }
                BenchmarkType.TTS -> {
                    // TTS benchmark not fully implemented yet
                    SpeechBatchReport(
                        results = listOf(),
                        deviceInfo = benchmarkRunner.getDeviceInfo(),
                        totalDurationMs = 0
                    )
                }
            }

            withContext(Dispatchers.Main) {
                setLoading(false)
                lastReport = report
                displayReport(report)
                updateStatus("测试完成\n成功: ${report.results.count { it.success }}/${report.results.size}")
            }
        }
    }

    private fun updateProgress(progress: Int, result: SpeechModelBenchmarkResult?) {
        binding.progressBenchmark.progress = progress
        binding.tvProgressStatus.text = "进度: $progress%"
        if (result != null) {
            appendResult(result)
        }
    }

    private fun displayResult(result: SpeechModelBenchmarkResult) {
        binding.tvResults.text = result.toSummary()
        binding.btnExportReport.visibility = View.VISIBLE
    }

    private fun appendResult(result: SpeechModelBenchmarkResult) {
        val currentText = binding.tvResults.text.toString()
        if (currentText == "暂无测试结果") {
            binding.tvResults.text = result.toSummary()
        } else {
            binding.tvResults.append("\n\n${result.toSummary()}")
        }
    }

    private fun displayReport(report: SpeechBatchReport) {
        binding.tvResults.text = report.toExportText()
        binding.btnExportReport.visibility = View.VISIBLE
        binding.progressBenchmark.visibility = View.GONE
        binding.tvProgressStatus.visibility = View.GONE
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
        selectedAudioData = null
        selectedAudioName = ""
        binding.tvAudioInfo.visibility = View.GONE
        binding.tvTranscriptionResult.visibility = View.GONE
        updateStatus("结果已清空")
    }

    private fun setLoading(loading: Boolean) {
        binding.btnStartBenchmark.isEnabled = !loading
        binding.btnRecordAudio.isEnabled = !loading
        binding.btnSelectAudio.isEnabled = !loading
        binding.btnUseTestAudio.isEnabled = !loading
    }

    private fun updateStatus(message: String) {
        binding.tvStatusArea.text = message
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isRecording) {
            audioRecorder?.stopRecording()
            isRecording = false
        }
        _binding = null
    }
}
