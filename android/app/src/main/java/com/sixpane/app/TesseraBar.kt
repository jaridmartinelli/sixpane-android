package com.sixpane.app

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import java.net.URI

/** The visible Tessera-inspired navigation shell; it never creates or rebinds browser profiles. */
internal class TesseraBar(private val host:MainActivity) {
    private val surface=0xff141920.toInt()
    private val surface2=0xff1c222b.toInt()
    private val surface3=0xff252c37.toInt()
    private val stroke=0xff2b3340.toInt()
    private val text=0xffeef2f7.toInt()
    private val secondary=0xb3ffffff.toInt()
    private val accent=0xff7ea6ff.toInt()
    private fun dp(n:Int)=host.dp(n)
    private fun round(fill:Int,radius:Int=12,line:Int=0,edge:Int=stroke)=GradientDrawable().apply{
        setColor(fill);cornerRadius=dp(radius).toFloat()
        if(line>0)setStroke(dp(line).coerceAtLeast(1),edge)
    }
    private fun txt(value:String,sp:Float=15f)=TextView(host).apply{
        text=value;textSize=sp;setTextColor(this@TesseraBar.text);gravity=Gravity.CENTER_VERTICAL
    }
    private fun button(value:String,action:()->Unit)=txt(value,19f).apply{
        gravity=Gravity.CENTER;minWidth=dp(44);minHeight=dp(44)
        background=RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x50ffffff),
            ColorDrawable(Color.TRANSPARENT),null)
        setOnClickListener{action()}
    }
    val filterChips=HorizontalScrollView(host).apply{
        isHorizontalScrollBarEnabled=false;setBackgroundColor(0xff0b0e13.toInt())
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
        visibility=View.GONE
    }
    private val filterContents=LinearLayout(host).apply{gravity=Gravity.CENTER_VERTICAL}
    val dock=LinearLayout(host).apply{
        orientation=LinearLayout.VERTICAL;setBackgroundColor(surface)
    }
    private val progress=View(host).apply{setBackgroundColor(Color.WHITE)}
    private val items=LinearLayout(host).apply{
        gravity=Gravity.CENTER_VERTICAL
        setPadding(dp(8),dp(6),dp(4),dp(6))
    }
    private val chipNumber=txt("1",14f).apply{
        gravity=Gravity.CENTER
        setTypeface(null,Typeface.BOLD)
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val chipExpand=txt("↗",11f).apply{
        gravity=Gravity.CENTER
        includeFontPadding=false
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,11f)
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val paneChip=FrameLayout(host).apply{
        isClickable=true;isFocusable=true
        minimumWidth=dp(44);minimumHeight=dp(44)
        addView(chipNumber,FrameLayout.LayoutParams(-1,-1))
        addView(chipExpand,FrameLayout.LayoutParams(dp(13),dp(13),
            Gravity.END or Gravity.BOTTOM).apply{
            rightMargin=dp(3);bottomMargin=dp(3)
        })
        setOnClickListener{
            host.tesseraActivePane()?.let{host.tesseraActivate(it.id,true)}
        }
        setOnLongClickListener{
            host.tesseraMenuActivePane(false)
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);true
        }
    }
    private var chipFill:Int?=null
    private val lead=txt("⌕",16f)
    private val address=EditText(host).apply{
        textSize=15f;setTextColor(this@TesseraBar.text)
        setHintTextColor(secondary);hint="Search or enter address";setSingleLine(true)
        inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        imeOptions=EditorInfo.IME_ACTION_GO
        setPadding(0,0,0,0);background=null;setSelectAllOnFocus(true)
        setOnFocusChangeListener{_,focus->
            if(focus){
                editingPaneId=host.tesseraActivePane()?.id
                setText(host.tesseraActivePane()?.url?.takeUnless{it=="about:blank"}.orEmpty())
                selectAll();showSuggestions()
            }else{
                editingPaneId=null
                suggestions?.dismiss()
            }
        }
        setOnEditorActionListener{_,action,event->
            val hardware=event?.keyCode==KeyEvent.KEYCODE_ENTER && event.action==KeyEvent.ACTION_DOWN
            if(action==EditorInfo.IME_ACTION_GO||hardware){
                submit(text.toString());true
            }else false
        }
    }
    private val star=button("☆"){
        val card=host.tesseraActiveCard()
        if(card?.loading==true){
            card.webView.stopLoading();render()
        }else{
            val pane=host.tesseraActivePane()
            if(pane==null||pane.url=="about:blank"){
                host.notice("Open a page first, then bookmark it")
            }else{
                val bookmarked=host.store.toggleBookmarkForPane(pane.id,pane.url)
                val url=pane.url
                render()
                val site=runCatching{URI(url).host}.getOrNull()?:"site"
                if(bookmarked!=null)snack(if(bookmarked)"Bookmarked "+site else "Bookmark removed"){
                    // Undo only the matching action, never an intervening change
                    // made by the user while the snack was visible.
                    if(host.store.bookmarkedInPane(pane.id,url)==bookmarked){
                        host.store.toggleBookmarkForPane(pane.id,url)
                        render()
                    }
                }
            }
        }
    }
    private val addressPill=LinearLayout(host).apply{
        gravity=Gravity.CENTER_VERTICAL
        setPadding(dp(8),0,0,0)
        background=round(surface2,22,1)
        addView(lead,LinearLayout.LayoutParams(dp(20),dp(44)))
        addView(address,LinearLayout.LayoutParams(0,dp(44),1f))
        addView(star,LinearLayout.LayoutParams(dp(40),dp(44)))
    }
    private val forward=button("→"){
        val view=host.tesseraActiveCard()?.webView
        if(view!=null&&view.canGoForward())view.goForward()
        else host.notice("Nothing to go forward to")
    }.apply{contentDescription="Forward in the active pane"}
    private val workspaceNumber=txt("1",12.5f).apply{
        gravity=Gravity.CENTER
        includeFontPadding=false
        setTypeface(null,Typeface.BOLD)
        background=round(Color.TRANSPARENT,3,2,secondary)
    }
    private val workspace=FrameLayout(host).apply{
        minimumWidth=dp(44);minimumHeight=dp(44)
        isClickable=true;isFocusable=true
        background=RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x50ffffff),
            ColorDrawable(Color.TRANSPARENT),null)
        addView(workspaceNumber,FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER))
        contentDescription="Workspaces; tap to browse, hold for workspace menu"
        setOnClickListener{workspaceScreen()}
        setOnLongClickListener{
            workspaceMenu(host.store.active())
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);true
        }
    }
    private val more=button("⋮"){host.tesseraMenuAll()}.apply{
        contentDescription="Pane menu and all panes actions"
    }
    private var selectingRendered:Boolean?=null
    private var lastSelectionKey:String?=null
    private var suggestions:PopupWindow?=null
    private var editingPaneId:String?=null
    private var lastSnack:PopupWindow?=null
    init {
        filterChips.addView(filterContents)
        dock.addView(progress,LinearLayout.LayoutParams(-1,dp(2)))
        dock.addView(items,LinearLayout.LayoutParams(-1,dp(54)))
        regular()
    }
    private fun regular(){
        items.removeAllViews()
        items.addView(paneChip,LinearLayout.LayoutParams(dp(44),dp(44)))
        items.addView(addressPill,LinearLayout.LayoutParams(0,dp(44),1f).apply{leftMargin=dp(4)})
        items.addView(forward,LinearLayout.LayoutParams(dp(44),dp(44)))
        items.addView(workspace,LinearLayout.LayoutParams(dp(44),dp(44)))
        items.addView(more,LinearLayout.LayoutParams(dp(44),dp(44)))
    }
    private fun selection(){
        items.removeAllViews()
        val scroll=HorizontalScrollView(host).apply{isHorizontalScrollBarEnabled=false}
        val controls=LinearLayout(host).apply{gravity=Gravity.CENTER_VERTICAL}
        fun add(title:String,w:Int,description:String,action:()->Unit){
            controls.addView(button(title,action).apply{contentDescription=description},
                LinearLayout.LayoutParams(dp(w),dp(44)))
        }
        add("×",44,"Exit selection"){host.tesseraExitSelection()}
        val selectionCount=host.tesseraSelected().size
        val workspaceCount=host.store.spaces.count{ws->
            ws.panes.any{it.id in host.tesseraSelected()}
        }
        val status=LinearLayout(host).apply{orientation=LinearLayout.VERTICAL}
        status.addView(txt(selectionCount.toString()+" selected",15f).apply{
            setTypeface(null,Typeface.BOLD)
        })
        status.addView(txt(if(workspaceCount<=1)"In this workspace" else
            "Across "+workspaceCount+" workspaces",10f).apply{
            setTextColor(secondary)
        })
        // Fit primary selection controls at common 360dp phone widths;
        // retain horizontal scrolling only for narrower accessibility layouts.
        val narrow=host.resources.displayMetrics.widthPixels<dp(400)
        controls.addView(status,LinearLayout.LayoutParams(dp(if(narrow)76 else 100),dp(44)))
        val currentIds=host.tesseraVisibleSelectionIds()
        val allVisible=currentIds.isNotEmpty() &&
            currentIds.all{it in host.tesseraSelected()}
        add(if(allVisible)"None" else "All",if(narrow)44 else 56,
            if(allVisible)"Deselect all visible panes" else "Select all visible panes"){
            host.tesseraSelectAll()
        }
        add("⌑",44,"Lock selected panes"){host.tesseraLockSelected(true)}
        add("♢",44,"Unlock selected panes"){host.tesseraLockSelected(false)}
        add("●",44,"Change colour of selected panes"){
            colorDialog(host.tesseraSelected())
        }
        add("⋮",44,"More actions for selected panes"){selectionScreen()}
        scroll.addView(controls)
        items.addView(scroll,LinearLayout.LayoutParams(-1,dp(44)))
    }
    fun render(){
        val select=host.tesseraSelecting()
        val selectionKey=if(select)WorkspacePolicy.selectionBarKey(
            host.store.currentId,host.tesseraSelected(),host.tesseraVisibleSelectionIds()) else null
        if(select!=selectingRendered){
            selectingRendered=select
            if(select)selection() else regular()
            lastSelectionKey=selectionKey
        }
        if(select){
            if(lastSelectionKey!=selectionKey){
                lastSelectionKey=selectionKey
                selection()
            }
            progress.visibility=View.INVISIBLE
        }else {
            val pane=host.tesseraActivePane()
            val card=host.tesseraActiveCard()
            val number=pane?.let{host.paneIndex(it.id)}?:0
            chipNumber.text=number.toString()
            chipExpand.text=if(pane!=null&&host.store.focusId==pane.id)"↙" else "↗"
            paneChip.contentDescription="Pane $number; tap to expand or shrink; hold for pane menu"
            val color=pane?.let{host.store.frameColor(it.id)}?:surface3
            val fill=if(color==0xff51606e.toInt())surface3 else color
            val ink=FramePalette.textOnTag(fill)
            chipNumber.setTextColor(ink);chipExpand.setTextColor(ink)
            // Replacing a pressed RippleDrawable on every render cancels feedback.
            // Recreate it only when its colour actually changes.
            if(chipFill!=fill){
                paneChip.background=RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x50ffffff),
                    round(fill,12,1,Color.WHITE),round(Color.WHITE,12)
                )
                chipFill=fill
            }
            val currentWorkspaceNumber=host.store.spaces.indexOfFirst{
                it.id==host.store.currentId
            }+1
            workspaceNumber.text=currentWorkspaceNumber.toString()
            workspace.contentDescription="Workspaces; current "+currentWorkspaceNumber+
                "; tap to browse, hold for workspace menu"
            lead.text=if(pane?.locked==true)"♢" else "⌕"
            lead.setTextColor(if(pane?.locked==true)0xffff4d57.toInt() else secondary)
            if(!address.hasFocus()){
                val value=pane?.url.orEmpty().takeUnless{it=="about:blank"}.orEmpty()
                val visible=runCatching{URI(value).host?:value}.getOrDefault(value)
                if(address.text.toString()!=visible)address.setText(visible)
            }
            progress.visibility=if(card?.loading==true)View.VISIBLE else View.INVISIBLE
            star.text=if(card?.loading==true)"×" else
                if(pane!=null&&host.store.bookmarkedInPane(pane.id,pane.url))"★" else "☆"
            forward.alpha=if(card?.webView?.canGoForward()==true)1f else .3f
        }
        updateChips()
    }
    private fun submit(raw:String){
        // Never send text entered for Pane A to a subsequently selected Pane B.
        if(address.hasFocus()&&editingPaneId!=host.tesseraActivePane()?.id){
            closeAddress()
            host.notice("Active pane changed; reopen the address")
            return
        }
        if(!host.tesseraGo(raw)){address.error="Enter an address or search";return}
        closeAddress()
        render()
    }
    fun focusAddress(id:String){
        host.tesseraActivate(id)
        address.requestFocus()
        (host.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(address,InputMethodManager.SHOW_IMPLICIT)
    }
    fun addressFocused():Boolean=address.hasFocus()
    fun closeAddress():Boolean{
        if(!address.hasFocus())return false
        address.clearFocus()
        suggestions?.dismiss()
        (host.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(address.windowToken,0)
        render();return true
    }
    private fun showSuggestions(){
        suggestions?.dismiss()
        val contents=LinearLayout(host).apply{
            orientation=LinearLayout.VERTICAL;setPadding(dp(8),dp(8),dp(8),dp(8))
            background=round(surface,18,1)
        }
        // Snapshot the source pane: a stale popup must never remove a bookmark
        // from another browser after the active pane changes.
        val sourcePaneId=editingPaneId?:host.tesseraActivePane()?.id
        val clipboard=host.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if(clipboard.primaryClipDescription!=null){
            contents.addView(button("Paste & go"){
                if(sourcePaneId!=host.tesseraActivePane()?.id){
                    closeAddress()
                    host.notice("Active pane changed; reopen the address")
                }else{
                    // The clipboard contents are read only after the user taps.
                    val text=clipboard.primaryClip?.getItemAt(0)?.coerceToText(host)?.toString().orEmpty()
                    if(text.isNotBlank())submit(text)
                }
            }.apply{contentDescription="Paste clipboard into the active pane"},LinearLayout.LayoutParams(-1,dp(44)))
        }
        val links=sourcePaneId?.let{host.store.bookmarksForPane(it)}.orEmpty()
        links.take(8).forEach{url->
            val domain=runCatching{URI(url).host}.getOrNull()?:"Saved website"
            val active=host.tesseraActiveCard()
            val knownTitle=active?.takeIf{it.state.id==sourcePaneId&&it.state.url==url}
                ?.webView?.title?.trim()?.takeIf{it.isNotBlank()}
            val pathLabel=runCatching{URI(url).rawPath.orEmpty().trim('/').substringAfterLast('/')}
                .getOrNull()?.takeIf{it.isNotBlank()}
            val title=knownTitle?:pathLabel?:domain
            val row=LinearLayout(host).apply{
                gravity=Gravity.CENTER_VERTICAL
                isClickable=true;isFocusable=true
                contentDescription="Open bookmark "+domain+" in this pane"
                setOnClickListener{
                    if(sourcePaneId!=host.tesseraActivePane()?.id){
                        closeAddress()
                        host.notice("Active pane changed; reopen the address")
                    }else submit(url)
                }
            }
            val labels=LinearLayout(host).apply{
                orientation=LinearLayout.VERTICAL
                gravity=Gravity.CENTER_VERTICAL
                importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            labels.addView(txt(title.take(55),14f).apply{
                maxLines=1
                ellipsize=android.text.TextUtils.TruncateAt.END
            })
            labels.addView(txt(domain,12f).apply{
                setTextColor(secondary);maxLines=1
                ellipsize=android.text.TextUtils.TruncateAt.END
            })
            row.addView(labels,LinearLayout.LayoutParams(0,dp(52),1f))
            row.addView(button("×"){
                // Do not toggle (which would re-add a stale bookmark).
                if(sourcePaneId!=null&&host.store.bookmarkedInPane(sourcePaneId,url)){
                    host.store.toggleBookmarkForPane(sourcePaneId,url)
                }
                suggestions?.dismiss()
                if(address.hasFocus())showSuggestions()
            }.apply{
                contentDescription="Remove bookmark "+domain+" from this pane"
            },LinearLayout.LayoutParams(dp(44),dp(44)))
            contents.addView(row)
        }
        // This is a non-modal suggestion panel. Keeping it non-focusable lets
        // the dock EditText retain the IME and receive Enter/Go normally.
        val pop=PopupWindow(contents,dp(310),-2,false).apply{
            isOutsideTouchable=true;elevation=dp(6).toFloat()
            setBackgroundDrawable(ColorDrawable(surface))
        }
        suggestions=pop
        dock.post{
            if(address.hasFocus()){
                contents.measure(
                    View.MeasureSpec.makeMeasureSpec(dp(310),View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dp(500),View.MeasureSpec.AT_MOST))
                // Anchor to the dock rather than a one-time screen Y position.
                // The dock moves when the IME opens; an absolute popup stayed
                // over Gboard instead of above the address field.
                pop.showAsDropDown(dock,dp(6),
                    -dock.height-contents.measuredHeight,Gravity.START)
            }
        }
    }
    private var lastChipsFingerprint:String?=null
    private fun updateChips(){
        val colors=host.store.selectedFrameColors
        val query=host.store.filterQuery
        val count=host.store.spaces.flatMap{it.panes}.count{p->
            FramePalette.matches(p.url,host.store.frameColor(p.id),query,colors)
        }
        // Page-load callbacks may refresh the command dock many times a second.
        // Never tear down and recreate focused/accessible filter controls when
        // neither the filter nor its result count has actually changed.
        val fingerprint=query+"|"+colors.sorted().joinToString(",")+"|"+count
        if(lastChipsFingerprint==fingerprint)return
        lastChipsFingerprint=fingerprint
        filterContents.removeAllViews()
        val desiredHeight=if(colors.isEmpty()&&query.isBlank())0 else dp(48)
        val params=filterChips.layoutParams
        if(params!=null && params.height!=desiredHeight){
            // Reassign the parent's LayoutParams so LinearLayout measures the
            // chips instead of retaining the initial zero-height allocation.
            params.height=desiredHeight
            filterChips.layoutParams=params
        }
        filterChips.visibility=if(desiredHeight==0)View.GONE else View.VISIBLE
        if(desiredHeight==0)return
        filterContents.addView(button("⌕"){filterScreen()}.apply{
            contentDescription="Open filters"
        },LinearLayout.LayoutParams(dp(44),dp(44)))
        // The visible chip is 34dp tall, but the tap target remains 44dp.
        // The remove glyph owns a 34dp-wide area inside that face.
        fun addFilterChip(name:String,dotColor:Int?,description:String,
                          width:Int,remove:()->Unit){
            val tap=FrameLayout(host).apply{
                minimumHeight=dp(44)
                isClickable=true;isFocusable=true
                contentDescription=description
                background=RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x50ffffff),
                    ColorDrawable(Color.TRANSPARENT),null)
                setOnClickListener{remove()}
            }
            val face=LinearLayout(host).apply{
                gravity=Gravity.CENTER_VERTICAL
                background=round(surface2,16,1)
                setPadding(dp(6),0,0,0)
                importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            if(dotColor!=null){
                face.addView(View(host).apply{
                    background=GradientDrawable().apply{
                        shape=GradientDrawable.OVAL
                        setColor(dotColor)
                    }
                },LinearLayout.LayoutParams(dp(10),dp(10)).apply{
                    rightMargin=dp(5)
                })
            }
            face.addView(txt(name,12f).apply{
                gravity=Gravity.CENTER_VERTICAL
                maxLines=1
            },LinearLayout.LayoutParams(0,dp(34),1f))
            face.addView(txt("×",16f).apply{
                gravity=Gravity.CENTER
                setTextColor(secondary)
            },LinearLayout.LayoutParams(dp(34),dp(34)))
            tap.addView(face,FrameLayout.LayoutParams(-1,dp(34),Gravity.CENTER))
            filterContents.addView(tap,LinearLayout.LayoutParams(dp(width),dp(44)))
        }
        colors.forEach{name->
            val index=FramePalette.tesseraNames.indexOf(name)
            val legacy=FramePalette.names.indexOf(name)
            val dotColor=if(index>=0)FramePalette.tesseraColors[index]
                else if(legacy>=0)FramePalette.colors[legacy] else surface3
            addFilterChip(name,dotColor,"Remove "+name+" colour filter",112){
                host.tesseraSetFilter(query,colors-name)
            }
        }
        if(query.isNotBlank()){
            addFilterChip("URL has \""+query.take(11)+"\"",null,
                "Remove URL filter",172){
                host.tesseraSetFilter("",colors)
            }
        }
        filterContents.addView(txt(count.toString()+" matches",12f).apply{
            gravity=Gravity.CENTER
        },LinearLayout.LayoutParams(dp(85),dp(44)))
        filterContents.addView(button("Clear all"){host.tesseraClearFilters()}.apply{
            contentDescription="Clear all filters"
        },LinearLayout.LayoutParams(dp(88),dp(44)))
    }
    private fun page(title:String,subtitle:String="",closeGlyph:String="‹",
                     footerHeight:Int=64,
                     content:(LinearLayout,LinearLayout,Dialog)->Unit){
        closeAddress()
        val dialog=Dialog(host,android.R.style.Theme_Material_NoActionBar)
        val outer=LinearLayout(host).apply{
            orientation=LinearLayout.VERTICAL;setBackgroundColor(0xff0b0e13.toInt())
        }
        val header=LinearLayout(host).apply{gravity=Gravity.CENTER_VERTICAL}
        val closeControl=button(closeGlyph){dialog.dismiss()}.apply{
            contentDescription=if(closeGlyph=="×")"Close $title" else "Back from $title"
        }
        // Close icons for top-level screens belong on the right; nested pages
        // retain the back affordance at the left, as in the reference layouts.
        if(closeGlyph!="×"){
            header.addView(closeControl,LinearLayout.LayoutParams(dp(48),dp(48)))
        }
        val heading=LinearLayout(host).apply{
            orientation=LinearLayout.VERTICAL
            if(closeGlyph=="×")setPadding(dp(12),0,0,0)
        }
        heading.addView(txt(title,21f).apply{setTypeface(null,Typeface.BOLD)})
        if(subtitle.isNotBlank())heading.addView(txt(subtitle,13f).apply{setTextColor(secondary)})
        header.addView(heading,LinearLayout.LayoutParams(0,dp(60),1f))
        if(closeGlyph=="×"){
            header.addView(closeControl,LinearLayout.LayoutParams(dp(48),dp(48)))
        }
        outer.addView(header,LinearLayout.LayoutParams(-1,dp(60)))
        val scroll=ScrollView(host).apply{isFillViewport=true}
        val body=LinearLayout(host).apply{
            orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(4),dp(12),dp(8))
        }
        scroll.addView(body)
        outer.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val footer=LinearLayout(host).apply{
            gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(6),dp(8),dp(6))
        }
        outer.addView(footer,LinearLayout.LayoutParams(-1,dp(footerHeight)))
        content(body,footer,dialog)
        dialog.setContentView(outer)
        // Full-screen menus must not place their Back control or title under status-bar icons.
        // Use actual insets rather than a device-specific fixed top margin.
        if(android.os.Build.VERSION.SDK_INT>=30){
            outer.setOnApplyWindowInsetsListener{v,insets->
                val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars())
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom)
                insets
            }
        }
        dialog.show()
        dialog.window?.setLayout(-1,-1)
        if(android.os.Build.VERSION.SDK_INT>=30)outer.requestApplyInsets()
    }
    private fun row(body:LinearLayout,title:String,description:String="",enabled:Boolean=true,
                    icon:String="◦",action:()->Unit){
        val r=LinearLayout(host).apply{
            gravity=Gravity.CENTER_VERTICAL;setPadding(dp(9),dp(5),dp(9),dp(5))
            minimumHeight=dp(52);alpha=if(enabled)1f else .45f
        }
        r.addView(txt(icon,22f),LinearLayout.LayoutParams(dp(39),dp(44)))
        val bodyText=LinearLayout(host).apply{orientation=LinearLayout.VERTICAL}
        val dangerAction=title=="End session"||title=="End sessions…"||
            title=="Close workspace"
        bodyText.addView(txt(title,15f).apply{
            if(dangerAction)setTextColor(0xffff6b6b.toInt())
        })
        if(description.isNotEmpty())bodyText.addView(txt(description,12f).apply{
            setTextColor(if(enabled)secondary else 0xffff6b6b.toInt())
        })
        r.addView(bodyText,LinearLayout.LayoutParams(0,-2,1f))
        r.setOnClickListener{
            if(enabled)action()
            else {
                if(description.contains("lock",ignoreCase=true)){
                    host.tesseraActiveCard()?.lock?.blockedFeedback()
                }
                host.notice(description)
            }
        }
        body.addView(r,LinearLayout.LayoutParams(-1,-2))
    }
    private fun footerButton(footer:LinearLayout,value:String,action:()->Unit){
        footer.addView(button(value,action).apply{
            textSize=15f;setTypeface(null,Typeface.BOLD)
            setTextColor(0xff0b0e13.toInt());background=round(accent)
        },LinearLayout.LayoutParams(0,dp(52),1f).apply{
            leftMargin=dp(4);rightMargin=dp(4)
        })
    }
    /** UI-only fallback: opening ⋮ or holding the chip must never do nothing.
     * No WebView is created, reloaded or reconfigured by this menu.
     */
    fun paneUnavailableMenu(){
        val n=host.tesseraActivePane()?.let{host.paneIndex(it.id)}?:0
        page("Pane $n · Browser unavailable","The browser view is not ready"){body,_,dialog->
            body.addView(txt("Pane controls are unavailable until its browser view is ready.",14f)
                .apply{setPadding(dp(8),dp(10),dp(8),dp(12))})
            row(body,"Check pane again","Opens the pane menu when its view is ready",icon="↻"){
                dialog.dismiss()
                val card=host.tesseraActiveCard()
                if(card!=null)paneMenu(card,true)
                else host.notice("Pane $n is still unavailable")
            }
            row(body,"Workspaces",icon="▣"){dialog.dismiss();workspaceScreen()}
            body.addView(txt("All panes",17f).apply{
                setPadding(dp(12),dp(20),0,dp(8))
            })
            row(body,"Filter panes",icon="⌕"){dialog.dismiss();filterScreen()}
            row(body,"Check isolation & health",icon="♧"){
                dialog.dismiss();SessionIntegrityReport(host).open()
            }
            row(body,"Settings",icon="⚙"){dialog.dismiss();settingsScreen()}
        }
    }
    /** Match the reference menu structure: editable colour choices live in the first row. */
    private fun paneColourRow(body:LinearLayout,pane:PaneState){
        val block=LinearLayout(host).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(9),dp(4),dp(9),dp(6))
        }
        block.addView(txt("COLOUR TAG",11f).apply{
            setTextColor(secondary)
        },LinearLayout.LayoutParams(-1,dp(26)))
        val strip=LinearLayout(host).apply{gravity=Gravity.CENTER_VERTICAL}
        val dots=mutableListOf<Pair<Int,TextView>>()
        val choices=listOf(0xff51606e.toInt())+FramePalette.tesseraColors
        fun updateRings(){
            val selected=host.store.frameColor(pane.id)
            dots.forEach{(value,dot)->
                dot.background=GradientDrawable().apply{
                    shape=GradientDrawable.OVAL
                    setColor(if(value==0xff51606e.toInt())surface3 else value)
                    setStroke(dp(if(selected==value)3 else 1),
                        if(selected==value)Color.WHITE else stroke)
                }
            }
        }
        choices.forEachIndexed{index,value->
            val name=if(index==0)"None" else FramePalette.tesseraNames[index-1]
            val touch=FrameLayout(host).apply{
                isClickable=true;isFocusable=true
                contentDescription="Set pane colour to $name"
                background=RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x50ffffff),
                    ColorDrawable(Color.TRANSPARENT),null)
            }
            val dot=txt(if(index==0)"×" else "",17f).apply{
                gravity=Gravity.CENTER
                setTextColor(this@TesseraBar.text)
                importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            dots.add(value to dot)
            touch.addView(dot,FrameLayout.LayoutParams(dp(34),dp(34),Gravity.CENTER))
            touch.setOnClickListener{
                val exists=host.store.spaces.any{ws->ws.panes.any{p->p.id==pane.id}}
                if(!exists){host.notice("Pane is no longer available");return@setOnClickListener}
                val old=host.store.frameColor(pane.id)
                if(old==value)return@setOnClickListener
                host.tesseraRecolor(setOf(pane.id),value)
                updateRings()
                snack("Coloured $name: 1 pane"){
                    val live=host.store.spaces.any{ws->ws.panes.any{p->p.id==pane.id}}
                    if(live && host.store.frameColor(pane.id)==value){
                        host.tesseraRecolor(setOf(pane.id),old)
                        updateRings()
                    }
                }
            }
            strip.addView(touch,LinearLayout.LayoutParams(dp(44),dp(44)))
        }
        updateRings()
        block.addView(HorizontalScrollView(host).apply{
            isHorizontalScrollBarEnabled=false
            addView(strip)
        },LinearLayout.LayoutParams(-1,dp(44)))
        body.addView(block,LinearLayout.LayoutParams(-1,-2))
    }
    /** A native state control on the right, without changing the existing desktop action. */
    private fun toggleRow(body:LinearLayout,title:String,detail:String,enabled:Boolean,
                          checked:Boolean,icon:String,action:()->Unit){
        val r=LinearLayout(host).apply{
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(9),dp(5),dp(9),dp(5))
            minimumHeight=dp(52);alpha=if(enabled)1f else .45f
        }
        r.addView(txt(icon,22f),LinearLayout.LayoutParams(dp(39),dp(44)))
        val labels=LinearLayout(host).apply{orientation=LinearLayout.VERTICAL}
        labels.addView(txt(title,15f))
        labels.addView(txt(detail,12f).apply{
            setTextColor(if(enabled)secondary else 0xffff6b6b.toInt())
        })
        r.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
        r.addView(android.widget.Switch(host).apply{
            isChecked=checked;isClickable=false;isFocusable=false
            isEnabled=enabled
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },LinearLayout.LayoutParams(dp(52),dp(44)))
        r.setOnClickListener{
            if(enabled)action()
            else {
                host.tesseraActiveCard()?.lock?.blockedFeedback()
                host.notice(detail)
            }
        }
        body.addView(r,LinearLayout.LayoutParams(-1,-2))
    }
    fun paneMenu(card:PaneCard,withAll:Boolean){
        val pane=card.state
        val workspace=host.store.spaces.indexOfFirst{it.panes.any{p->p.id==pane.id}}+1
        val site=runCatching{URI(pane.url).host}.getOrNull().orEmpty()
        page("Pane "+host.paneIndex(pane.id)+" · Workspace "+workspace,
            card.webView.title.orEmpty().take(40)+" · "+site){body,_,dialog->
            paneColourRow(body,pane)
            row(body,if(pane.locked)"Unlock pane" else "Lock pane",icon="♢"){
                val previous=pane.locked
                val applied=!previous
                pane.locked=applied;host.updateWindowLocks();dialog.dismiss()
                snack(if(applied)"Locked 1 pane" else "Unlocked 1 pane"){
                    val live=host.store.spaces.any{ws->ws.panes.any{it.id==pane.id}}
                    if(live&&pane.locked==applied){
                        pane.locked=previous;host.updateWindowLocks()
                    }
                }
            }
            toggleRow(body,"Desktop site",
                if(pane.locked)"Unlock pane first" else "Changes this pane's layout; reloads the page",
                !pane.locked,pane.desktop,"▣"){
                dialog.dismiss();card.toggleDesktop()
            }
            row(body,"Cookies & site data",if(pane.locked)"View-only while locked" else "",icon="☷"){
                dialog.dismiss();host.cookieEditor(card)
            }
            row(body,"Copy diagnostics",icon="⧉"){host.copyPanePageReport(card);dialog.dismiss()}
            row(body,"Select multiple",icon="☑"){
                dialog.dismiss();host.enterSelection(pane.id)
            }
            row(body,"End session",if(pane.locked)"Unlock pane first" else
                "Fresh profile replaces this slot",!pane.locked,"×"){
                dialog.dismiss();host.tesseraEnd(setOf(pane.id))
            }
            if(withAll){
                body.addView(txt("All panes",17f).apply{
                    setPadding(dp(12),dp(20),0,dp(8))
                })
                row(body,"Filter panes",icon="⌕"){dialog.dismiss();filterScreen()}
                row(body,"Check isolation & health",icon="♧"){
                    dialog.dismiss();SessionIntegrityReport(host).open()
                }
                row(body,"Settings",icon="⚙"){dialog.dismiss();settingsScreen()}
            }
        }
    }
    private fun colorDialog(ids:Set<String>){
        if(ids.isEmpty())return
        val dialog=Dialog(host)
        val root=LinearLayout(host).apply{
            orientation=LinearLayout.VERTICAL;setPadding(dp(15),dp(15),dp(15),dp(15))
            background=round(surface,22)
        }
        root.addView(txt("Colour tag",18f),LinearLayout.LayoutParams(-1,dp(40)))
        val swatches=LinearLayout(host).apply{gravity=Gravity.CENTER_VERTICAL}
        val prior=ids.associateWith{host.store.frameColor(it)}
        (listOf(0xff51606e.toInt())+FramePalette.tesseraColors).forEachIndexed{index,color->
            val name=if(index==0)"None" else FramePalette.tesseraNames[index-1]
            swatches.addView(button(if(index==0)"×" else "●"){
                val live=host.store.spaces.flatMap{it.panes}.map{it.id}.toSet()
                if(!live.containsAll(ids)){
                    dialog.dismiss()
                    host.notice("Selected panes changed; colour not applied")
                    return@button
                }
                val changed=ids.filter{host.store.frameColor(it)!=color}.toSet()
                if(changed.isEmpty()){dialog.dismiss();return@button}
                host.tesseraRecolor(changed,color);dialog.dismiss();render()
                snack("Coloured "+name+": "+changed.size+" panes"){
                    // Fresh panes must never inherit an ended session's colour.
                    // Do not undo an intervening recolour or touch stale IDs.
                    val nowLive=host.store.spaces.flatMap{it.panes}.map{it.id}.toSet()
                    changed.forEach{id->
                        if(id in nowLive && host.store.frameColor(id)==color){
                            host.tesseraRecolor(setOf(id),prior.getValue(id))
                        }
                    }
                }
            }.apply{
                contentDescription="Set selected panes colour to "+name
                setTextColor(if(index==0)this@TesseraBar.text else color)
                background=round(surface3,20,if(ids.any{host.store.frameColor(it)==color})3 else 1,
                    if(ids.any{host.store.frameColor(it)==color})Color.WHITE else stroke)
            },LinearLayout.LayoutParams(0,dp(44),1f))
        }
        root.addView(swatches)
        dialog.setContentView(root);dialog.show()
        dialog.window?.setLayout((host.resources.displayMetrics.widthPixels*.92).toInt()
            .coerceAtMost(dp(440)),-2)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.setDimAmount(.55f)
    }
    fun workspaceScreen(){
        page("Workspaces",host.store.spaces.size.toString()+" of 7","×",80){body,footer,dialog->
            val cols=if(host.resources.displayMetrics.widthPixels>=dp(600))3 else 2
            val width=(host.resources.displayMetrics.widthPixels-dp(48))/cols
            val grid=android.widget.GridLayout(host).apply{columnCount=cols}
            host.store.spaces.forEachIndexed{index,ws->
                val visible=ws.panes.any{p->
                    FramePalette.matches(p.url,host.store.frameColor(p.id),
                        host.store.filterQuery,host.store.selectedFrameColors)
                }
                if(visible||host.store.filterQuery.isBlank()&&host.store.selectedFrameColors.isEmpty()){
                    val card=LinearLayout(host).apply{
                        orientation=LinearLayout.VERTICAL
                        setPadding(dp(6),dp(6),dp(6),dp(6))
                        background=round(surface,16,if(ws.id==host.store.currentId)2 else 1,
                            if(ws.id==host.store.currentId)Color.WHITE else stroke)
                    }
                    // The reference places workspace number, count, lock and close
                    // together in the card header, BEFORE the 2×3 live preview.
                    val cardHeader=LinearLayout(host).apply{gravity=Gravity.CENTER_VERTICAL}
                    cardHeader.addView(txt((index+1).toString(),17f).apply{
                        setTypeface(null,Typeface.BOLD)
                        contentDescription="Workspace "+(index+1)
                    },LinearLayout.LayoutParams(dp(20),dp(44)))
                    cardHeader.addView(txt(
                        ws.panes.count{it.url!="about:blank"}.toString()+" open",10f
                    ).apply{
                        setTextColor(secondary)
                    },LinearLayout.LayoutParams(0,dp(44),1f))
                    cardHeader.addView(button("⌑"){
                        val next=!ws.panes.all{it.locked}
                        ws.panes.forEach{it.locked=next}
                        host.updateWindowLocks();dialog.dismiss();workspaceScreen()
                    }.apply{
                        contentDescription=(if(ws.panes.all{it.locked})"Unlock" else "Lock")+
                            " all panes in workspace "+(index+1)
                    },LinearLayout.LayoutParams(dp(44),dp(44)))
                    val canCloseWorkspace=!ws.locked&&ws.panes.none{it.locked}
                    cardHeader.addView(button("×"){
                        if(canCloseWorkspace)dialog.dismiss()
                        // A protected workspace stays open; the close action explains
                        // the refusal instead of dismissing the card silently.
                        host.tesseraCloseWorkspace(ws.id)
                    }.apply{
                        contentDescription="Close workspace "+(index+1)+
                            (if(canCloseWorkspace)"" else "; unlock workspace and panes first")
                        alpha=if(canCloseWorkspace)1f else .45f
                    },LinearLayout.LayoutParams(dp(44),dp(44)))
                    card.addView(cardHeader,LinearLayout.LayoutParams(-1,dp(44)))
                    val previews=android.widget.GridLayout(host).apply{columnCount=2}
                    ws.panes.forEach{p->
                        val tile=LinearLayout(host).apply{
                            orientation=LinearLayout.VERTICAL
                            setBackgroundColor(surface2)
                        }
                        tile.addView(View(host).apply{
                            setBackgroundColor(host.store.frameColor(p.id))
                        },LinearLayout.LayoutParams(-1,dp(4)))
                        val live=host.windowCard(p.id)?.webView
                        val tileWidth=(width-dp(22))/2
                        val tileHeight=dp(if(cols==3)64 else 72)-dp(4)
                        fun fallbackPreview(){
                            val label=if(p.url=="about:blank")"New"
                                else runCatching{URI(p.url).host}.getOrNull()?:"Empty"
                            tile.addView(txt(label+(if(p.locked)" ♢" else ""),9f).apply{
                                gravity=Gravity.CENTER
                                maxLines=2
                                ellipsize=android.text.TextUtils.TruncateAt.END
                                contentDescription="Pane "+host.paneIndex(p.id)+": "+label+
                                    (if(p.locked)", locked" else "")
                                setPadding(dp(2),0,dp(2),0)
                            },LinearLayout.LayoutParams(-1,tileHeight))
                        }
                        if(p.url=="about:blank"){
                            fallbackPreview()
                        }else if(live!=null&&live.width>0&&live.height>0){
                            try{
                                val bitmap=android.graphics.Bitmap.createBitmap(
                                    tileWidth.coerceAtLeast(1),tileHeight.coerceAtLeast(1),
                                    android.graphics.Bitmap.Config.RGB_565)
                                val canvas=android.graphics.Canvas(bitmap)
                                canvas.drawColor(Color.WHITE)
                                canvas.save()
                                // Preserve the site's proportions in the tiny
                                // workspace preview; scale to tile width and crop
                                // the lower page instead of squashing its height.
                                val scale=tileWidth.toFloat()/live.width
                                canvas.scale(scale,scale)
                                live.draw(canvas)
                                canvas.restore()
                                // WebView.draw may produce an empty/solid bitmap even
                                // when the page has a URL. Avoid misleading blank previews.
                                val samples=listOf(
                                    bitmap.getPixel(0,0),
                                    bitmap.getPixel(bitmap.width/2,bitmap.height/2),
                                    bitmap.getPixel(bitmap.width-1,0),
                                    bitmap.getPixel(0,bitmap.height-1),
                                    bitmap.getPixel(bitmap.width-1,bitmap.height-1))
                                val first=samples.first()
                                val featureless=samples.all{value->
                                    kotlin.math.abs(Color.red(value)-Color.red(first))<=8 &&
                                    kotlin.math.abs(Color.green(value)-Color.green(first))<=8 &&
                                    kotlin.math.abs(Color.blue(value)-Color.blue(first))<=8
                                }
                                if(featureless)fallbackPreview()
                                else tile.addView(android.widget.ImageView(host).apply{
                                    setImageBitmap(bitmap)
                                    scaleType=android.widget.ImageView.ScaleType.FIT_XY
                                    contentDescription="Snapshot of pane "+host.paneIndex(p.id)
                                },LinearLayout.LayoutParams(-1,tileHeight))
                            }catch(_:RuntimeException){fallbackPreview()}
                        }else fallbackPreview()
                        previews.addView(tile,android.widget.GridLayout.LayoutParams().apply{
                            this.width=(width-dp(22))/2
                            height=dp(if(cols==3)64 else 72)
                            setMargins(dp(1),dp(1),dp(1),dp(1))
                        })
                    }
                    card.addView(previews)
                    card.setOnClickListener{
                        host.tesseraOpenWorkspace(ws.id);dialog.dismiss()
                    }
                    card.setOnLongClickListener{
                        dialog.dismiss();workspaceMenu(ws)
                        card.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);true
                    }
                    grid.addView(card,android.widget.GridLayout.LayoutParams().apply{
                        this.width=width;height=-2
                        setMargins(dp(4),dp(4),dp(4),dp(4))
                    })
                }
            }
            body.addView(grid)
            val addReason=when{
                host.tesseraSelecting()->"Finish selecting first"
                host.store.spaces.size>=7->"7 is the maximum; close a workspace to add one"
                host.store.filterQuery.isNotBlank()||
                    host.store.selectedFrameColors.isNotEmpty()->"Clear filters to add a workspace"
                else->""
            }
            val footerStack=LinearLayout(host).apply{
                orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER
            }
            footerStack.addView(button("+ New workspace"){
                if(addReason.isNotBlank())host.notice(addReason)
                else{
                    dialog.dismiss();host.tesseraNewWorkspace();workspaceScreen()
                }
            }.apply{
                textSize=15f
                setTypeface(null,Typeface.BOLD)
                setTextColor(0xff0b0e13.toInt())
                background=round(accent,12)
                alpha=if(addReason.isBlank())1f else .45f
                contentDescription=if(addReason.isBlank())"Create new workspace"
                    else "New workspace unavailable: "+addReason
            },LinearLayout.LayoutParams(-1,dp(52)))
            if(addReason.isNotBlank()){
                footerStack.addView(txt(addReason,10f).apply{
                    gravity=Gravity.CENTER
                    setTextColor(0xffff6b6b.toInt())
                },LinearLayout.LayoutParams(-1,dp(16)))
            }
            footer.addView(footerStack,LinearLayout.LayoutParams(0,-2,1f))
        }
    }
    private fun workspaceMenu(ws:WorkspaceState){
        val n=host.store.spaces.indexOfFirst{it.id==ws.id}+1
        page("Workspace "+n,
            ws.panes.count{it.url!="about:blank"}.toString()+" of 6 panes open · "+
                ws.panes.count{it.locked}+" locked"){body,_,dialog->
            // These rows are conditional in the approved handoff, not disabled
            // duplicates. The current workspace never shows an inapplicable action.
            if(!ws.panes.all{it.locked}){
                row(body,"Lock all 6 panes","Only this workspace",icon="⌑"){
                    ws.panes.forEach{it.locked=true}
                    host.updateWindowLocks()
                    dialog.dismiss()
                }
            }
            val lockedCount=ws.panes.count{it.locked}
            if(lockedCount>0){
                row(body,"Unlock all panes",lockedCount.toString()+" locked",icon="♢"){
                    ws.panes.forEach{it.locked=false}
                    host.updateWindowLocks()
                    dialog.dismiss()
                }
            }
            row(body,"New workspace to the right",
                if(host.tesseraSelecting())"Finish selecting first" else
                    if(host.store.spaces.size==7)"7 is the maximum" else
                    if(host.store.filterQuery.isNotBlank()||host.store.selectedFrameColors.isNotEmpty())
                        "Clear filters first" else "",
                !host.tesseraSelecting() && host.store.spaces.size<7 &&
                    host.store.filterQuery.isBlank() && host.store.selectedFrameColors.isEmpty(),
                "+"){dialog.dismiss();host.tesseraNewWorkspace()}
            row(body,"Close workspace",if(ws.locked||ws.panes.any{it.locked})
                "Unlock workspace and panes first" else
                "Ends all 6 sessions. Reopening starts fresh.",
                !ws.locked&&ws.panes.none{it.locked},"×"){
                dialog.dismiss();host.tesseraCloseWorkspace(ws.id)
            }
        }
    }
    private fun selectionScreen(){
        val count=host.tesseraSelected().size
        page("Selected panes",count.toString()+" selected"){body,footer,dialog->
            // Keep the six reference menu actions in their expected order. Cross-
            // workspace selection remains reachable from a docked footer action.
            footerButton(footer,"Workspaces"){
                dialog.dismiss();workspaceScreen()
            }
            val available=count>0
            val reason=if(available)"" else "Select at least one pane first"
            row(body,"Reload selected",reason,available,"↻"){
                dialog.dismiss();host.tesseraBulkReload()
            }
            row(body,"Open an address in selected",reason,available,"⌕"){
                dialog.dismiss();bulkAddressDialog()
            }
            row(body,"Desktop site on",reason,available,"▣"){
                dialog.dismiss();host.tesseraBulkDesktop(true)
            }
            row(body,"Desktop site off",reason,available,"▣"){
                dialog.dismiss();host.tesseraBulkDesktop(false)
            }
            row(body,"Clear selection",reason,available,"×"){
                host.tesseraExitSelection();dialog.dismiss()
            }
            row(body,"End sessions…",reason,available,"×"){
                dialog.dismiss();host.tesseraEnd(host.tesseraSelected())
            }
        }
    }
    /** The handoff requires a full-screen entry page, not a compact picker. */
    private fun bulkAddressDialog(){
        val selectedCount=host.tesseraSelected().size
        page("Open in selected panes",
            selectedCount.toString()+" selected · locked panes will be skipped"){
                body,footer,dialog->
            val input=EditText(host).apply{
                hint="Search or enter address"
                contentDescription="Address or search for selected panes"
                setSingleLine(true)
                textSize=15f
                setTextColor(this@TesseraBar.text)
                setHintTextColor(secondary)
                inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                imeOptions=EditorInfo.IME_ACTION_GO
                setPadding(dp(12),0,dp(12),0)
                background=round(surface2,12,1)
            }
            body.addView(input,LinearLayout.LayoutParams(-1,dp(52)))
            body.addView(txt("Opens in each selected unlocked pane. Locked panes are skipped.",
                12f).apply{
                setTextColor(secondary)
                setPadding(dp(8),dp(10),dp(8),dp(4))
            })
            val submitBulk:()->Unit = {
                val resolved=BrowserPolicy.resolveAddressOrSearch(input.text.toString())
                if(resolved==null){
                    input.error="Enter an address"
                }else{
                    dialog.dismiss()
                    host.tesseraBulkOpenAddress(resolved)
                }
            }
            input.setOnEditorActionListener{_,action,event->
                val hardware=event?.keyCode==KeyEvent.KEYCODE_ENTER &&
                    event.action==KeyEvent.ACTION_DOWN
                if(action==EditorInfo.IME_ACTION_GO||hardware){
                    submitBulk()
                    true
                }else false
            }
            footer.addView(button("Cancel"){dialog.dismiss()}.apply{
                textSize=15f
                contentDescription="Cancel opening address in selected panes"
                setTextColor(this@TesseraBar.text)
                background=round(surface3,12,1)
            },LinearLayout.LayoutParams(0,dp(52),1f).apply{
                leftMargin=dp(4);rightMargin=dp(4)
            })
            footerButton(footer,"Open"){submitBulk()}
        }
    }
    private fun filterScreen(){
        page("Filter panes",
            "Matching panes stay in place; others fade. Sessions keep running."){body,footer,dialog->
            body.addView(txt("COLOURS · ANY OF THESE",11f).apply{
                setTextColor(secondary)
                setPadding(dp(8),dp(12),0,dp(8))
            })
            val swatches=LinearLayout(host).apply{gravity=Gravity.CENTER_VERTICAL}
            val circles=linkedMapOf<String,TextView>()
            val swatchTargets=linkedMapOf<String,FrameLayout>()
            fun refreshSwatches(){
                FramePalette.tesseraNames.forEachIndexed{i,name->
                    val selected=name in host.store.selectedFrameColors
                    val color=FramePalette.tesseraColors[i]
                    circles[name]?.apply{
                        text=if(selected)"✓" else ""
                        setTextColor(if(Color.luminance(color)>.18)
                            0xff0b0e13.toInt() else Color.WHITE)
                        background=round(color,18,if(selected)3 else 1,
                            if(selected)Color.WHITE else stroke)
                    }
                    swatchTargets[name]?.apply{
                        isSelected=selected
                        contentDescription=name+" filter "+(if(selected)"selected" else "not selected")
                    }
                }
            }
            FramePalette.tesseraNames.forEach{name->
                val circle=txt("",15f).apply{
                    gravity=Gravity.CENTER
                    setTypeface(null,Typeface.BOLD)
                }
                val target=FrameLayout(host).apply{
                    minimumHeight=dp(48)
                    isClickable=true
                    isFocusable=true
                    setOnClickListener{
                        val colors=host.store.selectedFrameColors.toMutableSet()
                        if(!colors.add(name))colors.remove(name)
                        host.tesseraSetFilter(host.store.filterQuery,colors)
                        // Update in place: do not close the page, lose URL text,
                        // or dismiss the keyboard after each colour selection.
                        refreshSwatches()
                    }
                }
                target.addView(circle,FrameLayout.LayoutParams(
                    dp(36),dp(36),Gravity.CENTER))
                circles[name]=circle
                swatchTargets[name]=target
                swatches.addView(target,LinearLayout.LayoutParams(0,dp(48),1f))
            }
            refreshSwatches()
            body.addView(swatches)
            body.addView(txt("URL CONTAINS",11f).apply{
                setTextColor(secondary)
                setPadding(dp(8),dp(20),0,dp(8))
            })
            val edit=EditText(host).apply{
                hint="URL contains";setSingleLine(true);setText(host.store.filterQuery)
                setTextColor(this@TesseraBar.text);setHintTextColor(secondary)
                background=round(surface2,12,1);setPadding(dp(12),0,dp(12),0)
                addTextChangedListener(object:TextWatcher{
                    override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
                    override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
                        host.tesseraSetFilter(s?.toString().orEmpty(),host.store.selectedFrameColors.toSet())
                    }
                    override fun afterTextChanged(s:Editable?){}
                })
            }
            body.addView(edit,LinearLayout.LayoutParams(-1,dp(52)))
            body.addView(txt("Colours match any selected colour. The URL must also match. Filters stay on until you clear them.",12f).apply{
                setTextColor(secondary)
                setPadding(dp(8),dp(16),dp(8),dp(8))
            })
            footerButton(footer,"Clear all"){host.tesseraClearFilters();dialog.dismiss()}
            footerButton(footer,"Done"){dialog.dismiss()}
        }
    }
    private fun settingsScreen(){
        page("Settings"){body,_,dialog->
            fun section(name:String){
                body.addView(txt(name,11f).apply{
                    setTextColor(secondary)
                    setPadding(dp(9),dp(16),0,dp(8))
                })
            }
            section("KEEP SESSIONS ALIVE")
            val notificationAllowed=(android.os.Build.VERSION.SDK_INT<33 ||
                host.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==
                    android.content.pm.PackageManager.PERMISSION_GRANTED) &&
                (host.getSystemService(Context.NOTIFICATION_SERVICE) as
                    android.app.NotificationManager).areNotificationsEnabled()
            row(body,"Keep-alive notification",
                if(notificationAllowed)"✓ Allowed" else "Allow",icon="◉"){
                dialog.dismiss();host.requestNotificationPermissionFromSettings()
            }
            val power=host.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val batteryExempt=power.isIgnoringBatteryOptimizations(host.packageName)
            row(body,"Battery optimisation",
                if(batteryExempt)"✓ Exempt" else "Fix",icon="◉"){
                dialog.dismiss();host.requestBatteryExemption(true)
            }
            row(body,"App info","Android system app settings",icon="⚙"){
                dialog.dismiss();host.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:"+host.packageName)))
            }
            if(host.store.pendingDeletion.isNotEmpty()){
                val pending=host.store.pendingDeletion.size
                row(body,"Finish erasing ended sessions ("+pending+" waiting)",
                    "Close and reopen SIXPANE to retry retired-profile cleanup",icon="↻"){
                    host.notice("Restart SIXPANE to retry erasing "+
                        pending+" retired profiles. Active panes are not affected.")
                }
            }
            section("LAYOUT")
            row(body,"Full-size pages in small panes",
                if(host.store.fullSizeSmallPanes)"On" else "Off",icon="▣"){
                host.store.fullSizeSmallPanes=!host.store.fullSizeSmallPanes
                host.persist();dialog.dismiss();settingsScreen()
            }
            listOf("automatic" to "Automatic","2x3" to "Always 2×3",
                "3x2" to "Always 3×2").forEach{(value,label)->
                row(body,label,if(host.store.gridPreference==value)"✓" else "",icon="◫"){
                    host.tesseraSetGrid(value);dialog.dismiss();settingsScreen()
                }
            }
            toggleRow(body,"Condensed filter results",
                "Optional: group matches across workspaces in six-slot pages",
                true,host.store.compactFilterResults,"▣"){
                host.tesseraSetCompactFilterResults(!host.store.compactFilterResults)
                dialog.dismiss();settingsScreen()
            }
            section("ABOUT")
            row(body,"How sessions work",icon="ⓘ"){dialog.dismiss();howSessions()}
            // The second legacy Settings menu was an unapproved duplicate.
            // Pane diagnostics and the All panes integrity report are available
            // from their canonical menu actions; cleanup is surfaced above
            // when pending and never deletes live profiles.
        }
    }
    private fun howSessions(){
        page("How sessions work"){body,_,_->
            listOf("Each pane is its own private browser.",
                "A login in one pane is not shared with any other pane.",
                "Ending a session or closing a workspace retires its profile.",
                "Bookmarks are addresses saved separately in each pane.",
                "Android may pause live pages when the app is closed.",
                "Check isolation & health from All panes."
            ).forEach{
                body.addView(txt(it).apply{setPadding(dp(8),dp(14),dp(8),dp(14))})
            }
        }
    }
    fun confirm(title:String,message:String,yes:String,action:()->Unit){
        val dialog=Dialog(host)
        val outer=LinearLayout(host).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18),dp(18),dp(18),dp(14))
            background=round(surface,22)
        }
        outer.addView(txt(title,19f).apply{setTypeface(null,Typeface.BOLD)})
        outer.addView(txt(message,14f).apply{
            setTextColor(secondary);setPadding(0,dp(12),0,dp(14))
        })
        val actions=LinearLayout(host).apply{gravity=Gravity.CENTER_VERTICAL}
        actions.addView(button("Cancel"){dialog.dismiss()}.apply{
            textSize=15f;background=round(surface3,12,1)
        },LinearLayout.LayoutParams(0,dp(52),1f).apply{rightMargin=dp(6)})
        actions.addView(button(yes){
            dialog.dismiss();action()
        }.apply{
            textSize=15f;setTypeface(null,Typeface.BOLD)
            setTextColor(Color.WHITE)
            background=round(0xffff6b6b.toInt(),12)
        },LinearLayout.LayoutParams(0,dp(52),1f))
        outer.addView(actions)
        dialog.setContentView(outer);dialog.show()
        dialog.window?.setLayout((host.resources.displayMetrics.widthPixels*.92).toInt()
            .coerceAtMost(dp(440)),-2)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.setDimAmount(.55f)
    }
    fun welcomeScreen(){
        page("Welcome to SIXPANE","42 private browsers — 7 workspaces of 6 panes."){body,footer,dialog->
            body.addView(txt("The bar controls your active pane. Use Android's back gesture to go back.",15f).apply{
                setPadding(dp(12),dp(14),dp(12),dp(14))
            })
            body.addView(txt("Tap a pane header to activate, double-tap to enlarge, hold for its menu.",15f).apply{
                setPadding(dp(12),dp(14),dp(12),dp(14))
            })
            body.addView(txt("Help keep sessions available",17f).apply{
                setPadding(dp(12),dp(18),dp(12),dp(8))
            })
            val power=host.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if(!power.isIgnoringBatteryOptimizations(host.packageName)){
                row(body,"Allow background operation","Android battery restrictions",icon="◉"){
                    host.requestBatteryExemption(true)
                }
            }
            if(android.os.Build.VERSION.SDK_INT>=33 &&
                host.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=
                    android.content.pm.PackageManager.PERMISSION_GRANTED){
                row(body,"Allow keep-alive notification",icon="◉"){
                    host.requestNotificationPermissionFromSettings()
                }
            }
            footerButton(footer,"Got it"){
                host.store.welcomeSeen=true;host.persist();dialog.dismiss()
            }
        }
    }
    fun snack(message:String,undo:(()->Unit)?=null){
        if(dock.windowToken==null)return
        lastSnack?.dismiss()
        val box=LinearLayout(host).apply{
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(14),dp(4),dp(10),dp(4))
            background=round(surface3,14,1)
            elevation=dp(8).toFloat()
        }
        box.addView(txt(message,13f),LinearLayout.LayoutParams(0,dp(40),1f))
        val width=(host.resources.displayMetrics.widthPixels-dp(24)).coerceAtLeast(dp(200))
        val height=dp(48)
        val popup=PopupWindow(box,width,height,false)
        if(undo!=null)box.addView(button("Undo"){
            undo();popup.dismiss()
        },LinearLayout.LayoutParams(dp(56),dp(44)))
        lastSnack=popup
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        // Position relative to the actual dock, not the bottom of the physical
        // screen: the IME lifts the dock but does not necessarily lift popups
        // shown with Gravity.BOTTOM. Never cover a website's keyboard controls.
        fun aboveDock(){
            if(dock.windowToken==null)return
            val position=IntArray(2)
            dock.getLocationOnScreen(position)
            val availableWidth=dock.width.takeIf{it>0}
                ?:host.resources.displayMetrics.widthPixels
            val x=position[0]+((availableWidth-width)/2).coerceAtLeast(0)
            val y=(position[1]-height-dp(10)).coerceAtLeast(0)
            if(popup.isShowing)popup.update(x,y,width,height)
            else popup.showAtLocation(dock,Gravity.TOP or Gravity.LEFT,x,y)
        }
        val move=View.OnLayoutChangeListener{_,_,_,_,_,_,_,_,_->aboveDock()}
        dock.addOnLayoutChangeListener(move)
        popup.setOnDismissListener{
            dock.removeOnLayoutChangeListener(move)
            if(lastSnack===popup)lastSnack=null
        }
        aboveDock()
        dock.postDelayed({if(popup.isShowing)popup.dismiss()},
            if(undo==null)TesseraMotion.SNACK_MS else TesseraMotion.SNACK_WITH_ACTION_MS)
    }
}
