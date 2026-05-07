package com.hiringai.mobile.ui.benchmark

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.charts.RadarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.data.RadarData
import com.github.mikephil.charting.data.RadarDataSet
import com.github.mikephil.charting.data.RadarEntry
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

    private var currentChartType: ChartType? = null
    private var isInitialized = false

    enum class ChartType {
        BAR,
        LINE,
        PIE,
        RADAR
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.layout_benchmark_chart, this, true)
    }

    private fun initCharts() {
        if (isInitialized) return
        isInitialized = true

        barChart = findViewById(R.id.bar_chart)
        lineChart = findViewById(R.id.line_chart)
        pieChart = findViewById(R.id.pie_chart)
        radarChart = findViewById(R.id.radar_chart)

        setupBarChart()
        setupLineChart()
        setupPieChart()
        setupRadarChart()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        initCharts()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clearCharts()
    }

    private fun clearCharts() {
        barChart?.apply { clear(); invalidate() }
        lineChart?.apply { clear(); invalidate() }
        pieChart?.apply { clear(); invalidate() }
        radarChart?.apply { clear(); invalidate() }
    }

    private fun setupBarChart() {
        barChart.apply {
            description.isEnabled = false
            setPinchZoom(false)
            setDrawBarShadow(false)
            setDrawGridBackground(false)
            setDrawValueAboveBar(true)
            setFitBars(true)
            animateY(300)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textSize = 10f
                setDrawLabels(true)
            }

            axisLeft.apply {
                setDrawGridLines(true)
                granularity = 1f
                textSize = 10f
                axisMinimum = 0f
            }

            axisRight.isEnabled = false
            legend.isEnabled = true
            legend.textSize = 10f

            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
        }
    }

    private fun setupLineChart() {
        lineChart.apply {
            description.isEnabled = false
            setPinchZoom(true)
            setDrawGridBackground(false)
            animateX(300)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textSize = 10f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                granularity = 1f
                textSize = 10f
            }

            axisRight.isEnabled = false
            legend.isEnabled = true
            legend.textSize = 10f

            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            isDoubleTapToZoomEnabled = false
        }
    }

    private fun setupPieChart() {
        pieChart.apply {
            description.isEnabled = false
            isDrawHoleEnabled = true
            setHoleColor(Color.WHITE)
            holeRadius = 45f
            setTransparentCircleRadius(50f)
            legend.isEnabled = true
            legend.textSize = 10f
            animateY(300)
            setUsePercentValues(true)
            setEntryLabelTextSize(10f)
            setEntryLabelColor(Color.BLACK)
        }
    }

    private fun setupRadarChart() {
        radarChart.apply {
            description.isEnabled = false
            setDrawWeb(true)
            setWebColor(Color.LTGRAY)
            setWebLineWidth(1f)
            setWebAlpha(100)
            legend.isEnabled = true
            legend.textSize = 10f
            animateXY(300, 300)

            yAxis.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                textSize = 8f
                setDrawLabels(true)
            }

            xAxis.apply {
                textSize = 10f
            }

            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
        }
    }

    fun showChart(type: ChartType, chartData: BenchmarkChartData) {
        initCharts()

        hideAllCharts()

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

        currentChartType = type
    }

    fun showMultiDataSetChart(type: ChartType, dataSets: List<BenchmarkChartData>) {
        initCharts()

        hideAllCharts()

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

        currentChartType = type
    }

    private fun hideAllCharts() {
        barChart.visibility = GONE
        lineChart.visibility = GONE
        pieChart.visibility = GONE
        radarChart.visibility = GONE
    }

    private fun populateBarChart(chartData: BenchmarkChartData) {
        val entries = chartData.values.mapIndexed { index, value ->
            BarEntry(index.toFloat(), value)
        }

        val dataSet = BarDataSet(entries, chartData.label).apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueTextSize = 10f
            valueTextColor = Color.BLACK
            setDrawValues(true)
        }

        barChart.apply {
            xAxis.valueFormatter = IndexAxisValueFormatter(chartData.labels)
            data = BarData(dataSet).apply { barWidth = 0.6f }
            invalidate()
        }
    }

    private fun populateMultiBarChart(dataSets: List<BenchmarkChartData>) {
        val barDataSets = dataSets.mapIndexed { dataSetIndex, chartData ->
            val entries = chartData.values.mapIndexed { index, value ->
                BarEntry(index.toFloat(), value)
            }

            BarDataSet(entries, chartData.label).apply {
                color = ColorTemplate.MATERIAL_COLORS[dataSetIndex % ColorTemplate.MATERIAL_COLORS.size]
                valueTextSize = 8f
                valueTextColor = Color.BLACK
            }
        }

        barChart.apply {
            xAxis.valueFormatter = IndexAxisValueFormatter(dataSets.firstOrNull()?.labels ?: emptyList())
            data = BarData(barDataSets.toList()).apply { barWidth = 0.3f }
            groupBars(0f, 0.4f, 0.08f)
            invalidate()
        }
    }

    private fun populateLineChart(chartData: BenchmarkChartData) {
        val entries = chartData.values.mapIndexed { index, value ->
            Entry(index.toFloat(), value)
        }

        val dataSet = LineDataSet(entries, chartData.label).apply {
            color = Color.BLUE
            valueTextSize = 10f
            valueTextColor = Color.BLACK
            lineWidth = 2f
            circleRadius = 4f
            setDrawCircleHole(false)
            setDrawFilled(false)
        }

        lineChart.apply {
            xAxis.valueFormatter = IndexAxisValueFormatter(chartData.labels)
            data = LineData(dataSet)
            invalidate()
        }
    }

    private fun populateMultiLineChart(dataSets: List<BenchmarkChartData>) {
        val lineDataSets = dataSets.mapIndexed { dataSetIndex, chartData ->
            val entries = chartData.values.mapIndexed { index, value ->
                Entry(index.toFloat(), value)
            }

            LineDataSet(entries, chartData.label).apply {
                color = ColorTemplate.MATERIAL_COLORS[dataSetIndex % ColorTemplate.MATERIAL_COLORS.size]
                valueTextSize = 8f
                valueTextColor = Color.BLACK
                lineWidth = 2f
                circleRadius = 3f
                setDrawCircleHole(false)
            }
        }

        lineChart.apply {
            xAxis.valueFormatter = IndexAxisValueFormatter(dataSets.firstOrNull()?.labels ?: emptyList())
            data = LineData(lineDataSets.toList())
            invalidate()
        }
    }

    private fun populatePieChart(chartData: BenchmarkChartData) {
        val entries = chartData.values.mapIndexed { index, value ->
            PieEntry(value, chartData.labels.getOrNull(index) ?: "Item $index")
        }

        val dataSet = PieDataSet(entries, chartData.label).apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueTextSize = 10f
            valueTextColor = Color.BLACK
            sliceSpace = 2f
            valueLinePart1OffsetPercentage = 80f
            valueLinePart1Length = 0.4f
            valueLinePart2Length = 0.4f
        }

        pieChart.apply {
            data = PieData(dataSet)
            invalidate()
        }
    }

    private fun populateRadarChart(chartData: BenchmarkChartData) {
        val entries = chartData.values.map { value ->
            RadarEntry(value)
        }

        val dataSet = RadarDataSet(entries, chartData.label).apply {
            color = Color.BLUE
            fillColor = Color.BLUE
            fillAlpha = 50
            lineWidth = 2f
            valueTextSize = 8f
            valueTextColor = Color.BLACK
            setDrawValues(true)
        }

        radarChart.apply {
            xAxis.valueFormatter = IndexAxisValueFormatter(chartData.labels)
            data = RadarData(dataSet)
            invalidate()
        }
    }

    private fun populateMultiRadarChart(dataSets: List<BenchmarkChartData>) {
        val radarDataSets = dataSets.mapIndexed { dataSetIndex, chartData ->
            val entries = chartData.values.map { value ->
                RadarEntry(value)
            }

            RadarDataSet(entries, chartData.label).apply {
                color = ColorTemplate.MATERIAL_COLORS[dataSetIndex % ColorTemplate.MATERIAL_COLORS.size]
                fillColor = ColorTemplate.MATERIAL_COLORS[dataSetIndex % ColorTemplate.MATERIAL_COLORS.size]
                fillAlpha = 30
                lineWidth = 2f
                valueTextSize = 8f
                valueTextColor = Color.BLACK
            }
        }

        radarChart.apply {
            xAxis.valueFormatter = IndexAxisValueFormatter(dataSets.firstOrNull()?.labels ?: emptyList())
            data = RadarData(radarDataSets.toList())
            invalidate()
        }
    }

    data class BenchmarkChartData(
        val label: String,
        val labels: List<String>,
        val values: List<Float>
    )
}
