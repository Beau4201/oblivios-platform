package org.sovereignos.guardian.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.sovereignos.guardian.contracts.GuardianIncidentV1
import org.sovereignos.guardianui.screens.GuardianChoicesScreen
import org.sovereignos.guardianui.screens.GuardianFirstRunScreen
import org.sovereignos.guardianui.screens.GuardianHomeScreen
import org.sovereignos.guardianui.screens.GuardianIncidentReviewScreen
import org.sovereignos.guardianui.screens.GuardianIncidentUiState
import org.sovereignos.guardianui.screens.GuardianRetentionOption
import org.sovereignos.guardianui.screens.GuardianTrustScreen
import org.sovereignos.guardianui.theme.GuardianColor
import org.sovereignos.guardianui.theme.GuardianTheme

/**
 * Destinations in Guardian's UI. [INCIDENT_REVIEW] is named by
 * `GuardianWatcherService`'s notification PendingIntent through
 * `GuardianHomeActivity`'s `EXTRA_ROUTE`, so its name is part of a real
 * cross-process contract — do not rename it without updating both.
 *
 * The set is the five screens of the 2026-08-13 visual-identity design. The
 * superseded UI had two extra routes (GUARDING_NOW, RECENT_CHANGES) for
 * dedicated full-list screens; that design folds both into Home's own
 * "Recent changes" list, so those routes no longer exist. Nothing outside
 * the old navigation shell ever referenced them.
 */
enum class GuardianRoute {
    FIRST_RUN, HOME, TRUST, CHOICES, INCIDENT_REVIEW
}

/** What the incident-review screen is currently reviewing. */
internal sealed interface GuardianReviewSource {
    val facts: GuardianIncidentFacts

    /** The head of the pending-detection queue, reviewed through [GuardianUiController]. */
    data class PendingDetection(override val facts: GuardianIncidentFacts) : GuardianReviewSource

    /** An applied, still-active protection, opened so the owner can undo it. */
    data class ActiveProtection(
        override val facts: GuardianIncidentFacts,
        val auditId: String,
    ) : GuardianReviewSource
}

/**
 * The incident screen's secondary action, as a type rather than a label.
 *
 * The tapjacking guard (ADR-034 residual 5) has to fire for every path that
 * reverses an already-applied change, and the same reversal ships under more
 * than one piece of button copy ("Undo" after a verified protection, "Try
 * again" after a failed undo). Keying the guard on the displayed string left
 * the second one unprotected. [Undo] cannot be constructed without the undo
 * call itself, so the guard and the behaviour cannot drift apart again.
 */
internal sealed interface GuardianSecondaryAction {
    val label: String

    fun perform()

    /** Reverses a change Guardian already applied to the device. Always guarded. */
    class Undo(
        override val label: String,
        private val auditId: String?,
        private val onUndo: (String?) -> Unit,
    ) : GuardianSecondaryAction {
        override fun perform() = onUndo(auditId)
    }

    /** Anything that does not reverse an applied change. */
    class Other(
        override val label: String,
        private val onClick: () -> Unit,
    ) : GuardianSecondaryAction {
        override fun perform() = onClick()
    }
}

/** The incident screen's two actions, resolved from the controller's state. */
internal class IncidentActions(
    val facts: GuardianIncidentFacts,
    val primaryLabel: String,
    val secondary: GuardianSecondaryAction?,
    val onPrimary: () -> Unit,
    /** Caveat shown under [secondary]; see [DEFERRAL_NOTE]. */
    val secondaryNote: String? = null,
)

/**
 * True once guardian-core has recorded an outcome for whatever the incident
 * screen is showing — the protection was applied and verified, the undo ran,
 * or the attempt failed (which guardian-core still records, as an audit entry
 * plus a `ProtectionAttention` overview entry).
 *
 * [GuardianUiState.Detected] and null are the two undecided states: nothing
 * has run and nothing has been written anywhere.
 */
internal fun GuardianUiState?.hasRecordedOutcome(): Boolean = when (this) {
    is GuardianUiState.Verified,
    is GuardianUiState.Undone,
    is GuardianUiState.Failure,
    -> true
    is GuardianUiState.Detected, null -> false
}

/**
 * Whether an open detection must be dropped from `GuardianApplication`'s
 * pending queue.
 *
 * Deliberately keyed on the *outcome*, never on which control the owner used
 * to leave, and — since the fix below — never on leaving at all. Both
 * directions of that are real bugs that have shipped here:
 *
 * - Keyed on the button, a terminal outcome reached and then left via the back
 *   gesture stayed queued. Home then showed the just-applied protection and a
 *   "needs a look" line for the same app at the same time, and re-opening it
 *   re-ran `protect()` on an already-remediated package — a fail-closed but
 *   alarming state for something that had actually succeeded.
 * - The mirror image: an undecided detection dropped because the owner tapped
 *   a particular button. It has no record anywhere else, so every exit path —
 *   "Decide later", the on-screen back arrow, the system back gesture — must
 *   leave it queued.
 *
 * Keyed on any *exit* the same first bug survives in a second form: the exit
 * handlers live in this Activity's composition, and a teardown that is not a
 * user-initiated exit never runs one. See `recordOutcome()`, which is where
 * this rule is applied and why it is applied there.
 *
 * An [GuardianReviewSource.ActiveProtection] is never in the queue to begin
 * with, so it never resolves anything.
 */
internal fun resolvesPendingDetection(
    source: GuardianReviewSource?,
    lifecycle: GuardianUiState?,
): Boolean = source is GuardianReviewSource.PendingDetection && lifecycle.hasRecordedOutcome()

/**
 * Navigation root. Home / Trust / Choices are bottom-nav tabs, First Run is
 * shown once (persistence is the caller's, via [onFirstRunSeen]), and
 * incident review is a pushed screen reached from a detection notification or
 * from a Home row.
 *
 * Deliberately a plain `when` with no transition animation: the
 * visual-identity design defers all motion work to a separate plan, and the
 * reference module's own navigation does the same.
 *
 * @param pendingIncident head of `GuardianApplication`'s detection queue, or
 *   null when nothing is waiting. Read separately from [controller] because
 *   `GuardianUiState.Detected` carries only a severity and an action ID — the
 *   explanation facts and timestamp the review screen shows live on the
 *   `GuardianIncidentV1` itself, which the controller consumes and discards.
 *   Both read the same queue head, so the two cannot disagree.
 * @param pendingIncidentCount size of that queue, for Home's attention line.
 * @param retention the retention period the audit store has recorded. Shown
 *   read-only, and explicitly labelled as not enforced — nothing in this build
 *   calls `applyRetention()`. See `GuardianChoicesUiState.retentionChangeable`
 *   and `retentionEnforced`.
 */
@Composable
fun GuardianApp(
    controller: GuardianUiController,
    startRoute: GuardianRoute = GuardianRoute.FIRST_RUN,
    onFirstRunSeen: () -> Unit = {},
    onDetectionHandled: () -> Unit = {},
    pendingIncident: () -> GuardianIncidentV1? = { null },
    pendingIncidentCount: () -> Int = { 0 },
    retention: GuardianRetentionOption = GuardianRetentionOption.ThirtyDays,
) {
    GuardianTheme {
        var overview by remember(controller) { mutableStateOf(controller.overview()) }
        var pendingFacts by remember(controller) { mutableStateOf(pendingIncident()?.toIncidentFacts()) }
        var pendingCount by remember(controller) { mutableStateOf(pendingIncidentCount()) }

        // A notification tap asks for INCIDENT_REVIEW; if the detection it
        // referred to has since been resolved there is nothing to review, so
        // fall back to Home rather than opening an empty screen.
        val reviewFromNotification =
            startRoute == GuardianRoute.INCIDENT_REVIEW && pendingFacts != null
        var review by remember(controller) {
            mutableStateOf<GuardianReviewSource?>(
                pendingFacts?.takeIf { reviewFromNotification }
                    ?.let { GuardianReviewSource.PendingDetection(it) },
            )
        }
        // start() is what arms the controller's protect(): only correct to
        // call when a review of the pending detection is actually opening.
        var lifecycle by remember(controller) {
            mutableStateOf(if (reviewFromNotification) controller.start() else null)
        }
        // Whether the currently open review has already dropped its detection
        // from the pending queue. Scoped to one open review; see
        // recordOutcome(), which is the only thing that sets it.
        var pendingResolved by remember(controller) { mutableStateOf(false) }
        var route by remember(controller) {
            mutableStateOf(
                if (startRoute == GuardianRoute.INCIDENT_REVIEW && !reviewFromNotification) {
                    GuardianRoute.HOME
                } else {
                    startRoute
                },
            )
        }

        fun refresh() {
            overview = controller.overview()
            pendingFacts = pendingIncident()?.toIncidentFacts()
            pendingCount = pendingIncidentCount()
        }

        /**
         * Applies a state guardian-core has just returned, and drops the open
         * detection from the pending queue at that same moment if the outcome
         * resolves it ([resolvesPendingDetection]).
         *
         * **The drop belongs here and nowhere else.** `review` and [lifecycle]
         * are `remember`ed in this Activity's composition; the pending queue
         * lives on the `Application`. Every teardown that is not a
         * user-initiated screen exit — a rotation, a dark-mode / font-size /
         * locale change, the system reclaiming the backgrounded Activity —
         * destroys the first without running any exit handler at all. Keying
         * the drop on an exit therefore left an already-remediated detection
         * queued whenever one of those happened before the owner tapped
         * "Done": `onCreate` re-ran against the same notification intent,
         * `peekDetection()` still returned the incident, and the review
         * reopened showing a resolved incident as undecided. Keying it on the
         * recorded outcome makes it correct by construction for every exit
         * path, because it no longer depends on there being one.
         *
         * [pendingResolved] is what makes it exactly-once. Two terminal
         * outcomes can follow each other inside one open review — Verified then
         * Undone, or Failure then a successful retry — and a second
         * `onDetectionHandled()` would poll the queue again and silently
         * destroy whatever *next*, unrelated, still-undecided detection had
         * reached the head by then.
         */
        fun recordOutcome(next: GuardianUiState) {
            lifecycle = next
            if (pendingResolved || !resolvesPendingDetection(review, next)) return
            onDetectionHandled()
            pendingResolved = true
            // The shell's own view of the queue must not outlive the drop: this
            // Activity can be torn down before any exit runs, and Home is built
            // from these values. The open review renders from its own captured
            // facts, so clearing them here cannot blank the screen.
            refresh()
        }

        /**
         * The single exit from incident review, and there is genuinely no
         * other one *from the owner's side*: that screen carries no bottom nav
         * bar, so the primary button, the secondary button, the on-screen back
         * arrow and the system back gesture are the complete set, and all four
         * arrive here.
         *
         * It deliberately does **not** touch the pending queue. That decision
         * has already been made, by [recordOutcome], at the moment
         * guardian-core recorded the outcome. Dropping here as well would fire
         * twice for the same detection, and the second `onDetectionHandled()`
         * would take the queue's new head — an unrelated detection nobody has
         * decided on. Resetting [pendingResolved] is safe only because this
         * also clears `review`: the next review starts from nothing dropped.
         */
        fun leaveReview() {
            review = null
            lifecycle = null
            pendingResolved = false
            refresh()
            route = GuardianRoute.HOME
        }

        fun openPendingReview() {
            val facts = pendingFacts ?: return
            review = GuardianReviewSource.PendingDetection(facts)
            pendingResolved = false
            // Not routed through recordOutcome(): start() only reads the queue
            // head, it records nothing. Its one non-Detected result is the
            // failure it returns when that head is already gone — in which case
            // there is nothing left in the queue to drop either.
            lifecycle = controller.start()
            route = GuardianRoute.INCIDENT_REVIEW
        }

        fun openProtectionReview(action: GuardianHomeRowAction.UndoProtection) {
            review = GuardianReviewSource.ActiveProtection(action.facts, action.auditId)
            lifecycle = null
            pendingResolved = false
            route = GuardianRoute.INCIDENT_REVIEW
        }

        fun selectTab(index: Int) {
            when (index) {
                0 -> {
                    refresh()
                    route = GuardianRoute.HOME
                }
                1 -> route = GuardianRoute.TRUST
                2 -> route = GuardianRoute.CHOICES
            }
        }

        val home = remember(overview, pendingFacts, pendingCount) {
            overview.toHomeModel(pendingCount, pendingFacts)
        }
        val source = review
        val effectiveRoute =
            if (route == GuardianRoute.INCIDENT_REVIEW && source == null) GuardianRoute.HOME else route

        // Incident review is a screen pushed on top of the tab shell, not its
        // own Activity, so without this the system back gesture finishes the
        // whole Activity from it instead of returning to Home. Routed to the
        // same single exit as the on-screen back arrow, so the two agree.
        // Scoped to this route only: back from a tab keeps its default
        // behaviour. The pending queue no longer depends on this handler
        // running — recordOutcome() has already settled that — but the
        // navigation still does.
        BackHandler(enabled = effectiveRoute == GuardianRoute.INCIDENT_REVIEW) { leaveReview() }

        // Edge-to-edge is forced on this platform SDK and nothing in this
        // tree was consuming WindowInsets: content drew directly under the
        // real status bar with no clearance, which is why the top bar's
        // own 14dp of breathing room (matched to the design spec's
        // .m3-topbar padding-top) never actually showed -- the real status
        // bar icons and Guardian's own top bar were fighting for the same
        // pixels. statusBarsPadding() reserves the real inset first; each
        // screen's own top padding starts only after that.
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        when (effectiveRoute) {
            GuardianRoute.FIRST_RUN -> GuardianFirstRunScreen(
                onGetStarted = {
                    onFirstRunSeen()
                    route = GuardianRoute.HOME
                },
            )

            GuardianRoute.HOME -> GuardianHomeScreen(
                state = home.state,
                onRecentChangeClick = { change ->
                    when (val action = change.key?.let { home.actions[it] }) {
                        is GuardianHomeRowAction.ReviewPendingIncident -> openPendingReview()
                        is GuardianHomeRowAction.UndoProtection -> openProtectionReview(action)
                        // Informational row: nothing recorded that the owner
                        // can act on, so tapping it deliberately does nothing.
                        null -> Unit
                    }
                },
                onNavSelect = ::selectTab,
            )

            GuardianRoute.TRUST -> GuardianTrustScreen(
                state = overview.toTrustUiState(),
                onNavSelect = ::selectTab,
            )

            GuardianRoute.CHOICES -> GuardianChoicesScreen(
                state = guardianChoicesUiState(retention),
                // Never invoked: every switch on this screen is disabled
                // because it reports a fixed property of this build, and the
                // retention selector is rendered read-only because this
                // build has no reviewed way to change the period.
                onWatchToggle = { _, _ -> },
                onNotifyImmediatelyToggle = {},
                onRetentionSelected = {},
                onNavSelect = ::selectTab,
            )

            GuardianRoute.INCIDENT_REVIEW -> IncidentReview(
                actions = incidentActions(
                    source = requireNotNull(source),
                    lifecycle = lifecycle,
                    // Both go through recordOutcome(), which is where the queue
                    // entry is dropped — at the instant guardian-core records
                    // an outcome, not when the screen is later closed.
                    onProtect = { recordOutcome(controller.protect()) },
                    onUndo = { auditId ->
                        recordOutcome(
                            if (auditId == null) controller.undo() else controller.undo(auditId),
                        )
                    },
                    // One exit for every control, and the same one the back
                    // gesture uses. It only navigates; it never touches the
                    // queue.
                    onFinish = ::leaveReview,
                ),
                onBack = ::leaveReview,
            )
        }
        }
    }
}

/**
 * Resolves the incident screen's two actions from the controller's current
 * state. The visual-identity design puts the whole Observe -> Explain ->
 * Choose -> Enforce -> Verify -> Undo loop on this one screen, so every
 * controller outcome is rendered here rather than on separate result screens.
 *
 * [onFinish] is the one way off this screen, shared by both buttons and by the
 * back gesture, and it only navigates. Nothing here decides anything about the
 * pending queue: that is [resolvesPendingDetection]'s single responsibility,
 * applied in the shell at the moment [onProtect] / [onUndo] return an outcome.
 * An earlier split into `onResolved`/`onDismissed` encoded the rule per button,
 * which is how the back gesture came to disagree with "Done"; keying it on any
 * exit at all left the same defect for exits that never happen, such as the
 * Activity being recreated mid-review.
 */
internal fun incidentActions(
    source: GuardianReviewSource,
    lifecycle: GuardianUiState?,
    onProtect: () -> Unit,
    onUndo: (String?) -> Unit,
    onFinish: () -> Unit,
): IncidentActions {
    val pending = source is GuardianReviewSource.PendingDetection
    val undoAuditId = (source as? GuardianReviewSource.ActiveProtection)?.auditId

    return when (lifecycle) {
        is GuardianUiState.Verified -> IncidentActions(
            facts = source.facts.asProtected(),
            primaryLabel = "Done",
            secondary = GuardianSecondaryAction.Undo(UNDO_LABEL, undoAuditId, onUndo),
            onPrimary = onFinish,
        )
        is GuardianUiState.Undone -> IncidentActions(
            facts = source.facts.asUndone(),
            primaryLabel = "Done",
            secondary = null,
            onPrimary = onFinish,
        )
        is GuardianUiState.Failure -> IncidentActions(
            facts = source.facts.asFailed(lifecycle.detail),
            primaryLabel = "Done",
            // "Try again" means retry the protection for a pending detection,
            // but retry the *undo* for an active protection — the same
            // reversal the Undo button performs, so it is typed as one.
            secondary = if (pending) {
                GuardianSecondaryAction.Other("Try again", onProtect)
            } else {
                GuardianSecondaryAction.Undo("Try again", undoAuditId, onUndo)
            },
            onPrimary = onFinish,
        )
        is GuardianUiState.Detected, null ->
            if (pending) {
                IncidentActions(
                    facts = source.facts,
                    primaryLabel = "Restrict ${source.facts.permissionLabel.replaceFirstChar { it.lowercase() }}",
                    // Nothing has been decided and nothing has been recorded,
                    // so nothing may drop the queue entry from here — see
                    // resolvesPendingDetection(), which is what enforces that,
                    // and which no exit consults. The label says "later", not
                    // "no", for the same reason.
                    secondary = GuardianSecondaryAction.Other(DECIDE_LATER_LABEL, onFinish),
                    onPrimary = onProtect,
                    // ...and the caveat says what "later" is actually worth,
                    // because the queue holding it is in memory only.
                    secondaryNote = DEFERRAL_NOTE,
                )
            } else {
                // An active protection opened from Home: already applied and
                // verified, so Undo is the only thing left to offer.
                IncidentActions(
                    facts = source.facts,
                    primaryLabel = "Done",
                    secondary = GuardianSecondaryAction.Undo(UNDO_LABEL, undoAuditId, onUndo),
                    onPrimary = onFinish,
                )
            }
    }
}

private const val UNDO_LABEL = "Undo"
private const val DECIDE_LATER_LABEL = "Decide later"

/**
 * The honest lifetime of a deferral, shown under "Decide later".
 *
 * `GuardianApplication.pendingDetections` is a plain in-memory
 * `ConcurrentLinkedDeque`. Nothing writes it to disk, so a process restart
 * loses every deferred detection; the app being `persistent="true"` makes that
 * rarer, not impossible. Nothing re-raises it afterwards either —
 * `BackgroundLocationWatcher` only fires on a *new* AppOps event.
 *
 * It names **both** restarts, because they are genuinely independent and the
 * owner can only anticipate one of them: Guardian's own process can be
 * restarted (a crash, a system update) while the phone stays on, and an
 * earlier wording of "a restart" read as if it meant the phone's.
 *
 * Everything here is limited to what the code establishes. In particular it
 * gives no duration and no likelihood: when either restart happens is not
 * knowable from here, and inventing a number would be exactly the kind of
 * claim this UI refuses to make elsewhere.
 *
 * The fix for the underlying weakness is a persistent queue, which is a
 * `GuardianApplication`/guardian-core change and is not what this note
 * replaces — it makes the current behaviour visible until then.
 */
internal const val DEFERRAL_NOTE =
    "Guardian keeps this reminder in memory only. It is cleared if your phone " +
        "restarts, or if Guardian itself does, and it will not come back " +
        "unless this happens again."

@Composable
private fun IncidentReview(actions: IncidentActions, onBack: () -> Unit) {
    // Undo is the one control whose tapjacked activation reverses a
    // just-applied protection (ADR-034 residual 5). Guard the whole screen
    // whenever it is on offer. Keyed on the action's type, never on its
    // label: the same reversal ships under two different labels.
    val guard = if (actions.secondary is GuardianSecondaryAction.Undo) {
        Modifier.filterTouchesWhenObscured()
    } else {
        Modifier
    }

    Box(modifier = guard) {
        GuardianIncidentReviewScreen(
            state = GuardianIncidentUiState(
                appName = actions.facts.appName,
                permissionLabel = actions.facts.permissionLabel,
                unusualReason = actions.facts.statusLine,
                observed = actions.facts.observed,
                firstSeen = actions.facts.firstSeen,
                ifRestricted = actions.facts.ifRestricted,
                // Deliberately a plain, unbranded placeholder. The design
                // calls for the reviewed app's own launcher icon, which needs
                // a package name guardian-core does not expose on this path
                // (see UNKNOWN_APP_LABEL). Drawing Guardian's own mark here
                // would misrepresent whose icon it is.
                appIconContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(GuardianColor.SurfaceContainerHigh),
                    )
                },
                tone = actions.facts.tone,
            ),
            onBack = onBack,
            onRestrict = actions.onPrimary,
            onNotNow = { actions.secondary?.perform() },
            primaryActionLabel = actions.primaryLabel,
            secondaryActionLabel = actions.secondary?.label,
            secondaryActionNote = actions.secondaryNote,
        )
    }
}
