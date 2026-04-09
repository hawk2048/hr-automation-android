package com.hiringai.mobile

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HiringAIApplication : Application() {
    
    override fun onCreate() {
        // Install crash handler BEFORE anything else
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
        super.onCreate()
        instance = this
        
        // Pre-validate native libraries — if they fail to load, we catch it here
        // instead of crashing later when the user triggers ML features
        validateNativeLibraries()
    }
    
    /**
     * 安全验证 native 库加载
     * 
     * onnxruntime-android 和 llama-kotlin-android 的 .so 文件
     * 可能在某些设备上不兼容（SIGILL、ABI 不匹配等）。
     * 在这里提前验证并记录状态，UI 层可据此提示用户。
     * 
     * 注意：如果 native 库在初始化时触发 SIGILL/SIGSEGV，
     * Java 层的 try-catch 无法捕获，应用会直接闪退。
     * 所以这里只做 Java 层面的验证。
     */
    private fun validateNativeLibraries() {
        val results = mutableMapOf<String, Boolean>()
        
        // Test ONNX Runtime — only check if the .so is loadable
        try {
            System.loadLibrary("onnxruntime")
            results["onnxruntime"] = true
            Log.i(TAG, "ONNX Runtime native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            results["onnxruntime"] = false
            Log.e(TAG, "ONNX Runtime native library FAILED to load", e)
        } catch (e: Exception) {
            results["onnxruntime"] = false
            Log.e(TAG, "ONNX Runtime native library error", e)
        }
        
        // Test llama.cpp — native lib name is "llama-android" (from AAR: libllama-android.so)
        // Note: LlamaModel class will also call System.loadLibrary internally,
        // duplicate calls are safe (JVM ignores them if already loaded)
        try {
            System.loadLibrary("llama-android")
            results["llama"] = true
            Log.i(TAG, "llama.cpp native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            results["llama"] = false
            Log.e(TAG, "llama.cpp native library FAILED to load", e)
        } catch (e: Exception) {
            results["llama"] = false
            Log.e(TAG, "llama.cpp native library error", e)
        }
        
        nativeLibrariesAvailable = results
    }
    
    /**
     * 检查指定 native 库是否可用
     */
    fun isNativeLibraryAvailable(name: String): Boolean {
        return nativeLibrariesAvailable[name] == true
    }
    
    companion object {
        private const val TAG = "HiringAI"
        
        lateinit var instance: HiringAIApplication
            private set
        
        private var nativeLibrariesAvailable: Map<String, Boolean> = emptyMap()
        
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