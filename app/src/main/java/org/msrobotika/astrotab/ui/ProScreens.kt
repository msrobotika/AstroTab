package org.msrobotika.astrotab.ui

import android.app.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import org.msrobotika.astrotab.R
import org.msrobotika.astrotab.astro.*
import org.msrobotika.astrotab.data.*
import org.msrobotika.astrotab.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import kotlin.math.*

/** Pantallas PRO. CPU/almacenamiento en worker, publicación con token de pantalla. */
class ProScreens(private val activity: Activity, private val settings: Settings, private val repository: NetworkRepository,
    private val worker: ExecutorService, private val alive: ()->Boolean, private val go: (String)->Unit,
    private val export: ()->Unit, private val now: Long) {
    private val red=settings.red
    private val zone=settings.zone
    private val palette=Palette(red)
    private val widgets=Widgets(activity,palette)
    private val fg=palette.text
    private val bg=palette.surface
    private val preferences=activity.getSharedPreferences("pro-values",0)
    private fun dp(v: Int)=(v*activity.resources.displayMetrics.density).roundToInt()
    private fun column()=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
    private fun text(s: String,size: Float=18f)=TextView(activity).apply { text=s; textSize=size; setTextColor(fg); setPadding(dp(8),dp(8),dp(8),dp(8)) }
    private fun button(s: String, action: ()->Unit)=Button(activity).apply {
        text=s; textSize=16f; isAllCaps=false; minHeight=dp(56); minimumHeight=dp(56); setTextColor(fg)
        background=widgets.shape(palette.raised)
        layoutParams=LinearLayout.LayoutParams(-1,dp(64)).apply { setMargins(dp(4),dp(4),dp(4),dp(4)) }
        setOnClickListener { action() }
        widgets.styleValueButton(this,s)
    }
    private fun scroll(c: View)=ScrollView(activity).apply { isFillViewport=true; addView(c,ViewGroup.LayoutParams(-1,-2)) }
    private fun card(c: LinearLayout, heading: String, message: String) {
        val box=column().apply { background=widgets.shape(); setPadding(dp(8),dp(4),dp(8),dp(4)) }
        box.addView(widgets.section(heading,message)); box.addView(text(DisplayFormat.summary(message),16f))
        c.addView(box,LinearLayout.LayoutParams(-1,-2).apply { setMargins(dp(4),dp(4),dp(4),dp(8)) })
    }
    private fun n(v: Double?,unit: String="",digits: Int=0)=DisplayFormat.number(v,unit,digits)
    private fun date(t: Long, pattern: String="dd/MM HH:mm z")=SimpleDateFormat(pattern,Locale.forLanguageTag("es-ES")).apply { timeZone=zone }.format(Date(t))
    private fun async(c: LinearLayout, compute: ()->(()->Unit)) {
        val loading=text("Calculando…"); c.addView(loading)
        worker.execute {
            if(!alive()) return@execute
            val result=runCatching(compute)
            activity.runOnUiThread { if(alive()) { c.removeView(loading); result.onSuccess { it() }.onFailure { card(c,"No se ha podido cargar","Se conservan los datos guardados. ${it.message ?: "Revisa los datos de entrada."}") } } }
        }
    }
    fun render(name: String): View {
        val c=column(); c.addView(text(name,Design.TITLE))
        when(name) {
            "Tiempo" -> weather(c)
            "Zona" -> zones(c)
            "Planetas","Planeta" -> planets(c,name=="Planeta")
            "Meteoros" -> meteors(c)
            "Calculadoras" -> calculators(c)
            "Hiperfocal y profundidad de campo","Timelapse","Circumpolares / trazos","Panorámica / mosaico","Apilado" -> calculators(c,name)
            "Bitácora" -> log(c)
            "Linterna" -> { c.addView(button("Abrir linterna roja") { flashlight() }); flashlight() }
        }
        return scroll(c)
    }
    private fun zones(c: LinearLayout) {
        card(c,"Zona horaria de AstroTab","Actual: ${zone.id}. Cambia las horas de todas las pantallas, sin modificar la hora ni la zona del sistema. Los cálculos internos conservan UTC.")
        c.addView(button("Usar zona del sistema (${TimeZone.getDefault().id})") { settings.zoneId=null; go("Ajustes") })
        for(id in listOf("Europe/Madrid","Atlantic/Canary","Europe/Lisbon","Europe/London","Europe/Paris","UTC")) c.addView(button(id) { settings.zoneId=id; go("Ajustes") })
        val list=column(); c.addView(list); val ids=TimeZone.getAvailableIDs().sorted(); var page=0
        fun showPage() {
            list.removeAllViews(); list.addView(text("Todas · página ${page+1}/${(ids.size+19)/20}"))
            list.addView(button("Anterior") { if(page>0) { page--; showPage() } })
            list.addView(button("Siguiente") { if((page+1)*20<ids.size) { page++; showPage() } })
            ids.drop(page*20).take(20).forEach { id -> list.addView(button(id) { settings.zoneId=id; go("Ajustes") }) }
        }
        showPage()
    }
    private fun weather(c: LinearLayout) {
        val weatherNow=System.currentTimeMillis()
        val place=settings.location
        if(place==null) { card(c,"Ubicación sin configurar","Introduce la ubicación en Ajustes."); return }
        val enabled=settings.networkEnabled; val data=repository.clouds
        card(c,"Open-Meteo · CC BY 4.0", "${if(enabled) DataPolicy.freshness(data?.fetchedAt,System.currentTimeMillis()).label else "Red desactivada · sin datos"} · ${repository.status}\nConsulta: ${data?.fetchedAt?.let { date(it) } ?: "sin datos"}")
        card(c,"Próximas 48 h","Toca o arrastra cada gráfica para consultar la hora. Fondo: oscuridad astronómica; franja superior: Luna sobre horizonte. Rocío sombreado si T−Td <3 °C. Capas apiladas normalizadas al 100 % cuando se solapan; cursor conserva los porcentajes originales.")
        async(c) {
            val times=(0 until 48).map { weatherNow/HOUR*HOUR+it*HOUR }
            val rows=times.map { DataPolicy.weatherAt(data,it,System.currentTimeMillis(),place,enabled) }
            val hours=times.map { Night.hour(it,System.currentTimeMillis(),place,data,enabled) }
            val dark=AstroPhoto.above(times,hours.map { -it.sunAltitude }.toDoubleArray(),18.0)
            val moon=AstroPhoto.above(times,hours.map { it.moonAltitude }.toDoubleArray(),0.0)
            val risk=rows.mapIndexedNotNull { i,w -> if(Weather.dewRisk(w?.temperature,w?.dewPoint)=="alto") TimeSpan(times[i],min(times.last(),times[i]+HOUR)) else null }
            val labels=PlotData.labels(times,zone)
            val cursor=rows.mapIndexed { i,w -> labels[i]+"\n"+if(w==null) "Sin datos meteorológicos" else
                "Nubes ${n(w.cover," %")} (baja ${n(w.low)}, media ${n(w.mid)}, alta ${n(w.high)}) · lluvia ${n(w.rain," %")}\nT ${n(w.temperature," °C")} · Td ${n(w.dewPoint," °C")} · HR ${n(w.humidity," %")} · viento ${n(w.wind," km/h")} · rachas ${n(w.gusts," km/h")}\nVisibilidad ${w.visibility?.let { n(it/1000," km") } ?: "sin datos"} · presión ${n(w.pressure," hPa")} · 250 hPa ${n(w.jet," km/h")} · turbulencia estimada ${Forecast.turbulence(w.jet)} (no seeing medido)" }
            fun values(field: (Cloud)->Double?)=rows.map { it?.let(field) ?: Double.NaN }.toDoubleArray()
            val layers=rows.map { it?.let(Forecast::layers) }
            val temperature=values { it.temperature }; val dew=values { it.dewPoint }
            val temperatures=(temperature.toList()+dew.toList()).filter { it.isFinite() }
            val low=(temperatures.minOrNull() ?: 0.0)-2; val high=(temperatures.maxOrNull() ?: 20.0)+2
            val plots=listOf(
                PlotData("Nubes por capas · %",times,listOf("Bajas","Medias","Altas").mapIndexed { index,s -> PlotSeries(s,layers.map { it?.get(index) ?: Double.NaN }.toDoubleArray()) },0.0,100.0,labels,cursor,dark,moon,stacked=true),
                PlotData("Temperatura y punto de rocío · °C",times,listOf(PlotSeries("Temperatura",temperature),PlotSeries("Rocío",dew)),low,high,labels,cursor,dark,moon,risk),
                PlotData("Viento y rachas · km/h",times,listOf(PlotSeries("Viento",values { it.wind }),PlotSeries("Rachas",values { it.gusts })),0.0,max(40.0,rows.mapNotNull { it?.gusts ?: it?.wind }.maxOrNull() ?: 40.0),labels,cursor,dark,moon),
                PlotData("Probabilidad de lluvia · %",times,listOf(PlotSeries("Lluvia",values { it.rain })),0.0,100.0,labels,cursor,dark,moon)).map { it.copy(emptyMessage="sin datos meteorológicos") }
            val week=Forecast.week(weatherNow,zone,place,data,enabled) { !alive() }; val best=Forecast.best(week)
            val publish: ()->Unit = {
                plots.forEach { plot ->
                    val readout=text(if(plot.hasData) "Toca la gráfica para consultar valores" else plot.emptyMessage,16f)
                    c.addView(SeriesChart(activity,plot,red) { readout.text=it },LinearLayout.LayoutParams(-1,dp(280))); c.addView(readout)
                }
                card(c,"Mejor noche de la semana",best?.let { "${date(it.start,"EEEE dd/MM")} · ${it.score}/100" } ?: "Sin noches con cobertura meteorológica oscura completa.")
                week.forEach { night ->
                    val box=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL; background=widgets.shape() }
                    box.addView(ScoreView(activity,night.score,palette),LinearLayout.LayoutParams(dp(96),dp(96)))
                    val data=column(); data.addView(text(date(night.start,"EEEE dd/MM"),Design.TITLE)); data.addView(text("${DisplayFormat.duration(night.darkHours)} oscuridad · nubes ${n(night.clouds," %")} · viento ${n(night.wind," km/h")}",16f)); box.addView(data,LinearLayout.LayoutParams(0,-2,1f))
                    box.addView(widgets.pill(night.score)); box.addView(widgets.info("Condiciones de la noche","Margen T−Td ${n(night.dew," °C")}. Turbulencia estimada ${Forecast.turbulence(night.jet)}, no seeing medido. Cobertura ${DisplayFormat.duration(night.coveredHours)} de ${DisplayFormat.duration(night.darkHours)}. Sin oscuridad o cobertura incompleta: no compite a mejor noche."),LinearLayout.LayoutParams(dp(56),dp(56)))
                    c.addView(box,LinearLayout.LayoutParams(-1,dp(104)).apply {setMargins(dp(4),dp(4),dp(4),dp(4))})
                }
                c.addView(widgets.section("Cómo se calcula la nota","Media ponderada de las horas oscuras: 100 × (1−nubes/100) × (1−0,8×factor lunar) × rocío × viento × (1−lluvia/100). Rocío 0,65 si T−Td<3 °C; viento 0,7 a 15 km/h y 0,4 a 30. Factores opcionales ausentes sin penalizar y sin evaluar. Turbulencia heurística: <70 km/h baja, 70–140 moderada, ≥140 alta; también influyen capas bajas, relieve y estabilidad. No sustituye un seeing medido."))
            }
            publish
        }
    }
    private fun planets(c: LinearLayout, detail: Boolean) {
        val place=settings.location ?: run { card(c,"Ubicación sin configurar","Abre Ajustes."); return }
        val index=preferences.getInt("planet",0).coerceIn(0,4)
        card(c,"Planetas · horizonte geométrico","Noche del mediodía local al siguiente. Sin cruce: no ocurre en ese intervalo. Modelo Schlyter aproximado heredado, sin refracción ni relieve; magnitudes orientativas, posiciones para orientación (orden de 1°, no precisión instrumental). Urano/Neptuno fuera del motor actual.")
        async(c) {
            val night=AstroPhoto.timeline(now,zone,place); val planets=ProAstro.planets(night,place)
            val plot=PlotData.altitudes(night,if(detail) listOf(planets[index]) else planets,zone)
            val publish: ()->Unit = {
                if(!detail) {
                    val table=TableLayout(activity).apply { isStretchAllColumns=true }
                    fun tableRow(cells: List<String>) {
                        val row=TableRow(activity)
                        cells.forEach { cell -> row.addView(text(cell,14f)) }
                        table.addView(row)
                    }
                    tableRow(listOf("Planeta","Salida","Culmina","Puesta","Alt. máx.","Mag. ≈","Elong."))
                    planets.forEach { p -> tableRow(listOf(p.name,p.rise?.let { date(it,"HH:mm z") } ?: "—",date(p.transit,"HH:mm z"),p.set?.let { date(it,"HH:mm z") } ?: "—",n(p.maximum,"°"),n(p.magnitude),n(p.elongation,"°"))) }
                    c.addView(HorizontalScrollView(activity).apply { addView(table) })
                }
                val readout=text("Toca la curva para ver hora y alturas")
                c.addView(SeriesChart(activity,plot,red) { readout.text=it },LinearLayout.LayoutParams(-1,dp(320))); c.addView(readout)
                for(p in if(detail) listOf(planets[index]) else planets) {
                    card(c,p.name,"Salida ${p.rise?.let { date(it) } ?: "sin cruce"} · culminación ${date(p.transit)} · puesta ${p.set?.let { date(it) } ?: "sin cruce"}\nAltura de culminación ${n(p.maximum,"°")} · mag. ≈${n(p.magnitude)} · elongación ${n(p.elongation,"°")}\nMagnitud y elongación evaluadas en la culminación, que puede ser de día.")
                    if(!detail) c.addView(button("Curva · ${p.name}") { preferences.edit().putInt("planet",p.index).apply(); go("Planeta") })
                }
                if(detail) c.addView(button("Todos los planetas") { go("Planetas") })
            }; publish
        }
    }
    private fun meteors(c: LinearLayout) {
        val place=settings.location ?: run { card(c,"Ubicación sin configurar","Abre Ajustes."); return }
        card(c,"Lluvias de meteoros","Fechas de máximo aproximadas y THZ ideal orientativa, variables según año. Fuente: International Meteor Organization, Meteor Shower Calendar (imo.net/resources/calendar/). No consultado en red en esta sesión. El radiante se calcula para la medianoche local de esta noche; su posición cambia durante la actividad. THZ no equivale a meteoros que verás.")
        async(c) {
            val rows=ProAstro.meteors(now,zone,place)
            val publish: ()->Unit = { rows.forEach { row -> card(c,row.shower.name,"Próximo máximo ≈${date(row.peak,"dd/MM/yyyy")} · THZ ≈${row.shower.zhr}\nRadiante a medianoche: ${n(row.radiantAltitude,"°")} · culminación ${date(row.transit)}\nLuna a medianoche: ${n(row.illumination*100," %")} · altura ${n(row.moonAltitude,"°")}") } }; publish
        }
    }
    private fun value(key: String,initial: Double)=preferences.getString(key,null)?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: initial
    private fun field(c: LinearLayout,key: String,label: String,initial: Double,range: ClosedFloatingPointRange<Double>) {
        val current=value(key,initial)
        c.addView(button("$label: ${n(current,digits=if(current==floor(current)) 0 else 2)}") {
            keyboard(label,current.toString(),true) { raw ->
                val v=Coordinates.number(raw)
                if(v==null || v !in range) "Valor entre ${range.start} y ${range.endInclusive}" else { preferences.edit().putString(key,v.toString()).apply(); go(preferences.getString("activeCalculator","Calculadoras")!!); null }
            }
        })
        val r=LinearLayout(activity)
        for(delta in listOf(-1,1)) r.addView(button(if(delta<0) "−" else "+") { val next=(current+delta).coerceIn(range); preferences.edit().putString(key,next.toString()).apply(); go(preferences.getString("activeCalculator","Calculadoras")!!) },LinearLayout.LayoutParams(0,dp(56),1f).apply { setMargins(dp(4),dp(4),dp(4),dp(4)) }); c.addView(r)
    }
    private fun calculators(c: LinearLayout,name: String?=null) {
        if(name==null) {
            listOf("Hiperfocal y profundidad de campo","Timelapse","Circumpolares / trazos","Panorámica / mosaico","Apilado").chunked(3).forEach { group -> val r=LinearLayout(activity); group.forEach { label -> r.addView(button(label) { go(label) }.apply { setCompoundDrawablesWithIntrinsicBounds(null,IconDrawable(Icon.CAMERA,palette.accent,dp(32)),null,null) },LinearLayout.LayoutParams(0,dp(112),1f).apply { setMargins(dp(4),dp(4),dp(4),dp(4)) }) }; c.addView(r) }; return
        }
        preferences.edit().putString("activeCalculator",name).apply()
        c.addView(button("Todas las calculadoras") { go("Calculadoras") })
        val e=settings.equipment
        if(name=="Hiperfocal y profundidad de campo") {
        val focus=value("focus",10.0)
        val f=runCatching { ProTools.focus(e,focus) }.getOrNull()
        c.addView(text(f?.let { n(it.hyperfocalMetres," m",1) } ?: "—",Design.DISPLAY))
        card(c,"Hiperfocal y profundidad de campo",f?.let { "Nítido desde ${n(it.nearMetres," m",1)} hasta ${it.farMetres?.let { far -> n(far," m",1) } ?: "infinito"}\nCoC convencional: diagonal del sensor/1500 = ${n(it.cocMm," mm",4)}. Hiperfocal ${n(it.hyperfocalMetres," m")} · foco ${n(focus," m")}\nDepende de ampliación/visualización, sin difracción ni curvatura. Equipo de Ajustes: ${n(e.focal," mm")} f/${n(e.aperture)}." } ?: "Distancia de enfoque menor que la focal; aumenta la distancia o revisa Ajustes.")
        field(c,"focus","Enfoque (m)",10.0,0.01..1000000.0)
        }
        if(name=="Timelapse") {
        val interval=value("interval",15.0); val event=value("event",120.0); val fps=value("fps",25.0); val mb=value("mb",25.0)
        val lapse=ProTools.timelapse(interval,event*60,fps,mb)
        c.addView(text("${lapse.frames} fotos",Design.DISPLAY))
        card(c,"Timelapse","${lapse.frames} fotos · clip ${n(lapse.clipSeconds," s")} · espacio ≈${n(lapse.megabytes," MB")}\nTomas desde t=0 hasta antes del final. Intervalo entre inicios: debe permitir exposición y escritura; no incluye espacio de procesado.")
        field(c,"interval","Intervalo (s)",15.0,0.1..86400.0); field(c,"event","Duración evento (min)",120.0,0.1..100000.0)
        field(c,"fps","FPS del clip",25.0,1.0..240.0); field(c,"mb","MB por foto",25.0,0.1..1000.0)
        }
        if(name=="Circumpolares / trazos") {
        val minutes=value("trail",60.0); val arc=value("arc",30.0)
        c.addView(text(n(ProTools.arc(minutes*60),"°",1),Design.DISPLAY))
        card(c,"Circumpolares / trazos","${n(minutes," min")} → arco ${n(ProTools.arc(minutes*60),"°")}\nArco ${n(arc,"°")} → ${n(ProTools.arcSeconds(arc)/60," min")}\nRotación sidérea: 360°/86164,09 s. Arco alrededor del polo; longitud aparente de la traza depende de la declinación/proyección.")
        field(c,"trail","Duración de trazos (min)",60.0,0.0..100000.0); field(c,"arc","Arco deseado (°)",30.0,0.0..3600.0)
        }
        if(name=="Panorámica / mosaico") {
        val w=value("mosaicW",120.0); val h=value("mosaicH",60.0); val overlap=value("overlap",30.0)
        val cols=ProTools.mosaicAxis(w,Optics.field(e.width,e.focal),overlap); val rows=ProTools.mosaicAxis(h,Optics.field(e.height,e.focal),overlap)
        c.addView(text("${cols.toLong()*rows} fotos",Design.DISPLAY))
        card(c,"Panorámica / mosaico","${cols} horizontal × $rows vertical = ${cols.toLong()*rows} fotos\nCampo de cada foto ${n(Optics.field(e.width,e.focal),"°")} × ${n(Optics.field(e.height,e.focal),"°")}. Solape lineal ${n(overlap," %")}; cálculo plano, orientativo para campos grandes/proyección esférica. Sensor en orientación de Ajustes.")
        field(c,"mosaicW","Cobertura horizontal (°)",120.0,0.1..360.0); field(c,"mosaicH","Cobertura vertical (°)",60.0,0.1..180.0); field(c,"overlap","Solape (%)",30.0,0.0..95.0)
        }
        if(name=="Apilado") {
        val count=value("stack",100.0).toInt(); val exposure=value("stackExp",30.0); val stack=ProTools.stack(count,exposure)
        c.addView(text(DisplayFormat.duration(stack.integrationSeconds/3600),Design.DISPLAY))
        card(c,"Apilado","$count tomas × ${n(exposure," s")} = ${n(stack.integrationSeconds/60," min")}\nMejora SNR ideal √n = ×${n(stack.snrGain, digits=2)} (ruido aleatorio independiente; sin saturación).\nSugerencia orientativa: ${stack.darks} darks, ${stack.flats} flats, ${stack.bias} bias. Igualar temperatura/exposición en darks e ISO/ganancia; flats según óptica. Algunos sensores requieren dark-flats en lugar de bias.")
        field(c,"stack","Tomas para apilar",100.0,1.0..1000000.0); field(c,"stackExp","Exposición por toma (s)",30.0,0.01..86400.0)
        }
    }
    private fun log(c: LinearLayout) {
        val book=LogBook(File(activity.filesDir,"bitacora.json"))
        var target=""; var equipment="${n(settings.equipment.focal)} mm f/${n(settings.equipment.aperture,digits=1)} ${settings.equipment.sensor.label}"
        var notes=""; var frames=1; var exposure=30.0; var dateText=date(System.currentTimeMillis(),"yyyy-MM-dd HH:mm")
        card(c,"Nueva sesión","Almacenamiento privado de la app. Usa su teclado propio. Exporta CSV antes de borrar los datos o desinstalar. No guarda imágenes. Límite 2000 sesiones / 4 MiB.")
        fun edit(label: String,initial: ()->String, numeric: Boolean=false, save: (String)->String?) {
            val b=button("$label: ${initial().ifEmpty { "—" }}") {}
            b.setOnClickListener { keyboard(label,initial(),numeric) { raw -> val error=save(raw); if(error==null) b.text=activity.getString(R.string.pro_field_value,label,initial().take(100)); error } }; c.addView(b)
        }
        edit("Fecha local",{dateText}) { raw ->
            val f=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT).apply { timeZone=zone; isLenient=false }
            val pos=java.text.ParsePosition(0)
            if(f.parse(raw,pos)==null || pos.index!=raw.length) "Formato yyyy-MM-dd HH:mm en ${zone.id}" else { dateText=raw; null }
        }
        edit("Objeto",{target}) { target=it.take(4000); null }; edit("Equipo",{equipment}) { equipment=it.take(4000); null }
        edit("Tomas",{frames.toString()},true) { raw -> val v=raw.toIntOrNull(); if(v==null || v !in 1..1000000) "Entre 1 y 1000000" else { frames=v; null } }
        edit("Exposición (s)",{exposure.toString()},true) { raw -> val v=Coordinates.number(raw); if(v==null || v !in 0.01..86400.0) "Entre 0,01 y 86400" else { exposure=v; null } }
        edit("Notas",{notes}) { notes=it.take(4000); null }
        val status=text(""); c.addView(status)
        val save=button("Guardar sesión") {}; c.addView(save)
        save.setOnClickListener {
            if(target.isBlank()) { status.setText(R.string.pro_target_required); return@setOnClickListener }
            val f=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT).apply { timeZone=zone; isLenient=false }
            val entry=LogEntry(f.parse(dateText)!!.time,target,equipment,frames,exposure,notes)
            save.isEnabled=false
            worker.execute { val result=runCatching { book.append(entry) }; activity.runOnUiThread { if(alive()) { save.isEnabled=true; if(result.isSuccess) go("Bitácora") else status.text=activity.getString(R.string.pro_not_saved,result.exceptionOrNull()?.message) } } }
        }
        c.addView(button("Exportar bitácora a CSV") { export() })
        async(c) {
            val rows=book.read().asReversed()
            val publish: ()->Unit = {
                val saved=column(); c.addView(saved); var page=0
                fun showPage() {
                    saved.removeAllViews()
                    if(rows.isEmpty()) { card(saved,"Sesiones guardadas","Todavía no hay sesiones."); return }
                    saved.addView(text("${rows.size} sesiones · página ${page+1}/${(rows.size+19)/20}"))
                    saved.addView(button("Sesiones anteriores") { if((page+1)*20<rows.size) { page++; showPage() } })
                    saved.addView(button("Sesiones más recientes") { if(page>0) { page--; showPage() } })
                    rows.drop(page*20).take(20).forEach { e -> card(saved,"${date(e.time)} · ${e.target}","${e.equipment}\n${e.frames} × ${n(e.exposure," s")} · ${n(e.frames*e.exposure/60," min")}\n${e.notes}") }
                }
                showPage()
            }; publish
        }
    }
    /** Teclado sin IME; todas las teclas con área de 56 dp o más. */
    private fun keyboard(title: String,initial: String,numeric: Boolean,accept: (String)->String?) {
        var value=initial; var caps=true; val c=column(); c.setBackgroundColor(bg)
        val display=text(value.takeLast(240).ifEmpty { "—" },20f); val error=text("",16f)
        c.addView(text(title,24f)); c.addView(display); c.addView(error)
        val dialog=Dialog(activity); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        fun update() { display.text=value.takeLast(240).ifEmpty { "—" } }
        fun buttons(keys: List<String>, action: (String)->Unit) {
            val row=LinearLayout(activity)
            keys.forEach { key -> row.addView(button(key) { action(key) },LinearLayout.LayoutParams(0,dp(56),1f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) }) }; c.addView(row)
        }
        val alphabet=if(numeric) listOf("123","456","789","0.,−") else listOf("12345678","90-:/.,_","QWERTYUI","OPASDFGH","JKLÑZXCV","BNMÁÉÍÓÚ")
        alphabet.forEach { line -> buttons(line.map { it.toString() }) { key -> if(value.length<4000) value+=if(caps) key else key.lowercase(Locale.forLanguageTag("es-ES")); update() } }
        buttons(if(numeric) listOf("Borrar","Limpiar") else listOf("Espacio","Borrar","Limpiar","May/min")) { key ->
            when(key) { "Espacio" -> if(value.length<4000) value+=" "; "Borrar" -> value=value.dropLast(1); "Limpiar" -> value=""; "May/min" -> caps=!caps }; update()
        }
        buttons(listOf("Cancelar","Aceptar")) { key -> if(key=="Cancelar") dialog.dismiss() else { val problem=accept(value); if(problem==null) dialog.dismiss() else error.text=problem } }
        dialog.setContentView(scroll(c)); dialog.show()
        dialog.window?.apply { setBackgroundDrawableResource(android.R.color.transparent); setLayout(min(activity.resources.displayMetrics.widthPixels-dp(24),dp(880)),activity.resources.displayMetrics.heightPixels-dp(24)); val a=attributes; a.screenBrightness=if(red) 0.03f else -1f; attributes=a
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
    }
    private fun flashlight() {
        val dialog=Dialog(activity,android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val c=column().apply { setBackgroundColor(Color.rgb(200,0,0)); gravity=Gravity.CENTER }
        val label=text("Linterna roja · toca el fondo para salir",24f).apply { setTextColor(Color.rgb(60,0,0)) }; c.addView(label)
        val seek=SeekBar(activity).apply { max=100; progress=20; minimumHeight=dp(56); progressTintList=android.content.res.ColorStateList.valueOf(Color.rgb(100,0,0)); thumbTintList=android.content.res.ColorStateList.valueOf(Color.rgb(70,0,0)) }
        c.addView(seek,LinearLayout.LayoutParams(-1,dp(72)).apply { setMargins(dp(50),dp(8),dp(50),dp(8)) })
        c.setOnClickListener { dialog.dismiss(); go("Herramientas") }; label.setOnClickListener { dialog.dismiss(); go("Herramientas") }
        seek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?,progress: Int,fromUser: Boolean) { dialog.window?.apply { val a=attributes; a.screenBrightness=max(0.01f,progress/100f); attributes=a } }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        dialog.setContentView(c); dialog.show(); dialog.window?.apply { setLayout(-1,-1); val a=attributes; a.screenBrightness=0.2f; attributes=a
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
    }
}
