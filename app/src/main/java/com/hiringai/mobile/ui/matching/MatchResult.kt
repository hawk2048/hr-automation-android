package com.hiringai.mobile.ui.matching

/**
 * Match result data class for matching center UI
 */
data class MatchResult(
    val id: Long,
    val score: Float,
    val title: String,
    val info: String,
    val description: String = "",
    val scoreBreakdown: MatchScoreBreakdown? = null,
    // Card type: CANDIDATE or JOB
    val type: MatchType = MatchType.CANDIDATE,
    // Extended candidate info
    val gender: String = "",
    val age: Int = 0,
    val city: String = "",
    val experienceYears: Int = 0,
    val education: String = "",
    val company: String = "",
    val skills: List<String> = emptyList(),
    // Extended job info
    val salaryRange: String = "",
    val jobAttractions: String = "",
    val location: String = "",
    val experienceRequirement: String = "",
    val educationRequirement: String = "",
    // References
    val candidateId: Long = 0,
    val jobId: Long = 0
)

/**
 * Match type enum
 */
enum class MatchType {
    CANDIDATE,  // 候选人卡片
    JOB         // 职位卡片
}

/**
 * Match score breakdown for detailed matching analysis
 */
data class MatchScoreBreakdown(
    val overallScore: Float,      // 综合匹配度 0-100
    val skillScore: Float,        // 技能匹配度
    val experienceScore: Float,   // 经验匹配度
    val educationScore: Float,    // 学历匹配度
    val reason: String            // 推荐理由
)