package org.msrobotika.astrotab.data

import java.text.SimpleDateFormat
import java.util.*

const val HOUR = 3600000L
const val MINUTE = 60000L

data class Location(val latitude: Double, val longitude: Double, val origin: String, val savedAt: Long) {
    init { require(latitude.isFinite() && latitude in -90.0..90.0); require(longitude.isFinite() && longitude in -180.0..180.0) }
    val key: String get() = String.format(Locale.US,"%.6f,%.6f",latitude,longitude)
}
/** Solo estados cerrados: nunca incluye coordenadas ni texto libre del proveedor. */
object LocationStatus {
    fun label(location: Location?): String = when {
        location==null -> "sin ubicación"
        location.origin=="manual" -> "ubicación manual"
        else -> "ubicación aproximada"
    }
}
object Coordinates {
    fun number(text: String): Double? = text.trim().replace('−','-').replace(',','.').toDoubleOrNull()?.takeIf { it.isFinite() }
    fun parse(latitude: String, longitude: String, now: Long, origin: String="manual"): Location? {
        val lat=number(latitude) ?: return null; val lon=number(longitude) ?: return null
        if(lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return Location(lat,lon,origin,now)
    }
}
/** Persistencia desacoplada de Android para comprobar reinicio y validación en JVM. */
interface PreferenceStore {
    fun get(key: String): String?
    fun put(values: Map<String,String?>)
}
class Settings(private val store: PreferenceStore) {
    var zoneId: String?
        get()=store.get("zoneId")?.takeIf { it in TimeZone.getAvailableIDs() }
        set(value) { require(value==null || value in TimeZone.getAvailableIDs()); store.put(mapOf("zoneId" to value)) }
    val zone: TimeZone get()=zoneId?.let(TimeZone::getTimeZone) ?: TimeZone.getDefault()
    private var legacyEquipment: org.msrobotika.astrotab.astro.Equipment
        get() = runCatching {
            org.msrobotika.astrotab.astro.Equipment(store.get("focal")?.toDoubleOrNull() ?: 50.0,
                store.get("aperture")?.toDoubleOrNull() ?: 2.8,
                org.msrobotika.astrotab.astro.SensorFormat.valueOf(store.get("sensorFormat") ?: "FULL"),
                store.get("pixel")?.toDoubleOrNull() ?: 4.3,store.get("mobileWidth")?.toDoubleOrNull() ?: 6.4,store.get("mobileHeight")?.toDoubleOrNull() ?: 4.8)
        }.getOrDefault(org.msrobotika.astrotab.astro.Equipment())
        set(value) { store.put(mapOf("focal" to value.focal.toString(),"aperture" to value.aperture.toString(),"sensorFormat" to value.sensor.name,"pixel" to value.pixel.toString(),"mobileWidth" to value.mobileWidth.toString(),"mobileHeight" to value.mobileHeight.toString())) }
    val profiles: List<EquipmentProfile>
        get()=runCatching { EquipmentProfiles.decode(store.get("equipmentProfiles") ?: "") }.getOrNull()?.takeIf {it.isNotEmpty()}
            ?: listOf(EquipmentProfile("legacy","Mi equipo",legacyEquipment))
    var activeProfileId: String
        get()=store.get("activeEquipment")?.takeIf {id -> profiles.any {it.id==id}} ?: profiles.first().id
        set(value) { require(profiles.any {it.id==value}); store.put(mapOf("activeEquipment" to value)) }
    val activeProfile get()=profiles.first {it.id==activeProfileId}
    var equipment: org.msrobotika.astrotab.astro.Equipment
        get()=activeProfile.equipment
        set(value) { saveProfile(activeProfile.copy(equipment=value)) }
    fun saveProfile(profile: EquipmentProfile) {
        val all=profiles.toMutableList(); val i=all.indexOfFirst {it.id==profile.id}
        if(i<0) all.add(profile) else all[i]=profile
        store.put(mapOf("equipmentProfiles" to EquipmentProfiles.encode(all)))
    }
    fun removeProfile(id: String) {
        val all=profiles.filter {it.id!=id}; require(all.isNotEmpty())
        store.put(mapOf("equipmentProfiles" to EquipmentProfiles.encode(all)))
        if(all.none {it.id==activeProfileId}) activeProfileId=all.first().id
    }
    var minimumAltitude: Double
        get()=(store.get("minimumAltitude")?.toDoubleOrNull() ?: 30.0).takeIf { it.isFinite() && it in 0.0..90.0 } ?: 30.0
        set(value) { require(value.isFinite() && value in 0.0..90.0); store.put(mapOf("minimumAltitude" to value.toString())) }
    var showMessier: Boolean
        get()=store.get("showMessier")=="true"
        set(value) { store.put(mapOf("showMessier" to value.toString())) }
    var location: Location?
        get() = Coordinates.parse(store.get("latitude") ?: "",store.get("longitude") ?: "",store.get("locationDate")?.toLongOrNull() ?: 0,store.get("origin") ?: "manual")
        set(value) { store.put(mapOf("latitude" to value?.latitude?.toString(),"longitude" to value?.longitude?.toString(),"origin" to value?.origin,"locationDate" to value?.savedAt?.toString())) }
    var bortle: Int
        get() = (store.get("bortle")?.toIntOrNull() ?: 5).coerceIn(1,9)
        set(value) { require(value in 1..9); store.put(mapOf("bortle" to value.toString())) }
    var networkEnabled: Boolean
        get() = store.get("network")=="true"
        set(value) { store.put(mapOf("network" to value.toString())) }
    var red: Boolean
        get() = store.get("red")=="true"
        set(value) { store.put(mapOf("red" to value.toString())) }
    var autoRed: Boolean
        get() = store.get("autoRed")=="true"
        set(value) { store.put(mapOf("autoRed" to value.toString())) }
    var sensorEnabled: Boolean
        get() = store.get("sensor")!="false"
        set(value) { store.put(mapOf("sensor" to value.toString())) }
    var kioskEnabled: Boolean
        get() = store.get("kiosk")=="true"
        set(value) { store.put(mapOf("kiosk" to value.toString())) }
}
object Utc {
    fun parse(value: String): Long {
        val formats=if(value.endsWith("Z")) listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'","yyyy-MM-dd'T'HH:mm:ss'Z'","yyyy-MM-dd'T'HH:mm'Z'")
            else listOf("yyyy-MM-dd HH:mm:ss.SSS","yyyy-MM-dd HH:mm:ss","yyyy-MM-dd HH:mm","yyyy-MM-dd'T'HH:mm:ss","yyyy-MM-dd'T'HH:mm")
        for(pattern in formats) {
            val parser=SimpleDateFormat(pattern,Locale.US).apply { timeZone=TimeZone.getTimeZone("UTC"); isLenient=false }
            val position=java.text.ParsePosition(0)
            val date=parser.parse(value,position)
            if(date!=null && position.index==value.length) return date.time
        }
        throw IllegalArgumentException("Fecha UTC inválida: $value")
    }
    fun nightStart(now: Long, zone: TimeZone): Long {
        val c=Calendar.getInstance(zone).apply { timeInMillis=now }
        if(c.get(Calendar.HOUR_OF_DAY)<7) c.add(Calendar.DAY_OF_MONTH,-1)
        c.set(Calendar.HOUR_OF_DAY,18); c.set(Calendar.MINUTE,0); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0)
        return c.timeInMillis
    }
}
