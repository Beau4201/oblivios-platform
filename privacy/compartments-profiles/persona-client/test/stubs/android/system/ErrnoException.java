// SPDX-License-Identifier: Apache-2.0
package android.system;

/** Host stand-in for android.system.ErrnoException. Test use only. */
public final class ErrnoException extends Exception {
    private static final long serialVersionUID = 1L;
    public final int errno;

    public ErrnoException(String functionName, int errno) {
        super(functionName + " failed: " + errno);
        this.errno = errno;
    }
}
