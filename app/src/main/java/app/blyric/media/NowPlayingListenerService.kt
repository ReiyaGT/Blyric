package app.blyric.media

import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import app.blyric.lyrics.TrackInfo

/**
 * Cukup dengan izin "Akses notifikasi", Android mengizinkan app ini membaca MediaSession
 * aktif milik Poweramp, YouTube Music, Spotify, dll — tanpa mic, tanpa root.
 */
class NowPlayingListenerService : NotificationListenerService() {
    private var sessionManager: MediaSessionManager? = null
    private var component: ComponentName? = null
    private var active: List<MediaController> = emptyList()
    private val callbacks = HashMap<MediaController, MediaController.Callback>()

    // Thumbnail sampul di-cache per lagu supaya tidak diolah ulang di setiap perubahan status play/pause.
    private var artKey: String? = null
    private var artThumb: Bitmap? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        onSessions(list.orEmpty())
    }

    override fun onListenerConnected() {
        val cn = ComponentName(this, NowPlayingListenerService::class.java)
        val sm = getSystemService(MediaSessionManager::class.java)
        component = cn
        sessionManager = sm
        sm.addOnActiveSessionsChangedListener(sessionsListener, cn)
        onSessions(sm.getActiveSessions(cn))
    }

    override fun onListenerDisconnected() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        clearCallbacks()
        NowPlayingTracker.update(null, null)
    }

    private fun clearCallbacks() {
        callbacks.forEach { (c, cb) -> c.unregisterCallback(cb) }
        callbacks.clear()
    }

    private fun onSessions(list: List<MediaController>) {
        clearCallbacks()
        active = list
        list.forEach { c ->
            val cb = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
                override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
                override fun onSessionDestroyed() = refresh()
            }
            c.registerCallback(cb)
            callbacks[c] = cb
        }
        refresh()
    }

    private fun refresh() {
        val chosen = active.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING && it.metadata != null
        } ?: active.firstOrNull {
            it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) != null
        }
        val meta = chosen?.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (chosen == null || meta == null || title.isNullOrBlank()) {
            NowPlayingTracker.update(null, null)
            return
        }
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
        val st = chosen.playbackState
        val now = SystemClock.elapsedRealtime()
        val key = "$title|$artist|${meta.getString(MediaMetadata.METADATA_KEY_ALBUM)}"
        if (key != artKey) {
            artKey = key
            artThumb = null
        }
        // Sebagian app mengirim sampul sedikit terlambat; selama masih null, coba lagi di refresh berikutnya.
        if (artThumb == null) artThumb = thumbnail(meta)
        NowPlayingTracker.update(
            NowPlaying(
                track = TrackInfo(
                    title = title,
                    artist = artist,
                    album = meta.getString(MediaMetadata.METADATA_KEY_ALBUM),
                    durationMs = meta.getLong(MediaMetadata.METADATA_KEY_DURATION),
                    packageName = chosen.packageName,
                ),
                isPlaying = st?.state == PlaybackState.STATE_PLAYING,
                positionMs = (st?.position ?: 0L).coerceAtLeast(0L),
                speed = st?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
                updatedAtMs = st?.lastPositionUpdateTime?.takeIf { it > 0 } ?: now,
                artwork = artThumb,
            ),
            chosen,
        )
    }

    private fun thumbnail(meta: MediaMetadata): Bitmap? {
        val src = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null
        return try {
            // Salinan software berukuran kecil: aman dibaca piksel-nya dan tidak ikut di-recycle oleh app musik.
            Bitmap.createScaledBitmap(src, THUMB_SIZE, THUMB_SIZE, true)
                .let { if (it === src) it.copy(Bitmap.Config.ARGB_8888, false) else it }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val THUMB_SIZE = 48
    }
}
