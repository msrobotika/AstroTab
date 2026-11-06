package org.msrobotika.astrotab.astro

import org.msrobotika.astrotab.catalog.MessierObject
import org.msrobotika.astrotab.data.*
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

data class TimeSpan(val start: Long, val end: Long) { val hours get()=(end-start)/HOUR.toDouble() }
data class NightSample(val time: Long, val sun: Double, val moon: Double, val moonEq: Astro.Eq, val illumination: Double,
    val sidereal: Double=Astro.gmst(Astro.dayNumber(time)))
data class NightTimeline(val start: Long, val end: Long, val samples: List<NightSample>) {
    fun intervals(predicate: (NightSample)->Boolean): List<TimeSpan> {
        val spans=mutableListOf<TimeSpan>(); var begin: Long?=null
        for(i in 0 until samples.lastIndex) {
            val a=samples[i]; val b=samples[i+1]; val yes=predicate(a)
            // Intervalos discretos para resúmenes; curvas de diez minutos por defecto.
            if(yes && begin==null) begin=a.time
            if(begin!=null && (!yes || i==samples.lastIndex-1)) {
                spans.add(TimeSpan(begin,if(yes) b.time else a.time)); begin=null
            }
        }
        return spans
    }
    val dark by lazy { AstroPhoto.above(samples.map { it.time },samples.map { -it.sun }.toDoubleArray(),18.0) }
    val moonless by lazy { AstroPhoto.intersect(dark,AstroPhoto.above(samples.map { it.time },samples.map { -it.moon }.toDoubleArray(),0.0)) }
}
data class ObjectPlan(val target: MessierObject, val altitudes: DoubleArray, val usefulHours: Double,
    val transit: Long, val transitAltitude: Double, val score: Int, val timeFactor: Double, val altitudeFactor: Double, val moonFactor: Double,
    val windows: List<TimeSpan> = emptyList(),val miniAltitudes: DoubleArray=altitudes)

object AstroPhoto {
    /** Precesión media IAU 1976, J2000 → fecha (Meeus, cap. 21). Sin nutación/movimiento propio. */
    fun precess(eq: Astro.Eq, time: Long): Astro.Eq {
        val t=(time/86400000.0+2440587.5-2451545.0)/36525
        val zeta=(2306.2181*t+0.30188*t*t+0.017998*t*t*t)/3600
        val z=(2306.2181*t+1.09468*t*t+0.018203*t*t*t)/3600
        val theta=(2004.3109*t-0.42665*t*t-0.041833*t*t*t)/3600
        val a=Astro.cosd(eq.dec)*Astro.sind(eq.ra+zeta)
        val b=Astro.cosd(theta)*Astro.cosd(eq.dec)*Astro.cosd(eq.ra+zeta)-Astro.sind(theta)*Astro.sind(eq.dec)
        val c=Astro.sind(theta)*Astro.cosd(eq.dec)*Astro.cosd(eq.ra+zeta)+Astro.cosd(theta)*Astro.sind(eq.dec)
        return eq.copy(ra=Astro.rev(Astro.atan2d(a,b)+z),dec=Astro.atan2d(c,hypot(a,b)))
    }
    fun horizon(eq: Astro.Eq, time: Long, place: Location): Astro.Hor = Astro.toHorizon(eq.ra,eq.dec,Astro.gmst(Astro.dayNumber(time))+place.longitude,place.latitude)
    fun separation(a: Astro.Eq, b: Astro.Eq)=acos((Astro.sind(a.dec)*Astro.sind(b.dec)+Astro.cosd(a.dec)*Astro.cosd(b.dec)*Astro.cosd(a.ra-b.ra)).coerceIn(-1.0,1.0))/Astro.RAD
    fun nightBounds(now: Long, zone: TimeZone): TimeSpan {
        val c=Calendar.getInstance(zone).apply { timeInMillis=now }
        if(c.get(Calendar.HOUR_OF_DAY)<12) c.add(Calendar.DAY_OF_MONTH,-1)
        c.set(Calendar.HOUR_OF_DAY,12); c.set(Calendar.MINUTE,0); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0)
        val start=c.timeInMillis; c.add(Calendar.DAY_OF_MONTH,1)
        return TimeSpan(start,c.timeInMillis)
    }
    fun timeline(now: Long, zone: TimeZone, place: Location, stepMinutes: Int=10): NightTimeline {
        require(stepMinutes in 1..15)
        val span=nightBounds(now,zone); val rows=ArrayList<NightSample>()
        var t=span.start
        while(t<=span.end) {
            val d=Astro.dayNumber(t); val lst=Astro.gmst(d)+place.longitude
            val sun=Astro.sun(d); val moon=Astro.moon(d)
            val lunar=Astro.toHorizon(moon.eq.ra,moon.eq.dec,lst,place.latitude)
            val lunarAltitude=lunar.alt-Astro.asind(1/moon.eq.dist)*Astro.cosd(lunar.alt)
            val elong=Astro.cosd(moon.lon-Astro.sunState(d).lon)*Astro.cosd(moon.lat)
            rows.add(NightSample(t,Astro.toHorizon(sun.ra,sun.dec,lst,place.latitude).alt,lunarAltitude,moon.eq,(1-elong.coerceIn(-1.0,1.0))/2,lst-place.longitude))
            if(t==span.end) break
            t=min(span.end,t+stepMinutes*MINUTE)
        }
        return NightTimeline(span.start,span.end,rows)
    }
    /** Cruces lineales entre muestras; no vuelve a calcular Sol/Luna por objeto. */
    fun above(times: List<Long>, values: DoubleArray, level: Double): List<TimeSpan> {
        require(times.size==values.size)
        val result=mutableListOf<TimeSpan>()
        for(i in 0 until times.lastIndex) {
            val a=values[i]-level; val b=values[i+1]-level
            if(a<=0 && b<=0) continue
            val cross=if(a*b<0) times[i]+((times[i+1]-times[i])*a/(a-b)).roundToLong() else times[i]
            val start=if(a<0) cross else times[i]; val end=if(b<0) cross else times[i+1]
            if(end<=start) continue
            if(result.lastOrNull()?.end==start) result[result.lastIndex]=TimeSpan(result.last().start,end) else result.add(TimeSpan(start,end))
        }
        return result
    }
    fun intersect(a: List<TimeSpan>, b: List<TimeSpan>): List<TimeSpan> = a.flatMap { x -> b.mapNotNull { y ->
        val start=max(x.start,y.start); val end=min(x.end,y.end); if(end>start) TimeSpan(start,end) else null
    } }
    /** Tránsito superior siguiente al inicio; una vuelta sidérea en 86164,09 s. */
    fun transit(eq: Astro.Eq, start: Long, place: Location): Long {
        val angle=Astro.rev(eq.ra-Astro.gmst(Astro.dayNumber(start))-place.longitude)
        var t=start+(angle/360*86164090).roundToLong()
        repeat(3) {
            val ha=Astro.rev(Astro.gmst(Astro.dayNumber(t))+place.longitude-eq.ra+180)-180
            t-=(ha/360*86164090).roundToLong()
        }
        return t
    }
    fun plan(target: MessierObject, night: NightTimeline, place: Location, minimum: Double): ObjectPlan {
        require(minimum in 0.0..90.0)
        val eq=precess(target.eq,night.start); val altitudes=DoubleArray(night.samples.size)
        var useful=0.0; var height=0.0; var moon=0.0
        for(i in night.samples.indices) {
            val s=night.samples[i]; val alt=Astro.toHorizon(eq.ra,eq.dec,s.sidereal+place.longitude,place.latitude).alt; altitudes[i]=alt
            if(i<night.samples.lastIndex && alt>=minimum && s.sun < -18) {
                val dt=(night.samples[i+1].time-s.time)/HOUR.toDouble(); useful+=dt
                height+=Astro.sind(alt).coerceAtLeast(0.0)*dt
                val penalty=if(s.moon>0) s.illumination*(1-separation(eq,s.moonEq)/180)*Astro.sind(s.moon).coerceAtLeast(0.0) else 0.0
                moon+=(1-penalty)*dt
            }
        }
        val windows=intersect(night.dark,above(night.samples.map { it.time },altitudes,minimum))
        val af=if(useful>0) height/useful else 0.0; val mf=if(useful>0) moon/useful else 0.0
        useful=windows.sumOf { it.hours }
        val tf=(useful/6).coerceIn(0.0,1.0)
        val transit=transit(eq,night.start,place)
        val count=((night.end-night.start)/(2*MINUTE)).toInt()+1
        val sampleStep=night.samples.getOrNull(1)?.let {it.time-night.start} ?: 10*MINUTE
        val mini=DoubleArray(count) {i ->
            val t=night.start+(night.end-night.start)*i/(count-1).coerceAtLeast(1)
            val index=((t-night.start)/sampleStep.coerceAtLeast(1)).toInt().coerceIn(0,night.samples.lastIndex)
            val a=night.samples[index];val b=night.samples[(index+1).coerceAtMost(night.samples.lastIndex)]
            val fraction=if(b.time==a.time)0.0 else (t-a.time).toDouble()/(b.time-a.time)
            val sidereal=a.sidereal+Astro.rev(b.sidereal-a.sidereal)*fraction
            Astro.toHorizon(eq.ra,eq.dec,sidereal+place.longitude,place.latitude).alt
        }
        return ObjectPlan(target,altitudes,useful,transit,90-abs(place.latitude-eq.dec),(100*tf*af*mf).roundToInt(),tf,af,mf,windows,mini)
    }
    data class Galactic(val windows: List<TimeSpan>, val highest: Long?, val altitude: Double?, val azimuth: Double?)
    fun galactic(night: NightTimeline, place: Location): Galactic {
        val eq=precess(Astro.Eq(266.4051,-28.9362),night.start)
        val selected=night.samples.filter { it.sun < -18 && horizon(eq,it.time,place).alt>10 }
        val peak=selected.maxByOrNull { horizon(eq,it.time,place).alt }
        val h=peak?.let { horizon(eq,it.time,place) }
        return Galactic(night.intervals { it.sun < -18 && horizon(eq,it.time,place).alt>10 },peak?.time,h?.alt,h?.az)
    }
    /** Tres noches representativas por mes; compara horas útiles, no garantiza meteorología. */
    fun galacticMonths(year: Int, zone: TimeZone, place: Location): List<Pair<Int,Double>> = (1..12).map { month ->
        val mean=listOf(5,15,25).map { day ->
            val c=Calendar.getInstance(zone).apply { clear(); set(year,month-1,day,18,0) }
            val span=nightBounds(c.timeInMillis,zone); val eq=precess(Astro.Eq(266.4051,-28.9362),span.start)
            var t=span.start; var useful=0.0
            while(t<span.end) {
                val next=min(t+15*MINUTE,span.end); val mid=(t+next)/2
                if(horizon(Astro.sun(Astro.dayNumber(mid)),mid,place).alt < -18 && horizon(eq,mid,place).alt>10) useful+=(next-t)/HOUR.toDouble()
                t=next
            }
            useful
        }.average()
        month to mean
    }.sortedByDescending { it.second }
    data class Event(val label: String, val time: Long)
    fun events(night: NightTimeline, place: Location): List<Event> {
        val events=mutableListOf<Event>()
        fun crossings(level: Double, moon: Boolean, label: String) {
            for(i in 0 until night.samples.lastIndex) {
                val a=night.samples[i]; val b=night.samples[i+1]
                val av=if(moon) a.moon else a.sun; val bv=if(moon) b.moon else b.sun
                if((av>=level)==(bv>=level)) continue
                val rise=bv>av; var lo=a.time; var hi=b.time
                repeat(14) {
                    val mid=(lo+hi)/2; val d=Astro.dayNumber(mid)
                    val alt=if(moon) Astro.moonHorizon(d,Astro.gmst(d)+place.longitude,place.latitude).alt else horizon(Astro.sun(d),mid,place).alt
                    if((alt>=level)==rise) hi=mid else lo=mid
                }
                events.add(Event(if(moon) (if(rise) "Salida de la Luna" else "Puesta de la Luna") else "$label · ${if(rise) "ascendente" else "descendente"}",(lo+hi)/2))
            }
        }
        for((level,label) in listOf(6.0 to "Dorada: Sol +6°",-4.0 to "Dorada/azul: Sol −4°",-6.0 to "Civil/azul: Sol −6°",-12.0 to "Náutico: Sol −12°",-18.0 to "Astronómico: Sol −18°",-0.833 to "Salida/puesta del Sol")) {
            crossings(level,false,label)
        }
        crossings(0.0,true,"Luna")
        return events.sortedBy { it.time }
    }
    data class Polar(val star: String, val hourAngle: Double, val clockAngle: Double, val radius: Double)
    fun polar(now: Long, place: Location): Polar {
        val south=place.latitude<0
        val eq=precess(if(south) Astro.Eq(317.195,-88.9565) else Astro.Eq(37.95456067,89.26410897),now)
        val ha=Astro.rev(Astro.gmst(Astro.dayNumber(now))+place.longitude-eq.ra)
        return Polar(if(south) "Sigma Octantis" else "Polaris",ha,Astro.rev(if(south) ha else -ha),90-abs(eq.dec))
    }
}

enum class SensorFormat(val label: String, val width: Double, val height: Double, val crop: Double) {
    FULL("Completo",36.0,24.0,1.0), APS_C("APS-C 23,6 × 15,7 mm",23.6,15.7,1.5), CANON("APS-C 22,3 × 14,9 mm",22.3,14.9,1.6),
    MFT("MFT",17.3,13.0,2.0), ONE_INCH("1 pulgada",13.2,8.8,2.7), MOBILE("Personalizado (editable)",6.4,4.8,5.4),
    ASTRO_4_3("Astronomía 19,1 × 13 mm",19.1,13.0,1.87), ASTRO_1("Astronomía 13,2 × 8,8 mm",13.2,8.8,2.7),
    ASTRO_533("Astronomía 11,3 × 11,3 mm",11.3,11.3,2.7), ASTRO_585("Astronomía 11,2 × 6,3 mm",11.2,6.3,3.4),
    ASTRO_174("Astronomía 11,3 × 7,1 mm",11.3,7.1,3.2), ASTRO_290("Astronomía 5,6 × 3,2 mm",5.6,3.2,6.7)
}
data class Equipment(val focal: Double=50.0, val aperture: Double=2.8, val sensor: SensorFormat=SensorFormat.FULL,
    val pixel: Double=4.3, val mobileWidth: Double=6.4, val mobileHeight: Double=4.8,
    val tracking: Boolean=false, val equatorial: Boolean=false) {
    init { require(listOf(focal,aperture,pixel,mobileWidth,mobileHeight).all { it.isFinite() && it>0 }) }
    val width get()=if(sensor==SensorFormat.MOBILE) mobileWidth else sensor.width
    val height get()=if(sensor==SensorFormat.MOBILE) mobileHeight else sensor.height
    val crop get()=hypot(36.0,24.0)/hypot(width,height)
}
object Optics {
    fun rule500(e: Equipment)=500/(e.focal*e.crop)
    fun npf(e: Equipment, declination: Double): Double? {
        require(declination.isFinite() && declination in -90.0..90.0)
        return if(abs(declination)>=89) null else (35*e.aperture+30*e.pixel)/(e.focal*Astro.cosd(declination))
    }
    fun field(size: Double, focal: Double): Double { require(size>0 && focal>0); return 2*atan(size/(2*focal))/Astro.RAD }
    fun scale(e: Equipment)=206.264806*e.pixel/e.focal
    data class Fit(val widthPercent: Double, val heightPercent: Double, val fits: Boolean)
    fun fit(e: Equipment, target: MessierObject): Fit? {
        val major=target.majorArcmin ?: return null; val minor=target.minorArcmin ?: return null
        val w=major/60/field(max(e.width,e.height),e.focal)*100
        val h=minor/60/field(min(e.width,e.height),e.focal)*100
        return Fit(w,h,w<=100 && h<=100)
    }
}
