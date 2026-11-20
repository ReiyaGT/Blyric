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

    /** Nama sumber lirik sesuai urutan provider; dipakai UI untuk berpindah sumber. */
    val sources: List<String> = providers.map { it.name }

    suspend fun load(track: TrackInfo): Lyrics? {
        cache.get(track)?.let { return it }

        val title = TrackNormalizer.title(track.title)
        val artists = TrackNormalizer.artistCandidates(track.artist)
        for (provider in providers) {
            val result = fetchFrom(provider, track, title, artists)
            if (result != null) {
                cache.put(track, result)
                return result
            }
        }
        return null
    }

    /**
     * Ambil lirik dari satu sumber tertentu (untuk tombol ganti sumber). Tidak menyentuh cache supaya
     * pilihan default untuk lagu ini tidak berubah; hasilnya disimpan sementara oleh pemanggil.
     */
    suspend fun loadFrom(track: TrackInfo, source: String): Lyrics? {
        val provider = providers.firstOrNull { it.name == source } ?: return null
        return fetchFrom(provider, track, TrackNormalizer.title(track.title), TrackNormalizer.artistCandidates(track.artist))
    }

    private suspend fun fetchFrom(
        provider: LyricsProvider,
        track: TrackInfo,
        title: String,
        artists: List<String>,
    ): Lyrics? {
        for (artist in artists) {
            val result = try {
                provider.fetch(track.copy(title = title, artist = artist))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (result != null && result.lines.isNotEmpty()) return result
        }
        return null
    }
}
