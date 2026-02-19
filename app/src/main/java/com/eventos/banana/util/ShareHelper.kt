package com.eventos.banana.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.eventos.banana.domain.model.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShareHelper(private val context: Context) {

    fun shareEvent(event: Event) {
        CoroutineScope(Dispatchers.IO).launch {
            val shareText = buildShareText(event)
            val imageUri = if (!event.imageUrl.isNullOrBlank()) {
                downloadImageToCache(event.imageUrl)
            } else {
                null
            }

            withContext(Dispatchers.Main) {
                launchShareIntent(shareText, imageUri)
            }
        }
    }

    private fun buildShareText(event: Event): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date(event.startAt))
        
        // 🔗 Deep link — abre directo el evento si ya tienen la app
        val deepLink = "https://bananaapp-aa46e.web.app/event/${event.id}"
        // 🏪 Play Store link — para quienes no tienen la app
        val playStoreLink = "https://play.google.com/store/apps/details?id=com.eventos.banana"

        return """
            🍌 *${event.title}*
            
            📅 $dateStr
            📍 ${event.commune}, ${event.region}
            
            ${event.description}
            
            📲 ¿Ya tienes Banana? Ábrelo aquí: $deepLink
            
            📱 ¿No la tienes? Descárgala gratis: $playStoreLink
            
            📸 @getbananaapp
        """.trimIndent()
    }

    private suspend fun downloadImageToCache(imageUrl: String): Uri? {
        return try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false) // Must be software bitmap to save
                .build()

            val result = (loader.execute(request) as? SuccessResult)?.drawable
            val originalBitmap = (result as? BitmapDrawable)?.bitmap ?: return null

            // 🎨 Watermark removed via user request ("se ve feo")

            val imagesFolder = File(context.cacheDir, "images")
            if (!imagesFolder.exists()) imagesFolder.mkdirs()

            val file = File(imagesFolder, "share_image_${System.currentTimeMillis()}.png")
            val stream = FileOutputStream(file)
            originalBitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
            stream.flush()
            stream.close()

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun addWatermark(original: Bitmap): Bitmap {
        val width = original.width
        val height = original.height
        
        // Create mutable bitmap
        val config = original.config ?: Bitmap.Config.ARGB_8888
        val result = original.copy(config, true)
        val canvas = android.graphics.Canvas(result)
        
        // Load Logo using ContextCompat (Handles Adaptive Icons/Vectors correctly)
        // Using mipmap.ic_launcher (default) to be safe or round if available
        val logoDrawable = androidx.core.content.ContextCompat.getDrawable(
            context, 
            com.eventos.banana.R.mipmap.ic_launcher_round
        ) ?: androidx.core.content.ContextCompat.getDrawable(
            context,
            com.eventos.banana.R.mipmap.ic_launcher
        )
        
        if (logoDrawable != null) {
            val logoBitmap = drawableToBitmap(logoDrawable)
            
            // Resize logo to be ~15% of the image width
            val logoSize = (width * 0.15f).toInt().coerceAtLeast(100) 
            val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, logoSize, logoSize, true)
            
            // Position: Bottom Right with padding
            val padding = (width * 0.05f).toInt()
            val left = width - scaledLogo.width - padding
            val top = height - scaledLogo.height - padding
            
            // Draw Logo
            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(scaledLogo, left.toFloat(), top.toFloat(), paint)
        }
        
        return result
    }

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            if (drawable.bitmap != null) return drawable.bitmap
        }
        
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 100
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 100
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun launchShareIntent(text: String, imageUri: Uri?) {
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            if (imageUri != null) {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
            putExtra(Intent.EXTRA_TEXT, text)
        }
        
        val chooser = Intent.createChooser(intent, "Compartir evento en...")
        // Important: flag for non-activity context if needed, though usually called from UI
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) 
        context.startActivity(chooser)
    }
}
