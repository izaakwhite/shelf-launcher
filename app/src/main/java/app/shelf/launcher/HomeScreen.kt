package app.shelf.launcher

import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.roundToInt

/** Geometry for one screen size + column count (all in dp). */
class Metrics(val screenW: Dp, val screenH: Dp, columns: Int) {
    val marginH = screenW * 0.058f
    val safeTop = screenH * 0.05f
    val gap = if (columns >= 6) 26.dp else 32.dp
    val tileW = (screenW - marginH * 2 - gap * (columns - 1)) / columns
    val tileH = tileW * 9f / 16f
    val labelSpace = 40.dp
    val shelfH = screenH * 0.45f
    val heroH = shelfH - safeTop - 74.dp
    val heroW = heroH * 16f / 9f
}

@Composable
fun rememberImage(cacheKey: String, load: suspend () -> ImageBitmap): ImageBitmap? {
    val images = (LocalContext.current.applicationContext as ShelfApp).images
    return produceState(images.cached(cacheKey), cacheKey) {
        value = images.cached(cacheKey) ?: runCatching { load() }.getOrNull()
    }.value
}

@Composable
fun HomeScreen(
    controller: HomeController,
    rows: List<GridRow>,
    arranged: Arranged,
    settings: Settings,
    backdrop: Backdrop?,
    session: Session,
) {
    val app = LocalContext.current.applicationContext as ShelfApp
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val m = remember(maxWidth, maxHeight, settings.columns) { Metrics(maxWidth, maxHeight, settings.columns) }
        val motion = !settings.reduceMotion
        val requesters = remember { HashMap<String, FocusRequester>() }
        fun req(k: String) = requesters.getOrPut(k) { FocusRequester() }
        val keyToRow = remember(rows) {
            HashMap<String, Int>().apply { rows.forEachIndexed { r, row -> row.items.forEach { put(it.key, r) } } }
        }
        val itemsByKey = remember(rows) { rows.flatMap { it.items }.associateBy { it.key } }
        val firstKey = rows.firstOrNull()?.items?.firstOrNull()?.key

        // ---- scrolling: deterministic, driven by the focused row (no bring-into-view guessing) ----
        val rowTops = remember { mutableStateMapOf<Int, Float>() }
        val contentH = remember { mutableFloatStateOf(0f) }
        val scroll = remember { Animatable(0f) }
        val screenHpx = with(density) { maxHeight.toPx() }
        val pivotPx = with(density) { (m.safeTop + 46.dp).toPx() }
        val bottomPadPx = with(density) { 40.dp.toPx() }
        val shelfPx = with(density) { m.shelfH.toPx() }
        val target by remember(rows) {
            derivedStateOf {
                val r = keyToRow[controller.focusedKey] ?: 0
                if (r == 0) 0f else {
                    val maxScroll = (contentH.floatValue - screenHpx + bottomPadPx).coerceAtLeast(0f)
                    ((rowTops[r] ?: 0f) - pivotPx).coerceIn(0f, maxScroll)
                }
            }
        }
        LaunchedEffect(target, motion) {
            if (motion) scroll.animateTo(target, spring(dampingRatio = 1f, stiffness = 260f))
            else scroll.animateTo(target, tween(160))
        }

        // ---- focus restore / Home press / edit-mode re-focus ----
        fun focus(k: String?) = k?.let { runCatching { requesters[it]?.requestFocus() } }
        val resumeTick = session.resumeTick.intValue
        LaunchedEffect(firstKey != null, resumeTick) {
            if (firstKey == null) return@LaunchedEffect
            withFrameNanos { }
            if (controller.overlay == Overlay.NONE) {
                focus(session.lastFocusedKey?.takeIf { it in keyToRow } ?: firstKey)
            }
        }
        val homeTick = session.homeTick.intValue
        LaunchedEffect(homeTick) {
            if (homeTick == 0) return@LaunchedEffect
            controller.resetToTop()
            withFrameNanos { }
            focus(firstKey)
        }
        LaunchedEffect(controller.editVersion) {
            withFrameNanos { }
            focus(controller.editKey)
        }
        // If the focused tile vanished (uninstalled, hidden), move focus to its neighbour.
        LaunchedEffect(keyToRow) {
            val f = controller.focusedKey ?: return@LaunchedEffect
            if (f in keyToRow || controller.overlay != Overlay.NONE) return@LaunchedEffect
            withFrameNanos { }
            val prevRow = session.lastRow.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
            val row = rows.getOrNull(prevRow)
            focus(row?.items?.getOrNull(session.lastCol.coerceAtMost(row.items.size - 1))?.key ?: firstKey)
        }
        LaunchedEffect(controller.overlay) {
            if (controller.overlay == Overlay.NONE) { withFrameNanos { }; focus(session.lastFocusedKey ?: firstKey) }
        }

        Background(backdrop)

        // ---- hero banner area ----
        val focusedItem = itemsByKey[controller.focusedKey]
        Box(
            Modifier
                .fillMaxWidth()
                .height(m.shelfH + 70.dp)
                .graphicsLayer { alpha = (1f - scroll.value / (shelfPx * 0.55f)).coerceIn(0f, 1f) },
        ) {
            TopShelf(focusedItem, m, controller.editKey != null)
        }

        // ---- Grid ----
        val tileWpx = with(density) { (m.tileW * FOCUS_SCALE).toPx() }.roundToInt()
        val tileHpx = with(density) { (m.tileH * FOCUS_SCALE).toPx() }.roundToInt()
        val canFocus = controller.overlay == Overlay.NONE && controller.sheetFor == null
        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .graphicsLayer { translationY = -scroll.value }
                .onPlaced { if (contentH.floatValue != it.size.height.toFloat()) contentH.floatValue = it.size.height.toFloat() }
                .padding(horizontal = m.marginH),
        ) {
            Spacer(Modifier.height(m.shelfH))
            rows.forEachIndexed { ri, row ->
                key(row.id) {
                    Column(
                        Modifier.onPlaced {
                            val y = it.positionInParent().y
                            if (rowTops[ri] != y) rowTops[ri] = y
                        },
                    ) {
                        if (row.header != null) {
                            Text(row.header, style = Type.header, modifier = Modifier.padding(bottom = 12.dp, top = 4.dp))
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(m.gap),
                            modifier = if (row.kind == RowKind.DOCK) Modifier.drawBehind {
                                // A faint glass shelf behind the favorites row, like a dock.
                                val p = 14.dp.toPx()
                                drawRoundRect(
                                    Color.White.copy(alpha = 0.07f),
                                    topLeft = Offset(-p, -p),
                                    size = Size(size.width + 2 * p, m.tileH.toPx() + 2 * p),
                                    cornerRadius = CornerRadius(TileCorner.toPx() + p),
                                )
                            } else Modifier,
                        ) {
                            row.items.forEachIndexed { ci, item ->
                                key(item.key) {
                                    val img = rememberImage(app.images.tileKey(item, tileWpx, tileHpx)) {
                                        app.images.tile(item, tileWpx, tileHpx)
                                    }
                                    val isApp = item is GridItem.App
                                    // Banner tiles get a label under the focused tile; generated
                                    // tiles already carry their name inside the tile.
                                    val showLabel = item is GridItem.App && item.app.hasBanner
                                    ShelfTile(
                                        image = img,
                                        label = if (showLabel) item.label else null,
                                        width = m.tileW,
                                        height = m.tileH,
                                        requester = req(item.key),
                                        motion = motion,
                                        editing = controller.editKey == item.key,
                                        canFocus = canFocus,
                                        onFocused = {
                                            controller.focusedKey = item.key
                                            session.lastFocusedKey = item.key
                                            session.lastRow = ri; session.lastCol = ci
                                        },
                                        onClick = {
                                            if (controller.editKey != null) controller.drop() else controller.open(item)
                                        },
                                        onLongPress = {
                                            if (isApp) {
                                                if (controller.editKey == item.key) controller.sheetFor = item.key
                                                else if (controller.editKey == null) controller.startEdit(item.key)
                                            }
                                        },
                                        onMenu = {
                                            if (isApp) {
                                                if (controller.editKey == null) controller.startEdit(item.key)
                                                controller.sheetFor = item.key
                                            }
                                        },
                                        onEditMove = { d -> controller.move(d, arranged, settings.columns) },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(m.labelSpace))
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }

        // ---- scrim + clock ----
        Box(
            Modifier
                .fillMaxWidth()
                .height(m.safeTop + 70.dp)
                .graphicsLayer { alpha = (scroll.value / (shelfPx * 0.5f)).coerceIn(0f, 1f) }
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))),
        )
        if (settings.showClock) {
            GlassClock(Modifier.align(Alignment.TopCenter).padding(top = m.safeTop))
        }
    }
}

@Composable
private fun TopShelf(item: GridItem?, m: Metrics, editing: Boolean) {
    val app = LocalContext.current.applicationContext as ShelfApp
    val density = LocalDensity.current
    val heroWpx = with(density) { m.heroW.toPx() }.roundToInt()
    val heroHpx = with(density) { m.heroH.toPx() }.roundToInt()
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = item, animationSpec = tween(380), label = "shelf") { it ->
            if (it == null) return@Crossfade
            Box(Modifier.fillMaxSize()) {
                val wash = rememberImage(app.images.washKey(it)) { app.images.wash(it) }
                if (wash != null) {
                    Image(
                        wash, null, contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Low,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f },
                    )
                }
                Row(
                    Modifier.padding(start = m.marginH, top = m.safeTop + 30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val hero = rememberImage(app.images.heroKey(it, heroWpx, heroHpx)) { app.images.hero(it, heroWpx, heroHpx) }
                    Box(
                        Modifier
                            .size(m.heroW, m.heroH)
                            .graphicsLayer {
                                shape = RoundedCornerShape(16.dp); clip = true
                                shadowElevation = 20.dp.toPx()
                            },
                    ) {
                        if (hero != null) Image(hero, null, filterQuality = FilterQuality.Low, modifier = Modifier.fillMaxSize())
                    }
                    Column(Modifier.padding(start = 36.dp).width(m.screenW * 0.42f)) {
                        Text(it.label, style = Type.shelfTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val hint = when {
                            editing -> "D-pad to move · OK to drop · Back to cancel · Hold OK for options"
                            it is GridItem.App -> "Hold OK to rearrange, hide, or add to favorites"
                            it is GridItem.Input -> "TV input"
                            else -> null
                        }
                        if (hint != null) Text(hint, style = Type.shelfHint, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun Clock(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val text by produceState(format(ctx)) {
        while (true) {
            value = format(ctx)
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50)
        }
    }
    Text(text, style = Type.clock, modifier = modifier)
}

private fun format(ctx: android.content.Context): String =
    DateFormat.format(if (DateFormat.is24HourFormat(ctx)) "H:mm" else "h:mm", Date()).toString()
