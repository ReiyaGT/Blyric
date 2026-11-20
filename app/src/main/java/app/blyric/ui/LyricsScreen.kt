package app.blyric.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import app.blyric.SourceUi
import app.blyric.lyrics.LyricLine
import app.blyric.lyrics.Lyrics
import app.blyric.lyrics.Romanizer
import app.blyric.lyrics.SyncLevel
import app.blyric.media.NowPlaying
import app.blyric.media.NowPlayingTracker
import app.blyric.media.hasNotificationAccess
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

// Status posisi per baris. Baris yang tidak aktif menerima konstanta sehingga Compose bisa
// men-skip recomposition (hanya baris aktif yang di-update tiap frame).
private const val UPCOMING = -1L
private const val STATIC = -2L
private const val PAST = Long.MAX_VALUE

private val SwitcherHeight = 44.dp

// Transisi lirik -> kontrol playback (portrait): panjang zona fade dan blur maksimum per baris (API 31+).
private val BottomFadeLength = 88.dp
private val LandscapeBottomFade = 56.dp
private val TopFadeLength = 20.dp
private val MaxLineBlur = 5.dp

@Composable
fun LyricsScreen(vm: LyricsViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val np by vm.nowPlaying.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val sourceUi by vm.source.collectAsStateWithLifecycle()
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
    val showSwitcher = hasAccess && nowPlaying != null && sourceUi.selected != null &&
        sourceUi.sources.size > 1 && (ready != null || state is LyricsUi.Missing)

    // Latar gradient (warna album) digambar di MainActivity, di belakang system bars; di sini hanya inset.
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
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
                    if (nowPlaying != null) {
                        SourceSwitcherSlot(showSwitcher, sourceUi, compact = true) { vm.switchSource(it) }
                        PlayerControls(nowPlaying.isPlaying, compact = true)
                    }
                    if (showOffset) OffsetControls(offsetMs, compact = true) { offsetMs += it }
                    Spacer(Modifier.weight(1f))
                }
                LyricsArea(
                    hasAccess = hasAccess,
                    hasTrack = nowPlaying != null,
                    ui = state,
                    switching = sourceUi.switching,
                    positionMs = positionMs,
                    offsetMs = offsetMs,
                    romanize = romanize,
                    landscape = true,
                    bottomObscured = 0.dp,
                    fadeLength = LandscapeBottomFade,
                    modifier = Modifier.weight(0.6f).fillMaxHeight(),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Header(nowPlaying, onOpenSettings, Modifier.fillMaxWidth())

                // Lirik mengisi seluruh area di bawah header, termasuk di belakang kontrol playback. Kontrol
                // ditumpuk di bawahnya; zona transisi (fade + blur ringan) ada di dalam LyricsList, sehingga
                // lirik memudar halus sebelum menyentuh tombol, bukan terpotong lurus.
                var controlsHeightPx by remember { mutableIntStateOf(0) }
                val controlsHeight = with(LocalDensity.current) { controlsHeightPx.toDp() }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LyricsArea(
                        hasAccess = hasAccess,
                        hasTrack = nowPlaying != null,
                        ui = state,
                        switching = sourceUi.switching,
                        positionMs = positionMs,
                        offsetMs = offsetMs,
                        romanize = romanize,
                        landscape = false,
                        bottomObscured = controlsHeight,
                        fadeLength = BottomFadeLength,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .onSizeChanged { controlsHeightPx = it.height },
                    ) {
                        if (nowPlaying != null) {
                            PlayerControls(nowPlaying.isPlaying, Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (showOffset) {
                                    TextButton(
                                        onClick = { offsetMs -= 100 },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                    ) { Text("−100 ms") }
                                }
                                SourceSwitcherSlot(
                                    showSwitcher,
                                    sourceUi,
                                    compact = true,
                                    modifier = Modifier.width(208.dp),
                                ) { vm.switchSource(it) }
                                if (showOffset) {
                                    TextButton(
                                        onClick = { offsetMs += 100 },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                    ) { Text("+100 ms") }
                                }
                            }
                            if (showOffset) {
                                Text(
                                    "Offset ${if (offsetMs > 0) "+" else ""}$offsetMs ms",
                                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
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

/**
 * Slot tinggi tetap untuk [SourceSwitcher]: kontrol muncul/hilang dengan fade tanpa menggeser lirik.
 */
@Composable
private fun SourceSwitcherSlot(
    visible: Boolean,
    sourceUi: SourceUi,
    compact: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onSwitch: (Int) -> Unit,
) {
    Box(modifier.height(SwitcherHeight), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
            SourceSwitcher(
                label = sourceUi.selected.orEmpty(),
                switching = sourceUi.switching,
                labelWidth = if (compact) 112.dp else 148.dp,
                onPrevious = { onSwitch(-1) },
                onNext = { onSwitch(1) },
            )
        }
    }
}

/** Kontrol minimalis `‹  SUMBER  ›`: nama sumber di tengah (bergeser sesuai arah), chevron kiri/kanan sebagai tombol. */
@Composable
private fun SourceSwitcher(
    label: String,
    switching: Boolean,
    labelWidth: Dp,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    var direction by remember { mutableIntStateOf(1) }
    val labelAlpha by animateFloatAsState(if (switching) 0.5f else 0.92f, tween(200), label = "sourceAlpha")
    Row(
        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChevronButton(pointsLeft = true, description = "Sumber lirik sebelumnya") {
            direction = -1
            onPrevious()
        }
        // Lebar label tetap + clip: ganti nama sumber tidak mengubah ukuran kontrol maupun layout di bawahnya.
        Box(Modifier.width(labelWidth).clipToBounds(), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = label,
                transitionSpec = {
                    val d = direction
                    (slideInHorizontally(tween(260)) { d * it / 3 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(260)) { -d * it / 3 } + fadeOut(tween(160)))
                },
                label = "sourceLabel",
            ) { name ->
                Text(
                    name.uppercase(),
                    color = Color.White.copy(alpha = labelAlpha),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ChevronButton(pointsLeft = false, description = "Sumber lirik berikutnya") {
            direction = 1
            onNext()
        }
    }
}

@Composable
private fun ChevronButton(pointsLeft: Boolean, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 48.dp, height = SwitcherHeight)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 8.dp, height = 14.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                if (pointsLeft) {
                    moveTo(w, 0f); lineTo(0f, h / 2f); lineTo(w, h)
                } else {
                    moveTo(0f, 0f); lineTo(w, h / 2f); lineTo(0f, h)
                }
            }
            drawPath(
                path,
                Color.White.copy(alpha = 0.85f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
private fun OffsetControls(offsetMs: Long, compact: Boolean, onChange: (Long) -> Unit) {
    val label = "Offset ${if (offsetMs > 0) "+" else ""}$offsetMs ms"
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
    switching: Boolean,
    positionMs: Long,
    offsetMs: Long,
    romanize: Boolean,
    landscape: Boolean,
    bottomObscured: Dp,
    fadeLength: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Saat sumber lirik diganti, lirik lama tetap tampil tapi diredupkan sampai yang baru siap (tanpa lompatan layout).
    val dim = animateFloatAsState(if (switching) 0.35f else 1f, tween(200), label = "lyricsDim")
    Box(modifier) {
        when {
            !hasAccess -> Message(
                "Blyric perlu akses notifikasi untuk membaca lagu yang sedang diputar di app musik lain.",
                "Beri akses",
            ) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

            !hasTrack -> Message("Putar lagu di Poweramp, YouTube Music, Spotify, atau app musik lain.")

            else -> Crossfade(targetState = ui, animationSpec = tween(300), label = "lyricsContent") { shown ->
                when (shown) {
                    LyricsUi.Idle, LyricsUi.Loading -> Message("Mencari lirik…")
                    LyricsUi.NotFound -> Message("Lirik tidak ditemukan untuk lagu ini.")
                    is LyricsUi.Missing -> Message("Lirik tidak tersedia di ${shown.source}.")
                    is LyricsUi.Ready -> LyricsList(
                        lyrics = shown.lyrics,
                        positionMs = positionMs,
                        offsetMs = offsetMs,
                        romanize = romanize,
                        landscape = landscape,
                        bottomObscured = bottomObscured,
                        fadeLength = fadeLength,
                        modifier = Modifier.graphicsLayer { alpha = dim.value },
                    )
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
private fun LyricsList(
    lyrics: Lyrics,
    positionMs: Long,
    offsetMs: Long,
    romanize: Boolean,
    landscape: Boolean,
    bottomObscured: Dp,
    fadeLength: Dp,
    modifier: Modifier = Modifier,
) {
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

    // Baris aktif diletakkan di sekitar [anchor] dari tinggi area lirik yang *terlihat*: tengah di landscape,
    // sedikit di atas tengah di portrait. Area di belakang kontrol playback tidak dihitung.
    val anchor = if (landscape) 0.5f else 0.4f
    val fallbackItemPx = with(LocalDensity.current) { 48.dp.roundToPx() }

    // Instance daftar yang baru (lagu/sumber baru) langsung loncat ke baris aktif tanpa animasi scroll dari atas;
    // perpindahan baris berikutnya baru dianimasikan.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(lyrics, shown, currentIndex, anchor) {
        if (currentIndex < 0) {
            listState.scrollToItem(0)
        } else {
            if (!placed) listState.scrollToItem(currentIndex, fallbackItemPx / 2)
            listState.centerOn(currentIndex, fallbackItemPx)
        }
        placed = true
    }

    val mainSize = if (landscape) 26.sp else 30.sp
    val bgSize = if (landscape) 18.sp else 20.sp

    val density = LocalDensity.current
    val obscuredPx = with(density) { bottomObscured.toPx() }
    val fadeLenPx = with(density) { fadeLength.toPx() }
    val topFadePx = with(density) { TopFadeLength.toPx() }
    val tailPx = with(density) { 4.dp.toPx() }
    val maxBlurPx = with(density) { MaxLineBlur.toPx() }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            // Mask alfa (bukan warna): teks memudar menjadi transparan sehingga gradient latar tetap terlihat apa
            // adanya di belakangnya, di semua versi Android. Atas: fade pendek. Bawah: fade panjang yang selesai
            // tepat di tepi atas kontrol playback.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val h = size.height
                if (h > 0f) {
                    val top = (topFadePx / h).coerceIn(0f, 1f)
                    val end = ((h - obscuredPx + tailPx) / h).coerceIn(top, 1f)
                    val start = ((h - obscuredPx - fadeLenPx) / h).coerceIn(top, end)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                top to Color.Black,
                                start to Color.Black,
                                end to Color.Transparent,
                                1f to Color.Transparent,
                            ),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
    ) {
        val viewportHeight = maxHeight
        val topPadding = (viewportHeight - bottomObscured).coerceAtLeast(0.dp) * anchor
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Padding atas/bawah supaya baris pertama dan terakhir juga bisa mencapai anchor (dan lirik terakhir
            // tetap bisa di-scroll sampai keluar dari zona fade).
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = topPadding,
                bottom = viewportHeight - topPadding + 72.dp,
            ),
        ) {
            itemsIndexed(lines) { index, line ->
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
                // Baris beromanisasi sedikit lebih lega supaya teks asal + romanisasi tidak terasa padat.
                val vertical = if (line.roman != null) 13.dp else 10.dp

                Box(Modifier.fillMaxWidth().bottomFadeBlur(listState, index, obscuredPx, fadeLenPx, maxBlurPx)) {
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
                        LineText(line, pos, mainSize, FontWeight.Bold, FontStyle.Normal, seek.padding(vertical = vertical))
                    }
                }
            }
        }
    }
}

/**
 * Blur ringan per baris untuk baris yang masuk zona transisi di atas kontrol playback (hanya Android 12+,
 * RenderEffect). Makin dekat ke kontrol makin kabur (4 tingkat, maks [maxBlurPx]); baris di area baca utama
 * tidak disentuh. Di Android lebih lama efek ini dilewati dan fade alfa saja yang bekerja.
 * Dibaca di fase layer, jadi tidak memicu recomposition saat scroll.
 */
private fun Modifier.bottomFadeBlur(
    state: LazyListState,
    index: Int,
    obscuredPx: Float,
    fadeLenPx: Float,
    maxBlurPx: Float,
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || fadeLenPx <= 0f) return this
    return graphicsLayer {
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return@graphicsLayer
        val fadeEnd = info.viewportSize.height - obscuredPx
        val centerY = (item.offset - info.viewportStartOffset) + item.size / 2f
        val progress = ((centerY - (fadeEnd - fadeLenPx)) / fadeLenPx).coerceIn(0f, 1f)
        val level = (progress * 4f).toInt()
        if (level > 0) {
            val radius = maxBlurPx * level / 4f
            renderEffect = BlurEffect(radius, radius, TileMode.Decal)
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

    // animateContentSize: romanisasi yang baru selesai dihitung / dinyalakan menambah tinggi baris dengan halus.
    Column(Modifier.fillMaxWidth().then(modifier).animateContentSize()) {
        Text(text, fontSize = size, fontWeight = weight, fontStyle = style, lineHeight = size * 1.2f)
        line.roman?.let { roman ->
            // Dibedakan dari teks asal lewat ukuran (±½), bobot normal (asal: bold), jarak huruf, dan opasitas lebih
            // rendah; tetap mengikuti status baris (aktif / akan datang / lewat).
            val romanColor = when (pos) {
                UPCOMING -> Color.White.copy(alpha = 0.30f)
                PAST -> Color.White.copy(alpha = 0.45f)
                else -> Color.White.copy(alpha = 0.80f)
            }
            Text(
                roman,
                color = romanColor,
                fontSize = size * 0.52f,
                fontWeight = FontWeight.Normal,
                fontStyle = style,
                letterSpacing = 0.3.sp,
                lineHeight = size * 0.7f,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
