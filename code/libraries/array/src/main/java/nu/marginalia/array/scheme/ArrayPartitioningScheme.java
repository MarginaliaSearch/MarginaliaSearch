package nu.marginalia.array.scheme;

public interface ArrayPartitioningScheme {

    static ArrayPartitioningScheme forPartitionSize(int size) {
        if (Integer.highestOneBit(size) == size) {
            return new PowerOf2PartitioningScheme(size);
        }
        else {
            return new SequentialPartitioningScheme(size);
        }
    }
    static int getRequiredPartitions(long cardinality, int partitionSize) {
        return (int) (cardinality / partitionSize + Long.signum(cardinality % partitionSize));
    }


    int getPartitions(long cardinality);

    int getPage(long at);

    boolean isSamePage(long a, long b);

    /** Get the page offset corresponding to at */
    int getOffset(long at);

    /** Variant of getOffset that doesn't wrap around the page boundary, necessary when
     * translating an exclusive end offset that getOffset(...) will translate to 0 and consider
     * part of the next page.
     *
     * It is also necessary to consider the start offset to determine when the end offset
     *
     */
    default int getEndOffset(long start, long end) {
        if (end == 0 || end <= start)
            return getOffset(end);

        return 1 + getOffset(end - 1);
    }

    /** Get the end of the buffer containing at, or endTotal, whichever is smaller
     */
    long getPageEnd(long at, long endTotal);

    /**
     * toRealIndex(getBuffer(val), getOffset(val)) = val
     */
    long toRealIndex(int buffer, int offset);

    int getRequiredPageSize(int buffer, long cardinality);
}
