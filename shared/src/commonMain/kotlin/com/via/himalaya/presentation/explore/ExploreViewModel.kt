package com.via.himalaya.presentation.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.via.himalaya.domain.repo.AppConfigRepository
import com.via.himalaya.domain.repo.AuthRepository
import com.via.himalaya.domain.repo.FeedbackRepository
import com.via.himalaya.domain.repo.TrekRepository
import kotlinx.datetime.Clock
import com.via.himalaya.util.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExploreViewModel(
    private val trekRepository: TrekRepository,
    private val appConfigRepository: AppConfigRepository,
    private val feedbackRepository: FeedbackRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 10
    }

    private val _state = MutableStateFlow(ExploreScreenUIState())
    val state: StateFlow<ExploreScreenUIState> = _state.asStateFlow()

    init {
        loadTreks()
        viewModelScope.launch {
            trekRepository.syncNavigatorTrek()
        }
        observeAppConfig()
    }

    /**
     * Mirrors the shared config into this screen's state.
     *
     * Collected rather than read once, so the banner appears when the launch
     * fetch lands instead of only on the next visit to Explore. The refresh
     * itself is fired once per process from MainActivity - repeating it here
     * would re-request on every entry to this screen.
     */
    private fun observeAppConfig() = viewModelScope.launch {
        appConfigRepository.config.collect { config ->
            _state.update { it.copy(banner = config.banner) }
        }
    }

    fun onEvent(event: ExploreScreenUIEvent) {
        when(event) {
            is ExploreScreenUIEvent.OnLoadMore -> {
                loadTreks()
            }
            is ExploreScreenUIEvent.ClearErrorToast -> {
                _state.update { it.copy(errorToast = null) }
            }
            is ExploreScreenUIEvent.OnClearSearch -> {
                clearSearchedTreks()
            }
            is ExploreScreenUIEvent.OnSearchTrek -> {
                searchTrek(event.query)
            }
            is ExploreScreenUIEvent.OnHideBanner -> {
                _state.update { it.copy(isBannerHidden = true) }
            }
            is ExploreScreenUIEvent.OnRequestTrek -> {
                submitTrekRequest(event.text)
            }
            is ExploreScreenUIEvent.ClearMessageDisplay -> {
                _state.update { it.copy(messageDisplay = null) }
            }
        }
    }

    /**
     * Sends the user's feedback, then reports what actually happened.
     *
     * The id is built here, once, rather than inside the repository, so a retry
     * would reuse it - the server keys on it to collapse duplicate deliveries.
     * Format matches navigator trek ids ("<email>/<millis>"), which is the
     * existing convention for client-built ids in this app.
     */
    private fun submitTrekRequest(text: String) = viewModelScope.launch {
        if (text.isBlank()) return@launch

        val email = authRepository.getCurrentUser()?.email
        if (email.isNullOrBlank()) {
            // Explore sits behind the sign-in gate, so this should be
            // unreachable. Reported rather than ignored, because silently
            // dropping something the user typed is the worse failure.
            _state.update { it.copy(errorToast = "Please sign in again to send feedback.") }
            return@launch
        }

        val feedbackId = "$email/${Clock.System.now().toEpochMilliseconds()}"

        when (feedbackRepository.submitFeedback(feedbackId, text)) {
            is Result.Success -> _state.update {
                it.copy(messageDisplay = "Thanks — we'll look into adding that trek.")
            }
            else -> _state.update {
                it.copy(errorToast = "Could not send that. Please try again.")
            }
        }
    }

    private fun loadTreks() = viewModelScope.launch {
        if(state.value.hasNextPage) {
            _state.update { it.copy(isLoading = true) }
            val treks = trekRepository.getTreks(
                page = state.value.page + 1,
                limit = PAGE_SIZE,
                seed = state.value.seed
            )
            when(treks) {
                is Result.Success -> {
                    val updatedTreks = state.value.treks.toMutableList()
                    val newTreks = treks.data?.treks.orEmpty()
                    updatedTreks.addAll(newTreks)
                    _state.update {
                        it.copy(
                            page = treks.data?.page?.pageNo ?: 0,
                            isLoading = false,
                            treks = updatedTreks,
                            seed = treks.data?.page?.seed,
                            hasNextPage = treks.data?.page?.hasNext ?: false
                        )
                    }
                }
                is Result.Error -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorToast = treks.message
                        )
                    }
                }
                is Result.Loading -> {
                    _state.update {
                        it.copy(
                            isLoading = true
                        )
                    }
                }
            }
        }
    }

    private fun searchTrek(query: String) = viewModelScope.launch {
        _state.update {
            it.copy(isLoading = true, isSearching = true, searchQuery = query)
        }
        val result = trekRepository.searchTreks(query)
        if(result is Result.Success) {
            _state.update {
                it.copy(
                    tempTreks = if (state.value.tempTreks.isEmpty()) state.value.treks else state.value.tempTreks,
                    treks = result.data ?: emptyList(),
                    isLoading = false
                )
            }
        } else {
            _state.update {
                it.copy(
                    isLoading = false,
                    errorToast = "No treks found: ${result.message}"
                )
            }
        }
    }

    private fun clearSearchedTreks() = viewModelScope.launch {
        _state.update {
            it.copy(
                treks = if (state.value.tempTreks.isNotEmpty()) state.value.tempTreks else state.value.treks,
                tempTreks = emptyList(),
                isSearching = false,
                searchQuery = ""
            )
        }
    }

}