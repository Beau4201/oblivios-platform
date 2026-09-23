package org.sovereignos.guardian.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.sovereignos.guardian.contracts.GuardianIncidentV1
import org.sovereignos.guardian.evidence.GuardianEvidenceRetentionPeriod
import org.sovereignos.guardian.overview.GuardianMeaningfulChangeType
import org.sovereignos.guardian.overview.GuardianOverviewEntry
import org.sovereignos.guardian.overview.GuardianOverviewSnapshot
import org.sovereignos.guardian.remediation.RemediationActionId
import org.sovereignos.guardian.remediation.RemediationCatalog
import org.sovereignos.guardianui.screens.GuardianActivityEntry
import org.sovereignos.guardianui.screens.GuardianChoicesUiState
import org.sovereignos.guardianui.screens.GuardianHomeUiState
import org.sovereignos.guardianui.screens.GuardianIncidentTone
import org.sovereignos.guardianui.screens.GuardianRecentChange
import org.sovereignos.guardianui.screens.GuardianRetentionOption
import org.sovereignos.guardianui.screens.GuardianTrustUiState
import org.sovereignos.guardianui.screens.GuardianWatchToggle

/**
 * Maps guardian-core's own output types onto the presentation-only UI-state
 * types of the reference screens in `org.sovereignos.guardianui.screens`.
 *
 * The screens deliberately know nothing about `GuardianUiController`,
 * `GuardianUiState` or `GuardianOverviewSnapshot`; guardian-core deliberately
 * knows nothing about Compose. This file is the only place the two meet, and
 * it is plain Kotlin on purpose — no Compose imports — so the derivations
 * below are unit-testable on a bare JVM.
 *
 * Rule for everything here: never invent a fact. Every string that claims
 * something about the owner's device is either read from a guardian-core
 * record or is a fixed label for a known enum value. Where guardian-core has
 * no answer at all (today: which app an incident belongs to), the UI says so
 * rather than filling the gap in — see [UNKNOWN_APP_LABEL].
 */

/**
 * Placeholder used when guardian-core genuinely cannot name the app.
 * `GuardianApplicationResult.DetectedIncident` carries only a
 * `GuardianIncidentV1`, which has no package name or app label — the evidence
 * record that does (`GuardianIncidentEvidenceV1`) is not reachable from the
 * UI path. The previous UI shipped the same placeholder for the same reason;
 * this is a known gap, not a regression.
 */
const val UNKNOWN_APP_LABEL = "This app"

/** Compose-free view of everything the incident-review screen displays. */
data class GuardianIncidentFacts(
    val appName: String,
    val permissionLabel: String,
    val statusLine: String,
    val observed: String,
    val firstSeen: String,
    val ifRestricted: String,
    val tone: GuardianIncidentTone,
)

/** What tapping a row in Home's "Recent changes" list resolves to. */
sealed interface GuardianHomeRowAction {
    /** Open the head of the pending-detection queue for review. */
    object ReviewPendingIncident : GuardianHomeRowAction

    /** Open a still-active protection so the owner can undo it. */
    data class UndoProtection(
        val auditId: String,
        val facts: GuardianIncidentFacts,
    ) : GuardianHomeRowAction
}

/**
 * Home's UI state plus the row-key -> action index the screen needs to turn a
 * tap back into a domain action. Keys are the overview entry IDs (unique per
 * snapshot), so two visually identical rows can never resolve to each other.
 */
data class GuardianHomeModel(
    val state: GuardianHomeUiState,
    val actions: Map<String, GuardianHomeRowAction>,
)

private const val PENDING_ROW_KEY = "pending-detection"

private val catalog = RemediationCatalog()

// ---------------------------------------------------------------------------
// Incident review
// ---------------------------------------------------------------------------

/**
 * Derives the incident-review screen's facts from the detected incident
 * itself. `observed` is the rule's own explanation fact, `firstSeen` the
 * incident's own timestamp, and `ifRestricted` the remediation catalog's own
 * compatibility-impact text for the recommended action — all three are
 * guardian-core records, none are written here.
 */
fun GuardianIncidentV1.toIncidentFacts(
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    now: Instant = Instant.now(),
): GuardianIncidentFacts = GuardianIncidentFacts(
    appName = UNKNOWN_APP_LABEL,
    permissionLabel = permissionLabelFor(recommendedActionId()),
    statusLine = statusLineFor(severity()),
    observed = sentence(explanationFacts().joinToString("; ")),
    firstSeen = timestampLabel(createdAt(), zone, locale, now),
    ifRestricted = compatibilityImpactFor(recommendedActionId()),
    tone = GuardianIncidentTone.Unusual,
)

/**
 * Derives the same facts for a protection that is already applied and still
 * active, so the owner can reach Undo by tapping its row on Home. Every value
 * comes from the overview projection guardian-core wrote when it verified the
 * protection.
 */
fun GuardianOverviewEntry.ActiveProtection.toIncidentFacts(
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    now: Instant = Instant.now(),
): GuardianIncidentFacts = GuardianIncidentFacts(
    appName = appLabel(),
    permissionLabel = permissionLabelForEffect(effect()),
    statusLine = "Protected, and double-checked it worked",
    observed = sentence(effect()),
    firstSeen = timestampLabel(occurredAt(), zone, locale, now),
    ifRestricted = compatibilityImpact(),
    tone = GuardianIncidentTone.Protected,
)

/** Same incident, after Guardian applied and verified the protection. */
fun GuardianIncidentFacts.asProtected(): GuardianIncidentFacts = copy(
    statusLine = "Protected, and double-checked it worked",
    tone = GuardianIncidentTone.Protected,
)

/** Same incident, after the owner undid the protection. */
fun GuardianIncidentFacts.asUndone(): GuardianIncidentFacts = copy(
    statusLine = "Undone. The earlier change has been reversed",
    tone = GuardianIncidentTone.Unusual,
)

/**
 * Same incident, after Guardian could not make (or could not verify) the
 * change. [detail] is guardian-core's own failure text and is shown verbatim.
 *
 * Deliberately no added reassurance such as "nothing about this app was
 * changed": `GuardianUiController` collapses every non-success remediation
 * outcome into this one state, including
 * `VERIFICATION_FAILED_ROLLBACK_FAILED`, where something *was* changed and
 * the rollback itself failed. Claiming otherwise would be false in exactly
 * the case where it matters most.
 */
fun GuardianIncidentFacts.asFailed(detail: String): GuardianIncidentFacts = copy(
    statusLine = detail,
    tone = GuardianIncidentTone.Attention,
)

// ---------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------

/**
 * Builds Home from the overview projection plus the pending-detection queue.
 *
 * [pendingCount] and [pendingFacts] come from `GuardianApplication`'s queue,
 * which the overview projection does not know about: a detection only reaches
 * the overview once it has been acted on. Surfacing it here is what keeps a
 * pending incident reachable after its notification has been dismissed.
 */
fun GuardianOverviewSnapshot.toHomeModel(
    pendingCount: Int = 0,
    pendingFacts: GuardianIncidentFacts? = null,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    now: Instant = Instant.now(),
): GuardianHomeModel {
    val actions = mutableMapOf<String, GuardianHomeRowAction>()
    val rows = mutableListOf<GuardianRecentChange>()

    if (pendingFacts != null) {
        actions[PENDING_ROW_KEY] = GuardianHomeRowAction.ReviewPendingIncident
        rows += GuardianRecentChange(
            appName = pendingFacts.appName,
            description = pendingFacts.observed,
            isSafe = false,
            key = PENDING_ROW_KEY,
        )
    }

    // An active protection and its PROTECTION_VERIFIED change entry share one
    // destination URI (the overview store writes the protection's own
    // detailDestination into both), so this join needs no string parsing and
    // stops matching as soon as the protection stops being active.
    val undoableByDestination = activeProtections()
        .filter { it.undoAvailable() }
        .associateBy { it.destination() }

    rows += timeline(zone, locale, now).map { entry ->
        val undoable = undoableByDestination[entry.destination]
        if (undoable != null) {
            actions[entry.key] = GuardianHomeRowAction.UndoProtection(
                auditId = undoable.auditId(),
                facts = undoable.toIncidentFacts(zone, locale, now),
            )
        }
        GuardianRecentChange(
            appName = entry.appLabel,
            description = entry.ownerReason,
            isSafe = entry.isSafe,
            key = entry.key,
        )
    }

    val attention = protectionAttention()
    return GuardianHomeModel(
        state = GuardianHomeUiState(
            headline = when {
                pendingCount > 0 || attention.isNotEmpty() -> "Something needs a look"
                activeProtections().isNotEmpty() -> "You're protected"
                // Deliberately not "You're protected": with nothing watched
                // into a record yet, that would be a claim Guardian cannot
                // back up. Watching is what it can actually evidence.
                else -> "Guardian is watching"
            },
            attentionLine = when {
                pendingCount > 0 ->
                    if (pendingCount == 1) "1 app needs a quick look" else "$pendingCount apps need a quick look"
                attention.isNotEmpty() -> attention.first().ownerReason()
                else -> null
            },
            recentChanges = rows,
        ),
        actions = actions,
    )
}

// ---------------------------------------------------------------------------
// Trust
// ---------------------------------------------------------------------------

/**
 * Trust's activity log is the same overview timeline Home summarises, without
 * the pending queue (nothing has been recorded for a detection nobody has
 * acted on yet) and without a cap beyond the overview store's own retention.
 */
fun GuardianOverviewSnapshot.toTrustUiState(
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    now: Instant = Instant.now(),
): GuardianTrustUiState = GuardianTrustUiState(
    activityLog = timeline(zone, locale, now).map { entry ->
        GuardianActivityEntry(
            appName = entry.appLabel,
            description = entry.ownerReason,
            timeLabel = entry.timeLabel,
            isSafe = entry.isSafe,
        )
    },
)

fun GuardianEvidenceRetentionPeriod.toRetentionOption(): GuardianRetentionOption = when (this) {
    GuardianEvidenceRetentionPeriod.SEVEN_DAYS -> GuardianRetentionOption.SevenDays
    GuardianEvidenceRetentionPeriod.THIRTY_DAYS -> GuardianRetentionOption.ThirtyDays
    GuardianEvidenceRetentionPeriod.NINETY_DAYS -> GuardianRetentionOption.NinetyDays
    GuardianEvidenceRetentionPeriod.UNTIL_DELETED -> GuardianRetentionOption.Forever
}

// ---------------------------------------------------------------------------
// Choices
// ---------------------------------------------------------------------------

/**
 * Choices describes what this build actually does, and marks every row as not
 * changeable, because it actually isn't:
 *
 * - `BackgroundLocationWatcher` is the only detector wired into
 *   `GuardianWatcherService`, and it is started unconditionally. Background
 *   location is therefore genuinely the one category Guardian watches, and it
 *   is genuinely always on.
 * - `GuardianWatcherService.onDetection` posts a notification on every
 *   detection with no digest path, so "Notify immediately" is genuinely true
 *   and genuinely fixed.
 * - There is no per-app exception mechanism anywhere in guardian-core, so the
 *   allowed-apps count is genuinely zero rather than merely unimplemented.
 *
 * Showing these as live switches would claim control that does not exist.
 *
 * [retentionEnforced] defaults to false, and the app leaves it there: the
 * period is read live from the audit store and is therefore a real recorded
 * value, but nothing in this build ever calls `applyRetention()`, so no record
 * is ever deleted on that schedule. The screen states the setting and says it
 * is not enforced, rather than implying a deletion that does not happen — on
 * this build's one screen whose own header promises every claim points to a
 * real record (moved here from Trust when the retention control itself
 * moved). Flip it to true in the same change that wires enforcement, not
 * before.
 */
fun guardianChoicesUiState(
    retention: GuardianRetentionOption,
    retentionChangeable: Boolean = false,
    retentionEnforced: Boolean = false,
): GuardianChoicesUiState = GuardianChoicesUiState(
    watchToggles = listOf(
        GuardianWatchToggle(
            title = "Background location",
            description = "Apps checking where you are while closed",
            enabled = true,
            changeable = false,
        ),
    ),
    notifyImmediately = true,
    allowedAppsCount = 0,
    notifyImmediatelyChangeable = false,
    retention = retention,
    retentionChangeable = retentionChangeable,
    retentionEnforced = retentionEnforced,
)

// ---------------------------------------------------------------------------
// Shared derivations
// ---------------------------------------------------------------------------

private class TimelineEntry(
    val key: String,
    val destination: String,
    val appLabel: String,
    val ownerReason: String,
    val occurredAt: Instant,
    val timeLabel: String,
    val isSafe: Boolean,
)

/**
 * Meaningful changes and protection-attention entries, newest first. Both
 * carry an owner-facing reason written by guardian-core, so neither needs a
 * headline invented here.
 *
 * Any still-active protection whose own change entry has already been evicted
 * is added back. guardian-core's overview store keeps only its three most
 * recent changes, and this design has no separate "everything Guardian is
 * guarding" screen, so without this a protection could stay in force with no
 * row anywhere to undo it from — a silent hole in the always-reversible
 * promise, not merely a missing list item.
 */
private fun GuardianOverviewSnapshot.timeline(
    zone: ZoneId,
    locale: Locale,
    now: Instant,
): List<TimelineEntry> {
    val representedDestinations = (changes().map { it.destination() } +
        protectionAttention().map { it.destination() }).toSet()
    val orphanedProtections = activeProtections()
        .filterNot { representedDestinations.contains(it.destination()) }
        .map { protection ->
            TimelineEntry(
                key = protection.entryId(),
                destination = protection.destination(),
                appLabel = protection.appLabel(),
                ownerReason = protection.effect(),
                occurredAt = protection.occurredAt(),
                timeLabel = timestampLabel(protection.occurredAt(), zone, locale, now),
                isSafe = true,
            )
        }
    val entries = orphanedProtections + changes().map { change ->
        TimelineEntry(
            key = change.entryId(),
            destination = change.destination(),
            appLabel = change.appLabel(),
            ownerReason = change.ownerReason(),
            occurredAt = change.occurredAt(),
            timeLabel = timestampLabel(change.occurredAt(), zone, locale, now),
            isSafe = change.changeType().isSafe(),
        )
    } + protectionAttention().map { attention ->
        TimelineEntry(
            key = attention.entryId(),
            destination = attention.destination(),
            appLabel = attention.appLabel(),
            ownerReason = attention.ownerReason(),
            occurredAt = attention.occurredAt(),
            timeLabel = timestampLabel(attention.occurredAt(), zone, locale, now),
            // Attention entries exist only for VERIFICATION_FAILED and
            // UNDO_FAILED, so this is never a safe outcome.
            isSafe = false,
        )
    }
    return entries.sortedByDescending { it.occurredAt }
}

/**
 * True when the change is something Guardian did successfully and the owner
 * asked for (or explicitly approved). Everything else is shown as needing a
 * look, never as safe.
 */
private fun GuardianMeaningfulChangeType.isSafe(): Boolean = when (this) {
    GuardianMeaningfulChangeType.PROTECTION_VERIFIED,
    GuardianMeaningfulChangeType.UNDO_COMPLETED,
    -> true
    GuardianMeaningfulChangeType.SENSITIVE_PERMISSION_ADDED,
    GuardianMeaningfulChangeType.POLICY_CHANGED,
    GuardianMeaningfulChangeType.POST_UPDATE_BEHAVIOUR_CHANGE,
    GuardianMeaningfulChangeType.VERIFICATION_FAILED,
    GuardianMeaningfulChangeType.UNDO_FAILED,
    -> false
}

/** Fixed owner-facing label for a known remediation action. */
private fun permissionLabelFor(actionId: String): String =
    when (runCatching { RemediationActionId.valueOf(actionId) }.getOrNull()) {
        RemediationActionId.REVOKE_BACKGROUND_LOCATION -> "Background location"
        RemediationActionId.BLOCK_BACKGROUND_NETWORK -> "Background network"
        RemediationActionId.REMOVE_OVERLAY_PERMISSION -> "Display over other apps"
        RemediationActionId.FREEZE_APPLICATION -> "Background activity"
        null -> "This permission"
    }

/**
 * The overview projection records a protection's effect text, not its action
 * ID, so an already-applied protection is labelled by matching that text.
 * The four possible values are fixed strings written by guardian-core's own
 * overview store ("Background location is restricted.", "Background network
 * access is restricted.", "Overlay access is restricted.", "The app is
 * temporarily paused."). Anything else falls back to the neutral label rather
 * than being guessed at.
 */
private fun permissionLabelForEffect(effect: String): String = when {
    effect.contains("location", ignoreCase = true) ->
        permissionLabelFor(RemediationActionId.REVOKE_BACKGROUND_LOCATION.name)
    effect.contains("network", ignoreCase = true) ->
        permissionLabelFor(RemediationActionId.BLOCK_BACKGROUND_NETWORK.name)
    effect.contains("overlay", ignoreCase = true) ->
        permissionLabelFor(RemediationActionId.REMOVE_OVERLAY_PERMISSION.name)
    effect.contains("paused", ignoreCase = true) ->
        permissionLabelFor(RemediationActionId.FREEZE_APPLICATION.name)
    else -> "This permission"
}

/** The remediation catalog's own compatibility-impact text for the action. */
private fun compatibilityImpactFor(actionId: String): String =
    runCatching { catalog.require(actionId).remediation().compatibilityImpact() }
        .getOrElse { "Guardian cannot describe the impact of this change" }

/** Fixed owner-facing label for a known detection severity. */
private fun statusLineFor(severity: String): String = when (severity) {
    "UNUSUAL" -> "Unusual for this app"
    "SUSPICIOUS" -> "Suspicious for this app"
    "PROBABLY_HARMFUL" -> "Probably harmful"
    else -> "Flagged for review"
}

private fun sentence(text: String): String =
    if (text.isBlank()) "No detail recorded" else text.replaceFirstChar { it.uppercase() }

/** Time of day for today's records, day and month for older ones. */
internal fun timestampLabel(
    at: Instant,
    zone: ZoneId,
    locale: Locale,
    now: Instant,
): String {
    val moment = at.atZone(zone)
    // atZone().toLocalDate() rather than LocalDate.ofInstant(): the latter is
    // a Java 9 addition and is not in Android's java.time surface at this
    // app's minimum API level.
    val today: LocalDate = now.atZone(zone).toLocalDate()
    return if (moment.toLocalDate() == today) {
        DateTimeFormatter.ofPattern("HH:mm", locale).format(moment)
    } else {
        DateTimeFormatter.ofPattern("d MMM, HH:mm", locale).format(moment)
    }
}
