package app.shelf.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Small, soft UI clicks (synthesised by tools/gen_sounds.py). Loaded once; playing is cheap.
 *
 * Personal override: put your own `sfx_tick.*`, `sfx_ok.*` or `sfx_back.*` (wav/mp3/ogg/m4a) in
 * /sdcard/Android/data/app.shelf.launcher/files/ and Shelf plays those instead. Nothing like that is bundled;
 * see scripts/private-sounds.sh.
 */
class SoundFx(ctx: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        ).build()
    private fun userFile(ctx: Context, name: String): String? = ctx.getExternalFilesDir(null)?.listFiles()
        ?.firstOrNull { it.isFile && it.name.lowercase().matches(Regex("$name\\.(wav|mp3|ogg|m4a|aac|flac)")) }?.path

    private fun load(ctx: Context, name: String, res: Int): Int =
        userFile(ctx, name)?.let { pool.load(it, 1) } ?: pool.load(ctx, res, 1)

    private val tick = load(ctx, "sfx_tick", R.raw.sfx_tick)
    private val ok = load(ctx, "sfx_ok", R.raw.sfx_ok)
    private val back = load(ctx, "sfx_back", R.raw.sfx_back)

    private fun play(id: Int, vol: Float) { pool.play(id, vol, vol, 1, 0, 1f) }
    fun tick() = play(tick, 0.35f)
    fun ok() = play(ok, 0.45f)
    fun back() = play(back, 0.4f)
}
