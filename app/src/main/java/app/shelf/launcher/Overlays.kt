package app.shelf.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlin.math.roundToInt

/** Long-press (in edit mode) / Menu sheet for one app. */
@Composable
fun EditSheet(
    app: AppEntry,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    onHide: () -> Unit,
    onInfo: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
        Column(
            Modifier
                .width(380.dp)
                .background(Color(0xF0102A44), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(app.label, style = Type.panelTitle, modifier = Modifier.padding(bottom = 8.dp))
            SheetButton(if (isFavorite) "Remove from Favorites" else "Move to Favorites", onFavorite, Modifier.focusRequester(first))
            SheetButton("Hide", onHide)
            SheetButton("App Info", onInfo)
            SheetButton("Cancel", onCancel)
        }
    }
}

@Composable
private fun SheetButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Text(text, style = Type.body)
    }
}

/** Small in-launcher settings panel, slides in from the right like tvOS. */
@Composable
fun SettingsPanel(
    settings: Settings,
    hiddenCount: Int,
    wallpaperPath: String,
    onChange: ((Settings) -> Settings) -> Unit,
    onHidden: () -> Unit,
    onAndroidSettings: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(460.dp)
                .glass(0.dp, tint = Color(0xC8061A2E))
                .padding(horizontal = 32.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Shelf", style = Type.panelTitle, modifier = Modifier.padding(bottom = 16.dp))
            Row("Style", styleName(settings.style), Modifier.focusRequester(first)) {
                onChange {
                    val order = listOf(STYLE_AERO, STYLE_AERO_DUSK, STYLE_AERO_NIGHT, STYLE_WAVE, STYLE_GRID)
                    it.copy(style = order[(order.indexOf(it.style) + 1) % order.size])
                }
            }
            if (settings.carousel) {
                Row("Icon size", if (settings.columns == 5) "Large" else "Compact") {
                    onChange { it.copy(columns = if (it.columns == 5) 6 else 5) }
                }
            } else if (settings.style == STYLE_GRID) {
                Row("Grid columns", "${settings.columns}") { onChange { it.copy(columns = if (it.columns == 5) 6 else 5) } }
            }
            Row("Clock", onOff(settings.showClock)) { onChange { it.copy(showClock = !it.showClock) } }
            Row("Inputs row", onOff(settings.showInputs)) { onChange { it.copy(showInputs = !it.showInputs) } }
            Row("Click sounds", onOff(settings.sounds)) { onChange { it.copy(sounds = !it.sounds) } }
            if (settings.style == STYLE_WAVE) Row("Wave music", onOff(settings.music)) { onChange { it.copy(music = !it.music) } }
            Row("Reduce motion", onOff(settings.reduceMotion)) { onChange { it.copy(reduceMotion = !it.reduceMotion) } }
            Row("Hidden apps", "$hiddenCount", onClick = onHidden)
            Row("Android settings", "›", onClick = onAndroidSettings)
            Text(
                "Wallpaper: push an image to\n$wallpaperPath/wallpaper.jpg",
                style = Type.small, color = Palette.textDim, modifier = Modifier.padding(top = 18.dp),
            )
            Text("Back to close", style = Type.small, color = Palette.textDim, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

private fun onOff(b: Boolean) = if (b) "On" else "Off"

private fun styleName(s: String) = when (s) {
    STYLE_AERO_DUSK -> "Aero Dusk"; STYLE_AERO_NIGHT -> "Aero Night"
    STYLE_WAVE -> "Wave"; STYLE_GRID -> "Classic grid"; else -> "Aero Aqua"
}

@Composable
private fun Row(title: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier,
        headlineContent = { Text(title, style = Type.body) },
        trailingContent = { Text(value, style = Type.body) },
    )
}

/** Grid of hidden apps; OK unhides. */
@Composable
fun HiddenAppsScreen(hidden: List<AppEntry>, m: Metrics, motion: Boolean, onUnhide: (AppEntry) -> Unit) {
    val app = LocalContext.current.applicationContext as ShelfApp
    val density = LocalDensity.current
    val requesters = remember(hidden) { hidden.associate { it.key to FocusRequester() } }
    LaunchedEffect(hidden.firstOrNull()?.key) {
        withFrameNanos { }
        hidden.firstOrNull()?.let { runCatching { requesters[it.key]?.requestFocus() } }
    }
    Box(Modifier.fillMaxSize().glass(0.dp, tint = Color(0xD8061626), sheen = 0f)) {
        Column(Modifier.padding(horizontal = m.marginH, vertical = m.safeTop)) {
            Text("Hidden Apps", style = Type.panelTitle)
            Text(
                if (hidden.isEmpty()) "No hidden apps. Back to return." else "OK to unhide · Back to return",
                style = Type.shelfHint, modifier = Modifier.padding(top = 6.dp, bottom = 28.dp),
            )
            val wpx = with(density) { (m.tileW * FOCUS_SCALE).toPx() }.roundToInt()
            val hpx = with(density) { (m.tileH * FOCUS_SCALE).toPx() }.roundToInt()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(m.gap), verticalArrangement = Arrangement.spacedBy(m.labelSpace)) {
                hidden.forEach { a ->
                    val item = GridItem.App(a)
                    val img = rememberImage(app.images.tileKey(item, wpx, hpx)) { app.images.tile(item, wpx, hpx) }
                    ShelfTile(
                        image = img, label = if (a.hasBanner) a.label else null,
                        width = m.tileW, height = m.tileH, requester = requesters.getValue(a.key),
                        motion = motion, editing = false, canFocus = true,
                        onFocused = {}, onClick = { onUnhide(a) }, onLongPress = {}, onMenu = {},
                        onEditMove = { false },
                    )
                }
            }
        }
    }
}
