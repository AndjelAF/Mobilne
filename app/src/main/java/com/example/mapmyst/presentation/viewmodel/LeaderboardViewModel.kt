package com.example.mapmyst.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapmyst.data.model.User
import com.example.mapmyst.domain.repository.LeaderboardRepository
import com.example.mapmyst.domain.repository.LeaderboardTimeFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeaderboardUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val topUsers: List<User> = emptyList(),
    val currentUserRank: Int? = null,
    val currentUser: User? = null,
    val searchQuery: String = "",
    val searchResults: List<User> = emptyList(),
    val isSearching: Boolean = false,
    val selectedTimeFrame: LeaderboardTimeFrame = LeaderboardTimeFrame.ALL_TIME,
    val error: String? = null,
    val isEmpty: Boolean = false
)

@OptIn(FlowPreview::class)
@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val leaderboardRepository: LeaderboardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        loadLeaderboard()
        setupSearch()
    }

    private fun setupSearch() {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        // Return empty flow when query is blank
                        kotlinx.coroutines.flow.flowOf(emptyList())
                    } else {
                        leaderboardRepository.searchUsers(query)
                    }
                }
                .collect { results ->
                    _uiState.value = _uiState.value.copy(
                        searchResults = results,
                        isSearching = false
                    )
                }
        }
    }

    fun loadLeaderboard(timeFrame: LeaderboardTimeFrame = _uiState.value.selectedTimeFrame) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                selectedTimeFrame = timeFrame
            )

            try {
                leaderboardRepository.getTopUsersByTimeFrame(timeFrame, 50)
                    .collect { users ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            topUsers = users,
                            isEmpty = users.isEmpty()
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Greška pri učitavanju leaderboard-a: ${e.message}"
                )
            }
        }
    }

    fun loadUserRank(userId: String) {
        viewModelScope.launch {
            try {
                leaderboardRepository.getUserRank(userId)
                    .collect { rank ->
                        _uiState.value = _uiState.value.copy(currentUserRank = rank)
                    }
            } catch (e: Exception) {
                android.util.Log.e("LeaderboardVM", "Error loading user rank", e)
            }
        }
    }

    fun setCurrentUser(user: User) {
        _uiState.value = _uiState.value.copy(currentUser = user)
        loadUserRank(user.id)
    }

    fun refreshLeaderboard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)

            val result = leaderboardRepository.refreshLeaderboard()

            result.fold(
                onSuccess = {
                    loadLeaderboard()
                    _uiState.value = _uiState.value.copy(isRefreshing = false)
                },
                onFailure = { exception ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        error = "Greška pri osvežavanju: ${exception.message}"
                    )
                }
            )
        }
    }

    fun searchUsers(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            isSearching = query.isNotBlank()
        )
        _searchQuery.value = query
    }

    fun clearSearch() {
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false
        )
        _searchQuery.value = ""
    }

    fun selectTimeFrame(timeFrame: LeaderboardTimeFrame) {
        if (timeFrame != _uiState.value.selectedTimeFrame) {
            loadLeaderboard(timeFrame)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun getUserPosition(user: User): Int? {
        val users = if (_uiState.value.isSearching) {
            _uiState.value.searchResults
        } else {
            _uiState.value.topUsers
        }

        val index = users.indexOfFirst { it.id == user.id }
        return if (index == -1) null else index + 1
    }

    fun getTimeFrameDisplayText(timeFrame: LeaderboardTimeFrame): String {
        return when (timeFrame) {
            LeaderboardTimeFrame.ALL_TIME -> "Svih vremena"
            LeaderboardTimeFrame.THIS_MONTH -> "Ovaj mesec"
            LeaderboardTimeFrame.THIS_WEEK -> "Ova nedelja"
            LeaderboardTimeFrame.TODAY -> "Danas"
        }
    }

    fun getRankText(rank: Int?): String {
        return rank?.let { "#$it" } ?: "Nerangiran"
    }

    fun isCurrentUser(user: User): Boolean {
        return _uiState.value.currentUser?.id == user.id
    }

    fun getScoreDifferenceFromTop(user: User): Int? {
        val topUsers = _uiState.value.topUsers
        if (topUsers.isEmpty()) return null

        val topScore = topUsers.firstOrNull()?.score ?: return null
        return topScore - user.score
    }

    fun getScoreDifferenceFromRank(user: User, targetRank: Int): Int? {
        val users = _uiState.value.topUsers
        if (targetRank <= 0 || targetRank > users.size) return null

        val targetUser = users.getOrNull(targetRank - 1) ?: return null
        return targetUser.score - user.score
    }
}