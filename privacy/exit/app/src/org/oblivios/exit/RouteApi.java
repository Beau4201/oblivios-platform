// SPDX-License-Identifier: Apache-2.0
package org.oblivios.exit;

import java.io.IOException;
import java.util.List;

/** The route helper as the controller sees it; the same failure rules as {@link DaemonApi}. */
public interface RouteApi {
    /** Opens the control socket and says Hello. */
    void connect() throws IOException;

    /**
     * Makes the helper's rules the ones of this specification. {@code netId} is the id of the Tor network once the app has
     * registered it, else 0. Ranges are {start, end} pairs.
     */
    void apply(String iface, long netId, List<int[]> steer, List<int[]> guard) throws IOException;

    /** Removes every rule the helper wrote. */
    void clear() throws IOException;

    void close();
}
