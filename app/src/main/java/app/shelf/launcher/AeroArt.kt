package app.shelf.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.random.Random

/**
 * Procedural "modern Frutiger Aero" backdrop: deep sky → aqua gradient, a soft sun bloom, light rays,
 * aurora swooshes and glossy bubbles. Drawn once in code (no bundled artwork) and cached on disk.
 */
class AeroPalette(
    val name: String,
    val base: IntArray,
    val sun: Int, val glowA: Int, val glowB: Int,
    val rib1From: Int, val rib1To: Int, val rib2From: Int, val rib2To: Int,
)

object AeroArt {
    const val VERSION = "aero_v2"
    val STOPS = floatArrayOf(0f, 0.45f, 0.75f, 1f)

    val AQUA = AeroPalette(
        "aqua", intArrayOf(0xFF06284F.toInt(), 0xFF0D5A92.toInt(), 0xFF1690A8.toInt(), 0xFF0B5552.toInt()),
        0x70CFF4FF, 0x6080E07A, 0x4834D3C8, 0x80A8F05A.toInt(), 0x5550E3E0, 0x6650E3E0, 0x40B8F7FF,
    )
    val DUSK = AeroPalette(
        "dusk", intArrayOf(0xFF140C34.toInt(), 0xFF43246E.toInt(), 0xFF9A4A7C.toInt(), 0xFF5A2440.toInt()),
        0x70FFD6A8, 0x60FF8FB0, 0x48FFB070, 0x80FFB38A.toInt(), 0x55FF7FB5, 0x66C99BFF, 0x40FFE0F0,
    )
    val NIGHT = AeroPalette(
        "night", intArrayOf(0xFF02050C.toInt(), 0xFF081A33.toInt(), 0xFF0E2C4D.toInt(), 0xFF040C18.toInt()),
        0x403A6FB0, 0x30307A9A, 0x302A5AA8, 0x5550D0E0, 0x337A6BFF, 0x403AA0FF, 0x26B8C8FF,
    )

    fun palette(style: String) = when (style) { STYLE_AERO_DUSK -> DUSK; STYLE_AERO_NIGHT -> NIGHT; else -> AQUA }

    fun render(pal: AeroPalette, w: Int = 1920, h: Int = 1080): Bitmap {
        // 1) Soft layer at quarter resolution, blurred, then upscaled (cheap, and naturally smooth).
        val sw = w / 4; val sh = h / 4
        val soft = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        Canvas(soft).apply {
            drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, sh.toFloat(), pal.base, STOPS, Shader.TileMode.CLAMP)
            })
            bloom(this, sw * 0.76f, sh * 0.02f, sw * 0.62f, pal.sun)
            bloom(this, sw * 0.18f, sh * 1.02f, sw * 0.62f, pal.glowA)  // "meadow" glow
            bloom(this, sw * 0.90f, sh * 1.05f, sw * 0.45f, pal.glowB)
            rays(this, sw, sh)
            ribbon(this, sw, sh, 0.80f, 0.55f, 1.00f, 0.60f, sh * 0.16f, pal.rib1From, pal.rib1To)
            ribbon(this, sw, sh, 0.92f, 0.70f, 0.82f, 0.74f, sh * 0.10f, pal.rib2From, pal.rib2To)
        }
        val softBlur = Blur.blur(soft, radius = 6)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(softBlur, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))

        // 2) Crisp details at full resolution.
        swooshLine(c, w, h, 0.80f, 0.55f, 1.00f, 0.60f, 0x55FFFFFF, 2.5f)
        swooshLine(c, w, h, 0.83f, 0.58f, 1.02f, 0.63f, 0x30FFFFFF, 1.5f)
        swooshLine(c, w, h, 0.92f, 0.70f, 0.82f, 0.74f, 0x40FFFFFF, 2f)
        bubbles(c, w, h)
        // Gentle bottom vignette keeps the lower UI readable.
        c.drawRect(0f, h * 0.7f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, h * 0.7f, 0f, h.toFloat(), 0x00000000, 0x40000814, Shader.TileMode.CLAMP)
        })
        return out
    }

    private fun bloom(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        c.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(x, y, r, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        })
    }

    private fun rays(c: Canvas, w: Int, h: Int) {
        val ox = w * 0.80f; val oy = -h * 0.15f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val angles = listOf(-0.15f to 0.05f, 0.12f to 0.06f, 0.35f to 0.04f, 0.6f to 0.05f)
        for ((a, spread) in angles) {
            val len = h * 1.4f
            val dx1 = -kotlin.math.sin(a - spread) * len; val dy1 = kotlin.math.cos(a - spread) * len
            val dx2 = -kotlin.math.sin(a + spread) * len; val dy2 = kotlin.math.cos(a + spread) * len
            p.shader = LinearGradient(ox, oy, ox + (dx1 + dx2) / 2, oy + (dy1 + dy2) / 2, 0x26FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawPath(Path().apply { moveTo(ox, oy); lineTo(ox + dx1, oy + dy1); lineTo(ox + dx2, oy + dy2); close() }, p)
        }
    }

    /** A cubic swoosh across the screen; start/end heights and control heights as fractions of h. */
    private fun curve(w: Int, h: Int, y0: Float, c1: Float, c2: Float, y1: Float) = Path().apply {
        moveTo(-w * 0.1f, h * y0)
        cubicTo(w * 0.30f, h * c1, w * 0.62f, h * c2, w * 1.1f, h * y1)
    }

    private fun ribbon(c: Canvas, w: Int, h: Int, y0: Float, c1: Float, c2: Float, y1: Float, width: Float, from: Int, to: Int) {
        c.drawPath(curve(w, h, y0, c1, c2, y1), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = width; strokeCap = Paint.Cap.ROUND
            shader = LinearGradient(0f, 0f, w.toFloat(), 0f, intArrayOf(from and 0x00FFFFFF, from, to, to and 0x00FFFFFF), floatArrayOf(0f, 0.3f, 0.75f, 1f), Shader.TileMode.CLAMP)
        })
    }

    private fun swooshLine(c: Canvas, w: Int, h: Int, y0: Float, c1: Float, c2: Float, y1: Float, color: Int, width: Float) {
        c.drawPath(curve(w, h, y0, c1, c2, y1), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = width
            shader = LinearGradient(0f, 0f, w.toFloat(), 0f, intArrayOf(color and 0x00FFFFFF, color, color, color and 0x00FFFFFF), floatArrayOf(0f, 0.25f, 0.8f, 1f), Shader.TileMode.CLAMP)
        })
    }

    private fun bubbles(c: Canvas, w: Int, h: Int) {
        val rnd = Random(7)
        repeat(18) {
            val r = (14 + rnd.nextFloat() * rnd.nextFloat() * 110).toFloat()
            // Keep them mostly to the sides and lower half, away from the clock/hero text.
            val side = rnd.nextBoolean()
            val x = if (side) w * (0.02f + rnd.nextFloat() * 0.22f) else w * (0.76f + rnd.nextFloat() * 0.22f)
            val y = h * (0.12f + rnd.nextFloat() * 0.8f)
            bubble(c, x, y, r)
        }
    }

    private fun bubble(c: Canvas, cx: Float, cy: Float, r: Float) {
        val aa = Paint.ANTI_ALIAS_FLAG
        c.drawCircle(cx, cy, r, Paint(aa).apply {
            shader = RadialGradient(cx + r * 0.2f, cy + r * 0.25f, r * 1.15f,
                intArrayOf(0x00FFFFFF, 0x14FFFFFF, 0x50E8FBFF), floatArrayOf(0f, 0.72f, 1f), Shader.TileMode.CLAMP)
        })
        c.drawCircle(cx, cy, r, Paint(aa).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = 0x55FFFFFF })
        // Specular highlight
        val hl = RectF(cx - r * 0.72f, cy - r * 0.78f, cx + r * 0.05f, cy - r * 0.28f)
        c.save(); c.rotate(-28f, hl.centerX(), hl.centerY())
        c.drawOval(hl, Paint(aa).apply {
            shader = LinearGradient(0f, hl.top, 0f, hl.bottom, 0xB0FFFFFF.toInt(), 0x10FFFFFF, Shader.TileMode.CLAMP)
        })
        c.restore()
        c.drawCircle(cx + r * 0.42f, cy + r * 0.48f, r * 0.08f, Paint(aa).apply { color = 0x50FFFFFF })
    }
}
