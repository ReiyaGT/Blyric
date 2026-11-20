package app.blyric.lyrics

/** Deteksi aksara (script) Unicode. Murni JVM, tanpa API Android, supaya bisa diuji lewat unit test. */
internal object Scripts {
    private fun script(cp: Int): Character.UnicodeScript = Character.UnicodeScript.of(cp)

    /** Hiragana / Katakana (+ tanda panjang ー yang secara Unicode berstatus Common). */
    fun isKana(cp: Int): Boolean {
        if (cp == 0x30FC) return true
        val s = script(cp)
        return s == Character.UnicodeScript.HIRAGANA || s == Character.UnicodeScript.KATAKANA
    }

    fun hasKana(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isKana(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    fun isHan(cp: Int): Boolean = script(cp) == Character.UnicodeScript.HAN

    /** True bila ada kana atau kanji/Han (huruf yang butuh pembacaan khusus bahasa Jepang). */
    fun hasJapanese(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isKana(cp) || isHan(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    /** True bila ada karakter yang bukan Latin/angka/tanda baca (misalnya Kiril, Hangul, Han, Arab, Thai). */
    fun needsRomanization(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val s = script(cp)
            if (s != Character.UnicodeScript.LATIN &&
                s != Character.UnicodeScript.COMMON &&
                s != Character.UnicodeScript.INHERITED &&
                s != Character.UnicodeScript.UNKNOWN
            ) return true
            i += Character.charCount(cp)
        }
        return false
    }

    /** Pecah teks menjadi potongan berurutan: (potongan, apakahKana). */
    fun splitKana(text: String): List<Pair<String, Boolean>> {
        val out = mutableListOf<Pair<String, Boolean>>()
        val sb = StringBuilder()
        var current: Boolean? = null
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val kana = isKana(cp)
            val c = current
            if (c != null && c != kana) {
                out.add(Pair(sb.toString(), c))
                sb.setLength(0)
            }
            current = kana
            sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        val last = current
        if (last != null) out.add(Pair(sb.toString(), last))
        return out
    }
}
