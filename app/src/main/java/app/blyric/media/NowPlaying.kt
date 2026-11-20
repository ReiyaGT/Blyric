package app.blyric.media

import android.content.Context
import android.graphics.Bitmap
import android.media.session.MediaController
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import app.blyric.lyrics.TrackInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NowPlaying(
    val track: TrackInfo,
    val isPlaying: Boolean,
    val positionMs: Long,
    val speed: Float,
    /** Waktu (elapsedRealtime) saat [positionMs] diambil. */
    val updatedAtMs: Long,
    /** Thumbnail kecil (48 px) sampul album untuk menentukan warna latar; null bila app musik tidak menyediakannya. */
    val artwork: Bitmap? = null,
) {
    fun currentPosition(now: Long = SystemClock.elapsedRealtime()): Long =
        if (isPlaying) positionMs + ((now - updatedAtMs) * speed).toLong() else positionMs
}

/** Penghubung antara service pembaca sesi media dan UI. */
object NowPlayingTracker {
    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state

    @Volatile private var controller: MediaController? = null

    fun update(np: NowPlaying?, c: MediaController?) {
        controller = c
        _state.value = np
    }

    fun seekTo(ms: Long) {
        controller?.transportControls?.seekTo(ms.coerceAtLeast(0))
    }

    fun skipToPrevious() {
        controller?.transportControls?.skipToPrevious()
    }

    fun skipToNext() {
        controller?.transportControls?.skipToNext()
    }

    /** Jeda bila sedang diputar, lanjutkan bila sedang dijeda. */
    fun togglePlayPause() {
        val c = controller ?: return
        if (_state.value?.isPlaying == true) c.transportControls.pause() else c.transportControls.play()
    }
}

fun hasNotificationAccess(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
