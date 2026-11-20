package app.blyric.lyrics

import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer
import java.text.Normalizer

/**
 * Romanisasi lirik Jepang yang membaca **hiragana, katakana, dan kanji** sesuai konteks kalimat.
 *
 * Kanji dibaca lewat analisis morfologi Kuromoji (kamus IPADIC): tiap kata mendapat pembacaan (yomi) yang
 * dipilih dari konteksnya, mis. 今日 -> kyou, 日本 -> nihon, 一人 -> hitori. Pembacaan lalu diubah ke romaji oleh [Kana].
 *
 * Aturan tambahan agar hasilnya enak dibaca:
 *  - Partikel は / へ / を dibunyikan wa / e / o (lewat field pronunciation Kuromoji).
 *  - Kata bantu yang menempel (て, た, ない, ます, る pada 〜てる, dst.) digabung ke kata sebelumnya: 愛してる -> aishiteru.
 *  - だ / です dan partikel akhir tetap dipisah spasi: 好きだよ -> suki da yo.
 *  - Teks Latin, angka, dan simbol dipertahankan; tanda baca Jepang dipetakan ke padanan Latin.
 */
internal object JapaneseRomanizer {
    private val lazyTokenizer = lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Tokenizer() }

    @Volatile private var failed = false

    /** Hasil romaji, atau null bila kamus tidak bisa dimuat / baris gagal diproses (pemanggil memakai cadangan). */
    fun romanize(text: String): String? {
        if (failed) return null
        val tokenizer = try {
            lazyTokenizer.value
        } catch (t: Throwable) {
            failed = true // kamus tidak bisa dimuat (mis. OutOfMemoryError): jangan coba lagi tiap baris
            return null
        }
        return try {
            val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
            join(build(tokenizer.tokenize(normalized)))
        } catch (e: Exception) {
            null
        }
    }

    // ---- Penyusunan potongan -------------------------------------------------------------------------------------

    private class Piece(
        var text: String,
        val kana: Boolean,
        val noSpaceBefore: Boolean = false,
        val noSpaceAfter: Boolean = false,
    )

    private fun build(tokens: List<Token>): List<Piece> {
        val pieces = ArrayList<Piece>()
        for (t in tokens) {
            val surface: String = t.surface
            if (surface.isBlank()) continue

            val mark = punctuation(surface)
            if (mark != null) {
                pieces.add(mark)
                continue
            }

            val reading = kanaReading(t)
            if (reading == null) {
                pieces.add(Piece(surface, kana = false))
                continue
            }
            val last = pieces.lastOrNull()
            if (last != null && last.kana && shouldAttach(t)) {
                last.text += reading
            } else {
                pieces.add(Piece(reading, kana = true))
            }
        }
        return pieces
    }

    private fun join(pieces: List<Piece>): String {
        val sb = StringBuilder()
        var prevNoSpaceAfter = true
        for (p in pieces) {
            val rom = if (p.kana) Kana.toRomaji(p.text) else p.text
            if (rom.isEmpty()) continue
            if (sb.isNotEmpty() && !p.noSpaceBefore && !prevNoSpaceAfter) sb.append(' ')
            sb.append(rom)
            prevNoSpaceAfter = p.noSpaceAfter
        }
        return sb.toString()
    }

    /** Pembacaan katakana untuk token berhuruf Jepang; null bila token bukan Jepang atau tidak bisa dibaca. */
    private fun kanaReading(t: Token): String? {
        val surface: String = t.surface
        if (!Scripts.hasJapanese(surface)) return null

        val reading: String? = t.reading
        val base = when {
            t.isKnown && !reading.isNullOrEmpty() && reading != "*" -> reading
            surface.all { Scripts.isKana(it.code) } -> Kana.hiraganaToKatakana(surface)
            else -> return null // kanji di luar kamus: biarkan apa adanya daripada menebak
        }
        return applyPronunciation(base, t.pronunciation)
    }

    /**
     * Kuromoji memberi `reading` (ejaan: ハ, ヘ, ヲ) dan `pronunciation` (bunyi: ワ, エ, オ; vokal panjang jadi ー).
     * Ejaan dipakai supaya ありがとう tetap "arigatou", tetapi ハ/ヘ/ヲ diganti bunyinya (partikel は -> wa).
     */
    private fun applyPronunciation(reading: String, pronunciation: String?): String {
        if (pronunciation.isNullOrEmpty() || pronunciation.length != reading.length) return reading
        val sb = StringBuilder(reading)
        for (i in reading.indices) {
            val r = reading[i]
            val p = pronunciation[i]
            if ((r == 'ハ' && p == 'ワ') || (r == 'ヘ' && p == 'エ') || (r == 'ヲ' && p == 'オ')) sb.setCharAt(i, p)
        }
        return sb.toString()
    }

    /** Apakah [t] harus ditempel ke kata sebelumnya (tanpa spasi). */
    private fun shouldAttach(t: Token): Boolean {
        val pos1: String = t.partOfSpeechLevel1
        val pos2: String = t.partOfSpeechLevel2
        return when (pos1) {
            "助動詞" -> t.surface !in SEPARATE_AUXILIARIES
            "動詞" -> pos2 == "非自立" || pos2 == "接尾"
            "形容詞", "名詞" -> pos2 == "接尾"
            "助詞" -> pos2 == "接続助詞" && (t.surface == "て" || t.surface == "で")
            else -> false
        }
    }

    private val SEPARATE_AUXILIARIES = setOf("だ", "だっ", "だろ", "です", "でし", "でしょ", "じゃ")

    // ---- Tanda baca -----------------------------------------------------------------------------------------------

    private const val TRAILING_MARKS = ",.!?;:~"

    /** Petakan token yang hanya berisi simbol; null bila token berisi huruf/angka (atau bukan simbol). */
    private fun punctuation(surface: String): Piece? {
        if (surface.any { it.isLetterOrDigit() }) return null
        val mapped = StringBuilder()
        for (c in surface) {
            when (c) {
                '、' -> mapped.append(',')
                '。' -> mapped.append('.')
                '・' -> mapped.append(' ')
                '〜', '～' -> mapped.append('~')
                '「', '『' -> mapped.append('“')
                '」', '』' -> mapped.append('”')
                '（', '【', '［', '〈', '《' -> mapped.append('(')
                '）', '】', '］', '〉', '》' -> mapped.append(')')
                else -> mapped.append(c)
            }
        }
        val text = mapped.toString().trim()
        if (text.isEmpty()) return null
        return when {
            text.all { it == '“' || it == '(' || it == '[' } -> Piece(text, kana = false, noSpaceAfter = true)
            text.all { it == '”' || it == ')' || it == ']' || it in TRAILING_MARKS } ->
                Piece(text, kana = false, noSpaceBefore = true)
            else -> Piece(text, kana = false)
        }
    }
}
