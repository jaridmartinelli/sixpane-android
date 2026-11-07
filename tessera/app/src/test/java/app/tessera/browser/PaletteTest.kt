package app.tessera.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {
    private fun lum(c: Int): Double {
        fun lin(x: Int): Double { val v = x / 255.0; return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4) }
        return 0.2126 * lin((c shr 16) and 0xFF) + 0.7152 * lin((c shr 8) and 0xFF) + 0.0722 * lin(c and 0xFF)
    }
    private fun contrast(a: Int, b: Int): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }

    @Test fun exactlyTheSixSuppliedColours() {
        assertEquals(listOf(0xFF204DDA, 0xFF59B435, 0xFFB5381C, 0xFFC39533, 0xFF9A9D32, 0xFF7CC5B4).map { it.toInt() }, T.palette.toList())
        assertEquals(6, T.paletteNames.size)
    }

    @Test fun headerTextIsReadableOnEveryTag() {
        T.palette.forEach { c ->
            val ratio = contrast(T.ink(c), c)
            // WCAG large/bold text threshold; header labels are bold 11-14sp.
            assertTrue("ink on #${Integer.toHexString(c)} contrast $ratio", ratio >= 3.0)
        }
    }
}
