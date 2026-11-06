package org.msrobotika.astrotab

import org.junit.Assert.*
import org.junit.Test
import org.msrobotika.astrotab.satellites.*
import org.msrobotika.astrotab.astro.*
import org.msrobotika.astrotab.catalog.*
import org.msrobotika.astrotab.data.*
import java.util.TimeZone
import java.io.File
import kotlin.math.abs

class PolishTest {
    private fun resource(name: String)=javaClass.getResourceAsStream("/sgp4/$name")!!.bufferedReader().use {it.readText()}
    private val place=Location(40.4168,-3.7038,"manual",0)
    private class Memory: PreferenceStore {
        val data=mutableMapOf<String,String>()
        override fun get(key: String)=data[key]
        override fun put(values: Map<String,String?>){values.forEach {(k,v)->if(v==null)data.remove(k)else data[k]=v}}
    }
    @Test fun visibleHeightFilterAndChronologicalOrderUseVisiblePart() {
        val tle=TleParser.parse(resource("iss.tle")).single()
        fun pass(time: Long,alt: Double,visible: Boolean=true): SatellitePass {
            val q=SatellitePoint(time,alt,90.0,visible,true)
            return SatellitePass(tle,q,q,q,listOf(q))
        }
        val passes=listOf(pass(50,30.0),pass(10,9.9),pass(40,20.0),pass(20,10.0),pass(30,85.0,false))
        assertEquals(listOf(20L,40L,50L),SatellitePassPolicy.select(passes,0).map {it.start.time})
        assertEquals(listOf(40L,50L),SatellitePassPolicy.select(passes,0,20).map {it.start.time})
        assertEquals(listOf(50L),SatellitePassPolicy.select(passes,0,30).map {it.start.time})
        assertTrue(SatellitePassPolicy.select(passes,50).isEmpty())
        val shadowPeak=passes.first().copy(maximum=SatellitePoint(51,80.0,90.0,false,true))
        assertEquals(30.0,SatellitePassPolicy.visiblePeak(shadowPeak)!!.altitude,0.0)
    }
    @Test fun cacheReusesIdentityAndInvalidatesTlePlaceAndTime() {
        val tle=TleParser.parse(resource("iss.tle")).single();val tles=listOf(tle)
        val passes=SatellitePredictor.predict(tles,tle.epoch,place,days=1)
        val cache=SatellitePredictionCache();assertNull(cache.get(tles,place,tle.epoch))
        cache.put(tles,place,tle.epoch,passes)
        assertSame(passes,cache.get(tles,place.copy(origin="ip",savedAt=123),tle.epoch+HOUR))
        assertNull(cache.get(tles,place.copy(latitude=41.0),tle.epoch+HOUR))
        assertNull(cache.get(listOf(tle.copy(line1=tle.line1.replace("9998","9988"))),place,tle.epoch+HOUR))
        assertNull(cache.get(tles,place,tle.epoch+24*HOUR));assertNull(cache.get(tles,place,tle.epoch-1))
    }
    @Test fun issIsPublishedFirstAndCompletePassHasShadowTransition() {
        val iss=TleParser.parse(resource("iss.tle")).single()
        val group=TleParser.parse(resource("visual.tle")).filter {it.number!=25544}.take(155)
        val start=System.nanoTime();var firstMs=0.0;var first=true
        val passes=SatellitePredictor.predict(group+iss,iss.epoch,place,partial={done,result ->
            if(first){first=false;firstMs=(System.nanoTime()-start)/1e6;assertEquals(1,done);assertTrue(result.isNotEmpty());assertTrue(result.all {it.tle.number==25544})}
        })
        val totalMs=(System.nanoTime()-start)/1e6
        val shadow=passes.first {it.track.any {q->q.visible} && it.track.last().illuminated.not()}
        assertTrue(shadow.track.any {it.visible});assertTrue(shadow.start.time<shadow.end.time)
        assertTrue(abs(shadow.start.altitude)<0.1 || shadow.start.time==iss.epoch)
        assertTrue(abs(shadow.end.altitude)<0.1 || shadow.end.time==iss.epoch+5*24*HOUR)
        assertTrue(passes.zipWithNext().all {it.first.start.time<=it.second.start.time})
        File("build/test-artifacts/benchmark.txt").apply { parentFile?.mkdirs() }.writeText("JVM PC, ${group.size+1} satélites × 5 días: primera publicación ISS $firstMs ms; lista completa $totalMs ms.\nSin extrapolación a tablet: los objetivos físicos <2 s / <15 s siguen pendientes.\n")
        println("PULIDO ISS=${firstMs}ms total=${totalMs}ms satellites=${group.size+1}")
    }
    @Test fun coarseDetectionRetainsEveryHighVisiblePassOfIndependentTenSecondScan() {
        val iss=TleParser.parse(resource("iss.tle")).single()
        val tles=(listOf(iss)+TleParser.parse(resource("visual.tle")).take(5)).distinctBy {it.number}
        val passes=SatellitePredictor.predict(tles,iss.epoch,place,days=1)
        for(tle in tles) {
            val sgp=Sgp4(tle);var t=iss.epoch
            while(t<=iss.epoch+24*HOUR) {
                val observer=SatelliteGeometry.observer(t,place)
                if(observer.sunAltitude< -6)sgp.at(t)?.let {SatelliteGeometry.point(it,t,observer)}?.let {q ->
                    if(q.visible && q.altitude>=10)assertTrue("${tle.name} visible at $t",passes.any {it.tle.number==tle.number && t in it.start.time..it.end.time})
                }
                t+=10000
            }
        }
    }
    @Test fun impossibleInclinationCullsOnlyUnreachableLatitudes() {
        val iss=TleParser.parse(resource("iss.tle")).single()
        assertTrue(SatellitePredictor.canReach(iss,place))
        assertFalse(SatellitePredictor.canReach(iss,place.copy(latitude=89.0)))
    }
    @Test fun automaticTleRefreshHonoursOneDayAndNetworkAndBackoff() {
        val tle=TleParser.parse(resource("iss.tle")).single();var calls=0
        val data=mutableMapOf("satellites" to Cached(tle.epoch,resource("iss.tle")))
        val cache=object: CacheStore {override fun read(key: String)=data[key];override fun write(key: String,value: Cached){data[key]=value}}
        val r=SatelliteRepository(cache,Transport {calls++;resource("iss.tle")});r.load()
        r.refreshIfNeeded({false},tle.epoch+25*HOUR);assertEquals(0,calls)
        r.refreshIfNeeded({true},tle.epoch+23*HOUR);assertEquals(0,calls)
        r.refreshIfNeeded({true},tle.epoch+25*HOUR);assertEquals(2,calls)
        r.refreshIfNeeded({true},tle.epoch+25*HOUR+MINUTE);assertEquals(2,calls)
    }
    @Test fun alarmsEnabledStateSurvivesFreshStoreAndMigratesLegacy() {
        val memory=Memory();val store=AlarmStore(memory)
        val a=store.add(AlarmKind.MOONRISE,"Luna",10000)
        store.setEnabled(a.id,false)
        val restart=AlarmStore(memory);assertFalse(restart.read().single().enabled)
        assertFalse(AlarmPolicy.due(restart.read().single(),10000))
        restart.setEnabled(a.id,true);assertTrue(AlarmStore(memory).read().single().enabled)
        val legacy=org.json.JSONArray(memory.data.getValue("alarms"));legacy.getJSONObject(0).remove("enabled")
        memory.data["alarms"]=legacy.toString()
        assertTrue(AlarmStore(memory).read().single().enabled)
    }
    @Test fun automaticPlanContainsThreeDiverseCompleteNonOverlappingObjects() {
        val catalog=MessierCatalog(javaClass.getResourceAsStream("/catalog/messier.tsv")!!)
        val zone=TimeZone.getTimeZone("Europe/Madrid")
        for(date in listOf("2026-11-06T18:00Z","2027-03-20T18:00Z","2027-07-01T18:00Z")) {
            val night=AstroPhoto.timeline(Utc.parse(date),zone,place)
            val plans=catalog.objects.map {AstroPhoto.plan(it,night,place,30.0)}
            val equipment=Equipment(tracking=true,equatorial=true)
            val requests=SessionPlanner.automatic(plans,30.0,5.0,equipment)
            assertEquals(date,3,requests.size)
            assertTrue(requests.map {r ->plans.first {it.target.id==r.id}.target.type}.distinct().size>=2)
            val result=SessionPlanner.propose(requests,plans,30.0,5.0,equipment)
            assertTrue(result.unscheduled.isEmpty());assertEquals(3,result.blocks.size)
            assertTrue(result.blocks.zipWithNext().all {it.first.end<=it.second.start})
            result.blocks.forEach {b -> assertTrue(b.integrationSeconds>=1800);assertTrue(plans.first {it.target.id==b.id}.windows.any {b.start>=it.start && b.end<=it.end})}
            assertTrue(SessionPlanner.automatic(plans.map {it.copy(windows=emptyList())},30.0,5.0,equipment).isEmpty())
        }
    }
}
