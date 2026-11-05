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
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
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
    private lateinit var tabStrip:LinearLayout
    private lateinit var topBar:LinearLayout
    private lateinit var topFilter:EditText
    private lateinit var topAddress:EditText
    private lateinit var topStar:TextView
    private lateinit var topClear:TextView
    private lateinit var topDots:TextView
    private lateinit var filterEmpty:TextView
    private var updatingTop=false
    private val saveFilter=Runnable{if(::store.isInitialized)store.save()}
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
        setupUniversalBar()
        screen.addView(topBar,LinearLayout.LayoutParams(-1,dp(48)))
        screen.addView(stage,LinearLayout.LayoutParams(-1,0,1f))
        screen.addView(tabStrip,LinearLayout.LayoutParams(-1,dp(48)))
        filterEmpty=TextView(this).apply{
            text="No windows match";gravity=Gravity.CENTER
            textSize=13f;setTextColor(Color.rgb(174,186,206));visibility=View.GONE
        }
        stage.addView(filterEmpty,FrameLayout.LayoutParams(-1,-1))
        setContentView(screen)
        renderTabs()
        updateUniversalBar()
        if(pendingFailures>0)notice("${pendingFailures} retired profiles await deletion on a later clean start; no IDs will be reused")
    }

    /**
     * Universal control occupies a single compact row, never a second permanent toolbar.
     * Filtering changes only pane geometry; it never destroys or reloads WebViews.
     */
    private fun setupUniversalBar(){
        topBar=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(5),dp(3),dp(5),dp(3))
            setBackgroundColor(Color.rgb(19,26,37))
        }
        fun field(hintText:String):EditText=EditText(this).apply{
            hint=hintText;textSize=13f;setSingleLine(true)
            setTextColor(Color.WHITE);setHintTextColor(Color.rgb(146,159,179))
            setPadding(dp(10),0,dp(7),0)
            inputType=InputType.TYPE_CLASS_TEXT
            background=GradientDrawable().apply{
                setColor(Color.rgb(39,49,64));cornerRadius=dp(8).toFloat()
            }
        }
        topFilter=field("Filter URL or color")
        topFilter.imeOptions=EditorInfo.IME_ACTION_SEARCH
        topFilter.addTextChangedListener(object:TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){}
            override fun afterTextChanged(s:Editable?){
                if(updatingTop||!::store.isInitialized)return
                store.filterQuery=s?.toString().orEmpty()
                handler.removeCallbacks(saveFilter);handler.postDelayed(saveFilter,250L)
                if(selecting){
                    val allowed=currentFilteredPanes().map{it.id}.toSet()
                    selectedPanes.retainAll(allowed);updateSelection()
                }
                updateUniversalBar();positionCards(false)
            }
        })
        topAddress=field("Search Google or type URL")
        topAddress.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        topAddress.imeOptions=EditorInfo.IME_ACTION_GO
        topAddress.setOnEditorActionListener{_,actionId,_->
            if(actionId==EditorInfo.IME_ACTION_GO){submitFocusedAddress();true}else false
        }
        topStar=smallButton("☆",40){
            val id=store.focusId?:return@smallButton
            val pane=store.active().panes.firstOrNull{it.id==id}?:return@smallButton
            val added=store.toggleBookmarkForPane(id,pane.url)
            when(added){
                true->notice("Saved to this browser's bookmarks")
                false->notice("Removed from this browser's bookmarks")
                null->notice("Open a webpage before bookmarking")
            }
            updateUniversalBar()
        }
        topStar.setOnLongClickListener{
            store.focusId?.let{showBookmarksForPane(it)};true
        }
        topClear=smallButton("×",34){clearAllFilters()}
        topDots=smallButton("⋮",42){universalMenu()}
        topDots.contentDescription="Window selection and batch actions"
        topBar.addView(topFilter,LinearLayout.LayoutParams(0,-1,1f))
        topBar.addView(topAddress,LinearLayout.LayoutParams(0,-1,1f))
        topBar.addView(topStar,LinearLayout.LayoutParams(dp(40),-1))
        topBar.addView(topClear,LinearLayout.LayoutParams(dp(34),-1))
        topBar.addView(topDots,LinearLayout.LayoutParams(dp(42),-1))
        updatingTop=true;topFilter.setText(store.filterQuery);updatingTop=false
    }
    private fun updateUniversalBar(){
        if(!::topBar.isInitialized||!::store.isInitialized)return
        val focused=store.active().panes.firstOrNull{it.id==store.focusId}
        val inFocus=focused!=null
        topFilter.visibility=if(inFocus)View.GONE else View.VISIBLE
        topAddress.visibility=if(inFocus)View.VISIBLE else View.GONE
        topStar.visibility=if(inFocus)View.VISIBLE else View.GONE
        topClear.visibility=if(!inFocus &&
            (store.filterQuery.isNotBlank()||store.selectedFrameColors.isNotEmpty()))View.VISIBLE else View.GONE
        if(!inFocus){
            if(!topFilter.hasFocus()&&topFilter.text.toString()!=store.filterQuery){
                updatingTop=true;topFilter.setText(store.filterQuery);updatingTop=false
            }
            val chosen=store.selectedFrameColors
            topFilter.hint=if(chosen.isEmpty())"Filter URL or color"
                else "Colors: "+FramePalette.names.filter{it in chosen}.joinToString(", ")
        }else{
            if(!topAddress.hasFocus()&&topAddress.text.toString()!=focused.url)
                topAddress.setText(focused.url)
            val saved=store.bookmarkedInPane(focused.id,focused.url)
            topStar.text=if(saved)"★" else "☆"
            topStar.contentDescription=if(saved)"Remove bookmark from this browser" else "Bookmark this page in this browser"
        }
        topDots.contentDescription=if(selecting)
            "Window batch actions: "+selectedPanes.size+" selected" else "Window selection and batch actions"
    }
    private fun submitFocusedAddress(){
        val id=store.focusId?:return
        val raw=topAddress.text.toString()
        val card=cards[id]
        val success=if(card!=null)card.submitAddress(raw) else{
            val url=BrowserPolicy.resolveAddressOrSearch(raw)
            if(url==null)false else {
                store.active().panes.firstOrNull{it.id==id}?.url=url;store.save();true
            }
        }
        if(!success){topAddress.error="Enter a URL or search";return}
        topAddress.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(topAddress.windowToken,0)
        updateUniversalBar()
    }
    fun onPaneUrlChanged(id:String){
        if(!::topBar.isInitialized)return
        if(store.focusId==id)updateUniversalBar()
        if(store.filterQuery.isNotBlank())positionCards(false)
    }
    private fun clearAllFilters(){
        store.filterQuery=""
        store.selectedFrameColors.clear();store.save()
        updatingTop=true;topFilter.setText("");updatingTop=false
        if(selecting){selectedPanes.clear();updateSelection()}
        updateUniversalBar();positionCards(true)
    }
    private fun currentFilteredPanes():List<PaneState>{
        val matches=store.active().panes.filter{p->
            FramePalette.matches(p.url,store.frameColor(p.id),
                store.filterQuery,store.selectedFrameColors)
        }
        val focusId=store.focusId
        // Keep an actively focused pane visible even if its URL changes mid-edit.
        return if(focusId!=null)store.active().panes.filter{it.id==focusId||it in matches}
            else matches
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
                store.filterQuery="";store.save()
                updatingTop=true;topFilter.setText("");updatingTop=false
                if(selecting){selectedPanes.clear();updateSelection()}
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
    /** Permanent top-right menu in both grid and focus modes. */
    private fun universalMenu(){
        val popup=PopupMenu(this,topDots)
        val count=selectedPanes.size
        val panes=store.active().panes.filter{it.id in selectedPanes}
        val anyLocked=panes.any{it.locked}
        val anyUnlocked=panes.any{!it.locked}
        popup.menu.add(if(selecting)"Exit selection" else "Select multiple")
        popup.menu.add("Select all visible")
        popup.menu.add("Deselect all").isEnabled=count>0
        popup.menu.add("Lock selected").isEnabled=anyUnlocked
        popup.menu.add("Unlock selected").isEnabled=anyLocked
        popup.menu.add("Close selected").isEnabled=count>0&&!anyLocked
        popup.menu.add("Bookmark selected").isEnabled=count>0
        popup.menu.add("Share selected").isEnabled=count>0
        popup.menu.add("Set selected frame color").isEnabled=count>0
        popup.menu.add("Filter colors ("+store.selectedFrameColors.size+"/6)")
        popup.menu.add("Clear filters").isEnabled=store.filterQuery.isNotBlank()||
            store.selectedFrameColors.isNotEmpty()
        if(store.focusId!=null)popup.menu.add("Bookmarks for this browser")
        popup.menu.add("Workspace tools")
        popup.setOnMenuItemClickListener{item->
            when(item.title.toString()){
                "Select multiple"->enterSelection()
                "Exit selection"->exitSelection()
                "Select all visible"->selectVisiblePanes()
                "Deselect all"->{selectedPanes.clear();updateSelection()}
                "Lock selected"->{panes.forEach{it.locked=true;cards[it.id]?.updateLockStatus()}
                    store.save();updateSelection()}
                "Unlock selected"->{panes.forEach{it.locked=false;cards[it.id]?.updateLockStatus()}
                    store.save();updateSelection()}
                "Close selected"->confirmCloseSelected()
                "Bookmark selected"->{panes.forEach{store.bookmarkForPane(it.id,it.url)}
                    notice("Saved bookmarks in "+panes.size+" browsers");updateUniversalBar()}
                "Share selected"->shareUrls(panes.map{it.url})
                "Set selected frame color"->chooseColorForSelected()
                "Clear filters"->clearAllFilters()
                "Bookmarks for this browser"->store.focusId?.let{showBookmarksForPane(it)}
                "Workspace tools"->workspaceMenu()
                else->if(item.title.toString().startsWith("Filter colors"))pickFilterColors()
            };true
        }
        popup.show()
    }
    private fun chooseColorForSelected(){
        val ids=selectedPanes.toSet()
        if(ids.isEmpty())return
        AlertDialog.Builder(this).setTitle("Frame color · "+ids.size+" windows")
            .setItems(FramePalette.names.toTypedArray()){_,which->
                recolorWindows(ids,FramePalette.colors[which])
            }.show()
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
        repeat(WorkspacePolicy.MAX_WORKSPACES){index->
            val ws=store.spaces.getOrNull(index)
            val active=ws?.id==store.currentId
            val next=index==store.spaces.size
            val label=if(active)"${index+1}\n${store.columns}×${6/store.columns}" else if(next)"${index+1}\n+" else "${index+1}"
            val tab=smallButton(label,0){
                if(ws==null){if(next)createWorkspace();return@smallButton}
                if(active){
                    store.columns=if(store.columns==2)3 else 2;store.focusId=null;store.save();renderTabs();positionCards(true)
                }else{
                    store.currentId=ws.id;store.focusId=null;store.save();renderTabs();positionCards(true)
                }
            }.apply{
                textSize=if(active||next)12f else 14f
                isEnabled=(!selecting||active)&&(ws!=null||next)
                alpha=if(ws==null).45f else 1f
                setTextColor(if(active)Color.rgb(184,210,237) else Color.rgb(232,237,244))
                background=GradientDrawable().apply{setColor(if(active)Color.rgb(45,64,85) else Color.TRANSPARENT);cornerRadius=dp(8).toFloat()}
                contentDescription=if(ws==null)"Create workspace ${index+1}" else "Workspace ${index+1}${if(active)"; tap to swap layout" else ""}; hold for workspace tools"
                if(ws!=null)setOnLongClickListener{
                    if(selecting)selectionMenu()
                    else{store.currentId=ws.id;store.focusId=null;store.save();renderTabs();positionCards(true);workspaceMenu()}
                    true
                }
            }
            tabStrip.addView(tab,LinearLayout.LayoutParams(0,dp(44),1f))
        }
    }
    private fun createWorkspace(){
        if(!WorkspacePolicy.canCreate(store.spaces.size))return
        try{
            val new=store.create();renderTabs();positionCards(false)
            new.panes.forEachIndexed{i,p->handler.postDelayed({
                if(!isFinishing&&!isDestroyed&&store.spaces.any{w->w.id==new.id&&w.panes.any{it.id==p.id}}){
                    try{val card=PaneCard(this,p);cards[p.id]=card;stage.addView(card,FrameLayout.LayoutParams(dp(150),dp(140)));positionCards(false)}
                    catch(e:Throwable){notice("Browser allocation failed: ${e.message}")}
                }
            },i*130L)}
        }catch(e:Exception){notice("Cannot create workspace: ${e.message}")}
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
        renderTabs();updateUniversalBar()
        cards.forEach{(id,card)->
            card.updateSelection(selecting && store.active().panes.any{it.id==id},selectedPanes.contains(id))
        }
    }
    /** Exactly seven pane actions. A workspace-tab hold owns workspace-wide tools. */
    fun windowContextMenu(card:PaneCard){
        if(selecting&&!selectedPanes.contains(card.state.id)){selectedPanes.add(card.state.id);updateSelection()}
        val targets=if(selecting&&selectedPanes.isNotEmpty())store.active().panes.filter{it.id in selectedPanes} else listOf(card.state)
        WindowActions(this,card,targets).open()
    }
    fun selectingWindows():Boolean=selecting
    fun toggleSelectionFromMenu(card:PaneCard){if(selecting)exitSelection() else enterSelection(card.state.id)}
    fun updateWindowLocks(){cards.values.forEach{it.updateLockStatus()};persist();updateSelection()}
    fun windowCard(id:String):PaneCard?=cards[id]
    fun recolorWindows(ids:Set<String>,color:Int){store.setFrameColor(ids,color);ids.forEach{cards[it]?.updateFrameColor()};positionCards(false)}
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
        val panes=store.active().panes
        val focused=panes.firstOrNull{it.id==store.focusId}
        if(focused!=null){showBookmarksForPane(focused.id);return}
        AlertDialog.Builder(this).setTitle("Bookmarks by browser")
            .setItems(panes.mapIndexed{index,p->
                "Browser "+(index+1)+" · "+p.bookmarks.size+" saved"
            }.toTypedArray()){_,which->showBookmarksForPane(panes[which].id)}.show()
    }
    private fun workspaceMenu(){
        val options=arrayOf(if(store.columns==2)"Switch to 3 × 2 grid" else "Switch to 2 × 3 grid",
            if(store.active().panes.all{it.locked})"Unlock all six" else "Lock all six",
            "Controlled test page", "Pending deletion status", "Select windows", "Bookmarks",
            "Open in all panes · this workspace", "Open in all panes · all workspaces",
            "Session audit", "Desktop UA: ${if(store.desktopUserAgent)"on" else "off"}",
            "Battery optimization exemption", "Lock / unlock workspace", "Close workspace")
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
            11->{store.active().locked=!store.active().locked;persist();renderTabs()}
            12->closeWorkspace(store.active())
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
            4->{store.bookmarkForPane(card.state.id,card.state.url);notice("Page bookmarked in this browser");updateUniversalBar()}
            5->shareUrls(listOf(card.state.url))
            6->AlertDialog.Builder(this).setItems(arrayOf("Reset page (keeps cookies)")){_,_->card.reset()}.show()
        }}.show()
    }
    fun cookieEditor(card:PaneCard){CookieEditor(this,card).open()}
    fun focus(card:PaneCard){
        store.focusId=if(store.focusId==card.state.id)null else card.state.id
        store.save();updateUniversalBar();positionCards(true)
    }
    private fun positionCards(animate:Boolean){
        if(!started||!::stage.isInitialized)return
        val width=stage.width;val height=stage.height
        if(width<10||height<10)return
        val layoutRun=++layoutGeneration
        val gap=dp(2);val visible=currentFilteredPanes()
        val f=store.focusId?.takeIf{id->visible.any{it.id==id}}
        val rects=PaneLayout.visibleBounds(width,height,gap,store.columns,visible.size,
            f?.let{id->visible.indexOfFirst{it.id==id}},dp(72),dp(80))
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
