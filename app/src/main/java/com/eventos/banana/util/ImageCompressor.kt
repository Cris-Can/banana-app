package com.eventos.banana.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

object ImageCompressor {
    /**
     * Comprime una imagen desde una URI de Android a un ByteArray de JPEG.
     * Redimensiona la imagen si excede los límites especificados.
     */
    fun compressFromUri(
        context: Context, 
        uri: Uri, 
        maxWidth: Int = 1024, 
        maxHeight: Int = 1024, 
        quality: Int = 80
    ): ByteArray? {
        return try {
            val contentResolver = context.contentResolver
            
            // 1. Obtener dimensiones originales
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use { 
                BitmapFactory.decodeStream(it, null, options)
            }

            // 2. Calcular inSampleSize para redimensionar
            var inSampleSize = 1
            if (options.outHeight > maxHeight || options.outWidth > maxWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= maxHeight && halfWidth / inSampleSize >= maxWidth) {
                    inSampleSize *= 2
                }
            }

            // 3. Decodificar con el sample size
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            // 4. Comprimir a JPEG
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val result = outputStream.toByteArray()
            
            bitmap.recycle() // Liberar memoria
            
            android.util.Log.d("ImageCompressor", "Original: ${options.outWidth}x${options.outHeight}, Compressed: ${result.size / 1024} KB")
            
            result
        } catch (e: Exception) {
            android.util.Log.e("ImageCompressor", "Error compressing image from $uri", e)
            null
        }
    }
}
