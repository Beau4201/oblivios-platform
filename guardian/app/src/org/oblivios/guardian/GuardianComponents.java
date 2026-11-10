// SPDX-License-Identifier: Apache-2.0
package org.oblivios.guardian;

import android.content.Context;
import android.os.Process;

import org.oblivios.guardian.application.GuardianApplicationService;
import org.oblivios.guardian.audit.FileGuardianAuditStore;
import org.oblivios.guardian.detection.GuardianDetectionService;
import org.oblivios.guardian.detection.InMemoryGuardianDetectionCommitStore;
import org.oblivios.guardian.evidence.GuardianEvidenceRetentionPeriod;
import org.oblivios.guardian.overview.FileGuardianOverviewStore;
import org.oblivios.guardian.platform.android.ObliviOSPlatformGateway;
import org.oblivios.guardian.remediation.GuardianRemediationService;
import org.oblivios.guardian.remediation.RemediationCatalog;
import org.oblivios.guardian.validation.GuardianEventParser;

import java.io.File;

public final class GuardianComponents {
    private final GuardianApplicationService applicationService;
    private final FileGuardianAuditStore auditStore;

    private GuardianComponents(
            GuardianApplicationService applicationService, FileGuardianAuditStore auditStore) {
        this.applicationService = applicationService;
        this.auditStore = auditStore;
    }

    public static GuardianComponents create(Context context) {
        ObliviOSPlatformGateway gateway = new ObliviOSPlatformGateway(
            context.getPackageManager(), Process.myUserHandle());

        // Mirrors GuardianReferenceScenario.createService() in oblivios-services,
        // substituting SimulatedGuardianPlatformGateway with the real gateway
        // above, and (as of the residuals-hardening pass) InMemoryGuardianAuditStore
        // with a FileGuardianAuditStore backed by this app's private storage --
        // Android already keeps getFilesDir() sandboxed to Guardian's own UID.
        // InMemoryGuardianDetectionCommitStore is passed as both the detection
        // commit store and the remediation incident store -- it implements both
        // interfaces; this is not a mistake, it is how the existing, working
        // reference wiring does it.
        InMemoryGuardianDetectionCommitStore incidentStore = new InMemoryGuardianDetectionCommitStore();
        FileGuardianOverviewStore overviewStore = new FileGuardianOverviewStore(
            new File(context.getFilesDir(), "guardian-overview.jsonl").toPath());
        FileGuardianAuditStore auditStore = new FileGuardianAuditStore(
            new File(context.getFilesDir(), "guardian-audit.jsonl").toPath());
        GuardianRemediationService remediation = new GuardianRemediationService(
            new RemediationCatalog(),
            gateway,
            auditStore,
            incidentStore,
            overviewStore);
        GuardianApplicationService applicationService = new GuardianApplicationService(
            new GuardianEventParser(), new GuardianDetectionService(incidentStore), remediation, overviewStore);
        return new GuardianComponents(applicationService, auditStore);
    }

    public GuardianApplicationService applicationService() {
        return applicationService;
    }

    /**
     * The retention period the audit store has recorded, so the Trust screen
     * can state the real value instead of asserting a default. Exposes the
     * value only, not the store -- the UI has no business writing audits.
     *
     * Recorded, not enforced: nothing in this app calls applyRetention() or
     * setRetentionPeriod(), so no audit entry is ever deleted on this
     * schedule. The Trust screen says so explicitly
     * (GuardianTrustUiState.retentionEnforced). Wiring enforcement needs an
     * owner decision first -- applyRetention() deletes audit entries, and
     * undo(auditId) needs the audit entry, so shortening retention would
     * silently make an active protection un-undoable.
     */
    public GuardianEvidenceRetentionPeriod retentionPeriod() {
        return auditStore.retentionPeriod();
    }
}
