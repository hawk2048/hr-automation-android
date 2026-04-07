package com.hiringai.mobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hiringai.mobile.data.local.entity.JobEntity
import com.hiringai.mobile.data.local.entity.CandidateEntity
import com.hiringai.mobile.data.local.entity.MatchEntity
import com.hiringai.mobile.data.local.dao.JobDao
import com.hiringai.mobile.data.local.dao.CandidateDao
import com.hiringai.mobile.data.local.dao.MatchDao

@Database(
    entities = [JobEntity::class, CandidateEntity::class, MatchEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun candidateDao(): CandidateDao
    abstract fun matchDao(): MatchDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hr_automation_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}