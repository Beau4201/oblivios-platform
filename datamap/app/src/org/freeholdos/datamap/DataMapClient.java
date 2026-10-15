// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap;

import android.os.IBinder;

import com.android.internal.datamap.IDataMapService;

import java.lang.reflect.Method;

/**
 * Finds the Data Map service. {@code ServiceManager} is not in the SDK; this package is the only one in
 * its sysconfig exemption (hidden-api-whitelisted-app-org.freeholdos.datamap.xml), and SELinux lets only
 * the {@code datamap_app} domain find the service, so no other app can use this route.
 */
final class DataMapClient {
    static final String SERVICE_NAME = "datamap";

    private DataMapClient() {}

    /** @return the service, or null if this build has none or this app may not find it. */
    static IDataMapService connect() {
        try {
            Method get = Class.forName("android.os.ServiceManager").getMethod("getService", String.class);
            IBinder binder = (IBinder) get.invoke(null, SERVICE_NAME);
            return binder == null ? null : IDataMapService.Stub.asInterface(binder);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
