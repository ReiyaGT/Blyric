package app.blyric

import app.blyric.lyrics.Scripts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptsTest {
    @Test fun latinTextNeedsNoRomanization() {
        assertFalse(Scripts.needsRomanization("Hello, world! 123 café"))
        assertFalse(Scripts.needsRomanization("Aku sayang kamu..."))
    }

    @Test fun detectsNonLatinScripts() {
        assertTrue(Scripts.needsRomanization("привет"))
        assertTrue(Scripts.needsRomanization("你好"))
        assertTrue(Scripts.needsRomanization("안녕"))
        assertTrue(Scripts.needsRomanization("こんにちは"))
        assertTrue(Scripts.needsRomanization("Hello 世界"))
    }

    @Test fun detectsKana() {
        assertTrue(Scripts.hasKana("さよなら"))
        assertTrue(Scripts.hasKana("ラーメン"))
        assertFalse(Scripts.hasKana("你好"))
        assertFalse(Scripts.hasKana("안녕"))
    }

    @Test fun splitsKanaRuns() {
        assertEquals(listOf(Pair("愛", false), Pair("してる", true)), Scripts.splitKana("愛してる"))
        assertEquals(listOf(Pair("ABC", false)), Scripts.splitKana("ABC"))
        assertEquals(emptyList<Pair<String, Boolean>>(), Scripts.splitKana(""))
    }
}
