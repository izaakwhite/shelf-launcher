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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.core.spring
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
 * Cold-start splash on a white screen, centred as one group: SHELF in light letters with a thicker black
 * line under it that drops in and bounces to rest (no sideways wipe). The line is wider than the letters,
 * so it has padding either side, and a soft glisten keeps sweeping across the letters. The whole screen
 * fades out once the home screen has loaded. Shown once per process. Only alpha / translation / a
 * draw-phase gradient are animated, so it stays cheap on this TV's weak GPU.
 */
@Composable
fun SplashOverlay(session: Session) {
    if (session.splashDone) return
    val drop = remember { Animatable(0f) }      // 0 = high above its place, 1 = at rest (overshoots when it bounces)
    val lineAlpha = remember { Animatable(0f) }
    val letters = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { lineAlpha.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
            // A springy drop with real overshoot: it lands, rebounds a few times and settles.
            launch { drop.animateTo(1f, spring(dampingRatio = 0.34f, stiffness = 90f)) }
            launch { delay(750); letters.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
            // Hold long enough to see one full glisten, and until home is composed.
            launch { delay(2400); while (!session.homeReady.value) withFrameNanos { } }
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
        contentAlignment = Alignment.Center,
    ) {
        // The column is as wide as the letters plus side padding; the line fills that width.
        Column(Modifier.width(IntrinsicSize.Max), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.tv.material3.Text(
                "SHELF",
                style = TextStyle(
                    fontFamily = Inter, fontWeight = FontWeight.Light, fontSize = 54.sp,
                    letterSpacing = 22.sp, color = Color(0xFF0B0D12),
                ),
                modifier = Modifier
                    // Letter-spacing leaves a gap after the last letter; the extra start padding re-centres the glyphs.
                    .padding(start = 22.dp, end = 0.dp)
                    .padding(horizontal = 44.dp)
                    .graphicsLayer {
                        alpha = letters.value
                        translationY = (1f - letters.value) * 10f
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        // A soft silvery band sweeping left to right across the letters (only where letters are).
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
            Spacer(Modifier.height(22.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .graphicsLayer {
                        // Drops from 120 px above and bounces to rest; no sideways motion.
                        translationY = (1f - drop.value) * -120f
                        alpha = lineAlpha.value
                    }
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.Black),
            )
        }
    }
}
