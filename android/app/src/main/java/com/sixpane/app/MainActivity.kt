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
    private val tabLockIcons=mutableMapOf<String,CornerIcon>()
    private lateinit var universalBar: UniversalBar
    private lateinit var tessera:TesseraBar
    private lateinit var filterEmpty:TextView
    private val saveFilter=Runnable{if(::store.isInitialized)store.save()}
    private var selecting=false
    private val selectedPanes=linkedSetOf<String>()
    private var filteredPageIndex=0
    private lateinit var stage:FrameLayout
    private val cards=linkedMapOf<String,PaneCard>()
    private var allocatingCards=false
    private fun syncVisibleWebViews(visible:List<PaneState>){
        if(allocatingCards)return
        allocatingCards=true
        try {
            val am=getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val memory=ActivityManager.MemoryInfo().also{am.getMemoryInfo(it)}
            val limit=WorkspacePolicy.concurrentWebViewLimit(am.memoryClass,memory.availMem,memory.lowMemory)
            val desired=visible.map{it.id}.toSet()
            val missing=visible.count{it.id !in cards}
            val excess=(cards.size+missing-limit).coerceAtLeast(0)
            cards.keys.filter{it !in desired}.take(excess).forEach{id->
                cards.remove(id)?.let{
                    it.flushCookies()
                    stage.removeView(it);it.destroyExplicitly()
                }
            }
            visible.filter{it.id !in cards}.forEach{pane->
                if(cards.size<limit){
                    try {
                        val card=PaneCard(this,pane)
                        cards[pane.id]=card
                        stage.addView(card,FrameLayout.LayoutParams(dp(150),dp(140)))
                    }catch(e:Throwable){notice("Browser allocation failed: "+e.message)}
                }
            }
        }finally{allocatingCards=false}
    }
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
        // Only allocate the visible panes; all 42 persisted slots remain available.
        stage.post { if(!isFinishing&&!isDestroyed)positionCards(false) }
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
        screen=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(11,14,19))}
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
        screen.addView(stage,LinearLayout.LayoutParams(-1,0,1f))
        // Retain the original workspace and universal action models without
        // rendering their obsolete top bar or tab row.
        tessera=TesseraBar(this)
        screen.addView(tessera.filterChips,LinearLayout.LayoutParams(-1,dp(0)))
        screen.addView(tessera.dock,LinearLayout.LayoutParams(-1,dp(56)))
        filterEmpty=TextView(this).apply{
            text="No panes match these filters\nYour sessions are still running.\nClear filters"
            gravity=Gravity.CENTER
            setOnClickListener{clearAllFilters()}
            textSize=13f;setTextColor(Color.rgb(174,186,206));visibility=View.GONE
        }
        stage.addView(filterEmpty,FrameLayout.LayoutParams(-1,-1))
        setContentView(screen)
        applyChromeTheme()
        renderTabs()
        updateUniversalBar()
        tessera.render()
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
        if (::tessera.isInitialized)tessera.render()
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
            .setTitle("Filter colors")
            .setMultiChoiceItems(names,checked){_,index,selected->
                val name=names[index]
                if(selected)chosen.add(name) else chosen.remove(name)
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
            workspaceToolsEnabled = !filtering(),
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
    fun notice(message:String){
        if(::tessera.isInitialized){tessera.snack(message);return}
        textNotice?.cancel();textNotice=Toast.makeText(this,message,Toast.LENGTH_LONG);textNotice?.show()
    }
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
            tabStrip.addView(tab,LinearLayout.LayoutParams(0,dp(44),1f).apply{
                leftMargin=dp(1);rightMargin=dp(1)
            })
        }
        // Keep unoccupied filter tab positions blank, not new source workspaces.
        repeat((WorkspacePolicy.MAX_WORKSPACES-pages.size).coerceAtLeast(0)){
            tabStrip.addView(View(this),LinearLayout.LayoutParams(0,dp(44),1f))
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

    /** Seven visible non-scrolling workspace slots; no version-specific tab strip. */
    private fun renderTabs(){
        tabStrip.removeAllViews()
        tabLockIcons.clear()
        val light=store.lightMode
        tabStrip.setBackgroundColor(if(light)Color.rgb(225,233,242) else Color.rgb(17,23,29))
        tabStrip.setPadding(dp(2),dp(2),dp(2),dp(2))
        if(filtering()){
            clampFilteredPage();renderFilteredTabs();return
        }
        repeat(WorkspacePolicy.MAX_WORKSPACES){index->
            val ws=store.spaces.getOrNull(index)
            val active=ws?.id==store.currentId
            val next=index==store.spaces.size
            val frame=GradientDrawable().apply{
                setColor(if(active){
                    if(light)Color.rgb(197,215,237) else Color.rgb(45,64,85)
                }else if(light)Color.rgb(233,239,245) else Color.rgb(23,31,42))
                setStroke(dp(1).coerceAtLeast(1),
                    if(light)Color.rgb(167,183,200) else Color.rgb(76,89,104))
                cornerRadius=dp(6).toFloat()
            }
            val slot=FrameLayout(this).apply{background=frame}
            val label=when{
                ws?.locked==true->"${index+1}"
                active->"${index+1}\n${store.columns}×${6/store.columns}"
                next->"${index+1}\n+"
                else->(index+1).toString()
            }
            val number=TextView(this).apply{
                text=label
                gravity=if(ws?.locked==true)Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.CENTER
                textSize=if(active||next||ws?.locked==true)11f else 13f
                setTextColor(if(light)Color.rgb(26,43,63) else Color.rgb(231,239,250))
                if(ws?.locked==true)setPadding(0,dp(1),0,0)
                alpha=if(ws==null).48f else 1f
                contentDescription=when{
                    ws==null&&next->"Create workspace ${index+1}"
                    ws==null->"Unused workspace slot ${index+1}"
                    ws?.locked==true->"Workspace ${index+1} locked; tap red lock to unlock"
                    active->"Workspace ${index+1}; tap to change grid layout; hold to lock"
                    else->"Workspace ${index+1}; tap to switch; hold to lock"
                }
                isEnabled=(!selecting||active)&&(ws!=null||next)
                if(ws!=null){
                    setOnClickListener{
                        if(ws.id!=store.currentId){
                            store.currentId=ws.id;store.focusId=null;store.save()
                            renderTabs();updateUniversalBar();positionCards(true)
                        }else changeGridColumns(if(store.columns==2)3 else 2)
                    }
                    setOnLongClickListener{
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        if(!ws.locked)toggleWorkspaceLock(ws)
                        else notice("Tap red lock on workspace ${index+1} to unlock")
                        true
                    }
                }else if(next)setOnClickListener{if(!selecting)createWorkspace()}
            }
            slot.addView(number,FrameLayout.LayoutParams(-1,-1))
            if(ws?.locked==true){
                val redLock=CornerIcon(this,CornerIcon.Kind.LOCK).apply{
                    locked=true
                    contentDescription="Unlock workspace ${index+1}"
                    setOnClickListener{pressedFeedback();toggleWorkspaceLock(ws)}
                    setOnLongClickListener{
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);true
                    }
                }
                tabLockIcons[ws.id]=redLock
                slot.addView(redLock,FrameLayout.LayoutParams(dp(30),dp(24),
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
            }
            tabStrip.addView(slot,LinearLayout.LayoutParams(0,dp(44),1f).apply{
                leftMargin=dp(1);rightMargin=dp(1)
            })
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
        screen.setBackgroundColor(Color.rgb(11,14,19))
        stage.setBackgroundColor(Color.rgb(11,14,19))
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
            // Slots are persisted now; visible WebViews allocate on demand.
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
            // The replacement workspace remains in LocalStore; WebViews allocate on demand.
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
        val visibleIds=currentFilteredPanes().map{it.id}.toSet()
        renderTabs();updateUniversalBar()
        cards.forEach{(id,card)->
            card.updateSelection(selecting && id in visibleIds,selectedPanes.contains(id))
        }
        if(::tessera.isInitialized)tessera.render()
    }
    /** Exactly seven pane actions. A workspace-tab hold owns workspace-wide tools. */
    fun windowContextMenu(card:PaneCard){
        if(::tessera.isInitialized){tessera.paneMenu(card,false);return}
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
    /** Preserve the original source workspaces even when selecting virtual filter pages. */
    private fun confirmCloseWindows(requested:Set<String>){
        val crossWorkspace=filtering()
        val originalWorkspace=store.currentId
        val available=if(crossWorkspace)allPanes() else store.active().panes
        val selected=available.filter{it.id in requested}
        if(selected.isEmpty())return
        val ids=selected.map{it.id}.toSet()
        if(selected.any{it.locked}){
            selected.filter{it.locked}.forEach{cards[it.id]?.lock?.blockedFeedback()}
            notice("Unlock selected browsers before closing them");return
        }
        if(crossWorkspace && store.spaces.any{ws->ws.locked&&ws.panes.any{it.id in ids}}){
            notice("Unlock the original workspace tabs before closing filtered browsers")
            return
        }
        confirm("Close "+selected.size+" window"+(if(selected.size==1)"" else "s")+"?",
            "These sessions and their saved browser data will be retired for deletion. "+
            "Fresh isolated profiles replace them in their ORIGINAL workspaces. "+
            "Disk cleanup may require a clean restart.",
            "Close & Replace"){
            if(crossWorkspace!=filtering()||
                (!crossWorkspace && store.currentId!=originalWorkspace)){
                notice("The active view changed; close cancelled");return@confirm
            }
            val live=allPanes().filter{it.id in ids}
            if(live.size!=ids.size||live.any{it.locked}){
                live.filter{it.locked}.forEach{cards[it.id]?.lock?.blockedFeedback()}
                notice("A selected browser changed or was locked; close cancelled")
                return@confirm
            }
            if(crossWorkspace&&store.spaces.any{ws->
                ws.locked&&ws.panes.any{it.id in ids}
            }){
                notice("Source workspace became locked; close cancelled");return@confirm
            }
            val replacements=try{
                if(crossWorkspace)store.replacePanesAcrossWorkspaces(ids)
                else store.replacePanes(ids)
            }catch(e:IllegalArgumentException){
                notice("Close cancelled: "+e.message);return@confirm
            }
            replacements.forEach{(old,_)->
                cards.remove(old.id)?.let{stage.removeView(it);it.destroyExplicitly()}
            }
            exitSelection()
            // Replacement profiles remain durable; active panes allocate on demand.
            clampFilteredPage()
            renderTabs()
            positionCards(false)
            notice(replacements.size.toString()+
                " sessions retired; new profiles created in their original workspaces.")
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
        if(filtering()){notice("Clear the filter to manage original workspaces");return}
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
            "Back", "Forward", "Refresh", "Bookmark page", "Share page", "Advanced…", "Copy page report")
        AlertDialog.Builder(this).setItems(choices){_,idx->when(idx){
            0->card.toggleDesktop()
            1->card.back()
            2->card.forward()
            3->card.refresh()
            4->{store.bookmarkForPane(card.state.id,card.state.url);notice("Page bookmarked in this browser");updateUniversalBar()}
            5->shareUrls(listOf(card.state.url))
            6->AlertDialog.Builder(this).setItems(arrayOf("Reset page (keeps cookies)")){_,_->card.reset()}.show()
            7->copyPanePageReport(card)
        }}.show()
    }
    fun cookieEditor(card:PaneCard){CookieEditor(this,card).open()}
    fun copyPanePageReport(card:PaneCard){PanePageReport(this).copy(card)}
    fun focus(card:PaneCard){tesseraActivate(card.state.id,true)}
    fun tesseraActivate(id:String,expand:Boolean=false){
        if(selecting){toggleWindowSelection(id);return}
        if(store.active().panes.none{it.id==id})return
        store.activePaneId=id
        if(expand)store.focusId=if(store.focusId==id)null else id
        else if(store.focusId!=null && store.focusId!=id)store.focusId=id
        store.save();updateUniversalBar();positionCards(expand)
    }
    fun tesseraIsActive(id:String)=store.activePaneId==id
    fun tesseraActivePane():PaneState?=store.active().panes.firstOrNull{
        it.id==store.activePaneId
    }?:store.active().panes.firstOrNull()
    fun tesseraActiveCard():PaneCard?=tesseraActivePane()?.let{cards[it.id]}
    fun tesseraSelected():Set<String> =selectedPanes.toSet()
    fun tesseraSelecting():Boolean =selecting
    fun tesseraSelectAll(){
        if(selectedPanes.size==store.active().panes.size){
            selectedPanes.clear();updateSelection()
        }else selectVisiblePanes()
    }
    fun tesseraExitSelection(){exitSelection()}
    fun tesseraSetFilter(query:String,colors:Set<String>){
        store.filterQuery=query
        store.selectedFrameColors.clear();store.selectedFrameColors.addAll(colors)
        store.save();updateUniversalBar();positionCards(false)
    }
    fun tesseraClearFilters(){clearAllFilters()}
    fun tesseraOpenWorkspace(id:String){
        if(store.spaces.none{it.id==id})return
        store.currentId=id
        store.activePaneId=store.active().panes.firstOrNull()?.id
        store.focusId=null;store.save();renderTabs();positionCards(true);updateUniversalBar()
    }
    fun tesseraNewWorkspace(){createWorkspace()}
    fun tesseraCloseWorkspace(id:String){store.spaces.firstOrNull{it.id==id}?.let{closeWorkspace(it)}}
    fun tesseraLockSelected(value:Boolean){
        allPanes().filter{it.id in selectedPanes}.forEach{
            it.locked=value;cards[it.id]?.updateLockStatus()
        }
        store.save();updateSelection()
    }
    fun tesseraRecolor(ids:Set<String>,color:Int){recolorWindows(ids,color)}
    fun tesseraEnd(ids:Set<String>){closeWindows(ids)}
    fun tesseraBookmarksForActive():List<String> =tesseraActivePane()?.let{
        store.bookmarksForPane(it.id)
    }.orEmpty()
    fun tesseraToggleBookmark(){
        val pane=tesseraActivePane()?:return
        if(pane.url=="about:blank"){notice("Open a page first, then bookmark it");return}
        store.toggleBookmarkForPane(pane.id,pane.url)
        updateUniversalBar()
    }
    fun tesseraGo(raw:String):Boolean{
        val pane=tesseraActivePane()?:return false
        val resolved=BrowserPolicy.resolveAddressOrSearch(raw)?:return false
        cards[pane.id]?.loadAddress(resolved)?:run{pane.url=resolved;store.save()}
        updateUniversalBar()
        return true
    }
    fun tesseraOpenSettings(){AppSettings(this).open()}
    fun tesseraMenuAll(){tesseraActiveCard()?.let{tessera.paneMenu(it,true)}}
    fun tesseraSetGrid(value:String){
        if(value !in listOf("automatic","2x3","3x2"))return
        store.gridPreference=value;store.save();positionCards(true)
    }
    private fun positionCards(animate:Boolean){
        if(!started||!::stage.isInitialized)return
        val width=stage.width;val height=stage.height
        if(width<10||height<10)return
        val layoutRun=++layoutGeneration
        val gap=dp(4);val visible=store.active().panes.toList()
        syncVisibleWebViews(visible)
        if(store.focusId!=null && visible.none{it.id==store.focusId}){
            store.focusId=null
            store.save()
        }
        val f=store.focusId
        // Allocate full six-slot geometry even with only 1–5 filtered matches.
        // Unfilled cells stay the stage background, never stretched panes.
        val activeColumns=when(store.gridPreference){
            "2x3"->2
            "3x2"->3
            else->if(width.toFloat()>=height.toFloat()*0.95f)3 else 2
        }
        val rects=FilteredPanePages.bounds(width,height,gap,activeColumns,visible.size,
            f?.let{id->visible.indexOfFirst{it.id==id}},dp(54),dp(80))
        filterEmpty.visibility=if(filtering()&&visible.none{p->
            FramePalette.matches(p.url,store.frameColor(p.id),
                store.filterQuery,store.selectedFrameColors)
        })View.VISIBLE else View.GONE
        updateUniversalBar()
        val desired=visible.mapIndexed{i,p->val r=rects[i];p.id to intArrayOf(r.x,r.y,r.width,r.height)}.toMap()
        for((id,card) in cards){
            card.updatePaneNumber()
            val matching=!filtering()||FramePalette.matches(card.state.url,
                store.frameColor(id),store.filterQuery,store.selectedFrameColors)
            card.alpha=if(matching)1f else .16f
            val r=desired[id]
            if(r==null){
                // Hidden pages stay live and attached, without pauseTimers/stopLoading.
                // A negative layout margin is stable across parent re-layouts,
                // unlike View.setX(), which may be reset after resize/keyboard changes.
                card.animate().cancel()
                card.translationX=0f
                card.translationY=0f
                card.scaleX=1f
                card.scaleY=1f
                val offscreen=card.layoutParams as FrameLayout.LayoutParams
                if(offscreen.leftMargin!=-10000 || offscreen.topMargin!=0){
                    offscreen.leftMargin=-10000
                    offscreen.topMargin=0
                    card.layoutParams=offscreen
                }
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
        if(::tessera.isInitialized&&tessera.closeAddress())return
        if(selecting){exitSelection();return}
        val card=tesseraActiveCard()
        if(card!=null&&card.webView.canGoBack()){card.back();return}
        if(::store.isInitialized&&store.focusId!=null){
            store.focusId=null;persist();updateUniversalBar();positionCards(true);return
        }
        moveTaskToBack(true)
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
