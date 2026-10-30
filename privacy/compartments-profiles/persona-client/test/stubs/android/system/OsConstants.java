// SPDX-License-Identifier: Apache-2.0
package android.system;

/** Host stand-in for android.system.OsConstants (the Linux x86-64 values). Test use only. */
public final class OsConstants {
    public static final int O_RDONLY = 0;
    public static final int O_NOFOLLOW = 0x20000;
    public static final int O_CLOEXEC = 0x80000;

    private OsConstants() {}

    public static boolean S_ISREG(int mode) {
        return (mode & 0170000) == 0100000;
    }
}
