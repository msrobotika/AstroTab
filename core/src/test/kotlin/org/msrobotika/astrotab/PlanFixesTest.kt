package org.msrobotika.astrotab

import org.junit.Assert.*
import org.junit.Test
import org.msrobotika.astrotab.astro.*
import org.msrobotika.astrotab.catalog.*
import org.msrobotika.astrotab.data.*
import java.util.TimeZone

class PlanFixesTest {
    private val start=Utc.parse("2026-11-06T19:00Z")
    private fun target(id: Int,dec: Double=0.0,type: String="s")=MessierObject("M$id",0.0,dec,type,5.0,null,null,"","")
    private fun plan(id: Int,dec: Double=0.0,windows: List<TimeSpan> = listOf(TimeSpan(start,start+11*HOUR)),type: String="s") =
        ObjectPlan(target(id,dec,type),doubleArrayOf(),11.0,start+5*HOUR,70.0,80,1.0,0.9,1.0,windows)
    private fun verify(result: ObservingPlan,plans: List<ObjectPlan>,gap: Int=10) {
        assertTrue(result.unscheduled.isEmpty())
        assertTrue(result.blocks.zipWithNext().all {it.second.start-it.first.end>=gap*MINUTE})
        result.blocks.forEach {b ->
            assertTrue(plans.first {it.target.id==b.id}.windows.any {b.start>=it.start && b.end<=it.end})
            assertEquals(b.frames*b.exposure,b.integrationSeconds,1e-8)
        }
    }
    @Test fun untrackedUsesNpfForEachDeclinationAndFrameTiming() {
        val equipment=Equipment();val plans=listOf(plan(1),plan(2,60.0))
        val result=SessionPlanner.propose(listOf(TargetRequest("M1"),TargetRequest("M2")),plans,30.0,5.0,equipment)
        assertEquals(2,result.blocks.size)
        result.blocks.forEach {b ->
            val dec=plans.first {it.target.id==b.id}.target.dec
            assertEquals(Optics.npf(equipment,dec)!!,b.exposure,1e-9)
            assertTrue(b.warnings.any {it.contains("limitada por NPF: sin seguimiento")})
            assertEquals((b.frames*b.exposure+(b.frames-1)*5.0)*1000,(b.end-b.start).toDouble(),1.0)
        }
        assertEquals(4.54,result.blocks.first {it.id=="M1"}.exposure,0.001)
        verify(result,plans)
    }
    @Test fun trackingUsesSessionAndShorterUserExposureIsPreserved() {
        for((equipment,exposure) in listOf(Equipment(tracking=true) to 30.0,Equipment() to 2.0)) {
            val block=SessionPlanner.propose(listOf(TargetRequest("M1")),listOf(plan(1)),exposure,5.0,equipment).blocks.single()
            assertEquals(exposure,block.exposure,0.0)
            assertFalse(block.warnings.any {it.contains("limitada por NPF")})
        }
    }
    @Test fun shortNpfExposureCanBePassedToSessionTimer() {
        val equipment=Equipment(focal=10000.0)
        val block=SessionPlanner.propose(listOf(TargetRequest("M1",1)),listOf(plan(1)),30.0,5.0,equipment).blocks.single()
        assertEquals(0.0227,block.exposure,1e-10)
        val session=SessionPlan(block.frames,block.exposure,5.0)
        assertEquals((block.end-block.start).toDouble(),session.durationMillis.toDouble(),1.0)
        assertFalse(SessionTimer(session) {0L}.state().done)
    }
    @Test fun elevenHourNightIsFilledWithRealTimeBlocks() {
        val plans=listOf(plan(1),plan(2,type="oc"),plan(3,type="pn"))
        for(equipment in listOf(Equipment(),Equipment(tracking=true))) {
            val requests=SessionPlanner.automatic(plans,30.0,5.0,equipment)
            val result=SessionPlanner.propose(requests,plans,30.0,5.0,equipment)
            verify(result,plans)
            val coverage=result.blocks.sumOf {it.end-it.start}/(11*HOUR).toDouble()
            assertTrue("Coverage $coverage for $equipment",coverage>=0.7)
            assertTrue(result.blocks.all {it.end-it.start<=240*MINUTE})
        }
    }
    @Test fun seasonalAstronomicalNightsMeetCoverageAndWindows() {
        val place=Location(40.4168,-3.7038,"manual",0)
        val catalog=MessierCatalog(javaClass.getResourceAsStream("/catalog/messier.tsv")!!)
        for(date in listOf("2026-11-06T18:00Z","2027-03-20T18:00Z","2027-07-01T18:00Z")) {
            val night=AstroPhoto.timeline(Utc.parse(date),TimeZone.getTimeZone("Europe/Madrid"),place)
            val plans=catalog.objects.map {AstroPhoto.plan(it,night,place,30.0)}
            for(equipment in listOf(Equipment(),Equipment(tracking=true,equatorial=true))) {
                val result=SessionPlanner.propose(SessionPlanner.automatic(plans,30.0,5.0,equipment),plans,30.0,5.0,equipment)
                verify(result,plans)
                val coverage=result.blocks.sumOf {it.end-it.start}/night.dark.sumOf {it.end-it.start}.toDouble()
                println("PLAN_FIXES $date tracking=${equipment.tracking} coverage=$coverage blocks=${result.blocks.size}")
                assertTrue("$date coverage=$coverage",coverage>=0.7)
            }
        }
    }
    @Test fun fragmentedWindowsAndUnavailableTargetsStayHonest() {
        val plans=listOf(plan(1,windows=listOf(TimeSpan(start,start+2*HOUR),TimeSpan(start+5*HOUR,start+6*HOUR))),
            plan(2,windows=listOf(TimeSpan(start+2*HOUR,start+5*HOUR))),
            plan(3,windows=listOf(TimeSpan(start+6*HOUR,start+11*HOUR)),type="pn"))
        val equipment=Equipment(tracking=true)
        val result=SessionPlanner.propose(SessionPlanner.automatic(plans,30.0,5.0,equipment),plans,30.0,5.0,equipment)
        verify(result,plans)
        assertTrue(result.blocks.sumOf {it.end-it.start}>=(11*HOUR*0.7).toLong())
        val empty=plans.map {it.copy(windows=emptyList())}
        assertTrue(SessionPlanner.automatic(empty,30.0,5.0,equipment).isEmpty())
        assertEquals(listOf("M1"),SessionPlanner.propose(listOf(TargetRequest("M1")),empty,30.0,5.0,equipment).unscheduled)
    }
    @Test fun configurableGapAndShortWindowsCanRequireMoreThanThreeObjects() {
        val plans=(1..6).map {i ->plan(i,windows=listOf(TimeSpan(start+(i-1)*2*HOUR,start+i*2*HOUR)))}
        for(gap in listOf(0,10,30)) {
            val equipment=Equipment(tracking=true)
            val requests=SessionPlanner.automatic(plans,30.0,5.0,equipment,gap)
            val result=SessionPlanner.propose(requests,plans,30.0,5.0,equipment,gap)
            verify(result,plans,gap)
            assertTrue(result.blocks.size>3)
            assertTrue(result.blocks.sumOf {it.end-it.start}>=12*HOUR*0.7)
        }
    }
    @Test fun impossibleCoverageAndUndefinedPolarNpfAreReportedWithoutInventingTime() {
        val polar=plan(1,90.0)
        val result=SessionPlanner.propose(listOf(TargetRequest("M1")),listOf(polar),30.0,5.0,Equipment())
        assertEquals(30.0,result.blocks.single().exposure,0.0)
        assertTrue(result.blocks.single().warnings.any {it.contains("NPF no definida")})
        val requests=SessionPlanner.automatic(listOf(polar),30.0,5.0,Equipment())
        val limited=SessionPlanner.propose(requests,listOf(polar),30.0,5.0,Equipment())
        assertEquals(1,limited.blocks.size)
        assertTrue(limited.blocks.single().end-limited.blocks.single().start<=240*MINUTE)
        assertTrue(SessionPlanner.automatic(emptyList(),30.0,5.0,Equipment()).isEmpty())
    }
}
