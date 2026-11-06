package org.msrobotika.astrotab.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import org.json.*

data class LogEntry(val time: Long, val target: String, val equipment: String, val frames: Int, val exposure: Double, val notes: String) {
    init { require(frames in 1..1000000 && exposure.isFinite() && exposure>0); require(listOf(target,equipment,notes).all { it.length<=4000 }) }
}
/** JSON local acotado; sustitución con copia anterior para no perder sesiones tras un fallo. */
class LogBook(private val file: File) {
    fun read(): List<LogEntry> {
        val source=if(file.exists()) file else File(file.path+".bak")
        if(!source.exists()) return emptyList()
        require(source.length()<=4*1024*1024)
        val rows=JSONArray(source.readText(Charsets.UTF_8)); require(rows.length()<=2000)
        return (0 until rows.length()).map { i -> val o=rows.getJSONObject(i)
            LogEntry(o.getLong("time"),o.getString("target"),o.getString("equipment"),o.getInt("frames"),o.getDouble("exposure"),o.getString("notes")) }
    }
    fun append(entry: LogEntry) {
        val entries=read()+entry; require(entries.size<=2000) { "Límite de 2000 sesiones: exporta la bitácora" }
        val rows=JSONArray(); entries.forEach { rows.put(JSONObject().put("time",it.time).put("target",it.target).put("equipment",it.equipment).put("frames",it.frames).put("exposure",it.exposure).put("notes",it.notes)) }
        val raw=rows.toString(); require(raw.toByteArray(Charsets.UTF_8).size<=4*1024*1024)
        file.parentFile?.mkdirs(); val temporary=File(file.path+".tmp"); val previous=File(file.path+".bak")
        java.io.FileOutputStream(temporary).use { out -> out.write(raw.toByteArray(Charsets.UTF_8)); out.fd.sync() }
        if(previous.exists()) check(previous.delete())
        check(!file.exists() || file.renameTo(previous))
        if(!temporary.renameTo(file)) { previous.renameTo(file); error("No se ha podido guardar") }
    }
    companion object {
        fun csv(entries: List<LogEntry>, zone: TimeZone): String {
            // Patrón Z clásico de Android 6; insertar ':' conserva offset ISO sin depender de X.
            val f=SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.ROOT).apply { timeZone=zone }
            fun cell(value: String): String {
                // Evitar fórmulas al abrir texto libre en una hoja de cálculo.
                val safe=if(value.trimStart().firstOrNull() in listOf('=','+','-','@')) "'"+value else value
                return "\""+safe.replace("\"","\"\"")+"\""
            }
            return "\uFEFFfecha,zona,objeto,equipo,tomas,exposicion_s,integracion_s,notas\r\n"+entries.joinToString("") { e ->
                val stamp=f.format(Date(e.time)).let { it.dropLast(2)+":"+it.takeLast(2) }
                listOf(stamp,zone.id,e.target,e.equipment,e.frames.toString(),e.exposure.toString(),(e.frames*e.exposure).toString(),e.notes).joinToString(",",transform=::cell)+"\r\n"
            }
        }
    }
}
