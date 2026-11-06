package org.msrobotika.astrotab

import android.graphics.*
import android.os.Looper
import android.view.*
import android.widget.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.Shadows.shadowOf
import org.msrobotika.astrotab.ui.*
import org.msrobotika.astrotab.ui.theme.*
import java.io.File
import java.util.concurrent.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[23],qualifiers="w1024dp-h600dp-land-mdpi")
class RedesignScreensTest {
    private fun all(v: View): List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap {all(v.getChildAt(it))} else emptyList()
    private fun settle(a: MainActivity) {
        val worker=MainActivity::class.java.getDeclaredField("worker").apply {isAccessible=true}.get(a) as ExecutorService
        repeat(8) { worker.submit {}.get(60,TimeUnit.SECONDS); shadowOf(Looper.getMainLooper()).idle() }
    }
    private fun start(): org.robolectric.android.controller.ActivityController<MainActivity> {
        RuntimeEnvironment.getApplication().getSharedPreferences("astrotab",0).edit().clear().putString("latitude","40.4168").putString("longitude","-3.7038").putString("zoneId","Europe/Madrid").putString("sensor","false").commit()
        return Robolectric.buildActivity(MainActivity::class.java).setup()
    }
    private fun root(a: MainActivity)=a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
    private fun layout(v: View) { v.measure(View.MeasureSpec.makeMeasureSpec(1024,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY)); v.layout(0,0,1024,600) }
    private fun bounds(v: View,x: Int=0,y: Int=0) {
        if(v.visibility!=View.VISIBLE) return
        val xx=x+v.left; val yy=y+v.top
        assertTrue("${v.javaClass.simpleName} ${if(v is TextView) v.text else v.tag}: $xx,$yy ${v.width}x${v.height}",xx>=0 && yy>=0 && xx+v.width<=1024 && yy+v.height<=600)
        if(v is ViewGroup) for(i in 0 until v.childCount) bounds(v.getChildAt(i),xx,yy)
    }
    private fun go(a: MainActivity,s: String) { MainActivity::class.java.getDeclaredMethod("show",String::class.java).apply {isAccessible=true}.invoke(a,s); settle(a); layout(root(a)) }
    @Test fun homeInflatesAt1024x600NoScrollAndEveryVisibleChildFits() {
        val c=start(); try { val a=c.get(); settle(a); val r=root(a); layout(r)
            val home=all(r).filterIsInstance<HomePanel>().single(); assertFalse(all(home).any {it is ScrollView})
            assertTrue(all(home).filterIsInstance<TextView>().any {it.text.contains("UTC") && it.text.contains("TSL")})
            assertEquals(3,all(home).filterIsInstance<Button>().count {it.contentDescription?.startsWith("M") == true && it.contentDescription?.contains("de 100")==true})
            bounds(r)
            assertTrue(all(home).filterIsInstance<TextView>().any {it.text.contains("sin datos",true)})
            assertNull(all(home).filterIsInstance<ScoreView>().single().score)
            all(home).filterIsInstance<Button>().first {it.contentDescription?.startsWith("M")==true}.performClick(); settle(a)
            assertFalse(all(root(a)).any {it is HomePanel})
        } finally { c.pause().stop().destroy() }
    }
    @Test @Config(sdk=[28],qualifiers="w1024dp-h600dp-land-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun nativeHomeWithDewWarningFitsAndMoonCellsOpenDetails() {
        val c=start(); try { val a=c.get(); settle(a); val r=root(a); layout(r)
            val home=all(r).filterIsInstance<HomePanel>().single()
            val model=HomePanel::class.java.getDeclaredField("model").apply {isAccessible=true}.get(home) as HomeData
            // Datos sintéticos de prueba, nunca se guardan en caché ni en capturas de entrega.
            val weather=org.msrobotika.astrotab.data.Cloud(model.now,96.6,temperature=8.0,dewPoint=7.0,wind=12.0)
            home.bind(model.copy(weather=weather,week=model.week.map {it.copy(score=2,clouds=96.6)},source="Fixture sintética de prueba")); layout(r); bounds(r)
            assertTrue(all(home).filterIsInstance<TextView>().any {it.text.contains("Riesgo de rocío")})
            assertTrue(all(home).filterIsInstance<TextView>().any {it.text=="Nubes 97 %"})
            go(a,"Luna"); val calendar=all(root(a)).filterIsInstance<MoonCalendar>().single()
            val offset=MoonCalendar::class.java.getDeclaredField("offset").apply {isAccessible=true}.getInt(calendar)
            val event=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,(offset%7+0.5f)*calendar.width/7,30f,0)
            calendar.dispatchTouchEvent(event); event.recycle(); calendar.performClick()
            assertTrue(org.robolectric.shadows.ShadowDialog.getLatestDialog().isShowing)
        } finally { c.pause().stop().destroy() }
    }
    @Test fun skySearchCentresTargetAndSliderAndFrameToggleWork() {
        val c=start(); try { val a=c.get(); settle(a); go(a,"Cielo")
            var r=root(a); val map=all(r).filterIsInstance<SkyView>().single()
            all(r).filterIsInstance<Button>().first {it.text=="Buscar objeto"}.performClick()
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()
            all(dialog.window!!.decorView).filterIsInstance<Button>().first {it.text.startsWith("M31 ·") || it.text.startsWith("M31 ")}.performClick()
            assertTrue(all(r).filterIsInstance<TextView>().any {it.text.contains("° alt") && it.text.contains("° az")})
            all(r).filterIsInstance<Button>().first {it.text=="Campo de cámara: no"}.performClick(); settle(a); r=root(a)
            assertTrue(all(r).filterIsInstance<SkyView>().single().showField)
            val slider=all(r).filterIsInstance<SeekBar>().single(); val listener=shadowOf(slider).onSeekBarChangeListener
            listener.onProgressChanged(slider,1000,true)
            val offset=MainActivity::class.java.getDeclaredField("offset").apply {isAccessible=true}
            assertTrue(offset.getLong(a)>0); all(r).filterIsInstance<Button>().first {it.text=="Ahora"}.performClick(); assertEquals(0L,offset.getLong(a))
            assertTrue(map.orientation.bearing.isFinite())
        } finally { c.pause().stop().destroy() }
    }
    @Test @Config(sdk=[28],qualifiers="w1024dp-h600dp-land-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun nativeScreens1024x600() {
        val c=start(); try { val a=c.get(); settle(a); val dir=File(System.getProperty("astrotab.screens")!!); dir.mkdirs()
            val screens=listOf("Inicio","Cielo","Objetos","Esta noche","Tiempo","Luna","Auroras","Herramientas","Ajustes","Objeto","Exposición","Encuadre","Reloj polar","Vía Láctea","Horario","Calculadoras","Hiperfocal y profundidad de campo","Timelapse","Circumpolares / trazos","Panorámica / mosaico","Apilado","Sesión","Bitácora","Planetas","Meteoros")
            for((i,s) in screens.withIndex()) { go(a,s); val r=root(a); val bitmap=Bitmap.createBitmap(1024,600,Bitmap.Config.ARGB_8888); r.draw(Canvas(bitmap))
                if(s=="Inicio") bounds(r)
                assertTrue("Render no vacío $s",bitmap.getPixel(10,10)!=bitmap.getPixel(512,300) || all(r).filterIsInstance<TextView>().isNotEmpty())
                File(dir,"%02d-%s.png".format(i,s.replace(' ','-').replace('/','-'))).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }; bitmap.recycle()
            }
            all(root(a)).filterIsInstance<Button>().first {it.text=="Modo rojo"}.performClick(); settle(a); go(a,"Inicio")
            val bitmap=Bitmap.createBitmap(1024,600,Bitmap.Config.ARGB_8888); root(a).draw(Canvas(bitmap)); File(dir,"26-Inicio-rojo.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            val pixels=IntArray(1024*600); bitmap.getPixels(pixels,0,1024,0,0,1024,600)
            assertTrue("Sin verde/azul dominante en Inicio rojo",pixels.all {Color.red(it)>=Color.green(it) && Color.red(it)>=Color.blue(it)})
            bitmap.recycle()
            for((i,s) in screens.take(9).drop(1).withIndex()) {
                go(a,s); val b=Bitmap.createBitmap(1024,600,Bitmap.Config.ARGB_8888); root(a).draw(Canvas(b))
                val colors=IntArray(1024*600); b.getPixels(colors,0,1024,0,0,1024,600)
                assertTrue("Sin verde/azul dominante en rojo: $s",colors.all {Color.red(it)>=Color.green(it) && Color.red(it)>=Color.blue(it)})
                File(dir,"%02d-%s-rojo.png".format(27+i,s.replace(' ','-'))).outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)}; b.recycle()
            }
        } finally { c.pause().stop().destroy() }
    }
}
