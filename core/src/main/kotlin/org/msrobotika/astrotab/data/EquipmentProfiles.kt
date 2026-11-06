package org.msrobotika.astrotab.data

import org.json.*
import org.msrobotika.astrotab.astro.*

data class EquipmentProfile(val id: String,val name: String,val equipment: Equipment) {
    init {require(id.length in 1..80 && name.isNotBlank() && name.length<=80)}
}
object EquipmentProfiles {
    fun encode(profiles: List<EquipmentProfile>): String {
        require(profiles.size in 1..32 && profiles.map {it.id}.distinct().size==profiles.size)
        val rows=JSONArray()
        for(p in profiles) { val e=p.equipment
            rows.put(JSONObject().put("id",p.id).put("name",p.name).put("focal",e.focal).put("aperture",e.aperture)
                .put("sensor",e.sensor.name).put("pixel",e.pixel).put("width",e.mobileWidth).put("height",e.mobileHeight)
                .put("tracking",e.tracking).put("equatorial",e.equatorial))
        }; return rows.toString()
    }
    fun decode(raw: String): List<EquipmentProfile> {
        require(raw.length<=32768); val rows=JSONArray(raw); require(rows.length() in 1..32)
        return (0 until rows.length()).map { i -> val o=rows.getJSONObject(i)
            EquipmentProfile(o.getString("id"),o.getString("name"),Equipment(o.getDouble("focal"),o.getDouble("aperture"),SensorFormat.valueOf(o.getString("sensor")),
                o.getDouble("pixel"),o.getDouble("width"),o.getDouble("height"),o.optBoolean("tracking"),o.optBoolean("equatorial")))
        }.also {require(it.map {p -> p.id}.distinct().size==it.size)}
    }
}
