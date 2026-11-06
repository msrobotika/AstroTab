package org.msrobotika.astrotab

import android.content.Intent
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
import org.msrobotika.astrotab.astro.*
import org.msrobotika.astrotab.data.*
import org.msrobotika.astrotab.ui.SessionTimeline
import org.msrobotika.astrotab.ui.theme.Palette
import java.io.File
import java.util.TimeZone
import java.util.concurrent.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[23],qualifiers="w1024dp-h600dp-land-mdpi")
class PlanFixesScreensTest {
    private fun all(v: View): List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap {all(v.getChildAt(it))}else emptyList()
    private fun views(a: MainActivity)=all(a.findViewById(android.R.id.content))
    private fun field(a: Any,name: String)=a.javaClass.getDeclaredField(name).apply {isAccessible=true}.get(a)
    private fun settle(a: MainActivity) {repeat(3){(field(a,"worker") as ExecutorService).submit {}.get(30,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle()}}
    private fun show(a: MainActivity,name: String) {a.javaClass.getDeclaredMethod("show",String::class.java).apply {isAccessible=true}.invoke(a,name);settle(a)}
    private fun entry(category: String)=Intent(Intent.ACTION_MAIN).addCategory(category)
    @Test fun homeAndLauncherReturnToInicioWhileOrdinaryResumeKeepsTab() {
        val c=Robolectric.buildActivity(MainActivity::class.java,entry(Intent.CATEGORY_LAUNCHER)).setup()
        try {
            val a=c.get();settle(a);assertEquals("Inicio",field(a,"screen"))
            show(a,"Herramientas");c.pause().stop().restart().start().resume();settle(a)
            assertEquals("Herramientas",field(a,"screen"))
            c.newIntent(Intent("org.msrobotika.astrotab.OTHER"));settle(a)
            assertEquals("Herramientas",field(a,"screen"))
            c.newIntent(entry(Intent.CATEGORY_HOME));settle(a)
            assertEquals("Inicio",field(a,"screen"))
            show(a,"Ajustes");c.newIntent(entry(Intent.CATEGORY_LAUNCHER));settle(a)
            assertEquals("Inicio",field(a,"screen"))
        } finally {c.pause().stop().destroy()}
    }
    @Test fun coldStartAndRecreationAlwaysShowInicio() {
        val c=Robolectric.buildActivity(MainActivity::class.java,entry(Intent.CATEGORY_HOME)).setup()
        try {settle(c.get());show(c.get(),"Herramientas");c.recreate();settle(c.get());assertEquals("Inicio",field(c.get(),"screen"))}
        finally {c.pause().stop().destroy()}
        val next=Robolectric.buildActivity(MainActivity::class.java,entry(Intent.CATEGORY_LAUNCHER)).setup()
        try {settle(next.get());assertEquals("Inicio",field(next.get(),"screen"))} finally {next.pause().stop().destroy()}
    }
    @Test fun perObjectExposureInfoGapPersistenceAndIntegrationControls() {
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("astrotab",0).edit().clear().putString("latitude","40.4168").putString("longitude","-3.7038").putString("zoneId","Europe/Madrid").commit()
        app.getSharedPreferences("session-plan",0).edit().clear().commit()
        val sessions=SessionStore(FileCache(File(app.filesDir,"session-plans")))
        sessions.read().forEach {sessions.remove(it.id)}
        val session=ObservingSession("regression","Prueba NPF",Utc.parse("2026-11-06T18:00Z"),listOf(TargetRequest("M31",120),TargetRequest("M42",120)))
        sessions.save(session)
        val c=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val a=c.get();settle(a);show(a,"Plan de sesión");settle(a)
            assertTrue(views(a).filterIsInstance<TextView>().any {it.text.contains(" s por toma")})
            views(a).first {it.contentDescription=="Información: M31"}.performClick()
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()
            assertTrue(all(dialog.window!!.decorView).filterIsInstance<TextView>().any {it.text.contains("limitada por NPF: sin seguimiento")})
            dialog.dismiss()
            views(a).filterIsInstance<Button>().first {it.text=="Margen + 5 min"}.performClick();settle(a)
            assertEquals(15,app.getSharedPreferences("session-plan",0).getInt("object-gap-minutes",-1))
            show(a,"Herramientas");show(a,"Plan de sesión")
            assertTrue(views(a).filterIsInstance<Button>().any {it.text=="Margen: 15 min"})
            views(a).filterIsInstance<Button>().first {it.text=="+ 5 min"}.performClick();settle(a)
            assertEquals(125,sessions.read().single().requests.first().integrationMinutes)
            views(a).filterIsInstance<Button>().first {it.text=="M31 · 125 min −"}.performClick();settle(a)
            assertEquals(120,sessions.read().single().requests.first().integrationMinutes)
            val bar=views(a).filterIsInstance<SessionTimeline>().single()
            assertTrue(bar.contentDescription.contains(" → "))
        } finally {c.pause().stop().destroy()}
    }
    @Test @Config(sdk=[28]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun timelinePixelsUseActualBlockStartAndEnd() {
        val start=Utc.parse("2026-11-06T19:00Z");val end=start+11*HOUR
        val night=NightTimeline(start-6*HOUR,end+7*HOUR,listOf(
            NightSample(start,-30.0,-20.0,Astro.Eq(0.0,0.0),0.0),NightSample(end,-30.0,-20.0,Astro.Eq(0.0,0.0),0.0)))
        val plan=ObservingPlan(listOf(SessionBlock("M1",start+HOUR,start+4*HOUR,10,30.0,300.0,emptyList()),
            SessionBlock("M2",start+5*HOUR,start+10*HOUR,10,30.0,300.0,emptyList())),emptyList())
        val view=SessionTimeline(RuntimeEnvironment.getApplication(),night,plan,false,TimeZone.getTimeZone("UTC"))
        view.measure(View.MeasureSpec.makeMeasureSpec(1024,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(120,View.MeasureSpec.EXACTLY));view.layout(0,0,1024,120)
        val bitmap=Bitmap.createBitmap(1024,120,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        fun pixel(hour: Double)=bitmap.getPixel((12+1000*hour/11).toInt(),60)
        val palette=Palette(false)
        assertEquals(palette.background,pixel(0.5));assertEquals(palette.series[0],pixel(1.1));assertEquals(palette.series[0],pixel(3.9))
        assertEquals(palette.background,pixel(4.5));assertEquals(palette.series[1],pixel(5.1));assertEquals(palette.series[1],pixel(9.9));assertEquals(palette.background,pixel(10.5))
        File(System.getProperty("astrotab.screens")!!,"timeline.png").apply { parentFile?.mkdirs() }.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
    }
}
