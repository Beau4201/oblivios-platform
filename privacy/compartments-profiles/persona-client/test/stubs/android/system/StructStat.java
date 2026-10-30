// SPDX-License-Identifier: Apache-2.0
package android.system;

/** Host stand-in for android.system.StructStat, only the three fields that PersonaReader reads. Test use only. */
public final class StructStat {
    public final int st_mode;
    public final int st_uid;
    public final long st_size;

    public StructStat(int mode, int uid, long size) {
        st_mode = mode;
        st_uid = uid;
        st_size = size;
    }
}
