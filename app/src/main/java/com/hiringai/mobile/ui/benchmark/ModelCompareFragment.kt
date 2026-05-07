package com.hiringai.mobile.ui.benchmark

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.hiringai.mobile.R
import com.hiringai.mobile.databinding.FragmentModelCompareBinding
import com.hiringai.mobile.ui.benchmark.BenchmarkChartView.ChartType.RADAR

class ModelCompareFragment : Fragment() {

    private var _binding: FragmentModelCompareBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ModelCompareAdapter
    private lateinit var chartView: BenchmarkChartView

    companion object {
        fun newInstance() = ModelCompareFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentModelCompareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupChart()
        loadMockData()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupRecyclerView() {
        adapter = ModelCompareAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupChart() {
        binding.chartContainer.removeAllViews()
        chartView = BenchmarkChartView(requireContext())
        chartView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            400
        )
        binding.chartContainer.addView(chartView)
    }

    private fun loadMockData() {
        val mockResults = listOf(
            ModelComparison(
                modelName = "Gemma-2B-Q4",
                category = "LLM",
                latency = 45.2f,
                throughput = 22.0f,
                memory = 1200f,
                accuracy = 85f,
                success = true
            ),
            ModelComparison(
                modelName = "Qwen-2B-Q4",
                category = "LLM",
                latency = 38.5f,
                throughput = 26.3f,
                memory = 1100f,
                accuracy = 88f,
                success = true
            ),
            ModelComparison(
                modelName = "Phi-2-Q4",
                category = "LLM",
                latency = 35.8f,
                throughput = 28.0f,
                memory = 950f,
                accuracy = 82f,
                success = true
            ),
            ModelComparison(
                modelName = "Whisper-Small",
                category = "语音",
                latency = 125.6f,
                throughput = 8.0f,
                memory = 520f,
                accuracy = 92f,
                success = true
            ),
            ModelComparison(
                modelName = "ViT-Small",
                category = "图像",
                latency = 28.3f,
                throughput = 35.3f,
                memory = 380f,
                accuracy = 78f,
                success = true
            )
        )

        adapter.submitList(mockResults)
        displayRadarChart(mockResults)
    }

    private fun displayRadarChart(results: List<ModelComparison>) {
        val labels = listOf("延迟", "吞吐量", "内存", "准确率")

        val dataSets = results.map { result ->
            val latencyScore = if (result.latency > 0) (100f - result.latency.coerceAtMost(100f)) else 0f
            val throughputScore = (result.throughput / 50f) * 100f
            val memoryScore = (1 - result.memory / 2000f).coerceAtLeast(0f) * 100f
            val accuracyScore = result.accuracy

            BenchmarkChartView.BenchmarkChartData(
                label = result.modelName,
                labels = labels,
                values = listOf(latencyScore, throughputScore, memoryScore, accuracyScore)
            )
        }

        chartView.showMultiDataSetChart(RADAR, dataSets)
    }

    data class ModelComparison(
        val modelName: String,
        val category: String,
        val latency: Float,
        val throughput: Float,
        val memory: Float,
        val accuracy: Float,
        val success: Boolean
    )

    inner class ModelCompareAdapter : androidx.recyclerview.widget.ListAdapter<ModelComparison, ModelCompareAdapter.ViewHolder>(
        object : androidx.recyclerview.widget.DiffUtil.ItemCallback<ModelComparison>() {
            override fun areItemsTheSame(oldItem: ModelComparison, newItem: ModelComparison): Boolean {
                return oldItem.modelName == newItem.modelName
            }

            override fun areContentsTheSame(oldItem: ModelComparison, newItem: ModelComparison): Boolean {
                return oldItem == newItem
            }
        }
    ) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_model_comparison, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            holder.bind(item)
        }

        inner class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
            private val tvModelName = itemView.findViewById<android.widget.TextView>(R.id.tv_model_name)
            private val tvCategory = itemView.findViewById<android.widget.TextView>(R.id.tv_category)
            private val tvLatency = itemView.findViewById<android.widget.TextView>(R.id.tv_latency)
            private val tvThroughput = itemView.findViewById<android.widget.TextView>(R.id.tv_throughput)
            private val tvMemory = itemView.findViewById<android.widget.TextView>(R.id.tv_memory)
            private val tvAccuracy = itemView.findViewById<android.widget.TextView>(R.id.tv_accuracy)
            private val chipStatus = itemView.findViewById<ModelStatusChip>(R.id.chip_status)

            fun bind(item: ModelComparison) {
                tvModelName.text = item.modelName
                tvCategory.text = item.category
                tvLatency.text = "${item.latency}ms"
                tvThroughput.text = "${item.throughput}/s"
                tvMemory.text = "${item.memory}MB"
                tvAccuracy.text = "${item.accuracy}%"
                chipStatus.setStatus(if (item.success) ModelStatusChip.Status.COMPLETED else ModelStatusChip.Status.FAILED)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
