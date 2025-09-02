package com.example.mapmyst.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.mapmyst.domain.repository.StorageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PRIVREMENA implementacija koja čuva slike lokalno umesto na Firebase Storage
 * Koristiti dok se ne aktivira Firebase Storage billing
 */
@Singleton
class LocalStorageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : StorageRepository {

    private val TAG = "LocalStorageRepo"

    // Direktorijum za profile slike
    private val profileImagesDir = File(context.filesDir, "profile_images").apply {
        if (!exists()) mkdirs()
    }

    // Direktorijum za cache slike
    private val cacheImagesDir = File(context.filesDir, "cache_images").apply {
        if (!exists()) mkdirs()
    }

    override suspend fun uploadProfileImage(userId: String, imageUri: Uri): Result<String> {
        return try {
            Log.d(TAG, "🔄 Starting LOCAL profile image upload for user: $userId")
            Log.d(TAG, "📁 Image URI: $imageUri")

            // Kompresujemo sliku
            val compressedFile = compressImage(context, imageUri).getOrThrow()

            // Kreiramo finalni fajl u profile_images direktorijumu
            val finalFile = File(profileImagesDir, "$userId.jpg")

            // Kopiramo kompresovanu sliku u finalni fajl
            compressedFile.copyTo(finalFile, overwrite = true)

            // Brišemo temp fajl
            compressedFile.delete()

            Log.d(TAG, "✅ Profile image saved locally: ${finalFile.absolutePath}")

            // Vraćamo lokalni URI kao "download URL"
            val localUri = "file://${finalFile.absolutePath}"
            Result.success(localUri)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save profile image locally: ${e.message}")
            Result.failure(Exception("Failed to save profile image locally: ${e.message}"))
        }
    }

    override suspend fun uploadCacheImage(cacheId: String, imageUri: Uri): Result<String> {
        return try {
            Log.d(TAG, "🔄 Starting LOCAL cache image upload for cache: $cacheId")

            // Kompresujemo sliku
            val compressedFile = compressImage(context, imageUri).getOrThrow()

            // Kreiramo jedinstveno ime za cache sliku
            val imageName = "${cacheId}_${UUID.randomUUID()}.jpg"
            val finalFile = File(cacheImagesDir, imageName)

            // Kopiramo kompresovanu sliku
            compressedFile.copyTo(finalFile, overwrite = true)
            compressedFile.delete()

            Log.d(TAG, "✅ Cache image saved locally: ${finalFile.absolutePath}")

            val localUri = "file://${finalFile.absolutePath}"
            Result.success(localUri)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save cache image locally: ${e.message}")
            Result.failure(Exception("Failed to save cache image locally: ${e.message}"))
        }
    }

    override suspend fun getImageUrl(path: String): Result<String> {
        return try {
            // Za lokalno storage, path je već finalni URI
            if (path.startsWith("file://")) {
                val file = File(path.removePrefix("file://"))
                if (file.exists()) {
                    Result.success(path)
                } else {
                    Result.failure(Exception("Local file does not exist: $path"))
                }
            } else {
                Result.failure(Exception("Invalid local path: $path"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to get local image URL: ${e.message}"))
        }
    }

    override suspend fun deleteImage(path: String): Result<Unit> {
        return try {
            if (path.startsWith("file://")) {
                val file = File(path.removePrefix("file://"))
                if (file.exists() && file.delete()) {
                    Log.d(TAG, "✅ Local image deleted: $path")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to delete local file or file doesn't exist"))
                }
            } else {
                Result.failure(Exception("Invalid local path for deletion: $path"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to delete local image: ${e.message}"))
        }
    }

    override suspend fun compressImage(context: Context, imageUri: Uri): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Starting image compression...")

            // Otvaramo input stream za čitanje originalne slike
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return@withContext Result.failure(Exception("Cannot open input stream"))

            // Prvo čitamo dimenzije bez učitavanja u memoriju
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            val imageWidth = options.outWidth
            val imageHeight = options.outHeight
            Log.d(TAG, "📏 Original dimensions: ${imageWidth}x${imageHeight}")

            // Ponovo otvaramo stream za pravo učitavanje
            val newInputStream = context.contentResolver.openInputStream(imageUri)
                ?: return@withContext Result.failure(Exception("Cannot open input stream"))

            // Računamo scale factor za optimizaciju memorije
            val maxDimension = 1024
            val scaleFactor = maxOf(1, minOf(imageWidth / maxDimension, imageHeight / maxDimension))

            // Učitavamo sliku sa smanjenom rezolucijom
            options.apply {
                inJustDecodeBounds = false
                inSampleSize = scaleFactor
            }

            val bitmap = BitmapFactory.decodeStream(newInputStream, null, options)
                ?: return@withContext Result.failure(Exception("Failed to decode bitmap"))
            newInputStream.close()

            Log.d(TAG, "📏 Scaled dimensions: ${bitmap.width}x${bitmap.height}")

            // Kompresujemo u JPEG sa 80% kvalitetom
            val compressQuality = 80
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, compressQuality, outputStream)

            // Čuvamo u cache direktorijum
            val tempFile = File(context.cacheDir, "compressed_image_${UUID.randomUUID()}.jpg")
            FileOutputStream(tempFile).use { fileOutputStream ->
                fileOutputStream.write(outputStream.toByteArray())
            }

            // Cleanup
            bitmap.recycle()
            outputStream.close()

            Log.d(TAG, "✅ Image compressed: ${tempFile.length()} bytes")
            Result.success(tempFile)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Image compression failed: ${e.message}")
            Result.failure(Exception("Failed to compress image: ${e.message}"))
        }
    }

    /**
     * HELPER METODA - čisti cache fajlove starije od X dana
     */
    fun cleanOldCacheImages(daysOld: Int = 7) {
        try {
            val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)

            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("compressed_image_") && file.lastModified() < cutoffTime) {
                    file.delete()
                    Log.d(TAG, "🗑️ Deleted old cache file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to clean cache: ${e.message}")
        }
    }
}