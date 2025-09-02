package com.example.mapmyst.data.firebase

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage

object FirebaseModule {
    val auth = Firebase.auth
    val firestore = Firebase.firestore
    val storage = Firebase.storage

    // Konstante za Firestore kolekcije
    const val USERS_COLLECTION = "users"
    const val CACHES_COLLECTION = "caches"
    const val CACHE_FINDS_COLLECTION = "cache_finds"
    const val LOCATION_EVIDENCE_COLLECTION = "location_evidence"
    const val LOCATION_SESSIONS_COLLECTION = "location_sessions"

    // Konstante za Storage putanje
    const val PROFILE_IMAGES_PATH = "profile_images"
    const val CACHE_IMAGES_PATH = "cache_images"
}