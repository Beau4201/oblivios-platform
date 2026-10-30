// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

import android.system.Os;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Map;

/** Host test of PersonaReader over a stand-in of android.system.Os: java -ea, no framework. */
public final class PersonaReaderTest {
    private static int checks = 0;

    private static void check(boolean cond, String what) {
        checks++;
        if (!cond) {
            throw new AssertionError(what);
        }
    }

    private static String file(int appId, int place) {
        Map<Integer, Integer> m = new HashMap<>();
        m.put(appId, place);
        return PersonaFile.format(1L, m);
    }

    private static Path write(Path dir, String name, String text, String perms) throws Exception {
        Path p = dir.resolve(name);
        Files.write(p, text.getBytes(StandardCharsets.UTF_8));
        Files.setPosixFilePermissions(p, PosixFilePermissions.fromString(perms));
        return p;
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("persona-reader");
        try {
            run(dir);
        } finally {
            try (var s = Files.walk(dir)) {
                s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        System.out.println("ok " + checks + " checks");
    }

    private static void run(Path dir) throws Exception {
        int me = (Integer) Files.getAttribute(dir, "unix:uid");
        int uid = 10123; // app id 10123 of user 0
        int otherUser = 1010123; // the same app in user 10

        Path good = write(dir, "good", file(10123, 7), "rw-r--r--");
        PersonaReader r = new PersonaReader(good.toString(), me);
        check(r.placeOf(uid, 0) == 7, "an app with a persona has its place");
        check(r.placeOf(otherUser, 0) == 7, "the same app in another user has it too");
        check(r.placeOf(10124, 0) == 0, "an app without one has none");
        check(r.placeOf(1000, 0) == 0, "a system uid has none");

        // looked at again at most once a second
        long t0 = 100_000;
        PersonaReader timed = new PersonaReader(good.toString(), me);
        check(timed.placeOf(uid, t0) == 7, "the first look");
        Files.write(good, file(10123, 9).getBytes(StandardCharsets.UTF_8));
        check(timed.placeOf(uid, t0 + PersonaReader.INTERVAL_MS - 1) == 7, "no new read inside the second");
        check(timed.placeOf(uid, t0 + PersonaReader.INTERVAL_MS) == 9, "a new read after the second");
        check(timed.placeOf(uid, t0 + PersonaReader.INTERVAL_MS + 1) == 9, "and the time of that read is kept");
        Files.write(good, file(10123, 7).getBytes(StandardCharsets.UTF_8));
        check(timed.placeOf(uid, t0 + 2 * PersonaReader.INTERVAL_MS - 1) == 9, "the second counts from the last read");
        check(timed.placeOf(uid, t0 + 2 * PersonaReader.INTERVAL_MS) == 7, "and a read follows");

        // a read that returns a few bytes at a time still gets the whole file
        Os.readChunk = 3;
        check(new PersonaReader(good.toString(), me).placeOf(uid, 0) == 7, "a file read in pieces");
        Os.readChunk = 0;

        // what fstat reports decides before anything is read
        Os.statModeOverride = 040755;
        check(new PersonaReader(good.toString(), me).placeOf(uid, 0) == 0, "a directory by its mode");
        Os.statModeOverride = 0140755;
        check(new PersonaReader(good.toString(), me).placeOf(uid, 0) == 0, "a socket by its mode");
        Os.statModeOverride = -1;
        int reads = Os.readCount;
        Os.statSizeOverride = PersonaReader.MAX_BYTES + 1;
        check(new PersonaReader(good.toString(), me).placeOf(uid, 0) == 0 && Os.readCount == reads, "over the limit: nothing is read");
        Os.statSizeOverride = PersonaReader.MAX_BYTES;
        new PersonaReader(good.toString(), me).placeOf(uid, 0);
        check(Os.readCount > reads, "exactly the limit is read");
        Os.statSizeOverride = -1;
        r = new PersonaReader(good.toString(), me);

        // a file that cannot be used means no persona for anyone
        check(new PersonaReader(dir.resolve("missing").toString(), me).placeOf(uid, 0) == 0, "a missing file");
        check(new PersonaReader(dir.toString(), me).placeOf(uid, 0) == 0, "a directory");
        check(new PersonaReader(good.toString(), me + 1).placeOf(uid, 0) == 0, "a file of another owner");
        Path groupW = write(dir, "groupw", file(10123, 7), "rw-rw-r--");
        check(new PersonaReader(groupW.toString(), me).placeOf(uid, 0) == 0, "a file writable by the group");
        Path otherW = write(dir, "otherw", file(10123, 7), "rw-r--rw-");
        check(new PersonaReader(otherW.toString(), me).placeOf(uid, 0) == 0, "a file writable by others");
        Path link = dir.resolve("link");
        Files.createSymbolicLink(link, good);
        check(new PersonaReader(link.toString(), me).placeOf(uid, 0) == 0, "a link is not followed");
        Path junk = write(dir, "junk", "p 10123 7\n", "rw-r--r--");
        check(new PersonaReader(junk.toString(), me).placeOf(uid, 0) == 0, "a file without the salt line");
        Path bad = write(dir, "bad", file(10123, 7) + "p 10124 x\n", "rw-r--r--");
        check(new PersonaReader(bad.toString(), me).placeOf(uid, 0) == 0, "one bad line refuses the whole file");
        Path empty = write(dir, "empty", "", "rw-r--r--");
        check(new PersonaReader(empty.toString(), me).placeOf(uid, 0) == 0, "an empty file");
        char[] big = new char[PersonaReader.MAX_BYTES + 1];
        java.util.Arrays.fill(big, '\n');
        Path huge = write(dir, "huge", new String(big), "rw-r--r--");
        check(new PersonaReader(huge.toString(), me).placeOf(uid, 0) == 0, "a file over the size limit");

        // a file that becomes unusable keeps the last table (never "no persona"); a good one replaces it
        check(r.placeOf(uid, 4 * PersonaReader.INTERVAL_MS) == 7, "a good file before it turns bad");
        Files.write(good, "junk".getBytes(StandardCharsets.UTF_8));
        check(r.placeOf(uid, 5 * PersonaReader.INTERVAL_MS) == 7, "a file that turns bad keeps the last table");
        Files.delete(good);
        check(r.placeOf(uid, 6 * PersonaReader.INTERVAL_MS) == 7, "a file that is gone keeps the last table");
        Files.write(good, file(10123, 3).getBytes(StandardCharsets.UTF_8));
        check(r.placeOf(uid, 7 * PersonaReader.INTERVAL_MS) == 3, "and turns good again");

        check(Os.openCount() == 0, "every descriptor is closed, also after a refused file");
    }
}
