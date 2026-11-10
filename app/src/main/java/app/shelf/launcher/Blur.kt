package app.shelf.launcher

import android.graphics.Bitmap

/** Three box-blur passes ≈ Gaussian. Only ever run on small bitmaps (≤ ~240 px wide). */
object Blur {
    fun blur(src: Bitmap, radius: Int, passes: Int = 3): Bitmap {
        val w = src.width
        val h = src.height
        val a = IntArray(w * h)
        src.getPixels(a, 0, w, 0, 0, w, h)
        val b = IntArray(w * h)
        repeat(passes) {
            pass(a, b, w, h, radius, horizontal = true)
            pass(b, a, w, h, radius, horizontal = false)
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(a, 0, w, 0, 0, w, h) }
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = 2 * r + 1
        for (line in 0 until lines) {
            fun idx(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + c else c * w + line
            }
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val p = src[idx(i)]
                sa += p ushr 24; sr += (p shr 16) and 0xff; sg += (p shr 8) and 0xff; sb += p and 0xff
            }
            for (i in 0 until len) {
                dst[idx(i)] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val pOut = src[idx(i - r)]
                val pIn = src[idx(i + r + 1)]
                sa += (pIn ushr 24) - (pOut ushr 24)
                sr += ((pIn shr 16) and 0xff) - ((pOut shr 16) and 0xff)
                sg += ((pIn shr 8) and 0xff) - ((pOut shr 8) and 0xff)
                sb += (pIn and 0xff) - (pOut and 0xff)
            }
        }
    }
}
