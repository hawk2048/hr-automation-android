package com.hiringai.mobile.benchmark.accuracy.metrics

/**
 * Word Error Rate (WER) and Character Error Rate (CER) calculator.
 * Uses dynamic programming with Levenshtein distance algorithm.
 */
object WERCalculator {

    /**
     * Result of WER/CER calculation containing all edit operation details
     */
    data class EditResult(
        val substitutions: Int,
        val deletions: Int,
        val insertions: Int,
        val totalEdits: Int,
        val referenceLength: Int
    ) {
        val errorRate: Double
            get() = if (referenceLength > 0) {
                totalEdits.toDouble() / referenceLength
            } else {
                0.0
            }

        val accuracy: Double
            get() = (1.0 - errorRate).coerceAtLeast(0.0)
    }

    /**
     * Detailed alignment between reference and hypothesis
     */
    data class Alignment(
        val referenceTokens: List<String>,
        val hypothesisTokens: List<String>,
        val operations: List<EditOperation>
    )

    /**
     * Single edit operation in alignment
     */
    sealed class EditOperation {
        abstract val position: Int

        data class Match(override val position: Int, val token: String) : EditOperation()
        data class Substitution(
            override val position: Int,
            val reference: String,
            val hypothesis: String
        ) : EditOperation()

        data class Deletion(override val position: Int, val token: String) : EditOperation()
        data class Insertion(override val position: Int, val token: String) : EditOperation()
    }

    /**
     * Calculate Word Error Rate (WER) between reference and hypothesis strings.
     *
     * @param reference The ground truth text
     * @param hypothesis The predicted/recognized text
     * @param normalize Whether to normalize text (lowercase, remove punctuation)
     * @return WER metric with detailed edit counts
     */
    fun calculateWER(
        reference: String,
        hypothesis: String,
        normalize: Boolean = true
    ): WER {
        val refTokens = tokenize(reference, normalize)
        val hypTokens = tokenize(hypothesis, normalize)

        val result = calculateEditDistance(refTokens, hypTokens)

        return WER.fromEditCounts(
            substitutions = result.substitutions,
            deletions = result.deletions,
            insertions = result.insertions,
            referenceLength = result.referenceLength
        )
    }

    /**
     * Calculate Character Error Rate (CER) between reference and hypothesis strings.
     *
     * @param reference The ground truth text
     * @param hypothesis The predicted/recognized text
     * @param normalize Whether to normalize text (lowercase, remove whitespace)
     * @return CER metric with detailed edit counts
     */
    fun calculateCER(
        reference: String,
        hypothesis: String,
        normalize: Boolean = true
    ): CER {
        val refChars = prepareCharacters(reference, normalize)
        val hypChars = prepareCharacters(hypothesis, normalize)

        val result = calculateEditDistance(refChars, hypChars)

        return CER.fromEditCounts(
            substitutions = result.substitutions,
            deletions = result.deletions,
            insertions = result.insertions,
            referenceLength = result.referenceLength
        )
    }

    /**
     * Calculate both WER and CER for a given reference/hypothesis pair.
     *
     * @param reference The ground truth text
     * @param hypothesis The predicted/recognized text
     * @param normalize Whether to normalize text
     * @return Pair of (WER, CER) metrics
     */
    fun calculateBoth(
        reference: String,
        hypothesis: String,
        normalize: Boolean = true
    ): Pair<WER, CER> {
        return Pair(
            calculateWER(reference, hypothesis, normalize),
            calculateCER(reference, hypothesis, normalize)
        )
    }

    /**
     * Calculate WER with alignment information for detailed analysis.
     *
     * @param reference The ground truth text
     * @param hypothesis The predicted/recognized text
     * @param normalize Whether to normalize text
     * @return Pair of (WER metric, Alignment details)
     */
    fun calculateWERWithAlignment(
        reference: String,
        hypothesis: String,
        normalize: Boolean = true
    ): Pair<WER, Alignment> {
        val refTokens = tokenize(reference, normalize)
        val hypTokens = tokenize(hypothesis, normalize)

        val result = calculateEditDistance(refTokens, hypTokens)
        val alignment = computeAlignment(refTokens, hypTokens)

        val wer = WER.fromEditCounts(
            substitutions = result.substitutions,
            deletions = result.deletions,
            insertions = result.insertions,
            referenceLength = result.referenceLength
        )

        return Pair(wer, alignment)
    }

    /**
     * Batch calculate WER for multiple reference/hypothesis pairs.
     *
     * @param pairs List of (reference, hypothesis) pairs
     * @param normalize Whether to normalize text
     * @return Aggregated WER across all pairs
     */
    fun batchWER(
        pairs: List<Pair<String, String>>,
        normalize: Boolean = true
    ): WER {
        require(pairs.isNotEmpty()) { "Pairs list cannot be empty" }

        var totalSubstitutions = 0
        var totalDeletions = 0
        var totalInsertions = 0
        var totalReferenceLength = 0

        for ((reference, hypothesis) in pairs) {
            val result = calculateEditDistance(
                tokenize(reference, normalize),
                tokenize(hypothesis, normalize)
            )
            totalSubstitutions += result.substitutions
            totalDeletions += result.deletions
            totalInsertions += result.insertions
            totalReferenceLength += result.referenceLength
        }

        return WER.fromEditCounts(
            substitutions = totalSubstitutions,
            deletions = totalDeletions,
            insertions = totalInsertions,
            referenceLength = totalReferenceLength
        )
    }

    /**
     * Core Levenshtein distance calculation with operation tracking.
     * Uses dynamic programming with O(n*m) time and O(min(n,m)) space.
     */
    private fun calculateEditDistance(
        reference: List<String>,
        hypothesis: List<String>
    ): EditResult {
        val n = reference.size
        val m = hypothesis.size

        if (n == 0 && m == 0) {
            return EditResult(0, 0, 0, 0, 0)
        }

        if (n == 0) {
            return EditResult(0, 0, m, m, 0)
        }

        if (m == 0) {
            return EditResult(0, n, 0, n, n)
        }

        // Use two rows for space optimization
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)

        // Track operations (simplified - just counts)
        var substitutions = 0
        var deletions = 0
        var insertions = 0

        for (j in 1..m) {
            curr[0] = j

            for (i in 1..n) {
                val cost = if (reference[i - 1] == hypothesis[j - 1]) 0 else 1

                curr[i] = minOf(
                    prev[i] + 1,        // Deletion
                    curr[i - 1] + 1,    // Insertion
                    prev[i - 1] + cost  // Substitution or match
                )
            }

            // Swap rows
            val temp = prev
            prev = curr
            curr = temp
        }

        // Backtrack to count operations
        val operationCounts = backtrackOperations(reference, hypothesis)
        substitutions = operationCounts.first
        deletions = operationCounts.second
        insertions = operationCounts.third

        return EditResult(
            substitutions = substitutions,
            deletions = deletions,
            insertions = insertions,
            totalEdits = substitutions + deletions + insertions,
            referenceLength = n
        )
    }

    /**
     * Backtrack through the DP matrix to count each operation type.
     */
    private fun backtrackOperations(
        reference: List<String>,
        hypothesis: List<String>
    ): Triple<Int, Int, Int> {
        val n = reference.size
        val m = hypothesis.size

        // Build full DP matrix for backtracking
        val dp = Array(n + 1) { IntArray(m + 1) }

        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j

        for (i in 1..n) {
            for (j in 1..m) {
                val cost = if (reference[i - 1] == hypothesis[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,       // Deletion
                    dp[i][j - 1] + 1,       // Insertion
                    dp[i - 1][j - 1] + cost // Substitution or match
                )
            }
        }

        // Backtrack from bottom-right
        var substitutions = 0
        var deletions = 0
        var insertions = 0
        var i = n
        var j = m

        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] &&
                        reference[i - 1] == hypothesis[j - 1] -> {
                    // Match - no operation
                    i--
                    j--
                }

                i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1 -> {
                    // Substitution
                    substitutions++
                    i--
                    j--
                }

                i > 0 && dp[i][j] == dp[i - 1][j] + 1 -> {
                    // Deletion
                    deletions++
                    i--
                }

                j > 0 && dp[i][j] == dp[i][j - 1] + 1 -> {
                    // Insertion
                    insertions++
                    j--
                }

                i > 0 -> {
                    deletions++
                    i--
                }

                j > 0 -> {
                    insertions++
                    j--
                }

                else -> break
            }
        }

        return Triple(substitutions, deletions, insertions)
    }

    /**
     * Compute detailed alignment with operation sequence.
     */
    private fun computeAlignment(
        reference: List<String>,
        hypothesis: List<String>
    ): Alignment {
        val n = reference.size
        val m = hypothesis.size

        if (n == 0 && m == 0) {
            return Alignment(emptyList(), emptyList(), emptyList())
        }

        // Build DP matrix
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j

        for (i in 1..n) {
            for (j in 1..m) {
                val cost = if (reference[i - 1] == hypothesis[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        // Backtrack to build operations
        val operations = mutableListOf<EditOperation>()
        var i = n
        var j = m

        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] &&
                        reference[i - 1] == hypothesis[j - 1] -> {
                    operations.add(EditOperation.Match(i - 1, reference[i - 1]))
                    i--
                    j--
                }

                i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1 -> {
                    operations.add(
                        EditOperation.Substitution(i - 1, reference[i - 1], hypothesis[j - 1])
                    )
                    i--
                    j--
                }

                i > 0 && dp[i][j] == dp[i - 1][j] + 1 -> {
                    operations.add(EditOperation.Deletion(i - 1, reference[i - 1]))
                    i--
                }

                j > 0 && dp[i][j] == dp[i][j - 1] + 1 -> {
                    operations.add(EditOperation.Insertion(i, hypothesis[j - 1]))
                    j--
                }

                i > 0 -> {
                    operations.add(EditOperation.Deletion(i - 1, reference[i - 1]))
                    i--
                }

                j > 0 -> {
                    operations.add(EditOperation.Insertion(i, hypothesis[j - 1]))
                    j--
                }

                else -> break
            }
        }

        return Alignment(
            referenceTokens = reference,
            hypothesisTokens = hypothesis,
            operations = operations.reversed()
        )
    }

    /**
     * Tokenize text into words.
     */
    private fun tokenize(text: String, normalize: Boolean): List<String> {
        val processed = if (normalize) {
            text.lowercase()
                .replace(Regex("[^a-z0-9\\s]"), "")
                .trim()
        } else {
            text.trim()
        }

        return if (processed.isEmpty()) {
            emptyList()
        } else {
            processed.split(Regex("\\s+"))
        }
    }

    /**
     * Prepare text for character-level comparison.
     */
    private fun prepareCharacters(text: String, normalize: Boolean): List<String> {
        val processed = if (normalize) {
            text.lowercase()
                .replace(Regex("\\s+"), "")
        } else {
            text
        }

        return processed.map { it.toString() }
    }
}
