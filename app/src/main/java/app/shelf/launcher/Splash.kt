package app.shelf.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Timeline in milliseconds.
private const val LINE_END = 1400L       // the line sweeps across and fades in/out
private const val LETTERS_START = 1100L  // letters start appearing as the line fades
private const val LETTER_STAGGER = 110L
private const val LETTER_FADE = 600L
private const val GLINT_START = 1700L    // shimmer sweeps start, and repeat until the end
private const val GLINT_PERIOD = 1500L
private const val FADE_OUT_START = 4200L
private const val FADE_OUT = 800L
private const val TOTAL = FADE_OUT_START + FADE_OUT

private const val WORD = "Shelf"

private fun clamp01(x: Float) = min(1f, max(0f, x))
private fun easeInOutQuart(x: Float): Float {
    val t = clamp01(x)
    return if (t < 0.5f) 8f * t * t * t * t else 1f - 8f * (1f - t) * (1f - t) * (1f - t) * (1f - t)
}
private fun easeOutQuart(x: Float): Float { val u = 1f - clamp01(x); return 1f - u * u * u * u }

/**
 * The launch intro: on a white screen a thin black line sweeps across (quartic ease, fading in then out),
 * the letters of "Shelf" appear one by one, a glint keeps running across them, then the whole screen fades
 * out to the home screen. Draws nothing once [onDone] has been called.
 */
@Composable
fun SplashOverlay(onDone: () -> Unit) {
    var t by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (t < TOTAL) t = withFrameNanos { (it - start) / 1_000_000L }
        onDone()
    }
    val ms = t.toFloat()
    val screenAlpha = 1f - easeInOutQuart((ms - FADE_OUT_START) / FADE_OUT)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = screenAlpha }
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        // The line: its right end travels across the screen with a quartic ease, and its opacity
        // rises then falls on the same curve (a quartic bell), so it is gone by the time the letters land.
        val lp = clamp01(ms / LINE_END)
        val lineAlpha = easeInOutQuart(if (lp < 0.5f) lp * 2f else (1f - lp) * 2f)
        if (lineAlpha > 0f) {
            Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                val head = size.width * easeInOutQuart(lp * 1.15f)
                val tail = size.width * easeInOutQuart((lp - 0.35f) / 0.65f)
                drawLine(
                    color = Color.Black.copy(alpha = lineAlpha),
                    start = Offset(tail, size.height / 2),
                    end = Offset(head, size.height / 2),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }

        // The word, drawn off-screen so the glint can be painted only where the letters are.
        Box(
            Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val g = ms - GLINT_START
                    if (g < 0f) return@drawWithContent
                    val phase = (g % GLINT_PERIOD) / GLINT_PERIOD
                    // Band travels from well left of the word to well right of it, slanted.
                    val band = size.width * 0.35f
                    val x = -band + (size.width + 2 * band) * easeInOutQuart(phase)
                    drawRect(
                        brush = Brush.linearGradient(
                            0f to Color.Transparent,
                            0.42f to Color(0x66B9C3CF),
                            0.5f to Color(0xF2FFFFFF),
                            0.58f to Color(0x66B9C3CF),
                            1f to Color.Transparent,
                            start = Offset(x - band, 0f),
                            end = Offset(x + band * 0.5f, size.height),
                        ),
                        blendMode = BlendMode.SrcAtop,
                    )
                    // A small four-point sparkle rides the band's peak across the word.
                    val s = sin(PI * phase).toFloat()
                    if (s > 0.05f) {
                        val c = Offset(x.coerceIn(0f, size.width), size.height * 0.28f)
                        val r = 9.dp.toPx() * s
                        val w = r * 0.18f
                        val star = Path().apply {
                            moveTo(c.x, c.y - r); lineTo(c.x + w, c.y - w); lineTo(c.x + r, c.y); lineTo(c.x + w, c.y + w)
                            lineTo(c.x, c.y + r); lineTo(c.x - w, c.y + w); lineTo(c.x - r, c.y); lineTo(c.x - w, c.y - w); close()
                        }
                        drawPath(star, Color(0xFF9AA6B4).copy(alpha = s), blendMode = BlendMode.SrcOver)
                    }
                },
        ) {
            Row {
                WORD.forEachIndexed { i, ch ->
                    val p = easeOutQuart((ms - LETTERS_START - i * LETTER_STAGGER) / LETTER_FADE)
                    Text(
                        text = ch.toString(),
                        style = TextStyle(
                            fontFamily = Inter, fontWeight = FontWeight.Light, fontSize = 72.sp,
                            color = Color(0xFF111111), letterSpacing = 2.sp,
                        ),
                        modifier = Modifier.graphicsLayer {
                            alpha = p
                            translationY = (1f - p) * 14.dp.toPx()
                        },
                    )
                }
            }
        }
    }
}
