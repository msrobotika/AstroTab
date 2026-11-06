package org.msrobotika.astrotab.kiosk

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.msrobotika.astrotab.data.PreferenceStore

class PinGate(private val store: PreferenceStore, private val clock: ()->Long=System::currentTimeMillis) {
    val configured: Boolean get()=store.get("pinHash")!=null && store.get("pinSalt")!=null
    private fun hex(bytes: ByteArray)=bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun unhex(value: String)=ByteArray(value.length/2) { value.substring(it*2,it*2+2).toInt(16).toByte() }
    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec=PBEKeySpec(pin.toCharArray(),salt,60000,256)
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    fun create(pin: String) {
        check(!configured) { "El PIN ya está configurado" }
        require(pin.matches(Regex("[0-9]{6,12}"))) { "Usa entre 6 y 12 cifras" }
        val salt=ByteArray(16).also { SecureRandom().nextBytes(it) }
        store.put(mapOf("pinSalt" to hex(salt),"pinHash" to hex(hash(pin,salt)),"pinFailures" to "0","pinBlockedUntil" to "0"))
    }
    fun verify(pin: String): Boolean {
        val now=clock(); val until=store.get("pinBlockedUntil")?.toLongOrNull() ?: 0
        if(!configured || now<until) return false
        val valid=runCatching { MessageDigest.isEqual(hash(pin,unhex(store.get("pinSalt")!!)),unhex(store.get("pinHash")!!)) }.getOrDefault(false)
        if(valid) store.put(mapOf("pinFailures" to "0","pinBlockedUntil" to "0"))
        else {
            val failures=(store.get("pinFailures")?.toIntOrNull() ?: 0)+1
            store.put(mapOf("pinFailures" to (if(failures>=5) 0 else failures).toString(),"pinBlockedUntil" to (if(failures>=5) now+30000 else 0).toString()))
        }
        return valid
    }
}
object KioskPolicy {
    fun shouldLock(owner: Boolean, permitted: Boolean, enabled: Boolean, pin: Boolean, maintenance: Boolean)=owner && permitted && enabled && pin && !maintenance
    fun mayExit(pinValid: Boolean)=pinValid
}
