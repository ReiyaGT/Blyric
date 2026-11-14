package app.blyric.lyrics

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * Parser TTML (format lirik sinkron per kata/suku kata ala Apple Music / Better Lyrics).
 *
 *  <p begin="0:12.300" end="0:15.000" ttm:agent="v1">
 *    <span begin="0:12.300" end="0:12.800">Hel</span><span begin="0:12.800" end="0:13.200">lo </span>
 *    <span ttm:role="x-bg"><span begin=".." end="..">(ooh)</span></span>
 *  </p>
 *
 * Span ber-role "x-bg" dijadikan baris latar (isBackground = true) yang bisa overlap dengan baris utama.
 */
object TtmlParser {
    private class Span(val begin: Long?, val end: Long?, val bg: Boolean)

    private class Builder {
        val spans = ArrayDeque<Span>()
        var begin: Long? = null
        var end: Long? = null
        var agent: String? = null
        var words = mutableListOf<LyricWord>()
        var bgWords = mutableListOf<LyricWord>()
        val plain = StringBuilder()

        fun reset(begin: Long?, end: Long?, agent: String?) {
            this.begin = begin; this.end = end; this.agent = agent
            spans.clear(); words = mutableListOf(); bgWords = mutableListOf(); plain.setLength(0)
        }

        fun onText(t: String) {
            val bg = spans.any { it.bg }
            val target = if (bg) bgWords else words
            if (t.isBlank()) {
                // spasi antar-span -> tempelkan ke kata sebelumnya
                val last = target.lastOrNull()
                if (last != null && !last.text.endsWith(" ")) target[target.lastIndex] = last.copy(text = last.text + " ")
                return
            }
            val timed = spans.lastOrNull { it.begin != null }
            if (timed != null) {
                val b = timed.begin!!
                target += LyricWord(b, timed.end ?: b, t)
            } else {
                plain.append(t)
            }
        }

        fun finish(out: MutableList<LyricLine>) {
            val mainText = if (words.isNotEmpty()) words.joinToString("") { it.text }.trim() else plain.toString().trim()
            if (mainText.isNotEmpty()) {
                val start = begin ?: words.firstOrNull()?.startMs ?: 0L
                val wordsEnd = words.lastOrNull()?.endMs
                val e = maxOf(end ?: wordsEnd ?: start, wordsEnd ?: 0L)
                out += LyricLine(start, e, mainText, words.toList(), false, agent)
            }
            if (bgWords.isNotEmpty()) {
                val text = bgWords.joinToString("") { it.text }.trim()
                out += LyricLine(bgWords.first().startMs, bgWords.last().endMs, text, bgWords.toList(), true, agent)
            }
        }
    }

    fun parse(xml: String, source: String): Lyrics? {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        val out = mutableListOf<LyricLine>()
        val b = Builder()
        var inP = false

        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (local(parser.name)) {
                    "p" -> {
                        inP = true
                        b.reset(time(attr(parser, "begin")), time(attr(parser, "end")), attr(parser, "agent"))
                    }
                    "span" -> if (inP) {
                        b.spans.addLast(
                            Span(time(attr(parser, "begin")), time(attr(parser, "end")), attr(parser, "role") == "x-bg"),
                        )
                    }
                }
                XmlPullParser.TEXT -> if (inP) b.onText(parser.text)
                XmlPullParser.END_TAG -> when (local(parser.name)) {
                    "span" -> if (inP && b.spans.isNotEmpty()) b.spans.removeLast()
                    "p" -> if (inP) { b.finish(out); inP = false }
                }
            }
            ev = parser.next()
        }
        if (out.isEmpty()) return null

        val sorted = out.sortedBy { it.startMs }
        val fixed = sorted.mapIndexed { i, l ->
            if (l.endMs > l.startMs) l else l.copy(endMs = sorted.getOrNull(i + 1)?.startMs ?: (l.startMs + 4_000))
        }
        val sync = if (fixed.any { it.words.size > 1 }) SyncLevel.WORD else SyncLevel.LINE
        return Lyrics(fixed, sync, source)
    }

    private fun local(name: String) = name.substringAfter(':')

    private fun attr(p: XmlPullParser, name: String): String? {
        for (i in 0 until p.attributeCount) if (local(p.getAttributeName(i)) == name) return p.getAttributeValue(i)
        return null
    }

    /** "1:02:03.5", "02:03.500", "12.5", "12.5s", "500ms" -> milidetik */
    internal fun time(raw: String?): Long? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        if (t.endsWith("ms")) return t.dropLast(2).toDoubleOrNull()?.toLong()
        if (t.endsWith("s")) return t.dropLast(1).toDoubleOrNull()?.times(1000)?.toLong()
        var sec = 0.0
        for (part in t.split(":")) sec = sec * 60 + (part.toDoubleOrNull() ?: return null)
        return (sec * 1000).toLong()
    }
}
