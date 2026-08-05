package org.aegisos.guardian.platform.android;

import android.content.pm.PackageManager;
import android.os.UserHandle;

import org.aegisos.guardian.platform.GatewayExecution;
import org.aegisos.guardian.platform.GuardianPlatformGateway;
import org.aegisos.guardian.platform.PlatformSnapshot;
import org.aegisos.guardian.platform.VerificationResult;
import org.aegisos.guardian.remediation.RemediationActionId;

import java.util.Map;

public final class AegisOSPlatformGateway implements GuardianPlatformGateway {
    private static final String BACKGROUND_LOCATION_PERMISSION =
        "android.permission.ACCESS_BACKGROUND_LOCATION";
    private static final String BACKGROUND_LOCATION_GRANTED_KEY =
        "BACKGROUND_LOCATION_GRANTED";

    private final PackageManager packageManager;
    private final UserHandle user;

    public AegisOSPlatformGateway(PackageManager packageManager, UserHandle user) {
        this.packageManager = packageManager;
        this.user = user;
    }

    @Override
    public PlatformSnapshot snapshot(String packageName) {
        return new PlatformSnapshot(packageName, Map.of(
            BACKGROUND_LOCATION_GRANTED_KEY, String.valueOf(hasBackgroundLocation(packageName))));
    }

    @Override
    public GatewayExecution apply(RemediationActionId actionId, String packageName) {
        if (actionId != RemediationActionId.REVOKE_BACKGROUND_LOCATION) {
            return new GatewayExecution(false, "Unsupported action for this gateway: " + actionId);
        }
        try {
            packageManager.revokeRuntimePermission(packageName, BACKGROUND_LOCATION_PERMISSION, user);
            return new GatewayExecution(true, "Revoked " + BACKGROUND_LOCATION_PERMISSION);
        } catch (SecurityException | IllegalArgumentException failure) {
            return new GatewayExecution(false, "revokeRuntimePermission failed: " + failure.getMessage());
        }
    }

    @Override
    public VerificationResult verify(RemediationActionId actionId, String packageName) {
        boolean stillGranted = hasBackgroundLocation(packageName);
        return new VerificationResult(!stillGranted,
            stillGranted ? "Background location is still granted" : "Background location is denied");
    }

    @Override
    public GatewayExecution rollback(RemediationActionId actionId, String packageName, PlatformSnapshot previous) {
        return restore(packageName, previous);
    }

    @Override
    public GatewayExecution undo(RemediationActionId actionId, String packageName, PlatformSnapshot previous) {
        return restore(packageName, previous);
    }

    private GatewayExecution restore(String packageName, PlatformSnapshot previous) {
        boolean wasGranted = Boolean.parseBoolean(previous.state().get(BACKGROUND_LOCATION_GRANTED_KEY));
        if (!wasGranted) {
            return new GatewayExecution(true, "No prior grant to restore");
        }
        try {
            packageManager.grantRuntimePermission(packageName, BACKGROUND_LOCATION_PERMISSION, user);
            return new GatewayExecution(true, "Restored " + BACKGROUND_LOCATION_PERMISSION);
        } catch (SecurityException | IllegalArgumentException failure) {
            return new GatewayExecution(false, "grantRuntimePermission failed: " + failure.getMessage());
        }
    }

    private boolean hasBackgroundLocation(String packageName) {
        return packageManager.checkPermission(BACKGROUND_LOCATION_PERMISSION, packageName)
            == PackageManager.PERMISSION_GRANTED;
    }
}
