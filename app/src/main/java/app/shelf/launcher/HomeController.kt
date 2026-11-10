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

    fun arrange(apps: List<AppEntry>, columns: Int): Arranged = LayoutLogic.arrange(apps, layout, columns)

    /** First run: pick some favorites and tuck away TCL's internal utility activities. */
    fun seedIfNeeded(apps: List<AppEntry>, columns: Int) {
        if (!layoutLoaded || layout.seeded || apps.isEmpty()) return
        layout = LayoutLogic.seed(apps, columns)
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
    fun move(dir: Dir, arranged: Arranged, columns: Int): Boolean =
        applyEdit(editKey?.let { LayoutLogic.move(layout, it, dir, arranged, columns) })

    /** Carousel / list move (favourites are the front of the line). */
    fun moveLinear(dir: Dir, arranged: Arranged, crossDivider: Boolean = true): Boolean =
        applyEdit(editKey?.let { LayoutLogic.moveLinear(layout, it, dir, arranged, crossDivider) })

    private fun applyEdit(next: Layout?): Boolean {
        if (next == null) return false
        layout = next
        editVersion++
        return true
    }

    fun toggleFavorite(key: String, arranged: Arranged, columns: Int) {
        layout = LayoutLogic.toggleFavorite(layout, key, arranged, columns)
        drop()
    }

    fun hide(key: String) {
        layout = LayoutLogic.hide(layout, key)
        drop()
    }

    fun unhide(key: String) {
        layout = LayoutLogic.unhide(layout, key)
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
