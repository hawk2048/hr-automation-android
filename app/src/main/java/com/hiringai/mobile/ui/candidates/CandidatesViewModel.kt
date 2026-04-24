package com.hiringai.mobile.ui.candidates

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hiringai.mobile.data.local.entity.CandidateEntity
import com.hiringai.mobile.data.local.entity.ApplicationEntity
import com.hiringai.mobile.data.repository.CandidateRepository
import com.hiringai.mobile.data.repository.ApplicationRepository
import com.hiringai.mobile.ml.LocalLLMService
import com.hiringai.mobile.ml.bridge.CandidateInfo
import com.hiringai.mobile.util.PdfExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CandidatesUiState(
    val isLoading: Boolean = false,
    val candidates: List<CandidateEntity> = emptyList(),
    val error: String? = null,
    val importProgress: ImportProgress? = null
)

data class ImportProgress(
    val total: Int,
    val processed: Int,
    val success: Int,
    val failed: Int,
    val currentFile: String
)

class CandidatesViewModel(
    private val candidateRepository: CandidateRepository,
    private val applicationRepository: ApplicationRepository,
    private val llmService: LocalLLMService,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CandidatesUiState())
    val uiState: StateFlow<CandidatesUiState> = _uiState.asStateFlow()

    init {
        loadCandidates()
    }

    fun loadCandidates() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val candidates = candidateRepository.getAllCandidates()
                _uiState.value = _uiState.value.copy(isLoading = false, candidates = candidates)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun addCandidate(name: String, email: String, phone: String, resume: String) {
        viewModelScope.launch {
            try {
                candidateRepository.insert(CandidateEntity(name = name, email = email, phone = phone, resume = resume))
                loadCandidates()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun updateCandidate(candidate: CandidateEntity) {
        viewModelScope.launch {
            try {
                candidateRepository.update(candidate)
                loadCandidates()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deleteCandidate(candidate: CandidateEntity) {
        viewModelScope.launch {
            try {
                candidateRepository.delete(candidate)
                loadCandidates()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun generateProfile(candidate: CandidateEntity, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val profile = llmService.generateCandidateProfile(CandidateInfo(name = candidate.name, email = candidate.email, phone = candidate.phone, resume = candidate.resume))
                candidateRepository.update(candidate.copy(profile = profile))
                loadCandidates()
                onComplete(profile)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun importFromPdf(uri: Uri, onComplete: (CandidateEntity?) -> Unit) {
        viewModelScope.launch {
            try {
                val text = PdfExtractor.extractText(context, uri)
                if (text == null || text.isEmpty()) {
                    onComplete(null)
                    return@launch
                }
                val cleanText = PdfExtractor.cleanText(text)
                val name = "候选人_${System.currentTimeMillis() % 10000}"
                val profile = llmService.generate(cleanText, maxTokens = 800)

                val candidate = CandidateEntity(name = name, resume = profile ?: cleanText)
                candidateRepository.insert(candidate)
                loadCandidates()
                onComplete(candidate)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
                onComplete(null)
            }
        }
    }

    fun batchImport(uris: List<Uri>, onProgress: (ImportProgress) -> Unit, onComplete: (Int, Int) -> Unit) {
        viewModelScope.launch {
            val total = uris.size
            var success = 0
            var failed = 0

            for ((index, uri) in uris.withIndex()) {
                _uiState.value = _uiState.value.copy(
                    importProgress = ImportProgress(
                        total = total,
                        processed = index,
                        success = success,
                        failed = failed,
                        currentFile = uri.lastPathSegment ?: "文件${index + 1}"
                    )
                )
                onProgress(_uiState.value.importProgress!!)

                try {
                    val text = PdfExtractor.extractText(context, uri)
                    if (text != null && text.isNotEmpty()) {
                        val cleanText = PdfExtractor.cleanText(text)
                        val profile = llmService.generate(cleanText, maxTokens = 800)
                        val name = "候选人_${System.currentTimeMillis() % 10000}"
                        candidateRepository.insert(CandidateEntity(name = name, resume = profile ?: cleanText))
                        success++
                    } else {
                        failed++
                    }
                } catch (e: Exception) {
                    failed++
                }
            }

            _uiState.value = _uiState.value.copy(importProgress = null)
            loadCandidates()
            onComplete(success, failed)
        }
    }

    fun submitApplication(candidateId: Long, jobId: Long, coverLetter: String) {
        viewModelScope.launch {
            try {
                val existingApps = applicationRepository.getApplicationsByCandidateId(candidateId)
                if (existingApps.any { it.jobId == jobId }) {
                    _uiState.value = _uiState.value.copy(error = "您已投递过该职位")
                    return@launch
                }
                applicationRepository.insert(ApplicationEntity(jobId = jobId, candidateId = candidateId, status = "pending", coverLetter = coverLetter))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun isLlmLoaded(): Boolean = llmService.isModelLoaded

    class Factory(
        private val candidateRepository: CandidateRepository,
        private val applicationRepository: ApplicationRepository,
        private val llmService: LocalLLMService,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CandidatesViewModel(candidateRepository, applicationRepository, llmService, context) as T
        }
    }
}