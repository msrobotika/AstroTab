package org.msrobotika.astrotab.astro

import org.msrobotika.astrotab.data.*
import java.util.*

data class PlanetNight(val index: Int, val name: String, val altitudes: DoubleArray, val rise: Long?, val transit: Long,
    val set: Long?, val maximum: Double, val magnitude: Double, val elongation: Double)
data class MoonDay(val day: Int, val time: Long, val age: Double, val illumination: Double, val moonlessHours: Double)
data class MeteorShower(val name: String, val month: Int, val day: Int, val zhr: Int, val ra: Double, val dec: Double)
data class MeteorTonight(val shower: MeteorShower, val peak: Long, val radiantAltitude: Double, val transit: Long, val illumination: Double, val moonAltitude: Double)

object ProAstro {
    val planetNames=listOf("Mercurio","Venus","Marte","Júpiter","Saturno")
    fun planets(night: NightTimeline, place: Location): List<PlanetNight> = planetNames.mapIndexed { p,name ->
        fun altitude(t: Long)=AstroPhoto.horizon(Astro.planet(p,Astro.dayNumber(t)).eq,t,place).alt
        val values=night.samples.map { altitude(it.time) }.toDoubleArray()
        fun crossing(rising: Boolean): Long? {
            for(i in 0 until values.lastIndex) {
                if(if(rising) values[i]<0 && values[i+1]>=0 else values[i]>0 && values[i+1]<=0) {
                    var lo=night.samples[i].time; var hi=night.samples[i+1].time
                    repeat(14) { val mid=(lo+hi)/2; if((altitude(mid)>=0)==rising) hi=mid else lo=mid }
                    return (lo+hi)/2
                }
            }
            return null
        }
        var transit=AstroPhoto.transit(Astro.planet(p,Astro.dayNumber(night.start)).eq,night.start,place)
        repeat(3) { transit=AstroPhoto.transit(Astro.planet(p,Astro.dayNumber(transit)).eq,night.start,place) }
        val d=Astro.dayNumber(transit); val state=Astro.planet(p,d)
        PlanetNight(p,name,values,crossing(true),transit,crossing(false),altitude(transit),state.magnitude,AstroPhoto.separation(state.eq,Astro.sun(d)))
    }
    fun moonMonth(now: Long, zone: TimeZone, place: Location, cancelled: ()->Boolean = { false }): List<MoonDay> {
        val c=Calendar.getInstance(zone).apply { timeInMillis=now; set(Calendar.DAY_OF_MONTH,1); set(Calendar.HOUR_OF_DAY,12); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
        val count=c.getActualMaximum(Calendar.DAY_OF_MONTH)
        return (1..count).map { day ->
            if(cancelled()) return emptyList()
            c.set(Calendar.DAY_OF_MONTH,day); val t=c.timeInMillis
            val phase=Astro.moonPhase(Astro.dayNumber(t+8*HOUR))
            val night=AstroPhoto.timeline(t,zone,place)
            MoonDay(day,t,phase.age,phase.illumination,night.moonless.sumOf { it.hours })
        }
    }
    /** Valores climatológicos redondeados: IMO Meteor Shower Calendar, no predicción anual. */
    val showers=listOf(
        MeteorShower("Cuadrántidas",1,4,120,230.0,49.0), MeteorShower("Líridas",4,22,18,271.0,34.0),
        MeteorShower("Eta Acuáridas",5,6,50,338.0,-1.0), MeteorShower("Delta Acuáridas sur",7,30,25,340.0,-16.0),
        MeteorShower("Perseidas",8,12,100,48.0,58.0), MeteorShower("Oriónidas",10,21,20,95.0,16.0),
        MeteorShower("Leónidas",11,17,15,152.0,22.0), MeteorShower("Gemínidas",12,14,150,112.0,33.0),
        MeteorShower("Úrsidas",12,22,10,217.0,76.0))
    fun meteors(now: Long, zone: TimeZone, place: Location): List<MeteorTonight> {
        val bounds=AstroPhoto.nightBounds(now,zone)
        return showers.map { shower ->
            val c=Calendar.getInstance(zone).apply { timeInMillis=now; set(Calendar.MONTH,shower.month-1); set(Calendar.DAY_OF_MONTH,shower.day); set(Calendar.HOUR_OF_DAY,12); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
            if(c.timeInMillis<now-24*HOUR) c.add(Calendar.YEAR,1)
            val eq=AstroPhoto.precess(Astro.Eq(shower.ra,shower.dec),now)
            val transit=AstroPhoto.transit(eq,bounds.start,place)
            // Hora común representativa de esta noche: medianoche local (12 h tras mediodía en UTC salvo DST).
            val midnight=Calendar.getInstance(zone).apply { timeInMillis=bounds.start; add(Calendar.DAY_OF_MONTH,1); set(Calendar.HOUR_OF_DAY,0) }.timeInMillis
            val d=Astro.dayNumber(midnight)
            MeteorTonight(shower,c.timeInMillis,AstroPhoto.horizon(eq,midnight,place).alt,transit,Astro.moonPhase(d).illumination,
                Astro.moonHorizon(d,Astro.gmst(d)+place.longitude,place.latitude).alt)
        }.sortedBy { it.peak }
    }
}
