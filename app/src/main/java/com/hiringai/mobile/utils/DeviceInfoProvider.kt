package com.hiringai.mobile.utils

import android.content.Context
import android.os.Build
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException

object DeviceInfoProvider {

    data class DeviceSpec(
        val manufacturer: String,
        val model: String,
        val androidVersion: String,
        val apiLevel: Int,
        val cpuModel: String,
        val cpuCores: Int,
        val ramSizeGB: Float,
        val gpuModel: String,
        val abi: String,
        val deviceTier: DeviceTier
    )

    enum class DeviceTier {
        HIGH_END,
        MID_RANGE,
        BUDGET
    }

    fun getDeviceSpec(context: Context): DeviceSpec {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val androidVersion = Build.VERSION.RELEASE
        val apiLevel = Build.VERSION.SDK_INT
        val cpuModel = getCpuModel()
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val ramSizeGB = getTotalMemoryGB()
        val gpuModel = getGpuModel()
        val abi = getPrimaryAbi()
        val deviceTier = determineDeviceTier(ramSizeGB, cpuCores, apiLevel)

        return DeviceSpec(
            manufacturer = manufacturer,
            model = model,
            androidVersion = androidVersion,
            apiLevel = apiLevel,
            cpuModel = cpuModel,
            cpuCores = cpuCores,
            ramSizeGB = ramSizeGB,
            gpuModel = gpuModel,
            abi = abi,
            deviceTier = deviceTier
        )
    }

    private fun getCpuModel(): String {
        return try {
            val reader = BufferedReader(FileReader("/proc/cpuinfo"))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line?.startsWith("Hardware") == true) {
                    return line?.split(":")?.get(1)?.trim() ?: "Unknown"
                }
                if (line?.startsWith("model name") == true) {
                    return line?.split(":")?.get(1)?.trim() ?: "Unknown"
                }
            }
            reader.close()
            Build.HARDWARE
        } catch (e: IOException) {
            Build.HARDWARE
        }
    }

    private fun getTotalMemoryGB(): Float {
        return try {
            val reader = BufferedReader(FileReader("/proc/meminfo"))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line?.startsWith("MemTotal") == true) {
                    val parts = line?.split("\\s+".toRegex())
                    if (parts?.size ?: 0 >= 2) {
                        val kb = parts?.get(1)?.toLong() ?: 0L
                        reader.close()
                        return kb / (1024f * 1024f)
                    }
                }
            }
            reader.close()
            4f
        } catch (e: IOException) {
            4f
        }
    }

    private fun getGpuModel(): String {
        return try {
            val reader = BufferedReader(FileReader("/proc/cpuinfo"))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line?.contains("GPU") == true || line?.contains("gpu") == true) {
                    reader.close()
                    return line?.split(":")?.get(1)?.trim() ?: "Unknown"
                }
            }
            reader.close()
            detectGpuFromHardware()
        } catch (e: IOException) {
            detectGpuFromHardware()
        }
    }

    private fun detectGpuFromHardware(): String {
        val hardware = Build.HARDWARE.lowercase()
        return when {
            hardware.contains("qcom") || hardware.contains("snapdragon") -> "Qualcomm Adreno"
            hardware.contains("mediatek") || hardware.contains("mtk") -> "Mali (MediaTek)"
            hardware.contains("exynos") -> "Mali (Exynos)"
            hardware.contains("kirin") -> "Mali (Kirin)"
            else -> "Unknown GPU"
        }
    }

    private fun getPrimaryAbi(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS.firstOrNull() ?: Build.CPU_ABI
        } else {
            Build.CPU_ABI
        }
    }

    private fun determineDeviceTier(ramGB: Float, cpuCores: Int, apiLevel: Int): DeviceTier {
        return when {
            ramGB >= 8 && cpuCores >= 8 && apiLevel >= 33 -> DeviceTier.HIGH_END
            ramGB >= 4 && cpuCores >= 4 && apiLevel >= 30 -> DeviceTier.MID_RANGE
            else -> DeviceTier.BUDGET
        }
    }

    fun formatDeviceSpec(spec: DeviceSpec): String {
        return buildString {
            appendLine("📱 设备信息")
            appendLine("制造商: ${spec.manufacturer}")
            appendLine("型号: ${spec.model}")
            appendLine("Android 版本: ${spec.androidVersion} (API ${spec.apiLevel})")
            appendLine("CPU: ${spec.cpuModel} (${spec.cpuCores}核)")
            appendLine("内存: ${String.format("%.1f", spec.ramSizeGB)} GB")
            appendLine("GPU: ${spec.gpuModel}")
            appendLine("架构: ${spec.abi}")
            appendLine("设备等级: ${getTierDescription(spec.deviceTier)}")
        }
    }

    fun getTierDescription(tier: DeviceTier): String {
        return when (tier) {
            DeviceTier.HIGH_END -> "高端设备"
            DeviceTier.MID_RANGE -> "中端设备"
            DeviceTier.BUDGET -> "入门设备"
        }
    }
}
