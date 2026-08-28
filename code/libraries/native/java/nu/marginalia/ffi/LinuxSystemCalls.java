package nu.marginalia.ffi;

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
public class LinuxSystemCalls {
    private final MethodHandle openDirect;
    private final MethodHandle openBuffered;
    private final MethodHandle closeFd;
    private final MethodHandle readAtFd;
    private final MethodHandle readVectoredAtFd;
    private final MethodHandle fadviseRandom;
    private final MethodHandle fadviseWillneed;
    private final MethodHandle fadviseWillneedRange;
    private final MethodHandle madviseRandom;
    private final MethodHandle madviseNormal;
    private final MethodHandle madviseWillneed;
    private final MethodHandle pageResident;

    public static final LinuxSystemCalls instance;

    /** Indicates whether the native implementations are available */
    public static final boolean isAvailable;

    private static final Logger logger = LoggerFactory.getLogger(LinuxSystemCalls.class);

    private LinuxSystemCalls(Path libFile) {
        SymbolLookup libraryLookup = SymbolLookup.libraryLookup(libFile, Arena.global());
        var nativeLinker = Linker.nativeLinker();
        MemorySegment handle = libraryLookup.findOrThrow("open_direct_fd");
        openDirect = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(JAVA_INT, ADDRESS));
        handle = libraryLookup.findOrThrow("open_buffered_fd");
        openBuffered = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(JAVA_INT, ADDRESS));

        handle = libraryLookup.findOrThrow("fadvise_random");
        fadviseRandom = nativeLinker.downcallHandle(handle, FunctionDescriptor.ofVoid(JAVA_INT));

        handle = libraryLookup.findOrThrow("fadvise_willneed");
        fadviseWillneed = nativeLinker.downcallHandle(handle, FunctionDescriptor.ofVoid(JAVA_INT));

        handle = libraryLookup.findOrThrow("fadvise_willneed_range");
        fadviseWillneedRange = nativeLinker.downcallHandle(handle, FunctionDescriptor.ofVoid(JAVA_INT, JAVA_LONG, JAVA_LONG));

        handle = libraryLookup.findOrThrow("madvise_random");
        madviseRandom = nativeLinker.downcallHandle(handle, FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG));

        handle = libraryLookup.findOrThrow("madvise_normal");
        madviseNormal = nativeLinker.downcallHandle(handle, FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG));

        handle = libraryLookup.findOrThrow("close_fd");
        closeFd = nativeLinker.downcallHandle(handle, FunctionDescriptor.ofVoid(JAVA_INT));

        // The the methods below pass raw addresses as longs rather than a segment.
        //
        // Acquiring the session of a shared or global arena segment contends on a single counter
        // between all query threads, a bottleneck for hot methods.
        //
        // This is cursed and it's upon the caller to keep the segment alive across the call.

        handle = libraryLookup.findOrThrow("madvise_willneed");
        madviseWillneed = nativeLinker.downcallHandle(handle, FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG));

        handle = libraryLookup.findOrThrow("page_resident");
        pageResident = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(JAVA_INT, JAVA_LONG));

        handle = libraryLookup.findOrThrow("read_at");
        readAtFd = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_LONG));

        handle = libraryLookup.findOrThrow("read_vectored_at");
        readVectoredAtFd = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_LONG));
    }

    static {
        Path libFile;
        LinuxSystemCalls nativeAlgosI = null;
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
            nativeAlgosI = new LinuxSystemCalls(libFile);
        }
        catch (Exception e) {
            logger.info("Failed to load native library, likely not built", e);
        }

        instance = nativeAlgosI;
        isAvailable = instance != null;
    }

    // Kept in static finals so the JIT can constant fold the handles and inline the downcalls
    private static final MethodHandle READ_AT = isAvailable ? instance.readAtFd : null;
    private static final MethodHandle READ_VECTORED_AT = isAvailable ? instance.readVectoredAtFd : null;
    private static final MethodHandle MADVISE_WILLNEED = isAvailable ? instance.madviseWillneed : null;
    private static final MethodHandle PAGE_RESIDENT = isAvailable ? instance.pageResident : null;

    public static int openDirect(Path filename) {
        try (var arena = Arena.ofConfined()) {
            MemorySegment filenameCStr = arena.allocateFrom(filename.toString());
            return (Integer) instance.openDirect.invoke(filenameCStr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static int openBuffered(Path filename) {
        try (var arena = Arena.ofConfined()) {
            MemorySegment filenameCStr = arena.allocateFrom(filename.toString());
            return (Integer) instance.openBuffered.invoke(filenameCStr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static int readAt(int fd, MemorySegment dest, long offset) {
        try {
            return (int) READ_AT.invokeExact(fd, dest.address(), (int) dest.byteSize(), offset);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    // 'iovecs' is an array of
    //
    //      struct iovec {
    //         void   *iov_base;  /* Starting address */
    //         size_t  iov_len;   /* Size of the memory pointed to by iov_base. */
    //     };
    //
    //   ... assumed packed
    public static long readVectoredAt(int fd, MemorySegment iovecs, int count, long offset) {
        try {
            return (long) READ_VECTORED_AT.invokeExact(fd, iovecs.address(), count, offset);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static void fadviseRandom(int fd) {
        try {
            instance.fadviseRandom.invoke(fd);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static void fadviseWillneed(int fd) {
        try {
            instance.fadviseWillneed.invoke(fd);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static void fadviseWillneed(int fd, long offset, long size) {
        try {
            instance.fadviseWillneedRange.invoke(fd, offset, size);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }
    /** Hint the kernel to read ahead the mapped range.  Address and length should
     *  be page aligned, and the caller must keep the mapping alive for the call. */
    public static void madviseWillneed(long address, long size) {
        try {
            MADVISE_WILLNEED.invokeExact(address, size);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    /** True if the page holding the mapped address is resident in the page
     *  cache.  The caller must keep the mapping alive for the call. */
    public static boolean isPageResident(long address) {
        try {
            return (int) PAGE_RESIDENT.invokeExact(address) > 0;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static void madviseRandom(MemorySegment segment) {
        try {
            instance.madviseRandom.invoke(segment, segment.byteSize());
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static void madviseNormal(MemorySegment segment) {
        try {
            instance.madviseNormal.invoke(segment, segment.byteSize());
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }
    public static void closeFd(int fd) {
        try {
            instance.closeFd.invoke(fd);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }
}
