package app.shelf.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Renders every tile to a single bitmap at the size it is drawn (focused scale included), off the
 * main thread, and caches it. Compose then only has to blit one image per tile.
 */
class ImageLoader(private val ctx: Context) {
    private val cache = object : LruCache<String, ImageBitmap>(28 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val io = Dispatchers.IO.limitedParallelism(2)
    private val pm get() = ctx.packageManager
    private val labelFont by lazy { ResourcesCompat.getFont(ctx, R.font.inter_medium) }

    fun cached(key: String): ImageBitmap? = cache.get(key)
    fun trim() = cache.trimToSize(cache.maxSize() / 4)

    private suspend fun load(key: String, render: () -> Bitmap): ImageBitmap {
        cache.get(key)?.let { return it }
        return withContext(io) {
            cache.get(key) ?: render().asImageBitmap().also { cache.put(key, it) }
        }
    }

    // ---- keys (also used for synchronous cache lookups during composition) ----
    fun tileKey(item: GridItem, w: Int, h: Int) = "tile|${stamp(item)}|$w|$h"
    fun heroKey(item: GridItem, w: Int, h: Int) = "hero|${stamp(item)}|$w|$h"
    fun washKey(item: GridItem) = "wash|${stamp(item)}"
    private fun stamp(item: GridItem) = if (item is GridItem.App) item.key + "@" + item.app.stamp else item.key

    suspend fun tile(item: GridItem, w: Int, h: Int) = load(tileKey(item, w, h)) { render(item, w, h) }
    suspend fun hero(item: GridItem, w: Int, h: Int) = load(heroKey(item, w, h)) { render(item, w, h) }

    /** A tiny, heavily blurred version of the tile: used as the Top Shelf color wash. */
    suspend fun wash(item: GridItem) = load(washKey(item)) {
        val small = render(item, 64, 36)
        Blur.blur(small, radius = 5).also { fadeAlpha(it, 0.45f, 1f, dim = 0.5f) }
    }

    // ---- rendering ----
    private fun render(item: GridItem, w: Int, h: Int): Bitmap = when (item) {
        is GridItem.App -> renderApp(item.app, w, h)
        is GridItem.Input -> renderSymbol(item.label, if (item.input.passthrough) Symbol.INPUT else Symbol.ANTENNA, w, h, 0xFF24344F.toInt())
        is GridItem.System -> when (item.kind) {
            SystemKind.HIDDEN_APPS -> renderSymbol(item.label, Symbol.HIDDEN, w, h, 0xFF2B2F3A.toInt())
            SystemKind.ANDROID_SETTINGS -> renderSymbol(item.label, Symbol.SLIDERS, w, h, 0xFF2B2F3A.toInt())
            SystemKind.SHELF_SETTINGS -> renderSymbol(item.label, Symbol.GRID, w, h, 0xFF2B2F3A.toInt())
        }
    }

    companion object {
        /** Bakes a vertical fade-out (alpha 1 → 0 between [from] and [to], as fractions of height) into [b]. */
        fun fadeAlpha(b: Bitmap, from: Float, to: Float, dim: Float = 1f) {
            val w = b.width; val h = b.height
            val row = IntArray(w)
            for (y in 0 until h) {
                val f = y / (h - 1f)
                val k = when { f <= from -> 1f; f >= to -> 0f; else -> 1f - (f - from) / (to - from) }
                if (k >= 1f && dim >= 1f) continue
                b.getPixels(row, 0, w, 0, y, w, 1)
                for (x in 0 until w) {
                    val p = row[x]
                    val a = ((p ushr 24) * k).toInt()
                    val r = (((p shr 16) and 0xff) * dim).toInt()
                    val g = (((p shr 8) and 0xff) * dim).toInt()
                    val bl = ((p and 0xff) * dim).toInt()
                    row[x] = (a shl 24) or (r shl 16) or (g shl 8) or bl
                }
                b.setPixels(row, 0, w, 0, y, w, 1)
            }
        }
    }

    private fun activityInfo(app: AppEntry): ActivityInfo? =
        runCatching { pm.getActivityInfo(ComponentName(app.packageName, app.className), 0) }.getOrNull()

    private fun banner(app: AppEntry): Drawable? {
        val ai = activityInfo(app)
        // ActivityInfo.loadBanner falls back to the application banner itself; ask explicitly too.
        return runCatching { ai?.loadBanner(pm) }.getOrNull()
            ?: runCatching { pm.getApplicationBanner(app.packageName) }.getOrNull()
    }

    private fun renderApp(app: AppEntry, w: Int, h: Int): Bitmap {
        banner(app)?.let { return drawCropped(it, w, h) }
        val icon = runCatching { activityInfo(app)?.loadIcon(pm) }.getOrNull()
            ?: runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull()
            ?: pm.defaultActivityIcon
        return renderIconTile(icon, app.label, w, h)
    }

    /** Draw a drawable filling w×h, center-cropped, with bitmap filtering. */
    private fun drawCropped(d: Drawable, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val iw = d.intrinsicWidth.takeIf { it > 0 } ?: w
        val ih = d.intrinsicHeight.takeIf { it > 0 } ?: h
        val scale = max(w / iw.toFloat(), h / ih.toFloat())
        val dw = (iw * scale).toInt()
        val dh = (ih * scale).toInt()
        val left = (w - dw) / 2
        val top = (h - dh) / 2
        val bmp = (d as? BitmapDrawable)?.bitmap
        if (bmp != null) {
            c.drawBitmap(bmp, null, Rect(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        } else {
            d.setBounds(left, top, left + dw, top + dh)
            d.draw(c)
        }
        return out
    }

    private fun drawableToBitmap(d: Drawable, size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        return b
    }

    /** Fallback tile: icon centered on a color sampled from the icon, label underneath. */
    private fun renderIconTile(icon: Drawable, label: String, w: Int, h: Int): Bitmap {
        val iconSize = (h * 0.42f).toInt()
        val iconBmp = drawableToBitmap(icon, iconSize)
        val palette = Palette.from(iconBmp).maximumColorCount(16).generate()
        val sampled = (palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch)?.rgb ?: 0xFF3A4152.toInt()
        val hsl = FloatArray(3).also { ColorUtils.colorToHSL(sampled, it) }
        hsl[1] = hsl[1].coerceAtMost(0.65f)
        hsl[2] = hsl[2].coerceIn(0.18f, 0.32f) // keep it dark enough for white text
        val top = ColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1], hsl[2] + 0.06f))
        val bottom = ColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1], hsl[2] - 0.05f))

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        })
        val ix = (w - iconSize) / 2f
        val iy = h * 0.38f - iconSize / 2f
        c.drawBitmap(iconBmp, ix, iy, Paint(Paint.FILTER_BITMAP_FLAG))
        drawLabel(c, label, w, h)
        return out
    }

    private fun drawLabel(c: Canvas, label: String, w: Int, h: Int) {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 255, 255, 255)
            textSize = h * 0.135f
            typeface = labelFont
            textAlign = Paint.Align.CENTER
        }
        val text = TextUtils.ellipsize(label, p, w * 0.86f, TextUtils.TruncateAt.END).toString()
        c.drawText(text, w / 2f, h * 0.86f, p)
    }

    private enum class Symbol { INPUT, ANTENNA, HIDDEN, SLIDERS, GRID }

    /** Tiles for inputs and system entries: a simple line icon drawn in code (no icon assets needed). */
    private fun renderSymbol(label: String, symbol: Symbol, w: Int, h: Int, base: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val top = ColorUtils.blendARGB(base, Color.WHITE, 0.06f)
        val bottom = ColorUtils.blendARGB(base, Color.BLACK, 0.18f)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        })
        val s = h * 0.34f            // icon box size
        val cx = w / 2f
        val cy = h * 0.38f
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = max(2f, h * 0.022f); color = Color.WHITE
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val l = cx - s / 2; val t = cy - s / 2; val r = cx + s / 2; val b = cy + s / 2
        when (symbol) {
            Symbol.INPUT -> {
                // HDMI-style connector outline
                val p = Path().apply {
                    moveTo(l, t + s * 0.22f); lineTo(r, t + s * 0.22f); lineTo(r, t + s * 0.52f)
                    lineTo(r - s * 0.16f, t + s * 0.78f); lineTo(l + s * 0.16f, t + s * 0.78f)
                    lineTo(l, t + s * 0.52f); close()
                }
                c.drawPath(p, stroke)
                c.drawLine(l + s * 0.22f, t + s * 0.45f, r - s * 0.22f, t + s * 0.45f, stroke)
            }
            Symbol.ANTENNA -> {
                c.drawRoundRect(RectF(l, t + s * 0.3f, r, b), s * 0.08f, s * 0.08f, stroke)
                c.drawLine(cx, t + s * 0.3f, l + s * 0.2f, t, stroke)
                c.drawLine(cx, t + s * 0.3f, r - s * 0.2f, t, stroke)
            }
            Symbol.HIDDEN -> {
                val eye = RectF(l, cy - s * 0.3f, r, cy + s * 0.3f)
                c.drawArc(eye, 200f, 140f, false, stroke)
                c.drawArc(eye, 20f, 140f, false, stroke)
                c.drawCircle(cx, cy, s * 0.12f, fill)
                c.drawLine(l + s * 0.1f, b - s * 0.1f, r - s * 0.1f, t + s * 0.1f, stroke)
            }
            Symbol.SLIDERS -> {
                for ((i, k) in listOf(0.3f, 0.7f, 0.45f).withIndex()) {
                    val y = t + s * (0.2f + i * 0.3f)
                    c.drawLine(l, y, r, y, stroke)
                    c.drawCircle(l + s * k, y, s * 0.09f, fill)
                }
            }
            Symbol.GRID -> {
                val g = s * 0.12f
                val q = (s - g) / 2
                val rr = s * 0.08f
                for (i in 0..1) for (j in 0..1) {
                    val x0 = l + i * (q + g); val y0 = t + j * (q + g)
                    c.drawRoundRect(RectF(x0, y0, x0 + q, y0 + q), rr, rr, stroke)
                }
            }
        }
        drawLabel(c, label, w, h)
        return out
    }
}
