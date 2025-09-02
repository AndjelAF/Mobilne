package com.example.mapmyst.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.widget.ImageView
import coil.load
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageUtils {

    const val MAX_IMAGE_SIZE = 1024 // Max width/height
    const val COMPRESSION_QUALITY = 80 // JPEG quality

    /**
     * Učitava sliku u ImageView sa podrškom za lokalne fajlove
     */
    fun loadImageIntoView(imageView: ImageView, imageUrl: String?) {
        if (imageUrl.isNullOrBlank()) {
            // Postaviti default sliku
            imageView.setImageResource(android.R.drawable.ic_menu_camera)
            return
        }

        when {
            imageUrl.startsWith("file://") -> {
                // Lokalni fajl
                val file = File(imageUrl.removePrefix("file://"))
                if (file.exists()) {
                    imageView.load(file) {
                        crossfade(true)
                        placeholder(android.R.drawable.ic_menu_gallery)
                        error(android.R.drawable.ic_menu_camera)
                    }
                } else {
                    imageView.setImageResource(android.R.drawable.ic_menu_camera)
                }
            }
            imageUrl.startsWith("http") -> {
                // Firebase/online URL
                imageView.load(imageUrl) {
                    crossfade(true)
                    placeholder(android.R.drawable.ic_menu_gallery)
                    error(android.R.drawable.ic_menu_camera)
                }
            }
            else -> {
                imageView.setImageResource(android.R.drawable.ic_menu_camera)
            }
        }
    }

    /**
     * Kompresuje i rotira sliku na osnovu EXIF podataka
     */
    fun compressAndRotateImage(
        context: Context,
        uri: Uri,
        maxSize: Int = MAX_IMAGE_SIZE,
        quality: Int = COMPRESSION_QUALITY
    ): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            // Prvo čitamo dimenzije slike
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Računamo sample size za smanjenje memorije
            val sampleSize = calculateInSampleSize(options, maxSize, maxSize)

            // Učitavamo sliku sa sample size
            val newInputStream = context.contentResolver.openInputStream(uri) ?: return null
            val finalOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inJustDecodeBounds = false
            }

            var bitmap = BitmapFactory.decodeStream(newInputStream, null, finalOptions)
            newInputStream.close()

            bitmap ?: return null

            // Rotiramo sliku na osnovu EXIF podataka
            bitmap = rotateImageIfRequired(context, bitmap, uri)

            // Dodatno smanjujemo ako je potrebno
            bitmap = scaleBitmapIfNeeded(bitmap, maxSize)

            // Čuvamo u temp file
            val tempFile = File(context.cacheDir, "compressed_image_${UUID.randomUUID()}.jpg")
            val outputStream = FileOutputStream(tempFile)

            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            outputStream.close()

            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun rotateImageIfRequired(context: Context, img: Bitmap, selectedImage: Uri): Bitmap {
        return try {
            val input = context.contentResolver.openInputStream(selectedImage)
            val ei = input?.let { ExifInterface(it) }
            input?.close()

            val orientation = ei?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) ?: ExifInterface.ORIENTATION_NORMAL

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270f)
                else -> img
            }
        } catch (e: Exception) {
            img
        }
    }

    private fun rotateImage(img: Bitmap, degree: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree)
        val rotatedImg = Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
        img.recycle()
        return rotatedImg
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }

        val ratio = if (width > height) {
            maxSize.toFloat() / width
        } else {
            maxSize.toFloat() / height
        }

        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        if (scaledBitmap != bitmap) {
            bitmap.recycle()
        }

        return scaledBitmap
    }

    /**
     * Kreira prazan placeholder fajl za kameru
     */
    fun createImageFile(context: Context): File {
        return File(context.cacheDir, "camera_image_${UUID.randomUUID()}.jpg")
    }

    /**
     * Proverava da li je URL lokalni fajl
     */
    fun isLocalFile(url: String?): Boolean {
        return url?.startsWith("file://") == true
    }

    /**
     * Proverava da li lokalni fajl postoji
     */
    fun localFileExists(url: String): Boolean {
        return if (isLocalFile(url)) {
            val file = File(url.removePrefix("file://"))
            file.exists() && file.isFile()
        } else {
            false
        }
    }
}