package com.sixpane.app

import android.webkit.WebView
import android.webkit.WebSettings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** RUN ON A CLEAN TEST INSTALL ONLY. Creates two isolated persistent test profiles. */
@RunWith(AndroidJUnit4::class)
class ProfileIsolationInstrumentedTest {

    @Test fun bothDisplayModesKeepGenuineSystemWebViewUserAgent(){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val ctx=instrumentation.targetContext
        lateinit var expected:String
        instrumentation.runOnMainSync{
            expected=WebSettings.getDefaultUserAgent(ctx)
            val w=WebView(ctx)
            try {
                assertEquals(expected,w.settings.userAgentString)
                for(desktop in listOf(false,true,false)){
                    w.settings.useWideViewPort=true
                    w.settings.loadWithOverviewMode=!desktop
                    assertEquals("Desktop mode must only change viewport behavior",expected,w.settings.userAgentString)
                }
            } finally {w.destroy()}
        }
    }
    @Test fun cookieManagersNeverLeakBetweenNamedProfiles(){
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val ctx=instrumentation.targetContext
        val origin="https://appassets.androidplatform.net/"
        val nameA="qa-${UUID.randomUUID()}"
        val nameB="qa-${UUID.randomUUID()}"
        lateinit var wa:WebView
        lateinit var wb:WebView
        instrumentation.runOnMainSync {
            wa=WebView(ctx);WebViewCompat.setProfile(wa,nameA)
            wb=WebView(ctx);WebViewCompat.setProfile(wb,nameB)
            val ca=WebViewCompat.getProfile(wa).cookieManager
            val cb=WebViewCompat.getProfile(wb).cookieManager
            ca.setCookie(origin,"sixpane_isolation=only_a; Path=/; Secure")
            cb.setCookie(origin,"sixpane_isolation=only_b; Path=/; Secure")
            ca.flush();cb.flush()
            assertTrue(ca.getCookie(origin).orEmpty().contains("sixpane_isolation=only_a"))
            assertTrue(cb.getCookie(origin).orEmpty().contains("sixpane_isolation=only_b"))
            assertFalse(ca.getCookie(origin).orEmpty().contains("only_b"))
            assertFalse(cb.getCookie(origin).orEmpty().contains("only_a"))
            wa.destroy();wb.destroy()
        }
        // Retire only test profiles; loaded WebKit profile objects may require clean process restart.
        // The clean test install must be cleared/uninstalled after running QA.
    }
}
