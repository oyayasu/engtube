package com.oyayasu.engtube

import com.oyayasu.engtube.data.SourceTools
import org.junit.Assert.*
import org.junit.Test

class SourceToolsTest {
    @Test fun acceptsYouTubeOnly() {
        val source = SourceTools.youtube("See https://youtu.be/dQw4w9WgXcQ?t=1m12s")!!
        assertEquals("dQw4w9WgXcQ", source.id)
        assertEquals(72000, source.startMs)
        assertEquals(null, SourceTools.youtube("https://notyoutube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(null, SourceTools.youtube("https://youtube.com.evil.com/watch?v=dQw4w9WgXcQ"))
    }
    @Test fun parsesSrtTimestamps() {
        val captions = SourceTools.transcript("1\n00:00:04,500 --> 00:00:06,100\nThat's wonderful!\n\n2\n00:00:06,100 --> 00:00:08,200\nQuite right.")
        assertEquals(2, captions.size)
        assertEquals("That's wonderful!", captions[0].text)
        assertEquals(4500L, captions[0].startMs)
        assertEquals(8200L, captions[1].endMs)
    }
    @Test fun untimedTextIsUnaligned() {
        val captions = SourceTools.transcript("Hello there. How are you?")
        assertEquals(2, captions.size)
        assertNull(captions[0].startMs)
    }
    @Test fun rejectsInvalidTime() {
        assertNull(SourceTools.parseClock("12:65"))
        assertEquals(90125, SourceTools.parseClock("01:30.125"))
    }
}
