package com.example.mapmyst.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapmyst.data.model.User
import com.example.mapmyst.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AuthViewModel upravlja stanjem autentifikacije korisnika.
 * Sadrži logiku za registraciju, prijavu, odjavu i praćenje trenutnog korisnika.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val firebaseAuth = FirebaseAuth.getInstance()

    // State za UI autentifikacije
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // State za trenutnog korisnika
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // State za loading indikatore
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // State za greške
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        viewModelScope.launch {
            val firebaseUser = auth.currentUser
            if (firebaseUser != null) {
                // Firebase korisnik postoji - učitaj podatke iz baze
                try {
                    // kratko kašnjenje da se završi Firestore operacija
                    delay(500)
                    val result = userRepository.getUserById(firebaseUser.uid)
                    result.fold(
                        onSuccess = { user ->
                            _currentUser.value = user
                            _authState.value = AuthState.Authenticated(user)
                        },
                        onFailure = {
                            //  Pokušavamo ponovo posle kratkog kašnjenja
                            delay(1000)
                            val retryResult = userRepository.getUserById(firebaseUser.uid)
                            retryResult.fold(
                                onSuccess = { user ->
                                    _currentUser.value = user
                                    _authState.value = AuthState.Authenticated(user)
                                },
                                onFailure = {
                                    _authState.value = AuthState.Unauthenticated
                                }
                            )
                        }
                    )
                } catch (e: Exception) {
                    _authState.value = AuthState.Unauthenticated
                }
            } else {
                // Nema Firebase korisnika
                _currentUser.value = null
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    init {
        firebaseAuth.addAuthStateListener(authStateListener)
    }

    /**
     * Registruje novog korisnika
     */
    fun registerUser(
        username: String,
        firstName: String,
        lastName: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                // Validacija podataka
                val validationResult = validateRegistrationInput(
                    username, firstName, lastName, email, phone, password, confirmPassword
                )

                if (!validationResult.isValid) {
                    val errorMsg = validationResult.errorMessage ?: "Validation failed"
                    _errorMessage.value = errorMsg
                    _authState.value = AuthState.Error(errorMsg)
                    return@launch
                }

                // Kreiranje korisnika
                val user = User(
                    username = username.trim(),
                    firstName = firstName.trim(),
                    lastName = lastName.trim(),
                    email = email.trim().lowercase(),
                    phone = phone.trim(),
                    password = password
                )

                // Poziv repository-a za registraciju
                val result = userRepository.registerUser(user)

                result.fold(
                    onSuccess = { registeredUser ->
                        // Odmah postavljamo state nakon uspešne registracije
                        _currentUser.value = registeredUser
                        _authState.value = AuthState.Authenticated(registeredUser)
                        // AuthStateListener će se takođe pozvati, ali neće prepisati već postavljen state
                    },
                    onFailure = { exception ->
                        val errorMsg = exception.message ?: "Registration failed"
                        _errorMessage.value = errorMsg
                        _authState.value = AuthState.Error(errorMsg)
                    }
                )
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unexpected error during registration"
                _errorMessage.value = errorMsg
                _authState.value = AuthState.Error(errorMsg)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Prijavljuje korisnika pomoću email-a i lozinke
     */
    fun loginWithEmail(email: String, password: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                // Osnovna validacija
                if (email.isBlank() || password.isBlank()) {
                    _errorMessage.value = "Email and password are required"
                    _authState.value = AuthState.Error("Email and password are required")
                    return@launch
                }

                if (!isValidEmail(email)) {
                    _errorMessage.value = "Invalid email format"
                    _authState.value = AuthState.Error("Invalid email format")
                    return@launch
                }

                // Poziv repository-a za prijavu
                val result = userRepository.loginUser(email.trim().lowercase(), password)

                result.fold(
                    onSuccess = { user ->
                        //  Odmah postavljamo state
                        _currentUser.value = user
                        _authState.value = AuthState.Authenticated(user)
                    },
                    onFailure = { exception ->
                        val errorMsg = exception.message ?: "Login failed"
                        _errorMessage.value = errorMsg
                        _authState.value = AuthState.Error(errorMsg)
                    }
                )
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unexpected error during login"
                _errorMessage.value = errorMsg
                _authState.value = AuthState.Error(errorMsg)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Prijavljuje korisnika pomoću username-a i lozinke
     */
    fun loginWithUsername(username: String, password: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                // Osnovna validacija
                if (username.isBlank() || password.isBlank()) {
                    _errorMessage.value = "Username and password are required"
                    _authState.value = AuthState.Error("Username and password are required")
                    return@launch
                }

                // Validacija username formata
                if (!username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                    _errorMessage.value = "Username can only contain letters, numbers, and underscores"
                    _authState.value = AuthState.Error("Invalid username format")
                    return@launch
                }

                // Poziv repository-a za prijavu sa username-om
                val result = userRepository.loginUserWithUsername(username.trim(), password)

                result.fold(
                    onSuccess = { user ->
                        //  Odmah postavljamo state
                        _currentUser.value = user
                        _authState.value = AuthState.Authenticated(user)
                    },
                    onFailure = { exception ->
                        val errorMsg = when {
                            exception.message?.contains("User not found") == true ->
                                "Username not found"
                            exception.message?.contains("Authentication failed") == true ->
                                "Invalid username or password"
                            else -> exception.message ?: "Login failed"
                        }
                        _errorMessage.value = errorMsg
                        _authState.value = AuthState.Error(errorMsg)
                    }
                )
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unexpected error during login"
                _errorMessage.value = errorMsg
                _authState.value = AuthState.Error(errorMsg)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Odjavljuje trenutnog korisnika
     */
    fun signOut() {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                val result = userRepository.signOut()

                result.fold(
                    onSuccess = {
                        // Odmah resetujemo state
                        _currentUser.value = null
                        _authState.value = AuthState.Unauthenticated
                        _errorMessage.value = null
                    },
                    onFailure = { exception ->
                        val errorMsg = exception.message ?: "Sign out failed"
                        _errorMessage.value = errorMsg
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unexpected error during sign out"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Ažurira trenutnog korisnika u state-u
     * Poziva se iz drugih viewModel-ova kada se korisnik ažurira
     */
    fun updateCurrentUser(user: User) {
        _currentUser.value = user
        _authState.value = AuthState.Authenticated(user)
    }

    /**
     * Resetuje error message
     */
    fun clearError() {
        _errorMessage.value = null
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Initial
        }
    }

    override fun onCleared() {
        super.onCleared()
        firebaseAuth.removeAuthStateListener(authStateListener)
    }

    /**
     * Validira podatke za registraciju
     */
    private fun validateRegistrationInput(
        username: String,
        firstName: String,
        lastName: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String
    ): ValidationResult {

        // Username validacija
        if (username.isBlank()) {
            return ValidationResult(false, "Username is required")
        }
        if (username.length < 3 || username.length > 30) {
            return ValidationResult(false, "Username must be between 3 and 30 characters")
        }
        if (!username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            return ValidationResult(false, "Username can only contain letters, numbers, and underscores")
        }

        // First name validacija
        if (firstName.isBlank()) {
            return ValidationResult(false, "First name is required")
        }
        if (firstName.length < 2 || firstName.length > 50) {
            return ValidationResult(false, "First name must be between 2 and 50 characters")
        }

        // Last name validacija
        if (lastName.isBlank()) {
            return ValidationResult(false, "Last name is required")
        }
        if (lastName.length < 2 || lastName.length > 50) {
            return ValidationResult(false, "Last name must be between 2 and 50 characters")
        }

        // Email validacija
        if (email.isBlank()) {
            return ValidationResult(false, "Email is required")
        }
        if (!isValidEmail(email)) {
            return ValidationResult(false, "Invalid email format")
        }

        // Phone validacija
        if (phone.isBlank()) {
            return ValidationResult(false, "Phone number is required")
        }
        if (!phone.matches(Regex("^[+]?[0-9]{8,15}$"))) {
            return ValidationResult(false, "Phone number must contain between 8 and 15 digits, may start with +")
        }

        // Password validacija
        if (password.isBlank()) {
            return ValidationResult(false, "Password is required")
        }
        if (password.length < 8) {
            return ValidationResult(false, "Password must be at least 8 characters long")
        }
        if (!password.matches(Regex("^(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#\$%^&*]).{8,}$"))) {
            return ValidationResult(false, "Password must contain at least one uppercase letter, one number, and one special character")
        }

        // Confirm password validacija
        if (password != confirmPassword) {
            return ValidationResult(false, "Passwords do not match")
        }

        return ValidationResult(true, null)
    }

    /**
     * Proverava da li je email validan
     */
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    /**
     * Data klasa za rezultat validacije
     */
    private data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String?
    )
}

/**
 * Sealed class koji predstavlja različita stanja autentifikacije
 */
sealed class AuthState {
    object Initial : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}