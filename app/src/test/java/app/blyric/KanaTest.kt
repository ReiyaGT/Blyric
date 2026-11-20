package app.blyric

import app.blyric.lyrics.Kana
import org.junit.Assert.assertEquals
import org.junit.Test

class KanaTest {
    @Test fun katakanaLongVowelRepeatsVowel() {
        assertEquals("konpyuutaa", Kana.toRomaji("コンピューター"))
        assertEquals("tii", Kana.toRomaji("ティー"))
    }

    @Test fun hiraganaIsConvertedToo() {
        assertEquals("arigatou", Kana.toRomaji("ありがとう"))
        assertEquals("aishiteru", Kana.toRomaji("あいしてる"))
        assertEquals("kimi", Kana.toRomaji("きみ"))
    }

    @Test fun sokuonDoublesConsonant() {
        assertEquals("gakkou", Kana.toRomaji("ガッコウ"))
        assertEquals("datta", Kana.toRomaji("ダッタ"))
        assertEquals("matcha", Kana.toRomaji("マッチャ"))
        assertEquals("happii", Kana.toRomaji("ハッピー"))
    }

    @Test fun moraicNUsesApostropheBeforeVowelOrY() {
        assertEquals("gen'in", Kana.toRomaji("ゲンイン"))
        assertEquals("hon'ya", Kana.toRomaji("ホンヤ"))
        assertEquals("sensei", Kana.toRomaji("センセイ"))
    }

    @Test fun digraphsAndLoanSounds() {
        assertEquals("kyou", Kana.toRomaji("キョウ"))
        assertEquals("shinjatta", Kana.toRomaji("シンジャッタ"))
        assertEquals("faito", Kana.toRomaji("ファイト"))
        assertEquals("vaiorin", Kana.toRomaji("ヴァイオリン"))
    }

    @Test fun particlePronunciationAndNonKanaPassThrough() {
        assertEquals("konnichiwa", Kana.toRomaji("コンニチワ"))
        assertEquals("ABC", Kana.toRomaji("ABC"))
        assertEquals("", Kana.toRomaji(""))
    }
}
