package com.sixpane.app
import org.junit.Assert.*
import org.junit.Test
class FramePaletteTest {
    @Test fun urlQueriesMatchAndHideOtherHosts(){
        assertTrue(FramePalette.matches("https://www.Google.com/maps",FramePalette.colors[0],"google.com",emptySet()))
        assertFalse(FramePalette.matches("https://example.com",FramePalette.colors[0],"google.com",emptySet()))
        assertFalse(FramePalette.matches("https://notgoogle.com",FramePalette.colors[0],"google.com",emptySet()))
        assertFalse(FramePalette.matches("https://example.com/?ref=google.com",FramePalette.colors[0],"google.com",emptySet()))
        assertTrue(FramePalette.matches("https://mail.google.com",FramePalette.colors[0],"google.com",emptySet()))
        assertTrue(FramePalette.matches("https://example.com",FramePalette.colors[0],"",emptySet()))
    }
    @Test fun colorTermsMatchFrameNotUrl(){
        assertTrue(FramePalette.matches("https://example.com",FramePalette.colors[1],"blue",emptySet()))
        assertFalse(FramePalette.matches("https://blue.example",FramePalette.colors[2],"blue",emptySet()))
        assertTrue(FramePalette.matches("about:blank",FramePalette.colors[2],"blue, teal",emptySet()))
        assertFalse(FramePalette.matches("about:blank",FramePalette.colors[4],"blue, teal",emptySet()))
    }
    @Test fun sixSelectedColorsAreORAndUrlCanFurtherNarrow(){
        val chosen=FramePalette.names.take(6).toSet()
        assertTrue(FramePalette.validSelection(chosen))
        assertFalse(FramePalette.validSelection(FramePalette.names.take(7).toSet()))
        assertTrue(FramePalette.matches("https://example.com",FramePalette.colors[4],"",chosen))
        assertFalse(FramePalette.matches("https://example.com",FramePalette.colors[7],"",chosen))
        assertTrue(FramePalette.matches("https://google.com",FramePalette.colors[2],"google.com",chosen))
        assertFalse(FramePalette.matches("https://bing.com",FramePalette.colors[2],"google.com",chosen))
        assertTrue(FramePalette.matches("about:blank",FramePalette.colors[7],"green",chosen))
    }
}
