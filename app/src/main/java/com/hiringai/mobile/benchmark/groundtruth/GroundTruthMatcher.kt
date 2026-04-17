package com.hiringai.mobile.benchmark.groundtruth

import android.graphics.RectF

/**
 * Ground Truth Matcher for AI Model Evaluation
 *
 * Matches predictions against ground truth labels and computes accuracy metrics.
 * Supports vision (object detection/classification), speech (transcription), and text tasks.
 */
object GroundTruthMatcher {

    // =========================================================================
    // Vision Tasks - Object Detection Matching
    // =========================================================================

    /**
     * Ground truth for object detection
     */
    data class DetectionGroundTruth(
        val boundingBox: RectF,
        val label: String,
        val isDifficult: Boolean = false
    )

    /**
     * Predicted detection result
     */
    data class DetectionPrediction(
        val boundingBox: RectF,
        val label: String,
        val confidence: Float
    )

    /**
     * Detection matching result
     */
    data class DetectionMatchResult(
        val truePositives: Int,
        val falsePositives: Int,
        val falseNegatives: Int,
        val precision: Float,
        val recall: Float,
        val f1Score: Float,
        val mAP: Float, // mean Average Precision
        val matchedPredictions: List<MatchedPrediction>
    )

    /**
     * A prediction matched to ground truth
     */
    data class MatchedPrediction(
        val prediction: DetectionPrediction,
        val groundTruth: DetectionGroundTruth?,
        val iou: Float,
        val isCorrect: Boolean
    )

    /**
     * Match detection predictions to ground truth using IoU threshold
     *
     * @param predictions List of predicted detections
     * @param groundTruths List of ground truth detections
     * @param iouThreshold IoU threshold for matching (default 0.5)
     * @param confidenceThreshold Minimum confidence to consider (default 0.5)
     * @return DetectionMatchResult with precision, recall, F1, and mAP
     */
    fun matchDetections(
        predictions: List<DetectionPrediction>,
        groundTruths: List<DetectionGroundTruth>,
        iouThreshold: Float = 0.5f,
        confidenceThreshold: Float = 0.5f
    ): DetectionMatchResult {
        // Filter predictions by confidence
        val filteredPredictions = predictions
            .filter { it.confidence >= confidenceThreshold }
            .sortedByDescending { it.confidence }

        val matchedGTs = mutableSetOf<Int>()
        val matchedPredictions = mutableListOf<MatchedPrediction>()
        var truePositives = 0
        var falsePositives = 0

        for (prediction in filteredPredictions) {
            var bestIoU = 0f
            var bestGTIndex = -1

            // Find best matching ground truth
            for ((gtIndex, gt) in groundTruths.withIndex()) {
                if (gt.label != prediction.label) continue
                if (gtIndex in matchedGTs) continue
                if (gt.isDifficult) continue // Skip difficult examples in standard evaluation

                val iou = calculateIoU(prediction.boundingBox, gt.boundingBox)
                if (iou > bestIoU) {
                    bestIoU = iou
                    bestGTIndex = gtIndex
                }
            }

            if (bestIoU >= iouThreshold && bestGTIndex >= 0) {
                matchedGTs.add(bestGTIndex)
                truePositives++
                matchedPredictions.add(
                    MatchedPrediction(
                        prediction = prediction,
                        groundTruth = groundTruths[bestGTIndex],
                        iou = bestIoU,
                        isCorrect = true
                    )
                )
            } else {
                falsePositives++
                matchedPredictions.add(
                    MatchedPrediction(
                        prediction = prediction,
                        groundTruth = null,
                        iou = bestIoU,
                        isCorrect = false
                    )
                )
            }
        }

        val falseNegatives = groundTruths.count { !it.isDifficult } - matchedGTs.size

        val precision = if (truePositives + falsePositives > 0) {
            truePositives.toFloat() / (truePositives + falsePositives)
        } else 0f

        val recall = if (groundTruths.isNotEmpty()) {
            truePositives.toFloat() / (groundTruths.count { !it.isDifficult })
        } else 0f

        val f1Score = if (precision + recall > 0) {
            2 * precision * recall / (precision + recall)
        } else 0f

        // Calculate mAP (simplified - uses single IoU threshold)
        val mAP = calculateMAP(filteredPredictions, groundTruths, iouThreshold)

        return DetectionMatchResult(
            truePositives = truePositives,
            falsePositives = falsePositives,
            falseNegatives = falseNegatives,
            precision = precision,
            recall = recall,
            f1Score = f1Score,
            mAP = mAP,
            matchedPredictions = matchedPredictions
        )
    }

    /**
     * Calculate Intersection over Union (IoU) for two bounding boxes
     */
    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersectLeft = maxOf(box1.left, box2.left)
        val intersectTop = maxOf(box1.top, box2.top)
        val intersectRight = minOf(box1.right, box2.right)
        val intersectBottom = minOf(box1.bottom, box2.bottom)

        if (intersectRight < intersectLeft || intersectBottom < intersectTop) {
            return 0f // No intersection
        }

        val intersectionArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
        val box1Area = box1.width() * box1.height()
        val box2Area = box2.width() * box2.height()
        val unionArea = box1Area + box2Area - intersectionArea

        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }

    /**
     * Calculate mean Average Precision (mAP)
     */
    private fun calculateMAP(
        predictions: List<DetectionPrediction>,
        groundTruths: List<DetectionGroundTruth>,
        iouThreshold: Float
    ): Float {
        // Group by class label
        val gtByLabel = groundTruths.groupBy { it.label }
        val predByLabel = predictions.groupBy { it.label }

        var totalAP = 0f
        var labelCount = 0

        for ((label, gtsForLabel) in gtByLabel) {
            val predsForLabel = predByLabel[label]?.sortedByDescending { it.confidence } ?: emptyList()
            val ap = calculateAPForClass(predsForLabel, gtsForLabel, iouThreshold)
            totalAP += ap
            labelCount++
        }

        return if (labelCount > 0) totalAP / labelCount else 0f
    }

    /**
     * Calculate Average Precision for a single class
     */
    private fun calculateAPForClass(
        predictions: List<DetectionPrediction>,
        groundTruths: List<DetectionGroundTruth>,
        iouThreshold: Float
    ): Float {
        val matchedGTs = mutableSetOf<Int>()
        val precisions = mutableListOf<Float>()
        val recalls = mutableListOf<Float>()

        var truePositives = 0

        for (prediction in predictions) {
            var matched = false
            for ((gtIndex, gt) in groundTruths.withIndex()) {
                if (gtIndex in matchedGTs) continue
                val iou = calculateIoU(prediction.boundingBox, gt.boundingBox)
                if (iou >= iouThreshold) {
                    matchedGTs.add(gtIndex)
                    matched = true
                    break
                }
            }

            if (matched) truePositives++

            val precision = if (predictions.indexOf(prediction) + 1 > 0) {
                truePositives.toFloat() / (predictions.indexOf(prediction) + 1)
            } else 0f

            val recall = if (groundTruths.isNotEmpty()) {
                matchedGTs.size.toFloat() / groundTruths.size
            } else 0f

            precisions.add(precision)
            recalls.add(recall)
        }

        // Interpolate precision at standard recall levels
        var ap = 0f
        for (r in 0..10) {
            val recallThreshold = r / 10f
            val precisionAtRecall = precisions
                .filterIndexed { i, _ -> recalls[i] >= recallThreshold }
                .maxOrNull() ?: 0f
            ap += precisionAtRecall
        }
        ap /= 11f

        return ap
    }

    // =========================================================================
    // Vision Tasks - Classification Matching
    // =========================================================================

    /**
     * Classification match result
     */
    data class ClassificationMatchResult(
        val correct: Int,
        val total: Int,
        val accuracy: Float,
        val topKAccuracy: Map<Int, Float>, // Top-1, Top-3, Top-5 accuracy
        val perClassAccuracy: Map<String, Float>,
        val confusionMatrix: Map<String, Map<String, Int>>
    )

    /**
     * Match classification predictions to ground truth labels
     *
     * @param predictions List of predicted labels (or top-k predictions per sample)
     * @param groundTruths List of ground truth labels
     * @param topKPredictions Optional: top-k predictions per sample for top-k accuracy
     * @return ClassificationMatchResult with accuracy metrics
     */
    fun matchClassifications(
        predictions: List<String>,
        groundTruths: List<String>,
        topKPredictions: List<List<String>>? = null
    ): ClassificationMatchResult {
        require(predictions.size == groundTruths.size) {
            "Predictions and ground truths must have same size"
        }

        var correct = 0
        val confusionMatrix = mutableMapOf<String, MutableMap<String, Int>>()
        val classCorrect = mutableMapOf<String, Int>()
        val classTotal = mutableMapOf<String, Int>()

        for ((pred, gt) in predictions.zip(groundTruths)) {
            // Update confusion matrix
            confusionMatrix.getOrPut(gt) { mutableMapOf() }
                .merge(pred, 1) { old, new -> old + new }!!

            // Update class counts
            classTotal.merge(gt, 1) { old, new -> old + new }
            if (pred == gt) {
                correct++
                classCorrect.merge(gt, 1) { old, new -> old + new }
            }
        }

        val accuracy = if (predictions.isNotEmpty()) {
            correct.toFloat() / predictions.size
        } else 0f

        // Calculate top-k accuracy if available
        val topKAccuracy = mutableMapOf<Int, Float>()
        if (topKPredictions != null && topKPredictions.size == groundTruths.size) {
            for (k in listOf(1, 3, 5)) {
                var topKCorrect = 0
                for ((topPreds, gt) in topKPredictions.zip(groundTruths)) {
                    if (gt in topPreds.take(k)) {
                        topKCorrect++
                    }
                }
                topKAccuracy[k] = if (groundTruths.isNotEmpty()) {
                    topKCorrect.toFloat() / groundTruths.size
                } else 0f
            }
        } else {
            // Top-1 is just regular accuracy
            topKAccuracy[1] = accuracy
        }

        // Calculate per-class accuracy
        val perClassAccuracy = classTotal.mapValues { (label, total) ->
            (classCorrect[label] ?: 0).toFloat() / total
        }

        // Convert confusion matrix to immutable
        val immutableConfusionMatrix = confusionMatrix.mapValues { (_, inner) ->
            inner.toMap()
        }

        return ClassificationMatchResult(
            correct = correct,
            total = predictions.size,
            accuracy = accuracy,
            topKAccuracy = topKAccuracy,
            perClassAccuracy = perClassAccuracy,
            confusionMatrix = immutableConfusionMatrix
        )
    }

    // =========================================================================
    // Vision Tasks - Label Matching (ImageNet style)
    // =========================================================================

    /**
     * Match predicted labels to ground truth for image classification
     *
     * @param predictedLabels Predicted class indices or names
     * @param groundTruthLabels Ground truth class indices or names
     * @param labelNames Optional mapping from index to name
     * @return Classification accuracy metrics
     */
    fun matchLabels(
        predictedLabels: List<Int>,
        groundTruthLabels: List<Int>,
        labelNames: Map<Int, String>? = null
    ): ClassificationMatchResult {
        val predNames = if (labelNames != null) {
            predictedLabels.map { labelNames[it] ?: it.toString() }
        } else {
            predictedLabels.map { it.toString() }
        }

        val gtNames = if (labelNames != null) {
            groundTruthLabels.map { labelNames[it] ?: it.toString() }
        } else {
            groundTruthLabels.map { it.toString() }
        }

        return matchClassifications(predNames, gtNames)
    }

    // =========================================================================
    // Speech Tasks - Transcription Matching
    // =========================================================================

    /**
     * Speech transcription match result
     */
    data class TranscriptionMatchResult(
        val wer: Float,              // Word Error Rate
        val cer: Float,              // Character Error Rate
        val mer: Float,              // Match Error Rate
        val wil: Float,              // Word Information Lost
        val wip: Float,              // Word Information Preserved
        val totalWords: Int,
        val totalChars: Int,
        val substitutions: Int,
        val deletions: Int,
        val insertions: Int,
        val alignedReference: String,
        val alignedHypothesis: String
    )

    /**
     * Match speech transcription predictions to ground truth
     *
     * Uses dynamic programming to compute Word Error Rate (WER) and related metrics.
     *
     * @param hypothesis Predicted transcription
     * @param reference Ground truth transcription
     * @return TranscriptionMatchResult with WER, CER, and alignment
     */
    fun matchTranscription(
        hypothesis: String,
        reference: String
    ): TranscriptionMatchResult {
        val hypWords = hypothesis.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val refWords = reference.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

        // Compute edit distance with alignment
        val (distance, alignment) = computeEditDistanceWithAlignment(hypWords, refWords)

        val totalWords = refWords.size
        val (substitutions, deletions, insertions) = alignment

        val wer = if (totalWords > 0) {
            (substitutions + deletions + insertions).toFloat() / totalWords
        } else if (hypWords.isNotEmpty()) 1f else 0f

        // Calculate MER (Match Error Rate)
        val matches = totalWords - substitutions - deletions
        val mer = if (substitutions + deletions + insertions + matches > 0) {
            (substitutions + deletions + insertions).toFloat() /
                (substitutions + deletions + insertions + matches)
        } else 0f

        // Calculate WIL (Word Information Lost)
        val wil = 1f - (matches.toFloat() / totalWords.coerceAtLeast(1)) *
            (matches.toFloat() / (matches + insertions).coerceAtLeast(1))

        // Calculate WIP (Word Information Preserved)
        val wip = (matches.toFloat() / totalWords.coerceAtLeast(1)) *
            (matches.toFloat() / (matches + insertions).coerceAtLeast(1))

        // Calculate CER (Character Error Rate)
        val cer = calculateCER(hypothesis, reference)

        // Generate aligned strings for visualization
        val (alignedRef, alignedHyp) = generateAlignmentVisualization(hypWords, refWords, alignment)

        return TranscriptionMatchResult(
            wer = wer,
            cer = cer,
            mer = mer,
            wil = wil,
            wip = wip,
            totalWords = totalWords,
            totalChars = reference.length,
            substitutions = substitutions,
            deletions = deletions,
            insertions = insertions,
            alignedReference = alignedRef,
            alignedHypothesis = alignedHyp
        )
    }

    /**
     * Compute edit distance with alignment tracking
     */
    private fun computeEditDistanceWithAlignment(
        hyp: List<String>,
        ref: List<String>
    ): Pair<Int, Triple<Int, Int, Int>> {
        val m = hyp.size
        val n = ref.size

        // DP table
        val dp = Array(m + 1) { IntArray(n + 1) }

        // Initialize
        for (i in 0..m) dp[i][0] = i // Deletions from hyp
        for (j in 0..n) dp[0][j] = j // Insertions to match ref

        // Fill DP table
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (hyp[i - 1] == ref[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // Deletion
                    dp[i][j - 1] + 1,      // Insertion
                    dp[i - 1][j - 1] + cost // Substitution or match
                )
            }
        }

        // Backtrack to count operations
        var i = m
        var j = n
        var substitutions = 0
        var deletions = 0
        var insertions = 0

        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + if (hyp[i - 1] == ref[j - 1]) 0 else 1 -> {
                    if (hyp[i - 1] != ref[j - 1]) substitutions++
                    i--
                    j--
                }
                i > 0 && dp[i][j] == dp[i - 1][j] + 1 -> {
                    deletions++  // Word in hyp not in ref
                    i--
                }
                j > 0 -> {
                    insertions++  // Word in ref not in hyp
                    j--
                }
                else -> break
            }
        }

        return Pair(dp[m][n], Triple(substitutions, deletions, insertions))
    }

    /**
     * Calculate Character Error Rate
     */
    private fun calculateCER(hypothesis: String, reference: String): Float {
        val hypChars = hypothesis.lowercase().replace(Regex("\\s+"), "").toList()
        val refChars = reference.lowercase().replace(Regex("\\s+"), "").toList()

        if (refChars.isEmpty()) return if (hypChars.isEmpty()) 0f else 1f

        val dp = IntArray(hypChars.size + 1) { it }
        for (j in 1..refChars.size) {
            var prev = dp[0]
            dp[0] = j
            for (i in 1..hypChars.size) {
                val temp = dp[i]
                dp[i] = minOf(
                    dp[i] + 1,
                    dp[i - 1] + 1,
                    prev + if (hypChars[i - 1] == refChars[j - 1]) 0 else 1
                )
                prev = temp
            }
        }

        return dp[hypChars.size].toFloat() / refChars.size
    }

    /**
     * Generate aligned strings for visualization
     */
    private fun generateAlignmentVisualization(
        hyp: List<String>,
        ref: List<String>,
        alignment: Triple<Int, Int, Int>
    ): Pair<String, String> {
        val (subs, dels, ins) = alignment
        val alignedRef = StringBuilder()
        val alignedHyp = StringBuilder()

        var hypIdx = 0
        var refIdx = 0

        // Simplified alignment visualization
        while (hypIdx < hyp.size || refIdx < ref.size) {
            when {
                hypIdx < hyp.size && refIdx < ref.size && hyp[hypIdx] == ref[refIdx] -> {
                    alignedRef.append(ref[refIdx]).append(" ")
                    alignedHyp.append(hyp[hypIdx]).append(" ")
                    hypIdx++
                    refIdx++
                }
                hypIdx < hyp.size && refIdx < ref.size -> {
                    alignedRef.append("[${ref[refIdx]}]").append(" ")
                    alignedHyp.append("[${hyp[hypIdx]}]").append(" ")
                    hypIdx++
                    refIdx++
                }
                hypIdx < hyp.size -> {
                    alignedRef.append("*** ")
                    alignedHyp.append("[${hyp[hypIdx]}] ")
                    hypIdx++
                }
                refIdx < ref.size -> {
                    alignedRef.append("[${ref[refIdx]}] ")
                    alignedHyp.append("*** ")
                    refIdx++
                }
            }
        }

        return Pair(alignedRef.toString().trim(), alignedHyp.toString().trim())
    }

    // =========================================================================
    // Text Tasks - Reference Matching
    // =========================================================================

    /**
     * Text reference match result
     */
    data class TextMatchResult(
        val exactMatch: Boolean,
        val fuzzyMatchScore: Float,
        val bleuScore: Float,
        val rougeScore: Float,
        val editDistance: Int,
        val normalizedEditDistance: Float,
        val referenceLength: Int,
        val hypothesisLength: Int
    )

    /**
     * Match text predictions to reference text
     *
     * @param hypothesis Predicted text
     * @param reference Ground truth reference text
     * @return TextMatchResult with various similarity scores
     */
    fun matchText(
        hypothesis: String,
        reference: String
    ): TextMatchResult {
        val exactMatch = hypothesis.trim() == reference.trim()

        // Calculate edit distance
        val editDistance = calculateLevenshteinDistance(hypothesis, reference)
        val maxLen = maxOf(hypothesis.length, reference.length)
        val normalizedEditDistance = if (maxLen > 0) editDistance.toFloat() / maxLen else 0f

        // Fuzzy match score (0-1)
        val fuzzyMatchScore = 1f - normalizedEditDistance

        // BLEU score (simplified implementation)
        val bleuScore = calculateBleuScore(hypothesis, reference)

        // ROUGE score (simplified ROUGE-L)
        val rougeScore = calculateRougeScore(hypothesis, reference)

        return TextMatchResult(
            exactMatch = exactMatch,
            fuzzyMatchScore = fuzzyMatchScore,
            bleuScore = bleuScore,
            rougeScore = rougeScore,
            editDistance = editDistance,
            normalizedEditDistance = normalizedEditDistance,
            referenceLength = reference.length,
            hypothesisLength = hypothesis.length
        )
    }

    /**
     * Calculate Levenshtein edit distance
     */
    private fun calculateLevenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length

        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + if (s1[i - 1] == s2[j - 1]) 0 else 1
                )
            }
        }

        return dp[m][n]
    }

    /**
     * Calculate BLEU score (simplified)
     */
    private fun calculateBleuScore(hypothesis: String, reference: String): Float {
        val hypWords = hypothesis.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val refWords = reference.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

        if (hypWords.isEmpty() || refWords.isEmpty()) return 0f

        // Calculate n-gram precisions (up to 4-grams)
        var totalPrecision = 0f

        for (n in 1..4) {
            val hypNgrams = extractNgrams(hypWords, n)
            val refNgrams = extractNgrams(refWords, n).toMutableList()

            var matches = 0
            for (ngram in hypNgrams) {
                if (ngram in refNgrams) {
                    matches++
                    refNgrams.remove(ngram)
                }
            }

            val precision = if (hypNgrams.isNotEmpty()) {
                matches.toFloat() / hypNgrams.size
            } else 0f

            totalPrecision += precision
        }

        val avgPrecision = totalPrecision / 4f

        // Brevity penalty
        val bp = if (hypWords.size < refWords.size) {
            Math.exp(1.0 - refWords.size.toDouble() / hypWords.size).toFloat()
        } else 1f

        return bp * avgPrecision
    }

    /**
     * Extract n-grams from word list
     */
    private fun extractNgrams(words: List<String>, n: Int): List<String> {
        if (words.size < n) return emptyList()

        return (0..words.size - n).map { i ->
            words.subList(i, i + n).joinToString(" ")
        }
    }

    /**
     * Calculate ROUGE-L score
     */
    private fun calculateRougeScore(hypothesis: String, reference: String): Float {
        val hypWords = hypothesis.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val refWords = reference.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

        if (hypWords.isEmpty() || refWords.isEmpty()) return 0f

        // Find LCS (Longest Common Subsequence) length
        val lcsLength = findLCS(hypWords, refWords)

        // ROUGE-L F1 score
        val precision = if (hypWords.isNotEmpty()) lcsLength.toFloat() / hypWords.size else 0f
        val recall = if (refWords.isNotEmpty()) lcsLength.toFloat() / refWords.size else 0f

        return if (precision + recall > 0) {
            2 * precision * recall / (precision + recall)
        } else 0f
    }

    /**
     * Find Longest Common Subsequence length
     */
    private fun findLCS(s1: List<String>, s2: List<String>): Int {
        val m = s1.size
        val n = s2.size
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (s1[i - 1] == s2[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        return dp[m][n]
    }

    // =========================================================================
    // Batch Evaluation
    // =========================================================================

    /**
     * Batch evaluation result for multiple samples
     */
    data class BatchEvaluationResult(
        val visionResults: List<DetectionMatchResult>,
        val classificationResults: List<ClassificationMatchResult>,
        val transcriptionResults: List<TranscriptionMatchResult>,
        val textResults: List<TextMatchResult>,
        val averageMetrics: Map<String, Float>,
        val summary: String
    )

    /**
     * Generate evaluation summary
     */
    fun generateSummary(
        classificationResults: List<ClassificationMatchResult>,
        transcriptionResults: List<TranscriptionMatchResult>,
        detectionResults: List<DetectionMatchResult>
    ): String {
        return buildString {
            appendLine("=== Ground Truth Evaluation Summary ===")
            appendLine()

            if (classificationResults.isNotEmpty()) {
                val avgAccuracy = classificationResults.map { it.accuracy }.average().toFloat()
                appendLine("Classification:")
                appendLine("  Samples: ${classificationResults.size}")
                appendLine("  Average Accuracy: ${"%.2f".format(avgAccuracy * 100)}%")
                appendLine()
            }

            if (transcriptionResults.isNotEmpty()) {
                val avgWER = transcriptionResults.map { it.wer }.average().toFloat()
                val avgCER = transcriptionResults.map { it.cer }.average().toFloat()
                appendLine("Speech Transcription:")
                appendLine("  Samples: ${transcriptionResults.size}")
                appendLine("  Average WER: ${"%.2f".format(avgWER * 100)}%")
                appendLine("  Average CER: ${"%.2f".format(avgCER * 100)}%")
                appendLine()
            }

            if (detectionResults.isNotEmpty()) {
                val avgPrecision = detectionResults.map { it.precision }.average().toFloat()
                val avgRecall = detectionResults.map { it.recall }.average().toFloat()
                val avgF1 = detectionResults.map { it.f1Score }.average().toFloat()
                appendLine("Object Detection:")
                appendLine("  Samples: ${detectionResults.size}")
                appendLine("  Average Precision: ${"%.2f".format(avgPrecision * 100)}%")
                appendLine("  Average Recall: ${"%.2f".format(avgRecall * 100)}%")
                appendLine("  Average F1: ${"%.2f".format(avgF1 * 100)}%")
                appendLine()
            }
        }
    }
}
