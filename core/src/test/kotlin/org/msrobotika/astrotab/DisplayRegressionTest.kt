package org.msrobotika.astrotab

import org.junit.Assert.*
import org.junit.Test
import org.msrobotika.astrotab.data.*
import org.msrobotika.astrotab.ui.*

class DisplayRegressionTest {
    @Test fun locationStatusNeverFormatsCoordinatesOrFreeText() {
        assertEquals("sin ubicación",LocationStatus.label(null))
        assertEquals("ubicación manual",LocationStatus.label(Location(40.4168,-3.7038,"manual",0)))
        for(origin in listOf("IP aproximada · ipapi.co","40,4168°, -3,7038°","","GPS")) {
            assertEquals("ubicación aproximada",LocationStatus.label(Location(40.4168,-3.7038,origin,0)))
        }
        assertEquals(LocationStatus.label(Location(-90.0,180.0,"manual",0)),LocationStatus.label(Location(90.0,-180.0,"manual",100)))
    }
    @Test fun segmentKeepsShortVisibleEndpointsAndHorizonBoundary() {
        assertTrue(SkyGeometry.segmentVisible(0.0,0f,0f,30.0,160f,120f,1000,400))
        assertTrue(SkyGeometry.segmentVisible(30.0,1000f,400f,0.0,1000f,200f,1000,400))
    }
    @Test fun segmentRejectsEitherEndpointUnderHorizonOrOutsideField() {
        assertFalse(SkyGeometry.segmentVisible(-0.01,100f,100f,10.0,110f,110f,1000,400))
        assertFalse(SkyGeometry.segmentVisible(10.0,100f,100f,-0.01,110f,110f,1000,400))
        for(point in listOf(-1f to 10f,1001f to 10f,10f to -1f,10f to 401f)) {
            assertFalse(SkyGeometry.segmentVisible(10.0,point.first,point.second,10.0,100f,100f,1000,400))
            assertFalse(SkyGeometry.segmentVisible(10.0,100f,100f,10.0,point.first,point.second,1000,400))
        }
    }
    @Test fun segmentRejectsLongHorizontalVerticalAndDiagonalLines() {
        assertFalse(SkyGeometry.segmentVisible(30.0,0f,0f,30.0,201f,0f,1000,400))
        assertFalse(SkyGeometry.segmentVisible(30.0,0f,0f,30.0,0f,201f,1000,400))
        assertFalse(SkyGeometry.segmentVisible(30.0,0f,0f,30.0,150f,150f,1000,400))
        // En retrato también manda la dimensión menor.
        assertFalse(SkyGeometry.segmentVisible(30.0,0f,0f,30.0,0f,201f,400,1000))
    }
    @Test fun segmentRejectsNonFiniteValuesAndAzimuthDiscontinuity() {
        assertFalse(SkyGeometry.segmentVisible(Double.NaN,0f,0f,30.0,10f,10f,1000,400))
        assertFalse(SkyGeometry.segmentVisible(30.0,0f,0f,Double.POSITIVE_INFINITY,10f,10f,1000,400))
        assertFalse(SkyGeometry.segmentVisible(30.0,Float.NaN,0f,30.0,10f,10f,1000,400))
        assertFalse(SkyGeometry.segmentVisible(30.0,0f,0f,30.0,Float.POSITIVE_INFINITY,10f,1000,400))
        val x=SkyGeometry.x(179.0,0.0,1000); val next=SkyGeometry.x(181.0,0.0,1000)
        assertFalse(SkyGeometry.segmentVisible(35.0,x,200f,35.0,next,200f,1000,400))
        assertFalse(SkyGeometry.segmentVisible(30.0,0f,0f,30.0,0f,0f,0,400))
    }
    @Test fun rollRotatesPositionsAndSegmentLengthIsMeasuredOnScreen() {
        val projection=SkyProjection()
        projection.configure(1000,400,180.0,35.0,0.0)
        assertEquals(580f,projection.x(190.0,35.0),0.01f)
        assertEquals(200f,projection.y(190.0,35.0),0.01f)
        projection.configure(1000,400,180.0,35.0,90.0)
        assertEquals(500f,projection.x(190.0,35.0),0.01f)
        assertEquals(280f,projection.y(190.0,35.0),0.01f)
        assertTrue(SkyGeometry.segmentVisible(35.0,500f,200f,35.0,projection.x(190.0,35.0),projection.y(190.0,35.0),1000,400))
        assertFalse(SkyGeometry.segmentVisible(35.0,500f,200f,35.0,projection.x(220.0,35.0),projection.y(220.0,35.0),1000,400))
    }
    @Test fun labelRectanglesPreserveEarlierPriorityAndRejectOverlapAndEdges() {
        val labels=LabelLayout()
        assertTrue(labels.offer(10f,10f,100f,30f,640,400,4f))
        assertFalse(labels.offer(20f,15f,110f,35f,640,400,4f))
        assertFalse(labels.offer(103f,10f,130f,30f,640,400,4f))
        assertTrue(labels.offer(104f,10f,130f,30f,640,400,4f))
        assertFalse(labels.offer(-1f,50f,100f,70f,640,400,4f))
        assertFalse(labels.offer(600f,50f,650f,70f,640,400,4f))
        assertFalse(labels.offer(10f,390f,100f,410f,640,400,4f))
        labels.reset()
        assertTrue(labels.offer(20f,15f,110f,35f,640,400,4f))
    }
}
