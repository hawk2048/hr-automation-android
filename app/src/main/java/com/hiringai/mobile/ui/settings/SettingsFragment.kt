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
import com.hiringai.mobile.ml.LocalEmbeddingService
import com.hiringai.mobile.ml.LocalLLMService
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var llmService: LocalLLMService
    private lateinit var embeddingService: LocalEmbeddingService
    private lateinit var prefs: SharedPreferences

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

        llmService = LocalLLMService(requireContext())
        embeddingService = LocalEmbeddingService(requireContext())
        prefs = requireContext().getSharedPreferences("hra_settings", 0)

        setupInferenceMode()
        setupLLMModelSelector()
        setupEmbeddingModelSelector()
        setupOllamaConfig()
        setupDownloadButton()
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

    private fun setupLLMModelSelector() {
        val modelNames = LocalLLMService.AVAILABLE_MODELS.map { "${it.name} (${formatSize(it.size)}, ${it.requiredRAM}GB RAM)" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, modelNames)
        binding.spinnerLlmModel.setAdapter(adapter)
        binding.spinnerLlmModel.setText(modelNames.firstOrNull() ?: "", false)

        // Auto-select first non-downloaded model, or first if all downloaded
        val firstNotDownloaded = LocalLLMService.AVAILABLE_MODELS.indexOfFirst { !llmService.isModelDownloaded(it.name) }
        if (firstNotDownloaded >= 0) {
            binding.spinnerLlmModel.setText(modelNames[firstNotDownloaded], false)
        }
    }

    private fun setupEmbeddingModelSelector() {
        val modelNames = LocalEmbeddingService.AVAILABLE_MODELS.map { "${it.name} (${formatSize(it.modelSize)})" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, modelNames)
        binding.spinnerEmbeddingModel.setAdapter(adapter)
        binding.spinnerEmbeddingModel.setText(modelNames.firstOrNull() ?: "", false)
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
            val position = binding.spinnerLlmModel.listSelection
            val models = LocalLLMService.AVAILABLE_MODELS
            if (position < 0 || position >= models.size) {
                // Fallback: try to match by text
                val selectedText = binding.spinnerLlmModel.text.toString()
                val model = models.firstOrNull { selectedText.startsWith(it.name) } ?: return@setOnClickListener
                downloadLLMModel(model)
            } else {
                downloadLLMModel(models[position])
            }
        }

        binding.btnDownloadEmbeddingModel.setOnClickListener {
            val selectedText = binding.spinnerEmbeddingModel.text.toString()
            val model = LocalEmbeddingService.AVAILABLE_MODELS.firstOrNull { selectedText.startsWith(it.name) } ?: return@setOnClickListener
            downloadEmbeddingModel(model)
        }

        binding.btnLoadModel.setOnClickListener {
            loadModels()
        }
    }

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
                    Toast.makeText(requireContext(), getString(R.string.settings_download_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun downloadEmbeddingModel(config: LocalEmbeddingService.EmbeddingModelConfig) {
        binding.progressDownloadEmbedding.visibility = View.VISIBLE
        binding.btnDownloadEmbeddingModel.isEnabled = false

        lifecycleScope.launch {
            val success = embeddingService.downloadModel(config) { progress ->
                requireActivity().runOnUiThread {
                    binding.progressDownloadEmbedding.progress = progress
                    if (progress == 100) {
                        binding.progressDownloadEmbedding.visibility = View.GONE
                        binding.btnDownloadEmbeddingModel.isEnabled = true
                        updateModelStatus()
                    }
                }
            }
            if (!success) {
                requireActivity().runOnUiThread {
                    binding.progressDownloadEmbedding.visibility = View.GONE
                    binding.btnDownloadEmbeddingModel.isEnabled = true
                    Toast.makeText(requireContext(), getString(R.string.settings_download_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadModels() {
        binding.btnLoadModel.isEnabled = false

        lifecycleScope.launch {
            var llmLoaded = false
            var embLoaded = false

            // Load LLM
            val llmConfig = LocalLLMService.AVAILABLE_MODELS.firstOrNull { llmService.isModelDownloaded(it.name) }
            if (llmConfig != null) {
                llmLoaded = llmService.loadModel(llmConfig)
            }

            // Load Embedding
            val embConfig = LocalEmbeddingService.AVAILABLE_MODELS.firstOrNull { embeddingService.isModelDownloaded(it.name) }
            if (embConfig != null) {
                embLoaded = embeddingService.loadModel(embConfig)
            }

            requireActivity().runOnUiThread {
                binding.btnLoadModel.isEnabled = true
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
        val llmDownloaded = LocalLLMService.AVAILABLE_MODELS.any { llmService.isModelDownloaded(it.name) }
        val embDownloaded = LocalEmbeddingService.AVAILABLE_MODELS.any { embeddingService.isModelDownloaded(it.name) }
        val llmLoaded = llmService.isModelLoaded
        val embLoaded = embeddingService.loaded
        
        // Check SafeNativeLoader status for more accurate reporting
        val onnxAvailable = com.hiringai.mobile.SafeNativeLoader.isAvailable("onnxruntime")
        val llamaAvailable = com.hiringai.mobile.SafeNativeLoader.isAvailable("llama-android")
        val hasCrashMarker = com.hiringai.mobile.SafeNativeLoader.hasCrashMarker()

        binding.tvLlmStatus.text = when {
            !llamaAvailable && hasCrashMarker -> "LLM disabled (native crash detected) — use Ollama"
            !com.hiringai.mobile.SafeNativeLoader.isDeviceCompatible -> "LLM disabled (device incompatible)"
            llmLoaded -> getString(R.string.settings_llm_loaded, llmService.getLoadedModelName())
            llmDownloaded -> getString(R.string.settings_llm_downloaded)
            else -> getString(R.string.settings_model_not_downloaded)
        }

        binding.tvEmbeddingStatus.text = when {
            !onnxAvailable && hasCrashMarker -> "Embedding disabled (native crash detected)"
            !com.hiringai.mobile.SafeNativeLoader.isDeviceCompatible -> "Embedding disabled (device incompatible)"
            embLoaded -> getString(R.string.settings_embedding_loaded)
            embDownloaded -> getString(R.string.settings_embedding_downloaded)
            else -> getString(R.string.settings_model_not_downloaded)
        }

        // Show load button only if native libs are available and models are downloaded
        val canLoadML = com.hiringai.mobile.SafeNativeLoader.isDeviceCompatible && 
                        !hasCrashMarker && (llmDownloaded || embDownloaded)
        binding.btnLoadModel.visibility = if (canLoadML) View.VISIBLE else View.GONE
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
