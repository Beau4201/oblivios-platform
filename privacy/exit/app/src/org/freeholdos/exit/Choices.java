// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * What the user chose: which apps use Tor, which apps are never hidden from, and for which companies an app may show its
 * real address (the answer to the "cannot reach host Y while it is hidden" notification). An app that is in none of the
 * lists is Direct for Tor and Masked for the default tier ({@link Choice#DIRECT}: advertising and analytics hosts do not see
 * its address). An app set to {@link Choice#OPEN} is never masked: it connects as if the tier were off (ADR-188), for a
 * browser or a video app that the tier slows down. Stored as a versioned text file in device-protected storage:
 *
 * <pre>
 * exit-choices TAB 1
 * tor TAB package
 * open TAB package
 * show TAB package TAB company id
 * </pre>
 *
 * {@link #parse} never throws: a file it cannot fully read gives the lines it could read and sets {@link #damaged()}, so
 * that the app can tell the user instead of silently running with fewer choices. Pure Java.
 */
public final class Choices {
    /** DIRECT: the default, masked destinations. TOR: every connection through Tor. OPEN: never masked. */
    public enum Choice { DIRECT, TOR, OPEN }

    public static final String HEADER = "exit-choices\t1";
    /** Bounds: a file or a list past them is damaged, never read further. */
    public static final int MAX_TOR_APPS = 48;
    public static final int MAX_OPEN_APPS = 24;
    public static final int MAX_SHOWN = 256;
    public static final int MAX_FILE_CHARS = 64 * 1024;
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");

    private final Set<String> tor = new TreeSet<>();
    private final Set<String> open = new TreeSet<>();
    /** package -> companies whose hosts may see this app's real address. */
    private final Map<String, Set<Integer>> shown = new TreeMap<>();
    private boolean damaged;

    public static boolean validPackage(String name) {
        return name != null && name.length() <= 255 && PACKAGE.matcher(name).matches();
    }

    public Choice choiceOf(String pkg) {
        return tor.contains(pkg) ? Choice.TOR : open.contains(pkg) ? Choice.OPEN : Choice.DIRECT;
    }

    /** Whether {@link #set} would take the choice: a bad package name, the 49th app on Tor and the 25th open app are refused. */
    public boolean accepts(String pkg, Choice choice) {
        if (!validPackage(pkg)) {
            return false;
        }
        switch (choice) {
            case TOR:
                return tor.contains(pkg) || tor.size() < MAX_TOR_APPS;
            case OPEN:
                return open.contains(pkg) || open.size() < MAX_OPEN_APPS;
            default:
                return true; // Direct is always taken
        }
    }

    /** False when the choice was not taken (see {@link #accepts}); then nothing changed. */
    public boolean set(String pkg, Choice choice) {
        if (!accepts(pkg, choice)) {
            return false;
        }
        tor.remove(pkg);
        open.remove(pkg);
        if (choice == Choice.TOR) {
            tor.add(pkg);
        } else if (choice == Choice.OPEN) {
            open.add(pkg);
        }
        return true;
    }

    public Set<String> torApps() {
        return Collections.unmodifiableSet(tor);
    }

    /** The apps that are never masked. */
    public Set<String> openApps() {
        return Collections.unmodifiableSet(open);
    }

    public boolean showsAddress(String pkg, int company) {
        Set<Integer> s = shown.get(pkg);
        return s != null && s.contains(company);
    }

    /** False when the choice was not taken (bad package name, company below 1, or the table is full). */
    public boolean setShowsAddress(String pkg, int company, boolean show) {
        if (!validPackage(pkg) || company < 1) {
            return false;
        }
        if (!show) {
            Set<Integer> s = shown.get(pkg);
            if (s != null) {
                s.remove(company);
                if (s.isEmpty()) {
                    shown.remove(pkg);
                }
            }
            return true;
        }
        if (!showsAddress(pkg, company) && shownCount() >= MAX_SHOWN) {
            return false;
        }
        shown.computeIfAbsent(pkg, k -> new TreeSet<>()).add(company);
        return true;
    }

    /** The companies an app may show its address to, sorted; the list that goes to the daemon in SetOverrides. */
    public int[] shownCompanies(String pkg) {
        Set<Integer> s = shown.get(pkg);
        if (s == null) {
            return new int[0];
        }
        int[] out = new int[s.size()];
        int i = 0;
        for (int c : s) {
            out[i++] = c;
        }
        return out;
    }

    /** The packages that have a "show the address" choice. */
    public Set<String> packagesWithOverrides() {
        return Collections.unmodifiableSet(shown.keySet());
    }

    private int shownCount() {
        int n = 0;
        for (Set<Integer> s : shown.values()) {
            n += s.size();
        }
        return n;
    }

    /** True when the file this was read from was not read whole. */
    public boolean damaged() {
        return damaged;
    }

    public String serialize() {
        StringBuilder b = new StringBuilder(HEADER).append('\n');
        for (String p : tor) {
            b.append("tor\t").append(p).append('\n');
        }
        for (String p : open) {
            b.append("open\t").append(p).append('\n');
        }
        for (Map.Entry<String, Set<Integer>> e : shown.entrySet()) {
            for (int c : e.getValue()) {
                b.append("show\t").append(e.getKey()).append('\t').append(c).append('\n');
            }
        }
        return b.toString();
    }

    public static Choices parse(String text) {
        Choices c = new Choices();
        if (text == null || text.isEmpty()) {
            return c; // no file yet: the defaults, nothing was lost
        }
        List<String> lines = new ArrayList<>();
        if (text.length() > MAX_FILE_CHARS) {
            c.damaged = true;
            return c;
        }
        for (String l : text.split("\n", -1)) {
            lines.add(l);
        }
        if (!HEADER.equals(lines.get(0))) {
            c.damaged = true;
            return c;
        }
        for (String line : lines.subList(1, lines.size())) {
            if (line.isEmpty()) {
                continue;
            }
            String[] f = line.split("\t", -1);
            boolean ok = false;
            if (f.length == 2 && f[0].equals("tor")) {
                // An app on a tor line and an open line is a damaged file: the later line would silently replace the earlier.
                ok = c.choiceOf(f[1]) != Choice.OPEN && c.set(f[1], Choice.TOR);
            } else if (f.length == 2 && f[0].equals("open")) {
                ok = c.choiceOf(f[1]) != Choice.TOR && c.set(f[1], Choice.OPEN);
            } else if (f.length == 3 && f[0].equals("show") && f[2].matches("[0-9]{1,9}")) {
                ok = c.setShowsAddress(f[1], Integer.parseInt(f[2]), true);
            }
            if (!ok) {
                c.damaged = true;
            }
        }
        return c;
    }
}
