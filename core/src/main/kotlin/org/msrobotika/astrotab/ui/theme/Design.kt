package org.msrobotika.astrotab.ui.theme

import org.msrobotika.astrotab.astro.*
import org.msrobotika.astrotab.data.*
import java.util.Locale
import kotlin.math.*

/** Tokens compartidos por Views, iconos y Canvas; enteros ARGB independientes de Android. */
object Design {
    const val SPACE=8
    const val RADIUS=12
    const val DISPLAY=40f
    const val TITLE=24f
    const val BODY=16f
    const val DATA=28f
    val colors=intArrayOf(0xFF05070F.toInt(),0xFF101827.toInt(),0xFF182438.toInt(),0xFF7FB8FF.toInt(),
        0xFFE7EDF7.toInt(),0xFF96A7BE.toInt(),0xFFFFB020.toInt(),0xFF70D6A3.toInt(),0xFFFF7185.toInt())
    val series=intArrayOf(colors[3],colors[6],colors[7],0xFFBC9AFF.toInt(),colors[8],0xFF8BDAD8.toInt())
    val twilight=intArrayOf(0xFF101827.toInt(),0xFF283A5C.toInt(),0xFF394B69.toInt(),0xFF607CA6.toInt(),0xFF9B7436.toInt(),0xFF6A563B.toInt())
    val planets=intArrayOf(0xFFFFC568.toInt(),0xFFD7E2EF.toInt(),0xFFCEBDA6.toInt(),0xFFFFE4B1.toInt(),0xFFFF9A75.toInt(),0xFFE8C99C.toInt(),0xFFF0DCAB.toInt())
    fun star(bv: Int)=when { bv==127 || bv== -128 -> 0xFFFFFFFF.toInt(); bv<0 -> 0xFFAFC8FF.toInt(); bv<5 -> 0xFFE1EBFF.toInt(); bv<10 -> 0xFFFFEED1.toInt(); else -> 0xFFFFC188.toInt() }
    fun red(color: Int): Int {
        val r=color ushr 16 and 255; val g=color ushr 8 and 255; val b=color and 255
        val v=max(r,max(g,b)); val low=(v*0.18).roundToInt()
        return (color and -0x1000000) or (v shl 16) or (low shl 8) or (low/2)
    }
}
class Palette(val red: Boolean) {
    fun color(c: Int)=if(red) Design.red(c) else c
    private fun token(i: Int)=color(Design.colors[i])
    val background=token(0); val surface=token(1); val raised=token(2); val accent=token(3)
    val text=token(4); val muted=token(5); val warning=token(6); val good=token(7); val bad=token(8)
    val series=IntArray(Design.series.size) { color(Design.series[it]) }
    fun twilight(sun: Double)=color(Design.twilight[when { sun< -18 -> 0; sun< -12 -> 1; sun< -6 -> 2; sun< -4 -> 3; sun<6 -> 4; else -> 5 }])
    fun score(score: Int?)=when { score==null -> muted; score>=70 -> good; score>=40 -> warning; else -> bad }
}
object DisplayFormat {
    fun summary(message: String): String {
        val sentence=message.lineSequence().first().substringBefore(". ")
        return if(sentence.length<=110) sentence else sentence.take(106).substringBeforeLast(' ')+"…"
    }
    fun number(v: Double?, unit: String="", digits: Int=0): String =
        v?.takeIf { it.isFinite() }?.let {
            val rounded=String.format(Locale.forLanguageTag("es-ES"),"%.${digits}f",it)
            (if(rounded.startsWith("-") && rounded.drop(1).all { ch -> ch=='0' || ch==',' || ch=='.' }) rounded.drop(1) else rounded)+unit
        } ?: "sin datos"
    fun duration(hours: Double): String {
        val minutes=(hours.coerceAtLeast(0.0)*60).roundToInt()
        return if(minutes<60) "$minutes min" else "${minutes/60} h"+if(minutes%60==0) "" else " ${minutes%60} min"
    }
    fun status(score: Int?)=when { score==null -> "Sin datos"; score>=70 -> "Buena"; score>=40 -> "Regular"; else -> "Mala" }
    fun sidereal(time: Long,longitude: Double): String {
        val seconds=(Astro.rev(Astro.gmst(Astro.dayNumber(time))+longitude)*240).toInt()%86400
        return "%02d:%02d:%02d".format(Locale.ROOT,seconds/3600,seconds/60%60,seconds%60)
    }
}
object SkyControls {
    fun background(sunAltitude: Double,red: Boolean): Int {
        val color=when {sunAltitude>6 -> 0xFF142D50.toInt();sunAltitude>0 -> 0xFF253147.toInt();sunAltitude> -12 -> 0xFF151D32.toInt();else -> Design.colors[0]}
        return if(red)Design.red(color)else color
    }
    val cardinals=arrayOf("N","NE","E","SE","S","SO","O","NO")
    fun time(start: Long, dawn: Long, progress: Int, maximum: Int=1000): Long =
        start+((dawn.coerceAtLeast(start)-start)*progress.coerceIn(0,maximum).toDouble()/maximum).roundToLong()
    fun dawn(now: Long, events: List<AstroPhoto.Event>): Long = events.firstOrNull { it.time>now && it.label.startsWith("Salida/puesta del Sol") && it.label.endsWith("ascendente") }?.time ?: now+24*HOUR
    fun field(e: Equipment)=Optics.field(e.width,e.focal) to Optics.field(e.height,e.focal)
    fun target(ra: Double,dec: Double,time: Long,place: Location,precess: Boolean=false): Astro.Hor =
        AstroPhoto.horizon(if(precess) AstroPhoto.precess(Astro.Eq(ra,dec),time) else Astro.Eq(ra,dec),time,place)
    fun automaticRed(sunAltitude: Double, rising: Boolean)=sunAltitude < if(rising) 0.0 else -6.0
    /** Tras la salida del Sol, Inicio prepara la próxima noche sin cambiar los límites del motor. */
    fun nightReference(now: Long,zone: java.util.TimeZone,place: Location): Long {
        val hour=java.util.Calendar.getInstance(zone).apply {timeInMillis=now}.get(java.util.Calendar.HOUR_OF_DAY)
        val sun=AstroPhoto.horizon(Astro.sun(Astro.dayNumber(now)),now,place).alt
        return if(hour<12 && sun>=0) now+12*HOUR else now
    }
}
