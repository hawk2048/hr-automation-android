package com.hiringai.mobile.data.local.dao

import androidx.room.*
import com.hiringai.mobile.data.local.entity.JobEntity
import com.hiringai.mobile.data.local.entity.CandidateEntity
import com.hiringai.mobile.data.local.entity.MatchEntity

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY createdAt DESC")
    suspend fun getAll(): List<JobEntity>
    
    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun getById(id: Long): JobEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: JobEntity): Long
    
    @Update
    suspend fun update(job: JobEntity)
    
    @Delete
    suspend fun delete(job: JobEntity)
}

@Dao
interface CandidateDao {
    @Query("SELECT * FROM candidates ORDER BY createdAt DESC")
    suspend fun getAll(): List<CandidateEntity>
    
    @Query("SELECT * FROM candidates WHERE id = :id")
    suspend fun getById(id: Long): CandidateEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(candidate: CandidateEntity): Long
    
    @Update
    suspend fun update(candidate: CandidateEntity)
    
    @Delete
    suspend fun delete(candidate: CandidateEntity)
}

@Dao
interface MatchDao {
    @Query("SELECT * FROM matches WHERE jobId = :jobId ORDER BY score DESC")
    suspend fun getByJobId(jobId: Long): List<MatchEntity>
    
    @Query("SELECT * FROM matches ORDER BY score DESC")
    suspend fun getAll(): List<MatchEntity>
    
    @Query("SELECT * FROM matches WHERE id = :id")
    suspend fun getById(id: Long): MatchEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(match: MatchEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(matches: List<MatchEntity>)
    
    @Update
    suspend fun update(match: MatchEntity)
    
    @Query("UPDATE matches SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)
    
    @Delete
    suspend fun delete(match: MatchEntity)
}