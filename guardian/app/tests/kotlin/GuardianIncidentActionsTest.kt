// SPDX-License-Identifier: Apache-2.0
package org.oblivios.guardian.ui

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.oblivios.guardianui.screens.GuardianIncidentTone
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
 * 2. Deferring a pending detection must not resolve it. Resolving is wired to
 *    `GuardianApplication.clearDetection()`, which drops the queue head for
 *    good; a detection nobody acted on has no record anywhere else.
 *
 * `incidentActions()` deliberately no longer decides (2) per button: it has a
 * single [onFinish] and the rule lives in `resolvesPendingDetection()`, keyed
 * on state. Those tests, and the end-to-end tests that drive the shell's real
 * exit paths, are in `GuardianPendingQueueTest.kt`.
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

/** Records which of the three collaborators `incidentActions()` reached. */
private class Calls {
    var protects = 0
    var undos = mutableListOf<String?>()
    var finishes = 0
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
        onFinish = { calls.finishes++ },
    ) to calls

private fun pending() = GuardianReviewSource.PendingDetection(FACTS)

private fun active() = GuardianReviewSource.ActiveProtection(FACTS, AUDIT_ID)

class GuardianIncidentActionsTest {

    // -----------------------------------------------------------------------
    // Deferring a pending detection must not destroy it
    // -----------------------------------------------------------------------

    @Test
    fun `deferring a pending detection only leaves the screen`() {
        val (actions, calls) = actionsFor(pending(), lifecycle = null)

        assertEquals("Decide later", actions.secondary?.label)
        actions.secondary?.perform()

        // Deferring reaches the shell's single exit and nothing else. Whether
        // that exit drops the queue entry is resolvesPendingDetection()'s
        // decision, tested in GuardianPendingQueueTest.
        assertEquals(1, calls.finishes)
        assertEquals(0, calls.protects)
        assertTrue(calls.undos.isEmpty())
    }

    @Test
    fun `the deferral button is not labelled as a decision`() {
        // "Not now" read as a decision and behaved like one (it cleared the
        // queue). The behaviour is fixed; the label must not re-imply it.
        val (actions, _) = actionsFor(pending(), lifecycle = null)

        assertEquals("Decide later", actions.secondary?.label)
    }

    @Test
    fun `deferring is the only action that carries the restart caveat`() {
        // The note states that the pending queue is in memory only. It belongs
        // on the one control that invites the owner to rely on that queue, and
        // nowhere else — every other secondary action reverses or retries
        // something guardian-core has already recorded on disk.
        val (deferral, _) = actionsFor(pending(), lifecycle = null)
        assertEquals(DEFERRAL_NOTE, deferral.secondaryNote)
        assertTrue(
            DEFERRAL_NOTE.contains("restart"),
            "the caveat must name the thing that actually loses it",
        )

        val others = listOf(pending(), active()).flatMap { source ->
            listOf(
                GuardianUiState.Verified(AUDIT_ID, "Verified."),
                GuardianUiState.Undone(AUDIT_ID, "Undone."),
                GuardianUiState.Failure("Failed."),
            ).map { source to it }
        } + (active() to null)

        others.forEach { (source, lifecycle) ->
            val (actions, _) = actionsFor(source, lifecycle)
            assertNull(actions.secondaryNote, "unexpected caveat on $source / $lifecycle")
        }
    }

    @Test
    fun `the primary button protects before an outcome exists and finishes after`() {
        listOf(
            GuardianUiState.Verified(AUDIT_ID, "Verified."),
            GuardianUiState.Undone(AUDIT_ID, "Undone."),
            GuardianUiState.Failure("Could not verify."),
        ).forEach { lifecycle ->
            val (actions, calls) = actionsFor(pending(), lifecycle)
            actions.onPrimary()
            assertEquals("Done", actions.primaryLabel)
            assertEquals(1, calls.finishes, "finished after $lifecycle")
            assertEquals(0, calls.protects)
        }

        val (initial, initialCalls) = actionsFor(pending(), lifecycle = null)
        initial.onPrimary()
        assertEquals(0, initialCalls.finishes)
        assertEquals(1, initialCalls.protects)
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
