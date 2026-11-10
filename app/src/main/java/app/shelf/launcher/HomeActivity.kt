package app.shelf.launcher

import android.annotation.SuppressLint
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import android.util.Log
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.launch

class HomeActivity : ComponentActivity() {
    private val app get() = application as ShelfApp
    private var windowFocused = false
    /** Mirrors the current settings / controller for the key-sound hook below. */
    var settingsNow: Settings? = null
    var controllerNow: HomeController? = null

    // Both are false positives: we only *observe* keys to play a click (Back isn't intercepted; TVs have no
    // back gesture), and calling super.dispatchKeyEvent on an androidx ComponentActivity is flagged by mistake.
    @SuppressLint("GestureBackNavigation", "RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val s = settingsNow
        if (s?.sounds == true && event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val c = controllerNow
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN -> app.sounds.tick()
                android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> app.sounds.tick()
                android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> app.sounds.ok()
                android.view.KeyEvent.KEYCODE_BACK ->
                    if (c != null && (c.editKey != null || c.sheetFor != null || c.overlay != Overlay.NONE)) app.sounds.back()
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    ShelfRoot(app, this@HomeActivity)
                    SplashOverlay(app.session)
                }
            }
        }
        // Compose paints every pixel; the window background would be a second full-screen fill per frame.
        window.decorView.post { window.setBackgroundDrawable(null) }
        applyThemeAlias(intent)
    }

    /** Launched from one of the "Shelf <theme>" menu entries: switch to that theme. */
    private fun applyThemeAlias(intent: Intent?) {
        val style = when (intent?.component?.className?.substringAfterLast('.')) {
            "ThemeAero" -> STYLE_AERO
            "ThemeWave" -> STYLE_WAVE
            "ThemeGrid" -> STYLE_GRID
            else -> return
        }
        lifecycleScope.launch { app.prefs.updateSettings { s -> if (style == STYLE_AERO && s.carousel) s else s.copy(style = style) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyThemeAlias(intent)
        // Home pressed while we're already in front → jump back to the top of the grid.
        // (When returning from another app the intent is "brought to front" and we just restore focus.)
        val alreadyOnHome = windowFocused &&
            (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
        if (intent.hasCategory(Intent.CATEGORY_HOME) && alreadyOnHome) app.session.homeTick.intValue++
    }

    /** Wave music runs only while Shelf is on screen in the Wave style with music enabled. */
    private var resumed = false
    fun updateMusic() {
        val s = settingsNow
        if (resumed && s != null && s.style == STYLE_WAVE && s.music) app.music.start() else app.music.stop()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        app.music.stop()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        window.decorView.postDelayed({ updateMusic() }, 600)
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
    // TV inputs register late after boot (TCL's input service starts after the launcher), so reload
    // on every resume and retry a few times while the list is still empty.
    var inputs by remember { mutableStateOf(emptyList<InputEntry>()) }
    val resumeTick = app.session.resumeTick.intValue
    LaunchedEffect(resumeTick) {
        for (wait in listOf(0L, 3_000L, 10_000L, 30_000L)) {
            delay(wait)
            val loaded = withContext(Dispatchers.IO) { app.inputs.load() }
            if (loaded != inputs) inputs = loaded
            if (loaded.isNotEmpty()) break
        }
    }

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

    // Marks "interactive": apps + settings + layout are loaded and the home content is composed.
    LaunchedEffect(Unit) { withFrameNanos { }; activity.reportFullyDrawn(); app.session.homeReady.value = true }
    BackHandler(enabled = true) { controller.back() }
    activity.settingsNow = s
    activity.controllerNow = controller
    LaunchedEffect(s.style, s.music) { activity.updateMusic() }
    // One log line per UI state change; the end-to-end tests (e2e/run.sh) read these from logcat.
    LaunchedEffect(s.style) { Log.i("ShelfState", "style=${s.style}") }
    // Also log the state each time Shelf comes to the front, so e2e tests can read it even if nothing changed.
    LaunchedEffect(resumeTick) {
        delay(600)
        Log.i("ShelfState", "focus=${controller.focusedKey} overlay=${controller.overlay} edit=${controller.editKey}")
    }
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(controller.focusedKey, controller.overlay, controller.editKey) }.collect { (f, o, e) ->
            Log.i("ShelfState", "focus=$f overlay=$o edit=$e")
        }
    }

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
                onLicenses = { controller.overlay = Overlay.LICENSES },
            )
            Overlay.LICENSES -> LicensesScreen()
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
