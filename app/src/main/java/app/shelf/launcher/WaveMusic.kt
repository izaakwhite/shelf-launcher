package app.shelf.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * Background music for the Wave style. Plays the user's own file if present
 * (/sdcard/Android/data/app.shelf.launcher/files/wave_music.(mp3|ogg|m4a|wav)), otherwise an original
 * synthesized ambient loop. Never starts while another app is playing audio; stops when Shelf leaves the screen.
 */
class WaveMusic(private val ctx: Context) {
    private var player: MediaPlayer? = null
    private var source: String? = null

    private fun userFile(): File? = ctx.getExternalFilesDir(null)?.listFiles()
        ?.firstOrNull { it.isFile && it.name.lowercase().matches(Regex("wave_music\\.(mp3|ogg|m4a|aac|wav|flac)")) }

    fun start() {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val file = userFile()
        val wanted = file?.let { "${it.path}:${it.lastModified()}" } ?: "builtin"
        player?.let { p ->
            if (source == wanted) { if (!p.isPlaying) p.start(); return }
            stop()
        }
        if (am.isMusicActive) return // e.g. Spotify still playing in the background: don't talk over it
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
            )
            if (file != null) p.setDataSource(file.path)
            else p.setDataSource(ctx, Uri.parse("android.resource://${ctx.packageName}/${R.raw.wave_ambient}"))
            p.isLooping = true
            p.setVolume(0.35f, 0.35f)
            p.setOnPreparedListener { it.start() }
            p.prepareAsync()
            player = p; source = wanted
        } catch (e: Exception) {
            Log.w("ShelfMusic", "Can't play wave music", e)
            stop()
        }
    }

    fun stop() {
        player?.runCatching { stop(); release() }
        player = null; source = null
    }
}
