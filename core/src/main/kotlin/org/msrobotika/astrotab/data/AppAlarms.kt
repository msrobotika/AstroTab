package org.msrobotika.astrotab.data

import org.json.*
import org.msrobotika.astrotab.astro.*
import org.msrobotika.astrotab.satellites.*

enum class AlarmKind { ASTRONOMICAL_DUSK, OBJECT_ALTITUDE, ISS, KP, MOONRISE }
data class AppAlarm(val id: Int,val kind: AlarmKind,val label: String,val time: Long?,val sound: Boolean=false,val target: String?=null,val enabled: Boolean=true) {
    init {require(id>0 && label.length in 1..200);require(time!=null || kind==AlarmKind.KP)}
}
object AlarmPolicy {
    fun objectRise(now: Long,eq: Astro.Eq,place: Location,minimum: Double): Long? {
        require(minimum in 0.0..90.0)
        fun alt(t: Long)=AstroPhoto.horizon(AstroPhoto.precess(eq,t),t,place).alt
        var t=now;var previous=alt(t)
        while(t<now+48*HOUR) {val next=t+5*MINUTE;val value=alt(next)
            if(previous<minimum && value>=minimum){var lo=t;var hi=next;repeat(12){val m=(lo+hi)/2;if(alt(m)>=minimum)hi=m else lo=m};return (lo+hi)/2}
            t=next;previous=value
        };return null
    }
    fun next(kind: AlarmKind,now: Long,events: List<AstroPhoto.Event>,plans: List<ObjectPlan>,passes: List<SatellitePass>,target: String?=null): Long? = when(kind) {
        AlarmKind.ASTRONOMICAL_DUSK -> events.filter {it.label.startsWith("Astronómico") && it.label.endsWith("descendente")}.map {it.time}
        AlarmKind.MOONRISE -> events.filter {it.label=="Salida de la Luna"}.map {it.time}
        AlarmKind.OBJECT_ALTITUDE -> plans.filter {it.target.id==target}.flatMap {it.windows}.map {it.start}
        AlarmKind.ISS -> passes.filter {it.tle.number==25544}.map {it.start.time-5*MINUTE}
        AlarmKind.KP -> emptyList()
    }.filter {it>now}.minOrNull()
    fun kpReached(data: KpData?,now: Long,threshold: Double): Boolean =
        DataPolicy.currentKp(data,now)?.let {it.value>=threshold}==true
    fun due(alarm: AppAlarm,now: Long,kp: Boolean=false)=alarm.enabled && (alarm.time?.let {now>=it && now-it<=30*MINUTE} ?: (alarm.kind==AlarmKind.KP && kp))
}
class AlarmStore(private val store: PreferenceStore) {
    fun read(): List<AppAlarm> = runCatching {
        val raw=store.get("alarms") ?: "[]";require(raw.length<=65536);val rows=JSONArray(raw);require(rows.length()<=100)
        (0 until rows.length()).map {i ->val o=rows.getJSONObject(i)
            AppAlarm(o.getInt("id"),AlarmKind.valueOf(o.getString("kind")),o.getString("label"),if(o.isNull("time"))null else o.getLong("time"),o.optBoolean("sound"),if(o.isNull("target"))null else o.getString("target"),o.optBoolean("enabled",true))}
    }.getOrDefault(emptyList())
    fun save(alarms: List<AppAlarm>) {require(alarms.size<=100 && alarms.map {it.id}.distinct().size==alarms.size)
        val rows=JSONArray();alarms.forEach {rows.put(JSONObject().put("id",it.id).put("kind",it.kind.name).put("label",it.label).put("time",it.time ?: JSONObject.NULL).put("sound",it.sound).put("target",it.target ?: JSONObject.NULL).put("enabled",it.enabled))}
        store.put(mapOf("alarms" to rows.toString()))
    }
    fun add(kind: AlarmKind,label: String,time: Long?,sound: Boolean=false,target: String?=null): AppAlarm {
        val all=read();val id=(all.maxOfOrNull {it.id} ?: 0)+1
        val a=AppAlarm(id,kind,label,time,sound,target);save(all+a);return a
    }
    fun remove(id: Int) {save(read().filter {it.id!=id})}
    fun setEnabled(id: Int,enabled: Boolean): AppAlarm? {
        val all=read().map {if(it.id==id)it.copy(enabled=enabled)else it};save(all);return all.firstOrNull {it.id==id}
    }
}
