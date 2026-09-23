package org.sovereignos.guardian.ui

import androidx.activity.compose.HarnessBackDispatcher
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.sovereignos.guardian.application.GuardianApplicationResult
import org.sovereignos.guardian.contracts.GuardianIncidentV1
import org.sovereignos.guardian.overview.GuardianOverviewEntry
import org.sovereignos.guardian.overview.GuardianOverviewSnapshot
import org.sovereignos.guardian.overview.GuardianProtectionLifecycle
import org.sovereignos.guardian.remediation.RemediationOutcome
import org.sovereignos.guardianui.screens.GuardianIncidentTone
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
    fun `the deferral caveat names both restarts`() {
        // "A restart clears it" read as the phone's restart. Guardian's own
        // process can restart independently of it — a crash, an app update —
        // despite persistent="true", and that is the likelier of the two.
        assertTrue(DEFERRAL_NOTE.contains("your phone"), DEFERRAL_NOTE)
        assertTrue(DEFERRAL_NOTE.contains("Guardian itself"), DEFERRAL_NOTE)
        // No duration and no likelihood: neither is knowable from this code.
        assertFalse(DEFERRAL_NOTE.contains("may"), DEFERRAL_NOTE)
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

private fun incident(eventId: String = "event-1") = GuardianIncidentV1(
    1,
    "guardian-v1-background_location_v1-$eventId",
    listOf(eventId),
    "SUSPICIOUS",
    80,
    listOf("background precise location allowed"),
    "REVOKE_BACKGROUND_LOCATION",
    Instant.parse("2026-08-13T09:12:00Z"),
    "OPEN",
)

private const val PROTECTION_EFFECT = "Background location is restricted."

/**
 * An overview holding one applied, still-undoable protection and nothing else,
 * so Home offers a row that opens an `ActiveProtection` review. Nothing about
 * it is in the pending queue, which is the point: undoing it reaches a terminal
 * outcome that must leave the queue alone.
 */
private fun overviewWithActiveProtection() = GuardianOverviewSnapshot(
    listOf(
        GuardianOverviewEntry.ActiveProtection(
            "protection-1",
            "This app",
            "guardian:application",
            GuardianProtectionLifecycle.VERIFIED_ACTIVE,
            Instant.parse("2026-08-13T09:41:00Z"),
            PROTECTION_EFFECT,
            "Background navigation can stop.",
            true,
            "guardian://protections/protection-1",
            AUDIT_ID,
        ),
    ),
    emptyList(),
    emptyList(),
)

/**
 * Drives a real [GuardianUiController] from a queue the test owns, the same
 * shape `GuardianHomeActivity` wires: `detectBackgroundLocation()` peeks the
 * head non-destructively, and only `GuardianApp`'s `onDetectionHandled`
 * removes anything.
 */
private class FakeScenario(
    private val queue: ArrayDeque<GuardianIncidentV1>,
    private val snapshot: GuardianOverviewSnapshot =
        GuardianOverviewSnapshot(emptyList(), emptyList(), emptyList()),
) : GuardianUiScenario {
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
        GuardianApplicationResult.Overview(snapshot)
}

class GuardianPendingQueueShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val queue = ArrayDeque(listOf(incident()))

    private fun startAtReview() = start(GuardianRoute.INCIDENT_REVIEW)

    private fun start(
        route: GuardianRoute,
        snapshot: GuardianOverviewSnapshot =
            GuardianOverviewSnapshot(emptyList(), emptyList(), emptyList()),
    ) {
        HarnessBackDispatcher.reset()
        composeRule.setContent {
            GuardianApp(
                controller = GuardianUiController(FakeScenario(queue, snapshot)),
                startRoute = route,
                // The real drop: GuardianApplication.pollFirst(). It takes
                // whatever is at the head *now*, which is why dropping twice
                // destroys an unrelated detection rather than being a no-op.
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
    fun `a resolved detection is dropped at the outcome, before any exit`() {
        startAtReview()

        composeRule.onNodeWithText("Restrict background location").performClick()

        // No "Done", no back arrow, no back gesture: the review is still open
        // and the queue entry is already gone. That is the fix. `review` and
        // `lifecycle` are composition state, so an Activity teardown that is
        // not a user-initiated exit — rotation, dark mode, font size, locale,
        // the system reclaiming a backgrounded Activity — runs no exit handler
        // at all, and the detection used to survive it as if undecided.
        composeRule.onNodeWithText("Protected, and double-checked it worked").assertExists()
        assertEquals(0, queue.size, "the drop must not wait for the screen to close")
    }

    @org.junit.Test
    fun `the drop survives the review being destroyed instead of exited`() {
        startAtReview()
        composeRule.onNodeWithText("Restrict background location").performClick()

        // Stand-in for `onCreate` re-running against the same notification
        // intent after a configuration change: the shell is rebuilt from the
        // Application's queue, with no exit handler having run in between.
        // Nothing is left to review, so the notification route falls back to
        // Home — instead of reopening an incident that has been remediated.
        composeRule.setContent {
            GuardianApp(
                controller = GuardianUiController(FakeScenario(queue)),
                startRoute = GuardianRoute.INCIDENT_REVIEW,
                onDetectionHandled = { queue.removeFirstOrNull() },
                pendingIncident = { queue.firstOrNull() },
                pendingIncidentCount = { queue.size },
            )
        }

        composeRule.onNodeWithText("Restrict background location").assertDoesNotExist()
        composeRule.onNodeWithText("1 app needs a quick look").assertDoesNotExist()
    }

    @org.junit.Test
    fun `a second outcome in the same review never drops a second detection`() {
        // event-2 is queued behind the one under review and nobody has seen it.
        queue.addLast(incident("event-2"))
        startAtReview()

        composeRule.onNodeWithText("Restrict background location").performClick()
        assertEquals(1, queue.size, "exactly one detection may be dropped")

        // Undone is a recorded outcome too. Without the exactly-once guard this
        // second outcome would poll the queue again and take event-2 — the bug
        // class this branch has already shipped twice.
        composeRule.onAllNodesWithText("Undo").filterToOne(hasClickAction()).performClick()
        composeRule.onNodeWithText("Undone. The earlier change has been reversed").assertExists()
        assertEquals(1, queue.size, "a second outcome must not drop an unrelated detection")

        // ...and neither may the exit that follows it, which is the double-drop
        // the outcome-point move could have introduced on its own.
        pressSystemBack()

        assertEquals(1, queue.size, "leaving after an outcome must drop nothing further")
        assertEquals(
            "guardian-v1-background_location_v1-event-2",
            queue.first().incidentId(),
            "the surviving detection must be the one nobody has decided on",
        )
        composeRule.onNodeWithText("1 app needs a quick look").assertExists()
    }

    @org.junit.Test
    fun `an undecided detection is untouched by the outcome path`() {
        queue.addLast(incident("event-2"))
        startAtReview()

        // Opening a review calls controller.start(), which reads the queue head
        // but records nothing. Deferring reaches no outcome either.
        composeRule.onNodeWithText("Decide later").performClick()

        assertEquals(2, queue.size, "an undecided detection must survive")
        assertEquals("guardian-v1-background_location_v1-event-1", queue.first().incidentId())
        composeRule.onNodeWithText("2 apps need a quick look").assertExists()
    }

    @org.junit.Test
    fun `undoing an active protection leaves the pending queue alone`() {
        // A terminal outcome reached from a source that was never in the queue.
        // The rule's source gate is what stops it, and it now has to hold at
        // the outcome point rather than on the way out.
        start(GuardianRoute.HOME, overviewWithActiveProtection())

        composeRule.onNodeWithText(PROTECTION_EFFECT).performClick()
        composeRule.onAllNodesWithText("Undo").filterToOne(hasClickAction()).performClick()
        composeRule.onNodeWithText("Undone. The earlier change has been reversed").assertExists()

        assertEquals(1, queue.size, "an active protection must never drop a queued detection")

        pressSystemBack()

        assertEquals(1, queue.size)
        composeRule.onNodeWithText("1 app needs a quick look").assertExists()
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

    @org.junit.Test
    fun `the caveat is announced as part of the deferral button`() {
        startAtReview()

        // One node carries the click action, the label and the caveat, so a
        // screen reader touch-exploring the button hears the caveat with it.
        // Before this the note was a separate, unclickable node reached only
        // after the button — i.e. after the decision it qualifies.
        composeRule.onNode(
            hasClickAction() and hasText("Decide later") and hasText(DEFERRAL_NOTE),
        ).assertExists()
    }
}
