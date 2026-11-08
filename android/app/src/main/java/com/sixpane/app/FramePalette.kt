package com.sixpane.app
import java.util.Locale

/** Per-pane frame-color palette, search matching and any available color filtering. */
object FramePalette {
    val MAX_SELECTED_COLORS:Int get()=(names+tesseraNames).distinct().size
    val names=listOf("Graphite","Blue","Teal","Amber","Purple","Rose","Silver","Green")
    val colors=listOf(0xff51606e.toInt(),0xff82b6ff.toInt(),0xff72dbcf.toInt(),0xffe8bd76.toInt(),
        0xffb5a0ef.toInt(),0xffe89eae.toInt(),0xffc8d1da.toInt(),0xff9acb91.toInt())
    val tesseraNames=listOf("Blue","Green","Red","Gold","Olive","Teal")
    val tesseraColors=listOf(0xff204dda.toInt(),0xff59b435.toInt(),0xffb5381c.toInt(),
        0xffc39533.toInt(),0xff9a9d32.toInt(),0xff7cc5b4.toInt())
    fun textOnTag(color:Int):Int{
        val red=android.graphics.Color.red(color)/255.0
        val green=android.graphics.Color.green(color)/255.0
        val blue=android.graphics.Color.blue(color)/255.0
        fun channel(c:Double):Double=if(c<=0.04045)c/12.92
            else Math.pow((c+0.055)/1.055,2.4)
        val luminance=.2126*channel(red)+.7152*channel(green)+.0722*channel(blue)
        return if((luminance+0.05)/0.05 >= 1.05/(luminance+0.05))
            0xff0b0e13.toInt() else 0xffffffff.toInt()
    }
    fun nameFor(color:Int):String{
        val target=tesseraColors.indexOf(color)
        if(target>=0)return tesseraNames[target]
        return names.getOrElse(colors.indexOf(color)){"Graphite"}
    }
    fun typedColors(query:String):Set<String>? {
        val terms=query.split(",").map{it.trim()}.filter{it.isNotEmpty()}
        if(terms.isEmpty()||terms.size>MAX_SELECTED_COLORS)return null
        val recognized=(names+tesseraNames).distinct()
        return if(terms.all{t->recognized.any{it.equals(t,ignoreCase=true)}})
            terms.map{t->recognized.first{it.equals(t,ignoreCase=true)}}.toSet() else null
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
        selected.size<=MAX_SELECTED_COLORS&&selected.all{it in names||it in tesseraNames}
}
