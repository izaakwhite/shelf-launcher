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

/** The screen background plus a small pre-blurred copy that glass panels sample from. Both 16:9. */
class Backdrop(val sharp: ImageBitmap, val blurred: ImageBitmap, val user: Boolean)

/**
 * Background source: the user's wallpaper if present
 * (/sdcard/Android/data/app.shelf.launcher/files/wallpaper.(jpg|png|webp)), otherwise the generated
 * Aero art. Blurring happens once, on a 480×270 copy, and is cached until the source changes.
 */
class BackdropRepository(private val ctx: Context) {
    private val _state = MutableStateFlow<Backdrop?>(null)
    val state: StateFlow<Backdrop?> = _state
    private var stamp: String? = null
    @Volatile var palette: AeroPalette = AeroArt.AQUA

    fun dir(): File? = ctx.getExternalFilesDir(null)

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val file = dir()?.listFiles()?.firstOrNull { it.isFile && it.name.lowercase().matches(Regex("wallpaper\\.(jpe?g|png|webp)")) }
        val newStamp = file?.let { "user_${it.length()}_${it.lastModified()}" } ?: (AeroArt.VERSION + "_" + palette.name)
        if (newStamp == stamp) return@withContext
        runCatching {
            val sharp = if (file != null) decodeUser(file) else aero()
            val blurFile = File(ctx.cacheDir, "blur_$newStamp.png")
            val blurred = BitmapFactory.decodeFile(blurFile.path) ?: run {
                ctx.cacheDir.listFiles()?.filter { it.name.startsWith("blur_") }?.forEach { it.delete() }
                val small = Bitmap.createScaledBitmap(sharp, 480, 270, true)
                Blur.blur(small, radius = 9).also { b -> blurFile.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            }
            _state.value = Backdrop(sharp.asImageBitmap(), blurred.asImageBitmap(), user = file != null)
            stamp = newStamp
        }
    }

    private fun aero(): Bitmap {
        val pal = palette
        val f = File(ctx.cacheDir, AeroArt.VERSION + "_" + pal.name + ".jpg")
        BitmapFactory.decodeFile(f.path)?.let { return it }
        return AeroArt.render(pal).also { b -> f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 94, it) } }
    }

    /** Decode near screen size and center-crop to 16:9 so glass sampling lines up with the screen. */
    private fun decodeUser(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1920 && bounds.outHeight / (sample * 2) >= 1080) sample *= 2
        val src = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("undecodable wallpaper")
        val targetRatio = 16f / 9f
        val (cw, ch) = if (src.width / src.height.toFloat() > targetRatio) {
            (src.height * targetRatio).toInt() to src.height
        } else src.width to (src.width / targetRatio).toInt()
        val cropped = Bitmap.createBitmap(src, (src.width - cw) / 2, (src.height - ch) / 2, cw, ch)
        return if (cw > 1920) Bitmap.createScaledBitmap(cropped, 1920, 1080, true) else cropped
    }
}
