package org.aegisos.guardian;

import android.content.Context;
import android.os.Process;

import org.aegisos.guardian.application.GuardianApplicationService;
import org.aegisos.guardian.audit.FileGuardianAuditStore;
import org.aegisos.guardian.detection.GuardianDetectionService;
import org.aegisos.guardian.detection.InMemoryGuardianDetectionCommitStore;
import org.aegisos.guardian.evidence.GuardianEvidenceRetentionPeriod;
import org.aegisos.guardian.overview.FileGuardianOverviewStore;
import org.aegisos.guardian.platform.android.AegisOSPlatformGateway;
import org.aegisos.guardian.remediation.GuardianRemediationService;
import org.aegisos.guardian.remediation.RemediationCatalog;
import org.aegisos.guardian.validation.GuardianEventParser;

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
        AegisOSPlatformGateway gateway = new AegisOSPlatformGateway(
            context.getPackageManager(), Process.myUserHandle());

        // Mirrors GuardianReferenceScenario.createService() in aegisos-services,
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
     * The retention period the audit store is actually enforcing, so the
     * Trust screen can state it instead of asserting a default. Exposes the
     * value only, not the store -- the UI has no business writing audits.
     */
    public GuardianEvidenceRetentionPeriod retentionPeriod() {
        return auditStore.retentionPeriod();
    }
}
