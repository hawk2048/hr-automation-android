package com.hiringai.mobile.ui.jobs

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hiringai.mobile.R
import com.hiringai.mobile.data.local.AppDatabase
import com.hiringai.mobile.data.local.entity.JobEntity
import com.hiringai.mobile.databinding.FragmentJobsBinding
import kotlinx.coroutines.launch

class JobsFragment : Fragment() {

    private var _binding: FragmentJobsBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
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

        db = AppDatabase.getInstance(requireContext())

        setupRecyclerView()
        setupFab()
        loadJobs()
    }

    private fun setupRecyclerView() {
        adapter = JobAdapter { job ->
            // Handle item click (e.g., show details or edit)
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

    private fun loadJobs() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val jobs = db.jobDao().getAll()

            requireActivity().runOnUiThread {
                binding.progressBar.visibility = View.GONE

                if (jobs.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.rvJobs.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.rvJobs.visibility = View.VISIBLE
                    adapter.submitList(jobs)
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

                addJob(title, requirements)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addJob(title: String, requirements: String) {
        lifecycleScope.launch {
            db.jobDao().insert(JobEntity(title = title, requirements = requirements))

            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "职位已添加", Toast.LENGTH_SHORT).show()
                loadJobs()
            }
        }
    }

    private fun showJobDetails(job: JobEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(job.title)
            .setMessage("要求: ${job.requirements}\n\n状态: ${job.status}")
            .setPositiveButton("确定", null)
            .setNegativeButton("删除") { _, _ ->
                deleteJob(job)
            }
            .show()
    }

    private fun deleteJob(job: JobEntity) {
        lifecycleScope.launch {
            db.jobDao().delete(job)

            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "职位已删除", Toast.LENGTH_SHORT).show()
                loadJobs()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}