package nu.marginalia.index.results.model.ids;

import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.LongStream;

public final class DocMetadataList {
    private final long[] array;

    public DocMetadataList(long[] array) {
        this.array = array;
    }

    public DocMetadataList(LongArrayList list) {
        this(list.toLongArray());
    }

    public int size() {
        return array.length;
    }

    public LongStream stream() {
        return LongStream.of(array);
    }

    public long[] array() {
        return array;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (DocMetadataList) obj;
        return Arrays.equals(this.array, that.array);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(array);
    }

}
