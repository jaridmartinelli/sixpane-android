package com.sixpane.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.*
import android.webkit.*
import android.widget.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewAssetLoader
import kotlin.math.roundToInt

/** One named profile per card. Layout changes never replace the WebView; renderer death does. */
class PaneCard(
    private val host:MainActivity,
    val state:PaneState
):FrameLayout(host) {
    var webView:WebView
        private set
    private val body=FrameLayout(host)
    val lock=CornerIcon(host,CornerIcon.Kind.LOCK)
    private val expand=CornerIcon(host,CornerIcon.Kind.EXPAND)
    private val paneNumber=TextView(host)
    private val recoveryIndicator=TextView(host)
    private val indicator=TextView(host)
    private val badge=LinearLayout(host)
    val menuAnchor:View get()=badge
    private val selectionLayer=FrameLayout(host)
    private val selectionMark=TextView(host)
    private val assetLoader=WebViewAssetLoader.Builder().addPathHandler("/assets/",WebViewAssetLoader.AssetsPathHandler(host)).build()
    private var baseUserAgent=""
    private val recoveryHandler=Handler(Looper.getMainLooper())
    private var webViewAlive=false
    private var disposed=false
    private var recoveryAttempts=0
    private var retry:Runnable?=null
    private var loadTimeout:Runnable?=null
    var interrupted=false
        private set
    private var lastWidth=0
    private var lastHeight=0
    var compact=false
        set(value){field=value;updateControls()}
    var focused=false
        set(value){field=value; expand.expanded=value;updateControls()}
    private val onPageChanged=object:WebViewClient(){
        override fun shouldInterceptRequest(view:WebView, request:WebResourceRequest):WebResourceResponse? =
            assetLoader.shouldInterceptRequest(request.url)
        override fun onPageFinished(view:WebView?, url:String?) {
            if(view!==webView||!webViewAlive||disposed)return
            if(url.isNullOrEmpty()||url!=view?.url)return // Ignore completion from an abandoned navigation.
            if(!url.isNullOrEmpty() && url!=state.url){state.url=url;host.persist()}
            if(interrupted){
                loadTimeout?.let{recoveryHandler.removeCallbacks(it)};loadTimeout=null
                interrupted=false;recoveryAttempts=0;recoveryIndicator.visibility=View.GONE
            }
            updateSiteLabel()
        }
        override fun onPageStarted(view:WebView?,url:String?,favicon:android.graphics.Bitmap?){
            if(view!==webView||!webViewAlive||disposed)return
            // Capture redirects before completion as well as normal page finishes.
            if(!url.isNullOrEmpty()&&url!=state.url){state.url=url;host.persist();updateSiteLabel()}
        }
        override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError){
            if(view===webView&&interrupted&&request.isForMainFrame)recoveryLoadFailed()
        }
        override fun onReceivedHttpError(view:WebView,request:WebResourceRequest,response:WebResourceResponse){
            if(view===webView&&interrupted&&request.isForMainFrame&&response.statusCode>=400)recoveryLoadFailed()
        }
        override fun onReceivedSslError(view:WebView,handler:SslErrorHandler,error:android.net.http.SslError){
            handler.cancel()
            if(view===webView&&interrupted&&error.url==view.url)recoveryLoadFailed()
        }
        override fun onRenderProcessGone(view:WebView?, detail:RenderProcessGoneDetail?):Boolean {
            if(view===webView&&!disposed){
                interrupted=true
                discardWebView()
                scheduleRecovery()
            }
            return true
        }
    }
    init {
        isFocusable=true
        setBackgroundColor(Color.rgb(27,34,45))
        updateFrameColor()
        clipChildren=true;clipToPadding=true
        require(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) { "Multi-profile WebView unavailable" }
        webView=createWebView()
        webViewAlive=true
        // Only overlays occupy app chrome: no header, URL strip, cookie button or overflow bar.
        addView(body,LayoutParams(-1,-1).apply{setMargins(host.dp(1),host.dp(1),host.dp(1),host.dp(1))})
        body.addView(webView,LayoutParams(-1,-1))
        body.setOnLongClickListener{host.windowContextMenu(this);true}
        badge.apply{
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.argb(232,12,18,25))
            setOnClickListener{host.windowContextMenu(this@PaneCard)}
            setOnLongClickListener{host.windowContextMenu(this@PaneCard);true}
        }
        paneNumber.apply{textSize=11f;setTextColor(Color.WHITE);gravity=Gravity.CENTER}
        badge.addView(paneNumber,LinearLayout.LayoutParams(host.dp(24),host.dp(22)))
        lock.isClickable=false;lock.isFocusable=false
        badge.addView(lock,LinearLayout.LayoutParams(host.dp(16),host.dp(22)))
        body.addView(badge,LayoutParams(-2,host.dp(22),Gravity.TOP or Gravity.LEFT).apply{leftMargin=host.dp(3);topMargin=host.dp(3)})
        updatePaneNumber()
        // Retain expand / restore outside the seven-item menu; the WebViews resize in place.
        body.addView(expand,LayoutParams(host.dp(44),host.dp(44),Gravity.BOTTOM or Gravity.RIGHT))
        expand.setOnClickListener{expand.pressedFeedback();host.focus(this)}
        selectionLayer.setBackgroundColor(Color.argb(48,82,149,252))
        selectionLayer.visibility=View.GONE
        selectionLayer.isClickable=true
        selectionLayer.setOnClickListener{host.toggleWindowSelection(state.id)}
        selectionLayer.setOnLongClickListener{host.windowContextMenu(this);true}
        selectionMark.apply{
            text="○";textSize=21f;setTextColor(Color.WHITE);gravity=Gravity.CENTER
            setBackgroundColor(Color.argb(200,30,42,62))
        }
        selectionLayer.addView(selectionMark,LayoutParams(host.dp(35),host.dp(35),Gravity.TOP or Gravity.RIGHT))
        addView(selectionLayer,LayoutParams(-1,-1))
        recoveryIndicator.apply{
            textSize=11f;setTextColor(Color.rgb(255,215,125));gravity=Gravity.CENTER
            setPadding(host.dp(4),host.dp(2),host.dp(4),host.dp(2))
            setBackgroundColor(Color.argb(225,37,33,28));visibility=View.GONE
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
            accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        body.addView(recoveryIndicator,LayoutParams(-2,-2,Gravity.CENTER))
        body.addOnLayoutChangeListener{_,l,t,r,b,_,_,_,_->
            val w=r-l;val h=b-t
            if(w>0&&h>0&&(w!=lastWidth||h!=lastHeight)){
                lastWidth=w;lastHeight=h;applyViewportDimensions(w,h)
            }
        }
        updateControls()
        updateLockStatus()
        webView.loadUrl(state.url)
    }
    private fun createWebView():WebView {
        val fresh=WebView(host)
        try {
            // First operation on EVERY instance, including recovery. Never mint a profile ID here.
            WebViewCompat.setProfile(fresh,state.profile)
            baseUserAgent=fresh.settings.userAgentString
            fresh.settings.apply {
                javaScriptEnabled=true;domStorageEnabled=true;databaseEnabled=true
                loadsImagesAutomatically=true;javaScriptCanOpenWindowsAutomatically=false
                setSupportMultipleWindows(false)
                builtInZoomControls=true;displayZoomControls=false
                useWideViewPort=true;loadWithOverviewMode=!state.desktop
                mediaPlaybackRequiresUserGesture=true
                userAgentString=if(host.store.desktopUserAgent||state.desktop)BrowserPolicy.DESKTOP_USER_AGENT else baseUserAgent
            }
            WebViewCompat.getProfile(fresh).cookieManager.setAcceptThirdPartyCookies(fresh,true)
            fresh.webViewClient=onPageChanged
            fresh.webChromeClient=object:WebChromeClient(){
                override fun onProgressChanged(view:WebView?,newProgress:Int){
                    if(view!==webView||!webViewAlive)return
                    if(newProgress<100)indicator.text="·" else updateZoomIndicator()
                }
            }
            fresh.setOnLongClickListener{host.windowContextMenu(this@PaneCard);true}
            fresh.setBackgroundColor(Color.rgb(23,32,39))
            fresh.setInitialScale(state.zoom)
            // Page drags belong to the page; no workspace gesture interceptor is installed.
            return fresh
        }catch(e:Exception){fresh.destroy();throw e}
    }
    private fun discardWebView(){
        loadTimeout?.let{recoveryHandler.removeCallbacks(it)};loadTimeout=null
        if(webViewAlive){webViewAlive=false;body.removeView(webView);webView.destroy()}
    }
    private fun recoveryLoadFailed(){
        if(disposed||!interrupted)return
        discardWebView();scheduleRecovery()
    }
    private fun scheduleRecovery(){
        if(disposed||retry!=null)return
        recoveryIndicator.visibility=View.VISIBLE
        if(recoveryAttempts>=BrowserPolicy.MAX_RECOVERY_ATTEMPTS){
            recoveryIndicator.text="Recovery stopped · 10/10";return
        }
        val attempt=recoveryAttempts+1
        val delay=BrowserPolicy.recoveryDelayMs(attempt)
        recoveryIndicator.text="Recovering $attempt/10 · ${delay/1000}s"
        retry=Runnable {
            retry=null
            if(disposed)return@Runnable
            recoveryAttempts=attempt
            recoveryIndicator.text="Recovering $attempt/10"
            try {
                webView=createWebView();webViewAlive=true
                body.addView(webView,0,LayoutParams(-1,-1))
                if(lastWidth>0&&lastHeight>0)applyViewportDimensions(lastWidth,lastHeight)
                loadTimeout=Runnable{recoveryLoadFailed()}.also{
                    recoveryHandler.postDelayed(it,BrowserPolicy.RECOVERY_LOAD_TIMEOUT_MS)
                }
                webView.loadUrl(state.url)
            }catch(_:Exception){discardWebView();scheduleRecovery()}
        }.also{recoveryHandler.postDelayed(it,delay)}
    }
    fun updatePaneNumber(){
        val index=host.paneIndex(state.id)
        paneNumber.text=index.toString();paneNumber.contentDescription="Pane $index"
    }
    fun updateFrameColor(){
        background=GradientDrawable().apply{
            setColor(Color.rgb(16,20,24));setStroke(host.dp(1).coerceAtLeast(1),host.store.frameColor(this@PaneCard.state.id));cornerRadius=host.dp(3).toFloat()
        }
    }
    private fun applyViewportDimensions(w:Int,h:Int){
        if(!webViewAlive)return
        // Small genuine WebView viewport-size differences within identical visible card geometry.
        // View transforms visually fit the independent surface into its aligned host cell.
        val sx=WorkspacePolicy.normalViewScale(state.widthPercent)
        val sy=WorkspacePolicy.normalViewScale(state.heightPercent)
        val trueW=(w/sx).roundToInt().coerceAtLeast(1)
        val trueH=(h/sy).roundToInt().coerceAtLeast(1)
        val lp=webView.layoutParams
        if(lp.width!=trueW||lp.height!=trueH){lp.width=trueW;lp.height=trueH;webView.layoutParams=lp}
        webView.pivotX=0f;webView.pivotY=0f;webView.scaleX=sx;webView.scaleY=sy
    }
    fun updateLockStatus(){
        lock.locked=state.locked
        lock.visibility=if(state.locked)View.VISIBLE else View.GONE
        badge.contentDescription="Window ${host.paneIndex(state.id)} actions${if(state.locked)"; locked" else ""}"
    }
    private fun updateSiteLabel(){
        paneNumber.contentDescription="Window ${host.paneIndex(state.id)} · ${state.url}"
    }
    fun updateSelection(active:Boolean,selected:Boolean){
        selectionLayer.visibility=if(active)View.VISIBLE else View.GONE
        selectionMark.text=if(selected)"✓" else "○"
        selectionLayer.setBackgroundColor(if(selected)Color.argb(92,97,158,248) else Color.argb(16,97,158,248))
        selectionLayer.contentDescription=if(selected)"Selected browser" else "Select browser"
    }
    fun updateControls(){
        // Focus and grid changes never replace a WebView or force the toolbar visible.
        expand.contentDescription=if(focused)"Restore six-window grid" else "Expand window ${host.paneIndex(state.id)}"
    }
    private fun normalize(raw:String):String?{
        val t=raw.trim();if(t=="about:blank")return t
        val candidate=if("://" in t)t else "https://$t"
        return try{val u=android.net.Uri.parse(candidate);if(u.scheme in listOf("http","https")&&!u.host.isNullOrBlank())candidate else null}catch(_:Exception){null}
    }
    /** Explicit submit from the thumb-reachable sheet: no navigation on focus loss. */
    fun submitAddress(raw:String):Boolean {
        val next=normalize(raw) ?: return false
        if(disposed)return false
        loadAddress(next)
        return true
    }
    fun loadAddress(url:String){
        if(disposed)return
        state.url=url;host.persist();updateSiteLabel()
        // A navigation during backoff updates the recovery target without changing its profile.
        if(webViewAlive)webView.loadUrl(url)
    }
    fun zoomByStep(direction:Int)=zoom(WorkspacePolicy.ZOOM_STEP*direction)
    fun resetZoom(){zoomTo(100)}
    private fun zoom(step:Int)=zoomTo((state.zoom+step).coerceIn(50,200))
    private fun zoomTo(target:Int){
        if(target==state.zoom)return
        // Apply relative zoom to the actual WebView, rather than CSS-zooming a screenshot.
        if(webViewAlive)webView.zoomBy(target.toFloat()/state.zoom.toFloat())
        state.zoom=target
        updateZoomIndicator();host.persist()
    }
    private fun updateZoomIndicator(){indicator.text=WorkspacePolicy.displayZoom(state.zoom);indicator.visibility=if(state.zoom==100)View.GONE else View.VISIBLE}
    private fun updateDesktopWithoutReload(){
        if(!webViewAlive)return
        webView.settings.userAgentString=if(host.store.desktopUserAgent||state.desktop)BrowserPolicy.DESKTOP_USER_AGENT else baseUserAgent
        webView.settings.useWideViewPort=true
        webView.settings.loadWithOverviewMode=!state.desktop
    }
    fun applyGlobalUserAgent(){updateDesktopWithoutReload()}
    fun setDesktopMode(value:Boolean){
        if(state.locked)return
        state.desktop=value;updateDesktopWithoutReload();host.persist()
        if(webViewAlive)webView.reload()
    }
    fun toggleDesktop(){
        if(state.locked){blocked("Unlock browser to change Desktop mode");return}
        host.confirm("Change display mode?","This page will reload. Cookies stay in this browser."){
            state.desktop=!state.desktop;updateDesktopWithoutReload();host.persist();if(webViewAlive)webView.reload()
        }
    }
    fun back(){if(webViewAlive&&webView.canGoBack())webView.goBack()}
    fun forward(){if(webViewAlive&&webView.canGoForward())webView.goForward()}
    fun refresh(){if(webViewAlive)webView.reload()}
    fun reset(){
        if(state.locked){blocked("Reset blocked");return}
        host.confirm("Reset page?","This browser will navigate to a blank page; its cookies are preserved."){
            loadAddress("about:blank")
        }
    }
    fun blocked(reason:String){lock.blockedFeedback();host.notice(reason)}
    fun destroyExplicitly(){
        disposed=true;recoveryHandler.removeCallbacksAndMessages(null);retry=null;loadTimeout=null
        discardWebView();interrupted=true
    }
    fun flushCookies(){if(webViewAlive)WebViewCompat.getProfile(webView).cookieManager.flush()}
}
