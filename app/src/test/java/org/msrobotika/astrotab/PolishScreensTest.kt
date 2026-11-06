package org.msrobotika.astrotab

import android.app.AlarmManager
import android.content.Context
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
import org.msrobotika.astrotab.satellites.*
import org.msrobotika.astrotab.ui.*
import org.msrobotika.astrotab.ui.theme.*
import java.io.File
import java.util.concurrent.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[23],qualifiers="w1024dp-h600dp-land-mdpi")
class PolishScreensTest {
    private fun all(v: View): List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap {all(v.getChildAt(it))}else emptyList()
    private fun root(a: MainActivity)=a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
    private fun field(a: Any,name: String)=a.javaClass.getDeclaredField(name).apply {isAccessible=true}.get(a)
    private fun settle(a: MainActivity){repeat(6){for(name in listOf("worker","satelliteWorker"))(field(a,name) as ExecutorService).submit {}.get(60,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle()}}
    private fun layout(v: View){v.measure(View.MeasureSpec.makeMeasureSpec(1024,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY));v.layout(0,0,1024,600)}
    private fun go(a: MainActivity,name: String){a.javaClass.getDeclaredMethod("show",String::class.java).apply {isAccessible=true}.invoke(a,name);settle(a);layout(root(a))}
    private fun click(a: MainActivity,label: String){all(root(a)).filterIsInstance<Button>().first {it.text==label}.performClick();settle(a);layout(root(a))}
    private fun start(): org.robolectric.android.controller.ActivityController<MainActivity> {
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("astrotab",0).edit().clear().putString("latitude","40.4168").putString("longitude","-3.7038").putString("zoneId","Europe/Madrid").putString("sensor","false").commit()
        val raw=File("../core/src/test/resources/sgp4/iss.tle").readText()
        FileCache(File(app.filesDir,"satellites")).write("satellites",Cached(System.currentTimeMillis(),raw))
        val sessions=SessionStore(FileCache(File(app.filesDir,"session-plans")));sessions.read().forEach {sessions.remove(it.id)}
        return Robolectric.buildActivity(MainActivity::class.java).setup()
    }
    @Test fun alarmsSurviveActivityRestartDisabledStaysUnscheduledAndDeleteWorks() {
        val c=start();val a=c.get();settle(a)
        val store=AlarmStore(alarmPreferences(a));val now=System.currentTimeMillis()
        val active=store.add(AlarmKind.MOONRISE,"Luna de prueba",now+HOUR)
        val inactive=store.add(AlarmKind.ASTRONOMICAL_DUSK,"Crepúsculo de prueba",now+2*HOUR)
        store.setEnabled(inactive.id,false)
        go(a,"Alarmas")
        val switches=all(root(a)).filterIsInstance<Switch>();assertEquals(2,switches.size)
        assertEquals(setOf("Activa","Inactiva"),switches.map {it.text.toString()}.toSet())
        assertTrue(all(root(a)).filterIsInstance<TextView>().any {it.text.startsWith("Hoy ")})
        switches.first {it.isChecked}.performClick();settle(a)
        assertFalse(AlarmStore(alarmPreferences(a)).read().first {it.id==active.id}.enabled)
        c.pause().stop().destroy()
        val restarted=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val b=restarted.get();settle(b);go(b,"Alarmas")
            assertTrue(AlarmStore(alarmPreferences(b)).read().all {!it.enabled})
            val manager=b.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            assertFalse(shadowOf(manager).scheduledAlarms.any {it.triggerAtTime==active.time || it.triggerAtTime==inactive.time})
            all(root(b)).filterIsInstance<Switch>().first().performClick();settle(b)
            assertTrue(AlarmStore(alarmPreferences(b)).read().any {it.enabled})
            click(b,"Borrar alarma");assertEquals(1,AlarmStore(alarmPreferences(b)).read().size)
        }finally{restarted.pause().stop().destroy()}
    }
    @Test fun emptyPlanProposesThreeAndEquipmentCardActivatesAndSheetSaves() {
        val c=start();try {
            val a=c.get();settle(a);go(a,"Plan de sesión")
            assertTrue(all(root(a)).filterIsInstance<TextView>().any {it.text=="Tu próxima noche empieza aquí"})
            click(a,"Planificar esta noche")
            val sessions=field(a,"sessionStore") as SessionStore
            assertEquals(3,sessions.read().single().requests.size)
            assertTrue(all(root(a)).any {it is SessionTimeline})
            click(a,"Quitar");assertEquals(2,sessions.read().single().requests.size)
            val settings=field(a,"settings") as Settings
            settings.saveProfile(EquipmentProfile("test","Telescopio",Equipment(focal=800.0)))
            go(a,"Equipos");all(root(a)).first {it.contentDescription=="Activar equipo Telescopio"}.performClick();settle(a)
            assertEquals("test",settings.activeProfileId)
            val box=all(root(a)).first {it.contentDescription=="Activar equipo Telescopio"}
            all(box).filterIsInstance<Button>().first {it.text=="Editar"}.performClick()
            val d=org.robolectric.shadows.ShadowDialog.getLatestDialog()
            all(d.window!!.decorView).first {it.contentDescription=="Aumentar Focal"}.performClick()
            all(d.window!!.decorView).filterIsInstance<Button>().first {it.text=="Guardar"}.performClick();settle(a)
            assertEquals(805.0,Settings(alarmPreferences(a)).equipment.focal,0.0)
        }finally{c.pause().stop().destroy()}
    }
    @Test fun navigatingBackReusesOrbitalCacheAndFiltersWithoutPrediction() {
        val c=start();try {
            val a=c.get();settle(a);go(a,"Satélites")
            val cache=field(a,"satelliteCache") as SatellitePredictionCache
            val repo=field(a,"satelliteRepository") as SatelliteRepository;val settings=field(a,"settings") as Settings
            val original=cache.get(repo.tles,settings.location!!,System.currentTimeMillis());assertNotNull(original)
            click(a,"≥ 30°");go(a,"Inicio");go(a,"Satélites")
            assertSame(original,cache.get(repo.tles,settings.location!!,System.currentTimeMillis()))
            assertTrue(all(root(a)).filterIsInstance<TextView>().any {it.text=="✓ ≥ 30°"})
        }finally{c.pause().stop().destroy()}
    }
    @Test fun selectedPassOpensItsTrackAtVisibleMaximumInSky() {
        val c=start();try {
            val a=c.get();settle(a);go(a,"Satélites")
            all(root(a)).first {it.contentDescription?.startsWith("Abrir paso ")==true}.performClick();settle(a)
            val pass=(field(a,"proBState") as ProBState).pass!!
            click(a,"Ver en el cielo")
            val map=all(root(a)).filterIsInstance<SkyView>().single()
            assertEquals(pass.track,map.satelliteTrack)
            assertTrue(kotlin.math.abs(SatellitePassPolicy.visiblePeak(pass)!!.time-System.currentTimeMillis()-(field(a,"offset") as Long))<1000)
            assertTrue(map.satelliteLabel.contains("Estación Espacial"))
        }finally{c.pause().stop().destroy()}
    }
    @Test @Config(sdk=[28]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun navigationIconsHaveDifferentMappingsAndDifferentPixels() {
        val icons=listOf("Herramientas","Más","Ajustes","Equipos","Eventos","Alarmas").map(Icons::screen)
        assertEquals(6,icons.distinct().size)
        val rendered=icons.map {icon -> val bitmap=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888)
            Icons.draw(Canvas(bitmap),Paint(Paint.ANTI_ALIAS_FLAG),icon,0f,0f,48f,Color.WHITE)
            val pixels=IntArray(48*48);bitmap.getPixels(pixels,0,48,0,0,48,48);bitmap.recycle();pixels.toList()}
        assertEquals(6,rendered.distinct().size)
    }
    private fun shot(v: View,name: String) {
        layout(v);val bitmap=Bitmap.createBitmap(1024,600,Bitmap.Config.ARGB_8888);v.draw(Canvas(bitmap))
        if(name.endsWith("rojo")){val pixels=IntArray(1024*600);bitmap.getPixels(pixels,0,1024,0,0,1024,600);assertTrue(name,pixels.all {Color.red(it)>=Color.green(it) && Color.red(it)>=Color.blue(it)})}
        File(System.getProperty("astrotab.screens")!!,"pulido-$name.png").outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
    }
    @Test @Config(sdk=[28],qualifiers="w1024dp-h600dp-land-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun nativePolishEmptyAndPopulatedScreensAndEquipmentSheet() {
        val c=start();try {
            val a=c.get();settle(a);val settings=field(a,"settings") as Settings
            val raw=(listOf("iss.tle","visual.tle").flatMap {TleParser.parse(File("../core/src/test/resources/sgp4/$it").readText())}).distinctBy {it.number}
                .joinToString("\n") {"${it.name}\n${it.line1}\n${it.line2}"}
            FileCache(File(a.filesDir,"satellites")).write("satellites",Cached(System.currentTimeMillis(),raw))
            (field(a,"satelliteRepository") as SatelliteRepository).load()
            settings.equipment=settings.equipment.copy(tracking=true,equatorial=true)
            val alarms=AlarmStore(alarmPreferences(a));val at=System.currentTimeMillis()+HOUR
            alarms.add(AlarmKind.MOONRISE,"Salida de la Luna",at)
            val off=alarms.add(AlarmKind.ISS,"Estación Espacial en 5 minutos",at+HOUR);alarms.setEnabled(off.id,false)
            go(a,"Plan de sesión");shot(root(a),"01-plan-vacio")
            click(a,"Planificar esta noche");shot(root(a),"02-plan-propuesto")
            for((index,s) in listOf("Inicio","Satélites","Eventos","Equipos","Alarmas").withIndex()) {go(a,s);shot(root(a),"%02d-%s".format(index+3,s))}
            go(a,"Satélites");all(root(a)).first {it.contentDescription?.startsWith("Abrir paso ")==true}.performClick();settle(a);shot(root(a),"09-ficha-paso")
            click(a,"Ver en el cielo");shot(root(a),"09b-paso-en-cielo")
            go(a,"Equipos");click(a,"Editar")
            val d=org.robolectric.shadows.ShadowDialog.getLatestDialog();shot(d.window!!.decorView,"08-editar-equipo");d.dismiss()
            settings.red=true;a.javaClass.getDeclaredMethod("buildShell").apply {isAccessible=true}.invoke(a);settle(a)
            for((index,s) in listOf("Inicio","Satélites","Plan de sesión","Equipos","Alarmas","Eventos").withIndex()){go(a,s);shot(root(a),"%02d-%s-rojo".format(index+10,s))}
        }finally{c.pause().stop().destroy()}
    }
}
