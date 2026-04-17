package com.hiringai.mobile.data.repository

import com.hiringai.mobile.data.local.dao.CandidateDao
import com.hiringai.mobile.data.local.entity.CandidateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CandidateRepository(private val candidateDao: CandidateDao) {

    suspend fun getAllCandidates(): List<CandidateEntity> = withContext(Dispatchers.IO) {
        candidateDao.getAll()
    }

    suspend fun getCandidateById(id: Long): CandidateEntity? = withContext(Dispatchers.IO) {
        candidateDao.getById(id)
    }

    suspend fun insert(candidate: CandidateEntity) = withContext(Dispatchers.IO) {
        candidateDao.insert(candidate)
    }

    suspend fun update(candidate: CandidateEntity) = withContext(Dispatchers.IO) {
        candidateDao.update(candidate)
    }

    suspend fun delete(candidate: CandidateEntity) = withContext(Dispatchers.IO) {
        candidateDao.delete(candidate)
    }
}