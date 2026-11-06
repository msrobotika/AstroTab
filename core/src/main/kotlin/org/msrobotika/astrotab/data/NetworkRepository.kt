package org.msrobotika.astrotab.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory
import java.net.URL

/** No realiza peticiones al construirlo. Todas las llamadas son explícitas y fuera de Canvas. */
fun interface Transport { fun get(url: String): String }
class HttpsTransport(private val socketFactory: SSLSocketFactory?=null) : Transport {
    override fun get(url: String): String {
        require(url.startsWith("https://")) { "Solo se permite HTTPS" }
        val connection=URL(url).openConnection() as HttpsURLConnection
        socketFactory?.let { connection.sslSocketFactory=it }
        connection.connectTimeout=8000; connection.readTimeout=8000; connection.instanceFollowRedirects=false
        connection.setRequestProperty("User-Agent","AstroTab/0.7.0 (MS Robotika; no comercial)")
        try {
            require(connection.responseCode==200) { "Respuesta HTTP ${connection.responseCode}" }
            require(connection.contentLength<=MAX_BYTES) { "Respuesta demasiado grande" }
            return connection.inputStream.use { readBounded(it) }
        } finally { connection.disconnect() }
    }
    companion object {
        const val MAX_BYTES=512*1024
        fun readBounded(input: InputStream): String {
            val out=ByteArrayOutputStream(); val chunk=ByteArray(8192)
            while(true) {
                val count=input.read(chunk); if(count<0) break
                require(out.size()+count<=MAX_BYTES) { "Respuesta demasiado grande" }
                out.write(chunk,0,count)
            }
            return out.toString("UTF-8")
        }
    }
}
data class Cached(val fetchedAt: Long, val payload: String)
interface CacheStore { fun read(key: String): Cached?; fun write(key: String, value: Cached) }
class FileCache(private val directory: File): CacheStore {
    private fun file(key: String): File { require(key.matches(Regex("[a-zA-Z0-9_.-]+"))); return File(directory,key) }
    @Synchronized override fun read(key: String): Cached? = try {
        val target=file(key)
        DataInputStream(FileInputStream(if(target.exists()) target else file("$key.bak"))).use { stream ->
            val fetched=stream.readLong(); val size=stream.readInt()
            require(size in 0..HttpsTransport.MAX_BYTES)
            val bytes=ByteArray(size); stream.readFully(bytes); require(stream.read()==-1)
            Cached(fetched,String(bytes,Charsets.UTF_8))
        }
    } catch(_: Exception) { null }
    @Synchronized override fun write(key: String, value: Cached) {
        directory.mkdirs()
        val bytes=value.payload.toByteArray(Charsets.UTF_8); require(bytes.size<=HttpsTransport.MAX_BYTES)
        val target=file(key); val temporary=file("$key.tmp")
        FileOutputStream(temporary).use { output ->
            val stream=DataOutputStream(output); stream.writeLong(value.fetchedAt); stream.writeInt(bytes.size); stream.write(bytes); stream.flush(); output.fd.sync()
        }
        // Sustitución atómica en Android/Linux; conservar caché anterior si falla.
        if(!temporary.renameTo(target)) {
            val previous=file("$key.bak")
            if(previous.exists()) check(previous.delete())
            check(!target.exists() || target.renameTo(previous)) { "No se ha podido conservar la caché anterior" }
            if(!temporary.renameTo(target)) { previous.renameTo(target); error("No se ha podido guardar la caché") }
            previous.delete()
        }
    }
}
data class Cloud(val time: Long, val cover: Double, val temperature: Double?=null, val humidity: Double?=null,
    val dewPoint: Double?=null, val wind: Double?=null, val low: Double?=null, val mid: Double?=null,
    val high: Double?=null, val rain: Double?=null, val gusts: Double?=null, val visibility: Double?=null,
    val pressure: Double?=null, val jet: Double?=null)

object Weather {
    fun dewRisk(temperature: Double?, dewPoint: Double?): String =
        if(temperature==null || dewPoint==null) "sin datos" else if(temperature-dewPoint<3) "alto" else "bajo"
    fun dewFactor(temperature: Double?, dewPoint: Double?)=if(dewRisk(temperature,dewPoint)=="alto") 0.65 else 1.0
    fun windFactor(wind: Double?)=when { wind==null -> 1.0; wind>=30 -> 0.4; wind>=15 -> 0.7; else -> 1.0 }
}
enum class KpKind { OBSERVED, ESTIMATED, PREDICTED }
data class KpSample(val time: Long, val value: Double, val kind: KpKind, val duration: Long=3*HOUR)
data class CloudData(val fetchedAt: Long, val locationKey: String, val hours: List<Cloud>)
data class KpData(val fetchedAt: Long, val samples: List<KpSample>)
enum class Freshness(val label: String) { FRESH("actualizado"), STALE("desactualizado"), EXPIRED("caducado"), MISSING("sin datos") }
object DataPolicy {
    fun weatherAt(data: CloudData?, time: Long, now: Long, location: Location, enabled: Boolean): Cloud? {
        if(!enabled || data==null || data.locationKey!=location.key || !usable(data.fetchedAt,now)) return null
        return data.hours.lastOrNull { time>=it.time && time<it.time+HOUR }
    }
    fun freshness(fetched: Long?, now: Long): Freshness = when {
        fetched==null -> Freshness.MISSING
        now<fetched-5*MINUTE || now-fetched>=3*HOUR -> Freshness.EXPIRED
        now-fetched>=HOUR -> Freshness.STALE
        else -> Freshness.FRESH
    }
    fun usable(fetched: Long, now: Long) = freshness(fetched,now) in listOf(Freshness.FRESH,Freshness.STALE)
    fun cloudAt(data: CloudData?, time: Long, now: Long, location: Location): Double? {
        if(data==null || data.locationKey!=location.key || !usable(data.fetchedAt,now)) return null
        return data.hours.lastOrNull { time>=it.time && time<it.time+HOUR }?.cover
    }
    fun currentKp(data: KpData?, now: Long): KpSample? {
        if(data==null || !usable(data.fetchedAt,now)) return null
        return data.samples.filter { it.kind!=KpKind.PREDICTED && now>=it.time && now<it.time+it.duration }
            .maxWithOrNull(compareBy<KpSample> { it.time }.thenBy { if(it.kind==KpKind.OBSERVED) 1 else 0 })
    }
}
object Parsers {
    fun clouds(raw: String, fetched: Long, location: Location): CloudData {
        val root=JSONObject(raw)
        require(root.optInt("utc_offset_seconds",0)==0) { "Se esperaba UTC" }
        val hourly=root.getJSONObject("hourly"); val times=hourly.getJSONArray("time"); val covers=hourly.getJSONArray("cloud_cover")
        require(times.length()==covers.length() && times.length()<=1000)
        val hours=ArrayList<Cloud>()
        fun optional(key: String, index: Int, range: ClosedFloatingPointRange<Double>): Double? {
            val array=hourly.optJSONArray(key) ?: return null
            require(array.length()==times.length())
            if(array.isNull(index)) return null
            return array.getDouble(index).also { require(it.isFinite() && it in range) }
        }
        repeat(times.length()) { i ->
            if(!covers.isNull(i)) {
                val cover=covers.getDouble(i); require(cover.isFinite() && cover in 0.0..100.0)
                hours.add(Cloud(Utc.parse(times.getString(i)),cover,optional("temperature_2m",i,-100.0..70.0),
                    optional("relative_humidity_2m",i,0.0..100.0),optional("dew_point_2m",i,-120.0..70.0),optional("wind_speed_10m",i,0.0..500.0),
                    optional("cloud_cover_low",i,0.0..100.0),optional("cloud_cover_mid",i,0.0..100.0),optional("cloud_cover_high",i,0.0..100.0),
                    optional("precipitation_probability",i,0.0..100.0),optional("wind_gusts_10m",i,0.0..500.0),optional("visibility",i,0.0..1000000.0),
                    optional("pressure_msl",i,800.0..1100.0),optional("wind_speed_250hPa",i,0.0..1000.0)))
            }
        }
        require(hours.isNotEmpty()) { "Sin nubes horarias" }
        return CloudData(fetched,location.key,hours.distinctBy { it.time }.sortedBy { it.time })
    }
    fun kp(raw: String, fetched: Long, forecast: Boolean): KpData {
        val array=JSONArray(raw); require(array.length() in 1..2000)
        // NOAA pasó en 2026 de tablas con cabecera a lista de objetos; se aceptan ambos formatos.
        if(array.opt(0) is JSONObject) {
            val samples=ArrayList<KpSample>()
            for(i in 0 until array.length()) {
                val o=array.getJSONObject(i); val keys=o.keys().asSequence().associateBy { it.lowercase(java.util.Locale.ROOT) }
                val value=o.getDouble(keys["kp"] ?: error("Falta Kp")); require(value.isFinite() && value in 0.0..9.0)
                val kind=if(!forecast) KpKind.OBSERVED else when(keys["observed"]?.let { o.getString(it).lowercase(java.util.Locale.ROOT) }) {
                    "observed" -> KpKind.OBSERVED
                    "estimated" -> KpKind.ESTIMATED
                    "predicted" -> KpKind.PREDICTED
                    else -> error("Tipo Kp desconocido")
                }
                samples.add(KpSample(Utc.parse(o.getString(keys["time_tag"] ?: error("Falta time_tag"))),value,kind))
            }
            return KpData(fetched,samples.distinctBy { Pair(it.time,it.kind) }.sortedBy { it.time })
        }
        require(array.length()>=2)
        val header=array.getJSONArray(0)
        val columns=(0 until header.length()).associateBy { header.getString(it).lowercase(java.util.Locale.ROOT) }
        val timeColumn=columns["time_tag"] ?: error("Falta time_tag")
        val valueColumn=columns["kp"] ?: error("Falta Kp")
        val kindColumn=columns["observed"]
        val samples=ArrayList<KpSample>()
        for(i in 1 until array.length()) {
            val row=array.getJSONArray(i)
            val value=row.getString(valueColumn).toDouble(); require(value.isFinite() && value in 0.0..9.0)
            val kind=if(!forecast) KpKind.OBSERVED else when(kindColumn?.let { row.getString(it).lowercase(java.util.Locale.ROOT) }) {
                "observed" -> KpKind.OBSERVED
                "estimated" -> KpKind.ESTIMATED
                "predicted" -> KpKind.PREDICTED
                else -> error("Tipo Kp desconocido")
            }
            samples.add(KpSample(Utc.parse(row.getString(timeColumn)),value,kind))
        }
        return KpData(fetched,samples.distinctBy { Pair(it.time,it.kind) }.sortedBy { it.time })
    }
    fun ip(raw: String, now: Long): Location {
        val root=JSONObject(raw); require(!root.optBoolean("error",false)) { "El proveedor no ha podido localizar la IP" }
        return Location(root.getDouble("latitude"),root.getDouble("longitude"),"IP aproximada · ipapi.co",now)
    }
}
class NetworkRepository(private val cache: CacheStore, private val transport: Transport=HttpsTransport(),
    private val onWarning: (String,Throwable)->Unit={ _,_ -> }, private val clock: ()->Long=System::currentTimeMillis) {
    @Volatile var enabled=false
    private val lastAttempt=mutableMapOf<String,Long>()
    @Volatile var clouds: CloudData?=null; private set
    @Volatile var observed: KpData?=null; private set
    @Volatile var forecast: KpData?=null; private set
    @Volatile var status="Red desactivada"; private set
    @Volatile var failures: Map<String,NetworkFailure> = emptyMap(); private set
    private fun updateStatus() {
        status=if(failures.isEmpty()) "Consulta completada · intervalo mínimo 30 min" else
            "No se ha podido actualizar: "+failures.entries.joinToString(" · ") { (key,cause) -> "${serviceName(key)}: ${cause.label}" }+" · se conserva la caché"
    }
    private fun saveFailure(key: String, error: Exception?) {
        failures=failures.toMutableMap().apply { if(error==null) remove(key) else put(key,NetworkFailure.from(error)) }.toMap()
        if(error!=null) onWarning(serviceName(key),NetworkFailure.forLog(error))
        try {
            val json=JSONObject(); failures.forEach { (service,cause) -> json.put(service,cause.name) }
            cache.write("network_errors",Cached(clock(),json.toString()))
        } catch(e: Exception) { onWarning("Diagnóstico de red",NetworkFailure.forLog(e)) }
        updateStatus()
    }
    private fun serviceName(key: String)=when(key) { "clouds" -> "Tiempo"; "observed" -> "Kp observado"; "forecast" -> "Kp previsto"; else -> "Ubicación IP" }
    @Synchronized fun load(location: Location?) {
        failures=cache.read("network_errors")?.let { saved -> runCatching {
            val json=JSONObject(saved.payload)
            listOf("clouds","observed","forecast","ip").mapNotNull { key -> json.optString(key).takeIf { it.isNotEmpty() }?.let { key to NetworkFailure.valueOf(it) } }.toMap()
        }.getOrNull() } ?: emptyMap()
        if(failures.isNotEmpty()) updateStatus()
        clouds=location?.let { place -> cache.read("clouds")?.let { c -> runCatching {
            val root=JSONObject(c.payload); require(root.getString("location")==place.key)
            Parsers.clouds(root.getString("data"),c.fetchedAt,place)
        }.getOrNull() } }
        observed=cache.read("observed")?.let { runCatching { Parsers.kp(it.payload,it.fetchedAt,false) }.getOrNull() }
        forecast=cache.read("forecast")?.let { runCatching { Parsers.kp(it.payload,it.fetchedAt,true) }.getOrNull() }
    }
    @Synchronized fun refresh(location: Location?) {
        if(!enabled) { status="Red desactivada · se conserva la caché fechada"; return }
        fun request(key: String, url: String, apply: (String,Long)->String) {
            if(!enabled) return
            val now=clock(); val attemptKey=if(key=="clouds") key+location?.key else key
            val previous=lastAttempt[attemptKey] ?: when(key) {
                "clouds" -> clouds?.takeIf { it.locationKey==location?.key }?.fetchedAt
                "observed" -> observed?.fetchedAt
                "forecast" -> forecast?.fetchedAt
                else -> null
            }
            if(previous!=null && now-previous in 0 until 30*MINUTE) return
            lastAttempt[attemptKey]=now
            try {
                val raw=transport.get(url)
                if(!enabled) return
                val stored=apply(raw,now); cache.write(key,Cached(now,stored))
                saveFailure(key,null)
            } catch(e: Exception) { saveFailure(key,e) }
        }
        if(location!=null) request("clouds","https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}&hourly=cloud_cover,cloud_cover_low,cloud_cover_mid,cloud_cover_high,precipitation_probability,temperature_2m,dew_point_2m,relative_humidity_2m,wind_speed_10m,wind_gusts_10m,visibility,pressure_msl,wind_speed_250hPa&wind_speed_unit=kmh&timezone=UTC&forecast_days=7") { raw,now ->
            val parsed=Parsers.clouds(raw,now,location); clouds=parsed
            JSONObject().put("location",location.key).put("data",raw).toString()
        }
        request("observed","https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json") { raw,now -> observed=Parsers.kp(raw,now,false); raw }
        request("forecast","https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json") { raw,now -> forecast=Parsers.kp(raw,now,true); raw }
        updateStatus()
    }
    @Synchronized fun locateByIp(explicitRequest: Boolean): Location {
        check(enabled && explicitRequest) { "La ubicación por IP necesita red y petición explícita" }
        try {
            val raw=transport.get("https://ipapi.co/json/")
            check(enabled) { "Red desactivada durante la consulta" }
            return Parsers.ip(raw,clock()).also { saveFailure("ip",null) }
        } catch(e: Exception) { saveFailure("ip",e); throw e }
    }
}
