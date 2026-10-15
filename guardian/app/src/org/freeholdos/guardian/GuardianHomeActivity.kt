// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.guardian

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import org.freeholdos.guardian.application.GuardianApplicationResult
import org.freeholdos.guardian.ui.GuardianApp
import org.freeholdos.guardian.ui.GuardianRoute
import org.freeholdos.guardian.ui.GuardianUiController
import org.freeholdos.guardian.ui.GuardianUiScenario
import org.freeholdos.guardian.ui.toRetentionOption

private const val PREFS_NAME = "guardian_prefs"
private const val KEY_FIRST_RUN_SEEN = "first_run_seen"

/** Extra on the incoming Intent naming a [GuardianRoute] to start on, e.g.
 * [GuardianRoute.INCIDENT_REVIEW] from [GuardianWatcherService]'s notification
 * PendingIntent. Falls back to first-run-or-Home when absent. */
const val EXTRA_ROUTE = "org.freeholdos.guardian.EXTRA_ROUTE"

class GuardianHomeActivity : ComponentActivity() {
    // POST_NOTIFICATIONS is protectionLevel="dangerous" (confirmed against real
    // AOSP source -- see this plan's Task 7); declaring it in the manifest alone
    // never grants it. Without this request, GuardianWatcherService's
    // notify() calls silently no-op on Android 13+ and detections are never
    // seen. Registered unconditionally as a field per the Activity Result API's
    // own requirement (must happen before STARTED), fired once from onCreate.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val app = application as GuardianApplication
        val components = app.components()
        val controller = GuardianUiController(
            object : GuardianUiScenario {
                override fun detectBackgroundLocation() = app.peekDetection()
                override fun approve(incidentId: String, actionId: String) =
                    components.applicationService().approve(incidentId, actionId)
                override fun undo(auditId: String) =
                    components.applicationService().undo(auditId)
                override fun overview() =
                    components.applicationService().overview()
            },
        )

        val prefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val requestedRoute = intent.getStringExtra(EXTRA_ROUTE)?.let { name ->
            runCatching { GuardianRoute.valueOf(name) }.getOrNull()
        }
        val startRoute = requestedRoute
            ?: if (prefs.getBoolean(KEY_FIRST_RUN_SEEN, false)) GuardianRoute.HOME else GuardianRoute.FIRST_RUN

        setContent {
            GuardianApp(
                controller = controller,
                startRoute = startRoute,
                onFirstRunSeen = { prefs.edit().putBoolean(KEY_FIRST_RUN_SEEN, true).apply() },
                onDetectionHandled = { app.clearDetection() },
                // The incident-review screen shows the detection's own
                // explanation facts and timestamp, which live on the
                // GuardianIncidentV1 the controller consumes and discards --
                // so the UI reads the same queue head directly. peekDetection()
                // is non-destructive; only onDetectionHandled above removes it.
                pendingIncident = {
                    (app.peekDetection() as? GuardianApplicationResult.DetectedIncident)?.incident()
                },
                pendingIncidentCount = { app.pendingDetectionCount() },
                retention = components.retentionPeriod().toRetentionOption(),
            )
        }
    }
}
