package app.tessera.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridGeometryTest {
    private fun overlap(a: Box, b: Box) = a.l < b.r && b.l < a.r && a.t < b.b && b.t < a.b

    private fun checkTiles(boxes: List<Box>, w: Int, h: Int) {
        assertEquals(6, boxes.size)
        boxes.forEach { assertTrue(it.l >= 0 && it.t >= 0 && it.r <= w && it.b <= h && it.w > 0 && it.h > 0) }
        for (i in boxes.indices) for (j in i + 1 until boxes.size) assertFalse("$i/$j overlap", overlap(boxes[i], boxes[j]))
    }

    @Test fun autoColumns() { assertEquals(2, GridGeometry.autoColumns(1000, 2000)); assertEquals(3, GridGeometry.autoColumns(2000, 1900)) }
    @Test fun grid2x3() { val b = GridGeometry.grid(1000, 2000, 2, 8); checkTiles(b, 1000, 2000); assertEquals(b[0].t, b[1].t); assertTrue(b[2].t > b[0].t) }
    @Test fun grid3x2() { val b = GridGeometry.grid(2000, 1500, 3, 8); checkTiles(b, 2000, 1500); assertEquals(b[0].t, b[2].t); assertTrue(b[3].t > b[0].t) }
    @Test fun gridFillsEdges() { val b = GridGeometry.grid(1001, 2003, 2, 7); assertEquals(1001, b[5].r); assertEquals(2003, b[5].b) }
    @Test fun expandedTallEveryFocus() {
        for (f in 0 until 6) {
            val b = GridGeometry.expanded(1000, 2000, f, 8, 300)
            checkTiles(b, 1000, 2000)
            assertTrue(b.filterIndexed { i, _ -> i != f }.all { it.w * it.h < b[f].w * b[f].h / 4 })
            assertTrue(b.filterIndexed { i, _ -> i != f }.all { it.t > b[f].b })
        }
    }
    @Test fun expandedWideEveryFocus() {
        for (f in 0 until 6) {
            val b = GridGeometry.expanded(2000, 1800, f, 8, 300)
            checkTiles(b, 2000, 1800)
            assertTrue(b.filterIndexed { i, _ -> i != f }.all { it.l > b[f].r })
        }
    }
    @Test fun lerpEnds() { val a = Box(0, 0, 10, 10); val z = Box(10, 20, 30, 40); assertEquals(a, a.lerp(z, 0f)); assertEquals(z, a.lerp(z, 1f)) }
}
