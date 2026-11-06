package org.msrobotika.astrotab.astro

data class SessionPlan(val lights: Int=30, val exposureSeconds: Double=30.0, val pauseSeconds: Double=5.0,
    val darks: Int=0, val flats: Int=0, val flatSeconds: Double=1.0) {
    init {
        require(lights in 1..10000 && darks in 0..10000 && flats in 0..10000)
        // Un bloque sin seguimiento puede quedar por debajo de 0,1 s al aplicar NPF.
        require(exposureSeconds.isFinite() && exposureSeconds in 0.001..86400.0 && flatSeconds.isFinite() && flatSeconds in 0.1..86400.0)
        require(pauseSeconds.isFinite() && pauseSeconds in 0.0..3600.0)
    }
    val count get()=lights+darks+flats
    fun exposure(index: Int)=if(index<lights+darks) exposureSeconds else flatSeconds
    fun kind(index: Int)=when { index<lights -> "Luz"; index<lights+darks -> "Dark · tapa el objetivo"; else -> "Flat · prepara iluminación" }
    val durationMillis get()=(((lights+darks)*exposureSeconds+flats*flatSeconds+(count-1)*pauseSeconds)*1000).toLong()
}
data class SessionState(val done: Boolean, val index: Int, val exposing: Boolean, val remainingMillis: Long, val totalRemainingMillis: Long)
/** Depende solo del reloj monotónico inyectado. No acumula retrasos de callbacks ni usa hora civil. */
class SessionTimer(val plan: SessionPlan, private val clock: ()->Long) {
    private val start=clock()
    private var stopped=false
    fun stop() { stopped=true }
    fun state(): SessionState {
        var elapsed=(clock()-start).coerceAtLeast(0)
        val total=(plan.durationMillis-elapsed).coerceAtLeast(0)
        if(stopped || total==0L) return SessionState(true,plan.count-1,false,0,0)
        for(index in 0 until plan.count) {
            val exposure=(plan.exposure(index)*1000).toLong()
            if(elapsed<exposure) return SessionState(false,index,true,exposure-elapsed,total)
            elapsed-=exposure
            val pause=(plan.pauseSeconds*1000).toLong()
            if(index<plan.count-1 && elapsed<pause) return SessionState(false,index,false,pause-elapsed,total)
            elapsed-=pause
        }
        return SessionState(true,plan.count-1,false,0,0)
    }
}
