package org.msrobotika.astrotab.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import org.msrobotika.astrotab.MainActivity
import org.msrobotika.astrotab.kiosk.KioskPolicy

class AstroAdminReceiver : DeviceAdminReceiver()

class KioskController(private val activity: Activity) {
    private val manager=activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin=ComponentName(activity,AstroAdminReceiver::class.java)
    val owner: Boolean get()=manager.isDeviceOwnerApp(activity.packageName)
    var maintenance=false; private set
    private var locked=false
    fun enter(enabled: Boolean, pinConfigured: Boolean) {
        if(!owner || !enabled || !pinConfigured || maintenance) return
        manager.setLockTaskPackages(admin,arrayOf(activity.packageName))
        val filter=IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME); addCategory(Intent.CATEGORY_DEFAULT) }
        manager.addPersistentPreferredActivity(admin,filter,ComponentName(activity,MainActivity::class.java))
        if(!locked && KioskPolicy.shouldLock(owner,manager.isLockTaskPermitted(activity.packageName),enabled,pinConfigured,maintenance)) {
            activity.startLockTask(); locked=true
        }
    }
    fun unlock(pinValid: Boolean): Boolean {
        if(!KioskPolicy.mayExit(pinValid)) return false
        val state=(activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).lockTaskModeState
        if(locked || state!=ActivityManager.LOCK_TASK_MODE_NONE) activity.stopLockTask()
        locked=false; maintenance=true
        return true
    }
    fun resume(enabled: Boolean, pinConfigured: Boolean) { maintenance=false; enter(enabled,pinConfigured) }
    fun disable() {
        check(maintenance) { "Entra en mantenimiento con PIN" }
        if(owner) {
            manager.clearPackagePersistentPreferredActivities(admin,activity.packageName)
            manager.setLockTaskPackages(admin,emptyArray())
        }
        locked=false
    }
}
