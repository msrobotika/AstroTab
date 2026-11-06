package org.msrobotika.astrotab.ui.theme

import android.app.Dialog
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import org.msrobotika.astrotab.R
import org.msrobotika.astrotab.astro.Astro
import kotlin.math.*

/** Trazos originales MIT. Coordenadas normalizadas 0..24; sin recursos externos. */
enum class Icon { MOON,CLOUD,DEW,WIND,STAR,GALAXY,NEBULA,CLUSTER,PLANET,TELESCOPE,CAMERA,CLOCK,AURORA,SETTINGS,SUN,HOME,CHART,TOOLS,MORE,CALENDAR,BELL,SATELLITE }
object Icons {
    fun type(type: String)=when { type.contains("Galax",true) -> Icon.GALAXY; type.contains("Cúm",true) -> Icon.CLUSTER; type.contains("Neb",true) -> Icon.NEBULA; else -> Icon.STAR }
    fun screen(s: String)=when(s) { "Inicio" -> Icon.HOME; "Cielo" -> Icon.STAR; "Objetos" -> Icon.GALAXY; "Noche","Esta noche","Plan de sesión" -> Icon.TELESCOPE; "Tiempo" -> Icon.CLOUD; "Luna" -> Icon.MOON; "Auroras" -> Icon.AURORA; "Ajustes" -> Icon.SETTINGS; "Más" -> Icon.MORE; "Equipos" -> Icon.CAMERA; "Eventos" -> Icon.CALENDAR; "Alarmas" -> Icon.BELL; "Satélites" -> Icon.SATELLITE; else -> Icon.TOOLS }
    fun draw(c: Canvas,p: Paint,icon: Icon,x: Float,y: Float,size: Float,color: Int,age: Double=90.0) {
        c.save(); c.translate(x,y); c.scale(size/24,size/24)
        p.color=color; p.alpha=255; p.style=Paint.Style.STROKE; p.strokeWidth=1.5f; p.strokeCap=Paint.Cap.ROUND
        fun line(a: Float,b: Float,d: Float,e: Float)=c.drawLine(a,b,d,e,p)
        when(icon) {
            Icon.MOON -> {
                p.alpha=40; p.style=Paint.Style.FILL; c.drawCircle(12f,12f,9f,p); p.alpha=255
                val cosine=cos(age*Astro.RAD).toFloat(); var yy=-9f
                while(yy<=9) { val h=sqrt(max(0f,81-yy*yy)); val a=if(age<180) cosine*h else -h; val b=if(age<180) h else -cosine*h; c.drawRect(12+a,12+yy,12+b,12+yy+0.6f,p); yy+=0.5f }
            }
            Icon.CLOUD -> { c.drawArc(3f,8f,14f,18f,90f,180f,false,p); c.drawArc(8f,4f,20f,17f,180f,180f,false,p); c.drawArc(15f,10f,23f,18f,270f,180f,false,p); line(8f,18f,19f,18f) }
            Icon.DEW -> { line(12f,2f,5f,13f); line(12f,2f,19f,13f); c.drawArc(5f,8f,19f,22f,0f,180f,false,p); line(8f,15f,10f,18f) }
            Icon.WIND -> { line(2f,8f,18f,8f); c.drawArc(15f,2f,22f,8f,180f,270f,false,p); line(2f,12f,15f,12f); line(4f,16f,20f,16f); c.drawArc(16f,16f,22f,22f,270f,270f,false,p) }
            Icon.STAR -> { line(12f,2f,12f,22f); line(2f,12f,22f,12f); line(6f,6f,18f,18f); line(18f,6f,6f,18f); c.drawCircle(12f,12f,3f,p) }
            Icon.GALAXY -> { c.rotate(-30f,12f,12f); c.drawOval(2f,7f,22f,17f,p); c.drawOval(7f,9f,17f,15f,p); c.drawCircle(12f,12f,1f,p) }
            Icon.NEBULA -> { c.drawRoundRect(3f,4f,21f,20f,6f,6f,p); line(8f,12f,16f,12f); line(12f,8f,12f,16f) }
            Icon.CLUSTER -> { c.drawCircle(12f,12f,9f,p); p.style=Paint.Style.FILL; c.drawCircle(8f,9f,1.2f,p); c.drawCircle(14f,7f,1.2f,p); c.drawCircle(12f,13f,1.2f,p); c.drawCircle(17f,14f,1.2f,p); c.drawCircle(8f,16f,1.2f,p) }
            Icon.PLANET -> { c.drawCircle(12f,12f,6f,p); c.rotate(-25f,12f,12f); c.drawOval(1f,9f,23f,15f,p) }
            Icon.CAMERA -> { c.drawRoundRect(2f,6f,22f,20f,3f,3f,p); c.drawCircle(12f,13f,5f,p); line(7f,3f,15f,3f) }
            Icon.TELESCOPE -> { c.rotate(-25f,12f,9f); c.drawRect(3f,5f,20f,12f,p); c.restore(); c.save(); c.translate(x,y); c.scale(size/24,size/24); line(12f,12f,6f,22f); line(12f,12f,18f,22f); line(12f,12f,12f,22f) }
            Icon.CLOCK -> { c.drawCircle(12f,12f,9f,p); line(12f,5f,12f,12f); line(12f,12f,17f,15f) }
            Icon.AURORA -> { line(3f,20f,3f,8f); line(7f,17f,7f,4f); line(11f,20f,11f,9f); line(15f,17f,15f,3f); line(19f,20f,19f,7f); line(23f,17f,23f,5f) }
            Icon.SUN -> { c.drawCircle(12f,12f,5f,p); for(i in 0..7) { val a=i*PI/4; line((12+8*cos(a)).toFloat(),(12+8*sin(a)).toFloat(),(12+11*cos(a)).toFloat(),(12+11*sin(a)).toFloat()) } }
            Icon.HOME -> { line(2f,11f,12f,2f); line(12f,2f,22f,11f); line(5f,9f,5f,22f); line(5f,22f,19f,22f); line(19f,22f,19f,9f) }
            Icon.CHART -> { line(3f,3f,3f,21f); line(3f,21f,22f,21f); line(5f,17f,10f,10f); line(10f,10f,15f,13f); line(15f,13f,21f,4f) }
            Icon.SETTINGS -> { c.drawCircle(12f,12f,7f,p); c.drawCircle(12f,12f,2f,p); for(i in 0..7) { val a=i*PI/4; line((12+8*cos(a)).toFloat(),(12+8*sin(a)).toFloat(),(12+11*cos(a)).toFloat(),(12+11*sin(a)).toFloat()) } }
            Icon.TOOLS -> { c.drawArc(12f,2f,22f,12f,0f,265f,false,p); line(13f,9f,3f,19f); line(3f,19f,5f,22f); line(5f,22f,16f,11f); line(17f,2f,17f,7f); line(17f,7f,22f,7f); c.drawCircle(5f,19f,0.7f,p) }
            Icon.MORE -> {p.style=Paint.Style.FILL; for(x0 in listOf(6f,18f))for(y0 in listOf(6f,18f))c.drawCircle(x0,y0,3f,p)}
            Icon.CALENDAR -> {c.drawRoundRect(3f,5f,21f,22f,2f,2f,p);line(3f,10f,21f,10f);line(8f,2f,8f,7f);line(16f,2f,16f,7f);line(7f,14f,10f,14f);line(14f,14f,17f,14f);line(7f,18f,10f,18f)}
            Icon.BELL -> {c.drawArc(5f,3f,19f,18f,180f,180f,false,p);line(5f,10f,5f,17f);line(19f,10f,19f,17f);line(3f,18f,21f,18f);c.drawArc(9f,17f,15f,23f,0f,180f,false,p);line(12f,1f,12f,3f)}
            Icon.SATELLITE -> {c.rotate(-35f,12f,12f);c.drawRect(9f,7f,15f,17f,p);c.drawRect(1f,8f,7f,16f,p);c.drawRect(17f,8f,23f,16f,p);line(7f,12f,9f,12f);line(15f,12f,17f,12f);line(12f,3f,12f,7f)}
        }
        p.style=Paint.Style.FILL; p.alpha=255; c.restore()
    }
}
class IconDrawable(private val icon: Icon,private val color: Int,private val pixels: Int): Drawable() {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas)=Icons.draw(canvas,p,icon,bounds.left.toFloat(),bounds.top.toFloat(),bounds.width().toFloat(),color)
    override fun getIntrinsicWidth()=pixels
    override fun getIntrinsicHeight()=pixels
    override fun setAlpha(alpha: Int) { p.alpha=alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { p.colorFilter=colorFilter }
    @Deprecated("Drawable API") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
class Widgets(val context: Context,val palette: Palette) {
    /** Los controles que contienen un valor se leen como filas, con alineación y pictograma. */
    fun styleValueButton(button: Button,label: String) {
        if(!label.contains(':') || label.startsWith("Rojo:"))return
        button.gravity=Gravity.START or Gravity.CENTER_VERTICAL
        button.textSize=18f
        button.setPadding(dp(16),dp(8),dp(16),dp(8))
        val icon=when {
            label.startsWith("Fecha") || label.startsWith("Zona") -> Icon.CALENDAR
            label.startsWith("Latitud") || label.startsWith("Longitud") -> Icon.PLANET
            label.startsWith("Objeto") || label.startsWith("Declinación") || label.startsWith("Altura") -> Icon.STAR
            label.startsWith("Equipo") || label.startsWith("Focal") || label.startsWith("Sensor") || label.startsWith("Píxel") -> Icon.CAMERA
            label.contains("(s)") || label.contains("(min)") || label.startsWith("Exposición") || label.startsWith("Pausa") -> Icon.CLOCK
            else -> Icon.TOOLS
        }
        button.setCompoundDrawablesWithIntrinsicBounds(IconDrawable(icon,palette.accent,dp(24)),null,null,null)
        button.compoundDrawablePadding=dp(12)
    }
    fun dp(n: Int)=(n*context.resources.displayMetrics.density).roundToInt()
    fun shape(color: Int=palette.surface)=GradientDrawable().apply { setColor(color); cornerRadius=dp(Design.RADIUS).toFloat() }
    fun column()=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
    fun row()=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
    fun text(s: String,size: Float=Design.BODY,color: Int=palette.text)=TextView(context).apply { text=s; textSize=size; setTextColor(color); includeFontPadding=false; setPadding(dp(8),dp(4),dp(8),dp(4)) }
    fun heading(s: String)=text(s,Design.TITLE).apply { setTypeface(typeface,Typeface.BOLD) }
    fun pill(score: Int?)=text(DisplayFormat.status(score),14f,palette.score(score)).apply { background=shape(palette.raised); gravity=Gravity.CENTER; setPadding(dp(12),dp(8),dp(12),dp(8)) }
    fun info(label: String,message: String): Button=Button(context).apply {
        text="ⓘ"; contentDescription="Información: $label"; isAllCaps=false; textSize=20f; setTextColor(palette.accent); background=shape(); minWidth=dp(56); minimumWidth=dp(56); minHeight=dp(56)
        setOnClickListener { sheet(label,message) }
    }
    fun sheet(label: String,message: String) {
        val dialog=Dialog(context); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val c=column().apply { setPadding(dp(16),dp(16),dp(16),dp(16)); setBackgroundColor(palette.background) }
        c.addView(heading(label)); c.addView(text(message)); c.addView(Button(context).apply { setText(R.string.ui_close); setTextColor(palette.accent); background=shape(); minimumHeight=dp(56); setOnClickListener { dialog.dismiss() } })
        dialog.setContentView(ScrollView(context).apply { addView(c) }); dialog.show()
        dialog.window?.apply { setBackgroundDrawableResource(android.R.color.transparent); setLayout((context.resources.displayMetrics.widthPixels-dp(64)).coerceAtMost(dp(800)),context.resources.displayMetrics.heightPixels-dp(48)); val a=attributes; a.screenBrightness=if(palette.red) 0.03f else -1f; attributes=a
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }
    fun section(label: String,detail: String): View=row().apply { addView(heading(label),LinearLayout.LayoutParams(0,-2,1f)); addView(info(label,detail),LinearLayout.LayoutParams(dp(56),dp(56))) }
    fun metric(icon: Icon,value: String,label: String,detail: String,age: Double=90.0): LinearLayout=column().apply {
        background=shape(); setPadding(dp(8),dp(4),dp(8),dp(4))
        val top=row(); top.addView(IconView(context,icon,palette,age),LinearLayout.LayoutParams(dp(32),dp(32))); top.addView(text(label,14f,palette.muted),LinearLayout.LayoutParams(0,-2,1f)); top.addView(info(label,detail),LinearLayout.LayoutParams(dp(56),dp(56))); addView(top)
        val data=text(value,Design.DATA)
        val unit=value.indexOf(' ')
        if(unit>0 && value!="sin datos") data.text=android.text.SpannableString(value).apply { setSpan(android.text.style.AbsoluteSizeSpan(16,true),unit,length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        addView(data)
    }
}
@android.annotation.SuppressLint("ViewConstructor")
class IconView(context: Context,private val icon: Icon,private val palette: Palette,private val age: Double=90.0): View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    init { contentDescription=icon.name }
    override fun onDraw(c: Canvas) { Icons.draw(c,p,icon,0f,0f,min(width,height).toFloat(),palette.accent,age) }
}
@android.annotation.SuppressLint("ViewConstructor")
class ScoreView(context: Context,val score: Int?,private val palette: Palette): View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG); private val label=score?.toString() ?: "—"
    init { contentDescription="Nota ${score?.let { "$it de 100" } ?: "sin datos"}" }
    override fun onDraw(c: Canvas) {
        val size=min(width,height).toFloat(); val r=size*0.39f; val cx=width/2f; val cy=height/2f
        p.style=Paint.Style.STROKE; p.strokeWidth=size*0.065f; p.strokeCap=Paint.Cap.ROUND; p.color=palette.raised
        c.drawArc(cx-r,cy-r,cx+r,cy+r,135f,270f,false,p); p.color=palette.score(score)
        if(score!=null) c.drawArc(cx-r,cy-r,cx+r,cy+r,135f,270f*score/100,false,p)
        p.style=Paint.Style.FILL; p.color=palette.text; p.textSize=size*0.31f; p.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL); c.drawText(label,cx-p.measureText(label)/2,cy+size*0.07f,p)
        p.color=palette.muted; p.textSize=size*0.1f; c.drawText("/ 100",cx-p.measureText("/ 100")/2,cy+size*0.23f,p)
    }
}
