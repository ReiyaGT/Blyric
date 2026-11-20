package app.blyric.ui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Ekstraksi warna dominan dari piksel sampul + penyesuaian otomatis untuk kebutuhan UI.
 * Murni Kotlin (tanpa android.graphics / Compose) supaya bisa diuji lewat unit test JVM.
 *
 * Alur: piksel -> histogram hue (12 bin, diberi bobot saturasi & kecerahan tengah) -> hue dominan (+ hue kedua bila
 * cukup berbeda) -> tiga warna gradient (atas, tengah, bawah) yang dipaksa gelap & tidak terlalu jenuh agar lirik
 * putih tetap terbaca.
 */
internal object PaletteMath {
    private const val BINS = 12
    private const val BIN_WIDTH = 360f / BINS
    private const val HUE_SHIFT = BIN_WIDTH / 2f // supaya merah (≈0°) berada di tengah bin, bukan terbelah di tepi

    /** Luminansi relatif maksimum warna atas: menjaga kontras terhadap teks putih tetap ≥ ±7:1. */
    const val MAX_TOP_LUMINANCE = 0.085f

    /** Hasil: ARGB atas, tengah, bawah. Null bila tidak ada piksel yang bisa dipakai. */
    class Colors(val top: Int, val mid: Int, val bottom: Int)

    fun fromPixels(pixels: IntArray): Colors? {
        val weight = FloatArray(BINS)
        val sumSat = FloatArray(BINS)
        val sumHue = FloatArray(BINS)
        var chromatic = 0f
        var counted = 0
        val hsl = FloatArray(3)

        for (px in pixels) {
            if ((px ushr 24) < 200) continue // abaikan piksel transparan
            counted++
            rgbToHsl(px, hsl)
            val s = hsl[1]
            val l = hsl[2]
            // Hampir abu-abu / hampir putih / hampir hitam tidak membawa "warna" yang bisa dipakai.
            if (s < 0.12f || l < 0.08f || l > 0.93f) continue
            val w = (0.25f + s) * (1f - abs(2f * l - 1f))
            val shifted = (hsl[0] + HUE_SHIFT) % 360f
            val bin = (shifted / BIN_WIDTH).toInt().coerceIn(0, BINS - 1)
            weight[bin] += w
            sumSat[bin] += s * w
            sumHue[bin] += shifted * w
            chromatic += w
        }
        if (counted == 0) return null

        // Sampul (nyaris) hitam-putih: pakai nada netral kebiruan agar tetap selaras dengan identitas Blyric.
        if (chromatic < counted * 0.04f) {
            return Colors(
                top = darkened(222f, 0.22f, 0.22f),
                mid = hslToArgb(222f, 0.20f, 0.14f),
                bottom = hslToArgb(222f, 0.26f, 0.075f),
            )
        }

        var best = 0
        for (i in 1 until BINS) if (weight[i] > weight[best]) best = i
        val hue1 = binHue(sumHue[best], weight[best])
        val sat1 = sumSat[best] / weight[best]

        var second = -1
        for (i in 0 until BINS) {
            if (i == best || weight[i] < weight[best] * 0.35f) continue
            if (hueDistance(binHue(sumHue[i], weight[i]), hue1) < 45f) continue
            if (second == -1 || weight[i] > weight[second]) second = i
        }

        val s1 = sat1.coerceIn(0.40f, 0.65f)
        val top = darkened(hue1, s1, 0.30f)
        val mid = if (second != -1) {
            val h2 = binHue(sumHue[second], weight[second])
            hslToArgb(h2, (sumSat[second] / weight[second]).coerceIn(0.30f, 0.55f), 0.17f)
        } else {
            // Satu warna dominan: turunkan hue sedikit supaya gradient punya kedalaman, bukan satu warna datar.
            hslToArgb((hue1 + 14f) % 360f, (s1 * 0.85f).coerceIn(0.30f, 0.55f), 0.17f)
        }
        val bottom = hslToArgb(hue1, (s1 * 0.6f).coerceIn(0.25f, 0.45f), 0.075f)
        return Colors(top, mid, bottom)
    }

    /** Warna HSL yang diturunkan kecerahannya sampai luminansinya ≤ [MAX_TOP_LUMINANCE]. */
    private fun darkened(h: Float, s: Float, startL: Float): Int {
        var l = startL
        var c = hslToArgb(h, s, l)
        while (luminance(c) > MAX_TOP_LUMINANCE && l > 0.12f) {
            l -= 0.02f
            c = hslToArgb(h, s, l)
        }
        return c
    }

    private fun binHue(sumShifted: Float, w: Float): Float = ((sumShifted / w) - HUE_SHIFT + 360f) % 360f

    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    /** Luminansi relatif WCAG (0..1). */
    fun luminance(argb: Int): Float {
        fun ch(v: Int): Float {
            val c = v / 255f
            return if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        }
        val r = ch((argb shr 16) and 0xFF)
        val g = ch((argb shr 8) and 0xFF)
        val b = ch(argb and 0xFF)
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /** out = [hue 0..360, saturasi 0..1, lightness 0..1]. */
    fun rgbToHsl(argb: Int, out: FloatArray) {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        val d = mx - mn
        var h = 0f
        var s = 0f
        if (d > 1e-6f) {
            s = d / (1f - abs(2f * l - 1f))
            h = when (mx) {
                r -> 60f * (((g - b) / d) % 6f)
                g -> 60f * (((b - r) / d) + 2f)
                else -> 60f * (((r - g) / d) + 4f)
            }
            if (h < 0f) h += 360f
        }
        out[0] = h
        out[1] = s.coerceIn(0f, 1f)
        out[2] = l
    }

    fun hslToArgb(h: Float, s: Float, l: Float): Int {
        val hh = ((h % 360f) + 360f) % 360f
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = hh / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val r1: Float
        val g1: Float
        val b1: Float
        when (hp.toInt()) {
            0 -> { r1 = c; g1 = x; b1 = 0f }
            1 -> { r1 = x; g1 = c; b1 = 0f }
            2 -> { r1 = 0f; g1 = c; b1 = x }
            3 -> { r1 = 0f; g1 = x; b1 = c }
            4 -> { r1 = x; g1 = 0f; b1 = c }
            else -> { r1 = c; g1 = 0f; b1 = x }
        }
        val m = l - c / 2f
        fun to255(v: Float): Int = ((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (to255(r1) shl 16) or (to255(g1) shl 8) or to255(b1)
    }
}
