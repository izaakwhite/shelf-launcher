package app.shelf.launcher

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlin.math.roundToInt

val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * Pseudo "liquid glass": the panel shows the pre-blurred backdrop at its own screen position
 * (so it reads as frosted glass with zero real-time blur), plus a tint, a top specular sheen,
 * a faint bottom glow, and a bright rim that fades down the sides.
 * Only for panels that don't move much — the sampling position is read from layout.
 */
fun Modifier.glass(
    corner: Dp,
    tint: Color = Color.White.copy(alpha = 0.10f),
    sheen: Float = 1f,
): Modifier = composed {
    val backdrop = LocalBackdrop.current
    val pos = remember { mutableStateOf(Offset.Zero) }
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWpx = with(density) { config.screenWidthDp.dp.toPx() }
    this
        .onGloballyPositioned { pos.value = it.positionInRoot() }
        // Rasterise the panel once into a cached texture: the rounded clip + blur sample + gradients
        // are far too slow to redo every frame on this GPU, but compositing a texture is cheap.
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val r = CornerRadius(corner.toPx())
            val path = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r)) }
            val gloss = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.22f * sheen),
                0.45f to Color.White.copy(alpha = 0.05f * sheen),
                1f to Color.Transparent,
            )
            val glow = Brush.verticalGradient(0.65f to Color.Transparent, 1f to Color.White.copy(alpha = 0.07f * sheen))
            val rim = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.75f * sheen),
                    Color.White.copy(alpha = 0.10f * sheen),
                    Color.White.copy(alpha = 0.28f * sheen),
                ),
            )
            val stroke = Stroke(1.3.dp.toPx())
            onDrawBehind {
                clipPath(path) {
                    val b = backdrop?.blurred
                    if (b != null && screenWpx > 0f) {
                        val s = b.width / screenWpx
                        val p = pos.value
                        val sx = (p.x * s).roundToInt().coerceIn(0, b.width - 1)
                        val sy = (p.y * s).roundToInt().coerceIn(0, b.height - 1)
                        val sw = (size.width * s).roundToInt().coerceIn(1, b.width - sx)
                        val sh = (size.height * s).roundToInt().coerceIn(1, b.height - sy)
                        drawImage(
                            b, srcOffset = IntOffset(sx, sy), srcSize = IntSize(sw, sh),
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            filterQuality = FilterQuality.Low,
                        )
                    }
                    drawRect(tint)
                    drawRect(gloss)
                    drawRect(glow)
                }
                drawRoundRect(rim, cornerRadius = r, style = stroke)
            }
        }
}

@Composable
fun Background(backdrop: Backdrop?) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1E33), Color(0xFF06111D)))),
    )
    Crossfade(backdrop, animationSpec = tween(500), label = "bg") { b ->
        if (b != null) {
            Image(b.sharp, null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low, modifier = Modifier.fillMaxSize())
            if (b.user) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        }
    }
}

@Composable
fun GlassClock(modifier: Modifier = Modifier) {
    Box(modifier.glass(22.dp, tint = Color.White.copy(alpha = 0.12f)).padding(horizontal = 26.dp, vertical = 5.dp)) {
        Clock()
    }
}

@Composable
fun GlassLabel(text: String, modifier: Modifier = Modifier) {
    Box(modifier.glass(16.dp).padding(horizontal = 16.dp, vertical = 6.dp)) { Text(text, style = Type.header) }
}
