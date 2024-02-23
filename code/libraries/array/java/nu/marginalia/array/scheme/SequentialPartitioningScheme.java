package nu.marginalia.array.scheme;

public class SequentialPartitioningScheme implements ArrayPartitioningScheme {

    final int partitionSize;

    public SequentialPartitioningScheme(int partitionSize) {
        this.partitionSize = partitionSize;
    }

    public static int getRequiredPartitions(long cardinality, int partitionSize) {
        return (int) (cardinality / partitionSize + Long.signum(cardinality % partitionSize));
    }

    @Override
    public int getPartitions(long cardinality) {
        return getRequiredPartitions(cardinality, partitionSize);
    }

    @Override
    public int getPage(long at) {
        return (int) (at / partitionSize);
    }

    public long getPageEnd(long at, long endTotal) {
        return Math.min(endTotal, partitionSize * (1L + getPage(at)));
    }


    @Override
    public boolean isSamePage(long a, long b) {
        return (int) (a / partitionSize) == (int)(b/partitionSize);
    }

    @Override
    public int getOffset(long at) {
        return (int) (at % partitionSize);
    }

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
