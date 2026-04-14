package com.hiringai.mobile.ui.candidates

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
import com.hiringai.mobile.data.local.entity.CandidateEntity
import com.hiringai.mobile.databinding.FragmentCandidatesBinding
import kotlinx.coroutines.launch

class CandidatesFragment : Fragment() {

    private var _binding: FragmentCandidatesBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var adapter: CandidateAdapter

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

        db = AppDatabase.getInstance(requireContext())

        setupRecyclerView()
        setupFab()
        loadCandidates()
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

    private fun loadCandidates() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val candidates = db.candidateDao().getAll()

            requireActivity().runOnUiThread {
                binding.progressBar.visibility = View.GONE

                if (candidates.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.rvCandidates.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.rvCandidates.visibility = View.VISIBLE
                    adapter.submitList(candidates)
                }
            }
        }
    }

    private fun showAddCandidateDialog() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        val etName = EditText(requireContext()).apply {
            hint = "姓名 (必填)"
        }
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
            hint = "简历简介 (选填)"
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

                addCandidate(name, email, phone, resume)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addCandidate(name: String, email: String, phone: String, resume: String) {
        lifecycleScope.launch {
            db.candidateDao().insert(
                CandidateEntity(
                    name = name,
                    email = email,
                    phone = phone,
                    resume = resume
                )
            )

            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "候选人已添加", Toast.LENGTH_SHORT).show()
                loadCandidates()
            }
        }
    }

    private fun showCandidateDetails(candidate: CandidateEntity) {
        val details = buildString {
            append("姓名: ${candidate.name}\n")
            append("邮箱: ${candidate.email.ifEmpty { "无" }}\n")
            append("电话: ${candidate.phone.ifEmpty { "无" }}\n")
            append("简历: ${candidate.resume.ifEmpty { "无" }}\n")
        }

        AlertDialog.Builder(requireContext())
            .setTitle(candidate.name)
            .setMessage(details)
            .setPositiveButton("确定", null)
            .setNegativeButton("删除") { _, _ ->
                deleteCandidate(candidate)
            }
            .show()
    }

    private fun deleteCandidate(candidate: CandidateEntity) {
        lifecycleScope.launch {
            db.candidateDao().delete(candidate)

            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "候选人已删除", Toast.LENGTH_SHORT).show()
                loadCandidates()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}