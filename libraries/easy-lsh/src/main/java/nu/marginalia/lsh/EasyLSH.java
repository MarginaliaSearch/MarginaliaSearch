package nu.marginalia.lsh;

/** This is a very simple locality sensitive hash for collections of Java objects.
 * <p>
 * The resulting LSH is a 64 bit value, whose hamming distance is a measure
 * of the similarity of the two collections, where smaller similarities imply
 * similarity.
 * <p>
 * It hinges on a lot of relatively sketchy assumptions about Object$hashCode().
 *
 */
public class EasyLSH {
    // This parameter determines the amount of shingling. Increasing this
    // increases the sensitivity to word ordering, but also magnifies
    // the impact of each difference overall.
    //
    // This must be a power of 2, lest things break
    private static final int SHINGLING = 2;
    static { assert Integer.bitCount(SHINGLING) == 1; }

    private final int[] fields = new int[64];
    private final int[] prevHashes = new int[SHINGLING];
    private int prevHashIdx = 0;

    public void addUnordered(Object o) {
        addHashUnordered(o.hashCode());
    }

    public void addOrdered(Object o) {
        addHashOrdered(o.hashCode());
    }

    public void addHashOrdered(int hashCode) {
        addHashUnordered(shingleHash(hashCode));
    }

    public void addHashUnordered(int hashCode) {
        int value = 1 - (hashCode & 2);

        // Try to extract all the remaining entropy
        // into selecting the field to update

        int field = (hashCode >> 2)
                  ^ (hashCode >>> 8)
                  ^ (hashCode >>> 14)
                  ^ (hashCode >>> 20)
                  ^ (hashCode >>> 26);

        fields[field & 63] += value;
    }

    private int shingleHash(int nextHash) {
        prevHashes[prevHashIdx++ & (SHINGLING-1)] = nextHash;

        int ret = 0;
        for (int hashPart : prevHashes) {
            ret = hashPart ^ ret;
        }

        return ret;
    }

    public long get() {
        long val = 0;

        for (int f : fields) {
            val = (val << 1) | (f >>> 31);
        }

        return val;
    }

    public static int hammingDistance(long a, long b) {
        return Long.bitCount(a^b);
    }

    public static int hammingDistance(EasyLSH a, EasyLSH b) {
        int distance = 0;

        for (int i = 0; i < a.fields.length; i++) {
            distance += (a.fields[i] ^ b.fields[i]) >>> 31;
        }

        return distance;
    }

}
