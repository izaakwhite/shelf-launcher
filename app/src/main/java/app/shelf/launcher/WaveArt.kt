package app.shelf.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.sin

/**
 * A flowing ribbon of thin light lines over a soft translucent surface — drawn once into a wide
 * bitmap whose pattern repeats every [period] px, so the UI animates it by sliding (no per-frame paths).
 */
object WaveArt {
    fun render(w: Int, h: Int, period: Int, amp: Float, lines: Int, phase: Float, surface: Boolean): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val twoPi = (2 * PI).toFloat()
        fun y(x: Float, k: Float): Float {
            val base = h * 0.5f
            val a = amp * (1f - 0.04f * k)
            return base +
                a * sin(twoPi * x / period + phase + k * 0.09f) +
                a * 0.32f * sin(twoPi * x / (period / 2f) + phase * 1.7f + k * 0.21f) +
                k * 2.4f
        }
        val step = 12f
        if (surface) {
            val top = Path(); val bottom = ArrayList<Pair<Float, Float>>()
            var x = 0f
            top.moveTo(0f, y(0f, 0f))
            while (x <= w) { top.lineTo(x, y(x, 0f)); bottom += x to y(x, lines.toFloat()) + 30f; x += step }
            for (i in bottom.indices.reversed()) top.lineTo(bottom[i].first, bottom[i].second)
            top.close()
            c.drawPath(top, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, h * 0.25f, 0f, h * 0.85f, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            })
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f }
        for (k in 0 until lines) {
            val mid = 1f - kotlin.math.abs(k - lines / 2f) / (lines / 2f)   // brighter in the middle of the bundle
            p.color = (((0x10 + 0x34 * mid).toInt()) shl 24) or 0xFFFFFF
            val path = Path()
            var x = 0f
            path.moveTo(0f, y(0f, k.toFloat()))
            while (x <= w) { path.lineTo(x, y(x, k.toFloat())); x += step }
            c.drawPath(path, p)
        }
        return out
    }
}
