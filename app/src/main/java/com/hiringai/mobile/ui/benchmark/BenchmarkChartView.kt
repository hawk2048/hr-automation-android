package com.hiringai.mobile.ui.benchmark

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.charts.RadarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.components.RadarAxisRenderer
import com.github.mikephil.charting.components.RadarChart
import com.github.mikephil.charting.data.*
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
    private lateinit var pieChart: PieChart
    private lateinit var radarChart: RadarChart

    enum class ChartType {
        BAR,
        LINE,
        PIE,
        RADAR
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.layout_benchmark_chart, this, true)
        barChart = findViewById(R.id.bar_chart)
        lineChart = findViewById(R.id.line_chart)
        pieChart = findViewById(R.id.pie_chart)
        radarChart = findViewById(R.id.radar_chart)
        setupBarChart()
        setupLineChart()
        setupPieChart()
        setupRadarChart()
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

    private fun setupPieChart() {
        pieChart.description.isEnabled = false
        pieChart.isDrawHoleEnabled = true
        pieChart.setHoleColor(android.graphics.Color.WHITE)
        pieChart.setTransparentCircleRadius(50f)
        pieChart.legend.isEnabled = true
        pieChart.legend.textSize = 10f
    }

    private fun setupRadarChart() {
        radarChart.description.isEnabled = false
        radarChart.setDrawWeb(true)
        radarChart.setWebColor(android.graphics.Color.LTGRAY)
        radarChart.setWebLineWidth(1f)
        radarChart.setWebAlpha(100)
        radarChart.legend.isEnabled = true
        radarChart.legend.textSize = 10f

        radarChart.yAxis.apply {
            axisMinimum = 0f
            axisMaximum = 100f
            textSize = 8f
        }

        radarChart.xAxis.apply {
            textSize = 10f
        }
    }

    fun showChart(type: ChartType, chartData: BenchmarkChartData) {
        barChart.visibility = GONE
        lineChart.visibility = GONE
        pieChart.visibility = GONE
        radarChart.visibility = GONE

        when (type) {
            ChartType.BAR -> {
                barChart.visibility = VISIBLE
                populateBarChart(chartData)
            }
            ChartType.LINE -> {
                lineChart.visibility = VISIBLE
                populateLineChart(chartData)
            }
            ChartType.PIE -> {
                pieChart.visibility = VISIBLE
                populatePieChart(chartData)
            }
            ChartType.RADAR -> {
                radarChart.visibility = VISIBLE
                populateRadarChart(chartData)
            }
        }
    }

    fun showMultiDataSetChart(type: ChartType, dataSets: List<BenchmarkChartData>) {
        barChart.visibility = GONE
        lineChart.visibility = GONE
        pieChart.visibility = GONE
        radarChart.visibility = GONE

        when (type) {
            ChartType.BAR -> {
                barChart.visibility = VISIBLE
                populateMultiBarChart(dataSets)
            }
            ChartType.LINE -> {
                lineChart.visibility = VISIBLE
                populateMultiLineChart(dataSets)
            }
            ChartType.RADAR -> {
                radarChart.visibility = VISIBLE
                populateMultiRadarChart(dataSets)
            }
            ChartType.PIE -> {
                pieChart.visibility = VISIBLE
                populatePieChart(dataSets.firstOrNull() ?: return)
            }
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

    private fun populateMultiBarChart(dataSets: List<BenchmarkChartData>) {
        val barDataSets = mutableListOf<BarDataSet>()
        dataSets.forEachIndexed { dataSetIndex, chartData ->
            val entries = mutableListOf<BarEntry>()
            chartData.values.forEachIndexed { index, value ->
                entries.add(BarEntry(index.toFloat(), value))
            }

            val dataSet = BarDataSet(entries, chartData.label).apply {
                color = ColorTemplate.MATERIAL_COLORS[dataSetIndex % ColorTemplate.MATERIAL_COLORS.size]
                valueTextSize = 8f
                valueTextColor = android.graphics.Color.BLACK
            }
            barDataSets.add(dataSet)
        }

        val barData = BarData(barDataSets)
        barData.barWidth = 0.3f

        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(dataSets.firstOrNull()?.labels ?: emptyList())
        barChart.groupBars(0f, 0.4f, 0.08f)
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

    private fun populateMultiLineChart(dataSets: List<BenchmarkChartData>) {
        val lineDataSets = mutableListOf<LineDataSet>()
        dataSets.forEachIndexed { dataSetIndex, chartData ->
            val entries = mutableListOf<Entry>()
            chartData.values.forEachIndexed { index, value ->
                entries.add(Entry(index.toFloat(), value))
            }

            val dataSet = LineDataSet(entries, chartData.label).apply {
                color = ColorTemplate.MATERIAL_COLORS[dataSetIndex % ColorTemplate.MATERIAL_COLORS.size]
                valueTextSize = 8f
                valueTextColor = android.graphics.Color.BLACK
                lineWidth = 2f
                circleRadius = 3f
                setDrawCircleHole(false)
            }
            lineDataSets.add(dataSet)
        }

        val lineData = LineData(lineDataSets)

        lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(dataSets.firstOrNull()?.labels ?: emptyList())
        lineChart.data = lineData
        lineChart.invalidate()
    }

    private fun populatePieChart(chartData: BenchmarkChartData) {
        val entries = mutableListOf<PieEntry>()
        chartData.values.forEachIndexed { index, value ->
            entries.add(PieEntry(value, chartData.labels.getOrNull(index) ?: "Item $index"))
        }

        val dataSet = PieDataSet(entries, chartData.label).apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueTextSize = 10f
            valueTextColor = android.graphics.Color.BLACK
        }

        val pieData = PieData(dataSet)
        pieChart.data = pieData
        pieChart.invalidate()
    }

    private fun populateRadarChart(chartData: BenchmarkChartData) {
        val entries = mutableListOf<RadarEntry>()
        chartData.values.forEach { value ->
            entries.add(RadarEntry(value))
        }

        val dataSet = RadarDataSet(entries, chartData.label).apply {
            color = android.graphics.Color.BLUE
            fillColor = android.graphics.Color.BLUE
            fillAlpha = 50
            lineWidth = 2f
            valueTextSize = 8f
            valueTextColor = android.graphics.Color.BLACK
        }

        val radarData = RadarData(dataSet)
        radarChart.data = radarData
        radarChart.xAxis.valueFormatter = IndexAxisValueFormatter(chartData.labels)
        radarChart.invalidate()
    }

    private fun populateMultiRadarChart(dataSets: List<BenchmarkChartData>) {
        val radarDataSets = mutableListOf<RadarDataSet>()
        dataSets.forEachIndexed { dataSetIndex, chartData ->
            val entries = mutableListOf<RadarEntry>()
            chartData.values.forEach { value ->
                entries.add(RadarEntry(value))
            }

            val dataSet = RadarDataSet(entries, chartData.label).apply {
                color = ColorTemplate.MATERIAL_COLORS[dataSetIndex % ColorTemplate.MATERIAL_COLORS.size]
                fillColor = ColorTemplate.MATERIAL_COLORS[dataSetIndex % ColorTemplate.MATERIAL_COLORS.size]
                fillAlpha = 30
                lineWidth = 2f
                valueTextSize = 8f
                valueTextColor = android.graphics.Color.BLACK
            }
            radarDataSets.add(dataSet)
        }

        val radarData = RadarData(radarDataSets)
        radarChart.xAxis.valueFormatter = IndexAxisValueFormatter(dataSets.firstOrNull()?.labels ?: emptyList())
        radarChart.data = radarData
        radarChart.invalidate()
    }

    data class BenchmarkChartData(
        val label: String,
        val labels: List<String>,
        val values: List<Float>
    )
}
