package org.msrobotika.astrotab

import android.app.AlarmManager
import android.content.*
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
import java.io.File
import java.time.Duration
import java.util.concurrent.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[23],qualifiers="w1024dp-h600dp-land-mdpi")
class ProBScreensTest {
    private fun all(v: View): List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap {all(v.getChildAt(it))}else emptyList()
    private fun root(a: MainActivity)=a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
    private fun field(a: Any,name: String)=a.javaClass.getDeclaredField(name).apply {isAccessible=true}.get(a)
    private fun invoke(a: Any,name: String)=a.javaClass.getDeclaredMethod(name).apply {isAccessible=true}.invoke(a)
    private fun settle(a: MainActivity){val worker=field(a,"worker") as ExecutorService;repeat(8){worker.submit {}.get(60,TimeUnit.SECONDS);(field(a,"satelliteWorker") as ExecutorService).submit {}.get(60,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle()}}
    private fun layout(v: View){v.measure(View.MeasureSpec.makeMeasureSpec(1024,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY));v.layout(0,0,1024,600)}
    private fun go(a: MainActivity,name: String){a.javaClass.getDeclaredMethod("show",String::class.java).apply {isAccessible=true}.invoke(a,name);settle(a);layout(root(a))}
    private fun start(): org.robolectric.android.controller.ActivityController<MainActivity> {
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("astrotab",0).edit().clear().putString("latitude","40.4168").putString("longitude","-3.7038").putString("zoneId","Europe/Madrid").putString("sensor","false").commit()
        // Fixture identificada: TLE real descargado para inspección de formato, no caché distribuida.
        val raw=File("../core/src/test/resources/sgp4/iss.tle").readText()
        FileCache(File(app.filesDir,"satellites")).write("satellites",Cached(System.currentTimeMillis(),raw))
        SessionStore(FileCache(File(app.filesDir,"session-plans"))).save(ObservingSession("test","Noche de prueba",System.currentTimeMillis(),listOf(TargetRequest("M31"),TargetRequest("M45"),TargetRequest("M42"))))
        return Robolectric.buildActivity(MainActivity::class.java).setup()
    }
    @Test fun objectsReuseSameCacheAndArraysAcrossTabsAndHome() {
        val c=start();try{val a=c.get();settle(a);go(a,"Objetos")
            val cached=field(a,"photoCache");val plans=field(cached!!,"plans") as List<*>
            assertEquals(110,plans.size)
            go(a,"Inicio");go(a,"Objetos");assertSame(cached,field(a,"photoCache"));assertSame(plans,field(field(a,"photoCache")!!,"plans"))
            assertFalse(all(root(a)).filterIsInstance<TextView>().any {it.visibility==View.VISIBLE && it.text.contains("Calculando")})
            assertTrue(all(root(a)).filterIsInstance<MiniAltitude>().isNotEmpty())
        }finally{c.pause().stop().destroy()}
    }
    @Test fun api23SchedulesExactReceiverAndOnlyInternetPermission() {
        val c=start();try{val a=c.get();settle(a)
            val now=System.currentTimeMillis();val store=AlarmStore(alarmPreferences(a));val alarm=store.add(AlarmKind.MOONRISE,"Salida de la Luna de prueba",now+60000)
            assertTrue(AndroidAlarms.schedule(a,alarm))
            val manager=a.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val scheduled=shadowOf(manager).scheduledAlarms.single {it.triggerAtTime==alarm.time}
            assertEquals(AlarmManager.RTC_WAKEUP,scheduled.type)
            val saved=shadowOf(scheduled.operation).savedIntent
            assertEquals(AlarmReceiver::class.java.name,saved.component!!.className)
            AndroidAlarms.cancel(a,alarm.id);assertFalse(shadowOf(manager).scheduledAlarms.any {it.triggerAtTime==alarm.time})
            store.remove(alarm.id)
            val due=store.add(AlarmKind.MOONRISE,"Aviso de prueba",now-1000)
            AlarmReceiver().onReceive(a,Intent(a,AlarmReceiver::class.java).putExtra("alarmId",due.id))
            assertEquals(MainActivity::class.java.name,shadowOf(a).nextStartedActivity.component!!.className)
            invoke(a,"checkAlarms");assertTrue(org.robolectric.shadows.ShadowDialog.getLatestDialog().isShowing)
            assertTrue(store.read().isEmpty())
            @Suppress("DEPRECATION")
            val permissions=a.packageManager.getPackageInfo(a.packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions!!.toList()
            assertEquals(listOf("android.permission.INTERNET"),permissions)
        }finally{c.pause().stop().destroy()}
    }
    @Test fun plannedTimerAutomaticallyWritesCompletedObjectToLog() {
        val c=start();try{val a=c.get();settle(a)
            val block=SessionBlock("M31",0,100,1,0.1,0.1,emptyList())
            a.javaClass.getDeclaredMethod("startPlannedBlock",SessionBlock::class.java).apply {isAccessible=true}.invoke(a,block)
            org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofSeconds(1));invoke(a,"updateSession");settle(a)
            val entries=LogBook(File(a.filesDir,"bitacora.json")).read();assertTrue(entries.any {it.target=="M31" && it.frames==1 && it.exposure==0.1})
            val size=entries.size;invoke(a,"updateSession");settle(a);assertEquals(size,LogBook(File(a.filesDir,"bitacora.json")).read().size)
        }finally{c.pause().stop().destroy()}
    }
    @Test @Config(sdk=[28],qualifiers="w1024dp-h600dp-land-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun nativeProBScreensAndRedAt1024x600() {
        val c=start();try{val a=c.get();settle(a);val dir=File(System.getProperty("astrotab.screens")!!)
            val state=field(a,"proBState") as ProBState
            val iss=TleParser.parse(File("../core/src/test/resources/sgp4/iss.tle").readText()).single()
            state.pass=SatellitePassPolicy.select(SatellitePredictor.predict(listOf(iss),iss.epoch,Location(40.4,-3.7,"manual",0)),iss.epoch).first()
            for(red in listOf(false,true)) {
                (field(a,"settings") as Settings).red=red;invoke(a,"buildShell");settle(a)
                for((index,s) in listOf("Más","Satélites","Paso","Eventos","Plan de sesión","Equipos","Alarmas","Inicio","Cielo").withIndex()) {
                    go(a,s);val r=root(a)
                    assertEquals(1024,r.width);assertEquals(600,r.height)
                    for(b in all(r).filterIsInstance<Button>().filter {it.visibility==View.VISIBLE}){assertTrue("$s botón ${b.text} ${b.width}×${b.height}",b.width>=56 && b.height>=56)}
                    val bitmap=Bitmap.createBitmap(1024,600,Bitmap.Config.ARGB_8888);r.draw(Canvas(bitmap))
                    if(red){val pixels=IntArray(1024*600);bitmap.getPixels(pixels,0,1024,0,0,1024,600);assertTrue("Paleta roja $s",pixels.all {Color.red(it)>=Color.green(it) && Color.red(it)>=Color.blue(it)})}
                    File(dir,"pro-b-%02d-%s%s.png".format(index,s.replace(' ','-'),if(red) "-rojo" else "")).outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
                }
                val alarm=AlarmStore(alarmPreferences(a)).add(AlarmKind.ISS,"Estación Espacial en 5 minutos · prueba",System.currentTimeMillis()-1000)
                invoke(a,"checkAlarms");val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();val v=dialog.window!!.decorView;layout(v)
                val bitmap=Bitmap.createBitmap(1024,600,Bitmap.Config.ARGB_8888);v.draw(Canvas(bitmap))
                File(dir,"pro-b-09-Aviso${if(red) "-rojo" else ""}.png").outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle();dialog.dismiss()
            }
        }finally{c.pause().stop().destroy()}
    }
}
