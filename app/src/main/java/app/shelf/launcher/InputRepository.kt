package app.shelf.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.util.Log

/** Physical TV inputs (HDMI etc.). Best effort: vendors implement this unevenly. */
class InputRepository(private val ctx: Context) {

    fun load(): List<InputEntry> = runCatching {
        val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        tim.tvInputList
            .filter { !it.isHidden(ctx) && isHardware(it) }
            .map { info ->
                val label = (info.loadCustomLabel(ctx) ?: info.loadLabel(ctx))?.toString()?.trim()
                InputEntry(info.id, label.takeUnless { it.isNullOrEmpty() } ?: "Input", info.isPassthroughInput)
            }
            .sortedWith(compareBy({ if (it.passthrough) 0 else 1 }, { it.label }))
    }.onFailure { Log.w(TAG, "TvInputManager failed", it) }.getOrDefault(emptyList())

    // Passthrough inputs (HDMI, AV) plus hardware-backed tuners; skips app-provided "inputs" like
    // video-preview services that some apps register.
    private fun isHardware(info: TvInputInfo) =
        info.isPassthroughInput || (info.type == TvInputInfo.TYPE_TUNER && info.id.contains("/HW"))

    fun launch(activity: Activity, input: InputEntry): Boolean {
        val uri = if (input.passthrough) TvContract.buildChannelUriForPassthroughInput(input.id)
        else TvContract.buildChannelsUriForInput(input.id)
        val attempts = listOf(
            Intent(Intent.ACTION_VIEW, uri),
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, TvContract.Channels.CONTENT_ITEM_TYPE).setPackage(TCL_TV),
            Intent(Intent.ACTION_MAIN).setClassName(TCL_TV, "$TCL_TV.TVActivity").setData(uri),
        )
        for (intent in attempts) {
            try {
                activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Log.i(TAG, "Launched input ${input.id} via $intent")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Input launch attempt failed: $intent", e)
            }
        }
        return false
    }

    companion object {
        private const val TAG = "ShelfInputs"
        const val TCL_TV = "com.tcl.tv"
    }
}
