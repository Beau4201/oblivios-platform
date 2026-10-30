// SPDX-License-Identifier: Apache-2.0
package android.system;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/** Host stand-in for android.system.Os, only what PersonaReader calls, over java.nio on Linux. Test use only. */
public final class Os {
    private static final Map<FileDescriptor, FileChannel> CHANNELS = new HashMap<>();
    private static final Map<FileDescriptor, Path> PATHS = new HashMap<>();

    /** Test only: what fstat reports instead of the real mode or size (-1: the real one), the most that one read returns
     *  (0: no limit), and how many reads were made. */
    public static int statModeOverride = -1;
    public static long statSizeOverride = -1;
    public static int readChunk = 0;
    public static int readCount = 0;

    private Os() {}

    public static FileDescriptor open(String path, int flags, int mode) throws ErrnoException {
        Path p = Paths.get(path);
        try {
            if ((flags & OsConstants.O_NOFOLLOW) != 0 && Files.isSymbolicLink(p)) {
                throw new ErrnoException("open", 40);
            }
            FileChannel ch = FileChannel.open(p, StandardOpenOption.READ);
            FileDescriptor fd = new FileDescriptor();
            CHANNELS.put(fd, ch);
            PATHS.put(fd, p);
            return fd;
        } catch (IOException e) {
            throw new ErrnoException("open", 2);
        }
    }

    public static StructStat fstat(FileDescriptor fd) throws ErrnoException {
        try {
            Path p = PATHS.get(fd);
            int uid = (Integer) Files.getAttribute(p, "unix:uid");
            int mode = statModeOverride >= 0 ? statModeOverride : (Integer) Files.getAttribute(p, "unix:mode");
            return new StructStat(mode, uid, statSizeOverride >= 0 ? statSizeOverride : CHANNELS.get(fd).size());
        } catch (IOException e) {
            throw new ErrnoException("fstat", 5);
        }
    }

    public static int read(FileDescriptor fd, byte[] bytes, int off, int len) throws ErrnoException, InterruptedIOException {
        readCount++;
        try {
            return CHANNELS.get(fd).read(ByteBuffer.wrap(bytes, off, readChunk > 0 ? Math.min(len, readChunk) : len));
        } catch (IOException e) {
            throw new ErrnoException("read", 5);
        }
    }

    public static void close(FileDescriptor fd) throws ErrnoException {
        try {
            CHANNELS.remove(fd).close();
            PATHS.remove(fd);
        } catch (IOException e) {
            throw new ErrnoException("close", 5);
        }
    }

    /** Test only: descriptors that were opened and not closed. */
    public static int openCount() {
        return CHANNELS.size();
    }
}
