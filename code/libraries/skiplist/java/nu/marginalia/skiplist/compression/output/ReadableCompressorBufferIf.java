package nu.marginalia.skiplist.compression.output;

public interface ReadableCompressorBufferIf {
    long get(int bytes);

    long getPos();
    void setPos(long pos);
    void advancePos(int n);
}
