package com.hiringai.mobile.benchmark.accuracy.metrics

/**
 * Top-K Accuracy calculator for image classification evaluation.
 *
 * Supports Top-1 and Top-5 accuracy metrics commonly used in
 * image classification benchmarks like ImageNet.
 */
class TopKAccuracy {

    /**
     * Calculate Top-1 accuracy for a single prediction.
     *
     * @param predictedIndex The index of the predicted class
     * @param groundTruthIndex The index of the ground truth class
     * @return 1 if correct, 0 if incorrect
     */
    fun top1Accuracy(predictedIndex: Int, groundTruthIndex: Int): Int {
        return if (predictedIndex == groundTruthIndex) 1 else 0
    }

    /**
     * Calculate Top-1 accuracy from probability distribution.
     *
     * @param probabilities Array of class probabilities
     * @param groundTruthIndex The index of the ground truth class
     * @return 1 if highest probability matches ground truth, 0 otherwise
     */
    fun top1Accuracy(probabilities: FloatArray, groundTruthIndex: Int): Int {
        require(probabilities.isNotEmpty()) { "Probabilities array cannot be empty" }
        require(groundTruthIndex in probabilities.indices) {
            "Ground truth index $groundTruthIndex out of bounds for array size ${probabilities.size}"
        }

        val predictedIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
        return if (predictedIndex == groundTruthIndex) 1 else 0
    }

    /**
     * Calculate Top-K accuracy for a single prediction.
     *
     * @param probabilities Array of class probabilities
     * @param groundTruthIndex The index of the ground truth class
     * @param k Number of top predictions to consider
     * @return 1 if ground truth is in top K predictions, 0 otherwise
     */
    fun topKAccuracy(probabilities: FloatArray, groundTruthIndex: Int, k: Int): Int {
        require(probabilities.isNotEmpty()) { "Probabilities array cannot be empty" }
        require(k > 0) { "K must be positive" }
        require(groundTruthIndex in probabilities.indices) {
            "Ground truth index $groundTruthIndex out of bounds for array size ${probabilities.size}"
        }

        val effectiveK = minOf(k, probabilities.size)

        // Get indices of top K probabilities
        val topKIndices = probabilities.indices
            .sortedByDescending { probabilities[it] }
            .take(effectiveK)

        return if (groundTruthIndex in topKIndices) 1 else 0
    }

    /**
     * Calculate Top-K accuracy from sorted prediction indices.
     *
     * @param topKPredictions Array of predicted class indices sorted by confidence (highest first)
     * @param groundTruthIndex The index of the ground truth class
     * @return 1 if ground truth is in predictions, 0 otherwise
     */
    fun topKAccuracy(topKPredictions: IntArray, groundTruthIndex: Int): Int {
        return if (groundTruthIndex in topKPredictions) 1 else 0
    }

    /**
     * Evaluate a batch of predictions and return accuracy metrics.
     *
     * @param predictions List of probability arrays for each sample
     * @param groundTruth List of ground truth class indices
     * @param kValues List of K values to compute (default: [1, 5])
     * @return BatchAccuracyResult with accuracy for each K value
     */
    fun evaluateBatch(
        predictions: List<FloatArray>,
        groundTruth: List<Int>,
        kValues: List<Int> = listOf(1, 5)
    ): BatchAccuracyResult {
        require(predictions.size == groundTruth.size) {
            "Predictions and ground truth must have same size. Got ${predictions.size} vs ${groundTruth.size}"
        }
        require(predictions.isNotEmpty()) { "Cannot evaluate empty batch" }

        val results = mutableMapOf<Int, TopKResult>()
        val sampleResults = mutableListOf<SampleAccuracyResult>()

        for (k in kValues) {
            results[k] = TopKResult(k, 0, 0)
        }

        predictions.indices.forEach { index ->
            val probs = predictions[index]
            val truth = groundTruth[index]

            val sampleCorrect = mutableMapOf<Int, Boolean>()

            for (k in kValues) {
                val correct = topKAccuracy(probs, truth, k)
                results[k] = results[k]!!.copy(
                    correctCount = results[k]!!.correctCount + correct,
                    totalCount = results[k]!!.totalCount + 1
                )
                sampleCorrect[k] = (correct == 1)
            }

            // Store top-1 prediction for sample result
            val top1Prediction = probs.indices.maxByOrNull { probs[it] } ?: -1
            sampleResults.add(
                SampleAccuracyResult(
                    sampleIndex = index,
                    predictedClass = top1Prediction,
                    groundTruthClass = truth,
                    topKCorrect = sampleCorrect.toMap()
                )
            )
        }

        return BatchAccuracyResult(
            totalCount = predictions.size,
            kResults = results.toMap(),
            sampleResults = sampleResults
        )
    }

    /**
     * Evaluate batch using pre-computed top predictions.
     *
     * @param topPredictions List of (top-1 prediction, top-5 predictions) for each sample
     * @param groundTruth List of ground truth class indices
     * @return BatchAccuracyResult
     */
    fun evaluateBatchWithPredictions(
        topPredictions: List<Pair<Int, IntArray>>,
        groundTruth: List<Int>
    ): BatchAccuracyResult {
        require(topPredictions.size == groundTruth.size) {
            "Predictions and ground truth must have same size"
        }
        require(topPredictions.isNotEmpty()) { "Cannot evaluate empty batch" }

        var top1Correct = 0
        var top5Correct = 0

        val sampleResults = topPredictions.indices.map { index ->
            val (top1, top5) = topPredictions[index]
            val truth = groundTruth[index]

            val isTop1Correct = top1 == truth
            val isTop5Correct = truth in top5

            if (isTop1Correct) top1Correct++
            if (isTop5Correct) top5Correct++

            SampleAccuracyResult(
                sampleIndex = index,
                predictedClass = top1,
                groundTruthClass = truth,
                topKCorrect = mapOf(1 to isTop1Correct, 5 to isTop5Correct)
            )
        }

        return BatchAccuracyResult(
            totalCount = topPredictions.size,
            kResults = mapOf(
                1 to TopKResult(1, top1Correct, topPredictions.size),
                5 to TopKResult(5, top5Correct, topPredictions.size)
            ),
            sampleResults = sampleResults
        )
    }

    /**
     * Stream evaluation for large datasets.
     * Call for each sample, then get results.
     */
    class StreamingEvaluator(private val kValues: List<Int> = listOf(1, 5)) {
        private val correctCounts = mutableMapOf<Int, Int>()
        private var totalCount = 0

        init {
            kValues.forEach { correctCounts[it] = 0 }
        }

        /**
         * Add a single evaluation.
         */
        fun add(probabilities: FloatArray, groundTruthIndex: Int) {
            for (k in kValues) {
                val accuracy = TopKAccuracy().topKAccuracy(probabilities, groundTruthIndex, k)
                correctCounts[k] = correctCounts[k]!! + accuracy
            }
            totalCount++
        }

        /**
         * Get current accuracy results.
         */
        fun getResults(): Map<Int, Double> {
            if (totalCount == 0) return kValues.associateWith { 0.0 }
            return correctCounts.mapValues { (_, count) ->
                count.toDouble() / totalCount
            }
        }

        /**
         * Get detailed results.
         */
        fun getDetailedResults(): Map<Int, TopKResult> {
            return correctCounts.mapValues { (k, count) ->
                TopKResult(k, count, totalCount)
            }
        }

        /**
         * Reset the evaluator.
         */
        fun reset() {
            kValues.forEach { correctCounts[it] = 0 }
            totalCount = 0
        }
    }

    /**
     * Get top K class indices from probability array.
     *
     * @param probabilities Array of class probabilities
     * @param k Number of top predictions to return
     * @return IntArray of top K class indices sorted by probability (descending)
     */
    fun getTopKPredictions(probabilities: FloatArray, k: Int): IntArray {
        require(probabilities.isNotEmpty()) { "Probabilities array cannot be empty" }
        require(k > 0) { "K must be positive" }

        val effectiveK = minOf(k, probabilities.size)
        return probabilities.indices
            .sortedByDescending { probabilities[it] }
            .take(effectiveK)
            .toIntArray()
    }

    /**
     * Get confidence scores for top K predictions.
     *
     * @param probabilities Array of class probabilities
     * @param k Number of top predictions
     * @return List of (classIndex, confidence) pairs
     */
    fun getTopKWithConfidence(probabilities: FloatArray, k: Int): List<Pair<Int, Float>> {
        require(probabilities.isNotEmpty()) { "Probabilities array cannot be empty" }
        require(k > 0) { "K must be positive" }

        val effectiveK = minOf(k, probabilities.size)
        return probabilities.indices
            .sortedByDescending { probabilities[it] }
            .take(effectiveK)
            .map { index -> Pair(index, probabilities[index]) }
    }
}

/**
 * Result for a specific K value.
 */
data class TopKResult(
    val k: Int,
    val correctCount: Int,
    val totalCount: Int
) {
    /**
     * Calculate accuracy as percentage.
     */
    val accuracy: Double
        get() = if (totalCount == 0) 0.0 else correctCount.toDouble() / totalCount

    /**
     * Accuracy as percentage (0-100).
     */
    val accuracyPercent: Double
        get() = accuracy * 100

    /**
     * Format as human-readable string.
     */
    fun toFormattedString(): String {
        return "Top-$k Accuracy: ${"%.2f".format(accuracyPercent)}% ($correctCount/$totalCount)"
    }
}

/**
 * Result for a single sample in batch evaluation.
 */
data class SampleAccuracyResult(
    val sampleIndex: Int,
    val predictedClass: Int,
    val groundTruthClass: Int,
    val topKCorrect: Map<Int, Boolean>
) {
    /**
     * Check if prediction was correct at specific K.
     */
    fun isCorrectAtK(k: Int): Boolean = topKCorrect[k] ?: false

    /**
     * Check if this was a top-1 error.
     */
    val isTop1Error: Boolean
        get() = topKCorrect[1] == false
}

/**
 * Complete batch evaluation result.
 */
data class BatchAccuracyResult(
    val totalCount: Int,
    val kResults: Map<Int, TopKResult>,
    val sampleResults: List<SampleAccuracyResult>
) {
    /**
     * Get Top-1 accuracy.
     */
    val top1Accuracy: Double
        get() = kResults[1]?.accuracy ?: 0.0

    /**
     * Get Top-5 accuracy.
     */
    val top5Accuracy: Double
        get() = kResults[5]?.accuracy ?: 0.0

    /**
     * Get count of samples where top-5 was correct but top-1 was wrong.
     */
    val top5ButNotTop1Count: Int
        get() = sampleResults.count { !it.isCorrectAtK(1) && it.isCorrectAtK(5) }

    /**
     * Get misclassified samples.
     */
    val errors: List<SampleAccuracyResult>
        get() = sampleResults.filter { !it.isCorrectAtK(1) }

    /**
     * Get summary report.
     */
    fun getSummary(): String {
        return buildString {
            appendLine("Classification Accuracy Results:")
            appendLine("  Total samples: $totalCount")
            for ((k, result) in kResults.toSortedMap()) {
                appendLine("  ${result.toFormattedString()}")
            }
            appendLine("  Top-5 but not Top-1: $top5ButNotTop1Count samples")
        }
    }

    /**
     * Export results to CSV format.
     */
    fun toCsv(): String {
        return buildString {
            appendLine("sample_index,predicted,ground_truth,top1_correct,top5_correct")
            sampleResults.forEach { sample ->
                appendLine("${sample.sampleIndex},${sample.predictedClass},${sample.groundTruthClass},${sample.isCorrectAtK(1)},${sample.isCorrectAtK(5)}")
            }
        }
    }
}
