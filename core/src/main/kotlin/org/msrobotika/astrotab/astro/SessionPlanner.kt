package org.msrobotika.astrotab.astro

import org.msrobotika.astrotab.data.*
import kotlin.math.*

data class TargetRequest(val id: String,val integrationMinutes: Int=30) {
    init {require(id.matches(Regex("M(?:[1-9][0-9]?|10[0-9]|110)")) && integrationMinutes in 1..720)}
}
data class ObservingSession(val id: String,val name: String,val night: Long,val requests: List<TargetRequest>) {
    init {require(id.length in 1..80 && name.isNotBlank() && name.length<=80 && requests.size<=110);require(requests.map {it.id}.distinct().size==requests.size)}
}
data class SessionBlock(val id: String,val start: Long,val end: Long,val frames: Int,val exposure: Double,val integrationSeconds: Double,val warnings: List<String>)
data class ObservingPlan(val blocks: List<SessionBlock>,val unscheduled: List<String>)

object SessionPlanner {
    const val DEFAULT_GAP_MINUTES=10
    const val MAX_AUTOMATIC_MINUTES=240

    private fun exposureFor(plan: ObjectPlan,value: Double,equipment: Equipment)=
        if(equipment.tracking)value else min(value,Optics.npf(equipment,plan.target.dec) ?: value)

    /** Unión de ventanas: nunca contar dos veces la oscuridad compartida por objetos. */
    private fun usefulWindows(plans: List<ObjectPlan>): List<TimeSpan> {
        val result=mutableListOf<TimeSpan>()
        for(w in plans.flatMap {it.windows}.filter {it.end>it.start}.sortedBy {it.start}) {
            val last=result.lastOrNull()
            if(last!=null && w.start<=last.end)result[result.lastIndex]=TimeSpan(last.start,max(last.end,w.end)) else result.add(w)
        }
        return result
    }

    /** Búsqueda acotada: cobertura primero; calidad, diversidad y meridiano desempatan.
     * Se reparten horas de reloj, incluidas pausas, con un máximo automático de 4 h.
     * Tres objetos si bastan; ampliar la selección cuando sus ventanas son cortas. */
    fun automatic(plans: List<ObjectPlan>,exposure: Double,pause: Double,equipment: Equipment,gapMinutes: Int=DEFAULT_GAP_MINUTES): List<TargetRequest> {
        require(exposure.isFinite() && exposure>0 && pause.isFinite() && pause>=0 && gapMinutes in 0..60)
        val useful=plans.filter {it.score>0 && it.windows.isNotEmpty()}
        val windows=usefulWindows(useful);val total=windows.sumOf {it.end-it.start}
        if(total<=0)return emptyList()
        val byId=useful.associateBy {it.target.id}
        data class Choice(val requests: List<TargetRequest>,val plan: ObservingPlan) {
            val covered=plan.blocks.sumOf {it.end-it.start}
        }
        fun quality(choice: Choice): Double {
            val types=mutableSetOf<String>()
            return choice.plan.blocks.sumOf {b ->
                val p=byId.getValue(b.id)
                val diversity=if(types.add(p.target.type))18 else 0
                val meridian=(abs((b.start+b.end)/2-p.transit)/HOUR.toDouble()).coerceAtMost(4.0)*5
                (b.end-b.start)/MINUTE.toDouble()*(p.score+diversity-meridian)
            }
        }
        val rank=compareByDescending<Choice> {it.covered}.thenByDescending {quality(it)}
        var beam=listOf(Choice(emptyList(),ObservingPlan(emptyList(),emptyList())))
        var best=beam.first()
        repeat(useful.size) {depth ->
            val next=beam.flatMap {choice ->
                val cursor=choice.plan.blocks.lastOrNull()?.let {it.end+gapMinutes*MINUTE} ?: windows.first().start
                val remaining=windows.sumOf {(it.end-max(it.start,cursor)).coerceAtLeast(0)}
                val budget=min(MAX_AUTOMATIC_MINUTES*MINUTE,remaining/(3-depth).coerceAtLeast(1))
                useful.filter {p -> choice.requests.none {it.id==p.target.id}}.mapNotNull {p ->
                    val room=p.windows.maxOfOrNull {(it.end-max(it.start,cursor)).coerceAtLeast(0)} ?: 0
                    val seconds=min(budget,room)/1000.0
                    val perFrame=exposureFor(p,exposure,equipment)
                    val frames=floor((seconds+pause)/(perFrame+pause)).toInt().coerceAtMost(10000)
                    val minutes=floor(frames*perFrame/60).toInt().coerceAtMost(720)
                    if(minutes<1)return@mapNotNull null
                    val requests=choice.requests+TargetRequest(p.target.id,minutes)
                    val result=propose(requests,plans,exposure,pause,equipment,gapMinutes)
                    if(result.blocks.size!=requests.size || result.blocks.any {it.end-it.start>MAX_AUTOMATIC_MINUTES*MINUTE})null
                    else Choice(requests,result)
                }
            }.sortedWith(rank).take(12)
            if(next.isEmpty())return best.requests
            beam=next
            if(rank.compare(beam.first(),best)<0)best=beam.first()
            if(depth>=2 && best.covered>=total*0.7)return best.requests
        }
        return best.requests
    }
    fun propose(requests: List<TargetRequest>,plans: List<ObjectPlan>,exposure: Double,pause: Double,equipment: Equipment,gapMinutes: Int=DEFAULT_GAP_MINUTES): ObservingPlan {
        require(exposure.isFinite() && exposure>0 && pause.isFinite() && pause>=0)
        require(gapMinutes in 0..60)
        val remaining=requests.toMutableList(); val blocks=mutableListOf<SessionBlock>(); val skipped=mutableListOf<String>()
        var cursor=plans.flatMap {it.windows}.minOfOrNull {it.start} ?: 0L
        while(remaining.isNotEmpty()) {
            data class Candidate(val request: TargetRequest,val plan: ObjectPlan,val start: Long,val end: Long,val frames: Int,val desired: Int,val exposure: Double)
            val choices=remaining.mapNotNull { request -> val p=plans.firstOrNull {it.target.id==request.id} ?: return@mapNotNull null
                val perFrame=exposureFor(p,exposure,equipment)
                val desired=ceil(request.integrationMinutes*60/perFrame).toInt().coerceAtMost(10000)
                p.windows.mapNotNull { w ->
                    val earliest=max(w.start,cursor)
                    if(earliest>=w.end) return@mapNotNull null
                    val frames=min(desired,floor(((w.end-earliest)/1000.0+pause)/(perFrame+pause)).toInt())
                    if(frames<1) return@mapNotNull null
                    val duration=((frames*perFrame+(frames-1)*pause)*1000).roundToLong()
                    Candidate(request,p,earliest,earliest+duration,frames,desired,perFrame)
                }.minByOrNull {it.start}
            }
            // No retrasar hasta el tránsito dejando oscuridad vacía. Ventana que cierra
            // antes primero; calidad lunar/altura y proximidad al meridiano desempatan.
            val next=choices.minWithOrNull(compareBy<Candidate> {it.start}
                .thenBy {c -> c.plan.windows.first {c.start>=it.start && c.end<=it.end}.end}
                .thenByDescending {it.plan.moonFactor}.thenByDescending {it.plan.score}
                .thenBy {abs((it.start+it.end)/2-it.plan.transit)})
            if(next==null) {skipped.addAll(remaining.map {it.id});break}
            val warnings=mutableListOf<String>()
            if(next.frames<next.desired)warnings.add("Integración recortada por ventana útil")
            if(equipment.equatorial && next.plan.transit in (next.start-5*MINUTE)..(next.end+5*MINUTE))warnings.add("Volteo de meridiano durante el bloque o en los 5 min adyacentes")
            if(next.plan.moonFactor<0.8)warnings.add("Luna: penalización orientativa")
            if(next.exposure<exposure)warnings.add("Exposición limitada por NPF: sin seguimiento")
            if(!equipment.tracking && Optics.npf(equipment,next.plan.target.dec)==null)warnings.add("NPF no definida cerca del polo: se conserva la exposición de Sesión")
            blocks.add(SessionBlock(next.request.id,next.start,next.end,next.frames,next.exposure,next.frames*next.exposure,warnings))
            cursor=next.end+gapMinutes*MINUTE; remaining.remove(next.request)
        }
        return ObservingPlan(blocks,skipped)
    }
}
