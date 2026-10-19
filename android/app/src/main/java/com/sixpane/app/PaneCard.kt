package com.sixpane.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
    private val address=EditText(host)
    private val indicator=TextView(host)
    private val zoomMinus=TextView(host)
    private val zoomPlus=TextView(host)
    private val more=TextView(host)
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
            if(!address.hasFocus()) address.setText(state.url)
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
        val density=resources.displayMetrics.density
        val headerH=(28*density).roundToInt()
        addView(header,LayoutParams(-1,headerH,Gravity.TOP))
        addView(body,LayoutParams(-1,-1).apply { topMargin=headerH })
        header.setBackgroundColor(Color.rgb(51,65,86))
        header.addView(lock,LayoutParams(host.dp(28),-1,Gravity.TOP or Gravity.LEFT))
        lock.locked=state.locked
        lock.contentDescription="Lock browser"
        lock.setOnClickListener {state.locked=!state.locked;lock.locked=state.locked;lock.pressedFeedback();host.persist()}
        address.setText(state.url)
        address.setSingleLine(true)
        address.textSize=10f
        address.setTextColor(Color.WHITE)
        address.setHintTextColor(Color.LTGRAY)
        address.setPadding(host.dp(4),0,host.dp(3),0)
        address.setSelectAllOnFocus(true)
        address.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        address.imeOptions=EditorInfo.IME_ACTION_GO
        address.setBackgroundColor(Color.rgb(36,49,68))
        address.setOnEditorActionListener { _, actionId, event ->
            if(actionId==EditorInfo.IME_ACTION_GO || event?.keyCode==KeyEvent.KEYCODE_ENTER){go();true}else false
        }
        header.addView(address,LayoutParams(host.dp(78),host.dp(21),Gravity.CENTER_VERTICAL or Gravity.LEFT).apply{leftMargin=host.dp(28)})
        val right=LinearLayout(host).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        fun smallText(text:String):TextView = TextView(host).apply{
            this.text=text;textSize=15f;setTextColor(Color.argb(179,255,255,255))
            gravity=Gravity.CENTER;setOnTouchListener{v,e->
                if(e.actionMasked==MotionEvent.ACTION_DOWN)v.animate().scaleX(.80f).scaleY(.80f).setDuration(90).start()
                if(e.actionMasked==MotionEvent.ACTION_UP||e.actionMasked==MotionEvent.ACTION_CANCEL)v.animate().scaleX(1f).scaleY(1f).setDuration(130).start()
                false
            }
        }
        zoomMinus.text="−";zoomPlus.text="+";more.text="⋮"
        listOf(zoomMinus,zoomPlus,more).forEach {x->
            x.textSize=16f;x.setTextColor(Color.argb(179,255,255,255));x.gravity=Gravity.CENTER
            right.addView(x,LinearLayout.LayoutParams(host.dp(24),host.dp(27)))
        }
        zoomMinus.contentDescription="Zoom out";zoomPlus.contentDescription="Zoom in";more.contentDescription="Browser options"
        zoomMinus.setOnClickListener {zoom(-WorkspacePolicy.ZOOM_STEP)}
        zoomPlus.setOnClickListener {zoom(WorkspacePolicy.ZOOM_STEP)}
        more.setOnClickListener {host.paneMenu(this)}
        header.addView(right,LayoutParams(-2,-1,Gravity.RIGHT or Gravity.CENTER_VERTICAL))
        body.addView(webView,LayoutParams(-1,-1))
        body.addView(cookie,LayoutParams(host.dp(32),host.dp(32),Gravity.BOTTOM or Gravity.LEFT))
        body.addView(expand,LayoutParams(host.dp(32),host.dp(32),Gravity.BOTTOM or Gravity.RIGHT))
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
        webView.loadUrl(state.url)
    }
    private fun outline(color:Int)=GradientDrawable().apply{setColor(color);setStroke(host.dp(1),Color.rgb(70,83,101));cornerRadius=host.dp(6).toFloat()}
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
    fun updateControls(){
        zoomMinus.visibility=if(compact) View.GONE else View.VISIBLE
        zoomPlus.visibility=if(compact) View.GONE else View.VISIBLE
        more.visibility=if(compact) View.GONE else View.VISIBLE
        // Address shrinks while pane is not focused; no popup editor.
        (address.layoutParams as LayoutParams).apply{
            width=host.dp(if(compact) 46 else if(focused) 104 else 62)
            address.layoutParams=this
        }
        indicator.visibility=if(state.zoom==100)View.GONE else View.VISIBLE
    }
    private fun normalize(raw:String):String?{
        val t=raw.trim();if(t=="about:blank")return t
        val candidate=if("://" in t)t else "https://$t"
        return try{val u=android.net.Uri.parse(candidate);if(u.scheme in listOf("http","https")&&!u.host.isNullOrBlank())candidate else null}catch(_:Exception){null}
    }
    private fun go(){
        val next=normalize(address.text.toString())
        if(next==null){address.error="Enter a valid address";return}
        (host.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(address.windowToken,0)
        address.clearFocus()
        state.url=next;host.persist();webView.loadUrl(next)
    }
    fun zoomByStep(direction:Int)=zoom(WorkspacePolicy.ZOOM_STEP*direction)
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
