package com.hiringai.mobile.data.repository

import com.hiringai.mobile.data.local.dao.MatchDao
import com.hiringai.mobile.data.local.entity.MatchEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MatchRepository(private val matchDao: MatchDao) {

    suspend fun getAllMatches(): List<MatchEntity> = withContext(Dispatchers.IO) {
        matchDao.getAll()
    }

    suspend fun getMatchesByJobId(jobId: Long): List<MatchEntity> = withContext(Dispatchers.IO) {
        matchDao.getByJobId(jobId)
    }

    suspend fun getMatchesByCandidateId(candidateId: Long): List<MatchEntity> = withContext(Dispatchers.IO) {
        // MatchDao doesn't have getByCandidateId, return empty list
        emptyList()
    }

    suspend fun insert(match: MatchEntity) = withContext(Dispatchers.IO) {
        matchDao.insert(match)
    }

    suspend fun update(match: MatchEntity) = withContext(Dispatchers.IO) {
        matchDao.update(match)
    }

    suspend fun delete(match: MatchEntity) = withContext(Dispatchers.IO) {
        matchDao.delete(match)
    }
}