package org.msrobotika.astrotab

import org.junit.Assert.*
import org.junit.Test
import org.msrobotika.astrotab.astro.*
import org.msrobotika.astrotab.catalog.Catalog
import org.msrobotika.astrotab.data.*
import org.msrobotika.astrotab.ui.*
import org.msrobotika.astrotab.kiosk.*
import java.io.*
import java.util.TimeZone
import javax.net.ssl.SSLHandshakeException

class MemoryPreferences : PreferenceStore {
    val values=mutableMapOf<String,String>()
    override fun get(key: String)=values[key]
    override fun put(values: Map<String,String?>) { values.forEach { (k,v) -> if(v==null) this.values.remove(k) else this.values[k]=v } }
}
class MemoryCache : CacheStore {
    val values=mutableMapOf<String,Cached>()
    override fun read(key: String)=values[key]
    override fun write(key: String,value: Cached) { values[key]=value }
}
class AstroTest {
    // Valores y tolerancias originales de selfTest, ejemplos de Astronomical Algorithms (Meeus).
    private val sun=Astro.sun(Astro.dayNumber(718934400000L))
    private val moon=Astro.moon(Astro.dayNumber(703036800000L))
    private val venus=Astro.planet(1,Astro.dayNumber(724809600000L))
    private val horizon=Astro.toHorizon(347.3193375,-6.719892,Astro.rev(Astro.gmst(Astro.dayNumber(545080860000L))-77.06556),38.92139)
    @Test fun meeusSunRa() { assertEquals(198.38083,sun.ra,0.05) }
    @Test fun meeusSunDec() { assertEquals(-7.78507,sun.dec,0.05) }
    @Test fun meeusMoonRa() { assertEquals(134.68847,moon.eq.ra,0.15) }
    @Test fun meeusMoonDec() { assertEquals(13.76837,moon.eq.dec,0.15) }
    @Test fun meeusMoonDistance() { assertEquals(368409.7,moon.eq.dist*6378.14,1500.0) }
    @Test fun meeusVenusRa() { assertEquals(316.17273,venus.eq.ra,0.05) }
    @Test fun meeusVenusDec() { assertEquals(-18.88801,venus.eq.dec,0.05) }
    @Test fun meeusGmst() { assertEquals(197.693195,Astro.gmst(Astro.dayNumber(545011200000L)),0.05) }
    @Test fun meeusAltitude() { assertEquals(15.1249,horizon.alt,0.1) }
    @Test fun meeusAzimuth() { assertEquals(248.0337,horizon.az,0.1) }
    @Test fun epochsAndMidnight() {
        assertEquals(0.0,Astro.dayNumber(946598400000),1e-12)
        val a=Utc.parse("2026-11-06T23:59:00Z"); val b=Utc.parse("2026-11-07T00:01:00Z")
        assertEquals(120000,b-a); assertEquals(1.0/720,Astro.dayNumber(b)-Astro.dayNumber(a),1e-9)
        assertTrue(Astro.gmst(Astro.dayNumber(Utc.parse("1900-01-01T12:00:00Z"))) in 0.0..360.0)
    }
    @Test fun realMoonPhasesAndEvents() {
        val new=Utc.parse("2024-04-08T18:21:00Z"); val full=Utc.parse("2024-03-25T07:00:00Z")
        assertTrue(Astro.moonPhase(Astro.dayNumber(new)).illumination<0.001)
        assertTrue(Astro.moonPhase(Astro.dayNumber(full)).illumination>0.999)
        val next=Astro.nextPhase(full,0.0)!!
        assertTrue(kotlin.math.abs(next-new)<2*HOUR)
        assertEquals("Cuarto creciente",Astro.phaseName(90.0))
    }
    @Test fun hemispheresAndNoCrossing() {
        fun alt(time: Long,lat: Double)=Astro.toHorizon(0.0,80.0,Astro.gmst(Astro.dayNumber(time)),lat).alt
        val now=Utc.parse("2026-11-06T00:00:00Z")
        assertNull(Astro.nextCross(now,26,0.0,true) { alt(it,60.0) })
        assertNull(Astro.nextCross(now,26,0.0,false) { alt(it,-60.0) })
        assertTrue(alt(now,60.0)>0); assertTrue(alt(now,-60.0)<0)
        val cross=Astro.nextCross(0,2,0.0,true) { it/3600000.0-0.5 }!!
        assertTrue(kotlin.math.abs(HOUR/2-cross)<30)
    }
    @Test fun lunarParallaxAndAllPlanets() {
        val d=Astro.dayNumber(Utc.parse("2026-11-06T00:00:00Z")); val m=Astro.moon(d)
        val geo=Astro.toHorizon(m.eq.ra,m.eq.dec,Astro.gmst(d),40.0)
        val topo=Astro.moonHorizon(d,Astro.gmst(d),40.0)
        assertTrue(geo.alt>=topo.alt); assertTrue(geo.alt-topo.alt<1.1)
        for(p in 0..4) { val planet=Astro.planet(p,d); assertTrue(planet.eq.ra in 0.0..360.0); assertTrue(planet.eq.dist>0); assertTrue(planet.magnitude.isFinite()) }
    }
}
class CatalogTest {
    private fun catalog()=Catalog(javaClass.getResourceAsStream("/catalog/stars.bin")!!)
    @Test fun completeCatalogAndUnits() {
        val c=catalog(); assertEquals(5044,c.count); assertEquals(893,c.pointsRa.size); assertEquals(150,c.polylines.size)
        assertEquals(89,c.labels.size); assertEquals(89,c.constellations.size); assertEquals(156,c.names.size)
        assertEquals("Sirio",c.name(0)); assertEquals(-1.4,c.magnitude[0],1e-10)
        assertEquals(101.288,c.ra[0],0.004); assertEquals(-16.72,c.dec[0],0.001)
        assertEquals("Altair",c.name(11)); assertEquals("Estrella Polar",c.names[46])
        assertEquals(6.0,c.magnitude.maxOrNull()!!,0.0)
        assertEquals(2,c.constellations.count { it=="Serpiente" })
        assertTrue(c.polylines.all { it.first+it.count<=c.pointsRa.size })
    }
    @Test fun countsAgainstHorizonAndBortle() {
        val c=catalog(); val lat=40.4; val lst=120.0
        val north=c.visibleCount(6.0,lst,lat); val antipodal=c.visibleCount(6.0,lst+180,-lat)
        assertEquals(5044,north+antipodal)
        assertTrue(c.visibleCount(3.8,lst,lat)<north)
        assertEquals(north,c.visibleCount(Astro.limitingMag(1),lst,lat))
        val counts=(1..9).map { c.visibleCount(Astro.limitingMag(it),lst,lat) }
        assertTrue(counts.zipWithNext().all { (a,b)->a>=b })
    }
    @Test fun truncatedCatalogRejected() {
        val bad=javaClass.getResourceAsStream("/catalog/stars.bin")!!.readBytes().copyOf(100)
        assertThrows(Exception::class.java) { Catalog(ByteArrayInputStream(bad)) }
    }
}
class SettingsGeometryTest {
    @Test fun settingsRestartOfflineAndInvalidInput() {
        val store=MemoryPreferences(); val settings=Settings(store)
        assertNull(settings.location); assertFalse(settings.networkEnabled)
        settings.location=Coordinates.parse("40,4168","−3,7038",100)
        settings.bortle=9; settings.red=true
        val restart=Settings(store)
        assertEquals(-3.7038,restart.location!!.longitude,1e-10); assertEquals(100L,restart.location!!.savedAt)
        assertEquals(9,restart.bortle); assertTrue(restart.red); assertFalse(restart.networkEnabled)
        for(pair in listOf("" to "0","NaN" to "0","91" to "0","0" to "181","1,2,3" to "0","Infinity" to "0")) assertNull(Coordinates.parse(pair.first,pair.second,0))
        assertNotNull(Coordinates.parse("-90","180",0))
        assertThrows(IllegalArgumentException::class.java) { settings.bortle=0 }
    }
    @Test fun geometryWrapAndNearestObject() {
        assertEquals(500f,SkyGeometry.x(0.0,360.0,1000),0.01f)
        assertEquals(508f,SkyGeometry.x(1.0,0.0,1000),0.01f)
        assertEquals(200f,SkyGeometry.y(35.0,35.0,400),0.01f)
        val xs=floatArrayOf(100f,110f,Float.NaN); val ys=floatArrayOf(100f,100f,Float.NaN)
        assertEquals(1,SkyGeometry.nearest(xs,ys,3,109f,102f,32f))
        assertEquals(-1,SkyGeometry.nearest(xs,ys,3,200f,200f,32f))
    }
    @Test fun accelerationDoesNotChangeBearingAndRemapsRotation() {
        val orientation=Orientation(); orientation.reference(270.0)
        repeat(50) { orientation.acceleration(0f,9.8f,2f,1) }
        assertEquals(270.0,orientation.bearing,0.0)
        val a=Orientation(); val b=Orientation()
        a.acceleration(-5f,4f,7f,0); b.acceleration(4f,5f,7f,1)
        assertEquals(a.elevation,b.elevation,1e-9); assertEquals(a.roll,b.roll,1e-9)
        val old=orientation.elevation; orientation.drag(80f,80f,1000,400,true)
        assertEquals(old,orientation.elevation,0.0); assertTrue(orientation.bearing!=270.0)
        orientation.drag(0f,20f,1000,400,false); assertTrue(orientation.elevation>old)
    }
    @Test fun sensorLifecyclePolicy() {
        assertTrue(SensorPolicy.active(true,true,true,true))
        for(i in 0..3) { val states=BooleanArray(4) { it!=i }; assertFalse(SensorPolicy.active(states[0],states[1],states[2],states[3])) }
    }
    @Test fun nightStartUsesSystemZoneAndDst() {
        val zone=TimeZone.getTimeZone("Europe/Madrid")
        assertEquals(Utc.parse("2026-10-24T16:00:00Z"),Utc.nightStart(Utc.parse("2026-10-25T02:00:00Z"),zone))
        assertEquals(Utc.parse("2026-10-25T17:00:00Z"),Utc.nightStart(Utc.parse("2026-10-25T12:00:00Z"),zone))
    }
}
class NetworkTest {
    private val now=Utc.parse("2026-11-06T20:00:00Z")
    private val location=Location(40.0,-3.0,"manual",now)
    private val cloudRaw="""{"utc_offset_seconds":0,"hourly":{"time":["2026-11-06T21:00","2026-11-06T20:00","2026-11-06T22:00"],"cloud_cover":[25,10,null]}}"""
    private val observedRaw="""[["time_tag","Kp","a_running","station_count"],["2026-11-06 18:00:00.000","5.67","0","8"],["2026-11-06 15:00:00.000","3","0","8"]]"""
    private val forecastRaw="""[["time_tag","kp","observed","noaa_scale"],["2026-11-06 21:00:00","6.33","predicted","G2"],["2026-11-06 18:00:00","5.67","estimated",null],["2026-11-06 15:00:00","3.00","observed",null]]"""
    @Test fun parseCloudsUtcOrderAndMissingHours() {
        val data=Parsers.clouds(cloudRaw,now,location)
        assertEquals(now,data.hours.first().time); assertEquals(10.0,DataPolicy.cloudAt(data,now,now,location)!!,0.0)
        assertNull(DataPolicy.cloudAt(data,now+2*HOUR,now,location))
        assertNull(DataPolicy.cloudAt(data,now,now,location.copy(latitude=41.0)))
        assertThrows(Exception::class.java) { Parsers.clouds(cloudRaw.replace("25","101"),now,location) }
        assertThrows(IllegalArgumentException::class.java) { Utc.parse("2026-02-30T12:00") }
    }
    @Test fun kpObjectFormat2026() {
        val o=Parsers.kp("""[{"time_tag":"2026-10-30T00:00:00","Kp":1.67,"a_running":6,"station_count":8},{"time_tag":"2026-10-30T03:00:00","Kp":2.33,"a_running":9,"station_count":8}]""",now,false)
        assertEquals(listOf(1.67,2.33),o.samples.map { it.value })
        val f=Parsers.kp("""[{"time_tag":"2026-10-30T00:00:00","kp":1.67,"observed":"observed","noaa_scale":null},{"time_tag":"2026-11-07T00:00:00","kp":4.0,"observed":"predicted","noaa_scale":null}]""",now,true)
        assertEquals(listOf(KpKind.OBSERVED,KpKind.PREDICTED),f.samples.map { it.kind })
    }
    @Test fun kpTypesOrderAndPeriodCoverage() {
        val obs=Parsers.kp(observedRaw,now,false); val fore=Parsers.kp(forecastRaw,now,true)
        assertEquals(KpKind.OBSERVED,obs.samples.last().kind)
        assertEquals(listOf(KpKind.OBSERVED,KpKind.ESTIMATED,KpKind.PREDICTED),fore.samples.map { it.kind })
        assertEquals(5.67,DataPolicy.currentKp(obs,now)!!.value,0.0)
        assertNull(DataPolicy.currentKp(obs,now+HOUR))
        assertNull(DataPolicy.currentKp(KpData(now,listOf(KpSample(now,9.0,KpKind.PREDICTED))),now))
    }
    @Test fun cacheFreshnessBoundariesAndFutureDates() {
        assertEquals(Freshness.MISSING,DataPolicy.freshness(null,now))
        assertEquals(Freshness.FRESH,DataPolicy.freshness(now-HOUR+1,now))
        assertEquals(Freshness.STALE,DataPolicy.freshness(now-HOUR,now))
        assertEquals(Freshness.EXPIRED,DataPolicy.freshness(now-3*HOUR,now))
        assertEquals(Freshness.EXPIRED,DataPolicy.freshness(now+HOUR,now))
        assertNull(DataPolicy.cloudAt(Parsers.clouds(cloudRaw,now-3*HOUR,location),now,now,location))
    }
    @Test fun networkOffThrottleRestartAndLocationIsolation() {
        val cache=MemoryCache(); var calls=0; var time=now
        val transport=Transport { url -> calls++; when { url.contains("open-meteo") -> cloudRaw; url.contains("forecast.json") -> forecastRaw; else -> observedRaw } }
        val repo=NetworkRepository(cache,transport) { time }
        repo.refresh(location); assertEquals(0,calls)
        repo.enabled=true; repo.refresh(location); assertEquals(3,calls)
        repeat(30) { repo.refresh(location) }; assertEquals(3,calls)
        val restart=NetworkRepository(cache,transport) { now }; restart.load(location)
        assertNotNull(restart.clouds); assertNotNull(restart.observed); assertFalse(restart.enabled)
        restart.enabled=true; restart.refresh(location); assertEquals(3,calls)
        restart.load(location.copy(longitude=2.0)); assertNull(restart.clouds)
        time+=30*MINUTE; repo.refresh(location); assertEquals(6,calls)
        repo.enabled=false; repo.refresh(location); assertEquals(6,calls)
    }
    @Test fun tlsFailureAndDisconnectRetainCache() {
        val cache=MemoryCache(); cache.write("observed",Cached(now,observedRaw))
        val repo=NetworkRepository(cache,Transport { throw SSLHandshakeException("Certificado inválido simulado") }) { now }
        repo.load(location); repo.enabled=true; repo.refresh(location)
        assertNotNull(repo.observed); assertTrue(repo.status.contains("No se ha podido"))
        val disconnect=NetworkRepository(cache,Transport { throw IOException("Sin conexión simulada") }) { now }
        disconnect.load(location); disconnect.enabled=true; disconnect.refresh(location); assertNotNull(disconnect.observed)
    }
    @Test fun payloadLimitAndHttpsOnly() {
        assertEquals("{}",HttpsTransport.readBounded(ByteArrayInputStream("{}".toByteArray())))
        assertThrows(IllegalArgumentException::class.java) { HttpsTransport.readBounded(ByteArrayInputStream(ByteArray(HttpsTransport.MAX_BYTES+1))) }
        assertThrows(IllegalArgumentException::class.java) { HttpsTransport().get("http://example.invalid") }
    }
    @Test fun ipRequiresExplicitActionAndValidCoordinates() {
        var calls=0; val repo=NetworkRepository(MemoryCache(),Transport { calls++; """{"latitude":40.4,"longitude":-3.7}""" }) { now }
        assertThrows(IllegalStateException::class.java) { repo.locateByIp(true) }
        repo.enabled=true; assertThrows(IllegalStateException::class.java) { repo.locateByIp(false) }; assertEquals(0,calls)
        assertEquals("IP aproximada · ipapi.co",repo.locateByIp(true).origin); assertEquals(1,calls)
        assertThrows(Exception::class.java) { Parsers.ip("""{"error":true}""",now) }
    }
    @Test fun fileCacheRestartAndCorruption() {
        val directory=java.nio.file.Files.createTempDirectory("astrotab-cache").toFile()
        try {
            FileCache(directory).write("observed",Cached(now,observedRaw))
            assertEquals(Cached(now,observedRaw),FileCache(directory).read("observed"))
            FileCache(directory).write("observed",Cached(now+HOUR,observedRaw))
            assertEquals(now+HOUR,FileCache(directory).read("observed")!!.fetchedAt)
            File(directory,"observed").writeBytes(byteArrayOf(1,2,3)); assertNull(FileCache(directory).read("observed"))
        } finally { directory.listFiles()?.forEach { it.delete() }; directory.delete() }
    }
}
class NightAuroraKioskTest {
    @Test fun exactNightFactorsAndBortle() {
        val clear=Night.factors(0,0.0,-20.0,-5.0,1.0,20.0)
        assertEquals(1.0,clear.quality!!,1e-12); assertTrue(clear.galacticVisible)
        val full=Night.factors(HOUR,0.0,-20.0,30.0,1.0,20.0)
        assertEquals(0.2,full.quality!!,1e-12)
        assertEquals(6,Night.bestWindow(listOf(clear,full),0,1)!!.score)
        assertEquals(3,Night.bestWindow(listOf(clear,full),0,9)!!.score)
        assertNull(Night.factors(0,null,-20.0,-5.0,0.0,20.0).quality)
        assertEquals(0.5,Night.factors(0,0.0,-15.0,-5.0,0.0,20.0).quality!!,0.0)
    }
    @Test fun noPastWindowNoGapNoMissingCloud() {
        fun hour(t: Long,c: Double?=0.0)=Night.factors(t,c,-20.0,-5.0,0.0,20.0)
        assertNull(Night.bestWindow(listOf(hour(0),hour(HOUR)),1,1))
        assertNull(Night.bestWindow(listOf(hour(0),hour(2*HOUR)),0,1))
        assertNull(Night.bestWindow(listOf(hour(0),hour(HOUR,null)),0,1))
        assertEquals(2*HOUR,Night.bestWindow(listOf(hour(0),hour(HOUR),hour(2*HOUR),hour(3*HOUR)),HOUR+1,1)!!.start)
    }
    @Test fun polarNightAndMidnightSun() {
        val place=Location(89.0,0.0,"manual",0)
        val winter=Utc.parse("2026-12-21T00:00:00Z"); val summer=Utc.parse("2026-06-21T00:00:00Z")
        val dark=(0..23).map { Night.hour(winter+it*HOUR,winter,place,null) }
        val light=(0..23).map { Night.hour(summer+it*HOUR,summer,place,null) }
        assertTrue(Night.darknessLabel(dark).contains("todo el intervalo"))
        assertTrue(Night.darknessLabel(light).startsWith("Sin noche"))
        assertNull(Night.bestWindow(dark,winter,1))
    }
    @Test fun kpBothHemispheresOutsideScaleAndStale() {
        assertEquals(Astro.kpNeeded(55.0),Astro.kpNeeded(-55.0),0.0)
        assertEquals(80.8,Astro.geomagLat(90.0,0.0),1e-9)
        assertEquals(-80.8,Astro.geomagLat(-90.0,0.0),1e-9)
        val equator=Location(0.0,0.0,"manual",0); val north=Location(65.0,-72.6,"manual",0)
        val data=KpData(0,listOf(KpSample(0,9.0,KpKind.OBSERVED)))
        assertTrue(Aurora.threshold(0.0,0.0)>9); assertFalse(Aurora.currentAlert(data,0,equator))
        assertTrue(Aurora.currentAlert(data,0,north)); assertFalse(Aurora.currentAlert(data,3*HOUR,north))
        assertTrue(Aurora.currentAlert(data,HOUR,north))
    }
    @Test fun pinPersistenceThrottleAndKioskPolicy() {
        val store=MemoryPreferences(); var now=1000L; val gate=PinGate(store) { now }
        assertFalse(gate.configured); assertFalse(gate.verify("123456"))
        assertThrows(IllegalArgumentException::class.java) { gate.create("1234") }
        gate.create("839201"); assertTrue(gate.configured); assertFalse(store.values.values.contains("839201"))
        assertTrue(PinGate(store) { now }.verify("839201"))
        repeat(5) { assertFalse(gate.verify("000000")) }; assertFalse(gate.verify("839201"))
        now+=30001; assertTrue(gate.verify("839201"))
        assertThrows(IllegalStateException::class.java) { gate.create("111111") }
        assertTrue(KioskPolicy.shouldLock(true,true,true,true,false))
        assertFalse(KioskPolicy.shouldLock(false,true,true,true,false))
        assertFalse(KioskPolicy.shouldLock(true,false,true,true,false))
        assertFalse(KioskPolicy.shouldLock(true,true,true,false,false))
        assertFalse(KioskPolicy.shouldLock(true,true,true,true,true))
        assertFalse(KioskPolicy.mayExit(false)); assertTrue(KioskPolicy.mayExit(true))
    }
}

