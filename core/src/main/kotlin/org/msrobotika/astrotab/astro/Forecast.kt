package org.msrobotika.astrotab.astro

import org.msrobotika.astrotab.data.*
import java.util.*
import kotlin.math.*

data class ForecastNight(val start: Long, val darkHours: Double, val coveredHours: Double, val score: Int?,
    val clouds: Double?, val moon: Double?, val dew: Double?, val wind: Double?, val jet: Double?)
object Forecast {
    fun turbulence(jet: Double?): String=when { jet==null -> "sin datos"; jet<70 -> "baja"; jet<140 -> "moderada"; else -> "alta" }
    fun layers(c: Cloud): DoubleArray? {
        val a=c.low ?: return null; val b=c.mid ?: return null; val d=c.high ?: return null
        val factor=100/max(100.0,a+b+d)
        return doubleArrayOf(a*factor,b*factor,d*factor)
    }
    fun week(now: Long, zone: TimeZone, place: Location, data: CloudData?, enabled: Boolean, nightReference: Long=now, cancelled: ()->Boolean = { false }): List<ForecastNight> {
        val c=Calendar.getInstance(zone).apply { timeInMillis=AstroPhoto.nightBounds(nightReference,zone).start }
        return (0..6).map {
            if(cancelled()) return emptyList()
            val night=AstroPhoto.timeline(c.timeInMillis,zone,place); c.add(Calendar.DAY_OF_MONTH,1)
            val dark=night.dark.sumOf { it.hours }
            val rows=if(enabled && data!=null && data.locationKey==place.key && DataPolicy.usable(data.fetchedAt,now)) data.hours else emptyList()
            val selected=rows.mapNotNull { w ->
                val weight=AstroPhoto.intersect(night.dark,listOf(TimeSpan(w.time,w.time+HOUR))).sumOf { it.hours }
                if(weight>0) w to weight else null
            }
            val coverage=selected.sumOf { it.second }
            fun mean(field: (Cloud)->Double?): Double? {
                if(selected.isEmpty() || selected.any { field(it.first)==null }) return null
                return selected.sumOf { field(it.first)!!*it.second }/coverage
            }
            var quality=0.0; var moon=0.0
            for((w,weight) in selected) {
                val h=Night.hour(w.time+HOUR/2,now,place,data,enabled)
                val mf=1-0.8*h.moonFactor; moon+=h.moonFactor*weight
                val rain=1-(w.rain ?: 0.0)/100
                quality+=(1-w.cover/100)*mf*Weather.dewFactor(w.temperature,w.dewPoint)*Weather.windFactor(w.wind)*rain*weight
            }
            val complete=dark>0 && abs(coverage-dark)<0.001
            ForecastNight(night.start,dark,coverage,if(complete) (100*quality/dark).roundToInt().coerceIn(0,100) else null,
                mean { it.cover },if(coverage>0) moon/coverage else null,
                mean { w -> if(w.temperature!=null && w.dewPoint!=null) w.temperature-w.dewPoint else null },mean { it.wind },mean { it.jet })
        }
    }
    fun best(nights: List<ForecastNight>): ForecastNight?=nights.filter { it.score!=null }.maxByOrNull { it.score!! }
}
