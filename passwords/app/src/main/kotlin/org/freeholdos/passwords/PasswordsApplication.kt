// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.freeholdos.passwords.data.DomainIndexStore
import org.freeholdos.passwords.fill.AndroidPackageFacts
import org.freeholdos.passwords.fill.OriginPolicy
import org.freeholdos.passwords.fill.PendingSaveSlot
import org.freeholdos.passwords.fill.PrefsTrustStore
import org.freeholdos.passwords.data.VaultRepository
import org.freeholdos.passwords.keys.AndroidKeyGateway
import org.freeholdos.passwords.lock.LockState
import org.freeholdos.passwords.lock.AndroidClock
import org.freeholdos.passwords.lock.AndroidEnvironment
import org.freeholdos.passwords.lock.LockPolicy
import org.freeholdos.passwords.lock.PrefsPolicyStore
import org.freeholdos.passwords.lock.SessionManager
import org.freeholdos.passwords.lock.VaultFiles
import org.freeholdos.passwords.ui.LogoSource

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
    val logos: LogoSource by lazy { LogoSource(this) }

    /** Which package may report a website (fill/Origin.kt): Vanadium of the image and the browsers the user trusts. */
    val packageFacts: AndroidPackageFacts by lazy { AndroidPackageFacts(this) }
    val originPolicy: OriginPolicy by lazy { OriginPolicy(packageFacts, PrefsTrustStore(getSharedPreferences("trusted_browsers", MODE_PRIVATE))) }

    /** The login submitted while the vault was locked, until the unlock that lets it be saved. */
    val pendingSave = PendingSaveSlot(schedule = { ms, run -> Handler(Looper.getMainLooper()).postDelayed({ run() }, ms) }) { SystemClock.elapsedRealtime() }

    override fun onCreate() {
        super.onCreate()
        val gateway = AndroidKeyGateway()
        val files = VaultFiles(filesDir)
        val policy = LockPolicy(AndroidClock(), PrefsPolicyStore(getSharedPreferences("lock_policy", MODE_PRIVATE)), AndroidEnvironment(this, gateway, files))
        session = SessionManager(this, files, gateway, policy)
        indexStore = DomainIndexStore(filesDir, gateway)
        repository = VaultRepository(files, session, indexStore)
        // The domain index is rebuilt from the open vault at every unlock, so a damaged or stale one repairs itself.
        // A login that waits for the unlock does not outlive a lock of the open vault (P-07); it is taken right after an unlock.
        var wasUnlocked = false
        session.observe {
            if (it is LockState.Unlocked) indexStore.rebuild(it.handle) else if (wasUnlocked) pendingSave.clear()
            wasUnlocked = it is LockState.Unlocked
        }

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                pendingSave.clear()
                session.onScreenOff()
            }
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
