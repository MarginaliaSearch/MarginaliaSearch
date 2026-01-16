package nu.marginalia.skiplist.compression.input;

public interface CompressorInput {
    long at(int n);

    int size();
}
