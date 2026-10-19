package com.sixpane.app

import android.app.*
import android.content.res.Configuration
import android.graphics.Color
import android.os.*
import android.view.*
import android.widget.*
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import java.util.Locale
import kotlin.math.roundToInt

/** SIXPANE phone-only client: local WebViews, no socket or remote browser service. */
class MainActivity:Activity(){
    lateinit var store:LocalStore
        private set
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var screen:LinearLayout
    private lateinit var tabScroller:HorizontalScrollView
    private lateinit var tabStrip:LinearLayout
    private lateinit var stage:FrameLayout
    private val cards=linkedMapOf<String,PaneCard>()
    private var started=false
    private var pendingFailures=0
    private var lastLayoutWidth=0
    private var lastLayoutHeight=0
    private var textNotice:Toast?=null
    fun dp(value:Int):Int=(value*resources.displayMetrics.density).roundToInt()
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        try{store=LocalStore(this)}catch(e:Exception){fatal("Saved workspace manifest could not be loaded: ${e.message}");return}
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)){
            fatal("This device WebView does not support MULTI_PROFILE. SIXPANE will not start in a shared-cookie mode. Update Android System WebView and try again.")
            return
        }
        cleanupDeletedProfiles()
        buildUI()
        started=true
        // Stagger WebView allocation only to avoid device resource spikes, not to evade site controls.
        val panes=store.spaces.flatMap { it.panes }
        panes.forEachIndexed { index,pane ->handler.postDelayed({
            if(!isFinishing&&!isDestroyed){
                try {
                    val card=PaneCard(this,pane)
                    cards[pane.id]=card
                    stage.addView(card,FrameLayout.LayoutParams(dp(150),dp(140)))
                    positionCards(false)
                }catch(e:Throwable){
                    notice("Browser ${index+1} could not start: ${e.message}. Session NOT active.")
                }
            }
        },index*130L)}
    }
    private fun fatal(message:String){
        val label=TextView(this).apply {text=message;textSize=17f;setTextColor(Color.WHITE);setPadding(dp(20),dp(35),dp(20),dp(20))}
        setContentView(ScrollView(this).apply{setBackgroundColor(Color.rgb(25,33,45));addView(label)})
    }
    private fun cleanupDeletedProfiles(){
        val profiles=store.pendingDeletion.toList()
        for(name in profiles){
            try {
                // Only invoked before any WebViews/profiles are created in this process.
                if(ProfileStore.getInstance().deleteProfile(name)){
                    store.pendingDeletion.remove(name)
                    store.save()
                }else{
                    // Missing profile is already gone.
                    store.pendingDeletion.remove(name);store.save()
                }
            }catch(_:IllegalStateException){pendingFailures++}
             catch(_:Exception){pendingFailures++}
        }
    }
    private fun buildUI(){
        val bg=Color.rgb(15,21,31)
        screen=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(bg)}
        if(Build.VERSION.SDK_INT>=30)screen.setOnApplyWindowInsetsListener{v,insets->
            val bars=insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            insets
        }
        val tabsRow=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(4),dp(4),dp(4),dp(4));setBackgroundColor(Color.rgb(26,34,47))
        }
        tabStrip=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        tabScroller=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;clipChildren=false;addView(tabStrip)}
        tabsRow.addView(tabScroller,LinearLayout.LayoutParams(0,dp(39),1f))
        val workspaceMore=smallButton("⋮",28){workspaceMenu()}
        workspaceMore.contentDescription="Workspace controls"
        tabsRow.addView(workspaceMore,LinearLayout.LayoutParams(dp(30),dp(38)))
        screen.addView(tabsRow,LinearLayout.LayoutParams(-1,dp(47)))
        stage=FrameLayout(this).apply{
            clipChildren=true;clipToPadding=true;setBackgroundColor(bg)
            addOnLayoutChangeListener{_,l,t,r,b,_,_,_,_->
                if(r-l!=lastLayoutWidth||b-t!=lastLayoutHeight){lastLayoutWidth=r-l;lastLayoutHeight=b-t;positionCards(false)}
            }
        }
        screen.addView(stage,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(screen)
        renderTabs()
        if(pendingFailures>0)notice("${pendingFailures} retired profiles await deletion on a later clean start; no IDs will be reused")
    }
    private fun smallButton(label:String,width:Int=32,action:()->Unit):TextView=TextView(this).apply{
        text=label;textSize=17f;setTextColor(Color.argb(208,255,255,255));gravity=Gravity.CENTER
        setBackgroundColor(Color.TRANSPARENT);setOnClickListener{action()};minWidth=dp(width)
    }
    fun persist(){store.save()}
    fun notice(message:String){textNotice?.cancel();textNotice=Toast.makeText(this,message,Toast.LENGTH_LONG);textNotice?.show()}
    fun confirm(title:String,message:String,yes:String="Continue",action:()->Unit){
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setNegativeButton("Cancel",null).setPositiveButton(yes){_,_->action()}.show()
    }
    fun confirm(title:String,message:String,action:()->Unit)=confirm(title,message,"Continue",action)
    private fun renderTabs(){
        tabStrip.removeAllViews()
        store.spaces.forEachIndexed {index,ws->
            val row=LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
                setBackgroundColor(if(ws.id==store.currentId)Color.rgb(62,88,124) else Color.rgb(40,51,70))
            }
            val number=smallButton("${index+1}",24){
                store.currentId=ws.id;store.focusId=null;store.save();renderTabs();positionCards(true)
            }
            number.textSize=12f;row.addView(number,LinearLayout.LayoutParams(dp(25),dp(34)))
            val lock=CornerIcon(this,CornerIcon.Kind.LOCK).apply{
                locked=ws.locked;contentDescription=if(ws.locked)"Unlock tab ${index+1}" else "Lock tab ${index+1}"
                setOnClickListener {ws.locked=!ws.locked;locked=ws.locked;pressedFeedback();store.save();renderTabs()}
            }
            row.addView(lock,LinearLayout.LayoutParams(dp(27),dp(34)))
            val close=smallButton("×",27){
                if(ws.locked){lock.blockedFeedback();notice("Unlock Workspace ${index+1} to close it")}
                else closeWorkspace(ws)
            }
            close.alpha=if(ws.locked) .22f else .70f
            close.contentDescription="Close Workspace ${index+1}"
            row.addView(close,LinearLayout.LayoutParams(dp(23),dp(34)))
            tabStrip.addView(row,LinearLayout.LayoutParams(dp(75),dp(35)).apply{marginEnd=dp(3)})
        }
        if(WorkspacePolicy.canCreate(store.spaces.size)){
            val add=smallButton("+",34){
                try{
                    store.create();renderTabs();positionCards(false)
                    val new=store.active()
                    new.panes.forEachIndexed {i,p-> handler.postDelayed({
                        if(!isFinishing && store.spaces.any{it.id==new.id}){
                            try{val card=PaneCard(this,p);cards[p.id]=card;stage.addView(card,FrameLayout.LayoutParams(dp(150),dp(140)));positionCards(false)}
                            catch(e:Throwable){notice("Browser allocation failed: ${e.message}")}
                        }
                    },i*130L)}
                }catch(e:Exception){notice("Cannot create workspace: ${e.message}")}
            }
            add.contentDescription="Create six fresh browsers"
            tabStrip.addView(add,LinearLayout.LayoutParams(dp(35),dp(35)))
        }
        tabScroller.post{tabScroller.scrollTo(if(store.spaces.indexOfFirst{it.id==store.currentId}>=3)tabStrip.width else 0,0)}
    }
    private fun closeWorkspace(ws:WorkspaceState){
        if(ws.locked){notice("Workspace locked");return}
        confirm("Close workspace?","All six browser sessions will close immediately and their stored profiles will be retired for deletion. Disk cleanup may require a clean app restart. New workspaces start with fresh profiles.","Close & Delete"){
            // Don't mark cleanup complete while WebKit still has references to the profiles.
            store.close(ws.id) // durable tombstone FIRST: even a crash cannot restore these profiles
            for(p in ws.panes){
                cards.remove(p.id)?.let{stage.removeView(it);it.destroyExplicitly()}
            }
            if(store.spaces.size==1 && store.active().panes.none{cards.containsKey(it.id)}){
                val fresh=store.active()
                fresh.panes.forEachIndexed{i,p->handler.postDelayed({
                    try{val card=PaneCard(this,p);cards[p.id]=card;stage.addView(card,FrameLayout.LayoutParams(dp(150),dp(140)));positionCards(false)}
                    catch(e:Throwable){notice("Browser allocation failed: ${e.message}")}
                },i*130L)}
            }
            // Can't safely call deleteProfile after a profile has been loaded in this process.
            // Pending tombstones are retired at the next cold start; UI never claims disk deletion is complete.
            renderTabs();positionCards(false)
            notice("Six sessions closed. Their profiles are retired; on-disk cleanup is pending a clean restart.")
        }
    }
    private fun workspaceMenu(){
        val options=arrayOf(if(store.columns==2)"Switch to 3 × 2 grid" else "Switch to 2 × 3 grid",
            if(store.active().panes.all{it.locked})"Unlock all six" else "Lock all six",
            "Controlled test page", "Pending deletion status")
        AlertDialog.Builder(this).setItems(options){_,idx->when(idx){
            0->{store.columns=if(store.columns==2)3 else 2;store.save();positionCards(true)}
            1->{val value=!store.active().panes.all{it.locked};store.active().panes.forEach {it.locked=value;cards[it.id]?.lock?.locked=value};store.save()}
            2->{confirm("Open controlled test pages?","Navigates all six browsers in this workspace to the local isolation test page. Existing live page state would be interrupted.","Open test pages"){
                store.active().panes.forEach {it.url="https://appassets.androidplatform.net/assets/isolation_test.html";cards[it.id]?.webView?.loadUrl(it.url)};persist()
            }}
            3->notice("${store.pendingDeletion.size} retired profiles still awaiting deletion on a clean process start")
        }}.show()
    }
    fun paneMenu(card:PaneCard){
        val choices=arrayOf(if(card.state.desktop)"Desktop site: on" else "Desktop site: off",
            "Back", "Forward", "Refresh", "Zoom out −", "Zoom in +", "Advanced…")
        AlertDialog.Builder(this).setItems(choices){_,idx->when(idx){
            0->card.toggleDesktop()
            1->card.back()
            2->card.forward()
            3->card.refresh()
            4->card.zoomByStep(-1)
            5->card.zoomByStep(1)
            6->AlertDialog.Builder(this).setItems(arrayOf("Reset page (keeps cookies)")){_,_->card.reset()}.show()
        }}.show()
    }
    fun cookieEditor(card:PaneCard){CookieEditor(this,card).open()}
    fun browserInterrupted(state:PaneState,reason:String){notice("Browser ${state.id.takeLast(5)} interrupted: $reason")}
    fun focus(card:PaneCard){
        store.focusId=if(store.focusId==card.state.id)null else card.state.id
        store.save();positionCards(true)
    }
    private fun positionCards(animate:Boolean){
        if(!started||!::stage.isInitialized)return
        val width=stage.width;val height=stage.height
        if(width<10||height<10)return
        val gap=dp(4);val visible=store.active().panes
        val f=store.focusId?.takeIf{id->visible.any{it.id==id}}
        val desired=mutableMapOf<String,IntArray>()
        if(f==null){
            val cols=store.columns;val rows=6/cols
            val cellW=(width-gap*(cols+1))/cols
            val cellH=(height-gap*(rows+1))/rows
            visible.forEachIndexed{i,p->desired[p.id]=intArrayOf(gap+(i%cols)*(cellW+gap),gap+(i/cols)*(cellH+gap),cellW,cellH)}
        }else{
            val stackW=(width*.235f).toInt().coerceAtLeast(dp(72))
            val mainW=(width-stackW-gap*3).coerceAtLeast(dp(80))
            val mainH=height-2*gap
            desired[f]=intArrayOf(gap,gap,mainW,mainH)
            val others=visible.filter{it.id!=f}
            val cellH=(mainH-gap*4)/5
            others.forEachIndexed{i,p->desired[p.id]=intArrayOf(mainW+gap*2,gap+i*(cellH+gap),stackW,cellH)}
        }
        for((id,card) in cards){
            val r=desired[id]
            if(r==null){
                // Keep off-workspace WebViews allocated and attached; never pauseTimers/stopLoading.
                card.x=-10000f
                card.y=0f
                card.visibility=View.VISIBLE
                continue
            }
            val oldX=card.x;val oldY=card.y;val oldW=card.width;val oldH=card.height
            card.animate().cancel();card.translationX=0f;card.translationY=0f;card.scaleX=1f;card.scaleY=1f
            val lp=card.layoutParams as FrameLayout.LayoutParams
            lp.width=r[2].coerceAtLeast(dp(60));lp.height=r[3].coerceAtLeast(dp(50))
            lp.leftMargin=r[0];lp.topMargin=r[1]
            card.layoutParams=lp
            card.compact=r[2]<dp(155)
            card.focused=f==id
            if(animate&&oldW>0&&oldH>0&&oldX>-1000f){
                card.post{
                    if(card.parent!==stage)return@post
                    card.pivotX=0f;card.pivotY=0f
                    card.scaleX=oldW.toFloat()/card.width.coerceAtLeast(1)
                    card.scaleY=oldH.toFloat()/card.height.coerceAtLeast(1)
                    card.translationX=oldX-card.left.toFloat()
                    card.translationY=oldY-card.top.toFloat()
                    card.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(320).start()
                }
            }
        }
    }
    override fun onConfigurationChanged(newConfig:Configuration){super.onConfigurationChanged(newConfig);stage.post{positionCards(false)}}
    override fun onPause(){
        cards.values.forEach{try{it.flushCookies()}catch(_:Exception){}}
        store.save();super.onPause()
    }
    override fun onDestroy(){
        // A real Android Activity teardown necessarily loses in-memory JS runtime state.
        handler.removeCallbacksAndMessages(null)
        cards.values.forEach {try{it.destroyExplicitly()}catch(_:Throwable){}}
        cards.clear()
        super.onDestroy()
    }
}
