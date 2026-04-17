package com.hiringai.mobile.ui.candidates

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.hiringai.mobile.R
import com.hiringai.mobile.data.local.AppDatabase
import com.hiringai.mobile.data.local.entity.CandidateEntity
import com.hiringai.mobile.data.repository.CandidateRepository
import com.hiringai.mobile.data.repository.ApplicationRepository
import com.hiringai.mobile.databinding.FragmentCandidatesBinding
import com.hiringai.mobile.ml.LocalLLMService
import kotlinx.coroutines.launch

class CandidatesFragment : Fragment() {

    private var _binding: FragmentCandidatesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CandidatesViewModel by viewModels {
        val db = AppDatabase.getInstance(requireContext())
        val candidateRepository = CandidateRepository(db.candidateDao())
        val applicationRepository = ApplicationRepository(db.applicationDao())
        val llmService = LocalLLMService.getInstance(requireContext())
        CandidatesViewModel.Factory(candidateRepository, applicationRepository, llmService, requireContext())
    }

    private lateinit var adapter: CandidateAdapter

    private val pdfPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.importFromPdf(uri) { candidate ->
                    if (candidate != null) {
                        Toast.makeText(requireContext(), "PDF导入成功!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "PDF导入失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCandidatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFab()
        setupBatchImport()
        observeUiState()
    }

    private fun setupRecyclerView() {
        adapter = CandidateAdapter { candidate ->
            showCandidateDetails(candidate)
        }
        binding.rvCandidates.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCandidates.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            showAddCandidateDialog()
        }
    }

    private fun setupBatchImport() {
        binding.btnBatchImport.setOnClickListener {
            if (!viewModel.isLlmLoaded()) {
                Toast.makeText(requireContext(), "请先在设置中加载LLM模型", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            batchPickerLauncher.launch(intent)
        }
    }

    private val batchPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val clipData = result.data?.clipData
            val uris = mutableListOf<Uri>()

            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    uris.add(clipData.getItemAt(i).uri)
                }
            } else {
                result.data?.data?.let { uris.add(it) }
            }

            if (uris.isNotEmpty()) {
                startBatchImport(uris)
            }
        }
    }

    private fun startBatchImport(uris: List<Uri>) {
        binding.cardImportProgress.visibility = View.VISIBLE
        binding.btnBatchImport.isEnabled = false

        viewModel.batchImport(uris,
            onProgress = { progress ->
                binding.tvImportStatus.text = "正在处理: ${progress.processed + 1}/${progress.total}"
                binding.progressImport.progress = ((progress.processed * 100) / progress.total)
                binding.tvImportDetail.text = "处理中: ${progress.currentFile}"
            },
            onComplete = { success, failed ->
                binding.tvImportStatus.text = "导入完成!"
                binding.progressImport.progress = 100
                binding.tvImportDetail.text = "成功: $success, 失败: $failed"
                binding.btnBatchImport.isEnabled = true

                Toast.makeText(requireContext(), "批量导入完成! 成功: $success, 失败: $failed", Toast.LENGTH_LONG).show()

                binding.root.postDelayed({
                    binding.cardImportProgress.visibility = View.GONE
                }, 3000)
            }
        )
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Loading state
                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                    // Error handling
                    state.error?.let { error ->
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                    }

                    // Data display
                    if (state.candidates.isEmpty() && !state.isLoading) {
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.rvCandidates.visibility = View.GONE
                    } else {
                        binding.tvEmpty.visibility = View.GONE
                        binding.rvCandidates.visibility = View.VISIBLE
                        adapter.submitList(state.candidates)
                    }
                }
            }
        }
    }

    private fun showAddCandidateDialog() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        val etName = EditText(requireContext()).apply { hint = "姓名 (必填)" }
        layout.addView(etName)

        val etEmail = EditText(requireContext()).apply {
            hint = "邮箱 (选填)"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        layout.addView(etEmail)

        val etPhone = EditText(requireContext()).apply {
            hint = "电话 (选填)"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        layout.addView(etPhone)

        val etResume = EditText(requireContext()).apply {
            hint = "简历内容"
            minLines = 3
        }
        layout.addView(etResume)

        AlertDialog.Builder(requireContext())
            .setTitle("添加候选人")
            .setView(layout)
            .setPositiveButton("添加") { _, _ ->
                val name = etName.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                val resume = etResume.text.toString().trim()

                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "请输入姓名", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                viewModel.addCandidate(name, email, phone, resume)
                Toast.makeText(requireContext(), "候选人已添加", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCandidateDetails(candidate: CandidateEntity) {
        val hasProfile = candidate.profile.isNotEmpty()

        AlertDialog.Builder(requireContext())
            .setTitle(candidate.name)
            .setMessage("邮箱: ${candidate.email.ifEmpty { "无" }}\n电话: ${candidate.phone.ifEmpty { "无" }}\n简历: ${candidate.resume.take(100)}...")
            .setPositiveButton(if (!hasProfile) "生成画像" else "查看画像") { _, _ ->
                if (!hasProfile) {
                    viewModel.generateProfile(candidate) { profile ->
                        Toast.makeText(requireContext(), "画像生成完成!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("投递简历") { _, _ ->
                // 显示职位选择对话框，需要获取职位列表
            }
            .setNeutralButton("删除") { _, _ ->
                viewModel.deleteCandidate(candidate)
                Toast.makeText(requireContext(), "候选人已删除", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}