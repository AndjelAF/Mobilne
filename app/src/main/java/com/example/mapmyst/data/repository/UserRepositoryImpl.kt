package com.example.mapmyst.data.repository

import android.util.Log
import com.example.mapmyst.data.firebase.FirebaseModule
import com.example.mapmyst.data.model.User
import com.example.mapmyst.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementacija UserRepository interfejsa koja koristi Firebase kao backend.
 * Ova klasa upravlja korisničkim podacima, autentifikacijom i operacijama korisnika.
 */
@Singleton
class UserRepositoryImpl @Inject constructor() : UserRepository {

    private val auth: FirebaseAuth = FirebaseModule.auth
    private val firestore: FirebaseFirestore = FirebaseModule.firestore
    private val usersCollection = firestore.collection(FirebaseModule.USERS_COLLECTION)

    private val TAG = "UserRepo"

    override suspend fun registerUser(user: User): Result<User> {
        return try {
            // Proverava da li već postoji korisnik sa istim username-om
            val existingUser = usersCollection.whereEqualTo("username", user.username).get().await()
            if (!existingUser.isEmpty) {
                return Result.failure(Exception("Username already exists"))
            }

            // Kreiranje Firebase Auth korisnika
            val authResult = auth.createUserWithEmailAndPassword(user.email, user.password ?: "").await()
            val firebaseUser = authResult.user ?: throw Exception("Failed to create user")

            // KAŠNJENJE da se Auth state sinhronizuje
            kotlinx.coroutines.delay(1000) // 1 sekunda

            // Postavljanje display name-a
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(user.username)
                .build()
            firebaseUser.updateProfile(profileUpdates).await()

            // Kreiranje korisničkog dokumenta u Firestore-u
            val userWithId = user.copy(
                id = firebaseUser.uid,
                password = null // Ne čuvamo lozinku u Firestore
            )

            usersCollection.document(firebaseUser.uid).set(userWithId).await()

            Result.success(userWithId)
        } catch (e: FirebaseAuthException) {
            // Ako se dogodi greška u Auth, obrišemo kreiranog korisnika
            auth.currentUser?.delete()
            Result.failure(Exception("Authentication failed: ${e.message}"))
        } catch (e: Exception) {
            // Ako se dogodi greška u Firestore, obrišemo kreiranog korisnika
            auth.currentUser?.delete()
            Result.failure(Exception("Registration failed: ${e.message}"))
        }
    }

    override suspend fun loginUser(email: String, password: String): Result<User> {
        return try {
            // Prijava korišćenjem email-a i lozinke direktno
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: throw Exception("Login failed")

            // Dohvatimo podatke korisnika iz Firestore-a
            val userDoc = usersCollection.document(firebaseUser.uid).get().await()

            if (!userDoc.exists()) {
                return Result.failure(Exception("User data not found"))
            }

            val user = userDoc.toObject(User::class.java) ?: throw Exception("Failed to convert document to User")
            Result.success(user)
        } catch (e: FirebaseAuthException) {
            Result.failure(Exception("Authentication failed: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("Login failed: ${e.message}"))
        }
    }

    override suspend fun loginUserWithUsername(username: String, password: String): Result<User> {
        return try {
            // Prvo moramo dobiti email koji odgovara korisničkom imenu
            val querySnapshot = usersCollection.whereEqualTo("username", username).get().await()

            if (querySnapshot.isEmpty) {
                return Result.failure(Exception("User not found"))
            }

            val userDoc = querySnapshot.documents.first()
            val email = userDoc.getString("email") ?: throw Exception("Email not found")

            // Zatim se prijavimo korišćenjem email-a i lozinke
            loginUser(email, password)
        } catch (e: Exception) {
            Result.failure(Exception("Login with username failed: ${e.message}"))
        }
    }

    override suspend fun getUserById(userId: String): Result<User> {
        return try {
            val documentSnapshot = usersCollection.document(userId).get().await()

            if (!documentSnapshot.exists()) {
                return Result.failure(Exception("User not found"))
            }

            val user = documentSnapshot.toObject(User::class.java)
                ?: return Result.failure(Exception("Failed to convert document to User"))

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to get user: ${e.message}"))
        }
    }
    override suspend fun updateUser(user: User): Result<User> {
        return try {
            Log.d(TAG, "🔄 Starting updateUser for: ${user.username}")

            // Ne dozvoljavamo ažuriranje lozinke kroz ovu metodu
            val userToUpdate = user.copy(password = null)

            // Proveravamo da li se username menja i da li već postoji
            val currentUserDoc = usersCollection.document(user.id).get().await()
            val currentUser = currentUserDoc.toObject(User::class.java)

            // Ako se username menja, proveravamo da li novi username već postoji
            if (currentUser != null && currentUser.username != user.username) {
                val existingUserQuery = usersCollection
                    .whereEqualTo("username", user.username)
                    .get()
                    .await()

                val conflictingUser = existingUserQuery.documents.find { doc ->
                    doc.id != user.id
                }

                if (conflictingUser != null) {
                    return Result.failure(Exception("Username already exists"))
                }
            }

            Log.d(TAG, "💾 Writing to Firestore...")
            //Koristimo set() sa merge opcijom
            usersCollection.document(user.id)
                .set(userToUpdate, com.google.firebase.firestore.SetOptions.merge())
                .await()

            // Ažuriramo Firebase Auth profil ako se menja korisničko ime
            val currentAuthUser = auth.currentUser
            if (currentAuthUser != null && currentAuthUser.uid == user.id) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(user.username)
                    .build()
                currentAuthUser.updateProfile(profileUpdates).await()
            }

            Log.d(TAG, "✅ User updated successfully: ${userToUpdate.username}")
            Result.success(userToUpdate)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to update user: ${e.message}")
            Result.failure(Exception("Failed to update user: ${e.message}"))
        }
    }

    suspend fun forceRefreshUser(userId: String): Result<User> {
        return try {
            Log.d(TAG, "🔄 Force refreshing user: $userId")

            // Koristimo fresh fetch iz baze
            val documentSnapshot = usersCollection.document(userId)
                .get(com.google.firebase.firestore.Source.SERVER) // Force server fetch
                .await()

            if (!documentSnapshot.exists()) {
                return Result.failure(Exception("User not found"))
            }

            val user = documentSnapshot.toObject(User::class.java)
                ?: return Result.failure(Exception("Failed to convert document to User"))

            Log.d(TAG, "✅ User force refreshed: ${user.username}")
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to force refresh user: ${e.message}")
            Result.failure(Exception("Failed to force refresh user: ${e.message}"))
        }
    }

    override suspend fun updateUserScore(userId: String, newScore: Int): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("score", newScore)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to update user score: ${e.message}"))
        }
    }

    override suspend fun addFoundCache(userId: String, cacheId: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("foundCaches", FieldValue.arrayUnion(cacheId))
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to add found cache: ${e.message}"))
        }
    }

    override suspend fun addCreatedCache(userId: String, cacheId: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("createdCaches", FieldValue.arrayUnion(cacheId))
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to add created cache: ${e.message}"))
        }
    }

    override suspend fun removeCreatedCache(userId: String, cacheId: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .update("createdCaches", FieldValue.arrayRemove(cacheId))
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to remove created cache: ${e.message}"))
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to sign out: ${e.message}"))
        }
    }

    override fun getCurrentUser(): Flow<User?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            val firebaseUser = auth.currentUser
            if (firebaseUser != null) {
                // Ako je korisnik prijavljen, dohvati njegove podatke iz Firestore-a
                usersCollection.document(firebaseUser.uid).get()
                    .addOnSuccessListener { document ->
                        val user = document.toObject(User::class.java)
                        trySend(user)
                    }
                    .addOnFailureListener {
                        trySend(null)
                    }
            } else {
                trySend(null)
            }
        }

        auth.addAuthStateListener(authStateListener)

        awaitClose {
            auth.removeAuthStateListener(authStateListener)
        }
    }

    override fun getUsersWithTopScores(limit: Int): Flow<List<User>> = callbackFlow {
        val listenerRegistration = usersCollection
            .orderBy("score", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }

                val users = snapshot?.documents?.mapNotNull { it.toObject(User::class.java) } ?: emptyList()
                trySend(users)
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }
}