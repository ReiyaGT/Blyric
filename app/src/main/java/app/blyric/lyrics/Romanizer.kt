package app.blyric.lyrics

import android.icu.text.Transliterator

/**
 * Romanisasi lirik berhuruf non-Latin memakai ICU bawaan Android (android.icu, API 24+; minSdk app = 26),
 * jadi tidak menambah ukuran APK.
 *
 * Cakupan: Kiril, Yunani, Arab, Ibrani, Devanagari, Thai, Hangul, Han (pinyin), Hiragana/Katakana (Hepburn), dll.
 * Batasan (sifat transliterasi karakter, bukan pelafalan):
 *  - Lirik Jepang: hanya kana yang diromanisasi. Kanji dibiarkan karena ICU membacanya sebagai pinyin Mandarin
 *    (salah untuk bahasa Jepang). Membaca kanji butuh kamus morfologi (mis. Kuromoji) yang menambah beberapa MB.
 *  - Hangul memakai skema transliterasi ICU, bukan Revised Romanization resmi (mis. 사랑 -> "salang").
 *  - Arab/Ibrani tanpa harakat hanya perkiraan; partikel Jepang は/を/へ ditulis apa adanya (ha/o/he).
 *  - Diakritik (nada pinyin, makron) dibuang lewat Latin-ASCII agar mudah dibaca.
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
        val raw = if (japanese) {
            Scripts.splitKana(text).joinToString("") { part ->
                if (part.second) toLatin(part.first) else part.first
            }
        } else {
            toLatin(text)
        }
        return raw.replace(spaces, " ").trim().replaceFirstChar { it.uppercaseChar() }
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
