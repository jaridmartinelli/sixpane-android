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
    private val paneChip=button("1"){host.tesseraActivePane()?.let{host.tesseraActivate(it.id,true)}}.apply{
        setOnLongClickListener{
            host.tesseraActiveCard()?.let{paneMenu(it,false)}
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);true
        }
    }
    private val lead=txt("⌕",16f)
    private val address=EditText(host).apply{
        textSize=15f;setTextColor(this@TesseraBar.text)
        setHintTextColor(secondary);hint="Search or enter address";setSingleLine(true)
        inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        imeOptions=EditorInfo.IME_ACTION_GO
        setPadding(0,0,0,0);background=null;setSelectAllOnFocus(true)
        setOnFocusChangeListener{_,focus->
            if(focus){
                setText(host.tesseraActivePane()?.url?.takeUnless{it=="about:blank"}.orEmpty())
                selectAll();showSuggestions()
            }else suggestions?.dismiss()
        }
        setOnEditorActionListener{_,action,event->
            val hardware=event?.keyCode==KeyEvent.KEYCODE_ENTER && event.action==KeyEvent.ACTION_DOWN
            if(action==EditorInfo.IME_ACTION_GO||hardware){
                submit(text.toString());true
            }else false
        }
    }
    private val star=button("☆"){val card=host.tesseraActiveCard()
        if(card?.loading==true)card.webView.stopLoading()
        else host.tesseraToggleBookmark()
        render()
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
    }
    private val workspace=button("□"){workspaceScreen()}.apply{
        setOnLongClickListener{
            workspaceMenu(host.store.active())
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);true
        }
    }
    private val more=button("⋮"){host.tesseraMenuAll()}
    private var selectingRendered:Boolean?=null
    private var suggestions:PopupWindow?=null
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
        fun add(title:String,w:Int,action:()->Unit){
            controls.addView(button(title,action),LinearLayout.LayoutParams(dp(w),dp(44)))
        }
        add("×",44){host.tesseraExitSelection()}
        controls.addView(txt(host.tesseraSelected().size.toString()+" selected",14f).apply{
            setTypeface(null,Typeface.BOLD)
        },LinearLayout.LayoutParams(dp(84),dp(44)))
        add(if(host.tesseraSelected().size==6)"None" else "All",56){host.tesseraSelectAll()}
        add("⌑",44){host.tesseraLockSelected(true)}
        add("♢",44){host.tesseraLockSelected(false)}
        add("●",44){colorDialog(host.tesseraSelected())}
        add("⋮",44){selectionScreen()}
        scroll.addView(controls)
        items.addView(scroll,LinearLayout.LayoutParams(-1,dp(44)))
    }
    fun render(){
        val select=host.tesseraSelecting()
        if(select!=selectingRendered){
            selectingRendered=select
            if(select)selection() else regular()
        }
        if(select)selection() else {
            val pane=host.tesseraActivePane()
            val card=host.tesseraActiveCard()
            paneChip.text=(pane?.let{host.paneIndex(it.id)}?:0).toString()+" ↗"
            paneChip.textSize=14f
            paneChip.setTypeface(null,Typeface.BOLD)
            val color=pane?.let{host.store.frameColor(it.id)}?:surface3
            paneChip.background=round(if(color==0xff51606e.toInt())surface3 else color,12,1,Color.WHITE)
            workspace.text="▣"+(host.store.spaces.indexOfFirst{it.id==host.store.currentId}+1)
            workspace.textSize=15f
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
        val clipboard=host.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if(clipboard.hasPrimaryClip()){
            contents.addView(button("Paste & go"){
                val text=clipboard.primaryClip?.getItemAt(0)?.coerceToText(host)?.toString().orEmpty()
                if(text.isNotBlank())submit(text)
            },LinearLayout.LayoutParams(-1,dp(44)))
        }
        host.tesseraBookmarksForActive().take(8).forEach{url->
            val row=LinearLayout(host).apply{gravity=Gravity.CENTER_VERTICAL}
            val title=txt((runCatching{URI(url).host}.getOrNull()?:"Site")+"\n"+url.take(90),12f)
            title.setOnClickListener{submit(url)}
            row.addView(title,LinearLayout.LayoutParams(0,dp(52),1f))
            row.addView(button("×"){
                host.tesseraActivePane()?.let{host.store.toggleBookmarkForPane(it.id,url)}
                suggestions?.dismiss();showSuggestions()
            },LinearLayout.LayoutParams(dp(44),dp(44)))
            contents.addView(row)
        }
        val pop=PopupWindow(contents,dp(310),-2,true).apply{
            isOutsideTouchable=true;elevation=dp(6).toFloat()
            setBackgroundDrawable(ColorDrawable(surface))
        }
        suggestions=pop
        dock.post{
            if(address.hasFocus()){
                contents.measure(
                    View.MeasureSpec.makeMeasureSpec(dp(310),View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dp(500),View.MeasureSpec.AT_MOST))
                val pos=IntArray(2);dock.getLocationOnScreen(pos)
                pop.showAtLocation(dock,Gravity.TOP or Gravity.LEFT,
                    dp(6),(pos[1]-contents.measuredHeight).coerceAtLeast(0))
            }
        }
    }
    private fun updateChips(){
        filterContents.removeAllViews()
        val colors=host.store.selectedFrameColors
        val query=host.store.filterQuery
        if(colors.isEmpty()&&query.isBlank()){
            filterChips.visibility=View.GONE
            filterChips.layoutParams?.height=0
            return
        }
        filterChips.visibility=View.VISIBLE
        filterChips.layoutParams?.height=dp(42)
        filterContents.addView(button("⌕"){filterScreen()},LinearLayout.LayoutParams(dp(40),dp(34)))
        colors.forEach{name->
            filterContents.addView(txt(name+" ×",12f).apply{
                background=round(surface2,16,1);gravity=Gravity.CENTER
                setOnClickListener{host.tesseraSetFilter(query,colors-name)}
            },LinearLayout.LayoutParams(dp(88),dp(34)))
        }
        if(query.isNotBlank()){
            filterContents.addView(txt("URL has \""+query.take(11)+"\" ×",11f).apply{
                gravity=Gravity.CENTER;background=round(surface2,16,1)
                setOnClickListener{host.tesseraSetFilter("",colors)}
            },LinearLayout.LayoutParams(dp(150),dp(34)))
        }
        filterContents.addView(button("Clear all"){host.tesseraClearFilters()},
            LinearLayout.LayoutParams(dp(88),dp(34)))
    }
    private fun page(title:String,subtitle:String="",content:(LinearLayout,LinearLayout,Dialog)->Unit){
        closeAddress()
        val dialog=Dialog(host,android.R.style.Theme_Material_NoActionBar)
        val outer=LinearLayout(host).apply{
            orientation=LinearLayout.VERTICAL;setBackgroundColor(0xff0b0e13.toInt())
        }
        val header=LinearLayout(host).apply{gravity=Gravity.CENTER_VERTICAL}
        header.addView(button("‹"){dialog.dismiss()},LinearLayout.LayoutParams(dp(48),dp(48)))
        val heading=LinearLayout(host).apply{orientation=LinearLayout.VERTICAL}
        heading.addView(txt(title,21f).apply{setTypeface(null,Typeface.BOLD)})
        if(subtitle.isNotBlank())heading.addView(txt(subtitle,13f).apply{setTextColor(secondary)})
        header.addView(heading,LinearLayout.LayoutParams(0,dp(60),1f))
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
        outer.addView(footer,LinearLayout.LayoutParams(-1,dp(64)))
        content(body,footer,dialog)
        dialog.setContentView(outer)
        dialog.show()
        dialog.window?.setLayout(-1,-1)
    }
    private fun row(body:LinearLayout,title:String,description:String="",enabled:Boolean=true,
                    icon:String="◦",action:()->Unit){
        val r=LinearLayout(host).apply{
            gravity=Gravity.CENTER_VERTICAL;setPadding(dp(9),dp(5),dp(9),dp(5))
            minimumHeight=dp(52);alpha=if(enabled)1f else .45f
        }
        r.addView(txt(icon,22f),LinearLayout.LayoutParams(dp(39),dp(44)))
        val bodyText=LinearLayout(host).apply{orientation=LinearLayout.VERTICAL}
        bodyText.addView(txt(title,15f))
        if(description.isNotEmpty())bodyText.addView(txt(description,12f).apply{
            setTextColor(if(enabled)secondary else 0xffff6b6b.toInt())
        })
        r.addView(bodyText,LinearLayout.LayoutParams(0,-2,1f))
        r.setOnClickListener{if(enabled)action()else host.notice(description)}
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
    fun paneMenu(card:PaneCard,withAll:Boolean){
        val pane=card.state
        val workspace=host.store.spaces.indexOfFirst{it.panes.any{p->p.id==pane.id}}+1
        val site=runCatching{URI(pane.url).host}.getOrNull().orEmpty()
        page("Pane "+host.paneIndex(pane.id)+" · Workspace "+workspace,
            card.webView.title.orEmpty().take(40)+" · "+site){body,_,dialog->
            row(body,"Colour tag","Six colours",icon="●"){
                dialog.dismiss();colorDialog(setOf(pane.id))
            }
            row(body,if(pane.locked)"Unlock pane" else "Lock pane",icon="♢"){
                pane.locked=!pane.locked;host.updateWindowLocks();dialog.dismiss()
            }
            row(body,"Desktop site",if(pane.locked)"Unlock pane first" else
                if(pane.desktop)"On" else "Off",!pane.locked,"▣"){
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
        (listOf(0xff51606e.toInt())+FramePalette.tesseraColors).forEachIndexed{index,color->
            swatches.addView(button(if(index==0)"×" else "●"){
                host.tesseraRecolor(ids,color);dialog.dismiss();render()
            }.apply{
                setTextColor(if(index==0)text else color)
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
        page("Workspaces",host.store.spaces.size.toString()+" of 7"){body,footer,dialog->
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
                    card.addView(txt("Workspace "+(index+1),16f).apply{
                        setTypeface(null,Typeface.BOLD)
                    },LinearLayout.LayoutParams(-1,dp(30)))
                    card.addView(txt(ws.panes.count{it.url!="about:blank"}.toString()+" open",12f))
                    val previews=android.widget.GridLayout(host).apply{columnCount=2}
                    ws.panes.forEach{p->
                        val tile=LinearLayout(host).apply{
                            orientation=LinearLayout.VERTICAL
                            setBackgroundColor(surface2)
                        }
                        tile.addView(View(host).apply{
                            setBackgroundColor(host.store.frameColor(p.id))
                        },LinearLayout.LayoutParams(-1,dp(4)))
                        tile.addView(txt((runCatching{URI(p.url).host}.getOrNull()?:"Empty")+
                            if(p.locked)" ♢" else "",9f).apply{maxLines=2},
                            LinearLayout.LayoutParams(-1,dp(58)))
                        previews.addView(tile,android.widget.GridLayout.LayoutParams().apply{
                            this.width=(width-dp(22))/2
                            height=dp(if(cols==3)64 else 72)
                            setMargins(dp(1),dp(1),dp(1),dp(1))
                        })
                    }
                    card.addView(previews)
                    val actions=LinearLayout(host).apply{gravity=Gravity.CENTER_VERTICAL}
                    actions.addView(button("⌑"){
                        val next=!ws.panes.all{it.locked}
                        ws.panes.forEach{it.locked=next}
                        host.updateWindowLocks();dialog.dismiss();workspaceScreen()
                    },LinearLayout.LayoutParams(0,dp(44),1f))
                    actions.addView(button("×"){
                        dialog.dismiss();host.tesseraCloseWorkspace(ws.id)
                    },LinearLayout.LayoutParams(0,dp(44),1f))
                    card.addView(actions)
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
            footerButton(footer,"+ New workspace"){
                if(host.store.spaces.size>=7){
                    host.notice("7 is the maximum. Close a workspace to add one.")
                }else if(host.store.filterQuery.isNotBlank()||
                    host.store.selectedFrameColors.isNotEmpty()){
                    host.notice("Clear filters to add a workspace")
                }else{
                    dialog.dismiss();host.tesseraNewWorkspace();workspaceScreen()
                }
            }
        }
    }
    private fun workspaceMenu(ws:WorkspaceState){
        val n=host.store.spaces.indexOfFirst{it.id==ws.id}+1
        page("Workspace "+n,
            ws.panes.count{it.url!="about:blank"}.toString()+" of 6 panes open · "+
                ws.panes.count{it.locked}+" locked"){body,_,dialog->
            row(body,"Lock all 6 panes","Only this workspace",!ws.panes.all{it.locked},"⌑"){
                ws.panes.forEach{it.locked=true};host.updateWindowLocks();dialog.dismiss()
            }
            row(body,"Unlock all panes",ws.panes.count{it.locked}.toString()+" locked",
                ws.panes.any{it.locked},"♢"){
                ws.panes.forEach{it.locked=false};host.updateWindowLocks();dialog.dismiss()
            }
            row(body,"New workspace to the right",
                if(host.store.spaces.size==7)"7 is the maximum" else "",host.store.spaces.size<7,
                "+"){dialog.dismiss();host.tesseraNewWorkspace()}
            row(body,"Close workspace",if(ws.panes.any{it.locked})"Unlock panes first" else
                "Ends all 6 sessions. Reopening starts fresh.",ws.panes.none{it.locked},"×"){
                dialog.dismiss();host.tesseraCloseWorkspace(ws.id)
            }
        }
    }
    private fun selectionScreen(){
        page("Selected panes",host.tesseraSelected().size.toString()+" selected"){body,_,dialog->
            row(body,"Reload selected",icon="↻"){
                host.tesseraSelected().forEach{
                    val pane=host.store.spaces.flatMap{ws->ws.panes}.firstOrNull{p->p.id==it}
                    if(pane?.locked!=true)host.windowCard(it)?.refresh()
                };dialog.dismiss()
            }
            row(body,"Open an address in selected",icon="⌕"){
                dialog.dismiss();bulkAddressDialog()
            }
            row(body,"Desktop site on",icon="▣"){
                host.tesseraSelected().forEach{id->
                    val card=host.windowCard(id)
                    if(card?.state?.locked==false)card.setDesktopMode(true)
                };dialog.dismiss()
            }
            row(body,"Desktop site off",icon="▣"){
                host.tesseraSelected().forEach{id->
                    val card=host.windowCard(id)
                    if(card?.state?.locked==false)card.setDesktopMode(false)
                };dialog.dismiss()
            }
            row(body,"Clear selection",icon="×"){
                host.tesseraSelected().forEach{};host.tesseraExitSelection();dialog.dismiss()
            }
            row(body,"End sessions…",icon="×"){
                dialog.dismiss();host.tesseraEnd(host.tesseraSelected())
            }
        }
    }
    private fun bulkAddressDialog(){
        val dialog=Dialog(host)
        val input=EditText(host).apply{
            hint="Search or enter address";setSingleLine(true);textSize=15f
            setTextColor(this@TesseraBar.text);setHintTextColor(secondary)
            background=round(surface2,12,1)
        }
        val outer=LinearLayout(host).apply{
            orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(18),dp(18),dp(12))
            background=round(surface,22)
        }
        outer.addView(txt("Open in selected panes",18f))
        outer.addView(input,LinearLayout.LayoutParams(-1,dp(52)))
        outer.addView(button("Open"){
            val resolved=BrowserPolicy.resolveAddressOrSearch(input.text.toString())
            if(resolved==null){input.error="Enter an address";return@button}
            val ids=host.tesseraSelected()
            var skipped=0
            for(ws in host.store.spaces)for(pane in ws.panes){
                if(pane.id in ids){
                    if(pane.locked){skipped++;continue}
                    host.windowCard(pane.id)?.loadAddress(resolved)?:run{pane.url=resolved}
                }
            }
            host.persist();dialog.dismiss()
            if(skipped>0)host.notice("Skipped "+skipped+" locked panes")
        },LinearLayout.LayoutParams(-1,dp(48)))
        dialog.setContentView(outer);dialog.show()
        dialog.window?.setLayout((host.resources.displayMetrics.widthPixels*.92f).toInt(),-2)
    }
    private fun filterScreen(){
        page("Filter panes","By colour or URL"){body,footer,dialog->
            val swatches=LinearLayout(host).apply{gravity=Gravity.CENTER_VERTICAL}
            FramePalette.tesseraNames.forEachIndexed{i,name->
                val selected=name in host.store.selectedFrameColors
                swatches.addView(button("●"){
                    val colors=host.store.selectedFrameColors.toMutableSet()
                    if(!colors.add(name))colors.remove(name)
                    host.tesseraSetFilter(host.store.filterQuery,colors)
                    dialog.dismiss();filterScreen()
                }.apply{
                    setTextColor(FramePalette.tesseraColors[i])
                    background=round(surface2,22,if(selected)3 else 1,
                        if(selected)Color.WHITE else stroke)
                },LinearLayout.LayoutParams(0,dp(44),1f))
            }
            body.addView(swatches)
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
            footerButton(footer,"Clear all"){host.tesseraClearFilters();dialog.dismiss()}
            footerButton(footer,"Done"){dialog.dismiss()}
        }
    }
    private fun settingsScreen(){
        page("Settings"){body,_,dialog->
            row(body,"Keep-alive notification","✓ or Allow",icon="◉"){
                dialog.dismiss();host.requestNotificationPermissionFromSettings()
            }
            row(body,"Battery optimisation","✓ or Fix",icon="◉"){
                dialog.dismiss();host.requestBatteryExemption(true)
            }
            row(body,"App info","OnePlus background settings",icon="⚙"){
                dialog.dismiss();host.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:"+host.packageName)))
            }
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
            row(body,"How sessions work",icon="ⓘ"){dialog.dismiss();howSessions()}
            row(body,"Developer tools","Preserved SIXPANE tools",icon="⚙"){
                dialog.dismiss();host.tesseraOpenSettings()
            }
        }
    }
    private fun howSessions(){
        page("How sessions work"){body,_,_->
            listOf("Each pane is its own private browser.",
                "A login in one pane is not shared with any other pane.",
                "Ending a session or closing a workspace retires its profile.",
                "Bookmarks are just addresses.",
                "Android may pause live pages when the app is closed.",
                "Check isolation & health from All panes."
            ).forEach{
                body.addView(txt(it).apply{setPadding(dp(8),dp(14),dp(8),dp(14))})
            }
        }
    }
    fun snack(message:String){
        if(dock.windowToken==null)return
        lastSnack?.dismiss()
        val label=txt(message,13f).apply{
            setPadding(dp(14),dp(4),dp(14),dp(4))
            background=round(surface3,14,1)
            elevation=dp(8).toFloat()
        }
        val popup=PopupWindow(label,
            (host.resources.displayMetrics.widthPixels-dp(24)).coerceAtLeast(dp(200)),
            dp(48),false)
        lastSnack=popup
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.showAtLocation(dock,Gravity.BOTTOM,0,dp(66))
        dock.postDelayed({if(popup.isShowing)popup.dismiss()},3200L)
    }
}
