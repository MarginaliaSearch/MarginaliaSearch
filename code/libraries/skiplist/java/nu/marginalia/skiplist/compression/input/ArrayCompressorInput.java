package nu.marginalia.skiplist.compression.input;

public class ArrayCompressorInput implements CompressorInput {
    private final long[] data;

    public ArrayCompressorInput(long... data) {
        this.data = data;
    }
    @Override
    public long at(int i) {
        return data[i];
    }

    @Override
    public int size() {
        return data.length;
    }
}
