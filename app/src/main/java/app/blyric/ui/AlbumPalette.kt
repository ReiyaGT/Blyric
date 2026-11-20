package app.blyric.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Tiga warna gradient latar (atas, tengah, bawah) yang diturunkan dari sampul album. */
@Immutable
data class AlbumPalette(val top: Color, val mid: Color, val bottom: Color) {
    companion object {
        private val FallbackTop = Color(0xFF123A8C)
        private val FallbackBottom = Color(0xFF0A1226)

        /** Tema bawaan Blyric (sama seperti gradient sebelum ada warna dinamis). */
        val Fallback = AlbumPalette(FallbackTop, lerp(FallbackTop, FallbackBottom, 0.5f), FallbackBottom)

        /** Memproses thumbnail kecil (±48x48); panggil dari thread latar. */
        fun from(bitmap: Bitmap): AlbumPalette {
            val w = bitmap.width
            val h = bitmap.height
            if (w <= 0 || h <= 0) return Fallback
            return try {
                val pixels = IntArray(w * h)
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                PaletteMath.fromPixels(pixels)?.let { AlbumPalette(Color(it.top), Color(it.mid), Color(it.bottom)) }
                    ?: Fallback
            } catch (e: Exception) {
                Fallback
            }
        }
    }
}

/**
 * Latar gradient vertikal berwarna album. Perpindahan warna dianimasikan (±1 dtk) dan hanya dibaca di fase gambar,
 * jadi animasi tidak memicu recomposition seluruh layar.
 */
@Composable
fun Modifier.albumBackground(palette: AlbumPalette): Modifier {
    val spec = tween<Color>(durationMillis = 1000, easing = FastOutSlowInEasing)
    val top = animateColorAsState(palette.top, spec, label = "bgTop")
    val mid = animateColorAsState(palette.mid, spec, label = "bgMid")
    val bottom = animateColorAsState(palette.bottom, spec, label = "bgBottom")
    return this.fillMaxSize().drawBehind {
        drawRect(
            Brush.verticalGradient(
                colorStops = arrayOf(0f to top.value, 0.5f to mid.value, 1f to bottom.value),
            ),
        )
    }
}
