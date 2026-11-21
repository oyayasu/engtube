package com.oyayasu.engtube.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class WordIpa(val uk: String?, val us: String?)
data class IpaResult(val uk: String, val us: String, val missing: List<String>)

object Pronunciation {
    private val cache = ConcurrentHashMap<String, WordIpa>()
    private val wordPattern = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?")

    suspend fun phrase(text: String): IpaResult = coroutineScope {
        val words = wordPattern.findAll(text).map { it.value }.toList().take(32)
        val results = words.distinctBy { it.lowercase(Locale.ROOT) }.map { word ->
            async(Dispatchers.IO) { word.lowercase(Locale.ROOT) to lookup(word) }
        }.awaitAll().toMap()
        val missing = words.filter {
            val ipa = results[it.lowercase(Locale.ROOT)]
            ipa?.uk == null || ipa.us == null
        }.distinct()
        fun assemble(accent: String): String {
            if (words.isEmpty()) return ""
            return "/" + words.joinToString(" ") { word ->
                val ipa = results[word.lowercase(Locale.ROOT)]
                (if (accent == "UK") ipa?.uk else ipa?.us)?.trim('/') ?: "?"
            } + "/"
        }
        IpaResult(assemble("UK"), assemble("US"), missing)
    }

    private fun lookup(word: String): WordIpa {
        val key = word.lowercase(Locale.ROOT).replace('’', '\'')
        cache[key]?.let { return it }
        val parsed = runCatching {
            val url = URL("https://api.dictionaryapi.dev/api/v2/entries/en/" +
                URLEncoder.encode(key, "UTF-8"))
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 4500
                connection.readTimeout = 4500
                connection.setRequestProperty("Accept", "application/json")
                if (connection.responseCode !in 200..299) return@runCatching WordIpa(null, null)
                val data = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
                var uk: String? = null
                var us: String? = null
                for (i in 0 until data.length()) {
                    val phonetics = data.getJSONObject(i).optJSONArray("phonetics") ?: continue
                    for (j in 0 until phonetics.length()) {
                        val item = phonetics.getJSONObject(j)
                        val audio = item.optString("audio").lowercase(Locale.ROOT)
                        val ipa = item.optString("text").takeIf { it.isNotBlank() } ?: continue
                        // Unlabelled phonetics must NOT be presented as UK/US-specific.
                        if (audio.contains("-uk.") || audio.contains("/uk/") || audio.contains("en-gb")) uk = uk ?: ipa
                        if (audio.contains("-us.") || audio.contains("/us/") || audio.contains("en-us")) us = us ?: ipa
                    }
                }
                WordIpa(uk, us)
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(WordIpa(null, null))
        cache[key] = parsed
        return parsed
    }
}

/** Playback of user-selected local audio files only. No YouTube audio extraction. */
class AudioSegmentPlayer(
    private val context: Context,
    private val onPlaying: (Boolean) -> Unit
) {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var endMs: Long = 0
    private var loop: Boolean = false
    private var startMs: Long = 0
    private val watch = object : Runnable {
        override fun run() {
            val mp = player ?: return
            if (!mp.isPlaying) return
            if (endMs > startMs && mp.currentPosition >= endMs - 35) {
                if (loop) mp.seekTo(startMs.toInt()) else { stop(); return }
            }
            handler.postDelayed(this, 80)
        }
    }
    fun play(uri: Uri, from: Long, to: Long, speed: Float = 1f, repeat: Boolean = false) {
        stop()
        startMs = from.coerceAtLeast(0)
        endMs = to.coerceAtLeast(startMs)
        loop = repeat
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            mp.setDataSource(context, uri)
            mp.setOnPreparedListener {
                val safeStart = startMs.coerceAtMost(mp.duration.toLong()).toInt()
                if (endMs == startMs) endMs = mp.duration.toLong()
                mp.seekTo(safeStart)
                mp.playbackParams = PlaybackParams().setSpeed(speed.coerceIn(0.5f, 1.5f))
                mp.start()
                onPlaying(true)
                handler.post(watch)
            }
            mp.setOnCompletionListener { stop() }
            mp.prepareAsync()
        } catch (_: Exception) {
            stop()
        }
    }
    fun stop() {
        handler.removeCallbacks(watch)
        player?.runCatching { release() }
        player = null
        onPlaying(false)
    }
}
