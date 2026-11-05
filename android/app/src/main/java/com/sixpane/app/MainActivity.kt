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
    private lateinit var tabStrip:LinearLayout
    private lateinit var tabScroller:HorizontalScrollView
    private val tabLockIcons=mutableMapOf<String,CornerIcon>()
    private lateinit var universalBar: UniversalBar
    private lateinit var filterEmpty:TextView
    private val saveFilter=Runnable{if(::store.isInitialized)store.save()}
    private var selecting=false
    private val selectedPanes=linkedSetOf<String>()
    private var filteredPageIndex=0
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
        tabStrip=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(17,23,29))
        }
        stage=FrameLayout(this).apply{
            clipChildren=true;clipToPadding=true;setBackgroundColor(bg)
            addOnLayoutChangeListener{_,l,t,r,b,_,_,_,_->
                if(r-l!=lastLayoutWidth||b-t!=lastLayoutHeight){lastLayoutWidth=r-l;lastLayoutHeight=b-t;positionCards(false)}
            }
        }
        universalBar = UniversalBar(
            activity = this,
            initialFilter = store.filterQuery,
            onFilterChanged = ::onFilterChanged,
            onAddressSubmitted = ::submitFocusedAddress,
            onBookmarkClick = ::toggleFocusedBookmark,
            onBookmarkLongClick = ::showFocusedBookmarks,
            onClearFilters = ::clearAllFilters,
            onMenuClick = ::universalMenu,
        )
        screen.addView(universalBar.view,LinearLayout.LayoutParams(-1,dp(48)))
        screen.addView(stage,LinearLayout.LayoutParams(-1,0,1f))
        tabScroller=HorizontalScrollView(this).apply{
            isHorizontalScrollBarEnabled=false
            addView(tabStrip,android.widget.FrameLayout.LayoutParams(-2,-1))
        }
        screen.addView(tabScroller,LinearLayout.LayoutParams(-1,dp(48)))
        filterEmpty=TextView(this).apply{
            text="No windows match";gravity=Gravity.CENTER
            textSize=13f;setTextColor(Color.rgb(174,186,206));visibility=View.GONE
        }
        stage.addView(filterEmpty,FrameLayout.LayoutParams(-1,-1))
        setContentView(screen)
        applyChromeTheme()
        renderTabs()
        updateUniversalBar()
        if(pendingFailures>0)notice("${pendingFailures} retired profiles await deletion on a later clean start; no IDs will be reused")
    }

    private fun onFilterChanged(query: String) {
        store.filterQuery = query
        filteredPageIndex=0
        if(store.focusId!=null)store.focusId=null
        handler.removeCallbacks(saveFilter)
        handler.postDelayed(saveFilter, 250L)
        if(selecting){selectedPanes.clear();updateSelection()}
        renderTabs()
        updateUniversalBar()
        positionCards(false)
    }

    private fun updateUniversalBar() {
        if (!::universalBar.isInitialized) return
        val focused = allPanes().firstOrNull { it.id == store.focusId }
        universalBar.render(
            filterQuery = store.filterQuery,
            selectedColors = store.selectedFrameColors,
            focusedUrl = focused?.url,
            bookmarked = focused?.let { store.bookmarkedInPane(it.id, it.url) } == true,
            selectionCount = selectedPanes.size.takeIf { selecting },
        )
    }

    private fun toggleFocusedBookmark() {
        val id = store.focusId ?: return
        val pane = allPanes().firstOrNull { it.id == id } ?: return
        when (store.toggleBookmarkForPane(id, pane.url)) {
            true -> notice("Saved to this browser's bookmarks")
            false -> notice("Removed from this browser's bookmarks")
            null -> notice("Open a webpage before bookmarking")
        }
        updateUniversalBar()
    }

    private fun showFocusedBookmarks() {
        store.focusId?.let { showBookmarksForPane(it) }
    }

    private fun submitFocusedAddress(raw: String) {
        val id = store.focusId ?: return
        val card = cards[id]
        val success = if (card != null) card.submitAddress(raw) else {
            val url = BrowserPolicy.resolveAddressOrSearch(raw)
            if (url == null) false else {
                allPanes().firstOrNull { it.id == id }?.url = url
                store.save()
                true
            }
        }
        universalBar.finishAddressSubmission(success)
        if (success) updateUniversalBar()
    }

    private fun allPanes():List<PaneState> = store.spaces.flatMap{it.panes}

    private fun filtering():Boolean =
        store.filterQuery.isNotBlank()||store.selectedFrameColors.isNotEmpty()

    /** Stable source order; matching never moves a pane between real workspaces. */
    private fun filteredMatches():List<PaneState> =
        allPanes().filter{pane->
            FramePalette.matches(pane.url,store.frameColor(pane.id),
                store.filterQuery,store.selectedFrameColors)
        }

    private fun clampFilteredPage(){
        if(filtering()){
            filteredPageIndex=filteredPageIndex.coerceIn(
                0,FilteredPanePages.maxPageIndex(filteredMatches().size)
            )
        }else filteredPageIndex=0
    }

    private fun currentFilteredPanes():List<PaneState>{
        if(!filtering())return store.active().panes.toList()
        clampFilteredPage()
        return FilteredPanePages.page(filteredMatches(),filteredPageIndex)
    }

    fun onPaneUrlChanged(id:String){
        if(!::universalBar.isInitialized)return
        if(filtering()){
            clampFilteredPage()
            if(store.focusId!=null && currentFilteredPanes().none{it.id==store.focusId}){
                store.focusId=null;store.save()
            }
            if(selecting){
                selectedPanes.retainAll(currentFilteredPanes().map{it.id}.toSet())
                updateSelection()
            }
            renderTabs()
            positionCards(false)
        }
        if(store.focusId==id || !filtering())updateUniversalBar()
    }
    private fun clearAllFilters(){
        store.filterQuery=""
        store.selectedFrameColors.clear()
        filteredPageIndex=0
        store.focusId=null
        store.save()
        universalBar.setFilterText("")
        if(selecting){selectedPanes.clear();updateSelection()}
        renderTabs()
        updateUniversalBar();positionCards(true)
    }
    private fun pickFilterColors(){
        val names=FramePalette.names.toTypedArray()
        val chosen=store.selectedFrameColors.toMutableSet()
        val checked=BooleanArray(names.size){names[it] in chosen}
        val dialog=AlertDialog.Builder(this)
            .setTitle("Filter colors · up to 6")
            .setMultiChoiceItems(names,checked){dialogInterface,index,selected->
                val name=names[index]
                if(selected&&chosen.size>=FramePalette.MAX_SELECTED_COLORS){
                    (dialogInterface as AlertDialog).listView.setItemChecked(index,false)
                    notice("Choose up to six colors")
                }else if(selected)chosen.add(name) else chosen.remove(name)
            }
            .setNegativeButton("Cancel",null)
            .setNeutralButton("Clear"){_,_->clearAllFilters()}
            .setPositiveButton("Apply"){_,_->
                if(!FramePalette.validSelection(chosen))return@setPositiveButton
                store.selectedFrameColors.clear();store.selectedFrameColors.addAll(chosen)
                // Selecting picker chips explicitly replaces the prior typed filter.
                store.filterQuery=""
                filteredPageIndex=0
                store.focusId=null
                store.save()
                universalBar.setFilterText("")
                if(selecting){selectedPanes.clear();updateSelection()}
                renderTabs()
                updateUniversalBar();positionCards(true)
            }.create()
        dialog.show()
    }
    private fun selectVisiblePanes(){
        if(!selecting)enterSelection()
        selectedPanes.clear()
        selectedPanes.addAll(currentFilteredPanes().map{it.id})
        updateSelection()
    }
    private fun universalMenu() {
        val panes = allPanes().filter { it.id in selectedPanes }
        universalBar.showMenu(
            selecting = selecting,
            selectionCount = selectedPanes.size,
            anyLocked = panes.any { it.locked },
            anyUnlocked = panes.any { !it.locked },
            colorCount = store.selectedFrameColors.size,
            hasFilters = store.filterQuery.isNotBlank() || store.selectedFrameColors.isNotEmpty(),
            hasFocusedPane = store.focusId != null,
            columns = store.columns,
        ) { action ->
            when (action) {
                UniversalMenuAction.SELECT_MULTIPLE -> enterSelection()
                UniversalMenuAction.EXIT_SELECTION -> exitSelection()
                UniversalMenuAction.SELECT_VISIBLE -> selectVisiblePanes()
                UniversalMenuAction.DESELECT_ALL -> {
                    selectedPanes.clear()
                    updateSelection()
                }
                UniversalMenuAction.LOCK_SELECTED, UniversalMenuAction.UNLOCK_SELECTED -> {
                    val lock = action == UniversalMenuAction.LOCK_SELECTED
                    panes.forEach {
                        it.locked = lock
                        cards[it.id]?.updateLockStatus()
                    }
                    store.save()
                    updateSelection()
                }
                UniversalMenuAction.CLOSE_SELECTED -> confirmCloseSelected()
                UniversalMenuAction.BOOKMARK_SELECTED -> {
                    panes.forEach { store.bookmarkForPane(it.id, it.url) }
                    notice("Saved bookmarks in ${panes.size} browsers")
                    updateUniversalBar()
                }
                UniversalMenuAction.SHARE_SELECTED -> shareUrls(panes.map { it.url })
                UniversalMenuAction.COLOR_SELECTED -> chooseColorForSelected()
                UniversalMenuAction.FILTER_COLORS -> pickFilterColors()
                UniversalMenuAction.CLEAR_FILTERS -> clearAllFilters()
                UniversalMenuAction.PANE_BOOKMARKS -> showFocusedBookmarks()
                UniversalMenuAction.WORKSPACE_TOOLS -> workspaceMenu()
                UniversalMenuAction.TOGGLE_GRID ->
                    changeGridColumns(if(store.columns==2)3 else 2)
                UniversalMenuAction.SETTINGS -> AppSettings(this).open()
            }
        }
    }
    private fun chooseColorForSelected(){
        val ids=selectedPanes.toSet()
        if(ids.isEmpty())return
        AlertDialog.Builder(this).setTitle("Frame color · "+ids.size+" windows")
            .setItems(FramePalette.names.toTypedArray()){_,which->
                recolorWindows(ids,FramePalette.colors[which])
            }.show()
    }
    fun persist(){store.save()}
    fun notice(message:String){textNotice?.cancel();textNotice=Toast.makeText(this,message,Toast.LENGTH_LONG);textNotice?.show()}
    fun confirm(title:String,message:String,yes:String="Continue",action:()->Unit){
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setNegativeButton("Cancel",null).setPositiveButton(yes){_,_->action()}.show()
    }
    fun confirm(title:String,message:String,action:()->Unit)=confirm(title,message,"Continue",action)
    /** Temporary result tabs (6 matches per tab) don't alter source workspaces. */
    private fun renderFilteredTabs(){
        val pages=FilteredPanePages.pages(filteredMatches())
        val light=store.lightMode
        pages.forEachIndexed{index,page->
            val active=index==filteredPageIndex
            val base=GradientDrawable().apply{
                setColor(if(active){
                    if(light)Color.rgb(197,215,237) else Color.rgb(45,64,85)
                }else Color.TRANSPARENT)
                cornerRadius=dp(9).toFloat()
            }
            val tab=LinearLayout(this).apply{
                orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
                background=android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.argb(85,130,165,205)),base,null)
                contentDescription="Filtered tab "+(index+1)+", "+page.size+" windows"
                isSelected=active
                setOnClickListener{
                    if(index!=filteredPageIndex){
                        filteredPageIndex=index
                        store.focusId=null;store.save()
                        if(selecting){selectedPanes.clear();updateSelection()}
                        renderTabs();updateUniversalBar();positionCards(true)
                    }
                }
                setOnLongClickListener{
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    toggleFilteredGroupLocks(index);true
                }
            }
            tab.addView(TextView(this).apply{
                text=(index+1).toString();textSize=15f;gravity=Gravity.CENTER
                setTextColor(if(light)Color.rgb(26,43,63) else Color.rgb(231,239,250))
            },LinearLayout.LayoutParams(0,-1,1f))
            val groupLock=CornerIcon(this,CornerIcon.Kind.LOCK).apply{
                locked=page.isNotEmpty()&&page.all{it.locked}
                contentDescription=(if(locked)"Unlock" else "Lock")+" filtered group "+(index+1)
                setOnClickListener{pressedFeedback();toggleFilteredGroupLocks(index)}
                setOnLongClickListener{
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    toggleFilteredGroupLocks(index);true
                }
            }
            tab.addView(groupLock,LinearLayout.LayoutParams(dp(24),dp(36)))
            tabStrip.addView(tab,LinearLayout.LayoutParams(dp(64),dp(44)).apply{rightMargin=dp(2)})
        }
    }
    /** A filtered tab lock protects only the shown panes, not their source workspaces. */
    private fun toggleFilteredGroupLocks(index:Int){
        val page=FilteredPanePages.pages(filteredMatches()).getOrNull(index)?:return
        if(page.isEmpty())return
        val locked=!page.all{it.locked}
        page.forEach{
            it.locked=locked
            cards[it.id]?.updateLockStatus()
        }
        store.save()
        renderTabs()
    }

    /** Existing workspace tabs, immediately followed by one faded + tab. */
    private fun renderTabs(){
        tabStrip.removeAllViews()
        tabLockIcons.clear()
        val light=store.lightMode
        tabStrip.setBackgroundColor(if(light)Color.rgb(225,233,242) else Color.rgb(17,23,29))
        if(filtering()){
            clampFilteredPage()
            renderFilteredTabs()
            return
        }
        store.spaces.forEachIndexed{index,ws->
            val active=ws.id==store.currentId
            val tint=if(light)Color.rgb(197,215,237) else Color.rgb(45,64,85)
            val base=GradientDrawable().apply{
                setColor(if(active)tint else Color.TRANSPARENT);cornerRadius=dp(9).toFloat()
            }
            val tab=LinearLayout(this).apply{
                orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
                background=android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.argb(85,130,165,205)),base,null)
                contentDescription="Workspace "+(index+1)+
                    (if(ws.locked)"; locked" else "")+"; hold to lock or unlock"
                isEnabled=!selecting||active
                isSelected=active
                setOnClickListener{
                    if(ws.id!=store.currentId){
                        store.currentId=ws.id;store.focusId=null;store.save()
                        renderTabs();updateUniversalBar();positionCards(true)
                    }
                }
                setOnLongClickListener{
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    toggleWorkspaceLock(ws);true
                }
            }
            tab.addView(TextView(this).apply{
                text=(index+1).toString();textSize=15f;gravity=Gravity.CENTER
                setTextColor(if(light)Color.rgb(26,43,63) else Color.rgb(231,239,250))
                isClickable=false
            },LinearLayout.LayoutParams(0,-1,1f))
            // Never replace the tab lock with an X or the workspace number.
            val tabLock=CornerIcon(this,CornerIcon.Kind.LOCK).apply{
                locked=ws.locked
                contentDescription=(if(ws.locked)"Unlock" else "Lock")+" workspace "+(index+1)
                setOnClickListener{
                    pressedFeedback()
                    toggleWorkspaceLock(ws)
                }
                setOnLongClickListener{
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    toggleWorkspaceLock(ws);true
                }
                isEnabled=!selecting||active
            }
            tabLockIcons[ws.id]=tabLock
            tab.addView(tabLock,LinearLayout.LayoutParams(dp(24),dp(36)))
            tabStrip.addView(tab,LinearLayout.LayoutParams(dp(64),dp(44)).apply{rightMargin=dp(2)})
        }
        val count=store.spaces.size
        if(WorkspacePolicy.canCreate(count)){
            val plus=chromeButton("+ "+(count+1),dp(62)){
                if(!selecting)createWorkspace()
            }.apply{
                textSize=14f;alpha=.46f;isEnabled=!selecting
                contentDescription="Create workspace "+(count+1)
                setTextColor(if(light)Color.rgb(45,65,89) else Color.rgb(219,228,240))
            }
            tabStrip.addView(plus,LinearLayout.LayoutParams(dp(62),dp(44)))
        }
    }
    private fun toggleWorkspaceLock(ws:WorkspaceState){
        if(store.spaces.none{it.id==ws.id})return
        ws.locked=!ws.locked;store.save();renderTabs()
    }
    private fun changeGridColumns(columns:Int){
        if(store.columns==columns)return
        store.columns=columns;store.save();positionCards(true)
    }
    /** Light mode styles the app chrome, never webpages or browser identities. */
    fun applyChromeTheme(){
        if(!::screen.isInitialized||!::universalBar.isInitialized)return
        val light=store.lightMode
        val surface=if(light)Color.rgb(235,241,249) else Color.rgb(15,21,31)
        screen.setBackgroundColor(surface);stage.setBackgroundColor(surface)
        tabStrip.setBackgroundColor(if(light)Color.rgb(225,233,242) else Color.rgb(17,23,29))
        filterEmpty.setTextColor(if(light)Color.rgb(65,78,96) else Color.rgb(174,186,206))
        universalBar.applyChromeTheme(light)
        renderTabs()
    }
    private fun createWorkspace(){
        if(filtering()){notice("Clear the filter to add a workspace");return}
        if(!WorkspacePolicy.canCreate(store.spaces.size))return
        try{
            val new=store.create();renderTabs();positionCards(false)
            tabScroller.post{tabScroller.fullScroll(View.FOCUS_RIGHT)}
            new.panes.forEachIndexed{i,p->handler.postDelayed({
                if(!isFinishing&&!isDestroyed&&store.spaces.any{w->w.id==new.id&&w.panes.any{it.id==p.id}}){
                    try{val card=PaneCard(this,p);cards[p.id]=card;stage.addView(card,FrameLayout.LayoutParams(dp(150),dp(140)));positionCards(false)}
                    catch(e:Throwable){notice("Browser allocation failed: ${e.message}")}
                }
            },i*130L)}
        }catch(e:Exception){notice("Cannot create workspace: ${e.message}")}
    }
    private fun closeWorkspace(ws:WorkspaceState){
        if(!WorkspacePolicy.canCloseWorkspace(ws.locked,ws.panes.map{it.locked})){
            tabLockIcons[ws.id]?.blockedFeedback()
            ws.panes.filter{it.locked}.forEach{cards[it.id]?.lock?.blockedFeedback()}
            notice("Unlock workspace and all six browsers before closing");return
        }
        confirm("Close workspace?","All six browser sessions will close immediately and their stored profiles will be retired for deletion. Disk cleanup may require a clean app restart. New workspaces start with fresh profiles.","Close & Delete"){
            // Recheck the live locks at confirmation time. Never delete protected profiles.
            val current=store.spaces.firstOrNull{it.id==ws.id}?:return@confirm
            if(!WorkspacePolicy.canCloseWorkspace(current.locked,current.panes.map{it.locked})){
                current.panes.filter{it.locked}.forEach{cards[it.id]?.lock?.blockedFeedback()}
                notice("Workspace or a browser became locked; close cancelled");return@confirm
            }
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
    /** Selection is scoped to the six panes shown on the current workspace/filter tab. */
    fun enterSelection(initial:String?=null){
        selecting=true
        selectedPanes.clear()
        if(initial!=null && currentFilteredPanes().any{it.id==initial}) selectedPanes.add(initial)
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
        if(currentFilteredPanes().none{it.id==id})return
        if(!selectedPanes.add(id))selectedPanes.remove(id)
        updateSelection()
    }
    private fun updateSelection(){
        renderTabs();updateUniversalBar()
        cards.forEach{(id,card)->
            card.updateSelection(selecting && currentFilteredPanes().any{it.id==id},selectedPanes.contains(id))
        }
    }
    /** Exactly seven pane actions. A workspace-tab hold owns workspace-wide tools. */
    fun windowContextMenu(card:PaneCard){
        if(selecting&&!selectedPanes.contains(card.state.id)){selectedPanes.add(card.state.id);updateSelection()}
        val targets=if(selecting&&selectedPanes.isNotEmpty())allPanes().filter{it.id in selectedPanes} else listOf(card.state)
        WindowActions(this,card,targets).open()
    }
    fun selectingWindows():Boolean=selecting
    fun toggleSelectionFromMenu(card:PaneCard){if(selecting)exitSelection() else enterSelection(card.state.id)}
    fun updateWindowLocks(){cards.values.forEach{it.updateLockStatus()};persist();updateSelection()}
    fun windowCard(id:String):PaneCard?=cards[id]
    fun recolorWindows(ids:Set<String>,color:Int){
        store.setFrameColor(ids,color)
        ids.forEach{cards[it]?.updateFrameColor()}
        if(filtering()){
            clampFilteredPage()
            if(store.focusId!=null && currentFilteredPanes().none{it.id==store.focusId})
                store.focusId=null
            if(selecting)selectedPanes.retainAll(currentFilteredPanes().map{it.id}.toSet())
            renderTabs();updateUniversalBar()
        }
        positionCards(false)
    }
    fun closeWindows(ids:Set<String>)=confirmCloseWindows(ids)
    private fun shareUrls(urls:List<String>){
        val sites=urls.filter{it.startsWith("https://")||it.startsWith("http://")}.distinct()
        if(sites.isEmpty()){notice("No web addresses to share");return}
        val share=android.content.Intent(android.content.Intent.ACTION_SEND).apply{
            type="text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT,sites.joinToString("\n"))
        }
        startActivity(android.content.Intent.createChooser(share,"Share browser links"))
    }
    private fun selectionMenu()=universalMenu()
    private fun confirmCloseSelected()=confirmCloseWindows(selectedPanes.toSet())
    private fun confirmCloseWindows(requested:Set<String>){
        val ws=store.active()
        val selected=ws.panes.filter{it.id in requested}
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
    private fun showBookmarksForPane(id:String){
        val urls=store.bookmarksForPane(id)
        if(urls.isEmpty()){notice("No bookmarks in this browser");return}
        AlertDialog.Builder(this).setTitle("Browser bookmarks")
            .setItems(urls.toTypedArray()){_,which->
                val url=urls[which]
                val pane=store.spaces.flatMap{it.panes}.firstOrNull{it.id==id}
                val card=cards[id]
                if(card!=null)card.loadAddress(url)
                else if(pane!=null){pane.url=url;store.save()}
            }.show()
    }
    private fun showBookmarks(){
        val panes=if(filtering())currentFilteredPanes() else store.active().panes
        val focused=allPanes().firstOrNull{it.id==store.focusId}
        if(focused!=null){showBookmarksForPane(focused.id);return}
        AlertDialog.Builder(this).setTitle("Bookmarks by browser")
            .setItems(panes.mapIndexed{index,p->
                "Browser "+(index+1)+" · "+p.bookmarks.size+" saved"
            }.toTypedArray()){_,which->showBookmarksForPane(panes[which].id)}.show()
    }
    private fun workspaceMenu(){
        val options=arrayOf(
            if(store.active().panes.all{it.locked})"Unlock all six windows" else "Lock all six windows",
            "Select windows","Bookmarks",
            "Open in all panes · this workspace","Open in all panes · all workspaces",
            "Close workspace"
        )
        AlertDialog.Builder(this).setTitle("Workspace tools").setItems(options){_,idx->when(idx){
            0->{
                val value=!store.active().panes.all{it.locked}
                store.active().panes.forEach{it.locked=value;cards[it.id]?.updateLockStatus()}
                store.save()
            }
            1->enterSelection()
            2->showBookmarks()
            3->bulkNavigation(false)
            4->bulkNavigation(true)
            5->closeWorkspace(store.active())
        }}.show()
    }
    fun openControlledTestPage(){
        confirm("Open controlled test pages?","This replaces the live pages in this workspace without deleting the six browser profiles.","Open test pages"){
            navigatePanes(store.active().panes.toList(),
                "https://appassets.androidplatform.net/assets/isolation_test.html")
        }
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
    fun paneMenu(card:PaneCard){
        val choices=arrayOf(if(card.state.desktop)"Desktop site: on" else "Desktop site: off",
            "Back", "Forward", "Refresh", "Bookmark page", "Share page", "Advanced…")
        AlertDialog.Builder(this).setItems(choices){_,idx->when(idx){
            0->card.toggleDesktop()
            1->card.back()
            2->card.forward()
            3->card.refresh()
            4->{store.bookmarkForPane(card.state.id,card.state.url);notice("Page bookmarked in this browser");updateUniversalBar()}
            5->shareUrls(listOf(card.state.url))
            6->AlertDialog.Builder(this).setItems(arrayOf("Reset page (keeps cookies)")){_,_->card.reset()}.show()
        }}.show()
    }
    fun cookieEditor(card:PaneCard){CookieEditor(this,card).open()}
    fun focus(card:PaneCard){
        if(currentFilteredPanes().none{it.id==card.state.id})return
        store.focusId=if(store.focusId==card.state.id)null else card.state.id
        store.save();updateUniversalBar();positionCards(true)
    }
    private fun positionCards(animate:Boolean){
        if(!started||!::stage.isInitialized)return
        val width=stage.width;val height=stage.height
        if(width<10||height<10)return
        val layoutRun=++layoutGeneration
        val gap=dp(2);val visible=currentFilteredPanes()
        if(store.focusId!=null && visible.none{it.id==store.focusId}){
            store.focusId=null
            store.save()
        }
        val f=store.focusId
        // Allocate full six-slot geometry even with only 1–5 filtered matches.
        // Unfilled cells stay the stage background, never stretched panes.
        val rects=FilteredPanePages.bounds(width,height,gap,store.columns,visible.size,
            f?.let{id->visible.indexOfFirst{it.id==id}},dp(54),dp(80))
        filterEmpty.visibility=if(visible.isEmpty())View.VISIBLE else View.GONE
        updateUniversalBar()
        val desired=visible.mapIndexed{i,p->val r=rects[i];p.id to intArrayOf(r.x,r.y,r.width,r.height)}.toMap()
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
            lp.width=r[2].coerceAtLeast(1);lp.height=r[3].coerceAtLeast(1)
            lp.leftMargin=r[0];lp.topMargin=r[1]
            card.layoutParams=lp
            card.compact=f!=null&&id!=f
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
    @Deprecated("Android callback")
    override fun onBackPressed(){
        if(selecting){exitSelection();return}
        if(::store.isInitialized&&store.focusId!=null){store.focusId=null;persist();updateUniversalBar();positionCards(true);return}
        super.onBackPressed()
    }
    override fun onConfigurationChanged(newConfig:Configuration){super.onConfigurationChanged(newConfig);if(::stage.isInitialized)stage.post{positionCards(false)}}
    override fun onStart(){
        super.onStart()
        if(started)try{startForegroundService(Intent(this,PaneKeepAliveService::class.java))}
        catch(e:RuntimeException){notice("Background process priority unavailable: ${e.message}")}
    }
    override fun onResume(){super.onResume()}
    /** Settings is the only entrypoint for permission requests. */
    fun requestNotificationPermissionFromSettings(){
        if(Build.VERSION.SDK_INT<33){notice("Notification controls are managed by Android");return}
        if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED){
            notice("Notifications already enabled");return
        }
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),NOTIFICATION_REQUEST)
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==NOTIFICATION_REQUEST)notice(
            if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)
                "Notifications enabled" else "Notifications remain disabled"
        )
    }
    fun requestBatteryExemption(explicit:Boolean=true){
        val manager=getSystemService(PowerManager::class.java)
        if(manager.isIgnoringBatteryOptimizations(packageName)){
            if(explicit)notice("Battery optimization exemption already enabled")
            return
        }
        try{startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:$packageName")))}
        catch(_:RuntimeException){notice("Open Android App info → Battery → Unrestricted for SIXPANE")}
    }
    override fun onPause(){
        handler.removeCallbacks(saveFilter)
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
