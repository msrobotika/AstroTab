package org.msrobotika.astrotab.astro

import kotlin.math.*

/** Portado de astro.h de AstroCYD MS (MIT, MS Robotika). Método de Schlyter.
 * Grados; d desde 2000-01-00 UTC; distancias UA salvo Luna (radios terrestres).
 * Aproximación para orientación: no apta para guiar monturas. */
object Astro {
    const val RAD = PI / 180.0
    fun rev(x: Double) = ((x % 360) + 360) % 360
    fun sind(x: Double) = sin(x * RAD)
    fun cosd(x: Double) = cos(x * RAD)
    fun atan2d(y: Double, x: Double) = atan2(y, x) / RAD
    fun asind(x: Double) = asin(x.coerceIn(-1.0, 1.0)) / RAD
    fun dayNumber(utcMillis: Long) = utcMillis / 86400000.0 - 10956.0
    fun obliquity(d: Double) = 23.4393 - 3.563e-7 * d
    data class Eq(val ra: Double, val dec: Double, val dist: Double = 1.0)
    data class Hor(val alt: Double, val az: Double)
    data class SunState(val lon: Double, val r: Double, val meanLon: Double, val anomaly: Double)
    data class MoonState(val eq: Eq, val lon: Double, val lat: Double)
    data class Phase(val illumination: Double, val age: Double)
    data class Planet(val eq: Eq, val magnitude: Double)

    fun eclToEq(lon: Double, lat: Double, r: Double, d: Double): Eq {
        val x = r * cosd(lon) * cosd(lat)
        val y = r * sind(lon) * cosd(lat)
        val z = r * sind(lat)
        val ecl = obliquity(d)
        val ye = y * cosd(ecl) - z * sind(ecl)
        val ze = y * sind(ecl) + z * cosd(ecl)
        return Eq(rev(atan2d(ye, x)), atan2d(ze, sqrt(x * x + ye * ye)), r)
    }
    fun eccAnomaly(m: Double, e: Double): Double {
        var result = m + e / RAD * sind(m) * (1 + e * cosd(m))
        repeat(8) {
            val delta = (result - e / RAD * sind(result) - m) / (1 - e * cosd(result))
            result -= delta
            if (abs(delta) < 1e-6) return result
        }
        return result
    }
    fun sunState(d: Double): SunState {
        val w = 282.9404 + 4.70935e-5 * d
        val e = 0.016709 - 1.151e-9 * d
        val m = rev(356.0470 + 0.9856002585 * d)
        val anomaly = eccAnomaly(m, e)
        val x = cosd(anomaly) - e
        val y = sqrt(1 - e * e) * sind(anomaly)
        return SunState(rev(atan2d(y, x) + w), hypot(x, y), rev(w + m), m)
    }
    fun sun(d: Double): Eq = sunState(d).let { eclToEq(it.lon, 0.0, it.r, d) }
    fun gmst(d: Double): Double {
        var ut = (d + 10956.0) % 1.0 * 24
        if (ut < 0) ut += 24
        return rev(sunState(d).meanLon + 180 + ut * 15)
    }
    fun toHorizon(ra: Double, dec: Double, lst: Double, lat: Double): Hor {
        val ha = lst - ra
        val x = cosd(ha) * cosd(dec)
        val y = sind(ha) * cosd(dec)
        val z = sind(dec)
        return Hor(asind(x * cosd(lat) + z * sind(lat)), rev(atan2d(y, x * sind(lat) - z * cosd(lat)) + 180))
    }
    fun moon(d: Double): MoonState {
        val node = rev(125.1228 - 0.0529538083 * d)
        val i = 5.1454
        val w = rev(318.0634 + 0.1643573223 * d)
        val a = 60.2666
        val e = 0.054900
        val m = rev(115.3654 + 13.0649929509 * d)
        val anomaly = eccAnomaly(m, e)
        val xv = a * (cosd(anomaly) - e)
        val yv = a * sqrt(1 - e * e) * sind(anomaly)
        val v = atan2d(yv, xv)
        var r = hypot(xv, yv)
        val xh = r * (cosd(node) * cosd(v + w) - sind(node) * sind(v + w) * cosd(i))
        val yh = r * (sind(node) * cosd(v + w) + cosd(node) * sind(v + w) * cosd(i))
        val zh = r * sind(v + w) * sind(i)
        var lon = atan2d(yh, xh)
        var lat = atan2d(zh, hypot(xh, yh))
        val s = sunState(d)
        val ms = s.anomaly
        val lm = rev(node + w + m)
        val elong = lm - s.meanLon
        val f = lm - node
        lon += -1.274*sind(m-2*elong) + 0.658*sind(2*elong) - 0.186*sind(ms) - 0.059*sind(2*m-2*elong) -
            0.057*sind(m-2*elong+ms) + 0.053*sind(m+2*elong) + 0.046*sind(2*elong-ms) + 0.041*sind(m-ms) -
            0.035*sind(elong) - 0.031*sind(m+ms) - 0.015*sind(2*f-2*elong) + 0.011*sind(m-4*elong)
        lat += -0.173*sind(f-2*elong) - 0.055*sind(m-f-2*elong) - 0.046*sind(m+f-2*elong) +
            0.033*sind(f+2*elong) + 0.017*sind(2*m+f)
        r += -0.58*cosd(m-2*elong) - 0.46*cosd(2*elong)
        return MoonState(eclToEq(rev(lon), lat, r, d), rev(lon), lat)
    }
    fun moonPhase(d: Double): Phase {
        val m = moon(d)
        val s = sunState(d)
        val elong = acos((cosd(m.lon - s.lon) * cosd(m.lat)).coerceIn(-1.0, 1.0))
        return Phase((1 - cos(elong)) / 2, rev(m.lon - s.lon))
    }
    fun moonHorizon(d: Double, lst: Double, lat: Double): Hor {
        val m = moon(d)
        val h = toHorizon(m.eq.ra, m.eq.dec, lst, lat)
        return h.copy(alt = h.alt - asind(1 / m.eq.dist) * cosd(h.alt))
    }
    private val elements = arrayOf(
        doubleArrayOf(48.3313,3.24587e-5,7.0047,5e-8,29.1241,1.01444e-5,0.387098,0.205635,5.59e-10,168.6562,4.0923344368),
        doubleArrayOf(76.6799,2.46590e-5,3.3946,2.75e-8,54.8910,1.38374e-5,0.723330,0.006773,-1.302e-9,48.0052,1.6021302244),
        doubleArrayOf(49.5574,2.11081e-5,1.8497,-1.78e-8,286.5016,2.92961e-5,1.523688,0.093405,2.516e-9,18.6021,0.5240207766),
        doubleArrayOf(100.4542,2.76854e-5,1.3030,-1.557e-7,273.8777,1.64505e-5,5.20256,0.048498,4.469e-9,19.8950,0.0830853001),
        doubleArrayOf(113.6634,2.38980e-5,2.4886,-1.081e-7,339.3939,2.97661e-5,9.55475,0.055546,-9.499e-9,316.9670,0.0334442282)
    )
    fun planet(p: Int, d: Double): Planet {
        require(p in 0..4)
        val k = elements[p]
        val node=k[0]+k[1]*d; val i=k[2]+k[3]*d; val w=k[4]+k[5]*d
        val a=k[6]; val e=k[7]+k[8]*d; val m=rev(k[9]+k[10]*d)
        val anomaly=eccAnomaly(m,e)
        val xv=a*(cosd(anomaly)-e); val yv=a*sqrt(1-e*e)*sind(anomaly)
        val v=atan2d(yv,xv); val r=hypot(xv,yv)
        var xh=r*(cosd(node)*cosd(v+w)-sind(node)*sind(v+w)*cosd(i))
        var yh=r*(sind(node)*cosd(v+w)+cosd(node)*sind(v+w)*cosd(i))
        var zh=r*sind(v+w)*sind(i)
        var lon=atan2d(yh,xh); var lat=atan2d(zh,hypot(xh,yh))
        if(p>=3) {
            val mj=rev(19.8950+0.0830853001*d); val ms=rev(316.9670+0.0334442282*d)
            if(p==3) lon += -0.332*sind(2*mj-5*ms-67.6)-0.056*sind(2*mj-2*ms+21)+0.042*sind(3*mj-5*ms+21)-
                0.036*sind(mj-2*ms)+0.022*cosd(mj-ms)+0.023*sind(2*mj-3*ms+52)-0.016*sind(mj-5*ms-69)
            else {
                lon += 0.812*sind(2*mj-5*ms-67.6)-0.229*cosd(2*mj-4*ms-2)+0.119*sind(mj-2*ms-3)+
                    0.046*sind(2*mj-6*ms-69)+0.014*sind(mj-3*ms+32)
                lat += -0.020*cosd(2*mj-4*ms-2)+0.018*sind(2*mj-6*ms-49)
            }
        }
        xh=r*cosd(lon)*cosd(lat); yh=r*sind(lon)*cosd(lat); zh=r*sind(lat)
        val s=sunState(d)
        val xg=xh+s.r*cosd(s.lon); val yg=yh+s.r*sind(s.lon)
        val distance=sqrt(xg*xg+yg*yg+zh*zh)
        val eq=eclToEq(atan2d(yg,xg),atan2d(zh,hypot(xg,yg)),distance,d)
        val phase=acos(((r*r+distance*distance-s.r*s.r)/(2*r*distance)).coerceIn(-1.0,1.0))/RAD
        val base=doubleArrayOf(-0.36,-4.34,-1.51,-9.25,-9.0)
        val slope=doubleArrayOf(0.027,0.013,0.016,0.014,0.044)
        var mag=base[p]+5*log10(r*distance)+slope[p]*phase
        if(p==0) mag+=2.2e-13*phase.pow(6)
        if(p==1) mag+=4.2e-7*phase.pow(3)
        return Planet(eq,mag)
    }
    fun geomagLat(lat: Double, lon: Double) = asind(sind(lat)*sind(80.8)+cosd(lat)*cosd(80.8)*cosd(lon+72.6))
    fun kpNeeded(geomagneticLatitude: Double) = (66.5-abs(geomagneticLatitude))/2.6
    fun limitingMag(bortle: Int) = doubleArrayOf(7.6,7.1,6.6,6.1,5.6,5.1,4.6,4.1,3.8)[bortle.coerceIn(1,9)-1]
    fun phaseName(age: Double): String = when {
        age < 10 || age > 350 -> "Luna nueva"
        age < 80 -> "Creciente"
        age < 100 -> "Cuarto creciente"
        age < 170 -> "Gibosa creciente"
        age < 190 -> "Luna llena"
        age < 260 -> "Gibosa menguante"
        age < 280 -> "Cuarto menguante"
        else -> "Menguante"
    }
    fun nextPhase(from: Long, target: Double): Long? {
        var previous=rev(moonPhase(dayNumber(from)).age-target+180)
        for(hour in 1..(32*24)) {
            val t=from+hour*3600000L
            val next=rev(moonPhase(dayNumber(t)).age-target+180)
            if(previous<180 && next>=180) return t
            previous=next
        }
        return null
    }
    /** Cruce interpolado por bisección; null significa sin cruce en el intervalo, no «nunca». */
    fun nextCross(from: Long, hours: Int, level: Double, rising: Boolean, altitude: (Long)->Double): Long? {
        require(hours>=0)
        var t=from; var previous=altitude(t)-level
        repeat(hours*12) {
            val end=t+300000L; val next=altitude(end)-level
            if((rising && previous<0 && next>=0) || (!rising && previous>0 && next<=0)) {
                var low=t; var high=end
                repeat(14) {
                    val mid=(low+high)/2
                    if((altitude(mid)>=level)==rising) high=mid else low=mid
                }
                return (low+high)/2
            }
            t=end; previous=next
        }
        return null
    }
    /** Recorrer el tiempo al revés invierte el sentido del cruce, no su fecha real. */
    fun lastCross(from: Long, hours: Int, level: Double, rising: Boolean, altitude: (Long)->Double): Long? =
        nextCross(0L,hours,level,!rising) { elapsed -> altitude(from-elapsed) }?.let { from-it }

    data class HorizonEvents(val above: Boolean, val rise: Long?, val set: Long?)
    fun horizonEvents(now: Long, altitude: (Long)->Double): HorizonEvents {
        val above=altitude(now)>=0.0
        return HorizonEvents(above,if(above) lastCross(now,24,0.0,true,altitude) else nextCross(now,24,0.0,true,altitude),
            nextCross(now,24,0.0,false,altitude))
    }
}
