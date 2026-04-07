package com.hiringai.mobile

import android.app.Application

class HiringAIApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    companion object {
        lateinit var instance: HiringAIApplication
            private set
    }
}