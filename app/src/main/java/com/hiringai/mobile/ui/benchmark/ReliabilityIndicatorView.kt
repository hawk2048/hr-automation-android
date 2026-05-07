package com.hiringai.mobile.ui.benchmark

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.hiringai.mobile.R
import com.hiringai.mobile.benchmark.ResultReliabilityAnalyzer
import com.hiringai.mobile.benchmark.ResultReliabilityAnalyzer.ReliabilityLevel
import com.hiringai.mobile.benchmark.ResultReliabilityAnalyzer.ReliabilityMetrics

class ReliabilityIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var tvScore: TextView
    private lateinit var tvLevel: TextView
    private lateinit var tvMean: TextView
    private lateinit var tvStdDev: TextView
    private lateinit var tvCV: TextView
    private lateinit var tvOutliers: TextView
    private lateinit var tvConfidence: TextView

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.layout_reliability_indicator, this, true)
        initViews()
    }

    private fun initViews() {
        tvScore = findViewById(R.id.tv_reliability_score)
        tvLevel = findViewById(R.id.tv_reliability_level)
        tvMean = findViewById(R.id.tv_mean)
        tvStdDev = findViewById(R.id.tv_std_dev)
        tvCV = findViewById(R.id.tv_cv)
        tvOutliers = findViewById(R.id.tv_outliers)
        tvConfidence = findViewById(R.id.tv_confidence)
    }

    fun setMetrics(metrics: ReliabilityMetrics) {
        tvScore.text = "${metrics.reliabilityScore}/100"
        tvLevel.text = ResultReliabilityAnalyzer.getReliabilityLevelDescription(metrics.reliabilityLevel)
        tvLevel.setTextColor(ResultReliabilityAnalyzer.getReliabilityLevelColor(metrics.reliabilityLevel))

        tvMean.text = "平均值: ${String.format("%.2f", metrics.mean)}"
        tvStdDev.text = "标准差: ${String.format("%.2f", metrics.standardDeviation)}"
        tvCV.text = "变异系数: ${String.format("%.1f%%", metrics.cv)}"
        tvOutliers.text = "异常值: ${metrics.outliers.size}"
        tvConfidence.text = "置信区间: [${String.format("%.2f", metrics.confidenceInterval.first)}, ${String.format("%.2f", metrics.confidenceInterval.second)}]"
    }

    fun setResults(results: List<Double>) {
        val metrics = ResultReliabilityAnalyzer.analyze(results)
        setMetrics(metrics)
    }

    fun clear() {
        tvScore.text = "--/100"
        tvLevel.text = "无数据"
        tvLevel.setTextColor(context.getColor(R.color.text_secondary))
        tvMean.text = "平均值: --"
        tvStdDev.text = "标准差: --"
        tvCV.text = "变异系数: --"
        tvOutliers.text = "异常值: --"
        tvConfidence.text = "置信区间: --"
    }
}
