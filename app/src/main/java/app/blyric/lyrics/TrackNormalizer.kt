package app.blyric.lyrics

/** Membersihkan metadata kotor dari app musik, mis. "Judul (Official Video) [HD]" atau "Artis - Topic". */
object TrackNormalizer {
    private val noiseBrackets = Regex(
        """\s*[(\[][^)\]]*(official|video|audio|lyrics?|visualizer|remaster(ed)?|\bhd\b|\bhq\b|\bmv\b|m/v|karaoke|explicit)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val featTail = Regex("""\s*[(\[]?\s*(feat\.?|ft\.?|featuring)\s.*$""", RegexOption.IGNORE_CASE)
    private val topicSuffix = Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE)
    private val vevoSuffix = Regex("""VEVO$""", RegexOption.IGNORE_CASE)
    private val artistSplit = Regex("""\s*(,|&|;|\sx\s|\sfeat\.?\s|\sft\.?\s)\s*""", RegexOption.IGNORE_CASE)

    fun title(raw: String): String =
        raw.replace(noiseBrackets, "").replace(featTail, "").trim().ifEmpty { raw.trim() }

    /** Kandidat nama artis: versi lengkap dulu, lalu artis pertama saja. */
    fun artistCandidates(raw: String): List<String> {
        val full = raw.replace(topicSuffix, "").replace(vevoSuffix, "").trim()
        val first = full.split(artistSplit).first().trim()
        return listOf(full, first).filter { it.isNotEmpty() }.distinct()
    }
}
