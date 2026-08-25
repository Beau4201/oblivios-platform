package org.aegisos.guardian.ui

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.aegisos.guardian.contracts.GuardianIncidentV1
import org.aegisos.guardian.evidence.GuardianEvidenceRetentionPeriod
import org.aegisos.guardian.overview.GuardianMeaningfulChangeType
import org.aegisos.guardian.overview.GuardianOverviewEntry
import org.aegisos.guardian.overview.GuardianOverviewSnapshot
import org.aegisos.guardian.overview.GuardianProtectionLifecycle
import org.aegisos.guardianui.screens.GuardianIncidentTone
import org.aegisos.guardianui.screens.GuardianRetentionOption
import org.junit.jupiter.api.Test

/**
 * Unit tests for `GuardianScreenModels.kt`, the mapping layer between
 * guardian-core's records and the reference screens' UI state.
 *
 * There is no in-tree runner for these: `guardian/app` is built by Soong as
 * an `android_app` and has no test target. See `guardian/app/tests/README.md`
 * for the JVM harness these were written and run against.
 */

private val ZONE: ZoneId = ZoneId.of("UTC")
private val LOCALE: Locale = Locale.UK
private val NOW: Instant = Instant.parse("2026-08-13T12:00:00Z")
private const val PROTECTION_URI = "guardian://protections/protection-1"

private fun activeProtection(
    entryId: String = "protection-1",
    auditId: String = "audit-1",
    undoAvailable: Boolean = true,
    destination: String = PROTECTION_URI,
) = GuardianOverviewEntry.ActiveProtection(
    entryId,
    "Application",
    "guardian:application",
    GuardianProtectionLifecycle.VERIFIED_ACTIVE,
    Instant.parse("2026-08-13T09:41:00Z"),
    "Background location is restricted.",
    "Background navigation can stop.",
    undoAvailable,
    destination,
    auditId,
)

private fun verifiedChange(
    entryId: String = "change-1",
    occurredAt: Instant = Instant.parse("2026-08-13T09:41:00Z"),
    destination: String = PROTECTION_URI,
) = GuardianOverviewEntry.MeaningfulChange(
    entryId,
    "Application",
    "guardian:application",
    GuardianMeaningfulChangeType.PROTECTION_VERIFIED,
    occurredAt,
    "Guardian verified a temporary protection.",
    "Background navigation can stop.",
    destination,
)

private fun undoneChange(
    entryId: String = "change-2",
    occurredAt: Instant = Instant.parse("2026-08-13T10:15:00Z"),
) = GuardianOverviewEntry.MeaningfulChange(
    entryId,
    "Application",
    "guardian:application",
    GuardianMeaningfulChangeType.UNDO_COMPLETED,
    occurredAt,
    "The temporary protection was undone.",
    "Background navigation can stop.",
    PROTECTION_URI,
)

private fun attention(
    entryId: String = "attention-1",
    occurredAt: Instant = Instant.parse("2026-08-13T11:00:00Z"),
) = GuardianOverviewEntry.ProtectionAttention(
    entryId,
    "Application",
    "guardian:application",
    GuardianMeaningfulChangeType.VERIFICATION_FAILED,
    occurredAt,
    "Guardian could not verify a temporary protection.",
    "guardian://incidents/incident-1/protection-diagnosis",
)

private fun snapshot(
    active: List<GuardianOverviewEntry.ActiveProtection> = emptyList(),
    changes: List<GuardianOverviewEntry.MeaningfulChange> = emptyList(),
    attention: List<GuardianOverviewEntry.ProtectionAttention> = emptyList(),
) = GuardianOverviewSnapshot(active, changes, attention)

private fun incident(
    severity: String = "SUSPICIOUS",
    actionId: String = "REVOKE_BACKGROUND_LOCATION",
    facts: List<String> = listOf("background precise location allowed"),
    createdAt: Instant = Instant.parse("2026-08-13T09:12:00Z"),
) = GuardianIncidentV1(
    1,
    "guardian-v1-background_location_v1-event-1",
    listOf("event-1"),
    severity,
    80,
    facts,
    actionId,
    createdAt,
    "OPEN",
)

private fun GuardianOverviewSnapshot.home(pendingCount: Int = 0, pending: GuardianIncidentFacts? = null) =
    toHomeModel(pendingCount, pending, ZONE, LOCALE, NOW)

class GuardianHomeModelTest {
    @Test
    fun `nothing recorded yet does not claim the owner is protected`() {
        val model = snapshot().home()

        assertEquals("Guardian is watching", model.state.headline)
        assertNull(model.state.attentionLine)
        assertTrue(model.state.recentChanges.isEmpty())
        assertTrue(model.actions.isEmpty())
    }

    @Test
    fun `an active protection reads as protected and offers undo on its own row`() {
        val model = snapshot(active = listOf(activeProtection()), changes = listOf(verifiedChange())).home()

        assertEquals("You're protected", model.state.headline)
        assertNull(model.state.attentionLine)

        val row = model.state.recentChanges.single()
        assertEquals("Application", row.appName)
        assertEquals("Guardian verified a temporary protection.", row.description)
        assertTrue(row.isSafe)

        val action = model.actions[row.key] as GuardianHomeRowAction.UndoProtection
        assertEquals("audit-1", action.auditId)
        assertEquals(GuardianIncidentTone.Protected, action.facts.tone)
        assertEquals("Background location", action.facts.permissionLabel)
    }

    @Test
    fun `an undone protection no longer offers undo`() {
        // The protection is gone from activeProtections(); only its history remains.
        val model = snapshot(changes = listOf(verifiedChange(), undoneChange())).home()

        assertTrue(model.actions.isEmpty())
        assertTrue(model.state.recentChanges.all { it.isSafe })
    }

    @Test
    fun `a protection whose undo is unavailable is not offered as undoable`() {
        val model = snapshot(
            active = listOf(activeProtection(undoAvailable = false)),
            changes = listOf(verifiedChange()),
        ).home()

        assertTrue(model.actions.isEmpty())
    }

    @Test
    fun `a pending detection leads the list and is reachable without its notification`() {
        val facts = incident().toIncidentFacts(ZONE, LOCALE, NOW)
        val model = snapshot(changes = listOf(verifiedChange())).home(pendingCount = 1, pending = facts)

        assertEquals("Something needs a look", model.state.headline)
        assertEquals("1 app needs a quick look", model.state.attentionLine)

        val first = model.state.recentChanges.first()
        assertEquals("Background precise location allowed", first.description)
        assertEquals(false, first.isSafe)
        assertEquals(
            GuardianHomeRowAction.ReviewPendingIncident,
            model.actions[first.key],
        )
    }

    @Test
    fun `more than one pending detection is counted, not rounded to one`() {
        val facts = incident().toIncidentFacts(ZONE, LOCALE, NOW)
        val model = snapshot().home(pendingCount = 3, pending = facts)

        assertEquals("3 apps need a quick look", model.state.attentionLine)
    }

    @Test
    fun `a failed protection surfaces guardian-core's own reason and never reads as safe`() {
        val model = snapshot(attention = listOf(attention())).home()

        assertEquals("Something needs a look", model.state.headline)
        assertEquals("Guardian could not verify a temporary protection.", model.state.attentionLine)
        assertEquals(false, model.state.recentChanges.single().isSafe)
    }

    @Test
    fun `a protection whose change entry aged out is still undoable`() {
        // guardian-core's overview store keeps only its three most recent
        // changes; the protection itself stays active.
        val model = snapshot(
            active = listOf(activeProtection()),
            changes = listOf(
                verifiedChange(entryId = "other-1", occurredAt = Instant.parse("2026-08-13T10:00:00Z"), destination = "guardian://protections/other"),
            ),
        ).home()

        val row = model.state.recentChanges.single { it.key == "protection-1" }
        assertEquals("Background location is restricted.", row.description)
        assertTrue(row.isSafe)
        assertEquals("audit-1", (model.actions.getValue("protection-1") as GuardianHomeRowAction.UndoProtection).auditId)
    }

    @Test
    fun `a protection with its own change entry is listed once, not twice`() {
        val model = snapshot(active = listOf(activeProtection()), changes = listOf(verifiedChange())).home()

        assertEquals(1, model.state.recentChanges.size)
    }

    @Test
    fun `the timeline is newest first`() {
        val model = snapshot(
            changes = listOf(verifiedChange(occurredAt = Instant.parse("2026-08-13T09:41:00Z"))),
            attention = listOf(attention(occurredAt = Instant.parse("2026-08-13T11:00:00Z"))),
        ).home()

        assertEquals(
            listOf("Guardian could not verify a temporary protection.", "Guardian verified a temporary protection."),
            model.state.recentChanges.map { it.description },
        )
    }
}

class GuardianIncidentFactsTest {
    @Test
    fun `incident facts come from the incident and the remediation catalog`() {
        val facts = incident().toIncidentFacts(ZONE, LOCALE, NOW)

        assertEquals(UNKNOWN_APP_LABEL, facts.appName)
        assertEquals("Background location", facts.permissionLabel)
        assertEquals("Suspicious for this app", facts.statusLine)
        assertEquals("Background precise location allowed", facts.observed)
        assertEquals("09:12", facts.firstSeen)
        // Verbatim from RemediationCatalog's REVOKE_BACKGROUND_LOCATION entry.
        assertEquals("Background navigation and geofencing can stop.", facts.ifRestricted)
        assertEquals(GuardianIncidentTone.Unusual, facts.tone)
    }

    @Test
    fun `every severity the detection rules can emit gets a real label`() {
        assertEquals("Unusual for this app", incident(severity = "UNUSUAL").toIncidentFacts(ZONE, LOCALE, NOW).statusLine)
        assertEquals(
            "Probably harmful",
            incident(severity = "PROBABLY_HARMFUL").toIncidentFacts(ZONE, LOCALE, NOW).statusLine,
        )
        assertEquals(
            "Flagged for review",
            incident(severity = "SOMETHING_NEW").toIncidentFacts(ZONE, LOCALE, NOW).statusLine,
        )
    }

    @Test
    fun `an unknown action never invents an impact claim`() {
        val facts = incident(actionId = "NOT_A_REAL_ACTION").toIncidentFacts(ZONE, LOCALE, NOW)

        assertEquals("This permission", facts.permissionLabel)
        assertEquals("Guardian cannot describe the impact of this change", facts.ifRestricted)
    }

    @Test
    fun `an active protection is labelled from the effect guardian-core recorded`() {
        val facts = activeProtection().toIncidentFacts(ZONE, LOCALE, NOW)

        assertEquals("Application", facts.appName)
        assertEquals("Background location", facts.permissionLabel)
        assertEquals("Background navigation can stop.", facts.ifRestricted)
        assertEquals(GuardianIncidentTone.Protected, facts.tone)
    }

    @Test
    fun `outcome variants change the tone the chip renders`() {
        val detected = incident().toIncidentFacts(ZONE, LOCALE, NOW)

        assertEquals(GuardianIncidentTone.Protected, detected.asProtected().tone)
        assertEquals(GuardianIncidentTone.Unusual, detected.asUndone().tone)
        assertEquals("Undone. The earlier change has been reversed", detected.asUndone().statusLine)

        // guardian-core's own failure text, verbatim and with nothing added:
        // the controller collapses rollback-failed into this same state.
        val failed = detected.asFailed("No successful change was applied")
        assertEquals(GuardianIncidentTone.Attention, failed.tone)
        assertEquals("No successful change was applied", failed.statusLine)
    }

    @Test
    fun `older records show a date, today's show a time`() {
        assertEquals(
            "13 Aug, 09:12",
            incident(createdAt = Instant.parse("2026-08-13T09:12:00Z"))
                .toIncidentFacts(ZONE, LOCALE, Instant.parse("2026-08-14T12:00:00Z")).firstSeen,
        )
        assertEquals(
            "09:12",
            incident(createdAt = Instant.parse("2026-08-13T09:12:00Z"))
                .toIncidentFacts(ZONE, LOCALE, NOW).firstSeen,
        )
    }
}

class GuardianTrustAndChoicesTest {
    @Test
    fun `the trust log mirrors the overview timeline`() {
        val state = snapshot(changes = listOf(verifiedChange()), attention = listOf(attention()))
            .toTrustUiState(zone = ZONE, locale = LOCALE, now = NOW)

        assertEquals(
            listOf("Guardian could not verify a temporary protection.", "Guardian verified a temporary protection."),
            state.activityLog.map { it.description },
        )
        assertEquals(listOf("11:00", "09:41"), state.activityLog.map { it.timeLabel })
        assertEquals(listOf(false, true), state.activityLog.map { it.isSafe })
    }

    @Test
    fun `every retention period the audit store can hold has a screen option`() {
        assertEquals(
            listOf(
                GuardianRetentionOption.SevenDays,
                GuardianRetentionOption.ThirtyDays,
                GuardianRetentionOption.NinetyDays,
                GuardianRetentionOption.Forever,
            ),
            GuardianEvidenceRetentionPeriod.values().map { it.toRetentionOption() },
        )
    }

    @Test
    fun `choices never presents a control this build cannot honour`() {
        val state = guardianChoicesUiState(GuardianRetentionOption.ThirtyDays)

        assertEquals(listOf("Background location"), state.watchToggles.map { it.title })
        assertTrue(state.watchToggles.all { it.enabled && !it.changeable })
        assertTrue(state.notifyImmediately)
        assertEquals(false, state.notifyImmediatelyChangeable)
        assertEquals(0, state.allowedAppsCount)
        assertEquals(GuardianRetentionOption.ThirtyDays, state.retention)
        assertEquals(false, state.retentionChangeable)
    }

    @Test
    fun `the retention period is never presented as enforced by default`() {
        // Nothing in this app calls applyRetention() or setRetentionPeriod(),
        // so the recorded period deletes nothing. Choices must say so;
        // defaulting this to true would make the screen claim a deletion
        // schedule the device does not run.
        val state = guardianChoicesUiState(GuardianRetentionOption.ThirtyDays)

        assertEquals(false, state.retentionEnforced)
    }
}
