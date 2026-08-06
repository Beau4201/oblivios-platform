package org.aegisos.guardian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.aegisos.guardian.ui.GuardianReferenceScreen
import org.aegisos.guardian.ui.GuardianUiController
import org.aegisos.guardian.ui.GuardianUiScenario

class GuardianActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as GuardianApplication
        val components = app.components()
        val controller = GuardianUiController(
            object : GuardianUiScenario {
                override fun detectBackgroundLocation() = app.consumeDetection()
                override fun approve(incidentId: String, actionId: String) =
                    components.applicationService().approve(incidentId, actionId)
                override fun undo(auditId: String) =
                    components.applicationService().undo(auditId)
                override fun overview() =
                    components.applicationService().overview()
            },
        )
        setContent {
            GuardianReferenceScreen(controller = controller, onDone = { finish() })
        }
    }
}
