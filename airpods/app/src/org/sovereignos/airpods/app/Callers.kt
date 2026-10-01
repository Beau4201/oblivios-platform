package org.sovereignos.airpods.app

import android.os.Binder
import android.os.Process

internal object Callers {
    /**
     * Settings runs as the system uid and binds our services as that uid; our own process may call
     * too. The services are also exported="false", so this is a second, independent check.
     */
    fun enforceSettings() {
        val uid = Binder.getCallingUid()
        if (uid != Process.SYSTEM_UID && uid != Process.myUid()) {
            throw SecurityException("caller not allowed")
        }
    }
}
