package com.oyayasu.engtube.data

import java.net.URI
import java.util.Locale

data class VideoSource(val id: String, val url: String, val startMs: Long = 0)
data class CaptionSegment(val text: String, val startMs: Long? = null, val endMs: Long? = null)

object SourceTools {
    private val link = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")
    private val timedLine = Regex("""(?m)^\s*((?:\d{1,2}:)?\d{2}:\d{2}[.,]\d{1,3})\s*-->\s*((?:\d{1,2}:)?\d{2}:\d{2}[.,]\d{1,3})(?:\s+.*)?$""")

    fun youtube(source: String): VideoSource? {
        val raw = link.find(source)?.value?.trimEnd('.', ',', ')', ']', ';') ?: source.trim()
        val url = runCatching { URI(raw) }.getOrNull() ?: return null
        val host = (url.host ?: return null).lowercase(Locale.ROOT)
            .removePrefix("www.").removePrefix("m.")
        val path = url.path.orEmpty().trim('/')
        val segments = path.split('/')
        val query = url.rawQuery.orEmpty().split('&').mapNotNull {
            val parts = it.split('=', limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
        val id = when {
            host == "youtu.be" -> segments.firstOrNull()
            host == "youtube.com" || host == "youtube-nocookie.com" -> when (segments.firstOrNull()) {
                "watch" -> query["v"]
                "shorts", "live", "embed" -> segments.getOrNull(1)
                else -> null
            }
            else -> null
        } ?: return null
        if (!idPattern.matches(id)) return null
        val time = query["t"] ?: query["start"] ?: url.fragment?.removePrefix("t=") ?: "0"
        return VideoSource(id, "https://www.youtube.com/watch?v=$id", youtubeTime(time) * 1000)
    }

    fun youtubeTime(raw: String): Long {
        val value = raw.trim().lowercase(Locale.ROOT).removeSuffix("s")
        value.toLongOrNull()?.let { return it.coerceAtLeast(0) }
        var total = 0L
        var found = false
        for (match in Regex("""(\d+)(h|m|s)""").findAll(raw.lowercase(Locale.ROOT))) {
            found = true
            val number = match.groupValues[1].toLongOrNull() ?: 0
            total += number * when (match.groupValues[2]) {
                "h" -> 3600
                "m" -> 60
                else -> 1
            }
        }
        return if (found) total else 0
    }

    fun clock(ms: Long): String {
        val seconds = (ms.coerceAtLeast(0) / 1000)
        return if (seconds >= 3600) "%02d:%02d:%02d".format(
            Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60
        ) else "%02d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
    }

    fun parseClock(raw: String): Long? {
        val parts = raw.trim().replace(',', '.').split(':')
        if (parts.size !in 2..3) return null
        val seconds = parts.last().toDoubleOrNull() ?: return null
        if (seconds < 0 || seconds >= 60) return null
        val minutes = parts[parts.size - 2].toLongOrNull() ?: return null
        if (minutes < 0 || (parts.size == 3 && minutes >= 60)) return null
        val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0
        if (hours < 0) return null
        return ((hours * 3600 + minutes * 60) * 1000 + seconds * 1000).toLong()
    }

    fun transcript(raw: String): List<CaptionSegment> {
        val text = raw.trim()
        if (text.isBlank()) return emptyList()
        val matches = timedLine.findAll(text).toList()
        if (matches.isNotEmpty()) {
            return matches.mapIndexedNotNull { index, match ->
                val start = parseClock(match.groupValues[1]) ?: return@mapIndexedNotNull null
                val end = parseClock(match.groupValues[2]) ?: return@mapIndexedNotNull null
                val bodyStart = match.range.last + 1
                val bodyEnd = matches.getOrNull(index + 1)?.range?.first ?: text.length
                val content = text.substring(bodyStart, bodyEnd)
                    .replace(Regex("""(?m)^\s*\d+\s*$"""), "")
                    .replace(Regex("""<[^>]+>"""), "")
                    .replace(Regex("""(?m)^\s*(WEBVTT|NOTE.*)\s*$"""), "")
                    .replace('\n', ' ').trim()
                if (content.isBlank() || end <= start) null else
                    CaptionSegment(content, start, end)
            }.take(600)
        }
        return text.lines().flatMap { line ->
            line.trim().split(Regex("""(?<=[.!?])\s+"""))
                .map { it.trim() }.filter { it.isNotBlank() }
                .map { CaptionSegment(it) }
        }.take(600)
    }
}
