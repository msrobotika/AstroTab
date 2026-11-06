package org.msrobotika.astrotab.satellites

import org.msrobotika.astrotab.astro.*
import org.msrobotika.astrotab.data.*
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

data class Tle(val name: String, val number: Int, val epoch: Long, val line1: String, val line2: String) {
    val periodMinutes get()=1440.0/line2.substring(52,63).trim().toDouble()
    fun old(now: Long)=now-epoch>72*HOUR || epoch-now>24*HOUR
}

object TleParser {
    const val MAX_OBJECTS=200
    private fun valid(line: String, prefix: String): Boolean = line.length==69 && line.startsWith(prefix) &&
        line.last().isDigit() && line.take(68).sumOf { if(it.isDigit()) it-'0' else if(it=='-') 1 else 0 }%10==line.last()-'0'
    fun parse(raw: String): List<Tle> {
        require(raw.toByteArray(Charsets.UTF_8).size<=HttpsTransport.MAX_BYTES) { "TLE demasiado grande" }
        val lines=raw.lineSequence().map { it.trimEnd() }.filter { it.isNotBlank() }.toList()
        require(lines.size<=MAX_OBJECTS*3)
        val result=mutableListOf<Tle>(); var i=0
        while(i<lines.size) {
            val name=if(lines[i].startsWith("1 ")) "NORAD ${lines[i].substring(2,7).trim()}" else lines[i++].removePrefix("0 ").trim()
            require(name.length in 1..80 && i+1<lines.size) { "Registro TLE incompleto" }
            val a=lines[i++]; val b=lines[i++]
            require(valid(a,"1 ") && valid(b,"2 ") && a.substring(2,7)==b.substring(2,7)) { "Checksum o identificación TLE inválidos" }
            val number=a.substring(2,7).trim().toInt()
            val yy=a.substring(18,20).toInt(); val year=if(yy<57) 2000+yy else 1900+yy
            val day=a.substring(20,32).trim().toDouble()
            val cal=Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(year,0,1) }
            require(day>=1 && day<cal.getActualMaximum(Calendar.DAY_OF_YEAR)+1)
            val epoch=cal.timeInMillis+((day-1)*86400000).roundToLong()
            fun field(start: Int,end: Int)=b.substring(start,end).trim().toDouble().also { require(it.isFinite()) }
            require(field(8,16) in 0.0..180.0 && field(17,25) in 0.0..360.0 && field(34,42) in 0.0..360.0 && field(43,51) in 0.0..360.0)
            require(b.substring(26,33).all { it.isDigit() } && field(52,63) in 0.01..20.0)
            result.add(Tle(name,number,epoch,a,b)); require(result.size<=MAX_OBJECTS)
        }
        require(result.isNotEmpty()); return result.distinctBy { it.number }
    }
    internal fun exponent(value: String): Double {
        require(value.matches(Regex("[ +\\-][0-9]{5}[+\\-][0-9]")))
        return (if(value[0]=='-') -1 else 1)*value.substring(1,6).toDouble()*1e-5*10.0.pow(value.substring(6).toInt())
    }
}

/** TEME km y km/s, WGS72, modo mejorado de los vectores oficiales. Una instancia por worker. */
class Sgp4(val tle: Tle) {
    private val record=Sgp4Record()
    private val position=DoubleArray(3); private val velocity=DoubleArray(3)
    init {
        val a=tle.line1; val b=tle.line2
        val epoch=tle.epoch/86400000.0+2440587.5-2433281.5
        record.jdsatepoch=epoch+2433281.5
        check(Sgp4Math.sgp4init(Sgp4Math.Gravconsttype.wgs72,'i',tle.number,epoch,TleParser.exponent(a.substring(53,61)),
            ("0."+b.substring(26,33)).toDouble(),b.substring(34,42).trim().toDouble()*Astro.RAD,
            b.substring(8,16).trim().toDouble()*Astro.RAD,b.substring(43,51).trim().toDouble()*Astro.RAD,
            b.substring(52,63).trim().toDouble()*2*PI/1440,b.substring(17,25).trim().toDouble()*Astro.RAD,record)) { "Órbita no propagable: ${record.error}" }
    }
    data class State(val x: Double,val y: Double,val z: Double,val vx: Double,val vy: Double,val vz: Double)
    fun atMinutes(minutes: Double): State? {
        require(minutes.isFinite())
        if(!Sgp4Math.sgp4(record,minutes,position,velocity)) return null
        if(position.any { !it.isFinite() } || velocity.any { !it.isFinite() }) return null
        return State(position[0],position[1],position[2],velocity[0],velocity[1],velocity[2])
    }
    fun at(time: Long)=atMinutes((time-tle.epoch)/60000.0)
    companion object { fun sidereal(time: Long)=Sgp4Math.gstime(time/86400000.0+2440587.5) }
}

data class SatellitePoint(val time: Long,val altitude: Double,val azimuth: Double,val illuminated: Boolean,val dark: Boolean) {
    val visible get()=altitude>0 && illuminated && dark
}
data class SatellitePass(val tle: Tle,val start: SatellitePoint,val maximum: SatellitePoint,val end: SatellitePoint,val track: List<SatellitePoint>)

object SatelliteGeometry {
    data class Observer(val x: Double,val y: Double,val z: Double,val latitude: Double,val theta: Double,val sunAltitude: Double,
        val sunX: Double,val sunY: Double,val sunZ: Double,
        val ux: Double=cos(latitude)*cos(theta),val uy: Double=cos(latitude)*sin(theta),val uz: Double=sin(latitude),
        val ex: Double= -sin(theta),val ey: Double=cos(theta),
        val nx: Double= -sin(latitude)*cos(theta),val ny: Double= -sin(latitude)*sin(theta),val nz: Double=cos(latitude))
    fun nearHorizon(state: Sgp4.State,o: Observer): Boolean {
        val x=state.x-o.x;val y=state.y-o.y;val z=state.z-o.z
        return o.ux*x+o.uy*y+o.uz*z> -0.10452846326765347*sqrt(x*x+y*y+z*z) // −6°
    }
    fun aboveHorizon(state: Sgp4.State,o: Observer)=o.ux*(state.x-o.x)+o.uy*(state.y-o.y)+o.uz*(state.z-o.z)>0
    fun observer(time: Long,place: Location): Observer {
        val lat=place.latitude*Astro.RAD; val theta=Sgp4.sidereal(time)+place.longitude*Astro.RAD
        val n=6378.137/sqrt(1-0.00669437999014*sin(lat).pow(2))
        val d=Astro.dayNumber(time);val sun=Astro.sunState(d);val lon=sun.lon*Astro.RAD;val e=Astro.obliquity(d)*Astro.RAD
        val sx=cos(lon);val sy=sin(lon)*cos(e);val sz=sin(lon)*sin(e)
        val sunAltitude=asin((cos(lat)*cos(theta)*sx+cos(lat)*sin(theta)*sy+sin(lat)*sz).coerceIn(-1.0,1.0))/Astro.RAD
        return Observer(n*cos(lat)*cos(theta),n*cos(lat)*sin(theta),n*(1-0.00669437999014)*sin(lat),lat,theta,
            sunAltitude,sx,sy,sz)
    }
    fun interpolate(a: Observer,b: Observer,f: Double): Observer {
        fun mix(x: Double,y: Double)=x+(y-x)*f
        var dt=b.theta-a.theta;if(dt>PI)dt-=2*PI;if(dt< -PI)dt+=2*PI
        return Observer(mix(a.x,b.x),mix(a.y,b.y),mix(a.z,b.z),a.latitude,a.theta+dt*f,
            mix(a.sunAltitude,b.sunAltitude),mix(a.sunX,b.sunX),mix(a.sunY,b.sunY),mix(a.sunZ,b.sunZ),
            mix(a.ux,b.ux),mix(a.uy,b.uy),a.uz,mix(a.ex,b.ex),mix(a.ey,b.ey),mix(a.nx,b.nx),mix(a.ny,b.ny),a.nz)
    }
    fun point(state: Sgp4.State,time: Long,o: Observer): SatellitePoint {
        val x=state.x-o.x; val y=state.y-o.y; val z=state.z-o.z
        val east=o.ex*x+o.ey*y
        val north=o.nx*x+o.ny*y+o.nz*z
        val up=o.ux*x+o.uy*y+o.uz*z
        val along=state.x*o.sunX+state.y*o.sunY+state.z*o.sunZ
        val radial=sqrt((state.x*state.x+state.y*state.y+state.z*state.z-along*along).coerceAtLeast(0.0))
        // Umbra cónica: radio decrece hacia el extremo a ~1,38 millones km.
        val shadow=along<0 && radial<6378.137+along*(695700.0-6378.137)/149597870.7
        return SatellitePoint(time,atan2(up,hypot(east,north))/Astro.RAD,Astro.rev(atan2(east,north)/Astro.RAD),!shadow,o.sunAltitude< -6)
    }
}

/** Filtrado compartido por lista, Inicio y pruebas. El máximo visible puede diferir del geométrico. */
object SatellitePassPolicy {
    fun visiblePeak(pass: SatellitePass)=pass.track.filter {it.visible}.maxByOrNull {it.altitude}
    fun select(passes: List<SatellitePass>,now: Long,minimum: Int=10,onlyIss: Boolean=false): List<SatellitePass> {
        require(minimum in listOf(10,20,30))
        return passes.filter {it.end.time>now && (!onlyIss || it.tle.number==25544) &&
            (visiblePeak(it)?.altitude ?: -90.0)>=minimum}.sortedBy {it.start.time}
    }
}

/** Memoria de navegación. Acceso desde un único worker; no depende de filtros de presentación. */
class SatellitePredictionCache {
    private var key="";private var computedAt=0L;private var value: List<SatellitePass>?=null
    private fun key(tles: List<Tle>,place: Location)=place.key+":"+tles.sortedBy {it.number}.joinToString {it.line1+it.line2}
    @Synchronized fun get(tles: List<Tle>,place: Location,now: Long): List<SatellitePass>? =
        value?.takeIf {key==key(tles,place) && now>=computedAt && now-computedAt<24*HOUR}
    @Synchronized fun put(tles: List<Tle>,place: Location,now: Long,passes: List<SatellitePass>) {
        key=key(tles,place);computedAt=now;value=passes
    }
}

object SatellitePredictor {
    /** Cota conservadora: ni a apogeo llega al horizonte de esta latitud. Margen 2° para perturbaciones. */
    fun canReach(tle: Tle,place: Location): Boolean {
        val inclination=tle.line2.substring(8,16).trim().toDouble().let {min(it,180-it)}
        val eccentricity=("0."+tle.line2.substring(26,33)).toDouble()
        val radius=(398600.8*(tle.periodMinutes*60/(2*PI)).pow(2)).pow(1.0/3)*(1+eccentricity)
        val horizon=acos((6378.135/radius).coerceIn(0.0,1.0))/Astro.RAD
        return abs(place.latitude)<=inclination+horizon+2
    }
    /** Detección a 60 s; Hermite a 180 s solo para buscar. Todo paso final usa SGP4 directo.
     * No se recorren mallas finas por cada satélite. ISS siempre primera, publicación inmediata.
     * La malla puede omitir rasantes breves por debajo del filtro de 10°.
     */
    fun predict(tles: List<Tle>,start: Long,place: Location,days: Int=5,cancelled: ()->Boolean={false},
        partial: (Int,List<SatellitePass>)->Unit={_,_->},interpolate: Boolean=true): List<SatellitePass> {
        require(days in 1..5 && tles.size<=TleParser.MAX_OBJECTS)
        if(tles.isEmpty())return emptyList()
        val end=start+days*24*HOUR;val step=60000L;val count=((end-start)/step).toInt()
        val anchors=(0..count step 2).map {SatelliteGeometry.observer(start+it*step,place)}
        val observers=(0..count).map {i -> if(i%2==0)anchors[i/2] else SatelliteGeometry.interpolate(anchors[i/2],anchors[i/2+1],0.5)}
        fun observerAt(t: Long): SatelliteGeometry.Observer {
            val i=((t-start)/step).toInt().coerceIn(0,count)
            if(i==count || t==start+i*step)return observers[i]
            return SatelliteGeometry.interpolate(observers[i],observers[i+1],(t-start-i*step).toDouble()/step)
        }
        val result=mutableListOf<SatellitePass>()
        val ordered=tles.sortedBy {if(it.number==25544)0 else 1}
        for((index,tle) in ordered.withIndex()) {
            if(cancelled())break
            if(canReach(tle,place)) {
                val propagator=runCatching {Sgp4(tle)}.getOrNull()
                if(propagator!=null) {
                    val eccentricity=("0."+tle.line2.substring(26,33)).toDouble()
                    val stride=if(interpolate && eccentricity<0.03 && tle.periodMinutes<225)3 else 1
                    var anchorIndex=-1;var a: Sgp4.State?=null;var b: Sgp4.State?=null
                    fun coarseState(i: Int): Sgp4.State? {
                        if(stride==1)return propagator.at(start+i*step)
                        val left=i/stride*stride;val right=(left+stride).coerceAtMost(count)
                        if(left!=anchorIndex){val prior=if(left==anchorIndex+stride)b else null;anchorIndex=left;a=prior ?: propagator.at(start+left*step);b=propagator.at(start+right*step)}
                        val aa=a ?: return null;val bb=b ?: return null
                        if(i==left || left==right)return aa
                        val dt=(right-left)*60.0;val f=(i-left).toDouble()/(right-left);val f2=f*f;val f3=f2*f
                        fun h(x: Double,vx: Double,y: Double,vy: Double)=(2*f3-3*f2+1)*x+(f3-2*f2+f)*dt*vx+(-2*f3+3*f2)*y+(f3-f2)*dt*vy
                        return Sgp4.State(h(aa.x,aa.vx,bb.x,bb.vx),h(aa.y,aa.vy,bb.y,bb.vy),h(aa.z,aa.vz,bb.z,bb.vz),0.0,0.0,0.0)
                    }
                    fun point(t: Long)=propagator.at(t)?.let {SatelliteGeometry.point(it,t,observerAt(t))}
                    fun above(t: Long)=point(t)?.altitude?.let {it>0}==true
                    fun horizon(loTime: Long,hiTime: Long,rise: Boolean): Long {
                        var lo=loTime;var hi=hiTime
                        while(hi-lo>125){val m=(lo+hi)/2;if(above(m)==rise)hi=m else lo=m}
                        return if(rise)hi else lo
                    }
                    var skipUntil=start-1
                    for(i in 0..count) {
                        if(i%120==0 && cancelled())break
                        val t=start+i*step
                        if(t<=skipUntil || observers[i].sunAltitude>=-5.5)continue
                        val state=coarseState(i) ?: continue
                        if(!SatelliteGeometry.aboveHorizon(state,observers[i]))continue
                        val sample=SatelliteGeometry.point(state,t,observers[i])
                        if(!sample.visible)continue
                        val exact=point(t) ?: continue
                        if(!exact.visible)continue
                        var left=t;var right=t
                        while(left>start && above(left))left=(left-step).coerceAtLeast(start)
                        while(right<end && above(right))right=(right+step).coerceAtMost(end)
                        val rise=if(left==start && above(left))left else horizon(left,(left+step).coerceAtMost(t),true)
                        val set=if(right==end && above(right))right else horizon((right-step).coerceAtLeast(t),right,false)
                        skipUntil=set
                        var lo=rise;var hi=set
                        while(hi-lo>250){val x=lo+(hi-lo)/3;val y=hi-(hi-lo)/3
                            if((point(x)?.altitude ?: -90.0)<(point(y)?.altitude ?: -90.0))lo=x else hi=y}
                        val peak=point((lo+hi)/2) ?: continue
                        // Ningún tramo puede superar su máximo geométrico. El filtro mínimo es 10°.
                        if(peak.altitude<9.9 && (point(rise)?.altitude ?: -90.0)<9.9 && (point(set)?.altitude ?: -90.0)<9.9)continue
                        val track=mutableListOf<SatellitePoint>();val trackStep=max(5000L,(set-rise)/240)
                        var q=rise
                        while(q<set){point(q)?.let {track.add(it)};q+=trackStep}
                        point(set)?.let {track.add(it)}
                        // Añadir cruces de iluminación/oscuridad refinados, no redondeados a la muestra.
                        val transitions=mutableListOf<SatellitePoint>()
                        for((x,y) in track.zipWithNext())if(x.visible!=y.visible){var l=x.time;var h=y.time
                            while(h-l>125){val m=(l+h)/2;if(point(m)?.visible==y.visible)h=m else l=m}
                            point(l)?.let {transitions.add(it)};point(h)?.let {transitions.add(it)}}
                        track.addAll(transitions);track.add(peak);track.sortBy {it.time}
                        if(track.any {it.visible})result.add(SatellitePass(tle,point(rise) ?: continue,track.maxBy {it.altitude},point(set) ?: continue,track.distinctBy {it.time}))
                    }
                }
            }
            if(index==0 || (index+1)%5==0 || index==ordered.lastIndex)partial(index+1,result.sortedBy {it.start.time}.toList())
        }
        return result.sortedBy {it.start.time}
    }
}
