package nu.marginalia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.List;

import static java.lang.foreign.ValueLayout.*;

/** This class provides access to native implementations of key algorithms
 *  used in index construction and querying.
 *  <p></p>
 *  The native implementations are provided in a shared library, which is
 *  loaded at runtime. The shared library is copied from the resources
 *  to a temporary file, and then loaded using the foreign linker API.
 *  <p></p>
 *  isAvailable is a boolean flag that indicates whether the native
 *  implementations are available. If the shared library cannot be loaded,
 *  isAvailable will be false.  This flag must be checked before calling
 *  any of the native functions.
 * */
@SuppressWarnings("preview")
public class NativeAlgos {
    private final MethodHandle qsortHandle;
    private final MethodHandle qsort128Handle;
    private final MethodHandle openDirect;
    private final MethodHandle openBuffered;
    private final MethodHandle closeFd;
    private final MethodHandle readAtFd;

    private final MethodHandle uringInit;
    private final MethodHandle uringClose;
    private final MethodHandle uringReadBuffered;
    private final MethodHandle uringReadDirect;

    public static final NativeAlgos instance;

    /** Indicates whether the native implementations are available */
    public static final boolean isAvailable;

    private static final Logger logger = LoggerFactory.getLogger(NativeAlgos.class);

    private NativeAlgos(Path libFile) {
        SymbolLookup libraryLookup = SymbolLookup.libraryLookup(libFile, Arena.global());
        var nativeLinker = Linker.nativeLinker();

        MemorySegment handle = libraryLookup.findOrThrow("ms_sort_64");
        qsortHandle = nativeLinker.downcallHandle(handle, FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_LONG));

        handle = libraryLookup.findOrThrow("ms_sort_128");
        qsort128Handle = nativeLinker.downcallHandle(handle,
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_LONG));

        handle = libraryLookup.findOrThrow("open_direct_fd");
        openDirect = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(JAVA_INT, ADDRESS));
        handle = libraryLookup.findOrThrow("open_buffered_fd");
        openBuffered = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(JAVA_INT, ADDRESS));

        handle = libraryLookup.findOrThrow("uring_read_buffered");
        uringReadBuffered = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

        handle = libraryLookup.findOrThrow("uring_read_direct");
        uringReadDirect = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

        handle = libraryLookup.findOrThrow("initialize_uring");
        uringInit = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(ADDRESS, JAVA_INT));

        handle = libraryLookup.findOrThrow("close_uring");
        uringClose = nativeLinker.downcallHandle(handle, FunctionDescriptor.ofVoid(ADDRESS));

        handle = libraryLookup.findOrThrow("close_fd");
        closeFd = nativeLinker.downcallHandle(handle, FunctionDescriptor.ofVoid(JAVA_INT));

        handle = libraryLookup.findOrThrow("read_at");
        readAtFd = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG));
    }

    static {
        Path libFile;
        NativeAlgos nativeAlgosI = null;
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
            nativeAlgosI = new NativeAlgos(libFile);
        }
        catch (Exception e) {
            logger.info("Failed to load native library, likely not built", e);
        }

        instance = nativeAlgosI;
        isAvailable = instance != null;
    }

    public static int openDirect(Path filename) {
        try {
            MemorySegment filenameCStr = Arena.global().allocateFrom(filename.toString());
            return (Integer) instance.openDirect.invoke(filenameCStr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static int openBuffered(Path filename) {
        try {
            MemorySegment filenameCStr = Arena.global().allocateFrom(filename.toString());
            return (Integer) instance.openBuffered.invoke(filenameCStr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static int readAt(int fd, MemorySegment dest, long offset) {
        try {
            return (Integer) instance.readAtFd.invoke(fd, dest, (int) dest.byteSize(), offset);
        }
        catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
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

    public static int uringRead(int fd, UringQueue ring, List<MemorySegment> dest, List<Long> offsets, boolean direct) {
        if (offsets.isEmpty()) {
            throw new IllegalArgumentException("Empty offset list in  uringRead");
        }
        if (offsets.size() == 1) {
            if (readAt(fd, dest.getFirst(), offsets.getFirst()) > 0)
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

    public static void closeFd(int fd) {
        try {
            instance.closeFd.invoke(fd);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }
    public static void sort(MemorySegment ms, long start, long end) {
        try {
            instance.qsortHandle.invoke(ms, start, end);
        }
        catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static void sort128(MemorySegment ms, long start, long end) {
        try {
            instance.qsort128Handle.invoke(ms, start, end);
        }
        catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

}
