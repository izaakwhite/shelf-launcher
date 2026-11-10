package app.shelf.launcher

import android.text.format.DateFormat
import android.view.KeyEvent as AKeyEvent
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
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

    // Ambient motion (ribbon drift, halo breathing, hue shift) runs on one 12 fps clock and never pauses, so the
    // background keeps moving while you scroll.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(motion) {
        if (!motion) return@LaunchedEffect
        while (true) { delay(84); tick++ }
    }

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

    LaunchedEffect(cat, motion) { if (motion) catAnim.animateTo(cat.toFloat(), spring(0.9f, 380f)) else catAnim.snapTo(cat.toFloat()) }
    LaunchedEffect(cat) { itemAnim.snapTo(sel.toFloat()) }
    LaunchedEffect(sel, motion) { if (motion) itemAnim.animateTo(sel.toFloat(), spring(0.9f, 520f)) else itemAnim.snapTo(sel.toFloat()) }
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
        // 0.5 degrees every 3 ticks (250 ms), as before, but driven by the shared ambient clock.
        val hue by remember { androidx.compose.runtime.derivedStateOf { (225f + (tick / 3) * 0.5f) % 360f } }

        // ---- background: month-tinted gradient ----
        Box(
            Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.background(
                Brush.linearGradient(
                    listOf(hsl(hue, 0.55f, 0.36f), hsl(hue, 0.60f, 0.20f), hsl(hue, 0.65f, 0.07f)),
                    start = Offset(0f, 0f), end = Offset(wPx, with(density) { H.toPx() } * 1.2f),
                ),
            ),
        )
        WaveLayer(motion, { tick }, wPx.roundToInt(), Modifier.align(Alignment.TopStart).padding(top = H * 0.41f))

        // ---- clock + date, top right ----
        WaveClock(Modifier.align(Alignment.TopEnd).padding(top = H * 0.05f, end = W * 0.058f))

        // ---- cross bar ----
        // Category bar sits top-left, inside the overscan-safe margins.
        val barY = H * 0.08f
        val anchorX = W * 0.058f + 34.dp
        val catPitch = 78.dp
        val catPitchPx = with(density) { catPitch.toPx() }
        categories.forEachIndexed { ci, c ->
            val focusedCat = ci == clampCat()
            // Categories already passed slide off to the left and fade out.
            val a by animateFloatAsState(if (focusedCat) 1f else if (ci < clampCat()) 0f else 0.5f, tween(200), label = "ca")
            val s by animateFloatAsState(if (focusedCat) 1.1f else 0.85f, spring(0.7f, 400f), label = "cs")
            Column(
                Modifier
                    .offset(x = anchorX - 30.dp, y = barY)
                    .width(60.dp)
                    .graphicsLayer {
                        translationX = (ci - catAnim.value) * catPitchPx
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        alpha = a; scaleX = s; scaleY = s
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(Modifier.size(36.dp).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                    // Inset leaves room for the soft glow around the focused category's icon.
                    inset(size.width * 0.11f) { drawCategoryIcon(c.icon, focusedCat) }
                }
                Text(
                    c.title, style = Type.small.copy(color = Color.White, fontSize = 12.sp), maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp).graphicsLayer { alpha = if (focusedCat) 1f else 0f },
                )
            }
        }

        // ---- breathing halo behind the selected icon ----
        run {
            if (current?.items?.isNotEmpty() == true) {
                Box(
                    Modifier
                        .offset(x = anchorX - 95.dp, y = H * 0.58f - 95.dp)
                        .size(190.dp)
                        .graphicsLayer {
                            // Breathing: 5.2 s cycle (62.5 ticks at 12 fps), eased with a raised cosine between 0.16 and 0.40.
                            alpha = if (motion) 0.16f + 0.24f * (0.5f - 0.5f * kotlin.math.cos(2f * PI.toFloat() * tick / 62.5f)) else 0.28f
                        }
                        .background(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0.25f), Color.Transparent))),
                )
            }
        }

        // ---- item wheel for the focused category: the selection stays put, the list turns past it ----
        // Switching category swaps the list and fades it in (a Crossfade kept both lists alive and doubled the layers).
        val listFade = remember { Animatable(0f) }
        LaunchedEffect(current?.id) { listFade.snapTo(0f); listFade.animateTo(1f, tween(150)) }
        run {
            val id = current?.id
            val c = categories.firstOrNull { it.id == id } ?: return@run
            val pitchPx = with(density) { 64.dp.toPx() }
            val clearPx = with(density) { 22.dp.toPx() }      // extra room around the selected item
            val curvePx = with(density) { 90.dp.toPx() }
            val anchorY = H * 0.58f
            val iconPx = with(density) { 72.dp.toPx() }.roundToInt()
            c.items.forEachIndexed { i, item ->
                if (abs(i - sel) > 4) return@forEachIndexed   // far rows are invisible; skip their layers
                val isSel = id == current?.id && i == sel
                val s by animateFloatAsState(if (isSel) 1f else 0.62f, spring(0.7f, 380f), label = "is")
                Row(
                    Modifier
                        .offset(x = anchorX - 60.dp, y = anchorY - 60.dp)
                        .graphicsLayer {
                            val rel = i - itemAnim.value
                            val ar = abs(rel)
                            translationY = rel * pitchPx + clearPx * rel.coerceIn(-1f, 1f)
                            translationX = (1f - kotlin.math.cos(rel * 0.4f)) * curvePx
                            rotationX = (-rel * 11f).coerceIn(-45f, 45f)
                            cameraDistance = 14f * this.density
                            // Cache the row as a texture: tilting live text in 3D is very slow on this GPU.
                            compositingStrategy = CompositingStrategy.Offscreen
                            alpha = listFade.value * when {
                                ar < 0.5f -> 1f
                                rel < -2.1f -> ((rel + 2.7f) / 0.6f).coerceIn(0f, 0.6f) // two items fit above; keep clear of the bar
                                rel > 1.6f -> (0.75f * (2.8f - rel) / 1.2f).coerceIn(0f, 0.75f)
                                else -> (0.95f - 0.18f * ar).coerceIn(0.2f, 0.85f)
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Padding keeps the selection glow inside this row's cached texture (no hard edges).
                    Box(Modifier.padding(24.dp).size(72.dp).graphicsLayer { scaleX = s; scaleY = s }) {
                        if (isSel) {
                            Box(
                                Modifier.fillMaxSize().graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }.background(
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
                            // App icons already have rounded corners baked in, so no live clip is needed.
                            modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = wiggle },
                        )
                    }
                    // One font size for every row; the selected label grows by animated scale, so the text
                    // never re-lays-out (which made it jump).
                    val ts by animateFloatAsState(if (isSel) 1f else 0.8f, spring(0.8f, 380f), label = "ts")
                    Text(
                        item.label,
                        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 24.sp, color = Color.White),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = 0.dp, end = 8.dp)
                            .widthIn(max = W * 0.36f)
                            .graphicsLayer {
                                scaleX = ts; scaleY = ts
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                            },
                    )
                }
            }
        }

        // ---- info panel on the right ----
        Box(Modifier.align(Alignment.TopEnd).padding(top = H * 0.22f, end = W * 0.058f).width(W * 0.34f)) {
            WaveInfoPanel(selected, controller, W * 0.34f, motion)
        }
    }
}

/** Debounced, fade-in info panel in its own recomposition scope. */
@Composable
private fun WaveInfoPanel(selected: GridItem?, controller: HomeController, panelW: androidx.compose.ui.unit.Dp, motion: Boolean) {
    var shown by remember { mutableStateOf(selected) }
    LaunchedEffect(selected?.key) { delay(140); shown = selected }
    val item = shown ?: return
    androidx.compose.runtime.key(item.key) {
        val fade = remember { Animatable(if (motion) 0f else 1f) }
        LaunchedEffect(Unit) { fade.animateTo(1f, tween(160)) }
        // Text and image never overlap, so per-draw alpha is exact and avoids a full offscreen buffer.
        Box(Modifier.graphicsLayer { alpha = fade.value; compositingStrategy = CompositingStrategy.ModulateAlpha }) {
            WaveInfo(item, editing = controller.editKey == item.key, panelW = panelW)
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
            val rPx = with(density) { 14.dp.toPx() }.roundToInt()
            rememberImage(app.images.waveHeroKey(item, wPx, hPx, rPx)) { app.images.waveHero(item, wPx, hPx, rPx) }
        }
        // Corners are baked into both images (icon and hero), so there is no live clip here.
        Box(Modifier.size(imgW, imgH)) {
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


/**
 * Two pre-rendered ribbons slid at different speeds: the main bundle of light threads over a soft
 * translucent surface, and a thin faster ribbon. Each bitmap is rendered at half size and stretched 2x when
 * blitted, so the wave is large without costing texture memory. The drift is stepped at ~12 fps: it is far
 * too slow to see the steps, and it lets most frames skip redrawing the ribbon (this GPU is fill-rate bound).
 */
@Composable
private fun WaveLayer(motion: Boolean, tick: () -> Int, screenWpx: Int, modifier: Modifier) {
    val ribbons by produceState(WaveArtCache.a?.let { it to WaveArtCache.b!! }) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                val a = WaveArt.render(1920, 140, period = 960, amp = 34f, lines = 12, phase = 0f, surface = true, px = 0.5f).asImageBitmap()
                val b = WaveArt.render(1920, 84, period = 640, amp = 22f, lines = 7, phase = 1.3f, surface = false, px = 0.5f).asImageBitmap()
                WaveArtCache.a = a; WaveArtCache.b = b
                a to b
            }
        }
    }
    val r = ribbons ?: return
    val density = LocalDensity.current
    Box(
        modifier
            .fillMaxWidth()
            .height(with(density) { 320.toDp() })
            .drawBehind {
                val p = if (motion) (tick() % 1080) / 1080f else 0.25f   // 1080 ticks x 84 ms = a 90 s loop
                val sway = sin(p * 2 * PI.toFloat() * 6) * 9f
                val half = (screenWpx / 2 + 1).coerceAtMost(1920 - 960)   // source px that cover the screen at 2x
                val sa = ((p * 960 * 3) % 960).toInt()
                val sb = (640 - ((p * 640 * 5) % 640)).toInt() % 640
                val w2 = half * 2
                drawImage(r.first, srcOffset = IntOffset(sa, 0), srcSize = IntSize(half, 140), dstOffset = IntOffset(0, 20 + sway.toInt()), dstSize = IntSize(w2, 280), filterQuality = FilterQuality.Low)
                drawImage(r.second, srcOffset = IntOffset(sb, 0), srcSize = IntSize(half, 84), dstOffset = IntOffset(0, (100 - sway).toInt()), dstSize = IntSize(w2, 168), filterQuality = FilterQuality.Low, alpha = 0.85f)
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
            value = "${DateFormat.format("MM/dd/yyyy", d)}   $time"
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

private fun hsl(h: Float, s: Float, l: Float) = Color(ColorUtils.HSLToColor(floatArrayOf(h, s, l)))

private fun DrawScope.drawCategoryIcon(icon: CatIcon, glow: Boolean) {
    if (glow) paintCategoryIcon(icon, 3.4f, Color.White.copy(alpha = 0.22f))
    paintCategoryIcon(icon, 1f, Color.White)
}

private fun DrawScope.paintCategoryIcon(icon: CatIcon, mul: Float, c: Color) {
    val w = size.width; val h = size.height
    val st = Stroke(width = w * 0.06f * mul, cap = StrokeCap.Round)
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
