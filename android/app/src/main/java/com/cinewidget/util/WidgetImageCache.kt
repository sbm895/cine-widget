package com.cinewidget.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Scale
import com.cinewidget.data.model.UnifiedScheduleResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object WidgetImageCache {

    private val inMemoryCache = ConcurrentHashMap<String, Bitmap>()

    private fun hashUrl(url: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "widget_posters")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Lectura instantánea (0 ms): Solo busca en RAM o archivo local. Cero llamadas HTTP.
     */
    fun getCachedBitmap(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        
        inMemoryCache[url]?.let { return it }

        val file = File(getCacheDir(context), "${hashUrl(url)}.webp")
        if (file.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    inMemoryCache[url] = bitmap
                    return bitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    /**
     * Pre-descarga en segundo plano durante el Worker: Guarda en disco a tamaño thumbnail (50x75px)
     */
    suspend fun prefetchPosters(context: Context, schedule: UnifiedScheduleResponse?) {
        if (schedule == null) return

        withContext(Dispatchers.IO) {
            try {
                val okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        val original = chain.request()
                        val reqBuilder = original.newBuilder()
                        if (original.url.host.contains("moviexchange.com")) {
                            reqBuilder.header("Referer", "https://www.cinecolombia.com/")
                        }
                        chain.proceed(reqBuilder.build())
                    }
                    .build()

                val imageLoader = ImageLoader.Builder(context)
                    .okHttpClient(okHttpClient)
                    .build()

                val cacheDir = getCacheDir(context)
                val urls = schedule.cinemas.flatMap { it.movies }.mapNotNull { it.coverImage }.distinct()

                for (url in urls) {
                    val file = File(cacheDir, "${hashUrl(url)}.webp")
                    if (!file.exists()) {
                        try {
                            val request = ImageRequest.Builder(context)
                                .data(url)
                                .size(50, 75)
                                .scale(Scale.FIT)
                                .allowHardware(false)
                                .build()
                            val drawable = imageLoader.execute(request).drawable
                            drawable?.toBitmap(50, 75)?.let { bitmap ->
                                inMemoryCache[url] = bitmap
                                FileOutputStream(file).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.WEBP, 80, out)
                                }
                            }
                        } catch (e: Exception) {
                            // Ignorar errores individuales para no trabar el resto
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
