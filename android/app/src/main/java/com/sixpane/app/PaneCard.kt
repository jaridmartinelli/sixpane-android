package com.sixpane.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.webkit.*
import android.widget.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewAssetLoader
import kotlin.math.roundToInt

/** Single attached, persistent WebView for one named profile. Never re-created to change layouts. */
class PaneCard(
    private val host:MainActivity,
    val state:PaneState
):FrameLayout(host) {
    val webView:WebView
    private val header=FrameLayout(host)
    private val body=FrameLayout(host)
    val lock=CornerIcon(host,CornerIcon.Kind.LOCK)
    private val cookie=CornerIcon(host,CornerIcon.Kind.COOKIE)
    private val expand=CornerIcon(host,CornerIcon.Kind.EXPAND)
    private val siteLabel=TextView(host)
    private val indicator=TextView(host)
    private val more=TextView(host)
    val menuAnchor:View get()=more
    private val selectionLayer=FrameLayout(host)
    private val selectionMark=TextView(host)
    private val stack=LinearLayout(host)
    private val assetLoader=WebViewAssetLoader.Builder().addPathHandler("/assets/",WebViewAssetLoader.AssetsPathHandler(host)).build()
    private var baseUserAgent=""
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
            if(!url.isNullOrEmpty() && url!=state.url){state.url=url;host.persist()}
            updateSiteLabel()
        }
        override fun onRenderProcessGone(view:WebView?, detail:RenderProcessGoneDetail?):Boolean {
            interrupted=true
            body.removeView(webView)
            webView.destroy() // Android requires destroyed crashed WebViews to be discarded.
            indicator.text="!"
            indicator.visibility=View.VISIBLE
            host.browserInterrupted(state,"Renderer exited; browser must be explicitly re-created")
            return true // Never misreport process recovery as uninterrupted execution.
        }
    }
    init {
        isFocusable=true
        setBackgroundColor(Color.rgb(27,34,45))
        background=outline(Color.rgb(31,39,53))
        clipChildren=true;clipToPadding=true
        require(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) { "Multi-profile WebView unavailable" }
        webView=WebView(host)
        // MUST happen before any other WebView settings/client operations.
        WebViewCompat.setProfile(webView,state.profile)
        baseUserAgent=webView.settings.userAgentString
        webView.settings.apply {
            javaScriptEnabled=true
            domStorageEnabled=true
            databaseEnabled=true
            loadsImagesAutomatically=true
            javaScriptCanOpenWindowsAutomatically=false
            setSupportMultipleWindows(false)
            builtInZoomControls=true
            displayZoomControls=false
            useWideViewPort=true
            loadWithOverviewMode=true
            mediaPlaybackRequiresUserGesture=true
        }
        WebViewCompat.getProfile(webView).cookieManager.setAcceptThirdPartyCookies(webView,true)
        webView.webViewClient=onPageChanged
        webView.webChromeClient=object:WebChromeClient(){
            override fun onProgressChanged(view:WebView?, newProgress:Int){
                if(newProgress<100)indicator.text="·" else updateZoomIndicator()
            }
        }
        updateDesktopWithoutReload()
        webView.setInitialScale(state.zoom)
        val headerH=host.dp(20)
        addView(header,LayoutParams(-1,headerH,Gravity.TOP))
        addView(body,LayoutParams(-1,-1).apply { topMargin=headerH })
        // Minimal chrome: only domain hint + overflow; URL/zoom controls live in a bottom sheet.
        // The top strip (not the webpage itself) owns the long-press action.
        header.setBackgroundColor(Color.rgb(38,49,64))
        header.setOnLongClickListener { host.windowContextMenu(this);true }
        siteLabel.apply {
            textSize=9f
            setTextColor(Color.argb(160,255,255,255))
            gravity=Gravity.CENTER_VERTICAL
            maxLines=1
            ellipsize=android.text.TextUtils.TruncateAt.END
            setPadding(host.dp(3),0,host.dp(3),0)
            setBackgroundColor(Color.TRANSPARENT)
            setOnLongClickListener { host.windowContextMenu(this@PaneCard);true }
            contentDescription="Hold for address and browser controls"
        }
        updateSiteLabel()
        header.addView(siteLabel,LayoutParams(-1,-1).apply{
            leftMargin=host.dp(if(state.locked)24 else 2)
            rightMargin=host.dp(35)
        })
        // A lock is an indicator, not a persistent button. Long-press opens controls.
        lock.locked=state.locked
        lock.contentDescription="Browser locked; hold for browser controls"
        lock.isClickable=false
        lock.isFocusable=false
        lock.setOnLongClickListener {host.windowContextMenu(this);true}
        header.addView(lock,LayoutParams(host.dp(24),-1,Gravity.TOP or Gravity.LEFT))
        updateLockStatus()
        more.apply {
            text="⋮";textSize=17f
            setTextColor(Color.argb(192,255,255,255))
            gravity=Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription="Browser controls: address, zoom, lock and selection"
            setOnClickListener {host.windowContextMenu(this@PaneCard)}
            setOnLongClickListener {host.windowContextMenu(this@PaneCard);true}
        }
        // The glyph remains tiny; the invisible target is 36dp for one-handed use.
        addView(more,LayoutParams(host.dp(36),host.dp(36),Gravity.TOP or Gravity.RIGHT))
        body.addView(webView,LayoutParams(-1,-1))
        body.addView(cookie,LayoutParams(host.dp(32),host.dp(32),Gravity.BOTTOM or Gravity.LEFT))
        body.addView(expand,LayoutParams(host.dp(32),host.dp(32),Gravity.BOTTOM or Gravity.RIGHT))
        // Selection shield prevents website interaction while selection mode is active.
        selectionLayer.setBackgroundColor(Color.argb(48,82,149,252))
        selectionLayer.visibility=View.GONE
        selectionLayer.isClickable=true
        selectionLayer.setOnClickListener{host.toggleWindowSelection(state.id)}
        selectionMark.apply{
            text="○";textSize=21f;setTextColor(Color.WHITE);gravity=Gravity.CENTER
            setBackgroundColor(Color.argb(200,30,42,62))
        }
        selectionLayer.addView(selectionMark,LayoutParams(host.dp(35),host.dp(35),Gravity.TOP or Gravity.RIGHT))
        addView(selectionLayer,LayoutParams(-1,-1))
        cookie.contentDescription="Cookie editor";expand.contentDescription="Expand browser"
        cookie.setOnClickListener {cookie.pressedFeedback();host.cookieEditor(this)}
        expand.setOnClickListener {expand.pressedFeedback();host.focus(this)}
        indicator.apply {
            textSize=9f;setTextColor(Color.argb(190,255,255,255));gravity=Gravity.CENTER
            setBackgroundColor(Color.argb(210,37,52,73));visibility=View.GONE
            setOnClickListener {zoomTo(100)}
        }
        body.addView(indicator,LayoutParams(host.dp(42),host.dp(22),Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        updateZoomIndicator()
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
    private fun outline(color:Int)=GradientDrawable().apply{setColor(color);setStroke(host.dp(1),Color.rgb(58,71,89));cornerRadius=host.dp(3).toFloat()}
    private fun applyViewportDimensions(w:Int,h:Int){
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
        (siteLabel.layoutParams as? LayoutParams)?.let{params->
            params.leftMargin=host.dp(if(state.locked)24 else 2)
            siteLabel.layoutParams=params
        }
    }
    private fun updateSiteLabel(){
        siteLabel.text=if(state.url=="about:blank") "New browser" else
            try { android.net.Uri.parse(state.url).host ?: state.url } catch(_:Exception){state.url}
    }
    fun updateSelection(active:Boolean,selected:Boolean){
        selectionLayer.visibility=if(active)View.VISIBLE else View.GONE
        selectionMark.text=if(selected)"✓" else "○"
        selectionLayer.setBackgroundColor(if(selected)Color.argb(92,97,158,248) else Color.argb(16,97,158,248))
        selectionLayer.contentDescription=if(selected)"Selected browser" else "Select browser"
    }
    fun updateControls(){
        // Focus and grid changes never replace a WebView or force the toolbar visible.
        indicator.visibility=if(state.zoom==100)View.GONE else View.VISIBLE
    }
    private fun normalize(raw:String):String?{
        val t=raw.trim();if(t=="about:blank")return t
        val candidate=if("://" in t)t else "https://$t"
        return try{val u=android.net.Uri.parse(candidate);if(u.scheme in listOf("http","https")&&!u.host.isNullOrBlank())candidate else null}catch(_:Exception){null}
    }
    /** Explicit submit from the thumb-reachable sheet: no navigation on focus loss. */
    fun submitAddress(raw:String):Boolean {
        val next=normalize(raw) ?: return false
        if(interrupted)return false
        state.url=next
        updateSiteLabel()
        host.persist()
        webView.loadUrl(next)
        return true
    }
    fun loadAddress(url:String){
        if(interrupted)return
        state.url=url;host.persist();updateSiteLabel();webView.loadUrl(url)
    }
    fun zoomByStep(direction:Int)=zoom(WorkspacePolicy.ZOOM_STEP*direction)
    fun resetZoom(){zoomTo(100)}
    private fun zoom(step:Int)=zoomTo((state.zoom+step).coerceIn(50,200))
    private fun zoomTo(target:Int){
        if(target==state.zoom)return
        // Apply relative zoom to the actual WebView, rather than CSS-zooming a screenshot.
        webView.zoomBy(target.toFloat()/state.zoom.toFloat())
        state.zoom=target
        updateZoomIndicator();host.persist()
    }
    private fun updateZoomIndicator(){indicator.text=WorkspacePolicy.displayZoom(state.zoom);indicator.visibility=if(state.zoom==100)View.GONE else View.VISIBLE}
    private fun updateDesktopWithoutReload(){
        webView.settings.userAgentString=if(state.desktop)baseUserAgent.replace(" Mobile","") else baseUserAgent
        webView.settings.useWideViewPort=true
        webView.settings.loadWithOverviewMode=!state.desktop
    }
    fun toggleDesktop(){
        if(state.locked){blocked("Unlock browser to change Desktop mode");return}
        host.confirm("Change display mode?","This page will reload. Cookies stay in this browser."){
            state.desktop=!state.desktop;updateDesktopWithoutReload();host.persist();webView.reload()
        }
    }
    fun back(){if(webView.canGoBack())webView.goBack()}
    fun forward(){if(webView.canGoForward())webView.goForward()}
    fun refresh(){webView.reload()}
    fun reset(){
        if(state.locked){blocked("Reset blocked");return}
        host.confirm("Reset page?","This browser will navigate to a blank page; its cookies are preserved."){
            webView.loadUrl("about:blank");state.url="about:blank";host.persist()
        }
    }
    fun blocked(reason:String){lock.blockedFeedback();host.notice(reason)}
    fun destroyExplicitly(){
        if(!interrupted){webView.stopLoading();body.removeView(webView);webView.destroy()}
        interrupted=true
    }
    fun flushCookies(){if(!interrupted)WebViewCompat.getProfile(webView).cookieManager.flush()}
}
