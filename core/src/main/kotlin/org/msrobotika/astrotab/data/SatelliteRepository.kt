package org.msrobotika.astrotab.data

import org.msrobotika.astrotab.satellites.*

/** La caché TLE sigue disponible offline. No se inicia red salvo refresh(enabled=true). */
class SatelliteRepository(private val cache: CacheStore,private val transport: Transport) {
    var tles=emptyList<Tle>(); private set
    var fetchedAt: Long?=null; private set
    var status="Sin TLE · activa la red y actualiza"; private set
    private var autoAttemptAt: Long?=null
    fun refreshIfNeeded(enabled: ()->Boolean,now: Long) {
        if(!enabled() || (fetchedAt?.let {now-it in 0..24*HOUR}==true && tles.none {now-it.epoch>24*HOUR}))return
        if(autoAttemptAt?.let {now-it in 0 until HOUR}==true)return
        autoAttemptAt=now
        refresh(enabled,now)
    }
    fun load() {
        cache.read("satellites")?.let { c -> runCatching { TleParser.parse(c.payload) }.onSuccess { tles=it; fetchedAt=c.fetchedAt; status="CelesTrak · caché local" } }
    }
    fun refresh(enabled: Boolean,now: Long) {
        refresh({enabled},now)
    }
    fun refresh(enabled: ()->Boolean,now: Long) {
        if(!enabled()) { status="Red desactivada · se conserva la caché TLE"; return }
        if(fetchedAt?.let { now-it in 0 until HOUR }==true) return
        try {
            val visual=transport.get(VISUAL); val group=TleParser.parse(visual)
            if(!enabled()) {status="Red desactivada · se conserva la caché TLE";return}
            val issRaw=transport.get(ISS); val iss=TleParser.parse(issRaw).single {it.number==25544}
            val all=(listOf(iss)+group).distinctBy {it.number}.take(TleParser.MAX_OBJECTS)
            val payload=all.joinToString("\n") {"${it.name}\n${it.line1}\n${it.line2}"}
            cache.write("satellites",Cached(now,payload)); tles=all; fetchedAt=now; status="CelesTrak · actualizado"
        } catch(e: Exception) { status="${NetworkFailure.from(e).label} · se conserva la caché TLE" }
    }
    fun old(now: Long)=fetchedAt?.let {now-it>72*HOUR || now<it}!=false || tles.any {it.old(now)}
    companion object {
        const val ISS="https://celestrak.org/NORAD/elements/gp.php?CATNR=25544&FORMAT=TLE"
        const val VISUAL="https://celestrak.org/NORAD/elements/gp.php?GROUP=visual&FORMAT=TLE"
    }
}
