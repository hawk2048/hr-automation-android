package com.hiringai.mobile.ui.jobs

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.hiringai.mobile.R
import com.hiringai.mobile.data.local.AppDatabase
import com.hiringai.mobile.data.local.entity.JobEntity
import com.hiringai.mobile.data.repository.JobRepository
import com.hiringai.mobile.databinding.FragmentJobsBinding
import com.hiringai.mobile.ml.LocalLLMService
import kotlinx.coroutines.launch

class JobsFragment : Fragment() {

    private var _binding: FragmentJobsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: JobsViewModel by viewModels {
        val db = AppDatabase.getInstance(requireContext())
        val repository = JobRepository(db.jobDao())
        val llmService = LocalLLMService.getInstance(requireContext())
        JobsViewModel.Factory(repository, llmService)
    }

    private lateinit var adapter: JobAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentJobsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFab()
        observeUiState()
    }

    private fun setupRecyclerView() {
        adapter = JobAdapter { job ->
            showJobDetails(job)
        }
        binding.rvJobs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvJobs.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            showAddJobDialog()
        }
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
                    if (state.jobs.isEmpty() && !state.isLoading) {
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.rvJobs.visibility = View.GONE
                    } else {
                        binding.tvEmpty.visibility = View.GONE
                        binding.rvJobs.visibility = View.VISIBLE
                        adapter.submitList(state.jobs)
                    }
                }
            }
        }
    }

    private fun showAddJobDialog() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        val etTitle = EditText(requireContext()).apply {
            hint = "职位名称 (如: Android开发工程师)"
        }
        layout.addView(etTitle)

        val etRequirements = EditText(requireContext()).apply {
            hint = "职位要求 (如: 3年经验, 熟悉Kotlin)"
            minLines = 3
        }
        layout.addView(etRequirements)

        AlertDialog.Builder(requireContext())
            .setTitle("添加职位")
            .setView(layout)
            .setPositiveButton("添加") { _, _ ->
                val title = etTitle.text.toString().trim()
                val requirements = etRequirements.text.toString().trim()

                if (title.isEmpty()) {
                    Toast.makeText(requireContext(), "请输入职位名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                viewModel.addJob(title, requirements)
                Toast.makeText(requireContext(), "职位已添加", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showJobDetails(job: JobEntity) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_job_details, null)

        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tv_detail_title)
        val tvRequirements = dialogView.findViewById<android.widget.TextView>(R.id.tv_detail_requirements)
        val tvStatus = dialogView.findViewById<android.widget.TextView>(R.id.tv_detail_status)
        val tvProfile = dialogView.findViewById<android.widget.TextView>(R.id.tv_detail_profile)
        val btnGenerateProfile = dialogView.findViewById<Button>(R.id.btn_generate_profile)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_profile)

        tvTitle.text = job.title
        tvRequirements.text = "要求: ${job.requirements}"
        tvStatus.text = "状态: ${when (job.status) {
            "active" -> "招聘中"
            "closed" -> "已关闭"
            else -> job.status
        }}"

        // 显示已有画像
        if (job.profile.isNotEmpty()) {
            tvProfile.visibility = View.VISIBLE
            tvProfile.text = "职位画像:\n${job.profile}"
        } else {
            tvProfile.visibility = View.GONE
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("职位详情")
            .setView(dialogView)
            .setPositiveButton("确定", null)
            .setNegativeButton("删除") { _, _ ->
                viewModel.deleteJob(job)
                Toast.makeText(requireContext(), "职位已删除", Toast.LENGTH_SHORT).show()
            }
            .create()

        btnGenerateProfile.setOnClickListener {
            btnGenerateProfile.isEnabled = false
            progressBar.visibility = View.VISIBLE

            viewModel.generateProfile(job) { profile ->
                progressBar.visibility = View.GONE
                btnGenerateProfile.isEnabled = true
                tvProfile.visibility = View.VISIBLE
                tvProfile.text = "职位画像:\n$profile"
                Toast.makeText(requireContext(), "画像生成成功", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}