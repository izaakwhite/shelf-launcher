package app.shelf.launcher

import android.view.KeyEvent as AKeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

private class PressState { var down = false; var long = false }

private fun dirOf(code: Int): Dir? = when (code) {
    AKeyEvent.KEYCODE_DPAD_LEFT -> Dir.LEFT
    AKeyEvent.KEYCODE_DPAD_RIGHT -> Dir.RIGHT
    AKeyEvent.KEYCODE_DPAD_UP -> Dir.UP
    AKeyEvent.KEYCODE_DPAD_DOWN -> Dir.DOWN
    else -> null
}

private fun isSelect(code: Int) = code == AKeyEvent.KEYCODE_DPAD_CENTER || code == AKeyEvent.KEYCODE_ENTER ||
    code == AKeyEvent.KEYCODE_NUMPAD_ENTER || code == AKeyEvent.KEYCODE_BUTTON_A

const val FOCUS_SCALE = 1.1f
val TileCorner = 12.dp

/**
 * One grid tile with the TV-style focus treatment:
 * spring scale + growing shadow ("lift"), a one-shot diagonal shine, a parallax tilt from the
 * direction focus arrived, and a nudge when pressing toward an edge with nowhere to go.
 */
@Composable
fun ShelfTile(
    image: ImageBitmap?,
    label: String?,
    width: Dp,
    height: Dp,
    requester: FocusRequester,
    motion: Boolean,
    editing: Boolean,
    canFocus: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onMenu: () -> Unit,
    onEditMove: (Dir) -> Boolean,
    modifier: Modifier = Modifier,
    focusScale: Float = FOCUS_SCALE,
    corner: Dp = TileCorner,
    dimAlpha: Float = 0.30f,
    bakedCorners: Boolean = false,   // image already has transparent rounded corners: skip the GPU clip
) {
    var focused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val press = remember { PressState() }

    val scale by animateFloatAsState(
        if (focused) focusScale else 1f,
        spring(dampingRatio = 0.6f, stiffness = 420f), label = "scale",
    )
    val lift by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "lift")
    val shine = remember { Animatable(1f) }
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val nudge = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val wiggle = if (editing) {
        rememberInfiniteTransition(label = "wiggle").animateFloat(
            -1.4f, 1.4f, infiniteRepeatable(tween(130, easing = LinearEasing), RepeatMode.Reverse), label = "w",
        )
    } else null

    val nudgePx = with(density) { 9.dp.toPx() }
    fun nudge(d: Dir) {
        scope.launch {
            nudge.animateTo(d.vec * nudgePx, tween(80))
            nudge.animateTo(Offset.Zero, spring(dampingRatio = 0.45f, stiffness = 500f))
        }
        if (motion) scope.launch {
            // Lean into the direction pressed, then settle.
            launch { tiltY.animateTo(d.vec.x * 7f, tween(80)); tiltY.animateTo(0f, spring(0.45f, 260f)) }
            tiltX.animateTo(-d.vec.y * 7f, tween(80)); tiltX.animateTo(0f, spring(0.45f, 260f))
        }
    }

    val shape = RoundedCornerShape(corner)
    Column(modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width, height)
                .focusRequester(requester)
                .focusProperties { this.canFocus = canFocus }
                .onFocusChanged { st ->
                    if (st.isFocused == focused) return@onFocusChanged
                    focused = st.isFocused
                    if (!focused) {
                        press.down = false
                        scope.launch { tiltX.snapTo(0f); tiltY.snapTo(0f) }
                        return@onFocusChanged
                    }
                    onFocused()
                    if (motion) {
                        scope.launch {
                            shine.snapTo(0f)
                            shine.animateTo(1f, tween(1000, delayMillis = 60, easing = LinearEasing))
                        }
                        Nav.recent()?.let { d ->
                            // Arrive tilted away from where focus came from, then ease flat.
                            scope.launch {
                                launch { tiltY.snapTo(-d.vec.x * 5f); tiltY.animateTo(0f, spring(0.55f, 120f)) }
                                tiltX.snapTo(d.vec.y * 5f); tiltX.animateTo(0f, spring(0.55f, 120f))
                            }
                        }
                    }
                }
                .onPreviewKeyEvent { ev ->
                    val code = ev.nativeKeyEvent.keyCode
                    val d = dirOf(code)
                    when {
                        d != null -> {
                            if (ev.type == KeyEventType.KeyDown) {
                                if (editing) {
                                    if (!onEditMove(d)) nudge(d)
                                } else {
                                    Nav.mark(d)
                                    if (!focusManager.moveFocus(d.focus)) { Nav.clear(); nudge(d) }
                                }
                            }
                            true
                        }
                        isSelect(code) -> {
                            val ne = ev.nativeKeyEvent
                            if (ev.type == KeyEventType.KeyDown) {
                                if (ne.repeatCount == 0 && !ne.isLongPress) {
                                    press.down = true; press.long = false
                                } else if (press.down && !press.long) {
                                    press.long = true
                                    onLongPress()
                                }
                            } else if (ev.type == KeyEventType.KeyUp) {
                                if (press.down && !press.long) onClick()
                                press.down = false
                            }
                            true
                        }
                        code == AKeyEvent.KEYCODE_MENU -> {
                            if (ev.type == KeyEventType.KeyDown) onMenu()
                            true
                        }
                        else -> false
                    }
                }
                .focusable()
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = nudge.value.x; translationY = nudge.value.y
                    rotationX = tiltX.value; rotationY = tiltY.value
                    rotationZ = wiggle?.value ?: 0f
                    cameraDistance = 10f * this.density
                    shadowElevation = lift * 10.dp.toPx()
                    this.shape = shape
                    clip = !bakedCorners
                }
                .drawBehind {
                    if (image != null) {
                        drawImage(
                            image,
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                            filterQuality = FilterQuality.Low,
                        )
                    } else {
                        drawRect(Color(0xFF232A38))
                    }
                }
                .drawWithContent {
                    drawContent()
                    val dim = 1f - lift
                    val cr = CornerRadius(corner.toPx())
                    if (dim > 0.01f && dimAlpha > 0f) drawRoundRect(Color.Black.copy(alpha = dimAlpha * dim), cornerRadius = cr)
                    val p = shine.value
                    if (p > 0.001f && p < 0.999f) {
                        val w = size.width
                        val band = w * 0.5f
                        val x = -band + (w + 2 * band) * p
                        // Fade the sheen in and out over the sweep instead of letting it pop off the edge.
                        val k = kotlin.math.sin(p * Math.PI.toFloat()).let { it * it }
                        drawRoundRect(
                            cornerRadius = cr,
                            brush = Brush.linearGradient(
                                0f to Color.Transparent,
                                0.5f to Color.White.copy(alpha = 0.30f * k),
                                1f to Color.Transparent,
                                start = Offset(x - band / 2, 0f),
                                end = Offset(x + band / 2, size.height * 0.55f),
                            ),
                        )
                    }
                    if (editing) {
                        drawRoundRect(
                            Color.White.copy(alpha = 0.9f),
                            cornerRadius = CornerRadius(corner.toPx()),
                            style = Stroke(width = 3.dp.toPx()),
                        )
                    }
                },
        )
        if (label != null) {
            Text(
                label,
                style = Type.tileLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = height * ((focusScale - 1f) / 2f) + 8.dp)
                    .graphicsLayer { alpha = lift },
            )
        }
    }
}
