package app.shelf.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class Overlay { NONE, SETTINGS, HIDDEN, LICENSES }

data class Arranged(val dock: List<AppEntry>, val main: List<AppEntry>)

/** Home-screen state and actions: ordering, favorites, hiding, edit ("jiggle") mode, launching. */
class HomeController(
    private val app: ShelfApp,
    private val activity: Activity,
    private val scope: CoroutineScope,
) {
    var layout by mutableStateOf(Layout())
    var layoutLoaded by mutableStateOf(false)
    var editKey by mutableStateOf<String?>(null)
        private set
    var sheetFor by mutableStateOf<String?>(null)
    var overlay by mutableStateOf(Overlay.NONE)
    var focusedKey by mutableStateOf<String?>(null)
    /** Bumped whenever the edited tile changes position, so the UI re-focuses it. */
    var editVersion by mutableIntStateOf(0)
        private set
    private var snapshot: Layout? = null

    fun onStoredLayout(l: Layout) {
        if (editKey == null) layout = l
        layoutLoaded = true
    }

    fun arrange(apps: List<AppEntry>, columns: Int): Arranged {
        val byKey = apps.associateBy { it.key }
        val l = layout
        val favs = l.favorites.mapNotNull { byKey[it] }.filter { it.key !in l.hidden }
        val dock = favs.take(columns)
        val overflow = favs.drop(columns)
        val used = HashSet<String>().apply { dock.forEach { add(it.key) }; overflow.forEach { add(it.key) } }
        val ordered = l.order.mapNotNull { byKey[it] }.filter { it.key !in used && it.key !in l.hidden }
        ordered.forEach { used += it.key }
        val rest = apps.filter { it.key !in used && it.key !in l.hidden }
        return Arranged(dock, overflow + ordered + rest)
    }

    /** First run: pick some favorites and tuck away TCL's internal utility activities. */
    fun seedIfNeeded(apps: List<AppEntry>, columns: Int) {
        if (!layoutLoaded || layout.seeded || apps.isEmpty()) return
        val preferred = listOf(
            "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.hulu.livingroomplus",
            "com.spotify.tv.android", "io.github.jqssun.airplay", "com.google.android.youtube.tv",
        )
        val favs = apps.filter { it.packageName in preferred }
            .sortedBy { preferred.indexOf(it.packageName) }
            .take(columns).map { it.key }
            .ifEmpty { apps.filter { it.leanback }.take(columns).map { it.key } }
        val utility = setOf(
            "com.tcl.tcl_bt_rcu_service", "com.tcl.initsetup", "com.tcl.useragreement",
            "com.tcl.rc.ota", "com.tcl.framework.custom",
        )
        val hidden = apps.filter { it.packageName in utility }.map { it.key }.toSet()
        layout = Layout(favorites = favs, order = emptyList(), hidden = hidden, seeded = true)
        persist()
    }

    private fun persist() {
        val l = layout
        scope.launch { app.prefs.saveLayout(l) }
    }

    // ---- edit mode ----
    fun startEdit(key: String) {
        snapshot = layout
        editKey = key
    }

    fun drop() {
        editKey = null; snapshot = null; sheetFor = null
        persist()
    }

    fun cancelEdit() {
        snapshot?.let { layout = it }
        editKey = null; snapshot = null; sheetFor = null
    }

    /** Moves the tile being edited one step. Returns false if it can't go that way. */
    fun move(dir: Dir, arranged: Arranged, columns: Int): Boolean {
        val key = editKey ?: return false
        val dock = arranged.dock.map { it.key }.toMutableList()
        val main = arranged.main.map { it.key }.toMutableList()
        val inDock = key in dock
        val list = if (inDock) dock else main
        val i = list.indexOf(key)
        if (i < 0) return false
        when (dir) {
            Dir.LEFT -> { if (i == 0) return false; list.removeAt(i); list.add(i - 1, key) }
            Dir.RIGHT -> { if (i == list.lastIndex) return false; list.removeAt(i); list.add(i + 1, key) }
            Dir.UP -> {
                if (inDock) return false
                if (i >= columns) { list.removeAt(i); list.add(i - columns, key) }
                else {
                    if (dock.size >= columns) return false
                    main.removeAt(i); dock.add(i.coerceAtMost(dock.size), key)
                }
            }
            Dir.DOWN -> {
                if (inDock) { dock.removeAt(i); main.add(i.coerceAtMost(main.size), key) }
                else {
                    if (i + columns > main.lastIndex && (i / columns) == (main.lastIndex / columns)) return false
                    list.removeAt(i); list.add((i + columns).coerceAtMost(list.size), key)
                }
            }
        }
        layout = layout.copy(favorites = dock, order = main)
        editVersion++
        return true
    }

    /** Carousel move: favourites are the front of one line; crossing the divider (un)favourites. */
    fun moveLinear(dir: Dir, arranged: Arranged, crossDivider: Boolean = true): Boolean {
        val key = editKey ?: return false
        if (!crossDivider) {
            val list = if (arranged.dock.any { it.key == key }) arranged.dock else arranged.main
            val i = list.indexOfFirst { it.key == key }
            if ((dir == Dir.LEFT && i == 0) || (dir == Dir.RIGHT && i == list.lastIndex)) return false
        }
        val dock = arranged.dock.map { it.key }.toMutableList()
        val main = arranged.main.map { it.key }.toMutableList()
        when (dir) {
            Dir.LEFT -> when {
                key in main -> {
                    val i = main.indexOf(key)
                    if (i == 0) { main.removeAt(0); dock.add(key) } else { main.removeAt(i); main.add(i - 1, key) }
                }
                else -> {
                    val i = dock.indexOf(key)
                    if (i <= 0) return false
                    dock.removeAt(i); dock.add(i - 1, key)
                }
            }
            Dir.RIGHT -> when {
                key in dock -> {
                    val i = dock.indexOf(key)
                    if (i == dock.lastIndex) { dock.removeAt(i); main.add(0, key) } else { dock.removeAt(i); dock.add(i + 1, key) }
                }
                else -> {
                    val i = main.indexOf(key)
                    if (i < 0 || i == main.lastIndex) return false
                    main.removeAt(i); main.add(i + 1, key)
                }
            }
            else -> return false
        }
        layout = layout.copy(favorites = dock, order = main)
        editVersion++
        return true
    }

    fun toggleFavorite(key: String, arranged: Arranged, columns: Int) {
        val dock = arranged.dock.map { it.key }.toMutableList()
        val main = arranged.main.map { it.key }.toMutableList()
        if (key in dock) {
            dock.remove(key); main.add(0, key)
        } else {
            main.remove(key)
            if (dock.size >= columns) main.add(0, dock.removeAt(dock.lastIndex))
            dock.add(key)
        }
        layout = layout.copy(favorites = dock, order = main)
        drop()
    }

    fun hide(key: String) {
        val l = layout
        layout = l.copy(favorites = l.favorites - key, order = l.order - key, hidden = l.hidden + key)
        drop()
    }

    fun unhide(key: String) {
        layout = layout.copy(hidden = layout.hidden - key)
        persist()
    }

    /** Back: cancel edit, close overlays, otherwise do nothing (this is the home screen). */
    fun back() {
        when {
            sheetFor != null -> sheetFor = null
            editKey != null -> cancelEdit()
            overlay != Overlay.NONE -> overlay = Overlay.NONE
        }
    }

    fun resetToTop() {
        if (editKey != null) cancelEdit()
        sheetFor = null
        overlay = Overlay.NONE
    }

    // ---- launching ----
    fun open(item: GridItem) {
        when (item) {
            is GridItem.App -> {
                val a = item.app
                start(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(if (a.leanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
                        .setComponent(ComponentName(a.packageName, a.className))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                )
            }
            is GridItem.Input -> if (!app.inputs.launch(activity, item.input)) toast("Couldn't switch to ${item.label}")
            is GridItem.System -> when (item.kind) {
                SystemKind.HIDDEN_APPS -> overlay = Overlay.HIDDEN
                SystemKind.SHELF_SETTINGS -> overlay = Overlay.SETTINGS
                SystemKind.ANDROID_SETTINGS -> start(Intent(AndroidSettings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun appInfo(pkg: String) {
        start(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun start(i: Intent) {
        try {
            activity.startActivity(i)
        } catch (e: ActivityNotFoundException) {
            Log.w("Shelf", "No activity for $i", e); toast("Can't open that")
        } catch (e: SecurityException) {
            Log.w("Shelf", "Not allowed: $i", e); toast("Can't open that")
        }
    }

    private fun toast(s: String) = Toast.makeText(activity, s, Toast.LENGTH_SHORT).show()
}
