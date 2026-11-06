package org.msrobotika.astrotab.astro

import kotlin.math.*

/** Unidades explícitas; aproximaciones geométricas para planificar fotografía. */
object ProTools {
    private fun positive(vararg values: Double) { require(values.all { it.isFinite() && it>0 }) }
    data class Focus(val cocMm: Double, val hyperfocalMetres: Double, val nearMetres: Double, val farMetres: Double?)
    fun focus(e: Equipment, distanceMetres: Double): Focus {
        positive(distanceMetres)
        val coc=hypot(e.width,e.height)/1500.0 // Criterio convencional diagonal/1500, no nitidez por píxel.
        val h=e.focal*e.focal/(e.aperture*coc)+e.focal
        val s=distanceMetres*1000; require(s>e.focal)
        return Focus(coc,h/1000,(h-e.focal)*s/(h+s-2*e.focal)/1000,
            if(s>=h) null else (h-e.focal)*s/(h-s)/1000)
    }
    data class Timelapse(val frames: Long, val clipSeconds: Double, val megabytes: Double)
    fun timelapse(intervalSeconds: Double, eventSeconds: Double, fps: Double, mbPerPhoto: Double): Timelapse {
        positive(intervalSeconds,eventSeconds,fps,mbPerPhoto)
        // Tomas a t=0, intervalo,... estrictamente antes del final del evento.
        val n=ceil(eventSeconds/intervalSeconds).toLong()
        return Timelapse(n,n/fps,n*mbPerPhoto)
    }
    fun arc(durationSeconds: Double): Double { require(durationSeconds.isFinite() && durationSeconds>=0); return durationSeconds*360/86164.0905 }
    fun arcSeconds(degrees: Double): Double { require(degrees.isFinite() && degrees>=0); return degrees*86164.0905/360 }
    fun mosaicAxis(coverage: Double, field: Double, overlapPercent: Double): Int {
        positive(coverage,field); require(overlapPercent.isFinite() && overlapPercent in 0.0..95.0)
        return if(coverage<=field) 1 else 1+ceil((coverage-field)/(field*(1-overlapPercent/100))).toInt()
    }
    data class Stack(val snrGain: Double, val integrationSeconds: Double, val darks: Int, val flats: Int, val bias: Int)
    fun stack(frames: Int, exposureSeconds: Double): Stack {
        require(frames in 1..1000000); positive(exposureSeconds)
        return Stack(sqrt(frames.toDouble()),frames*exposureSeconds,max(20,ceil(sqrt(frames.toDouble())*2).toInt()),30,50)
    }
}
