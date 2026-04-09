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
    }
    
    companion object {
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