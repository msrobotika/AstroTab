package org.msrobotika.astrotab

import org.junit.Assert.*
import org.junit.Test
import org.msrobotika.astrotab.ui.theme.*
import org.msrobotika.astrotab.astro.*
import org.msrobotika.astrotab.data.*
import java.util.TimeZone

class ProBRegressionTest {
    @Test fun roundedZeroHasNoSign() {
        assertEquals("0°",DisplayFormat.number(-0.1,"°"))
        assertEquals("0,0",DisplayFormat.number(-0.01,digits=1))
        assertEquals("0",DisplayFormat.number(-0.0))
        assertEquals("-1°",DisplayFormat.number(-0.9,"°"))
        assertEquals("sin datos",DisplayFormat.number(Double.NaN))
    }
    @Test fun morningNightReferenceIsStableOnRepeatedUse() {
        val now=Utc.parse("2026-11-06T10:00:00Z"); val z=TimeZone.getTimeZone("Europe/Madrid")
        val p=Location(40.4,-3.7,"manual",0)
        val ref=SkyControls.nightReference(now,z,p)
        assertEquals(AstroPhoto.nightBounds(ref,z),AstroPhoto.nightBounds(SkyControls.nightReference(ref,z,p),z))
    }
    @Test fun daylightTwilightAndNightHaveDifferentBackgroundsInBothThemes() {
        assertNotEquals(SkyControls.background(15.0,false),SkyControls.background(2.0,false))
        assertNotEquals(SkyControls.background(2.0,false),SkyControls.background(-20.0,false))
        for(alt in listOf(-20.0,-5.0,2.0,15.0)) {val color=SkyControls.background(alt,true);val r=color ushr 16 and 255
            assertTrue(r>=color.ushr(8).and(255));assertTrue(r>=color.and(255))}
    }
}
