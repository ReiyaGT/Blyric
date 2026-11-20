package app.blyric

import app.blyric.ui.PaletteMath
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteMathTest {
    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun hsl(argb: Int) = FloatArray(3).also { PaletteMath.rgbToHsl(argb, it) }

    @Test fun noUsablePixelsGivesNull() {
        assertNull(PaletteMath.fromPixels(IntArray(100))) // semua transparan
    }

    @Test fun redCoverGivesRedGradient() {
        val c = PaletteMath.fromPixels(IntArray(2000) { argb(200, 30, 30) })
        assertNotNull(c)
        val hue = hsl(c!!.top)[0]
        assertTrue("hue=$hue", hue < 15f || hue > 345f)
    }

    @Test fun greyscaleCoverGetsMutedNeutralTone() {
        val c = PaletteMath.fromPixels(IntArray(2000) { argb(128, 128, 128) })
        assertNotNull(c)
        assertTrue(hsl(c!!.top)[1] < 0.35f)
    }

    @Test fun pureWhiteAndPureBlackCoversStayDark() {
        for (v in intArrayOf(5, 250)) {
            val c = PaletteMath.fromPixels(IntArray(2000) { argb(v, v, v) })
            assertNotNull(c)
            assertTrue(PaletteMath.luminance(c!!.top) <= PaletteMath.MAX_TOP_LUMINANCE + 1e-4f)
        }
    }

    @Test fun topColorAlwaysKeepsContrastWithWhiteText() {
        // Semua hue, termasuk kuning/hijau terang yang paling sulit digelapkan.
        var hue = 0f
        while (hue < 360f) {
            for (l in floatArrayOf(0.35f, 0.5f, 0.7f)) {
                val px = PaletteMath.hslToArgb(hue, 1f, l)
                val c = PaletteMath.fromPixels(IntArray(500) { px })
                assertNotNull(c)
                assertTrue("hue=$hue l=$l", PaletteMath.luminance(c!!.top) <= PaletteMath.MAX_TOP_LUMINANCE + 1e-4f)
            }
            hue += 5f
        }
    }

    @Test fun twoDistinctColorsGiveDifferentMid() {
        val px = IntArray(2500) { if (it < 1500) argb(30, 60, 200) else argb(230, 120, 20) }
        val c = PaletteMath.fromPixels(px)
        assertNotNull(c)
        val topHue = hsl(c!!.top)[0]
        val midHue = hsl(c.mid)[0]
        assertTrue("top=$topHue mid=$midHue", kotlin.math.abs(topHue - midHue) > 45f)
    }
}
