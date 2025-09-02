package com.example.mapmyst.domain.repository

import android.content.Context
import android.net.Uri
import java.io.File

interface StorageRepository {
    suspend fun uploadProfileImage(userId: String, imageUri: Uri): Result<String>
    suspend fun uploadCacheImage(cacheId: String, imageUri: Uri): Result<String>
    suspend fun getImageUrl(path: String): Result<String>
    suspend fun deleteImage(path: String): Result<Unit>
    suspend fun compressImage(context: Context, imageUri: Uri): Result<File>
}