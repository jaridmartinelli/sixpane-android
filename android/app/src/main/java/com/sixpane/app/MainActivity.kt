package com.sixpane.app

import android.app.*
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.net.Uri
import android.provider.Settings
import android.view.*
import android.widget.*
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
    private lateinit var workspaceMore:TextView
    private var selecting=false
    private val selectedPanes=linkedSetOf<String>()
    private lateinit var stage:FrameLayout
    private val cards=linkedMapOf<String,PaneCard>()
    private var started=false
    private var pendingFailures=0
    private var lastLayoutWidth=0
    private var lastLayoutHeight=0
    private var layoutGeneration=0
    private var textNotice:Toast?=null
    fun dp(value:Int):Int=(value*resources.displayMetrics.density).roundToInt()
    fun paneIndex(id:String):Int=store.spaces.flatMap{it.panes}.indexOfFirst{it.id==id}+1
    fun auditPanes():List<Pair<PaneState,PaneCard?>> = store.spaces.flatMap{it.panes}.map{it to cards[it.id]}
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
            if(!isFinishing&&!isDestroyed&&store.spaces.any{ws->ws.panes.any{it.id==pane.id}}){
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
        workspaceMore=smallButton("⋮",28){if(selecting)selectionMenu() else workspaceMenu()}
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
        if(selecting){
            val back=smallButton("←",38){exitSelection()}
            back.contentDescription="Exit window selection"
            tabStrip.addView(back,LinearLayout.LayoutParams(dp(40),dp(35)))
            val count=TextView(this).apply{
                text=if(selectedPanes.isEmpty())"Select windows" else "${selectedPanes.size} selected"
                textSize=14f;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL
            }
            tabStrip.addView(count,LinearLayout.LayoutParams(-2,dp(35)))
            workspaceMore.contentDescription="Selection actions"
            return
        }
        workspaceMore.contentDescription="Workspace controls"
        store.spaces.forEachIndexed {index,ws->
            val row=LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
                setBackgroundColor(if(ws.id==store.currentId)Color.rgb(62,88,124) else Color.rgb(40,51,70))
            }
            val number=smallButton("${index+1}",24){
                if(selecting)exitSelection()
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
                        if(!isFinishing && store.spaces.any{w->w.id==new.id&&w.panes.any{p2->p2.id==p.id}}){
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
    /** Selection is scoped to the six panes in the current workspace, never across tabs. */
    fun enterSelection(initial:String?=null){
        selecting=true
        selectedPanes.clear()
        if(initial!=null && store.active().panes.any{it.id==initial}) selectedPanes.add(initial)
        // Select from the full six-pane grid rather than tiny Right Stack thumbnails.
        if(store.focusId!=null){store.focusId=null;store.save()}
        updateSelection()
        positionCards(true)
    }
    private fun exitSelection(){
        selecting=false;selectedPanes.clear();updateSelection()
    }
    fun toggleWindowSelection(id:String){
        if(!selecting){enterSelection(id);return}
        if(store.active().panes.none{it.id==id})return
        if(!selectedPanes.add(id))selectedPanes.remove(id)
        updateSelection()
    }
    private fun updateSelection(){
        renderTabs()
        cards.forEach{(id,card)->
            card.updateSelection(selecting && store.active().panes.any{it.id==id},selectedPanes.contains(id))
        }
    }
    /** Thumb-reachable controls: long-press the minimal top strip or tap the pane's ⋮. */
    fun windowContextMenu(card:PaneCard){
        if(selecting){toggleWindowSelection(card.state.id);return}
        val dlg=Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val palette=Color.rgb(38,49,65)
        fun rounded(color:Int,radius:Int=14)=GradientDrawable().apply{
            setColor(color);cornerRadius=dp(radius).toFloat()
        }
        fun touch(label:String,action:()->Unit):TextView=TextView(this).apply{
            text=label;textSize=14f;gravity=Gravity.CENTER
            setTextColor(Color.WHITE)
            background=rounded(Color.rgb(55,72,96),8)
            setOnClickListener{action()}
            contentDescription=label
        }
        val sheet=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16),dp(14),dp(16),dp(18))
            background=rounded(palette,18)
        }
        val heading=TextView(this).apply{
            text="Browser controls";textSize=14f;setTextColor(Color.WHITE)
            setPadding(0,0,0,dp(9))
        }
        sheet.addView(heading)
        // One direct URL field, no nested editor panel. Android IME Go also submits.
        val addressRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val address=EditText(this).apply{
            setSingleLine(true)
            setText(card.state.url)
            setSelectAllOnFocus(true)
            textSize=14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(10),0,dp(8),0)
            background=rounded(Color.rgb(25,35,50),7)
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions=EditorInfo.IME_ACTION_GO
            contentDescription="Browser URL"
        }
        fun navigate(){
            if(card.submitAddress(address.text.toString())){
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(address.windowToken,0)
                dlg.dismiss()
            }else address.error="Enter a valid web address"
        }
        address.setOnEditorActionListener{_,action,event->
            if(action==EditorInfo.IME_ACTION_GO || event?.keyCode==KeyEvent.KEYCODE_ENTER){navigate();true}else false
        }
        addressRow.addView(address,LinearLayout.LayoutParams(0,dp(48),1f))
        val go=touch("Go"){navigate()}
        addressRow.addView(go,LinearLayout.LayoutParams(dp(55),dp(48)).apply{marginStart=dp(8)})
        sheet.addView(addressRow)
        val zoomRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val zoomText=TextView(this).apply{
            text="${card.state.zoom}%";textSize=14f;setTextColor(Color.WHITE);gravity=Gravity.CENTER
            contentDescription="Reset zoom to 100 percent"
            setOnClickListener { card.resetZoom();text="100%" }
        }
        val minus=touch("−") {card.zoomByStep(-1);zoomText.text="${card.state.zoom}%"}
        val plus=touch("+") {card.zoomByStep(1);zoomText.text="${card.state.zoom}%"}
        zoomRow.addView(minus,LinearLayout.LayoutParams(dp(52),dp(48)))
        zoomRow.addView(zoomText,LinearLayout.LayoutParams(0,dp(48),1f))
        zoomRow.addView(plus,LinearLayout.LayoutParams(dp(52),dp(48)))
        sheet.addView(zoomRow,LinearLayout.LayoutParams(-1,dp(56)).apply{topMargin=dp(10)})
        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val lockButton=touch(if(card.state.locked)"Unlock" else "Lock"){
            card.state.locked=!card.state.locked
            card.updateLockStatus();store.save()
            dlg.dismiss()
        }
        val selectButton=touch("Select") {dlg.dismiss();enterSelection(card.state.id)}
        actions.addView(lockButton,LinearLayout.LayoutParams(0,dp(48),1f).apply{marginEnd=dp(5)})
        actions.addView(selectButton,LinearLayout.LayoutParams(0,dp(48),1f).apply{marginStart=dp(5)})
        sheet.addView(actions,LinearLayout.LayoutParams(-1,dp(48)).apply{topMargin=dp(7)})
        val tools=touch("More browser tools  ›") {dlg.dismiss();paneMenu(card)}
        sheet.addView(tools,LinearLayout.LayoutParams(-1,dp(44)).apply{topMargin=dp(8)})
        dlg.setContentView(sheet)
        dlg.window?.apply{
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            attributes=attributes.apply{dimAmount=.5f;gravity=Gravity.BOTTOM}
        }
        dlg.show()
        dlg.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT)
        dlg.window?.setGravity(Gravity.BOTTOM)
    }
    private fun shareUrls(urls:List<String>){
        val sites=urls.filter{it.startsWith("https://")||it.startsWith("http://")}.distinct()
        if(sites.isEmpty()){notice("No web addresses to share");return}
        val share=android.content.Intent(android.content.Intent.ACTION_SEND).apply{
            type="text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT,sites.joinToString("\n"))
        }
        startActivity(android.content.Intent.createChooser(share,"Share browser links"))
    }
    private fun selectionMenu(){
        val popup=PopupMenu(this,workspaceMore)
        val ws=store.active()
        val panes=ws.panes.filter {it.id in selectedPanes}
        val anyLocked=panes.any {it.locked}
        val anyUnlocked=panes.any {!it.locked}
        popup.menu.add("Select all")
        popup.menu.add("Deselect all").isEnabled=selectedPanes.isNotEmpty()
        popup.menu.add("Lock selected").isEnabled=anyUnlocked
        popup.menu.add("Unlock selected").isEnabled=anyLocked
        popup.menu.add("Close selected windows").isEnabled=panes.isNotEmpty()&&!anyLocked
        popup.menu.add("Bookmark selected").isEnabled=panes.isNotEmpty()
        popup.menu.add("Share selected").isEnabled=panes.isNotEmpty()
        popup.setOnMenuItemClickListener{item->
            when(item.title.toString()){
                "Select all"->{selectedPanes.clear();selectedPanes.addAll(ws.panes.map{it.id});updateSelection()}
                "Deselect all"->{selectedPanes.clear();updateSelection()}
                "Lock selected"->{panes.forEach{it.locked=true;cards[it.id]?.updateLockStatus()};store.save();updateSelection()}
                "Unlock selected"->{panes.forEach{it.locked=false;cards[it.id]?.updateLockStatus()};store.save();updateSelection()}
                "Close selected windows"->confirmCloseSelected()
                "Bookmark selected"->{panes.forEach{store.bookmark(it.url)};notice("${panes.size} pages bookmarked")}
                "Share selected"->shareUrls(panes.map{it.url})
            }
            true
        }
        popup.show()
    }
    private fun confirmCloseSelected(){
        val ws=store.active()
        val selected=ws.panes.filter{it.id in selectedPanes}
        if(selected.isEmpty())return
        if(selected.any{it.locked}){
            selected.filter{it.locked}.forEach{cards[it.id]?.lock?.blockedFeedback()}
            notice("Unlock all selected windows before closing them")
            return
        }
        val currentId=ws.id
        confirm("Close ${selected.size} window${if(selected.size==1)"" else "s"}?",
            "Selected sessions and their saved browser data will be retired for deletion. New blank, isolated browsers will replace them to preserve SIXPANE's six-window layout. Disk cleanup may require an app restart.",
            "Close & Replace"){
            if(store.active().id!=currentId){notice("Workspace changed; close cancelled");return@confirm}
            val ids=selected.map{it.id}.toSet()
            if(store.active().panes.filter{it.id in ids}.any{it.locked}){
                notice("Selected windows were locked; close cancelled");return@confirm
            }
            val replacements=store.replacePanes(ids)
            replacements.forEach{(old,_)->cards.remove(old.id)?.let{stage.removeView(it);it.destroyExplicitly()}}
            exitSelection()
            replacements.forEachIndexed{index,(_,fresh)->
                handler.postDelayed({
                    if(!isFinishing&&!isDestroyed&&store.spaces.any{w->w.id==currentId&&w.panes.any{it.id==fresh.id}}){
                        try{
                            val card=PaneCard(this,fresh);cards[fresh.id]=card
                            stage.addView(card,FrameLayout.LayoutParams(dp(150),dp(140)));positionCards(false)
                        }catch(e:Throwable){notice("New window could not start: ${e.message}")}
                    }
                },index*130L)
            }
            notice("${selected.size} sessions retired. Fresh profiles created; disk deletion pending clean restart.")
        }
    }
    private fun showBookmarks(){
        if(store.bookmarks.isEmpty()){notice("No bookmarks yet");return}
        val values=store.bookmarks.toTypedArray()
        AlertDialog.Builder(this).setTitle("Bookmarks").setItems(values){_,which->
            val target=store.focusId?.let{cards[it]}?:store.active().panes.firstNotNullOfOrNull{cards[it.id]}
            if(target==null)notice("No browser ready yet") else target.loadAddress(values[which])
        }.show()
    }
    private fun workspaceMenu(){
        val options=arrayOf(if(store.columns==2)"Switch to 3 × 2 grid" else "Switch to 2 × 3 grid",
            if(store.active().panes.all{it.locked})"Unlock all six" else "Lock all six",
            "Controlled test page", "Pending deletion status", "Select windows", "Bookmarks",
            "Open in all panes · this workspace", "Open in all panes · all workspaces",
            "Session audit", "Desktop UA: ${if(store.desktopUserAgent)"on" else "off"}",
            "Battery optimization exemption")
        AlertDialog.Builder(this).setItems(options){_,idx->when(idx){
            0->{store.columns=if(store.columns==2)3 else 2;store.save();positionCards(true)}
            1->{val value=!store.active().panes.all{it.locked};store.active().panes.forEach {it.locked=value;cards[it.id]?.updateLockStatus()};store.save()}
            2->{confirm("Open controlled test pages?","Navigates all six browsers in this workspace to the local isolation test page. Existing live page state would be interrupted.","Open test pages"){
                navigatePanes(store.active().panes.toList(),"https://appassets.androidplatform.net/assets/isolation_test.html")
            }}
            3->notice("${store.pendingDeletion.size} retired profiles still awaiting deletion on a clean process start")
            4->enterSelection()
            5->showBookmarks()
            6->bulkNavigation(false)
            7->bulkNavigation(true)
            8->SessionAudit(this).open()
            9->toggleGlobalUserAgent()
            10->requestBatteryExemption(true)
        }}.show()
    }
    private fun navigatePanes(panes:List<PaneState>,url:String){
        panes.forEach{pane->
            val card=cards[pane.id]
            if(card!=null)card.loadAddress(url) else pane.url=url
        }
        persist()
    }
    private fun bulkNavigation(allWorkspaces:Boolean){
        // Snapshot the target identities; never read an already-loaded pane URL for this field.
        val targets=if(allWorkspaces)store.spaces.flatMap{it.panes} else store.active().panes.toList()
        val input=EditText(this).apply{
            hint="Original portal URL";setSingleLine(true)
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setPadding(dp(18),dp(10),dp(18),dp(10))
        }
        val content=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply{
                text="Enter the original portal address. Each of the ${targets.size} existing panes enters independently. Never use a URL copied from a queued pane. This replaces their current live pages."
                setPadding(dp(18),dp(10),dp(18),dp(10))
            });addView(input)
        }
        val dialog=AlertDialog.Builder(this).setTitle("Open in ${targets.size} panes")
            .setView(content).setNegativeButton("Cancel",null).setPositiveButton("Open",null).create()
        dialog.setOnShowListener{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                val url=BrowserPolicy.normalizeAddress(input.text.toString())
                if(url==null||url=="about:blank"){input.error="Enter the original HTTP or HTTPS portal address";return@setOnClickListener}
                val existing=store.spaces.flatMap{it.panes}.map{it.id}.toSet()
                navigatePanes(targets.filter{it.id in existing},url)
                dialog.dismiss()
            }
        };dialog.show()
    }
    private fun toggleGlobalUserAgent(){
        if(store.spaces.any{ws->ws.panes.any{it.locked}}){notice("Unlock all panes before changing the shared user agent");return}
        confirm("Change desktop UA for all panes?",
            "This changes all browser request identities and may reload pages. Do this before joining a queue. The per-pane Desktop site toggle continues to control viewport fitting; when shared desktop UA is off it also selects desktop UA for that pane."){
            if(store.spaces.any{ws->ws.panes.any{it.locked}}){notice("A pane was locked; change cancelled");return@confirm}
            store.desktopUserAgent=!store.desktopUserAgent
            cards.values.forEach{it.applyGlobalUserAgent()}
        }
    }
    fun paneMenu(card:PaneCard){
        val choices=arrayOf(if(card.state.desktop)"Desktop site: on" else "Desktop site: off",
            "Back", "Forward", "Refresh", "Bookmark page", "Share page", "Advanced…")
        AlertDialog.Builder(this).setItems(choices){_,idx->when(idx){
            0->card.toggleDesktop()
            1->card.back()
            2->card.forward()
            3->card.refresh()
            4->{store.bookmark(card.state.url);notice("Page bookmarked")}
            5->shareUrls(listOf(card.state.url))
            6->AlertDialog.Builder(this).setItems(arrayOf("Reset page (keeps cookies)")){_,_->card.reset()}.show()
        }}.show()
    }
    fun cookieEditor(card:PaneCard){CookieEditor(this,card).open()}
    fun focus(card:PaneCard){
        store.focusId=if(store.focusId==card.state.id)null else card.state.id
        store.save();positionCards(true)
    }
    private fun positionCards(animate:Boolean){
        if(!started||!::stage.isInitialized)return
        val width=stage.width;val height=stage.height
        if(width<10||height<10)return
        val layoutRun=++layoutGeneration
        val gap=dp(2);val visible=store.active().panes
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
            card.updatePaneNumber()
            val r=desired[id]
            if(r==null){
                // Keep off-workspace WebViews allocated and attached; never pauseTimers/stopLoading.
                card.animate().cancel()
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
            card.updateSelection(selecting && visible.any{it.id==id},selectedPanes.contains(id))
            if(animate&&oldW>0&&oldH>0&&oldX>-1000f){
                card.post{
                    if(card.parent!==stage||layoutRun!=layoutGeneration)return@post
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
    override fun onConfigurationChanged(newConfig:Configuration){super.onConfigurationChanged(newConfig);if(::stage.isInitialized)stage.post{positionCards(false)}}
    override fun onStart(){
        super.onStart()
        if(started)try{startForegroundService(Intent(this,PaneKeepAliveService::class.java))}
        catch(e:RuntimeException){notice("Background process priority unavailable: ${e.message}")}
    }
    override fun onResume(){
        super.onResume()
        if(!started)return
        val prompts=getSharedPreferences("sixpane-runtime-prompts",MODE_PRIVATE)
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED
            &&!prompts.getBoolean("notifications-asked",false)){
            prompts.edit().putBoolean("notifications-asked",true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),NOTIFICATION_REQUEST)
        }else requestBatteryExemption()
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==NOTIFICATION_REQUEST){
            if(grantResults.firstOrNull()!=PackageManager.PERMISSION_GRANTED)
                notice("Notifications denied. Background service can still run; Android shows it in Active apps. Keep SIXPANE open for live sessions.")
            requestBatteryExemption()
        }
    }
    private fun requestBatteryExemption(explicit:Boolean=false){
        val manager=getSystemService(PowerManager::class.java)
        if(manager.isIgnoringBatteryOptimizations(packageName)){
            if(explicit)notice("SIXPANE already has a battery optimization exemption")
            return
        }
        val prompts=getSharedPreferences("sixpane-runtime-prompts",MODE_PRIVATE)
        if(!explicit&&prompts.getBoolean("battery-asked",false))return
        prompts.edit().putBoolean("battery-asked",true).apply()
        try{startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:$packageName")))}
        catch(_:RuntimeException){notice("Battery exemption request unavailable. Set SIXPANE battery use to Unrestricted in Android app settings.")}
    }
    override fun onPause(){
        cards.values.forEach{try{it.flushCookies()}catch(_:Exception){}}
        if(::store.isInitialized)store.save();super.onPause()
    }
    override fun onDestroy(){
        // A real Android Activity teardown necessarily loses in-memory JS runtime state.
        handler.removeCallbacksAndMessages(null)
        cards.values.forEach {try{it.destroyExplicitly()}catch(_:Throwable){}}
        cards.clear()
        if(started)stopService(Intent(this,PaneKeepAliveService::class.java))
        super.onDestroy()
    }
    companion object {private const val NOTIFICATION_REQUEST=42}
}
