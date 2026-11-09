package org.msrobotika.astrotab

import android.app.Activity
import android.app.Dialog
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.hardware.*
import android.os.*
import android.provider.Settings as AndroidSettings
import android.view.*
import android.widget.*
import org.msrobotika.astrotab.astro.*
import org.msrobotika.astrotab.catalog.Catalog
import org.msrobotika.astrotab.catalog.MessierCatalog
import org.msrobotika.astrotab.catalog.MessierObject
import org.msrobotika.astrotab.data.*
import org.msrobotika.astrotab.kiosk.*
import org.msrobotika.astrotab.ui.*
import org.msrobotika.astrotab.ui.theme.*
import org.msrobotika.astrotab.satellites.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.*

class MainActivity : Activity(), SensorEventListener {
    private lateinit var settings: Settings
    private lateinit var store: PreferenceStore
    private lateinit var pin: PinGate
    private lateinit var kiosk: KioskController
    private lateinit var repository: NetworkRepository
    private lateinit var satelliteRepository: SatelliteRepository
    private lateinit var sessionStore: SessionStore
    private val proBState=ProBState()
    private val satelliteCache=SatellitePredictionCache()
    private var satelliteInFlight=""
    private var satelliteDone=0
    private var satellitePartial=emptyList<SatellitePass>()
    private val satelliteListeners=mutableListOf<(Int,Int,List<SatellitePass>)->Unit>()
    private var plannedTarget: SessionBlock?=null
    private var plannedEquipment=""
    private var sessionCancelled=false
    private var pendingLog: LogEntry?=null
    private var alarmDialog: Dialog?=null
    private lateinit var catalog: Catalog
    private lateinit var messier: MessierCatalog
    private var query=""
    private var objectGroup="Todos"
    private var messierId="M31"
    private var choosingFrame=false
    private var targetDeclination=0.0
    private data class PhotoData(val key: String, val night: NightTimeline, val plans: List<ObjectPlan>, val events: List<AstroPhoto.Event>, val galactic: AstroPhoto.Galactic)
    private var photoCache: PhotoData?=null // Acceso solo desde worker.
    private var monthKey=""
    private var monthCache=emptyList<Pair<Int,Double>>()
    private var sessionPlan=SessionPlan()
    private var sessionTimer: SessionTimer?=null
    private var sessionText: TextView?=null
    private var soundEnabled=false
    private var tone: android.media.ToneGenerator?=null
    private var previousSessionState: SessionState?=null
    private val sessionTick=object: Runnable {
        override fun run() {
            if(!resumed) return
            updateSession()
            val delay=sessionTimer?.state()?.takeUnless { it.done }?.remainingMillis?.coerceIn(10L,200L) ?: 200L
            handler.postDelayed(this,delay)
        }
    }
    private lateinit var root: LinearLayout
    private lateinit var body: FrameLayout
    private lateinit var headerText: TextView
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor?=null
    private var sensorRegistered=false
    private var maintenanceDialog: Dialog?=null
    private var resumed=false
    private var screen="Inicio"
    private var home: HomePanel?=null
    private var guideText: TextView?=null
    private var showCameraField=false
    private val tabs=mutableMapOf<String,Button>()
    private var sky: SkyView?=null
    private val orientation=Orientation()
    private var offset=0L
    private var selected: Selection?=null
    @Volatile private var generation=0
    private var minuteStamp=0L
    private var requestedScroll=0
    private val handler=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val networkWorker=Executors.newSingleThreadExecutor()
    private val satelliteWorker=Executors.newSingleThreadExecutor()
    private val zone get()=settings.zone
    private val palette get()=Palette(settings.red)
    private val background get()=palette.background
    private val panel get()=palette.surface
    private val foreground get()=palette.text
    private val accent get()=palette.accent
    private val muted get()=palette.muted
    private fun dp(value: Int)=(value*resources.displayMetrics.density).roundToInt()
    private fun time()=System.currentTimeMillis()+offset
    private fun number(value: Double, places: Int=1)=DisplayFormat.number(value,digits=places)
    private fun date(value: Long, pattern: String="dd/MM HH:mm z")=SimpleDateFormat(pattern,Locale.forLanguageTag("es-ES")).apply { timeZone=zone }.format(Date(value))
    private val tick=object: Runnable {
        override fun run() {
            if(!resumed) return
            updateAutoRed(); updateHeader(); home?.tick(System.currentTimeMillis()); sky?.recalculateAsync(time(),worker); updateGuide(); checkAlarms()
            val currentMinute=System.currentTimeMillis()/MINUTE
            if(currentMinute!=minuteStamp) {
                minuteStamp=currentMinute
                if(screen in listOf("Inicio","Luna","Esta noche","Tiempo","Auroras","Tu cielo","Ficha","Objetos","Objeto","Herramientas","Planetas","Planeta")) show(screen)
            }
            handler.postDelayed(this,1000)
        }
    }
    private val networkTick=object: Runnable {
        override fun run() {
            if(!resumed) return
            if(settings.networkEnabled) {refreshNetwork(); autoRefreshSatellites()}
            handler.postDelayed(this,30*MINUTE)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences=getSharedPreferences("astrotab",MODE_PRIVATE)
        store=object: PreferenceStore {
            override fun get(key: String)=preferences.getString(key,null)
            override fun put(values: Map<String,String?>) {
                val edit=preferences.edit(); values.forEach { (k,v) -> if(v==null) edit.remove(k) else edit.putString(k,v) }; edit.apply()
            }
        }
        settings=Settings(store); pin=PinGate(store); kiosk=KioskController(this)
        val roots=listOf(R.raw.isrg_root_x2,R.raw.amazon_root_ca1,R.raw.gts_root_r4,R.raw.usertrust_rsa).map { resources.openRawResource(it) }
        val tls=try { SecureTls.socketFactory(SecureTls.roots(roots)) } finally { roots.forEach { it.close() } }
        repository=NetworkRepository(FileCache(java.io.File(filesDir,"network")),HttpsTransport(tls),
            onWarning={ service,error -> android.util.Log.w("AstroTabNetwork",service,error) }).apply { enabled=settings.networkEnabled }
        satelliteRepository=SatelliteRepository(FileCache(java.io.File(filesDir,"satellites")),HttpsTransport(tls))
        sessionStore=SessionStore(FileCache(java.io.File(filesDir,"session-plans")))
        val savedPlan=getSharedPreferences("session-plan",MODE_PRIVATE)
        sessionPlan=SessionPlan(exposureSeconds=savedPlan.getFloat("exposure",30f).toDouble(),pauseSeconds=savedPlan.getFloat("pause",5f).toDouble())
        AlarmStore(store).read().filter {a -> a.enabled && a.time?.let {it>System.currentTimeMillis()}==true}.forEach {AndroidAlarms.schedule(this,it)}
        catalog=Catalog(assets.open("catalog/stars.bin"))
        messier=MessierCatalog(assets.open("catalog/messier.tsv"))
        (lastNonConfigurationInstance as? SessionTimer)?.let { sessionTimer=it; sessionPlan=it.plan }
        sensorManager=getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer=sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        offset=savedInstanceState?.getLong("offset") ?: 0
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        showOverLockScreen()
        buildShell()
        if(Build.VERSION.SDK_INT>=33) registerModernBack()
        worker.execute { repository.load(settings.location); satelliteRepository.load(); runOnUiThread { if(!isDestroyed) {show(screen);checkAlarms();autoRefreshSatellites()} } }
    }
    // The system lock screen stays portrait on Android 6; AstroTab covers it so the tablet wakes straight into landscape.
    // A swipe lock is removed; a PIN lock still guards everything outside AstroTab.
    @Suppress("DEPRECATION")
    private fun showOverLockScreen() {
        if(Build.VERSION.SDK_INT>=27) setShowWhenLocked(true)
        else window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
    }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.putLong("offset",offset) }
    @Suppress("DEPRECATION")
    private fun immersiveFlags() = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility=immersiveFlags()
        window.statusBarColor=background; window.navigationBarColor=background
        val attributes=window.attributes; attributes.screenBrightness=if(settings.red) 0.03f else -1f; window.attributes=attributes
    }
    private fun column()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun row()=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
    private fun shape(color: Int, border: Int=panel)=GradientDrawable().apply { setColor(color); cornerRadius=dp(Design.RADIUS).toFloat(); setStroke(dp(1),border) }
    private fun text(value: String, size: Float=18f, color: Int=foreground)=TextView(this).apply {
        text=value; textSize=size; setTextColor(color); setPadding(dp(8),dp(6),dp(8),dp(6)); setLineSpacing(dp(3).toFloat(),1f)
    }
    private fun title(value: String)=text(value,Design.TITLE).apply { setTypeface(typeface,Typeface.BOLD) }
    private fun button(label: String, action: ()->Unit)=Button(this).apply {
        text=label; textSize=16f; isAllCaps=false; minHeight=dp(56); minimumHeight=dp(56)
        setPadding(dp(10),dp(8),dp(10),dp(8)); setTextColor(this@MainActivity.foreground)
        background=StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed),shape(panel,accent))
            addState(intArrayOf(android.R.attr.state_focused),shape(panel,accent))
            addState(intArrayOf(),shape(panel))
        }
        layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(56)).apply { setMargins(dp(4),dp(4),dp(4),dp(4)) }
        Widgets(this@MainActivity,palette).styleValueButton(this,label)
        setOnClickListener { action() }
    }
    private fun LinearLayout.addButtons(vararg actions: Pair<String,()->Unit>) {
        val buttons=row()
        actions.forEach { (label,action) -> buttons.addView(button(label,action),LinearLayout.LayoutParams(0,dp(56),1f).apply { setMargins(dp(4),dp(4),dp(4),dp(4)) }) }
        addView(buttons)
    }
    private fun card(parent: LinearLayout, heading: String, message: String) {
        val box=column().apply { background=shape(panel); setPadding(dp(10),dp(8),dp(10),dp(8)) }
        val widgets=Widgets(this,palette)
        box.addView(widgets.section(heading,message)); box.addView(text(DisplayFormat.summary(message),16f))
        parent.addView(box,LinearLayout.LayoutParams(-1,-2).apply { setMargins(dp(4),dp(4),dp(4),dp(8)) })
    }
    private fun scroll(content: View)=ScrollView(this).apply {
        layoutParams=FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
        isFillViewport=true; clipToPadding=false
        addView(content,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    private fun buildShell() {
        immersive()
        root=column().apply { setBackgroundColor(this@MainActivity.background) }
        val header=row().apply { setPadding(dp(12),0,dp(12),0) }
        header.addView(text("AstroTab",24f,accent).apply { setTypeface(typeface,Typeface.BOLD) })
        headerText=text("",14f,muted)
        header.addView(headerText,LinearLayout.LayoutParams(0,-2,1f))
        header.addView(button(if(settings.red) "Rojo: sí" else "Modo rojo") { settings.autoRed=false; settings.red=!settings.red; buildShell() },LinearLayout.LayoutParams(dp(136),dp(56)).apply { setMargins(dp(4),0,dp(4),0) })
        root.addView(header,LinearLayout.LayoutParams(-1,dp(56)))
        body=FrameLayout(this).apply { setPadding(dp(12),dp(6),dp(12),dp(6)) }
        root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        val nav=row().apply { setPadding(dp(8),dp(4),dp(8),dp(4)) }
        tabs.clear()
        for(label in listOf("Inicio","Cielo","Objetos","Noche","Tiempo","Herramientas","Más","Ajustes")) {
            val tab=button(label) { show(if(label=="Noche") "Esta noche" else label) }.apply { textSize=12f; setPadding(dp(2),dp(4),dp(2),0); setCompoundDrawablesWithIntrinsicBounds(null,IconDrawable(Icons.screen(label),accent,dp(22)),null,null); compoundDrawablePadding=dp(3) }
            tabs[label]=tab
            nav.addView(tab,LinearLayout.LayoutParams(0,dp(56),1f).apply { setMargins(dp(2),0,dp(2),0) })
        }
        root.addView(nav,LinearLayout.LayoutParams(-1,dp(64)))
        setContentView(root); show(screen); updateHeader()
    }
    private fun updateHeader() {
        val locationLabel=LocationStatus.label(settings.location)
        headerText.text=getString(R.string.header_status,date(time(),"dd/MM/yyyy HH:mm z"),zone.id,locationLabel,if(offset!=0L) " · hora desplazada" else "")
    }
    private fun autoRefreshSatellites() {
        if(!settings.networkEnabled)return
        networkWorker.execute {
            val before=satelliteRepository.tles
            satelliteRepository.refreshIfNeeded({settings.networkEnabled},System.currentTimeMillis())
            if(before!=satelliteRepository.tles)runOnUiThread {if(!isDestroyed && screen in listOf("Inicio","Satélites"))show(screen)}
        }
    }
    private fun refreshSatellites() {
        if(!settings.networkEnabled) {message("Red desactivada","Activa la red opcional en Ajustes para consultar CelesTrak.");return}
        networkWorker.execute {satelliteRepository.refresh({settings.networkEnabled},System.currentTimeMillis())
            runOnUiThread {if(!isDestroyed && screen=="Satélites")show("Satélites")}}
    }
    private fun predictSatellites(progress: (Int,Int,List<SatellitePass>)->Unit) {
        val place=settings.location ?: run {progress(0,0,emptyList());return}
        val tles=satelliteRepository.tles.toList();val now=System.currentTimeMillis()
        satelliteCache.get(tles,place,now)?.let {progress(tles.size,tles.size,it);return}
        val key=place.key+tles.joinToString {it.line1+it.line2}
        if(satelliteInFlight==key){satelliteListeners.add(progress);progress(satelliteDone,tles.size,satellitePartial);return}
        satelliteInFlight=key;satelliteDone=0;satellitePartial=emptyList();satelliteListeners.clear();satelliteListeners.add(progress)
        fun publish(done: Int,passes: List<SatellitePass>) {
            if(isDestroyed || satelliteInFlight!=key)return
            satelliteDone=done;satellitePartial=passes
            val listeners=satelliteListeners.toList()
            if(done==tles.size){satelliteInFlight="";satelliteListeners.clear()}
            listeners.forEach {it(done,tles.size,passes)}
        }
        satelliteWorker.execute {
            val result=SatellitePredictor.predict(tles,now,place,cancelled={isDestroyed || Thread.currentThread().isInterrupted},partial={done,passes ->
                if(done<tles.size)runOnUiThread {publish(done,passes)} })
            if(!isDestroyed)satelliteCache.put(tles,place,now,result)
            runOnUiThread {publish(tles.size,result)}
        }
    }
    private fun addTargetToSession(id: String) {
        val all=sessionStore.read();val current=all.firstOrNull {it.id==proBState.sessionId} ?: all.firstOrNull()
            ?: ObservingSession(UUID.randomUUID().toString(),"Noche ${date(System.currentTimeMillis(),"dd/MM")}",System.currentTimeMillis(),emptyList())
        if(current.requests.none {it.id==id})sessionStore.save(current.copy(requests=current.requests+TargetRequest(id)))
        else sessionStore.save(current)
        proBState.sessionId=current.id;show("Plan de sesión")
    }
    private fun startPlannedBlock(block: SessionBlock) {
        if(sessionTimer?.state()?.done==false){message("Sesión en curso","Termina o para la sesión actual antes de iniciar otro objeto.");return}
        sessionPlan=SessionPlan(block.frames,block.exposure,sessionPlan.pauseSeconds)
        plannedTarget=block;plannedEquipment=settings.activeProfile.name;sessionCancelled=false
        sessionTimer=SessionTimer(sessionPlan,SystemClock::elapsedRealtime);previousSessionState=null;show("Sesión")
    }
    private fun addObjectAlarm(target: MessierObject) {
        val place=settings.location ?: return;val now=System.currentTimeMillis();val minimum=settings.minimumAltitude
        worker.execute {val at=AlarmPolicy.objectRise(now,target.eq,place,minimum)
            runOnUiThread {if(!isDestroyed) {
                if(at==null)message("Sin cruce ascendente","${target.id} no cruza ${DisplayFormat.number(minimum,"°")} hacia arriba en las próximas 48 h.")
                else {val alarm=AlarmStore(store).add(AlarmKind.OBJECT_ALTITUDE,"${target.id} supera ${DisplayFormat.number(minimum,"°")}",at,proBState.alarmSound,target.id);AndroidAlarms.schedule(this,alarm);message("Alarma guardada",date(at))}
            }}}
    }
    private fun savePendingLog() {
        val entry=pendingLog ?: return;pendingLog=null
        worker.execute {val result=runCatching {LogBook(java.io.File(filesDir,"bitacora.json")).append(entry)}
            runOnUiThread {if(!isDestroyed){if(result.isSuccess)Toast.makeText(this,"${entry.target} guardado en Bitácora",Toast.LENGTH_LONG).show()
                else {pendingLog=entry;message("Bitácora sin guardar","No se ha podido guardar. Conserva la app abierta y pulsa Reintentar en Sesión.")}}}}
    }
    private fun checkAlarms() {
        if(!::settings.isInitialized || alarmDialog?.isShowing==true)return
        val now=System.currentTimeMillis();val place=settings.location
        val kp=place?.let {settings.networkEnabled && (AlarmPolicy.kpReached(repository.observed,now,Aurora.threshold(it.latitude,it.longitude)) || AlarmPolicy.kpReached(repository.forecast,now,Aurora.threshold(it.latitude,it.longitude)))}==true
        val storeAlarms=AlarmStore(store);val alarms=storeAlarms.read()
        val requested=intent?.getIntExtra("alarmId",-1) ?: -1
        val a=alarms.firstOrNull {it.id==requested && AlarmPolicy.due(it,now,kp)} ?: alarms.firstOrNull {AlarmPolicy.due(it,now,kp)} ?: return
        intent?.removeExtra("alarmId");storeAlarms.remove(a.id);AndroidAlarms.cancel(this,a.id)
        val c=column().apply {setPadding(dp(40),dp(32),dp(40),dp(32));setBackgroundColor(this@MainActivity.background)}
        c.addView(IconView(this,Icon.BELL,palette),LinearLayout.LayoutParams(dp(72),dp(72)));c.addView(title("Aviso · AstroTab"));c.addView(text(a.label,40f,accent));c.addView(text(date(now),24f))
        val dialog=Dialog(this);alarmDialog=dialog;dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.setContentView(c)
        c.addView(button("Entendido"){dialog.dismiss();alarmDialog=null;tone?.stopTone()})
        dialog.show();dialog.window?.apply {setBackgroundDrawableResource(android.R.color.transparent);setLayout(-1,-1)
            @Suppress("DEPRECATION")
            addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            val a=attributes;a.screenBrightness=if(settings.red)0.03f else -1f;attributes=a}
        if(a.sound){if(tone==null)tone=runCatching {android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC,55)}.getOrNull();tone?.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2,1200)}
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent);setIntent(intent)
        if(intent.action==Intent.ACTION_MAIN && (intent.hasCategory(Intent.CATEGORY_HOME) || intent.hasCategory(Intent.CATEGORY_LAUNCHER)))show("Inicio")
        checkAlarms()
    }
    private fun show(name: String) {
        if(name=="Cielo" && screen=="Paso")proBState.pass?.let {pass ->
            val peak=SatellitePassPolicy.visiblePeak(pass) ?: pass.maximum
            offset=peak.time-System.currentTimeMillis();orientation.reference(peak.azimuth);orientation.tilt(peak.altitude-orientation.elevation)
        }
        requestedScroll=if(screen==name) (body.getChildAt(0) as? ScrollView)?.scrollY ?: 0 else 0
        screen=name; generation++; sky=null; home=null; guideText=null; sessionText=null; body.removeAllViews()
        if(name=="Inicio") offset=0
        val tabName=when(name) { "Esta noche","Planetas","Planeta","Meteoros","Tu cielo" -> "Noche"; "Objeto" -> "Objetos"; "Ficha" -> "Cielo"; "Zona" -> "Ajustes"; "Exposición","Encuadre","Reloj polar","Vía Láctea","Horario","Sesión","Calculadoras","Bitácora","Linterna","Hiperfocal y profundidad de campo","Timelapse","Circumpolares / trazos","Panorámica / mosaico","Apilado" -> "Herramientas"; else -> name }
        val selectedTab=if(name in listOf("Luna","Auroras","Satélites","Paso","Eventos","Plan de sesión","Equipos","Alarmas")) "Más" else tabName
        for((label,tab) in tabs) { val active=label==selectedTab; tab.background=shape(if(active) palette.raised else background,if(active) accent else background); tab.setTextColor(if(active) accent else muted); tab.setCompoundDrawablesWithIntrinsicBounds(null,IconDrawable(Icons.screen(label),if(active) accent else muted,dp(22)),null,null); tab.isSelected=active }
        when(name) {
            "Más","Satélites","Paso","Eventos","Plan de sesión","Equipos","Alarmas" -> {
                val token=generation
                val b=ProBScreens(this,settings,store,sessionStore,satelliteRepository,worker,proBState,{!isDestroyed && generation==token},::show,
                    {callback -> photoData {data -> callback(data.night,data.plans)}},::predictSatellites,::refreshSatellites,::startPlannedBlock)
                body.addView(b.render(name))
            }
            "Inicio" -> homeScreen()
            "Cielo" -> skyScreen()
            "Ficha" -> objectScreen()
            "Luna" -> moonScreen()
            "Esta noche" -> nightScreen()
            "Auroras" -> auroraScreen()
            "Tu cielo" -> bortleScreen()
            "Objetos" -> messierScreen()
            "Objeto" -> messierDetail()
            "Herramientas" -> toolsScreen()
            "Exposición","Encuadre","Reloj polar","Vía Láctea","Horario" -> equipmentToolScreen(name)
            "Sesión" -> sessionScreen()
            "Tiempo","Planetas","Planeta","Meteoros","Calculadoras","Bitácora","Zona","Linterna","Hiperfocal y profundidad de campo","Timelapse","Circumpolares / trazos","Panorámica / mosaico","Apilado" -> {
                val token=generation
                val pro=ProScreens(this,settings,repository,worker,{ !isDestroyed && generation==token },::show,::exportLog,time())
                body.addView(pro.render(name))
            }
            else -> settingsScreen()
        }
        restoreScroll()
        updateHeader()
        updateSensors()
    }
    private fun restoreScroll() {
        val y=requestedScroll
        if(y>0) (body.getChildAt(0) as? ScrollView)?.let { view -> view.post { view.scrollTo(0,y) } }
    }
    private fun updateAutoRed() {
        if(!settings.autoRed) return
        val place=settings.location ?: return
        val now=System.currentTimeMillis()
        fun sun(t: Long)=AstroPhoto.horizon(Astro.sun(Astro.dayNumber(t)),t,place).alt
        val altitude=sun(now)
        val red=SkyControls.automaticRed(altitude,sun(now+MINUTE)>altitude)
        if(settings.red!=red) { settings.red=red; buildShell() }
    }
    private fun homeScreen() {
        val place=settings.location
        val view=HomePanel(this,settings.red,zone,place,::show) { messierId=it; show("Objeto") }
        home=view; body.addView(view,FrameLayout.LayoutParams(-1,-1))
        if(place==null) return
        if(satelliteRepository.tles.isNotEmpty()) {
            val homeToken=generation
            view.bindIss(null,true)
            predictSatellites {_,_,passes -> if(!isDestroyed && generation==homeToken) {
                val pass=SatellitePassPolicy.select(passes,System.currentTimeMillis(),10,true).firstOrNull()
                view.bindIss(pass,true) {proBState.pass=pass;show("Paso")}
            }}
        }
        val now=System.currentTimeMillis(); val enabled=settings.networkEnabled; val clouds=repository.clouds
        val kp=if(enabled) DataPolicy.currentKp(repository.observed,now) ?: DataPolicy.currentKp(repository.forecast,now) else null
        val source=networkStatus(clouds?.fetchedAt,"Open-Meteo · CC BY 4.0")
        val token=generation; val tz=zone; val minimum=settings.minimumAltitude
        worker.execute {
                val reference=SkyControls.nightReference(now,tz,place)
                val key="${AstroPhoto.nightBounds(reference,tz).start}:${place.key}:${tz.id}:$minimum"
                val cached=photoCache?.takeIf {it.key==key && it.plans.size==110}
                val data=cached ?: run {
                    val night=AstroPhoto.timeline(reference,tz,place)
                    val plans=ArrayList<ObjectPlan>(110)
                    for(target in messier.objects) { if(isDestroyed) return@execute; plans.add(AstroPhoto.plan(target,night,place,minimum)) }
                    PhotoData(key,night,plans,AstroPhoto.events(night,place),AstroPhoto.galactic(night,place)).also {photoCache=it}
                }
                val week=Forecast.week(now,tz,place,clouds,enabled,nightReference=reference) { generation!=token }
                if(generation!=token) return@execute
                val hours=(0 until ceil((data.night.end-data.night.start)/HOUR.toDouble()).toInt()).map { Night.hour(data.night.start+it*HOUR,now,place,clouds,enabled) }
                val planets=ProAstro.planets(data.night,place).filter { planet -> data.night.samples.indices.any { data.night.samples[it].sun< -6 && planet.altitudes[it]>0 } }.map { it.name }
                fun moonAlt(t: Long): Double { val d=Astro.dayNumber(t); return Astro.moonHorizon(d,Astro.gmst(d)+place.longitude,place.latitude).alt }
                val eclipseTable=assets.open("events/eclipses.tsv").let(EventCalendar::eclipses)
                val nextEvents=(0..1).flatMap {month -> val c=Calendar.getInstance(tz).apply {timeInMillis=now;add(Calendar.MONTH,month)};val cacheKey="${SimpleDateFormat("yyyy-MM",Locale.ROOT).apply {timeZone=tz}.format(c.time)}:${tz.id}:${place.key}"
                    proBState.eventCache[cacheKey] ?: EventCalendar.month(c.timeInMillis,tz,place,eclipseTable).also {proBState.eventCache[cacheKey]=it}}
                val featured=nextEvents.firstOrNull {it.time>=now && it.highlighted}
                val model=HomeData(now,data.night,data.events,week,data.plans,DataPolicy.weatherAt(clouds,now,now,place,enabled),kp,Aurora.threshold(place.latitude,place.longitude),planets,Astro.horizonEvents(now,::moonAlt),hours,source,featured)
                runOnUiThread { if(!isDestroyed && generation==token) view.bind(model) }
        }
    }
    private fun simpleScreen(heading: String, message: String) { val c=column(); c.addView(title(heading)); card(c,"AstroTab",message); body.addView(scroll(c)) }
    private fun requireLocation(): Location? {
        val place=settings.location
        if(place==null) {
            val c=column(); c.addView(title("¿Desde dónde observas?")); card(c,"Ubicación sin configurar","Introduce latitud y longitud en Ajustes. No suponemos una ubicación ni dependemos de GPS o servicios de Google.")
            c.addView(button("Abrir Ajustes") { show("Ajustes") }); body.addView(scroll(c))
        }
        return place
    }
    private fun skyScreen() {
        if(requireLocation()==null) return
        val layout=row(); val map=SkyView(this,catalog,orientation) { if(it.messierId!=null) { messierId=it.messierId; show("Objeto") } else { selected=it; show("Ficha") } }.apply {
            red=settings.red; bortle=settings.bortle; location=settings.location; useSensor=settings.sensorEnabled && accelerometer!=null
            messierObjects=if(settings.showMessier) messier.objects else emptyList()
            val field=SkyControls.field(settings.equipment); fieldWidth=field.first; fieldHeight=field.second; showField=showCameraField
        }
        val mapColumn=column(); sky=map; mapColumn.addView(map,LinearLayout.LayoutParams(-1,0,1f)); layout.addView(mapColumn,LinearLayout.LayoutParams(0,-1,0.68f).apply { setMargins(0,0,dp(12),0) })
        predictSatellites {_,_,passes -> if(sky===map) {
            val next=proBState.pass?.takeIf {time() in it.start.time..it.end.time}
                ?: SatellitePassPolicy.select(passes,time()).firstOrNull {it.start.time<=time()+30*MINUTE}
            map.satelliteTrack=next?.track ?: emptyList();map.satelliteLabel=next?.let {"${if(it.tle.number==25544) "Estación Espacial" else it.tle.name.take(42)} · ${date(it.start.time,"dd/MM HH:mm")}"} ?: "";map.invalidate()
        }}
        val side=column()
        side.addView(Widgets(this,palette).section("Explora el cielo","Arrastra para girar, toca para abrir una ficha. Rumbo manual: no hay brújula. El acelerómetro solo mide elevación/roll. Catálogo J2000 y modelos aproximados conservados; no es guiado instrumental."))
        side.addView(button("Buscar objeto") { skySearch(map) })
        guideText=text("Arrastra el rumbo para orientarte",16f,accent); side.addView(guideText); updateGuide()
        side.addView(button(if(showCameraField) "Campo de cámara: sí" else "Campo de cámara: no") { showCameraField=!showCameraField; map.showField=showCameraField; map.invalidate(); show("Cielo") })
        side.addView(button(if(settings.showMessier) "Messier: visibles" else "Mostrar Messier") { settings.showMessier=!settings.showMessier; show("Cielo") })
        side.addButtons("− alt." to { orientation.tilt(-10.0); map.updateFrameLabel(); map.invalidate(); updateGuide() },"+ alt." to { orientation.tilt(10.0); map.updateFrameLabel(); map.invalidate(); updateGuide() })
        side.addView(button("Fijar referencia de rumbo") { numericInput("Rumbo de referencia (0–360°)",orientation.bearing.roundToInt().toString(),false) { value ->
            val degrees=Coordinates.number(value)
            if(degrees==null || degrees !in 0.0..360.0) "Introduce un rumbo entre 0 y 360°" else { orientation.reference(degrees); map.invalidate(); null }
        } })
        side.addView(button(if(settings.sensorEnabled && accelerometer!=null) "Inclinación: sensor" else "Inclinación: táctil") {
            settings.sensorEnabled=!settings.sensorEnabled; orientation.resetFilter(); show("Cielo")
        })
        val timeRow=row(); val clock=text(date(time(),"HH:mm z"),18f,accent); timeRow.addView(clock,LinearLayout.LayoutParams(dp(108),-2))
        val seek=SeekBar(this).apply { max=1000; progress=0; minimumHeight=dp(56); progressTintList=android.content.res.ColorStateList.valueOf(accent); thumbTintList=android.content.res.ColorStateList.valueOf(accent) }; timeRow.addView(seek,LinearLayout.LayoutParams(0,dp(56),1f))
        val start=System.currentTimeMillis(); var dawn=start+24*HOUR
        val skyReference=time()
        val token=generation; val place=settings.location!!
        worker.execute { val timeline=AstroPhoto.timeline(skyReference,zone,place); var events=AstroPhoto.events(timeline,place)
            if(events.none { it.time>skyReference && it.label.startsWith("Salida/puesta del Sol") && it.label.endsWith("ascendente") }) events=AstroPhoto.events(AstroPhoto.timeline(skyReference+24*HOUR,zone,place),place)
            val next=SkyControls.dawn(skyReference,events)
            runOnUiThread { if(generation==token) { dawn=next; seek.progress=(((time()-start)*1000/(dawn-start).coerceAtLeast(1)).toInt()).coerceIn(0,1000); seek.contentDescription="Hora simulada, ahora hasta ${date(dawn,"dd/MM HH:mm")}" } }
        }
        seek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?,progress: Int,fromUser: Boolean) { if(fromUser) { offset=SkyControls.time(start,dawn,progress)-System.currentTimeMillis(); clock.text=date(time(),"HH:mm z"); map.recalculateAsync(time(),worker); updateGuide() } }
            override fun onStartTrackingTouch(bar: SeekBar?)=Unit
            override fun onStopTrackingTouch(bar: SeekBar?)=Unit
        })
        timeRow.addView(button("Ahora") { offset=0; seek.progress=0; clock.text=date(time(),"HH:mm z"); updateHeader(); map.recalculateAsync(time(),worker); updateGuide() },LinearLayout.LayoutParams(dp(88),dp(56))); mapColumn.addView(timeRow,LinearLayout.LayoutParams(-1,dp(64)))
        layout.addView(scroll(side),LinearLayout.LayoutParams(0,-1,0.32f)); body.addView(layout); map.recalculateAsync(time(),worker)
    }
    private fun selectedHorizon(): Astro.Hor? {
        val s=selected ?: return null; val place=settings.location ?: return null
        val now=time(); val d=Astro.dayNumber(now)
        if(s.moon) return Astro.moonHorizon(d,Astro.gmst(d)+place.longitude,place.latitude)
        val index=listOf("Mercurio","Venus","Marte","Júpiter","Saturno").indexOf(s.name)
        val eq=if(index>=0) Astro.planet(index,d).eq else Astro.Eq(s.ra,s.dec)
        return SkyControls.target(eq.ra,eq.dec,now,place,s.messierId!=null)
    }
    private fun updateGuide() {
        val view=guideText ?: return; val h=selectedHorizon() ?: return
        val delta=h.alt-orientation.elevation
        view.textSize=24f
        val direction=if(h.alt<0) getString(R.string.sky_below) else if(abs(delta)<2) getString(R.string.sky_aligned) else if(delta>0) getString(R.string.sky_up,delta.roundToInt()) else getString(R.string.sky_down,abs(delta).roundToInt())
        view.text=getString(R.string.sky_guide,selected?.name,h.alt.roundToInt(),h.az.roundToInt(),direction)
    }
    private fun skySearch(map: SkyView) {
        val c=column(); val results=column(); var filter=""
        val d=dialog("Buscar objeto · catálogo offline",c)
        val choices=ArrayList<Selection>()
        messier.objects.forEach { choices.add(Selection(it.label,it.type,it.ra,it.dec,it.magnitude,messierId=it.id)) }
        val day=Astro.dayNumber(time())
        listOf("Mercurio","Venus","Marte","Júpiter","Saturno").forEachIndexed { i,name -> val state=Astro.planet(i,day); choices.add(Selection(name,"Planeta",state.eq.ra,state.eq.dec,state.magnitude)) }
        val moon=Astro.moon(day); choices.add(Selection("Luna","Sistema Solar",moon.eq.ra,moon.eq.dec,null,true))
        for(i in 0 until catalog.count) if(catalog.nameIndex[i]!=255) choices.add(Selection(catalog.name(i),"Estrella J2000",catalog.ra[i],catalog.dec[i],catalog.magnitude[i]))
        val label=text("Todos · toca para centrar",16f,muted); c.addView(label)
        fun render() { label.text=filter.ifEmpty { "Todos · toca para centrar" }; results.removeAllViews(); choices.filter { it.name.contains(filter,true) }.forEach { choice -> results.addView(button(choice.name) {
                selected=choice; selectedHorizon()?.let { orientation.reference(it.az); orientation.tilt(it.alt-orientation.elevation) }; map.updateFrameLabel(); map.invalidate(); updateGuide(); d.dismiss()
            }); if(choice.messierId!=null)results.addView(button("+ Plan · ${choice.messierId}"){d.dismiss();addTargetToSession(choice.messierId)}) }
        }
        for(keys in listOf("1234567890","QWERTYUIOP","ASDFGHJKL","ZXCVBNM")) c.addButtons(*keys.map { ch -> ch.toString() to { if(filter.length<30) filter+=ch; render() } }.toTypedArray())
        c.addButtons("Borrar" to { filter=filter.dropLast(1); render() },"Limpiar" to { filter=""; render() })
        c.addView(results); render(); d.show(); styleDialog(d)
    }
    private fun objectScreen() {
        var s=selected ?: run { show("Cielo"); return }; val place=requireLocation() ?: return
        val d=Astro.dayNumber(time()); val lst=Astro.gmst(d)+place.longitude
        if(s.kind.startsWith("Sistema Solar") || s.kind=="Planeta") {
            val p=listOf("Mercurio","Venus","Marte","Júpiter","Saturno").indexOf(s.name)
            val eq=when { s.name=="Sol" -> Astro.sun(d); s.moon -> Astro.moon(d).eq; p>=0 -> Astro.planet(p,d).eq; else -> Astro.Eq(s.ra,s.dec) }
            s=s.copy(ra=eq.ra,dec=eq.dec,magnitude=if(p>=0) Astro.planet(p,d).magnitude else s.magnitude)
        }
        val h=if(s.moon) Astro.moonHorizon(d,lst,place.latitude) else Astro.toHorizon(s.ra,s.dec,lst,place.latitude)
        val c=column(); c.addView(title(s.name)); c.addView(text(s.kind,20f,accent))
        card(c,"Posición · ${date(time())}","Altura ${number(h.alt)}° · acimut ${number(h.az)}° (norte → este)\nMagnitud ${s.magnitude?.let { number(it) } ?: "variable según fase"}")
        card(c,"Cómo encontrarlo","Fija una referencia conocida y gira el rumbo con el dedo. ${if(h.alt<0) "Ahora está bajo el horizonte." else "Ahora está sobre el horizonte geométrico."}\nEstrellas J2000 sin precesión: error de unos 0,4° en 2026. Posiciones aproximadas; sin refracción ni relieve.")
        c.addView(button("Volver al cielo") { show("Cielo") }); body.addView(scroll(c))
    }
    private fun moonScreen() {
        val place=requireLocation() ?: return
        val now=time(); val d=Astro.dayNumber(now); val phase=Astro.moonPhase(d)
        val layout=row(); val disc=MoonView(this,phase.age,place.latitude<0,settings.red)
        layout.addView(disc,LinearLayout.LayoutParams(0,-1,0.43f))
        val c=column(); c.addView(title(Astro.phaseName(phase.age)))
        val details=text("Calculando eventos…",19f); c.addView(details)
        c.addView(Widgets(this,palette).section("${number(Astro.moon(d).eq.dist*6378.137,0)} km · ${number(Astro.moonHorizon(d,Astro.gmst(d)+place.longitude,place.latitude).alt,0)}° alt","Iluminación ${DisplayFormat.number(phase.illumination*100," %")}. Distancia aproximada, horizonte geométrico con paralaje. Sin refracción ni relieve."))
        layout.addView(scroll(c),LinearLayout.LayoutParams(0,-1,0.57f)); body.addView(layout)
        val token=generation
        worker.execute {
            if(generation!=token) return@execute
            fun alt(t: Long): Double { val day=Astro.dayNumber(t); return Astro.moonHorizon(day,Astro.gmst(day)+place.longitude,place.latitude).alt }
            val events=Astro.horizonEvents(now,::alt)
            val new=Astro.nextPhase(now,0.0); val full=Astro.nextPhase(now,180.0)
            val calendar=ProAstro.moonMonth(now,zone,place) { generation!=token }
            val message="${if(events.above) "Última salida" else "Salida"}: ${events.rise?.let { date(it) } ?: "sin salida en 24 h"}\nPuesta: ${events.set?.let { date(it) } ?: "sin puesta en 24 h"}\nPróxima nueva: ${new?.let { date(it) } ?: "sin calcular"}\nPróxima llena: ${full?.let { date(it) } ?: "sin calcular"}\nEventos en ventanas de 24 h: salida anterior si está arriba y próxima puesta. Horizonte geométrico; fases con resolución de 1 h."
            runOnUiThread { if(generation==token && !isDestroyed) {
                details.text=getString(R.string.moon_short,(phase.illumination*100).roundToInt(),events.rise?.let { date(it,"HH:mm") } ?: "—",events.set?.let { date(it,"HH:mm") } ?: "—")
                c.addView(Widgets(this,palette).section("${date(now,"MMMM yyyy")}",message+"\n★: al menos 2 h de oscuridad astronómica sin Luna. Fases a las 20 h; sin refracción."))
                c.addView(MoonCalendar(this,calendar,settings.red,zone,place.latitude<0),LinearLayout.LayoutParams(-1,dp(264)))
            } }
        }
    }
    private fun nightScreen() {
        val place=requireLocation() ?: return
        val c=column(); c.addView(title("Esta noche")); val details=text("Calculando el intervalo de esta noche…",19f); c.addView(details)
        val status=text("Open-Meteo · ${if(settings.networkEnabled) DataPolicy.freshness(repository.clouds?.fetchedAt,System.currentTimeMillis()).label else "sin datos · red desactivada"}",15f,muted); c.addView(status)
        c.addButtons("Planetas y curvas" to { show("Planetas") },"Lluvias de meteoros" to { show("Meteoros") },"Tu cielo · Bortle" to { show("Tu cielo") })
        val hero=row(); c.addView(hero); body.addView(scroll(c))
        val token=generation; val now=System.currentTimeMillis(); val start=AstroPhoto.nightBounds(now,zone).start; val clouds=repository.clouds; val network=settings.networkEnabled
        worker.execute {
            if(generation!=token) return@execute
            val night=AstroPhoto.timeline(now,zone,place)
            val hours=(0 until ceil((night.end-night.start)/HOUR.toDouble()).toInt()).map { Night.hour(start+it*HOUR,now,place,clouds,network) }
            val best=Night.bestWindow(hours,now,settings.bortle)
            val events=AstroPhoto.events(night,place)
            val dusk=events.firstOrNull { it.label.startsWith("Astronómico") && it.label.endsWith("descendente") }?.time
            val dawn=events.firstOrNull { it.label.startsWith("Astronómico") && it.label.endsWith("ascendente") }?.time
            val b=StringBuilder()
            if(best==null) b.append("Nota no disponible · faltan dos horas futuras consecutivas con nubes vigentes.\n")
            else {
                b.append("${best.score}/10 · mejor ventana ${date(best.start)} → ${date(best.end)}\n")
                b.append("Factores: Bortle ×${number(1-(settings.bortle-1)*0.06,2)}; media de dos horas:\n")
                best.hours.forEach { b.append("${date(it.time,"HH:mm z")}: nubes ${number(it.cloud!!,0)} %, oscuridad ×${number(it.darkFactor)}, Luna ×${number(1-0.8*it.moonFactor,2)}, rocío ×${number(it.dewFactor,2)}, viento ×${number(it.windFactor,2)}\n") }
            }
            b.append("\n${Night.darknessLabel(hours)}\n")
            b.append("Crepúsculo astronómico: ${dusk?.let { date(it) } ?: "sin cruce de anochecer"} → ${dawn?.let { date(it) } ?: "sin cruce de amanecer"}\n")
            val galactic=hours.filter { it.galacticVisible && it.time>=now }
            b.append(if(galactic.isEmpty()) "Centro de la Vía Láctea: sin horas futuras >10° con noche astronómica.\n" else "Centro de la Vía Láctea >10° y noche: ${galactic.joinToString { date(it.time,"HH:mm") }}\n")
            b.append("\n${date(start)} → ${date(night.end)}\n")
            hours.forEach { b.append("${date(it.time,"HH:mm")}  ${it.quality?.let { q -> "${(q*10).roundToInt()}/10" } ?: "sin nota"}  · nubes ${it.cloud?.let { v -> "${v.roundToInt()} %" } ?: "sin datos"} · Sol ${number(it.sunAltitude,0)}° · Luna ${number(it.moonAltitude,0)}°\n") }
            val w=DataPolicy.weatherAt(clouds,now,now,place,network)
            b.append("\nMeteorología ahora: T ${w?.temperature?.let { number(it)+" °C" } ?: "sin datos"}; humedad ${w?.humidity?.let { number(it,0)+" %" } ?: "sin datos"}; punto de rocío ${w?.dewPoint?.let { number(it)+" °C" } ?: "sin datos"}; viento ${w?.wind?.let { number(it)+" km/h" } ?: "sin datos"}. Riesgo de rocío: ${Weather.dewRisk(w?.temperature,w?.dewPoint)}.\n")
            b.append("Rocío alto si T−Td <3 °C: factor 0,65. Viento ≥15 km/h: 0,70; ≥30: 0,40. Sin datos no se aplica penalización y esos factores quedan sin evaluar.\nNota orientativa, sin transparencia ni seeing. Umbral de noche: Sol <−18°. Caché con fecha; meteorología sin datos con red desactivada.")
            val planets=ProAstro.planets(night,place)
            val plot=PlotData.altitudes(night,planets,zone)
            val week=Forecast.week(now,zone,place,clouds,network) { generation!=token }
            runOnUiThread { if(generation==token && !isDestroyed) {
                details.text=getString(R.string.night_short,best?.let { getString(R.string.night_window_short,date(it.start,"HH:mm"),date(it.end,"HH:mm")) } ?: getString(R.string.night_no_score),DisplayFormat.duration(night.dark.sumOf { it.hours }))
                hero.addView(ScoreView(this,week.firstOrNull()?.score,palette),LinearLayout.LayoutParams(dp(96),dp(96)))
                hero.addView(text("${DisplayFormat.status(week.firstOrNull()?.score)}\n${dusk?.let { date(it,"HH:mm") } ?: "—"} → ${dawn?.let { date(it,"HH:mm") } ?: "—"}",24f,accent),LinearLayout.LayoutParams(0,-2,1f))
                hero.addView(Widgets(this,palette).info("Detalle de esta noche",b.toString()+"\n"+networkStatus(clouds?.fetchedAt,"Open-Meteo · CC BY 4.0")),LinearLayout.LayoutParams(dp(56),dp(56)))
                c.addView(SeriesChart(this,plot,settings.red),LinearLayout.LayoutParams(-1,dp(300)))
                week.forEach { forecastCard(c,it) }
                c.addView(button("Actualizar datos (intervalo 30 min)") { refreshNetwork() })
            } }
        }
    }
    private fun networkStatus(fetchedAt: Long?, source: String): String = "$source\n${DataPolicy.freshness(fetchedAt,System.currentTimeMillis()).label} · consulta ${fetchedAt?.let { date(it) } ?: "sin datos"}\n${repository.status}"
    private fun forecastCard(c: LinearLayout,n: ForecastNight) {
        val w=Widgets(this,palette); val r=row().apply { background=shape(panel) }
        r.addView(ScoreView(this,n.score,palette),LinearLayout.LayoutParams(dp(96),dp(96)))
        val data=column(); data.addView(text(date(n.start,"EEEE d MMMM"),20f,accent)); data.addView(text("${DisplayFormat.duration(n.darkHours)} oscuridad · nubes ${DisplayFormat.number(n.clouds," %")} · viento ${DisplayFormat.number(n.wind," km/h")}",16f)); r.addView(data,LinearLayout.LayoutParams(0,-2,1f)); r.addView(w.pill(n.score)); c.addView(r,LinearLayout.LayoutParams(-1,dp(104)).apply { setMargins(dp(4),dp(4),dp(4),dp(4)) })
    }
    private fun auroraScreen() {
        val place=requireLocation() ?: return; val now=System.currentTimeMillis(); val c=column()
        c.addView(title("Auroras"))
        c.addView(text(repository.status,16f,muted))
        val gm=Astro.geomagLat(place.latitude,place.longitude); val threshold=Astro.kpNeeded(gm)
        card(c,"Umbral orientativo","Latitud geomagnética ${number(gm)}°\nKp necesario: ${if(threshold>9) "fuera de escala (${number(threshold)})" else number(max(0.0,threshold))}\nAproximación de dipolo. No es una probabilidad ni garantiza visibilidad; depende también de oscuridad, nubes y horizonte.")
        val current=if(settings.networkEnabled) DataPolicy.currentKp(repository.observed,now) ?: DataPolicy.currentKp(repository.forecast,now) else null
        card(c,"Kp actual",current?.let { "${number(it.value,2)} · ${kindName(it.kind)}\nPeriodo ${date(it.time)} → ${date(it.time+it.duration)}" } ?: "Sin datos vigentes para el periodo actual; no se emite aviso.")
        val currentData=if(DataPolicy.currentKp(repository.observed,now)!=null) repository.observed else repository.forecast
        if(Aurora.currentAlert(currentData,now,place)) c.addView(text("El Kp alcanza el umbral orientativo local. Comprueba el cielo y las condiciones.",19f,accent))
        card(c,"Observado · NOAA SWPC",kpSummary(repository.observed,now,false))
        card(c,"Estimado y previsto · NOAA SWPC",kpSummary(repository.forecast,now,true))
        if(settings.networkEnabled) {
            val samples=(repository.observed?.samples.orEmpty()+repository.forecast?.samples.orEmpty()).filter { it.time>=now-72*HOUR }.sortedBy { it.time }
            if(samples.isNotEmpty()) c.addView(SeriesChart(this,PlotData.kp(samples,threshold,zone),settings.red),LinearLayout.LayoutParams(-1,dp(300)))
        }
        c.addView(button("Actualizar datos (intervalo 30 min)") { refreshNetwork() }); body.addView(scroll(c))
    }
    private fun kindName(kind: KpKind)=when(kind) { KpKind.OBSERVED -> "observado"; KpKind.ESTIMATED -> "estimado"; KpKind.PREDICTED -> "previsto" }
    private fun kpSummary(data: KpData?, now: Long, forecast: Boolean): String {
        if(data==null) return networkStatus(null,"NOAA Space Weather Prediction Center")
        val samples=if(forecast) data.samples.filter { it.time+it.duration>now && it.kind!=KpKind.OBSERVED }.take(12) else data.samples.takeLast(3)
        return networkStatus(data.fetchedAt,"NOAA SWPC")+"\n"+(if(samples.isEmpty()) "Sin cobertura futura" else samples.joinToString("\n") { "${date(it.time)}: ${number(it.value,2)} · ${kindName(it.kind)}" })
    }
    private fun bortleScreen() {
        val place=requireLocation() ?: return; val c=column(); c.addView(title("Tu cielo · Bortle ${settings.bortle}"))
        val lst=Astro.gmst(Astro.dayNumber(time()))+place.longitude
        val ideal=catalog.visibleCount(6.0,lst,place.latitude); val actual=catalog.visibleCount(min(6.0,Astro.limitingMag(settings.bortle)),lst,place.latitude)
        c.addView(text("Ideal del catálogo: $ideal estrellas sobre horizonte · tu clase: $actual\nMagnitud límite estimada ${number(Astro.limitingMag(settings.bortle))}. Catálogo limitado a mag. 6; Bortle 1–3 no añade estrellas fuera del catálogo.",17f,muted))
        c.addButtons("− Bortle" to { settings.bortle=(settings.bortle-1).coerceAtLeast(1); show("Tu cielo") },"+ Bortle" to { settings.bortle=(settings.bortle+1).coerceAtMost(9); show("Tu cielo") })
        val maps=row()
        for(b in listOf(1,settings.bortle)) {
            val box=column(); box.addView(text(if(b==1) "Ideal · catálogo" else "Tu cielo · clase $b",18f,accent))
            val map=SkyView(this,catalog,Orientation()) { selected=it; show("Ficha") }.apply { red=settings.red; bortle=b; location=place }
            box.addView(map,LinearLayout.LayoutParams(-1,dp(190))); map.recalculateAsync(time(),worker)
            maps.addView(box,LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(4),0,dp(4),0) })
        }
        c.addView(maps); body.addView(scroll(c))
    }
    private fun settingsScreen() {
        val c=column(); c.addView(title("Ajustes")); val place=settings.location
        c.addView(title("Ubicación"))
        c.addView(text("Hora/zona de la app: ${date(System.currentTimeMillis(),"dd/MM/yyyy HH:mm:ss z")} · ${zone.id}\nUbicación: ${place?.origin ?: "sin configurar"}${place?.let { " · guardada ${date(it.savedAt)}" } ?: ""}",16f,muted))
        c.addView(button("Zona horaria: ${settings.zoneId ?: "Sistema"} · cambiar") { show("Zona") })
        var latitude=place?.latitude?.let { number(it,6) } ?: ""; var longitude=place?.longitude?.let { number(it,6) } ?: ""
        val latButton=button("Latitud: ${latitude.ifEmpty { "sin definir" }}°") {}
        val lonButton=button("Longitud: ${longitude.ifEmpty { "sin definir" }}°") {}
        fun editCoordinate(isLatitude: Boolean) {
            numericInput(if(isLatitude) "Latitud (−90 a 90°)" else "Longitud (−180 a 180°)",if(isLatitude) latitude else longitude,false) { value ->
                val parsed=Coordinates.number(value); val limit=if(isLatitude) 90.0 else 180.0
                if(parsed==null || parsed !in -limit..limit) "Coordenada inválida" else {
                    if(isLatitude) { latitude=value; latButton.text=getString(R.string.latitude_value,value) } else { longitude=value; lonButton.text=getString(R.string.longitude_value,value) }; null
                }
            }
        }
        latButton.setOnClickListener { editCoordinate(true) }; lonButton.setOnClickListener { editCoordinate(false) }
        c.addView(latButton); c.addView(lonButton)
        c.addView(button("Guardar coordenadas manuales") {
            val value=Coordinates.parse(latitude,longitude,System.currentTimeMillis())
            if(value==null) message("Revisa las coordenadas","Son dos campos separados. Se acepta coma decimal. No pueden estar vacíos.")
            else { settings.location=value; offset=0; loadLocation(); show("Ajustes") }
        })
        c.addView(title("Equipo")); equipmentSettings(c)
        c.addView(button("Perfiles de equipo") {show("Equipos")})
        c.addView(button("Alarmas activas · ${AlarmStore(store).read().size}"){show("Alarmas")})
        c.addView(button("Ubicación aproximada por IP") {
            if(!settings.networkEnabled) message("Red desactivada","Activa la red opcional antes de consultar la IP.")
            else confirm("Consultar ipapi.co","Se enviará tu IP a ipapi.co y se sustituirán las coordenadas guardadas. Puede errar decenas de kilómetros. No se consulta automáticamente.") {
                networkWorker.execute {
                    val result=runCatching { repository.locateByIp(true) }
                    runOnUiThread {
                        if(!isDestroyed) result.onSuccess { settings.location=it; offset=0; loadLocation(); show("Ajustes") }
                            .onFailure { show("Ajustes"); message("No se ha podido localizar","${NetworkFailure.from(it).label}. Se conserva tu ubicación.") }
                    }
                }
            }
        })
        c.addView(title("Pantalla"))
        c.addView(button(if(settings.autoRed) "Autorrojo: activado" else "Autorrojo: desactivado") { settings.autoRed=!settings.autoRed; updateAutoRed(); show("Ajustes") })
        c.addView(Widgets(this,palette).section("Autorrojo civil","Activa rojo al acabar el crepúsculo civil (Sol bajo −6° al anochecer) y vuelve al salir el Sol (0° geométrico). Usa ubicación y hora real. El botón manual de rojo desactiva el automatismo."))
        c.addButtons("Bortle −" to { settings.bortle=(settings.bortle-1).coerceAtLeast(1); show("Ajustes") },"Clase ${settings.bortle} · +" to { settings.bortle=(settings.bortle+1).coerceAtMost(9); show("Ajustes") })
        c.addView(title("Red"))
        c.addView(button(if(settings.networkEnabled) "Red activada · desactivar" else "Red desactivada · activar") {
            if(settings.networkEnabled) { settings.networkEnabled=false; repository.enabled=false; show("Ajustes") }
            else confirm("Activar red opcional","Open-Meteo recibirá coordenadas e IP para nubes (CC BY 4.0, no comercial). NOAA SWPC recibirá tu IP para Kp. CelesTrak recibirá tu IP al actualizar TLE desde Satélites, sin enviar ubicación. HTTPS, caché fechada y consultas cada 30 min en primer plano para tiempo/Kp. ipapi.co solo cuando lo pidas. Puedes desactivar la red en cualquier momento.") {
                settings.networkEnabled=true; repository.enabled=true; refreshNetwork(); autoRefreshSatellites(); show("Ajustes")
            }
        })
        c.addView(text("No hay consultas por fotograma. Al desactivar, no se inicia ninguna nueva; una conexión ya iniciada puede tardar hasta su timeout. La caché caduca a las 3 h.",15f,muted))
        c.addView(text("Diagnóstico de red: ${repository.status}",16f,muted))
        c.addView(title("Mantenimiento")); c.addView(button("Mantenimiento y kiosco") { maintenanceEntry() })
        c.addView(button("Acerca de · licencias y límites") { about() })
        body.addView(scroll(c))
    }
    private fun loadLocation() { val place=settings.location; networkWorker.execute { repository.load(place); if(repository.enabled) repository.refresh(place); runOnUiThread { if(!isDestroyed) show(screen) } } }
    private fun refreshNetwork() {
        if(!settings.networkEnabled) { if(screen!="Cielo") message("Red desactivada","Actívala en Ajustes si deseas consultar nubes o Kp."); return }
        val place=settings.location
        networkWorker.execute { repository.refresh(place); runOnUiThread { if(!isDestroyed && screen in listOf("Esta noche","Auroras","Tiempo","Ajustes")) show(screen) } }
    }
    private fun photoData(plansRequired: Boolean=true, progress: ((PhotoData)->Unit)?=null, callback: (PhotoData)->Unit) {
        val place=settings.location ?: return
        val tz=zone; val now=SkyControls.nightReference(time(),tz,place); val minimum=settings.minimumAltitude; val token=generation
        val key="${AstroPhoto.nightBounds(now,tz).start}:${place.key}:${tz.id}:$minimum"
        worker.execute {
            if(generation!=token) return@execute
            val data=photoCache?.takeIf { it.key==key } ?: run {
                val night=AstroPhoto.timeline(now,tz,place)
                PhotoData(key,night,emptyList(),AstroPhoto.events(night,place),AstroPhoto.galactic(night,place)).also { photoCache=it }
            }
            var ready=data
            if(plansRequired && ready.plans.isEmpty()) {
                val plans=ArrayList<ObjectPlan>(110)
                for(batch in messier.objects.chunked(22)) {
                    if(isDestroyed) return@execute
                    batch.forEach { plans.add(AstroPhoto.plan(it,data.night,place,minimum)) }
                    if(progress!=null && plans.size<110) {
                        val partial=data.copy(plans=plans.toList())
                        runOnUiThread { if(!isDestroyed && generation==token) progress(partial) }
                    }
                }
                ready=data.copy(plans=plans.toList()); photoCache=ready
            }
            val result=ready
            runOnUiThread { if(!isDestroyed && generation==token) { callback(result); restoreScroll() } }
        }
    }
    private fun editValue(heading: String, initial: Double, range: ClosedFloatingPointRange<Double>, save: (Double)->Unit) {
        numericInput(heading,number(initial,2),false) { raw ->
            val n=Coordinates.number(raw)
            if(n==null || n !in range) "Introduce un valor entre ${range.start} y ${range.endInclusive}" else { save(n); show(screen); null }
        }
    }
    private fun equipmentSettings(c: LinearLayout) {
        val e=settings.equipment
        c.addView(text("Perfil activo: ${settings.activeProfile.name}",24f,accent))
        c.addButtons((if(e.tracking) "Seguimiento: sí" else "Seguimiento: no") to {settings.equipment=e.copy(tracking=!e.tracking);show("Ajustes")},(if(e.equatorial) "Montura ecuatorial" else "Montura altacimutal / fija") to {settings.equipment=e.copy(equatorial=!e.equatorial);show("Ajustes")})
        card(c,"Equipo de astrofotografía","Valores iniciales de ejemplo: ajústalos a tu equipo. Sensor ${e.sensor.label}; ${number(e.width)} × ${number(e.height)} mm.")
        c.addView(button("Focal: ${number(e.focal)} mm") { editValue("Focal real (mm)",e.focal,0.5..10000.0) { settings.equipment=e.copy(focal=it) } })
        c.addView(button("Apertura: f/${number(e.aperture)}") { editValue("Número f/",e.aperture,0.5..64.0) { settings.equipment=e.copy(aperture=it) } })
        c.addView(button("Píxel: ${number(e.pixel,2)} µm") { editValue("Tamaño de píxel (µm)",e.pixel,0.1..100.0) { settings.equipment=e.copy(pixel=it) } })
        c.addView(button("Sensor: ${e.sensor.label} · cambiar") {
            val choices=column(); val d=dialog("Formato de sensor",choices)
            SensorFormat.entries.forEach { format -> choices.addView(button(format.label) { settings.equipment=e.copy(sensor=format); d.dismiss(); show("Ajustes") }) }
            d.show(); styleDialog(d)
        })
        if(e.sensor==SensorFormat.MOBILE) {
            c.addView(button("Sensor móvil ancho: ${number(e.width)} mm") { editValue("Ancho del sensor móvil (mm)",e.width,0.5..40.0) { settings.equipment=e.copy(mobileWidth=it) } })
            c.addView(button("Sensor móvil alto: ${number(e.height)} mm") { editValue("Alto del sensor móvil (mm)",e.height,0.5..40.0) { settings.equipment=e.copy(mobileHeight=it) } })
        }
        c.addView(button("Altura mínima: ${number(settings.minimumAltitude,0)}°") { editValue("Altura mínima del planificador",settings.minimumAltitude,0.0..90.0) { settings.minimumAltitude=it } })
    }
    private fun searchInput() {
        var value=query; val c=column(); val display=text(value.ifEmpty { "—" },24f,accent)
        c.addView(display); val d=dialog("Buscar número o nombre Messier",c)
        for(keys in listOf("1234567890","QWERTYUIOP","ASDFGHJKLÑ","ZXCVBNM")) {
            c.addButtons(*keys.map { ch -> ch.toString() to { if(value.length<40) value+=ch; display.text=value; Unit } }.toTypedArray())
        }
        c.addButtons("Espacio" to { value+=" "; display.text=value },"Borrar" to { value=value.dropLast(1); display.text=value.ifEmpty { "—" } },"Limpiar" to { value=""; display.text="—" })
        c.addButtons("Cancelar" to { d.dismiss() },"Buscar" to { query=value; d.dismiss(); show("Objetos") })
        d.show(); styleDialog(d)
    }
    private fun messierScreen() {
        if(requireLocation()==null) return
        val c=column(); c.addView(title(if(choosingFrame) "Elige objeto para el encuadre" else "Objetos · mejor esta noche"))
        val controls=row(); controls.addView(button("Buscar: ${query.ifEmpty { "número o nombre" }}") { searchInput() },LinearLayout.LayoutParams(0,dp(56),1f).apply {setMargins(dp(4),dp(4),dp(4),dp(4))})
        controls.addView(button("Altura mínima: ${number(settings.minimumAltitude,0)}°") { editValue("Altura mínima",settings.minimumAltitude,0.0..90.0) { settings.minimumAltitude=it } },LinearLayout.LayoutParams(dp(260),dp(56)).apply {setMargins(dp(4),dp(4),dp(4),dp(4))}); c.addView(controls)
        c.addButtons(*listOf("Todos","Galaxia","Nebulosa","Cúmulo").map { group -> (if(group==objectGroup) "✓ $group" else group) to { objectGroup=group; show("Objetos") } }.toTypedArray())
        val loading=text("Calculando la noche…",17f,muted); c.addView(loading); body.addView(scroll(c))
        val results=column(); c.addView(results)
        val render: (PhotoData)->Unit = { data ->
            loading.text=if(data.plans.size<110) "Calculando… ${data.plans.size}/110 · resultados parciales" else "Noche calculada · 110/110"
            loading.visibility=if(data.plans.size<110) View.VISIBLE else View.GONE
            results.removeAllViews()
            val allowed=messier.find(query,objectGroup).map { it.id }.toSet()
            val plans=data.plans.filter { it.target.id in allowed }.sortedWith(compareByDescending<ObjectPlan> { it.score }.thenByDescending { it.usefulHours }.thenBy { it.target.id.drop(1).toInt() })
            results.addView(Widgets(this,palette).section("${plans.size} objetos","Orden orientativo: 100 × min(horas útiles/6, 1) × media(sen altura) × factor lunar. Sin meteorología. Noche completa, incluye horas pasadas. Azul: curva de altura; marca horizontal: horizonte. Hora óptima dentro de oscuridad sobre altura mínima; si no hay ventana: —."))
            for(p in plans) {
                val item=button("${p.target.label}\n${p.target.type}") {
                messierId=p.target.id
                if(choosingFrame) { choosingFrame=false; show("Encuadre") } else show("Objeto")
                }.apply { gravity=Gravity.CENTER_VERTICAL or Gravity.START; setCompoundDrawablesWithIntrinsicBounds(IconDrawable(Icons.type(p.target.type),accent,dp(32)),null,null,null); compoundDrawablePadding=dp(12) }
                val r=row().apply { background=shape(panel) }; r.addView(item,LinearLayout.LayoutParams(0,dp(80),1f)); r.addView(MiniAltitude(this,p.miniAltitudes,settings.red),LinearLayout.LayoutParams(dp(150),dp(64)))
                val optimal=data.night.samples.indices.filter { data.night.samples[it].sun < -18 && p.altitudes[it]>=settings.minimumAltitude }.maxByOrNull { p.altitudes[it] }
                r.addView(text(optimal?.let { date(data.night.samples[it].time,"HH:mm") } ?: "—",24f,accent),LinearLayout.LayoutParams(dp(100),-2)); r.addView(text("${p.score}",28f,palette.score(p.score))); r.addView(Widgets(this,palette).pill(p.score)); results.addView(r,LinearLayout.LayoutParams(-1,dp(88)).apply { bottomMargin=dp(8) })
            }
        }
        photoData(progress=render,callback=render)
    }
    private fun messierDetail() {
        val place=requireLocation() ?: return; val now=time()
        val target=messier.objects.first { it.id==messierId }
        val eq=AstroPhoto.precess(target.eq,now); val h=AstroPhoto.horizon(eq,now,place); val d=Astro.dayNumber(now)
        val c=column(); c.addView(title(target.label))
        val hero=row(); hero.addView(IconView(this,Icons.type(target.type),palette),LinearLayout.LayoutParams(dp(72),dp(72))); hero.addView(text("${target.type}\n${target.constellation ?: "Catálogo Messier"}",24f,accent)); c.addView(hero)
        card(c,"Catálogo offline · d3-celestial","Tipo: ${target.type}\nConstelación: ${target.constellation ?: "—"}\nMagnitud: ${target.magnitude?.let { number(it) } ?: "—"}\nTamaño: ${target.size?.let { "$it minutos de arco" } ?: "—"}\n${target.designation}")
        card(c,"Ahora · ${date(now)}","Altura ${number(h.alt)}° · acimut ${number(h.az)}°\nDistancia angular a la Luna ${number(AstroPhoto.separation(eq,Astro.moon(d).eq))}° · iluminación ${number(Astro.moonPhase(d).illumination*100,0)} %")
        c.addView(button("Altura mínima: ${number(settings.minimumAltitude,0)}°") { editValue("Altura mínima",settings.minimumAltitude,0.0..90.0) { settings.minimumAltitude=it } })
        val loading=text("Calculando curva…"); c.addView(loading)
        c.addButtons("Lista de objetos" to { show("Objetos") },"Encuadre del objeto" to { show("Encuadre") })
        c.addButtons("Añadir al plan de sesión" to {addTargetToSession(target.id)},"Alarma de altura mínima" to {addObjectAlarm(target)})
        body.addView(scroll(c))
        photoData { data ->
            c.removeView(loading); val p=data.plans.first { it.target.id==target.id }
            card(c,"Planificador · ${p.score}/100","Culminación superior: ${date(p.transit)} · ${number(p.transitAltitude)}° (puede ser de día)\n${number(p.usefulHours,2)} h sobre ${number(settings.minimumAltitude,0)}° con Sol <−18°.\nPuntuación: 100 × tiempo ${number(p.timeFactor,2)} × altura ${number(p.altitudeFactor,2)} × Luna ${number(p.moonFactor,2)}. Tiempo=min(horas/6,1), altura=media sen(altura). Penalización lunar=iluminación × (1−separación/180) × sen(altura lunar), solo sobre horizonte. Sin horas útiles: 0.\nNoche completa ${date(data.night.start)} → ${date(data.night.end)}. Curvas 10 min; cruces interpolados; sin refracción/relieve. Precesión media desde J2000.")
            c.addView(NightChart(this,data.night,p.altitudes,settings.minimumAltitude,settings.red,zone,now,p.windows),LinearLayout.LayoutParams(-1,dp(240)))
        }
    }
    private fun spansLabel(spans: List<TimeSpan>)=if(spans.isEmpty()) "Sin ventana en el intervalo" else spans.joinToString("\n") { "${date(it.start)} → ${date(it.end)} (${DisplayFormat.duration(it.hours)})" }
    private fun toolsScreen() {
        val c=column(); c.addView(title("Herramientas de astrofotografía"))
        val entries=listOf("Exposición" to Icon.CAMERA,"Encuadre" to Icon.GALAXY,"Reloj polar" to Icon.CLOCK,"Vía Láctea" to Icon.NEBULA,"Horario" to Icon.SUN,"Planificar sesión de tomas" to Icon.TELESCOPE,"Más calculadoras" to Icon.TOOLS,"Bitácora" to Icon.STAR,"Linterna roja · tocar para salir" to Icon.MOON)
        entries.chunked(3).forEach { group -> val r=row(); group.forEach { (label,icon) -> r.addView(button(label) { show(when(label) { "Planificar sesión de tomas" -> "Sesión"; "Más calculadoras" -> "Calculadoras"; "Linterna roja · tocar para salir" -> "Linterna"; else -> label }) }.apply { setCompoundDrawablesWithIntrinsicBounds(null,IconDrawable(icon,accent,dp(36)),null,null); compoundDrawablePadding=dp(12) },LinearLayout.LayoutParams(0,dp(112),1f).apply { setMargins(dp(4),dp(4),dp(4),dp(4)) }) }; c.addView(r) }
        body.addView(scroll(c))
    }
    private fun equipmentToolScreen(name: String) {
        val e=settings.equipment; val c=column(); c.addView(title(name))
        c.addView(text("Equipo: ${number(e.focal)} mm · f/${number(e.aperture)} · ${e.sensor.label} · ${number(e.pixel,2)} µm. Valores editables en Ajustes.",17f,muted))
        c.addView(button("Todas las herramientas") { show("Herramientas") })
        if(name=="Exposición") {
        val r500=Optics.rule500(e); val npf=Optics.npf(e,targetDeclination)
        c.addView(text("${DisplayFormat.number(min(r500,npf ?: r500)," s",1)}",Design.DISPLAY,accent))
        card(c,"Exposición máxima sin trazos","Regla 500: ${number(r500,2)} s\nNPF simplificada: ${npf?.let { number(it,2)+" s" } ?: "fuera del modelo cerca del polo"}\nMás estricta: ${if(npf==null || r500<=npf) "500" else "NPF"}. Orientativas; comprueba las estrellas ampliadas.\n500/(focal × recorte); NPF=(35 × f/ + 30 × píxel)/(focal × cos δ). Sin seguimiento.")
        c.addView(button("Declinación del objetivo: ${number(targetDeclination,1)}°") { editValue("Declinación objetivo (°)",targetDeclination,-90.0..90.0) { targetDeclination=it } })
        c.addButtons("− 1°" to { targetDeclination=(targetDeclination-1).coerceAtLeast(-90.0); show(name) },"+ 1°" to { targetDeclination=(targetDeclination+1).coerceAtMost(90.0); show(name) })
        body.addView(scroll(c)); return
        }
        if(name=="Encuadre") {
        val target=messier.objects.first { it.id==messierId }; val fit=Optics.fit(e,target)
        c.addView(text("${DisplayFormat.number(Optics.field(e.width,e.focal),"°",1)} × ${DisplayFormat.number(Optics.field(e.height,e.focal),"°",1)}",Design.DISPLAY,accent))
        card(c,"Encuadre · ${target.label}","Campo ${number(Optics.field(e.width,e.focal),2)}° × ${number(Optics.field(e.height,e.focal),2)}° · escala ${number(Optics.scale(e),2)} ″/píxel\n${fit?.let { "Ocupa ${number(it.widthPercent)} % × ${number(it.heightPercent)} % del campo: ${if(it.fits) "cabe" else "no cabe"}." } ?: "Tamaño del objeto: —"}\nEjes mayor/menor alineados con el sensor; sin margen ni rotación fina.")
        c.addView(button("Elegir Messier para encuadre") { choosingFrame=true; show("Objetos") })
        c.addButtons("− focal" to { settings.equipment=e.copy(focal=(e.focal-1).coerceAtLeast(0.5)); show(name) },"+ focal" to { settings.equipment=e.copy(focal=(e.focal+1).coerceAtMost(10000.0)); show(name) }); body.addView(scroll(c)); return
        }
        if(settings.location==null) { card(c,"Ubicación sin configurar","Configura Ajustes para el reloj polar, la Vía Láctea y el horario."); body.addView(scroll(c)); return }
        val place=settings.location!!; val now=time(); val polar=AstroPhoto.polar(now,place)
        if(name=="Reloj polar") {
        card(c,"Alineación polar · ${polar.star}","Ángulo horario ${number(polar.hourAngle/15,2)} h (${number(polar.hourAngle)}°) · radio polar ${number(polar.radius,3)}°.\nVista directa hacia el polo: cenit arriba, acimut creciente a la derecha. Ángulo desde las 12 en sentido horario ${number(polar.clockAngle)}°. Adapta el giro/inversión de tu buscador. Precesión IAU 1976 desde J2000; sin nutación ni movimiento propio; orientación aproximada.")
        c.addView(PolarClock(this,polar,settings.red),LinearLayout.LayoutParams(-1,dp(200)))
        body.addView(scroll(c)); return
        }
        val loading=text("Calculando Vía Láctea y horario…"); c.addView(loading); body.addView(scroll(c))
        val token=generation
        photoData(plansRequired=false) { data ->
            c.removeView(loading); val g=data.galactic
            if(name=="Vía Láctea") {
            card(c,"Vía Láctea · núcleo galáctico",spansLabel(g.windows)+(g.highest?.let { "\nMáximo dentro de oscuridad y >10°: ${date(it)}, altura ${number(g.altitude!!)}°, acimut ${number(g.azimuth!!)}°." } ?: "\nNo supera 10° en oscuridad astronómica."))
            val months=text("Calculando meses representativos…",17f,muted); c.addView(months)
            worker.execute {
                val year=Calendar.getInstance(zone).apply { timeInMillis=now }.get(Calendar.YEAR)
                val key="$year:${place.key}:${zone.id}"
                if(key!=monthKey) { monthCache=AstroPhoto.galacticMonths(year,zone,place); monthKey=key }
                val message="Meses por horas útiles medias (noches 5, 15 y 25 de $year; clima sin evaluar):\n"+monthCache.joinToString(" · ") { "${it.first}: ${number(it.second,1)} h" }
                runOnUiThread { if(generation==token && !isDestroyed) { months.text=monthCache.take(3).joinToString(" · ") { "Mes ${it.first}: ${DisplayFormat.duration(it.second)}" }; c.addView(Widgets(this,palette).section("Todos los meses",message)) } }
            }
            } else {
            card(c,"Horario de la noche",data.events.joinToString("\n") { "${date(it.time)} · ${it.label}" }.ifEmpty { "Sin cruces en el intervalo (posible día/noche polar)." }+"\nLuna: horizonte geométrico con paralaje; sin refracción. Dorada: −4° a +6°; azul: −6° a −4°.")
            c.addView(NightBar(this,data.night,settings.red,zone),LinearLayout.LayoutParams(-1,dp(124)))
            card(c,"Oscuridad astronómica",spansLabel(data.night.dark))
            card(c,"Ventana oscura sin Luna",spansLabel(data.night.moonless)+"\nSol <−18°, centro lunar bajo horizonte. Curvas 10 min; cruces interpolados; incluye las horas ya pasadas de esta noche.")
            }
        }
    }
    private fun sessionScreen() {
        val c=column(); c.addView(title("Sesión de tomas · manual")); val p=sessionPlan
        getSharedPreferences("session-plan",MODE_PRIVATE).edit().putFloat("exposure",p.exposureSeconds.toFloat()).putFloat("pause",p.pauseSeconds.toFloat()).apply()
        plannedTarget?.let {c.addView(text("Objeto ${it.id} · equipo $plannedEquipment",24f,accent))}
        c.addView(button("Plan de sesión · objetos y noche"){show("Plan de sesión")})
        if(pendingLog!=null)c.addView(button("Reintentar guardar en Bitácora"){savePendingLog()})
        val running=sessionTimer?.state()?.done==false
        sessionText=text("",46f,Color.rgb(230,55,40)); c.addView(sessionText)
        c.addView(button(if(soundEnabled) "Pitido: sí" else "Pitido: no") { soundEnabled=!soundEnabled; show("Sesión") })
        c.addButtons("Iniciar" to {
            if(sessionTimer?.state()?.done!=false) { plannedTarget=null;sessionCancelled=false;sessionTimer=SessionTimer(sessionPlan,SystemClock::elapsedRealtime); previousSessionState=null; show("Sesión"); updateSession() }
        },"Parar" to { sessionCancelled=true;sessionTimer?.stop(); previousSessionState=null; show("Sesión") })
        val remaining=sessionTimer?.state()?.takeUnless { it.done }?.totalRemainingMillis ?: p.durationMillis
        card(c,"Plan","${p.lights} luces × ${number(p.exposureSeconds)} s; pausa ${number(p.pauseSeconds)} s entre tomas\n${p.darks} darks; ${p.flats} flats × ${number(p.flatSeconds)} s\nTotal ${number(p.durationMillis/60000.0,2)} min · fin previsto ${date(System.currentTimeMillis()+remaining)}\nDarks tras luces: tapa el objetivo. Flats al final: prepara iluminación y exposición. Sin pausa tras la última toma. El cambio de fase no espera confirmación.")
        if(!running) {
            fun edit(label: String, initial: Double, range: ClosedFloatingPointRange<Double>, save: (Double)->Unit) {
                c.addView(button(label) { editValue(label,initial,range) { save(it); sessionTimer=null } })
                c.addButtons("−" to { save((initial-1).coerceIn(range)); sessionTimer=null; show("Sesión") },"+" to { save((initial+1).coerceIn(range)); sessionTimer=null; show("Sesión") })
            }
            edit("Número de luces: ${p.lights}",p.lights.toDouble(),1.0..10000.0) { sessionPlan=p.copy(lights=it.toInt()) }
            edit("Exposición: ${number(p.exposureSeconds)} s",p.exposureSeconds,0.1..86400.0) { sessionPlan=p.copy(exposureSeconds=it) }
            edit("Pausa: ${number(p.pauseSeconds)} s",p.pauseSeconds,0.0..3600.0) { sessionPlan=p.copy(pauseSeconds=it) }
            edit("Darks: ${p.darks}",p.darks.toDouble(),0.0..10000.0) { sessionPlan=p.copy(darks=it.toInt()) }
            edit("Flats: ${p.flats}",p.flats.toDouble(),0.0..10000.0) { sessionPlan=p.copy(flats=it.toInt()) }
            edit("Exposición flats: ${number(p.flatSeconds)} s",p.flatSeconds,0.1..86400.0) { sessionPlan=p.copy(flatSeconds=it) }
        }
        c.addView(text("Reloj monotónico, independiente de la hora civil y del desplazamiento del mapa. Mantén AstroTab visible: al salir no suena; al volver recupera el tiempo transcurrido. Reiniciar el proceso cancela la sesión. Pantalla encendida mientras usas la app.",16f,muted))
        body.addView(scroll(c)); updateSession()
    }
    private fun beep(doubleBoundary: Boolean=false) {
        if(!soundEnabled) return
        if(tone==null) tone=runCatching { android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC,55) }.getOrNull()
        tone?.startTone(if(doubleBoundary) android.media.ToneGenerator.TONE_PROP_BEEP2 else android.media.ToneGenerator.TONE_PROP_BEEP,if(doubleBoundary) 220 else 120)
    }
    private fun updateSession() {
        val timer=sessionTimer ?: run { sessionText?.text=getString(R.string.session_ready); return }
        val state=timer.state(); val previous=previousSessionState
        if(previous==null) { if(!state.done && state.exposing) beep() }
        else if(previous!=state && (previous.index!=state.index || previous.exposing!=state.exposing || previous.done!=state.done)) {
            val ended=previous.exposing && (!state.exposing || previous.index!=state.index)
            val began=state.exposing && (previous.index!=state.index || !previous.exposing)
            if(ended || began) beep(ended && began)
        }
        previousSessionState=state
        if(state.done && plannedTarget!=null && !sessionCancelled) {
            val block=plannedTarget!!;plannedTarget=null
            pendingLog=LogEntry(System.currentTimeMillis(),block.id,plannedEquipment,timer.plan.lights,timer.plan.exposureSeconds,"Plan de sesión completado con temporizador; tomas estimadas, sin control de cámara")
            savePendingLog()
        }
        val seconds=ceil(state.remainingMillis/1000.0).toLong()
        sessionText?.text=if(state.done) "Sesión finalizada" else "${timer.plan.kind(state.index)} ${state.index+1}/${timer.plan.count}\n${if(state.exposing) "TOMA" else "PAUSA"}  ${seconds/60}:${(seconds%60).toString().padStart(2,'0')}\nFin ${date(System.currentTimeMillis()+state.totalRemainingMillis,"HH:mm:ss z")}"
    }
    private fun about() {
        val c=column(); c.addView(title("AstroTab 0.7.0 · MS Robotika"))
        c.addView(text("Pro B: satélites CelesTrak con SGP4 Vallado en Kotlin, calendario y eclipses NASA 2026–2035, plan de sesión y bitácora, avisos y perfiles de equipo. Satélites sin magnitud estimada; TLE de más de tres días: datos viejos. Calendario calculado orientativo (horas/~1°); eclipses: altura en máximo global, visibilidad solar local por confirmar. Alarmas exactas API23, restricciones en Android moderno. Todo sin permisos nuevos ni dependencias nuevas.",17f))
        c.addView(text("Inicio para esta noche, sistema visual común, Vía Láctea d3-celestial, encuadre de cámara, búsqueda y tiempo simulado. Autorrojo civil opcional. Motor astronómico y política de datos conservados. FPS, memoria y tacto de esta versión pendientes de medir en tablet.",17f))
        c.addView(text("Tiempo 48 h y 7 noches, curvas Canvas, planetas, meteoros, calendario lunar, calculadoras y bitácora CSV. Zona horaria propia. Turbulencia estimada por viento a 250 hPa: no es seeing medido. Curvas cada 10 min, cruces refinados. Sin datos meteorológicos ficticios. Objetivos tablet <3 s lista, <1 s pestañas, <70 MB RSS pendientes de medir en 0.4.",17f))
        c.addView(text("Proyecto abierto y no comercial. 5044 estrellas y 110 Messier offline (d3-celestial, BSD-3). Objetos y planificador, óptica 500/NPF, encuadre, reloj polar, Vía Láctea, horario y sesión manual de tomas. Meteorología opcional Open-Meteo. Sin cámara ni Bluetooth. Precesión media IAU 1976 para Messier/reloj/núcleo; sin refracción, nutación ni relieve. Curvas de 10 minutos, orientación aproximada: no guía de monturas. La nota es una heurística explicada. Memoria <70 MB, sensores, tacto, sonido y kiosco pendientes de comprobación física de esta versión.",17f))
        for(name in listOf("LICENSE","THIRD_PARTY_NOTICES.md","licenses/APACHE-2.0.txt")) c.addView(text(assets.open(name).bufferedReader().use { it.readText() },14f,muted))
        val dialog=dialog("Acerca de",c); c.addView(button("Cerrar") { dialog.dismiss() }); dialog.show(); styleDialog(dialog)
    }
    /** Teclado propio para no abrir un IME de terceros que rompa el modo rojo. */
    private fun numericInput(heading: String, initial: String, secret: Boolean, accept: (String)->String?) {
        var value=initial; val c=column(); val display=text("",24f,accent); val error=text("",15f,muted)
        fun update() { display.text=if(secret) "•".repeat(value.length) else value.ifEmpty { "—" } }
        update(); c.addView(display); c.addView(error)
        val dialog=dialog(heading,c)
        val keys=if(secret) listOf("1","2","3","4","5","6","7","8","9","Borrar","0","Limpiar") else listOf("1","2","3","4","5","6","7","8","9","−","0",",","Borrar","Limpiar")
        for(group in keys.chunked(3)) c.addButtons(*group.map { key -> key to {
            value=when(key) { "Borrar" -> value.dropLast(1); "Limpiar" -> ""; else -> if(value.length<24) value+key else value }; update(); Unit
        } }.toTypedArray())
        c.addButtons("Cancelar" to { dialog.dismiss() },"Aceptar" to { val problem=accept(value); if(problem==null) dialog.dismiss() else error.text=problem })
        dialog.show(); styleDialog(dialog)
    }
    private fun dialog(heading: String, content: LinearLayout): Dialog {
        val d=Dialog(this); d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val c=column().apply { setPadding(dp(12),dp(8),dp(12),dp(8)); setBackgroundColor(this@MainActivity.background) }
        c.addView(text(heading,23f,accent)); c.addView(content)
        d.setContentView(scroll(c)); return d
    }
    private fun styleDialog(dialog: Dialog) {
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent); setLayout(min(resources.displayMetrics.widthPixels-dp(40),dp(760)),resources.displayMetrics.heightPixels-dp(32))
            val a=attributes; a.screenBrightness=if(settings.red) 0.03f else -1f; attributes=a
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility=immersiveFlags()
        }
    }
    private fun message(heading: String, message: String) { val c=column(); c.addView(text(message)); val d=dialog(heading,c); c.addView(button("Cerrar") { d.dismiss() }); d.show(); styleDialog(d) }
    private fun confirm(heading: String, message: String, action: ()->Unit) {
        val c=column(); c.addView(text(message)); val d=dialog(heading,c)
        c.addButtons("Cancelar" to { d.dismiss() },"Confirmar" to { d.dismiss(); action() }); d.show(); styleDialog(d)
    }
    private fun maintenanceEntry() {
        if(!pin.configured) {
            numericInput("Crea un PIN de mantenimiento (6–12 cifras)","",true) { first ->
                if(!first.matches(Regex("[0-9]{6,12}"))) "Usa entre 6 y 12 cifras" else {
                    numericInput("Repite el PIN. Guárdalo en un lugar seguro.","",true) { second ->
                        if(second!=first) "Los PIN no coinciden" else { pin.create(second); kiosk.unlock(true); maintenancePanel(); null }
                    }; null
                }
            }
        } else numericInput("PIN de mantenimiento","",true) { value ->
            if(!pin.verify(value)) "PIN incorrecto o espera activa (30 s tras 5 intentos)" else { kiosk.unlock(true); maintenancePanel(); null }
        }
    }
    private fun maintenancePanel() {
        maintenanceDialog?.dismiss()
        val c=column(); c.addView(text(if(kiosk.owner) "Device Owner presente. Mantenimiento autenticado." else "Sin Device Owner: app normal. Aprovisionamiento pendiente de Abel."))
        val d=dialog("Mantenimiento",c)
        maintenanceDialog=d
        c.addButtons("Wi-Fi" to { launchSettings(AndroidSettings.ACTION_WIFI_SETTINGS) },"Fecha y hora" to { launchSettings(AndroidSettings.ACTION_DATE_SETTINGS) })
        c.addView(button("Exportar ajustes y diagnóstico") { exportSettings() })
        c.addView(button("Activar kiosco y volver") {
            if(!kiosk.owner) message("Sin Device Owner","Consulta docs/KIOSCO.md. No se ha fijado pantalla ni activado lock task.")
            else { settings.kioskEnabled=true; d.dismiss(); kiosk.resume(true,pin.configured) }
        })
        c.addView(button("Desactivar kiosco y HOME persistente") {
            settings.kioskEnabled=false; kiosk.disable(); d.dismiss()
            message("Kiosco desactivado","Se ha retirado HOME persistente y la lista de lock task. El rol Device Owner permanece hasta su retirada por el propietario o un restablecimiento.")
        })
        c.addView(button("Abrir inicio del sistema (kiosco desactivado)") {
            settings.kioskEnabled=false; kiosk.disable(); d.dismiss(); launchSettings(AndroidSettings.ACTION_HOME_SETTINGS)
        })
        c.addView(button("Volver a AstroTab") { d.dismiss(); kiosk.resume(settings.kioskEnabled,pin.configured) })
        d.setOnCancelListener { kiosk.resume(settings.kioskEnabled,pin.configured) }
        d.show(); styleDialog(d)
    }
    private fun launchSettings(action: String) { try { startActivity(Intent(action)) } catch(_: ActivityNotFoundException) { message("No disponible","El sistema no ofrece esta pantalla de ajustes.") } }
    private fun exportSettings() {
        try { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="text/plain"; putExtra(Intent.EXTRA_TITLE,"AstroTab-diagnostico.txt") },41) }
        catch(_: ActivityNotFoundException) { message("Exportación no disponible","No hay selector de documentos en este sistema.") }
    }
    private fun exportLog() {
        try { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="text/csv"; putExtra(Intent.EXTRA_TITLE,"AstroTab-bitacora.csv") },42) }
        catch(_: ActivityNotFoundException) { message("Exportación no disponible","No hay selector de documentos en este sistema.") }
    }
    @Deprecated("Interfaz compatible con API 23")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==42 && resultCode==RESULT_OK && data?.data!=null) {
            val uri=data.data!!; val tz=zone
            worker.execute {
                val result=runCatching {
                    val entries=LogBook(java.io.File(filesDir,"bitacora.json")).read()
                    val output=contentResolver.openOutputStream(uri) ?: error("Destino no disponible")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(LogBook.csv(entries,tz)) }
                }
                runOnUiThread { if(!isDestroyed) message(if(result.isSuccess) "Bitácora exportada" else "No se ha podido exportar",if(result.isSuccess) "CSV guardado con la zona ${tz.id}." else "Comprueba el destino y el espacio disponible.") }
            }
        }
        if(requestCode==41 && resultCode==RESULT_OK && data?.data!=null) {
            val uri=data.data!!; val place=settings.location
            try { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write("AstroTab 0.7.0 · ${date(System.currentTimeMillis())}\nUbicación: $place\nBortle: ${settings.bortle}\nRed: ${settings.networkEnabled}\n${repository.status}\nSin PIN ni credenciales. Pruebas físicas pendientes.\n") } }
            catch(_: Exception) { message("No se ha podido exportar","Comprueba el destino y el espacio disponible.") }
        }
    }
    private fun updateSensors() {
        val active=SensorPolicy.active(resumed,screen=="Cielo" && sky!=null,settings.sensorEnabled,accelerometer!=null)
        if(active && !sensorRegistered) { orientation.resetFilter(); sensorRegistered=sensorManager.registerListener(this,accelerometer,50000) }
        else if(!active && sensorRegistered) { sensorManager.unregisterListener(this); sensorRegistered=false; orientation.resetFilter() }
    }
    override fun onSensorChanged(event: SensorEvent) {
        @Suppress("DEPRECATION")
        val rotation=windowManager.defaultDisplay.rotation
        orientation.acceleration(event.values[0],event.values[1],event.values[2],rotation)
        sky?.updateFrameLabel(); sky?.postInvalidateDelayed(50); updateGuide()
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if(hasFocus && ::settings.isInitialized) immersive()
    }
    override fun onResume() {
        super.onResume(); resumed=true; immersive(); updateSensors(); previousSessionState=sessionTimer?.state(); handler.post(tick); handler.post(networkTick); handler.post(sessionTick)
        if(kiosk.maintenance) maintenancePanel() else runCatching { kiosk.enter(settings.kioskEnabled,pin.configured) }.onFailure { message("Kiosco no disponible","No se ha podido iniciar lock task. Revisa el aprovisionamiento y la recuperación documentada.") }
    }
    override fun onPause() { resumed=false; handler.removeCallbacks(tick); handler.removeCallbacks(networkTick); handler.removeCallbacks(sessionTick); updateSensors(); super.onPause() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); worker.shutdownNow(); networkWorker.shutdownNow(); satelliteWorker.shutdownNow(); tone?.release(); super.onDestroy() }
    @Deprecated("Retención compatible con API 23")
    override fun onRetainNonConfigurationInstance(): Any? = sessionTimer
    private fun registerModernBack() {
        if(Build.VERSION.SDK_INT>=33) onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { handleBack() }
    }
    private fun handleBack() { if(settings.kioskEnabled && !kiosk.maintenance) maintenanceEntry() else if(screen!="Cielo") show("Cielo") else finish() }
    // Rama de API 23–32; desde API 33 se registra OnBackInvokedCallback.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Deprecated("Navegación compatible con API 23")
    override fun onBackPressed() { handleBack() }
}
