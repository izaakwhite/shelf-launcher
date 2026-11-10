package app.shelf.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Quartic ease-in-out: slow, then fast, then slow — a long, soft acceleration at both ends. */
private val QuarticInOut = Easing { t -> if (t < 0.5f) 8f * t * t * t * t else 1f - Math.pow((-2.0 * t + 2.0), 4.0).toFloat() / 2f }

/**
 * Cold-start splash on a white screen: a thin black line draws out from the centre across the full
 * width (quartic ease), the SHELF letters fade in above it, a soft highlight glistens across the
 * letters on a loop, and the whole screen fades out once the home screen has loaded.
 * Shown once per process. Only alpha / translation / a draw-phase gradient are animated, so it stays
 * cheap on this TV's weak GPU.
 */
@Composable
fun SplashOverlay(session: Session) {
    if (session.splashDone) return
    val line = remember { Animatable(0f) }
    val letters = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { line.animateTo(1f, tween(1100, easing = QuarticInOut)) }
            launch { delay(700); letters.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
            // Hold long enough to see one full glisten, and until home is composed.
            launch { delay(2300); while (!session.homeReady.value) withFrameNanos { } }
        }
        exit.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        session.splashDone = true
    }

    val glisten by rememberInfiniteTransition(label = "glisten").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "glisten",
    )

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(Color.White),
    ) {
        // Thin black line, growing from the centre to both edges.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(2.dp)
                .graphicsLayer {
                    scaleX = line.value
                    alpha = (line.value * 3f).coerceIn(0f, 1f)
                }
                .background(Color.Black),
        )
        // Letters sit just above the line.
        androidx.tv.material3.Text(
            "SHELF",
            style = TextStyle(
                fontFamily = Inter, fontWeight = FontWeight.Light, fontSize = 54.sp,
                letterSpacing = 22.sp, color = Color(0xFF0B0D12),
            ),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-52).dp)
                .graphicsLayer {
                    alpha = letters.value
                    translationY = (1f - letters.value) * 10f
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    // A soft silvery band sweeping left→right across the letters (only where letters are).
                    val w = size.width
                    val x = -0.5f * w + glisten * 2f * w
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color(0xFFD9DEE8).copy(alpha = 0.95f), Color.Transparent),
                            start = Offset(x, 0f), end = Offset(x + 0.5f * w, size.height * 0.3f),
                        ),
                        blendMode = BlendMode.SrcAtop,
                    )
                },
        )
    }
}
