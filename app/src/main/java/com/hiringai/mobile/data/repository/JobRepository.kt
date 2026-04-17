package com.hiringai.mobile.data.repository

import com.hiringai.mobile.data.local.dao.JobDao
import com.hiringai.mobile.data.local.entity.JobEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JobRepository(private val jobDao: JobDao) {

    suspend fun getAllJobs(): List<JobEntity> = withContext(Dispatchers.IO) {
        jobDao.getAll()
    }

    suspend fun getJobById(id: Long): JobEntity? = withContext(Dispatchers.IO) {
        jobDao.getById(id)
    }

    suspend fun insert(job: JobEntity) = withContext(Dispatchers.IO) {
        jobDao.insert(job)
    }

    suspend fun update(job: JobEntity) = withContext(Dispatchers.IO) {
        jobDao.update(job)
    }

    suspend fun delete(job: JobEntity) = withContext(Dispatchers.IO) {
        jobDao.delete(job)
    }
}