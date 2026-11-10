package app.shelf.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lists launchable apps: Leanback (TV) entries first, then phone-style LAUNCHER entries for
 * packages that have no TV entry. Refreshes on package install/remove/update broadcasts.
 */
class AppRepository(private val ctx: Context, private val scope: CoroutineScope) {
    private val _apps = MutableStateFlow<List<AppEntry>?>(null)
    val apps: StateFlow<List<AppEntry>?> = _apps
    private var pending: Job? = null

    fun start() {
        refresh(0)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ctx.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = refresh(400)
        }, filter)
    }

    /** Debounced: an update fires REMOVED+ADDED+REPLACED in quick succession. */
    fun refresh(delayMs: Long) {
        pending?.cancel()
        pending = scope.launch {
            if (delayMs > 0) delay(delayMs)
            _apps.value = withContext(Dispatchers.IO) { query() }
        }
    }

    private fun query(): List<AppEntry> {
        val pm = ctx.packageManager
        val seen = HashSet<String>()
        val out = ArrayList<AppEntry>()
        fun collect(category: String, leanback: Boolean) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == ctx.packageName) continue
                if (!seen.add(ai.packageName)) continue
                val stamp = runCatching { pm.getPackageInfo(ai.packageName, 0).lastUpdateTime }.getOrDefault(0L)
                out += AppEntry(
                    key = ai.packageName + "/" + ai.name,
                    packageName = ai.packageName,
                    className = ai.name,
                    label = ri.loadLabel(pm).toString().trim().ifEmpty { ai.packageName },
                    leanback = leanback,
                    stamp = stamp,
                    hasBanner = ai.banner != 0 || (ai.applicationInfo?.banner ?: 0) != 0,
                )
            }
        }
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, true)
        collect(Intent.CATEGORY_LAUNCHER, false)
        return out.sortedBy { it.label.lowercase() }
    }
}
