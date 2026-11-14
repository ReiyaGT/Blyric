package app.blyric.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blyric.LyricsUi
import app.blyric.LyricsViewModel
import app.blyric.lyrics.LyricLine
import app.blyric.lyrics.Lyrics
import app.blyric.lyrics.SyncLevel
import app.blyric.media.NowPlayingTracker
import app.blyric.media.hasNotificationAccess
import kotlinx.coroutines.isActive

// Status posisi per baris. Baris yang tidak aktif menerima konstanta sehingga Compose bisa
// men-skip recomposition (hanya baris aktif yang di-update tiap frame).
private const val UPCOMING = -1L
private const val STATIC = -2L
private const val PAST = Long.MAX_VALUE

@Composable
fun LyricsScreen(vm: LyricsViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val np by vm.nowPlaying.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()

    var hasAccess by remember { mutableStateOf(hasNotificationAccess(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasAccess = hasNotificationAccess(context) }

    var offsetMs by rememberSaveable { mutableStateOf(0L) }
    var positionMs by remember { mutableStateOf(0L) }

    // Jam lirik: posisi dihitung sendiri per frame dari snapshot PlaybackState (bukan polling).
    LaunchedEffect(np, offsetMs) {
        val cur = np
        positionMs = (cur?.currentPosition() ?: 0L) + offsetMs
        while (cur?.isPlaying == true && isActive) {
            androidx.compose.runtime.withFrameNanos { }
            positionMs = cur.currentPosition() + offsetMs
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF123A8C), Color(0xFF0A1226))))
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        np?.track?.title ?: "Blyric",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    np?.track?.artist?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TextButton(onClick = onOpenSettings) { Text("Pengaturan") }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !hasAccess -> Message(
                        "Blyric perlu akses notifikasi untuk membaca lagu yang sedang diputar di app musik lain.",
                        "Beri akses",
                    ) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

                    np == null -> Message("Putar lagu di Poweramp, YouTube Music, Spotify, atau app musik lain.")

                    else -> when (val s = ui) {
                        LyricsUi.Idle, LyricsUi.Loading -> Message("Mencari lirik…")
                        LyricsUi.NotFound -> Message("Lirik tidak ditemukan untuk lagu ini.")
                        is LyricsUi.Ready -> LyricsList(s.lyrics, positionMs, offsetMs)
                    }
                }
            }

            val ready = ui as? LyricsUi.Ready
            if (np != null && ready != null && ready.lyrics.sync != SyncLevel.UNSYNCED) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { offsetMs -= 100 }) { Text("−100 ms") }
                    Text(
                        "Offset ${if (offsetMs > 0) "+" else ""}$offsetMs ms · ${ready.lyrics.source}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { offsetMs += 100 }) { Text("+100 ms") }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, buttonLabel: String? = null, onClick: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (buttonLabel != null && onClick != null) {
            Button(onClick = onClick, modifier = Modifier.padding(top = 16.dp)) { Text(buttonLabel) }
        }
    }
}

@Composable
private fun LyricsList(lyrics: Lyrics, positionMs: Long, offsetMs: Long) {
    val listState = rememberLazyListState()
    val lines = lyrics.lines
    val synced = lyrics.sync != SyncLevel.UNSYNCED
    val currentIndex = if (synced) lines.indexOfLast { !it.isBackground && it.startMs <= positionMs } else -1

    LaunchedEffect(lyrics) { listState.scrollToItem(0) }
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(currentIndex, -(listState.layoutInfo.viewportSize.height / 4))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 96.dp, bottom = 320.dp),
    ) {
        itemsIndexed(lines) { _, line ->
            val pos = when {
                !synced -> STATIC
                positionMs < line.startMs -> UPCOMING
                positionMs > line.endMs + 150 -> PAST
                else -> positionMs
            }
            val seek = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = synced,
            ) { NowPlayingTracker.seekTo(line.startMs - offsetMs) }

            if (line.isBackground) {
                // Vokal latar: muncul saat aktif, hilang setelahnya (bisa tampil bersamaan dengan baris utama).
                AnimatedVisibility(
                    visible = synced && pos != UPCOMING && pos != PAST,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    LineText(line, pos, 20.sp, FontWeight.Medium, FontStyle.Italic, seek.padding(bottom = 12.dp))
                }
            } else {
                LineText(line, pos, 30.sp, FontWeight.Bold, FontStyle.Normal, seek.padding(vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun LineText(
    line: LyricLine,
    pos: Long,
    size: TextUnit,
    weight: FontWeight,
    style: FontStyle,
    modifier: Modifier,
) {
    val bright = Color.White
    val dim = Color.White.copy(alpha = 0.32f)
    val past = Color.White.copy(alpha = 0.55f)

    val text = buildAnnotatedString {
        val wholeLine = line.words.isEmpty() || pos == UPCOMING || pos == PAST || pos == STATIC
        if (wholeLine) {
            val c = when (pos) {
                UPCOMING -> dim
                PAST -> past
                else -> bright
            }
            withStyle(SpanStyle(color = c)) { append(line.text) }
        } else {
            // Sinkron per kata/suku kata: warna naik dari redup ke terang sesuai progres kata.
            line.words.forEach { w ->
                val span = w.endMs - w.startMs
                val p = if (span <= 0) (if (pos >= w.startMs) 1f else 0f)
                else ((pos - w.startMs).toFloat() / span).coerceIn(0f, 1f)
                withStyle(SpanStyle(color = lerp(dim, bright, p))) { append(w.text) }
            }
        }
    }
    Text(text, fontSize = size, fontWeight = weight, fontStyle = style, lineHeight = size * 1.2f, modifier = modifier)
}
