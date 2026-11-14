package app.blyric.lyrics

data class TrackInfo(
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val packageName: String,
)

data class LyricWord(val startMs: Long, val endMs: Long, val text: String)

/**
 * Satu baris lirik.
 * - [words] kosong  -> sinkron per baris (atau tanpa sinkron)
 * - [words] terisi  -> sinkron per kata/suku kata (gaya Better Lyrics)
 * - [isBackground]  -> vokal latar / balasan; ditampilkan muncul-hilang & bisa overlap dengan baris utama
 */
data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val isBackground: Boolean = false,
    val agent: String? = null,
    /** Romanisasi (huruf Latin) untuk baris berhuruf non-Latin; null bila tidak perlu / dimatikan. */
    val roman: String? = null,
)

enum class SyncLevel { WORD, LINE, UNSYNCED }

data class Lyrics(val lines: List<LyricLine>, val sync: SyncLevel, val source: String)

interface LyricsProvider {
    val name: String
    suspend fun fetch(track: TrackInfo): Lyrics?
}
