package nu.marginalia.array.scheme;

public class PowerOf2PartitioningScheme implements ArrayPartitioningScheme {
    final int partitionSize;
    final long offsetMask;
    final long bufferMask;
    final int pageShift;

    public PowerOf2PartitioningScheme(int partitionSize) {
        assert partitionSize == Integer.highestOneBit(partitionSize);

        this.partitionSize = partitionSize;

        offsetMask = partitionSize - 1;
        bufferMask = ~offsetMask;
        pageShift = Integer.numberOfTrailingZeros(partitionSize);
    }

    @Override
    public int getPartitions(long cardinality) {
        return ArrayPartitioningScheme.getRequiredPartitions(cardinality, partitionSize);
    }

    @Override
    public int getPage(long at) { // very hot code
        return (int) (at >>> pageShift);
    }

    @Override
    public int getOffset(long at) { // very hot code
        return (int) (at & offsetMask);
    }

    @Override
    public boolean isSamePage(long a, long b) { // hot code
        return 0 == ((a ^ b) & bufferMask);
    }

    @Override
    public long getPageEnd(long at, long endTotal) {
        return Math.min(endTotal, partitionSize * (1L + getPage(at)));
    }

    @Override
    public long toRealIndex(int buffer, int offset) {
        return offset + (long) buffer * partitionSize;
    }

    @Override
    public int getRequiredPageSize(int buffer, long cardinality) {

        if ((long) (1 + buffer) * partitionSize <= cardinality) {
            return partitionSize;
        }

        return (int) (cardinality % partitionSize);
    }


}
