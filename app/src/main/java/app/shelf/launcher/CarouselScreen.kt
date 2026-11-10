package app.shelf.launcher

import android.view.KeyEvent as AKeyEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Geometry for the carousel layout (dp). */
class CarouselMetrics(val screenW: Dp, val screenH: Dp, columns: Int) {
    val marginH = screenW * 0.058f
    val safeTop = screenH * 0.05f
    val icon = if (columns >= 6) 88.dp else 104.dp
    val gap = 30.dp
    val pitch = icon + gap
    val focusScale = 1.22f
    val heroTop = safeTop + 58.dp
    val heroH = 160.dp
    val heroW = 640.dp
    val trackTop = heroTop + heroH + 18.dp
    val trackH = 178.dp
    val iconTop = trackTop + 22.dp
    val pillsTop = trackTop + trackH + 14.dp
    val dividerW = 2.dp
}

@Composable
fun CarouselScreen(
    controller: HomeController,
    apps: List<GridItem.App>,
    dockCount: Int,
    pills: List<GridItem>,
    arranged: Arranged,
    settings: Settings,
    backdrop: Backdrop?,
    session: Session,
) {
    val app = LocalContext.current.applicationContext as ShelfApp
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val m = remember(maxWidth, maxHeight, settings.columns) { CarouselMetrics(maxWidth, maxHeight, settings.columns) }
        val motion = !settings.reduceMotion
        val requesters = remember { HashMap<String, FocusRequester>() }
        fun req(k: String) = requesters.getOrPut(k) { FocusRequester() }
        val indexOf = remember(apps) { HashMap<String, Int>().apply { apps.forEachIndexed { i, a -> put(a.key, i) } } }
        val allKeys = remember(apps, pills) { (apps.map { it.key } + pills.map { it.key }).toHashSet() }
        val itemsByKey = remember(apps, pills) { (apps + pills).associateBy { it.key } }
        val firstKey = apps.firstOrNull()?.key ?: pills.firstOrNull()?.key

        val iconPx = with(density) { m.icon.toPx() }
        val pitchPx = with(density) { m.pitch.toPx() }
        val hasDivider = dockCount in 1 until apps.size
        val dividerExtraPx = if (hasDivider) with(density) { (m.dividerW + m.gap).toPx() } else 0f
        fun xOf(i: Int) = i * pitchPx + if (hasDivider && i >= dockCount) dividerExtraPx else 0f
        val trackInset = m.marginH * 0.5f
        val trackWpx = with(density) { (maxWidth - trackInset * 2).toPx() }
        val centerPx = trackWpx / 2f
        val edgeFadePx = with(density) { 70.dp.toPx() }

        // The carousel keeps its position while focus is down in the pills row.
        val focusedIdx = indexOf[controller.focusedKey] ?: indexOf[session.lastCarouselKey] ?: 0
        val target = centerPx - (xOf(focusedIdx) + iconPx / 2f)
        val scroll = remember { Animatable(target) }
        LaunchedEffect(target, motion) {
            if (motion) scroll.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 320f))
            else scroll.animateTo(target, tween(140))
        }

        // ---- focus restore / Home press / edit re-focus / vanished tile ----
        fun focus(k: String?) = k?.let { runCatching { requesters[it]?.requestFocus() } }
        val resumeTick = session.resumeTick.intValue
        LaunchedEffect(firstKey != null, resumeTick) {
            if (firstKey == null) return@LaunchedEffect
            withFrameNanos { }
            if (controller.overlay == Overlay.NONE) focus(session.lastFocusedKey?.takeIf { it in allKeys } ?: firstKey)
        }
        val homeTick = session.homeTick.intValue
        LaunchedEffect(homeTick) {
            if (homeTick == 0) return@LaunchedEffect
            controller.resetToTop()
            withFrameNanos { }
            focus(firstKey)
        }
        LaunchedEffect(controller.editVersion) { withFrameNanos { }; focus(controller.editKey) }
        LaunchedEffect(allKeys) {
            val f = controller.focusedKey ?: return@LaunchedEffect
            if (f in allKeys || controller.overlay != Overlay.NONE) return@LaunchedEffect
            withFrameNanos { }
            focus(apps.getOrNull(session.lastCol.coerceAtMost(apps.size - 1))?.key ?: firstKey)
        }
        LaunchedEffect(controller.overlay) {
            if (controller.overlay == Overlay.NONE) { withFrameNanos { }; focus(session.lastFocusedKey ?: firstKey) }
        }

        Background(backdrop)
        if (settings.showClock) GlassClock(Modifier.align(Alignment.TopCenter).padding(top = m.safeTop))

        // ---- hero ("top shelf") card ----
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = m.heroTop)
                .size(m.heroW, m.heroH)
                .glass(30.dp),
        ) {
            Crossfade(itemsByKey[controller.focusedKey], animationSpec = tween(320), label = "hero") { item ->
                if (item != null) HeroContent(item, m, controller.editKey != null)
            }
        }

        // ---- glass shelf + carousel ----
        val iconPxScaled = with(density) { (m.icon * m.focusScale).toPx() }.roundToInt()
        val canFocus = controller.overlay == Overlay.NONE && controller.sheetFor == null
        // Icons live inside the glass track and are clipped to it, fading out before the rounded ends.
        Box(
            Modifier
                .padding(top = m.trackTop, start = trackInset, end = trackInset)
                .fillMaxWidth()
                .height(m.trackH)
                .glass(44.dp, tint = Color.White.copy(alpha = 0.07f))
                .graphicsLayer { shape = RoundedCornerShape(44.dp); clip = true },
        ) {
        Row(
            Modifier
                .padding(top = m.iconTop - m.trackTop)
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .graphicsLayer { translationX = scroll.value },
            horizontalArrangement = Arrangement.spacedBy(m.gap),
        ) {
            apps.forEachIndexed { i, item ->
                key(item.key) {
                    Box(
                        Modifier.graphicsLayer {
                            // Depth: icons shrink and fade with distance from the centre.
                            val cx = xOf(i) + iconPx / 2f + scroll.value
                            val d = abs(cx - centerPx) / pitchPx
                            val s = 1f - 0.05f * min(d, 4f)
                            scaleX = s; scaleY = s
                            val edge = ((min(cx, trackWpx - cx) - iconPx / 2f) / edgeFadePx).coerceIn(0f, 1f)
                            alpha = min((1.3f - 0.15f * d).coerceIn(0.3f, 1f), edge)
                        },
                    ) {
                        val img = rememberImage(app.images.iconKey(item, iconPxScaled)) { app.images.icon(item, iconPxScaled) }
                        ShelfTile(
                            image = img,
                            label = item.label,
                            width = m.icon,
                            height = m.icon,
                            requester = req(item.key),
                            motion = motion,
                            editing = controller.editKey == item.key,
                            canFocus = canFocus,
                            focusScale = m.focusScale,
                            corner = m.icon * ImageLoader.ICON_CORNER_FRACTION,
                            dimAlpha = 0.10f,
                            onFocused = {
                                controller.focusedKey = item.key
                                session.lastFocusedKey = item.key
                                session.lastCarouselKey = item.key
                                session.lastCol = i
                            },
                            onClick = { if (controller.editKey != null) controller.drop() else controller.open(item) },
                            onLongPress = {
                                if (controller.editKey == item.key) controller.sheetFor = item.key
                                else if (controller.editKey == null) controller.startEdit(item.key)
                            },
                            onMenu = {
                                if (controller.editKey == null) controller.startEdit(item.key)
                                controller.sheetFor = item.key
                            },
                            onEditMove = { d -> controller.moveLinear(d, arranged) },
                        )
                    }
                }
                if (hasDivider && i == dockCount - 1) {
                    // Separates favourites from the rest of the apps.
                    Box(
                        Modifier
                            .padding(top = m.icon * 0.2f)
                            .size(m.dividerW, m.icon * 0.6f)
                            .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(1.dp)),
                    )
                }
            }
        }

        }

        // ---- inputs + system pills ----
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = m.pillsTop),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pills.forEachIndexed { i, item ->
                if (i > 0 && item is GridItem.System && pills[i - 1] is GridItem.Input) {
                    Box(Modifier.size(2.dp, 22.dp).background(Color.White.copy(alpha = 0.3f)))
                }
                key(item.key) {
                    val iconPx = with(density) { 20.dp.toPx() }.roundToInt()
                    GlassPill(
                        // System entries are round icon buttons; their names show in the hero card.
                        label = if (item is GridItem.System) null else item.label,
                        icon = rememberImage(app.images.symbolKey(item, iconPx)) { app.images.symbol(item, iconPx) },
                        requester = req(item.key),
                        up = { session.lastCarouselKey?.let { requesters[it] } ?: firstKey?.let { requesters[it] } },
                        left = pills.getOrNull(i - 1)?.let { req(it.key) },
                        right = pills.getOrNull(i + 1)?.let { req(it.key) },
                        canFocus = canFocus,
                        onFocused = { controller.focusedKey = item.key; session.lastFocusedKey = item.key },
                        onClick = { controller.open(item) },
                    )
                }
            }
        }
    }
}

private fun shortLabel(k: SystemKind) = when (k) {
    SystemKind.HIDDEN_APPS -> "Hidden"
    SystemKind.ANDROID_SETTINGS -> "Settings"
    SystemKind.SHELF_SETTINGS -> "Shelf"
}

@Composable
private fun HeroContent(item: GridItem, m: CarouselMetrics, editing: Boolean) {
    val app = LocalContext.current.applicationContext as ShelfApp
    val density = LocalDensity.current
    val imgH = m.heroH - 32.dp
    val wide = item !is GridItem.App || item.app.hasBanner
    val imgW = if (wide) imgH * 16f / 9f else imgH
    val wPx = with(density) { imgW.toPx() }.roundToInt()
    val hPx = with(density) { imgH.toPx() }.roundToInt()
    Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        val img = if (item is GridItem.App && !wide) {
            rememberImage(app.images.iconKey(item, hPx)) { app.images.icon(item, hPx) }
        } else {
            rememberImage(app.images.heroKey(item, wPx, hPx)) { app.images.hero(item, wPx, hPx) }
        }
        Box(
            Modifier
                .size(imgW, imgH)
                .graphicsLayer {
                    shape = RoundedCornerShape(if (wide) 16.dp else imgH * ImageLoader.ICON_CORNER_FRACTION)
                    clip = true
                    shadowElevation = 14.dp.toPx()
                },
        ) {
            if (img != null) Image(img, null, filterQuality = FilterQuality.Low, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.padding(start = 26.dp).width(m.heroW - imgW - 74.dp)) {
            Text(item.label, style = Type.shelfTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val hint = when {
                editing -> "◀ ▶ to move · OK to drop · Back to cancel · Hold OK for options"
                item is GridItem.App -> "Hold OK to rearrange, hide, or favourite"
                item is GridItem.Input -> "TV input"
                item is GridItem.System && item.kind == SystemKind.HIDDEN_APPS -> "Apps you've hidden"
                item is GridItem.System && item.kind == SystemKind.SHELF_SETTINGS -> "Layout, clock, motion"
                else -> "Android TV settings"
            }
            Text(hint, style = Type.shelfHint, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun GlassPill(
    label: String?,
    icon: androidx.compose.ui.graphics.ImageBitmap?,
    requester: FocusRequester,
    up: () -> FocusRequester?,
    left: FocusRequester?,
    right: FocusRequester?,
    canFocus: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var down by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, spring(0.6f, 420f), label = "pill")
    Box(
        Modifier
            .focusRequester(requester)
            .focusProperties { this.canFocus = canFocus }
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .onPreviewKeyEvent { ev ->
                val c = ev.nativeKeyEvent.keyCode
                // Explicit wiring: geometric focus search can otherwise jump up into the carousel.
                val target = when (c) {
                    AKeyEvent.KEYCODE_DPAD_LEFT -> left
                    AKeyEvent.KEYCODE_DPAD_RIGHT -> right
                    AKeyEvent.KEYCODE_DPAD_UP -> up()
                    else -> null
                }
                if (c == AKeyEvent.KEYCODE_DPAD_LEFT || c == AKeyEvent.KEYCODE_DPAD_RIGHT ||
                    c == AKeyEvent.KEYCODE_DPAD_UP || c == AKeyEvent.KEYCODE_DPAD_DOWN
                ) {
                    if (ev.type == KeyEventType.KeyDown && target != null) {
                        if (c == AKeyEvent.KEYCODE_DPAD_UP) Nav.mark(Dir.UP)
                        runCatching { target.requestFocus() }
                    }
                    return@onPreviewKeyEvent true
                }
                if (c == AKeyEvent.KEYCODE_DPAD_CENTER || c == AKeyEvent.KEYCODE_ENTER || c == AKeyEvent.KEYCODE_NUMPAD_ENTER) {
                    if (ev.type == KeyEventType.KeyDown && ev.nativeKeyEvent.repeatCount == 0) down = true
                    if (ev.type == KeyEventType.KeyUp && down) { down = false; onClick() }
                    true
                } else false
            }
            .focusable()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glass(20.dp, tint = if (focused) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.10f))
            .padding(start = 12.dp, end = if (label != null) 16.dp else 12.dp, top = 9.dp, bottom = 9.dp),
    ) {
        val fg = if (focused) Color(0xFF0A2238) else Palette.text
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Image(
                    icon, null, colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(fg),
                    modifier = Modifier.padding(end = if (label != null) 7.dp else 0.dp).size(20.dp),
                )
            }
            if (label != null) Text(label, style = Type.pill, color = fg, maxLines = 1)
        }
    }
}
