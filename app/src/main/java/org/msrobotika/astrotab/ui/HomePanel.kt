package org.msrobotika.astrotab.ui

import android.content.Context
import android.graphics.*
import android.view.View
import android.widget.*
import org.msrobotika.astrotab.R
import org.msrobotika.astrotab.astro.*
import org.msrobotika.astrotab.data.*
import org.msrobotika.astrotab.satellites.*
import org.msrobotika.astrotab.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

data class HomeData(val now: Long,val night: NightTimeline,val events: List<AstroPhoto.Event>,val week: List<ForecastNight>,
    val plans: List<ObjectPlan>,val weather: Cloud?,val kp: KpSample?,val threshold: Double,val planets: List<String>,val moonEvents: Astro.HorizonEvents,
    val hours: List<NightHour>,val source: String,val featured: SkyEvent?=null)

/** Panel fijo: cuatro bandas con pesos. Ningún contenedor desplazable. */
@android.annotation.SuppressLint("ViewConstructor") // Panel programático con dependencias explícitas, sin inflación XML.
class HomePanel(context: Context,private val red: Boolean,private val zone: TimeZone,private val place: Location?,
    private val open: (String)->Unit,private val target: (String)->Unit): LinearLayout(context) {
    private val p=Palette(red); private val w=Widgets(context,p)
    private val local=w.text("",Design.DISPLAY); private val clocks=w.text("",14f,p.muted)
    private var model: HomeData?=null
    private var hasTle=false
    private var iss: SatellitePass?=null
    private var openIss: ()->Unit={open("Satélites")}
    fun bindIss(pass: SatellitePass?,available: Boolean,action: ()->Unit={open("Satélites")}) {
        if(hasTle==available && iss==pass)return
        hasTle=available;iss=pass;openIss=if(pass==null)({open("Satélites")})else action
        render(model);tick(System.currentTimeMillis())
    }
    init { orientation=VERTICAL; tag="home-panel"; render(null); tick(System.currentTimeMillis()) }
    private fun date(t: Long,pattern: String="HH:mm",tz: TimeZone=zone)=SimpleDateFormat(pattern,Locale.forLanguageTag("es-ES")).apply { timeZone=tz }.format(Date(t))
    fun tick(now: Long) {
        local.text=date(now,"HH:mm:ss")
        clocks.text=context.getString(R.string.home_clocks,date(now,"HH:mm:ss",TimeZone.getTimeZone("UTC")),place?.let { DisplayFormat.sidereal(now,it.longitude) } ?: "—",date(now,"EEEE, d MMMM yyyy"))
    }
    fun bind(data: HomeData) { model=data; render(data); tick(System.currentTimeMillis()) }
    private fun band(weight: Float)=w.row().also { addView(it,LayoutParams(-1,0,weight).apply { bottomMargin=w.dp(8) }) }
    private fun LinearLayout.cell(v: View,weight: Float=1f) { addView(v,LayoutParams(0,-1,weight).apply { leftMargin=w.dp(4); rightMargin=w.dp(4) }) }
    private fun tile(icon: Icon,value: String,label: String,short: String,detail: String,age: Double=90.0)=w.metric(icon,value,label,detail,age).apply { addView(w.text(short,14f,p.muted)); isClickable=true }
    private fun render(d: HomeData?) {
        (local.parent as? android.view.ViewGroup)?.removeView(local); (clocks.parent as? android.view.ViewGroup)?.removeView(clocks)
        removeAllViews()
        val top=band(1.05f)
        val clock=w.column().apply { addView(local); addView(clocks) }; top.cell(clock,1.1f)
        val tonight=d?.week?.firstOrNull(); val score=tonight?.score
        val rating=w.row().apply { background=w.shape(); addView(ScoreView(context,score,p),LayoutParams(w.dp(112),-1))
            val c=w.column(); c.addView(w.text(if(score==null) "Esta noche · sin nota" else "Noche ${DisplayFormat.status(score).lowercase()}",20f,p.score(score)))
            c.addView(w.text(tonight?.clouds?.let { "Nubes ${DisplayFormat.number(it," %")}" } ?: if(place==null) "Ubicación sin configurar" else "Meteorología sin datos",16f,p.muted))
            val best=d?.week?.let(Forecast::best)
            c.addView(w.text(best?.let { "Mejor: ${date(it.start,"EEE d")} · ${it.score}/100" } ?: "Próximos 7 días: sin nota",14f,p.muted)); addView(c,LayoutParams(0,-2,1f))
            addView(w.info("Nota de la noche",d?.let { "${it.source}\nNota y mejor noche: cálculo Forecast de 0.4.1, sin cambios. Solo compiten noches con cobertura oscura completa. Nubes, iluminación/altura lunar, rocío, viento y lluvia. Sin transparencia ni seeing medidos. Sin datos: sin nota." } ?: "Configura ubicación en Ajustes. La astronomía funciona sin conexión; la nota requiere datos meteorológicos vigentes."),LayoutParams(w.dp(56),w.dp(56)))
        }; top.cell(rating,1.4f)
        val bar=band(0.6f)
        bar.cell(if(d==null) w.text(if(place==null) "Abre Ajustes para situar tu cielo →" else "Preparando esta noche…",16f,p.muted).apply { setOnClickListener { open("Ajustes") } } else HomeTimeline(context,d,red,zone))
        if(hasTle)bar.cell(w.row().apply {
            background=w.shape();setPadding(w.dp(6),0,w.dp(6),0)
            addView(IconView(context,Icon.SATELLITE,p),LayoutParams(w.dp(28),w.dp(28)))
            addView(w.column().apply {
                addView(w.text("Próximo paso de la ISS",12f,p.muted))
                addView(w.text(iss?.let {"${date(it.start.time,"dd/MM HH:mm")} · ${DisplayFormat.number(SatellitePassPolicy.visiblePeak(it)?.altitude,"°")}"} ?: "Consultar pasos →",18f,p.accent))
            },LayoutParams(0,-2,1f));setOnClickListener {openIss()}
        },0.6f)
        val metrics=band(1.6f)
        val phase=Astro.moonPhase(Astro.dayNumber(d?.now ?: System.currentTimeMillis()))
        val moon=d?.moonEvents
        metrics.cell(tile(Icon.MOON,if(d==null) "—" else DisplayFormat.number(phase.illumination*100," %"),"Luna",
            if(d==null) "Sin ubicación" else "↑ ${moon?.rise?.let { date(it) } ?: "—"}   ↓ ${moon?.set?.let { date(it) } ?: "—"}",
            "${Astro.phaseName(phase.age)}. Salida anterior si está arriba; próxima puesta. Horizonte geométrico con paralaje. Sin cruce: —.",phase.age).apply { setOnClickListener { open("Luna") } })
        fun event(prefix: String,rise: Boolean)=d?.events?.firstOrNull { it.label.startsWith(prefix) && it.label.endsWith(if(rise) "ascendente" else "descendente") }?.time
        metrics.cell(tile(Icon.SUN,event("Salida/puesta",false)?.let { date(it) } ?: "—","Puesta del Sol",
            "↑ ${event("Salida/puesta",true)?.let { date(it) } ?: "—"}  ·  astro ${event("Astronómico",false)?.let { date(it) } ?: "—"}",
            "Puesta / salida: umbral solar −0,833°. Fin del crepúsculo astronómico: −18°. — significa sin cruce en esta noche. Modelo aproximado existente.").apply { setOnClickListener { open("Esta noche") } })
        val weather=d?.weather
        metrics.cell(tile(Icon.DEW,DisplayFormat.number(weather?.temperature," °C"),"Tiempo ahora",
            "Rocío ${DisplayFormat.number(weather?.dewPoint,"°")} · ${DisplayFormat.number(weather?.wind," km/h")}",
            "${d?.source ?: "Sin datos"}\nRocío: ${Weather.dewRisk(weather?.temperature,weather?.dewPoint)}. Aviso si T−Td <3 °C. Open-Meteo CC BY 4.0.").apply {
                if(Weather.dewRisk(weather?.temperature,weather?.dewPoint)=="alto") addView(w.text("⚠ Riesgo de rocío",14f,p.warning))
                setOnClickListener { open("Tiempo") }
            })
        metrics.cell(tile(Icon.AURORA,d?.kp?.let { "${DisplayFormat.number(it.value,digits=1)} Kp" } ?: "sin datos","Auroras",
            d?.let { if(it.threshold>9) "Necesario: fuera de escala" else "Necesario: Kp ${DisplayFormat.number(it.threshold,digits=1)}" } ?: "Sin ubicación",
            "NOAA SWPC. ${d?.kp?.let { "${when(it.kind) { KpKind.OBSERVED -> "Observado"; KpKind.ESTIMATED -> "Estimado"; KpKind.PREDICTED -> "Previsto" }}: ${date(it.time,"dd/MM HH:mm")} → ${date(it.time+it.duration,"HH:mm")}." } ?: "Sin datos vigentes."} Umbral geomagnético aproximado, no probabilidad de ver auroras.").apply { setOnClickListener { open("Auroras") } })
        val lower=band(0.9f)
        val planets=w.column().apply { background=w.shape(); addView(w.text("Planetas esta noche",16f,p.muted)); val r=w.row(); addView(r)
            r.addView(IconView(context,Icon.PLANET,p),LayoutParams(w.dp(32),w.dp(32))); r.addView(w.text(d?.planets?.joinToString(" · ")?.ifEmpty { "Ninguno sobre el horizonte nocturno" } ?: "—",16f)); setOnClickListener { open("Planetas") }
            d?.featured?.let { e -> addView(w.text("★ ${date(e.time,"dd/MM")} ${e.title}",14f,p.accent).apply {contentDescription="Próximo evento destacado: ${e.title}"}) }
        }; lower.cell(planets,0.95f)
        val targets=w.column().apply { background=w.shape(); addView(w.text("Mejores Messier · esta noche",16f,p.muted)); val r=w.row(); addView(r,LayoutParams(-1,0,1f))
            if(d==null) r.addView(w.text(if(place==null) "Configura ubicación para planificar" else "Preparando catálogo offline…",14f,p.muted))
            d?.plans?.let { TargetChoice.diverse(it) }?.forEach { plan ->
                val b=Button(context).apply { text=context.getString(R.string.home_target,plan.target.id,plan.score,date(plan.transit)); textSize=14f; isAllCaps=false; setTextColor(p.text); background=w.shape(p.raised); setCompoundDrawablesWithIntrinsicBounds(IconDrawable(Icons.type(plan.target.type),p.accent,w.dp(24)),null,null,null); setOnClickListener { target(plan.target.id) }; contentDescription="${plan.target.label}, ${plan.score} de 100, culminación ${date(plan.transit)}" }
                r.addView(b,LayoutParams(0,-1,1f).apply { setMargins(w.dp(4),0,w.dp(4),0) })
            }
        }; lower.cell(targets,1.55f)
    }
}

@android.annotation.SuppressLint("ViewConstructor")
class HomeTimeline(context: Context,d: HomeData,red: Boolean,zone: TimeZone): View(context) {
    private val palette=Palette(red); private val p=Paint(Paint.ANTI_ALIAS_FLAG); private val density=resources.displayMetrics.density
    private val rows=d.night.samples; private val clouds=d.hours
    private val start=d.events.firstOrNull { it.label.startsWith("Salida/puesta") && it.label.endsWith("descendente") }?.time ?: d.night.start
    private val end=d.events.lastOrNull { it.label.startsWith("Salida/puesta") && it.label.endsWith("ascendente") }?.time ?: d.night.end
    private val now=d.now
    private val nowLabel=when { now<start -> "Ahora: antes del atardecer"; now>end -> "Ahora: después del amanecer"; else -> "│ Ahora" }
    private val fmt=SimpleDateFormat("HH:mm",Locale.ROOT).apply { timeZone=zone }
    private val labels=arrayOf("Atardecer ${fmt.format(Date(start))}","Crepúsculos · oscuridad · Luna · nubes", "Amanecer ${fmt.format(Date(end))}")
    init { contentDescription=labels.joinToString(". ")+". Franja de nubes sin datos cuando no hay meteorología." }
    private fun x(t: Long)=8*density+(width-16*density)*(t-start).toFloat()/(end-start).coerceAtLeast(1)
    override fun onDraw(c: Canvas) {
        p.textSize=12*density; p.color=palette.muted; c.drawText(labels[0],8*density,14*density,p)
        c.drawText(labels[2],width-p.measureText(labels[2])-8*density,14*density,p)
        val top=22*density; val bottom=height-23*density
        c.save(); c.clipRect(8*density,top,width-8*density,bottom)
        for(i in 0 until rows.lastIndex) { val a=rows[i]; val b=rows[i+1]; p.color=palette.twilight(a.sun); c.drawRect(x(a.time),top,x(b.time)+1,bottom,p)
            if(a.moon>0) { p.color=palette.warning; c.drawRect(x(a.time),top,x(b.time)+1,top+3*density,p) }
        }
        for(h in clouds) { val cloud=h.cloud; p.color=if(cloud==null) palette.muted else if(cloud<35) palette.good else if(cloud<70) palette.warning else palette.bad; p.alpha=if(cloud==null) 30 else 160; c.drawRect(x(h.time),bottom-5*density,x(h.time+HOUR),bottom,p) }; p.alpha=255
        p.color=palette.accent; p.strokeWidth=2*density; c.drawLine(x(now.coerceIn(start,end)),top,x(now.coerceIn(start,end)),bottom,p); c.restore()
        p.color=palette.muted; p.textSize=11*density; c.drawText(labels[1],8*density,height-5*density,p)
        p.color=palette.accent; c.drawText(nowLabel,width-p.measureText(nowLabel)-8*density,height-5*density,p)
    }
}
