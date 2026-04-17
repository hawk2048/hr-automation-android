package com.hiringai.mobile.ui.matching

/**
 * Filter options for matching results
 */
data class FilterOptions(
    val minMatchScore: Int = 0,        // 最低匹配度 0-100
    val maxMatchScore: Int = 100,      // 最高匹配度
    val minExperienceYears: Int = 0,   // 最低工作年限
    val maxExperienceYears: Int = 20,  // 最高工作年限
    val educationLevel: String = "all", // 学历要求: all/bachelor/master/doctor
    val salaryRange: String = "all",    // 薪资范围: all/10k20k/20k40k/40k60k/60kup
    val recentActiveDays: Int = 0,     // 最近活跃天数 0=不限
    val skills: List<String> = emptyList() // 关键技能筛选
) {
    companion object {
        const val EDUCATION_ALL = "all"
        const val EDUCATION_BACHELOR = "bachelor"
        const val EDUCATION_MASTER = "master"
        const val EDUCATION_DOCTOR = "doctor"

        const val SALARY_ALL = "all"
        const val SALARY_10K_20K = "10k20k"
        const val SALARY_20K_40K = "20k40k"
        const val SALARY_40K_60K = "40k60k"
        const val SALARY_60K_UP = "60kup"

        // Default salary ranges in K (thousands)
        val SALARY_RANGES = mapOf(
            SALARY_ALL to Pair(0, Int.MAX_VALUE),
            SALARY_10K_20K to Pair(10, 20),
            SALARY_20K_40K to Pair(20, 40),
            SALARY_40K_60K to Pair(40, 60),
            SALARY_60K_UP to Pair(60, Int.MAX_VALUE)
        )

        // Education level priority (higher = more qualified)
        val EDUCATION_PRIORITY = mapOf(
            EDUCATION_ALL to 0,
            EDUCATION_BACHELOR to 1,
            EDUCATION_MASTER to 2,
            EDUCATION_DOCTOR to 3
        )
    }

    /**
     * Check if a match result passes this filter
     */
    fun matches(result: MatchResult): Boolean {
        // Check match score range
        val scorePercent = (result.score * 100).toInt()
        if (scorePercent < minMatchScore || scorePercent > maxMatchScore) {
            return false
        }

        // Check score breakdown if available
        result.scoreBreakdown?.let { breakdown ->
            // Check experience years if available
            if (minExperienceYears > 0 || maxExperienceYears < 20) {
                val experienceYears = extractExperienceYears(breakdown.reason)
                if (experienceYears < minExperienceYears || experienceYears > maxExperienceYears) {
                    return false
                }
            }

            // Check education level
            if (educationLevel != EDUCATION_ALL) {
                val candidateEducation = extractEducationLevel(breakdown.reason)
                if (candidateEducation < EDUCATION_PRIORITY[educationLevel] ?: 0) {
                    return false
                }
            }

            // Check skills if specified
            if (skills.isNotEmpty()) {
                val hasRequiredSkills = skills.any { skill ->
                    breakdown.reason.contains(skill, ignoreCase = true)
                }
                if (!hasRequiredSkills) {
                    return false
                }
            }
        }

        return true
    }

    private fun extractExperienceYears(reason: String): Int {
        val patterns = listOf(
            Regex("(\\d+)\\+?[年年]"),
            Regex("(\\d+)\\+ years"),
            Regex("(\\d+)\\+ years of")
        )
        for (pattern in patterns) {
            val match = pattern.find(reason)
            if (match != null) {
                return match.groupValues[1].toIntOrNull() ?: 0
            }
        }
        return 0
    }

    private fun extractEducationLevel(reason: String): Int {
        return when {
            reason.contains("博士") || reason.contains("phd", ignoreCase = true) -> 3
            reason.contains("硕士") || reason.contains("master", ignoreCase = true) -> 2
            reason.contains("本科") || reason.contains("bachelor", ignoreCase = true) -> 1
            else -> 0
        }
    }

    /**
     * Get human-readable salary range text
     */
    fun getSalaryRangeText(): String {
        return when (salaryRange) {
            SALARY_ALL -> "不限"
            SALARY_10K_20K -> "10-20K"
            SALARY_20K_40K -> "20-40K"
            SALARY_40K_60K -> "40-60K"
            SALARY_60K_UP -> "60K+"
            else -> "不限"
        }
    }

    /**
     * Get human-readable education level text
     */
    fun getEducationLevelText(): String {
        return when (educationLevel) {
            EDUCATION_ALL -> "不限"
            EDUCATION_BACHELOR -> "本科"
            EDUCATION_MASTER -> "硕士"
            EDUCATION_DOCTOR -> "博士"
            else -> "不限"
        }
    }

    /**
     * Get human-readable recent active text
     */
    fun getRecentActiveText(): String {
        return when (recentActiveDays) {
            0 -> "不限"
            7 -> "7天内"
            30 -> "30天内"
            90 -> "90天内"
            else -> "不限"
        }
    }

    /**
     * Check if any filter is actively applied
     */
    fun hasActiveFilters(): Boolean {
        return minMatchScore > 0 ||
                maxMatchScore < 100 ||
                minExperienceYears > 0 ||
                maxExperienceYears < 20 ||
                educationLevel != EDUCATION_ALL ||
                salaryRange != SALARY_ALL ||
                recentActiveDays > 0 ||
                skills.isNotEmpty()
    }
}