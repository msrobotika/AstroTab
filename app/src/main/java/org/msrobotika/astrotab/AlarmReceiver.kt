package org.msrobotika.astrotab

import android.app.*
import android.content.*
import android.os.Build
import org.msrobotika.astrotab.data.*

internal fun alarmPreferences(context: Context): PreferenceStore {
    val p=context.getSharedPreferences("astrotab",Context.MODE_PRIVATE)
    return object: PreferenceStore {
        override fun get(key: String)=p.getString(key,null)
        override fun put(values: Map<String,String?>) {val edit=p.edit();values.forEach {(k,v)->if(v==null)edit.remove(k)else edit.putString(k,v)};edit.apply()}
    }
}
object AndroidAlarms {
    private fun pending(context: Context,id: Int)=PendingIntent.getBroadcast(context,id,Intent(context,AlarmReceiver::class.java).putExtra("alarmId",id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    /** API23 no precisa permiso. API31+ sin autorización exacta usa alarma inexacta + comprobación visible. */
    @android.annotation.SuppressLint("MissingPermission") // Guardas SDK/canScheduleExactAlarms; posible revocación capturada abajo.
    fun schedule(context: Context,alarm: AppAlarm): Boolean {
        if(!alarm.enabled){cancel(context,alarm.id);return true}
        val at=alarm.time ?: return true
        val manager=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if(Build.VERSION.SDK_INT>=31) {
                if(manager.canScheduleExactAlarms())manager.setExact(AlarmManager.RTC_WAKEUP,at,pending(context,alarm.id))
                else {manager.set(AlarmManager.RTC_WAKEUP,at,pending(context,alarm.id));return false}
            } else manager.setExact(AlarmManager.RTC_WAKEUP,at,pending(context,alarm.id))
        } catch(_: SecurityException) {manager.set(AlarmManager.RTC_WAKEUP,at,pending(context,alarm.id));return false}
        return true
    }
    fun cancel(context: Context,id: Int) {(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(context,id))}
}
class AlarmReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val store=AlarmStore(alarmPreferences(context));val alarm=store.read().firstOrNull {it.id==intent.getIntExtra("alarmId",-1)} ?: return
        if(!AlarmPolicy.due(alarm,System.currentTimeMillis()))return
        // Persistir hasta que MainActivity lo presente: permite recuperar si el SO bloquea abrir detrás.
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("alarmId",alarm.id))
    }
}
