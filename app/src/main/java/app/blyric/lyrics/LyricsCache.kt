package app.blyric.lyrics

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Cache lirik di cacheDir (JSON per lagu) supaya hemat request & bisa dipakai offline. */
class LyricsCache(private val dir: File) {
    init { dir.mkdirs() }

    private fun file(t: TrackInfo): File {
        val raw = "${t.artist.lowercase()}|${t.title.lowercase()}"
        val hash = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.json")
    }

    fun get(t: TrackInfo): Lyrics? = runCatching {
        val f = file(t)
        if (f.exists()) decode(JSONObject(f.readText())) else null
    }.getOrNull()

    fun put(t: TrackInfo, l: Lyrics) {
        runCatching { file(t).writeText(encode(l).toString()) }
    }

    private fun encode(l: Lyrics) = JSONObject()
        .put("source", l.source)
        .put("sync", l.sync.name)
        .put("lines", JSONArray(l.lines.map { line ->
            JSONObject()
                .put("s", line.startMs).put("e", line.endMs).put("t", line.text)
                .put("bg", line.isBackground).put("a", line.agent ?: "")
                .put("w", JSONArray(line.words.map { w ->
                    JSONObject().put("s", w.startMs).put("e", w.endMs).put("t", w.text)
                }))
        }))

    private fun decode(o: JSONObject): Lyrics {
        val arr = o.getJSONArray("lines")
        val lines = (0 until arr.length()).map { i ->
            val l = arr.getJSONObject(i)
            val wa = l.getJSONArray("w")
            LyricLine(
                startMs = l.getLong("s"), endMs = l.getLong("e"), text = l.getString("t"),
                words = (0 until wa.length()).map { j ->
                    val w = wa.getJSONObject(j)
                    LyricWord(w.getLong("s"), w.getLong("e"), w.getString("t"))
                },
                isBackground = l.getBoolean("bg"),
                agent = l.optString("a").ifEmpty { null },
            )
        }
        return Lyrics(lines, SyncLevel.valueOf(o.getString("sync")), o.getString("source"))
    }
}
