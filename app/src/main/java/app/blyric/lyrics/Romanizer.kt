package app.blyric.lyrics

import android.icu.text.Transliterator

/**
 * Romanisasi lirik berhuruf non-Latin.
 *
 *  - Lirik Jepang (ada kana di lagunya): hiragana, katakana, **dan kanji** dibaca oleh [JapaneseRomanizer]
 *    (Kuromoji, sadar konteks kalimat; partikel は/へ/を dibunyikan wa/e/o).
 *  - Aksara lain memakai ICU bawaan Android (android.icu, API 24+; minSdk app = 26): Kiril, Yunani, Arab, Ibrani,
 *    Devanagari, Thai, Hangul, dan Han tanpa kana (pinyin untuk lirik Mandarin).
 *
 * Batasan:
 *  - Hangul memakai skema transliterasi ICU, bukan Revised Romanization resmi (mis. 사랑 -> "salang").
 *  - Arab/Ibrani tanpa harakat hanya perkiraan.
 *  - Kanji yang tidak ada di kamus IPADIC (mis. nama / ateji langka) dibiarkan apa adanya, bukan ditebak.
 *  - Diakritik (nada pinyin) dibuang lewat Latin-ASCII agar mudah dibaca.
 */
object Romanizer {
    private val lock = Any()
    private var transliterator: Transliterator? = null
    private var unavailable = false
    private val spaces = Regex("\\s+")

    /** Mengembalikan [lyrics] dengan field `roman` terisi untuk baris yang berhuruf non-Latin. */
    fun annotate(lyrics: Lyrics): Lyrics {
        if (lyrics.lines.none { Scripts.needsRomanization(it.text) }) return lyrics
        val japanese = lyrics.lines.any { Scripts.hasKana(it.text) }
        val lines = lyrics.lines.map { line ->
            if (!Scripts.needsRomanization(line.text)) {
                line
            } else {
                val r = romanize(line.text, japanese)
                if (r.isBlank() || r.equals(line.text.trim(), ignoreCase = true)) line else line.copy(roman = r)
            }
        }
        return lyrics.copy(lines = lines)
    }

    fun romanize(text: String, japanese: Boolean): String {
        val raw = if (japanese) romanizeJapanese(text) else toLatin(text)
        return raw.replace(spaces, " ").trim().replaceFirstChar { it.uppercaseChar() }
    }

    private fun romanizeJapanese(text: String): String {
        // Baris tanpa kana/kanji di lagu Jepang (mis. Hangul atau Kiril yang terselip) tetap lewat ICU.
        if (!Scripts.hasJapanese(text)) return toLatin(text)
        JapaneseRomanizer.romanize(text)?.let { return it }
        // Cadangan bila kamus gagal dimuat (mis. memori kurang): hanya kana, kanji dibiarkan.
        return Scripts.splitKana(text).joinToString("") { part ->
            if (part.second) toLatin(part.first) else part.first
        }
    }

    private fun toLatin(s: String): String = synchronized(lock) {
        val t = obtain() ?: return s
        try {
            t.transliterate(s)
        } catch (e: Exception) {
            s
        }
    }

    private fun obtain(): Transliterator? {
        if (unavailable) return null
        transliterator?.let { return it }
        return try {
            val t = Transliterator.getInstance("Any-Latin; Latin-ASCII")
            transliterator = t
            t
        } catch (e: Exception) {
            unavailable = true
            null
        }
    }
}
