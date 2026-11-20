package app.blyric

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.blyric.lyrics.Lyrics
import app.blyric.lyrics.LyricsRepository
import app.blyric.media.NowPlaying
import app.blyric.media.NowPlayingTracker
import app.blyric.ui.AlbumPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LyricsUi {
    data object Idle : LyricsUi
    data object Loading : LyricsUi
    data object NotFound : LyricsUi
    data class Ready(val lyrics: Lyrics) : LyricsUi

    /** Sumber yang dipilih lewat tombol ganti sumber tidak punya lirik untuk lagu ini. */
    data class Missing(val source: String) : LyricsUi
}

/**
 * Status kontrol ganti sumber. [selected] null = belum ada sumber aktif (kontrol disembunyikan);
 * [switching] true selama lirik dari sumber tujuan sedang diambil.
 */
data class SourceUi(val sources: List<String>, val selected: String?, val switching: Boolean)

class LyricsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LyricsRepository(app)

    val nowPlaying: StateFlow<NowPlaying?> = NowPlayingTracker.state

    private val _ui = MutableStateFlow<LyricsUi>(LyricsUi.Idle)
    val ui: StateFlow<LyricsUi> = _ui.asStateFlow()

    private val _source = MutableStateFlow(SourceUi(repo.sources, null, false))
    val source: StateFlow<SourceUi> = _source.asStateFlow()

    // Hasil per sumber untuk lagu yang sedang diputar (hanya di memori). Nilai null = sudah dicoba, tidak ada lirik.
    private val perSource = HashMap<String, Lyrics?>()
    private var switchJob: Job? = null

    /**
     * Warna latar dari sampul album. Saat sampul hilang (mis. sesaat ketika lagu berganti) kembali ke tema bawaan
     * dengan jeda singkat, supaya perpindahan antar-lagu tidak berkedip lewat warna bawaan.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val palette: StateFlow<AlbumPalette> = NowPlayingTracker.state
        .map { it?.artwork }
        .distinctUntilChanged()
        .transformLatest { bitmap ->
            if (bitmap == null) {
                delay(800)
                emit(AlbumPalette.Fallback)
            } else {
                emit(withContext(Dispatchers.Default) { AlbumPalette.from(bitmap) })
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AlbumPalette.Fallback)

    init {
        viewModelScope.launch {
            // Muat ulang lirik hanya saat lagu berganti (bukan tiap update posisi).
            NowPlayingTracker.state
                .map { np -> np?.track?.let { it.title to it.artist } }
                .distinctUntilChanged()
                .collectLatest { key ->
                    switchJob?.cancel()
                    perSource.clear()
                    _source.value = SourceUi(repo.sources, null, false)

                    val track = NowPlayingTracker.state.value?.track
                    if (key == null || track == null) {
                        _ui.value = LyricsUi.Idle
                        return@collectLatest
                    }
                    _ui.value = LyricsUi.Loading
                    val lyrics = repo.load(track)
                    if (lyrics != null) {
                        perSource[lyrics.source] = lyrics
                        _source.value = SourceUi(repo.sources, lyrics.source, false)
                        _ui.value = LyricsUi.Ready(lyrics)
                    } else {
                        _ui.value = LyricsUi.NotFound
                    }
                }
        }
    }

    /** Pindah ke sumber sebelumnya (delta = -1) atau berikutnya (+1), berputar di ujung daftar. */
    fun switchSource(delta: Int) {
        val sources = repo.sources
        val current = _source.value.selected ?: return
        val track = NowPlayingTracker.state.value?.track ?: return
        if (sources.size < 2) return

        val from = sources.indexOf(current).coerceAtLeast(0)
        val target = sources[(from + delta).mod(sources.size)]

        switchJob?.cancel()
        switchJob = viewModelScope.launch {
            // Lirik lama tetap tampil (diredupkan oleh UI) selama sumber baru diambil, jadi layout tidak melompat.
            _source.value = SourceUi(sources, target, true)
            val lyrics = if (perSource.containsKey(target)) {
                perSource[target]
            } else {
                repo.loadFrom(track, target).also { perSource[target] = it }
            }
            _source.value = SourceUi(sources, target, false)
            _ui.value = if (lyrics != null) LyricsUi.Ready(lyrics) else LyricsUi.Missing(target)
        }
    }
}
