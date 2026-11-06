package org.msrobotika.astrotab.ui

import android.content.Context
import android.graphics.*
import android.view.View
import org.msrobotika.astrotab.astro.*
import org.msrobotika.astrotab.ui.theme.*
import kotlin.math.*

/** Solo dibuja muestras ya calculadas. Sin astronomía ni acceso a red en onDraw. */
@android.annotation.SuppressLint("ViewConstructor")
class NightChart(context: Context, private val night: NightTimeline, private val altitudes: DoubleArray?,
    private val minimum: Double, private val red: Boolean, private val zone: java.util.TimeZone=java.util.TimeZone.getDefault(),
    private val now: Long=System.currentTimeMillis(), private val windows: List<TimeSpan> = emptyList()): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val palette=Palette(red)
    private val density=resources.displayMetrics.density
    private val dark=night.dark
    private val levels=listOf(0 to "0°",30 to "30°",60 to "60°",90 to "90°")
    private val axisLabels=java.text.SimpleDateFormat("HH:mm z",java.util.Locale.forLanguageTag("es-ES")).let { formatter ->
        formatter.timeZone=zone
        (0..4).map { i -> val t=night.start+(night.end-night.start)*i/4; t to formatter.format(java.util.Date(t)) }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left=46*density; val right=width-12*density; val top=32*density; val bottom=height-34*density
        fun x(t: Long)=left+(t-night.start).toFloat()/(night.end-night.start)*(right-left)
        fun y(alt: Double)=bottom-((alt+30)/120).toFloat()*(bottom-top)
        for(span in dark) {
            paint.color=palette.raised
            canvas.drawRect(x(span.start),top,x(span.end),bottom,paint)
        }
        paint.color=palette.good
        for(span in windows) canvas.drawRect(x(span.start),bottom-6*density,x(span.end),bottom,paint)
        paint.strokeWidth=density; paint.textSize=12*density; paint.color=palette.muted
        for((level,label) in levels) { canvas.drawLine(left,y(level.toDouble()),right,y(level.toDouble()),paint); canvas.drawText(label,2*density,y(level.toDouble()),paint) }
        paint.color=palette.accent
        canvas.drawText("Altura · fondo: oscuridad · tenue: Luna · barra: ventana útil",left,20*density,paint)
        canvas.save(); canvas.clipRect(left,top,right,bottom)
        for(i in 1 until night.samples.size) {
            val a=night.samples[i-1]; val b=night.samples[i]
            if(altitudes!=null) { paint.alpha=255; paint.strokeWidth=2*density; canvas.drawLine(x(a.time),y(altitudes[i-1]),x(b.time),y(altitudes[i]),paint) }
            paint.alpha=95; paint.strokeWidth=density; canvas.drawLine(x(a.time),y(a.moon),x(b.time),y(b.moon),paint)
        }
        paint.alpha=200; paint.strokeWidth=density
        if(altitudes!=null) canvas.drawLine(left,y(minimum),right,y(minimum),paint)
        canvas.restore()
        if(now in night.start..night.end) {
            paint.alpha=255; canvas.drawLine(x(now),top,x(now),bottom,paint)
            canvas.drawText("Ahora",x(now).coerceIn(left,right-45*density),top+14*density,paint)
        }
        for((t,label) in axisLabels) {
            canvas.drawText(label,(x(t)-24*density).coerceIn(left,right-75*density),height-8*density,paint)
        }
        paint.alpha=255
    }
}

@android.annotation.SuppressLint("ViewConstructor")
class PolarClock(context: Context, private val state: AstroPhoto.Polar, private val red: Boolean): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val palette=Palette(red)
    private val labels=(1..12).map { it.toString() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius=min(width,height)*0.36f; val cx=width/2f; val cy=height/2f
        paint.color=palette.accent
        paint.strokeWidth=2f; paint.style=Paint.Style.STROKE; canvas.drawCircle(cx,cy,radius,paint); paint.style=Paint.Style.FILL
        paint.textSize=18*resources.displayMetrics.density
        for(i in 1..12) {
            val angle=i*PI/6
            canvas.drawText(labels[i-1],cx+sin(angle).toFloat()*radius-9,cy-cos(angle).toFloat()*radius+7,paint)
        }
        val a=state.clockAngle*Astro.RAD
        canvas.drawCircle(cx+sin(a).toFloat()*radius*0.75f,cy-cos(a).toFloat()*radius*0.75f,7*resources.displayMetrics.density,paint)
        canvas.drawCircle(cx,cy,3f,paint)
    }
}

@android.annotation.SuppressLint("ViewConstructor")
class NightBar(context: Context, private val night: NightTimeline, private val red: Boolean, zone: java.util.TimeZone=java.util.TimeZone.getDefault()): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val palette=Palette(red)
    private val moonless=night.moonless
    private val labels=java.text.SimpleDateFormat("dd/MM HH:mm z",java.util.Locale.forLanguageTag("es-ES")).let { formatter ->
        formatter.timeZone=zone
        formatter.format(java.util.Date(night.start)) to formatter.format(java.util.Date(night.end))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d=resources.displayMetrics.density; val left=12*d; val right=width-12*d
        fun x(t: Long)=left+(t-night.start).toFloat()/(night.end-night.start)*(right-left)
        for(i in 0 until night.samples.lastIndex) {
            val a=night.samples[i]; val b=night.samples[i+1]
            paint.color=palette.twilight(a.sun)
            canvas.drawRect(x(a.time),24*d,x(b.time)+1,72*d,paint)
        }
        paint.color=palette.accent
        for(span in moonless) canvas.drawRect(x(span.start),78*d,x(span.end),90*d,paint)
        paint.textSize=14*d
        canvas.drawText("Día · dorada · azul · civil · náutico · astronómico / línea: sin Luna",left,17*d,paint)
        canvas.drawText(labels.first,left,112*d,paint)
        canvas.drawText(labels.second,right-paint.measureText(labels.second),112*d,paint)
    }
}
