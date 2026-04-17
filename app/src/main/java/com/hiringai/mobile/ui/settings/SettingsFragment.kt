package com.hiringai.mobile.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.hiringai.mobile.R
import com.hiringai.mobile.databinding.FragmentSettingsBinding
import com.hiringai.mobile.ml.DeviceCapabilityDetector
import com.hiringai.mobile.ml.LocalEmbeddingService
import com.hiringai.mobile.ml.LocalLLMService
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var llmService: LocalLLMService
    private lateinit var embeddingService: LocalEmbeddingService
    private lateinit var deviceDetector: DeviceCapabilityDetector
    private lateinit var prefs: SharedPreferences

    // Track selected models
    private var selectedLlmModelIndex = 0
    private var selectedEmbeddingModelIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        llmService = LocalLLMService.getInstance(requireContext())
        embeddingService = LocalEmbeddingService.getInstance(requireContext())
        deviceDetector = DeviceCapabilityDetector(requireContext())
        prefs = requireContext().getSharedPreferences("hra_settings", 0)

        setupInferenceMode()
        setupDeviceDetection()
        setupStatusArea()
        setupLLMModelSelector()
        setupEmbeddingModelSelector()
        setupOllamaConfig()
        setupDownloadButton()
        setupUnloadButton()
        setupTestButton()
        setupBenchmarkButton()
        updateModelStatus()
    }

    private fun setupInferenceMode() {
        val modes = listOf("设备端推理 (llama.cpp)", "远程 Ollama")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, modes)
        binding.spinnerInferenceMode.setAdapter(adapter)

        val savedMode = prefs.getString("inference_mode", "local") ?: "local"
        binding.spinnerInferenceMode.setText(
            if (savedMode == "ollama") modes[1] else modes[0],
            false
        )

        binding.spinnerInferenceMode.setOnItemClickListener { _, _, position, _ ->
            val mode = if (position == 0) "local" else "ollama"
            prefs.edit().putString("inference_mode", mode).apply()
            updateVisibilityForMode(mode)
        }

        updateVisibilityForMode(savedMode)
    }

    private fun updateVisibilityForMode(mode: String) {
        val isLocal = mode == "local"
        binding.cardLocalModel.visibility = if (isLocal) View.VISIBLE else View.GONE
        binding.cardOllamaConfig.visibility = if (isLocal) View.GONE else View.VISIBLE
        binding.cardEmbeddingModel.visibility = if (isLocal) View.VISIBLE else View.GONE
    }

    private fun setupDeviceDetection() {
        binding.btnDetectDevice.setOnClickListener {
            detectDeviceCapabilities()
        }
    }

    private fun setupStatusArea() {
        updateStatusArea()
        binding.btnRefreshStatus.setOnClickListener {
            updateStatusArea()
        }
        binding.btnClearOutput.setOnClickListener {
            updateStatusArea()
            binding.btnClearOutput.visibility = View.GONE
        }
    }

    private fun updateStatusArea() {
        val sb = StringBuilder()
        val activityManager = requireContext().getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalRamGB = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val availRamGB = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
        val usedRamGB = totalRamGB - availRamGB
        val usedPercent = ((usedRamGB / totalRamGB) * 100).toInt()

        sb.append("📊 系统状态:\n")
        sb.append("  内存: %.1fGB / %.1fGB (已用 %d%%)\n".format(usedRamGB, totalRamGB, usedPercent))

        val processor = java.lang.Runtime.getRuntime()
        sb.append("  CPU核心: %d\n".format(processor.availableProcessors()))

        sb.append("\n🤖 模型状态:\n")
        if (llmService.isModelLoaded) {
            sb.append("  LLM: ✓ 已加载 (${llmService.getLoadedModelName()})\n")
        } else {
            sb.append("  LLM: ✗ 未加载\n")
        }
        if (embeddingService.loaded) {
            sb.append("  Embedding: ✓ 已加载\n")
        } else {
            sb.append("  Embedding: ✗ 未加载\n")
        }

        binding.tvStatusArea.text = sb.toString()
    }

    private fun detectDeviceCapabilities() {
        binding.btnDetectDevice.isEnabled = false
        binding.btnDetectDevice.text = "检测中..."

        lifecycleScope.launch {
            val capabilities = deviceDetector.detectCapabilities()

            requireActivity().runOnUiThread {
                // Show device info
                binding.tvDeviceInfo.text = deviceDetector.getDeviceSummary(capabilities)

                // Show recommendations
                val recommendations = deviceDetector.recommendModels(capabilities)
                val recText = buildString {
                    append("\n📋 模型推荐:\n")
                    recommendations.forEach { rec ->
                        val icon = if (rec.isRecommended) "✅" else "⚪"
                        val status = if (rec.isRecommended) "推荐" else "可选"
                        append("  $icon ${rec.modelName} ($status)\n")
                        append("      原因: ${rec.reason}\n")
                    }
                }
                binding.tvModelRecommendation.text = recText
                binding.tvModelRecommendation.visibility = View.VISIBLE

                binding.btnDetectDevice.isEnabled = true
                binding.btnDetectDevice.text = "重新检测"
            }
        }
    }

    private fun setupLLMModelSelector() {
        val models = LocalLLMService.AVAILABLE_MODELS
        val modelDisplayList = models.mapIndexed { index, config ->
            val isDownloaded = llmService.isModelDownloaded(config.name)
            val sizeStr = formatSize(config.size)
            if (isDownloaded) {
                "✓ ${config.name} ($sizeStr, ${config.requiredRAM}GB RAM)"
            } else {
                "${config.name} ($sizeStr, ${config.requiredRAM}GB RAM)"
            }
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, modelDisplayList)
        binding.spinnerLlmModel.setAdapter(adapter)

        val firstDownloaded = models.indexOfFirst { llmService.isModelDownloaded(it.name) }
        selectedLlmModelIndex = if (firstDownloaded >= 0) firstDownloaded else 0
        binding.spinnerLlmModel.setText(modelDisplayList[selectedLlmModelIndex], false)

        updateLlmButtonState()

        binding.spinnerLlmModel.setOnItemClickListener { _, _, position, _ ->
            selectedLlmModelIndex = position
            updateLlmButtonState()
            updateModelStatus()
        }
    }

    private fun updateLlmButtonState() {
        val models = LocalLLMService.AVAILABLE_MODELS
        if (selectedLlmModelIndex < 0 || selectedLlmModelIndex >= models.size) return

        val config = models[selectedLlmModelIndex]
        val isDownloaded = llmService.isModelDownloaded(config.name)
        val isLoaded = llmService.isModelLoaded && llmService.getLoadedModelName() == config.name

        binding.btnDownloadLlmModel.text = when {
            isLoaded -> "已加载 ✓"
            isDownloaded -> "重新下载"
            else -> "下载模型"
        }

        binding.btnDownloadLlmModel.isEnabled = !isLoaded
    }

    private fun setupEmbeddingModelSelector() {
        val models = LocalEmbeddingService.AVAILABLE_MODELS
        val modelDisplayList = models.map { config ->
            val isDownloaded = embeddingService.isModelDownloaded(config.name)
            if (isDownloaded) {
                "✓ ${config.name} (${formatSize(config.modelSize)}, ${config.dimension}维)"
            } else {
                "${config.name} (${formatSize(config.modelSize)}, ${config.dimension}维)"
            }
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, modelDisplayList)
        binding.spinnerEmbeddingModel.setAdapter(adapter)

        val firstDownloaded = models.indexOfFirst { embeddingService.isModelDownloaded(it.name) }
        selectedEmbeddingModelIndex = if (firstDownloaded >= 0) firstDownloaded else 0
        binding.spinnerEmbeddingModel.setText(modelDisplayList[selectedEmbeddingModelIndex], false)

        updateEmbeddingButtonState()

        binding.spinnerEmbeddingModel.setOnItemClickListener { _, _, position, _ ->
            selectedEmbeddingModelIndex = position
            updateEmbeddingButtonState()
            updateModelStatus()
        }
    }

    private fun updateEmbeddingButtonState() {
        val models = LocalEmbeddingService.AVAILABLE_MODELS
        if (selectedEmbeddingModelIndex < 0 || selectedEmbeddingModelIndex >= models.size) return

        val config = models[selectedEmbeddingModelIndex]
        val isDownloaded = embeddingService.isModelDownloaded(config.name)
        val isLoaded = embeddingService.loaded

        binding.btnDownloadEmbeddingModel.text = when {
            isLoaded -> "已加载 ✓"
            isDownloaded -> "重新下载"
            else -> "下载模型"
        }

        binding.btnDownloadEmbeddingModel.isEnabled = !isLoaded
    }

    private fun setupOllamaConfig() {
        val savedUrl = prefs.getString("ollama_url", "http://10.0.2.2:11434") ?: "http://10.0.2.2:11434"
        binding.etOllamaUrl.setText(savedUrl)

        binding.btnSaveOllamaUrl.setOnClickListener {
            val url = binding.etOllamaUrl.text.toString().trim()
            prefs.edit().putString("ollama_url", url).apply()
            Toast.makeText(requireContext(), getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupDownloadButton() {
        binding.btnDownloadLlmModel.setOnClickListener {
            val models = LocalLLMService.AVAILABLE_MODELS
            if (selectedLlmModelIndex < 0 || selectedLlmModelIndex >= models.size) return@setOnClickListener

            val config = models[selectedLlmModelIndex]
            if (llmService.isModelLoaded && llmService.getLoadedModelName() == config.name) {
                Toast.makeText(requireContext(), "模型已在内存中", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            downloadLLMModel(config)
        }

        binding.btnDownloadEmbeddingModel.setOnClickListener {
            val models = LocalEmbeddingService.AVAILABLE_MODELS
            if (selectedEmbeddingModelIndex < 0 || selectedEmbeddingModelIndex >= models.size) return@setOnClickListener

            val config = models[selectedEmbeddingModelIndex]
            if (embeddingService.loaded) {
                Toast.makeText(requireContext(), "Embedding模型已在内存中", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            downloadEmbeddingModel(config)
        }

        binding.btnLoadModel.setOnClickListener {
            loadModels()
        }
    }

    private fun setupUnloadButton() {
        binding.btnUnloadModel.setOnClickListener {
            llmService.unloadModel()
            embeddingService.unloadModel()
            updateModelStatus()
            Toast.makeText(requireContext(), "模型已从内存卸载", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTestButton() {
        binding.btnTestModel.setOnClickListener {
            testModels()
        }
    }

    private fun setupBenchmarkButton() {
        binding.btnBenchmark.setOnClickListener {
            // Navigate to unified benchmark hub
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, com.hiringai.mobile.ui.benchmark.BenchmarkHubFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun testModels() {
        if (!llmService.isModelLoaded) {
            Toast.makeText(requireContext(), "请先加载LLM模型", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnTestModel.isEnabled = false
        binding.btnTestModel.text = "生成中..."
        binding.btnClearOutput.visibility = View.VISIBLE

        // Show generating status in status area
        val currentStatus = binding.tvStatusArea.text.toString()
        binding.tvStatusArea.text = currentStatus + "\n🤖 LLM 生成中...\n  输入: 请用一句话介绍你自己\n  输出: "

        lifecycleScope.launch {
            val testPrompt = "请用一句话介绍你自己"

            val result = llmService.generate(testPrompt)

            requireActivity().runOnUiThread {
                binding.btnTestModel.isEnabled = true
                binding.btnTestModel.text = "测试模型效果"

                if (result != null) {
                    // Show result in status area
                    val newStatus = currentStatus + "\n🤖 LLM 生成完成!\n  输入: 请用一句话介绍你自己\n  输出: $result\n\n  ✅ 点击\"清空输出\"清除结果"
                    binding.tvStatusArea.text = newStatus
                    binding.tvStatusArea.append("\n\n📋 完整输出:\n$result")
                } else {
                    val errorStatus = currentStatus + "\n⚠️ LLM 生成失败，请检查模型是否正确加载"
                    binding.tvStatusArea.text = errorStatus
                    Toast.makeText(requireContext(), "模型生成失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showTestResult(result: String) {
        // Show in status area instead of dialog
        updateStatusArea()
        binding.tvStatusArea.append("\n\n🤖 模型测试结果:\n输入: 请用一句话介绍你自己\n\n输出: $result")
    }

    private fun downloadLLMModel(config: LocalLLMService.ModelConfig) {
        binding.progressDownloadLlm.visibility = View.VISIBLE
        binding.btnDownloadLlmModel.isEnabled = false
        binding.btnDownloadLlmModel.text = "下载中..."

        lifecycleScope.launch {
            val success = llmService.downloadModel(config) { progress ->
                requireActivity().runOnUiThread {
                    binding.progressDownloadLlm.progress = progress
                    if (progress == 100) {
                        binding.progressDownloadLlm.visibility = View.GONE
                        updateModelStatus()
                        Toast.makeText(requireContext(), "下载完成: ${config.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            if (!success) {
                requireActivity().runOnUiThread {
                    binding.progressDownloadLlm.visibility = View.GONE
                    Toast.makeText(requireContext(), getString(R.string.settings_download_failed), Toast.LENGTH_SHORT).show()
                }
            }
            updateModelStatus()
        }
    }

    private fun downloadEmbeddingModel(config: LocalEmbeddingService.EmbeddingModelConfig) {
        binding.progressDownloadEmbedding.visibility = View.VISIBLE
        binding.btnDownloadEmbeddingModel.isEnabled = false
        binding.btnDownloadEmbeddingModel.text = "下载中..."

        lifecycleScope.launch {
            val success = embeddingService.downloadModel(config) { progress ->
                requireActivity().runOnUiThread {
                    binding.progressDownloadEmbedding.progress = progress
                    if (progress == 100) {
                        binding.progressDownloadEmbedding.visibility = View.GONE
                        updateModelStatus()
                        Toast.makeText(requireContext(), "下载完成: ${config.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            if (!success) {
                requireActivity().runOnUiThread {
                    binding.progressDownloadEmbedding.visibility = View.GONE
                    Toast.makeText(requireContext(), getString(R.string.settings_download_failed), Toast.LENGTH_SHORT).show()
                }
            }
            updateModelStatus()
        }
    }

    private fun loadModels() {
        binding.btnLoadModel.isEnabled = false
        binding.btnLoadModel.text = "加载中..."

        lifecycleScope.launch {
            var llmLoaded = false
            var embLoaded = false

            val llmModels = LocalLLMService.AVAILABLE_MODELS
            if (selectedLlmModelIndex in llmModels.indices) {
                val llmConfig = llmModels[selectedLlmModelIndex]
                if (llmService.isModelDownloaded(llmConfig.name)) {
                    llmLoaded = llmService.loadModel(llmConfig)
                }
            }

            val embModels = LocalEmbeddingService.AVAILABLE_MODELS
            if (selectedEmbeddingModelIndex in embModels.indices) {
                val embConfig = embModels[selectedEmbeddingModelIndex]
                if (embeddingService.isModelDownloaded(embConfig.name)) {
                    embLoaded = embeddingService.loadModel(embConfig)
                }
            }

            requireActivity().runOnUiThread {
                updateModelStatus()

                val msg = when {
                    llmLoaded && embLoaded -> getString(R.string.settings_models_loaded)
                    llmLoaded -> getString(R.string.settings_llm_loaded_only)
                    embLoaded -> getString(R.string.settings_embedding_loaded_only)
                    else -> getString(R.string.settings_no_model_loaded)
                }
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateModelStatus() {
        setupLLMModelSelector()
        setupEmbeddingModelSelector()
        updateStatusArea()

        val llmDownloaded = LocalLLMService.AVAILABLE_MODELS.any { llmService.isModelDownloaded(it.name) }
        val embDownloaded = LocalEmbeddingService.AVAILABLE_MODELS.any { embeddingService.isModelDownloaded(it.name) }
        val llmLoaded = llmService.isModelLoaded
        val embLoaded = embeddingService.loaded

        val onnxAvailable = com.hiringai.mobile.SafeNativeLoader.isAvailable("onnxruntime")
        val llamaAvailable = com.hiringai.mobile.SafeNativeLoader.isAvailable("llama-android")
        val hasCrashMarker = com.hiringai.mobile.SafeNativeLoader.hasCrashMarker()

        binding.tvLlmStatus.text = buildString {
            if (!llamaAvailable && hasCrashMarker) {
                append("⚠ LLM已禁用 (native crash detected)")
            } else if (!com.hiringai.mobile.SafeNativeLoader.isDeviceCompatible) {
                append("⚠ LLM已禁用 (设备不兼容)")
            } else if (llmLoaded) {
                append("✓ 已加载: ${llmService.getLoadedModelName()}")
            } else if (llmDownloaded) {
                val downloaded = LocalLLMService.AVAILABLE_MODELS.filter { llmService.isModelDownloaded(it.name) }
                    .joinToString(", ") { it.name }
                append("✓ 已下载: $downloaded")
            } else {
                append("未下载任何模型")
            }
        }

        binding.tvEmbeddingStatus.text = buildString {
            if (!onnxAvailable && hasCrashMarker) {
                append("⚠ Embedding已禁用 (native crash detected)")
            } else if (!com.hiringai.mobile.SafeNativeLoader.isDeviceCompatible) {
                append("⚠ Embedding已禁用 (设备不兼容)")
            } else if (embLoaded) {
                append("✓ 已加载: all-MiniLM-L6-v2")
            } else if (embDownloaded) {
                append("✓ 已下载: all-MiniLM-L6-v2")
            } else {
                append("未下载")
            }
        }

        // Show buttons based on model status
        val canLoadML = com.hiringai.mobile.SafeNativeLoader.isDeviceCompatible &&
                        !hasCrashMarker && (llmDownloaded || embDownloaded)
        binding.btnLoadModel.visibility = if (canLoadML) View.VISIBLE else View.GONE
        if (canLoadML) {
            binding.btnLoadModel.text = getString(R.string.settings_load_models)
            binding.btnLoadModel.isEnabled = true
        }

        // Show unload button if any model is loaded
        val anyLoaded = llmLoaded || embLoaded
        binding.btnUnloadModel.visibility = if (anyLoaded) View.VISIBLE else View.GONE

        // Show test button only if LLM is loaded
        binding.btnTestModel.visibility = if (llmLoaded) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0)
            else -> "$bytes B"
        }
    }
}