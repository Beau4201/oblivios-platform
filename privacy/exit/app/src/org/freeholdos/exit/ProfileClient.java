// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import android.os.IBinder;
import android.os.RemoteException;

import com.android.internal.compartments.CompartmentsAppPolicy;
import com.android.internal.compartments.CompartmentsPlace;
import com.android.internal.compartments.ICompartmentsPolicy;

import java.lang.reflect.Method;

/**
 * The exit app's side of the Compartments policy service (ADR-208). {@code ServiceManager} is not in the SDK; the exit app is
 * the only package in its sysconfig exemption for this call and holds MANAGE_COMPARTMENTS, which SELinux and the permission
 * both require. Every call that the service refuses or that fails over Binder returns null, -1 or false, and the page reads the
 * state again, so no screen shows a value that was not stored.
 */
final class ProfileClient {
    static final String SERVICE_NAME = "compartments";

    private final ICompartmentsPolicy mService;

    private ProfileClient(ICompartmentsPolicy service) {
        mService = service;
    }

    /** A client for this build; {@link #available()} is false when there is no service (an image without ADR-208). */
    static ProfileClient find() {
        try {
            Method get = Class.forName("android.os.ServiceManager").getMethod("getService", String.class);
            IBinder binder = (IBinder) get.invoke(null, SERVICE_NAME);
            return new ProfileClient(binder == null ? null : ICompartmentsPolicy.Stub.asInterface(binder));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new ProfileClient(null);
        }
    }

    boolean available() {
        return mService != null;
    }

    CompartmentsAppPolicy getApp(String packageName) {
        try {
            return mService == null ? null : mService.getApp(packageName);
        } catch (RemoteException | RuntimeException e) {
            return null;
        }
    }

    CompartmentsAppPolicy getDefaults() {
        try {
            return mService == null ? null : mService.getDefaults();
        } catch (RemoteException | RuntimeException e) {
            return null;
        }
    }

    /** The packages the user has set, or null. */
    String[] getConfigured() {
        try {
            return mService == null ? null : mService.getConfigured();
        } catch (RemoteException | RuntimeException e) {
            return null;
        }
    }

    /** One of ProfileRows.PROFILE_*, or -1 when the service is absent or refuses. */
    int getProfile(String packageName) {
        try {
            return mService == null ? -1 : mService.getProfile(packageName);
        } catch (RemoteException | RuntimeException e) {
            return -1;
        }
    }

    boolean setDefaultProfile(int profile) {
        try {
            if (mService == null) {
                return false;
            }
            mService.setDefaultProfile(profile);
            return true;
        } catch (RemoteException | RuntimeException e) {
            return false;
        }
    }

    boolean setDefaultSensors(int value) {
        try {
            if (mService == null) {
                return false;
            }
            mService.setDefaultSensors(value);
            return true;
        } catch (RemoteException | RuntimeException e) {
            return false;
        }
    }

    /** The place id the user chose for one app (0 is Automatic), or -1 when the service is absent or refuses. */
    int getPlaceId(String packageName) {
        try {
            return mService == null ? -1 : mService.getPlaceId(packageName);
        } catch (RemoteException | RuntimeException e) {
            return -1;
        }
    }

    /** One place by its id, or null. */
    CompartmentsPlace getPlace(int placeId) {
        try {
            return mService == null ? null : mService.getPlace(placeId);
        } catch (RemoteException | RuntimeException e) {
            return null;
        }
    }

    boolean setProfile(String packageName, int profile) {
        try {
            if (mService == null) {
                return false;
            }
            mService.setProfile(packageName, profile);
            return true;
        } catch (RemoteException | RuntimeException e) {
            return false;
        }
    }

    boolean setClassValue(String packageName, int cls, int value) {
        try {
            if (mService == null) {
                return false;
            }
            mService.setClassValue(packageName, cls, value);
            return true;
        } catch (RemoteException | RuntimeException e) {
            return false;
        }
    }

    boolean clearApp(String packageName) {
        try {
            if (mService == null) {
                return false;
            }
            mService.clearApp(packageName);
            return true;
        } catch (RemoteException | RuntimeException e) {
            return false;
        }
    }
}
