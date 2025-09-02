package com.example.mapmyst.presentation.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapmyst.data.model.User
import com.example.mapmyst.domain.repository.StorageRepository
import com.example.mapmyst.domain.repository.UserRepository
import com.example.mapmyst.utils.ImageUtils
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val storageRepository: StorageRepository,
    application: Application
) : AndroidViewModel(application) {

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val TAG = "UserViewModel"

    // UI State
    private val _profileState = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val profileState: StateFlow<ProfileState> = _profileState.asStateFlow()

    // Trenutni korisnik - GLAVNI SOURCE OF TRUTH za UI
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error state
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Editing state
    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    // Top users
    private val _topUsers = MutableStateFlow<List<User>>(emptyList())
    val topUsers: StateFlow<List<User>> = _topUsers.asStateFlow()

    init {
        loadUserProfile()
        loadTopUsers()
    }

    fun loadUserProfile() {
        val currentFirebaseUser = firebaseAuth.currentUser
        if (currentFirebaseUser != null) {
            loadUserProfileById(currentFirebaseUser.uid)
        } else {
            _profileState.value = ProfileState.Error("User not authenticated")
        }
    }

    private fun loadUserProfileById(userId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _profileState.value = ProfileState.Loading

                val result = userRepository.getUserById(userId)
                result.fold(
                    onSuccess = { user ->
                        Log.d(TAG, "✅ Loaded user profile: ${user.username}")
                        _currentUser.value = user
                        _profileState.value = ProfileState.Success(user)
                    },
                    onFailure = { exception ->
                        val errorMsg = exception.message ?: "Failed to load user"
                        Log.e(TAG, "❌ Failed to load user: $errorMsg")
                        _errorMessage.value = errorMsg
                        _profileState.value = ProfileState.Error(errorMsg)
                    }
                )
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unexpected error"
                Log.e(TAG, "❌ Unexpected error: $errorMsg")
                _errorMessage.value = errorMsg
                _profileState.value = ProfileState.Error(errorMsg)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateUserProfile(
        username: String,
        firstName: String,
        lastName: String,
        phone: String,
        onSuccess: ((User) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🔄 Starting profile update...")
                _isLoading.value = true
                _errorMessage.value = null

                val currentUser = _currentUser.value ?: return@launch
                Log.d(TAG, "👤 Current user: ${currentUser.username}")

                val validationResult = validateProfileInput(username, firstName, lastName, phone)
                if (!validationResult.isValid) {
                    _errorMessage.value = validationResult.errorMessage
                    return@launch
                }

                val updatedUser = currentUser.copy(
                    username = username.trim(),
                    firstName = firstName.trim(),
                    lastName = lastName.trim(),
                    phone = phone.trim()
                )

                Log.d(TAG, "🔄 Updating to: ${updatedUser.username}")

                // 1. ODMAH ažuriramo lokalni state za trenutni UI response
                _currentUser.value = updatedUser
                _profileState.value = ProfileState.Success(updatedUser)
                _isEditing.value = false

                // 2. ODMAH pozivamo callback
                onSuccess?.invoke(updatedUser)

                // 3. Ažuriramo bazu u pozadini
                val result = userRepository.updateUser(updatedUser)
                result.fold(
                    onSuccess = { savedUser ->
                        Log.d(TAG, "✅ Successfully saved to database: ${savedUser.username}")

                        // Ažuriramo state sa podacima iz baze (ako su drugačiji)
                        if (savedUser != updatedUser) {
                            _currentUser.value = savedUser
                            _profileState.value = ProfileState.Success(savedUser)
                            onSuccess?.invoke(savedUser)
                        }
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "❌ Failed to save to database: ${exception.message}")

                        // Vraćamo stari state
                        _currentUser.value = currentUser
                        _profileState.value = ProfileState.Success(currentUser)

                        val errorMsg = exception.message ?: "Failed to update profile"
                        _errorMessage.value = errorMsg
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception during update: ${e.message}")
                _errorMessage.value = e.message ?: "Unexpected error during profile update"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Ažurira profilnu sliku korisnika - DEBUG VERZIJA
     */
    fun updateProfilePicture(
        imageUri: Uri,
        onSuccess: ((User) -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🔄 Starting profile picture update...")
                Log.d(TAG, "📱 Original URI: $imageUri")
                Log.d(TAG, "📱 URI scheme: ${imageUri.scheme}")
                Log.d(TAG, "📱 URI path: ${imageUri.path}")

                _isLoading.value = true
                _errorMessage.value = null

                val currentUser = _currentUser.value ?: return@launch
                val context = getApplication<Application>().applicationContext

                // 1. Kompresuj sliku
                Log.d(TAG, "🔄 Starting image compression...")
                val compressedFile = ImageUtils.compressAndRotateImage(context, imageUri)
                if (compressedFile == null) {
                    Log.e(TAG, "❌ Failed to compress image")
                    _errorMessage.value = "Failed to process image"
                    return@launch
                }

                Log.d(TAG, "✅ Image compressed successfully")
                Log.d(TAG, "📁 Compressed file path: ${compressedFile.absolutePath}")
                Log.d(TAG, "📁 File exists: ${compressedFile.exists()}")
                Log.d(TAG, "📁 File size: ${compressedFile.length()} bytes")

                val compressedUri = Uri.fromFile(compressedFile)
                Log.d(TAG, "📁 Compressed URI: $compressedUri")

                // 2. Upload na Firebase Storage
                Log.d(TAG, "🔄 Starting Firebase upload...")
                val uploadResult = storageRepository.uploadProfileImage(currentUser.id, compressedUri)

                uploadResult.fold(
                    onSuccess = { downloadUrl ->
                        Log.d(TAG, "✅ Image uploaded successfully: $downloadUrl")

                        // 3. Ažuriraj korisnika sa novim URL-om
                        val updatedUser = currentUser.copy(profilePicture = downloadUrl)

                        // 4. Ažuriraj lokalni state odmah
                        _currentUser.value = updatedUser
                        _profileState.value = ProfileState.Success(updatedUser)
                        onSuccess?.invoke(updatedUser)

                        // 5. Sačuvaj u Firestore
                        val updateResult = userRepository.updateUser(updatedUser)
                        updateResult.fold(
                            onSuccess = { savedUser ->
                                Log.d(TAG, "✅ Profile picture saved to database")
                                if (savedUser != updatedUser) {
                                    _currentUser.value = savedUser
                                    _profileState.value = ProfileState.Success(savedUser)
                                    onSuccess?.invoke(savedUser)
                                }
                            },
                            onFailure = { exception ->
                                Log.e(TAG, "❌ Failed to save to database: ${exception.message}")
                                // Vraćamo stari state
                                _currentUser.value = currentUser
                                _profileState.value = ProfileState.Success(currentUser)
                                _errorMessage.value = exception.message ?: "Failed to save profile picture"
                            }
                        )

                        // 6. Obriši temp fajl
                        compressedFile.delete()
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "❌ Failed to upload image: ${exception.message}")
                        _errorMessage.value = exception.message ?: "Failed to upload image"
                        compressedFile.delete()
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception during profile picture update: ${e.message}")
                e.printStackTrace()
                _errorMessage.value = e.message ?: "Unexpected error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Briše profilnu sliku korisnika
     */
    fun removeProfilePicture(onSuccess: ((User) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val currentUser = _currentUser.value ?: return@launch

                if (currentUser.profilePicture.isNullOrBlank()) {
                    _errorMessage.value = "No profile picture to remove"
                    return@launch
                }

                // Brisanje iz Storage-a (optional - može ostati za history)
                // storageRepository.deleteImage("profile_images/${currentUser.id}.jpg")

                // Ažuriranje korisnika
                val updatedUser = currentUser.copy(profilePicture = null)

                _currentUser.value = updatedUser
                _profileState.value = ProfileState.Success(updatedUser)
                onSuccess?.invoke(updatedUser)

                // Čuvanje u bazu
                userRepository.updateUser(updatedUser).fold(
                    onSuccess = { savedUser ->
                        Log.d(TAG, "✅ Profile picture removed successfully")
                        if (savedUser != updatedUser) {
                            _currentUser.value = savedUser
                            _profileState.value = ProfileState.Success(savedUser)
                            onSuccess?.invoke(savedUser)
                        }
                    },
                    onFailure = { exception ->
                        _currentUser.value = currentUser
                        _profileState.value = ProfileState.Success(currentUser)
                        _errorMessage.value = exception.message ?: "Failed to remove profile picture"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unexpected error"
            } finally {
                _isLoading.value = false
            }
        }
    }



    fun updateUserScore(newScore: Int, onSuccess: ((User) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val currentUser = _currentUser.value ?: return@launch

                // 1. ODMAH ažuriramo lokalni state
                val updatedUser = currentUser.copy(score = newScore)
                _currentUser.value = updatedUser
                _profileState.value = ProfileState.Success(updatedUser)
                onSuccess?.invoke(updatedUser)

                // 2. Ažuriramo bazu u pozadini
                val result = userRepository.updateUserScore(currentUser.id, newScore)
                result.fold(
                    onSuccess = {
                        Log.d(TAG, "✅ Score updated in database")
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "❌ Failed to update score: ${exception.message}")
                        // Vraćamo stari state
                        _currentUser.value = currentUser
                        _profileState.value = ProfileState.Success(currentUser)
                        _errorMessage.value = exception.message ?: "Failed to update score"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unexpected error"
            }
        }
    }

    fun addFoundCache(cacheId: String, cacheValue: Int, onSuccess: ((User) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val currentUser = _currentUser.value ?: return@launch

                // 1. ODMAH ažuriramo lokalni state
                val newScore = currentUser.score + cacheValue
                val updatedFoundCaches = currentUser.foundCaches + cacheId
                val updatedUser = currentUser.copy(
                    score = newScore,
                    foundCaches = updatedFoundCaches
                )

                _currentUser.value = updatedUser
                _profileState.value = ProfileState.Success(updatedUser)
                onSuccess?.invoke(updatedUser)

                // 2. Ažuriramo bazu u pozadini
                val result = userRepository.addFoundCache(currentUser.id, cacheId)
                result.fold(
                    onSuccess = {
                        updateUserScore(newScore)
                    },
                    onFailure = { exception ->
                        // Vraćamo stari state
                        _currentUser.value = currentUser
                        _profileState.value = ProfileState.Success(currentUser)
                        _errorMessage.value = exception.message ?: "Failed to add found cache"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unexpected error"
            }
        }
    }

    fun addCreatedCache(cacheId: String, onSuccess: ((User) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val currentUser = _currentUser.value ?: return@launch

                // 1. ODMAH ažuriramo lokalni state
                val updatedCreatedCaches = currentUser.createdCaches + cacheId
                val updatedUser = currentUser.copy(createdCaches = updatedCreatedCaches)

                _currentUser.value = updatedUser
                _profileState.value = ProfileState.Success(updatedUser)
                onSuccess?.invoke(updatedUser)

                // 2. Ažuriramo bazu u pozadini
                val result = userRepository.addCreatedCache(currentUser.id, cacheId)
                result.fold(
                    onSuccess = { },
                    onFailure = { exception ->
                        // Vraćamo stari state
                        _currentUser.value = currentUser
                        _profileState.value = ProfileState.Success(currentUser)
                        _errorMessage.value = exception.message ?: "Failed to add created cache"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unexpected error"
            }
        }
    }

    fun removeCreatedCache(cacheId: String, onSuccess: ((User) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val currentUser = _currentUser.value ?: return@launch

                // 1. ODMAH ažuriramo lokalni state
                val updatedCreatedCaches = currentUser.createdCaches - cacheId
                val updatedUser = currentUser.copy(createdCaches = updatedCreatedCaches)

                _currentUser.value = updatedUser
                _profileState.value = ProfileState.Success(updatedUser)
                onSuccess?.invoke(updatedUser)

                // 2. Ažuriramo bazu u pozadini
                val result = userRepository.removeCreatedCache(currentUser.id, cacheId)
                result.fold(
                    onSuccess = { },
                    onFailure = { exception ->
                        // Vraćamo stari state
                        _currentUser.value = currentUser
                        _profileState.value = ProfileState.Success(currentUser)
                        _errorMessage.value = exception.message ?: "Failed to remove created cache"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unexpected error"
            }
        }
    }

    fun loadTopUsers(limit: Int = 10) {
        viewModelScope.launch {
            try {
                userRepository.getUsersWithTopScores(limit).collect { users ->
                    _topUsers.value = users
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load top users"
            }
        }
    }

    fun setEditingMode(isEditing: Boolean) {
        _isEditing.value = isEditing
        if (!isEditing) {
            _errorMessage.value = null
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun refreshProfile() {
        loadUserProfile()
    }

    /**
     * Ažurira korisnika iz spoljašnjeg izvora (npr. AuthViewModel)
     * glavni način sinhronizacije
     */
    fun updateUserFromExternal(user: User) {
        Log.d(TAG, "🔄 UserViewModel changed: ${user.username}")

        // Proveravamo da li se podaci stvarno razlikuju
        val currentUser = _currentUser.value
        if (currentUser == null || currentUser.id != user.id || currentUser != user) {
            _currentUser.value = user
            _profileState.value = ProfileState.Success(user)
            Log.d(TAG, "✅ User state updated from external source")
        } else {
            Log.d(TAG, "ℹ️ No sync needed UserVM -> AuthVM")
        }
    }

    private fun validateProfileInput(
        username: String,
        firstName: String,
        lastName: String,
        phone: String
    ): ValidationResult {

        if (username.isBlank()) {
            return ValidationResult(false, "Username is required")
        }
        if (username.length < 3 || username.length > 30) {
            return ValidationResult(false, "Username must be between 3 and 30 characters")
        }
        if (!username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            return ValidationResult(false, "Username can only contain letters, numbers, and underscores")
        }

        if (firstName.isBlank()) {
            return ValidationResult(false, "First name is required")
        }
        if (firstName.length < 2 || firstName.length > 50) {
            return ValidationResult(false, "First name must be between 2 and 50 characters")
        }

        if (lastName.isBlank()) {
            return ValidationResult(false, "Last name is required")
        }
        if (lastName.length < 2 || lastName.length > 50) {
            return ValidationResult(false, "Last name must be between 2 and 50 characters")
        }

        if (phone.isBlank()) {
            return ValidationResult(false, "Phone number is required")
        }
        if (!phone.matches(Regex("^[+]?[0-9]{8,15}$"))) {
            return ValidationResult(false, "Phone number must contain between 8 and 15 digits, may start with +")
        }

        return ValidationResult(true, null)
    }

    private data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String?
    )
}

sealed class ProfileState {
    object Loading : ProfileState()
    data class Success(val user: User) : ProfileState()
    data class Error(val message: String) : ProfileState()
}