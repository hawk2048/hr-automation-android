package com.hiringai.mobile.ui.matching

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hiringai.mobile.R
import com.hiringai.mobile.data.local.AppDatabase
import com.hiringai.mobile.data.local.entity.ApplicationEntity
import com.hiringai.mobile.data.local.entity.CandidateEntity
import com.hiringai.mobile.data.local.entity.JobEntity
import com.hiringai.mobile.databinding.FragmentCandidateToJobBinding
import com.hiringai.mobile.ml.LocalEmbeddingService
import kotlinx.coroutines.launch

class CandidateToJobFragment : Fragment() {

    private var _binding: FragmentCandidateToJobBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var embeddingService: LocalEmbeddingService
    private lateinit var adapter: MatchResultAdapter

    private var candidates: List<CandidateEntity> = emptyList()
    private var jobs: List<JobEntity> = emptyList()
    private var selectedCandidate: CandidateEntity? = null
    private var currentFilter: FilterOptions = FilterOptions()
    private var allResults: List<MatchResult> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCandidateToJobBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getInstance(requireContext())
        embeddingService = LocalEmbeddingService.getInstance(requireContext())

        setupRecyclerView()
        setupFilterButton()
        loadData()
    }

    private fun setupRecyclerView() {
        adapter = MatchResultAdapter(
            onViewDetailsClick = { result ->
                // 查看职位详情
                Toast.makeText(requireContext(), "查看职位: ${result.title}", Toast.LENGTH_SHORT).show()
            },
            onApplyClick = { result ->
                // 创建申请/沟通
                Toast.makeText(requireContext(), "投递职位: ${result.title}", Toast.LENGTH_SHORT).show()
                createApplication(result)
            },
            onFavoriteClick = { result ->
                // 收藏
                Toast.makeText(requireContext(), if (adapter.isFavorite(result.id)) "已收藏" else "取消收藏", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvResults.adapter = adapter
    }

    private fun createApplication(result: MatchResult) {
        val candidate = selectedCandidate ?: return
        lifecycleScope.launch {
            val application = ApplicationEntity(
                jobId = result.jobId,
                candidateId = candidate.id,
                status = "pending"
            )
            db.applicationDao().insert(application)
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "已投递简历", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadData() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            candidates = db.candidateDao().getAll()
            jobs = db.jobDao().getAll()

            requireActivity().runOnUiThread {
                binding.progressBar.visibility = View.GONE
                setupSpinner()
            }
        }
    }

    private fun setupSpinner() {
        if (candidates.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_candidates, Toast.LENGTH_SHORT).show()
            return
        }

        val candidateNames = candidates.map { it.name }
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            candidateNames
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        binding.spinnerCandidate.adapter = spinnerAdapter
        binding.spinnerCandidate.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCandidate = candidates[position]
                runMatching()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedCandidate = null
                adapter.submitList(emptyList())
                updateResultsCount(0)
            }
        }
    }

    private fun runMatching() {
        val candidate = selectedCandidate ?: return

        if (!embeddingService.loaded) {
            Toast.makeText(requireContext(), R.string.model_not_loaded, Toast.LENGTH_SHORT).show()
            return
        }

        if (jobs.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_jobs, Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val results = mutableListOf<MatchResult>()

            for (job in jobs) {
                val score = calculateSimilarity(candidate, job)
                if (score > 0.3f) { // Only show results with >30% match
                    // Calculate score breakdown
                    val scoreBreakdown = calculateScoreBreakdown(candidate, job, score)

                    // Extract job info from requirements
                    val jobInfo = parseJobRequirements(job.requirements)

                    results.add(
                        MatchResult(
                            id = job.id,
                            score = scoreBreakdown.overallScore / 100f,
                            title = job.title,
                            info = job.requirements.take(50),
                            description = job.requirements,
                            scoreBreakdown = scoreBreakdown,
                            type = MatchType.JOB,
                            company = jobInfo["company"] ?: "",
                            salaryRange = jobInfo["salary"] ?: "",
                            location = jobInfo["location"] ?: "",
                            experienceRequirement = jobInfo["experience"] ?: "",
                            educationRequirement = jobInfo["education"] ?: "",
                            jobAttractions = jobInfo["attractions"] ?: "",
                            jobId = job.id,
                            candidateId = candidate.id
                        )
                    )
                }
            }

            // Sort by score descending
            results.sortByDescending { it.score }

            // Store all results and apply filter if active
            allResults = results

            requireActivity().runOnUiThread {
                binding.progressBar.visibility = View.GONE

                val displayResults = if (currentFilter.hasActiveFilters()) {
                    results.filter { currentFilter.matches(it) }
                } else {
                    results
                }

                adapter.submitList(displayResults)
                updateResultsCount(displayResults.size)

                if (results.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.rvResults.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.rvResults.visibility = View.VISIBLE
                }
            }
        }
    }

    private suspend fun calculateSimilarity(candidate: CandidateEntity, job: JobEntity): Float {
        val jobText = "${job.title} ${job.requirements}"
        val candidateText = "${candidate.name} ${candidate.resume}"

        val jobEmbedding = embeddingService.encode(jobText) ?: return 0f
        val candidateEmbedding = embeddingService.encode(candidateText) ?: return 0f

        return embeddingService.cosineSimilarity(jobEmbedding, candidateEmbedding)
    }

    /**
     * Calculate detailed score breakdown based on job requirements and candidate profile
     */
    private fun calculateScoreBreakdown(candidate: CandidateEntity, job: JobEntity, overallScore: Float): MatchScoreBreakdown {
        val jobRequirements = job.requirements.lowercase()
        val candidateResume = candidate.resume.lowercase()
        val jobTitle = job.title.lowercase()

        // Skill matching - keywords from job requirements
        val skillKeywords = listOf(
            "python", "java", "kotlin", "swift", "go", "rust", "c++", "javascript", "typescript",
            "react", "vue", "angular", "node", "spring", "django", "flask",
            "kubernetes", "docker", "aws", "azure", "gcp", "linux",
            "mysql", "redis", "mongodb", "postgresql", "elasticsearch",
            "machine learning", "deep learning", "ai", "nlp", "tensorflow", "pytorch",
            "sql", "nosql", "api", "rest", "graphql", "microservices",
            "agile", "scrum", "ci/cd", "devops", "git"
        )

        var skillMatches = 0
        for (keyword in skillKeywords) {
            if (candidateResume.contains(keyword) || candidate.profile.lowercase().contains(keyword)) {
                skillMatches++
            }
        }
        val skillScore = (skillKeywords.size.coerceAtMost(skillMatches) * 100f / skillKeywords.size.coerceAtLeast(1)).coerceIn(0f, 100f)

        // Experience matching - years of experience
        val experienceKeywords = listOf("年", "years", "经验", "experience", "熟练", "精通", "资深", "高级")
        var experienceMatches = 0
        for (keyword in experienceKeywords) {
            if (jobRequirements.contains(keyword) || jobTitle.contains(keyword)) {
                experienceMatches++
            }
        }
        // Calculate experience score based on keyword presence and candidate profile
        val experienceScore = when {
            candidateResume.contains("5年") || candidateResume.contains("5+") -> 90f
            candidateResume.contains("3年") || candidateResume.contains("3+") -> 75f
            candidateResume.contains("1年") || candidateResume.contains("1+") -> 55f
            experienceMatches > 0 -> 70f
            else -> 50f
        }

        // Education matching - degree requirements
        val educationKeywords = listOf("博士", "硕士", "本科", "大专", "高中", "phd", "master", "bachelor", "college")
        var educationMatches = 0
        for (keyword in educationKeywords) {
            if (jobRequirements.contains(keyword)) {
                educationMatches++
            }
            if (candidateResume.contains(keyword) || candidate.profile.lowercase().contains(keyword)) {
                educationMatches++
            }
        }
        val educationScore = when {
            (jobRequirements.contains("硕士") || jobRequirements.contains("master") || jobRequirements.contains("phd")) &&
                (candidateResume.contains("硕士") || candidateResume.contains("master") || candidateResume.contains("博士")) -> 100f
            (jobRequirements.contains("本科") || jobRequirements.contains("bachelor")) &&
                (candidateResume.contains("本科") || candidateResume.contains("硕士") || candidateResume.contains("博士") || candidateResume.contains("master")) -> 100f
            educationMatches > 0 -> 60f
            else -> 40f
        }

        // Generate match reason
        val reasons = mutableListOf<String>()

        // Add skill reasons
        val matchedSkills = skillKeywords.filter {
            candidateResume.contains(it) || candidate.profile.lowercase().contains(it)
        }.take(3)
        if (matchedSkills.isNotEmpty()) {
            reasons.add("熟悉${matchedSkills.joinToString("、")}等技术")
        }

        // Add experience reasons
        if (experienceScore >= 70) {
            reasons.add("具备${if (experienceScore >= 90) "5年以上" else "3年以上"}相关工作经验")
        }

        // Add education reasons
        val candidateEducation = educationKeywords.find { candidateResume.contains(it) }
            ?: educationKeywords.find { candidate.profile.lowercase().contains(it) }
        if (candidateEducation != null && !listOf("年", "years", "经验", "experience").contains(candidateEducation)) {
            reasons.add("$candidateEducation 学历背景")
        }

        val reason = if (reasons.isNotEmpty()) {
            reasons.joinToString("，") + "，符合岗位需求"
        } else {
            "简历与职位要求基本匹配"
        }

        // Calculate overall score from breakdown (weighted average)
        val calculatedOverall = (skillScore * 0.4f + experienceScore * 0.35f + educationScore * 0.25f)

        return MatchScoreBreakdown(
            overallScore = calculatedOverall,
            skillScore = skillScore,
            experienceScore = experienceScore,
            educationScore = educationScore,
            reason = reason
        )
    }

    private fun updateResultsCount(count: Int) {
        val text = if (currentFilter.hasActiveFilters()) {
            getString(R.string.filter_results_count, count)
        } else {
            getString(R.string.results_count_format, count)
        }
        binding.tvResultsCount.text = text
    }

    /**
     * Parse job requirements into structured fields
     */
    private fun parseJobRequirements(requirements: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val reqLower = requirements.lowercase()

        // Extract salary range
        val salaryRegex = Regex("(\\d+)[kK]?-(\\d+)[kK]|(\\d+)-(\\d+)[kK]|(\\d+)[kK]")
        salaryRegex.find(requirements)?.let { match ->
            result["salary"] = match.value
        }

        // Extract experience requirement
        val expRegex = Regex("(\\d+)[-~]?(\\d+)?\\s*年|(\\d+)\\+?\\s*年")
        expRegex.find(requirements)?.let { match ->
            result["experience"] = match.value.replace(" ", "")
        }

        // Extract education
        val eduKeywords = listOf("博士", "硕士", "本科", "大专", "高中", "phd", "master", "bachelor")
        eduKeywords.find { reqLower.contains(it) }?.let {
            result["education"] = it
        }

        // Extract common job attractions
        val attractionsList = mutableListOf<String>()
        if (reqLower.contains("六险") || reqLower.contains("五险")) attractionsList.add("六险一金")
        if (reqLower.contains("三餐") || reqLower.contains("免费餐")) attractionsList.add("免费三餐")
        if (reqLower.contains("房补") || reqLower.contains("租房补贴")) attractionsList.add("房补")
        if (reqLower.contains("年假") || reqLower.contains("带薪休假")) attractionsList.add("年假")
        if (reqLower.contains("股票") || reqLower.contains("期权")) attractionsList.add("股票期权")
        if (attractionsList.isNotEmpty()) {
            result["attractions"] = attractionsList.joinToString("、")
        }

        // Try to detect location
        val locations = listOf("北京", "上海", "深圳", "广州", "杭州", "南京", "成都", "武汉", "西安", "苏州")
        locations.find { reqLower.contains(it) }?.let {
            result["location"] = it
        }

        return result
    }

    private fun setupFilterButton() {
        binding.btnFilter.setOnClickListener {
            showFilterBottomSheet()
        }
    }

    private fun showFilterBottomSheet() {
        val bottomSheet = FilterBottomSheet.newInstance()
        bottomSheet.setCurrentFilter(currentFilter)
        bottomSheet.setFilterCallback(object : FilterBottomSheet.FilterCallback {
            override fun onFilterApplied(filter: FilterOptions) {
                currentFilter = filter
                applyFilter()
            }
        })
        bottomSheet.show(childFragmentManager, FilterBottomSheet.TAG)
    }

    private fun applyFilter() {
        val filteredResults = allResults.filter { currentFilter.matches(it) }

        adapter.submitList(filteredResults)
        updateResultsCount(filteredResults.size)

        if (filteredResults.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvResults.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvResults.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}