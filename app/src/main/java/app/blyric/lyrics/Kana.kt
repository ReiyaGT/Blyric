package app.blyric.lyrics

/**
 * Konversi kana -> romaji (Hepburn yang disederhanakan). Murni JVM, tanpa API Android, supaya bisa diuji lewat unit test.
 *
 * Aturan yang dipakai:
 *  - Vokal panjang ditulis dengan mengulang vokal (ー -> ulang vokal sebelumnya): コンピューター -> konpyuutaa,
 *    ありがとう -> arigatou (ejaan kana-nya dipertahankan, bukan makron).
 *  - っ/ッ menggandakan konsonan berikutnya (がっこう -> gakkou, まっちゃ -> matcha).
 *  - ん/ン diberi apostrof bila diikuti vokal / y (げんいん -> gen'in) supaya tidak ambigu.
 */
internal object Kana {
    private val mono: Map<Char, String> = parse(
        """
        ア a イ i ウ u エ e オ o
        カ ka キ ki ク ku ケ ke コ ko
        ガ ga ギ gi グ gu ゲ ge ゴ go
        サ sa シ shi ス su セ se ソ so
        ザ za ジ ji ズ zu ゼ ze ゾ zo
        タ ta チ chi ツ tsu テ te ト to
        ダ da ヂ ji ヅ zu デ de ド do
        ナ na ニ ni ヌ nu ネ ne ノ no
        ハ ha ヒ hi フ fu ヘ he ホ ho
        バ ba ビ bi ブ bu ベ be ボ bo
        パ pa ピ pi プ pu ペ pe ポ po
        マ ma ミ mi ム mu メ me モ mo
        ヤ ya ユ yu ヨ yo
        ラ ra リ ri ル ru レ re ロ ro
        ワ wa ヲ o ン n ヴ vu
        ァ a ィ i ゥ u ェ e ォ o ヮ wa ヰ i ヱ e ヵ ka ヶ ke
        """,
    ).mapKeys { it.key[0] }

    private val digraph: Map<String, String> = parse(
        """
        キャ kya キュ kyu キョ kyo ギャ gya ギュ gyu ギョ gyo
        シャ sha シュ shu ショ sho ジャ ja ジュ ju ジョ jo
        チャ cha チュ chu チョ cho ヂャ ja ヂュ ju ヂョ jo
        ニャ nya ニュ nyu ニョ nyo ヒャ hya ヒュ hyu ヒョ hyo
        ビャ bya ビュ byu ビョ byo ピャ pya ピュ pyu ピョ pyo
        ミャ mya ミュ myu ミョ myo リャ rya リュ ryu リョ ryo
        イェ ye キェ kye シェ she ジェ je チェ che
        ティ ti ディ di トゥ tu ドゥ du テュ tyu デュ dyu
        ファ fa フィ fi フェ fe フォ fo フュ fyu
        ウィ wi ウェ we ウォ wo
        ヴァ va ヴィ vi ヴェ ve ヴォ vo
        ツァ tsa ツィ tsi ツェ tse ツォ tso
        クァ kwa クィ kwi クェ kwe クォ kwo グァ gwa
        スィ si ズィ zi
        """,
    )

    private const val APOSTROPHE_BEFORE = "アイウエオヤユヨ"
    private const val ROMAJI_VOWELS = "aiueo"

    private fun parse(table: String): Map<String, String> {
        val parts = table.trim().split(Regex("\\s+"))
        val map = HashMap<String, String>()
        var i = 0
        while (i + 1 < parts.size) {
            map[parts[i]] = parts[i + 1]
            i += 2
        }
        return map
    }

    /** Hiragana -> Katakana (U+3041..U+3096 digeser 0x60); karakter lain tidak berubah. */
    fun hiraganaToKatakana(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(if (c.code in 0x3041..0x3096) (c.code + 0x60).toChar() else c)
        }
        return sb.toString()
    }

    /** Hiragana/Katakana -> romaji huruf kecil. Karakter non-kana diteruskan apa adanya. */
    fun toRomaji(kana: String): String {
        val s = hiraganaToKatakana(kana)
        val out = StringBuilder()
        var sokuon = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == 'ッ') {
                sokuon = true
                i++
                continue
            }
            if (c == 'ー') {
                // Perpanjang vokal sebelumnya; diabaikan bila sebelumnya bukan vokal (mis. setelah ん).
                if (out.isNotEmpty() && out.last() in ROMAJI_VOWELS) out.append(out.last())
                i++
                continue
            }
            var chunk: String? = null
            var consumed = 1
            if (i + 1 < s.length) {
                val pair = digraph[s.substring(i, i + 2)]
                if (pair != null) {
                    chunk = pair
                    consumed = 2
                }
            }
            if (chunk == null) chunk = mono[c]
            if (chunk == null) {
                sokuon = false
                out.append(c)
                i++
                continue
            }
            if (c == 'ン' && i + 1 < s.length && s[i + 1] in APOSTROPHE_BEFORE) chunk = "n'"
            if (sokuon) {
                chunk = doubled(chunk)
                sokuon = false
            }
            out.append(chunk)
            i += consumed
        }
        return out.toString()
    }

    private fun doubled(chunk: String): String = when {
        chunk.startsWith("ch") -> "t$chunk"
        chunk[0] in "aiueon" -> chunk
        else -> "${chunk[0]}$chunk"
    }
}
