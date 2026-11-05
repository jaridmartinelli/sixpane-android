package com.sixpane.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID

/** Small session manifest; cookie databases belong exclusively to WebKit named profiles. */
data class PaneState(
    val id: String = "pane-" + UUID.randomUUID().toString(),
    val profile: String = "sixpane-" + UUID.randomUUID().toString(),
    var url: String = "about:blank",
    var locked: Boolean = false,
    var desktop: Boolean = false,
    var zoom: Int = 100,
    val widthPercent: Int = randomDimension(),
    val heightPercent: Int = randomDimension(),
    val bookmarks:MutableList<String> = mutableListOf()
) {
    companion object {
        private val random = SecureRandom()
        private val variations = intArrayOf(-4,-3,-2,2,3,4)
        fun randomDimension(): Int = 100 + variations[random.nextInt(variations.size)]
        fun distinctSix(): MutableList<PaneState> {
            fun shuffled(): List<Int> = variations.toMutableList().also { values ->
                for(i in values.lastIndex downTo 1){val j=random.nextInt(i+1);val t=values[i];values[i]=values[j];values[j]=t}
            }
            val widths=shuffled()
            var heights:List<Int>
            do {heights=shuffled()} while((0 until 6).map{widths[it].toDouble()/heights[it]}.distinct().size < 6)
            return MutableList(6){ PaneState(widthPercent=100+widths[it],heightPercent=100+heights[it]) }
        }
        fun from(j: JSONObject): PaneState = PaneState(
            j.getString("id"), j.getString("profile"), j.optString("url", "about:blank"),
            j.optBoolean("locked"), j.optBoolean("desktop"), j.optInt("zoom", 100),
            j.optInt("widthPercent", 100), j.optInt("heightPercent", 100),
            mutableListOf<String>().apply{
                val saved=j.optJSONArray("paneBookmarks")?:JSONArray()
                for(i in 0 until saved.length())add(saved.getString(i))
            }
        )
    }
    fun json(): JSONObject = JSONObject().apply {
        put("id",id); put("profile",profile); put("url",url)
        put("locked",locked); put("desktop",desktop); put("zoom",zoom)
        put("widthPercent",widthPercent); put("heightPercent",heightPercent)
        put("paneBookmarks",JSONArray().apply{bookmarks.forEach{put(it)}})
    }
}
data class WorkspaceState(
    val id: String = "ws-" + UUID.randomUUID().toString(),
    var locked: Boolean = false,
    val panes: MutableList<PaneState> = PaneState.distinctSix()
) {
    companion object {
        fun from(j: JSONObject): WorkspaceState = WorkspaceState(
            j.getString("id"),j.optBoolean("locked"),
            mutableListOf<PaneState>().apply {
                val a=j.getJSONArray("panes")
                for(i in 0 until a.length()) add(PaneState.from(a.getJSONObject(i)))
            }
        )
    }
    fun json():JSONObject=JSONObject().apply {
        put("id",id);put("locked",locked);put("panes",JSONArray().apply { panes.forEach { put(it.json()) } })
    }
}

class LocalStore(ctx:Context){
    private val prefs=ctx.getSharedPreferences("sixpane-local-v2",Context.MODE_PRIVATE)
    val spaces:MutableList<WorkspaceState> = mutableListOf()
    val pendingDeletion:MutableSet<String> = linkedSetOf()
    val bookmarks:MutableSet<String> = linkedSetOf()
    var currentId:String=""
    var columns:Int=2
    var focusId:String?=null
    var filterQuery:String=""
    val selectedFrameColors:MutableSet<String> = linkedSetOf()
    // Separate preference: the established workspace manifest format stays unchanged.
    var desktopUserAgent:Boolean
        get()=prefs.getBoolean("desktop-user-agent",true)
        set(value){check(prefs.edit().putBoolean("desktop-user-agent",value).commit())}
    fun frameColor(id:String):Int=prefs.getInt("frame-color:$id",0xff51606e.toInt())
    fun setFrameColor(ids:Set<String>,color:Int){
        val live=spaces.flatMap{it.panes}.map{it.id}.toSet()
        require(ids.isNotEmpty()&&ids.all{it in live})
        val edit=prefs.edit()
        ids.forEach{edit.putInt("frame-color:$it",color)}
        check(edit.commit()){"Could not save window frame color"}
    }
    init {
        try {
            val j=JSONObject(prefs.getString("manifest", "{}")!!)
            val list=j.optJSONArray("spaces")?:JSONArray()
            for(i in 0 until list.length())spaces.add(WorkspaceState.from(list.getJSONObject(i)))
            val pending=j.optJSONArray("pending")?:JSONArray()
            for(i in 0 until pending.length())pendingDeletion.add(pending.getString(i))
            val savedBookmarks=j.optJSONArray("bookmarks")?:JSONArray()
            for(i in 0 until savedBookmarks.length())bookmarks.add(savedBookmarks.getString(i))
            currentId=j.optString("current", "")
            columns=if(j.optInt("columns",2)==3)3 else 2
            focusId=j.optString("focus", "").takeIf { it.isNotBlank() }
            filterQuery=j.optString("filterQuery", "")
            val filters=j.optJSONArray("selectedFrameColors")?:JSONArray()
            for(i in 0 until filters.length()){
                val candidate=filters.getString(i)
                if(candidate in FramePalette.names&&selectedFrameColors.size<FramePalette.MAX_SELECTED_COLORS)
                    selectedFrameColors.add(candidate)
            }
        } catch (_:Exception){
            // Never overwrite an unreadable manifest with new profiles: possible data loss.
            throw IllegalStateException("SIXPANE session manifest unreadable. Back up app data before repair.")
        }
        if (spaces.any { it.panes.size != WorkspacePolicy.PANE_COUNT } || spaces.size > WorkspacePolicy.MAX_WORKSPACES) {
            throw IllegalStateException("Invalid workspace manifest; refusing to discard existing profiles.")
        }
        if(spaces.isEmpty()) spaces.add(WorkspaceState())
        if(spaces.none { it.id==currentId }) currentId=spaces.first().id
        save()
    }
    fun active():WorkspaceState=spaces.first { it.id==currentId }
    fun save(){
        val j=JSONObject().apply{
            put("spaces",JSONArray().apply{spaces.forEach{put(it.json())}})
            put("pending",JSONArray().apply{pendingDeletion.forEach{put(it)}})
            put("bookmarks",JSONArray().apply{bookmarks.forEach{put(it)}})
            put("current",currentId);put("columns",columns);put("focus",focusId?:"")
            put("filterQuery",filterQuery)
            put("selectedFrameColors",JSONArray().apply{selectedFrameColors.forEach{put(it)}})
        }
        check(prefs.edit().putString("manifest",j.toString()).commit()) { "Could not durably save workspace manifest" }
    }
    fun bookmark(url:String){
        if(url.startsWith("http://")||url.startsWith("https://")){
            bookmarks.add(url);save()
        }
    }
    fun bookmarkForPane(id:String,url:String){
        if(!url.startsWith("http://")&&!url.startsWith("https://"))return
        val pane=spaces.flatMap{it.panes}.firstOrNull{it.id==id}?:return
        if(url !in pane.bookmarks){pane.bookmarks.add(url);save()}
    }
    fun toggleBookmarkForPane(id:String,url:String):Boolean? {
        if(!url.startsWith("http://")&&!url.startsWith("https://"))return null
        val pane=spaces.flatMap{it.panes}.firstOrNull{it.id==id}?:return null
        val added=if(url in pane.bookmarks){pane.bookmarks.remove(url);false}
            else{pane.bookmarks.add(url);true}
        save();return added
    }
    fun bookmarkedInPane(id:String,url:String):Boolean =
        spaces.flatMap{it.panes}.firstOrNull{it.id==id}?.bookmarks?.contains(url)==true
    fun bookmarksForPane(id:String):List<String> =
        spaces.flatMap{it.panes}.firstOrNull{it.id==id}?.bookmarks?.toList()?:emptyList()
    /** Close selected individual windows and create new blank profiles in the same six slots. */
    fun replacePanes(ids:Set<String>):List<Pair<PaneState,PaneState>> {
        val current=active()
        require(ids.isNotEmpty() && ids.all{id->current.panes.any{it.id==id}})
        require(current.panes.none{it.id in ids && it.locked}) {"Locked windows must be unlocked first"}
        val result=mutableListOf<Pair<PaneState,PaneState>>()
        current.panes.forEachIndexed{index,old->
            if(old.id in ids){
                pendingDeletion.add(old.profile)
                val replacement=PaneState()
                current.panes[index]=replacement
                result.add(old to replacement)
            }
        }
        if(focusId in ids)focusId=null
        save()
        return result
    }
    fun create():WorkspaceState {
        require(WorkspacePolicy.canCreate(spaces.size))
        val w=WorkspaceState();spaces.add(w);currentId=w.id;focusId=null;save();return w
    }
    /** Tombstone profile identifiers first, before any attempt at physical deletion. */
    fun close(id:String):WorkspaceState {
        val w=spaces.first{it.id==id};require(!w.locked)
        w.panes.forEach { pendingDeletion.add(it.profile) }
        spaces.remove(w)
        if(spaces.isEmpty())spaces.add(WorkspaceState()) // new six, never reusing retired identities
        currentId=spaces.last().id;focusId=null;save()
        return w
    }
}
