package org.msrobotika.astrotab

import org.junit.Assert.*
import org.junit.Test
import org.msrobotika.astrotab.astro.*
import org.msrobotika.astrotab.catalog.*
import org.msrobotika.astrotab.data.*
import java.util.TimeZone
import kotlin.math.*

class AstroPhotoTest {
    private val catalog=MessierCatalog(javaClass.getResourceAsStream("/catalog/messier.tsv")!!)
    private val place=Location(40.4,-3.7,"manual",0)
    private val zone=TimeZone.getTimeZone("Europe/Madrid")
    private val now=Utc.parse("2026-11-06T22:00:00Z")
    @Test fun completeMessierWithIndependentJ2000References() {
        assertEquals(110,catalog.objects.size)
        // Coordenadas de referencia redondeadas de las posiciones J2000 convencionales.
        for((id,ra,dec) in listOf(Triple("M31",10.6847,41.2688),Triple("M42",83.8221,-5.3911),Triple("M13",250.4235,36.4613))) {
            val o=catalog.objects.first { it.id==id }; assertEquals(ra,o.ra,0.1); assertEquals(dec,o.dec,0.1)
        }
        assertTrue(catalog.objects.all { it.constellation==null })
        assertEquals("M31",catalog.find("andromeda").single().id)
        assertTrue(catalog.find("42").any { it.id=="M42" })
        assertTrue(catalog.find("","Galaxia").all { it.group=="Galaxia" })
        assertEquals(110,listOf("Galaxia","Nebulosa","Cúmulo","Otros").sumOf { catalog.find("",it).size })
    }
    @Test fun precessionIdentityAndMeeusReference() {
        val j2000=Utc.parse("2000-01-01T12:00:00Z")
        val e=AstroPhoto.precess(Astro.Eq(41.054063,49.22775),j2000)
        assertEquals(41.054063,e.ra,1e-8); assertEquals(49.22775,e.dec,1e-8)
        // Meeus ejemplo 21.a, θ Persei sin movimiento propio, 2028-11-13,19.
        val p=AstroPhoto.precess(Astro.Eq(41.0499417,49.2284667),Utc.parse("2028-11-13T04:33:36Z"))
        assertEquals(41.5431,p.ra,0.003); assertEquals(49.3492,p.dec,0.003)
    }
    @Test fun dstNightsHaveRealDurationsAndMonotonicSamples() {
        for((date,hours) in listOf("2026-03-28T20:00:00Z" to 23,"2026-10-24T20:00:00Z" to 25)) {
            val night=AstroPhoto.timeline(Utc.parse(date),zone,place)
            assertEquals(hours*HOUR,night.end-night.start)
            assertTrue(night.samples.zipWithNext().all { (a,b) -> b.time-a.time==10*MINUTE })
            val plan=AstroPhoto.plan(catalog.objects.first(),night,place,30.0)
            assertTrue(plan.usefulHours in 0.0..hours.toDouble())
        }
    }
    @Test fun circumpolarNeverRisingAndNoAstronomicalNight() {
        val north=place.copy(latitude=70.0,longitude=0.0)
        val night=AstroPhoto.timeline(Utc.parse("2026-12-21T20:00:00Z"),TimeZone.getTimeZone("UTC"),north)
        val circ=catalog.objects.first().copy(ra=0.0,dec=89.0)
        val plan=AstroPhoto.plan(circ,night,north,30.0)
        assertTrue(plan.altitudes.all { it>60 }); assertTrue(plan.usefulHours>0); assertTrue(plan.score>0)
        val below=AstroPhoto.plan(circ.copy(dec=-89.0),night,north,0.0)
        assertEquals(0.0,below.usefulHours,0.0); assertEquals(0,below.score); assertTrue(below.altitudes.all { it<0 })
        val summer=AstroPhoto.timeline(Utc.parse("2026-06-21T20:00:00Z"),zone,north)
        assertTrue(summer.dark.isEmpty()); assertTrue(summer.moonless.isEmpty())
        assertEquals(0,AstroPhoto.plan(circ,summer,north,0.0).score)
    }
    @Test fun usefulDurationAndScoreFactorsCanBeReconstructed() {
        val night=AstroPhoto.timeline(now,zone,place)
        for(target in catalog.objects) {
            val p=AstroPhoto.plan(target,night,place,30.0)
            val expected=p.windows.sumOf { it.hours }
            assertEquals(expected,p.usefulHours,1e-10)
            assertEquals((100*p.timeFactor*p.altitudeFactor*p.moonFactor).roundToInt(),p.score)
            assertTrue(p.score in 0..100)
            val h=AstroPhoto.horizon(AstroPhoto.precess(target.eq,night.start),p.transit,place)
            assertEquals(p.transitAltitude,h.alt,0.001)
        }
    }
    @Test fun rule500AndNpfReferenceAndPoleLimit() {
        val e=Equipment(50.0,2.8,SensorFormat.FULL,4.3)
        assertEquals(10.0,Optics.rule500(e),1e-10)
        assertEquals(4.54,Optics.npf(e,0.0)!!,1e-10)
        assertEquals(9.08,Optics.npf(e,60.0)!!,1e-10)
        assertEquals(Optics.npf(e,60.0),Optics.npf(e,-60.0))
        assertNull(Optics.npf(e,90.0)); assertNull(Optics.npf(e,-89.0))
        assertTrue(Optics.rule500(e.copy(sensor=SensorFormat.CANON))<Optics.rule500(e))
    }
    @Test fun fieldScaleAndMessierFit() {
        val e=Equipment(50.0,2.8,SensorFormat.FULL,4.3)
        assertEquals(39.59775,Optics.field(e.width,e.focal),0.0001)
        assertEquals(17.738773,Optics.scale(e),0.00001)
        val m31=catalog.objects.first { it.id=="M31" }
        assertTrue(Optics.fit(e,m31)!!.fits)
        assertFalse(Optics.fit(e.copy(focal=2000.0),m31)!!.fits)
        assertNull(Optics.fit(e,m31.copy(size=null)))
        assertThrows(IllegalArgumentException::class.java) { Equipment(focal=0.0) }
    }
    @Test fun polarClockJ2000IndependentSiderealReferenceBothHemispheres() {
        val epoch=Utc.parse("2000-01-01T12:00:00Z")
        // GMST J2000 = 280,46061837°. H=GMST+longitud−AR.
        val n=AstroPhoto.polar(epoch,place.copy(longitude=0.0))
        assertEquals(242.5060577,n.hourAngle,0.02)
        assertEquals(117.4939423,n.clockAngle,0.02)
        assertEquals(0.73589103,n.radius,1e-7)
        val s=AstroPhoto.polar(epoch,place.copy(latitude=-40.4,longitude=0.0))
        assertEquals(323.2656184,s.hourAngle,0.02); assertEquals(s.hourAngle,s.clockAngle,1e-10)
        assertEquals("Sigma Octantis",s.star)
        val modern=AstroPhoto.polar(now,place)
        assertTrue(abs(modern.radius-n.radius)>0.05)
    }
    @Test fun galacticCoreLatitudesMonthsAndVisibility() {
        val summer=AstroPhoto.timeline(Utc.parse("2026-07-15T20:00:00Z"),zone,place)
        val g=AstroPhoto.galactic(summer,place)
        assertTrue(g.windows.isNotEmpty()); assertTrue(g.altitude!!>10); assertNotNull(g.azimuth)
        val north=place.copy(latitude=80.0)
        assertTrue(AstroPhoto.galactic(AstroPhoto.timeline(now,zone,north),north).windows.isEmpty())
        val months=AstroPhoto.galacticMonths(2026,zone,place)
        assertEquals(12,months.size); assertTrue(months.first().first in 4..8)
        assertTrue(months.zipWithNext().all { (a,b) -> a.second>=b.second })
    }
    @Test fun solarTwilightEventsMatchThresholdsAndMoonlessWindows() {
        val night=AstroPhoto.timeline(now,zone,place); val events=AstroPhoto.events(night,place)
        assertTrue(events.size>=12)
        val dusk=events.first { it.label.startsWith("Astronómico") && it.label.endsWith("descendente") }
        assertEquals(-18.0,AstroPhoto.horizon(Astro.sun(Astro.dayNumber(dusk.time)),dusk.time,place).alt,0.001)
        assertTrue(events.zipWithNext().all { (a,b) -> a.time<=b.time })
        for(span in night.moonless) assertTrue(night.samples.filter { it.time>=span.start && it.time<span.end }.all { it.sun < -18 && it.moon<0 })
        assertTrue(night.dark.sumOf { it.hours }>=night.moonless.sumOf { it.hours })
    }
    @Test fun dewThresholdAndWindFactors() {
        assertEquals("alto",Weather.dewRisk(10.0,7.01)); assertEquals("bajo",Weather.dewRisk(10.0,7.0))
        assertEquals("sin datos",Weather.dewRisk(null,3.0))
        assertEquals(0.65,Weather.dewFactor(10.0,9.0),0.0)
        assertEquals(1.0,Weather.dewFactor(null,null),0.0)
        assertEquals(0.7,Weather.windFactor(15.0),0.0); assertEquals(0.4,Weather.windFactor(30.0),0.0)
    }
    @Test fun weatherParserCacheOfflineAndNightPenalty() {
        val raw="""{"hourly":{"time":["2026-11-06T22:00"],"cloud_cover":[10],"temperature_2m":[10],"relative_humidity_2m":[95],"dew_point_2m":[9],"wind_speed_10m":[20]}}"""
        val data=Parsers.clouds(raw,now,place)
        assertEquals(95.0,data.hours.single().humidity!!,0.0)
        assertNull(DataPolicy.weatherAt(data,now,now,place,false))
        assertNull(DataPolicy.weatherAt(data,now,now+3*HOUR,place,true))
        assertNull(DataPolicy.weatherAt(data,now+HOUR,now,place,true))
        val yes=Night.hour(now,now,place,data,true); val no=Night.hour(now,now,place,data,false)
        val base=Night.factors(now,10.0,yes.sunAltitude,yes.moonAltitude,yes.illumination,0.0)
        assertEquals(base.quality!!*0.65*0.7,yes.quality!!,1e-10)
        assertNull(no.quality)
        assertNull(no.weather)
        assertThrows(Exception::class.java) { Parsers.clouds(raw.replace("[95]","[101]"),now,place) }
        assertThrows(Exception::class.java) { Parsers.clouds(raw.replace("[20]","[]"),now,place) }
    }
    @Test fun equipmentAndMinimumSurviveRestart() {
        val store=MemoryPreferences(); val s=Settings(store)
        s.equipment=Equipment(135.0,2.0,SensorFormat.MOBILE,2.4,8.0,6.0); s.minimumAltitude=45.0; s.showMessier=true
        val restart=Settings(store)
        assertEquals(s.equipment,restart.equipment); assertEquals(45.0,restart.minimumAltitude,0.0); assertTrue(restart.showMessier)
    }
    @Test fun weatherRequestAndCacheRetainOptionalFieldsWithoutExtraCalls() {
        val cache=MemoryCache(); var weatherCalls=0
        val raw="""{"hourly":{"time":["2026-11-06T22:00"],"cloud_cover":[10],"temperature_2m":[10],"relative_humidity_2m":[95],"dew_point_2m":[9],"wind_speed_10m":[20]}}"""
        val repo=NetworkRepository(cache,Transport { url ->
            if(url.contains("open-meteo")) {
                weatherCalls++
                for(field in listOf("temperature_2m","relative_humidity_2m","dew_point_2m","wind_speed_10m","wind_speed_unit=kmh")) assertTrue(url.contains(field))
                raw
            } else throw java.io.IOException("Proveedor Kp excluido de esta muestra")
        }) { now }
        repo.enabled=true; repeat(10) { repo.refresh(place) }
        assertEquals(1,weatherCalls)
        val restart=NetworkRepository(cache,Transport { throw AssertionError("No debe consultar") }) { now }
        restart.load(place)
        assertEquals(9.0,restart.clouds!!.hours.single().dewPoint!!,0.0)
        assertNull(DataPolicy.weatherAt(restart.clouds,now,now,place,false))
    }
    @Test fun sessionDurationBoundariesAndMonotonicCatchUp() {
        val p=SessionPlan(2,10.0,2.0,1,2,1.0)
        assertEquals(40000L,p.durationMillis)
        var clock=1000L; val timer=SessionTimer(p) { clock }
        assertEquals(10000L,timer.state().remainingMillis)
        clock+=10000; assertFalse(timer.state().exposing); assertEquals(2000L,timer.state().remainingMillis)
        clock+=2000; assertEquals(1,timer.state().index); assertTrue(timer.state().exposing)
        clock=35000; assertEquals(2,timer.state().index); assertEquals("Dark · tapa el objetivo",p.kind(2))
        clock=37000; assertEquals(3,timer.state().index); assertTrue(timer.state().exposing)
        clock=41000; assertTrue(timer.state().done)
    }
    @Test fun sessionNoFinalPauseStopAndInvalidPlans() {
        val p=SessionPlan(1,1.0,100.0)
        assertEquals(1000L,p.durationMillis)
        val t=SessionTimer(p) { 0 }; t.stop(); assertTrue(t.state().done)
        assertThrows(IllegalArgumentException::class.java) { SessionPlan(lights=0) }
        assertThrows(IllegalArgumentException::class.java) { SessionPlan(pauseSeconds=-1.0) }
        assertThrows(IllegalArgumentException::class.java) { SessionPlan(exposureSeconds=Double.NaN) }
    }
}
