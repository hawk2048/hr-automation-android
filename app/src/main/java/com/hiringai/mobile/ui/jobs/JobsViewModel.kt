package com.hiringai.mobile.ui.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hiringai.mobile.data.local.entity.JobEntity
import com.hiringai.mobile.data.repository.JobRepository
import com.hiringai.mobile.ml.LocalLLMService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class JobsUiState(
    val isLoading: Boolean = false,
    val jobs: List<JobEntity> = emptyList(),
    val error: String? = null
)

class JobsViewModel(
    private val repository: JobRepository,
    private val llmService: LocalLLMService
) : ViewModel() {

    private val _uiState = MutableStateFlow(JobsUiState())
    val uiState: StateFlow<JobsUiState> = _uiState.asStateFlow()

    init {
        loadJobs()
    }

    fun loadJobs() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val jobs = repository.getAllJobs()
                _uiState.value = _uiState.value.copy(isLoading = false, jobs = jobs)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun addJob(title: String, requirements: String) {
        viewModelScope.launch {
            try {
                repository.insert(JobEntity(title = title, requirements = requirements))
                loadJobs()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun updateJob(job: JobEntity) {
        viewModelScope.launch {
            try {
                repository.update(job)
                loadJobs()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deleteJob(job: JobEntity) {
        viewModelScope.launch {
            try {
                repository.delete(job)
                loadJobs()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun generateProfile(job: JobEntity, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val profile = llmService.generateJobProfile(job)
                repository.update(job.copy(profile = profile))
                loadJobs()
                onComplete(profile)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    class Factory(
        private val repository: JobRepository,
        private val llmService: LocalLLMService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return JobsViewModel(repository, llmService) as T
        }
    }
}