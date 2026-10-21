// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import org.freeholdos.passwords.data.DomainIndexStore
import org.freeholdos.passwords.data.VaultRepository
import org.freeholdos.passwords.keys.AndroidKeyGateway
import org.freeholdos.passwords.lock.LockState
import org.freeholdos.passwords.lock.AndroidClock
import org.freeholdos.passwords.lock.AndroidEnvironment
import org.freeholdos.passwords.lock.LockPolicy
import org.freeholdos.passwords.lock.PrefsPolicyStore
import org.freeholdos.passwords.lock.SessionManager
import org.freeholdos.passwords.lock.VaultFiles

/**
 * Builds the one [SessionManager] of the process, also when the process starts for the autofill service and no screen
 * exists. The vault locks when the screen turns off and one minute after the last screen has left the foreground.
 */
class PasswordsApplication : Application() {
    lateinit var session: SessionManager
        private set
    lateinit var indexStore: DomainIndexStore
        private set
    lateinit var repository: VaultRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val gateway = AndroidKeyGateway()
        val files = VaultFiles(filesDir)
        val policy = LockPolicy(AndroidClock(), PrefsPolicyStore(getSharedPreferences("lock_policy", MODE_PRIVATE)), AndroidEnvironment(this, gateway, files))
        session = SessionManager(this, files, gateway, policy)
        indexStore = DomainIndexStore(filesDir, gateway)
        repository = VaultRepository(files, session, indexStore)
        // The domain index is rebuilt from the open vault at every unlock, so a damaged or stale one repairs itself.
        session.observe { if (it is LockState.Unlocked) indexStore.rebuild(it.handle) }

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = session.onScreenOff()
        }, IntentFilter(Intent.ACTION_SCREEN_OFF))

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = session.onActivityStarted()
            override fun onActivityStopped(activity: Activity) = session.onActivityStopped()
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}

val Context.session: SessionManager get() = (applicationContext as PasswordsApplication).session
