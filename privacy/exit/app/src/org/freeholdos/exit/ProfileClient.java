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
    private volatile String mRefusal;

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

    /**
     * Why the last change was refused, in the words of the service (a message of at most 120 characters), or null when the
     * last failure was not a refusal (the service is gone, an unexpected error). Cleared by the next successful change.
     */
    String refusal() {
        return mRefusal;
    }

    private void note(Exception e) {
        String m = e instanceof IllegalArgumentException || e instanceof IllegalStateException ? e.getMessage() : null;
        mRefusal = m == null || m.isEmpty() ? null : m.length() > 120 ? m.substring(0, 120) : m;
    }

    /** False without a service and after the system server has restarted (the binder is dead), so a caller finds the service again. */
    boolean available() {
        return mService != null && mService.asBinder().isBinderAlive();
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
            mRefusal = null;
            return true;
        } catch (RemoteException | RuntimeException e) {
            note(e);
            return false;
        }
    }

    boolean setDefaultSensors(int value) {
        try {
            if (mService == null) {
                return false;
            }
            mService.setDefaultSensors(value);
            mRefusal = null;
            return true;
        } catch (RemoteException | RuntimeException e) {
            note(e);
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

    /** The place that the persona of one app has now (chosen or Automatic), 0 when it has none, -1 when the call fails. */
    int getPersonaPlaceId(String packageName) {
        try {
            return mService == null ? -1 : mService.getPersonaPlaceId(packageName);
        } catch (RemoteException | RuntimeException e) {
            return -1;
        }
    }

    /** The places whose name contains the query, at most {@code limit}; empty when there is no place data or the call fails. */
    CompartmentsPlace[] searchPlaces(String query, int limit) {
        try {
            CompartmentsPlace[] found = mService == null ? null : mService.searchPlaces(query, limit);
            return found == null ? new CompartmentsPlace[0] : found;
        } catch (RemoteException | RuntimeException e) {
            return new CompartmentsPlace[0];
        }
    }

    /** False when the service is absent or refuses (an app that does not substitute its location, an unknown place). */
    boolean setPlace(String packageName, int placeId) {
        try {
            if (mService == null) {
                return false;
            }
            mService.setPlace(packageName, placeId);
            mRefusal = null;
            return true;
        } catch (RemoteException | RuntimeException e) {
            note(e);
            return false;
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
            mRefusal = null;
            return true;
        } catch (RemoteException | RuntimeException e) {
            note(e);
            return false;
        }
    }

    boolean setClassValue(String packageName, int cls, int value) {
        try {
            if (mService == null) {
                return false;
            }
            mService.setClassValue(packageName, cls, value);
            mRefusal = null;
            return true;
        } catch (RemoteException | RuntimeException e) {
            note(e);
            return false;
        }
    }

    boolean clearApp(String packageName) {
        try {
            if (mService == null) {
                return false;
            }
            mService.clearApp(packageName);
            mRefusal = null;
            return true;
        } catch (RemoteException | RuntimeException e) {
            note(e);
            return false;
        }
    }
}
