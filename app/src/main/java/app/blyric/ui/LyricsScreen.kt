package app.blyric.ui

import android.content.Intent
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import app.blyric.Prefs
import app.blyric.lyrics.LyricLine
import app.blyric.lyrics.Lyrics
import app.blyric.lyrics.Romanizer
import app.blyric.lyrics.SyncLevel
import app.blyric.media.NowPlaying
import app.blyric.media.NowPlayingTracker
import app.blyric.media.hasNotificationAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

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
    val keepScreenOnPref by Prefs.keepScreenOn.collectAsStateWithLifecycle()
    val romanize by Prefs.romanize.collectAsStateWithLifecycle()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

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

    val nowPlaying = np
    val state = ui
    KeepScreenOn(keepScreenOnPref && nowPlaying?.isPlaying == true)

    val ready = state as? LyricsUi.Ready
    val showOffset = nowPlaying != null && ready != null && ready.lyrics.sync != SyncLevel.UNSYNCED
    val source = ready?.lyrics?.source.orEmpty()

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF123A8C), Color(0xFF0A1226))))
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
    ) {
        if (landscape) {
            // Landscape: panel kiri (judul + kontrol), lirik memakai seluruh tinggi layar di kanan
            // sehingga baris aktif benar-benar berada di tengah.
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(0.4f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Header(nowPlaying, onOpenSettings, Modifier.fillMaxWidth())
                    Spacer(Modifier.weight(1f))
                    if (nowPlaying != null) PlayerControls(nowPlaying.isPlaying, compact = true)
                    if (showOffset) OffsetControls(offsetMs, source, compact = true) { offsetMs += it }
                    Spacer(Modifier.weight(1f))
                }
                LyricsArea(
                    hasAccess = hasAccess,
                    hasTrack = nowPlaying != null,
                    ui = state,
                    positionMs = positionMs,
                    offsetMs = offsetMs,
                    romanize = romanize,
                    landscape = true,
                    modifier = Modifier.weight(0.6f).fillMaxHeight(),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Header(nowPlaying, onOpenSettings, Modifier.fillMaxWidth())
                LyricsArea(
                    hasAccess = hasAccess,
                    hasTrack = nowPlaying != null,
                    ui = state,
                    positionMs = positionMs,
                    offsetMs = offsetMs,
                    romanize = romanize,
                    landscape = false,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                if (nowPlaying != null) {
                    PlayerControls(nowPlaying.isPlaying, Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp))
                }
                if (showOffset) OffsetControls(offsetMs, source, compact = false) { offsetMs += it }
            }
        }
    }
}

/** Menjaga layar tetap menyala selama [enabled]; otomatis dilepas saat layar ini keluar dari komposisi. */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun Header(np: NowPlaying?, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(start = 24.dp, end = 8.dp, top = 12.dp),
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
}

@Composable
private fun OffsetControls(offsetMs: Long, source: String, compact: Boolean, onChange: (Long) -> Unit) {
    val label = "Offset ${if (offsetMs > 0) "+" else ""}$offsetMs ms · $source"
    if (compact) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onChange(-100) }) { Text("−100 ms") }
                TextButton(onClick = { onChange(100) }) { Text("+100 ms") }
            }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onChange(-100) }) { Text("−100 ms") }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { onChange(100) }) { Text("+100 ms") }
        }
    }
}

@Composable
private fun LyricsArea(
    hasAccess: Boolean,
    hasTrack: Boolean,
    ui: LyricsUi,
    positionMs: Long,
    offsetMs: Long,
    romanize: Boolean,
    landscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Box(modifier) {
        when {
            !hasAccess -> Message(
                "Blyric perlu akses notifikasi untuk membaca lagu yang sedang diputar di app musik lain.",
                "Beri akses",
            ) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

            !hasTrack -> Message("Putar lagu di Poweramp, YouTube Music, Spotify, atau app musik lain.")

            else -> when (ui) {
                LyricsUi.Idle, LyricsUi.Loading -> Message("Mencari lirik…")
                LyricsUi.NotFound -> Message("Lirik tidak ditemukan untuk lagu ini.")
                is LyricsUi.Ready -> LyricsList(ui.lyrics, positionMs, offsetMs, romanize, landscape)
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
private fun LyricsList(lyrics: Lyrics, positionMs: Long, offsetMs: Long, romanize: Boolean, landscape: Boolean) {
    // Romanisasi dihitung di luar main thread. Pair (lirik asal, hasil) dipakai supaya hasil
    // lagu sebelumnya tidak pernah tampil di lagu yang baru.
    val annotated by produceState<Pair<Lyrics, Lyrics>?>(initialValue = null, key1 = lyrics, key2 = romanize) {
        value = if (romanize) {
            Pair(lyrics, withContext(Dispatchers.Default) { Romanizer.annotate(lyrics) })
        } else {
            null
        }
    }
    val shown = annotated?.takeIf { it.first === lyrics }?.second ?: lyrics

    val listState = rememberLazyListState()
    val lines = shown.lines
    val synced = shown.sync != SyncLevel.UNSYNCED
    val currentIndex = if (synced) lines.indexOfLast { !it.isBackground && it.startMs <= positionMs } else -1

    // Baris aktif diletakkan di sekitar [anchor] dari tinggi area lirik: tengah di landscape,
    // sedikit di atas tengah di portrait.
    val anchor = if (landscape) 0.5f else 0.4f
    val fallbackItemPx = with(LocalDensity.current) { 48.dp.roundToPx() }

    LaunchedEffect(lyrics, shown, currentIndex, anchor) {
        if (currentIndex < 0) {
            listState.scrollToItem(0)
        } else {
            listState.centerOn(currentIndex, fallbackItemPx)
        }
    }

    val mainSize = if (landscape) 26.sp else 30.sp
    val bgSize = if (landscape) 18.sp else 20.sp

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Padding atas/bawah sebesar sisi viewport agar baris pertama dan terakhir juga bisa mencapai anchor.
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = viewportHeight * anchor,
                bottom = viewportHeight * (1f - anchor) + 72.dp,
            ),
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
                        LineText(line, pos, bgSize, FontWeight.Medium, FontStyle.Italic, seek.padding(bottom = 12.dp))
                    }
                } else {
                    LineText(line, pos, mainSize, FontWeight.Bold, FontStyle.Normal, seek.padding(vertical = 10.dp))
                }
            }
        }
    }
}

/**
 * Menggulir supaya item [index] berada di garis anchor (ditentukan oleh contentPadding atas).
 * Dengan contentPadding atas = anchor * tinggi viewport, scrollOffset 0 menaruh *awal* item di anchor;
 * menggeser setengah tinggi item ke atas membuat *tengah* item yang berada di anchor.
 */
private suspend fun LazyListState.centerOn(index: Int, fallbackItemPx: Int) {
    val size = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: fallbackItemPx
    animateScrollToItem(index, size / 2)
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

    Column(Modifier.fillMaxWidth().then(modifier)) {
        Text(text, fontSize = size, fontWeight = weight, fontStyle = style, lineHeight = size * 1.2f)
        line.roman?.let { roman ->
            val romanColor = when (pos) {
                UPCOMING -> Color.White.copy(alpha = 0.26f)
                PAST -> Color.White.copy(alpha = 0.42f)
                else -> Color.White.copy(alpha = 0.78f)
            }
            Text(
                roman,
                color = romanColor,
                fontSize = size * 0.55f,
                fontWeight = FontWeight.Medium,
                fontStyle = style,
                lineHeight = size * 0.7f,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
