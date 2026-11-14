package app.blyric.lyrics

import app.blyric.util.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Cadangan: LRCLIB (https://lrclib.net) — API terbuka, lirik sinkron per baris (LRC). */
class LrclibProvider : LyricsProvider {
    override val name = "LRCLIB"

    override suspend fun fetch(track: TrackInfo): Lyrics? = withContext(Dispatchers.IO) {
        val a = Http.enc(track.artist)
        val t = Http.enc(track.title)
        val exact = buildString {
            append("https://lrclib.net/api/get?artist_name=$a&track_name=$t")
            track.album?.takeIf { it.isNotBlank() }?.let { append("&album_name=${Http.enc(it)}") }
            if (track.durationMs > 0) append("&duration=${track.durationMs / 1000}")
        }
        val res = Http.get(exact)
        val obj: JSONObject? = if (res.ok) {
            JSONObject(res.body)
        } else {
            val search = Http.get("https://lrclib.net/api/search?artist_name=$a&track_name=$t")
            if (search.ok) JSONArray(search.body).let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull() } else null
        }
        obj ?: return@withContext null
        val synced = obj.str("syncedLyrics")
        val plain = obj.str("plainLyrics")
        when {
            !synced.isNullOrBlank() -> LrcParser.parse(synced, name)
            !plain.isNullOrBlank() -> LrcParser.parse(plain, name)
            else -> null
        }
    }

    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else getString(key)
}
