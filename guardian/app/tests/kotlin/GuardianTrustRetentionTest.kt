package org.aegisos.guardianui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.aegisos.guardianui.theme.GuardianTheme
import org.junit.Rule
import org.junit.Test

/**
 * AegisOS-specific tests for the Trust screen's retention section — the one
 * place on a screen headed "Every claim here points to a real record on this
 * device" that used to imply the device enforces a deletion schedule it never
 * runs.
 *
 * Kept separate from the reference module's own `GuardianTrustScreenTest`,
 * which is copied in unchanged and must stay that way. Runner: the JVM
 * harness in `guardian/app/tests/README.md`.
 */
class GuardianTrustRetentionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val notEnforcedNote =
        "This is the recorded setting only. Guardian is not yet deleting older records on this schedule."

    @Test
    fun `an unenforced retention period is labelled a setting and says it is not acted on`() {
        composeRule.setContent {
            GuardianTheme {
                GuardianTrustScreen(
                    state = GuardianTrustUiState(
                        retention = GuardianRetentionOption.ThirtyDays,
                        retentionChangeable = false,
                        retentionEnforced = false,
                    ),
                    onRetentionSelected = {},
                    onNavSelect = {},
                )
            }
        }

        composeRule.onNodeWithText("HISTORY RETENTION SETTING").assertExists()
        composeRule.onNodeWithText("KEEP HISTORY FOR").assertDoesNotExist()
        composeRule.onNodeWithText(notEnforcedNote).assertExists()
        // The real recorded value is still shown: it is a true fact, it is
        // only the enforcement that is not happening.
        composeRule.onNodeWithText("30 days").assertExists()
    }

    @Test
    fun `an enforced retention period keeps the reference wording and adds no caveat`() {
        composeRule.setContent {
            GuardianTheme {
                GuardianTrustScreen(
                    state = GuardianTrustUiState(retention = GuardianRetentionOption.ThirtyDays),
                    onRetentionSelected = {},
                    onNavSelect = {},
                )
            }
        }

        composeRule.onNodeWithText("KEEP HISTORY FOR").assertExists()
        composeRule.onNodeWithText("HISTORY RETENTION SETTING").assertDoesNotExist()
        composeRule.onNodeWithText(notEnforcedNote).assertDoesNotExist()
    }
}
