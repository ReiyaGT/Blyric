package app.blyric

import app.blyric.lyrics.LrcParser
import app.blyric.lyrics.SyncLevel
import app.blyric.lyrics.TrackNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LrcParserTest {
    @Test fun parsesLineSync() {
        val l = LrcParser.parse("[ar:X]\n[00:01.50]Halo\n[00:03.00]Dunia\n[00:05.00]", "t")!!
        assertEquals(SyncLevel.LINE, l.sync)
        assertEquals(2, l.lines.size)
        assertEquals(1500L, l.lines[0].startMs)
        assertEquals(3000L, l.lines[0].endMs)
        assertEquals(5000L, l.lines[1].endMs) // ditutup oleh baris kosong (jeda instrumental)
    }

    @Test fun fallsBackToUnsynced() {
        val l = LrcParser.parse("satu\ndua", "t")!!
        assertEquals(SyncLevel.UNSYNCED, l.sync)
        assertEquals(2, l.lines.size)
    }

    @Test fun emptyIsNull() = assertNull(LrcParser.parse("   ", "t"))

    @Test fun normalizesTitleAndArtist() {
        assertEquals("Blinding Lights", TrackNormalizer.title("Blinding Lights (Official Video) [HD]"))
        assertEquals("Song", TrackNormalizer.title("Song (feat. Someone)"))
        assertEquals(listOf("Simon & Garfunkel", "Simon"), TrackNormalizer.artistCandidates("Simon & Garfunkel - Topic"))
    }
}
