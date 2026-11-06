package org.msrobotika.astrotab.astro

import org.msrobotika.astrotab.data.*
import org.msrobotika.astrotab.ui.theme.SkyControls
import java.io.InputStream
import java.util.*
import kotlin.math.*

data class SkyEvent(val time: Long,val title: String,val kind: String,val detail: String,val highlighted: Boolean=false,val summary: String="")
data class Eclipse(val time: Long,val solar: Boolean,val type: String,val catalog: String)

object EventCalendar {
    fun eclipses(input: InputStream): List<Eclipse> = input.bufferedReader().useLines { lines ->
        lines.filter {it.isNotBlank() && !it.startsWith('#')}.map { val r=it.split('\t'); require(r.size==5)
            Eclipse(Utc.parse(r[0]),r[1]=="solar",r[2],r[3]) }.toList()
    }
    fun eclipseEvent(e: Eclipse,place: Location?): SkyEvent {
        val kind=when(e.type.first()) {'T' -> "total"; 'A' -> "anular"; 'H' -> "híbrido"; 'N' -> "penumbral"; else -> "parcial"}
        val altitude=place?.let { if(e.solar) AstroPhoto.horizon(Astro.sun(Astro.dayNumber(e.time)),e.time,it).alt else Astro.moonHorizon(Astro.dayNumber(e.time),Astro.gmst(Astro.dayNumber(e.time))+it.longitude,it.latitude).alt }
        val visibility=when { altitude==null -> "Configura ubicación para comprobar horizonte"; altitude<=0 -> "No visible en el máximo global: astro bajo el horizonte"; !e.solar -> "Visible en el máximo global si el cielo y horizonte lo permiten"; else -> "Sol sobre horizonte: visibilidad local por confirmar; la altura no demuestra que la sombra pase por aquí" }
        return SkyEvent(e.time,"Eclipse ${if(e.solar) "solar" else "lunar"} $kind","eclipse",
            "NASA/GSFC, Fred Espenak, catálogo ${e.catalog}. Máximo global UTC (TD−ΔT de tabla). $visibility. ${altitude?.let { "Altura ${org.msrobotika.astrotab.ui.theme.DisplayFormat.number(it,"°",1)}." } ?: ""} En eclipses solares la franja local requiere elementos besselianos; esta comprobación es solo de horizonte.",true)
    }
    private fun angle(x: Double)=Astro.rev(x+180)-180
    private fun refine(loTime: Long,hiTime: Long,maximum: Boolean,f: (Long)->Double): Long {
        var lo=loTime; var hi=hiTime
        while(hi-lo>MINUTE) { val a=lo+(hi-lo)/3; val b=hi-(hi-lo)/3
            if((f(a)<f(b))==maximum) lo=a else hi=b
        }
        return (lo+hi)/2
    }
    private fun longitude(eq: Astro.Eq,d: Double): Double = Astro.rev(Astro.atan2d(Astro.sind(eq.ra)*Astro.cosd(Astro.obliquity(d))+Astro.tanDec(eq.dec)*Astro.sind(Astro.obliquity(d)),Astro.cosd(eq.ra)))
    private fun extrema(start: Long,end: Long,maximum: Boolean,step: Long=3*HOUR,f: (Long)->Double): List<Long> {
        val result=mutableListOf<Long>(); var t=start-step; var a=f(t); var b=f(t+step)
        while(t+step<end) { val c=f(t+2*step)
            if(if(maximum) b>a && b>=c else b<a && b<=c) {
                val peak=refine(t,t+2*step,maximum,f)
                if(peak in start until end && (result.lastOrNull()?.let {peak-it>HOUR}!=false)) result.add(peak)
            }
            t+=step; a=b; b=c
        }; return result
    }
    fun month(now: Long,zone: TimeZone,place: Location?,eclipses: List<Eclipse>): List<SkyEvent> {
        val cal=Calendar.getInstance(zone).apply {timeInMillis=now; set(Calendar.DAY_OF_MONTH,1); set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}
        val start=cal.timeInMillis; cal.add(Calendar.MONTH,1); val end=cal.timeInMillis
        val events=mutableListOf<SkyEvent>()
        val accuracy="Modelo AstroCYD/Schlyter existente: orientativo, error de horas y separación ~1°; resolución numérica 1 min, sin precisión de almanaque."
        for((target,name) in listOf(0.0 to "Luna nueva",90.0 to "Cuarto creciente",180.0 to "Luna llena",270.0 to "Cuarto menguante")) {
            var t=start-3*HOUR; var previous=angle(Astro.moonPhase(Astro.dayNumber(t)).age-target)
            while(t<end) { val next=t+3*HOUR; val v=angle(Astro.moonPhase(Astro.dayNumber(next)).age-target)
                if(previous<=0 && v>0 && v-previous<180) {
                    var lo=t; var hi=next
                    while(hi-lo>MINUTE) { val mid=(lo+hi)/2; if(angle(Astro.moonPhase(Astro.dayNumber(mid)).age-target)>0) hi=mid else lo=mid }
                    val event=(lo+hi)/2
                    if(event in start until end) events.add(SkyEvent(event,name,"moon",accuracy,target==0.0 || target==180.0))
                }; t=next; previous=v
            }
        }
        for(maximum in listOf(false,true)) for(t in extrema(start,end,maximum) {Astro.moon(Astro.dayNumber(it)).eq.dist})
            events.add(SkyEvent(t,if(maximum) "Apogeo lunar" else "Perigeo lunar","moon","${(Astro.moon(Astro.dayNumber(t)).eq.dist*6378.137).roundToInt()} km. $accuracy",summary="Distancia ${(Astro.moon(Astro.dayNumber(t)).eq.dist*6378.137).roundToInt()} km"))
        fun eq(index: Int,t: Long)=if(index== -1) Astro.moon(Astro.dayNumber(t)).eq else Astro.planet(index,Astro.dayNumber(t)).eq
        fun label(index: Int)=if(index== -1) "Luna" else ProAstro.planetNames[index]
        for(a in -1..3) for(b in a+1..4) {
            fun sep(t: Long)=AstroPhoto.separation(eq(a,t),eq(b,t))
            for(t in extrema(start,end,false,f=::sep)) if(sep(t)<5)
                events.add(SkyEvent(t,"${label(a)} + ${label(b)}","conjunction","Conjunción aparente ${org.msrobotika.astrotab.ui.theme.DisplayFormat.number(sep(t),"°",1)} (<5°), mínimo angular geocéntrico. $accuracy",true,
                    "Separación ${org.msrobotika.astrotab.ui.theme.DisplayFormat.number(sep(t),"°",1)}"+place?.let {
                        val h=AstroPhoto.horizon(eq(a,t),t,it)
                        if(h.alt>0) " · ${org.msrobotika.astrotab.ui.theme.DisplayFormat.number(h.alt,"°")} sobre el horizonte ${SkyControls.cardinals[((h.az+22.5)/45).toInt()%8]}" else " · bajo el horizonte en el mínimo"
                    }.orEmpty()))
        }
        for(p in 0..4) {
            fun elong(t: Long)=AstroPhoto.separation(eq(p,t),Astro.sun(Astro.dayNumber(t)))
            if(p<2) for(t in extrema(start,end,true,f=::elong)) {
                val d=Astro.dayNumber(t); val east=angle(longitude(eq(p,t),d)-Astro.sunState(d).lon)>0
                events.add(SkyEvent(t,"${label(p)} · máxima elongación ${if(east) "este" else "oeste"}","planet","${org.msrobotika.astrotab.ui.theme.DisplayFormat.number(elong(t),"°",1)} del Sol. $accuracy",true))
            } else {
                var t=start-3*HOUR
                fun diff(time: Long): Double {val d=Astro.dayNumber(time);return angle(longitude(eq(p,time),d)-Astro.sunState(d).lon-180)}
                while(t<end) { val a=diff(t); val b=diff(t+3*HOUR)
                    if(a*b<=0 && abs(a-b)<180) {var lo=t;var hi=t+3*HOUR;while(hi-lo>MINUTE){val mid=(lo+hi)/2;if((diff(mid)>0)==(a>0))lo=mid else hi=mid};val event=(lo+hi)/2
                        if(event in start until end)events.add(SkyEvent(event,"${label(p)} · oposición","planet",accuracy,true))}
                    t+=3*HOUR
                }
            }
        }
        val year=Calendar.getInstance(zone).apply {timeInMillis=start}.get(Calendar.YEAR)
        for(s in ProAstro.showers) { val t=Calendar.getInstance(zone).apply {clear();set(year,s.month-1,s.day,12,0)}.timeInMillis
            if(t in start until end)events.add(SkyEvent(t,s.name,"meteor","Máximo climatológico IMO, fecha aproximada sin hora anual calculada. THZ ${s.zhr}; no es una tasa garantizada local.",true,"Máximo aproximado · THZ ${s.zhr}")) }
        events.addAll(eclipses.filter {it.time in start until end}.map {eclipseEvent(it,place)})
        return events.sortedBy {it.time}
    }
}

private fun Astro.tanDec(dec: Double)=tan(dec*RAD)
