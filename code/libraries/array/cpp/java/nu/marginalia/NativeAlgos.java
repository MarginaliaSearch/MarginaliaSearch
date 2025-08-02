package nu.marginalia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

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
    private final MethodHandle closeFd;
    private final MethodHandle readAtFd;

    public static final NativeAlgos instance;

    /** Indicates whether the native implementations are available */
    public static final boolean isAvailable;

    private static final Logger logger = LoggerFactory.getLogger(NativeAlgos.class);


    private NativeAlgos(Path libFile) {
        var libraryLookup = SymbolLookup.libraryLookup(libFile, Arena.global());
        var nativeLinker = Linker.nativeLinker();

        var handle = libraryLookup.find("ms_sort_64").get();
        qsortHandle = nativeLinker.downcallHandle(handle, FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_LONG));

        handle = libraryLookup.find("ms_sort_128").get();
        qsort128Handle = nativeLinker.downcallHandle(handle,
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_LONG));

        handle = libraryLookup.find("open_direct_fd").get();
        openDirect = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(JAVA_INT, ADDRESS));

        handle = libraryLookup.find("close_fd").get();
        closeFd = nativeLinker.downcallHandle(handle, FunctionDescriptor.ofVoid(JAVA_INT));

        handle = libraryLookup.find("read_at").get();
        readAtFd = nativeLinker.downcallHandle(handle, FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG));
    }

    static {
        Path libFile;
        NativeAlgos nativeAlgosI = null;
        // copy resource to temp file so it can be loaded
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
            e.printStackTrace();
            logger.info("Failed to load native library, likely not built");
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

    public static int readAt(int fd, MemorySegment dest, long offset) {
        try {
            return (Integer) instance.readAtFd.invoke(fd, dest, (int) dest.byteSize(), offset);
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
