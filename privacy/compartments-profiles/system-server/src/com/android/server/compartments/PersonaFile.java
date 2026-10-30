// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The file that tells the processes which place an Isolated app is in (ADR-208). The system server writes it; the telephony,
 * Wi-Fi and Bluetooth code read it, each with its own copy of this class, because they are other class loaders or processes
 * (a check of the package keeps the copies equal). Pure Java, no import beyond {@code java.util}.
 *
 * <p>Format: {@code s} and the 16 lower-case hex digits of the salt, then one line {@code p APPID PLACE} for each app that has a
 * persona, in app id order, every line ended by a newline, at most 8192 lines. An app id is of an ordinary app (10000 to 19999)
 * and a place is a positive number of {@link Places}. A reader that gets anything else refuses the whole file.
 */
final class PersonaFile {
    static final int MAX_LINES = 8192;
    private static final int FIRST_APP_ID = 10000;
    private static final int LAST_APP_ID = 19999;
    private static final int USER_OFFSET = 100000;
    private static final int SANDBOX_OFFSET = 10000;
    private static final Pattern SALT = Pattern.compile("s [0-9a-f]{16}");
    private static final Pattern LINE = Pattern.compile("p (0|[1-9][0-9]{0,9}) (0|[1-9][0-9]{0,9})");

    private final long salt;
    private final Map<Integer, Integer> places;

    private PersonaFile(long salt, Map<Integer, Integer> places) {
        this.salt = salt;
        this.places = places;
    }

    /** The text of the file for the apps (by app id) that have a place. */
    static String format(long salt, Map<Integer, Integer> appIdToPlace) {
        if (appIdToPlace == null || appIdToPlace.size() > MAX_LINES - 1) {
            throw new IllegalArgumentException("too many apps");
        }
        Map<Integer, Integer> sorted = new TreeMap<>();
        for (Map.Entry<Integer, Integer> e : appIdToPlace.entrySet()) {
            Integer appId = e.getKey();
            Integer place = e.getValue();
            if (appId == null || place == null || appId < FIRST_APP_ID || appId > LAST_APP_ID || place < 1) {
                throw new IllegalArgumentException("an app id or a place is out of range");
            }
            sorted.put(appId, place);
        }
        // Sixteen hex digits, padded with zeros: String.format is refused by the Bluetooth build (AndroidFrameworkEfficientStrings).
        String hex = Long.toHexString(salt);
        StringBuilder b = new StringBuilder("s ");
        for (int i = hex.length(); i < 16; i++) {
            b.append('0');
        }
        b.append(hex).append('\n');
        for (Map.Entry<Integer, Integer> e : sorted.entrySet()) {
            b.append("p ").append(e.getKey()).append(' ').append(e.getValue()).append('\n');
        }
        return b.toString();
    }

    /** @throws IllegalArgumentException for any text that is not exactly a file of the format */
    static PersonaFile parse(String text) {
        if (text == null || text.isEmpty() || text.charAt(text.length() - 1) != '\n') {
            throw new IllegalArgumentException("not a persona file");
        }
        Map<Integer, Integer> out = new HashMap<>();
        long salt = 0;
        int start = 0;
        int lines = 0;
        while (start < text.length()) {
            int end = text.indexOf('\n', start);
            String line = text.substring(start, end);
            start = end + 1;
            if (++lines > MAX_LINES) {
                throw new IllegalArgumentException("too many lines");
            }
            if (lines == 1) {
                if (!SALT.matcher(line).matches()) {
                    throw new IllegalArgumentException("the salt line must come first");
                }
                salt = Long.parseUnsignedLong(line.substring(2), 16);
                continue;
            }
            if (!LINE.matcher(line).matches()) {
                throw new IllegalArgumentException("not a place line");
            }
            int space = line.indexOf(' ', 2);
            long appId = Long.parseLong(line.substring(2, space));
            long place = Long.parseLong(line.substring(space + 1));
            if (appId < FIRST_APP_ID || appId > LAST_APP_ID || place < 1 || place > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("an app id or a place is out of range");
            }
            if (out.put((int) appId, (int) place) != null) {
                throw new IllegalArgumentException("an app id is repeated");
            }
        }
        return new PersonaFile(salt, Collections.unmodifiableMap(out));
    }

    long salt() {
        return salt;
    }

    /**
     * The place of the app that this uid belongs to (the same app in every user, and its SDK sandbox, 20000 to 29999, answers
     * for the app 10000 below it), or 0 when the app has no persona.
     */
    int placeOf(int uid) {
        if (uid < 0) {
            return 0;
        }
        int appId = uid % USER_OFFSET;
        if (appId >= FIRST_APP_ID + SANDBOX_OFFSET && appId <= LAST_APP_ID + SANDBOX_OFFSET) {
            appId -= SANDBOX_OFFSET;
        }
        Integer place = places.get(appId);
        return place == null ? 0 : place;
    }
}
