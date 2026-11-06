package org.msrobotika.astrotab.ui

import android.content.Context
import android.graphics.*
import android.view.*
import org.msrobotika.astrotab.astro.*
import org.msrobotika.astrotab.data.*
import org.msrobotika.astrotab.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

data class PlotSeries(val name: String, val values: DoubleArray, val dashed: Boolean=false)
data class PlotData(val title: String, val times: List<Long>, val series: List<PlotSeries>, val minimum: Double, val maximum: Double,
    val labels: List<String>, val cursor: List<String> = emptyList(), val dark: List<TimeSpan> = emptyList(),
    val moon: List<TimeSpan> = emptyList(), val risk: List<TimeSpan> = emptyList(), val stacked: Boolean=false, val threshold: Double?=null,
    val emptyMessage: String="sin datos") {
    val hasData: Boolean get()=times.isNotEmpty() && series.any { s -> s.values.any { it.isFinite() } }
    companion object {
        fun labels(times: List<Long>, zone: TimeZone): List<String> {
            val f=SimpleDateFormat("dd/MM HH:mm z",Locale.forLanguageTag("es-ES")).apply { timeZone=zone }
            return times.map { f.format(Date(it)) }
        }
        fun altitudes(n: NightTimeline, planets: List<PlanetNight>, zone: TimeZone): PlotData {
            val times=n.samples.map { it.time }
            return PlotData("Altura ° · fondo oscuro: noche astronómica",times,listOf(PlotSeries("Luna",n.samples.map { it.moon }.toDoubleArray()))+planets.map { PlotSeries(it.name,it.altitudes) },-30.0,90.0,labels(times,zone),dark=n.dark)
        }
        fun kp(samples: List<KpSample>, threshold: Double, zone: TimeZone): PlotData {
            val times=samples.map { it.time }.distinct().sorted()
            val series=KpKind.entries.map { kind ->
                val byTime=samples.filter { it.kind==kind }.associateBy { it.time }
                PlotSeries(when(kind) { KpKind.OBSERVED -> "Observado"; KpKind.ESTIMATED -> "Estimado"; else -> "Previsto" },times.map { byTime[it]?.value ?: Double.NaN }.toDoubleArray(),kind!=KpKind.OBSERVED)
            }
            return PlotData("Kp · últimas 72 h y previsión · línea: umbral local",times,series,0.0,max(9.0,threshold),labels(times,zone),threshold=threshold.coerceAtLeast(0.0))
        }
    }
}

/** Datos y textos preparados fuera de onDraw. Paths recalculados solo al cambiar tamaño. */
@android.annotation.SuppressLint("ViewConstructor")
class SeriesChart(context: Context, val data: PlotData, private val red: Boolean, private val onCursor: ((String)->Unit)?=null): View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val d=resources.displayMetrics.density
    private val dash=DashPathEffect(floatArrayOf(7*d,5*d),0f)
    private val palette=Palette(red)
    private val colors=palette.series
    private val paths=Array(data.series.size) { Path() }
    private val gridLabels=(0..4).map { DisplayFormat.number(data.minimum+(data.maximum-data.minimum)*it/4) }
    private var cursorIndex=-1
    private var downX=0f
    private var downY=0f
    private var moved=false
    private var cancelled=false
    private val slop=ViewConfiguration.get(context).scaledTouchSlop
    private val first=data.times.firstOrNull() ?: 0L
    private val duration=((data.times.lastOrNull() ?: HOUR)-first).coerceAtLeast(1)
    private fun x(t: Long)=42*d+(t-first).toFloat()/duration*(width-54*d)
    private fun y(v: Double)=height-42*d-((v-data.minimum)/(data.maximum-data.minimum)).toFloat()*(height-122*d)
    init { minimumHeight=(260*d).toInt(); contentDescription=data.title+if(data.hasData) ". Toca o arrastra para consultar valores." else ". ${data.emptyMessage}"; isClickable=data.hasData }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        if(!data.hasData) return
        for(s in data.series.indices) {
            val path=paths[s]; path.reset(); val values=data.series[s].values
            if(data.stacked) {
                // Polígonos independientes por hora: los huecos nunca se unen como datos conocidos.
                for(i in 0 until data.times.lastIndex) {
                    if(data.series.any { !it.values[i].isFinite() || !it.values[i+1].isFinite() }) continue
                    val a=(0 until s).sumOf { data.series[it].values[i] }; val b=(0 until s).sumOf { data.series[it].values[i+1] }
                    path.moveTo(x(data.times[i]),y(a)); path.lineTo(x(data.times[i]),y(a+values[i])); path.lineTo(x(data.times[i+1]),y(b+values[i+1])); path.lineTo(x(data.times[i+1]),y(b)); path.close()
                }
            } else {
                var drawing=false
                for(i in data.times.indices) {
                    if(!values[i].isFinite()) { drawing=false; continue }
                    if(!drawing || (i>0 && data.times[i]-data.times[i-1]>6*HOUR)) path.moveTo(x(data.times[i]),y(values[i])) else path.lineTo(x(data.times[i]),y(values[i]))
                    drawing=true
                }
            }
        }
    }
    override fun onDraw(c: Canvas) {
        super.onDraw(c); val top=80*d; val bottom=height-42*d
        p.style=Paint.Style.FILL; p.color=palette.surface; c.drawRect(42*d,top,width-12*d,bottom,p)
        if(!data.hasData) {
            p.color=palette.muted; p.textSize=16*d
            c.drawText(data.title,8*d,24*d,p)
            c.drawText(data.emptyMessage,42*d,top+28*d,p)
            return
        }
        p.color=palette.raised
        for(span in data.dark) c.drawRect(x(span.start),top,x(span.end),bottom,p)
        p.color=palette.warning
        for(span in data.moon) c.drawRect(x(span.start),top,x(span.end),top+5*d,p)
        p.color=palette.bad; p.alpha=45
        for(span in data.risk) c.drawRect(x(span.start),top+6*d,x(span.end),bottom,p)
        p.alpha=255; p.textSize=12*d; p.strokeWidth=d; p.color=palette.muted
        c.drawText(data.title,8*d,18*d,p)
        for(i in 0..4) {
            val yy=y(data.minimum+(data.maximum-data.minimum)*i/4)
            c.drawLine(42*d,yy,width-12*d,yy,p); c.drawText(gridLabels[i],2*d,yy,p)
        }
        c.save(); c.clipRect(42*d,top,width-12*d,bottom)
        for(s in paths.indices) {
            p.color=colors[s%colors.size]; p.strokeWidth=2*d; p.style=if(data.stacked) Paint.Style.FILL else Paint.Style.STROKE
            p.pathEffect=if(data.series[s].dashed) dash else null; c.drawPath(paths[s],p)
        }
        p.pathEffect=dash; p.style=Paint.Style.STROKE; p.color=palette.text
        data.threshold?.let { c.drawLine(42*d,y(it),width-12*d,y(it),p) }
        p.pathEffect=null
        if(cursorIndex>=0) { p.color=palette.text; c.drawLine(x(data.times[cursorIndex]),top,x(data.times[cursorIndex]),bottom,p) }
        c.restore(); p.style=Paint.Style.FILL
        for(s in data.series.indices) {
            p.color=colors[s%colors.size]; c.drawText(data.series[s].name,8*d+(s%3)*(width-16*d)/3,38*d+(s/3)*18*d,p)
        }
        p.color=palette.muted
        if(data.times.isNotEmpty()) for(i in 0..2) {
            val index=data.times.lastIndex*i/2; val label=data.labels[index]
            c.drawText(label,(x(data.times[index])-p.measureText(label)/2).coerceIn(2*d,(width-p.measureText(label)-2*d).coerceAtLeast(2*d)),height-18*d,p)
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(!data.hasData) return false
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX=event.x; downY=event.y; moved=false; cancelled=false }
            MotionEvent.ACTION_MOVE -> {
                if(cancelled) return false
                val dx=abs(event.x-downX); val dy=abs(event.y-downY)
                if(dx>slop || dy>slop) {
                    moved=true
                    if(dy>dx) { cancelled=true; parent?.requestDisallowInterceptTouchEvent(false); return false }
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP -> { parent?.requestDisallowInterceptTouchEvent(false); if(!moved && !cancelled) performClick(); return true }
            MotionEvent.ACTION_CANCEL -> { cancelled=true; parent?.requestDisallowInterceptTouchEvent(false); return true }
            else -> return true
        }
        if(event.actionMasked==MotionEvent.ACTION_DOWN || event.actionMasked==MotionEvent.ACTION_MOVE) {
            val fraction=((event.x-42*d)/(width-54*d)).coerceIn(0f,1f)
            val time=first+(duration*fraction).toLong()
            cursorIndex=data.times.indices.minByOrNull { abs(data.times[it]-time) } ?: 0
            val label=data.cursor.getOrNull(cursorIndex) ?: (data.labels[cursorIndex]+" · "+data.series.joinToString(" · ") { s -> s.name+": "+DisplayFormat.number(s.values[cursorIndex],digits=1) })
            contentDescription=label; onCursor?.invoke(label); invalidate(); return true
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}

@android.annotation.SuppressLint("ViewConstructor")
class MoonCalendar(context: Context, days: List<MoonDay>, private val red: Boolean, zone: TimeZone, south: Boolean=false): View(context) {
    private val palette=Palette(red)
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val d=resources.displayMetrics.density
    private val offset=Calendar.getInstance(zone).apply { timeInMillis=days.first().time }.get(Calendar.DAY_OF_WEEK).let { (it+5)%7 }
    private val dayLabels=days.map { "${it.day} · ${(it.illumination*100).roundToInt()} %" }
    private val hoursLabels=days.map { if(it.moonlessHours>=2) "★ ${it.moonlessHours.roundToInt()} h" else "—" }
    private val details=days.map { "${Astro.phaseName(it.age)} · ${(it.illumination*100).roundToInt()} %\nOscuridad astronómica sin Luna: ${DisplayFormat.duration(it.moonlessHours)}. Fase evaluada a las 20 h; noche completa." }
    private var touchedX=0f; private var touchedY=0f
    init { isClickable=true; contentDescription="Calendario lunar de fases. Toca un día para ver iluminación y horas sin Luna."; setOnClickListener {
        val row=(height-24*d)/6; val index=((touchedY-24*d)/row).toInt()*7+(touchedX/(width/7f)).toInt()-offset
        if(touchedY>=24*d && index in details.indices) Widgets(context,palette).sheet("Luna · día ${days[index].day}",details[index])
    } }
    // Solo registrar coordenadas; View conserva por completo slop, cancelación y detección de click.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean { if(event.actionMasked==MotionEvent.ACTION_DOWN) {touchedX=event.x; touchedY=event.y}; return super.dispatchTouchEvent(event) }
    override fun performClick(): Boolean { super.performClick(); return true }
    private val strips=days.map { day ->
        val litRight=(day.age<180) xor south; val cosine=cos(day.age*Astro.RAD).toFloat()
        FloatArray(17*4).apply { for(j in 0..16) {
            val yy=(j-8)/8f; val half=sqrt(max(0f,1-yy*yy))
            this[j*4]=if(litRight) cosine*half else -half; this[j*4+1]=yy
            this[j*4+2]=if(litRight) half else -cosine*half; this[j*4+3]=yy
        } }
    }
    private val phaseLines=Array(days.size) { FloatArray(17*4) }
    private val headings=listOf("L","M","X","J","V","S","D")
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        val cell=w/7f; val row=(h-24*d)/6; val r=10*d
        strips.forEachIndexed { i,strip ->
            val cx=((i+offset)%7)*cell+cell-17*d; val cy=((i+offset)/7)*row+24*d+row-14*d
            for(j in strip.indices) phaseLines[i][j]=strip[j]*r+if(j%2==0) cx else cy
        }
    }
    override fun onDraw(c: Canvas) {
        p.textSize=12*d; val cell=width/7f; val row=(height-24*d)/6
        p.color=palette.accent
        headings.forEachIndexed { i,s -> c.drawText(s,i*cell+5*d,18*d,p) }
        dayLabels.forEachIndexed { i,_ ->
            val x=((i+offset)%7)*cell; val y=((i+offset)/7)*row+24*d
            p.style=Paint.Style.STROKE; c.drawRect(x,y,x+cell,y+row,p); p.style=Paint.Style.FILL
            p.textSize=11*d; c.drawText(dayLabels[i],x+4*d,y+12*d,p); c.drawText(hoursLabels[i],x+4*d,y+row-5*d,p)
            // Terminadores preparados al cambiar el tamaño; sin trigonometría en onDraw.
            val cx=x+cell-17*d; val cy=y+row-14*d; val r=10*d
            p.color=palette.raised; c.drawCircle(cx,cy,r,p)
            p.color=palette.accent
            p.strokeWidth=d; c.drawLines(phaseLines[i],p)
        }
    }
}
