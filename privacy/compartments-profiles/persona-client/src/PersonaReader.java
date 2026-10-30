// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Reads the persona file of the system server for another process (ADR-208): the Telephony app, the Wi-Fi code and the
 * Bluetooth app each have a copy of this class and of {@link PersonaFile}, and ask which place an app is in. The rules are those
 * of the sensor service for {@code sensor_modes}: the file is opened without following a link, it must be a regular file owned
 * by the system user and not writable by group or others, it must not be larger than {@link #MAX_BYTES}, and it is parsed
 * strictly. A file that cannot be used keeps the table that was last read (a file that is unreadable for a moment must not
 * give every Isolated app the real data); before a first table is read the answer is 0, no persona. The file is looked at
 * again at most once a second, however often the question is asked.
 */
public final class PersonaReader {
    static final String PATH = "/data/system/compartments/persona";
    static final int SYSTEM_UID = 1000;
    static final long INTERVAL_MS = 1000;
    static final int MAX_BYTES = 262144;

    private final String mPath;
    private final int mOwnerUid;
    private PersonaFile mFile;
    private long mLoadedAtMs;
    private boolean mLoaded;

    public PersonaReader() {
        this(PATH, SYSTEM_UID);
    }

    PersonaReader(String path, int ownerUid) {
        mPath = path;
        mOwnerUid = ownerUid;
    }

    /** The place of the app of this uid, or 0 when it has none or the file cannot be used. */
    public int placeOf(int uid) {
        return placeOf(uid, System.nanoTime() / 1_000_000L);
    }

    synchronized int placeOf(int uid, long nowMs) {
        if (!mLoaded || nowMs - mLoadedAtMs >= INTERVAL_MS) {
            PersonaFile fresh = load();
            if (fresh != null) {
                mFile = fresh;  // a file that cannot be used now leaves the last table in force: it is never read as "no persona"
            }
            mLoadedAtMs = nowMs;
            mLoaded = true;
        }
        return mFile == null ? 0 : mFile.placeOf(uid);
    }

    private PersonaFile load() {
        FileDescriptor fd = null;
        try {
            fd = Os.open(mPath, OsConstants.O_RDONLY | OsConstants.O_NOFOLLOW | OsConstants.O_CLOEXEC, 0);
            StructStat st = Os.fstat(fd);
            if (!OsConstants.S_ISREG(st.st_mode) || st.st_uid != mOwnerUid || (st.st_mode & 0022) != 0
                    || st.st_size < 0 || st.st_size > MAX_BYTES) {
                return null;
            }
            byte[] buf = new byte[(int) st.st_size];
            int n = 0;
            while (n < buf.length) {
                int r = Os.read(fd, buf, n, buf.length - n);
                if (r <= 0) {
                    return null;
                }
                n += r;
            }
            return PersonaFile.parse(new String(buf, StandardCharsets.UTF_8));
        } catch (ErrnoException | IOException | RuntimeException e) {
            return null;
        } finally {
            if (fd != null) {
                try {
                    Os.close(fd);
                } catch (ErrnoException ignored) {
                    // nothing to do for a descriptor that did not close
                }
            }
        }
    }
}
