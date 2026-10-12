// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

import java.util.List;

/** Where the globe first looks: at the average place of the companies that are shown, else at Europe and Africa. */
public final class StartView {
    public static final double DEFAULT_LAT = 20;
    public static final double DEFAULT_LON = 10;

    private StartView() {}

    /**
     * @param places latitude and longitude in degrees, one pair per company
     * @return {latitude, longitude} of the view centre; the tilt is limited as the globe limits it
     */
    public static double[] of(List<double[]> places) {
        double x = 0;
        double y = 0;
        double z = 0;
        for (double[] p : places) {
            double lat = Math.toRadians(p[0]);
            double lon = Math.toRadians(p[1]);
            x += Math.cos(lat) * Math.cos(lon);
            y += Math.cos(lat) * Math.sin(lon);
            z += Math.sin(lat);
        }
        double len = Math.sqrt(x * x + y * y + z * z);
        if (places.isEmpty() || len < 1e-6 * places.size()) {   // none, or opposite sides of the globe
            return new double[] {DEFAULT_LAT, DEFAULT_LON};
        }
        double lat = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, z / len))));
        double lon = Math.toDegrees(Math.atan2(y, x));
        return new double[] {GlobeMath.clampTilt(lat), lon};
    }
}
