package nu.marginalia.ffi;

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
public class NativeAlgos {
    private final MethodHandle qsortHandle;
    private final MethodHandle qsort128Handle;
    private final MethodHandle qsort192Handle;
    private final MethodHandle countDistinct;
    private final MethodHandle mergeArrays1;
    private final MethodHandle mergeArrays2;
    private final MethodHandle mergeArrays3;
    private final MethodHandle decompressDocIds;
    private final MethodHandle decompressMatch;
    private final MethodHandle decodeVarintBatch;

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


        handle = libraryLookup.findOrThrow("ms_sort_192");
        qsort192Handle = nativeLinker.downcallHandle(handle,
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_LONG));

        handle = libraryLookup.findOrThrow("count_distinct");
        countDistinct = nativeLinker.downcallHandle(handle,
                FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG));

        handle = libraryLookup.findOrThrow("merge_arrays_3");
        mergeArrays3 = nativeLinker.downcallHandle(handle,
                FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG));


        handle = libraryLookup.findOrThrow("merge_arrays_2");
        mergeArrays2 = nativeLinker.downcallHandle(handle,
                FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG));

        handle = libraryLookup.findOrThrow("merge_arrays_1");
        mergeArrays1 = nativeLinker.downcallHandle(handle,
                FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG));

        // The critical linker option skips the thread state transition, and permits passing
        // heap segments such as the output array without copying.  The input is passed as
        // a raw address rather than a segment, as acquiring the session of a shared arena
        // segment on every call contends badly between query threads.  The caller must
        // keep the segment alive across the call.
        handle = libraryLookup.findOrThrow("ms_decompress_docids");
        decompressDocIds = nativeLinker.downcallHandle(handle,
                FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, ADDRESS),
                Linker.Option.critical(true));

        handle = libraryLookup.findOrThrow("ms_decode_varint_batch");
        decodeVarintBatch = nativeLinker.downcallHandle(handle,
                FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS),
                Linker.Option.critical(true));

        handle = libraryLookup.findOrThrow("ms_decompress_match");
        decompressMatch = nativeLinker.downcallHandle(handle,
                FunctionDescriptor.of(JAVA_LONG,
                        JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT,
                        ADDRESS, JAVA_INT, JAVA_INT,
                        JAVA_LONG, JAVA_LONG,
                        ADDRESS, JAVA_INT),
                Linker.Option.critical(true));
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

    // Kept in static finals so the JIT can constant fold the handles and inline the
    // downcalls, which an instance field load defeats.  The generic dispatch dominates
    // the call cost for the small blocks typical of query execution.
    private static final MethodHandle DECOMPRESS_DOC_IDS = isAvailable ? instance.decompressDocIds : null;
    private static final MethodHandle DECOMPRESS_MATCH = isAvailable ? instance.decompressMatch : null;
    private static final MethodHandle DECODE_VARINT_BATCH = isAvailable ? instance.decodeVarintBatch : null;

    /** Decompress n doc ids from the compressed representation in the input segment,
     *  starting at position pos, into the output array.  Returns the input position
     *  after the last consumed byte. */
    public static long decompressDocIds(MemorySegment input, long pos, int n, long[] output) {
        try {
            return (long) DECOMPRESS_DOC_IDS.invokeExact(input.address(), pos, input.byteSize(), n, MemorySegment.ofArray(output));
        }
        catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    /** Decode the compressed doc id run at pos and merge it against the sorted keys,
     *  writing a value offset or -1 per consumed key into outOffsets starting at outIdx.
     *  Returns the record index in the high 32 bits and the key index in the low. */
    public static long decompressMatch(MemorySegment input, long pos, int n,
                                       long[] keys, int keyIdx,
                                       long valuesOffset, long offsetStride,
                                       long[] outOffsets, int outIdx) {
        try {
            return (long) DECOMPRESS_MATCH.invokeExact(input.address(), pos, input.byteSize(), n,
                    MemorySegment.ofArray(keys), keys.length, keyIdx,
                    valuesOffset, offsetStride,
                    MemorySegment.ofArray(outOffsets), outIdx);
        }
        catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    /** Decode a batch of varint coded position sequences located at addrs[i]
     *  with byte lengths lens[i], writing all values sequentially into out and
     *  each sequence's value count into counts.  Returns the total number of
     *  values written.  The out array must have room for the worst case of one
     *  value per input byte, and the caller must keep the sequence memory alive
     *  across the call. */
    public static long decodeVarintBatch(long[] addrs, int[] lens, int n, int[] out, int[] counts) {
        return decodeVarintBatch(addrs, lens, 0, n, out, 0, counts);
    }

    /** Like {@link #decodeVarintBatch(long[], int[], int, int[], int[])}, reading
     *  n sequences starting at index from, and writing values from out[outOffset]
     *  and counts from counts[from]. */
    public static long decodeVarintBatch(long[] addrs, int[] lens, int from, int n, int[] out, long outOffset, int[] counts) {
        try {
            return (long) DECODE_VARINT_BATCH.invokeExact(
                    MemorySegment.ofArray(addrs).asSlice(8L * from),
                    MemorySegment.ofArray(lens).asSlice(4L * from),
                    n,
                    MemorySegment.ofArray(out).asSlice(4L * outOffset),
                    MemorySegment.ofArray(counts).asSlice(4L * from));
        }
        catch (Throwable t) {
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

    public static void sort192(MemorySegment ms, long start, long end) {
        try {
            instance.qsort192Handle.invoke(ms, start, end);
        }
        catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static long countDistinct(MemorySegment a, MemorySegment b, long aStart, long aEnd, long bStart, long bEnd) {
        try {
            return (Long) instance.countDistinct.invoke(
                    a.asSlice(aStart * JAVA_LONG.byteSize()),
                    b.asSlice(bStart * JAVA_LONG.byteSize()),
                    aEnd - aStart,
                    bEnd - bStart);
        }
        catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static long mergeArrays1(MemorySegment out, MemorySegment a, MemorySegment b, long outStart, long aStart, long aEnd, long bStart, long bEnd) {
        try {
            return (Long) instance.mergeArrays1.invoke(
                    out.asSlice(outStart * JAVA_LONG.byteSize()),
                    a.asSlice(aStart * JAVA_LONG.byteSize()),
                    b.asSlice(bStart * JAVA_LONG.byteSize()),
                    aEnd - aStart,
                    bEnd - bStart);
        }
        catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static long mergeArrays2(MemorySegment out, MemorySegment a, MemorySegment b, long outStart, long aStart, long aEnd, long bStart, long bEnd) {
        try {
            return (Long) instance.mergeArrays2.invoke(
                    out.asSlice(outStart * JAVA_LONG.byteSize()),
                    a.asSlice(aStart * JAVA_LONG.byteSize()),
                    b.asSlice(bStart * JAVA_LONG.byteSize()),
                    aEnd - aStart,
                    bEnd - bStart);
        }
        catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }

    public static long mergeArrays3(MemorySegment out, MemorySegment a, MemorySegment b, long outStart, long aStart, long aEnd, long bStart, long bEnd) {
        try {
            return (Long) instance.mergeArrays3.invoke(
                    out.asSlice(outStart * JAVA_LONG.byteSize()),
                    a.asSlice(aStart * JAVA_LONG.byteSize()),
                    b.asSlice(bStart * JAVA_LONG.byteSize()),
                    aEnd - aStart,
                    bEnd - bStart);
        }
        catch (Throwable t) {
            throw new RuntimeException("Failed to invoke native function", t);
        }
    }


}
