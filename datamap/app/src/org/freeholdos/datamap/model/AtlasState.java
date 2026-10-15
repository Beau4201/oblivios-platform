// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap.model;

/**
 * What is chosen in Atlas (ADR-160): the tab, an app, a place, full screen and the height of the sheet, with the rules
 * of the design page. Choosing an app closes a place; choosing a place keeps the app; full screen closes a place and
 * the tall sheet and any choice leaves it. Back takes one step at a time. Pure Java, no Android class.
 */
public final class AtlasState {
    public static final int MAP = 0;
    public static final int APPS = 1;
    public static final int COMPANIES = 2;
    public static final int SETTINGS = 3;

    public static final int SHEET_ALL = 0;
    public static final int SHEET_APP = 1;
    public static final int SHEET_PLACE = 2;

    private int tab = MAP;
    private String app;
    private String place;
    private boolean fullScreen;
    private boolean sheetTall;

    public int tab() {
        return tab;
    }

    public String app() {
        return app;
    }

    public String place() {
        return place;
    }

    public boolean fullScreen() {
        return fullScreen;
    }

    public boolean sheetTall() {
        return sheetTall;
    }

    /** What the sheet shows: the place over the app over all apps. */
    public int sheet() {
        if (place != null) {
            return SHEET_PLACE;
        }
        return app != null ? SHEET_APP : SHEET_ALL;
    }

    public void go(int tab) {
        this.tab = tab;
    }

    public void chooseApp(String pkg) {
        fullScreen = false;
        app = pkg;
        place = null;
        sheetTall = false;
    }

    public void clearApp() {
        app = null;
        place = null;
    }

    public void selectPlace(String key) {
        fullScreen = false;
        place = key;
    }

    public void deselectPlace() {
        place = null;
    }

    /** "Show on map" of a company: the app is released and the place of the company is shown. */
    public void showCompanyOnMap(String placeKey) {
        tab = MAP;
        app = null;
        selectPlace(placeKey);
    }

    public void toggleFullScreen() {
        fullScreen = !fullScreen;
        if (fullScreen) {
            place = null;
            sheetTall = false;
        }
    }

    public void toggleSheet() {
        sheetTall = !sheetTall;
    }

    /** One step back: the place, then full screen, then the app. @return false if there was nothing to close */
    public boolean back() {
        if (place != null) {
            place = null;
            return true;
        }
        if (fullScreen) {
            fullScreen = false;
            return true;
        }
        if (app != null) {
            app = null;
            return true;
        }
        return false;
    }

    // ---- a theme change recreates the activity: the state is saved as strings

    public String[] toBundleData() {
        return new String[] {
            Integer.toString(tab), app == null ? "" : app, place == null ? "" : place,
            fullScreen ? "1" : "0", sheetTall ? "1" : "0"};
    }

    public static AtlasState fromBundleData(String[] data) {
        AtlasState s = new AtlasState();
        if (data == null || data.length != 5) {
            return s;
        }
        try {
            int t = Integer.parseInt(data[0]);
            if (t < MAP || t > SETTINGS) {
                return s;
            }
            s.tab = t;
        } catch (NumberFormatException e) {
            return s;
        }
        s.app = data[1].isEmpty() ? null : data[1];
        s.place = data[2].isEmpty() ? null : data[2];
        s.fullScreen = "1".equals(data[3]);
        s.sheetTall = "1".equals(data[4]);
        return s;
    }
}
