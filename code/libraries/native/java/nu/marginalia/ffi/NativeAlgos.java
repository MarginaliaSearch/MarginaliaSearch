package nu.marginalia.ffi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

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
