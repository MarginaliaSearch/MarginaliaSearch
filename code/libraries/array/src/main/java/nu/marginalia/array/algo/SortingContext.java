package nu.marginalia.array.algo;

import java.nio.file.Path;

/**
 *
 * @param tempDir Directory where MergeSort will allocate temporary buffers
 * @param memorySortLimit Breaking point where MergeSort will be preferred over QuickSort. This is specified in
 *                        number of items. So for e.g. long array n=2, 16 bytes x this value is the memory usage
 */
public record SortingContext(
        Path tempDir,
        int memorySortLimit) {
}
