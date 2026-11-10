package app.shelf.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.launch

class HomeActivity : ComponentActivity() {
    private val app get() = application as ShelfApp
    private var windowFocused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { ShelfRoot(app, this) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while we're already in front → jump back to the top of the grid.
        // (When returning from another app the intent is "brought to front" and we just restore focus.)
        val alreadyOnHome = windowFocused &&
            (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
        if (intent.hasCategory(Intent.CATEGORY_HOME) && alreadyOnHome) app.session.homeTick.intValue++
    }

    override fun onResume() {
        super.onResume()
        app.session.resumeTick.intValue++
        lifecycleScope.launch { app.backdrop.refresh() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        windowFocused = hasFocus
    }
}

@Composable
private fun ShelfRoot(app: ShelfApp, activity: HomeActivity) {
    val scope = rememberCoroutineScope()
    val controller = remember { HomeController(app, activity, scope) }
    val apps by app.apps.apps.collectAsState()
    val settings by app.prefs.settings.collectAsState(initial = null)
    val backdrop by app.backdrop.state.collectAsState()
    val inputs = remember { app.inputs.load() }

    LaunchedEffect(Unit) { app.prefs.layout.collect { controller.onStoredLayout(it) } }

    val s = settings ?: return
    val list = apps ?: return
    if (!controller.layoutLoaded) return
    if (!controller.layout.seeded) {
        LaunchedEffect(list) { controller.seedIfNeeded(list, s.columns) }
        return
    }

    // In the carousel favourites are just the front of the line, so allow more of them.
    val dockCap = if (s.style == STYLE_GRID) s.columns else 12
    val arranged = remember(list, controller.layout, dockCap) { controller.arrange(list, dockCap) }
    val rows = remember(arranged, s.columns, s.showInputs, inputs, controller.layout.hidden.size) {
        buildList {
            if (arranged.dock.isNotEmpty()) add(GridRow("dock", RowKind.DOCK, null, arranged.dock.map { GridItem.App(it) }))
            arranged.main.chunked(s.columns).forEachIndexed { i, c ->
                add(GridRow("apps$i", RowKind.APPS, null, c.map { GridItem.App(it) }))
            }
            if (s.showInputs && inputs.isNotEmpty()) {
                inputs.chunked(s.columns).forEachIndexed { i, c ->
                    add(GridRow("inputs$i", RowKind.INPUTS, if (i == 0) "Inputs" else null, c.map { GridItem.Input(it) }))
                }
            }
            add(
                GridRow(
                    "system", RowKind.SYSTEM, null,
                    listOf(
                        GridItem.System(SystemKind.HIDDEN_APPS, "Hidden Apps"),
                        GridItem.System(SystemKind.ANDROID_SETTINGS, "Settings"),
                        GridItem.System(SystemKind.SHELF_SETTINGS, "Shelf"),
                    ),
                ),
            )
        }
    }

    BackHandler(enabled = true) { controller.back() }

    val carouselApps = remember(arranged) { (arranged.dock + arranged.main).map { GridItem.App(it) } }
    val pills = remember(inputs, s.showInputs) {
        (if (s.showInputs) inputs.map { GridItem.Input(it) } else emptyList()) + listOf(
            GridItem.System(SystemKind.HIDDEN_APPS, "Hidden Apps"),
            GridItem.System(SystemKind.ANDROID_SETTINGS, "Settings"),
            GridItem.System(SystemKind.SHELF_SETTINGS, "Shelf"),
        )
    }

    LaunchedEffect(s.style) {
        app.backdrop.palette = AeroArt.palette(s.style)
        app.backdrop.refresh()
    }
    val waveCats = remember(arranged, inputs, s.showInputs) {
        buildList {
            if (arranged.dock.isNotEmpty()) add(WaveCategory("fav", "Favorites", CatIcon.STAR, arranged.dock.map { GridItem.App(it) }))
            add(WaveCategory("apps", "Apps", CatIcon.APPS, arranged.main.map { GridItem.App(it) }))
            if (s.showInputs && inputs.isNotEmpty()) add(WaveCategory("tv", "TV", CatIcon.TV, inputs.map { GridItem.Input(it) }))
            add(
                WaveCategory(
                    "settings", "Settings", CatIcon.SETTINGS,
                    listOf(
                        GridItem.System(SystemKind.SHELF_SETTINGS, "Shelf settings"),
                        GridItem.System(SystemKind.ANDROID_SETTINGS, "Android settings"),
                        GridItem.System(SystemKind.HIDDEN_APPS, "Hidden apps"),
                    ),
                ),
            )
        }
    }

    // The Wave style has its own backdrop; glass panels there just use their tint.
    CompositionLocalProvider(LocalBackdrop provides if (s.style == STYLE_WAVE) null else backdrop) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (s.style == STYLE_WAVE) {
            WaveScreen(controller, waveCats, arranged, s, app.session)
        } else if (s.carousel) {
            CarouselScreen(controller, carouselApps, arranged.dock.size, pills, arranged, s, backdrop, app.session)
        } else {
            HomeScreen(controller, rows, arranged, s, backdrop, app.session)
        }

        val m = remember(maxWidth, maxHeight, s.columns) { Metrics(maxWidth, maxHeight, s.columns) }
        when (controller.overlay) {
            Overlay.SETTINGS -> SettingsPanel(
                settings = s,
                hiddenCount = controller.layout.hidden.count { k -> list.any { it.key == k } },
                wallpaperPath = app.backdrop.dir()?.path ?: "/sdcard/Android/data/${app.packageName}/files",
                onChange = { f -> scope.launch { app.prefs.updateSettings(f) } },
                onHidden = { controller.overlay = Overlay.HIDDEN },
                onAndroidSettings = { controller.open(GridItem.System(SystemKind.ANDROID_SETTINGS, "Settings")) },
            )
            Overlay.HIDDEN -> HiddenAppsScreen(
                hidden = list.filter { it.key in controller.layout.hidden },
                m = m, motion = !s.reduceMotion,
                onUnhide = { controller.unhide(it.key) },
            )
            Overlay.NONE -> Unit
        }

        val sheetKey = controller.sheetFor
        val sheetApp = sheetKey?.let { k -> list.firstOrNull { it.key == k } }
        if (sheetApp != null) {
            EditSheet(
                app = sheetApp,
                isFavorite = arranged.dock.any { it.key == sheetApp.key },
                onFavorite = { controller.toggleFavorite(sheetApp.key, arranged, dockCap) },
                onHide = { controller.hide(sheetApp.key) },
                onInfo = { controller.drop(); controller.appInfo(sheetApp.packageName) },
                onCancel = { controller.sheetFor = null },
            )
        }
    }
    }
}
