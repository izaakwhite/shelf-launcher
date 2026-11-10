package app.shelf.launcher

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-wide singletons. The launcher process is long-lived, so caches survive activity recreation. */
class ShelfApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs by lazy { ShelfPrefs(this) }
    val apps by lazy { AppRepository(this, scope) }
    val images by lazy { ImageLoader(this) }
    val inputs by lazy { InputRepository(this) }
    val wallpaper by lazy { WallpaperRepository(this) }
    val session = Session()

    override fun onCreate() {
        super.onCreate()
        apps.start()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) images.trim()
    }
}
