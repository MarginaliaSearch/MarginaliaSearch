package nu.marginalia.array.pool;

public interface PagePool extends AutoCloseable {

    MemoryPage get(long address);
    MemoryPage get(long address, int readAheadPages);

    void reset() throws InterruptedException;

    void close();
}
