package com.sixpane.app

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.min

/** Transparent 70%-white icons; icon size 11-13dp inside a 32dp touch target. */
class CornerIcon(context:Context, val kind:Kind):View(context){
    enum class Kind { COOKIE, EXPAND, LOCK }
    var locked=false
        set(v){field=v; invalidate()}
    var expanded=false
        set(v){field=v; invalidate()}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND;strokeWidth=2f}
    init { setLayerType(LAYER_TYPE_SOFTWARE,null); isClickable=true;isFocusable=true }
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas)
        val d=resources.displayMetrics.density
        val side=if(kind==Kind.COOKIE)11f*d else 13f*d
        val centerX=width/2f; val centerY=height/2f
        val scale=min(side/24f, min(width,height)/26f)
        val count=canvas.save()
        canvas.translate(centerX-12*scale,centerY-12*scale);canvas.scale(scale,scale)
        paint.color=if(kind==Kind.LOCK&&locked)Color.rgb(255,79,98) else Color.argb(179,255,255,255)
        paint.setShadowLayer(if(kind==Kind.LOCK&&locked)5f else 0f,0f,0f,Color.RED)
        paint.style=Paint.Style.STROKE
        when(kind){
            Kind.LOCK->{
                canvas.drawRoundRect(5f,10f,19f,21f,2f,2f,paint)
                val path=Path().apply{moveTo(8f,10f);lineTo(8f,7f);cubicTo(8f,1f,16f,1f,16f,7f);lineTo(16f,10f)}
                if(!locked){path.reset();path.moveTo(8f,10f);lineTo(8f,7f);cubicTo(8f,1f,15f,1f,16.5f,5f)}
                canvas.drawPath(path,paint)
            }
            Kind.EXPAND->{
                val v=if(expanded) floatArrayOf(3f,9f,9f,9f,9f,3f,15f,21f,15f,15f,21f,15f)
                      else floatArrayOf(9f,3f,3f,3f,3f,9f,15f,21f,21f,21f,21f,15f)
                canvas.drawLine(v[0],v[1],v[2],v[3],paint);canvas.drawLine(v[2],v[3],v[4],v[5],paint)
                canvas.drawLine(v[6],v[7],v[8],v[9],paint);canvas.drawLine(v[8],v[9],v[10],v[11],paint)
                canvas.drawLine(if(expanded)3f else 3f,if(expanded)3f else 3f,if(expanded)9f else 10f,if(expanded)9f else 10f,paint)
                canvas.drawLine(if(expanded)21f else 21f,if(expanded)21f else 21f,if(expanded)15f else 14f,if(expanded)15f else 14f,paint)
            }
            Kind.COOKIE->{
                val layer=canvas.saveLayer(0f,0f,24f,24f,null)
                paint.style=Paint.Style.FILL
                val cookie=Path().apply{moveTo(12f,2f);cubicTo(13.5f,2f,14.7f,2.3f,15.9f,2.9f);cubicTo(15.2f,5.2f,17f,7.8f,20.9f,6.7f);cubicTo(25f,16f,18f,22f,12f,22f);cubicTo(6.5f,22f,2f,17.5f,2f,12f);cubicTo(2f,6.5f,6.5f,2f,12f,2f);close()}
                canvas.drawPath(cookie,paint)
                paint.clearShadowLayer();paint.color=Color.BLACK
                paint.xfermode=PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                listOf(floatArrayOf(8f,8f,1.4f),floatArrayOf(14f,12f,1.5f),floatArrayOf(8f,16f,1.2f),floatArrayOf(15f,18f,1.1f)).forEach{canvas.drawCircle(it[0],it[1],it[2],paint)}
                paint.xfermode=null;canvas.restoreToCount(layer)
            }
        }
        paint.clearShadowLayer()
        canvas.restoreToCount(count)
    }
    fun pressedFeedback(){animate().scaleX(.76f).scaleY(.76f).alpha(1f).setDuration(90).withEndAction{animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(180).start()}.start()}
    fun blockedFeedback(){
        if(kind!=Kind.LOCK)return
        ObjectAnimator.ofFloat(this,"translationX",0f,-3f,3f,-2f,2f,0f).apply{duration=400}.start()
    }
}
