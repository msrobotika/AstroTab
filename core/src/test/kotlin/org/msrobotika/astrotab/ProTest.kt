package org.msrobotika.astrotab

import org.junit.Assert.*
import org.junit.Test
import org.msrobotika.astrotab.astro.*
import org.msrobotika.astrotab.catalog.MessierCatalog
import org.msrobotika.astrotab.data.*
import java.nio.file.Files
import java.util.*
import kotlin.math.*

class ProTest {
    private val now=Utc.parse("2026-11-06T22:00:00Z")
    private val place=Location(40.4168,-3.7038,"manual",0)
    private val zone=TimeZone.getTimeZone("Europe/Madrid")
    private val catalog=MessierCatalog(javaClass.getResourceAsStream("/catalog/messier.tsv")!!)
    @Test fun fullPlannerBudgetAndCoarseAccuracy() {
        fun full(): List<ObjectPlan> {
            val night=AstroPhoto.timeline(now,zone,place)
            assertEquals(145,night.samples.size)
            assertTrue(AstroPhoto.events(night,place).size>=12)
            AstroPhoto.galactic(night,place)
            return catalog.objects.map { AstroPhoto.plan(it,night,place,30.0) }
        }
        repeat(4) { full() }
        val start=System.nanoTime(); val plans=full(); val elapsed=(System.nanoTime()-start)/1e6
        println("PRO_FULL_PLANNER_MS=$elapsed (110 objetos + malla + eventos, presupuesto <300 ms)")
        assertTrue("Plan completo: $elapsed ms",elapsed<300)
        val fine=AstroPhoto.timeline(now,zone,place,1)
        for(p in plans) {
            val reference=AstroPhoto.plan(p.target,fine,place,30.0)
            assertEquals(p.target.id,reference.usefulHours,p.usefulHours,0.035)
            assertEquals(reference.transit,p.transit)
            assertTrue(p.score in 0..100)
        }
    }
    @Test fun monthlyBudgetAndInterpolationEdges() {
        AstroPhoto.galacticMonths(2026,zone,place)
        val start=System.nanoTime(); val months=AstroPhoto.galacticMonths(2026,zone,place)
        val ms=(System.nanoTime()-start)/1e6
        println("PRO_GALACTIC_MONTHS_MS=$ms")
        assertTrue(ms<300); assertEquals(12,months.size)
        assertEquals(listOf(TimeSpan(50,150)),AstroPhoto.above(listOf(0L,100L,200L),doubleArrayOf(-1.0,1.0,-1.0),0.0))
        assertEquals(listOf(TimeSpan(75,100)),AstroPhoto.intersect(listOf(TimeSpan(50,100)),listOf(TimeSpan(75,150))))
    }
    @Test fun focusIndependentReferenceAndInfinity() {
        val e=Equipment(50.0,2.8)
        val f=ProTools.focus(e,10.0)
        assertEquals(0.0288444,f.cocMm,0.000001)
        assertEquals(31.004, f.hyperfocalMetres,0.02)
        assertEquals(7.57,f.nearMetres,0.02); assertEquals(14.73,f.farMetres!!,0.03)
        assertNull(ProTools.focus(e,100.0).farMetres)
        val atHyperfocal=ProTools.focus(e,f.hyperfocalMetres)
        assertNull(atHyperfocal.farMetres); assertEquals(f.hyperfocalMetres/2,atHyperfocal.nearMetres,1e-9)
        assertTrue(ProTools.focus(e.copy(sensor=SensorFormat.MFT),10.0).hyperfocalMetres>f.hyperfocalMetres)
        assertThrows(IllegalArgumentException::class.java) { ProTools.focus(e,0.01) }
    }
    @Test fun timelapseTrailMosaicAndStackReferences() {
        assertEquals(ProTools.Timelapse(240,9.6,6000.0),ProTools.timelapse(15.0,3600.0,25.0,25.0))
        assertEquals(1,ProTools.timelapse(15.0,1.0,25.0,10.0).frames.toInt())
        assertEquals(360.0,ProTools.arc(86164.0905),1e-9)
        assertEquals(7200.0,ProTools.arcSeconds(ProTools.arc(7200.0)),1e-8)
        assertEquals(4,ProTools.mosaicAxis(100.0,40.0,50.0)); assertEquals(1,ProTools.mosaicAxis(20.0,40.0,30.0))
        val stack=ProTools.stack(100,30.0)
        assertEquals(10.0,stack.snrGain,0.0); assertEquals(3000.0,stack.integrationSeconds,0.0)
        assertEquals(20,stack.darks); assertEquals(30,stack.flats); assertEquals(50,stack.bias)
        assertThrows(IllegalArgumentException::class.java) { ProTools.timelapse(0.0,30.0,25.0,10.0) }
        assertThrows(IllegalArgumentException::class.java) { ProTools.mosaicAxis(100.0,20.0,100.0) }
        assertThrows(IllegalArgumentException::class.java) { ProTools.stack(0,10.0) }
        assertThrows(IllegalArgumentException::class.java) { ProTools.arc(Double.NaN) }
    }
    @Test fun expandedOpenMeteoSampleNullsRangesAndOneRequest() {
        val raw=javaClass.getResourceAsStream("/open-meteo-pro.json")!!.bufferedReader().use { it.readText() }
        val data=Parsers.clouds(raw,now,place); val row=data.hours.first()
        assertEquals(20.0,row.low!!,0.0); assertEquals(30.0,row.mid!!,0.0); assertEquals(40.0,row.high!!,0.0)
        assertEquals(15.0,row.rain!!,0.0); assertEquals(8.0,row.temperature!!,0.0); assertEquals(7.0,row.dewPoint!!,0.0)
        assertEquals(90.0,row.humidity!!,0.0); assertEquals(12.0,row.wind!!,0.0); assertEquals(22.0,row.gusts!!,0.0)
        assertEquals(18000.0,row.visibility!!,0.0); assertEquals(1015.0,row.pressure!!,0.0); assertEquals(150.0,row.jet!!,0.0)
        assertNull(data.hours[1].jet); assertNull(data.hours[1].rain)
        assertThrows(Exception::class.java) { Parsers.clouds(raw.replace("[15,null]","[101,null]"),now,place) }
        assertThrows(Exception::class.java) { Parsers.clouds(raw.replace("[150,null]","[]"),now,place) }
        var calls=0; val cache=MemoryCache()
        val repo=NetworkRepository(cache,Transport { url ->
            if(!url.contains("open-meteo")) throw java.io.IOException("sin NOAA en muestra")
            calls++; assertTrue(url.contains("forecast_days=7"))
            for(key in listOf("cloud_cover_low","cloud_cover_mid","cloud_cover_high","precipitation_probability","wind_gusts_10m","visibility","pressure_msl","wind_speed_250hPa")) assertTrue(url.contains(key))
            raw
        }) { now }
        repo.enabled=true; repeat(3) { repo.refresh(place) }; assertEquals(1,calls)
        val restart=NetworkRepository(cache,Transport { error("no red") }) { now }; restart.load(place)
        assertEquals(150.0,restart.clouds!!.hours.first().jet!!,0.0)
        assertNull(DataPolicy.weatherAt(restart.clouds,now,now,place,false))
        assertArrayEquals(doubleArrayOf(100.0/3,100.0/3,100.0/3),Forecast.layers(row.copy(low=100.0,mid=100.0,high=100.0)),1e-10)
        assertNull(Forecast.layers(row.copy(low=null)))
        assertEquals("alta",Forecast.turbulence(150.0)); assertEquals("baja",Forecast.turbulence(69.0)); assertEquals("moderada",Forecast.turbulence(70.0)); assertEquals("sin datos",Forecast.turbulence(null))
    }
    @Test fun sevenNightsNoInventedScoresCoverageMoonAndWeather() {
        val hours=(0..191).map { Cloud(now/HOUR*HOUR-12*HOUR+it*HOUR,0.0,10.0,60.0,0.0,0.0,rain=0.0,jet=50.0) }
        val data=CloudData(now,place.key,hours)
        val week=Forecast.week(now,zone,place,data,true)
        assertEquals(7,week.size); assertTrue(week.all { it.score!=null && it.score in 0..100 })
        assertEquals(week.maxOf { it.score!! },Forecast.best(week)!!.score)
        val bad=Forecast.week(now,zone,place,data.copy(hours=hours.map { it.copy(cover=100.0) }),true)
        assertTrue(bad.all { it.score==0 })
        assertTrue(Forecast.week(now,zone,place,data,false).all { it.score==null && it.clouds==null })
        assertTrue(Forecast.week(now,zone,place,data.copy(hours=emptyList()),true).all { it.score==null })
        assertTrue(Forecast.week(now,zone,place,data.copy(fetchedAt=now-4*HOUR),true).all { it.score==null })
        val partial=Forecast.week(now,zone,place,data.copy(hours=hours.take(20)),true)
        assertTrue(partial.any { it.score==null }); assertTrue(partial.filter { it.score==null }.all { it.coveredHours<it.darkHours })
        val polar=Forecast.week(Utc.parse("2026-06-21T12:00:00Z"),zone,place.copy(latitude=85.0),null,false)
        assertTrue(polar.all { it.darkHours==0.0 && it.score==null })
    }
    @Test fun zonePersistenceInvalidZoneAndDstCalendars() {
        val store=MemoryPreferences(); val s=Settings(store)
        assertNull(s.zoneId); assertEquals(TimeZone.getDefault().id,s.zone.id)
        s.zoneId="Atlantic/Canary"; assertEquals("Atlantic/Canary",Settings(store).zone.id)
        assertThrows(IllegalArgumentException::class.java) { s.zoneId="Inventada/Zone" }
        s.zoneId=null; assertNull(Settings(store).zoneId)
        val days=ProAstro.moonMonth(Utc.parse("2026-02-10T20:00:00Z"),zone,place)
        assertEquals(28,days.size); assertTrue(days.all { it.illumination in 0.0..1.0 && it.moonlessHours>=0 })
        assertTrue(days.any { it.illumination<0.02 }); assertTrue(days.any { it.illumination>0.98 })
        val march=ProAstro.moonMonth(Utc.parse("2026-03-01T18:00:00Z"),zone,place)
        assertEquals(31,march.size); assertTrue(march.zipWithNext().any { (a,b) -> b.time-a.time==23*HOUR })
        assertTrue(ProAstro.moonMonth(now,zone,place) { true }.isEmpty())
        assertTrue(Forecast.week(now,zone,place,null,false) { true }.isEmpty())
    }
    @Test fun planetsCrossingsTransitAndElongation() {
        val night=AstroPhoto.timeline(now,zone,place); val planets=ProAstro.planets(night,place)
        assertEquals(5,planets.size)
        planets.forEach { p ->
            assertEquals(night.samples.size,p.altitudes.size); assertTrue(p.magnitude.isFinite()); assertTrue(p.elongation in 0.0..180.0)
            for(cross in listOfNotNull(p.rise,p.set)) {
                assertTrue(cross in night.start..night.end)
                assertEquals(0.0,AstroPhoto.horizon(Astro.planet(p.index,Astro.dayNumber(cross)).eq,cross,place).alt,0.001)
            }
            val eq=Astro.planet(p.index,Astro.dayNumber(p.transit)).eq
            val angle=Astro.rev(Astro.gmst(Astro.dayNumber(p.transit))+place.longitude-eq.ra+180)-180
            assertEquals(0.0,angle,0.001)
            assertEquals(90-abs(place.latitude-eq.dec),p.maximum,0.001)
        }
        assertTrue(planets[0].elongation<30); assertTrue(planets[1].elongation<50)
    }
    @Test fun meteorDatesRadiantsAndMoonAreApproximateAndBounded() {
        val rows=ProAstro.meteors(now,zone,place)
        assertEquals(9,rows.size); assertEquals("Leónidas",rows.first().shower.name)
        assertTrue(rows.zipWithNext().all { (a,b) -> a.peak<=b.peak })
        assertTrue(rows.all { it.radiantAltitude in -90.0..90.0 && it.illumination in 0.0..1.0 && it.moonAltitude in -91.0..90.0 })
        assertEquals(150,rows.first { it.shower.name=="Gemínidas" }.shower.zhr)
    }
    @Test fun logPersistenceCsvEscapingAndZoneNoStoragePermission() {
        val dir=Files.createTempDirectory("pro-log").toFile(); val file=java.io.File(dir,"book.json")
        try {
            val entry=LogEntry(now,"M31, galaxia","50 mm \"equipo\"",20,30.0,"=SUM(A1)\nsegunda línea")
            val book=LogBook(file); assertTrue(book.read().isEmpty()); book.append(entry)
            assertEquals(listOf(entry),LogBook(file).read())
            book.append(entry.copy(target="M42")); assertEquals(2,book.read().size)
            val csv=LogBook.csv(book.read(),zone)
            assertTrue(csv.contains("2026-11-06 23:00:00 +01:00")); assertTrue(csv.contains("Europe/Madrid"))
            assertTrue(csv.contains("\"M31, galaxia\"")); assertTrue(csv.contains("\"\"equipo\"\"")); assertTrue(csv.contains("'=SUM(A1)\nsegunda línea"))
            assertTrue(csv.contains("600.0")); assertTrue(csv.startsWith("\uFEFF"))
            file.writeText("roto")
            assertThrows(Exception::class.java) { book.append(entry) }
        } finally { dir.deleteRecursively() }
    }
}
