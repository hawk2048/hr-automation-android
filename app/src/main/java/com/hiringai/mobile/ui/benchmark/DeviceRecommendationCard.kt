package com.hiringai.mobile.ui.benchmark

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import com.hiringai.mobile.R
import com.hiringai.mobile.utils.DeviceInfoProvider
import com.hiringai.mobile.utils.DeviceInfoProvider.DeviceSpec
import com.hiringai.mobile.utils.ModelRecommendationEngine
import com.hiringai.mobile.utils.ModelRecommendationEngine.RecommendedModel

class DeviceRecommendationCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var tvDeviceName: TextView
    private lateinit var tvDeviceTier: TextView
    private lateinit var tvDeviceSpec: TextView
    private lateinit var tvRecommendationTitle: TextView
    private lateinit var llRecommendations: LinearLayout
    private lateinit var btnRefresh: Button

    private lateinit var deviceSpec: DeviceSpec

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.layout_device_recommendation, this, true)
        initViews()
        loadDeviceInfo()
        loadRecommendations()
    }

    private fun initViews() {
        tvDeviceName = findViewById(R.id.tv_device_name)
        tvDeviceTier = findViewById(R.id.tv_device_tier)
        tvDeviceSpec = findViewById(R.id.tv_device_spec)
        tvRecommendationTitle = findViewById(R.id.tv_recommendation_title)
        llRecommendations = findViewById(R.id.ll_recommendations)
        btnRefresh = findViewById(R.id.btn_refresh)

        btnRefresh.setOnClickListener {
            loadDeviceInfo()
            loadRecommendations()
        }
    }

    private fun loadDeviceInfo() {
        deviceSpec = DeviceInfoProvider.getDeviceSpec(context)
        tvDeviceName.text = "${deviceSpec.manufacturer} ${deviceSpec.model}"
        tvDeviceTier.text = DeviceInfoProvider.getTierDescription(deviceSpec.deviceTier)
        tvDeviceSpec.text = buildString {
            appendLine("Android ${deviceSpec.androidVersion}")
            appendLine("${deviceSpec.cpuModel} (${deviceSpec.cpuCores}核)")
            appendLine("${String.format("%.1f", deviceSpec.ramSizeGB)} GB RAM")
            appendLine("${deviceSpec.gpuModel}")
        }
        updateTierColor()
    }

    private fun updateTierColor() {
        val tierColor = when (deviceSpec.deviceTier) {
            DeviceInfoProvider.DeviceTier.HIGH_END -> context.getColor(R.color.score_excellent)
            DeviceInfoProvider.DeviceTier.MID_RANGE -> context.getColor(R.color.score_good)
            DeviceInfoProvider.DeviceTier.BUDGET -> context.getColor(R.color.score_poor)
        }
        tvDeviceTier.setTextColor(tierColor)
    }

    private fun loadRecommendations() {
        llRecommendations.removeAllViews()

        val recommendations = ModelRecommendationEngine.getRecommendations(context, deviceSpec)
        val topRecommendations = recommendations
            .filter { it.recommended }
            .take(3)

        if (topRecommendations.isEmpty()) {
            val noRecView = LayoutInflater.from(context)
                .inflate(R.layout.item_recommendation, llRecommendations, false)
            noRecView.findViewById<TextView>(R.id.tv_model_name).text = "暂无推荐"
            noRecView.findViewById<TextView>(R.id.tv_reason).text = "请下载模型后查看推荐"
            noRecView.findViewById<TextView>(R.id.tv_score).visibility = GONE
            llRecommendations.addView(noRecView)
            return
        }

        topRecommendations.forEach { recommendation ->
            addRecommendationItem(recommendation)
        }
    }

    private fun addRecommendationItem(recommendation: RecommendedModel) {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_recommendation, llRecommendations, false)

        view.findViewById<TextView>(R.id.tv_model_name).text = "${recommendation.modelName} (${recommendation.category})"
        view.findViewById<TextView>(R.id.tv_reason).text = recommendation.reason
        view.findViewById<TextView>(R.id.tv_score).text = "评分: ${recommendation.performanceScore}/100"

        val icon = view.findViewById<TextView>(R.id.tv_icon)
        icon.text = when (recommendation.category) {
            "LLM" -> "🤖"
            "图像" -> "🖼️"
            "语音" -> "🎤"
            else -> "📦"
        }

        llRecommendations.addView(view)
    }
}
