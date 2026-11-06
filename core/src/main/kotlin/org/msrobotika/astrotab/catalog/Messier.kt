package org.msrobotika.astrotab.catalog

import java.io.InputStream
import java.text.Normalizer
import java.util.Locale
import org.msrobotika.astrotab.astro.Astro

data class MessierObject(val id: String, val ra: Double, val dec: Double, val sourceType: String,
    val magnitude: Double?, val size: String?, val constellation: String?, val sourceName: String, val designation: String) {
    val group get()=when(sourceType) { "s","e","i" -> "Galaxia"; "snr","sfr","pn","rn" -> "Nebulosa"; "oc","gc" -> "Cúmulo"; else -> "Otros" }
    val type get()=when(sourceType) { "gc" -> "Cúmulo globular"; "oc" -> "Cúmulo abierto"; "pn" -> "Nebulosa planetaria"; "snr" -> "Resto de supernova"; "sfr" -> "Región de formación estelar"; "rn" -> "Nebulosa de reflexión"; "pos" -> "Agrupación aparente"; "s","e","i" -> group; else -> "—" }
    val name get()=names[id] ?: ""
    val label get()=id+if(name.isEmpty()) "" else " · $name"
    val majorArcmin get()=size?.split('x')?.mapNotNull { it.toDoubleOrNull() }?.maxOrNull()
    val minorArcmin get()=size?.split('x')?.mapNotNull { it.toDoubleOrNull() }?.minOrNull()
    val eq get()=Astro.Eq(ra,dec)
    companion object {
        private val names=mapOf("M1" to "Nebulosa del Cangrejo","M6" to "Mariposa","M7" to "Cúmulo de Ptolomeo","M8" to "Laguna","M11" to "Pato Salvaje","M13" to "Gran Cúmulo de Hércules","M16" to "Águila","M17" to "Omega","M20" to "Trífida","M24" to "Nube estelar de Sagitario","M27" to "Mancuerna","M31" to "Andrómeda","M33" to "Triángulo","M42" to "Nebulosa de Orión","M44" to "Pesebre","M45" to "Pléyades","M51" to "Remolino","M57" to "Anillo","M63" to "Girasol","M64" to "Ojo Negro","M73" to "Asterismo de cuatro estrellas","M76" to "Pequeña Mancuerna","M77" to "Cetus A","M81" to "Galaxia de Bode","M82" to "Cigarro","M83" to "Molinillo Austral","M87" to "Virgo A","M94" to "Ojo de Gato","M97" to "Búho","M101" to "Molinillo","M102" to "Huso","M104" to "Sombrero")
    }
}
class MessierCatalog(input: InputStream) {
    val objects=input.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.map { line ->
        val p=line.split('\t'); require(p.size==9)
        MessierObject(p[0],p[1].toDouble(),p[2].toDouble(),p[3],p[4].toDoubleOrNull(),p[5].ifEmpty { null },p[6].ifEmpty { null },p[7],p[8])
    }.toList() }.also { rows ->
        require(rows.size==110 && rows.map { it.id }==(1..110).map { "M$it" })
        require(rows.all { it.ra in 0.0..360.0 && it.dec in -90.0..90.0 })
    }
    fun find(query: String, group: String="Todos"): List<MessierObject> {
        fun normalized(s: String)=Normalizer.normalize(s,Normalizer.Form.NFD).replace(Regex("\\p{M}"),"").lowercase(Locale.ROOT).replace(" ","")
        val key=normalized(query)
        return objects.filter { (group=="Todos" || it.group==group) && (key.isEmpty() || listOf(it.id,it.name,it.sourceName,it.designation,it.id.drop(1)).any { name -> normalized(name).contains(key) }) }
    }
}
