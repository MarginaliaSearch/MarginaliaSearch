
package nu.marginalia.ffi;

import nu.marginalia.uring.UringQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.*;

/** This class provides access to wrapper around Linux system calls.
 *  <p></p>
 *  isAvailable is a boolean flag that indicates whether the native
 *  implementations are available. If the shared library cannot be loaded,
 *  isAvailable will be false.  This flag must be checked before calling
 *  any of the native functions.
 * */
public class IoUring {

    private static boolean useIoUring = !Boolean.getBoolean("system.disableIoUring");

    private final MethodHandle uringInitRegisteredFd;
    private final MethodHandle uringClose;

    private final MethodHandle uringReadBufferedRaw;
    private final MethodHandle uringRegisterBufferRaw;
    private final MethodHandle uringReadFixedRaw;

    public static final IoUring instance;

    /** Indicates whether the native implementations are available */
    public static final boolean isAvailable;

    private static final Logger logger = LoggerFactory.getLogger(IoUring.class);

    private IoUring(Path libFile) {
        SymbolLookup libraryLookup = SymbolLookup.libraryLookup(libFile, Arena.global());
        var nativeLinker = Linker.nativeLinker();
        MemorySegment handle;

        useIoUring = useIoUring && libraryLookup.find("initialize_uring_single_file").isPresent();
        if (useIoUring) {
            System.err.println("io_uring enabled");
        }
        else {
            System.err.println("io_uring disabled");
        }


        if (useIoUring) {
            // The raw address bindings avoid a session acquire on every argument as
            // well as per call marshalling allocations.  The caller owns and reuses
            // the argument arrays.
            handle = libraryLookup.findOrThrow("uring_read_buffered");
            uringReadBufferedRaw = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG));

            handle = libraryLookup.findOrThrow("uring_register_buffer");
            uringRegisterBufferRaw = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG));

            handle = libraryLookup.findOrThrow("uring_read_fixed");
            uringReadFixedRaw = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG));

            handle = libraryLookup.findOrThrow("initialize_uring_single_file");
            uringInitRegisteredFd = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT));

            handle = libraryLookup.findOrThrow("close_uring");
            uringClose = nativeLinker.downcallHandle(handle, FunctionDescriptor.ofVoid(ADDRESS));
        }
        else {
            uringInitRegisteredFd = null;
            uringClose = null;
            uringReadBufferedRaw = null;
            uringRegisterBufferRaw = null;
            uringReadFixedRaw = null;
        }
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
        isAvailable = instance != null && useIoUring;
    }

    // Kept in a static final so the JIT can constant fold the handle and inline the downcall
    private static final MethodHandle URING_READ_BUFFERED_RAW =
            (instance != null) ? instance.uringReadBufferedRaw : null;
    private static final MethodHandle URING_READ_FIXED_RAW =
            (instance != null) ? instance.uringReadFixedRaw : null;

    /** Submit n buffered reads in one syscall and wait for all of them.  All pointer
     *  arguments are raw addresses to caller owned arrays of length n: buffer
     *  addresses, read sizes, and file offsets.  The ring must be used by one thread
     *  at a time and have capacity for n requests. */
    public static int readBatchRaw(UringQueue ring, int n, long buffersAddr, long sizesAddr, long offsetsAddr) {
        try {
            return (int) URING_READ_BUFFERED_RAW.invokeExact(ring.pointer().address(), n, buffersAddr, sizesAddr, offsetsAddr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    /** Register a buffer with the ring for use with readFixedBatchRaw.  All reads
     *  through that entry point must target memory within the registered buffer.
     *  Registered buffers are pinned and count against RLIMIT_MEMLOCK, so this can
     *  fail with -ENOMEM under default service limits.  Callers should treat
     *  registration as an optimization and fall back to unregistered reads. */
    public static void registerBuffer(UringQueue ring, long bufferAddr, long bufferLen) {
        int ret;
        try {
            ret = (int) instance.uringRegisterBufferRaw.invokeExact(ring.pointer().address(), bufferAddr, bufferLen);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
        if (ret != 0) {
            throw new IllegalStateException("io_uring_register_buffers failed: " + ret + (ret == -12 ? " (ENOMEM, likely RLIMIT_MEMLOCK)" : ""));
        }
    }

    /** Like readBatchRaw, but the reads skip per operation buffer import against
     *  the ring's registered buffer. */
    public static int readFixedBatchRaw(UringQueue ring, int n, long buffersAddr, long sizesAddr, long offsetsAddr) {
        try {
            return (int) URING_READ_FIXED_RAW.invokeExact(ring.pointer().address(), n, buffersAddr, sizesAddr, offsetsAddr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static UringQueue uringOpen(int fd, int queueSize) {
        if (!useIoUring) {
            throw new IllegalStateException("io_uring is not available");
        }

        MemorySegment ring;
        try {
            ring = (MemorySegment) instance.uringInitRegisteredFd.invoke(queueSize, fd);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }

        if (ring == null || ring.address() == 0) {
            throw new IllegalStateException("io_uring initialization failed");
        }
        return new UringQueue(ring, fd);
    }

    public static void uringClose(UringQueue ring) {
        if (useIoUring) {
            try {
                instance.uringClose.invoke(ring.pointer());
            } catch (Throwable t) {
                throw new RuntimeException("Failed to invoke native function", t);
            }
        }
    }

}
