package app.shelf.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Cold-start splash: the app icon eases in over the home gradient with a quiet wordmark and a thin
 * light sweeping under it, then dissolves once the home screen is ready. Shown once per process
 * (returning from another app never replays it). Animates only alpha / scale / translation, so it
 * stays on the cheap compositor path even on this TV's weak GPU.
 */
@Composable
fun SplashOverlay(session: Session) {
    if (session.splashDone) return
    val intro = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Intro and the "home is ready" signal run together; hold the splash a beat so it reads as
        // a deliberate moment rather than a flash, then dissolve.
        coroutineScope {
            launch { intro.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
            launch { while (!session.homeReady.value) withFrameNanos { } }
        }
        delay(250)
        exit.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
        session.splashDone = true
    }

    val sweep by rememberInfiniteTransition(label = "sweep").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(Brush.verticalGradient(listOf(Palette.bgTop, Palette.bgBottom))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier
                    .size(132.dp)
                    .graphicsLayer {
                        val t = intro.value
                        alpha = t
                        // Eases up from slightly small, then drifts a touch larger as it dissolves.
                        val s = 0.9f + 0.1f * t + 0.06f * exit.value
                        scaleX = s; scaleY = s
                    },
            )
            Spacer(Modifier.height(28.dp))
            androidx.tv.material3.Text(
                "SHELF",
                style = TextStyle(
                    fontFamily = Inter, fontWeight = FontWeight.Light, fontSize = 22.sp,
                    letterSpacing = 10.sp, color = Palette.text,
                ),
                modifier = Modifier.graphicsLayer { alpha = ((intro.value - 0.3f) / 0.7f).coerceIn(0f, 1f) * 0.85f },
            )
            Spacer(Modifier.height(36.dp))
            val trackW = 160.dp
            Box(
                Modifier
                    .width(trackW).height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .graphicsLayer { alpha = ((intro.value - 0.5f) / 0.5f).coerceIn(0f, 1f) },
            ) {
                Box(
                    Modifier
                        .width(trackW * 0.4f).height(2.dp)
                        .graphicsLayer { translationX = (sweep * 1.4f - 0.4f) * trackW.toPx() }
                        .background(
                            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent)),
                        ),
                )
            }
        }
    }
}
