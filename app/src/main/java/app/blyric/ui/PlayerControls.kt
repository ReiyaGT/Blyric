package app.blyric.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.blyric.media.NowPlayingTracker

/** Tombol Sebelumnya / Jeda-Lanjut / Berikutnya yang mengendalikan app musik lewat MediaController. */
@Composable
fun PlayerControls(isPlaying: Boolean, modifier: Modifier = Modifier, compact: Boolean = false) {
    val side = if (compact) 44.dp else 52.dp
    val main = if (compact) 56.dp else 64.dp
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlButton(side, false, "Lagu sebelumnya", { NowPlayingTracker.skipToPrevious() }) { drawPrevious(this, it) }
        ControlButton(main, true, if (isPlaying) "Jeda" else "Putar", { NowPlayingTracker.togglePlayPause() }) {
            if (isPlaying) drawPause(this, it) else drawPlay(this, it)
        }
        ControlButton(side, false, "Lagu berikutnya", { NowPlayingTracker.skipToNext() }) { drawNext(this, it) }
    }
}

@Composable
private fun ControlButton(
    diameter: Dp,
    filled: Boolean,
    description: String,
    onClick: () -> Unit,
    draw: DrawScope.(Color) -> Unit,
) {
    val fg = if (filled) Color(0xFF0A1226) else Color.White
    Box(
        Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.12f))
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(diameter * 0.5f)) { draw(this, fg) }
    }
}

// Ikon digambar manual (kotak 0..1) supaya tidak perlu dependensi material-icons.

private fun DrawScope.triangle(color: Color, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
    val s = this.size.minDimension
    val p = Path()
    p.moveTo(x1 * s, y1 * s)
    p.lineTo(x2 * s, y2 * s)
    p.lineTo(x3 * s, y3 * s)
    p.close()
    drawPath(p, color)
}

private fun DrawScope.bar(color: Color, x0: Float, x1: Float) {
    val s = this.size.minDimension
    drawRect(color, topLeft = Offset(x0 * s, 0.22f * s), size = Size((x1 - x0) * s, 0.56f * s))
}

private fun drawPlay(scope: DrawScope, color: Color) = scope.triangle(color, 0.30f, 0.20f, 0.30f, 0.80f, 0.82f, 0.50f)

private fun drawPause(scope: DrawScope, color: Color) {
    scope.bar(color, 0.26f, 0.42f)
    scope.bar(color, 0.58f, 0.74f)
}

private fun drawNext(scope: DrawScope, color: Color) {
    scope.triangle(color, 0.18f, 0.22f, 0.18f, 0.78f, 0.66f, 0.50f)
    scope.bar(color, 0.70f, 0.82f)
}

private fun drawPrevious(scope: DrawScope, color: Color) {
    scope.triangle(color, 0.82f, 0.22f, 0.82f, 0.78f, 0.34f, 0.50f)
    scope.bar(color, 0.18f, 0.30f)
}
