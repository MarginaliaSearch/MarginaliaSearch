package nu.marginalia.skiplist.compression.output;

public interface WritableCompressorBufferIf {
    void put(long val, int bytes);
    void padToLong();

    long getPos();
    void setPos(long pos);
    void advancePos(int n);
}
