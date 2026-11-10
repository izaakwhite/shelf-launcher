package app.shelf.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

/**
 * Background music for the Wave style.
 * - Built-in loop (original, tools/gen_sounds.py): played from memory with AudioTrack loop points, so the
 *   loop is sample-accurate with no gap.
 * - The user's own file (/sdcard/Android/data/app.shelf.launcher/files/wave_music.*): MediaPlayer, looping.
 * Fades in and out, never starts while another app is playing audio, stops when Shelf leaves the screen.
 */
class WaveMusic(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var player: MediaPlayer? = null
    private var source: String? = null
    private var fadeToken = 0

    private val target = 0.32f

    private fun userFile(): File? = ctx.getExternalFilesDir(null)?.listFiles()
        ?.firstOrNull { it.isFile && it.name.lowercase().matches(Regex("wave_music\\.(mp3|ogg|m4a|aac|wav|flac)")) }

    private fun setVol(v: Float) {
        track?.setVolume(v)
        player?.setVolume(v, v)
    }

    private fun fade(from: Float, to: Float, ms: Long, done: (() -> Unit)? = null) {
        val token = ++fadeToken
        val steps = 30
        for (i in 0..steps) {
            main.postDelayed({
                if (token == fadeToken) {
                    setVol(from + (to - from) * i / steps)
                    if (i == steps) done?.invoke()
                }
            }, ms * i / steps)
        }
    }

    fun start() {
        val file = userFile()
        val wanted = file?.let { "${it.path}:${it.lastModified()}" } ?: "builtin"
        if ((track != null || player != null) && source == wanted) return
        release()
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (am.isMusicActive) return // e.g. Spotify playing in the background: don't talk over it
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        try {
            if (file == null) {
                val bytes = ctx.resources.openRawResource(R.raw.wave_ambient).use { it.readBytes() }
                val pcm = bytes.copyOfRange(44, bytes.size)               // skip the 44-byte WAV header
                val t = AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(
                        AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(22050).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size)
                    .build()
                t.write(pcm, 0, pcm.size)
                t.setLoopPoints(0, pcm.size / 2, -1)
                t.setVolume(0f)
                t.play()
                track = t
            } else {
                val p = MediaPlayer()
                p.setAudioAttributes(attrs)
                p.setDataSource(file.path)
                p.isLooping = true
                p.setVolume(0f, 0f)
                p.setOnPreparedListener { it.start() }
                p.prepareAsync()
                player = p
            }
            source = wanted
            fade(0f, target, 1800)
        } catch (e: Exception) {
            Log.w("ShelfMusic", "Can't play wave music", e)
            release()
        }
    }

    fun stop() {
        if (track == null && player == null) return
        fade(target, 0f, 350) { release() }
    }

    private fun release() {
        fadeToken++
        track?.runCatching { stop(); release() }
        player?.runCatching { stop(); release() }
        track = null; player = null; source = null
    }
}
