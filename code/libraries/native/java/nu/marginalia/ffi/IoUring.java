
package nu.marginalia.ffi;

import nu.marginalia.uring.UringQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.List;

import static java.lang.foreign.ValueLayout.*;

/** This class provides access to wrapper around Linux system calls.
 *  <p></p>
 *  isAvailable is a boolean flag that indicates whether the native
 *  implementations are available. If the shared library cannot be loaded,
 *  isAvailable will be false.  This flag must be checked before calling
 *  any of the native functions.
 * */
@SuppressWarnings("preview")
public class IoUring {
    private final MethodHandle uringInit;
    private final MethodHandle uringClose;
    private final MethodHandle uringReadBuffered;
    private final MethodHandle uringReadDirect;

    public static final IoUring instance;

    /** Indicates whether the native implementations are available */
    public static final boolean isAvailable;

    private static final Logger logger = LoggerFactory.getLogger(LinuxSystemCalls.class);

    private IoUring(Path libFile) {
        SymbolLookup libraryLookup = SymbolLookup.libraryLookup(libFile, Arena.global());
        var nativeLinker = Linker.nativeLinker();
        MemorySegment handle = libraryLookup.findOrThrow("uring_read_buffered");
        uringReadBuffered = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

        handle = libraryLookup.findOrThrow("uring_read_direct");
        uringReadDirect = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

        handle = libraryLookup.findOrThrow("initialize_uring");
        uringInit = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(ADDRESS, JAVA_INT));

        handle = libraryLookup.findOrThrow("close_uring");
        uringClose = nativeLinker.downcallHandle(handle, FunctionDescriptor.ofVoid(ADDRESS));
    }

    static {
        Path libFile;
        IoUring nativeAlgosI = null;
        // copy resource to temp file so it can be loaded
        try (var is = NativeAlgos.class.getClassLoader().getResourceAsStream("liburing.so")) {
            var tempFile = File.createTempFile("liburing", ".so");
            tempFile.deleteOnExit();

            try (var os = new FileOutputStream(tempFile)) {
                is.transferTo(os);
                os.flush();
            }

            System.load(tempFile.getAbsolutePath());
        }
        catch (Exception e) {
            logger.info("Failed to load native library, likely not built", e);
        }

        try (var is = NativeAlgos.class.getClassLoader().getResourceAsStream("libcpp.so")) {
            var tempFile = File.createTempFile("libcpp", ".so");
            tempFile.deleteOnExit();

            try (var os = new FileOutputStream(tempFile)) {
                is.transferTo(os);
                os.flush();
            }

            libFile = tempFile.toPath();
            nativeAlgosI = new IoUring(libFile);
        }
        catch (Exception e) {
            logger.info("Failed to load native library, likely not built", e);
        }

        instance = nativeAlgosI;
        isAvailable = instance != null;
    }

    public static UringQueue uringOpen(int fd, int queueSize) {
        try {
            return new UringQueue((MemorySegment) instance.uringInit.invoke(queueSize), fd);
        }
        catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static void uringClose(UringQueue ring) {
        try {
            instance.uringClose.invoke(ring.pointer());
        }
        catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static int uringReadBatch(int fd, UringQueue ring, List<MemorySegment> dest, List<Long> offsets, boolean direct) {
        if (offsets.isEmpty()) {
            throw new IllegalArgumentException("Empty offset list in  uringRead");
        }
        if (offsets.size() == 1) {
            if (LinuxSystemCalls.readAt(fd, dest.getFirst(), offsets.getFirst()) > 0)
                return 1;
            else return -1;
        }
        try {
            MemorySegment bufferList = Arena.ofAuto().allocate(8L * offsets.size(), 8);
            MemorySegment sizeList = Arena.ofAuto().allocate(4L * offsets.size(), 8);
            MemorySegment offsetList = Arena.ofAuto().allocate(8L * offsets.size(), 8);

            if (dest.size() != offsets.size()) {
                throw new IllegalStateException();
            }

            for (int i = 0; i < offsets.size(); i++) {
                var buffer = dest.get(i);
                bufferList.setAtIndex(JAVA_LONG, i, buffer.address());
                sizeList.setAtIndex(JAVA_INT, i, (int) buffer.byteSize());
                offsetList.setAtIndex(JAVA_LONG, i, offsets.get(i));
            }
            if (direct) {
                return (Integer) instance.uringReadDirect.invoke(fd, ring.pointer(), dest.size(), bufferList, sizeList, offsetList);
            }
            else {
                return (Integer) instance.uringReadBuffered.invoke(fd, ring.pointer(), dest.size(), bufferList, sizeList, offsetList);
            }
        }
        catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

}
