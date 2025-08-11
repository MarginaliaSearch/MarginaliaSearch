
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
    public final MethodHandle uringInit;
    public final MethodHandle uringClose;
    private final MethodHandle uringReadBuffered;
    private final MethodHandle uringReadDirect;
    public final MethodHandle uringReadAndPoll;
    public final MethodHandle uringJustPoll;

    public static final IoUring instance;

    /** Indicates whether the native implementations are available */
    public static final boolean isAvailable;

    private static final Logger logger = LoggerFactory.getLogger(IoUring.class);

    private IoUring(Path libFile) {
        SymbolLookup libraryLookup = SymbolLookup.libraryLookup(libFile, Arena.global());
        var nativeLinker = Linker.nativeLinker();
        MemorySegment handle = libraryLookup.findOrThrow("uring_read_buffered");
        uringReadBuffered = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

        handle = libraryLookup.findOrThrow("uring_read_direct");
        uringReadDirect = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

        handle = libraryLookup.findOrThrow("uring_read_submit_and_poll");

        uringReadAndPoll = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(
                JAVA_INT,
                ADDRESS,  // io_uring* ring
                ADDRESS,  // long* result_ids
                JAVA_INT, // int in_flight_requests
                JAVA_INT, // int read_count
                ADDRESS,  // long* read_batch_ids
                ADDRESS,  // int* read_fds
                ADDRESS,  // void** read_buffers
                ADDRESS,  // unsigned int** read_sizes
                ADDRESS  // long* read_offsets
        ));
        handle = libraryLookup.findOrThrow("uring_poll");

        uringJustPoll = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(
                JAVA_INT,
                ADDRESS,  // io_uring* ring
                ADDRESS   // long* result_ids
        ));

        handle = libraryLookup.findOrThrow("initialize_uring");
        uringInit = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(ADDRESS, JAVA_INT));

        handle = libraryLookup.findOrThrow("close_uring");
        uringClose = nativeLinker.downcallHandle(handle, FunctionDescriptor.ofVoid(ADDRESS));
    }

    static {
        Path libFile;
        IoUring ioUringI = null;
        // copy resource to temp file so it can be loaded
        try (var is = IoUring.class.getClassLoader().getResourceAsStream("liburing.so")) {
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

        try (var is = IoUring.class.getClassLoader().getResourceAsStream("libcpp.so")) {
            var tempFile = File.createTempFile("libcpp", ".so");
            tempFile.deleteOnExit();

            try (var os = new FileOutputStream(tempFile)) {
                is.transferTo(os);
                os.flush();
            }

            libFile = tempFile.toPath();
            ioUringI = new IoUring(libFile);
        }
        catch (Exception e) {
            logger.info("Failed to load native library, likely not built", e);
        }

        instance = ioUringI;
        isAvailable = instance != null;
    }

    public static IoUring instance() {
        return instance;
    }

    public static UringQueue uringOpen(int fd, int queueSize) {
        try {
            System.out.println(instance.uringInit != null);
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
