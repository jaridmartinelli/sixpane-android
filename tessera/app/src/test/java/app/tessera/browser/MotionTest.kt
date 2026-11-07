package app.tessera.browser

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every UI transition must finish within 0.6 s (user requirement). */
class MotionTest {
    @Test fun codeDurationsWithinLimit() {
        Motion.all.forEach { assertTrue("animation of ${it}ms exceeds ${Motion.MAX_MS}ms", it in 1..Motion.MAX_MS) }
    }

    @Test fun popAndPressPairsWithinLimit() {
        assertTrue(Motion.PRESS_MS + Motion.RELEASE_MS <= Motion.MAX_MS)
        assertTrue(Motion.POP_MS <= Motion.MAX_MS)
    }

    @Test fun windowAnimationResourcesWithinLimit() {
        val dir = listOf(File("src/main/res/anim"), File("app/src/main/res/anim")).first { it.isDirectory }
        val files = dir.listFiles { f -> f.name.endsWith(".xml") }!!
        assertTrue("expected window animation files", files.size >= 4)
        files.forEach { f ->
            val durations = Regex("android:duration=\"(\\d+)\"").findAll(f.readText()).map { it.groupValues[1].toLong() }.toList()
            assertTrue("${f.name} has no durations", durations.isNotEmpty())
            durations.forEach { assertTrue("${f.name}: ${it}ms exceeds ${Motion.MAX_MS}ms", it <= Motion.MAX_MS) }
        }
    }

    @Test fun noHardCodedLongDurationsInSource() {
        val src = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val bad = mutableListOf<String>()
        src.walkTopDown().filter { it.name.endsWith(".kt") && it.name != "Motion.kt" }.forEach { f ->
            Regex("""setDuration\((\d+)L?\)|duration = (\d+)L?\b""").findAll(f.readText()).forEach { m ->
                val ms = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toLong()
                // The reload spinner's 800 ms is one turn of a repeating status indicator, not a transition.
                if (ms > Motion.MAX_MS && !(f.name == "PullHost.kt" && ms == 800L)) bad += "${f.name}: $ms ms"
            }
        }
        assertTrue("transitions longer than 0.6 s: $bad", bad.isEmpty())
    }
}
