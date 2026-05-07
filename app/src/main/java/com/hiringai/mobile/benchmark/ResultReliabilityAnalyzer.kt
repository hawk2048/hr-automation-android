package com.hiringai.mobile.benchmark

object ResultReliabilityAnalyzer {

    data class ReliabilityMetrics(
        val mean: Double,
        val variance: Double,
        val standardDeviation: Double,
        val cv: Double,
        val outliers: List<Int>,
        val reliabilityScore: Int,
        val reliabilityLevel: ReliabilityLevel,
        val confidenceInterval: Pair<Double, Double>,
        val sampleSize: Int
    )

    enum class ReliabilityLevel {
        EXCELLENT,
        GOOD,
        FAIR,
        POOR
    }

    fun analyze(results: List<Double>): ReliabilityMetrics {
        if (results.isEmpty()) {
            return ReliabilityMetrics(
                mean = 0.0,
                variance = 0.0,
                standardDeviation = 0.0,
                cv = 0.0,
                outliers = emptyList(),
                reliabilityScore = 0,
                reliabilityLevel = ReliabilityLevel.POOR,
                confidenceInterval = Pair(0.0, 0.0),
                sampleSize = 0
            )
        }

        val mean = results.average()
        val variance = calculateVariance(results, mean)
        val standardDeviation = Math.sqrt(variance)
        val cv = if (mean != 0.0) (standardDeviation / mean) * 100 else 0.0
        val outliers = detectOutliers(results)
        val reliabilityScore = calculateReliabilityScore(results, cv, outliers.size)
        val reliabilityLevel = determineReliabilityLevel(reliabilityScore)
        val confidenceInterval = calculateConfidenceInterval(results, mean, standardDeviation)

        return ReliabilityMetrics(
            mean = mean,
            variance = variance,
            standardDeviation = standardDeviation,
            cv = cv,
            outliers = outliers,
            reliabilityScore = reliabilityScore,
            reliabilityLevel = reliabilityLevel,
            confidenceInterval = confidenceInterval,
            sampleSize = results.size
        )
    }

    private fun calculateVariance(values: List<Double>, mean: Double): Double {
        return values.sumOf { Math.pow(it - mean, 2.0) } / values.size
    }

    private fun detectOutliers(values: List<Double>): List<Int> {
        if (values.size < 4) return emptyList()

        val sorted = values.sorted()
        val q1 = getMedian(sorted.subList(0, sorted.size / 2))
        val q3 = getMedian(sorted.subList((sorted.size + 1) / 2, sorted.size))
        val iqr = q3 - q1
        val lowerBound = q1 - 1.5 * iqr
        val upperBound = q3 + 1.5 * iqr

        val outliers = mutableListOf<Int>()
        values.forEachIndexed { index, value ->
            if (value < lowerBound || value > upperBound) {
                outliers.add(index)
            }
        }

        return outliers
    }

    private fun getMedian(values: List<Double>): Double {
        val sorted = values.sorted()
        val size = sorted.size
        return if (size % 2 == 0) {
            (sorted[size / 2 - 1] + sorted[size / 2]) / 2.0
        } else {
            sorted[size / 2]
        }
    }

    private fun calculateReliabilityScore(values: List<Double>, cv: Double, outlierCount: Int): Int {
        var score = 100

        if (values.size < 3) {
            score -= 30
        } else if (values.size < 5) {
            score -= 15
        }

        when {
            cv > 50 -> score -= 40
            cv > 30 -> score -= 25
            cv > 15 -> score -= 10
        }

        if (outlierCount > 0) {
            score -= outlierCount * 10
        }

        return score.coerceIn(0, 100)
    }

    private fun determineReliabilityLevel(score: Int): ReliabilityLevel {
        return when {
            score >= 90 -> ReliabilityLevel.EXCELLENT
            score >= 70 -> ReliabilityLevel.GOOD
            score >= 50 -> ReliabilityLevel.FAIR
            else -> ReliabilityLevel.POOR
        }
    }

    private fun calculateConfidenceInterval(values: List<Double>, mean: Double, stdDev: Double): Pair<Double, Double> {
        if (values.size < 2) return Pair(mean, mean)

        val zScore = 1.96
        val standardError = stdDev / Math.sqrt(values.size.toDouble())
        val marginOfError = zScore * standardError

        return Pair(mean - marginOfError, mean + marginOfError)
    }

    fun formatMetrics(metrics: ReliabilityMetrics): String {
        return buildString {
            appendLine("📊 可信度分析报告")
            appendLine("━".repeat(40))
            appendLine("样本数量: ${metrics.sampleSize}")
            appendLine("平均值: ${String.format("%.2f", metrics.mean)}")
            appendLine("方差: ${String.format("%.2f", metrics.variance)}")
            appendLine("标准差: ${String.format("%.2f", metrics.standardDeviation)}")
            appendLine("变异系数: ${String.format("%.1f%%", metrics.cv)}")
            appendLine("可信度评分: ${metrics.reliabilityScore}/100")
            appendLine("可信度等级: ${getReliabilityLevelDescription(metrics.reliabilityLevel)}")
            appendLine("置信区间: [${String.format("%.2f", metrics.confidenceInterval.first)}, ${String.format("%.2f", metrics.confidenceInterval.second)}]")
            if (metrics.outliers.isNotEmpty()) {
                appendLine("异常值数量: ${metrics.outliers.size}")
            }
        }
    }

    fun getReliabilityLevelDescription(level: ReliabilityLevel): String {
        return when (level) {
            ReliabilityLevel.EXCELLENT -> "优秀"
            ReliabilityLevel.GOOD -> "良好"
            ReliabilityLevel.FAIR -> "一般"
            ReliabilityLevel.POOR -> "较差"
        }
    }

    fun getReliabilityLevelColor(level: ReliabilityLevel): Int {
        return when (level) {
            ReliabilityLevel.EXCELLENT -> android.graphics.Color.GREEN
            ReliabilityLevel.GOOD -> android.graphics.Color.parseColor("#FF9800")
            ReliabilityLevel.FAIR -> android.graphics.Color.parseColor("#FFC107")
            ReliabilityLevel.POOR -> android.graphics.Color.RED
        }
    }
}
