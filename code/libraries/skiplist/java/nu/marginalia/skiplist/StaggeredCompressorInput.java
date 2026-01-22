package nu.marginalia.skiplist;

import nu.marginalia.array.LongArray;
import nu.marginalia.skiplist.compression.input.CompressorInput;

import static nu.marginalia.skiplist.SkipListConstants.RECORD_SIZE;

class StaggeredCompressorInput implements CompressorInput {
    private final LongArray array;

    private int n;
    private int start = 0;

    StaggeredCompressorInput(LongArray array, int n) {
        this.array = array;
        setBounds(0, n);
    }

    StaggeredCompressorInput(LongArray array, int start, int n) {
        this.array = array;
        setBounds(start, n);
    }

    public static StaggeredCompressorInput copyOf(StaggeredCompressorInput other) {
        return new StaggeredCompressorInput(other.array, other.start, other.n);
    }

    public static StaggeredCompressorInput copyOfShifted(StaggeredCompressorInput other, int offset) {
        return new StaggeredCompressorInput(other.array, other.start + offset, other.n);
    }

    public void setBounds(int start, int n) {
        this.start = Math.clamp(start, 0, (int) array.size() / RECORD_SIZE);
        this.n = Math.clamp(n, 0, (int) (array.size() / RECORD_SIZE) - start);
    }

    public void moveBounds(int offset) {
        setBounds(start + offset, n);
    }

    @Override
    public long at(int i) {
        return array.get((long) RECORD_SIZE * (start + i));
    }

    @Override
    public int size() {
        return n;
    }
}
