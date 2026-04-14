package com.hiringai.mobile.ui.matches

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hiringai.mobile.R
import com.hiringai.mobile.data.local.AppDatabase
import com.hiringai.mobile.data.local.entity.CandidateEntity
import com.hiringai.mobile.data.local.entity.JobEntity
import com.hiringai.mobile.data.local.entity.MatchEntity
import com.hiringai.mobile.databinding.FragmentMatchesBinding
import com.hiringai.mobile.ml.LocalEmbeddingService
import com.hiringai.mobile.ml.LocalLLMService
import kotlinx.coroutines.launch

class MatchesFragment : Fragment() {

    private var _binding: FragmentMatchesBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var llmService: LocalLLMService
    private lateinit var embeddingService: LocalEmbeddingService
    private lateinit var adapter: MatchAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMatchesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getInstance(requireContext())
        llmService = LocalLLMService.getInstance(requireContext())
        embeddingService = LocalEmbeddingService.getInstance(requireContext())

        setupRecyclerView()
        setupButtons()
        loadMatches()
    }

    private fun setupRecyclerView() {
        adapter = MatchAdapter { match ->
            showMatchDetails(match)
        }
        binding.rvMatches.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMatches.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnRunMatch.setOnClickListener {
            runMatching()
        }

        binding.btnScreen.setOnClickListener {
            runScreening()
        }
    }

    private fun loadMatches() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val matches = db.matchDao().getAll()

            requireActivity().runOnUiThread {
                binding.progressBar.visibility = View.GONE
                adapter.submitList(matches)
            }
        }
    }

    private fun runMatching() {
        if (!embeddingService.loaded) {
            Toast.makeText(requireContext(), "请先在设置中加载Embedding模型", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnRunMatch.isEnabled = false
        binding.btnRunMatch.text = "匹配中..."

        lifecycleScope.launch {
            try {
                // Get all jobs and candidates
                val jobs = db.jobDao().getAll()
                val candidates = db.candidateDao().getAll()

                if (jobs.isEmpty() || candidates.isEmpty()) {
                    requireActivity().runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        binding.btnRunMatch.isEnabled = true
                        binding.btnRunMatch.text = getString(R.string.btn_run_match)
                        Toast.makeText(requireContext(), "请先添加职位和候选人", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Clear existing matches
                val existingMatches = db.matchDao().getAll()
                existingMatches.forEach { db.matchDao().delete(it) }

                var matchCount = 0

                // Calculate similarity for each job-candidate pair
                for (job in jobs) {
                    for (candidate in candidates) {
                        val score = calculateSimilarity(job, candidate)
                        if (score > 0.3f) { // Only save matches with >30% similarity
                            val match = MatchEntity(
                                jobId = job.id,
                                candidateId = candidate.id,
                                score = score,
                                status = "pending"
                            )
                            db.matchDao().insert(match)
                            matchCount++
                        }
                    }
                }

                requireActivity().runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRunMatch.isEnabled = true
                    binding.btnRunMatch.text = getString(R.string.btn_run_match)
                    Toast.makeText(requireContext(), "匹配完成! 生成了 $matchCount 个匹配结果", Toast.LENGTH_SHORT).show()
                    loadMatches()
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.btnRunMatch.isEnabled = true
                    binding.btnRunMatch.text = getString(R.string.btn_run_match)
                    Toast.makeText(requireContext(), "匹配失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun calculateSimilarity(job: JobEntity, candidate: CandidateEntity): Float {
        // Combine job requirements and candidate resume for comparison
        val jobText = "${job.title} ${job.requirements}"
        val candidateText = "${candidate.name} ${candidate.resume}"

        // Get embeddings
        val jobEmbedding = embeddingService.encode(jobText) ?: return 0f
        val candidateEmbedding = embeddingService.encode(candidateText) ?: return 0f

        // Calculate cosine similarity
        return embeddingService.cosineSimilarity(jobEmbedding, candidateEmbedding)
    }

    private fun runScreening() {
        if (!llmService.isModelLoaded) {
            Toast.makeText(requireContext(), "请先在设置中加载LLM模型", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnScreen.isEnabled = false
        binding.btnScreen.text = "初筛中..."

        lifecycleScope.launch {
            try {
                val matches = db.matchDao().getAll().filter { it.status == "pending" }

                if (matches.isEmpty()) {
                    requireActivity().runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        binding.btnScreen.isEnabled = true
                        binding.btnScreen.text = getString(R.string.btn_screen)
                        Toast.makeText(requireContext(), "没有待筛选的匹配", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                for (match in matches) {
                    val job = db.jobDao().getById(match.jobId)
                    val candidate = db.candidateDao().getById(match.candidateId)

                    if (job != null && candidate != null) {
                        // Generate AI evaluation using LLM
                        val evaluation = generateEvaluation(job, candidate, match.score)
                        val updatedMatch = match.copy(
                            evaluation = evaluation,
                            status = "screened"
                        )
                        db.matchDao().update(updatedMatch)
                    }
                }

                requireActivity().runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.btnScreen.isEnabled = true
                    binding.btnScreen.text = getString(R.string.btn_screen)
                    Toast.makeText(requireContext(), "初筛完成! 已生成 ${matches.size} 个评估", Toast.LENGTH_SHORT).show()
                    loadMatches()
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.btnScreen.isEnabled = true
                    binding.btnScreen.text = getString(R.string.btn_screen)
                    Toast.makeText(requireContext(), "初筛失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun generateEvaluation(job: JobEntity, candidate: CandidateEntity, score: Float): String {
        val prompt = buildString {
            append("请分析这位候选人是否适合该职位。\n\n")
            append("职位: ${job.title}\n")
            append("要求: ${job.requirements}\n\n")
            append("候选人简历: ${candidate.resume}\n\n")
            append("基于相似度得分 ${(score * 100).toInt()}%，")
            append("请给出:\n")
            append("1. 候选人优势 (2-3点)\n")
            append("2. 需要关注的不足 (2-3点)\n")
            append("3. 综合推荐建议 (简短一句)\n")
        }

        val result = llmService.generate(prompt, maxTokens = 300) ?: "模型生成失败"
        return result
    }

    private fun showMatchDetails(match: MatchEntity) {
        lifecycleScope.launch {
            val job = db.jobDao().getById(match.jobId)
            val candidate = db.candidateDao().getById(match.candidateId)

            requireActivity().runOnUiThread {
                val details = buildString {
                    append("匹配度: ${(match.score * 100).toInt()}%\n")
                    append("状态: ${match.status}\n\n")
                    append("职位: ${job?.title ?: "未知"}\n")
                    append("要求: ${job?.requirements ?: "未知"}\n\n")
                    append("候选人: ${candidate?.name ?: "未知"}\n")
                    append("简历: ${candidate?.resume ?: "未知"}\n\n")
                    if (match.evaluation.isNotEmpty()) {
                        append("AI评估:\n${match.evaluation}")
                    }
                }

                AlertDialog.Builder(requireContext())
                    .setTitle("匹配详情")
                    .setMessage(details)
                    .setPositiveButton("确定", null)
                    .setNegativeButton(if (match.status == "pending") "删除" else null) { _, _ ->
                        if (match.status == "pending") {
                            deleteMatch(match)
                        }
                    }
                    .show()
            }
        }
    }

    private fun deleteMatch(match: MatchEntity) {
        lifecycleScope.launch {
            db.matchDao().delete(match)
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "匹配已删除", Toast.LENGTH_SHORT).show()
                loadMatches()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}