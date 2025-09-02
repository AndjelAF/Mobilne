package com.example.mapmyst.data.model

data class Cache(
    val id: String = "",
    val single: Boolean = false,
    val description: String = "",
    val picture: String? = null,
    val value: Int = 1,
    val found: Boolean = false,
    val created: Long = System.currentTimeMillis(),
    val expires: Long = System.currentTimeMillis() + 24 * 60 * 60 * 1000L,
    val foundByUsers: List<String> = emptyList(),
    val createdByUser: String = "",
    val location: Location = Location(),
    val status: CacheStatus = CacheStatus.ACTIVE,
    val difficulty: Int = 1,
    val terrain: Int = 1,
    val category: CacheCategory = CacheCategory.EASY,
    val tags: List<String> = emptyList(),
    val findCount: Int = 0,
    val maxFinds: Int = if (single) 1 else Int.MAX_VALUE,
    val lastFoundAt: Long? = null,
    val isDiscoverable: Boolean = true,
    val minDistance: Double = 10.0 // metri za pronalazak
) {

    // Validation functions (opciono)
    fun isValidDescription(): Boolean = description.length in 10..500
    fun isValidValue(): Boolean = value in 1..100
    fun isValidDifficulty(): Boolean = difficulty in 1..5
    fun isValidTerrain(): Boolean = terrain in 1..5
}