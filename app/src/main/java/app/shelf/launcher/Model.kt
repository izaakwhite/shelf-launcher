package app.shelf.launcher

import androidx.compose.runtime.mutableIntStateOf

data class AppEntry(
    val key: String,          // "package/activity"
    val packageName: String,
    val className: String,
    val label: String,
    val leanback: Boolean,
    val stamp: Long,          // lastUpdateTime, so cached bitmaps refresh after an update
    val hasBanner: Boolean,
)

data class InputEntry(
    val id: String,
    val label: String,
    val passthrough: Boolean,
)

enum class SystemKind { HIDDEN_APPS, ANDROID_SETTINGS, SHELF_SETTINGS }

/** Anything that can sit in the grid. */
sealed interface GridItem {
    val key: String
    val label: String

    data class App(val app: AppEntry) : GridItem {
        override val key get() = app.key
        override val label get() = app.label
    }

    data class Input(val input: InputEntry) : GridItem {
        override val key get() = "input:" + input.id
        override val label get() = input.label
    }

    data class System(val kind: SystemKind, override val label: String) : GridItem {
        override val key get() = "sys:" + kind.name
    }
}

enum class RowKind { DOCK, APPS, INPUTS, SYSTEM }

data class GridRow(val id: String, val kind: RowKind, val header: String?, val items: List<GridItem>)

/** UI-session state that outlives a single composition (e.g. after launching an app and coming back). */
class Session {
    var lastFocusedKey: String? = null
    var lastRow = 0
    var lastCol = 0
    var lastCarouselKey: String? = null
    /** Bumped when Home is pressed while already on the home screen. */
    val homeTick = mutableIntStateOf(0)
    /** Bumped on every onResume. */
    val resumeTick = mutableIntStateOf(0)
    /** The launch intro plays once per process start. */
    val splashDone = androidx.compose.runtime.mutableStateOf(false)
}
