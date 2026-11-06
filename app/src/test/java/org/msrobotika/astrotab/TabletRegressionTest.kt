package org.msrobotika.astrotab

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Looper
import android.os.SystemClock
import android.view.*
import android.widget.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.msrobotika.astrotab.astro.TimeSpan
import org.msrobotika.astrotab.data.*
import org.msrobotika.astrotab.ui.*
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.ExecutorService

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[23],qualifiers="w1024dp-h600dp-land-mdpi")
class TabletRegressionTest {
    private fun views(v: View): List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun layout(v: View) {
        v.measure(View.MeasureSpec.makeMeasureSpec(1024,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY)); v.layout(0,0,1024,600)
    }
    private fun workers(a: MainActivity) {
        val worker=MainActivity::class.java.getDeclaredField("worker").apply { isAccessible=true }.get(a) as ExecutorService
        repeat(3) { worker.submit {}.get(30,java.util.concurrent.TimeUnit.SECONDS); shadowOf(Looper.getMainLooper()).idle() }
    }
    private fun click(root: View,label: String) { views(root).filterIsInstance<Button>().first { it.text.toString()==label }.performClick() }
    private fun seed()=RuntimeEnvironment.getApplication().getSharedPreferences("astrotab",0).apply {
        edit().clear().putString("latitude","40.4168").putString("longitude","-3.7038").putString("zoneId","Europe/Madrid").commit()
    }
    private fun pointer(root: View,action: Int,x: Float,y: Float,start: Long,time: Long) {
        val event=MotionEvent.obtain(start,time,action,x,y,0)
        try { root.dispatchTouchEvent(event) } finally { event.recycle() }
    }
    private fun origin(view: View)=IntArray(2).also { view.getLocationOnScreen(it) }
    private fun reveal(scroll: ScrollView,target: View) {
        val targetOrigin=origin(target); val scrollOrigin=origin(scroll)
        scroll.scrollTo(0,scroll.scrollY+targetOrigin[1]-scrollOrigin[1]-scroll.height+80)
    }
    private fun gesture(root: View,target: View,drag: Boolean,cancel: Boolean=false) {
        val pt=origin(target); val base=origin(root)
        val x=pt[0]-base[0]+target.width/2f; val y=pt[1]-base[1]+target.height/2f
        val start=SystemClock.uptimeMillis()
        pointer(root,MotionEvent.ACTION_DOWN,x,y,start,start)
        if(drag) for(i in 1..10) pointer(root,MotionEvent.ACTION_MOVE,x,y-30*i,start,start+30*i)
        pointer(root,if(cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP,x,y-if(drag) 300 else 0,start,start+300)
        shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun vertical300px300msOnBortleDoesNotClickButTapClicksOnce() {
        val prefs=seed(); val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val a=controller.get(); val root=a.window.decorView; workers(a); click(root,"Ajustes"); layout(root)
            repeat(3) {
                val scroll=views(root).filterIsInstance<ScrollView>().single()
                val target=views(root).filterIsInstance<Button>().first { it.text.toString().endsWith("· +") }
                reveal(scroll,target); val before=scroll.scrollY
                gesture(root,target,true)
                assertEquals("Scroll no debe modificar clase","5",prefs.getString("bortle","5"))
                assertTrue("El gesto debe desplazar Ajustes",scroll.scrollY>before)
            }
            var scroll=views(root).filterIsInstance<ScrollView>().single()
            var target=views(root).filterIsInstance<Button>().first { it.text.toString().endsWith("· +") }
            reveal(scroll,target); gesture(root,target,false,cancel=true)
            assertEquals("5",prefs.getString("bortle","5"))
            gesture(root,target,false); assertEquals("6",prefs.getString("bortle",null))
            layout(root)
            scroll=views(root).filterIsInstance<ScrollView>().single()
            target=views(root).filterIsInstance<Button>().first { it.text=="Zona horaria: Europe/Madrid · cambiar" }
            scroll.scrollTo(0,0); gesture(root,target,true)
            assertTrue(views(root).filterIsInstance<Button>().any { it.text=="Guardar coordenadas manuales" })
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun verticalDragOnChartScrollsAndHorizontalDragSelectsWithoutClick() {
        val context=RuntimeEnvironment.getApplication()
        val scroll=ScrollView(context); val column=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
        val data=PlotData("Prueba",listOf(0L,HOUR,2*HOUR),listOf(PlotSeries("Nubes",doubleArrayOf(10.0,30.0,20.0))),0.0,100.0,listOf("00","01","02"))
        var clicks=0; var reads=0
        val chart=SeriesChart(context,data,false) { reads++ }.apply { setOnClickListener { clicks++ } }
        column.addView(chart,LinearLayout.LayoutParams(-1,400)); column.addView(View(context),LinearLayout.LayoutParams(-1,1800))
        scroll.addView(column); layout(scroll)
        val start=SystemClock.uptimeMillis()
        pointer(scroll,0,500f,350f,start,start)
        for(i in 1..10) pointer(scroll,2,500f,350f-30*i,start,start+30*i)
        pointer(scroll,1,500f,50f,start,start+300)
        assertTrue("Scroll vertical capturado por gráfica",scroll.scrollY>0); assertEquals(0,clicks)
        // Un DOWN sobre el contenedor detiene el fling antes de probar el gesto independiente.
        pointer(scroll,0,500f,500f,start+320,start+320)
        pointer(scroll,3,500f,500f,start+320,start+330)
        scroll.scrollTo(0,0)
        pointer(scroll,0,100f,200f,start+400,start+400)
        for(i in 1..10) pointer(scroll,2,100f+i*50,200f,start+400,start+400+i*30)
        pointer(scroll,1,600f,200f,start+400,start+700)
        assertEquals(0,scroll.scrollY); assertTrue("Cursor horizontal sin actualizaciones: $reads",reads>2); assertEquals(0,clicks)
        val before=reads; pointer(scroll,0,400f,200f,start+800,start+800); pointer(scroll,3,400f,200f,start+800,start+810)
        assertEquals(0,clicks); assertEquals(before+1,reads)
    }
    @Test fun weatherEmptyStatesDrawMessageWithoutSeriesOrBands() {
        seed(); val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val a=controller.get(); val root=a.window.decorView; workers(a)
            val repo=MainActivity::class.java.getDeclaredField("repository").apply { isAccessible=true }.get(a) as NetworkRepository
            val preferences=RuntimeEnvironment.getApplication().getSharedPreferences("astrotab",0)
            val now=System.currentTimeMillis(); val place=Location(40.4168,-3.7038,"manual",now)
            val field=NetworkRepository::class.java.getDeclaredField("clouds").apply { isAccessible=true }
            for(scenario in listOf("disabled","missing","expired")) {
                preferences.edit().putString("network",(scenario!="disabled").toString()).commit()
                field.set(repo,if(scenario=="missing") null else CloudData(now-if(scenario=="expired") 4*HOUR else 0,place.key,listOf(Cloud(now,30.0))))
                click(root,"Tiempo"); workers(a); layout(root)
                val charts=views(root).filterIsInstance<SeriesChart>(); assertEquals(4,charts.size)
                for(chart in charts) {
                    assertFalse(chart.data.hasData); assertTrue(chart.contentDescription.contains("sin datos meteorológicos"))
                    chart.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(280,View.MeasureSpec.EXACTLY)); chart.layout(0,0,1000,280)
                    val texts=mutableListOf<String>(); var paths=0; var rectangles=0
                    val recording=object: Canvas() {
                        override fun drawText(text: String,x: Float,y: Float,paint: Paint) { texts.add(text) }
                        override fun drawPath(path: Path,paint: Paint) { paths++ }
                        override fun drawRect(left: Float,top: Float,right: Float,bottom: Float,paint: Paint) { rectangles++ }
                    }
                    chart.draw(recording)
                    assertTrue(texts.toString(),texts.contains("sin datos meteorológicos"))
                    assertEquals(0,paths); assertEquals("Solo el fondo, ninguna banda de oscuridad/Luna",1,rectangles)
                }
            }
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun failedNetworkCauseVisibleOnWeatherAuroraAndSettingsWithoutCache() {
        val prefs=seed(); val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val a=controller.get(); val root=a.window.decorView; workers(a)
            val repo=MainActivity::class.java.getDeclaredField("repository").apply { isAccessible=true }.get(a) as NetworkRepository
            NetworkRepository::class.java.getDeclaredField("transport").apply { isAccessible=true }.set(repo,Transport { throw java.net.SocketTimeoutException("latitude=40.4168&longitude=-3.7038") })
            repo.enabled=true; repo.refresh(Location(40.4168,-3.7038,"manual",0)); prefs.edit().putString("network","true").commit()
            for(screen in listOf("Tiempo","Auroras","Ajustes")) {
                if(screen=="Auroras")click(root,"Más")
                click(root,screen); workers(a); layout(root)
                assertTrue("Falta causa en $screen",views(root).filterIsInstance<TextView>().any { it.text.contains("tiempo agotado") })
            }
            val logs=org.robolectric.shadows.ShadowLog.getLogsForTag("AstroTabNetwork")
            assertEquals(3,logs.size)
            assertTrue(logs.all { it.type==android.util.Log.WARN && it.throwable!=null })
            assertTrue(logs.all { !it.throwable!!.stackTraceToString().contains("40.4168") })
        } finally { controller.pause().stop().destroy() }
    }
}
