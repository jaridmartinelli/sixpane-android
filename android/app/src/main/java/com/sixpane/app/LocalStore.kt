package com.sixpane.app

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID

enum class DeploymentMode { EDGE, SERVER_CONNECTOR, CLIENT_JS, SIMPLE_LINK, UNKNOWN }
enum class SessionPhase { CREATED, ACTIVE, CLEARANCE_OBSERVED, RETIRED }
enum class CookieLifetime { FIXED, EXTENDABLE, UNKNOWN }

/** Only a WebView-observable response can provide cookie attributes. Never store its value twice. */
data class CookieScopeEvidence(
    val name:String, val domain:String?, val hostOnly:Boolean,
    val path:String, val secure:Boolean, val httpOnly:Boolean,
    val sameSite:String?, val deadlineMs:Long?, val source:String,
    val classification:CookieLifetime, val expiresSpecified:Boolean
) {
    fun json():JSONObject=JSONObject().apply{
        put("name",name);put("domain",domain?:JSONObject.NULL);put("hostOnly",hostOnly)
        put("path",path);put("secure",secure);put("httpOnly",httpOnly)
        put("sameSite",sameSite?:JSONObject.NULL);put("deadlineMs",deadlineMs?:JSONObject.NULL)
        put("source",source);put("classification",classification.name)
        put("expiresSpecified",expiresSpecified)
    }
    companion object {
        fun from(j:JSONObject)=CookieScopeEvidence(
            j.getString("name"),j.optString("domain").takeUnless{it=="null"||it.isBlank()},
            j.optBoolean("hostOnly",true),j.getString("path"),
            j.optBoolean("secure"),j.optBoolean("httpOnly"),
            j.optString("sameSite").takeUnless{it=="null"||it.isBlank()},
            if(j.isNull("deadlineMs"))null else j.optLong("deadlineMs"),
            j.optString("source"),CookieLifetime.valueOf(j.optString("classification","UNKNOWN")),
            j.optBoolean("expiresSpecified")
        )
    }
}

/** Small session manifest; cookie databases belong exclusively to WebKit named profiles. */
data class PaneState(
    val id: String = "pane-" + UUID.randomUUID().toString(),
    val profile: String = "sixpane-" + UUID.randomUUID().toString(),
    var url: String = "about:blank",
    var locked: Boolean = false,
    var desktop: Boolean = false,
    var zoom: Int = 100,
    var widthPercent: Int = randomDimension(),
    var heightPercent: Int = randomDimension(),
    val bookmarks:MutableList<String> = mutableListOf(),
    var deploymentMode:DeploymentMode = DeploymentMode.UNKNOWN,
    var deploymentLogged:Boolean = false,
    var assetRegionHint:String? = null,
    var assetRegionEvidence:String? = null,
    val sessionCreatedAt:Long = System.currentTimeMillis(),
    var phase:SessionPhase = SessionPhase.CREATED,
    var clearanceObservedAt:Long? = null,
    var retiredAt:Long? = null,
    var waitingRoomEntryPath:String? = null,
    var entryPathEvidence:String? = null,
    val cookieScopes:MutableList<CookieScopeEvidence> = mutableListOf()
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
            },
            runCatching{DeploymentMode.valueOf(j.optString("deploymentMode","UNKNOWN"))}.getOrDefault(DeploymentMode.UNKNOWN),
            j.optBoolean("deploymentLogged",false),
            j.optString("assetRegionHint").takeUnless{it=="null"||it.isBlank()},
            j.optString("assetRegionEvidence").takeUnless{it=="null"||it.isBlank()},
            j.optLong("sessionCreatedAt",System.currentTimeMillis()),
            runCatching{SessionPhase.valueOf(j.optString("phase","CREATED"))}.getOrDefault(SessionPhase.CREATED),
            if(j.isNull("clearanceObservedAt"))null else j.optLong("clearanceObservedAt"),
            if(j.isNull("retiredAt"))null else j.optLong("retiredAt"),
            j.optString("waitingRoomEntryPath").takeUnless{it=="null"||it.isBlank()},
            j.optString("entryPathEvidence").takeUnless{it=="null"||it.isBlank()},
            mutableListOf<CookieScopeEvidence>().apply{
                val evidence=j.optJSONArray("cookieScopes")?:JSONArray()
                for(i in 0 until evidence.length())add(CookieScopeEvidence.from(evidence.getJSONObject(i)))
            }
        )
    }
    fun json(): JSONObject = JSONObject().apply {
        put("id",id); put("profile",profile); put("url",url)
        put("locked",locked); put("desktop",desktop); put("zoom",zoom)
        put("widthPercent",widthPercent); put("heightPercent",heightPercent)
        put("paneBookmarks",JSONArray().apply{bookmarks.forEach{put(it)}})
        put("deploymentMode",deploymentMode.name);put("deploymentLogged",deploymentLogged)
        put("assetRegionHint",assetRegionHint?:JSONObject.NULL)
        put("assetRegionEvidence",assetRegionEvidence?:JSONObject.NULL)
        put("sessionCreatedAt",sessionCreatedAt);put("phase",phase.name)
        put("clearanceObservedAt",clearanceObservedAt?:JSONObject.NULL)
        put("retiredAt",retiredAt?:JSONObject.NULL)
        put("waitingRoomEntryPath",waitingRoomEntryPath?:JSONObject.NULL)
        put("entryPathEvidence",entryPathEvidence?:JSONObject.NULL)
        put("cookieScopes",JSONArray().apply{cookieScopes.forEach{put(it.json())}})
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
    val retiredSessions:MutableList<PaneState> = mutableListOf()
    var currentId:String=""
    var columns:Int=2
    var focusId:String?=null
    var activePaneId:String?=null
    var gridPreference:String="automatic"
    var fullSizeSmallPanes:Boolean=true
    var filterQuery:String=""
    val selectedFrameColors:MutableSet<String> = linkedSetOf()
    // Only app chrome, never a webpage or browser profile.
    var lightMode:Boolean
        get()=prefs.getBoolean("light-chrome",false)
        set(value){check(prefs.edit().putBoolean("light-chrome",value).commit())}
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
            val retired=j.optJSONArray("retiredSessions")?:JSONArray()
            for(i in 0 until retired.length())retiredSessions.add(PaneState.from(retired.getJSONObject(i)))
            val pending=j.optJSONArray("pending")?:JSONArray()
            for(i in 0 until pending.length())pendingDeletion.add(pending.getString(i))
            val savedBookmarks=j.optJSONArray("bookmarks")?:JSONArray()
            for(i in 0 until savedBookmarks.length())bookmarks.add(savedBookmarks.getString(i))
            currentId=j.optString("current", "")
            columns=if(j.optInt("columns",2)==3)3 else 2
            focusId=j.optString("focus", "").takeIf { it.isNotBlank() }
            activePaneId=j.optString("activePaneId", "").takeIf{it.isNotBlank()}
            gridPreference=j.optString("gridPreference","automatic").takeIf{
                it in listOf("automatic","2x3","3x2")
            }?:"automatic"
            fullSizeSmallPanes=j.optBoolean("fullSizeSmallPanes",true)
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
        // Repair legacy viewport ratio duplicates without changing session/profile IDs.
        allocateViewportRatios(spaces.flatMap{it.panes})
        if(spaces.none { it.id==currentId }) currentId=spaces.first().id
        if(spaces.flatMap{it.panes}.none{it.id==activePaneId})
            activePaneId=spaces.first{it.id==currentId}.panes.firstOrNull()?.id
        save()
    }
    /** Allocates proportional viewport ratios across every existing workspace. */
    private fun allocateViewportRatios(panes:List<PaneState>){
        val ids=panes.map{it.id}.toSet()
        require(ids.size==panes.size)
        val reserved=spaces.flatMap{it.panes}.filterNot{it.id in ids}
            .map{it.widthPercent to it.heightPercent}
        val assigned=ViewportRatioPolicy.allocate(reserved,
            panes.map{it.widthPercent to it.heightPercent})
        panes.zip(assigned).forEach{(pane,size)->
            pane.widthPercent=size.first;pane.heightPercent=size.second
        }
    }
    /** Prepare replacements before retiring their sessions; preserve surviving panes. */
    private fun newUniquePanes(retiring:Set<String>):Iterator<PaneState>{
        val live=spaces.flatMap{it.panes}
        require(live.count{it.id in retiring}==retiring.size)
        val fresh=List(retiring.size){PaneState()}
        val reserved=live.filterNot{it.id in retiring}
            .map{it.widthPercent to it.heightPercent}
        val assigned=ViewportRatioPolicy.allocate(reserved,
            fresh.map{it.widthPercent to it.heightPercent})
        fresh.zip(assigned).forEach{(pane,size)->
            pane.widthPercent=size.first;pane.heightPercent=size.second
        }
        return fresh.iterator()
    }
    fun active():WorkspaceState=spaces.first { it.id==currentId }
    fun save(){
        check(ViewportRatioPolicy.allUnique(spaces.flatMap{it.panes}
            .map{it.widthPercent to it.heightPercent})){
            "Duplicate viewport aspect ratios across SIXPANE workspaces"
        }
        // One durable deployment-mode observation per profile, including
        // profiles created while another workspace is active.
        spaces.flatMap{it.panes}.filterNot{it.deploymentLogged}.forEach {
            Log.i("SIXPANE_R1","profile="+it.profile+" deploymentMode="+it.deploymentMode.name)
            it.deploymentLogged=true
        }
        val j=JSONObject().apply{
            put("spaces",JSONArray().apply{spaces.forEach{put(it.json())}})
            put("retiredSessions",JSONArray().apply{retiredSessions.forEach{put(it.json())}})
            put("pending",JSONArray().apply{pendingDeletion.forEach{put(it)}})
            put("bookmarks",JSONArray().apply{bookmarks.forEach{put(it)}})
            put("current",currentId);put("columns",columns);put("focus",focusId?:"")
            put("activePaneId",activePaneId?:"");put("gridPreference",gridPreference)
            put("fullSizeSmallPanes",fullSizeSmallPanes)
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
        val fresh=newUniquePanes(ids)
        val result=mutableListOf<Pair<PaneState,PaneState>>()
        current.panes.forEachIndexed{index,old->
            if(old.id in ids){
                val replacement=fresh.next()
                old.phase=SessionPhase.RETIRED;old.retiredAt=System.currentTimeMillis()
                retiredSessions.add(old)
                pendingDeletion.add(old.profile)
                current.panes[index]=replacement
                result.add(old to replacement)
            }
        }
        if(focusId in ids)focusId=null
        if(activePaneId in ids)activePaneId=result.firstOrNull{it.first.id==activePaneId}?.second?.id
        save()
        return result
    }
    /**
     * Close panes selected from a temporary filter page, even when they belong
     * to different original workspaces. Preserve all other profile identities.
     * Never mutate workspace membership merely to display a filtered page.
     */
    fun replacePanesAcrossWorkspaces(ids:Set<String>):List<Pair<PaneState,PaneState>>{
        val all=spaces.flatMap{it.panes}
        require(ids.isNotEmpty()&&all.count{it.id in ids}==ids.size) {
            "Every selected pane must still exist"
        }
        require(all.none{it.id in ids&&it.locked}){"Locked browsers must be unlocked first"}
        require(spaces.none{it.locked&&it.panes.any{p->p.id in ids}}) {
            "Unlock the original workspace tab before replacing a browser"
        }
        val fresh=newUniquePanes(ids)
        val replacements=mutableListOf<Pair<PaneState,PaneState>>()
        spaces.forEach{space->
            space.panes.forEachIndexed{index,old->
                if(old.id in ids){
                    val replacement=fresh.next()
                    old.phase=SessionPhase.RETIRED;old.retiredAt=System.currentTimeMillis()
                    retiredSessions.add(old)
                    pendingDeletion.add(old.profile)
                    space.panes[index]=replacement
                    replacements.add(old to replacement)
                }
            }
        }
        if(focusId in ids)focusId=null
        if(activePaneId in ids)activePaneId=replacements.firstOrNull{it.first.id==activePaneId}?.second?.id
        save()
        return replacements
    }

    fun create():WorkspaceState {
        require(WorkspacePolicy.canCreate(spaces.size))
        val w=WorkspaceState();spaces.add(w)
        allocateViewportRatios(w.panes)
        currentId=w.id;focusId=null;activePaneId=w.panes.firstOrNull()?.id;save();return w
    }
    /** Tombstone profile identifiers first, before any attempt at physical deletion. */
    fun close(id:String):WorkspaceState {
        val w=spaces.first{it.id==id}
        require(WorkspacePolicy.canCloseWorkspace(w.locked,w.panes.map{it.locked})) {
            "Unlock workspace tab and all its browsers before closing"
        }
        w.panes.forEach {
            it.phase=SessionPhase.RETIRED;it.retiredAt=System.currentTimeMillis()
            retiredSessions.add(it);pendingDeletion.add(it.profile)
        }
        spaces.remove(w)
        if(spaces.isEmpty()){
            val fresh=WorkspaceState();spaces.add(fresh)
            allocateViewportRatios(fresh.panes) // never reuse retired identities
        }
        currentId=spaces.last().id;focusId=null
        activePaneId=spaces.last().panes.firstOrNull()?.id;save()
        return w
    }
}
