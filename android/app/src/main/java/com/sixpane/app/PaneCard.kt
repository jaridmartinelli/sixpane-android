package com.sixpane.app

import android.content.Context
import android.content.Intent
import android.app.AlertDialog
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.text.TextUtils
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
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
    private val header=LinearLayout(host)
    private val headerNumber=TextView(host)
    private val headerLock=TextView(host)
    private val headerHost=TextView(host)
    private val headerBadge=TextView(host)
    private val headerProgress=View(host)
    private val refreshIndicator=TextView(host)
    private val errorOverlay=LinearLayout(host)
    private var refreshAnimator:ObjectAnimator?=null
    private var selectedOutline=false
    var loading=false
        private set
    private var startY=0f
    private var pullPermitted=false
    private var pullTriggered=false
    private var refreshGestureGeneration=0
    private val thumbnailTap=FrameLayout(host)
    private val blankTap=FrameLayout(host)
    val lock=CornerIcon(host,CornerIcon.Kind.LOCK)
    private val expand=CornerIcon(host,CornerIcon.Kind.EXPAND)
    private val paneNumber=TextView(host)
    private val recoveryIndicator=TextView(host)
    private val indicator=TextView(host)
    private val badge=LinearLayout(host)
    val menuAnchor:View get()=header
    private val selectionLayer=FrameLayout(host)
    private val selectionMark=TextView(host)
    private val assetLoader=WebViewAssetLoader.Builder().addPathHandler("/assets/",WebViewAssetLoader.AssetsPathHandler(host)).build()
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
    private var entryCandidates:List<String> = emptyList()
    private var entryIndex=0
    private var activeEntry:String?=null
    var compact=false
        set(value){field=value;updateControls()}
    var focused=false
        set(value){field=value; expand.expanded=value;updateControls()}
    private val onPageChanged=object:WebViewClient(){
        override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean{
            val destination=request.url
            val scheme=destination.scheme?.lowercase().orEmpty()
            if(scheme in listOf("http","https","about","data","blob"))return false
            if(!request.hasGesture())return true
            try {
                val external=if(scheme=="intent")
                    Intent.parseUri(destination.toString(),Intent.URI_INTENT_SCHEME)
                else Intent(Intent.ACTION_VIEW,destination)
                external.addCategory(Intent.CATEGORY_BROWSABLE)
                host.startActivity(external)
            }catch(_:Exception){
                val fallback=runCatching{
                    Intent.parseUri(destination.toString(),Intent.URI_INTENT_SCHEME)
                        .getStringExtra("browser_fallback_url")
                }.getOrNull()
                if(fallback!=null && BrowserPolicy.normalizeAddress(fallback)==fallback)
                    loadAddress(fallback)
                else host.notice("No app can open this link")
            }
            return true
        }
        override fun shouldInterceptRequest(view:WebView, request:WebResourceRequest):WebResourceResponse? {
            BrowserPolicy.assetRegion(request.url.toString())?.let{(region,evidence)->
                host.runOnUiThread {
                    if(!disposed && state.assetRegionHint!=region){
                        state.assetRegionHint=region;state.assetRegionEvidence=evidence
                        Log.i("SIXPANE_R3","profile="+state.profile+" "+evidence)
                        host.persist()
                    }
                }
            }
            return assetLoader.shouldInterceptRequest(request.url)
        }
        override fun onPageFinished(view:WebView?, url:String?) {
            if(view!==webView||!webViewAlive||disposed)return
            if(url.isNullOrEmpty()||url!=view?.url)return // Ignore completion from an abandoned navigation.
            if(!url.isNullOrEmpty() && url!=state.url){state.url=url;host.persist()}
            if(interrupted){
                loadTimeout?.let{recoveryHandler.removeCallbacks(it)};loadTimeout=null
                interrupted=false;recoveryAttempts=0;recoveryIndicator.visibility=View.GONE
            }
            // Only an observed QueueITAccepted-* cookie is a local clearance signal.
            // A redirect alone NEVER marks clearance (nor server-side acceptance).
            if(state.phase!=SessionPhase.RETIRED && state.clearanceObservedAt==null &&
                url.startsWith("https://")){
                val cookies=WebViewCompat.getProfile(webView).cookieManager.getCookie(url).orEmpty()
                if(cookies.split(';').any{it.trim().substringBefore('=').startsWith("QueueITAccepted-",true)}){
                    state.clearanceObservedAt=System.currentTimeMillis()
                    state.phase=SessionPhase.CLEARANCE_OBSERVED
                    Log.i("SIXPANE_R4","profile="+state.profile+" clearance-cookie-observed")
                    host.persist()
                }
            }
            if(entryCandidates.isNotEmpty()){
                if(entryIndex<entryCandidates.lastIndex){
                    entryIndex++
                    activeEntry=entryCandidates[entryIndex]
                    webView.loadUrl(activeEntry!!)
                    return
                }
                entryCandidates=emptyList();activeEntry=null
            }
            loading=false
            stopPullIndicator()
            headerProgress.visibility=View.GONE
            updateHeader()
            updateSiteLabel();updateInteractionSurfaces();host.onPaneUrlChanged(state.id)
        }
        override fun onPageStarted(view:WebView?,url:String?,favicon:android.graphics.Bitmap?){
            if(view!==webView||!webViewAlive||disposed)return
            loading=true
            errorOverlay.visibility=View.GONE
            headerProgress.visibility=View.VISIBLE
            updateHeader();host.onPaneUrlChanged(state.id)
            // Capture redirects before completion as well as normal page finishes.
            if(!url.isNullOrEmpty()){
                val entry=activeEntry
                if(entry!=null && BrowserPolicy.isWaitingRoomRedirect(entry,url)){
                    state.waitingRoomEntryPath=runCatching{java.net.URI(entry).rawPath}.getOrNull()
                    state.entryPathEvidence="entryPath="+(state.waitingRoomEntryPath?:"/")+
                        ";redirectHost="+runCatching{java.net.URI(url).host}.getOrNull().orEmpty()
                    Log.i("SIXPANE_R2","profile="+state.profile+" "+state.entryPathEvidence)
                    entryCandidates=emptyList();activeEntry=null
                    host.persist()
                }
                if(state.phase==SessionPhase.CREATED && url!="about:blank"){
                    state.phase=SessionPhase.ACTIVE;host.persist()
                }
            }
            if(!url.isNullOrEmpty()&&url!=state.url){state.url=url;host.persist();updateSiteLabel();updateInteractionSurfaces();host.onPaneUrlChanged(state.id)}
        }
        override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError){
            if(view===webView&&request.isForMainFrame&&!interrupted){
                showLoadError(error.description?.toString().orEmpty())
            }
            if(view===webView&&interrupted&&request.isForMainFrame)recoveryLoadFailed()
        }
        override fun onReceivedHttpError(view:WebView,request:WebResourceRequest,response:WebResourceResponse){
            if(view===webView && CookieExpiryObserver.observe(state,request.url.toString(),response.responseHeaders))
                host.persist()
            if(view===webView&&interrupted&&request.isForMainFrame&&response.statusCode>=400)recoveryLoadFailed()
        }
        override fun onReceivedSslError(view:WebView,handler:SslErrorHandler,error:android.net.http.SslError){
            handler.cancel()
            if(view===webView && !interrupted){
                showLoadError("This site's security certificate isn't trusted, so it was blocked.")
            }
            if(view===webView&&interrupted&&error.url==view.url)recoveryLoadFailed()
        }
        override fun onRenderProcessGone(view:WebView?, detail:RenderProcessGoneDetail?):Boolean {
            if(view===webView&&!disposed){
                interrupted=true
                recoveryIndicator.text="Page crashed — restoring this session…"
                recoveryIndicator.visibility=View.VISIBLE
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
        addView(body,LayoutParams(-1,-1).apply{
            setMargins(host.dp(1),host.dp(26),host.dp(1),host.dp(1))
        })
        body.addView(webView,LayoutParams(-1,-1))
        // A miniaturized Right Stack window is a single tap target: it switches focus
        // without sending the tap to the loaded webpage or resetting its session.
        thumbnailTap.apply{
            isClickable=true;isFocusable=true
            contentDescription="Switch window into expanded view"
            background=android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(70,255,255,255)),null,null)
            setOnClickListener{host.focus(this@PaneCard)}
            setOnLongClickListener{host.windowContextMenu(this@PaneCard);true}
        }
        body.addView(thumbnailTap,LayoutParams(-1,-1))
        // Blank panes have no interactive webpage. Taps expand; holds open controls.
        // This overlay is never enabled over a loaded site's selectable text.
        blankTap.apply{
            isClickable=true;isFocusable=true
            contentDescription="Empty window: tap to enter an address, hold for pane menu"
            background=android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(55,255,255,255)),null,null)
            setOnClickListener{host.tesseraFocusAddress(state.id)}
            setOnLongClickListener{host.windowContextMenu(this@PaneCard);true}
        }
        body.addView(blankTap,LayoutParams(-1,-1))
        // Match the handoff's empty-pane structure: a label and a real separate
        // 44dp "Open a site" action, not one ambiguous two-line tap target.
        val emptyContent=LinearLayout(host).apply{
            orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER
        }
        emptyContent.addView(TextView(host).apply{
            text="New session";textSize=13f;gravity=Gravity.CENTER
            setTextColor(0xb3ffffff.toInt())
        },LinearLayout.LayoutParams(-1,host.dp(28)))
        emptyContent.addView(TextView(host).apply{
            text="Open a site";textSize=13f;gravity=Gravity.CENTER
            setTextColor(Color.WHITE)
            isClickable=true;isFocusable=true
            contentDescription="Open a site in pane "+host.paneIndex(state.id)
            background=android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x50ffffff),
                GradientDrawable().apply{
                    setColor(0xff252c37.toInt())
                    cornerRadius=host.dp(11).toFloat()
                },null)
            setOnClickListener{host.tesseraFocusAddress(state.id)}
        },LinearLayout.LayoutParams(-1,host.dp(44)))
        blankTap.addView(emptyContent,LayoutParams(host.dp(126),host.dp(72),Gravity.CENTER))
        badge.apply{
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
            // Transparent 44x36dp touch target around the visible 24x22dp number badge.
            // Webpage long presses remain owned by Android WebView (text/link selection).
            background=android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(90,190,208,230)),
                android.graphics.drawable.ColorDrawable(Color.TRANSPARENT),null)
            minimumWidth=host.dp(44);minimumHeight=host.dp(36)
            contentDescription="Window controls: long press number"
            setOnClickListener{
                if(compact)host.focus(this@PaneCard) else host.windowContextMenu(this@PaneCard)
            }
            setOnLongClickListener{host.windowContextMenu(this@PaneCard);true}
        }
        paneNumber.apply{
            textSize=11f;setTextColor(Color.WHITE);gravity=Gravity.CENTER
            setBackgroundColor(Color.argb(132,12,18,25))
        }
        badge.addView(paneNumber,LinearLayout.LayoutParams(host.dp(24),host.dp(22)))
        lock.isClickable=false;lock.isFocusable=false
        badge.addView(lock,LinearLayout.LayoutParams(host.dp(16),host.dp(22)))
        body.addView(badge,LayoutParams(host.dp(44),host.dp(36),Gravity.TOP or Gravity.RIGHT).apply{rightMargin=host.dp(3);topMargin=host.dp(3)})
        updatePaneNumber()
        // Retain expand / restore outside the seven-item menu; the WebViews resize in place.
        body.addView(expand,LayoutParams(host.dp(44),host.dp(44),Gravity.BOTTOM or Gravity.RIGHT))
        expand.setOnClickListener{expand.pressedFeedback();host.focus(this)}
        selectionLayer.setBackgroundColor(Color.argb(48,82,149,252))
        selectionLayer.visibility=View.GONE
        selectionLayer.isClickable=true
        selectionLayer.foreground=android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(Color.argb(80,255,255,255)),null,null)
        selectionLayer.setOnClickListener{host.toggleWindowSelection(state.id)}
        selectionMark.apply{
            text="○";textSize=21f;setTextColor(Color.WHITE);gravity=Gravity.CENTER
            setBackgroundColor(Color.argb(200,30,42,62))
        }
        selectionLayer.addView(selectionMark,LayoutParams(host.dp(26),host.dp(26),Gravity.TOP or Gravity.RIGHT))
        addView(selectionLayer,LayoutParams(-1,-1))
        recoveryIndicator.apply{
            textSize=11f;setTextColor(Color.rgb(255,215,125));gravity=Gravity.CENTER
            setPadding(host.dp(4),host.dp(2),host.dp(4),host.dp(2))
            setBackgroundColor(Color.argb(225,37,33,28));visibility=View.GONE
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
            accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        body.addView(recoveryIndicator,LayoutParams(-2,-2,Gravity.CENTER))
        errorOverlay.apply{
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER
            setPadding(host.dp(14),host.dp(14),host.dp(14),host.dp(14))
            setBackgroundColor(0xff141920.toInt())
            visibility=View.GONE
        }
        body.addView(errorOverlay,LayoutParams(-1,-1))
        refreshIndicator.apply{
            text="↻";textSize=22f;gravity=Gravity.CENTER
            setTextColor(0xff0b0e13.toInt())
            background=GradientDrawable().apply{
                setColor(Color.WHITE);shape=GradientDrawable.OVAL
            }
            visibility=View.GONE
        }
        body.addView(refreshIndicator,LayoutParams(host.dp(36),host.dp(36),
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply{topMargin=host.dp(12)})
        configureHeader()
        header.isClickable=true
        addView(header,LayoutParams(-1,host.dp(26),Gravity.TOP))
        addView(headerProgress,LayoutParams(-1,host.dp(2),Gravity.TOP).apply{
            topMargin=host.dp(24)
        })
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
            fresh.settings.apply {
                javaScriptEnabled=true;domStorageEnabled=true;databaseEnabled=true
                loadsImagesAutomatically=true;javaScriptCanOpenWindowsAutomatically=false
                setSupportMultipleWindows(false)
                // Native Android WebView gestures, no SIXPANE zoom buttons.
                setSupportZoom(true)
                builtInZoomControls=true;displayZoomControls=false
                useWideViewPort=true;loadWithOverviewMode=!state.desktop
                // QA variant ONLY: workspace 1 panes 1-3 are native-UA controls;
                // panes 4-6 omit "; wv)". Workspace 2 swaps assignments.
                // The same crossover repeats across subsequent workspaces.
                if (BuildConfig.QA_WV_AB) {
                    val ordinal = host.paneIndex(state.id) - 1
                    check(ordinal >= 0) { "QA pane missing from workspace manifest" }
                    val laterHalf = ordinal % WorkspacePolicy.PANE_COUNT >= 3
                    val reverse = (ordinal / WorkspacePolicy.PANE_COUNT) % 2 == 1
                    if (laterHalf != reverse) {
                        // The actual pane and instrumentation metadata arm use
                        // exactly the same UA/Client-Hints configuration path.
                        QaUaConfiguration.apply(
                            this, QaUaConfiguration.Treatment.UA_WITH_METADATA
                        )
                    }
                } else {
                    // Production builds apply the complete UA treatment to every pane.
                    // QA-only builds retain their existing control/treatment crossover.
                    QaUaConfiguration.apply(this, QaUaConfiguration.Treatment.UA_WITH_METADATA)
                }
                mediaPlaybackRequiresUserGesture=true
            }
            WebViewCompat.getProfile(fresh).cookieManager.setAcceptThirdPartyCookies(fresh,true)
            fresh.webViewClient=onPageChanged
            fresh.webChromeClient=object:WebChromeClient(){
                override fun onShowFileChooser(webView:WebView?,
                    filePathCallback:ValueCallback<Array<Uri>>?,
                    fileChooserParams:FileChooserParams?):Boolean {
                    if(filePathCallback==null)return false
                    host.tesseraChooseFile(filePathCallback)
                    return true
                }
                override fun onPermissionRequest(request:PermissionRequest?){
                    if(request==null)return
                    request.deny()
                    val resources=request.resources.joinToString(", ") {
                        when {
                            it.contains("AUDIO_CAPTURE")->"microphone"
                            it.contains("VIDEO_CAPTURE")->"camera"
                            else->"device permission"
                        }
                    }
                    host.notice((request.origin?.host?:"This site")+
                        " asked for "+resources+"; blocked")
                }
                override fun onGeolocationPermissionsShowPrompt(
                    origin:String?,callback:GeolocationPermissions.Callback?
                ){
                    callback?.invoke(origin,false,false)
                    host.notice((runCatching{Uri.parse(origin).host}.getOrNull()?:"This site")+
                        " requested location; blocked")
                }
                override fun onJsAlert(view:WebView?,url:String?,
                    message:String?,result:JsResult?):Boolean {
                    if(result==null)return false
                    if(host.isFinishing||host.isDestroyed){
                        result.cancel();return true
                    }
                    AlertDialog.Builder(host).setMessage(message.orEmpty())
                        .setPositiveButton("OK"){_,_->result.confirm()}
                        .setOnCancelListener{result.cancel()}.show()
                    return true
                }
                override fun onJsConfirm(view:WebView?,url:String?,
                    message:String?,result:JsResult?):Boolean{
                    if(result==null)return false
                    if(host.isFinishing||host.isDestroyed){
                        result.cancel();return true
                    }
                    AlertDialog.Builder(host).setMessage(message.orEmpty())
                        .setPositiveButton("OK"){_,_->result.confirm()}
                        .setNegativeButton("Cancel"){_,_->result.cancel()}
                        .setOnCancelListener{result.cancel()}.show()
                    return true
                }
                override fun onProgressChanged(view:WebView?,newProgress:Int){
                    if(view!==webView||!webViewAlive)return
                    loading=newProgress<100
                    headerProgress.visibility=if(loading)View.VISIBLE else View.GONE
                    if(newProgress<100)indicator.text="·" else updateZoomIndicator()
                    host.tesseraRefreshDock()
                }
            }
            fresh.setDownloadListener{url,userAgent,disposition,mimeType,_->
                if(!url.startsWith("http://")&&!url.startsWith("https://")){
                    host.notice("This download cannot be handled by Android DownloadManager")
                }else{
                    try {
                        val file=URLUtil.guessFileName(url,disposition,mimeType)
                            .replace(Regex("[\\\\/:*?\"<>|]"),"_")
                        val request=DownloadManager.Request(Uri.parse(url)).apply{
                            setTitle(file)
                            setMimeType(mimeType)
                            setNotificationVisibility(
                                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,file)
                            addRequestHeader("User-Agent",userAgent.orEmpty())
                            val cookies=WebViewCompat.getProfile(fresh).cookieManager
                                .getCookie(url).orEmpty()
                            if(cookies.isNotBlank())addRequestHeader("Cookie",cookies)
                        }
                        val manager=host.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        manager.enqueue(request)
                        host.notice("Downloading "+file)
                    }catch(e:Exception){
                        host.notice("Download failed: "+e.javaClass.simpleName)
                    }
                }
            }
            fresh.setBackgroundColor(Color.WHITE)
            fresh.setOnTouchListener { view,event ->
                when(event.actionMasked){
                    MotionEvent.ACTION_DOWN -> {
                        val generation=++refreshGestureGeneration
                        host.tesseraActivate(state.id)
                        startY=event.y;pullPermitted=false;pullTriggered=false
                        if(!state.locked && event.pointerCount==1 &&
                            (view as WebView).scrollY==0){
                            val density=host.resources.displayMetrics.density
                            val x=(event.x/density).toInt()
                            val y=(event.y/density).toInt()
                            val js="(function(){let e=document.elementFromPoint("+x+","+y+");"+
                                "while(e){if(e.tagName==='IFRAME')return false;"+
                                "if(e.scrollTop>0&&e.scrollHeight>e.clientHeight)return false;"+
                                "e=e.parentElement;}return true;})()"
                            fresh.evaluateJavascript(js){result->
                                // A delayed response from a prior drag must never
                                // arm a subsequent pinch or pull-to-refresh gesture.
                                if(generation==refreshGestureGeneration &&
                                    !disposed && webViewAlive && fresh===webView &&
                                    !state.locked){
                                    pullPermitted=result=="true"
                                }
                            }
                        }
                    }
                    MotionEvent.ACTION_POINTER_DOWN,MotionEvent.ACTION_POINTER_UP -> {
                        refreshGestureGeneration++
                        pullPermitted=false
                        pullTriggered=false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if(event.pointerCount==1 && !pullTriggered && !state.locked &&
                            pullPermitted && fresh.scrollY==0 &&
                            event.y-startY>=host.dp(144)){
                            pullTriggered=true;showPullIndicator();fresh.reload()
                            return@setOnTouchListener true
                        }
                    }
                    MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {
                        refreshGestureGeneration++
                        pullPermitted=false
                    }
                }
                false
            }
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

    private fun configureHeader(){
        header.orientation=LinearLayout.HORIZONTAL
        header.gravity=Gravity.CENTER_VERTICAL
        header.setPadding(host.dp(4),0,host.dp(4),0)
        headerNumber.apply{
            textSize=11f;setTypeface(null,Typeface.BOLD);gravity=Gravity.CENTER
            setTextColor(Color.WHITE)
            background=GradientDrawable().apply{
                setColor(0x66000000);cornerRadius=host.dp(5).toFloat()
            }
            minWidth=host.dp(20)
        }
        header.addView(headerNumber,LinearLayout.LayoutParams(host.dp(22),host.dp(18)))
        headerLock.apply{
            text="▣";textSize=12f;gravity=Gravity.CENTER
            setTextColor(0xffff4d57.toInt())
            background=GradientDrawable().apply{
                setColor(0xff141920.toInt());cornerRadius=host.dp(5).toFloat()
            }
        }
        header.addView(headerLock,LinearLayout.LayoutParams(host.dp(18),host.dp(18)))
        headerHost.apply{
            textSize=11.5f;setTextColor(Color.WHITE)
            maxLines=1;ellipsize=TextUtils.TruncateAt.END
            setPadding(host.dp(4),0,0,0)
        }
        header.addView(headerHost,LinearLayout.LayoutParams(0,-1,1f))
        headerBadge.apply{
            textSize=10f;setTextColor(Color.WHITE)
            gravity=Gravity.CENTER
        }
        header.addView(headerBadge,LinearLayout.LayoutParams(-2,-1))
        headerProgress.setBackgroundColor(Color.WHITE)
        val detector=GestureDetector(host,object:GestureDetector.SimpleOnGestureListener(){
            override fun onDown(e:MotionEvent):Boolean=true
            override fun onSingleTapConfirmed(e:MotionEvent):Boolean{
                host.tesseraActivate(state.id);return true
            }
            override fun onDoubleTap(e:MotionEvent):Boolean{
                host.tesseraActivate(state.id,true);return true
            }
            override fun onLongPress(e:MotionEvent){
                header.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                host.windowContextMenu(this@PaneCard)
            }
        })
        header.setOnTouchListener{_,event->detector.onTouchEvent(event);true}
        header.contentDescription="Pane header; make active, double tap to expand, hold for pane menu"
        header.accessibilityDelegate=object:View.AccessibilityDelegate(){
            override fun onInitializeAccessibilityNodeInfo(
                hostView:View,info:android.view.accessibility.AccessibilityNodeInfo
            ){
                super.onInitializeAccessibilityNodeInfo(hostView,info)
                info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,"Make active"))
                info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_EXPAND,"Expand or shrink"))
                info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK,"Pane menu"))
            }
            override fun performAccessibilityAction(view:View,action:Int,args:android.os.Bundle?):Boolean{
                when(action){
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK->{
                        host.tesseraActivate(state.id);return true
                    }
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_EXPAND->{
                        host.tesseraActivate(state.id,true);return true
                    }
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK->{
                        host.windowContextMenu(this@PaneCard);return true
                    }
                }
                return super.performAccessibilityAction(view,action,args)
            }
        }
        updateHeader()
    }
    private fun updateHeader(){
        if(header.childCount==0)return
        val tag=host.store.frameColor(state.id)
        val actual=if(tag!=0xff51606e.toInt())tag else
            if(host.tesseraIsActive(this@PaneCard.state.id))0xff252c37.toInt() else 0xff1c222b.toInt()
        header.setBackgroundColor(actual)
        val ink=FramePalette.textOnTag(actual)
        headerNumber.setTextColor(ink)
        headerHost.setTextColor(ink)
        headerBadge.setTextColor(ink)
        headerLock.visibility=if(state.locked)View.VISIBLE else View.GONE
        headerHost.text=runCatching{java.net.URI(state.url).host}.getOrNull()?:"New session"
        headerBadge.text=listOfNotNull(
            "Desktop".takeIf{state.desktop},
            (state.zoom.toString()+"%").takeIf{state.zoom!=100}
        ).joinToString(" ")
        headerProgress.visibility=if(loading)View.VISIBLE else View.GONE
    }
    private fun showLoadError(reason:String){
        loading=false
        errorOverlay.removeAllViews()
        errorOverlay.addView(TextView(host).apply{
            text="⚠\nCan't open this page\n"+reason
            textSize=15f;setTextColor(Color.WHITE);gravity=Gravity.CENTER
        },LinearLayout.LayoutParams(-1,host.dp(110)))
        errorOverlay.addView(TextView(host).apply{
            text="Retry";gravity=Gravity.CENTER;textSize=16f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xff252c37.toInt())
            setOnClickListener{errorOverlay.visibility=View.GONE;refresh()}
        },LinearLayout.LayoutParams(-1,host.dp(44)))
        errorOverlay.visibility=View.VISIBLE
        host.onPaneUrlChanged(state.id)
    }
    private fun showPullIndicator(){
        refreshIndicator.visibility=View.VISIBLE
        refreshAnimator?.cancel()
        refreshAnimator=ObjectAnimator.ofFloat(refreshIndicator,View.ROTATION,0f,360f).apply{
            duration=600L;repeatCount=ValueAnimator.INFINITE;start()
        }
    }
    private fun stopPullIndicator(){
        refreshAnimator?.cancel();refreshAnimator=null
        refreshIndicator.visibility=View.GONE
        refreshIndicator.rotation=0f
    }

    fun updatePaneNumber(){
        val index=host.paneIndex(state.id)
        paneNumber.text=index.toString();paneNumber.contentDescription="Pane $index"
        headerNumber.text=index.toString()
    }
    fun updateFrameColor(){
        background=GradientDrawable().apply{
            setColor(0xff0b0e13.toInt())
            setStroke((if(selectedOutline)host.dp(3)
                else if(host.tesseraIsActive(this@PaneCard.state.id))((host.dp(3)+1)/2)
                else host.dp(1)).coerceAtLeast(1),
                if(selectedOutline||host.tesseraIsActive(this@PaneCard.state.id))Color.WHITE else 0xff2b3340.toInt())
            cornerRadius=host.dp(8).toFloat()
        }
        updateHeader()
    }
    private fun applyViewportDimensions(w:Int,h:Int){
        if(!webViewAlive)return
        // Small genuine WebView viewport-size differences within identical visible card geometry.
        // View transforms visually fit the independent surface into its aligned host cell.
        val sx=WorkspacePolicy.normalViewScale(state.widthPercent)
        val sy=WorkspacePolicy.normalViewScale(state.heightPercent)
        val trueW=(w/sx).roundToInt().coerceAtLeast(1).let{
            if(host.store.fullSizeSmallPanes && w<host.dp(320))
                maxOf(it,(host.dp(320)/sx).roundToInt())
            else it
        }
        val trueH=(h/sy).roundToInt().coerceAtLeast(1)
        val lp=webView.layoutParams
        if(lp.width!=trueW||lp.height!=trueH){lp.width=trueW;lp.height=trueH;webView.layoutParams=lp}
        webView.pivotX=0f;webView.pivotY=0f
        webView.scaleX=w.toFloat()/trueW
        webView.scaleY=sy
    }
    fun updateLockStatus(){
        lock.locked=state.locked
        lock.visibility=if(state.locked)View.VISIBLE else View.GONE
        badge.contentDescription="Window ${host.paneIndex(state.id)} actions${if(state.locked)"; locked" else ""}"
        updateHeader()
    }
    private fun updateSiteLabel(){
        paneNumber.contentDescription="Window ${host.paneIndex(state.id)} · ${state.url}"
        updateHeader()
    }
    fun updateSelection(active:Boolean,selected:Boolean){
        selectionLayer.visibility=if(active)View.VISIBLE else View.GONE
        selectedOutline=selected
        selectionMark.text=if(selected)"✓" else "○"
        selectionMark.setTextColor(if(selected)Color.BLACK else Color.WHITE)
        selectionMark.background=GradientDrawable().apply{
            shape=GradientDrawable.OVAL
            setColor(if(selected)Color.WHITE else 0xff252c37.toInt())
        }
        selectionLayer.setBackgroundColor(if(selected)Color.argb(41,255,255,255) else Color.TRANSPARENT)
        updateFrameColor()
        selectionLayer.contentDescription=if(selected)"Selected browser" else "Select browser"
    }
    private fun updateInteractionSurfaces(){
        // Miniatures must be full tap targets even when their WebView is loaded:
        // one tap switches focus without sending an unintended click to the site.
        thumbnailTap.visibility=if(compact)View.VISIBLE else View.GONE
        blankTap.visibility=if(!compact && state.url=="about:blank")
            View.VISIBLE else View.GONE
    }
    fun updateControls(){
        // Focus and grid changes never replace WebViews or steal loaded-site gestures.
        expand.contentDescription=if(focused)"Restore six-window grid" else "Expand window ${host.paneIndex(state.id)}"
        // A thumbnail is already entirely tappable; hide its redundant expand arrow.
        expand.visibility=View.GONE
        badge.visibility=View.GONE
        updateHeader()
        updateInteractionSurfaces()
    }
    /** Explicit submit from the thumb-reachable sheet: no navigation on focus loss. */
    fun submitAddress(raw:String):Boolean {
        val next=BrowserPolicy.resolveAddressOrSearch(raw) ?: return false
        if(disposed)return false
        loadAddress(next)
        return true
    }
    fun loadAddress(url:String){
        if(disposed)return
        val candidates=if(url.startsWith("http://")||url.startsWith("https://"))
            BrowserPolicy.entryCandidates(url) else listOf(url)
        entryCandidates=if(candidates.size>1)candidates else emptyList()
        entryIndex=0;activeEntry=entryCandidates.firstOrNull()
        state.url=url;host.persist();updateSiteLabel();updateInteractionSurfaces();host.onPaneUrlChanged(state.id)
        // A navigation during backoff updates the recovery target without changing its profile.
        if(webViewAlive)webView.loadUrl(activeEntry?:url)
    }
    private fun updateZoomIndicator(){indicator.text=WorkspacePolicy.displayZoom(state.zoom);indicator.visibility=if(state.zoom==100)View.GONE else View.VISIBLE}
    private fun updateDesktopWithoutReload(){
        if(!webViewAlive)return
        webView.settings.useWideViewPort=true
        webView.settings.loadWithOverviewMode=!state.desktop
    }
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
        disposed=true;entryCandidates=emptyList();activeEntry=null
        recoveryHandler.removeCallbacksAndMessages(null);retry=null;loadTimeout=null
        discardWebView();interrupted=true
    }
    fun flushCookies(){if(webViewAlive)WebViewCompat.getProfile(webView).cookieManager.flush()}
}
