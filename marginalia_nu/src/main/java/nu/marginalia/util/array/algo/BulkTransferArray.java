package nu.marginalia.util.array.algo;

public interface BulkTransferArray<BufferType> {

    void set(long start, long end, BufferType buffer, int bufferStart);

    void get(long start, long end, BufferType buffer, int bufferStart);
}
