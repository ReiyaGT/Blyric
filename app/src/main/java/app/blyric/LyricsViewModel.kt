package app.blyric

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.blyric.lyrics.Lyrics
import app.blyric.lyrics.LyricsRepository
import app.blyric.media.NowPlaying
import app.blyric.media.NowPlayingTracker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface LyricsUi {
    data object Idle : LyricsUi
    data object Loading : LyricsUi
    data object NotFound : LyricsUi
    data class Ready(val lyrics: Lyrics) : LyricsUi
}

class LyricsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LyricsRepository(app)

    val nowPlaying: StateFlow<NowPlaying?> = NowPlayingTracker.state

    private val _ui = MutableStateFlow<LyricsUi>(LyricsUi.Idle)
    val ui: StateFlow<LyricsUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            // Muat ulang lirik hanya saat lagu berganti (bukan tiap update posisi).
            NowPlayingTracker.state
                .map { np -> np?.track?.let { it.title to it.artist } }
                .distinctUntilChanged()
                .collectLatest { key ->
                    val track = NowPlayingTracker.state.value?.track
                    if (key == null || track == null) {
                        _ui.value = LyricsUi.Idle
                        return@collectLatest
                    }
                    _ui.value = LyricsUi.Loading
                    val lyrics = repo.load(track)
                    _ui.value = if (lyrics != null) LyricsUi.Ready(lyrics) else LyricsUi.NotFound
                }
        }
    }
}
