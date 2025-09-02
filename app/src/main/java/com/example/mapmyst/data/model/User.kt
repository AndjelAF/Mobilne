package com.example.mapmyst.data.model

import com.google.firebase.firestore.Exclude

data class User(
    val id: String = "",
    val username: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val profilePicture: String? = null,

    //  @Exclude da Firestore ne pokušava da mapira password
    @Exclude val password: String? = null,

    val created: Long = System.currentTimeMillis(),
    val score: Int = 0,
    val foundCaches: List<String> = emptyList(),
    val createdCaches: List<String> = emptyList()
) {
    // Computed properties - ove će biti excluded iz Firestore
    @get:Exclude
    val validPhone: Boolean
        get() = isValidPhone()

    @get:Exclude
    val validEmail: Boolean
        get() = isValidEmail()

    @get:Exclude
    val validUsername: Boolean
        get() = isValidUsername()

    // Validation functions - ove će biti excluded iz Firestore
    @Exclude
    fun isValidUsername(): Boolean = username.length in 3..30

    @Exclude
    fun isValidName(name: String): Boolean = name.length in 2..50

    @Exclude
    fun isValidEmail(): Boolean = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

    @Exclude
    fun isValidPhone(): Boolean = phone.matches(Regex("^[+]?[0-9]{8,15}$"))
}