package org.aegisos.guardian;

import android.content.Context;
import android.os.Process;

import org.aegisos.guardian.application.GuardianApplicationService;
import org.aegisos.guardian.audit.InMemoryGuardianAuditStore;
import org.aegisos.guardian.detection.GuardianDetectionService;
import org.aegisos.guardian.detection.InMemoryGuardianDetectionCommitStore;
import org.aegisos.guardian.overview.InMemoryGuardianOverviewStore;
import org.aegisos.guardian.platform.android.AegisOSPlatformGateway;
import org.aegisos.guardian.remediation.GuardianRemediationService;
import org.aegisos.guardian.remediation.RemediationCatalog;
import org.aegisos.guardian.validation.GuardianEventParser;

public final class GuardianComponents {
    private final GuardianApplicationService applicationService;

    private GuardianComponents(GuardianApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public static GuardianComponents create(Context context) {
        AegisOSPlatformGateway gateway = new AegisOSPlatformGateway(
            context.getPackageManager(), Process.myUserHandle());

        // Mirrors GuardianReferenceScenario.createService() in aegisos-services
        // exactly, substituting SimulatedGuardianPlatformGateway with the real
        // gateway above. InMemoryGuardianDetectionCommitStore is passed as both
        // the detection commit store and the remediation incident store — it
        // implements both interfaces; this is not a mistake, it is how the
        // existing, working reference wiring does it.
        InMemoryGuardianDetectionCommitStore incidentStore = new InMemoryGuardianDetectionCommitStore();
        InMemoryGuardianOverviewStore overviewStore = new InMemoryGuardianOverviewStore();
        GuardianRemediationService remediation = new GuardianRemediationService(
            new RemediationCatalog(),
            gateway,
            new InMemoryGuardianAuditStore(),
            incidentStore,
            overviewStore);
        GuardianApplicationService applicationService = new GuardianApplicationService(
            new GuardianEventParser(), new GuardianDetectionService(incidentStore), remediation, overviewStore);
        return new GuardianComponents(applicationService);
    }

    public GuardianApplicationService applicationService() {
        return applicationService;
    }
}
