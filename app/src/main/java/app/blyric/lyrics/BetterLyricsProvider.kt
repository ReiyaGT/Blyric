package app.blyric.lyrics

import app.blyric.util.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Provider utama: API Better Lyrics -> GET {base}/getLyrics?a={artist}&s={song}
 * (lihat github.com/better-lyrics/api).
 *
 * Bentuk respons belum dikunci: klien ini mencari payload TTML atau LRC di dalam respons
 * (teks mentah atau di dalam JSON, field mana pun), lalu mem-parse-nya.
 * Kalau format aslinya ternyata beda, cukup ubah [extractPayload].
 */
class BetterLyricsProvider(private val baseUrl: String) : LyricsProvider {
    override val name = "Better Lyrics"

    override suspend fun fetch(track: TrackInfo): Lyrics? = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/getLyrics?a=${Http.enc(track.artist)}&s=${Http.enc(track.title)}"
        val res = Http.get(url)
        if (!res.ok) return@withContext null
        val payload = extractPayload(res.body) ?: return@withContext null
        parsePayload(payload, name)
    }

    private fun extractPayload(body: String): String? {
        val t = body.trim()
        if (t.isEmpty()) return null
        return when {
            t.startsWith("{") -> findPayload(JSONObject(t))
            t.startsWith("[") -> findPayload(JSONArray(t))
            else -> t
        }
    }

    private val lrcHint = Regex("""\[\d+:\d+""")

    private fun findPayload(node: Any?): String? = when (node) {
        is String -> node.takeIf { it.contains("<tt") || it.contains("<p ") || lrcHint.containsMatchIn(it) }
        is JSONObject -> node.keys().asSequence().firstNotNullOfOrNull { findPayload(node.opt(it)) }
        is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { findPayload(node.opt(it)) }
        else -> null
    }

    companion object {
        fun parsePayload(payload: String, source: String): Lyrics? =
            if (payload.contains("<p ") || payload.contains("<tt")) TtmlParser.parse(payload, source)
            else LrcParser.parse(payload, source)
    }
}
