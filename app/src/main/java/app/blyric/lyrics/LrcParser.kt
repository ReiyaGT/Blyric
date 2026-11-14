package app.blyric.lyrics

/** Parser LRC (sinkron per baris) + fallback teks biasa. */
object LrcParser {
    private val timestamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parse(text: String, source: String): Lyrics? {
        val entries = mutableListOf<Pair<Long, String>>()
        text.lineSequence().forEach { raw ->
            val stamps = timestamp.findAll(raw).toList()
            if (stamps.isEmpty()) return@forEach
            val content = raw.substring(stamps.last().range.last + 1).trim()
            stamps.forEach { entries += toMs(it) to content }
        }

        if (entries.isEmpty()) {
            val plain = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("[") }
            if (plain.isEmpty()) return null
            return Lyrics(plain.map { LyricLine(0, 0, it) }, SyncLevel.UNSYNCED, source)
        }

        val sorted = entries.sortedBy { it.first }
        val lines = mutableListOf<LyricLine>()
        sorted.forEachIndexed { i, (start, content) ->
            if (content.isEmpty()) return@forEachIndexed // jeda instrumental: hanya menutup baris sebelumnya
            val end = sorted.getOrNull(i + 1)?.first ?: (start + 5_000)
            lines += LyricLine(startMs = start, endMs = end, text = content)
        }
        return if (lines.isEmpty()) null else Lyrics(lines, SyncLevel.LINE, source)
    }

    private fun toMs(m: MatchResult): Long {
        val min = m.groupValues[1].toLong()
        val sec = m.groupValues[2].toLong()
        val frac = m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return min * 60_000 + sec * 1_000 + frac
    }
}
