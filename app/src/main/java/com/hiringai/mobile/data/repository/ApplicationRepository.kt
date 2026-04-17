package com.hiringai.mobile.data.repository

import com.hiringai.mobile.data.local.dao.ApplicationDao
import com.hiringai.mobile.data.local.entity.ApplicationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ApplicationRepository(private val applicationDao: ApplicationDao) {

    suspend fun getAllApplications(): List<ApplicationEntity> = withContext(Dispatchers.IO) {
        applicationDao.getAll()
    }

    suspend fun getApplicationsByJobId(jobId: Long): List<ApplicationEntity> = withContext(Dispatchers.IO) {
        applicationDao.getByJobId(jobId)
    }

    suspend fun getApplicationsByCandidateId(candidateId: Long): List<ApplicationEntity> = withContext(Dispatchers.IO) {
        applicationDao.getByCandidateId(candidateId)
    }

    suspend fun insert(application: ApplicationEntity) = withContext(Dispatchers.IO) {
        applicationDao.insert(application)
    }

    suspend fun update(application: ApplicationEntity) = withContext(Dispatchers.IO) {
        applicationDao.update(application)
    }

    suspend fun delete(application: ApplicationEntity) = withContext(Dispatchers.IO) {
        applicationDao.delete(application)
    }
}