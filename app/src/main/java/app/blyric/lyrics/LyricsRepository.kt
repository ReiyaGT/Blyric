package app.blyric.lyrics

import android.content.Context
import app.blyric.BuildConfig
import kotlinx.coroutines.CancellationException
import java.io.File

/** Urutan provider = prioritas. Better Lyrics dulu (sinkron per kata), LRCLIB sebagai cadangan. */
class LyricsRepository(context: Context) {
    private val cache = LyricsCache(File(context.cacheDir, "lyrics"))
    private val providers: List<LyricsProvider> = listOf(
        BetterLyricsProvider(BuildConfig.LYRICS_API_BASE),
        LrclibProvider(),
    )

    suspend fun load(track: TrackInfo): Lyrics? {
        cache.get(track)?.let { return it }

        val title = TrackNormalizer.title(track.title)
        val artists = TrackNormalizer.artistCandidates(track.artist)
        for (provider in providers) {
            for (artist in artists) {
                val result = try {
                    provider.fetch(track.copy(title = title, artist = artist))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (result != null && result.lines.isNotEmpty()) {
                    cache.put(track, result)
                    return result
                }
            }
        }
        return null
    }
}
