package app.shelf.launcher

/**
 * Pure ordering rules for the home screen (no Android, no Compose state), so they can be unit-tested.
 * Every function takes the current [Layout] and returns the new one, or null when a move isn't possible.
 */
object LayoutLogic {

    /** Favourites (up to [dockCap]) first; overflow favourites, the saved order, then new apps A–Z. Hidden apps excluded. */
    fun arrange(apps: List<AppEntry>, l: Layout, dockCap: Int): Arranged {
        val byKey = apps.associateBy { it.key }
        val favs = l.favorites.mapNotNull { byKey[it] }.filter { it.key !in l.hidden }.distinctBy { it.key }
        val dock = favs.take(dockCap)
        val overflow = favs.drop(dockCap)
        val used = HashSet<String>().apply { favs.forEach { add(it.key) } }
        val ordered = l.order.mapNotNull { byKey[it] }.filter { it.key !in used && it.key !in l.hidden }.distinctBy { it.key }
        ordered.forEach { used += it.key }
        val rest = apps.filter { it.key !in used && it.key !in l.hidden }
        return Arranged(dock, overflow + ordered + rest)
    }

    val PREFERRED_FAVORITES = listOf(
        "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.hulu.livingroomplus",
        "com.spotify.tv.android", "io.github.jqssun.airplay", "com.google.android.youtube.tv",
    )
    val TCL_UTILITIES = setOf(
        "com.tcl.tcl_bt_rcu_service", "com.tcl.initsetup", "com.tcl.useragreement",
        "com.tcl.rc.ota", "com.tcl.framework.custom",
    )

    /** First-run layout: well-known streaming apps as favourites, TCL's internal utilities hidden. */
    fun seed(apps: List<AppEntry>, columns: Int): Layout {
        val favs = apps.filter { it.packageName in PREFERRED_FAVORITES }
            .sortedBy { PREFERRED_FAVORITES.indexOf(it.packageName) }
            .take(columns).map { it.key }
            .ifEmpty { apps.filter { it.leanback }.take(columns).map { it.key } }
        val hidden = apps.filter { it.packageName in TCL_UTILITIES }.map { it.key }.toSet()
        return Layout(favorites = favs, order = emptyList(), hidden = hidden, seeded = true)
    }

    /** Grid move: one step in [dir]; Up from the first app row joins the favourites row, Down leaves it. */
    fun move(l: Layout, key: String, dir: Dir, arranged: Arranged, columns: Int): Layout? {
        val dock = arranged.dock.map { it.key }.toMutableList()
        val main = arranged.main.map { it.key }.toMutableList()
        val inDock = key in dock
        val list = if (inDock) dock else main
        val i = list.indexOf(key)
        if (i < 0) return null
        when (dir) {
            Dir.LEFT -> { if (i == 0) return null; list.removeAt(i); list.add(i - 1, key) }
            Dir.RIGHT -> { if (i == list.lastIndex) return null; list.removeAt(i); list.add(i + 1, key) }
            Dir.UP -> {
                if (inDock) return null
                if (i >= columns) { list.removeAt(i); list.add(i - columns, key) }
                else {
                    if (dock.size >= columns) return null
                    main.removeAt(i); dock.add(i.coerceAtMost(dock.size), key)
                }
            }
            Dir.DOWN -> {
                if (inDock) { dock.removeAt(i); main.add(i.coerceAtMost(main.size), key) }
                else {
                    if (i / columns == main.lastIndex / columns) return null // already on the last row
                    list.removeAt(i); list.add((i + columns).coerceAtMost(list.size), key)
                }
            }
        }
        return l.copy(favorites = dock, order = main)
    }

    /**
     * Carousel / list move: favourites are the front of one line. With [crossDivider], moving past the end of the
     * favourites un-favourites the app (and vice versa); without it, the move stops at the boundary.
     */
    fun moveLinear(l: Layout, key: String, dir: Dir, arranged: Arranged, crossDivider: Boolean): Layout? {
        val dock = arranged.dock.map { it.key }.toMutableList()
        val main = arranged.main.map { it.key }.toMutableList()
        val inDock = key in dock
        if (!inDock && key !in main) return null
        if (!crossDivider) {
            val list = if (inDock) dock else main
            val i = list.indexOf(key)
            if ((dir == Dir.LEFT && i == 0) || (dir == Dir.RIGHT && i == list.lastIndex)) return null
        }
        when (dir) {
            Dir.LEFT -> if (!inDock) {
                val i = main.indexOf(key)
                if (i == 0) { main.removeAt(0); dock.add(key) } else { main.removeAt(i); main.add(i - 1, key) }
            } else {
                val i = dock.indexOf(key)
                if (i == 0) return null
                dock.removeAt(i); dock.add(i - 1, key)
            }
            Dir.RIGHT -> if (inDock) {
                val i = dock.indexOf(key)
                if (i == dock.lastIndex) { dock.removeAt(i); main.add(0, key) } else { dock.removeAt(i); dock.add(i + 1, key) }
            } else {
                val i = main.indexOf(key)
                if (i == main.lastIndex) return null
                main.removeAt(i); main.add(i + 1, key)
            }
            else -> return null
        }
        return l.copy(favorites = dock, order = main)
    }

    /** Favourite ↔ not. A full favourites row bumps its last app to the front of the rest. */
    fun toggleFavorite(l: Layout, key: String, arranged: Arranged, dockCap: Int): Layout {
        val dock = arranged.dock.map { it.key }.toMutableList()
        val main = arranged.main.map { it.key }.toMutableList()
        if (key in dock) {
            dock.remove(key); main.add(0, key)
        } else {
            main.remove(key)
            if (dock.size >= dockCap && dock.isNotEmpty()) main.add(0, dock.removeAt(dock.lastIndex))
            dock.add(key)
        }
        return l.copy(favorites = dock, order = main)
    }

    fun hide(l: Layout, key: String) = l.copy(favorites = l.favorites - key, order = l.order - key, hidden = l.hidden + key)
    fun unhide(l: Layout, key: String) = l.copy(hidden = l.hidden - key)

    /** Style cycle used by Shelf settings → Style. */
    val STYLE_ORDER = listOf(STYLE_AERO, STYLE_AERO_DUSK, STYLE_AERO_NIGHT, STYLE_WAVE, STYLE_GRID)
    fun nextStyle(style: String) = STYLE_ORDER[(STYLE_ORDER.indexOf(style) + 1).mod(STYLE_ORDER.size)]
}
