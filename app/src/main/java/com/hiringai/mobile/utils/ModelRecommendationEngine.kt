package com.hiringai.mobile.utils

import android.content.Context
import com.hiringai.mobile.ml.LocalImageModelService
import com.hiringai.mobile.ml.LocalLLMService
import com.hiringai.mobile.ml.SpeechModelService
import com.hiringai.mobile.utils.DeviceInfoProvider.DeviceSpec
import com.hiringai.mobile.utils.DeviceInfoProvider.DeviceTier

object ModelRecommendationEngine {

    data class RecommendedModel(
        val category: String,
        val modelName: String,
        val modelConfig: Any?,
        val recommended: Boolean,
        val reason: String,
        val performanceScore: Int,
        val memoryRequirementGB: Float,
        val recommendedQuantization: String
    )

    fun getRecommendations(context: Context, spec: DeviceSpec): List<RecommendedModel> {
        val recommendations = mutableListOf<RecommendedModel>()

        recommendations.addAll(getLLMRecommendations(spec))
        recommendations.addAll(getImageRecommendations(spec))
        recommendations.addAll(getSpeechRecommendations(spec))

        return recommendations.sortedByDescending { it.performanceScore }
    }

    private fun getLLMRecommendations(spec: DeviceSpec): List<RecommendedModel> {
        val models = LocalLLMService.AVAILABLE_MODELS
        val recommendations = mutableListOf<RecommendedModel>()

        models.forEach { model ->
            val memoryRequired = model.requiredRAM.toFloat()
            val canRun = spec.ramSizeGB >= memoryRequired
            val isRecommended = isRecommendedForTier(spec.deviceTier, model.requiredRAM)

            val score = calculateScore(spec.deviceTier, memoryRequired, spec.ramSizeGB)
            val reason = when {
                !canRun -> "内存不足，需要 ${memoryRequired}GB"
                isRecommended -> "根据您的设备推荐"
                else -> "可用但非最优选择"
            }

            recommendations.add(RecommendedModel(
                category = "LLM",
                modelName = model.name,
                modelConfig = model,
                recommended = isRecommended,
                reason = reason,
                performanceScore = score,
                memoryRequirementGB = memoryRequired,
                recommendedQuantization = getRecommendedQuantization(spec.deviceTier)
            ))
        }

        return recommendations
    }

    private fun getImageRecommendations(spec: DeviceSpec): List<RecommendedModel> {
        val models = LocalImageModelService.AVAILABLE_MODELS
        val recommendations = mutableListOf<RecommendedModel>()

        models.forEach { model ->
            val memoryRequired = model.requiredRAM.toFloat()
            val canRun = spec.ramSizeGB >= memoryRequired
            val isRecommended = isRecommendedForTier(spec.deviceTier, model.requiredRAM)

            val score = calculateScore(spec.deviceTier, memoryRequired, spec.ramSizeGB)
            val reason = when {
                !canRun -> "内存不足，需要 ${memoryRequired}GB"
                isRecommended -> "根据您的设备推荐"
                else -> "可用但非最优选择"
            }

            recommendations.add(RecommendedModel(
                category = "图像",
                modelName = model.name,
                modelConfig = model,
                recommended = isRecommended,
                reason = reason,
                performanceScore = score,
                memoryRequirementGB = memoryRequired,
                recommendedQuantization = getRecommendedQuantization(spec.deviceTier)
            ))
        }

        return recommendations
    }

    private fun getSpeechRecommendations(spec: DeviceSpec): List<RecommendedModel> {
        val models = SpeechModelService.AVAILABLE_MODELS
        val recommendations = mutableListOf<RecommendedModel>()

        models.forEach { model ->
            val memoryRequired = model.requiredRAM.toFloat()
            val canRun = spec.ramSizeGB >= memoryRequired
            val isRecommended = isRecommendedForTier(spec.deviceTier, model.requiredRAM)

            val score = calculateScore(spec.deviceTier, memoryRequired, spec.ramSizeGB)
            val reason = when {
                !canRun -> "内存不足，需要 ${memoryRequired}GB"
                isRecommended -> "根据您的设备推荐"
                else -> "可用但非最优选择"
            }

            recommendations.add(RecommendedModel(
                category = "语音",
                modelName = model.name,
                modelConfig = model,
                recommended = isRecommended,
                reason = reason,
                performanceScore = score,
                memoryRequirementGB = memoryRequired,
                recommendedQuantization = getRecommendedQuantization(spec.deviceTier)
            ))
        }

        return recommendations
    }

    private fun isRecommendedForTier(tier: DeviceTier, requiredRAM: Int): Boolean {
        return when (tier) {
            DeviceTier.HIGH_END -> requiredRAM in 4..8
            DeviceTier.MID_RANGE -> requiredRAM in 2..4
            DeviceTier.BUDGET -> requiredRAM <= 2
        }
    }

    private fun calculateScore(tier: DeviceTier, requiredRAM: Float, availableRAM: Float): Int {
        if (availableRAM < requiredRAM) return 0

        val baseScore = when (tier) {
            DeviceTier.HIGH_END -> 100
            DeviceTier.MID_RANGE -> 75
            DeviceTier.BUDGET -> 50
        }

        val memoryHeadroom = (availableRAM - requiredRAM) / availableRAM
        val memoryBonus = (memoryHeadroom * 30).toInt()

        val ramScore = when (requiredRAM) {
            in 0..2 -> 20
            in 2..4 -> 15
            in 4..8 -> 10
            else -> 5
        }

        return (baseScore + memoryBonus + ramScore).coerceAtMost(100)
    }

    private fun getRecommendedQuantization(tier: DeviceTier): String {
        return when (tier) {
            DeviceTier.HIGH_END -> "Q4_0 / Q5_K_M"
            DeviceTier.MID_RANGE -> "Q4_0 / Q2_K"
            DeviceTier.BUDGET -> "Q2_K / Q2_K_M"
        }
    }

    fun getTopRecommendationForCategory(
        context: Context,
        spec: DeviceSpec,
        category: String
    ): RecommendedModel? {
        val recommendations = getRecommendations(context, spec)
        return recommendations
            .filter { it.category == category && it.recommended }
            .firstOrNull()
    }

    fun formatRecommendation(recommendation: RecommendedModel): String {
        return buildString {
            append("${if (recommendation.recommended) "✅" else "⚠️"} ")
            append("${recommendation.modelName}")
            append(" (${recommendation.category})")
            appendLine()
            append("  • 推荐原因: ${recommendation.reason}")
            appendLine()
            append("  • 性能评分: ${recommendation.performanceScore}/100")
            appendLine()
            append("  • 内存需求: ${recommendation.memoryRequirementGB}GB")
            appendLine()
            append("  • 推荐量化: ${recommendation.recommendedQuantization}")
        }
    }
}
