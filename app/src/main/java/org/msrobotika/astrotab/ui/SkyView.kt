package org.msrobotika.astrotab.ui

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import org.msrobotika.astrotab.astro.Astro
import org.msrobotika.astrotab.astro.AstroPhoto
import org.msrobotika.astrotab.catalog.MessierObject
import org.msrobotika.astrotab.catalog.Catalog
import org.msrobotika.astrotab.data.Location
import org.msrobotika.astrotab.ui.theme.*
import org.msrobotika.astrotab.satellites.SatellitePoint
import kotlin.math.*

data class Selection(val name: String, val kind: String, val ra: Double, val dec: Double, val magnitude: Double?, val moon: Boolean=false, val messierId: String?=null)

/** Arrays reutilizados: no crea objetos por estrella ni ejecuta astronomía en onDraw. */
@android.annotation.SuppressLint("ViewConstructor") // Construcción programática con dependencias explícitas.
class SkyView(context: Context, private val catalog: Catalog, val orientation: Orientation,
    private val onSelected: (Selection)->Unit) : View(context) {
    var red=false
    var bortle=5
    var useSensor=false
    var showField=false
    var fieldWidth=0.0
    var fieldHeight=0.0
    var guide: Selection?=null
    var satelliteTrack=emptyList<SatellitePoint>()
    var satelliteLabel=""
    private val band=MilkyWay(context.assets.open("catalog/milkyway.bin"))
    private val bandAlt=DoubleArray(band.ra.size)
    private val bandAz=DoubleArray(band.ra.size)
    private val bandPath=Path().apply { fillType=Path.FillType.EVEN_ODD }
    private val ground=Path()
    private var phase=0.0
    private val starColors=IntArray(catalog.count) { Design.star(catalog.colorIndex[it].toInt()) }
    private val planetColors=Design.planets
    private var frameLabel=""
    private val altitudeLabels=Array(7) { "${it*15}°" }
    var location: Location?=null
    var messierObjects: List<MessierObject> = emptyList()
    private val messierAlt=DoubleArray(110) { -90.0 }
    private val messierAz=DoubleArray(110)
    private val messierX=FloatArray(110) { Float.NaN }
    private val messierY=FloatArray(110) { Float.NaN }
    private var positionMinute=Long.MIN_VALUE
    private var positionPlace: Location?=null
    private var positionObjects: List<MessierObject> = emptyList()
    @Volatile private var positionRequest=0
    private var utc=System.currentTimeMillis()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val count=catalog.count+7
    private val alt=DoubleArray(count) { -90.0 }
    private val az=DoubleArray(count)
    private val pointAlt=DoubleArray(catalog.pointsRa.size) { -90.0 }
    private val pointAz=DoubleArray(catalog.pointsRa.size)
    private val labelAlt=DoubleArray(catalog.labels.size) { -90.0 }
    private val labelAz=DoubleArray(catalog.labels.size)
    private val xs=FloatArray(count) { Float.NaN }
    private val ys=FloatArray(count) { Float.NaN }
    private val bodies=arrayOfNulls<Selection>(7)
    private val projection=SkyProjection()
    private val labelLayout=LabelLayout()
    private val labelOrder=((0 until catalog.count).filter { catalog.magnitude[it]<2.1 && catalog.nameIndex[it]!=255 }+
        (catalog.count until count)).toIntArray()
    private var downX=0f; private var downY=0f; private var lastX=0f; private var lastY=0f; private var moved=false
    private val density=resources.displayMetrics.density
    init { contentDescription="Mapa del cielo: arrastra para girar el rumbo y toca para abrir la ficha. Elevación táctil si el sensor está desactivado."; isFocusable=true }
    fun recalculate(time: Long) {
        val place=location ?: run { invalidate(); return }
        applyPositions(calculatePositions(time,place,messierObjects))
    }
    /** La app calcula en su worker; recalculate síncrono se conserva para las pruebas JVM. */
    fun recalculateAsync(time: Long, executor: java.util.concurrent.ExecutorService) {
        val place=location ?: return
        val objects=messierObjects
        if(positionMinute==time/60000 && positionPlace==place && positionObjects==objects) return
        positionMinute=time/60000; positionPlace=place; positionObjects=objects
        val request=++positionRequest
        executor.execute {
            if(positionRequest!=request) return@execute
            val positions=calculatePositions(time,place,objects)
            post { if(positionRequest==request && isAttachedToWindow) applyPositions(positions) }
        }
    }
    private data class Positions(val time: Long, val bandAlt: DoubleArray,val bandAz: DoubleArray,val alt: DoubleArray, val az: DoubleArray, val pointAlt: DoubleArray,
        val pointAz: DoubleArray, val labelAlt: DoubleArray, val labelAz: DoubleArray, val messierAlt: DoubleArray,
        val messierAz: DoubleArray, val bodies: Array<Selection?>)
    private fun applyPositions(p: Positions) {
        utc=p.time; phase=Astro.moonPhase(Astro.dayNumber(utc)).age; p.bandAlt.copyInto(bandAlt); p.bandAz.copyInto(bandAz); p.alt.copyInto(alt); p.az.copyInto(az); p.pointAlt.copyInto(pointAlt); p.pointAz.copyInto(pointAz)
        p.labelAlt.copyInto(labelAlt); p.labelAz.copyInto(labelAz); p.messierAlt.copyInto(messierAlt); p.messierAz.copyInto(messierAz); p.bodies.copyInto(bodies)
        for(i in 1 until labelOrder.size) { val candidate=labelOrder[i]; var j=i-1; while(j>=0 && magnitude(labelOrder[j])>magnitude(candidate)) { labelOrder[j+1]=labelOrder[j]; j-- }; labelOrder[j+1]=candidate }
        updateFrameLabel(); invalidate()
    }
    private fun calculatePositions(time: Long, place: Location, objects: List<MessierObject>): Positions {
        val alt=DoubleArray(count); val az=DoubleArray(count)
        val pointAlt=DoubleArray(catalog.pointsRa.size); val pointAz=DoubleArray(catalog.pointsRa.size)
        val labelAlt=DoubleArray(catalog.labels.size); val labelAz=DoubleArray(catalog.labels.size)
        val messierAlt=DoubleArray(110); val messierAz=DoubleArray(110); val bodies=arrayOfNulls<Selection>(7)
        val d=Astro.dayNumber(time); val lst=Astro.gmst(d)+place.longitude
        val ba=DoubleArray(band.ra.size); val bz=DoubleArray(band.ra.size)
        for(i in ba.indices) { val h=Astro.toHorizon(band.ra[i].toDouble(),band.dec[i].toDouble(),lst,place.latitude); ba[i]=h.alt; bz[i]=h.az }
        for(i in objects.indices) {
            val h=AstroPhoto.horizon(AstroPhoto.precess(objects[i].eq,time),time,place)
            messierAlt[i]=h.alt; messierAz[i]=h.az
        }
        for(i in 0 until catalog.count) {
            val h=Astro.toHorizon(catalog.ra[i],catalog.dec[i],lst,place.latitude); alt[i]=h.alt; az[i]=h.az
        }
        for(i in pointAlt.indices) {
            val h=Astro.toHorizon(catalog.pointsRa[i],catalog.pointsDec[i],lst,place.latitude); pointAlt[i]=h.alt; pointAz[i]=h.az
        }
        for(i in labelAlt.indices) {
            val label=catalog.labels[i]; val h=Astro.toHorizon(label.ra,label.dec,lst,place.latitude); labelAlt[i]=h.alt; labelAz[i]=h.az
        }
        val sun=Astro.sun(d); val moon=Astro.moon(d)
        bodies[0]=Selection("Sol","Sistema Solar",sun.ra,sun.dec,-26.7)
        bodies[1]=Selection("Luna","Sistema Solar · paralaje aplicado",moon.eq.ra,moon.eq.dec,null,true)
        val names=arrayOf("Mercurio","Venus","Marte","Júpiter","Saturno")
        repeat(5) { p -> val state=Astro.planet(p,d); bodies[p+2]=Selection(names[p],"Planeta",state.eq.ra,state.eq.dec,state.magnitude) }
        repeat(7) { p ->
            val b=bodies[p]!!
            val h=if(b.moon) Astro.moonHorizon(d,lst,place.latitude) else Astro.toHorizon(b.ra,b.dec,lst,place.latitude)
            alt[catalog.count+p]=h.alt; az[catalog.count+p]=h.az
        }
        return Positions(time,ba,bz,alt,az,pointAlt,pointAz,labelAlt,labelAz,messierAlt,messierAz,bodies)
    }
    fun updateFrameLabel() { frameLabel=if(alt[catalog.count]>0) "De día: el Sol está a ${DisplayFormat.number(alt[catalog.count])}°" else "Rumbo ${orientation.bearing.roundToInt()}° manual · altura ${orientation.elevation.roundToInt()}°" }
    private fun magnitude(i: Int)=if(i<catalog.count) catalog.magnitude[i] else bodies[i-catalog.count]?.magnitude ?: -12.0
    private fun drawSegment(canvas: Canvas, az: Double, alt: Double, nextAz: Double, nextAlt: Double) {
        val x=projection.x(az,alt); val y=projection.y(az,alt)
        val nx=projection.x(nextAz,nextAlt); val ny=projection.y(nextAz,nextAlt)
        if(SkyGeometry.segmentVisible(alt,x,y,nextAlt,nx,ny,width,height)) canvas.drawLine(x,y,nx,ny,paint)
    }
    private fun drawLabel(canvas: Canvas, label: String, x: Float, y: Float) {
        if(label.isEmpty() || !SkyGeometry.inField(x,y,width,height)) return
        val length=paint.measureText(label)
        for(position in 0..3) {
            val left=if(position<2) x+8*density else x-8*density-length
            val baseline=if(position%2==0) y-7*density else y+7*density-paint.ascent()
            if(labelLayout.offer(left,baseline+paint.ascent(),left+length,baseline+paint.descent(),width,height,4*density)) {
                canvas.drawText(label,left,baseline,paint); return
            }
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val text=if(red) Design.red(Design.colors[4]) else Design.colors[4]
        val accent=if(red) Design.red(Design.colors[3]) else Design.colors[3]
        val line=if(red) Design.red(Design.colors[2]) else Design.colors[2]
        val solarAltitude=alt[catalog.count]
        canvas.drawColor(SkyControls.background(solarAltitude,red))
        paint.textSize=15*density; paint.color=text
        if(location==null) { canvas.drawText("Introduce tus coordenadas en Ajustes",24*density,height/2f,paint); return }
        projection.configure(width,height,orientation.bearing,orientation.elevation,if(useSensor) orientation.roll else 0.0)
        // Banda: desenvolver cada anillo antes de rotar. El clip de Canvas recorta el campo.
        paint.style=Paint.Style.FILL; paint.color=accent; paint.alpha=12
        val scale=width/125f
        val roll=if(useSensor) orientation.roll*Astro.RAD else 0.0
        val rollCos=cos(roll); val rollSin=sin(roll)
        val horizontalRange=(abs(rollCos)*width/2+abs(rollSin)*height/2)/scale
        val verticalRange=(abs(rollSin)*width/2+abs(rollCos)*height/2)/(height/85.0)
        for(layer in 0..4) {
            bandPath.rewind(); bandPath.fillType=Path.FillType.EVEN_ODD
            for(r in band.starts.indices) {
                if(band.flags[r]/2!=layer) continue
                val first=band.starts[r]; val end=first+band.lengths[r]
                // Anillos invisibles por debajo del horizonte se descartan antes de proyectar.
                var minAltitude=90.0; var maxAltitude= -90.0; var minDelta=Double.POSITIVE_INFINITY; var maxDelta=Double.NEGATIVE_INFINITY
                var previousBound=Astro.rev(bandAz[first]-orientation.bearing+180)-180
                for(i in first until end) {
                    var delta=Astro.rev(bandAz[i]-orientation.bearing+180)-180
                    while(delta-previousBound>180) delta-=360
                    while(delta-previousBound< -180) delta+=360
                    previousBound=delta; minDelta=min(minDelta,delta); maxDelta=max(maxDelta,delta)
                    minAltitude=min(minAltitude,bandAlt[i]); maxAltitude=max(maxAltitude,bandAlt[i])
                }
                if(maxAltitude<max(0.0,orientation.elevation-verticalRange) || minAltitude>orientation.elevation+verticalRange || minDelta>horizontalRange || maxDelta< -horizontalRange) continue
                var previous=Astro.rev(bandAz[first]-orientation.bearing+180)-180
                for(i in first until end) {
                    var delta=Astro.rev(bandAz[i]-orientation.bearing+180)-180
                    while(delta-previous>180) delta-=360
                    while(delta-previous< -180) delta+=360
                    previous=delta
                    val xx=projection.x(orientation.bearing+delta,bandAlt[i])
                    val yy=projection.y(orientation.bearing+delta,bandAlt[i])
                    // La proyección cilíndrica normal envuelve; desplazamiento continuo equivalente.
                    val wrapped=Astro.rev(delta+180)-180
                    val shift=(delta-wrapped)*scale
                    val x=xx+(shift*rollCos).toFloat(); val y=yy+(shift*rollSin).toFloat()
                    if(i==first) bandPath.moveTo(x,y) else bandPath.lineTo(x,y)
                }; bandPath.close()
            }; canvas.drawPath(bandPath,paint)
        }; paint.alpha=255
        // Suelo opaco, con el mismo roll que el cielo.
        ground.rewind(); ground.moveTo(projection.x(orientation.bearing-179,0.0),projection.y(orientation.bearing-179,0.0))
        ground.lineTo(projection.x(orientation.bearing+179,0.0),projection.y(orientation.bearing+179,0.0))
        ground.lineTo(projection.x(orientation.bearing+179,-180.0),projection.y(orientation.bearing+179,-180.0))
        ground.lineTo(projection.x(orientation.bearing-179,-180.0),projection.y(orientation.bearing-179,-180.0)); ground.close()
        paint.color=if(red) Design.red(0xFF090E16.toInt()) else 0xFF090E16.toInt(); canvas.drawPath(ground,paint)
        labelLayout.reset()
        // Reserva la leyenda fija antes de colocar nombres; todos los textos quedan horizontales.
        labelLayout.offer(0f,0f,width.toFloat(),32*density,width,height,0f)
        paint.strokeWidth=density; paint.color=line
        for(a in 0..90 step 15) {
            for(azimuth in 0 until 360 step 5) drawSegment(canvas,azimuth.toDouble(),a.toDouble(),(azimuth+5).toDouble(),a.toDouble())
        }
        for(a in 0..330 step 30) {
            for(altitude in 0 until 90 step 5) drawSegment(canvas,a.toDouble(),altitude.toDouble(),a.toDouble(),(altitude+5).toDouble())
        }
        paint.color=accent; paint.alpha=75
        for(polyIndex in catalog.polylines.indices) { val poly=catalog.polylines[polyIndex]; for(j in poly.first until poly.first+poly.count-1) {
            drawSegment(canvas,pointAz[j],pointAlt[j],pointAz[j+1],pointAlt[j+1])
        } }
        paint.alpha=255
        paint.style=Paint.Style.STROKE; paint.strokeWidth=2*density; paint.color=accent
        for(i in messierObjects.indices) {
            messierX[i]=Float.NaN; messierY[i]=Float.NaN
            if(messierAlt[i]<0) continue
            val x=projection.x(messierAz[i],messierAlt[i]); val y=projection.y(messierAz[i],messierAlt[i])
            if(!SkyGeometry.inField(x,y,width,height)) continue
            messierX[i]=x; messierY[i]=y
            Icons.draw(canvas,paint,Icons.type(messierObjects[i].type),x-6*density,y-6*density,12*density,accent)
        }
        paint.style=Paint.Style.FILL
        val limit=min(6.0,Astro.limitingMag(bortle))
        for(i in 0 until count) {
            xs[i]=Float.NaN; ys[i]=Float.NaN
            if(alt[i]<0 || (i<catalog.count && catalog.magnitude[i]>limit)) continue
            if(abs(alt[i]-orientation.elevation)>verticalRange || abs(Astro.rev(az[i]-orientation.bearing+180)-180)>horizontalRange) continue
            val x=projection.x(az[i],alt[i]); val y=projection.y(az[i],alt[i])
            if(!SkyGeometry.inField(x,y,width,height)) continue
            xs[i]=x; ys[i]=y
            val body=i>=catalog.count
            val mag=if(body) 0.0 else catalog.magnitude[i]
            val color=if(body) planetColors[i-catalog.count] else starColors[i]
            paint.color=if(red) Design.red(color) else color
            paint.alpha=if(body) 255 else (245-mag*18).toInt().coerceIn(90,255)
            if(i==catalog.count+1) Icons.draw(canvas,paint,Icon.MOON,x-9*density,y-9*density,18*density,if(red) Design.red(color) else color,phase)
            else canvas.drawCircle(x,y,if(body) 5*density else max(0.7,2.8-mag*0.36).toFloat()*density,paint)
        }
        // Prioridad por brillo preparada al publicar efemérides, nunca se ordena en el gesto.
        paint.alpha=220; paint.textSize=14*density
        for(i in labelOrder) {
            paint.color=if(i>=catalog.count) accent else text
            drawLabel(canvas,if(i>=catalog.count) bodies[i-catalog.count]?.name ?: "" else catalog.name(i),xs[i],ys[i])
        }
        paint.alpha=105; paint.textSize=13*density; paint.color=accent
        for(i in catalog.labels.indices) if(labelAlt[i]>0 && catalog.labels[i].rank<=2) {
            val label=catalog.constellations[i]; val x=projection.x(labelAz[i],labelAlt[i]); val y=projection.y(labelAz[i],labelAlt[i]); val length=paint.measureText(label)
            if(labelLayout.offer(x-length/2,y+paint.ascent(),x+length/2,y+paint.descent(),width,height,4*density)) canvas.drawText(label,x-length/2,y,paint)
        }
        paint.alpha=180; paint.color=text
        for(a in 0..90 step 15) {
            val azimuth=orientation.bearing-50
            drawLabel(canvas,altitudeLabels[a/15],projection.x(azimuth,a.toDouble()),projection.y(azimuth,a.toDouble()))
        }
        for(i in 0..7) {
            drawLabel(canvas,SkyControls.cardinals[i],projection.x(i*45.0,0.0),projection.y(i*45.0,0.0))
        }
        paint.alpha=255; paint.color=accent
        canvas.drawLine(width/2f-12*density,height/2f,width/2f+12*density,height/2f,paint)
        canvas.drawLine(width/2f,height/2f-12*density,width/2f,height/2f+12*density,paint)
        paint.textSize=14*density; paint.color=text
        canvas.drawText(frameLabel,12*density,24*density,paint)
        if(satelliteTrack.isNotEmpty()) {
            paint.color=if(red)Design.red(Design.colors[6])else Design.colors[6];paint.strokeWidth=2*density
            for(i in 0 until satelliteTrack.lastIndex){val a=satelliteTrack[i];val b=satelliteTrack[i+1];paint.alpha=if(a.visible && b.visible)255 else 75;drawSegment(canvas,a.azimuth,a.altitude,b.azimuth,b.altitude)}
            paint.alpha=255
            paint.textSize=14*density
            val labelX=(width-paint.measureText(satelliteLabel)-12*density).coerceAtLeast(12*density)
            paint.color=if(red)Design.red(Design.colors[1])else Design.colors[1]
            canvas.drawRoundRect(labelX-6*density,30*density,width-6*density,52*density,5*density,5*density,paint)
            paint.color=if(red)Design.red(Design.colors[6])else Design.colors[6]
            canvas.drawText(satelliteLabel,labelX,46*density,paint)
        }
        if(showField) { val fw=(fieldWidth*width/125).toFloat(); val fh=(fieldHeight*height/85).toFloat(); paint.style=Paint.Style.STROKE; paint.strokeWidth=2*density; paint.color=accent; canvas.drawRect(width/2f-fw/2,height/2f-fh/2,width/2f+fw/2,height/2f+fh/2,paint); paint.style=Paint.Style.FILL }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { parent.requestDisallowInterceptTouchEvent(true); downX=event.x; downY=event.y; lastX=event.x; lastY=event.y; moved=false; return true }
            MotionEvent.ACTION_MOVE -> {
                if(hypot(event.x-downX,event.y-downY)>8*density) moved=true
                if(moved) { orientation.drag(event.x-lastX,event.y-lastY,width,height,useSensor); updateFrameLabel(); postInvalidateDelayed(33) }
                lastX=event.x; lastY=event.y; return true
            }
            MotionEvent.ACTION_UP -> {
                if(!moved) {
                    val target=SkyGeometry.nearest(messierX,messierY,messierObjects.size,event.x,event.y,20*density)
                    if(target>=0) {
                        val m=messierObjects[target]
                        onSelected(Selection(m.label,m.type,m.ra,m.dec,m.magnitude,messierId=m.id))
                        performClick(); parent.requestDisallowInterceptTouchEvent(false); return true
                    }
                    val nearest=SkyGeometry.nearest(xs,ys,count,event.x,event.y,32*density)
                    if(nearest>=0) {
                        val selection=if(nearest<catalog.count) Selection(catalog.name(nearest),"Estrella · ${catalog.constellationName(nearest)} · J2000",catalog.ra[nearest],catalog.dec[nearest],catalog.magnitude[nearest]) else bodies[nearest-catalog.count]!!
                        onSelected(selection)
                    }
                    performClick()
                }
                parent.requestDisallowInterceptTouchEvent(false); return true
            }
            MotionEvent.ACTION_CANCEL -> { parent.requestDisallowInterceptTouchEvent(false); return true }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}

@android.annotation.SuppressLint("ViewConstructor") // No se infla desde XML.
class MoonView(context: Context, private val age: Double, private val south: Boolean, private val red: Boolean): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val palette=Palette(red)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r=min(width,height)*0.36f; val cx=width/2f; val cy=height/2f
        paint.color=palette.raised; canvas.drawCircle(cx,cy,r,paint)
        paint.color=palette.text
        val waxing=age<180; val litRight=waxing xor south
        val cosine=cos(age*Astro.RAD).toFloat()
        // Barrido de franjas sobre una esfera: terminador elíptico según elongación.
        var y=-r
        while(y<=r) {
            val half=sqrt(max(0f,r*r-y*y))
            val start=if(litRight) cosine*half else -half
            val end=if(litRight) half else -cosine*half
            canvas.drawRect(cx+start,cy+y,cx+end,cy+y+1.5f,paint); y+=1f
        }
    }
}
