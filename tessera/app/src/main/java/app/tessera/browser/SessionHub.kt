package app.tessera.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.os.Message
import android.net.Uri as AndroidUri
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/** Live, non-persisted status of one pane's browser. */
class PaneRuntime {
    var progress = 100
    var loading = false
    var canBack = false
    var canForward = false
    var error: String? = null
    var crashed = false
    var crashCount = 0
    var lastHeartbeat = 0L      // elapsedRealtime of last JS reply
    var visibilityState = "?"   // document.visibilityState from the last heartbeat
    var createdAt = SystemClock.elapsedRealtime()
}

/**
 * Owns every pane's WebView. Each WebView is created with an application-scoped
 * MutableContextWrapper (swapped to the Activity while attached) and is bound to
 * its own named profile before anything else touches it.
 */
class SessionHub(private val app: TesseraApp, private val store: Store) {
    interface Listener {
        fun onPaneChanged(paneId: String)
        fun onViewReplaced(paneId: String)
    }

    private val views = LinkedHashMap<String, PaneWebView>()
    private val runtimes = HashMap<String, PaneRuntime>()
    private val handler = Handler(Looper.getMainLooper())
    var listener: Listener? = null
    private var activity: Activity? = null

    val supported: Boolean get() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    fun runtime(id: String) = runtimes.getOrPut(id) { PaneRuntime() }
    fun view(id: String): PaneWebView? = views[id]
    fun liveCount() = views.size

    private val desktopUa: String by lazy {
        WebSettings.getDefaultUserAgent(app)
            .replace(Regex("\\(Linux; Android [^)]*\\)"), "(X11; Linux x86_64)")
            .replace(" Mobile", "")
            .replace("; wv", "")
    }

    // ---------------------------------------------------------------- lifecycle

    fun attachActivity(a: Activity) {
        activity = a
        views.values.forEach { (it.context as MutableContextWrapper).baseContext = a }
        startHeartbeat()
        // The Activity re-parents every browser right after this; then drop the holding window.
        handler.post { unpark() }
    }

    fun detachActivity(a: Activity) {
        if (activity !== a) return
        activity = null
        val sizes = views.mapValues { (_, v) -> v.width to v.height }
        views.values.forEach {
            (it.parent as? ViewGroup)?.removeView(it)
            (it.context as MutableContextWrapper).baseContext = app
        }
        park(sizes)
    }

    // ---- holding window: keeps pages attached (= running at full speed) when the
    // app's screen is closed, e.g. swiped away from Recents. Needs "Display over other apps".

    private var parkWin: android.widget.FrameLayout? = null
    val canPark: Boolean get() = android.provider.Settings.canDrawOverlays(app)
    val parked: Boolean get() = parkWin != null

    private fun park(sizes: Map<String, Pair<Int, Int>>) {
        if (!canPark || views.isEmpty() || parkWin != null) return
        runCatching {
            val wm = app.getSystemService(android.view.WindowManager::class.java)
            val holder = android.widget.FrameLayout(app)
            views.forEach { (id, v) ->
                val (w, h) = sizes[id] ?: (0 to 0)
                holder.addView(v, android.widget.FrameLayout.LayoutParams(w.coerceAtLeast(320), h.coerceAtLeast(480)))
                v.translationX = -100_000f // laid out and attached, never drawn on screen
            }
            val lp = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT).apply { alpha = 0f; title = "Tessera sessions" }
            wm.addView(holder, lp)
            parkWin = holder
        }.onFailure { parkWin = null }
    }

    private fun unpark() {
        val holder = parkWin ?: return
        parkWin = null
        views.values.forEach { if (it.parent === holder) { holder.removeView(it) }; it.translationX = 0f }
        runCatching { app.getSystemService(android.view.WindowManager::class.java).removeView(holder) }
    }

    /** Returns the pane's WebView, creating and binding it to its profile on first use. */
    @SuppressLint("SetJavaScriptEnabled")
    fun obtain(pane: Pane): PaneWebView {
        views[pane.id]?.let { it.translationX = 0f; return it }
        ProfileStore.getInstance().getOrCreateProfile(pane.profile)
        val wv = PaneWebView(MutableContextWrapper(activity ?: app), pane.id)
        // The profile must be bound before any settings, cookies or navigation.
        WebViewCompat.setProfile(wv, pane.profile)
        wv.keepAwake = true
        wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        // Web pages that set no background expect the browser's white canvas behind them
        // (their default text is black). Sites with a dark theme draw their own background.
        wv.setBackgroundColor(android.graphics.Color.WHITE)
        wv.isFocusableInTouchMode = true
        with(wv.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = pane.desktop
            userAgentString = if (pane.desktop) desktopUa else null
            // New-tab links and sign-in pop-ups open in a pop-up over the pane (same session),
            // so the pane's own page is never replaced. Pop-ups without a tap stay blocked.
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
        }
        WebViewCompat.getProfile(wv).cookieManager.setAcceptThirdPartyCookies(wv, true)
        wv.webViewClient = Client(pane.id)
        wv.webChromeClient = Chrome(pane.id)
        wv.setDownloadListener { url, ua, disposition, mime, _ -> download(pane, url, ua, disposition, mime) }
        views[pane.id] = wv
        runtime(pane.id).createdAt = SystemClock.elapsedRealtime()
        val start = pane.url.ifBlank { "about:blank" }
        wv.loadUrl(start)
        return wv
    }

    /** Destroys the pane's WebView. Its profile data is untouched (see [retireProfile]). */
    fun destroy(paneId: String) {
        views.remove(paneId)?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.stopLoading(); it.destroy()
        }
        runtimes.remove(paneId)
    }

    /** Queue a profile for permanent deletion and try now; Android may defer it to next launch. */
    fun retireProfile(name: String) {
        store.state.pendingDeletes += name
        store.save()
        handler.postDelayed({ purgePending() }, 1500)
    }

    fun purgePending() {
        val ps = runCatching { ProfileStore.getInstance() }.getOrNull() ?: return
        val inUse = store.state.allPanes().map { it.profile }.toSet()
        val done = store.state.pendingDeletes.filter { name ->
            name !in inUse && runCatching {
                if (name in ps.allProfileNames) ps.deleteProfile(name) else true
            }.getOrDefault(false)
        }
        if (done.isNotEmpty()) { store.state.pendingDeletes.removeAll(done.toSet()); store.save() }
    }

    fun flushCookies() {
        views.values.forEach { runCatching { WebViewCompat.getProfile(it).cookieManager.flush() } }
    }

    fun cookieManager(pane: Pane): CookieManager =
        views[pane.id]?.let { WebViewCompat.getProfile(it).cookieManager }
            ?: ProfileStore.getInstance().getOrCreateProfile(pane.profile).cookieManager

    // ------------------------------------------------------------- pane actions

    fun navigate(pane: Pane, url: String) {
        pane.url = url
        obtain(pane).loadUrl(url)
        store.save()
    }

    fun setDesktop(pane: Pane, on: Boolean) {
        pane.desktop = on
        views[pane.id]?.let {
            it.settings.userAgentString = if (on) desktopUa else null
            it.settings.loadWithOverviewMode = on
            it.reload()
        }
        store.save()
    }

    fun applyZoom(pane: Pane) {
        val z = pane.zoom
        views[pane.id]?.evaluateJavascript(
            "document.documentElement&&(document.documentElement.style.zoom=${if (z == 100) "''" else "'" + z / 100.0 + "'"});", null
        )
    }

    // ---------------------------------------------------------------- heartbeat

    private var beating = false
    private val beat = object : Runnable {
        override fun run() {
            views.forEach { (id, wv) ->
                runCatching {
                    wv.evaluateJavascript("document.visibilityState") { v ->
                        val r = runtime(id)
                        r.lastHeartbeat = SystemClock.elapsedRealtime()
                        r.visibilityState = v?.trim('"') ?: "?"
                    }
                }
            }
            flushCookies()
            handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    private fun startHeartbeat() {
        if (beating) return
        beating = true
        handler.postDelayed(beat, 2000)
    }

    /** Pings every live page right now; reports how many answered within [timeoutMs]. */
    fun pingAll(timeoutMs: Long, done: (answered: Int, visible: Int, total: Int) -> Unit) {
        val targets = views.toMap()
        if (targets.isEmpty()) { done(0, 0, 0); return }
        var answered = 0; var visible = 0; var finished = false
        val finish = Runnable { if (!finished) { finished = true; done(answered, visible, targets.size) } }
        targets.forEach { (id, wv) ->
            runCatching {
                wv.evaluateJavascript("document.visibilityState") { v ->
                    if (finished) return@evaluateJavascript
                    val r = runtime(id)
                    r.lastHeartbeat = SystemClock.elapsedRealtime()
                    r.visibilityState = v?.trim('"') ?: "?"
                    answered++
                    if (r.visibilityState == "visible") visible++
                    if (answered == targets.size) { handler.removeCallbacks(finish); finish.run() }
                }
            }
        }
        handler.postDelayed(finish, timeoutMs)
    }

    fun responsiveCount(withinMs: Long = HEARTBEAT_MS * 3): Int {
        val now = SystemClock.elapsedRealtime()
        return views.keys.count { now - runtime(it).lastHeartbeat <= withinMs }
    }

    // ------------------------------------------------------------------ clients

    private fun pane(id: String) = store.state.pane(id)
    private fun changed(id: String) = listener?.onPaneChanged(id)

    private fun updateNav(id: String, view: WebView) {
        val r = runtime(id)
        r.canBack = view.canGoBack(); r.canForward = view.canGoForward()
    }

    private inner class Client(val id: String) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val scheme = uri.scheme?.lowercase() ?: return false
            if (scheme in setOf("http", "https", "about", "data", "blob", "javascript")) return false
            if (!request.hasGesture()) return true
            val a = activity ?: return true
            runCatching {
                val intent = if (scheme == "intent") Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                    else Intent(Intent.ACTION_VIEW, uri)
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                intent.component = null; intent.selector = null
                a.startActivity(intent)
            }.onFailure {
                if (scheme == "intent") runCatching {
                    Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                        .getStringExtra("browser_fallback_url")?.let { view.loadUrl(it) }
                }
            }
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            val r = runtime(id); r.loading = true; r.error = null; r.crashed = false
            pane(id)?.let { p -> if (!url.isNullOrBlank() && url != "about:blank") p.url = url }
            updateNav(id, view); changed(id)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            val r = runtime(id); r.loading = false; r.progress = 100
            pane(id)?.let { p ->
                if (!url.isNullOrBlank() && url != "about:blank") p.url = url
                view.title?.takeIf { it.isNotBlank() && it != url }?.let { p.title = it }
                if (p.zoom != 100) applyZoom(p)
            }
            updateNav(id, view); store.save(); changed(id)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            pane(id)?.let { p -> if (!url.isNullOrBlank() && url != "about:blank") p.url = url }
            updateNav(id, view); store.save(); changed(id)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            runtime(id).error = error.description?.toString()?.ifBlank { null } ?: "The page could not be loaded."
            changed(id)
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            if (error.url == view.url || view.url.isNullOrBlank()) {
                runtime(id).error = "This site's security certificate isn't trusted, so it was blocked."
                changed(id)
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // Rebuild the view on the SAME profile; cookies and storage live on disk and are kept.
            val r = runtime(id); r.crashed = true; r.crashCount++
            if (views[id] === view) views.remove(id)
            (view.parent as? ViewGroup)?.removeView(view)
            runCatching { view.destroy() }
            val delay = (1000L shl (r.crashCount - 1).coerceAtMost(5)).coerceAtMost(30_000L)
            handler.postDelayed({
                val p = pane(id) ?: return@postDelayed
                if (views.containsKey(id)) return@postDelayed
                obtain(p)
                runtime(id).crashed = false
                listener?.onViewReplaced(id)
            }, delay)
            changed(id)
            return true
        }
    }

    private inner class Chrome(val id: String) : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            runtime(id).progress = newProgress; changed(id)
        }
        override fun onReceivedTitle(view: WebView, title: String?) {
            pane(id)?.let { if (!title.isNullOrBlank()) it.title = title }
            changed(id)
        }
        // With no Activity attached there is nowhere to show a dialog: dismiss safely.
        override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
            if (activity == null) { result.cancel(); return true }; return false
        }
        override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
            if (activity == null) { result.cancel(); return true }; return false
        }
        override fun onJsPrompt(view: WebView, url: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean {
            if (activity == null) { result.cancel(); return true }; return false
        }
        // A site's "Leave this page?" warning is shown to the user, never auto-answered.
        override fun onJsBeforeUnload(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
            if (activity == null) { result.cancel(); return true }; return false
        }
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            val a = activity as? MainActivity ?: return false
            val p = pane(id) ?: return false
            if (!isUserGesture) return false
            val popup = createPopup(p)
            (resultMsg.obj as WebView.WebViewTransport).webView = popup
            resultMsg.sendToTarget()
            a.showPopup(p, popup)
            return true
        }
        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<AndroidUri>>, params: FileChooserParams): Boolean =
            (activity as? MainActivity)?.pickFiles(callback, params) ?: false
        override fun onPermissionRequest(request: PermissionRequest) {
            request.deny()
            (activity as? MainActivity)?.toast("${AddressPolicy.label(request.origin.toString())} asked for camera or microphone \u2014 not available in Tessera")
        }
        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
            (activity as? MainActivity)?.toast("${AddressPolicy.label(origin.orEmpty())} asked for your location \u2014 not shared")
        }
    }

    /** Pop-up window bound to the SAME profile as its pane, so logins carry over. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun createPopup(p: Pane): WebView {
        val wv = WebView(MutableContextWrapper(activity ?: app))
        WebViewCompat.setProfile(wv, p.profile)
        with(wv.settings) {
            javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true
            setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
            useWideViewPort = true; setSupportMultipleWindows(false)
            allowFileAccess = false; allowContentAccess = false
        }
        WebViewCompat.getProfile(wv).cookieManager.setAcceptThirdPartyCookies(wv, true)
        wv.setBackgroundColor(android.graphics.Color.WHITE)
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) { (activity as? MainActivity)?.onPopupPage(url.orEmpty()) }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onCloseWindow(window: WebView) { (activity as? MainActivity)?.closePopup() }
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<AndroidUri>>, params: FileChooserParams): Boolean =
                (activity as? MainActivity)?.pickFiles(callback, params) ?: false
        }
        return wv
    }

    private fun download(pane: Pane, url: String, ua: String, disposition: String?, mime: String?) {
        val a = activity ?: return
        if (!AddressPolicy.isWeb(url)) { (a as? MainActivity)?.toast("This download type isn't supported"); return }
        runCatching {
            val name = URLUtil.guessFileName(url, disposition, mime)
            val req = DownloadManager.Request(Uri.parse(url))
                .addRequestHeader("Cookie", cookieManager(pane).getCookie(url) ?: "")
                .addRequestHeader("User-Agent", ua)
                .setMimeType(mime)
                .setTitle(name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            (a.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            (a as? MainActivity)?.toast("Downloading $name")
        }.onFailure { (a as? MainActivity)?.toast("Download failed: ${it.message}") }
    }

    companion object {
        const val HEARTBEAT_MS = 30_000L
    }
}
