package com.hiringai.mobile.ui.benchmark

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.hiringai.mobile.R

class ModelStatusChip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var tvStatus: TextView
    private lateinit var tvIcon: TextView

    enum class Status {
        IDLE,
        TESTING,
        COMPLETED,
        FAILED,
        NOT_INSTALLED
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.layout_status_chip, this, true)
        orientation = HORIZONTAL
        tvIcon = findViewById(R.id.tv_chip_icon)
        tvStatus = findViewById(R.id.tv_chip_text)
        setStatus(Status.IDLE)
    }

    fun setStatus(status: Status) {
        when (status) {
            Status.IDLE -> {
                tvIcon.text = "○"
                tvStatus.text = "待测试"
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                tvStatus.setTextColor(Color.parseColor("#757575"))
                tvIcon.setTextColor(Color.parseColor("#BDBDBD"))
            }
            Status.TESTING -> {
                tvIcon.text = "●"
                tvStatus.text = "测试中"
                setBackgroundColor(Color.parseColor("#E3F2FD"))
                tvStatus.setTextColor(Color.parseColor("#1976D2"))
                tvIcon.setTextColor(Color.parseColor("#2196F3"))
            }
            Status.COMPLETED -> {
                tvIcon.text = "✓"
                tvStatus.text = "已完成"
                setBackgroundColor(Color.parseColor("#E8F5E9"))
                tvStatus.setTextColor(Color.parseColor("#388E3C"))
                tvIcon.setTextColor(Color.parseColor("#4CAF50"))
            }
            Status.FAILED -> {
                tvIcon.text = "✗"
                tvStatus.text = "失败"
                setBackgroundColor(Color.parseColor("#FFEBEE"))
                tvStatus.setTextColor(Color.parseColor("#C62828"))
                tvIcon.setTextColor(Color.parseColor("#EF5350"))
            }
            Status.NOT_INSTALLED -> {
                tvIcon.text = "!"
                tvStatus.text = "未安装"
                setBackgroundColor(Color.parseColor("#FFF8E1"))
                tvStatus.setTextColor(Color.parseColor("#F57C00"))
                tvIcon.setTextColor(Color.parseColor("#FF9800"))
            }
        }
    }
}
