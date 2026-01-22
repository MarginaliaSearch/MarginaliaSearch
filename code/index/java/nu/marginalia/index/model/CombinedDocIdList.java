package nu.marginalia.index.model;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import nu.marginalia.array.page.LongQueryBuffer;
import org.checkerframework.checker.units.qual.C;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

/** A list of document ids, with their ranking bits still remaining.
 *
 * @see DocIdList
 * @see nu.marginalia.model.id.UrlIdCodec
 * */
public final class CombinedDocIdList {
    private final long[] data;

    public CombinedDocIdList(long... data) {
        this.data = Arrays.copyOf(data, data.length);
    }
    public CombinedDocIdList(long[] data, int start, int n) {
        this.data = Arrays.copyOfRange(data, start, start + n);
    }
    public CombinedDocIdList(LongQueryBuffer buffer) {
        this.data = buffer.copyData();
    }
    public CombinedDocIdList(LongArrayList data) {
        this.data = data.toLongArray();
    }
    public CombinedDocIdList() {
        this.data = new long[0];
    }

    public static CombinedDocIdList combineLists(CombinedDocIdList one, CombinedDocIdList other) {
        long[] data = new long[one.size() + other.size()];
        System.arraycopy(one.data, 0, data, 0, one.data.length);
        System.arraycopy(other.data, 0, data, one.data.length, other.data.length);
        return new CombinedDocIdList(data);
    }

    public List<CombinedDocIdList> split(int maxSize) {
        List<CombinedDocIdList> ret = new ArrayList<>(data.length / maxSize + Integer.signum(data.length % maxSize));
        for (int start = 0; start < data.length; start+=maxSize) {
            if (start + maxSize < data.length) {
                ret.add(new CombinedDocIdList(Arrays.copyOfRange(data, start, start+maxSize)));
            }
            else {
                ret.add(new CombinedDocIdList(Arrays.copyOfRange(data, start, data.length)));
            }
        }
        return ret;
    }

    public int size() {
        return data.length;
    }
    public boolean isEmpty() {
        return data.length == 0;
    }
    public long at(int i) { return data[i]; }

    public LongStream stream() {
        return Arrays.stream(data);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (CombinedDocIdList) obj;
        return Arrays.equals(this.data, that.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }


    public long[] array() {
        return data;
    }

    public void sort() {
        Arrays.sort(data);
    }

}

