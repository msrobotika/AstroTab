package org.msrobotika.astrotab

import org.junit.Assert.*
import org.junit.Test
import org.msrobotika.astrotab.astro.*
import org.msrobotika.astrotab.catalog.*
import org.msrobotika.astrotab.data.*
import org.msrobotika.astrotab.ui.theme.*
import java.util.*
import kotlin.math.*

class ProBTest {
    private val zone=TimeZone.getTimeZone("Europe/Madrid")
    private val place=Location(40.4168,-3.7038,"manual",0)
    private class Memory(val values:MutableMap<String,String> = mutableMapOf()): PreferenceStore {
        override fun get(key:String)=values[key]
        override fun put(values:Map<String,String?>){for((k,v) in values)if(v==null)this.values.remove(k)else this.values[k]=v}
    }
    @Test fun equipmentMigrationActiveRestartAndIndependentProfiles() {
        val store=Memory(mutableMapOf("focal" to "135.0","sensorFormat" to "APS_C","pixel" to "3.76"));val s=Settings(store)
        assertEquals(135.0,s.equipment.focal,0.0);assertEquals("Mi equipo",s.activeProfile.name)
        s.saveProfile(EquipmentProfile("scope","Telescopio",Equipment(800.0,4.0,SensorFormat.ASTRO_533,3.76,tracking=true,equatorial=true)))
        s.activeProfileId="scope";assertEquals(800.0,s.equipment.focal,0.0);assertTrue(s.equipment.tracking)
        s.equipment=s.equipment.copy(focal=600.0)
        val restart=Settings(store);assertEquals("scope",restart.activeProfileId);assertEquals(600.0,restart.equipment.focal,0.0)
        restart.activeProfileId="legacy";assertEquals(135.0,restart.equipment.focal,0.0)
        restart.removeProfile("legacy");assertEquals("scope",restart.activeProfileId)
        assertTrue(runCatching {restart.removeProfile("scope")}.isFailure)
        assertEquals(11.3,restart.equipment.width,0.0)
    }
    @Test fun nasaTableCompleteAndGlobalMaximumHorizonHonest() {
        val table=EventCalendar.eclipses(javaClass.getResourceAsStream("/events/eclipses.tsv")!!)
        assertEquals(45,table.size);assertEquals(22,table.count {it.solar});assertEquals(23,table.count {!it.solar})
        for(year in 2026..2035)assertTrue(table.count {Calendar.getInstance(zone).apply {timeInMillis=it.time}.get(Calendar.YEAR)==year}>=4)
        val august=table.first {it.solar && it.type=="T" && it.time in Utc.parse("2026-08-01T00:00Z")..Utc.parse("2026-09-01T00:00Z")}
        assertEquals(Utc.parse("2026-08-12T17:45:51Z"),august.time)
        assertTrue(EventCalendar.eclipseEvent(august,place).detail.contains("por confirmar"))
        assertTrue(EventCalendar.eclipseEvent(august,null).detail.contains("Configura ubicación"))
        val d=Astro.dayNumber(august.time);val sun=Astro.sun(d)
        val antipode=Location(-sun.dec,Astro.rev(sun.ra-Astro.gmst(d)+180+180)-180,"manual",0)
        assertTrue(EventCalendar.eclipseEvent(august,antipode).detail.contains("bajo el horizonte"))
    }
    @Test fun calendarPhasesExtremaAndConjunctionsAreCalculated() {
        val now=Utc.parse("2026-08-15T00:00Z");val list=EventCalendar.month(now,zone,place,emptyList())
        val newMoon=list.single {it.title=="Luna nueva"}
        assertTrue(abs(newMoon.time-Utc.parse("2026-08-12T17:45:51Z"))<12*HOUR)
        val phase=Astro.moonPhase(Astro.dayNumber(newMoon.time)).age
        assertTrue(phase<0.03 || phase>359.97)
        assertEquals(4,list.count {it.kind=="moon" && !it.title.contains("geo")})
        for(e in list.filter {it.title=="Perigeo lunar" || it.title=="Apogeo lunar"}) {
            val r=Astro.moon(Astro.dayNumber(e.time)).eq.dist
            val before=Astro.moon(Astro.dayNumber(e.time-3*HOUR)).eq.dist
            val after=Astro.moon(Astro.dayNumber(e.time+3*HOUR)).eq.dist
            assertTrue(if(e.title=="Perigeo lunar")r<=before && r<=after else r>=before && r>=after)
        }
        assertTrue(list.any {it.title=="Perseidas"});assertTrue(list.zipWithNext().all {it.first.time<=it.second.time})
        assertTrue(list.any {it.kind=="conjunction"});assertTrue(list.all {it.detail.isNotBlank()})
    }
    private fun plans(): Pair<NightTimeline,List<ObjectPlan>> {
        val catalog=MessierCatalog(javaClass.getResourceAsStream("/catalog/messier.tsv")!!)
        val night=AstroPhoto.timeline(Utc.parse("2026-11-06T18:00Z"),zone,place)
        return night to catalog.objects.map {AstroPhoto.plan(it,night,place,30.0)}
    }
    @Test fun diverseTargetsAreSeparatedAndCurvesHaveAllSamples() {
        val (night,plans)=plans();val selected=TargetChoice.diverse(plans)
        assertEquals(3,selected.size);assertTrue(selected.map {it.target.type}.distinct().size>=2)
        for(a in selected)for(b in selected)if(a!==b)assertTrue(AstroPhoto.separation(a.target.eq,b.target.eq)>=2)
        assertTrue(plans.all {it.altitudes.size>=139 && it.altitudes.size==night.samples.size})
        assertTrue(plans.all {it.miniAltitudes.size>600 && it.miniAltitudes.size>it.altitudes.size})
        assertTrue(plans.all {it.altitudes.all(Double::isFinite)})
    }
    @Test fun sessionBlocksStayInsideWindowsDoNotOverlapAndWarnMeridian() {
        val (_,plans)=plans();val useful=plans.filter {it.usefulHours>2}.take(8);assertTrue(useful.isNotEmpty())
        val requests=useful.map {TargetRequest(it.target.id,30)}
        val plan=SessionPlanner.propose(requests,plans,30.0,5.0,Equipment(tracking=true,equatorial=true))
        assertTrue(plan.blocks.isNotEmpty());assertTrue(plan.blocks.zipWithNext().all {it.first.end<=it.second.start})
        for(b in plan.blocks){val p=plans.single {it.target.id==b.id};assertTrue(p.windows.any {b.start>=it.start && b.end<=it.end});assertEquals(b.frames*30.0,b.integrationSeconds,0.0)
            assertEquals(((b.frames*30+(b.frames-1)*5)*1000).toLong(),b.end-b.start)
            if(p.transit in b.start..b.end)assertTrue(b.warnings.any {it.contains("meridiano")})}
        val none=SessionPlanner.propose(requests,plans.map {it.copy(windows=emptyList())},30.0,5.0,Equipment())
        assertEquals(requests.size,none.unscheduled.size);assertTrue(none.blocks.isEmpty())
        val session=ObservingSession("one","Noche",0,requests);assertEquals(listOf(session),SessionStore.decode(SessionStore.encode(listOf(session))))
        assertTrue(runCatching {TargetRequest("M111")}.isFailure)
    }
    @Test fun alarmsCrossingsKpExpirationAndPersistence() {
        val now=Utc.parse("2026-11-06T18:00Z");val eq=Astro.Eq(83.8,-5.4)
        val rise=AlarmPolicy.objectRise(now,eq,place,30.0)!!
        assertTrue(AstroPhoto.horizon(AstroPhoto.precess(eq,rise-MINUTE),rise-MINUTE,place).alt<30)
        assertTrue(AstroPhoto.horizon(AstroPhoto.precess(eq,rise+MINUTE),rise+MINUTE,place).alt>30)
        val memory=Memory();val store=AlarmStore(memory);val a=store.add(AlarmKind.OBJECT_ALTITUDE,"Objeto supera 30°",rise,true,"M42")
        assertEquals(listOf(a),AlarmStore(memory).read());assertFalse(AlarmPolicy.due(a,rise-1));assertTrue(AlarmPolicy.due(a,rise));assertFalse(AlarmPolicy.due(a,rise+31*MINUTE))
        assertFalse(AlarmPolicy.kpReached(null,now,5.0))
        val kp=KpData(now,listOf(KpSample(now,6.0,KpKind.OBSERVED)))
        assertTrue(AlarmPolicy.kpReached(kp,now,5.0));assertFalse(AlarmPolicy.kpReached(kp,now+4*HOUR,5.0))
        store.remove(a.id);assertTrue(store.read().isEmpty())
    }
}
