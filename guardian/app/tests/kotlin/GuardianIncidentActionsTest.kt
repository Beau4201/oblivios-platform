package org.aegisos.guardian.ui

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.aegisos.guardian.overview.GuardianOverviewSnapshot
import org.aegisos.guardianui.screens.GuardianIncidentTone
import org.junit.jupiter.api.Test

/**
 * Unit tests for `incidentActions()` in `GuardianApp.kt` — the function that
 * resolves the incident-review screen's two buttons from the controller's
 * current state.
 *
 * Two properties here are security-relevant rather than cosmetic:
 *
 * 1. Every path whose secondary button reverses an already-applied change must
 *    be a [GuardianSecondaryAction.Undo], because that type — not the button's
 *    label — is what `IncidentReview` keys the `filterTouchesWhenObscured()`
 *    tapjacking guard on (ADR-034 residual 5).
 * 2. Deferring a pending detection must not resolve it. `onResolved` is wired
 *    to `GuardianApplication.clearDetection()`, which drops the queue head for
 *    good; a detection nobody acted on has no record anywhere else.
 *
 * The guard itself is a Compose `Modifier` and is not observable from a plain
 * JVM test; see this file's last test and the note in the task report for how
 * that one line is verified instead.
 *
 * Runner: the JVM harness in `guardian/app/tests/README.md`, same as
 * `GuardianScreenModelsTest.kt`.
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

/** Records which of the four collaborators `incidentActions()` reached. */
private class Calls {
    var protects = 0
    var undos = mutableListOf<String?>()
    var resolved = 0
    var dismissed = 0
}

private fun actionsFor(
    source: GuardianReviewSource,
    lifecycle: GuardianUiState?,
    calls: Calls = Calls(),
): Pair<IncidentActions, Calls> =
    incidentActions(
        source = source,
        lifecycle = lifecycle,
        onProtect = { calls.protects++ },
        onUndo = { calls.undos += it },
        onResolved = { calls.resolved++ },
        onDismissed = { calls.dismissed++ },
    ) to calls

private fun pending() = GuardianReviewSource.PendingDetection(FACTS)

private fun active() = GuardianReviewSource.ActiveProtection(FACTS, AUDIT_ID)

class GuardianIncidentActionsTest {

    // -----------------------------------------------------------------------
    // Deferring a pending detection must not destroy it
    // -----------------------------------------------------------------------

    @Test
    fun `deferring a pending detection does not resolve it`() {
        val (actions, calls) = actionsFor(pending(), lifecycle = null)

        assertEquals("Decide later", actions.secondary?.label)
        actions.secondary?.perform()

        // The queue callback must not have fired: the detection stays pending,
        // keeps its row on Home (see the Home test below), and stays reachable
        // from the notification.
        assertEquals(0, calls.resolved)
        assertEquals(1, calls.dismissed)
        assertEquals(0, calls.protects)
        assertTrue(calls.undos.isEmpty())
    }

    @Test
    fun `a deferred detection still has a row on Home`() {
        // The whole point of the fix: after deferring, the detection must be
        // findable somewhere. `onResolved` is `GuardianApplication`'s queue
        // drop, modelled here by clearing `queued`; Home is then built from
        // whatever is left in the queue, exactly as GuardianApp does after
        // leaving the review screen.
        var queued: GuardianIncidentFacts? = FACTS
        val actions = incidentActions(
            source = pending(),
            lifecycle = null,
            onProtect = {},
            onUndo = {},
            onResolved = { queued = null },
            onDismissed = {},
        )

        actions.secondary?.perform()

        val stillQueued = assertNotNull(queued, "deferring must not drop the detection")
        val home = GuardianOverviewSnapshot(emptyList(), emptyList(), emptyList())
            .toHomeModel(pendingCount = 1, pendingFacts = stillQueued)
        assertEquals("1 app needs a quick look", home.state.attentionLine)
        assertTrue(
            home.actions.values.any { it is GuardianHomeRowAction.ReviewPendingIncident },
            "the deferred detection must still be openable from Home",
        )
    }

    @Test
    fun `the deferral button is not labelled as a decision`() {
        // "Not now" read as a decision and behaved like one (it cleared the
        // queue). The behaviour is fixed; the label must not re-imply it.
        val (actions, _) = actionsFor(pending(), lifecycle = null)

        assertEquals("Decide later", actions.secondary?.label)
    }

    @Test
    fun `a pending detection is only resolved once an outcome exists`() {
        // Protected, undone and failed all have a guardian-core record behind
        // them, so Done may drop the queue entry.
        listOf(
            GuardianUiState.Verified(AUDIT_ID, "Verified."),
            GuardianUiState.Undone(AUDIT_ID, "Undone."),
            GuardianUiState.Failure("Could not verify."),
        ).forEach { lifecycle ->
            val (actions, calls) = actionsFor(pending(), lifecycle)
            actions.onPrimary()
            assertEquals(1, calls.resolved, "resolved after $lifecycle")
        }

        // Nothing recorded yet: the primary button protects, it does not
        // resolve.
        val (initial, initialCalls) = actionsFor(pending(), lifecycle = null)
        initial.onPrimary()
        assertEquals(0, initialCalls.resolved)
        assertEquals(1, initialCalls.protects)
    }

    @Test
    fun `an active protection never touches the pending queue`() {
        listOf(
            null,
            GuardianUiState.Verified(AUDIT_ID, "Verified."),
            GuardianUiState.Undone(AUDIT_ID, "Undone."),
            GuardianUiState.Failure("Could not undo."),
        ).forEach { lifecycle ->
            val (actions, calls) = actionsFor(active(), lifecycle)
            actions.onPrimary()
            actions.secondary?.perform()
            assertEquals(0, calls.resolved, "resolved for $lifecycle")
        }
    }

    // -----------------------------------------------------------------------
    // The tapjacking guard's key: every reversal is typed as an Undo
    // -----------------------------------------------------------------------

    @Test
    fun `a verified protection offers undo as an Undo action`() {
        val (actions, calls) = actionsFor(pending(), GuardianUiState.Verified(AUDIT_ID, "Verified."))

        val secondary = actions.secondary
        assertIs<GuardianSecondaryAction.Undo>(secondary)
        assertEquals("Undo", secondary.label)
        secondary.perform()
        // Null: the controller's own just-applied audit ID is used.
        assertEquals(listOf<String?>(null), calls.undos)
    }

    @Test
    fun `an active protection opened from Home offers undo as an Undo action`() {
        val (actions, calls) = actionsFor(active(), lifecycle = null)

        val secondary = actions.secondary
        assertIs<GuardianSecondaryAction.Undo>(secondary)
        secondary.perform()
        assertEquals(listOf<String?>(AUDIT_ID), calls.undos)
    }

    @Test
    fun `a failed undo is an Undo action even though its label says Try again`() {
        // The regression this test exists for: the guard used to be keyed on
        // the string "Undo", so this path — a retry of a failed undo, the
        // exact reversal the guard protects — ran unguarded.
        val (actions, calls) = actionsFor(active(), GuardianUiState.Failure("Could not undo."))

        val secondary = actions.secondary
        assertEquals("Try again", secondary?.label)
        assertIs<GuardianSecondaryAction.Undo>(secondary)
        secondary.perform()
        assertEquals(listOf<String?>(AUDIT_ID), calls.undos)
    }

    @Test
    fun `a failed protection retries the protection and is not an Undo action`() {
        val (actions, calls) = actionsFor(pending(), GuardianUiState.Failure("Could not verify."))

        val secondary = actions.secondary
        assertEquals("Try again", secondary?.label)
        assertIs<GuardianSecondaryAction.Other>(secondary)
        secondary.perform()
        assertEquals(1, calls.protects)
        assertTrue(calls.undos.isEmpty())
    }

    @Test
    fun `an undone protection offers no secondary action at all`() {
        val (actions, _) = actionsFor(pending(), GuardianUiState.Undone(AUDIT_ID, "Undone."))

        assertNull(actions.secondary)
    }

    @Test
    fun `every secondary action that calls undo is typed as one`() {
        // Exhaustive over the whole state space this screen can be in: for
        // each combination, perform the secondary action and assert that it
        // reached onUndo if and only if it is a GuardianSecondaryAction.Undo.
        // This is what makes the one-line guard in IncidentReview() correct
        // for every path rather than for the paths someone remembered.
        val lifecycles = listOf(
            null,
            GuardianUiState.Detected("UNUSUAL", "REVOKE_BACKGROUND_LOCATION", "t", "s", "c"),
            GuardianUiState.Verified(AUDIT_ID, "Verified."),
            GuardianUiState.Undone(AUDIT_ID, "Undone."),
            GuardianUiState.Failure("Failed."),
        )

        listOf(pending(), active()).forEach { source ->
            lifecycles.forEach { lifecycle ->
                val (actions, calls) = actionsFor(source, lifecycle)
                actions.secondary?.perform()

                val calledUndo = calls.undos.isNotEmpty()
                val typedAsUndo = actions.secondary is GuardianSecondaryAction.Undo
                assertEquals(
                    typedAsUndo,
                    calledUndo,
                    "secondary action for $source / $lifecycle: typed as Undo=$typedAsUndo but " +
                        "called onUndo=$calledUndo",
                )
            }
        }
    }

    @Test
    fun `the undone facts never claim a protection is still applied`() {
        val (actions, _) = actionsFor(active(), GuardianUiState.Undone(AUDIT_ID, "Undone."))

        assertEquals("Undone. The earlier change has been reversed", actions.facts.statusLine)
        assertEquals(GuardianIncidentTone.Unusual, actions.facts.tone)
    }
}
