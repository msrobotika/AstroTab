package org.msrobotika.astrotab

import org.junit.Assert.*
import org.junit.Test
import org.msrobotika.astrotab.astro.*
import org.msrobotika.astrotab.data.*
import org.msrobotika.astrotab.ui.*
import org.msrobotika.astrotab.ui.theme.*

class RedesignTest {
    @Test fun milkyWayCountsAndGalacticCentre() {
        val band=MilkyWay(javaClass.getResourceAsStream("/catalog/milkyway.bin")!!)
        assertEquals(202,band.starts.size); assertEquals(3237,band.ra.size)
        assertEquals(setOf(0,1,2,3,4),band.flags.map { it/2 }.toSet())
        assertTrue("Centro galáctico dentro de la banda",band.contains(266.4051,-28.9362))
        assertFalse("Polo galáctico fuera de la banda",band.contains(192.8595,27.1283))
        assertTrue(band.lengths.all { it>=4 })
    }
    @Test fun allEightCardinalsProjectAtTheirOwnBearingAndWrap() {
        val p=SkyProjection()
        SkyControls.cardinals.forEachIndexed { i,_ -> p.configure(1024,600,i*45.0,0.0,0.0); assertEquals(512f,p.x(i*45.0,0.0),0.001f); assertEquals(300f,p.y(i*45.0,0.0),0.001f) }
        assertArrayEquals(arrayOf("N","NE","E","SE","S","SO","O","NO"),SkyControls.cardinals)
        p.configure(1024,600,0.0,0.0,0.0); assertTrue(p.x(315.0,0.0)<512); assertTrue(p.x(45.0,0.0)>512)
    }
    @Test fun cameraFrameMatchesExactAngularField() {
        val field=SkyControls.field(Equipment(focal=50.0))
        assertEquals(39.59775,field.first,0.0001); assertEquals(26.99147,field.second,0.0001)
        val p=SkyProjection(); p.configure(1024,600,180.0,35.0,0.0)
        assertEquals(field.first*1024/125,(p.x(180+field.first/2,35.0)-p.x(180-field.first/2,35.0)).toDouble(),0.001)
        assertEquals(field.second*600/85,(p.y(180.0,35-field.second/2)-p.y(180.0,35+field.second/2)).toDouble(),0.001)
    }
    @Test fun timeSliderEndpointsMidpointClampAndPolarFallback() {
        val now=Utc.parse("2026-11-06T20:00Z"); val dawn=now+10*HOUR
        assertEquals(now,SkyControls.time(now,dawn,0)); assertEquals(dawn,SkyControls.time(now,dawn,1000))
        assertEquals(now+5*HOUR,SkyControls.time(now,dawn,500)); assertEquals(now,SkyControls.time(now,dawn,-1)); assertEquals(dawn,SkyControls.time(now,dawn,1001))
        assertEquals(now+24*HOUR,SkyControls.dawn(now,emptyList()))
        assertEquals(dawn,SkyControls.dawn(now,listOf(AstroPhoto.Event("Salida/puesta del Sol · ascendente",dawn))))
    }
    @Test fun selectedObjectUsesSameAltitudeAzimuthAsExistingEngine() {
        val t=Utc.parse("2026-11-06T20:00Z"); val place=Location(40.4168,-3.7038,"manual",0)
        val eq=Astro.Eq(10.6847,41.269)
        val result=SkyControls.target(eq.ra,eq.dec,t,place,true)
        val reference=AstroPhoto.horizon(AstroPhoto.precess(eq,t),t,place)
        assertEquals(reference.alt,result.alt,1e-10); assertEquals(reference.az,result.az,1e-10)
        val transit=SkyControls.target(0.0,0.0,0,Location(0.0,-Astro.gmst(Astro.dayNumber(0)),"manual",0))
        assertEquals(90.0,transit.alt,0.0001)
    }
    @Test fun usefulRoundingAndDurationsCarryMinutes() {
        assertEquals("97 %",DisplayFormat.number(96.6," %")); assertEquals("2 h 10 min",DisplayFormat.duration(2.17))
        assertEquals("2 h",DisplayFormat.duration(1.999)); assertEquals("sin datos",DisplayFormat.number(Double.NaN))
        assertEquals("sin datos",DisplayFormat.number(null)); assertEquals("Regular",DisplayFormat.status(50))
        assertEquals("Datos listos",DisplayFormat.summary("Datos listos. Fórmula y fuentes en detalle."))
    }
    @Test fun everyPaletteAndArgbTransformHasNoDominantGreenOrBlue() {
        for(color in Design.colors+intArrayOf(0xFF00FF00.toInt(),0xFF0000FF.toInt(),0x607FFFFF)) {
            val red=Design.red(color); val r=red ushr 16 and 255
            assertTrue(r>=red ushr 8 and 255); assertTrue(r>=red and 255); assertEquals(color ushr 24,red ushr 24)
        }
        val p=Palette(true); assertTrue(p.good!=Palette(false).good); assertTrue(p.accent!=Palette(false).accent)
    }
    @Test fun automaticRedCivilDuskAndSunriseAndPreference() {
        assertFalse(SkyControls.automaticRed(-5.9,false)); assertTrue(SkyControls.automaticRed(-6.1,false))
        assertTrue(SkyControls.automaticRed(-0.1,true)); assertFalse(SkyControls.automaticRed(0.1,true))
        val data=mutableMapOf<String,String?>(); val store=object: PreferenceStore { override fun get(key: String)=data[key]; override fun put(values: Map<String,String?>) {data.putAll(values)} }
        val s=Settings(store); assertFalse(s.autoRed); s.autoRed=true; assertTrue(Settings(store).autoRed)
    }
    @Test fun homeMorningPreparesUpcomingNightWithoutMakingWeatherFresh() {
        val place=Location(40.4168,-3.7038,"manual",0); val zone=java.util.TimeZone.getTimeZone("Europe/Madrid")
        val morning=Utc.parse("2026-11-06T10:00Z")
        assertEquals(morning+12*HOUR,SkyControls.nightReference(morning,zone,place))
        val dark=Utc.parse("2026-11-06T05:00Z"); assertEquals(dark,SkyControls.nightReference(dark,zone,place))
        val reference=SkyControls.nightReference(morning,zone,place)
        val week=Forecast.week(morning,zone,place,null,false,nightReference=reference)
        assertEquals(AstroPhoto.nightBounds(reference,zone).start,week.first().start)
        assertTrue(week.all {it.score==null})
    }
}
