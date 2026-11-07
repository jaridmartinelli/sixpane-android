package com.sixpane.app

import android.util.Log
import android.webkit.WebSettings
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Shared QA-only UA treatment used by PaneCard and PhysicalUaSurfaceTest.
 * The primary experiment intentionally calls UA_ONLY; the real QA PaneCard
 * treatment and the separate metadata arm both call UA_WITH_METADATA.
 * No changes apply to the native control or any non-QA build.
 */
internal object QaUaConfiguration {
    enum class Treatment { NATIVE, UA_ONLY, UA_WITH_METADATA }

    data class Result(
        val treatment: Treatment,
        val originalUa: String,
        val configuredUa: String,
        val mobilePresent: Boolean,
        val mobileInserted: Boolean,
        val metadataSupported: Boolean,
        val metadataResult: String,
        val metadataReadback: String
    ) {
        fun logLine(): String =
            "R8_UA_CONFIG path=shared-PaneCard " +
                "treatment=${treatment.name} mobilePresent=$mobilePresent " +
                "mobileInserted=$mobileInserted feature_USER_AGENT_METADATA=$metadataSupported " +
                "setUserAgentMetadata=$metadataResult readback=$metadataReadback " +
                "configuredUa=$configuredUa"
    }

    fun apply(settings: WebSettings, treatment: Treatment): Result {
        val supported = WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)
        val original = settings.userAgentString.orEmpty()
        var configured = original
        var mobileInserted = false
        if (treatment != Treatment.NATIVE) {
            val stripped = original.replace("; wv)", ")")
            check(original != stripped) { "QA UA treatment requires the WebView ; wv) marker" }
            configured = if (" Mobile Safari/537.36" in stripped) stripped
                else stripped.replace(" Safari/537.36", " Mobile Safari/537.36")
            check(" Mobile Safari/537.36" in configured) {
                "QA Android mobile UA requires Safari/537.36 anchor"
            }
            mobileInserted = configured != stripped
            settings.userAgentString = configured
            configured = settings.userAgentString.orEmpty()
        }

        var result = if (treatment == Treatment.UA_WITH_METADATA) "SKIPPED_UNSUPPORTED"
            else "NOT_REQUESTED"
        var readback = "NOT_READ"
        if (treatment == Treatment.UA_WITH_METADATA && supported) {
            try {
                fun brand(name: String, major: String, full: String) =
                    UserAgentMetadata.BrandVersion.Builder()
                        .setBrand(name)
                        .setMajorVersion(major)
                        .setFullVersion(full)
                        .build()
                val metadata = UserAgentMetadata.Builder()
                    .setBrandVersionList(listOf(
                        brand("Chromium", "156", "156.0.0.0"),
                        brand("Google Chrome", "156", "156.0.0.0"),
                        brand("Not:A-Brand", "8", "8.0.0.0")
                    ))
                    .setFullVersion("156.0.0.0")
                    .setMobile(true)
                    .setPlatform("Android")
                    .setPlatformVersion("16")
                    .setModel("CPH2551")
                    .setArchitecture("")
                    .setBitness(UserAgentMetadata.BITNESS_DEFAULT)
                    .build()
                // This is a void API: returned without exception is not proof
                // that the server actually received or accepted Client Hints.
                WebSettingsCompat.setUserAgentMetadata(settings, metadata)
                result = "SET_RETURNED"
                val actual = WebSettingsCompat.getUserAgentMetadata(settings)
                readback = "mobile=${actual.isMobile};platform=${actual.platform};" +
                    "brands=${actual.brandVersionList.joinToString(",") { it.brand + "/" + it.majorVersion }}"
            } catch (e: Exception) {
                result = "EXCEPTION:${e.javaClass.simpleName}:${e.message.orEmpty()}"
                Log.e("SIXPANE_R8", "setUserAgentMetadata failed", e)
            }
        }

        // Read UA once more AFTER metadata setter and readback, rather than
        // logging only the intended string before the setter was invoked.
        val finalUa = settings.userAgentString.orEmpty()
        val diagnosis = Result(
            treatment, original, finalUa,
            " Mobile Safari/537.36" in finalUa, mobileInserted,
            supported, result, readback
        )
        Log.i("SIXPANE_R8", diagnosis.logLine())
        return diagnosis
    }
}
