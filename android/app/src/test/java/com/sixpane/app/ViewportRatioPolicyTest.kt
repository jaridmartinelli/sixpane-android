package com.sixpane.app

import org.junit.Assert.*
import org.junit.Test

class ViewportRatioPolicyTest {
    private val legacySix = listOf(
        96 to 98, 97 to 103, 98 to 102, 102 to 104, 103 to 96, 104 to 97
    )

    @Test fun all42UniqueAcrossSevenWorkspaces() {
        val allocated=mutableListOf<Pair<Int,Int>>()
        repeat(7){
            allocated += ViewportRatioPolicy.allocate(allocated,legacySix)
            assertTrue(ViewportRatioPolicy.allUnique(allocated))
        }
        assertEquals(42,allocated.size)
        assertEquals(42,allocated.map { ViewportRatioPolicy.ratioKey(it.first,it.second) }.toSet().size)
    }

    @Test fun existingUniqueRatiosStayUnchangedOnRestart() {
        val original=ViewportRatioPolicy.allocate(emptyList(), List(42){96 to 98})
        val afterRestart=ViewportRatioPolicy.allocate(emptyList(),original)
        assertEquals(original,afterRestart)
    }

    @Test fun replaceOneOrManyWithoutChangingSurvivingWindows() {
        var all=ViewportRatioPolicy.allocate(emptyList(),List(42){96 to 98})
        for (replacementCount in 1..6) {
            val retired=(0 until replacementCount).map { it*7 }.toSet()
            val reserved=all.filterIndexed{index,_->index !in retired}
            val fresh=ViewportRatioPolicy.allocate(reserved,List(replacementCount){96 to 98})
            assertEquals(reserved,all.filterIndexed{index,_->index !in retired})
            val replacements=fresh.iterator()
            all=all.mapIndexed{index,pair->if(index in retired)replacements.next() else pair}
            assertTrue(ViewportRatioPolicy.allUnique(all))
        }
    }

    @Test fun detectsSameRatioWithDifferentPercentPairs() {
        assertEquals(ViewportRatioPolicy.ratioKey(96,96),ViewportRatioPolicy.ratioKey(104,104))
        val repaired=ViewportRatioPolicy.allocate(emptyList(),listOf(96 to 96,104 to 104))
        assertEquals(96 to 96,repaired[0])
        assertTrue(ViewportRatioPolicy.allUnique(repaired))
    }

    @Test fun invalidSavedPercentagesAreRepairedNotClampedIntoDuplicates() {
        val repaired=ViewportRatioPolicy.allocate(emptyList(),List(42){100 to 100})
        assertEquals(42,repaired.size)
        assertTrue(ViewportRatioPolicy.allUnique(repaired))
        val invalid=ViewportRatioPolicy.allocate(emptyList(),listOf(300 to 100,0 to 100))
        assertTrue(ViewportRatioPolicy.allUnique(invalid))
    }

    @Test fun rejectsOverflowPast42() {
        val reserved=ViewportRatioPolicy.allocate(emptyList(),List(42){100 to 100})
        try {
            ViewportRatioPolicy.allocate(reserved,listOf(100 to 100))
            fail("43rd ratio must be rejected")
        } catch (_:IllegalArgumentException) {}
    }
}
