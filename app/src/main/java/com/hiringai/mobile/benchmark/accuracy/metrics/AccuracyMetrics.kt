package com.hiringai.mobile.benchmark.accuracy.metrics

/**
 * Sealed class hierarchy for all accuracy metric types in the AI benchmark framework.
 * Each metric includes its value, confidence interval, and relevant details.
 */
sealed class AccuracyMetric {
    abstract val value: Double
    abstract val confidenceInterval: ConfidenceInterval?
    abstract val timestamp: Long

    /**
     * Confidence interval for metric values
     */
    data class ConfidenceInterval(
        val lower: Double,
        val upper: Double,
        val confidenceLevel: Double = 0.95
    ) {
        init {
            require(lower <= upper) { "Lower bound must be <= upper bound" }
            require(confidenceLevel in 0.0..1.0) { "Confidence level must be between 0 and 1" }
        }

        val width: Double get() = upper - lower
    }
}

/**
 * Word Error Rate metric for speech-to-text evaluation.
 * Lower is better (0 = perfect).
 */
data class WER(
    override val value: Double,
    override val confidenceInterval: AccuracyMetric.ConfidenceInterval? = null,
    override val timestamp: Long = System.currentTimeMillis(),
    val substitutions: Int = 0,
    val deletions: Int = 0,
    val insertions: Int = 0,
    val totalWords: Int = 0,
    val referenceLength: Int = totalWords
) : AccuracyMetric() {
    init {
        require(value >= 0.0) { "WER value must be non-negative" }
    }

    /**
     * Word Accuracy = 1 - WER (clamped to 0)
     */
    val wordAccuracy: Double get() = (1.0 - value).coerceAtLeast(0.0)

    /**
     * Human-readable error breakdown
     */
    val errorBreakdown: String
        get() = "S:$substitutions D:$deletions I:$insertions / $referenceLength"

    companion object {
        /**
         * Calculate WER from edit counts
         */
        fun fromEditCounts(
            substitutions: Int,
            deletions: Int,
            insertions: Int,
            referenceLength: Int,
            confidenceInterval: AccuracyMetric.ConfidenceInterval? = null
        ): WER {
            require(referenceLength > 0) { "Reference length must be positive" }
            val wer = (substitutions + deletions + insertions).toDouble() / referenceLength
            return WER(
                value = wer,
                substitutions = substitutions,
                deletions = deletions,
                insertions = insertions,
                totalWords = referenceLength,
                referenceLength = referenceLength,
                confidenceInterval = confidenceInterval
            )
        }
    }
}

/**
 * Character Error Rate metric.
 * Lower is better (0 = perfect).
 */
data class CER(
    override val value: Double,
    override val confidenceInterval: AccuracyMetric.ConfidenceInterval? = null,
    override val timestamp: Long = System.currentTimeMillis(),
    val substitutions: Int = 0,
    val deletions: Int = 0,
    val insertions: Int = 0,
    val totalCharacters: Int = 0,
    val referenceLength: Int = totalCharacters
) : AccuracyMetric() {
    init {
        require(value >= 0.0) { "CER value must be non-negative" }
    }

    /**
     * Character Accuracy = 1 - CER (clamped to 0)
     */
    val characterAccuracy: Double get() = (1.0 - value).coerceAtLeast(0.0)

    companion object {
        /**
         * Calculate CER from edit counts
         */
        fun fromEditCounts(
            substitutions: Int,
            deletions: Int,
            insertions: Int,
            referenceLength: Int,
            confidenceInterval: AccuracyMetric.ConfidenceInterval? = null
        ): CER {
            require(referenceLength > 0) { "Reference length must be positive" }
            val cer = (substitutions + deletions + insertions).toDouble() / referenceLength
            return CER(
                value = cer,
                substitutions = substitutions,
                deletions = deletions,
                insertions = insertions,
                totalCharacters = referenceLength,
                referenceLength = referenceLength,
                confidenceInterval = confidenceInterval
            )
        }
    }
}

/**
 * Top-K Accuracy metric for classification tasks.
 * Higher is better (1.0 = perfect).
 */
data class TopAccuracy(
    override val value: Double,
    override val confidenceInterval: AccuracyMetric.ConfidenceInterval? = null,
    override val timestamp: Long = System.currentTimeMillis(),
    val k: Int = 1,
    val correctCount: Int = 0,
    val totalCount: Int = 0
) : AccuracyMetric() {
    init {
        require(value in 0.0..1.0) { "Top accuracy must be between 0 and 1" }
        require(k >= 1) { "K must be at least 1" }
    }

    val accuracyPercent: Double get() = value * 100

    companion object {
        fun calculate(
            correctCount: Int,
            totalCount: Int,
            k: Int = 1,
            confidenceInterval: AccuracyMetric.ConfidenceInterval? = null
        ): TopAccuracy {
            require(totalCount > 0) { "Total count must be positive" }
            val accuracy = correctCount.toDouble() / totalCount
            return TopAccuracy(
                value = accuracy,
                k = k,
                correctCount = correctCount,
                totalCount = totalCount,
                confidenceInterval = confidenceInterval
            )
        }
    }
}

/**
 * BLEU score for translation/text generation evaluation.
 * Higher is better (1.0 = perfect match).
 */
data class BLEU(
    override val value: Double,
    override val confidenceInterval: AccuracyMetric.ConfidenceInterval? = null,
    override val timestamp: Long = System.currentTimeMillis(),
    val n: Int = 4,
    val brevityPenalty: Double = 1.0,
    val precisions: List<Double> = emptyList()
) : AccuracyMetric() {
    init {
        require(value in 0.0..1.0) { "BLEU score must be between 0 and 1" }
        require(n >= 1) { "N-gram order must be at least 1" }
        require(brevityPenalty in 0.0..1.0) { "Brevity penalty must be between 0 and 1" }
    }

    val bleuScore: Double get() = value * 100

    companion object {
        /**
         * Calculate BLEU score from n-gram precisions and lengths
         */
        fun calculate(
            precisions: List<Double>,
            referenceLength: Int,
            hypothesisLength: Int,
            n: Int = 4,
            confidenceInterval: AccuracyMetric.ConfidenceInterval? = null
        ): BLEU {
            require(precisions.isNotEmpty()) { "Precisions list cannot be empty" }
            require(referenceLength > 0) { "Reference length must be positive" }

            // Brevity penalty
            val bp = if (hypothesisLength >= referenceLength) {
                1.0
            } else {
                Math.exp(1.0 - referenceLength.toDouble() / hypothesisLength)
            }

            // Geometric mean of precisions
            val logPrecisions = precisions.filter { it > 0 }.map { Math.log(it) }
            val geoMean = if (logPrecisions.isEmpty()) {
                0.0
            } else {
                Math.exp(logPrecisions.sum() / logPrecisions.size)
            }

            val bleu = bp * geoMean
            return BLEU(
                value = bleu,
                n = n,
                brevityPenalty = bp,
                precisions = precisions,
                confidenceInterval = confidenceInterval
            )
        }
    }
}

/**
 * Perplexity metric for language model evaluation.
 * Lower is better (1.0 = perfect certainty).
 */
data class Perplexity(
    override val value: Double,
    override val confidenceInterval: AccuracyMetric.ConfidenceInterval? = null,
    override val timestamp: Long = System.currentTimeMillis(),
    val crossEntropy: Double = 0.0,
    val tokenCount: Int = 0,
    val vocabularySize: Int = 0
) : AccuracyMetric() {
    init {
        require(value >= 1.0) { "Perplexity must be at least 1.0" }
    }

    /**
     * Normalized perplexity (perplexity per token)
     */
    val normalizedPerplexity: Double
        get() = if (tokenCount > 0) value / tokenCount else value

    companion object {
        /**
         * Calculate perplexity from log probabilities
         */
        fun fromLogProbs(
            logProbs: List<Double>,
            vocabularySize: Int = 0,
            confidenceInterval: AccuracyMetric.ConfidenceInterval? = null
        ): Perplexity {
            require(logProbs.isNotEmpty()) { "Log probabilities cannot be empty" }
            val avgLogProb = logProbs.sum() / logProbs.size
            val crossEntropy = -avgLogProb
            val perplexity = Math.exp(crossEntropy)
            return Perplexity(
                value = perplexity,
                crossEntropy = crossEntropy,
                tokenCount = logProbs.size,
                vocabularySize = vocabularySize,
                confidenceInterval = confidenceInterval
            )
        }
    }
}

/**
 * Semantic Similarity metric using embedding-based comparison.
 * Higher is better (1.0 = semantically identical).
 */
data class SemanticSimilarity(
    override val value: Double,
    override val confidenceInterval: AccuracyMetric.ConfidenceInterval? = null,
    override val timestamp: Long = System.currentTimeMillis(),
    val method: SimilarityMethod = SimilarityMethod.COSINE,
    val referenceEmbeddingDim: Int = 0,
    val hypothesisEmbeddingDim: Int = 0
) : AccuracyMetric() {
    init {
        require(value in -1.0..1.0) { "Semantic similarity must be between -1 and 1" }
    }

    /**
     * Similarity as percentage (0-100)
     */
    val similarityPercent: Double get() = ((value + 1) / 2 * 100).coerceIn(0.0, 100.0)

    /**
     * Methods for computing semantic similarity
     */
    enum class SimilarityMethod {
        COSINE,
        EUCLIDEAN,
        DOT_PRODUCT,
        MANHATTAN
    }

    companion object {
        /**
         * Calculate cosine similarity between two embedding vectors
         */
        fun cosineSimilarity(
            reference: FloatArray,
            hypothesis: FloatArray,
            confidenceInterval: AccuracyMetric.ConfidenceInterval? = null
        ): SemanticSimilarity {
            require(reference.size == hypothesis.size) {
                "Embedding dimensions must match: ${reference.size} vs ${hypothesis.size}"
            }
            require(reference.isNotEmpty()) { "Embeddings cannot be empty" }

            var dotProduct = 0.0
            var normRef = 0.0
            var normHyp = 0.0

            for (i in reference.indices) {
                dotProduct += reference[i] * hypothesis[i]
                normRef += reference[i] * reference[i]
                normHyp += hypothesis[i] * hypothesis[i]
            }

            val similarity = if (normRef > 0 && normHyp > 0) {
                dotProduct / (Math.sqrt(normRef) * Math.sqrt(normHyp))
            } else {
                0.0
            }

            return SemanticSimilarity(
                value = similarity,
                method = SimilarityMethod.COSINE,
                referenceEmbeddingDim = reference.size,
                hypothesisEmbeddingDim = hypothesis.size,
                confidenceInterval = confidenceInterval
            )
        }
    }
}

/**
 * Aggregated accuracy metrics for a complete benchmark run
 */
data class AccuracyMetricsSummary(
    val metrics: Map<String, AccuracyMetric>,
    val overallScore: Double,
    val benchmarkId: String,
    val modelName: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Get a specific metric by name
     */
    inline fun <reified T : AccuracyMetric> getMetric(name: String): T? {
        return metrics[name] as? T
    }

    /**
     * Combine multiple metrics into a single score
     */
    fun computeWeightedScore(weights: Map<String, Double>): Double {
        var totalWeight = 0.0
        var weightedSum = 0.0

        for ((name, weight) in weights) {
            metrics[name]?.let { metric ->
                val normalizedValue = when (metric) {
                    is WER, is CER, is Perplexity -> 1.0 - normalizeForScoring(metric.value, metric)
                    else -> metric.value
                }
                weightedSum += normalizedValue * weight
                totalWeight += weight
            }
        }

        return if (totalWeight > 0) weightedSum / totalWeight else 0.0
    }

    private fun normalizeForScoring(value: Double, metric: AccuracyMetric): Double {
        return when (metric) {
            is WER -> value.coerceIn(0.0, 1.0)
            is CER -> value.coerceIn(0.0, 1.0)
            is Perplexity -> Math.min(value / 100.0, 1.0) // Normalize perplexity to 0-1
            else -> value
        }
    }
}
