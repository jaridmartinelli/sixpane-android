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
    val heightPercent: Int = randomDimension()
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
            j.optInt("widthPercent", 100), j.optInt("heightPercent", 100)
        )
    }
    fun json(): JSONObject = JSONObject().apply {
        put("id",id); put("profile",profile); put("url",url)
        put("locked",locked); put("desktop",desktop); put("zoom",zoom)
        put("widthPercent",widthPercent); put("heightPercent",heightPercent)
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
    var currentId:String=""
    var columns:Int=2
    var focusId:String?=null
    init {
        try {
            val j=JSONObject(prefs.getString("manifest", "{}")!!)
            val list=j.optJSONArray("spaces")?:JSONArray()
            for(i in 0 until list.length())spaces.add(WorkspaceState.from(list.getJSONObject(i)))
            val pending=j.optJSONArray("pending")?:JSONArray()
            for(i in 0 until pending.length())pendingDeletion.add(pending.getString(i))
            currentId=j.optString("current", "")
            columns=if(j.optInt("columns",2)==3)3 else 2
            focusId=j.optString("focus", "").takeIf { it.isNotBlank() }
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
            put("current",currentId);put("columns",columns);put("focus",focusId?:"")
        }
        check(prefs.edit().putString("manifest",j.toString()).commit()) { "Could not durably save workspace manifest" }
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
