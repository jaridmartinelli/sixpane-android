package com.sixpane.app
import java.util.Locale

/** Per-pane frame-color palette, search matching and six-color filtering. */
object FramePalette {
    const val MAX_SELECTED_COLORS=6
    val names=listOf("Graphite","Blue","Teal","Amber","Purple","Rose","Silver","Green")
    val colors=listOf(0xff51606e.toInt(),0xff82b6ff.toInt(),0xff72dbcf.toInt(),0xffe8bd76.toInt(),
        0xffb5a0ef.toInt(),0xffe89eae.toInt(),0xffc8d1da.toInt(),0xff9acb91.toInt())
    fun nameFor(color:Int)=names.getOrElse(colors.indexOf(color)){"Graphite"}
    fun typedColors(query:String):Set<String>? {
        val terms=query.split(",").map{it.trim()}.filter{it.isNotEmpty()}
        if(terms.isEmpty()||terms.size>MAX_SELECTED_COLORS)return null
        return if(terms.all{t->names.any{it.equals(t,ignoreCase=true)}})
            terms.map{t->names.first{it.equals(t,ignoreCase=true)}}.toSet() else null
    }
    /** Known typed color(s) override picker chips; a URL narrows the selected color chips. */
    fun matches(url:String,color:Int,query:String,selectedColors:Set<String>):Boolean {
        val typed=typedColors(query);val name=nameFor(color)
        if(typed!=null)return name in typed
        if(selectedColors.isNotEmpty()&&name !in selectedColors)return false
        val q=query.trim().lowercase(Locale.ROOT)
        if(q.isEmpty())return true
        val normalized=q.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        val actual=url.lowercase(Locale.ROOT)
        // A hostname-shaped query must not match e.g. notgoogle.com or URL parameters.
        val hostQuery=Regex("""^(?:[a-z0-9-]+\.)+[a-z0-9-]+(?::[0-9]{1,5})?(?:/.*)?$""")
            .matches(normalized)
        if(hostQuery){
            val requestedHost=normalized.substringBefore('/').substringBefore(':')
            val requestedPath=normalized.substringAfter('/',"")
            val uri=try{java.net.URI(url)}catch(_:Exception){return false}
            val actualHost=uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.")?:return false
            if(actualHost!=requestedHost&&!actualHost.endsWith("."+requestedHost))return false
            return requestedPath.isEmpty() || (uri.rawPath?:"").contains(requestedPath)
        }
        return actual.contains(q)||actual.removePrefix("https://").removePrefix("http://")
            .removePrefix("www.").contains(normalized)
    }
    fun validSelection(selected:Set<String>):Boolean=
        selected.size<=MAX_SELECTED_COLORS&&selected.all{it in names}
}
