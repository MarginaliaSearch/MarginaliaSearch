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
    private final MethodHandle fadviseRandom;
    private final MethodHandle fadviseWillneed;
    private final MethodHandle madviseRandom;

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

        handle = libraryLookup.findOrThrow("madvise_random");
        madviseRandom = nativeLinker.downcallHandle(handle, FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG));

        handle = libraryLookup.findOrThrow("close_fd");
        closeFd = nativeLinker.downcallHandle(handle, FunctionDescriptor.ofVoid(JAVA_INT));

        // The buffer is passed as a raw address rather than a segment.  Acquiring the
        // session of a shared or global arena segment contends on a single counter
        // between all query threads, and readAt runs for every ranked document.
        // The caller must keep the segment alive across the call.
        handle = libraryLookup.findOrThrow("read_at");
        readAtFd = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_LONG));
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

    // Kept in a static final so the JIT can constant fold the handle and inline the downcall
    private static final MethodHandle READ_AT = isAvailable ? instance.readAtFd : null;

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

    public static void madviseRandom(MemorySegment segment) {
        try {
            instance.madviseRandom.invoke(segment, segment.byteSize());
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
