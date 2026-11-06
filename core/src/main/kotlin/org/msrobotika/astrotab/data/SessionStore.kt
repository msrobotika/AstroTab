package org.msrobotika.astrotab.data

import org.json.*
import org.msrobotika.astrotab.astro.*

class SessionStore(private val cache: CacheStore) {
    fun read(): List<ObservingSession> = cache.read("sessions")?.let { decode(it.payload) } ?: emptyList()
    fun save(value: ObservingSession) {
        val all=read().toMutableList(); val i=all.indexOfFirst {it.id==value.id}; if(i<0)all.add(value)else all[i]=value
        require(all.size<=50);cache.write("sessions",Cached(System.currentTimeMillis(),encode(all)))
    }
    fun remove(id: String) {cache.write("sessions",Cached(System.currentTimeMillis(),encode(read().filter {it.id!=id})))}
    companion object {
        fun encode(all: List<ObservingSession>): String {
            require(all.size<=50);val rows=JSONArray()
            for(s in all) {val targets=JSONArray();s.requests.forEach {targets.put(JSONObject().put("id",it.id).put("minutes",it.integrationMinutes))}
                rows.put(JSONObject().put("id",s.id).put("name",s.name).put("night",s.night).put("targets",targets))}
            return rows.toString().also {require(it.toByteArray().size<=512*1024)}
        }
        fun decode(raw: String): List<ObservingSession> {
            require(raw.toByteArray().size<=512*1024);val rows=JSONArray(raw);require(rows.length()<=50)
            return (0 until rows.length()).map {i -> val o=rows.getJSONObject(i);val targets=o.getJSONArray("targets");require(targets.length()<=110)
                ObservingSession(o.getString("id"),o.getString("name"),o.getLong("night"),(0 until targets.length()).map {j ->val t=targets.getJSONObject(j);TargetRequest(t.getString("id"),t.getInt("minutes"))})}
        }
    }
}
