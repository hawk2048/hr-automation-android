package com.hiringai.mobile.benchmark.accuracy.metrics

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/**
 * BLEU (Bilingual Evaluation Understudy) score calculator for text generation evaluation.
 *
 * Supports BLEU-1 through BLEU-4 with brevity penalty calculation.
 * Based on the original BLEU paper by Papineni et al. (2002).
 *
 * @property maxNgram Maximum n-gram order (default: 4 for BLEU-4)
 */
class BLEUCalculator(private val maxNgram: Int = 4) {

    init {
        require(maxNgram in 1..4) { "maxNgram must be between 1 and 4" }
    }

    /**
     * Calculate BLEU score between candidate and reference text.
     *
     * @param candidate The generated/candidate text
     * @param reference The reference/ground truth text
     * @return BLEU score between 0.0 and 1.0
     */
    fun calculate(candidate: String, reference: String): Double {
        return calculate(candidate, listOf(reference))
    }

    /**
     * Calculate BLEU score between candidate and multiple references.
     * Uses the closest reference length for brevity penalty.
     *
     * @param candidate The generated/candidate text
     * @param references List of reference/ground truth texts
     * @return BLEU score between 0.0 and 1.0
     */
    fun calculate(candidate: String, references: List<String>): Double {
        if (candidate.isBlank()) return 0.0
        if (references.isEmpty() || references.all { it.isBlank() }) return 0.0

        val candidateTokens = tokenize(candidate)
        val referenceTokensList = references.map { tokenize(it) }

        // Calculate brevity penalty
        val bp = calculateBrevityPenalty(candidateTokens, referenceTokensList)

        // Calculate modified n-gram precisions
        val precisions = (1..maxNgram).map { n ->
            calculateModifiedPrecision(candidateTokens, referenceTokensList, n)
        }

        // If any precision is 0, BLEU is 0
        if (precisions.any { it == 0.0 }) return 0.0

        // Calculate geometric mean of precisions (log space for numerical stability)
        val logPrecisionSum = precisions.sumOf { ln(it) }
        val geometricMean = exp(logPrecisionSum / maxNgram)

        return bp * geometricMean
    }

    /**
     * Calculate individual BLEU-N score (e.g., BLEU-1, BLEU-2, etc.)
     *
     * @param candidate The generated/candidate text
     * @param reference The reference/ground truth text
     * @param n The n-gram order (1-4)
     * @return BLEU-N precision score
     */
    fun calculateBLEUN(candidate: String, reference: String, n: Int): Double {
        require(n in 1..maxNgram) { "n must be between 1 and $maxNgram" }

        val candidateTokens = tokenize(candidate)
        val referenceTokensList = listOf(tokenize(reference))

        return calculateModifiedPrecision(candidateTokens, referenceTokensList, n)
    }

    /**
     * Calculate all BLEU scores (BLEU-1 through BLEU-4) with brevity penalty.
     *
     * @param candidate The generated/candidate text
     * @param reference The reference/ground truth text
     * @return BLEUScores containing all individual scores and final BLEU
     */
    fun calculateAll(candidate: String, reference: String): BLEUScores {
        return calculateAll(candidate, listOf(reference))
    }

    /**
     * Calculate all BLEU scores with multiple references.
     *
     * @param candidate The generated/candidate text
     * @param references List of reference/ground truth texts
     * @return BLEUScores containing all individual scores and final BLEU
     */
    fun calculateAll(candidate: String, references: List<String>): BLEUScores {
        val candidateTokens = tokenize(candidate)
        val referenceTokensList = references.map { tokenize(it) }

        val bleu1 = calculateModifiedPrecision(candidateTokens, referenceTokensList, 1)
        val bleu2 = calculateModifiedPrecision(candidateTokens, referenceTokensList, 2)
        val bleu3 = calculateModifiedPrecision(candidateTokens, referenceTokensList, 3)
        val bleu4 = calculateModifiedPrecision(candidateTokens, referenceTokensList, 4)

        val bp = calculateBrevityPenalty(candidateTokens, referenceTokensList)

        val finalBleu = if (listOf(bleu1, bleu2, bleu3, bleu4).take(maxNgram).any { it == 0.0 }) {
            0.0
        } else {
            val logSum = listOf(bleu1, bleu2, bleu3, bleu4)
                .take(maxNgram)
                .sumOf { ln(it) }
            bp * exp(logSum / maxNgram)
        }

        return BLEUScores(
            bleu1 = bleu1,
            bleu2 = bleu2,
            bleu3 = bleu3,
            bleu4 = bleu4,
            brevityPenalty = bp,
            finalBleu = finalBleu
        )
    }

    /**
     * Tokenize text into words.
     * Handles basic punctuation and whitespace.
     */
    private fun tokenize(text: String): List<String> {
        return text
            .lowercase()
            .replace(Regex("[^\\w\\s]"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
    }

    /**
     * Calculate brevity penalty (BP).
     * BP = 1 if c > r, else exp(1 - r/c)
     * where c = candidate length, r = reference length
     */
    private fun calculateBrevityPenalty(
        candidateTokens: List<String>,
        referenceTokensList: List<List<String>>
    ): Double {
        val c = candidateTokens.size

        // Find closest reference length
        val r = referenceTokensList
            .map { it.size }
            .minByOrNull { kotlin.math.abs(it - c) } ?: c

        return if (c > r) {
            1.0
        } else if (c == 0) {
            0.0
        } else {
            exp(1.0 - r.toDouble() / c)
        }
    }

    /**
     * Calculate modified n-gram precision.
     * Clips counts to max occurrences in any single reference.
     */
    private fun calculateModifiedPrecision(
        candidateTokens: List<String>,
        referenceTokensList: List<List<String>>,
        n: Int
    ): Double {
        val candidateNgrams = extractNgrams(candidateTokens, n)

        if (candidateNgrams.isEmpty()) return 0.0

        // Count max occurrences in any single reference
        val maxRefCounts = mutableMapOf<List<String>, Int>()
        for (referenceTokens in referenceTokensList) {
            val refNgrams = extractNgrams(referenceTokens, n)
            val refCounts = refNgrams.groupingBy { it }.eachCount()
            for ((ngram, count) in refCounts) {
                maxRefCounts[ngram] = maxOf(maxRefCounts[ngram] ?: 0, count)
            }
        }

        // Count candidate n-grams with clipping
        val candidateCounts = candidateNgrams.groupingBy { it }.eachCount()
        var clippedCount = 0
        var totalCount = 0

        for ((ngram, count) in candidateCounts) {
            val maxCount = maxRefCounts[ngram] ?: 0
            clippedCount += min(count, maxCount)
            totalCount += count
        }

        return if (totalCount == 0) 0.0 else clippedCount.toDouble() / totalCount
    }

    /**
     * Extract n-grams from token list.
     */
    private fun extractNgrams(tokens: List<String>, n: Int): List<List<String>> {
        if (tokens.size < n) return emptyList()
        return (0..tokens.size - n).map { i -> tokens.subList(i, i + n) }
    }

    /**
     * Batch calculation for multiple candidates and references.
     *
     * @param pairs List of (candidate, reference) pairs
     * @return Average BLEU score across all pairs
     */
    fun calculateBatch(pairs: List<Pair<String, String>>): Double {
        if (pairs.isEmpty()) return 0.0
        return pairs.map { (candidate, reference) ->
            calculate(candidate, reference)
        }.average()
    }

    /**
     * Detailed batch calculation returning individual and average scores.
     */
    fun calculateBatchDetailed(pairs: List<Pair<String, String>>): BatchBLEUResult {
        if (pairs.isEmpty()) {
            return BatchBLEUResult(emptyList(), 0.0, 0.0)
        }

        val individualScores = pairs.mapIndexed { index, (candidate, reference) ->
            PairResult(index, calculateAll(candidate, reference))
        }

        val averageBleu = individualScores.map { it.scores.finalBleu }.average()
        val averageBleu1 = individualScores.map { it.scores.bleu1 }.average()

        return BatchBLEUResult(individualScores, averageBleu, averageBleu1)
    }
}

/**
 * Individual BLEU score breakdown.
 */
data class BLEUScores(
    val bleu1: Double,
    val bleu2: Double,
    val bleu3: Double,
    val bleu4: Double,
    val brevityPenalty: Double,
    val finalBleu: Double
) {
    /**
     * Format scores as human-readable string.
     */
    fun toFormattedString(): String {
        return buildString {
            appendLine("BLEU Scores:")
            appendLine("  BLEU-1: ${"%.4f".format(bleu1)}")
            appendLine("  BLEU-2: ${"%.4f".format(bleu2)}")
            appendLine("  BLEU-3: ${"%.4f".format(bleu3)}")
            appendLine("  BLEU-4: ${"%.4f".format(bleu4)}")
            appendLine("  Brevity Penalty: ${"%.4f".format(brevityPenalty)}")
            appendLine("  Final BLEU: ${"%.4f".format(finalBleu)}")
        }
    }
}

/**
 * Result for a single pair in batch calculation.
 */
data class PairResult(
    val index: Int,
    val scores: BLEUScores
)

/**
 * Result for batch BLEU calculation.
 */
data class BatchBLEUResult(
    val individualResults: List<PairResult>,
    val averageBleu: Double,
    val averageBleu1: Double
) {
    /**
     * Get summary statistics.
     */
    fun getSummary(): String {
        if (individualResults.isEmpty()) return "No results"

        val bleus = individualResults.map { it.scores.finalBleu }
        return buildString {
            appendLine("Batch BLEU Summary (${individualResults.size} samples):")
            appendLine("  Average BLEU: ${"%.4f".format(averageBleu)}")
            appendLine("  Min BLEU: ${"%.4f".format(bleus.minOrNull() ?: 0.0)}")
            appendLine("  Max BLEU: ${"%.4f".format(bleus.maxOrNull() ?: 0.0)}")
        }
    }
}
