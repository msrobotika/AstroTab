package org.msrobotika.astrotab.astro

object TargetChoice {
    /** Conserva calidad, pero prioriza tipos nuevos y separa campos ≥2°. */
    fun diverse(plans: List<ObjectPlan>, count: Int=3): List<ObjectPlan> {
        val selected=mutableListOf<ObjectPlan>()
        val remaining=plans.sortedByDescending { it.score }.toMutableList()
        while(selected.size<count && remaining.isNotEmpty()) {
            val candidates=remaining.filter { p -> selected.all { AstroPhoto.separation(it.target.eq,p.target.eq)>=2.0 } }
            val best=candidates.maxByOrNull { p -> p.score+if(selected.none { it.target.type==p.target.type }) 18 else 0 } ?: break
            selected.add(best); remaining.remove(best)
        }
        return selected
    }
}
