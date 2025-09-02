package com.example.mapmyst.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.mapmyst.data.firebase.FirebaseModule
import com.example.mapmyst.domain.repository.StorageRepository
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementacija StorageRepository interfejsa koja koristi Firebase Storage kao backend.
 * Ova klasa upravlja otpremanjem, preuzimanjem i brisanjem slika, kao i kompresijom slika pre otpremanja.
 */
@Singleton
class StorageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : StorageRepository {

    private val storage: FirebaseStorage = FirebaseModule.storage
    private val profileImagesRef: StorageReference = storage.reference.child(FirebaseModule.PROFILE_IMAGES_PATH)
    private val cacheImagesRef: StorageReference = storage.reference.child(FirebaseModule.CACHE_IMAGES_PATH)

    private val TAG = "StorageRepository"

    override suspend fun uploadProfileImage(userId: String, imageUri: Uri): Result<String> {
        return try {
            Log.d(TAG, "🔄 Starting profile image upload for user: $userId")
            Log.d(TAG, "📁 Image URI: $imageUri")

            // Kreira se referenca za sliku profila sa ID-om korisnika
            val fileRef = profileImagesRef.child("$userId.jpg")
            Log.d(TAG, "📁 Firebase path: ${fileRef.path}")

            uploadImage(imageUri, fileRef)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to upload profile image: ${e.message}")
            Result.failure(Exception("Failed to upload profile image: ${e.message}"))
        }
    }

    override suspend fun uploadCacheImage(cacheId: String, imageUri: Uri): Result<String> {
        return try {
            // Generisanje jedinstvenog imena za sliku keša sa ID-em keša i UUID-om
            val imageName = "${cacheId}_${UUID.randomUUID()}.jpg"
            val fileRef = cacheImagesRef.child(imageName)
            uploadImage(imageUri, fileRef)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to upload cache image: ${e.message}"))
        }
    }

    /**
     * Pomoćna metoda za otpremanje slike na Firebase Storage.
     * REŠAVA PROBLEM SA FILE PROVIDER URI KORISTEĆI BYTES UMESTO URI!
     */
    private suspend fun uploadImage(imageUri: Uri, fileRef: StorageReference): Result<String> {
        return try {
            Log.d(TAG, "🔄 Starting upload to Firebase Storage...")
            Log.d(TAG, "📁 URI scheme: ${imageUri.scheme}")
            Log.d(TAG, "📁 URI path: ${imageUri.path}")

            when (imageUri.scheme) {
                "file" -> {
                    // Za lokalne fajlove - možemo koristiti putFile
                    val file = File(imageUri.path ?: "")
                    Log.d(TAG, "📁 File exists: ${file.exists()}")
                    Log.d(TAG, "📁 File size: ${file.length()} bytes")

                    if (!file.exists()) {
                        return Result.failure(Exception("File does not exist at path: ${file.absolutePath}"))
                    }

                    if (file.length() == 0L) {
                        return Result.failure(Exception("File is empty: ${file.absolutePath}"))
                    }

                    // UMESTO putFile, koristimo putBytes za konzistentnost
                    Log.d(TAG, "🔄 Reading file as bytes...")
                    val bytes = file.readBytes()
                    Log.d(TAG, "📁 Read ${bytes.size} bytes from file")

                    val uploadTask = fileRef.putBytes(bytes)
                    uploadTask.await()
                }

                "content" -> {
                    // Za content URI (FileProvider, Gallery) - koristimo putBytes
                    Log.d(TAG, "🔄 Content URI detected - reading as bytes...")

                    // Čitamo URI kao bytes preko ContentResolver
                    val bytes = readUriAsBytes(imageUri)
                    if (bytes == null || bytes.isEmpty()) {
                        return Result.failure(Exception("Cannot read image data from URI or file is empty"))
                    }

                    Log.d(TAG, "📁 Read ${bytes.size} bytes from content URI")

                    // Upload bytes umesto URI - OVO REŠAVA PROBLEM!
                    val uploadTask = fileRef.putBytes(bytes)
                    uploadTask.await()
                }

                else -> {
                    return Result.failure(Exception("Unsupported URI scheme: ${imageUri.scheme}"))
                }
            }

            Log.d(TAG, "✅ Upload completed successfully")

            // Dobijanje URL-a za preuzimanje
            Log.d(TAG, "🔄 Getting download URL...")
            val downloadUrl = fileRef.downloadUrl.await().toString()
            Log.d(TAG, "✅ Download URL obtained: $downloadUrl")

            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Upload failed: ${e.message}")
            Log.e(TAG, "❌ Exception type: ${e::class.java.simpleName}")
            e.printStackTrace()
            Result.failure(Exception("Failed to upload image: ${e.message}"))
        }
    }

    /**
     * KLJUČNA METODA - Čita URI kao byte array preko ContentResolver
     * Ovo rešava FileProvider problem definitivno!
     */
    private suspend fun readUriAsBytes(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "🔄 Opening content resolver stream...")

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                Log.d(TAG, "✅ Stream opened successfully")
                val bytes = inputStream.readBytes()
                Log.d(TAG, "✅ Read ${bytes.size} bytes from stream")
                bytes
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reading URI bytes: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    override suspend fun getImageUrl(path: String): Result<String> {
        return try {
            val storageRef = storage.reference.child(path)
            val url = storageRef.downloadUrl.await().toString()
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to get image URL: ${e.message}"))
        }
    }

    override suspend fun deleteImage(path: String): Result<Unit> {
        return try {
            val storageRef = storage.reference.child(path)
            storageRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to delete image: ${e.message}"))
        }
    }

    override suspend fun compressImage(context: Context, imageUri: Uri): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Otvaranje input stream-a za učitavanje slike
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return@withContext Result.failure(Exception("Cannot open input stream"))

            // Prvo očitavanje dimenzija slike bez učitavanja celokupne slike u memoriju
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            val imageWidth = options.outWidth
            val imageHeight = options.outHeight

            // Ponovno otvaranje streama za učitavanje slike
            val newInputStream = context.contentResolver.openInputStream(imageUri)
                ?: return@withContext Result.failure(Exception("Cannot open input stream"))

            // Računanje scale faktora za smanjenje veličine slike
            val maxDimension = 1024 // Maksimalna dimenzija slike (širina ili visina)
            val scaleFactor = maxOf(1, minOf(imageWidth / maxDimension, imageHeight / maxDimension))

            // Postavljanje opcija za dekodiranje sa scale faktorom
            options.apply {
                inJustDecodeBounds = false
                inSampleSize = scaleFactor
            }

            // Učitavanje bitamapa sa smanjenom veličinom
            val bitmap = BitmapFactory.decodeStream(newInputStream, null, options)
                ?: return@withContext Result.failure(Exception("Failed to decode bitmap"))
            newInputStream.close()

            // Kompresovanje bitmap-a
            val compressQuality = 80 // Kvalitet kompresije (0-100)
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, compressQuality, outputStream)

            // Čuvanje kompresovane slike u privremeni fajl u keš direktorijumu
            val tempFile = File(context.cacheDir, "compressed_${UUID.randomUUID()}.jpg")
            tempFile.outputStream().use { fileOutputStream ->
                fileOutputStream.write(outputStream.toByteArray())
            }

            Result.success(tempFile)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to compress image: ${e.message}"))
        }
    }
}