package app.shelf.launcher

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

/** Layout math at the TV's real size (1920×1080 px at density 2 = 960×540 dp). */
class MetricsTest {
    private val w = 960.dp; private val h = 540.dp
    private val safeX = w * 0.05f; private val safeY = h * 0.05f    // 5 % overscan margins

    @Test fun `grid tiles and gaps fit between the margins for 5 and 6 columns`() {
        for (cols in listOf(5, 6)) {
            val m = Metrics(w, h, cols)
            val used = m.tileW * cols + m.gap * (cols - 1) + m.marginH * 2
            assertTrue("cols=$cols used=$used", used <= w + 0.5.dp)
            assertTrue("16:9 tiles", (m.tileH / m.tileW - 9f / 16f) < 0.001f)
        }
    }

    @Test fun `a focused grid tile at the edge stays inside the overscan-safe area`() {
        for (cols in listOf(5, 6)) {
            val m = Metrics(w, h, cols)
            val grow = m.tileW * (FOCUS_SCALE - 1f) / 2f
            assertTrue("cols=$cols", m.marginH - grow >= safeX - 8.dp)   // shadow may poke out a few dp
        }
    }

    @Test fun `carousel rows stack top to bottom inside the safe area without overlapping`() {
        for (cols in listOf(5, 6)) {
            val m = CarouselMetrics(w, h, cols)
            assertTrue(m.safeTop >= safeY)
            assertTrue("hero below clock", m.heroTop >= m.safeTop + 44.dp)
            assertTrue("track below hero", m.trackTop >= m.heroTop + m.heroH)
            assertTrue("pills below track", m.pillsTop >= m.trackTop + m.trackH)
            assertTrue("pills inside safe area", m.pillsTop + 40.dp <= h - safeY)
            assertTrue("focused icon fits the track", m.icon * m.focusScale + 40.dp <= m.trackH)
            assertTrue("hero fits", m.heroW <= w - m.marginH * 2)
        }
    }
}
