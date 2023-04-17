package nu.marginalia.btree;

import nu.marginalia.array.algo.TwoArrayOperations;

import java.io.IOException;
import java.util.function.LongBinaryOperator;

/** Functions for merging btrees.
 *
 */
public class BTreeMerger {

    /** Merge two BTrees into a new BTree. The two BTrees must have an entry size of 1.
     *
     * @return the size of the written data
     */
    public static long merge1(BTreeReader left,
                              BTreeReader right,
                              BTreeWriter writer,
                              long writeOffset) throws IOException
    {
        assert left.ctx.entrySize == 1;
        assert right.ctx.entrySize == 1;

        final long size = TwoArrayOperations.countDistinctElements(
                left.data(),
                right.data(),
                0, left.numEntries(),
                0, right.numEntries()
        );

        int numEntries = (int) size;

        return writer.write(writeOffset, numEntries, slice -> {
            long end = TwoArrayOperations.mergeArrays(slice, left.data(), right.data(),
                    0, numEntries,
                    0, left.numEntries(),
                    0, right.numEntries()
            );
            assert end == numEntries;
        });
    }

    /** Merge two BTrees into a new BTree. The two BTrees must have an entry size of 2.
     * The merge function is applied to the values of the two BTrees.
     *
     * Caveat: This function merges the common values into the left tree before merging the two trees.
     *
     * @return the size of the written data
     */
    public static long merge2(BTreeReader left,
                              BTreeReader right,
                              BTreeWriter writer,
                              LongBinaryOperator mergeFunction,
                              long writeOffset) throws IOException
    {
        assert left.ctx.entrySize == 2;
        assert right.ctx.entrySize == 2;

        final long size = TwoArrayOperations.countDistinctElementsN(2,
                left.data(), right.data(),
                0, left.data().size(),
                0, right.data().size()
        );

        int numEntries = (int) size;

        long leftSize = left.data().size();
        long rightSize = right.data().size();

        // Merge the common values into the left tree
        TwoArrayOperations.mergeArrayValues(
                left.data(),
                right.data(),
                mergeFunction,
                0, leftSize,
                0, rightSize);

        return writer.write(writeOffset, numEntries, slice -> {
            TwoArrayOperations.mergeArrays2(slice,
                    left.data(),
                    right.data(),
                    0, 2 * size,
                    0, leftSize,
                    0, rightSize);
        });
    }

}
