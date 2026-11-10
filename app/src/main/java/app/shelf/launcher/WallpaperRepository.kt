package app.shelf.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

class Wallpaper(val sharp: ImageBitmap, val blurred: ImageBitmap)

/**
 * Optional user wallpaper: /sdcard/Android/data/app.shelf.launcher/files/wallpaper.(jpg|png|webp).
 * The blurred copy is computed once on a downscaled image and cached on disk until the file changes.
 */
class WallpaperRepository(private val ctx: Context) {
    private val _state = MutableStateFlow<Wallpaper?>(null)
    val state: StateFlow<Wallpaper?> = _state
    private var stamp: String? = null

    fun dir(): File? = ctx.getExternalFilesDir(null)

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val file = dir()?.listFiles()?.firstOrNull { it.isFile && it.name.lowercase().matches(Regex("wallpaper\\.(jpe?g|png|webp)")) }
        val newStamp = file?.let { "${it.length()}_${it.lastModified()}" }
        if (newStamp == stamp) return@withContext
        stamp = newStamp
        if (file == null) { _state.value = null; return@withContext }
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1920 && bounds.outHeight / (sample * 2) >= 1080) sample *= 2
            val sharp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
                inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565
            }) ?: return@runCatching null

            val cacheFile = File(ctx.cacheDir, "wallpaper_blur_$newStamp.png")
            val blurred = BitmapFactory.decodeFile(cacheFile.path) ?: run {
                ctx.cacheDir.listFiles()?.filter { it.name.startsWith("wallpaper_blur_") }?.forEach { it.delete() }
                val small = Bitmap.createScaledBitmap(sharp, 192, (192f * sharp.height / sharp.width).toInt().coerceAtLeast(1), true)
                Blur.blur(small.copy(Bitmap.Config.ARGB_8888, false), radius = 6).also { b ->
                    ImageLoader.fadeAlpha(b, 0.40f, 0.62f)
                    cacheFile.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            Wallpaper(sharp.asImageBitmap(), blurred.asImageBitmap())
        }.getOrNull().let { _state.value = it }
    }
}
