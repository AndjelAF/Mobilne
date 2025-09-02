package com.example.mapmyst.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapmyst.data.model.Cache
import com.example.mapmyst.data.model.CacheCategory
import com.example.mapmyst.data.model.CacheStatus
import com.example.mapmyst.data.model.Location
import com.example.mapmyst.domain.repository.CacheRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CacheUiState(
    val isLoading: Boolean = false,
    val caches: List<Cache> = emptyList(),
    val nearbyCaches: List<Cache> = emptyList(),
    val userCreatedCaches: List<Cache> = emptyList(),
    val userFoundCaches: List<Cache> = emptyList(),
    val selectedCache: Cache? = null,
    val error: String? = null,
    val isCreatingCache: Boolean = false,
    val cacheCreated: Boolean = false
)

data class CacheCreationState(
    val description: String = "",
    val category: CacheCategory = CacheCategory.EASY,
    val difficulty: Int = 1,
    val terrain: Int = 1,
    val value: Int = 1,
    val tags: List<String> = emptyList(),
    val expiresIn: Long = 24 * 60 * 60 * 1000L, // 24 sata default
    val location: Location? = null,
    val picture: String? = null,
    val single: Boolean = false,
    val isValid: Boolean = false
)

@HiltViewModel
class CacheViewModel @Inject constructor(
    private val cacheRepository: CacheRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CacheUiState())
    val uiState: StateFlow<CacheUiState> = _uiState.asStateFlow()

    private val _creationState = MutableStateFlow(CacheCreationState())
    val creationState: StateFlow<CacheCreationState> = _creationState.asStateFlow()

    // ===== CACHE OPERATIONS =====

    fun createCache(userId: String) {
        val currentState = _creationState.value

        if (!validateCreationState(currentState)) {
            _uiState.value = _uiState.value.copy(
                error = "Molim vas popunite sva obavezna polja"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingCache = true, error = null)

            val cache = Cache(
                id = "",
                description = currentState.description,
                category = currentState.category,
                difficulty = currentState.difficulty,
                terrain = currentState.terrain,
                value = currentState.value,
                tags = currentState.tags,
                expires = System.currentTimeMillis() + currentState.expiresIn,
                location = currentState.location!!,
                picture = currentState.picture,
                single = currentState.single,
                createdByUser = userId
            )

            val result = cacheRepository.createCache(cache)

            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isCreatingCache = false,
                        cacheCreated = true
                    )
                    resetCreationState()
                    loadUserCreatedCaches(userId)
                },
                onFailure = { exception ->
                    _uiState.value = _uiState.value.copy(
                        isCreatingCache = false,
                        error = "Greška pri kreiranju cache-a: ${exception.message}"
                    )
                }
            )
        }
    }

    fun loadNearbyCaches(userLocation: Location, radiusInMeters: Double = 5000.0) {
        viewModelScope.launch {
            cacheRepository.getNearbyActiveCaches(userLocation, radiusInMeters)
                .collect { caches ->
                    _uiState.value = _uiState.value.copy(nearbyCaches = caches)
                }
        }
    }

    fun loadUserCreatedCaches(userId: String) {
        viewModelScope.launch {
            cacheRepository.getCreatedCachesByUser(userId)
                .collect { caches ->
                    _uiState.value = _uiState.value.copy(userCreatedCaches = caches)
                }
        }
    }

    fun loadUserFoundCaches(userId: String) {
        viewModelScope.launch {
            cacheRepository.getFoundCachesByUser(userId)
                .collect { caches ->
                    _uiState.value = _uiState.value.copy(userFoundCaches = caches)
                }
        }
    }

    fun markCacheAsFound(cacheId: String, userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = cacheRepository.markCacheAsFound(cacheId, userId)

            result.fold(
                onSuccess = {
                    loadUserFoundCaches(userId)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                },
                onFailure = { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Greška pri označavanju cache-a: ${exception.message}"
                    )
                }
            )
        }
    }

    fun getCacheById(cacheId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = cacheRepository.getCacheById(cacheId)

            result.fold(
                onSuccess = { cache ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        selectedCache = cache
                    )
                },
                onFailure = { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Cache nije pronađen: ${exception.message}"
                    )
                }
            )
        }
    }

    fun searchCachesByCategory(category: CacheCategory) {
        viewModelScope.launch {
            cacheRepository.getCachesByCategory(category)
                .collect { caches ->
                    _uiState.value = _uiState.value.copy(caches = caches)
                }
        }
    }

    fun searchCachesByTags(tags: List<String>) {
        viewModelScope.launch {
            cacheRepository.searchCachesByTags(tags)
                .collect { caches ->
                    _uiState.value = _uiState.value.copy(caches = caches)
                }
        }
    }

    // ===== CACHE CREATION STATE MANAGEMENT =====

    fun updateDescription(description: String) {
        _creationState.value = _creationState.value.copy(description = description)
        validateCreationState()
    }

    fun updateCategory(category: CacheCategory) {
        _creationState.value = _creationState.value.copy(category = category)
        validateCreationState()
    }

    fun updateDifficulty(difficulty: Int) {
        _creationState.value = _creationState.value.copy(difficulty = difficulty)
        validateCreationState()
    }

    fun updateTerrain(terrain: Int) {
        _creationState.value = _creationState.value.copy(terrain = terrain)
        validateCreationState()
    }

    fun updateValue(value: Int) {
        _creationState.value = _creationState.value.copy(value = value)
        validateCreationState()
    }

    fun updateTags(tags: List<String>) {
        _creationState.value = _creationState.value.copy(tags = tags)
        validateCreationState()
    }

    fun updateExpiresIn(expiresIn: Long) {
        _creationState.value = _creationState.value.copy(expiresIn = expiresIn)
        validateCreationState()
    }

    fun updateLocation(location: Location) {
        _creationState.value = _creationState.value.copy(location = location)
        validateCreationState()
    }

    fun updatePicture(picture: String?) {
        _creationState.value = _creationState.value.copy(picture = picture)
        validateCreationState()
    }

    fun updateSingle(single: Boolean) {
        _creationState.value = _creationState.value.copy(single = single)
        validateCreationState()
    }

    private fun validateCreationState() {
        val state = _creationState.value
        val isValid = validateCreationState(state)
        _creationState.value = state.copy(isValid = isValid)
    }

    private fun validateCreationState(state: CacheCreationState): Boolean {
        return state.description.length >= 10 &&
                state.location != null &&
                state.difficulty in 1..5 &&
                state.terrain in 1..5 &&
                state.value in 1..100
    }

    fun resetCreationState() {
        _creationState.value = CacheCreationState()
    }

    // ===== ERROR HANDLING =====

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearCacheCreated() {
        _uiState.value = _uiState.value.copy(cacheCreated = false)
    }

    fun clearSelectedCache() {
        _uiState.value = _uiState.value.copy(selectedCache = null)
    }

    // ===== UTILITY FUNCTIONS =====

    fun getCacheIcon(category: CacheCategory): String {
        return when (category) {
            CacheCategory.EASY -> "🎯"
            CacheCategory.ADVENTURE -> "🏔️"
            CacheCategory.PUZZLE -> "🧩"
            CacheCategory.HISTORICAL -> "🏛️"
            CacheCategory.NATURE -> "🌿"
            CacheCategory.URBAN -> "🏙️"
            CacheCategory.MYSTERY -> "❓"
        }
    }

    fun getDifficultyText(difficulty: Int): String {
        return when (difficulty) {
            1 -> "Vrlo lako"
            2 -> "Lako"
            3 -> "Srednje"
            4 -> "Teško"
            5 -> "Vrlo teško"
            else -> "Nepoznato"
        }
    }

    fun getTerrainText(terrain: Int): String {
        return when (terrain) {
            1 -> "Pristupačno"
            2 -> "Lako hodanje"
            3 -> "Srednje hodanje"
            4 -> "Teško hodanje"
            5 -> "Vrlo teško"
            else -> "Nepoznato"
        }
    }

    fun getCategoryText(category: CacheCategory): String {
        return when (category) {
            CacheCategory.EASY -> "Lako"
            CacheCategory.ADVENTURE -> "Avantura"
            CacheCategory.PUZZLE -> "Slagalica"
            CacheCategory.HISTORICAL -> "Istorijski"
            CacheCategory.NATURE -> "Priroda"
            CacheCategory.URBAN -> "Gradski"
            CacheCategory.MYSTERY -> "Misterija"
        }
    }

    // ===== CACHE MANAGEMENT =====

    fun deleteCache(cacheId: String, userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = cacheRepository.deleteCache(cacheId)

            result.fold(
                onSuccess = {
                    loadUserCreatedCaches(userId)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                },
                onFailure = { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Greška pri brisanju cache-a: ${exception.message}"
                    )
                }
            )
        }
    }

    fun updateCacheStatus(cacheId: String, newStatus: CacheStatus, userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = cacheRepository.updateCacheStatus(cacheId, newStatus.name)

            result.fold(
                onSuccess = {
                    loadUserCreatedCaches(userId)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                },
                onFailure = { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Greška pri ažuriranju statusa: ${exception.message}"
                    )
                }
            )
        }
    }

    fun loadCacheFinds(userId: String) {
        viewModelScope.launch {
            cacheRepository.getCacheFinds(userId)
                .collect { finds ->
                    // Možete dodati finds u state ako je potrebno
                    android.util.Log.d("CacheViewModel", "Loaded ${finds.size} cache finds")
                }
        }
    }

    fun checkIfUserFoundCache(cacheId: String, userId: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = cacheRepository.hasUserFoundCache(cacheId, userId)
            result.fold(
                onSuccess = { hasFound -> callback(hasFound) },
                onFailure = { callback(false) }
            )
        }
    }
}