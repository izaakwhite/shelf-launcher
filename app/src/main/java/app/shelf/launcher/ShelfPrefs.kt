package app.shelf.launcher

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("shelf")

data class Settings(
    val columns: Int = 5,
    val showClock: Boolean = true,
    val showInputs: Boolean = true,
    val reduceMotion: Boolean = false,
    val carousel: Boolean = true,
)

data class Layout(
    val favorites: List<String> = emptyList(),
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val seeded: Boolean = false,
)

class ShelfPrefs(private val ctx: Context) {
    private object K {
        val columns = intPreferencesKey("columns")
        val clock = booleanPreferencesKey("show_clock")
        val inputs = booleanPreferencesKey("show_inputs")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val carousel = booleanPreferencesKey("carousel")
        val favorites = stringPreferencesKey("favorites")
        val order = stringPreferencesKey("order")
        val hidden = stringSetPreferencesKey("hidden")
        val seeded = booleanPreferencesKey("seeded")
    }

    val settings: Flow<Settings> = ctx.store.data.map { p ->
        Settings(
            columns = p[K.columns] ?: 5,
            showClock = p[K.clock] ?: true,
            showInputs = p[K.inputs] ?: true,
            reduceMotion = p[K.reduceMotion] ?: false,
            carousel = p[K.carousel] ?: true,
        )
    }.distinctUntilChanged()

    val layout: Flow<Layout> = ctx.store.data.map { p ->
        Layout(
            favorites = p[K.favorites].split(),
            order = p[K.order].split(),
            hidden = p[K.hidden] ?: emptySet(),
            seeded = p[K.seeded] ?: false,
        )
    }.distinctUntilChanged()

    suspend fun saveLayout(l: Layout) {
        ctx.store.edit { p ->
            p[K.favorites] = l.favorites.joinToString("\n")
            p[K.order] = l.order.joinToString("\n")
            p[K.hidden] = l.hidden
            p[K.seeded] = l.seeded
        }
    }

    suspend fun updateSettings(f: (Settings) -> Settings) {
        ctx.store.edit { p ->
            val cur = Settings(
                p[K.columns] ?: 5, p[K.clock] ?: true, p[K.inputs] ?: true, p[K.reduceMotion] ?: false,
                p[K.carousel] ?: true,
            )
            val n = f(cur)
            p[K.columns] = n.columns
            p[K.clock] = n.showClock
            p[K.inputs] = n.showInputs
            p[K.reduceMotion] = n.reduceMotion
            p[K.carousel] = n.carousel
        }
    }

    private fun String?.split(): List<String> = this?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    @Suppress("unused") private fun Preferences.dummy() = Unit
}
