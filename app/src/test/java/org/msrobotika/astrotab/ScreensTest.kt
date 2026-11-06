package org.msrobotika.astrotab

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Matrix
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.ScrollView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.msrobotika.astrotab.ui.SkyView
import org.msrobotika.astrotab.ui.Orientation
import org.msrobotika.astrotab.catalog.Catalog
import org.msrobotika.astrotab.data.Location
import org.msrobotika.astrotab.data.Utc
import org.msrobotika.astrotab.data.HOUR

/** Android simulado en JVM: no acredita pantalla, sensores ni aprovisionamiento físicos. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[23],qualifiers="w1024dp-h600dp-land-mdpi")
class ScreensTest {
    /** El Paint legacy de Robolectric API 23 devuelve ascent/descent=0.
     * Métricas sintéticas explícitas para ejercitar colocación; no validan tipografía física. */
    private class MetricPaint : Paint(Paint.ANTI_ALIAS_FLAG) {
        override fun ascent()=-textSize*0.8f
        override fun descent()=textSize*0.2f
    }
    private class RecordingCanvas : Canvas() {
        val names=mutableListOf<String>()
        val circles=mutableListOf<Pair<Float,Float>>()
        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            @Suppress("DEPRECATION")
            val values=FloatArray(9).also { getMatrix().getValues(it) }
            assertEquals("Texto girado: $text",0f,values[Matrix.MSKEW_X],0.0001f)
            assertEquals("Texto girado: $text",0f,values[Matrix.MSKEW_Y],0.0001f)
            names.add(text)
        }
        override fun drawLine(x: Float, y: Float, nx: Float, ny: Float, paint: Paint) {
            assertTrue("Segmento demasiado largo",kotlin.math.hypot(nx-x,ny-y)<=210.001f)
            assertTrue(x in 0f..640f && nx in 0f..640f && y in 0f..420f && ny in 0f..420f)
        }
        override fun drawCircle(x: Float, y: Float, radius: Float, paint: Paint) { circles.add(x to y) }
    }
    private fun views(view: View): List<View> = listOf(view)+if(view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun click(root: View, label: String) {
        if(label in listOf("Luna","Auroras") && views(root).filterIsInstance<Button>().none {it.text.toString()==label})click(root,"Más")
        if(label=="Esta noche") { click(root,"Noche"); return }
        if(label=="Tu cielo") { click(root,"Noche"); click(root,"Tu cielo · Bortle"); return }
        val found=views(root).filterIsInstance<Button>().firstOrNull { it.text.toString()==label }
        assertNotNull("No existe botón $label",found); assertTrue(found!!.performClick())
    }
    private fun seedLocation() {
        RuntimeEnvironment.getApplication().getSharedPreferences("astrotab",0).edit()
            .putString("latitude","40.4168").putString("longitude","-3.7038").putString("locationDate","1000").commit()
    }
    private fun layout(root: View, height: Int=600) {
        root.measure(View.MeasureSpec.makeMeasureSpec(1024,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
        root.layout(0,0,1024,height)
    }
    private fun completeWorkers(activity: MainActivity) {
        val worker=MainActivity::class.java.getDeclaredField("worker").apply { isAccessible=true }.get(activity) as java.util.concurrent.ExecutorService
        repeat(3) { worker.submit {}.get(30,java.util.concurrent.TimeUnit.SECONDS); shadowOf(android.os.Looper.getMainLooper()).idle() }
    }
    @Test fun proNavigationTimeZoneChartsAndNoNetworkData() {
        seedLocation()
        RuntimeEnvironment.getApplication().getSharedPreferences("astrotab",0).edit().putString("red","true").commit()
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity=controller.get(); val root=activity.window.decorView; completeWorkers(activity)
            click(root,"Ajustes")
            views(root).filterIsInstance<Button>().first { it.text.startsWith("Zona horaria:") }.performClick()
            click(root,"Atlantic/Canary"); completeWorkers(activity)
            assertTrue(views(root).filterIsInstance<TextView>().any { it.text.contains("Atlantic/Canary") })
            click(root,"Tiempo"); completeWorkers(activity); layout(root)
            val charts=views(root).filterIsInstance<org.msrobotika.astrotab.ui.SeriesChart>()
            assertEquals(4,charts.size)
            assertTrue(charts.all { chart -> chart.data.series.all { s -> s.values.all { it.isNaN() } } })
            val event=android.view.MotionEvent.obtain(0,0,android.view.MotionEvent.ACTION_DOWN,400f,140f,0)
            try { assertFalse(charts.first().onTouchEvent(event)); assertTrue(charts.first().contentDescription.contains("sin datos meteorológicos")) } finally { event.recycle() }
            assertFalse(views(root).any { it is android.widget.EditText })
            assertEquals(7,views(root).filterIsInstance<org.msrobotika.astrotab.ui.theme.ScoreView>().count { it.score==null })
            click(root,"Noche"); completeWorkers(activity)
            assertTrue(views(root).any { it is org.msrobotika.astrotab.ui.SeriesChart })
            click(root,"Planetas y curvas"); completeWorkers(activity); layout(root)
            click(root,"Curva · Saturno"); completeWorkers(activity)
            views(root).filterIsInstance<Button>().first { it.contentDescription=="Información: Saturno" }.performClick()
            val planetInfo=org.robolectric.shadows.ShadowDialog.getLatestDialog()
            assertTrue(views(planetInfo.window!!.decorView).filterIsInstance<TextView>().any { it.text.contains("elongación") }); planetInfo.dismiss()
            click(root,"Luna"); completeWorkers(activity); layout(root)
            assertTrue(views(root).any { it is org.msrobotika.astrotab.ui.MoonCalendar })
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun proCalculatorsLogOwnKeyboardAndCsvDocumentIntent() {
        seedLocation(); val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity=controller.get(); val root=activity.window.decorView; completeWorkers(activity)
            click(root,"Herramientas"); click(root,"Más calculadoras"); layout(root)
            for(label in listOf("Hiperfocal y profundidad de campo","Timelapse","Circumpolares / trazos","Panorámica / mosaico","Apilado")) assertTrue(views(root).filterIsInstance<TextView>().any { it.text==label })
            click(root,"Timelapse"); layout(root)
            assertTrue(views(root).filterIsInstance<TextView>().any { it.text.contains("fotos") })
            assertTrue(views(root).filterIsInstance<Button>().all { it.height>=56 })
            click(root,"Herramientas"); click(root,"Bitácora"); completeWorkers(activity)
            click(root,"Objeto: —")
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog(); val keys=dialog.window!!.decorView
            assertFalse(views(keys).any { it is android.widget.EditText })
            for(key in listOf("M","3","1","Aceptar")) click(keys,key)
            click(root,"Guardar sesión"); completeWorkers(activity)
            val rows=org.msrobotika.astrotab.data.LogBook(java.io.File(activity.filesDir,"bitacora.json")).read()
            assertEquals("M31",rows.single().target)
            click(root,"Exportar bitácora a CSV")
            val started=shadowOf(activity).nextStartedActivityForResult
            assertEquals(android.content.Intent.ACTION_CREATE_DOCUMENT,started.intent.action)
            assertEquals("text/csv",started.intent.type); assertEquals(42,started.requestCode)
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun flashlightFullScreenRedBrightnessAndExit() {
        seedLocation(); val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity=controller.get(); val root=activity.window.decorView; completeWorkers(activity)
            click(root,"Herramientas"); click(root,"Linterna roja · tocar para salir")
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog(); val content=dialog.window!!.decorView
            val seek=views(content).filterIsInstance<android.widget.SeekBar>().single()
            seek.progress=5; assertEquals(0.05f,dialog.window!!.attributes.screenBrightness,0.001f)
            views(content).filterIsInstance<TextView>().first { it.text.startsWith("Linterna roja · toca") }.performClick()
            assertFalse(dialog.isShowing)
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun plannerCachesWholeNightAndPublishesPartialBatches() {
        seedLocation(); val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity=controller.get(); val root=activity.window.decorView; completeWorkers(activity)
            val offset=MainActivity::class.java.getDeclaredField("offset").apply { isAccessible=true }
            val fixed=Utc.parse("2026-11-06T20:00:00Z")
            offset.setLong(activity,fixed-System.currentTimeMillis())
            val counts=mutableListOf<Int>()
            val receive: (Any)->Unit = { value ->
                val plans=value.javaClass.getDeclaredField("plans").apply { isAccessible=true }.get(value) as List<*>
                counts.add(plans.size)
            }
            val method=MainActivity::class.java.declaredMethods.single { it.name=="photoData" }.apply { isAccessible=true }
            // Inicio ya precalcula la noche: vaciar explícitamente para probar publicación por lotes.
            MainActivity::class.java.getDeclaredField("photoCache").apply { isAccessible=true }.set(activity,null)
            method.invoke(activity,true,receive,receive); completeWorkers(activity)
            assertEquals(listOf(22,44,66,88,110),counts)
            val cache=MainActivity::class.java.getDeclaredField("photoCache").apply { isAccessible=true }
            val first=cache.get(activity)
            offset.setLong(activity,fixed+HOUR-System.currentTimeMillis())
            click(root,"Objetos"); completeWorkers(activity)
            assertSame(first,cache.get(activity))
            val settings=MainActivity::class.java.getDeclaredField("settings").apply { isAccessible=true }.get(activity) as org.msrobotika.astrotab.data.Settings
            settings.minimumAltitude=45.0; click(root,"Objetos"); completeWorkers(activity)
            assertNotSame(first,cache.get(activity))
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun asyncSkyMatchesSynchronousCoordinatesAndCachesMinute() {
        seedLocation(); val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity=controller.get(); completeWorkers(activity); click(activity.window.decorView,"Cielo"); completeWorkers(activity)
            val map=views(activity.window.decorView).filterIsInstance<SkyView>().single()
            val worker=MainActivity::class.java.getDeclaredField("worker").apply { isAccessible=true }.get(activity) as java.util.concurrent.ExecutorService
            val fixed=Utc.parse("2026-11-06T20:00:00Z")
            val reference=SkyView(activity,Catalog(activity.assets.open("catalog/stars.bin")),Orientation()) {}.apply { location=map.location }
            reference.recalculate(fixed); map.recalculateAsync(fixed,worker); completeWorkers(activity)
            val alt=SkyView::class.java.getDeclaredField("alt").apply { isAccessible=true }
            assertArrayEquals(alt.get(reference) as DoubleArray,alt.get(map) as DoubleArray,0.0)
            val request=SkyView::class.java.getDeclaredField("positionRequest").apply { isAccessible=true }
            val before=request.getInt(map); map.recalculateAsync(fixed+1000,worker); completeWorkers(activity)
            assertEquals(before,request.getInt(map))
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun populatedWeatherAndKpCanvasDrawCurvesInRedAndTouchValues() {
        seedLocation(); val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity=controller.get(); completeWorkers(activity)
            val prefs=mutableMapOf<String,String?>()
            val settings=org.msrobotika.astrotab.data.Settings(object: org.msrobotika.astrotab.data.PreferenceStore {
                override fun get(key: String)=prefs[key]
                override fun put(values: Map<String,String?>) { prefs.putAll(values) }
            }).apply { location=Location(40.4168,-3.7038,"manual",0); red=true; networkEnabled=true; zoneId="Europe/Madrid" }
            val fixed=System.currentTimeMillis(); val hourly=org.json.JSONObject()
            val times=org.json.JSONArray(); val f=java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm",java.util.Locale.ROOT).apply { timeZone=java.util.TimeZone.getTimeZone("UTC") }
            repeat(168) { times.put(f.format(java.util.Date(fixed/HOUR*HOUR+it*HOUR))) }; hourly.put("time",times)
            val fields=mapOf("cloud_cover" to 50,"cloud_cover_low" to 20,"cloud_cover_mid" to 30,"cloud_cover_high" to 40,"temperature_2m" to 8,"dew_point_2m" to 7,"wind_speed_10m" to 12,"wind_gusts_10m" to 22,"precipitation_probability" to 15,"relative_humidity_2m" to 90,"visibility" to 18000,"pressure_msl" to 1015,"wind_speed_250hPa" to 150)
            fields.forEach { (key,v) -> hourly.put(key,org.json.JSONArray().apply { repeat(168) { put(v) } }) }
            val raw=org.json.JSONObject().put("hourly",hourly).toString()
            val cache=object: org.msrobotika.astrotab.data.CacheStore {
                override fun read(key: String): org.msrobotika.astrotab.data.Cached?=null
                override fun write(key: String,value: org.msrobotika.astrotab.data.Cached) = Unit
            }
            val repo=org.msrobotika.astrotab.data.NetworkRepository(cache,org.msrobotika.astrotab.data.Transport { url ->
                if(url.contains("open-meteo")) raw else throw java.io.IOException("NOAA sin transporte real en prueba")
            }) { fixed }.apply { enabled=true; refresh(settings.location) }
            val worker=MainActivity::class.java.getDeclaredField("worker").apply { isAccessible=true }.get(activity) as java.util.concurrent.ExecutorService
            val screen=org.msrobotika.astrotab.ui.ProScreens(activity,settings,repo,worker,{true},{},{},fixed).render("Tiempo")
            activity.setContentView(screen); completeWorkers(activity)
            val charts=views(screen).filterIsInstance<org.msrobotika.astrotab.ui.SeriesChart>()
            assertEquals(4,charts.size); assertTrue(charts.all { it.data.series.all { s -> s.values.all(Double::isFinite) } })
            class RedCanvas: Canvas() {
                var nonemptyPaths=0
                override fun drawPath(path: android.graphics.Path,paint: Paint) {
                    if(!path.isEmpty) nonemptyPaths++
                    assertTrue(android.graphics.Color.red(paint.color)>=2*android.graphics.Color.green(paint.color))
                    assertTrue(android.graphics.Color.red(paint.color)>=2*android.graphics.Color.blue(paint.color))
                }
            }
            for(chart in charts) {
                chart.measure(View.MeasureSpec.makeMeasureSpec(1024,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(280,View.MeasureSpec.EXACTLY)); chart.layout(0,0,1024,280)
                val canvas=RedCanvas(); chart.draw(canvas); assertTrue(canvas.nonemptyPaths>0)
                val event=android.view.MotionEvent.obtain(0,0,android.view.MotionEvent.ACTION_DOWN,500f,140f,0)
                try { chart.onTouchEvent(event); assertTrue(chart.contentDescription.contains("Nubes 50")); assertTrue(chart.contentDescription.contains("250 hPa")) } finally { event.recycle() }
            }
            val samples=(0..47).map { org.msrobotika.astrotab.data.KpSample(fixed-72*HOUR+it*3*HOUR,(it%9).toDouble(),if(it<24) org.msrobotika.astrotab.data.KpKind.OBSERVED else org.msrobotika.astrotab.data.KpKind.PREDICTED) }
            val kp=org.msrobotika.astrotab.ui.SeriesChart(activity,org.msrobotika.astrotab.ui.PlotData.kp(samples,7.0,settings.zone),true)
            kp.measure(View.MeasureSpec.makeMeasureSpec(1024,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY)); kp.layout(0,0,1024,300)
            val canvas=RedCanvas(); kp.draw(canvas); assertEquals(2,canvas.nonemptyPaths)
            assertEquals(7.0,kp.data.threshold!!,0.0); assertTrue(kp.data.series.last().dashed)
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun messierSearchUsesOwnKeyboardAndDetailHasPrecomputedChart() {
        seedLocation()
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity=controller.get(); val root=activity.window.decorView
            completeWorkers(activity); click(root,"Objetos"); completeWorkers(activity)
            click(root,"Buscar: número o nombre")
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog(); val keys=dialog.window!!.decorView
            assertFalse(views(keys).any { it is android.widget.EditText })
            for(key in listOf("M","3","1","Buscar")) click(keys,key)
            completeWorkers(activity); layout(root)
            val m31=views(root).filterIsInstance<Button>().first { it.text.startsWith("M31 ·") }
            m31.performClick(); completeWorkers(activity); layout(root)
            assertTrue(views(root).any { it is org.msrobotika.astrotab.ui.NightChart })
            views(root).filterIsInstance<Button>().first { it.contentDescription=="Información: Catálogo offline · d3-celestial" }.performClick()
            val detail=org.robolectric.shadows.ShadowDialog.getLatestDialog()
            assertTrue(views(detail.window!!.decorView).filterIsInstance<TextView>().any { it.text.contains("Constelación:") }); detail.dismiss()
            assertTrue(views(root).filterIsInstance<TextView>().any { it.text.contains("Culminación superior") })
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun toolsAndSessionScrollInRedWithLargeControlsAndNoNewPermission() {
        seedLocation()
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity=controller.get(); var root=activity.window.decorView
            completeWorkers(activity); click(root,"Modo rojo"); root=activity.window.decorView
            click(root,"Herramientas"); completeWorkers(activity)
            for(height in listOf(600,480)) {
                layout(root,height)
                assertTrue(views(root).filterIsInstance<Button>().all { it.height>=56 })
                val scroll=views(root).filterIsInstance<ScrollView>().single()
                assertTrue(scroll.getChildAt(0).height>=scroll.height)
                assertTrue(views(root).filterIsInstance<Button>().any { it.text=="Reloj polar" })
                assertTrue(views(root).filterIsInstance<Button>().any { it.text=="Horario" })
            }
            click(root,"Reloj polar"); completeWorkers(activity); assertTrue(views(root).any {it is org.msrobotika.astrotab.ui.PolarClock})
            click(root,"Herramientas"); click(root,"Horario"); completeWorkers(activity); assertTrue(views(root).any {it is org.msrobotika.astrotab.ui.NightBar})
            click(root,"Herramientas")
            click(root,"Planificar sesión de tomas"); layout(root)
            assertTrue(views(root).filterIsInstance<Button>().all { it.height>=56 })
            views(root).filterIsInstance<Button>().first { it.text=="+" }.performClick()
            assertTrue(views(root).filterIsInstance<Button>().any { it.text=="Número de luces: 31" })
            click(root,"Iniciar"); layout(root)
            assertTrue(activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON!=0)
            assertTrue(views(root).filterIsInstance<TextView>().any { it.text.contains("TOMA") })
            click(root,"Parar")
            assertTrue(views(root).filterIsInstance<TextView>().any { it.text=="Sesión finalizada" })
            val info=activity.packageManager.getPackageInfo(activity.packageName,android.content.pm.PackageManager.GET_PERMISSIONS)
            assertEquals(listOf("android.permission.INTERNET"),info.requestedPermissions.orEmpty().toList())
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun messierSkyMarkersCanBeSelected() {
        val context=RuntimeEnvironment.getApplication()
        val c=Catalog(context.assets.open("catalog/stars.bin"))
        val m=org.msrobotika.astrotab.catalog.MessierCatalog(context.assets.open("catalog/messier.tsv")).objects.first { it.id=="M31" }
        val place=Location(40.4,-3.7,"manual",0); val now=Utc.parse("2026-11-06T22:00:00Z")
        val h=org.msrobotika.astrotab.astro.AstroPhoto.horizon(org.msrobotika.astrotab.astro.AstroPhoto.precess(m.eq,now),now,place)
        var selected: String?=null
        val orientation=Orientation().apply { reference(h.az); tilt(h.alt-elevation) }
        val map=SkyView(context,c,orientation) { selected=it.messierId }.apply { location=place; messierObjects=listOf(m) }
        android.widget.FrameLayout(context).addView(map); map.layout(0,0,640,420); map.recalculate(now)
        map.draw(Canvas(Bitmap.createBitmap(640,420,Bitmap.Config.ARGB_8888)))
        fun array(name: String)=SkyView::class.java.getDeclaredField(name).apply { isAccessible=true }.get(map) as FloatArray
        val x=array("messierX")[0]; val y=array("messierY")[0]
        assertFalse(x.isNaN()); assertFalse(y.isNaN())
        val down=android.view.MotionEvent.obtain(0,0,0,x,y,0); val up=android.view.MotionEvent.obtain(0,10,1,x,y,0)
        try { map.onTouchEvent(down); map.onTouchEvent(up); assertEquals("M31",selected) } finally { down.recycle(); up.recycle() }
    }
    @Test fun observerCoordinatesAppearOnlyInSettingsFields() {
        RuntimeEnvironment.getApplication().getSharedPreferences("astrotab",0).edit()
            .putString("latitude","40.4168").putString("longitude","-3.7038").putString("origin","manual").commit()
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val root=controller.get().window.decorView
            for(label in listOf("Cielo","Luna","Esta noche","Auroras","Tu cielo","Objetos","Herramientas","Ajustes")) {
                click(root,label)
                val texts=views(root).filterIsInstance<TextView>()
                assertTrue("Falta estado privado en $label",texts.any { it.text.contains("ubicación manual") })
                val coordinates=texts.filter { it.text.contains("40,4168") || it.text.contains("-3,7038") }
                if(label=="Ajustes") assertEquals(2,coordinates.size) else assertTrue("Coordenadas visibles en $label",coordinates.isEmpty())
            }
        } finally { controller.pause().stop().destroy() }
    }
    @Suppress("DEPRECATION")
    @Test fun immersiveLayoutReturnsOnFocusAndResumeWithoutDeviceOwner() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity=controller.get(); val decor=activity.window.decorView
            val mask=View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            assertEquals(mask,decor.systemUiVisibility and mask)
            decor.systemUiVisibility=0
            activity.onWindowFocusChanged(false)
            assertEquals(0,decor.systemUiVisibility)
            activity.onWindowFocusChanged(true)
            assertEquals(mask,decor.systemUiVisibility and mask)
            controller.pause(); decor.systemUiVisibility=0; controller.resume()
            assertEquals(mask,decor.systemUiVisibility and mask)
            click(decor,"Ajustes"); click(decor,"Acerca de · licencias y límites")
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()
            assertEquals(mask,dialog.window!!.decorView.systemUiVisibility and mask)
            dialog.dismiss(); activity.onWindowFocusChanged(true)
            assertEquals(mask,decor.systemUiVisibility and mask)
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun settingsScrollsToLastButtonWithoutCompressingControls() {
        seedLocation()
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val root=controller.get().window.decorView
            click(root,"Ajustes")
            for(height in listOf(600,480)) {
                layout(root,height)
                val scroll=views(root).filterIsInstance<ScrollView>().single()
                val content=scroll.getChildAt(0)
                assertTrue("El contenido debe superar el área útil",content.height>scroll.height)
                assertTrue(views(content).filterIsInstance<Button>().all { it.height>=56 })
                scroll.scrollTo(0,content.height)
                val last=views(content).filterIsInstance<Button>().last()
                assertTrue("Último botón recortado",last.bottom-scroll.scrollY<=scroll.height)
                val network=views(content).filterIsInstance<Button>().first { it.text.startsWith("Red ") }
                scroll.scrollTo(0,network.top)
                assertTrue(network.top-scroll.scrollY>=0)
                assertTrue(network.bottom-scroll.scrollY<=scroll.height)
                assertTrue(views(content).filterIsInstance<Button>().any { it.text=="Bortle −" && it.height>=56 })
            }
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun skyDrawsHorizontalNamesShortSegmentsAndSelectsRotatedObjects() {
        val context=RuntimeEnvironment.getApplication()
        val catalog=Catalog(context.assets.open("catalog/stars.bin"))
        val orientation=Orientation().apply { acceleration(5f,5f,5f,0) }
        var selected=false
        val map=SkyView(context,catalog,orientation) { selected=true }.apply {
            location=Location(40.4168,-3.7038,"manual",0); useSensor=true
        }
        SkyView::class.java.getDeclaredField("paint").apply { isAccessible=true }.set(map,MetricPaint())
        android.widget.FrameLayout(context).addView(map)
        map.measure(View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(420,View.MeasureSpec.EXACTLY))
        map.layout(0,0,640,420)
        map.recalculate(Utc.parse("2026-11-06T21:00:00Z"))
        var namesDrawn=0
        val drawnNames=mutableListOf<String>()
        for(bearing in listOf(0.0,90.0,180.0,270.0)) {
            orientation.reference(bearing)
            val canvas=RecordingCanvas(); map.draw(canvas)
            drawnNames.addAll(canvas.names)
            val magnitudes=canvas.names.mapNotNull { name ->
                (0 until catalog.count).firstOrNull { catalog.nameIndex[it]!=255 && catalog.name(it)==name }?.let { catalog.magnitude[it] }
            }
            namesDrawn+=magnitudes.size
            assertTrue("Nombres sin prioridad de brillo",magnitudes.zipWithNext().all { (a,b) -> a<=b })
            assertTrue(canvas.circles.isNotEmpty())
            val point=canvas.circles.first()
            val down=android.view.MotionEvent.obtain(0,0,android.view.MotionEvent.ACTION_DOWN,point.first,point.second,0)
            val up=android.view.MotionEvent.obtain(0,10,android.view.MotionEvent.ACTION_UP,point.first,point.second,0)
            selected=false
            try { map.onTouchEvent(down); map.onTouchEvent(up); assertTrue("Selección tras roll",selected) }
            finally { down.recycle(); up.recycle() }
        }
        assertTrue("No se ejercitaron nombres de estrellas: $drawnNames",namesDrawn>2)
    }
    @Test fun firstStartHasNoInventedLocationAndNoOwnerLock() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val root=controller.get().window.decorView
            layout(root)
            assertTrue(views(root).filterIsInstance<TextView>().any { it.text.contains("Ubicación sin configurar") })
            assertTrue(views(root).filterIsInstance<SkyView>().isEmpty())
            click(root,"Ajustes")
            assertTrue(views(root).filterIsInstance<Button>().any { it.text.contains("Red desactivada") })
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun allMvpScreensInflateOnApi23AndSkyDraws() {
        seedLocation()
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val root=controller.get().window.decorView
            for(label in listOf("Cielo","Luna","Esta noche","Auroras","Tu cielo","Objetos","Herramientas","Ajustes")) {
                click(root,label); layout(root)
                root.draw(Canvas(Bitmap.createBitmap(1024,600,Bitmap.Config.ARGB_8888)))
                val buttons=views(root).filterIsInstance<Button>()
                assertTrue(buttons.isNotEmpty())
                assertTrue("Objetivo táctil menor que 56 dp en $label",buttons.all { it.height>=56 })
            }
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun redModeDimsWindowAndNumericDialogUsesOwnKeys() {
        seedLocation()
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            var root=controller.get().window.decorView
            click(root,"Modo rojo"); root=controller.get().window.decorView
            assertEquals(0.03f,controller.get().window.attributes.screenBrightness,0.0001f)
            click(root,"Ajustes")
            val coordinate=views(root).filterIsInstance<Button>().first { it.text.startsWith("Latitud:") }
            coordinate.performClick()
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()
            assertNotNull(dialog)
            assertTrue(views(dialog.window!!.decorView).filterIsInstance<Button>().any { it.text=="," })
            assertFalse(views(dialog.window!!.decorView).any { it is android.widget.EditText })
            dialog.dismiss()
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun kioskPolicyCallsOnSimulatedDeviceOwnerAndNormalApp() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity=controller.get()
            val manager=activity.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val admin=android.content.ComponentName(activity,org.msrobotika.astrotab.kiosk.AstroAdminReceiver::class.java)
            val kiosk=org.msrobotika.astrotab.kiosk.KioskController(activity)
            assertFalse(kiosk.owner)
            kiosk.enter(true,true)
            assertFalse(manager.isLockTaskPermitted(activity.packageName))
            assertFalse(kiosk.unlock(false))
            shadowOf(manager).setDeviceOwner(admin)
            assertTrue(kiosk.owner)
            kiosk.enter(true,false)
            assertFalse(manager.isLockTaskPermitted(activity.packageName))
            kiosk.enter(true,true)
            assertTrue(manager.isLockTaskPermitted(activity.packageName))
            assertTrue(kiosk.unlock(true))
            kiosk.disable()
            assertFalse(manager.isLockTaskPermitted(activity.packageName))
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun accelerometerIsPausedOutsideSkyAndOnPause() {
        seedLocation()
        val manager=RuntimeEnvironment.getApplication().getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val sensor=org.robolectric.shadows.ShadowSensor.newInstance(android.hardware.Sensor.TYPE_ACCELEROMETER)
        shadowOf(manager).addSensor(sensor)
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity=controller.get(); val root=activity.window.decorView
            assertFalse(shadowOf(manager).hasListener(activity)) // Arranca en Inicio, sin sensor.
            click(root,"Cielo"); assertTrue(shadowOf(manager).hasListener(activity))
            click(root,"Luna"); assertFalse(shadowOf(manager).hasListener(activity))
            click(root,"Cielo"); assertTrue(shadowOf(manager).hasListener(activity))
            controller.pause(); assertFalse(shadowOf(manager).hasListener(activity))
        } finally { controller.stop().destroy() }
    }
}

