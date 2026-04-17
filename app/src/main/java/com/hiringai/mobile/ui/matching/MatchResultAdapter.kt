package com.hiringai.mobile.ui.matching

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.hiringai.mobile.R
import com.hiringai.mobile.databinding.ItemMatchResultBinding

class MatchResultAdapter(
    private val onViewDetailsClick: (MatchResult) -> Unit,
    private val onApplyClick: (MatchResult) -> Unit,
    private val onFavoriteClick: (MatchResult) -> Unit,
    private val onItemClick: (MatchResult) -> Unit = {}
) : ListAdapter<MatchResult, MatchResultAdapter.MatchResultViewHolder>(MatchResultDiffCallback()) {

    // Track expanded states
    private val expandedItems = mutableSetOf<Long>()

    // Track favorite states
    private val favoriteItems = mutableSetOf<Long>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchResultViewHolder {
        val binding = ItemMatchResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MatchResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MatchResultViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun toggleExpanded(itemId: Long) {
        if (expandedItems.contains(itemId)) {
            expandedItems.remove(itemId)
        } else {
            expandedItems.add(itemId)
        }
        notifyItemChanged(currentList.indexOfFirst { it.id == itemId })
    }

    fun toggleFavorite(itemId: Long) {
        if (favoriteItems.contains(itemId)) {
            favoriteItems.remove(itemId)
        } else {
            favoriteItems.add(itemId)
        }
        notifyItemChanged(currentList.indexOfFirst { it.id == itemId })
    }

    fun isFavorite(itemId: Long): Boolean = favoriteItems.contains(itemId)

    inner class MatchResultViewHolder(
        private val binding: ItemMatchResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position)
                    onItemClick(item)
                    toggleExpanded(item.id)
                }
            }

            binding.btnExpand.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position)
                    toggleExpanded(item.id)
                }
            }

            binding.btnViewDetails.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onViewDetailsClick(getItem(position))
                }
            }

            binding.btnApply.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onApplyClick(getItem(position))
                }
            }

            binding.btnFavorite.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position)
                    toggleFavorite(item.id)
                    onFavoriteClick(item)
                }
            }
        }

        fun bind(result: MatchResult) {
            val context = binding.root.context

            // Set score and progress bar
            val scorePercent = (result.score * 100).toInt()
            binding.tvScore.text = context.getString(R.string.score_format, scorePercent)
            binding.progressScore.progress = scorePercent

            // Set score level and color
            val (levelText, levelColor) = when {
                result.score >= 0.8f -> "优秀" to R.color.score_excellent
                result.score >= 0.6f -> "良好" to R.color.score_good
                result.score >= 0.4f -> "一般" to R.color.score_average
                else -> "较差" to R.color.score_bad
            }
            binding.tvScoreLevel.text = levelText
            binding.tvScoreLevel.setTextColor(ContextCompat.getColor(context, levelColor))
            binding.tvScore.setTextColor(ContextCompat.getColor(context, levelColor))

            // Display based on type
            when (result.type) {
                MatchType.CANDIDATE -> bindCandidateCard(result, context)
                MatchType.JOB -> bindJobCard(result, context)
            }

            // Update favorite state
            val isFavorite = favoriteItems.contains(result.id)
            binding.btnFavorite.setIconResource(
                if (isFavorite) android.R.drawable.btn_star_big_on
                else android.R.drawable.btn_star_big_off
            )

            // Handle expanded state
            val isExpanded = expandedItems.contains(result.id)
            binding.layoutBreakdown.visibility = if (isExpanded) View.VISIBLE else View.GONE
            binding.btnExpand.setImageResource(
                if (isExpanded) android.R.drawable.arrow_up_float
                else android.R.drawable.arrow_down_float
            )

            // Handle score breakdown
            result.scoreBreakdown?.let { breakdown ->
                // Skill score
                binding.progressSkill.progress = breakdown.skillScore.toInt()
                binding.tvSkillScore.text = "${breakdown.skillScore.toInt()}%"
                updateProgressColor(binding.progressSkill, breakdown.skillScore, context)

                // Experience score
                binding.progressExperience.progress = breakdown.experienceScore.toInt()
                binding.tvExperienceScore.text = "${breakdown.experienceScore.toInt()}%"
                updateProgressColor(binding.progressExperience, breakdown.experienceScore, context)

                // Education score
                binding.progressEducation.progress = breakdown.educationScore.toInt()
                binding.tvEducationScore.text = "${breakdown.educationScore.toInt()}%"
                updateProgressColor(binding.progressEducation, breakdown.educationScore, context)

                // Match reason
                if (breakdown.reason.isNotEmpty()) {
                    binding.layoutReason.visibility = View.VISIBLE
                    binding.tvMatchReason.text = breakdown.reason
                } else {
                    binding.layoutReason.visibility = View.GONE
                }
            } ?: run {
                binding.layoutReason.visibility = View.GONE
            }
        }

        private fun bindCandidateCard(result: MatchResult, context: android.content.Context) {
            // Candidate name with gender/age
            val nameInfo = buildString {
                append(result.title)
                if (result.gender.isNotEmpty() || result.age > 0) {
                    append(" (")
                    if (result.gender.isNotEmpty()) append(result.gender)
                    if (result.gender.isNotEmpty() && result.age > 0) append("/")
                    if (result.age > 0) append("${result.age}岁")
                    append(")")
                }
            }
            binding.tvTitle.text = nameInfo

            // Location and experience
            val locationExp = buildString {
                if (result.city.isNotEmpty()) {
                    append(result.city)
                }
                if (result.experienceYears > 0) {
                    if (isNotEmpty()) append("·")
                    append("${result.experienceYears}年经验")
                }
            }
            binding.tvInfo.text = locationExp

            // Education and school
            val educationInfo = buildString {
                if (result.education.isNotEmpty()) {
                    append(result.education)
                }
            }
            binding.tvEducation.text = educationInfo

            // Company and position
            val companyInfo = buildString {
                if (result.company.isNotEmpty()) {
                    append(result.company)
                }
                if (result.info.isNotEmpty() && result.company.isEmpty()) {
                    append(result.info)
                }
            }
            binding.tvCompany.text = companyInfo

            // Show skills section
            if (result.skills.isNotEmpty()) {
                binding.layoutSkills.visibility = View.VISIBLE
                binding.chipGroupSkills.removeAllViews()
                result.skills.take(6).forEach { skill ->
                    val chip = Chip(context).apply {
                        text = skill
                        isClickable = false
                        textSize = 11f
                        chipMinHeight = 28f
                        setChipBackgroundColorResource(R.color.gray_light)
                        setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    }
                    binding.chipGroupSkills.addView(chip)
                }
            } else {
                binding.layoutSkills.visibility = View.GONE
            }
            binding.layoutAttractions.visibility = View.GONE

            // Action button text
            binding.btnViewDetails.text = "查看简历"
            binding.btnApply.text = "立即沟通"
        }

        private fun bindJobCard(result: MatchResult, context: android.content.Context) {
            // Job title
            binding.tvTitle.text = result.title

            // Company and salary
            val companySalary = buildString {
                if (result.company.isNotEmpty()) {
                    append(result.company)
                }
                if (result.salaryRange.isNotEmpty()) {
                    if (isNotEmpty()) append(" | ")
                    append(result.salaryRange)
                }
            }
            binding.tvInfo.text = companySalary

            // Location, experience and education requirements
            val requirements = buildString {
                if (result.location.isNotEmpty()) {
                    append(result.location)
                }
                if (result.experienceRequirement.isNotEmpty()) {
                    if (isNotEmpty()) append(" | ")
                    append(result.experienceRequirement)
                }
                if (result.educationRequirement.isNotEmpty()) {
                    if (isNotEmpty()) append(" | ")
                    append(result.educationRequirement)
                }
            }
            binding.tvEducation.text = requirements

            // Company (hidden for job card, reuse as additional info)
            binding.tvCompany.text = result.info

            // Hide skills, show attractions
            binding.layoutSkills.visibility = View.GONE
            if (result.jobAttractions.isNotEmpty()) {
                binding.layoutAttractions.visibility = View.VISIBLE
                binding.tvAttractions.text = result.jobAttractions
            } else {
                binding.layoutAttractions.visibility = View.GONE
            }

            // Action button text
            binding.btnViewDetails.text = "查看详情"
            binding.btnApply.text = "匹配候选人"
        }

        private fun updateProgressColor(progressBar: android.widget.ProgressBar, score: Float, context: android.content.Context) {
            val color = when {
                score >= 80 -> R.color.score_excellent
                score >= 60 -> R.color.score_good
                score >= 40 -> R.color.score_average
                else -> R.color.score_bad
            }
            progressBar.progressTintList = ContextCompat.getColorStateList(context, color)
        }
    }

    class MatchResultDiffCallback : DiffUtil.ItemCallback<MatchResult>() {
        override fun areItemsTheSame(
            oldItem: MatchResult,
            newItem: MatchResult
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: MatchResult,
            newItem: MatchResult
        ): Boolean {
            return oldItem == newItem
        }
    }
}