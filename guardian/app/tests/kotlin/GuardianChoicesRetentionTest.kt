package org.sovereignos.guardianui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.sovereignos.guardianui.theme.GuardianTheme
import org.junit.Rule
import org.junit.Test

/**
 * SovereignOS-specific tests for Choices' retention section — the one place on
 * this build that used to imply (on Trust, before the control moved here)
 * that the device enforces a deletion schedule it never runs.
 *
 * Kept separate from the reference module's own `GuardianChoicesScreenTest`,
 * which is copied in unchanged and must stay that way. Runner: the JVM
 * harness in `guardian/app/tests/README.md`.
 */
class GuardianChoicesRetentionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val notEnforcedNote =
        "This is the recorded setting only. Guardian is not yet deleting older records on this schedule."

    private val baseState = GuardianChoicesUiState(
        watchToggles = listOf(GuardianWatchToggle("Background location", "Apps checking where you are while closed", enabled = true)),
    )

    @Test
    fun `an unenforced retention period is labelled a setting and says it is not acted on`() {
        composeRule.setContent {
            GuardianTheme {
                GuardianChoicesScreen(
                    state = baseState.copy(
                        retention = GuardianRetentionOption.ThirtyDays,
                        retentionChangeable = false,
                        retentionEnforced = false,
                    ),
                    onWatchToggle = { _, _ -> },
                    onNotifyImmediatelyToggle = {},
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
                GuardianChoicesScreen(
                    state = baseState.copy(retention = GuardianRetentionOption.ThirtyDays),
                    onWatchToggle = { _, _ -> },
                    onNotifyImmediatelyToggle = {},
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
