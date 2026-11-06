package org.msrobotika.astrotab.ui

import android.app.*
import android.view.*
import android.widget.*
import org.msrobotika.astrotab.*
import org.msrobotika.astrotab.astro.*
import org.msrobotika.astrotab.data.*
import org.msrobotika.astrotab.satellites.*
import org.msrobotika.astrotab.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService

class ProBState {
    var sessionId: String?=null
    var monthOffset=0
    var pass: SatellitePass?=null
    var onlyIss=false
    var minimumPassAltitude=10
    var satellitePage=0
    var alarmSound=false
    val eventCache=mutableMapOf<String,List<SkyEvent>>()
}
class ProBScreens(private val activity: Activity,private val settings: Settings,private val store: PreferenceStore,
    private val sessions: SessionStore,private val satellites: SatelliteRepository,private val worker: ExecutorService,
    private val state: ProBState,private val alive: ()->Boolean,private val go: (String)->Unit,
    private val night: (((NightTimeline,List<ObjectPlan>)->Unit))->Unit,
    private val predict: (((Int,Int,List<SatellitePass>)->Unit))->Unit,private val refresh: ()->Unit,
    private val startBlock: (SessionBlock)->Unit) {
    private val w=Widgets(activity,Palette(settings.red));private val p=w.palette;private val now=System.currentTimeMillis()
    private fun date(t: Long,pattern: String="dd/MM HH:mm")=SimpleDateFormat(pattern,Locale.forLanguageTag("es-ES")).apply {timeZone=settings.zone}.format(Date(t))
    private fun button(label: String,action: ()->Unit)=Button(activity).apply {text=label;isAllCaps=false;textSize=16f;setTextColor(p.text);background=w.shape(p.raised);minimumHeight=w.dp(56)
        layoutParams=LinearLayout.LayoutParams(-1,w.dp(64)).apply {setMargins(w.dp(4),w.dp(4),w.dp(4),w.dp(4))};setOnClickListener {action()}}
    private fun card(c: LinearLayout,label: String,summary: String,detail: String,icon: Icon=Icon.STAR): LinearLayout {
        val box=w.column().apply {background=w.shape();setPadding(w.dp(8),w.dp(4),w.dp(8),w.dp(4))}
        val row=w.row();row.addView(IconView(activity,icon,p),LinearLayout.LayoutParams(w.dp(40),w.dp(40)));row.addView(w.section(label,detail),LinearLayout.LayoutParams(0,-2,1f));box.addView(row);if(summary.isNotBlank())box.addView(w.text(summary))
        c.addView(box,LinearLayout.LayoutParams(-1,-2).apply {setMargins(4,4,4,8)});return box
    }
    private fun actions(c: LinearLayout,vararg items: Pair<String,()->Unit>) {val r=w.row();items.forEach {(s,f)->r.addView(button(s,f),LinearLayout.LayoutParams(0,w.dp(64),1f).apply {setMargins(4,4,4,4)})};c.addView(r)}
    private fun editName(label: String,initial: String,save: (String)->Unit) {
        var value=initial;val c=w.column();val display=w.text(value,24f);c.addView(display)
        val d=Dialog(activity);d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        for(keys in listOf("1234567890","QWERTYUIOP","ASDFGHJKLÑ","ZXCVBNM"))actions(c,*keys.map {ch->ch.toString() to {if(value.length<60)value+=ch;display.text=value}}.toTypedArray())
        actions(c,"Espacio" to {if(value.length<60)value+=" ";display.text=value},"Borrar" to {value=value.dropLast(1);display.text=value},"Limpiar" to {value="";display.text=value})
        actions(c,"Cancelar" to {d.dismiss()},"Guardar" to {if(value.isNotBlank()){save(value.trim());d.dismiss()}})
        c.setPadding(12,12,12,12);c.setBackgroundColor(p.background);d.setContentView(ScrollView(activity).apply {addView(c)});d.setTitle(label);d.show();d.window?.setLayout(w.dp(940),w.dp(500))
    }
    fun render(name: String): View {
        val c=w.column();c.addView(w.heading(name))
        when(name) {
            "Más" -> {listOf("Satélites" to Icon.SATELLITE,"Eventos" to Icon.CALENDAR,"Plan de sesión" to Icon.TELESCOPE,"Sesión" to Icon.CAMERA,"Equipos" to Icon.CAMERA,"Alarmas" to Icon.BELL,"Luna" to Icon.MOON,"Auroras" to Icon.AURORA).chunked(3).forEach {group-> val r=w.row();group.forEach {(s,i)->r.addView(button(s){go(s)}.apply {setCompoundDrawablesWithIntrinsicBounds(null,IconDrawable(i,p.accent,w.dp(32)),null,null);compoundDrawablePadding=w.dp(8);setPadding(w.dp(8),w.dp(8),w.dp(8),w.dp(8))},LinearLayout.LayoutParams(0,w.dp(88),1f).apply {setMargins(4,4,4,4)})};c.addView(r)}}
            "Satélites" -> satelliteList(c)
            "Paso" -> pass(c)
            "Eventos" -> events(c)
            "Equipos" -> equipment(c)
            "Plan de sesión" -> planner(c)
            "Alarmas" -> alarms(c)
        }
        return ScrollView(activity).apply {isFillViewport=true;addView(c)}
    }
    private fun cardinal(q: SatellitePoint)=SkyControls.cardinals[((q.azimuth+22.5)/45).toInt()%8]
    private fun visibility(pass: SatellitePass): String {
        val visible=pass.track.filter {it.visible};if(visible.isEmpty())return "Sin tramo visible"
        val first=visible.first();val last=visible.last()
        val after=pass.track.firstOrNull {it.time>last.time}
        val ending=if(after!=null && !after.illuminated) " · entra en sombra a las "+date(last.time,"HH:mm:ss") else ""
        return "Visible "+date(first.time,"HH:mm:ss")+" → "+date(last.time,"HH:mm:ss")+ending
    }
    private fun satelliteList(c: LinearLayout) {
        card(c,"Pasos visibles",satellites.fetchedAt?.let {"TLE "+date(it)+" · "+if(satellites.old(now))"datos viejos" else "caché local"} ?: "Sin TLE guardados",
            "CelesTrak. "+satellites.status+". Actualización automática tras un día con red activa. Cinco días, Sol <−6°, satélite iluminado. Filtro por máximo visible. Sin magnitud: tamaño y albedo desconocidos. Trayectoria completa: gris; parte visible: acento.",Icon.SATELLITE)
        actions(c,"Actualizar TLE" to refresh,(if(state.onlyIss) "Mostrar todos" else "Solo ISS") to {state.onlyIss=!state.onlyIss;state.satellitePage=0;go("Satélites")},
            *listOf(10,20,30).map {alt -> (if(state.minimumPassAltitude==alt) "✓ ≥ "+alt+"°" else "≥ "+alt+"°") to {state.minimumPassAltitude=alt;state.satellitePage=0;go("Satélites")}}.toTypedArray())
        val progress=w.text("Calculando · ISS primero…",14f,p.muted);c.addView(progress);val rows=w.column();c.addView(rows)
        predict {done,total,passes->if(alive()) {
            progress.text=if(total==0) "Configura ubicación y carga TLE con la red opcional." else (if(done==total)"Calculado: " else "Calculando: ")+done+"/"+total+" · próximos cinco días"
            rows.removeAllViews();val filtered=SatellitePassPolicy.select(passes,now,state.minimumPassAltitude,state.onlyIss)
            if(done==total && total>0 && filtered.isEmpty())rows.addView(w.text("Sin pasos visibles ≥ "+state.minimumPassAltitude+"° en los próximos cinco días."))
            for(pass in filtered.drop(state.satellitePage*20).take(20)) {
                val row=w.row().apply {background=w.shape();setPadding(w.dp(12),w.dp(10),w.dp(12),w.dp(10));minimumHeight=w.dp(142)
                    isClickable=true;isFocusable=true;contentDescription="Abrir paso "+pass.tle.name;setOnClickListener {state.pass=pass;go("Paso")}}
                row.addView(IconView(activity,Icon.SATELLITE,p),LinearLayout.LayoutParams(w.dp(36),w.dp(36)))
                val data=w.column();data.addView(w.text(if(pass.tle.number==25544)"Estación Espacial · ISS" else pass.tle.name,18f,p.text))
                val headline=w.row();headline.addView(w.text(date(pass.start.time,"HH:mm:ss"),30f,p.accent));headline.addView(w.text(date(pass.start.time,"EEE dd/MM"),14f,p.muted))
                val altitude=SatellitePassPolicy.visiblePeak(pass)!!.altitude
                headline.addView(w.text((if(altitude>=60)"Alto" else if(altitude>=30)"Medio" else "Bajo")+" · "+DisplayFormat.number(altitude,"°"),14f,p.accent).apply {background=w.shape(p.raised)})
                data.addView(headline)
                data.addView(w.text("Inicio "+date(pass.start.time,"HH:mm:ss")+" "+cardinal(pass.start)+" → Máx "+date(pass.maximum.time,"HH:mm:ss")+" "+cardinal(pass.maximum)+" → Fin "+date(pass.end.time,"HH:mm:ss")+" "+cardinal(pass.end),14f,p.muted))
                data.addView(w.text(visibility(pass),14f,p.accent));row.addView(data,LinearLayout.LayoutParams(0,-2,1f))
                row.addView(PassPolar(activity,pass,settings.red,true),LinearLayout.LayoutParams(w.dp(116),w.dp(116)))
                rows.addView(row,LinearLayout.LayoutParams(-1,-2).apply {setMargins(4,4,4,8)})
            }
            if(filtered.size>20)actions(rows,"Anteriores" to {state.satellitePage=(state.satellitePage-1).coerceAtLeast(0);go("Satélites")},"Siguientes" to {if((state.satellitePage+1)*20<filtered.size)state.satellitePage++;go("Satélites")})
        }}
    }
    private fun point(q: SatellitePoint)=date(q.time,"HH:mm:ss")+" · "+DisplayFormat.number(q.altitude,"° alt")+" · "+cardinal(q)
    private fun pass(c: LinearLayout) {
        val pass=state.pass ?: run {c.addView(w.text("Elige un paso en Satélites"));return}
        card(c,if(pass.tle.number==25544)"Estación Espacial · ISS" else pass.tle.name,
            "Inicio "+point(pass.start)+"   →   Máximo "+point(pass.maximum)+"   →   Fin "+point(pass.end),
            "Paso completo sobre horizonte geométrico. Gris: no visible; acento: iluminado con Sol <−6°. Norte arriba, este derecha, cenit centro. Sin magnitud calculada. Época TLE "+date(pass.tle.epoch)+if(pass.tle.old(now))" · datos viejos" else "",Icon.SATELLITE)
        c.addView(w.text(visibility(pass),18f,p.accent))
        c.addView(PassPolar(activity,pass,settings.red),LinearLayout.LayoutParams(-1,w.dp(224)))
        actions(c,"Ver en el cielo" to {go("Cielo")},"Avisar 5 min antes" to {if(pass.tle.number==25544) addAlarm(AlarmKind.ISS,"Estación Espacial en 5 minutos",pass.start.time-5*MINUTE)else w.sheet("Alarma","El aviso de pasos está disponible para la Estación Espacial.")})
    }
    private fun events(c: LinearLayout) {
        val cal=Calendar.getInstance(settings.zone).apply {timeInMillis=now;add(Calendar.MONTH,state.monthOffset)}
        actions(c,"Mes anterior" to {state.monthOffset--;go("Eventos")},date(cal.timeInMillis,"MMMM yyyy") to {state.monthOffset=0;go("Eventos")},"Mes siguiente" to {state.monthOffset++;go("Eventos")})
        val loading=w.text("Calculando calendario…");c.addView(loading)
        val at=cal.timeInMillis;val zone=settings.zone;val place=settings.location
        worker.execute {
            val key="${date(at,"yyyy-MM")}:${zone.id}:${place?.key}"
            val list=state.eventCache[key] ?: EventCalendar.month(at,zone,place,activity.assets.open("events/eclipses.tsv").let(EventCalendar::eclipses)).also {if(state.eventCache.size>18)state.eventCache.clear();state.eventCache[key]=it}
            activity.runOnUiThread {if(alive()) {c.removeView(loading)
                c.addView(w.section("Calendario orientativo","Fases y extremos: modelo astronómico de AstroTab, error orientativo de horas y ~1° en conjunciones; resolución de búsqueda 1 min no equivale a exactitud. Meteoros: climatología IMO. Eclipses 2026–2035 NASA; altura en máximo global, no circunstancias locales solares."))
                var week=""
                for(e in list) {
                    val monday=Calendar.getInstance(zone).apply {timeInMillis=e.time;add(Calendar.DAY_OF_MONTH,-(get(Calendar.DAY_OF_WEEK)+5)%7)}
                    val key=date(monday.timeInMillis,"yyyy-MM-dd")
                    if(key!=week){week=key;c.addView(w.text("Semana del "+date(monday.timeInMillis,"d MMMM"),18f,p.accent))}
                    val today=date(e.time,"yyyy-MM-dd")==date(now,"yyyy-MM-dd")
                    card(c,(if(today)"Hoy · " else date(e.time,"EEE d · "))+date(e.time,"HH:mm")+" · "+e.title,
                        e.summary,e.detail,when(e.kind){"moon"->Icon.MOON;"eclipse"->Icon.SUN;"planet","conjunction"->Icon.PLANET;else->Icon.CALENDAR})
                }
            }}
        }
    }
    private fun equipment(c: LinearLayout) {
        c.addView(w.text("Toca una tarjeta para activar el equipo",16f,p.muted))
        for(profile in settings.profiles) {
            val e=profile.equipment;val active=profile.id==settings.activeProfileId
            val box=card(c,profile.name+(if(active)" · Activo" else ""),
                DisplayFormat.number(e.focal," mm")+" · f/"+DisplayFormat.number(e.aperture,digits=1)+" · "+e.sensor.label,
                "Píxel "+DisplayFormat.number(e.pixel," µm",2)+". Las herramientas, cielo y plan usan el perfil activo.",Icon.CAMERA)
            if(active)box.background=w.shape(p.raised)
            box.isClickable=true;box.isFocusable=true;box.contentDescription="Activar equipo "+profile.name
            box.setOnClickListener {settings.activeProfileId=profile.id;go("Equipos")}
            box.addView(w.text("Campo "+DisplayFormat.number(Optics.field(e.width,e.focal),"°",1)+" × "+DisplayFormat.number(Optics.field(e.height,e.focal),"°",1)+" · "+(if(e.tracking)"Con seguimiento" else "Sin seguimiento")+" · "+(if(e.equatorial)"Ecuatorial" else "Altacimutal / fija"),20f,p.accent))
            actions(box,"Editar" to {equipmentSheet(profile)},"Cambiar nombre" to {editName("Nombre de equipo",profile.name){settings.saveProfile(profile.copy(name=it));go("Equipos")}})
        }
        c.addView(button("+ Nuevo perfil"){if(settings.profiles.size<32)editName("Nuevo equipo",""){val profile=EquipmentProfile(UUID.randomUUID().toString(),it,settings.equipment);settings.saveProfile(profile);equipmentSheet(profile)}})
    }
    private fun equipmentSheet(profile: EquipmentProfile) {
        var draft=profile.equipment
        val d=Dialog(activity);d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val c=w.column().apply {setPadding(w.dp(16),w.dp(12),w.dp(16),w.dp(12));setBackgroundColor(p.background)}
        fun render() {
            c.removeAllViews();c.addView(w.heading(profile.name))
            fun step(label: String,value: String,change: (Int)->Unit) {
                val row=w.row();row.addView(w.text(label+" · "+value,20f),LinearLayout.LayoutParams(0,-2,1f))
                for(delta in listOf(-1,1))row.addView(button(if(delta<0)"−" else "+"){change(delta);render()}.apply {contentDescription=(if(delta<0)"Reducir " else "Aumentar ")+label},LinearLayout.LayoutParams(w.dp(72),w.dp(56)).apply {setMargins(4,4,4,4)})
                c.addView(row)
            }
            step("Focal",DisplayFormat.number(draft.focal," mm")){draft=draft.copy(focal=(draft.focal+it*5).coerceIn(1.0,10000.0))}
            step("Apertura","f/"+DisplayFormat.number(draft.aperture,digits=1)){draft=draft.copy(aperture=(draft.aperture+it*0.1).coerceIn(0.5,64.0))}
            step("Píxel",DisplayFormat.number(draft.pixel," µm",2)){draft=draft.copy(pixel=(draft.pixel+it*0.1).coerceIn(0.1,100.0))}
            step("Sensor",draft.sensor.label){val values=SensorFormat.entries;draft=draft.copy(sensor=values[(draft.sensor.ordinal+it+values.size)%values.size])}
            if(draft.sensor==SensorFormat.MOBILE){
                step("Ancho sensor",DisplayFormat.number(draft.mobileWidth," mm",1)){draft=draft.copy(mobileWidth=(draft.mobileWidth+it*0.1).coerceIn(0.1,100.0))}
                step("Alto sensor",DisplayFormat.number(draft.mobileHeight," mm",1)){draft=draft.copy(mobileHeight=(draft.mobileHeight+it*0.1).coerceIn(0.1,100.0))}
            }
            c.addView(w.text("Campo "+DisplayFormat.number(Optics.field(draft.width,draft.focal),"°",1)+" × "+DisplayFormat.number(Optics.field(draft.height,draft.focal),"°",1),24f,p.accent))
            actions(c,(if(draft.tracking)"Seguimiento: sí" else "Seguimiento: no") to {draft=draft.copy(tracking=!draft.tracking);render()},(if(draft.equatorial)"Montura: ecuatorial" else "Montura: altacimutal / fija") to {draft=draft.copy(equatorial=!draft.equatorial);render()})
            actions(c,"Cancelar" to {d.dismiss()},"Guardar" to {settings.saveProfile(profile.copy(equipment=draft));d.dismiss();go("Equipos")})
            if(settings.profiles.size>1)c.addView(button("Eliminar perfil"){settings.removeProfile(profile.id);d.dismiss();go("Equipos")})
        }
        render();d.setContentView(ScrollView(activity).apply {addView(c)});d.show()
        d.window?.apply {setBackgroundDrawableResource(android.R.color.transparent);setLayout((activity.resources.displayMetrics.widthPixels-w.dp(48)).coerceAtMost(w.dp(900)),activity.resources.displayMetrics.heightPixels-w.dp(40));val a=attributes;a.screenBrightness=if(settings.red)0.03f else -1f;attributes=a}
    }
    private fun currentSession(): ObservingSession?=sessions.read().let {all->all.firstOrNull {it.id==state.sessionId} ?: all.firstOrNull()?.also {state.sessionId=it.id}}
    private fun planner(c: LinearLayout) {
        val session=currentSession()
        val preferences=activity.getSharedPreferences("session-plan",0)
        val gap=preferences.getInt("object-gap-minutes",SessionPlanner.DEFAULT_GAP_MINUTES).coerceIn(0,60)
        fun changeGap(value: Int) {preferences.edit().putInt("object-gap-minutes",value.coerceIn(0,60)).apply();go("Plan de sesión")}
        actions(c,"Nueva sesión" to {editName("Nombre de sesión",""){val s=ObservingSession(UUID.randomUUID().toString(),it,now,emptyList());sessions.save(s);state.sessionId=s.id;go("Plan de sesión")}},"Elegir objetos" to {go("Objetos")},"Temporizador" to {go("Sesión")})
        actions(c,"Margen − 5 min" to {changeGap(gap-5)},"Margen: $gap min" to {w.sheet("Margen entre objetos","Tiempo para cambiar de objeto: de 0 a 60 min. La propuesta automática reparte la oscuridad útil entre ventanas, hasta 4 h de tiempo real por objeto, incluidas las pausas entre tomas. Puedes ajustar la integración con +/−.")},"Margen + 5 min" to {changeGap(gap+5)})
        for(s in sessions.read())if(s.id!=session?.id)c.addView(button("Abrir ${s.name} · ${date(s.night,"dd/MM")}"){state.sessionId=s.id;go("Plan de sesión")})
        if(session==null || session.requests.isEmpty()){
            val empty=w.column().apply {background=w.shape();setPadding(w.dp(24),w.dp(20),w.dp(24),w.dp(20))}
            empty.addView(IconView(activity,Icon.TELESCOPE,p),LinearLayout.LayoutParams(w.dp(64),w.dp(64)))
            empty.addView(w.heading("Tu próxima noche empieza aquí"));empty.addView(w.text("Repartimos la oscuridad útil entre tres objetos variados; añadimos más si hacen falta. Después puedes ajustar su integración, cambiarlos o quitarlos.",20f))
            empty.addView(button("Planificar esta noche") {
                if(settings.location==null){go("Ajustes")}else night {_,plans -> if(alive()) {
                    val pref=activity.getSharedPreferences("session-plan",0)
                    val requests=SessionPlanner.automatic(plans,pref.getFloat("exposure",30f).toDouble(),pref.getFloat("pause",5f).toDouble(),settings.equipment,gap)
                    if(requests.isEmpty())w.sheet("Sin ventanas esta noche","No hay ventanas de altura y oscuridad suficientes. Cambia ubicación o altura mínima en Ajustes/Objetos.")
                    else {val proposed=ObservingSession(session?.id ?: UUID.randomUUID().toString(),session?.name ?: "Noche "+date(now,"dd/MM"),now,requests);sessions.save(proposed);state.sessionId=proposed.id;go("Plan de sesión")}
                }}
            });c.addView(empty);return
        }
        card(c,session.name,"${session.requests.size} objetos · equipo ${settings.activeProfile.name}","Ventanas de oscuridad astronómica y altura mínima de Objetos. Cobertura útil con factores de altura/Luna y proximidad al meridiano. Con seguimiento: exposición de Sesión. Sin seguimiento: el menor valor entre Sesión y NPF para la declinación de cada objeto. Pausa de Sesión y margen de $gap min entre objetos. Los bloques no se solapan. Sin ventana: no programado. Revisa volteo y necesidades reales de montura.",Icon.TELESCOPE)
        val resultView=w.column();c.addView(resultView)
        actions(c,"Noche anterior" to {val d=Calendar.getInstance(settings.zone).apply {timeInMillis=session.night;add(Calendar.DAY_OF_MONTH,-1)};sessions.save(session.copy(night=d.timeInMillis));go("Plan de sesión")},"Noche siguiente" to {val d=Calendar.getInstance(settings.zone).apply {timeInMillis=session.night;add(Calendar.DAY_OF_MONTH,1)};sessions.save(session.copy(night=d.timeInMillis));go("Plan de sesión")})
        actions(c,"Cambiar nombre" to {editName("Sesión",session.name){sessions.save(session.copy(name=it));go("Plan de sesión")}},"Usar noche de hoy" to {sessions.save(session.copy(night=now));go("Plan de sesión")},"Eliminar sesión" to {sessions.remove(session.id);go("Plan de sesión")})
        for(r in session.requests)actions(c,"${r.id} · ${r.integrationMinutes} min −" to {sessions.save(session.copy(requests=session.requests.map {if(it.id==r.id)it.copy(integrationMinutes=(it.integrationMinutes-5).coerceAtLeast(1))else it}));go("Plan de sesión")},"+ 5 min" to {sessions.save(session.copy(requests=session.requests.map {if(it.id==r.id)it.copy(integrationMinutes=(it.integrationMinutes+5).coerceAtMost(720))else it}));go("Plan de sesión")},"Cambiar" to {changeTarget(session,r)},"Quitar" to {sessions.save(session.copy(requests=session.requests.filter {it.id!=r.id}));go("Plan de sesión")})
        val loading=w.text("Calculando orden de la noche…");c.addView(loading)
        night {timeline,plans->if(alive()) {
            c.removeView(loading)
            val pref=activity.getSharedPreferences("session-plan",0);val exposure=pref.getFloat("exposure",30f).toDouble();val pause=pref.getFloat("pause",5f).toDouble()
            // La noche guardada se recalcula al abrir; el usuario puede conservar varias listas para noches futuras.
            worker.execute {val place=settings.location ?: return@execute
                val ref=SkyControls.nightReference(session.night,settings.zone,place)
                val n=if(AstroPhoto.nightBounds(ref,settings.zone).start==timeline.start)timeline else AstroPhoto.timeline(ref,settings.zone,place)
                val pp=if(n===timeline)plans else plans.map {AstroPhoto.plan(it.target,n,place,settings.minimumAltitude)}
                val result=SessionPlanner.propose(session.requests,pp,exposure,pause,settings.equipment,gap)
                activity.runOnUiThread {if(alive()) {
                    val occupied=result.blocks.sumOf {it.end-it.start}
                    val dark=n.dark.sumOf {it.end-it.start}
                    resultView.addView(w.text("Noche ${date(n.start)} → ${date(n.end)} · ${if(dark>0)occupied*100/dark else 0} % de oscuridad ocupado · margen $gap min"))
                    resultView.addView(SessionTimeline(activity,n,result,settings.red,settings.zone),LinearLayout.LayoutParams(-1,w.dp(120)))
                    if(result.blocks.any {b ->b.warnings.any {it.contains("meridiano")}})resultView.addView(w.text("│ Marca de meridiano · revisa el volteo de la montura",14f,p.warning))
                    for(b in result.blocks){card(resultView,b.id,"${date(b.start,"HH:mm")} → ${date(b.end,"HH:mm")} · ${b.frames} tomas · ${DisplayFormat.number(b.exposure," s",2)} por toma · ${DisplayFormat.duration(b.integrationSeconds/3600)} integración",
                        "Exposición por toma: ${DisplayFormat.number(b.exposure," s",2)}. Con seguimiento: valor de Sesión. Sin seguimiento: min(Sesión, NPF del equipo a la declinación del objeto). NPF = (35 × apertura + 30 × píxel µm) / (focal mm × cos δ). El tiempo real incluye pausas entre tomas.\n"+b.warnings.joinToString("\n").ifEmpty {"Ventana de altura y oscuridad válida. Sin aviso lunar/meridiano."},Icon.CAMERA)
                        if(b.warnings.isNotEmpty())resultView.addView(w.text(b.warnings.joinToString(" · "),16f,p.warning))
                        resultView.addView(button("Iniciar Sesión con ${b.id}"){startBlock(b)})}
                    if(result.unscheduled.isNotEmpty())resultView.addView(w.text("Sin hueco útil: ${result.unscheduled.joinToString()}"))
                }}
            }
        }}
    }
    private fun changeTarget(session: ObservingSession,request: TargetRequest) {
        night {_,plans -> if(alive()) {
            val d=Dialog(activity);d.requestWindowFeature(Window.FEATURE_NO_TITLE)
            val c=w.column().apply {setBackgroundColor(p.background);setPadding(16,12,16,12)}
            c.addView(w.heading("Cambiar "+request.id))
            plans.filter {it.windows.isNotEmpty() && session.requests.none {r -> r.id==it.target.id}}.sortedByDescending {it.score}.take(20).forEach {plan ->
                c.addView(button(plan.target.label+" · "+plan.score+"/100") {sessions.save(session.copy(requests=session.requests.map {if(it.id==request.id)request.copy(id=plan.target.id)else it}));d.dismiss();go("Plan de sesión")})
            }
            c.addView(button("Cancelar"){d.dismiss()});d.setContentView(ScrollView(activity).apply {addView(c)});d.show();d.window?.setLayout(w.dp(800),w.dp(460))
        }}
    }
    private fun addAlarm(kind: AlarmKind,label: String,at: Long?,target: String?=null) {
        if(kind!=AlarmKind.KP && (at==null || at<=now)){w.sheet("Sin evento futuro","No hay un próximo cruce o paso disponible en el intervalo calculado.");return}
        val alarm=AlarmStore(store).add(kind,label,at,state.alarmSound,target)
        val exact=AndroidAlarms.schedule(activity,alarm)
        w.sheet("Alarma guardada",label+(at?.let {" · ${date(it)}"} ?: " · espera datos Kp vigentes")+if(exact) "" else "\nEste Android restringe alarmas exactas: aviso visible y alarma inexacta sin permisos nuevos.")
    }
    private fun alarms(c: LinearLayout) {
        c.addView(w.section("Avisos en AstroTab","API23: AlarmManager setExact RTC_WAKEUP y receiver propio. Sin permisos nuevos. Tras reiniciar el dispositivo se reprograman al abrir AstroTab. En Android moderno el SO puede limitar exactitud/apertura desde detrás; se recuperan avisos al volver en los siguientes 30 min. Kp se evalúa con datos vigentes mientras la app funciona. Sonido opcional y rojo integral."))
        c.addView(button(if(state.alarmSound) "Sonido para nuevas alarmas: sí" else "Sonido para nuevas alarmas: no"){state.alarmSound=!state.alarmSound;go("Alarmas")})
        val saved=AlarmStore(store).read().sortedWith(compareBy<AppAlarm> {!it.enabled}.thenBy {it.time ?: Long.MAX_VALUE})
        if(saved.isEmpty())card(c,"Aún no hay alarmas","Elige abajo el fenómeno del que quieras recibir un aviso.","Se guardan localmente, también cuando cierras AstroTab.",Icon.BELL)
        for(a in saved) {
            val dateLabel=a.time?.let {(if(date(it,"yyyy-MM-dd")==date(now,"yyyy-MM-dd"))"Hoy " else date(it,"EEE dd/MM "))+date(it,"HH:mm")} ?: "Al alcanzar el umbral Kp"
            val box=card(c,a.label,"",if(a.sound)"Sonido activado" else "Aviso silencioso",Icon.BELL)
            val row=w.row();row.addView(w.text(dateLabel,24f,p.accent),LinearLayout.LayoutParams(0,-2,1f))
            row.addView(Switch(activity).apply {text=if(a.enabled)"Activa" else "Inactiva";textSize=18f;setTextColor(if(a.enabled)p.accent else p.muted);isChecked=a.enabled;minHeight=w.dp(56);contentDescription="Estado de "+a.label
                thumbTintList=android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(p.accent,p.muted))
                trackTintList=android.content.res.ColorStateList.valueOf(p.raised)
                setOnCheckedChangeListener {_,enabled ->
                    val updated=AlarmStore(store).setEnabled(a.id,enabled) ?: return@setOnCheckedChangeListener
                    if(enabled)AndroidAlarms.schedule(activity,updated)else AndroidAlarms.cancel(activity,a.id);go("Alarmas")
                }})
            row.addView(button("Borrar alarma"){AndroidAlarms.cancel(activity,a.id);AlarmStore(store).remove(a.id);go("Alarmas")},LinearLayout.LayoutParams(w.dp(160),w.dp(64)).apply {leftMargin=w.dp(16)})
            box.addView(row)
        }
        if(settings.location==null){c.addView(w.text("Configura ubicación en Ajustes para añadir avisos."));return}
        val loading=w.text("Preparando próximos eventos…");c.addView(loading)
        night {_,plans->if(alive()) {
            c.removeView(loading)
            actions(c,"Fin crepúsculo astro" to {nextNightAlarm(AlarmKind.ASTRONOMICAL_DUSK,"Fin del crepúsculo astronómico")},"Salida de la Luna" to {nextNightAlarm(AlarmKind.MOONRISE,"Salida de la Luna")})
            c.addView(button("Kp alcanza el necesario"){addAlarm(AlarmKind.KP,"Kp alcanza el umbral local",null)})
            c.addView(button("Estación Espacial · 5 min antes"){
                var added=false
                predict {done,total,passes->if(alive() && !added && (passes.any {it.tle.number==25544} || done==total)) {
                    added=true;addAlarm(AlarmKind.ISS,"Estación Espacial en 5 minutos",AlarmPolicy.next(AlarmKind.ISS,now,emptyList(),emptyList(),SatellitePassPolicy.select(passes,now,10,true)))
                }}
            })
            // Selección explícita de objeto desde ficha evita una lista de 110 botones aquí.
            c.addView(button("Elegir objeto para aviso de altura"){go("Objetos")})
        }}
    }
    private fun nextNightAlarm(kind: AlarmKind,label: String) {
        val place=settings.location ?: return
        worker.execute {val events=(0..2).flatMap {AstroPhoto.events(AstroPhoto.timeline(now+it*24*HOUR,settings.zone,place),place)}
            val at=AlarmPolicy.next(kind,now,events,emptyList(),emptyList())
            activity.runOnUiThread {if(alive())addAlarm(kind,label,at)} }
    }
}
