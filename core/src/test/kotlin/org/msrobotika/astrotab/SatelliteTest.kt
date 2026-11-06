package org.msrobotika.astrotab

import org.junit.Assert.*
import org.junit.Test
import org.msrobotika.astrotab.satellites.*
import org.msrobotika.astrotab.data.*
import java.io.File

class SatelliteTest {
    private fun resource(name: String)=javaClass.getResourceAsStream("/sgp4/$name")!!.bufferedReader().use {it.readText()}
    @Test fun officialValladoVectorsNearAndDeepSpace() {
        val lines=resource("verification.tle").lineSequence().filter {it.startsWith("1 ") || it.startsWith("2 ")}.map {it.take(69)}.toList()
        val records=lines.chunked(2).mapNotNull { runCatching {TleParser.parse(it.joinToString("\n")).single()}.getOrNull() }.associateBy {it.number}
        val selected=setOf(5,4632,6251,8195,9880,9998,11801,14128,16925,20413,21897,22674,23177,23599,24208,25954,26900,26975,28057,28129,28623,28626,29238)
        var current=0; var count=0
        for(line in resource("expected.out").lineSequence()) {
            if(line.contains("xx")) { current=line.trim().substringBefore(' ').toInt(); continue }
            if(current !in selected || current !in records || line.isBlank()) continue
            val row=line.trim().split(Regex("\\s+")); if(row.size<7) continue
            val state=Sgp4(records.getValue(current)).atMinutes(row[0].toDouble())
            assertNotNull("Satélite $current t=${row[0]}",state)
            val actual=doubleArrayOf(state!!.x,state.y,state.z,state.vx,state.vy,state.vz)
            for(i in 0..5) assertEquals("$current t=${row[0]} componente $i",row[i+1].toDouble(),actual[i],if(i<3) 0.001 else 0.000001)
            count++
        }
        assertTrue("Más de 400 vectores oficiales, realmente $count",count>400)
        println("SGP4: $count vectores oficiales; tolerancia 1 m y 1 mm/s")
    }
    @Test fun malformedChecksumSizeAndStaleness() {
        val raw=resource("iss.tle"); val tle=TleParser.parse(raw).single()
        assertEquals(25544,tle.number); assertFalse(tle.old(tle.epoch+72*HOUR)); assertTrue(tle.old(tle.epoch+72*HOUR+1))
        assertTrue(runCatching {TleParser.parse(raw.replace("25544","25545"))}.isFailure)
        assertTrue(runCatching {TleParser.parse("x".repeat(512*1024+1))}.isFailure)
        val valid=TleParser.parse(resource("visual.tle")); assertTrue(valid.size>100)
    }
    @Test fun repositoryOfflineAndFailedRefreshPreservesData() {
        val memory=mutableMapOf<String,Cached>(); var calls=0
        val cache=object: CacheStore { override fun read(key:String)=memory[key]; override fun write(key:String,value:Cached){memory[key]=value} }
        val r=SatelliteRepository(cache,Transport {calls++; resource("iss.tle")})
        val now=TleParser.parse(resource("iss.tle")).single().epoch
        r.refresh(false,now); assertEquals(0,calls)
        r.refresh(true,now); assertEquals(2,calls); assertEquals(1,r.tles.size)
        val restarted=SatelliteRepository(cache,Transport {error("Sin red")}); restarted.load()
        assertEquals(r.tles,restarted.tles); restarted.refresh(true,now+2*HOUR); assertEquals(r.tles,restarted.tles)
        var enabled=true;var revokedCalls=0
        val revoked=SatelliteRepository(cache,Transport {revokedCalls++;enabled=false;resource("iss.tle")})
        revoked.refresh({enabled},now+4*HOUR);assertEquals(1,revokedCalls);assertTrue(revoked.tles.isEmpty())
    }
    @Test fun fiveDayBenchmarkAndVisibility() {
        val tles=TleParser.parse(resource("visual.tle")).take(150)
        val now=TleParser.parse(resource("iss.tle")).single().epoch
        val place=Location(40.4168,-3.7038,"manual",0)
        var updates=0; val begin=System.nanoTime()
        val passes=SatellitePredictor.predict(tles,now,place,partial={done,_ -> updates=done})
        val ms=(System.nanoTime()-begin)/1e6
        val warm=DoubleArray(3) {val start=System.nanoTime();val again=SatellitePredictor.predict(tles,now,place);assertEquals(passes.size,again.size);(System.nanoTime()-start)/1e6}.sorted()
        val median=warm[1]
        println("BENCHMARK_SGP4 satellites=${tles.size} days=5 coldPcMs=$ms warmPcMs=$median passes=${passes.size}")
        File("build/test-artifacts/benchmark-150.txt").apply { parentFile?.mkdirs() }.writeText("PC JVM: primera llamada $ms ms; tres cálculos posteriores ${warm.joinToString()} ms; mediana $median ms. ${tles.size} satélites × 5 días, ${passes.size} pasos.\nSin extrapolación a ARMv7; no acredita rendimiento físico.\n")
        assertEquals(tles.size,updates); assertTrue(passes.isNotEmpty())
        for(p in passes) { assertTrue(p.start.time<=p.maximum.time && p.maximum.time<=p.end.time); assertTrue(p.track.any {it.visible}); assertTrue(p.maximum.altitude>=p.start.altitude); assertTrue(p.maximum.altitude>=p.end.altitude); assertTrue(p.track.all {it.azimuth in 0.0..360.0}) }
    }
    @Test fun passInterpolationMatchesDirectSgp4WithinOneSecondAndTenthDegree() {
        val tle=TleParser.parse(resource("iss.tle")).single();val place=Location(40.4,-3.7,"manual",0)
        val fast=SatellitePredictor.predict(listOf(tle),tle.epoch,place,days=2)
        val direct=SatellitePredictor.predict(listOf(tle),tle.epoch,place,days=2,interpolate=false)
        assertEquals(direct.size,fast.size)
        for((a,b) in fast.zip(direct)) {
            assertTrue(kotlin.math.abs(a.start.time-b.start.time)<=1000)
            assertTrue(kotlin.math.abs(a.end.time-b.end.time)<=1000)
            assertEquals(b.maximum.altitude,a.maximum.altitude,0.1)
            val exact=SatelliteGeometry.point(Sgp4(tle).at(a.maximum.time)!!,a.maximum.time,SatelliteGeometry.observer(a.maximum.time,place))
            assertEquals(exact.altitude,a.maximum.altitude,0.1)
        }
    }
}
