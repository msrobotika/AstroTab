package org.msrobotika.astrotab.ui

import org.msrobotika.astrotab.astro.Astro
import kotlin.math.*

/** Proyección cilíndrica local del boceto, con rotación de roll opcional. */
object SkyGeometry {
    fun x(az: Double, bearing: Double, width: Int) = (width/2.0+(Astro.rev(az-bearing+180)-180)*width/125).toFloat()
    fun y(alt: Double, elevation: Double, height: Int) = (height/2.0-(alt-elevation)*height/85).toFloat()
    fun inField(x: Float, y: Float, width: Int, height: Int) =
        width>0 && height>0 && x.isFinite() && y.isFinite() && x in 0f..width.toFloat() && y in 0f..height.toFloat()

    /** Descarta el segmento completo: evita saltos de proyección y trazos desde fuera del campo. */
    fun segmentVisible(alt: Double, x: Float, y: Float, nextAlt: Double, nextX: Float, nextY: Float, width: Int, height: Int): Boolean {
        if(!alt.isFinite() || !nextAlt.isFinite() || alt<0 || nextAlt<0) return false
        if(!inField(x,y,width,height) || !inField(nextX,nextY,width,height)) return false
        return hypot((nextX-x).toDouble(),(nextY-y).toDouble())<=min(width,height)/2.0
    }
    fun nearest(xs: FloatArray, ys: FloatArray, size: Int, x: Float, y: Float, radius: Float): Int {
        var found=-1; var distance=radius*radius
        for(i in 0 until size) {
            val d=(xs[i]-x).pow(2)+(ys[i]-y).pow(2)
            if(d<=distance) { found=i; distance=d }
        }
        return found
    }
}
/** Rota posiciones, no el lienzo: símbolos, textos y selección comparten coordenadas de pantalla. */
class SkyProjection {
    private var width=0; private var height=0
    private var bearing=0.0; private var elevation=0.0
    private var cosine=1.0; private var sine=0.0
    fun configure(width: Int, height: Int, bearing: Double, elevation: Double, roll: Double) {
        this.width=width; this.height=height; this.bearing=bearing; this.elevation=elevation
        cosine=cos(roll*Astro.RAD); sine=sin(roll*Astro.RAD)
    }
    fun x(az: Double, alt: Double): Float {
        val dx=SkyGeometry.x(az,bearing,width)-width/2.0
        val dy=SkyGeometry.y(alt,elevation,height)-height/2.0
        return (width/2.0+dx*cosine-dy*sine).toFloat()
    }
    fun y(az: Double, alt: Double): Float {
        val dx=SkyGeometry.x(az,bearing,width)-width/2.0
        val dy=SkyGeometry.y(alt,elevation,height)-height/2.0
        return (height/2.0+dx*sine+dy*cosine).toFloat()
    }
}
/** Rectángulos reutilizados, en orden de prioridad: una etiqueta posterior no tapa las anteriores. */
class LabelLayout(capacity: Int=256) {
    private val bounds=FloatArray(capacity*4)
    private var count=0
    fun reset() { count=0 }
    fun offer(left: Float, top: Float, right: Float, bottom: Float, width: Int, height: Int, gap: Float): Boolean {
        if(!SkyGeometry.inField(left,top,width,height) || !SkyGeometry.inField(right,bottom,width,height) ||
            right<=left || bottom<=top || count==bounds.size/4) return false
        for(i in 0 until count) {
            val j=i*4
            if(left<bounds[j+2]+gap && right+gap>bounds[j] && top<bounds[j+3]+gap && bottom+gap>bounds[j+1]) return false
        }
        val j=count++*4; bounds[j]=left; bounds[j+1]=top; bounds[j+2]=right; bounds[j+3]=bottom
        return true
    }
}
class Orientation {
    var bearing=180.0; private set
    var elevation=35.0; private set
    var roll=0.0; private set
    private var filteredX=0.0; private var filteredY=0.0; private var filteredZ=0.0
    private var initial=true
    fun reference(degrees: Double) { require(degrees.isFinite()); bearing=Astro.rev(degrees) }
    fun drag(dx: Float, dy: Float, width: Int, height: Int, sensor: Boolean) {
        bearing=Astro.rev(bearing-dx*125/width.coerceAtLeast(1))
        if(!sensor) elevation=(elevation+dy*85/height.coerceAtLeast(1)).coerceIn(-10.0,90.0)
    }
    fun tilt(delta: Double) { elevation=(elevation+delta).coerceIn(-10.0,90.0) }
    fun resetFilter() { initial=true; roll=0.0 }
    /** Remapea aceleración física a ejes de pantalla. Rotación 0,1,2,3 = 0°,90°,180°,270°. */
    fun acceleration(x: Float, y: Float, z: Float, rotation: Int) {
        val sx: Double; val sy: Double
        when(rotation) {
            1 -> { sx=-y.toDouble(); sy=x.toDouble() }
            2 -> { sx=-x.toDouble(); sy=-y.toDouble() }
            3 -> { sx=y.toDouble(); sy=-x.toDouble() }
            else -> { sx=x.toDouble(); sy=y.toDouble() }
        }
        val alpha=if(initial) 1.0 else 0.16
        filteredX+=alpha*(sx-filteredX); filteredY+=alpha*(sy-filteredY); filteredZ+=alpha*(z-filteredZ); initial=false
        val norm=sqrt(filteredX*filteredX+filteredY*filteredY+filteredZ*filteredZ)
        if(norm<1) return
        elevation=Astro.asind(filteredZ/norm).coerceIn(-10.0,90.0)
        roll=Astro.atan2d(filteredX,filteredY).coerceIn(-75.0,75.0)
        // Ninguna lectura modifica bearing: el acelerómetro no conoce el norte.
    }
}
object SensorPolicy { fun active(resumed: Boolean, sky: Boolean, enabled: Boolean, available: Boolean)=resumed && sky && enabled && available }
