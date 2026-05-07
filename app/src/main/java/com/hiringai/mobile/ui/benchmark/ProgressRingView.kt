package com.hiringai.mobile.ui.benchmark

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.hiringai.mobile.R

class ProgressRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress = 0f
    private var strokeWidth = 12f
    private var ringColor = Color.BLUE
    private var bgColor = Color.LTGRAY
    private var centerText = ""
    private var stageText = ""

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = this@ProgressRingView.strokeWidth
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.BLACK
        textSize = 36f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val stagePaint = Paint().apply {
        isAntiAlias = true
        color = Color.GRAY
        textSize = 14f
        textAlign = Paint.Align.CENTER
    }

    init {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.ProgressRingView)
            progress = typedArray.getFloat(R.styleable.ProgressRingView_progress, 0f)
            strokeWidth = typedArray.getDimension(R.styleable.ProgressRingView_strokeWidth, 12f)
            ringColor = typedArray.getColor(R.styleable.ProgressRingView_ringColor, Color.BLUE)
            bgColor = typedArray.getColor(R.styleable.ProgressRingView_bgColor, Color.LTGRAY)
            typedArray.recycle()
        }
    }

    fun setProgress(progress: Float) {
        this.progress = progress.coerceIn(0f, 100f)
        invalidate()
    }

    fun setCenterText(text: String) {
        this.centerText = text
        invalidate()
    }

    fun setStageText(text: String) {
        this.stageText = text
        invalidate()
    }

    fun setRingColor(color: Int) {
        this.ringColor = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (Math.min(width, height) - strokeWidth) / 2f

        // 绘制背景圆环
        paint.color = bgColor
        paint.strokeWidth = strokeWidth
        paint.style = Paint.Style.STROKE
        canvas.drawCircle(centerX, centerY, radius, paint)

        // 绘制进度圆环
        paint.color = ringColor
        paint.strokeWidth = strokeWidth
        paint.strokeCap = Paint.Cap.ROUND
        val sweepAngle = (progress / 100f) * 360f
        canvas.drawArc(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius,
            -90f,
            sweepAngle,
            false,
            paint
        )

        // 绘制中心文字
        val textBounds = Rect()
        textPaint.getTextBounds(centerText, 0, centerText.length, textBounds)
        canvas.drawText(centerText, centerX, centerY + textBounds.height() / 2f, textPaint)

        // 绘制阶段文字
        stagePaint.getTextBounds(stageText, 0, stageText.length, textBounds)
        canvas.drawText(stageText, centerX, centerY + radius + 30f, stagePaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val size = Math.min(width, height)
        setMeasuredDimension(size, size)
    }
}
