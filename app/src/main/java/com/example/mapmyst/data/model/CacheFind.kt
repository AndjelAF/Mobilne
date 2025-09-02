package com.example.mapmyst.data.model

data class CacheFind(
    val id: String = "",
    val cacheId: String = "",
    val userId: String = "",
    val username: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val locationAtFind: Location = Location(),
    val pointsEarned: Int = 0,
    val note: String? = null,
    val verified: Boolean = false
) {
    // Validation
    fun isValidFind(): Boolean {
        return cacheId.isNotEmpty() &&
                userId.isNotEmpty() &&
                username.isNotEmpty() &&
                pointsEarned > 0
    }
}