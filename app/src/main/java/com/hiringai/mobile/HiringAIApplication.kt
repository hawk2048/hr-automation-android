package com.hiringai.mobile

import android.app.Application
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class HiringAIApplication : Application() {
    
    override fun onCreate() {
        // Install crash handler BEFORE anything else
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
        super.onCreate()
        instance = this
        
        // NOTE: Native libraries are NO LONGER pre-loaded here.
        // They are loaded lazily on first use via SafeNativeLoader.
        // This prevents SIGILL/SIGSEGV during app startup from killing
        // the entire process before the user even sees the UI.
        
        // Detect device compatibility for ML features
        SafeNativeLoader.detectDeviceCompatibility()
    }
    
    /**
     * 检查指定 native 库是否可用
     * Delegates to SafeNativeLoader which tracks lazy load results.
     */
    fun isNativeLibraryAvailable(name: String): Boolean {
        return SafeNativeLoader.isAvailable(name)
    }
    
    companion object {
        private const val TAG = "HiringAI"
        
        lateinit var instance: HiringAIApplication
            private set
        
        fun getCrashLog(context: Application): String? {
            val file = File(context.filesDir, "crash.log")
            return if (file.exists()) file.readText() else null
        }
        
        fun clearCrashLog(context: Application) {
            val file = File(context.filesDir, "crash.log")
            if (file.exists()) file.delete()
        }
    }
    
    private class CrashHandler(private val app: Application) : Thread.UncaughtExceptionHandler {
        private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            // Write crash log to file
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                pw.println("=== Crash at ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} ===")
                pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
                pw.println("ABI: ${Build.SUPPORTED_ABIS?.joinToString() ?: "unknown"}")
                pw.println("ML Status: onnxruntime=${SafeNativeLoader.isAvailable("onnxruntime")}, llama=${SafeNativeLoader.isAvailable("llama")}")
                pw.println()
                throwable.printStackTrace(pw)
                pw.flush()
                
                val logFile = File(app.filesDir, "crash.log")
                logFile.writeText(sw.toString())
                Log.e("HiringAI-Crash", "Crash log saved to ${logFile.absolutePath}", throwable)
            } catch (e: Exception) {
                Log.e("HiringAI-Crash", "Failed to write crash log", e)
            }
            
            // Let the default handler finish the process
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

/**
 * 安全的 Native 库加载器
 * 
 * 核心设计原则：
 * 1. 延迟加载 — 不在 Application.onCreate() 中加载，改为按需加载
 * 2. 设备兼容性检测 — 在加载前检测已知的危险设备/芯片组
 * 3. 加载结果缓存 — 只尝试加载一次，避免重复触发 crash
 * 4. 安全回退 — 如果 native 库不可用，ML 功能优雅降级
 * 
 * 注意：如果 native 库在加载时触发 SIGILL/SIGSEGV，
 * Java 层的 try-catch 无法捕获，应用会直接闪退。
 * 这就是为什么我们要做预防性检测 + 延迟加载的原因：
 * 即使闪退，用户至少能看到 UI，下次启动时标记为不可用。
 */
object SafeNativeLoader {
    private const val TAG = "SafeNativeLoader"
    
    private val loadAttempts = mutableMapOf<String, Boolean>()
    private val loading = AtomicBoolean(false)
    
    // Device compatibility info
    var isDeviceCompatible: Boolean = true
        private set
    var incompatibilityReason: String = ""
        private set
    
    /**
     * 检测设备兼容性
     * 
     * 在 Application.onCreate() 中调用，不加载任何 native 库。
     * 通过系统属性和设备信息判断是否适合加载 ML native 库。
     */
    fun detectDeviceCompatibility() {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        val brand = Build.BRAND?.lowercase() ?: ""
        val model = Build.MODEL ?: ""
        val abi = Build.SUPPORTED_ABIS?.firstOrNull() ?: ""
        
        Log.i(TAG, "Device: $manufacturer $brand $model, ABI: $abi, Android: ${Build.VERSION.SDK_INT}")
        
        // Check if running on emulator with wrong ABI
        val isEmulator = isEmulator()
        if (isEmulator && abi != "x86_64" && abi != "x86") {
            isDeviceCompatible = false
            incompatibilityReason = "Emulator with unsupported ABI: $abi"
            Log.w(TAG, "Incompatible: $incompatibilityReason")
            return
        }
        
        // Check ABI — we only support arm64-v8a and x86_64
        if (abi != "arm64-v8a" && abi != "x86_64") {
            isDeviceCompatible = false
            incompatibilityReason = "Unsupported ABI: $abi (need arm64-v8a or x86_64)"
            Log.w(TAG, "Incompatible: $incompatibilityReason")
            return
        }
        
        // Check for previously recorded native crash
        val crashMarker = getCrashMarkerFile()
        if (crashMarker.exists()) {
            val crashedLib = crashMarker.readText().trim()
            Log.w(TAG, "Previous native crash detected for: $crashedLib — marking as unavailable")
            loadAttempts[crashedLib] = false
            // Don't delete the marker — let user explicitly reset from settings
        }
        
        Log.i(TAG, "Device compatibility check passed")
    }
    
    /**
     * 安全加载 native 库
     * 
     * @param name 库名（不含 "lib" 前缀和 ".so" 后缀）
     * @return true 如果加载成功或已加载
     */
    fun loadLibrary(name: String): Boolean {
        // Already attempted — return cached result
        loadAttempts[name]?.let { return it }
        
        // Known crash — skip
        if (!isDeviceCompatible) {
            Log.w(TAG, "Skipping $name: device incompatible ($incompatibilityReason)")
            loadAttempts[name] = false
            return false
        }
        
        // Prevent concurrent loading
        if (!loading.compareAndSet(false, true)) {
            Log.w(TAG, "Skipping $name: another library is being loaded concurrently")
            return loadAttempts[name] ?: false
        }
        
        return try {
            Log.i(TAG, "Loading native library: $name")
            
            when (name) {
                "onnxruntime" -> {
                    System.loadLibrary("onnxruntime")
                    // Also verify OrtEnvironment can be created — this is where
                    // SIGILL typically happens on incompatible devices
                    ai.onnxruntime.OrtEnvironment.getEnvironment()
                }
                "llama-android" -> {
                    System.loadLibrary("llama-android")
                }
                else -> {
                    System.loadLibrary(name)
                }
            }
            
            loadAttempts[name] = true
            Log.i(TAG, "Native library loaded successfully: $name")
            true
        } catch (e: UnsatisfiedLinkError) {
            loadAttempts[name] = false
            Log.e(TAG, "Native library FAILED to load: $name (UnsatisfiedLinkError)", e)
            false
        } catch (e: Exception) {
            loadAttempts[name] = false
            Log.e(TAG, "Native library FAILED to load: $name", e)
            false
        } finally {
            loading.set(false)
        }
    }
    
    /**
     * 检查 native 库是否可用
     */
    fun isAvailable(name: String): Boolean {
        return loadAttempts[name] == true
    }
    
    /**
     * 尝试加载失败后标记（供 CrashHandler 使用）
     * 如果 app 在 native 库加载后立即闪退，下次启动时
     * detectDeviceCompatibility() 会读取这个标记
     */
    fun markCrashed(libName: String) {
        loadAttempts[libName] = false
        try {
            getCrashMarkerFile().writeText(libName)
            Log.w(TAG, "Crash marker written for: $libName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash marker", e)
        }
    }
    
    /**
     * 清除 crash marker（从设置页面操作）
     */
    fun clearCrashMarker(): Boolean {
        val file = getCrashMarkerFile()
        return if (file.exists()) file.delete() else true
    }
    
    /**
     * 检查是否有 crash marker
     */
    fun hasCrashMarker(): Boolean {
        return getCrashMarkerFile().exists()
    }
    
    /**
     * 重置所有加载状态，允许重新尝试加载
     */
    fun reset() {
        loadAttempts.clear()
        isDeviceCompatible = true
        incompatibilityReason = ""
        clearCrashMarker()
        detectDeviceCompatibility()
    }
    
    private fun getCrashMarkerFile(): File {
        return File(HiringAIApplication.instance.filesDir, "native_crash_marker")
    }
    
    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.FINGERPRINT.contains("emulator")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic")
                || Build.DEVICE.startsWith("generic")
                || Build.PRODUCT.contains("sdk")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || "google_sdk" == Build.PRODUCT)
    }
}
