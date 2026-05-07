package com.hiringai.mobile.ui.benchmark

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.hiringai.mobile.R

class BenchmarkChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var barChart: BarChart
    private lateinit var lineChart: LineChart

    enum class ChartType {
        BAR,
        LINE
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.layout_benchmark_chart, this, true)
        barChart = findViewById(R.id.bar_chart)
        lineChart = findViewById(R.id.line_chart)
        setupBarChart()
        setupLineChart()
    }

    private fun setupBarChart() {
        barChart.description.isEnabled = false
        barChart.setPinchZoom(false)
        barChart.setDrawBarShadow(false)
        barChart.setDrawGridBackground(false)

        barChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            textSize = 10f
        }

        barChart.axisLeft.apply {
            setDrawGridLines(true)
            granularity = 1f
            textSize = 10f
        }

        barChart.axisRight.isEnabled = false
        barChart.legend.isEnabled = true
        barChart.legend.textSize = 10f
    }

    private fun setupLineChart() {
        lineChart.description.isEnabled = false
        lineChart.setPinchZoom(true)
        lineChart.setDrawGridBackground(false)

        lineChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            textSize = 10f
        }

        lineChart.axisLeft.apply {
            setDrawGridLines(true)
            granularity = 1f
            textSize = 10f
        }

        lineChart.axisRight.isEnabled = false
        lineChart.legend.isEnabled = true
        lineChart.legend.textSize = 10f
        lineChart.isDoubleTapToZoomEnabled = false
    }

    fun showChart(type: ChartType, chartData: BenchmarkChartData) {
        barChart.visibility = if (type == ChartType.BAR) VISIBLE else GONE
        lineChart.visibility = if (type == ChartType.LINE) VISIBLE else GONE

        when (type) {
            ChartType.BAR -> populateBarChart(chartData)
            ChartType.LINE -> populateLineChart(chartData)
        }
    }

    private fun populateBarChart(chartData: BenchmarkChartData) {
        val entries = mutableListOf<BarEntry>()
        chartData.values.forEachIndexed { index, value ->
            entries.add(BarEntry(index.toFloat(), value))
        }

        val dataSet = BarDataSet(entries, chartData.label).apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueTextSize = 10f
            valueTextColor = android.graphics.Color.BLACK
        }

        val barData = BarData(dataSet)
        barData.barWidth = 0.6f

        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(chartData.labels)
        barChart.data = barData
        barChart.invalidate()
    }

    private fun populateLineChart(chartData: BenchmarkChartData) {
        val entries = mutableListOf<Entry>()
        chartData.values.forEachIndexed { index, value ->
            entries.add(Entry(index.toFloat(), value))
        }

        val dataSet = LineDataSet(entries, chartData.label).apply {
            color = android.graphics.Color.BLUE
            valueTextSize = 10f
            valueTextColor = android.graphics.Color.BLACK
            lineWidth = 2f
            circleRadius = 4f
            setDrawCircleHole(false)
        }

        val lineData = LineData(dataSet)

        lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(chartData.labels)
        lineChart.data = lineData
        lineChart.invalidate()
    }

    data class BenchmarkChartData(
        val label: String,
        val labels: List<String>,
        val values: List<Float>
    )
}
