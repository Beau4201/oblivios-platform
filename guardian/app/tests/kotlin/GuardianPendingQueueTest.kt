package org.aegisos.guardian.ui

import androidx.activity.compose.HarnessBackDispatcher
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.aegisos.guardian.application.GuardianApplicationResult
import org.aegisos.guardian.contracts.GuardianIncidentV1
import org.aegisos.guardian.overview.GuardianOverviewSnapshot
import org.aegisos.guardian.remediation.RemediationOutcome
import org.aegisos.guardianui.screens.GuardianIncidentTone
import org.junit.Rule

/**
 * Tests for the one rule that decides whether an open detection leaves
 * `GuardianApplication`'s pending queue, and for the exit paths that rule has
 * to hold on.
 *
 * Both directions of it have been real defects on this branch:
 *
 * - An undecided detection was destroyed because the owner tapped a
 *   particular button ("Not now"). Nothing else records a detection nobody
 *   acted on, so that lost it for good.
 * - A detection the owner *had* resolved stayed queued because they left via
 *   the back gesture rather than "Done". Home then showed the just-applied
 *   protection and a "needs a look" line for the same app at once, and
 *   re-opening it re-ran `protect()` on an already-remediated package.
 *
 * The fix for both is that leaving is not the decision — the recorded outcome
 * is. `resolvesPendingDetection()` is that rule; `leaveReview()` is the single
 * exit every control and the back gesture share.
 *
 * Runner: the JVM harness in `guardian/app/tests/README.md`. The back-gesture
 * tests need the `androidx.activity.compose.BackHandler` stand-in described
 * there, which records the handler the composition registered so a test can
 * fire it. What that verifies is `GuardianApp`'s own side of the wiring;
 * whether Android's real dispatcher delivers a gesture to a registered
 * handler is not verified here.
 */

private val FACTS = GuardianIncidentFacts(
    appName = "This app",
    permissionLabel = "Background location",
    statusLine = "Unusual for this app",
    observed = "Background precise location allowed",
    firstSeen = "09:12",
    ifRestricted = "Background navigation can stop.",
    tone = GuardianIncidentTone.Unusual,
)

private const val AUDIT_ID = "audit-1"

private fun pending() = GuardianReviewSource.PendingDetection(FACTS)

private fun active() = GuardianReviewSource.ActiveProtection(FACTS, AUDIT_ID)

private val UNDECIDED = listOf<GuardianUiState?>(
    null,
    GuardianUiState.Detected("UNUSUAL", "REVOKE_BACKGROUND_LOCATION", "t", "s", "c"),
)

private val TERMINAL = listOf<GuardianUiState?>(
    GuardianUiState.Verified(AUDIT_ID, "Verified."),
    GuardianUiState.Undone(AUDIT_ID, "Undone."),
    GuardianUiState.Failure("Could not verify."),
)

class GuardianPendingQueueRuleTest {

    @org.junit.jupiter.api.Test
    fun `an undecided detection is never dropped, whatever the exit path`() {
        // There is no third input: the rule sees only the open review and the
        // controller state, never which control was used. That is the whole
        // point — "Decide later", "Done", the back arrow and the system back
        // gesture cannot disagree with each other if none of them is an input.
        UNDECIDED.forEach { lifecycle ->
            assertFalse(
                resolvesPendingDetection(pending(), lifecycle),
                "undecided detection dropped for lifecycle $lifecycle",
            )
        }
    }

    @org.junit.jupiter.api.Test
    fun `a detection with a recorded outcome is dropped, whatever the exit path`() {
        TERMINAL.forEach { lifecycle ->
            assertTrue(
                resolvesPendingDetection(pending(), lifecycle),
                "resolved detection kept for lifecycle $lifecycle",
            )
        }
    }

    @org.junit.jupiter.api.Test
    fun `an active protection is never in the queue and never resolves one`() {
        (UNDECIDED + TERMINAL).forEach { lifecycle ->
            assertFalse(
                resolvesPendingDetection(active(), lifecycle),
                "active protection touched the queue for lifecycle $lifecycle",
            )
        }
        // No review open at all (the route fell back to Home): nothing to drop.
        (UNDECIDED + TERMINAL).forEach { lifecycle ->
            assertFalse(resolvesPendingDetection(null, lifecycle))
        }
    }

    @org.junit.jupiter.api.Test
    fun `only guardian-core outcomes count as recorded`() {
        // Failure is deliberately on the recorded side: guardian-core writes an
        // audit entry and a ProtectionAttention overview entry for it, so the
        // detection is not vanishing without a trace. Detected is not, because
        // nothing has run.
        assertTrue(GuardianUiState.Verified(AUDIT_ID, "d").hasRecordedOutcome())
        assertTrue(GuardianUiState.Undone(AUDIT_ID, "d").hasRecordedOutcome())
        assertTrue(GuardianUiState.Failure("d").hasRecordedOutcome())
        assertFalse(GuardianUiState.Detected("UNUSUAL", "A", "t", "s", "c").hasRecordedOutcome())
        assertFalse(null.hasRecordedOutcome())
    }

    @org.junit.jupiter.api.Test
    fun `a deferred detection still has a row on Home`() {
        // Models the shell exactly: apply the rule, drop the queue entry only
        // if it says so, then rebuild Home from what is left.
        var queued: GuardianIncidentFacts? = FACTS
        if (resolvesPendingDetection(pending(), lifecycle = null)) queued = null

        val stillQueued = requireNotNull(queued) { "deferring must not drop the detection" }
        val home = GuardianOverviewSnapshot(emptyList(), emptyList(), emptyList())
            .toHomeModel(pendingCount = 1, pendingFacts = stillQueued)
        assertEquals("1 app needs a quick look", home.state.attentionLine)
        assertTrue(
            home.actions.values.any { it is GuardianHomeRowAction.ReviewPendingIncident },
            "the deferred detection must still be openable from Home",
        )
    }
}

// ---------------------------------------------------------------------------
// End-to-end through the real navigation shell
// ---------------------------------------------------------------------------

private fun incident() = GuardianIncidentV1(
    1,
    "guardian-v1-background_location_v1-event-1",
    listOf("event-1"),
    "SUSPICIOUS",
    80,
    listOf("background precise location allowed"),
    "REVOKE_BACKGROUND_LOCATION",
    Instant.parse("2026-08-13T09:12:00Z"),
    "OPEN",
)

/**
 * Drives a real [GuardianUiController] from a queue the test owns, the same
 * shape `GuardianHomeActivity` wires: `detectBackgroundLocation()` peeks the
 * head non-destructively, and only `GuardianApp`'s `onDetectionHandled`
 * removes anything.
 */
private class FakeScenario(private val queue: ArrayDeque<GuardianIncidentV1>) : GuardianUiScenario {
    override fun detectBackgroundLocation(): GuardianApplicationResult =
        queue.firstOrNull()
            ?.let { GuardianApplicationResult.DetectedIncident(it) }
            ?: GuardianApplicationResult.NoIncident("none")

    override fun approve(incidentId: String, actionId: String): GuardianApplicationResult =
        GuardianApplicationResult.RemediationCompleted(
            RemediationOutcome.Status.VERIFIED_SUCCESS,
            AUDIT_ID,
            "Background location is restricted.",
        )

    override fun undo(auditId: String): GuardianApplicationResult =
        GuardianApplicationResult.UndoCompleted(
            RemediationOutcome.Status.UNDONE,
            auditId,
            "The temporary protection was undone.",
        )

    override fun overview(): GuardianApplicationResult =
        GuardianApplicationResult.Overview(
            GuardianOverviewSnapshot(emptyList(), emptyList(), emptyList()),
        )
}

class GuardianPendingQueueShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val queue = ArrayDeque(listOf(incident()))

    private fun startAtReview() {
        HarnessBackDispatcher.reset()
        composeRule.setContent {
            GuardianApp(
                controller = GuardianUiController(FakeScenario(queue)),
                startRoute = GuardianRoute.INCIDENT_REVIEW,
                onDetectionHandled = { queue.removeFirstOrNull() },
                pendingIncident = { queue.firstOrNull() },
                pendingIncidentCount = { queue.size },
            )
        }
    }

    private fun pressSystemBack() {
        composeRule.runOnIdle {
            assertTrue(HarnessBackDispatcher.press(), "no back handler was registered")
        }
        composeRule.waitForIdle()
    }

    @org.junit.Test
    fun `an undecided detection dismissed with the back gesture stays queued`() {
        startAtReview()
        composeRule.onNodeWithText("Restrict background location").assertExists()

        pressSystemBack()

        assertEquals(1, queue.size, "an undecided detection must survive any dismissal")
        composeRule.onNodeWithText("1 app needs a quick look").assertExists()
    }

    @org.junit.Test
    fun `an undecided detection deferred with the button stays queued`() {
        startAtReview()

        composeRule.onNodeWithText("Decide later").performClick()

        assertEquals(1, queue.size)
        composeRule.onNodeWithText("1 app needs a quick look").assertExists()
    }

    @org.junit.Test
    fun `a resolved detection dismissed with the back gesture is cleared`() {
        startAtReview()

        // Real controller, real approve(): this is a genuine terminal outcome,
        // not a lifecycle value handed to the shell.
        composeRule.onNodeWithText("Restrict background location").performClick()
        composeRule.onNodeWithText("Protected, and double-checked it worked").assertExists()

        pressSystemBack()

        assertEquals(0, queue.size, "a resolved detection must not stay queued")
        // The bug this closes was Home showing both at once. Neither the
        // attention line nor the pending row may survive.
        composeRule.onNodeWithText("1 app needs a quick look").assertDoesNotExist()
        composeRule.onNodeWithText("Something needs a look").assertDoesNotExist()
    }

    @org.junit.Test
    fun `a resolved detection dismissed with the on-screen back arrow is cleared`() {
        startAtReview()

        composeRule.onNodeWithText("Restrict background location").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(0, queue.size)
        composeRule.onNodeWithText("1 app needs a quick look").assertDoesNotExist()
    }

    @org.junit.Test
    fun `an undone detection dismissed with the back gesture is cleared`() {
        startAtReview()

        composeRule.onNodeWithText("Restrict background location").performClick()
        // "Undo" is on this screen twice in the Verified state: the fact row
        // "Undo — Always available" and the button itself. Only one of them is
        // clickable.
        composeRule.onAllNodesWithText("Undo").filterToOne(hasClickAction()).performClick()
        composeRule.onNodeWithText("Undone. The earlier change has been reversed").assertExists()

        pressSystemBack()

        assertEquals(0, queue.size, "an undone detection is decided, and must not stay queued")
    }

    @org.junit.Test
    fun `the pending screen states that the deferral is not durable`() {
        startAtReview()

        // Reachable in the real shell, not only on the screen in isolation.
        composeRule.onNodeWithText("Decide later").assertExists()
        composeRule.onNodeWithText(DEFERRAL_NOTE).assertExists()
    }

    @org.junit.Test
    fun `the caveat disappears once the detection has been decided`() {
        startAtReview()
        composeRule.onNodeWithText(DEFERRAL_NOTE).assertExists()

        composeRule.onNodeWithText("Restrict background location").performClick()

        // There is nothing left to defer, so the note would be noise at best
        // and misleading at worst — the protection it now describes *is*
        // recorded on disk.
        composeRule.onNodeWithText(DEFERRAL_NOTE).assertDoesNotExist()
    }
}
