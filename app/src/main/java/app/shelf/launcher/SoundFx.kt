package app.shelf.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/** Small, soft UI clicks (synthesised by tools/gen_sounds.py). Loaded once; playing is cheap. */
class SoundFx(ctx: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        ).build()
    private val tick = pool.load(ctx, R.raw.sfx_tick, 1)
    private val ok = pool.load(ctx, R.raw.sfx_ok, 1)
    private val back = pool.load(ctx, R.raw.sfx_back, 1)
    private val move = pool.load(ctx, R.raw.sfx_move, 1)

    private fun play(id: Int, vol: Float) { pool.play(id, vol, vol, 1, 0, 1f) }
    fun tick() = play(tick, 0.35f)
    fun ok() = play(ok, 0.45f)
    fun back() = play(back, 0.4f)
    /** Left/Right (switching category in Wave, sideways elsewhere). */
    fun move() = play(move, 0.4f)
}
