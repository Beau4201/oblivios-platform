// SPDX-License-Identifier: Apache-2.0
package org.oblivios.exit;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import com.android.internal.compartments.CompartmentsAppPolicy;
import com.android.internal.compartments.CompartmentsPlace;
import com.android.internal.compartments.ICompartmentsPolicy;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The exit app's side of the Compartments policy service (ADR-208). {@code ServiceManager} is not in the SDK; the exit app is
 * the only package in its sysconfig exemption for this call and holds MANAGE_COMPARTMENTS, which SELinux and the permission
 * both require. Every call that the service refuses or that fails over Binder returns null, -1 or false, and the page reads the
 * state again, so no screen shows a value that was not stored.
 *
 * <p>Every method is a Binder call (the first one also looks the service up), and the system server can hold a call for
 * seconds, so none runs on the main thread of a page: {@link Reader} and {@link #write} run them on one worker thread, in
 * order, and hand the result to the main thread (X1, G8).
 */
final class ProfileClient {
    static final String SERVICE_NAME = "compartments";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Handler sWorker;

    private volatile ICompartmentsPolicy mService;
    /** Whether the service was looked up; false after the binder was found dead, so the next call looks again. */
    private volatile boolean mLooked;
    private volatile String mRefusal;

    private ProfileClient() {
    }

    /** A client for this build; {@link #available()} is false when there is no service (an image without ADR-208). Looks nothing up yet. */
    static ProfileClient find() {
        return new ProfileClient();
    }

    private static synchronized Handler worker() {
        if (sWorker == null) {
            HandlerThread thread = new HandlerThread("compartments-client");
            thread.start();
            sWorker = new Handler(thread.getLooper());
        }
        return sWorker;
    }

    /**
     * Reads off the main thread and applies the result on it. A request that arrives while a read runs collapses into one more
     * read, so a page that is told to render often (the status polls) never queues more than one. Call {@link #run} from the
     * main thread only; {@code read} runs on the worker and may use the client freely, {@code apply} runs on the main thread.
     */
    static final class Reader<T> {
        private final Supplier<T> mRead;
        private final Consumer<T> mApply;
        private boolean mBusy;
        private boolean mAgain;

        Reader(Supplier<T> read, Consumer<T> apply) {
            mRead = read;
            mApply = apply;
        }

        void run() {
            if (mBusy) {
                mAgain = true;
                return;
            }
            mBusy = true;
            worker().post(() -> {
                T value = null;
                try {
                    value = mRead.get();
                } finally {
                    final T result = value;
                    MAIN.post(() -> {
                        mBusy = false;
                        if (result != null) {
                            mApply.accept(result);
                        }
                        if (mAgain) {
                            mAgain = false;
                            run();
                        }
                    });
                }
            });
        }
    }

    /**
     * Runs a change on the worker, after every read and change that was asked before it, and tells the main thread whether the
     * service took it. A read asked from {@code done} therefore sees the change.
     */
    static void write(BooleanSupplier change, Consumer<Boolean> done) {
        worker().post(() -> {
            boolean ok = change.getAsBoolean();
            MAIN.post(() -> done.accept(ok));
        });
    }

    private ICompartmentsPolicy service() {
        if (!mLooked) {
            ICompartmentsPolicy found = null;
            try {
                Method get = Class.forName("android.os.ServiceManager").getMethod("getService", String.class);
                IBinder binder = (IBinder) get.invoke(null, SERVICE_NAME);
                found = binder == null ? null : ICompartmentsPolicy.Stub.asInterface(binder);
            } catch (ReflectiveOperationException | RuntimeException e) {
                // no service: the client stays unavailable
            }
            mService = found;
            mLooked = true;
        }
        return mService;
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

    /** False without a service; after the system server has restarted (the binder is dead) the service is looked up again. */
    boolean available() {
        ICompartmentsPolicy s = service();
        if (s != null && !s.asBinder().isBinderAlive()) {
            mLooked = false;
            s = service();
        }
        return s != null;
    }

    CompartmentsAppPolicy getApp(String packageName) {
        try {
            ICompartmentsPolicy s = service();
            return s == null ? null : s.getApp(packageName);
        } catch (RemoteException | RuntimeException e) {
            return null;
        }
    }

    CompartmentsAppPolicy getDefaults() {
        try {
            ICompartmentsPolicy s = service();
            return s == null ? null : s.getDefaults();
        } catch (RemoteException | RuntimeException e) {
            return null;
        }
    }

    /** The packages the user has set, or null. */
    String[] getConfigured() {
        try {
            ICompartmentsPolicy s = service();
            return s == null ? null : s.getConfigured();
        } catch (RemoteException | RuntimeException e) {
            return null;
        }
    }

    /** One of ProfileRows.PROFILE_*, or -1 when the service is absent or refuses. */
    int getProfile(String packageName) {
        try {
            ICompartmentsPolicy s = service();
            return s == null ? -1 : s.getProfile(packageName);
        } catch (RemoteException | RuntimeException e) {
            return -1;
        }
    }

    boolean setDefaultProfile(int profile) {
        try {
            ICompartmentsPolicy s = service();
            if (s == null) {
                return false;
            }
            s.setDefaultProfile(profile);
            mRefusal = null;
            return true;
        } catch (RemoteException | RuntimeException e) {
            note(e);
            return false;
        }
    }

    boolean setDefaultSensors(int value) {
        try {
            ICompartmentsPolicy s = service();
            if (s == null) {
                return false;
            }
            s.setDefaultSensors(value);
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
            ICompartmentsPolicy s = service();
            return s == null ? -1 : s.getPlaceId(packageName);
        } catch (RemoteException | RuntimeException e) {
            return -1;
        }
    }

    /** The place that the persona of one app has now (chosen or Automatic), 0 when it has none, -1 when the call fails. */
    int getPersonaPlaceId(String packageName) {
        try {
            ICompartmentsPolicy s = service();
            return s == null ? -1 : s.getPersonaPlaceId(packageName);
        } catch (RemoteException | RuntimeException e) {
            return -1;
        }
    }

    /** The places whose name contains the query, at most {@code limit}; empty when there is no place data or the call fails. */
    CompartmentsPlace[] searchPlaces(String query, int limit) {
        try {
            ICompartmentsPolicy s = service();
            CompartmentsPlace[] found = s == null ? null : s.searchPlaces(query, limit);
            return found == null ? new CompartmentsPlace[0] : found;
        } catch (RemoteException | RuntimeException e) {
            return new CompartmentsPlace[0];
        }
    }

    /** False when the service is absent or refuses (an app that does not substitute its location, an unknown place). */
    boolean setPlace(String packageName, int placeId) {
        try {
            ICompartmentsPolicy s = service();
            if (s == null) {
                return false;
            }
            s.setPlace(packageName, placeId);
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
            ICompartmentsPolicy s = service();
            return s == null ? null : s.getPlace(placeId);
        } catch (RemoteException | RuntimeException e) {
            return null;
        }
    }

    boolean setProfile(String packageName, int profile) {
        try {
            ICompartmentsPolicy s = service();
            if (s == null) {
                return false;
            }
            s.setProfile(packageName, profile);
            mRefusal = null;
            return true;
        } catch (RemoteException | RuntimeException e) {
            note(e);
            return false;
        }
    }

    boolean setClassValue(String packageName, int cls, int value) {
        try {
            ICompartmentsPolicy s = service();
            if (s == null) {
                return false;
            }
            s.setClassValue(packageName, cls, value);
            mRefusal = null;
            return true;
        } catch (RemoteException | RuntimeException e) {
            note(e);
            return false;
        }
    }

    boolean clearApp(String packageName) {
        try {
            ICompartmentsPolicy s = service();
            if (s == null) {
                return false;
            }
            s.clearApp(packageName);
            mRefusal = null;
            return true;
        } catch (RemoteException | RuntimeException e) {
            note(e);
            return false;
        }
    }
}
