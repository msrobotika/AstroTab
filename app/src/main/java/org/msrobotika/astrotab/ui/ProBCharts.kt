package org.msrobotika.astrotab.ui

import android.content.Context
import android.graphics.*
import android.view.View
import org.msrobotika.astrotab.satellites.*
import org.msrobotika.astrotab.astro.*
import org.msrobotika.astrotab.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

@android.annotation.SuppressLint("ViewConstructor")
class PassPolar(context: Context,private val pass: SatellitePass,red: Boolean,private val mini: Boolean=false): View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG);private val palette=Palette(red);private val path=Path();private val arrow=Path();private val circles=intArrayOf(0,30,60)
    init {contentDescription="Paso polar ${pass.tle.name}. Norte arriba, este a la derecha; centro cenit. Inicio a fin con flecha."}
    override fun onDraw(c: Canvas) {
        c.drawColor(palette.surface);val cx=width/2f;val cy=height/2f;val radius=min(width,height)*0.39f
        p.style=Paint.Style.STROKE;p.strokeWidth=1f;p.color=palette.muted
        for(alt in circles)c.drawCircle(cx,cy,radius*(90-alt)/90,p)
        c.drawLine(cx-radius,cy,cx+radius,cy,p);c.drawLine(cx,cy-radius,cx,cy+radius,p)
        fun x(q: SatellitePoint)=cx+(sin(q.azimuth*Astro.RAD)*(90-q.altitude.coerceIn(0.0,90.0))/90*radius).toFloat()
        fun y(q: SatellitePoint)=cy-(cos(q.azimuth*Astro.RAD)*(90-q.altitude.coerceIn(0.0,90.0))/90*radius).toFloat()
        p.strokeWidth=if(mini)2f else 3f
        for((a,b) in pass.track.zipWithNext()){p.color=if(a.visible && b.visible)palette.accent else palette.muted;p.alpha=if(a.visible && b.visible)255 else 100;c.drawLine(x(a),y(a),x(b),y(b),p)}
        p.alpha=255;p.color=palette.accent;p.style=Paint.Style.FILL;p.textSize=(if(mini)10 else 14)*resources.displayMetrics.density
        val pad=if(mini)3f else 5f
        c.drawText("N",cx-p.measureText("N")/2,cy-radius-pad,p);c.drawText("S",cx-p.measureText("S")/2,cy+radius+p.textSize+pad,p)
        c.drawText("E",cx+radius+pad,cy+p.textSize/3,p);c.drawText("O",cx-radius-pad-p.measureText("O"),cy+p.textSize/3,p)
        c.drawCircle(x(pass.start),y(pass.start),if(mini)3f else 5f,p)
        if(!mini)c.drawText("Inicio",(x(pass.start)+8).coerceIn(8f,width-p.measureText("Inicio")-8), (y(pass.start)-8).coerceIn(20f,height-20f),p)
        val last=pass.end;val prior=pass.track.getOrNull(pass.track.lastIndex-1)
        if(prior!=null){val angle=atan2(y(last)-y(prior),x(last)-x(prior));arrow.rewind();arrow.moveTo(x(last),y(last));arrow.lineTo(x(last)-12*cos(angle-0.5f),y(last)-12*sin(angle-0.5f));arrow.lineTo(x(last)-12*cos(angle+0.5f),y(last)-12*sin(angle+0.5f));arrow.close();c.drawPath(arrow,p)}
        if(!mini)c.drawText("Fin",(x(last)+8).coerceIn(8f,width-p.measureText("Fin")-8),(y(last)+18).coerceIn(20f,height-8f),p)
    }
}

@android.annotation.SuppressLint("ViewConstructor")
class SessionTimeline(context: Context,private val night: NightTimeline,private val plan: ObservingPlan,red: Boolean,zone: TimeZone): View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG);private val palette=Palette(red)
    private val fmt=SimpleDateFormat("HH:mm",Locale.ROOT).apply {timeZone=zone}
    private val start=night.dark.firstOrNull()?.start ?: night.start
    private val end=night.dark.lastOrNull()?.end ?: night.end
    private val startLabel=fmt.format(Date(start));private val endLabel=fmt.format(Date(end))
    init {contentDescription="Barra de oscuridad astronómica $startLabel → $endLabel con ${plan.blocks.size} bloques de objetos. ${plan.blocks.joinToString {it.id+" "+fmt.format(Date(it.start))+" → "+fmt.format(Date(it.end))}}"}
    override fun onDraw(c: Canvas) {
        c.drawColor(palette.surface);val margin=12f;val span=(end-start).coerceAtLeast(1)
        fun x(t: Long)=margin+(width-2*margin)*(t-start).toFloat()/span
        p.color=palette.raised;c.drawRect(margin,36f,width-margin,height-24f,p)
        for(w in night.dark){p.color=palette.background;c.drawRect(x(w.start),36f,x(w.end),height-24f,p)}
        p.textSize=14*resources.displayMetrics.density;p.color=palette.muted
        c.drawText(startLabel,margin,22f,p);c.drawText(endLabel,width-margin-50,22f,p)
        for((i,b) in plan.blocks.withIndex()){p.color=palette.series[i%palette.series.size];p.alpha=if(palette.red)255-(i%4)*40 else 255;c.drawRect(x(b.start),42f,x(b.end),height-28f,p);p.alpha=255;p.color=palette.text
            // Etiquetas fuera de bloques estrechos; alternar dos niveles evita colisiones.
            c.drawText(b.id,(x(b.start)+3).coerceAtMost(width-p.measureText(b.id)-8),if(i%2==0) height-8f else 20f,p)
            if(b.warnings.any {it.contains("meridiano")}){p.color=palette.warning;p.strokeWidth=3f;c.drawLine(x((b.start+b.end)/2),36f,x((b.start+b.end)/2),height-24f,p)}
        }
    }
}
