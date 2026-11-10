package app.shelf.launcher

import android.text.format.DateFormat
import android.view.KeyEvent as AKeyEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** One column of the cross bar. */
class WaveCategory(val id: String, val title: String, val icon: CatIcon, val items: List<GridItem>)
enum class CatIcon { STAR, APPS, TV, SETTINGS }

private object WaveArtCache {
    var a: androidx.compose.ui.graphics.ImageBitmap? = null
    var b: androidx.compose.ui.graphics.ImageBitmap? = null
}

/**
 * "Wave" style: a console-dashboard-inspired cross bar. Left/Right picks a category, Up/Down picks
 * an item; the background is a month-tinted gradient with a slowly drifting ribbon of light.
 * The whole screen is one focus target, so focus can never get lost.
 */
@Composable
fun WaveScreen(
    controller: HomeController,
    categories: List<WaveCategory>,
    arranged: Arranged,
    settings: Settings,
    session: Session,
) {
    val app = LocalContext.current.applicationContext as ShelfApp
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val motion = !settings.reduceMotion

    // Selection state (per-category item index is remembered, like the original dashboard).
    var cat by remember { mutableIntStateOf(0) }
    val itemIdx = remember { mutableStateMapOf<String, Int>() }
    val catAnim = remember { Animatable(0f) }
    val itemAnim = remember { Animatable(0f) }
    val root = remember { FocusRequester() }

    fun clampCat() = cat.coerceIn(0, (categories.size - 1).coerceAtLeast(0))
    val current = categories.getOrNull(clampCat())
    val sel = current?.let { (itemIdx[it.id] ?: 0).coerceIn(0, (it.items.size - 1).coerceAtLeast(0)) } ?: 0
    val selected = current?.items?.getOrNull(sel)

    fun publish() {
        selected?.let {
            controller.focusedKey = it.key
            session.lastFocusedKey = it.key
        }
    }
    fun selectKey(k: String?): Boolean {
        if (k == null) return false
        categories.forEachIndexed { ci, c ->
            val ii = c.items.indexOfFirst { it.key == k }
            if (ii >= 0) { cat = ci; itemIdx[c.id] = ii; return true }
        }
        return false
    }

    LaunchedEffect(cat, motion) { if (motion) catAnim.animateTo(cat.toFloat(), spring(0.9f, 260f)) else catAnim.snapTo(cat.toFloat()) }
    LaunchedEffect(cat) { itemAnim.snapTo(sel.toFloat()) }
    LaunchedEffect(sel, motion) { if (motion) itemAnim.animateTo(sel.toFloat(), spring(0.9f, 320f)) else itemAnim.snapTo(sel.toFloat()) }
    LaunchedEffect(selected?.key) { publish() }

    val resumeTick = session.resumeTick.intValue
    LaunchedEffect(resumeTick, categories.isNotEmpty()) {
        if (categories.isEmpty()) return@LaunchedEffect
        selectKey(session.lastFocusedKey)
        withFrameNanos { }
        if (controller.overlay == Overlay.NONE) runCatching { root.requestFocus() }
    }
    val homeTick = session.homeTick.intValue
    LaunchedEffect(homeTick) {
        if (homeTick == 0) return@LaunchedEffect
        controller.resetToTop()
        cat = 0; itemIdx.clear()
        runCatching { root.requestFocus() }
    }
    LaunchedEffect(controller.overlay, controller.sheetFor) {
        if (controller.overlay == Overlay.NONE && controller.sheetFor == null) { withFrameNanos { }; runCatching { root.requestFocus() } }
    }
    // Keep the edited item selected as it moves.
    LaunchedEffect(controller.editVersion) { controller.editKey?.let { selectKey(it) } }

    val press = remember { BooleanArray(2) } // [down, longFired]

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .focusRequester(root)
            .onPreviewKeyEvent { ev ->
                val ne = ev.nativeKeyEvent
                val down = ev.type == KeyEventType.KeyDown
                val editing = controller.editKey != null
                when (ne.keyCode) {
                    AKeyEvent.KEYCODE_DPAD_LEFT, AKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (down && !editing) {
                            val d = if (ne.keyCode == AKeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                            cat = (clampCat() + d).coerceIn(0, categories.size - 1)
                        }
                        true
                    }
                    AKeyEvent.KEYCODE_DPAD_UP, AKeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (down && current != null) {
                            val up = ne.keyCode == AKeyEvent.KEYCODE_DPAD_UP
                            if (editing) controller.moveLinear(if (up) Dir.LEFT else Dir.RIGHT, arranged, crossDivider = false)
                            else itemIdx[current.id] = (sel + if (up) -1 else 1).coerceIn(0, current.items.size - 1)
                        }
                        true
                    }
                    AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER, AKeyEvent.KEYCODE_NUMPAD_ENTER, AKeyEvent.KEYCODE_BUTTON_A -> {
                        if (down) {
                            if (ne.repeatCount == 0 && !ne.isLongPress) { press[0] = true; press.fill(false, 1, 2) }
                            else if (press[0] && !press.getOrElse(1) { false }.let { it }) {
                                press[1] = true
                                (selected as? GridItem.App)?.let {
                                    if (controller.editKey == it.key) controller.sheetFor = it.key
                                    else if (controller.editKey == null) controller.startEdit(it.key)
                                }
                            }
                        } else if (ev.type == KeyEventType.KeyUp) {
                            if (press[0] && !press[1]) {
                                if (editing) controller.drop() else selected?.let { controller.open(it) }
                            }
                            press[0] = false
                        }
                        true
                    }
                    AKeyEvent.KEYCODE_MENU -> {
                        if (down) (selected as? GridItem.App)?.let {
                            if (controller.editKey == null) controller.startEdit(it.key)
                            controller.sheetFor = it.key
                        }
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        val W = maxWidth; val H = maxHeight
        val wPx = with(density) { W.toPx() }
        val hue = monthHue()

        // ---- background: month-tinted gradient ----
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    listOf(hsl(hue, 0.55f, 0.36f), hsl(hue, 0.60f, 0.20f), hsl(hue, 0.65f, 0.07f)),
                    start = Offset(0f, 0f), end = Offset(wPx, with(density) { H.toPx() } * 1.2f),
                ),
            ),
        )
        WaveLayer(motion, wPx.roundToInt(), Modifier.align(Alignment.TopStart).padding(top = H * 0.46f))

        // ---- clock + date, top right ----
        WaveClock(Modifier.align(Alignment.TopEnd).padding(top = H * 0.05f, end = W * 0.058f))

        // ---- cross bar ----
        // Category bar sits top-left, inside the overscan-safe margins.
        val barY = H * 0.08f
        val anchorX = W * 0.058f + 34.dp
        val catPitch = 96.dp
        val catPitchPx = with(density) { catPitch.toPx() }
        categories.forEachIndexed { ci, c ->
            val focusedCat = ci == clampCat()
            // Categories already passed slide off to the left and fade out.
            val a by animateFloatAsState(if (focusedCat) 1f else if (ci < clampCat()) 0f else 0.5f, tween(200), label = "ca")
            val s by animateFloatAsState(if (focusedCat) 1.1f else 0.85f, spring(0.7f, 400f), label = "cs")
            Column(
                Modifier
                    .offset(x = anchorX - 34.dp, y = barY)
                    .width(68.dp)
                    .graphicsLayer {
                        translationX = (ci - catAnim.value) * catPitchPx
                        alpha = a; scaleX = s; scaleY = s
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(Modifier.size(36.dp)) { drawCategoryIcon(c.icon) }
                Text(
                    c.title, style = Type.small.copy(color = Color.White), maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp).graphicsLayer { alpha = if (focusedCat) 1f else 0f },
                )
            }
        }

        // ---- item wheel for the focused category: the selection stays put, the list turns past it ----
        Crossfade(current?.id, animationSpec = tween(220), label = "items") { id ->
            val c = categories.firstOrNull { it.id == id } ?: return@Crossfade
            val pitchPx = with(density) { 64.dp.toPx() }
            val clearPx = with(density) { 22.dp.toPx() }      // extra room around the selected item
            val curvePx = with(density) { 90.dp.toPx() }
            val anchorY = H * 0.40f
            val iconPx = with(density) { 72.dp.toPx() }.roundToInt()
            c.items.forEachIndexed { i, item ->
                val isSel = id == current?.id && i == sel
                val s by animateFloatAsState(if (isSel) 1f else 0.62f, spring(0.7f, 380f), label = "is")
                Row(
                    Modifier
                        .offset(x = anchorX - 36.dp, y = anchorY - 36.dp)
                        .graphicsLayer {
                            val rel = i - itemAnim.value
                            val ar = abs(rel)
                            translationY = rel * pitchPx + clearPx * rel.coerceIn(-1f, 1f)
                            translationX = (1f - kotlin.math.cos(rel * 0.4f)) * curvePx
                            rotationX = (-rel * 11f).coerceIn(-45f, 45f)
                            cameraDistance = 14f * this.density
                            alpha = when {
                                ar < 0.5f -> 1f
                                rel < -1.1f -> ((rel + 1.7f) / 0.6f).coerceIn(0f, 0.6f) // only one item fits above; keep clear of the bar
                                rel > 3.2f -> (0.75f * (4.4f - rel) / 1.2f).coerceIn(0f, 0.75f)
                                else -> (0.95f - 0.18f * ar).coerceIn(0.2f, 0.85f)
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(72.dp).graphicsLayer { scaleX = s; scaleY = s }) {
                        if (isSel) {
                            Box(
                                Modifier.fillMaxSize().graphicsLayer { scaleX = 1.7f; scaleY = 1.7f }.background(
                                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent)),
                                ),
                            )
                        }
                        val img = when (item) {
                            is GridItem.App -> rememberImage(app.images.iconKey(item, iconPx)) { app.images.icon(item, iconPx) }
                            else -> rememberImage(app.images.symbolKey(item, iconPx)) { app.images.symbol(item, iconPx) }
                        }
                        val wiggle = if (controller.editKey == item.key) {
                            rememberInfiniteTransition(label = "w").animateFloat(-2f, 2f, infiniteRepeatable(tween(130, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "w").value
                        } else 0f
                        if (img != null) Image(
                            img, null, filterQuality = FilterQuality.Low,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                rotationZ = wiggle
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(72.dp * ImageLoader.ICON_CORNER_FRACTION)
                                clip = item is GridItem.App
                            },
                        )
                    }
                    Text(
                        item.label,
                        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = if (isSel) 24.sp else 19.sp, color = Color.White),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp).width(W * 0.36f),
                    )
                }
            }
        }

        // ---- info panel on the right ----
        Box(Modifier.align(Alignment.TopEnd).padding(top = H * 0.22f, end = W * 0.058f).width(W * 0.34f)) {
            Crossfade(selected, animationSpec = tween(260), label = "info") { item ->
                if (item != null) WaveInfo(item, editing = controller.editKey == item.key, panelW = W * 0.34f)
            }
        }
    }
}

@Composable
private fun WaveInfo(item: GridItem, editing: Boolean, panelW: androidx.compose.ui.unit.Dp) {
    val app = LocalContext.current.applicationContext as ShelfApp
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val wide = item !is GridItem.App || item.app.hasBanner
    val imgW = if (wide) panelW else panelW * 0.42f
    val imgH = if (wide) panelW * 9f / 16f else imgW
    val wPx = with(density) { imgW.toPx() }.roundToInt()
    val hPx = with(density) { imgH.toPx() }.roundToInt()
    val details by produceState<List<String>>(emptyList(), item.key) {
        value = withContext(Dispatchers.IO) {
            when (item) {
                is GridItem.App -> runCatching {
                    val pi = ctx.packageManager.getPackageInfo(item.app.packageName, 0)
                    val df = DateFormat.getMediumDateFormat(ctx)
                    listOfNotNull(
                        pi.versionName?.let { "Version $it" },
                        "Updated " + df.format(Date(pi.lastUpdateTime)),
                        if (item.app.leanback) "TV app" else "Phone/tablet app",
                        item.app.packageName,
                    )
                }.getOrDefault(listOf(item.app.packageName))
                is GridItem.Input -> listOf(if (item.input.passthrough) "External input" else "Built-in tuner")
                is GridItem.System -> listOf(
                    when (item.kind) {
                        SystemKind.SHELF_SETTINGS -> "Style, icon size, clock, motion"
                        SystemKind.ANDROID_SETTINGS -> "Network, display, sound, apps"
                        SystemKind.HIDDEN_APPS -> "Apps you've hidden from the home screen"
                    },
                )
            }
        }
    }
    Column {
        val img = if (item is GridItem.App && !wide) {
            rememberImage(app.images.iconKey(item, wPx)) { app.images.icon(item, wPx) }
        } else {
            rememberImage(app.images.heroKey(item, wPx, hPx)) { app.images.hero(item, wPx, hPx) }
        }
        Box(
            Modifier.size(imgW, imgH).graphicsLayer {
                shape = androidx.compose.foundation.shape.RoundedCornerShape(if (wide) 14.dp else imgW * ImageLoader.ICON_CORNER_FRACTION)
                clip = true
                shadowElevation = 18.dp.toPx()
            },
        ) {
            if (img != null) Image(img, null, filterQuality = FilterQuality.Low, modifier = Modifier.fillMaxSize())
        }
        Text(item.label, style = Type.shelfTitle.copy(fontSize = 30.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 18.dp))
        details.forEach { Text(it, style = Type.small.copy(fontSize = 15.sp, color = Color.White.copy(alpha = 0.72f)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
        Text(
            when {
                editing -> "▲▼ move · OK drop · Back cancel · Hold OK for options"
                item is GridItem.App -> "OK to open · Hold OK for options"
                else -> "OK to open"
            },
            style = Type.small.copy(fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f)),
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}


/** Two pre-rendered ribbon bitmaps slid at different speeds. Animation is a bitmap blit per frame. */
@Composable
private fun WaveLayer(motion: Boolean, screenWpx: Int, modifier: Modifier) {
    val ribbons by produceState(WaveArtCache.a?.let { it to WaveArtCache.b!! }) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                val a = WaveArt.render(3840, 420, period = 1920, amp = 70f, lines = 16, phase = 0f, surface = true).asImageBitmap()
                val b = WaveArt.render(3840, 420, period = 1280, amp = 46f, lines = 9, phase = 1.3f, surface = false).asImageBitmap()
                WaveArtCache.a = a; WaveArtCache.b = b
                a to b
            }
        }
    }
    val r = ribbons ?: return
    val t = if (motion) {
        rememberInfiniteTransition(label = "wave").animateFloat(0f, 1f, infiniteRepeatable(tween(90_000, easing = LinearEasing)), label = "t")
    } else null
    val density = LocalDensity.current
    Box(
        modifier
            .fillMaxWidth()
            .height(with(density) { 420.toDp() })
            .drawBehind {
                val p = t?.value ?: 0.25f
                val sway = sin(p * 2 * PI.toFloat() * 6) * 10f
                val sa = ((p * 1920 * 3) % 1920).toInt()
                val sb = (1280 - ((p * 1280 * 5) % 1280)).toInt() % 1280
                val w = screenWpx.coerceAtMost(3840 - 1920)
                drawImage(r.first, srcOffset = IntOffset(sa, 0), srcSize = IntSize(w, 420), dstOffset = IntOffset(0, sway.toInt()), dstSize = IntSize(w, 420), filterQuality = FilterQuality.Low)
                drawImage(r.second, srcOffset = IntOffset(sb, 0), srcSize = IntSize(w, 420), dstOffset = IntOffset(0, (40 - sway).toInt()), dstSize = IntSize(w, 420), filterQuality = FilterQuality.Low, alpha = 0.8f)
            },
    )
}

@Composable
private fun WaveClock(modifier: Modifier) {
    val ctx = LocalContext.current
    val text by produceState("") {
        while (true) {
            val d = Date()
            val time = DateFormat.format(if (DateFormat.is24HourFormat(ctx)) "H:mm" else "h:mm a", d)
            value = "${DateFormat.format("M/d", d)}   $time"
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50)
        }
    }
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(text, style = Type.clock.copy(fontSize = 20.sp))
        Box(Modifier.padding(top = 6.dp).width(220.dp).height(1.dp).background(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.6f))),
        ))
    }
}

private fun monthHue(): Float {
    // A different tint every month, like the old dashboards.
    val hues = floatArrayOf(205f, 215f, 140f, 95f, 175f, 45f, 25f, 190f, 280f, 30f, 225f, 350f)
    return hues[Calendar.getInstance().get(Calendar.MONTH)]
}

private fun hsl(h: Float, s: Float, l: Float) = Color(ColorUtils.HSLToColor(floatArrayOf(h, s, l)))

private fun DrawScope.drawCategoryIcon(icon: CatIcon) {
    val w = size.width; val h = size.height
    val st = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
    val c = Color.White
    when (icon) {
        CatIcon.STAR -> {
            val p = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) w * 0.46f else w * 0.2f
                val a = -PI / 2 + i * PI / 5
                val x = w / 2 + (r * kotlin.math.cos(a)).toFloat(); val y = h / 2 + (r * sin(a)).toFloat() + h * 0.03f
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close(); drawPath(p, c, style = st)
        }
        CatIcon.APPS -> {
            val g = w * 0.12f; val q = (w * 0.8f - g) / 2; val o = w * 0.1f
            for (i in 0..1) for (j in 0..1) drawRoundRect(
                c, topLeft = Offset(o + i * (q + g), o + j * (q + g)), size = androidx.compose.ui.geometry.Size(q, q),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(q * 0.22f), style = st,
            )
        }
        CatIcon.TV -> {
            drawRoundRect(c, topLeft = Offset(w * 0.06f, h * 0.24f), size = androidx.compose.ui.geometry.Size(w * 0.88f, h * 0.58f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f), style = st)
            drawLine(c, Offset(w * 0.5f, h * 0.24f), Offset(w * 0.32f, h * 0.06f), st.width, StrokeCap.Round)
            drawLine(c, Offset(w * 0.5f, h * 0.24f), Offset(w * 0.68f, h * 0.06f), st.width, StrokeCap.Round)
            drawLine(c, Offset(w * 0.3f, h * 0.94f), Offset(w * 0.7f, h * 0.94f), st.width, StrokeCap.Round)
        }
        CatIcon.SETTINGS -> {
            listOf(0.25f to 0.3f, 0.5f to 0.68f, 0.75f to 0.45f).forEach { (y, k) ->
                drawLine(c, Offset(w * 0.08f, h * y), Offset(w * 0.92f, h * y), st.width, StrokeCap.Round)
                drawCircle(c, w * 0.09f, Offset(w * (0.08f + 0.84f * k), h * y))
            }
        }
    }
}
