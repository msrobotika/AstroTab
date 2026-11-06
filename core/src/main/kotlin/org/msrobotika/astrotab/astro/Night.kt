package org.msrobotika.astrotab.astro

import org.msrobotika.astrotab.data.*
import kotlin.math.*

data class NightHour(val time: Long, val cloud: Double?, val sunAltitude: Double, val moonAltitude: Double,
    val illumination: Double, val darkFactor: Double, val moonFactor: Double, val quality: Double?, val galacticVisible: Boolean,
    val weather: Cloud?=null, val dewFactor: Double=1.0, val windFactor: Double=1.0)
data class NightWindow(val start: Long, val end: Long, val score: Int, val hours: List<NightHour>)
object Night {
    fun hour(time: Long, now: Long, location: Location, clouds: CloudData?, networkEnabled: Boolean=true): NightHour {
        val d=Astro.dayNumber(time); val lst=Astro.gmst(d)+location.longitude
        val sun=Astro.sun(d); val sa=Astro.toHorizon(sun.ra,sun.dec,lst,location.latitude).alt
        val ma=Astro.moonHorizon(d,lst,location.latitude).alt
        val illumination=Astro.moonPhase(d).illumination
        val w=DataPolicy.weatherAt(clouds,time,now,location,networkEnabled)
        val base=factors(time,w?.cover,sa,ma,illumination,
            Astro.toHorizon(266.405,-28.936,lst,location.latitude).alt)
        val dew=Weather.dewFactor(w?.temperature,w?.dewPoint); val wind=Weather.windFactor(w?.wind)
        return base.copy(quality=base.quality?.times(dew*wind),weather=w,dewFactor=dew,windFactor=wind)
    }
    /** Fórmula original sin sustituir nubes ausentes por un valor ficticio. */
    fun factors(time: Long, cloud: Double?, sunAlt: Double, moonAlt: Double, illumination: Double, galacticAlt: Double): NightHour {
        val dark=if(sunAlt < -18) 1.0 else if(sunAlt < -12) 0.5 else 0.0
        val moon=if(moonAlt>0) illumination*(0.5+0.5*min(1.0,moonAlt/30)) else 0.0
        val quality=cloud?.let { (1-it/100)*(1-0.8*moon)*dark }
        return NightHour(time,cloud,sunAlt,moonAlt,illumination,dark,moon,quality,galacticAlt>10 && sunAlt < -18)
    }
    /** Cada muestra representa una hora: se necesitan dos horas consecutivas con datos. */
    fun bestWindow(hours: List<NightHour>, now: Long, bortle: Int): NightWindow? {
        val sorted=hours.sortedBy { it.time }
        var best: NightWindow?=null; var bestQuality=-1.0
        for(i in 0 until sorted.size-1) {
            val a=sorted[i]; val b=sorted[i+1]
            if(a.time<now || b.time-a.time!=HOUR || a.quality==null || b.quality==null) continue
            val quality=(a.quality+b.quality)/2
            if(quality>bestQuality) {
                bestQuality=quality
                best=NightWindow(a.time,a.time+2*HOUR,(quality*(1-(bortle.coerceIn(1,9)-1)*0.06)*10).roundToInt(),listOf(a,b))
            }
        }
        return best
    }
    fun darknessLabel(hours: List<NightHour>): String = when {
        hours.isEmpty() -> "Sin intervalo calculado"
        hours.all { it.sunAltitude < -18 } -> "Noche astronómica durante todo el intervalo (posible noche polar)"
        hours.none { it.sunAltitude < -18 } -> "Sin noche astronómica en el intervalo"
        else -> "Hay noche astronómica (Sol por debajo de −18°)"
    }
}
object Aurora {
    fun threshold(latitude: Double, longitude: Double) = Astro.kpNeeded(Astro.geomagLat(latitude,longitude))
    fun currentAlert(data: KpData?, now: Long, location: Location): Boolean {
        val need=threshold(location.latitude,location.longitude)
        val current=DataPolicy.currentKp(data,now) ?: return false
        return need<=9 && current.value>=need
    }
}
