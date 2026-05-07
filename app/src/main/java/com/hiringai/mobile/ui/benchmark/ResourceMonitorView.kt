package com.hiringai.mobile.ui.benchmark

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.hiringai.mobile.R
import kotlinx.coroutines.*

class ResourceMonitorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var cpuProgress: ProgressBar
    private lateinit var memoryProgress: ProgressBar
    private lateinit var gpuProgress: ProgressBar
    private lateinit var cpuText: TextView
    private lateinit var memoryText: TextView
    private lateinit var gpuText: TextView

    private var isRunning = false
    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val updateIntervalMs = 1000L
    private var lastCpuValue = 0f
    private var lastMemoryValue = 0f
    private var lastGpuValue = 0f

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.layout_resource_monitor, this, true)
        initViews()
    }

    private fun initViews() {
        cpuProgress = findViewById(R.id.progress_cpu)
        memoryProgress = findViewById(R.id.progress_memory)
        gpuProgress = findViewById(R.id.progress_gpu)
        cpuText = findViewById(R.id.tv_cpu_value)
        memoryText = findViewById(R.id.tv_memory_value)
        gpuText = findViewById(R.id.tv_gpu_value)

        cpuProgress.max = 100
        memoryProgress.max = 100
        gpuProgress.max = 100
    }

    fun startMonitoring() {
        if (isRunning) return
        isRunning = true

        monitorJob = scope.launch {
            while (isActive && isRunning) {
                updateResources()
                delay(updateIntervalMs)
            }
        }
    }

    fun stopMonitoring() {
        isRunning = false
        monitorJob?.cancel()
        monitorJob = null
    }

    private suspend fun updateResources() {
        withContext(Dispatchers.IO) {
            val cpuUsage = getCPUUsage()
            val memoryUsage = getMemoryUsage()
            val gpuUsage = getGPUUsage()

            withContext(Dispatchers.Main) {
                updateUI(cpuUsage, memoryUsage, gpuUsage)
            }
        }
    }

    private fun updateUI(cpu: Float, memory: Float, gpu: Float) {
        if (shouldUpdate(cpu, lastCpuValue) || shouldUpdate(memory, lastMemoryValue) || shouldUpdate(gpu, lastGpuValue)) {
            cpuProgress.progress = cpu.toInt()
            cpuText.text = "${cpu.toInt()}%"

            memoryProgress.progress = memory.toInt()
            memoryText.text = "${memory.toInt()}%"

            gpuProgress.progress = gpu.toInt()
            gpuText.text = "${gpu.toInt()}%"

            lastCpuValue = cpu
            lastMemoryValue = memory
            lastGpuValue = gpu
        }
    }

    private fun shouldUpdate(newValue: Float, lastValue: Float): Boolean {
        return kotlin.math.abs(newValue - lastValue) >= 1f
    }

    private fun getCPUUsage(): Float {
        return try {
            val reader = java.io.RandomAccessFile("/proc/stat", "r")
            val load = reader.readLine()
            reader.close()

            val toks = load.split(" ")
            val idle1 = toks[4].toLong()
            val cpu1 = toks[2].toLong() + toks[3].toLong() + toks[5].toLong() + toks[6].toLong()

            Thread.sleep(100)

            val reader2 = java.io.RandomAccessFile("/proc/stat", "r")
            val load2 = reader2.readLine()
            reader2.close()

            val toks2 = load2.split(" ")
            val idle2 = toks2[4].toLong()
            val cpu2 = toks2[2].toLong() + toks2[3].toLong() + toks2[5].toLong() + toks2[6].toLong()

            val idle = idle2 - idle1
            val total = cpu2 + idle2 - cpu1 - idle1

            if (total == 0L) 0f else ((cpu2 - cpu1).toFloat() / total.toFloat()) * 100
        } catch (e: Exception) {
            0f
        }
    }

    private fun getMemoryUsage(): Float {
        val runtime = Runtime.getRuntime()
        val usedMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMB = runtime.maxMemory() / (1024 * 1024)
        return if (maxMB == 0L) 0f else (usedMB.toFloat() / maxMB.toFloat()) * 100
    }

    private fun getGPUUsage(): Float {
        return try {
            val reader = java.io.RandomAccessFile("/sys/devices/platform/kgsl-3d0/kgsl/kgsl-3d0/gpuclk", "r")
            val gpuClock = reader.readLine().toInt()
            reader.close()

            val maxClock = 800000000
            (gpuClock.toFloat() / maxClock.toFloat()) * 100
        } catch (e: Exception) {
            0f
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopMonitoring()
        scope.cancel()
    }
}
