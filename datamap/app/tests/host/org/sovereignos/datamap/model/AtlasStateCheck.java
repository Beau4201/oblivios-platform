// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

public final class AtlasStateCheck {
    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }

    public static void main(String[] args) {
        AtlasState s = new AtlasState();
        check(s.tab() == AtlasState.MAP && s.sheet() == AtlasState.SHEET_ALL, "starts on the map with all apps");

        // a place over all apps, and back
        s.selectPlace("goog");
        check(s.sheet() == AtlasState.SHEET_PLACE && "goog".equals(s.place()), "place");
        check(s.back() && s.place() == null && s.sheet() == AtlasState.SHEET_ALL, "back closes the place");
        check(!s.back(), "nothing left to close");

        // an app, then a place inside it: the app stays chosen, back closes the place first and the app last
        s.chooseApp("sp");
        check(s.sheet() == AtlasState.SHEET_APP && "sp".equals(s.app()), "app");
        s.selectPlace("meta");
        check(s.sheet() == AtlasState.SHEET_PLACE && "sp".equals(s.app()), "a place inside an app");
        check(s.back() && s.sheet() == AtlasState.SHEET_APP, "back to the app");
        check(s.back() && s.app() == null && s.sheet() == AtlasState.SHEET_ALL, "back to all apps");

        // choosing an app closes a place and the tall sheet; clearing the app closes a place too
        s.toggleSheet();
        s.selectPlace("goog");
        s.chooseApp("ch");
        check(s.place() == null && !s.sheetTall(), "choosing an app resets the place and the sheet");
        s.selectPlace("goog");
        s.clearApp();
        check(s.app() == null && s.place() == null, "clearing the app");

        // the handle switches the sheet between its two heights
        check(!s.sheetTall(), "normal");
        s.toggleSheet();
        check(s.sheetTall(), "tall");
        s.toggleSheet();
        check(!s.sheetTall(), "normal again");

        // full screen closes the place and the tall sheet; choosing a place or an app leaves it
        s.selectPlace("goog");
        s.toggleSheet();
        s.toggleFullScreen();
        check(s.fullScreen() && s.place() == null && !s.sheetTall(), "full screen");
        s.selectPlace("meta");
        check(!s.fullScreen() && "meta".equals(s.place()), "a place leaves full screen");
        s.toggleFullScreen();
        s.chooseApp("sp");
        check(!s.fullScreen(), "an app leaves full screen");

        // back in full screen leaves it first, then closes the app
        s.toggleFullScreen();
        check(s.fullScreen() && "sp".equals(s.app()), "full screen with an app");
        check(s.back() && !s.fullScreen() && "sp".equals(s.app()), "back leaves full screen");
        check(s.back() && s.app() == null, "then the app");

        // "show on map" from a company page releases the app and shows the place
        s.chooseApp("sp");
        s.showCompanyOnMap("goog");
        check(s.app() == null && "goog".equals(s.place()) && s.tab() == AtlasState.MAP, "show on map");

        // a tab can be chosen from anywhere and keeps the choice
        s.go(AtlasState.SETTINGS);
        check(s.tab() == AtlasState.SETTINGS && "goog".equals(s.place()), "the choice survives a tab");
        // saving and restoring (a theme change recreates the activity)
        android(s);
        System.out.println("PASS AtlasStateCheck");
    }

    private static void android(AtlasState s) {
        s.chooseApp("sp");
        s.selectPlace("meta");
        s.toggleSheet();
        AtlasState t = AtlasState.fromBundleData(s.toBundleData());
        check(t.tab() == s.tab() && "sp".equals(t.app()) && "meta".equals(t.place()) && t.sheetTall() == s.sheetTall()
                && t.fullScreen() == s.fullScreen(), "state survives a round trip");
        check(AtlasState.fromBundleData(null).tab() == AtlasState.MAP, "no saved state");
        check(AtlasState.fromBundleData(new String[] {"9", "x"}).tab() == AtlasState.MAP, "a damaged state is a new one");
        check(AtlasState.fromBundleData(new String[] {"2", "x"}).tab() == AtlasState.MAP,
                "a short state with a valid tab is a new one, not an out-of-range read");
        check(AtlasState.fromBundleData(new String[] {"9", "", "", "0", "0"}).tab() == AtlasState.MAP,
                "a tab that does not exist is the map");
    }
}
