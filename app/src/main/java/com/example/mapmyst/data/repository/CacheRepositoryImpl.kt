package com.example.mapmyst.data.repository

import com.example.mapmyst.data.firebase.FirebaseModule
import com.example.mapmyst.data.model.Cache
import com.example.mapmyst.data.model.CacheCategory
import com.example.mapmyst.data.model.CacheFind
import com.example.mapmyst.data.model.CacheStatus
import com.example.mapmyst.data.model.Location
import com.example.mapmyst.domain.repository.CacheRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementacija CacheRepository interfejsa koja koristi Firebase kao backend.
 * Ova klasa upravlja keš objektima, njihovim kreacijama i pretragama.
 */
@Singleton
class CacheRepositoryImpl @Inject constructor() : CacheRepository {

    private val firestore: FirebaseFirestore = FirebaseModule.firestore
    private val cachesCollection = firestore.collection(FirebaseModule.CACHES_COLLECTION)
    private val usersCollection = firestore.collection(FirebaseModule.USERS_COLLECTION)
    private val cacheFindsCollection = FirebaseModule.firestore.collection(FirebaseModule.CACHE_FINDS_COLLECTION)


    override suspend fun createCache(cache: Cache): Result<Cache> {
        return try {
            // Generisanje ID-a za novi keš ako nije definisan
            val cacheId = cache.id.takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
            val cacheWithId = cache.copy(id = cacheId)

            // Čuvanje keš-a u Firestore
            cachesCollection.document(cacheId).set(cacheWithId).await()

            // Dodavanje keš-a u listu kreiranih keš-ova korisnika
            val userId = cache.createdByUser
            if (userId.isNotEmpty()) {
                usersCollection.document(userId)
                    .update("createdCaches", FieldValue.arrayUnion(cacheId))
                    .await()
            }

            Result.success(cacheWithId)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to create cache: ${e.message}"))
        }
    }

    override suspend fun getCacheById(cacheId: String): Result<Cache> {
        return try {
            val documentSnapshot = cachesCollection.document(cacheId).get().await()

            if (!documentSnapshot.exists()) {
                return Result.failure(Exception("Cache not found"))
            }

            val cache = documentSnapshot.toObject(Cache::class.java)
                ?: return Result.failure(Exception("Failed to convert document to Cache"))

            Result.success(cache)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to get cache: ${e.message}"))
        }
    }

    override suspend fun updateCache(cache: Cache): Result<Cache> {
        return try {
            cachesCollection.document(cache.id).set(cache).await()
            Result.success(cache)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to update cache: ${e.message}"))
        }
    }

    override suspend fun deleteCache(cacheId: String): Result<Unit> {
        return try {
            // Prvo dobavljamo keš da bismo znali kreatora
            val cacheSnapshot = cachesCollection.document(cacheId).get().await()
            val cache = cacheSnapshot.toObject(Cache::class.java)

            // Brisanje keš-a iz Firestore-a
            cachesCollection.document(cacheId).delete().await()

            // Uklanjanje reference iz liste kreiranih keš-ova korisnika
            val creatorId = cache?.createdByUser
            if (!creatorId.isNullOrEmpty()) {
                usersCollection.document(creatorId)
                    .update("createdCaches", FieldValue.arrayRemove(cacheId))
                    .await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to delete cache: ${e.message}"))
        }
    }

    override suspend fun markCacheAsFound(cacheId: String, userId: String): Result<Unit> {
        return try {
            // Transakcija koja osigurava atomičnost operacije
            firestore.runTransaction { transaction ->
                // Dodajemo keš u listu pronađenih keš-ova korisnika
                val userRef = usersCollection.document(userId)
                transaction.update(userRef, "foundCaches", FieldValue.arrayUnion(cacheId))

                // Dodajemo korisnika u listu korisnika koji su pronašli keš
                val cacheRef = cachesCollection.document(cacheId)
                transaction.update(cacheRef, "foundByUsers", FieldValue.arrayUnion(userId))
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to mark cache as found: ${e.message}"))
        }
    }

    override suspend fun updateCacheStatus(cacheId: String, newStatus: String): Result<Unit> {
        return try {
            cachesCollection.document(cacheId)
                .update("status", CacheStatus.valueOf(newStatus))
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to update cache status: ${e.message}"))
        }
    }

    override fun getNearbyActiveCaches(location: Location, radiusInMeters: Double): Flow<List<Cache>> = callbackFlow {
        // Pretvaranje u kilometre
        val radiusInKm = radiusInMeters / 1000.0

        val listenerRegistration = cachesCollection
            .whereEqualTo("status", CacheStatus.ACTIVE)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }

                val caches = snapshot?.documents?.mapNotNull { it.toObject(Cache::class.java) } ?: emptyList()

                // Filtriranje keš-ova koji su u zadatom radijusu
                val nearbyCaches = caches.filter { cache ->
                    Location.calculateDistance(location, cache.location) <= radiusInMeters
                }

                trySend(nearbyCaches)
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    override fun getAllCachesByUser(userId: String): Flow<List<Cache>> = callbackFlow {
        val listenerRegistration = cachesCollection
            .whereEqualTo("createdByUser", userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }

                val caches = snapshot?.documents?.mapNotNull { it.toObject(Cache::class.java) } ?: emptyList()
                trySend(caches)
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    override fun getFoundCachesByUser(userId: String): Flow<List<Cache>> = callbackFlow {
        // Prvo dobavimo korisnika da vidimo koje je keš-ove pronašao
        val userDocRef = usersCollection.document(userId)

        val userListener = userDocRef.addSnapshotListener { userSnapshot, userError ->
            if (userError != null) {
                close(userError)
                return@addSnapshotListener
            }

            val foundCacheIds = userSnapshot?.get("foundCaches") as? List<String> ?: emptyList()

            if (foundCacheIds.isEmpty()) {
                trySend(emptyList())
                return@addSnapshotListener
            }

            // Sada dobavimo sve keš-ove čiji ID-evi su u listi pronađenih
            // Firebase ograničava na 10 vrednosti za whereIn, pa moramo praviti batches
            val cacheBatches = foundCacheIds.chunked(10)
            val allCaches = mutableListOf<Cache>()

            cacheBatches.forEach { batch ->
                cachesCollection
                    .whereIn("id", batch)
                    .get()
                    .addOnSuccessListener { cacheSnapshot ->
                        val batchCaches = cacheSnapshot.documents.mapNotNull { it.toObject(Cache::class.java) }
                        allCaches.addAll(batchCaches)

                        // Kada dodamo poslednju grupu, pošaljemo potpunu listu
                        if (allCaches.size >= foundCacheIds.size ||
                            batch == cacheBatches.last()) {
                            trySend(allCaches.toList())
                        }
                    }
                    .addOnFailureListener { e ->
                        close(e)
                    }
            }
        }

        awaitClose {
            userListener.remove()
        }
    }

    override fun getCreatedCachesByUser(userId: String): Flow<List<Cache>> = callbackFlow {
        val listenerRegistration = cachesCollection
            .whereEqualTo("createdByUser", userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }

                val caches = snapshot?.documents?.mapNotNull { it.toObject(Cache::class.java) } ?: emptyList()
                trySend(caches)
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    override fun getCachesByCategory(category: CacheCategory): Flow<List<Cache>> = callbackFlow {
        val listenerRegistration = cachesCollection
            .whereEqualTo("category", category)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }

                val caches = snapshot?.documents?.mapNotNull { it.toObject(Cache::class.java) } ?: emptyList()
                trySend(caches)
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    override fun getCachesByDifficulty(difficulty: Int): Flow<List<Cache>> = callbackFlow {
        val listenerRegistration = cachesCollection
            .whereEqualTo("difficulty", difficulty)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }

                val caches = snapshot?.documents?.mapNotNull { it.toObject(Cache::class.java) } ?: emptyList()
                trySend(caches)
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    override fun getCachesByTerrain(terrain: Int): Flow<List<Cache>> = callbackFlow {
        val listenerRegistration = cachesCollection
            .whereEqualTo("terrain", terrain)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }

                val caches = snapshot?.documents?.mapNotNull { it.toObject(Cache::class.java) } ?: emptyList()
                trySend(caches)
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    override fun getExpiredCaches(): Flow<List<Cache>> = callbackFlow {
        val currentTime = System.currentTimeMillis()

        val listenerRegistration = cachesCollection
            .whereLessThan("expires", currentTime)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }

                val caches = snapshot?.documents?.mapNotNull { it.toObject(Cache::class.java) } ?: emptyList()
                trySend(caches)
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    override fun searchCachesByTags(tags: List<String>): Flow<List<Cache>> = callbackFlow {
        if (tags.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }

        val primaryTag = tags.first()

        val listenerRegistration = cachesCollection
            .whereArrayContains("tags", primaryTag)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }

                val allCaches = snapshot?.documents?.mapNotNull {
                    it.toObject(Cache::class.java)
                } ?: emptyList()

                // Ako imamo više tagova, filtriramo rezultat
                val filteredCaches = if (tags.size > 1) {
                    val otherTags = tags.subList(1, tags.size)
                    allCaches.filter { cache ->
                        cache.tags.containsAll(otherTags)
                    }
                } else {
                    allCaches
                }

                trySend(filteredCaches)
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    override suspend fun saveCacheFind(cacheFind: CacheFind): Result<Unit> {
        return try {
            val findId = cacheFind.id.takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
            val findWithId = cacheFind.copy(id = findId)

            // Transakcija koja osigurava atomićnost
            firestore.runTransaction { transaction ->
                // Sačuvaj find
                val findRef = cacheFindsCollection.document(findId)
                transaction.set(findRef, findWithId)

                // Ažuriraj cache statistike
                val cacheRef = cachesCollection.document(cacheFind.cacheId)
                transaction.update(cacheRef, mapOf(
                    "findCount" to FieldValue.increment(1),
                    "lastFoundAt" to System.currentTimeMillis(),
                    "foundByUsers" to FieldValue.arrayUnion(cacheFind.userId)
                ))

                // Dodaj u user found caches
                val userRef = usersCollection.document(cacheFind.userId)
                transaction.update(userRef, mapOf(
                    "foundCaches" to FieldValue.arrayUnion(cacheFind.cacheId),
                    "score" to FieldValue.increment(cacheFind.pointsEarned.toLong())
                ))
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to save cache find: ${e.message}"))
        }
    }

    override suspend fun getCacheFinds(userId: String): Flow<List<CacheFind>> = callbackFlow {
        val listenerRegistration = cacheFindsCollection
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }

                val finds = snapshot?.documents?.mapNotNull {
                    it.toObject(CacheFind::class.java)
                } ?: emptyList()

                trySend(finds)
            }

        awaitClose { listenerRegistration.remove() }
    }

    override suspend fun hasUserFoundCache(cacheId: String, userId: String): Result<Boolean> {
        return try {
            val snapshot = cacheFindsCollection
                .whereEqualTo("cacheId", cacheId)
                .whereEqualTo("userId", userId)
                .limit(1)
                .get()
                .await()

            Result.success(!snapshot.isEmpty)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to check if user found cache: ${e.message}"))
        }
    }

    override suspend fun getDiscoverableCachesNear(location: Location, radiusInMeters: Double): Flow<List<Cache>> = callbackFlow {
        android.util.Log.d("CacheRepository", "🔍 Searching for caches near lat=${location.latitude}, lng=${location.longitude}, radius=${radiusInMeters}m")

        val listenerRegistration = cachesCollection
            .whereEqualTo("status", CacheStatus.ACTIVE)
            .whereGreaterThan("expires", System.currentTimeMillis())
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    android.util.Log.e("CacheRepository", "❌ Error in getDiscoverableCachesNear", e)
                    close(e)
                    return@addSnapshotListener
                }

                val allCaches = snapshot?.documents?.mapNotNull {
                    it.toObject(Cache::class.java)
                } ?: emptyList()

                android.util.Log.d("CacheRepository", "📦 Total active caches found: ${allCaches.size}")

                // Filtriraj po distanci (Firebase nema geo queries)
                val nearbyCaches = allCaches.filter { cache ->
                    val distance = Location.calculateDistance(location, cache.location)
                    android.util.Log.d("CacheRepository", "📏 Cache ${cache.id} distance: ${distance}m")
                    distance <= radiusInMeters
                }.sortedBy { cache ->
                    Location.calculateDistance(location, cache.location)
                }

                android.util.Log.d("CacheRepository", "🎯 Nearby discoverable caches: ${nearbyCaches.size}")
                trySend(nearbyCaches)
            }

        awaitClose {
            listenerRegistration.remove()
            android.util.Log.d("CacheRepository", "🔚 Stopped listening for discoverable caches")
        }
    }
}